// clip_algebra: line-for-line ports of the TimelineClip keyframe algebra
// (Keyframes.kt / AnimPresets.kt). See clip_algebra.h for the slice layout.
// Compiled with -ffp-contract=off (CMakeLists): strict-IEEE Kotlin float
// math must not be FMA-contracted.

#include "clip_algebra.h"

#include <algorithm>
#include <cmath>

#include "frame_eval.h"
#include "model_logic.h"

namespace am {

namespace {

// Scalar block indices (clip_algebra.h).
enum {
    S_TX = 0, S_TY = 1, S_SOX = 2, S_SOY = 3, S_SCALE = 4, S_SSX = 5, S_SSY = 6,
    S_ROT = 7, S_TZ = 8, S_ROTX = 9, S_ROTY = 10, S_CROPW = 11, S_CROPH = 12,
    S_CROPHX = 13, S_CROPVY = 14, S_OPACITY = 15, S_SPEED = 16,
    S_TRIM0 = 17, S_TRIM1 = 18, S_CORNER = 19,
    S_GX1 = 20, S_GY1 = 21, S_GX2 = 22, S_GY2 = 23
};

// AnimProp ordinals with special handling here.
enum {
    P_POSITION = 0, P_SCALE = 1, P_ROTATION = 2, P_POS_Z = 3, P_ROT_X = 4,
    P_ROT_Y = 5, P_FILL = 6, P_GRAD1 = 7, P_GRAD2 = 8, P_CROP_SIZE = 9,
    P_CROP_POS = 10, P_OPACITY = 11, P_SPEED = 12, P_TRIM = 13,
    P_CORNER = 14, P_GRAD_GEO = 15
};

// MIN_SPEED / MAX_SPEED come from frame_eval.h (am::kMinSpeed / am::kMaxSpeed).

/** Kotlin Float.coerceIn: NaN passes through (both comparisons false). */
float coerceInF(float v, float lo, float hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

/** Kotlin Float.coerceAtLeast: NaN passes through. */
float coerceAtLeastF(float v, float lo) {
    return v < lo ? lo : v;
}

/** Wrapping int64 add/sub (Kotlin Long arithmetic), via uint64 (well-defined). */
int64_t wrapAdd(int64_t a, int64_t b) {
    return static_cast<int64_t>(static_cast<uint64_t>(a) + static_cast<uint64_t>(b));
}
int64_t wrapSub(int64_t a, int64_t b) {
    return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
}

/** JVM float -> long conversion (Kotlin Float.toLong): NaN -> 0, saturates. */
int64_t floatToLongSaturating(float f) {
    if (std::isnan(f)) return 0;
    if (f >= 9223372036854775808.0f) return INT64_MAX;   // 2^63
    if (f <= -9223372036854775808.0f) return INT64_MIN;  // -2^63
    return static_cast<int64_t>(f);  // truncates toward zero, in range
}

bool propIsColor(int prop) { return prop == P_FILL || prop == P_GRAD1 || prop == P_GRAD2; }

void copyList(const int64_t* times, const float* vals, const int32_t* tags, int n, KfList& out) {
    out.times.assign(times, times + n);
    out.vals.assign(vals, vals + n * 6);
    out.tags.assign(tags, tags + n);
}

/** Stable sort of a KfList by timeMs (Kotlin sortedBy is stable). */
void stableSortByTime(KfList& l) {
    const int n = static_cast<int>(l.times.size());
    std::vector<int> idx(static_cast<size_t>(n));
    for (int i = 0; i < n; i++) idx[static_cast<size_t>(i)] = i;
    std::stable_sort(idx.begin(), idx.end(),
                     [&](int x, int y) { return l.times[static_cast<size_t>(x)] < l.times[static_cast<size_t>(y)]; });
    KfList s;
    s.times.resize(static_cast<size_t>(n));
    s.vals.resize(static_cast<size_t>(n) * 6);
    s.tags.resize(static_cast<size_t>(n));
    for (int i = 0; i < n; i++) {
        const int src = idx[static_cast<size_t>(i)];
        s.times[static_cast<size_t>(i)] = l.times[static_cast<size_t>(src)];
        for (int k = 0; k < 6; k++)
            s.vals[static_cast<size_t>(i) * 6 + k] = l.vals[static_cast<size_t>(src) * 6 + k];
        s.tags[static_cast<size_t>(i)] = l.tags[static_cast<size_t>(src)];
    }
    l = std::move(s);
}

}  // namespace

void algebraPropValues(const float* sc, const int64_t* cl, int gradType, int prop, float out[2]) {
    switch (prop) {
        case P_POSITION: out[0] = sc[S_TX]; out[1] = sc[S_TY]; return;
        case P_SCALE: out[0] = sc[S_SCALE]; out[1] = 0.0f; return;
        case P_ROTATION: out[0] = sc[S_ROT]; out[1] = 0.0f; return;
        case P_POS_Z: out[0] = sc[S_TZ]; out[1] = 0.0f; return;
        case P_ROT_X: out[0] = sc[S_ROTX]; out[1] = 0.0f; return;
        case P_ROT_Y: out[0] = sc[S_ROTY]; out[1] = 0.0f; return;
        case P_FILL: packColor(cl[0], out); return;
        case P_GRAD1: packColor(cl[1], out); return;
        case P_GRAD2: packColor(cl[2], out); return;
        case P_CROP_SIZE: out[0] = sc[S_CROPW]; out[1] = sc[S_CROPH]; return;
        case P_CROP_POS: out[0] = sc[S_CROPHX]; out[1] = sc[S_CROPVY]; return;
        case P_OPACITY: out[0] = sc[S_OPACITY]; out[1] = 0.0f; return;
        case P_SPEED: out[0] = sc[S_SPEED]; out[1] = 0.0f; return;
        case P_TRIM: out[0] = sc[S_TRIM0]; out[1] = sc[S_TRIM1]; return;
        case P_CORNER: out[0] = sc[S_CORNER]; out[1] = 0.0f; return;
        case P_GRAD_GEO:
            gradAngleScale(gradType, sc[S_GX1], sc[S_GY1], sc[S_GX2], sc[S_GY2], out);
            return;
        default: out[0] = 0.0f; out[1] = 0.0f; return;
    }
}

void algebraWithProp(float* sc, int64_t* cl, int gradType, int prop, float a, float b) {
    switch (prop) {
        case P_POSITION:
            sc[S_TX] = a; sc[S_TY] = b; sc[S_SOX] = a; sc[S_SOY] = b;
            return;
        case P_SCALE:
            // b > 0 is a separate height scale; b == 0 keeps the uniform law.
            sc[S_SCALE] = a;
            sc[S_SSX] = a / 100.0f;
            sc[S_SSY] = (b > 0.0f ? b : a) / 100.0f;
            return;
        case P_ROTATION: sc[S_ROT] = a; return;
        case P_POS_Z: sc[S_TZ] = a; return;
        case P_ROT_X: sc[S_ROTX] = a; return;
        case P_ROT_Y: sc[S_ROTY] = a; return;
        case P_FILL: cl[0] = unpackColor(a, b); return;
        case P_GRAD1: cl[1] = unpackColor(a, b); return;
        case P_GRAD2: cl[2] = unpackColor(a, b); return;
        case P_CROP_SIZE: sc[S_CROPW] = a; sc[S_CROPH] = b; return;
        case P_CROP_POS: sc[S_CROPHX] = a; sc[S_CROPVY] = b; return;
        case P_OPACITY: sc[S_OPACITY] = a; return;
        case P_SPEED: sc[S_SPEED] = coerceInF(a, kMinSpeed, kMaxSpeed); return;
        case P_TRIM:
            sc[S_TRIM0] = coerceInF(a, 0.0f, 1.0f);
            sc[S_TRIM1] = coerceInF(b, 0.0f, 1.0f);
            return;
        case P_CORNER: sc[S_CORNER] = a; return;
        case P_GRAD_GEO: {
            // GradGeometry.center: linear -> midpoint, radial/sweep -> first point.
            float cx, cy;
            if (gradType == 0 /* GRAD_LINEAR */) {
                cx = (sc[S_GX1] + sc[S_GX2]) / 2.0f;
                cy = (sc[S_GY1] + sc[S_GY2]) / 2.0f;
            } else {
                cx = sc[S_GX1];
                cy = sc[S_GY1];
            }
            float pts[4];
            gradEndpoints(gradType, cx, cy, a, b, pts);
            sc[S_GX1] = pts[0]; sc[S_GY1] = pts[1]; sc[S_GX2] = pts[2]; sc[S_GY2] = pts[3];
            return;
        }
        default: return;
    }
}

void algebraClipAt(int64_t startMs, float* scalars, int64_t* colorLongs,
                   int gradType, const int32_t* propCounts,
                   const int64_t* kfTimes, const float* kfVals,
                   const int64_t* pieStartTimes, const float* pieStartVals, int pieStartN,
                   const int64_t* pieEndTimes, const float* pieEndVals, int pieEndN,
                   const int64_t* arcStartTimes, const float* arcStartVals, int arcStartN,
                   const int64_t* arcEndTimes, const float* arcEndVals, int arcEndN,
                   float pieRadius, float arcRadius, bool arcClosed,
                   int64_t timeMs,
                   std::vector<float>& outSegments, bool& outHasSegments,
                   bool& outChanged) {
    outSegments.clear();
    outHasSegments = false;
    outChanged = false;
    const int64_t rel = wrapSub(timeMs, startMs);
    bool mapEmpty = true;
    for (int p = 0; p < kAlgebraPropCount; p++) {
        if (propCounts[p] >= 0) { mapEmpty = false; break; }
    }
    const bool hasPie = pieStartN > 0 && pieEndN > 0 && pieRadius > 0.0f;
    const bool hasArc = arcStartN > 0 && arcEndN > 0 && arcRadius > 0.0f;
    if (!mapEmpty) {
        int offset = 0;
        for (int p = 0; p < kAlgebraPropCount; p++) {
            const int cnt = propCounts[p];
            if (cnt < 0) continue;
            if (cnt > 0 && p != P_SPEED) {
                // Build 237: stored SPEED keyframes are never evaluated.
                float a, b;
                keyframeValueAt(kfTimes + offset, kfVals + offset * 6, cnt, rel,
                                propIsColor(p), a, b);
                algebraWithProp(scalars, colorLongs, gradType, p, a, b);
                outChanged = true;
            }
            offset += cnt;
        }
        if (hasPie) {
            float s, sb, e, eb;
            keyframeValueAt(pieStartTimes, pieStartVals, pieStartN, rel, false, s, sb);
            keyframeValueAt(pieEndTimes, pieEndVals, pieEndN, rel, false, e, eb);
            outSegments = pieOutlineSegments(static_cast<double>(s), static_cast<double>(e), 1.0);
            outHasSegments = true;
        }
        if (hasArc) {
            float s, sb, e, eb;
            keyframeValueAt(arcStartTimes, arcStartVals, arcStartN, rel, false, s, sb);
            keyframeValueAt(arcEndTimes, arcEndVals, arcEndN, rel, false, e, eb);
            outSegments = arcOutlineSegments(static_cast<double>(s), static_cast<double>(e), 1.0, arcClosed);
            outHasSegments = true;
        }
    } else {
        if (hasPie) {
            float s, sb, e, eb;
            keyframeValueAt(pieStartTimes, pieStartVals, pieStartN, rel, false, s, sb);
            keyframeValueAt(pieEndTimes, pieEndVals, pieEndN, rel, false, e, eb);
            outSegments = pieOutlineSegments(static_cast<double>(s), static_cast<double>(e), 1.0);
            outHasSegments = true;
        } else if (hasArc) {
            float s, sb, e, eb;
            keyframeValueAt(arcStartTimes, arcStartVals, arcStartN, rel, false, s, sb);
            keyframeValueAt(arcEndTimes, arcEndVals, arcEndN, rel, false, e, eb);
            outSegments = arcOutlineSegments(static_cast<double>(s), static_cast<double>(e), 1.0, arcClosed);
            outHasSegments = true;
        }
    }
    if (outHasSegments) outChanged = true;
}

void algebraWithKeyframe(const int64_t* times, const float* vals, const int32_t* tags, int n,
                         int64_t relMs, float a, float b, KfList& out) {
    const int idx = indexOnTime(times, n, relMs, kKeyframeTolMs);
    if (idx >= 0) {
        copyList(times, vals, tags, n, out);
        out.vals[static_cast<size_t>(idx) * 6 + 0] = a;
        out.vals[static_cast<size_t>(idx) * 6 + 1] = b;
        return;
    }
    copyList(times, vals, tags, n, out);
    out.times.push_back(relMs);
    // Keyframe(relMs, a, b): Easing.LINEAR = (1/3, 1/3, 2/3, 2/3), tag 0.
    const float lin[6] = {a, b, 1.0f / 3.0f, 1.0f / 3.0f, 2.0f / 3.0f, 2.0f / 3.0f};
    out.vals.insert(out.vals.end(), lin, lin + 6);
    out.tags.push_back(0);
    stableSortByTime(out);
}

void algebraWithoutKeyframe(const int64_t* times, const float* vals, const int32_t* tags, int n,
                            int index, KfList& out, float& removedA, float& removedB) {
    removedA = vals[static_cast<size_t>(index) * 6 + 0];
    removedB = vals[static_cast<size_t>(index) * 6 + 1];
    out.times.clear(); out.vals.clear(); out.tags.clear();
    for (int i = 0; i < n; i++) {
        if (i == index) continue;
        out.times.push_back(times[i]);
        out.vals.insert(out.vals.end(), vals + i * 6, vals + i * 6 + 6);
        out.tags.push_back(tags[i]);
    }
}

void algebraWithEasing(const int64_t* times, const float* vals, const int32_t* tags, int n,
                       int index, float x1, float y1, float x2, float y2, KfList& out) {
    copyList(times, vals, tags, n, out);
    out.vals[static_cast<size_t>(index) * 6 + 2] = x1;
    out.vals[static_cast<size_t>(index) * 6 + 3] = y1;
    out.vals[static_cast<size_t>(index) * 6 + 4] = x2;
    out.vals[static_cast<size_t>(index) * 6 + 5] = y2;
}

void algebraFilterNotTag(const int64_t* times, const float* vals, const int32_t* tags, int n,
                         int32_t dropTag, KfList& out) {
    out.times.clear(); out.vals.clear(); out.tags.clear();
    for (int i = 0; i < n; i++) {
        if (tags[i] == dropTag) continue;
        out.times.push_back(times[i]);
        out.vals.insert(out.vals.end(), vals + i * 6, vals + i * 6 + 6);
        out.tags.push_back(tags[i]);
    }
}

void algebraShiftKeyTimesByTag(const int64_t* times, const int32_t* tags, int n,
                               bool shiftTagged, int32_t tag, int64_t delta,
                               int64_t* outTimes) {
    for (int i = 0; i < n; i++) {
        const bool tagged = tags[i] == tag;
        outTimes[i] = (tagged == shiftTagged) ? wrapAdd(times[i], delta) : times[i];
    }
}

int64_t algebraAnimSpan(const int64_t* times, const int32_t* tags, int n, int32_t tag) {
    bool any = false;
    int64_t mn = 0, mx = 0;
    for (int i = 0; i < n; i++) {
        if (tags[i] != tag) continue;
        if (!any) { mn = mx = times[i]; any = true; }
        else { if (times[i] < mn) mn = times[i]; if (times[i] > mx) mx = times[i]; }
    }
    return any ? wrapSub(mx, mn) : 0;
}

int64_t algebraMaxAnimMs(int64_t otherSpan, int64_t durationMs) {
    const int64_t used = otherSpan > 0 ? wrapAdd(otherSpan, 50) : 0;  // ANIM_GAP_MS
    const int64_t r = wrapSub(durationMs, used);
    return r < 0 ? 0 : r;
}

void algebraShiftTimes(const int64_t* times, int n, int64_t delta, int64_t* outTimes) {
    for (int i = 0; i < n; i++) outTimes[i] = wrapAdd(times[i], delta);
}

void algebraScaleTimes(const int64_t* times, int n, float factor, int64_t* outTimes) {
    for (int i = 0; i < n; i++) {
        // Kotlin (timeMs * factor).toLong(): Long.toFloat() rounds, float
        // multiply, saturating conversion.
        const float f = static_cast<float>(times[i]) * factor;
        outTimes[i] = floatToLongSaturating(f);
    }
}

void algebraKeyframeTimes(const int64_t* times, int n, std::vector<int64_t>& out) {
    out.assign(times, times + n);
    std::sort(out.begin(), out.end());
    out.erase(std::unique(out.begin(), out.end()), out.end());
}

// ---- AnimPresets plans ------------------------------------------------------

namespace {

// AnimDir ordinals: 0 LEFT, 1 RIGHT, 2 UP, 3 DOWN.
enum { D_LEFT = 0, D_RIGHT = 1, D_UP = 2, D_DOWN = 3 };
// Base slice: 0 scalePercent, 1 transformX, 2 transformY, 3 rotationDeg,
// 4 cropWidthPercent, 5 cropHeightPercent, 6 cropHorizontalPercent,
// 7 cropVerticalPercent.

void planOffscreen(const float base[8], int dir, float canvasW, float canvasH, float out[2]) {
    const float s = coerceAtLeastF(base[0] / 100.0f, 0.1f);
    const float hx = canvasW / 2.0f * (1.0f + s);
    const float hy = canvasH / 2.0f * (1.0f + s);
    const float x = base[1];
    const float y = base[2];
    switch (dir) {
        case D_LEFT: out[0] = -hx; out[1] = y; return;
        case D_RIGHT: out[0] = hx; out[1] = y; return;
        case D_UP: out[0] = x; out[1] = -hy; return;
        default: out[0] = x; out[1] = hy; return;  // D_DOWN
    }
}

void planNudged(const float base[8], int dir, float fraction, float canvasW, float canvasH,
                float out[2]) {
    const float x = base[1];
    const float y = base[2];
    switch (dir) {
        case D_LEFT: out[0] = x - canvasW * fraction; out[1] = y; return;
        case D_RIGHT: out[0] = x + canvasW * fraction; out[1] = y; return;
        case D_UP: out[0] = x; out[1] = y - canvasH * fraction; return;
        default: out[0] = x; out[1] = y + canvasH * fraction; return;  // D_DOWN
    }
}

/** AnimPresets.rotated: In arrives turning in the chosen sense, Out leaves turning in it. */
float planRotated(const float base[8], int slotTag, int dir, float degrees) {
    const float sign = slotTag == 1 /* ANIM_TAG_IN */ ? -1.0f : 1.0f;
    const float sense = (dir == D_RIGHT || dir == D_DOWN) ? 1.0f : -1.0f;
    return base[3] + sign * sense * degrees;
}

int putAway(float* out, int pos, int prop, float a, float b) {
    out[pos] = static_cast<float>(prop);
    out[pos + 1] = a;
    out[pos + 2] = b;
    return pos + 3;
}

}  // namespace

int algebraClipAnimPlan(int presetIndex, int slotTag, int dir, const float base[8],
                        float canvasW, float canvasH, float* out) {
    // Away entries are written from slot 1; the header counts are patched in
    // at the end. awayCount <= 2 for every catalogue preset, restCount <= 1.
    int pos = 1;
    int restPos = -1;
    float v[2];
    switch (presetIndex) {
        case 0:  // fade
            pos = putAway(out, pos, P_OPACITY, 0.0f, 0.0f);
            break;
        case 1:  // slide
            planOffscreen(base, dir, canvasW, canvasH, v);
            pos = putAway(out, pos, P_POSITION, v[0], v[1]);
            break;
        case 2:  // slide_fade
            planNudged(base, dir, 0.2f, canvasW, canvasH, v);
            pos = putAway(out, pos, P_POSITION, v[0], v[1]);
            pos = putAway(out, pos, P_OPACITY, 0.0f, 0.0f);
            break;
        case 3:  // bounce
            planOffscreen(base, dir, canvasW, canvasH, v);
            pos = putAway(out, pos, P_POSITION, v[0], v[1]);
            break;
        case 4:  // zoom
            pos = putAway(out, pos, P_SCALE, base[0] * 0.0f, 0.0f);
            break;
        case 5:  // zoom_fade
            pos = putAway(out, pos, P_SCALE, base[0] * 0.5f, 0.0f);
            pos = putAway(out, pos, P_OPACITY, 0.0f, 0.0f);
            break;
        case 6:  // zoom_out
            pos = putAway(out, pos, P_SCALE, base[0] * 1.6f, 0.0f);
            pos = putAway(out, pos, P_OPACITY, 0.0f, 0.0f);
            break;
        case 7:  // pop
            pos = putAway(out, pos, P_SCALE, base[0] * 0.0f, 0.0f);
            break;
        case 8:  // spin
            pos = putAway(out, pos, P_ROTATION, planRotated(base, slotTag, dir, 180.0f), 0.0f);
            pos = putAway(out, pos, P_OPACITY, 0.0f, 0.0f);
            break;
        case 9:  // spin_zoom
            pos = putAway(out, pos, P_ROTATION, planRotated(base, slotTag, dir, 360.0f), 0.0f);
            pos = putAway(out, pos, P_SCALE, base[0] * 0.0f, 0.0f);
            break;
        case 10: {  // wipe
            const float minP = 5.0f;  // the renderer never crops below 5 %
            const float bw = base[4];
            const float bh = base[5];
            const float l0 = (1.0f - bw / 100.0f) * (base[6] / 100.0f);
            const float t0 = (1.0f - bh / 100.0f) * (base[7] / 100.0f);
            auto posFor = [](float edgeStart, float size) {
                const float room = 1.0f - size / 100.0f;
                return room < 1e-3f ? 50.0f
                                    : coerceInF(edgeStart / room * 100.0f, 0.0f, 100.0f);
            };
            float w = bw, h = bh, px = base[6], py = base[7];
            switch (dir) {
                case D_LEFT: w = minP; px = posFor(l0, w); break;
                case D_RIGHT: w = minP; px = posFor(l0 + bw / 100.0f - w / 100.0f, w); break;
                case D_UP: h = minP; py = posFor(t0, h); break;
                default: h = minP; py = posFor(t0 + bh / 100.0f - h / 100.0f, h); break;  // D_DOWN
            }
            const float restX = (bw >= 99.9f && (dir == D_LEFT || dir == D_RIGHT)) ? px : base[6];
            const float restY = (bh >= 99.9f && (dir == D_UP || dir == D_DOWN)) ? py : base[7];
            pos = putAway(out, pos, P_CROP_SIZE, w, h);
            pos = putAway(out, pos, P_CROP_POS, px, py);
            restPos = pos;
            out[restPos] = 1.0f;  // restCount
            out[restPos + 1] = static_cast<float>(P_CROP_POS);
            out[restPos + 2] = restX;
            out[restPos + 3] = restY;
            pos += 4;
            break;
        }
        default:
            out[0] = 0.0f;
            out[1] = 0.0f;
            return 2;
    }
    const int awayCount = (pos - 1) / 3 - (restPos >= 0 ? 1 : 0);
    if (restPos < 0) {
        out[pos] = 0.0f;  // restCount
        pos += 1;
    }
    out[0] = static_cast<float>(awayCount);
    return pos;
}

void algebraAnimWindowSplice(const int64_t* times, const float* vals, const int32_t* tags, int n,
                             int64_t winStart, int64_t winEnd,
                             const int64_t mineTimes[2], const float mineVals[12],
                             int32_t mineTag, KfList& out) {
    out.times.clear(); out.vals.clear(); out.tags.clear();
    for (int i = 0; i < n; i++) {
        if (times[i] < winStart || times[i] > winEnd) {
            out.times.push_back(times[i]);
            out.vals.insert(out.vals.end(), vals + i * 6, vals + i * 6 + 6);
            out.tags.push_back(tags[i]);
        }
    }
    for (int k = 0; k < 2; k++) {
        out.times.push_back(mineTimes[k]);
        out.vals.insert(out.vals.end(), mineVals + k * 6, mineVals + k * 6 + 6);
        out.tags.push_back(mineTag);
    }
    stableSortByTime(out);
}

}  // namespace am
