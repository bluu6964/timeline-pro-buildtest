// clip_graph.cpp — see clip_graph.h. Ports of the Kotlin clip-graph
// algebra (migration stage 2, build 245), decision-for-decision identical
// to the originals in model/GroupOps.kt, model/LaneLayout.kt,
// model/MultiSelectOps.kt, model/ControllerFanOut.kt, model/SoloState.kt,
// model/ClipMask.kt, model/MotionTrackModel.kt and model/ExtractAudio.kt.
// Object construction stays in the Kotlin wrappers; everything computed
// here is a plan the wrapper executes with the original's own copy() ops.
//
// Reused natives instead of re-implementations: am::descendantsOf
// (model_logic) backs GroupOps' traversal inside graphDuplicatePlan's
// caller-side originals computation in the bridge — the plans here work
// purely on the packed slice; am::algebraClipAt (clip_algebra) evaluates
// the fan-out clips; am::keyframeValueAt (frame_eval) reads the layer's
// SCALE keys; am::packColor / am::unpackColor (model_logic) convert the
// fan-out colour values.

#include "clip_graph.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "clip_algebra.h"
#include "frame_eval.h"
#include "model_logic.h"

namespace am {
namespace {

/** JVM float -> long conversion (Kotlin Float.toLong): NaN -> 0, saturates.
 *  (Same law as clip_algebra.cpp's file-local copy.) */
int64_t floatToLongSat(float f) {
    if (std::isnan(f)) return 0;
    if (f >= 9223372036854775808.0f) return INT64_MAX;   // 2^63
    if (f <= -9223372036854775808.0f) return INT64_MIN;  // -2^63
    return static_cast<int64_t>(f);  // truncates toward zero, in range
}

/** (value * factor).toLong() — float arithmetic, saturating conversion. */
int64_t mulToLong(int64_t value, float factor) {
    return floatToLongSat(static_cast<float>(value) * factor);
}

/** (value / divisor).toLong() — float arithmetic, saturating conversion. */
int64_t divToLong(int64_t value, float divisor) {
    return floatToLongSat(static_cast<float>(value) / divisor);
}

int64_t wrapAdd(int64_t a, int64_t b) {
    return static_cast<int64_t>(static_cast<uint64_t>(a) + static_cast<uint64_t>(b));
}
int64_t wrapSub(int64_t a, int64_t b) {
    return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
}

/** JVM Math.abs(Long) — Long.MIN_VALUE maps to itself. */
int64_t jvmAbs(int64_t v) {
    if (v >= 0) return v;
    return static_cast<int64_t>(0 - static_cast<uint64_t>(v));
}

int64_t clipEnd(const GraphPack& p, int32_t i) { return wrapAdd(p.startMs[i], p.durationMs[i]); }

/** GroupOps.canGroup over the slice: type only (Audio / Camera / Chain
 *  can never sit in a group) — no lock or free-length condition. */
bool canGroupClip(const GraphPack& p, int32_t i) {
    return p.type[i] != kTypeAudio && p.type[i] != 7 /* CAMERA */ && p.type[i] != kTypeChain;
}

/** GroupOps.isSelfOrAncestor over keys: true when [candidate] is the
 *  group [ofClip] itself or one of its ancestors — the walk starts at
 *  the GROUP and follows ITS parent chain looking for the candidate
 *  (linking an ancestor into its own descendant group would loop). At
 *  most 16 clips checked; dangling parents stop the walk. */
bool isSelfOrAncestor(const GraphPack& p, int32_t candidate, int32_t ofClip) {
    int32_t cur = ofClip;
    for (int step = 0; step < 16; step++) {
        if (p.idKey[cur] == p.idKey[candidate]) return true;
        int32_t pk = p.parentKey[cur];
        if (pk < 0 || pk >= p.keyCount) return false;
        int32_t next = p.keyToClip[pk];
        if (next < 0) return false;
        cur = next;
    }
    return false;
}

/** First clip (flat order) whose id key is [key] and whose type is GROUP. */
int32_t findGroupClip(const GraphPack& p, int32_t key) {
    for (int32_t i = 0; i < p.n; i++)
        if (p.idKey[i] == key && p.type[i] == kTypeGroup) return i;
    return -1;
}

}  // namespace

// ---- GroupOps ---------------------------------------------------------------

GroupPlan graphGroupPlan(const GraphPack& p, const std::vector<uint8_t>& selected) {
    GroupPlan plan;
    std::vector<int32_t> sel;
    for (int32_t i = 0; i < p.n; i++)
        if (selected[i] && canGroupClip(p, i)) sel.push_back(i);
    if (sel.empty()) return plan;
    std::vector<int32_t> groupsSel;
    for (int32_t i : sel)
        if (p.type[i] == kTypeGroup) groupsSel.push_back(i);
    if (groupsSel.size() == 1 && sel.size() == 1) return plan;  // a lone group
    if (groupsSel.size() == 1) {
        int32_t g = groupsSel[0];
        std::vector<int32_t> movers;
        for (int32_t i : sel) {
            if (i == g) continue;
            if (!isSelfOrAncestor(p, i, g)) movers.push_back(i);
        }
        if (movers.empty()) return plan;
        plan.mode = 1;
        plan.targetClip = g;
        plan.movers = movers;
        return plan;
    }
    int64_t start = INT64_MAX, end = INT64_MIN;
    for (int32_t i : sel) {
        start = std::min(start, p.startMs[i]);
        end = std::max(end, clipEnd(p, i));
    }
    // distinct group keys of the selection, encounter order; the outer
    // group counts only when the single distinct key names an existing
    // GROUP clip (the original's takeIf on byId)
    std::vector<int32_t> distinct;
    for (int32_t i : sel) {
        int32_t gk = p.groupKey[i];
        if (std::find(distinct.begin(), distinct.end(), gk) == distinct.end())
            distinct.push_back(gk);
    }
    int32_t outerKey = -1;
    if (distinct.size() == 1 && distinct[0] >= 0 && distinct[0] < p.keyCount) {
        int32_t holder = p.keyToClip[distinct[0]];
        if (holder >= 0 && p.type[holder] == kTypeGroup) outerKey = distinct[0];
    }
    int32_t groupCount = 0;
    int32_t topLane = INT32_MAX;
    for (int32_t i = 0; i < p.n; i++)
        if (p.type[i] == kTypeGroup) groupCount++;
    for (int32_t i : sel) topLane = std::min(topLane, p.laneIndex[i]);
    plan.mode = 2;
    plan.startMs = start;
    plan.durationMs = std::max(end - start, static_cast<int64_t>(100));
    plan.outerKey = outerKey;
    plan.topLaneIndex = topLane;
    plan.groupCount = groupCount;
    return plan;
}

std::vector<int32_t> graphUngroupPlan(const GraphPack& p, int32_t groupKey) {
    std::vector<int32_t> codes(p.n, 0);
    if (findGroupClip(p, groupKey) < 0) return codes;
    for (int32_t i = 0; i < p.n; i++) {
        if (p.idKey[i] == groupKey) codes[i] = 1;
        else if (p.groupKey[i] == groupKey) codes[i] = 2;
    }
    return codes;
}

std::vector<DupEntry> graphDuplicatePlan(const GraphPack& p,
                                         const std::vector<int32_t>& originalOrder,
                                         int32_t groupKey) {
    // key -> position in originals (for link resolution into minted keys)
    auto posOfKey = [&](int32_t key) -> int32_t {
        for (size_t j = 0; j < originalOrder.size(); j++)
            if (p.idKey[originalOrder[j]] == key) return static_cast<int32_t>(j);
        return -1;
    };
    auto resolve = [&](int32_t key) -> int32_t {
        if (key < 0) return -1;
        int32_t pos = posOfKey(key);
        return pos >= 0 ? p.keyCount + pos : key;
    };
    std::vector<DupEntry> out;
    for (int32_t idx : originalOrder) {
        DupEntry e;
        bool isGroup = p.idKey[idx] == groupKey;
        e.groupKeyOut = isGroup ? p.groupKey[idx] : resolve(p.groupKey[idx]);
        e.parentKeyOut = resolve(p.parentKey[idx]);
        e.controllerKeyOut = resolve(p.controllerKey[idx]);
        e.nameSuffix = isGroup ? 1 : 0;
        out.push_back(e);
    }
    return out;
}

SplitGroupPlan graphSplitGroupPlan(const GraphPack& p, int32_t groupKey, int64_t playheadMs) {
    SplitGroupPlan plan;
    int32_t g = findGroupClip(p, groupKey);
    if (g < 0) return plan;
    int64_t gStart = p.startMs[g];
    int64_t gEnd = clipEnd(p, g);
    if (!(playheadMs > wrapAdd(gStart, 500) && playheadMs < wrapSub(gEnd, 500))) return plan;
    std::vector<int32_t> kids;
    for (int32_t i = 0; i < p.n; i++)
        if (p.groupKey[i] == groupKey) kids.push_back(i);
    if (kids.empty()) return plan;
    for (int32_t k : kids)
        if (p.type[k] == kTypeGroup) return plan;
    // extended keys for the split kids' second halves, in kids order
    std::vector<int32_t> halfKeyOf(p.n, -1);
    int32_t splitCount = 0;
    for (int32_t k : kids) {
        if (p.startMs[k] < playheadMs && clipEnd(p, k) > playheadMs) {
            halfKeyOf[k] = p.keyCount + 1 + splitCount;
            splitCount++;
        }
    }
    auto resolveParent = [&](int32_t parentKey) -> int32_t {
        if (parentKey < 0) return -1;
        if (parentKey == groupKey) return p.keyCount;  // the new (second) group
        int32_t holder = (parentKey < p.keyCount) ? p.keyToClip[parentKey] : -1;
        if (holder >= 0 && halfKeyOf[holder] >= 0) return halfKeyOf[holder];
        return parentKey;
    };
    plan.ok = 1;
    plan.groupStartMs = gStart;
    plan.groupFirstDurMs = playheadMs - gStart;
    for (int32_t k : kids) {
        SplitKid sk;
        sk.clip = k;
        sk.halfKey = halfKeyOf[k];
        if (clipEnd(p, k) <= playheadMs) {
            sk.cls = 0;
            sk.parentOut = p.parentKey[k];
        } else if (p.startMs[k] >= playheadMs) {
            sk.cls = 1;
            sk.parentOut = resolveParent(p.parentKey[k]);
        } else {
            sk.cls = 2;
            sk.d1 = playheadMs - p.startMs[k];
            sk.parentOut = resolveParent(p.parentKey[k]);
        }
        plan.kids.push_back(sk);
    }
    return plan;
}

FitGroupsPass graphFitGroupsPass(const GraphPack& p) {
    FitGroupsPass pass;
    pass.clearGroup.assign(p.n, 0);
    pass.clearParent.assign(p.n, 0);
    std::vector<uint8_t> isGroupKey(p.keyCount, 0);
    for (int32_t i = 0; i < p.n; i++)
        if (p.type[i] == kTypeGroup && p.idKey[i] >= 0 && p.idKey[i] < p.keyCount)
            isGroupKey[p.idKey[i]] = 1;
    bool changed = false;
    for (int32_t i = 0; i < p.n; i++) {
        int32_t gk = p.groupKey[i];
        if (gk >= 0 && (gk >= p.keyCount || !isGroupKey[gk])) {
            pass.clearGroup[i] = 1;
            if (p.parentKey[i] == gk) pass.clearParent[i] = 1;
            changed = true;
        }
    }
    for (int32_t g = 0; g < p.n; g++) {
        if (p.type[g] != kTypeGroup || p.groupFreeLength[g]) continue;
        int64_t start = INT64_MAX, end = INT64_MIN;
        bool any = false;
        for (int32_t i = 0; i < p.n; i++) {
            if (p.groupKey[i] != p.idKey[g]) continue;
            any = true;
            start = std::min(start, p.startMs[i]);
            end = std::max(end, clipEnd(p, i));
        }
        if (!any) continue;
        int64_t oldEnd = clipEnd(p, g);
        if (start != p.startMs[g] || end != oldEnd) {
            FitGroupsPass::Refit r;
            r.clip = g;
            r.startMs = start;
            r.durationMs = std::max(end - start, static_cast<int64_t>(100));
            r.trimDelta = start - p.startMs[g];
            r.endDelta = end - oldEnd;
            pass.refits.push_back(r);
            changed = true;
        }
    }
    pass.changed = changed ? 1 : 0;
    return pass;
}

// ---- LaneLayout ---------------------------------------------------------------

NormalizePlan graphNormalizePlan(const GraphPack& p) {
    NormalizePlan plan;
    plan.clearChain.assign(p.n, 0);
    int32_t laneCount = static_cast<int32_t>(p.laneOffsets.size()) - 1;
    if (laneCount < 0) laneCount = 0;
    plan.keptPerLane.assign(laneCount, {});
    for (int32_t l = 0; l < laneCount; l++) {
        std::vector<int32_t> sorted;
        for (int32_t i = p.laneOffsets[l]; i < p.laneOffsets[l + 1]; i++) sorted.push_back(i);
        std::stable_sort(sorted.begin(), sorted.end(),
                         [&](int32_t a, int32_t b) { return p.startMs[a] < p.startMs[b]; });
        int32_t chainIdx = -1;
        for (int32_t i : sorted)
            if (p.type[i] == kTypeChain) { chainIdx = i; break; }
        if (chainIdx >= 0) {
            // Kept: the chain clip FIRST, then its members in start order
            // (the original builds listOf(chain) + members). Membership is
            // by ID: a clip whose id equals the chain's id is a "member"
            // for the rest-split but is filtered out of the kept tail —
            // with duplicate ids it drops out entirely, as in the original.
            int32_t chainKey = p.idKey[chainIdx];
            plan.keptPerLane[l].push_back(chainIdx);
            for (int32_t i : sorted) {
                bool member = p.idKey[i] == chainKey || p.chainKey[i] == chainKey;
                if (i != chainIdx && p.chainKey[i] == chainKey && p.idKey[i] != chainKey)
                    plan.keptPerLane[l].push_back(i);
                if (!member) {
                    if (p.chainKey[i] != -1) plan.clearChain[i] = 1;
                    plan.overflow.push_back(i);
                }
            }
        } else if (!sorted.empty()) {
            int32_t first = sorted[0];
            if (p.chainKey[first] != -1) plan.clearChain[first] = 1;
            plan.keptPerLane[l].push_back(first);
            for (size_t k = 1; k < sorted.size(); k++) {
                int32_t i = sorted[k];
                if (p.chainKey[i] != -1) plan.clearChain[i] = 1;
                plan.overflow.push_back(i);
            }
        }
    }
    return plan;
}

FitChainsPlan graphFitChains(const GraphPack& p) {
    FitChainsPlan plan;
    int32_t laneCount = static_cast<int32_t>(p.laneOffsets.size()) - 1;
    for (int32_t l = 0; l < laneCount; l++) {
        int32_t chainIdx = -1;
        for (int32_t i = p.laneOffsets[l]; i < p.laneOffsets[l + 1]; i++)
            if (p.type[i] == kTypeChain) { chainIdx = i; break; }
        if (chainIdx < 0) continue;
        int32_t chainKey = p.idKey[chainIdx];
        // The window comes from the MEMBERS alone — a chain wider than its
        // layers shrinks to them (the original never includes the chain's
        // own window in the min/max).
        int64_t start = INT64_MAX, end = INT64_MIN;
        bool any = false;
        for (int32_t i = p.laneOffsets[l]; i < p.laneOffsets[l + 1]; i++) {
            if (p.chainKey[i] != chainKey) continue;
            any = true;
            start = std::min(start, p.startMs[i]);
            end = std::max(end, clipEnd(p, i));
        }
        if (!any) continue;
        if (start != p.startMs[chainIdx] || end != clipEnd(p, chainIdx)) {
            FitChainsPlan::Entry e;
            e.clip = chainIdx;
            e.startMs = start;
            e.durationMs = end - start;
            plan.entries.push_back(e);
        }
    }
    plan.changed = plan.entries.empty() ? 0 : 1;
    return plan;
}

ReorderPlan graphReorder(int32_t laneCount, int32_t from, int32_t to) {
    ReorderPlan plan;
    if (laneCount == 0) {
        plan.identity = 1;
        plan.from = from;
        plan.to = to;
        return plan;
    }
    int32_t f = std::max(0, std::min(from, laneCount - 1));
    int32_t t = std::max(0, std::min(to, laneCount - 1));
    plan.identity = f == t ? 1 : 0;
    plan.from = f;
    plan.to = t;
    return plan;
}

int32_t graphPlaceInChainLane(const GraphPack& p, int32_t chainKey) {
    for (int32_t i = 0; i < p.n; i++)
        if (p.idKey[i] == chainKey && p.type[i] == kTypeChain) return p.laneIndex[i];
    return -1;
}

// ---- MultiSelectOps -----------------------------------------------------------

ChainSelectedPlan graphChainSelectedPlan(const GraphPack& p,
                                         const std::vector<uint8_t>& selected) {
    ChainSelectedPlan plan;
    std::vector<int32_t> sel;
    for (int32_t i = 0; i < p.n; i++)
        if (selected[i] && p.type[i] != kTypeChain) sel.push_back(i);
    if (sel.empty()) return plan;
    std::vector<int32_t> order = sel;
    std::stable_sort(order.begin(), order.end(),
                     [&](int32_t a, int32_t b) { return p.startMs[a] < p.startMs[b]; });
    int32_t topLane = INT32_MAX;
    int64_t end = INT64_MIN;
    for (int32_t i : sel) {
        topLane = std::min(topLane, p.laneIndex[i]);
        end = std::max(end, clipEnd(p, i));
    }
    // insertAt counts the lanes BEFORE the topmost selected lane that
    // still hold a non-selected clip (the original counts over the
    // pre-removal lane list; the wrapper inserts into the filtered list).
    int32_t insertAt = 0;
    int32_t laneCount = static_cast<int32_t>(p.laneOffsets.size()) - 1;
    for (int32_t l = 0; l < topLane && l < laneCount; l++) {
        for (int32_t i = p.laneOffsets[l]; i < p.laneOffsets[l + 1]; i++) {
            if (!(selected[i] && p.type[i] != kTypeChain)) { insertAt++; break; }
        }
    }
    plan.selOrder = order;
    plan.insertAt = insertAt;
    plan.chainStart = p.startMs[order[0]];
    plan.chainDur = std::max(end - plan.chainStart, static_cast<int64_t>(100));
    return plan;
}

std::vector<MultiEdit> graphMultiApplyPlan(const GraphPack& p,
                                           const std::vector<uint8_t>& selected,
                                           int32_t action, int64_t playheadMs) {
    std::vector<int32_t> sel;
    for (int32_t i = 0; i < p.n; i++)
        if (selected[i]) sel.push_back(i);
    std::vector<MultiEdit> edits;
    auto base = [&](int32_t i) {
        MultiEdit e;
        e.clip = i;
        e.newStart = p.startMs[i];
        e.newDur = p.durationMs[i];
        e.newTrim = p.trimStartMs[i];
        return e;
    };
    constexpr int32_t kSplit = 0, kDeleteLeft = 1, kDeleteRight = 2, kExtendStart = 3,
                      kMoveStart = 4, kExtendEnd = 5, kMoveEnd = 6, kAlignStart = 7,
                      kAlignEnd = 8, kSequence = 9;
    if (action == kSequence) {
        std::vector<int32_t> order = sel;
        std::stable_sort(order.begin(), order.end(), [&](int32_t a, int32_t b) {
            if (p.startMs[a] != p.startMs[b]) return p.startMs[a] < p.startMs[b];
            return p.laneIndex[a] < p.laneIndex[b];
        });
        int64_t cursor = INT64_MAX;
        for (int32_t i : sel) cursor = std::min(cursor, p.startMs[i]);
        std::vector<MultiEdit> byClip(p.n);
        std::vector<uint8_t> has(p.n, 0);
        for (int32_t i : order) {
            MultiEdit e = base(i);
            e.code = 3;
            e.newStart = cursor;
            cursor = wrapAdd(cursor, p.durationMs[i]);
            byClip[i] = e;
            has[i] = 1;
        }
        for (int32_t i : sel) edits.push_back(byClip[i]);
        (void)has;
        return edits;
    }
    if (action == kAlignStart || action == kAlignEnd) {
        int64_t target;
        if (action == kAlignStart) {
            target = INT64_MAX;
            for (int32_t i : sel) target = std::min(target, p.startMs[i]);
        } else {
            target = INT64_MIN;
            for (int32_t i : sel) target = std::max(target, clipEnd(p, i));
        }
        for (int32_t i : sel) {
            MultiEdit e = base(i);
            e.code = 3;
            e.newStart = action == kAlignStart
                ? target
                : std::max(target - p.durationMs[i], static_cast<int64_t>(0));
            edits.push_back(e);
        }
        return edits;
    }
    for (int32_t i : sel) {
        MultiEdit e = base(i);
        int64_t start = p.startMs[i], dur = p.durationMs[i], end = clipEnd(p, i);
        switch (action) {
            case kSplit:
                if (playheadMs - start >= 100 && end - playheadMs >= 100) {
                    int64_t d1 = playheadMs - start;
                    e.code = 2;
                    e.newDur = d1;
                    e.half2Trim = wrapAdd(p.trimStartMs[i], mulToLong(d1, p.speed[i]));
                }
                break;
            case kDeleteLeft:
                if (playheadMs > start && end - playheadMs >= 100) {
                    int64_t d = playheadMs - start;
                    e.code = 1;
                    e.newStart = playheadMs;
                    e.newDur = dur - d;
                    // the trim only follows when the clip has source media
                    e.newTrim = p.hasSource[i]
                        ? wrapAdd(p.trimStartMs[i], mulToLong(d, p.speed[i]))
                        : p.trimStartMs[i];
                    e.fadeMode = 1;
                    e.kfOp = 1;
                    e.kfDelta = d;
                }
                break;
            case kDeleteRight:
                if (playheadMs - start >= 100 && playheadMs < end) {
                    e.code = 1;
                    e.newDur = playheadMs - start;
                    e.fadeMode = 2;
                    e.kfOp = 2;
                    e.kfDelta = playheadMs - end;
                }
                break;
            case kExtendStart: {
                int64_t d0 = start - playheadMs;
                if (d0 > 0) {
                    int64_t d = d0;
                    if (p.hasSource[i]) {
                        // backwards growth is capped by the in-point; the
                        // cap divides by the speed floored at 0.01, while
                        // the trim delta below uses the RAW speed
                        float spd = p.speed[i] < 0.01f ? 0.01f : p.speed[i];
                        d = std::min(d, divToLong(p.trimStartMs[i], spd));
                    }
                    if (d > 0) {
                        e.code = 1;
                        e.newStart = start - d;
                        e.newDur = dur + d;
                        e.newTrim = p.hasSource[i]
                            ? std::max(p.trimStartMs[i] - mulToLong(d, p.speed[i]),
                                       static_cast<int64_t>(0))
                            : p.trimStartMs[i];
                        e.kfOp = 1;
                        e.kfDelta = -d;
                    }
                }
                break;
            }
            case kMoveStart:
                if (playheadMs < start) {
                    e.code = 3;
                    e.newStart = playheadMs;
                }
                break;
            case kExtendEnd: {
                int64_t d0 = playheadMs - end;
                if (d0 > 0) {
                    int64_t d = d0;
                    if (p.hasSource[i] && p.sourceDurationMs[i] > 0) {
                        float spd = p.speed[i] < 0.01f ? 0.01f : p.speed[i];
                        int64_t maxLen = divToLong(p.sourceDurationMs[i] - p.trimStartMs[i], spd);
                        int64_t room = maxLen - dur;
                        if (room < 0) room = 0;
                        d = std::min(d, room);
                    }
                    if (d > 0) {
                        e.code = 1;
                        e.newDur = dur + d;
                        e.kfOp = 2;
                        e.kfDelta = d;
                    }
                }
                break;
            }
            case kMoveEnd:
                if (playheadMs > end) {
                    e.code = 3;
                    e.newStart = std::max(playheadMs - dur, static_cast<int64_t>(0));
                }
                break;
            default:
                break;
        }
        edits.push_back(e);
    }
    return edits;
}

// ---- ControllerFanOut -----------------------------------------------------------

namespace {

/** Offset (in keys) of prop's segment inside the concatenated key arrays. */
int32_t segmentOffset(const int32_t counts[16], int32_t prop) {
    int32_t off = 0;
    for (int32_t i = 0; i < prop; i++)
        if (counts[i] > 0) off += counts[i];
    return off;
}

/** Evaluates one clip slice at timeMs (algebraClipAt): the evaluated
 *  scalar block AND the evaluated colour longs (the fan-out's colour
 *  comparisons read the evaluated colours, as the Kotlin original does). */
void evalSlice(const float scalars[24], const int64_t colors[3], int32_t gradType,
               const int32_t counts[16], const int64_t* times, const float* vals,
               int64_t startMs, int64_t timeMs, float outScalars[24],
               int64_t outColors[3]) {
    float s[24];
    std::memcpy(s, scalars, sizeof(s));
    int64_t c[3] = {colors[0], colors[1], colors[2]};
    std::vector<float> segs;
    bool hasSegs = false, changed = false;
    algebraClipAt(startMs, s, c, gradType, counts, times, vals,
                  nullptr, nullptr, 0, nullptr, nullptr, 0,
                  nullptr, nullptr, 0, nullptr, nullptr, 0,
                  0.0f, 0.0f, false, timeMs, segs, hasSegs, changed);
    std::memcpy(outScalars, s, sizeof(s));
    outColors[0] = c[0];
    outColors[1] = c[1];
    outColors[2] = c[2];
}

}  // namespace

FanPlan graphFanPlan(
    const float beforeScalars[24], const int64_t beforeColors[3], int32_t beforeGradType,
    const int32_t beforeCounts[16], const int64_t* beforeTimes, const float* beforeVals,
    int64_t beforeStart,
    const float afterScalars[24], const int64_t afterColors[3], int32_t afterGradType,
    const int32_t afterCounts[16], const int64_t* afterTimes, const float* afterVals,
    int64_t afterStart,
    const float layerScalars[24], const int64_t layerColors[3], int32_t layerGradType,
    const int32_t layerCounts[16], const int64_t* layerTimes, const float* layerVals,
    int64_t layerStart, int64_t layerDur, int32_t layerType, int64_t timeMs) {
    FanPlan plan;
    float b[24], a[24], l[24];
    int64_t bColors[3], aColors[3], lColors[3];
    evalSlice(beforeScalars, beforeColors, beforeGradType, beforeCounts, beforeTimes,
              beforeVals, beforeStart, timeMs, b, bColors);
    evalSlice(afterScalars, afterColors, afterGradType, afterCounts, afterTimes,
              afterVals, afterStart, timeMs, a, aColors);
    evalSlice(layerScalars, layerColors, layerGradType, layerCounts, layerTimes,
              layerVals, layerStart, timeMs, l, lColors);
    int64_t rel = timeMs - layerStart;
    if (rel < 0) rel = 0;
    if (rel > layerDur) rel = layerDur;
    plan.rel = rel;
    constexpr float kEps = 0.0005f;
    auto differs = [&](float x, float y) { return std::fabs(x - y) > kEps; };
    // The editor's keyframe rule (atPlayhead): a prop whose key list is
    // null OR EMPTY takes the field write; a non-empty list takes a
    // keyframe at rel. In slice terms: count > 0 -> keyframe.
    auto modeOf = [&](int32_t prop) { return layerCounts[prop] > 0 ? 1 : 0; };
    // POSITION (scalars 0,1) — delta from the layer's own evaluated place.
    if (differs(a[0], b[0]) || differs(a[1], b[1])) {
        FanEntry e;
        e.prop = kPropPosition;
        e.mode = modeOf(kPropPosition);
        e.a = l[0] + (a[0] - b[0]);
        e.b = l[1] + (a[1] - b[1]);
        plan.entries.push_back(e);
    }
    // ROTATION (scalar 7) — delta about the layer's own pivot.
    if (differs(a[7], b[7])) {
        FanEntry e;
        e.prop = kPropRotation;
        e.mode = modeOf(kPropRotation);
        e.a = l[7] + (a[7] - b[7]);
        e.b = 0.0f;
        plan.entries.push_back(e);
    }
    // SCALE (scalar 4) — relative: fires only when the BEFORE scale is
    // itself beyond EPS and the ratio moves beyond EPS of 1.
    if (std::fabs(b[4]) > kEps) {
        float ratio = a[4] / b[4];
        if (std::fabs(ratio - 1.0f) > kEps) {
            FanEntry e;
            e.prop = kPropScale;
            e.mode = modeOf(kPropScale);
            float lb = 0.0f;
            if (layerCounts[kPropScale] > 0) {
                int32_t off = segmentOffset(layerCounts, kPropScale);
                float va = 0.0f;
                keyframeValueAt(layerTimes + off, layerVals + off * 6,
                                layerCounts[kPropScale], rel, false, va, lb);
            }
            e.a = l[4] * ratio;
            e.b = lb;
            plan.entries.push_back(e);
        }
    }
    // OPACITY (scalar 15) — the AFTER value outright, not a delta.
    if (differs(a[15], b[15])) {
        FanEntry e;
        e.prop = kPropOpacity;
        e.mode = modeOf(kPropOpacity);
        e.a = a[15];
        e.b = 0.0f;
        plan.entries.push_back(e);
    }
    // Colour: the geometry comparisons read the STORED before/after
    // scalars; the colour comparisons read the EVALUATED colours
    // (a.textColor vs b.textColor in the original, where a/b are the
    // at() results). The wrapper adds the stored fillMode / fill-URI
    // comparisons, which never cross.
    bool scalarsChanged = beforeGradType != afterGradType;
    for (int i = 20; i < 24; i++)
        if (beforeScalars[i] != afterScalars[i]) scalarsChanged = true;
    for (int i = 0; i < 3; i++)
        if (aColors[i] != bColors[i]) scalarsChanged = true;
    plan.colourScalarsChanged = scalarsChanged ? 1 : 0;
    if (layerType == kTypeShape || layerType == 5 /* TEXT */) {
        const int32_t props[3] = {kPropFillColor, kPropGradColor1, kPropGradColor2};
        for (int i = 0; i < 3; i++) {
            if (aColors[i] == bColors[i]) continue;
            FanEntry e;
            e.prop = props[i];
            e.mode = modeOf(props[i]);
            float pk[2];
            packColor(aColors[i], pk);
            e.a = pk[0];
            e.b = pk[1];
            plan.colourEntries.push_back(e);
        }
    }
    return plan;
}

std::vector<int32_t> graphConnected(const GraphPack& p, int32_t controllerKey) {
    std::vector<int32_t> out;
    for (int32_t i = 0; i < p.n; i++) {
        if (p.idKey[i] == controllerKey) continue;
        if (p.controllerKey[i] == controllerKey || p.parentKey[i] == controllerKey)
            out.push_back(i);
    }
    return out;
}

// ---- SoloLaw --------------------------------------------------------------------

SoloPlan graphSoloPlan(const GraphPack& p, int32_t soloKey,
                       const std::vector<uint8_t>& stateHas,
                       const std::vector<uint8_t>& stateVisible,
                       const std::vector<uint8_t>& stateMuted) {
    SoloPlan plan;
    plan.soloVisible.assign(p.n, 0);
    plan.soloMuted.assign(p.n, 0);
    plan.soloCopy.assign(p.n, 0);
    plan.restVisible.assign(p.n, 0);
    plan.restMuted.assign(p.n, 0);
    plan.restCopy.assign(p.n, 0);
    for (int32_t i = 0; i < p.n; i++) {
        bool isSolo = soloKey >= 0 && p.idKey[i] == soloKey;
        plan.soloVisible[i] = isSolo ? 1 : 0;
        plan.soloMuted[i] = isSolo ? p.muted[i] : 1;
        plan.soloCopy[i] = isSolo ? (p.visible[i] ? 0 : 1) : 1;
        if (stateHas[i]) {
            plan.restVisible[i] = stateVisible[i];
            plan.restMuted[i] = stateMuted[i];
            plan.restCopy[i] = 1;
        } else {
            plan.restVisible[i] = p.visible[i];
            plan.restMuted[i] = p.muted[i];
            plan.restCopy[i] = 0;
        }
    }
    return plan;
}

// ---- ClipMask key algebra ---------------------------------------------------------

namespace {

float lerpF(float a, float b, float t) { return a + (b - a) * t; }

}  // namespace

MaskStateF graphMaskLerp(const MaskStateF& a, const MaskStateF& b, float t) {
    MaskStateF out;
    for (int i = 0; i < 8; i++) out.v[i] = lerpF(a.v[i], b.v[i], t);
    return out;
}

MaskStateF graphMaskStateAt(const MaskStateF& base, int32_t n, const int64_t* times,
                            const float* states, const float* easings, int64_t t) {
    if (n == 0) return base;
    if (t <= times[0]) {
        MaskStateF out;
        std::memcpy(out.v, states, sizeof(out.v));
        return out;
    }
    if (t >= times[n - 1]) {
        MaskStateF out;
        std::memcpy(out.v, states + (n - 1) * 8, sizeof(out.v));
        return out;
    }
    // The original's segment walk: advance while the NEXT key starts at
    // or before t (keys are stored sorted; the walk is replicated as-is).
    int32_t i = 0;
    while (i < n - 1 && times[i + 1] <= t) i++;
    int64_t span = times[i + 1] - times[i];
    if (span < 1) span = 1;
    float tt = static_cast<float>(t - times[i]) / static_cast<float>(span);
    const float* e = easings + i * 4;
    // k0.easing.isLinear (am::easingIsLinear, the stage-3 native) takes
    // the raw fraction; otherwise Easing.apply, whose 0/1 guards run
    // before the bezier solve — both replicated exactly.
    float pr;
    if (easingIsLinear(e[0], e[1], e[2], e[3])) pr = tt;
    else if (tt <= 0.0f) pr = 0.0f;
    else if (tt >= 1.0f) pr = 1.0f;
    else pr = easingApply(e[0], e[1], e[2], e[3], tt);
    MaskStateF a, b;
    std::memcpy(a.v, states + i * 8, sizeof(a.v));
    std::memcpy(b.v, states + (i + 1) * 8, sizeof(b.v));
    return graphMaskLerp(a, b, pr);
}

int32_t graphMaskKeyIndexAt(int32_t n, const int64_t* times, int64_t t) {
    int32_t best = -1;
    int64_t bestDist = INT64_MAX;
    for (int32_t i = 0; i < n; i++) {
        int64_t d = jvmAbs(wrapSub(t, times[i]));
        if (d < bestDist) {
            bestDist = d;
            best = i;
        }
    }
    return bestDist <= 40 ? best : -1;
}

// ---- MotionTrackModel ---------------------------------------------------------------

int32_t graphTrackIndexAt(int32_t n, const int64_t* timesMs, int64_t t) {
    if (n == 0) return -1;
    int64_t key = wrapAdd(t, 2);
    if (key < timesMs[0]) return 0;  // before the first frame: the first entry
    int32_t lo = 0, hi = n - 1;
    while (lo < hi) {
        int32_t mid = static_cast<int32_t>(
            (static_cast<uint32_t>(lo) + static_cast<uint32_t>(hi) + 1) >> 1);
        if (timesMs[mid] <= key) lo = mid;
        else hi = mid - 1;
    }
    return lo;
}

TrackRenderPlan graphTrackRender(int32_t n, const int64_t* times, const float* dx,
                                 const float* dy, const float* rot, const float* scale,
                                 float tx, float ty, float rotDeg, float scalePercent,
                                 float ssx, float ssy, int64_t t) {
    TrackRenderPlan plan;
    plan.x = tx; plan.y = ty; plan.rot = rotDeg;
    plan.scalePercent = scalePercent; plan.shapeScaleX = ssx; plan.shapeScaleY = ssy;
    int32_t i = graphTrackIndexAt(n, times, t);
    if (i < 0) return plan;
    plan.applied = 1;
    plan.x = tx + dx[i];
    plan.y = ty + dy[i];
    if (rot != nullptr) plan.rot = rotDeg + rot[i];
    if (scale != nullptr) {
        float s = scale[i];
        plan.scalePercent = scalePercent * s;
        plan.shapeScaleX = ssx * s;
        plan.shapeScaleY = ssy * s;
    }
    return plan;
}

void graphTrackOffset(int32_t n, const int64_t* times, const float* dx, const float* dy,
                      int64_t t, float out[2]) {
    int32_t i = graphTrackIndexAt(n, times, t);
    if (i < 0) {
        out[0] = 0.0f;
        out[1] = 0.0f;
        return;
    }
    out[0] = dx[i];
    out[1] = dy[i];
}

// ---- ExtractAudio ---------------------------------------------------------------------

uint8_t graphDetachedKeepSpeed(const int32_t propCounts[16]) {
    return propCounts[kPropSpeed] >= 0 ? 1 : 0;
}

int64_t graphImportedStart(int64_t startMs) { return startMs < 0 ? 0 : startMs; }

}  // namespace am
