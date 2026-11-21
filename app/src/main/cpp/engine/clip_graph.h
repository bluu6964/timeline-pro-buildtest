// clip_graph: the clip-GRAPH algebra, migrated from Kotlin (migration
// stage 2, build 245 — GroupOps' group / ungroup / duplicate / splitGroup /
// fitGroups decisions, LaneLayout's normalize / fitChains / reorder /
// lane-search decisions, MultiSelectOps.apply's per-action transforms and
// its SEQUENCE / ALIGN / split plans, ControllerFanOut's fan-out plan,
// SoloLaw's soloed / restored flag plans, the ClipMask key algebra,
// MotionTrackModel's TrackFollow index/render math, and the ExtractAudio
// constructors' two decisions).
//
// The graph never crosses JNI whole either. The Kotlin bridge
// (engine/ClipGraphNative.kt) packs a LIST-of-clips slice — the stage-1
// algebra slice extended to a whole project (see the stage-2 addendum in
// CPP-MIGRATION-REMAINING-MAP.md): per clip, identity KEYS (small ints the
// bridge assigns to id strings in encounter order; -1 = null, a dangling
// link id still gets a key), the lane layout, and exactly the fields these
// ops read. Strings, effects, masks' states and keyframe VALUES never
// cross (fan-out reuses the stage-1 slice per clip). The native side
// computes PLANS — which clips move where, with what numbers — and the
// Kotlin wrappers rebuild the objects with the original code's own copy()
// / list ops, minting UUIDs Kotlin-side in the plan's order. No Kotlin
// fallback copies of the moved logic remain.
//
// JVM number semantics are replicated exactly (the model_logic /
// clip_algebra precedent): Long overflow wraps (uint64 arithmetic),
// (long * float).toLong() and (long / float).toLong() are float arithmetic
// with a saturating float->long conversion (NaN -> 0). This TU is compiled
// with -ffp-contract=off (see CMakeLists).
//
// Verified by golden serialized-state diffs against the Kotlin originals
// (~/workspace/after-motion-dev/stage2-migration-golden/).
#pragma once

#include <cstdint>
#include <vector>

namespace am {

// LayerType ordinals (Kotlin enum order).
constexpr int kTypeVideo = 0;
constexpr int kTypeAudio = 1;
constexpr int kTypeShape = 2;
constexpr int kTypeChain = 8;
constexpr int kTypeGroup = 9;

// AnimProp ordinals used here (full table in clip_algebra.h).
constexpr int kPropPosition = 0;
constexpr int kPropScale = 1;
constexpr int kPropRotation = 2;
constexpr int kPropFillColor = 6;
constexpr int kPropGradColor1 = 7;
constexpr int kPropGradColor2 = 8;
constexpr int kPropOpacity = 11;

// ---- The graph slice (mirror of ClipGraphNative.packGraph) -----------------
struct GraphPack {
    int32_t n = 0;        // clip count
    int32_t keyCount = 0; // distinct identity keys (minted keys start here)
    std::vector<int32_t> laneOffsets;  // laneCount+1 entries
    std::vector<int32_t> laneIndex;    // per clip
    std::vector<int32_t> idKey, groupKey, chainKey, parentKey, controllerKey;
    std::vector<int32_t> keyToClip;    // per key: clip index or -1 (dangling)
    std::vector<int32_t> type;         // LayerType ordinal per clip
    std::vector<int64_t> startMs, durationMs;
    std::vector<uint8_t> locked, visible, muted, groupFreeLength, hasSource;
    std::vector<int64_t> fadeInMs, fadeOutMs, trimStartMs, sourceDurationMs;
    std::vector<float> speed;
};

// ---- GroupOps ---------------------------------------------------------------

/** GroupOps.group's decision: mode 0 = null result; 1 = into the existing
 *  group (target clip index + mover clip indices, ancestor-filtered);
 *  2 = build a new group (start, duration already floored at 100 ms,
 *  outer key — only when it names an existing GROUP clip, top lane =
 *  first lane holding a selected clip, group count for the name). */
struct GroupPlan {
    int32_t mode = 0;
    int32_t targetClip = -1;
    std::vector<int32_t> movers;
    int64_t startMs = 0, durationMs = 0;
    int32_t outerKey = -1;
    int32_t topLaneIndex = -1;
    int32_t groupCount = 0;
};
GroupPlan graphGroupPlan(const GraphPack& p, const std::vector<uint8_t>& selected);

/** GroupOps.ungroup's per-clip codes: 0 untouched, 1 drop (any clip whose
 *  id is the group's id — the original drops by id alone), 2 relink to the
 *  group's parent group. All zero when the group does not exist. */
std::vector<int32_t> graphUngroupPlan(const GraphPack& p, int32_t groupKey);

/** GroupOps.duplicate's per-original outputs, in originals order (flat
 *  order — the original mints ids in that order). The *Out keys are
 *  EXTENDED keys: < keyCount = an existing id, >= keyCount = keyCount + j
 *  (the j-th minted id), -1 = null. chainId is always nulled (not
 *  returned). nameSuffix marks the group clip itself (" (Copy)"). The
 *  group clip's own groupKeyOut is its stored group key, unresolved —
 *  the original keeps the copied group's outer group as-is. */
struct DupEntry {
    int32_t groupKeyOut, parentKeyOut, controllerKeyOut;
    uint8_t nameSuffix;
};
std::vector<DupEntry> graphDuplicatePlan(const GraphPack& p,
                                         const std::vector<int32_t>& originalOrder,
                                         int32_t groupKey);

/** GroupOps.splitGroup's plan. Mint order: the new group first (extended
 *  key keyCount), then the split kids' second halves in kids order
 *  (keyCount + 1 + j). kidParentOut is the resolved EXTENDED parent key
 *  for moved clips and second halves (-1 = no parent). The second half's
 *  trim delta is NOT here: the wrapper computes it with the stage-1
 *  sourceOffsetMs (which integrates SPEED keyframes), exactly as the
 *  original does. */
struct SplitKid {
    int32_t clip;
    int32_t cls;  // 0 stays, 1 moves whole, 2 splits at the playhead
    int64_t d1;          // cls 2: first-half duration
    int32_t parentOut;   // cls 1/2: resolved extended parent key
    int32_t halfKey;     // cls 2: extended key of the second half
};
struct SplitGroupPlan {
    uint8_t ok = 0;
    int64_t groupStartMs = 0, groupFirstDurMs = 0;
    std::vector<SplitKid> kids;
};
SplitGroupPlan graphSplitGroupPlan(const GraphPack& p, int32_t groupKey, int64_t playheadMs);

/** ONE pass of GroupOps.fitGroups over the pack snapshot. The wrapper
 *  re-packs and re-calls (at most 4 passes, the original's bound). */
struct FitGroupsPass {
    uint8_t changed = 0;
    std::vector<uint8_t> clearGroup;   // per clip: groupId is stale -> clear
    std::vector<uint8_t> clearParent;  // per clip: parent pointed at the stale group -> clear
    struct Refit { int32_t clip; int64_t startMs, durationMs, trimDelta, endDelta; };
    std::vector<Refit> refits;
};
FitGroupsPass graphFitGroupsPass(const GraphPack& p);

// ---- LaneLayout ---------------------------------------------------------------

/** LaneLayout.normalize's plan: the kept clips per input lane, the overflow
 *  clips in processing order (the wrapper prepends them reversed, one lane
 *  each), and the per-clip chainId-clear flags. */
struct NormalizePlan {
    std::vector<std::vector<int32_t>> keptPerLane;
    std::vector<int32_t> overflow;
    std::vector<uint8_t> clearChain;
};
NormalizePlan graphNormalizePlan(const GraphPack& p);

/** LaneLayout.fitChains' plan: per chain clip needing a new window. */
struct FitChainsPlan {
    uint8_t changed = 0;
    struct Entry { int32_t clip; int64_t startMs, durationMs; };
    std::vector<Entry> entries;
};
FitChainsPlan graphFitChains(const GraphPack& p);

/** LaneLayout.reorder's decision: both indices are clamped into range
 *  (the original coerces, it does not reject out-of-range input);
 *  identity when the clamped indices coincide or there are no lanes. */
struct ReorderPlan { uint8_t identity; int32_t from, to; };
ReorderPlan graphReorder(int32_t laneCount, int32_t from, int32_t to);

/** LaneLayout.placeInChain's lane search: the lane position of the first
 *  lane holding the CHAIN clip with [chainKey], or -1. */
int32_t graphPlaceInChainLane(const GraphPack& p, int32_t chainKey);

// ---- MultiSelectOps -----------------------------------------------------------

/** MultiSelectOps.chainSelected's plan: the selected non-CHAIN clips in
 *  stable start order, the insertion lane position, and the chain window. */
struct ChainSelectedPlan {
    std::vector<int32_t> selOrder;
    int32_t insertAt = 0;
    int64_t chainStart = 0, chainDur = 0;
};
ChainSelectedPlan graphChainSelectedPlan(const GraphPack& p,
                                         const std::vector<uint8_t>& selected);

/** One per-clip edit from MultiSelectOps.apply. code 0 = no edit;
 *  1 = copy with the given fields (+ optional keyframe shift op);
 *  2 = split at the playhead (newDur holds d1, half2Trim the second
 *  half's trimStartMs); 3 = copy with only startMs = newStart. */
struct MultiEdit {
    int32_t clip;
    int32_t code = 0;
    int64_t newStart = 0, newDur = 0, newTrim = 0;
    int32_t fadeMode = 0;  // 0 keep, 1 fadeInMs := 0, 2 fadeOutMs := 0
    int32_t kfOp = 0;      // 0 none, 1 withTrimmedStartKeyframes(kfDelta), 2 withEndMoved(kfDelta)
    int64_t kfDelta = 0;
    int64_t half2Trim = 0;
};
/** MultiAction ordinals 0..9 (SPLIT..SEQUENCE); CHAIN/GROUP never reach
 *  here (the wrapper routes them to chainSelected / GroupOps.group). */
std::vector<MultiEdit> graphMultiApplyPlan(const GraphPack& p,
                                           const std::vector<uint8_t>& selected,
                                           int32_t action, int64_t playheadMs);

// ---- ControllerFanOut -----------------------------------------------------------

/** One fan-out write, in application order: mode 0 = withProp(prop, a, b)
 *  (a field write), mode 1 = withKeyframe(prop, rel, a, b). */
struct FanEntry { int32_t prop, mode; float a, b; };
struct FanPlan {
    std::vector<FanEntry> entries;        // transform + opacity writes
    std::vector<FanEntry> colourEntries;  // FILL_COLOR / GRAD_COLOR writes (SHAPE / TEXT layers)
    int64_t rel = 0;
    // The native part of colourChanged: gradient type/geometry (stored)
    // or any evaluated colour differs. The wrapper ORs in the stored
    // fillMode / fill image / fill video comparisons.
    uint8_t colourScalarsChanged = 0;
};
/** The fan-out plan for one connected layer. Each clip crosses as its
 *  stage-1 algebra slice; before/after/layer are evaluated natively
 *  (algebraClipAt) exactly as the Kotlin original evaluates them. The
 *  string/uri parts of the decisions (blendMode, effects, fillMode,
 *  fill image/video) stay wrapper-side — they are equality checks and
 *  field copies, not computations. */
FanPlan graphFanPlan(
    const float beforeScalars[24], const int64_t beforeColors[3], int32_t beforeGradType,
    const int32_t beforeCounts[16], const int64_t* beforeTimes, const float* beforeVals,
    int64_t beforeStart,
    const float afterScalars[24], const int64_t afterColors[3], int32_t afterGradType,
    const int32_t afterCounts[16], const int64_t* afterTimes, const float* afterVals,
    int64_t afterStart,
    const float layerScalars[24], const int64_t layerColors[3], int32_t layerGradType,
    const int32_t layerCounts[16], const int64_t* layerTimes, const float* layerVals,
    int64_t layerStart, int64_t layerDur, int32_t layerType, int64_t timeMs);

/** ControllerFanOut.connected: indices of the clips connected to the
 *  controller key (controllerKey or parentKey), the controller itself
 *  excluded. */
std::vector<int32_t> graphConnected(const GraphPack& p, int32_t controllerKey);

// ---- SoloLaw --------------------------------------------------------------------

/** SoloLaw.soloed / restored flag plans (visibility AND mute — soloing
 *  hides and mutes every other layer; the solo layer's mute is untouched).
 *  soloCopy is 0 only for the solo clip when it is already visible (the
 *  original returns it untouched); restCopy is 0 for clips with no state
 *  entry (returned untouched). */
struct SoloPlan {
    std::vector<uint8_t> soloVisible, soloMuted, soloCopy, restVisible, restMuted, restCopy;
};
SoloPlan graphSoloPlan(const GraphPack& p, int32_t soloKey,
                       const std::vector<uint8_t>& stateHas,
                       const std::vector<uint8_t>& stateVisible,
                       const std::vector<uint8_t>& stateMuted);

// ---- ClipMask key algebra ---------------------------------------------------------

struct MaskStateF { float v[8]; };  // cx, cy, w, h, rot, feather, expand, opacity

/** ClipMask.stateAt: keys are parallel arrays (times, states n*8,
 *  easings n*4). The segment easing is the LEFT key's, tested with
 *  am::easingIsLinear (the stage-3 native behind Easing.isLinear) and
 *  applied with Easing.apply's 0/1 guards, exactly as the original. */
MaskStateF graphMaskStateAt(const MaskStateF& base, int32_t n, const int64_t* times,
                            const float* states, const float* easings, int64_t t);

/** ClipMask.keyIndexAt: nearest key within 40 ms, with the JVM's exact
 *  abs(Long) wrap semantics for the distance. */
int32_t graphMaskKeyIndexAt(int32_t n, const int64_t* times, int64_t t);

/** MaskState.lerp. */
MaskStateF graphMaskLerp(const MaskStateF& a, const MaskStateF& b, float t);

// ---- MotionTrackModel ---------------------------------------------------------------

/** TrackFollow.indexAt: last index whose time is <= t + 2 ms (wrapping
 *  add); 0 when t + 2 precedes the first entry; -1 only when empty. The
 *  original's exact upper-mid binary search. */
int32_t graphTrackIndexAt(int32_t n, const int64_t* timesMs, int64_t t);

/** MotionTrackModel.renderAt's numbers over the EVALUATED clip's stored
 *  transform: applied = 0 when the follow index is < 0 (the wrapper
 *  returns the evaluated clip untouched). rot/scale arrays may be null
 *  (hasRot / hasScale 0), mirroring TrackFollow's nullable fields. */
struct TrackRenderPlan {
    uint8_t applied = 0;
    float x, y, rot, scalePercent, shapeScaleX, shapeScaleY;
};
TrackRenderPlan graphTrackRender(int32_t n, const int64_t* times, const float* dx,
                                 const float* dy, const float* rot, const float* scale,
                                 float tx, float ty, float rotDeg, float scalePercent,
                                 float ssx, float ssy, int64_t t);

/** MotionTrackModel.trackOffsetAt: (0, 0) when the follow index is < 0. */
void graphTrackOffset(int32_t n, const int64_t* times, const float* dx, const float* dy,
                      int64_t t, float out[2]);

// ---- ExtractAudio ---------------------------------------------------------------------

/** ExtractAudio.detachedLayer's keyframe decision: the SPEED entry (and
 *  only it) survives into the detached audio layer. propCounts is the
 *  stage-1 slice layout (-1 = absent). */
uint8_t graphDetachedKeepSpeed(const int32_t propCounts[16]);

/** ExtractAudio.importedLayer's start coercion: coerceAtLeast(0). */
int64_t graphImportedStart(int64_t startMs);

}  // namespace am
