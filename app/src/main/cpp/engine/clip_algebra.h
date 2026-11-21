// clip_algebra: the TimelineClip keyframe algebra, migrated from Kotlin
// (migration stage 1, build 244 — Keyframes.kt's at / withProp / propValues /
// withKeyframe / withoutKeyframe / withEasing / withShiftedKeyframes /
// withScaledKeyframes / keyframeTimes and AnimPresets.kt's rebuilds).
//
// A TimelineClip never crosses JNI whole. The Kotlin wrappers pack the
// clip's ALGEBRA SLICE — every field the algebra reads or writes — into flat
// arrays (see CPP-MIGRATION-REMAINING-MAP.md §2): a fixed-order scalar block,
// three colour longs, the keyframes map in frame_eval's packKeyframes layout
// (times + vals stride 6) extended with a parallel tags array, the pie/arc
// key lists, and mask key times. Untouched fields (effects, masks' states,
// text, uris, tracking, ...) never leave the Kotlin object. The native side
// computes the whole op; the wrapper rebuilds the result with the original
// code's own copy() / map ops, so map order and reference-identity fast
// paths are Kotlin's own, identical by construction.
//
// The composition reuses the existing natives instead of re-implementing
// them: am::keyframeValueAt (frame_eval), am::packColor / am::unpackColor /
// am::indexOnTime (model_logic), am::gradEndpoints / am::gradAngleScale and
// am::pieOutlineSegments / am::arcOutlineSegments (frame_eval).
//
// JVM number semantics are replicated exactly (the model_logic precedent):
// Long overflow wraps (computed in uint64), (timeMs * factor).toLong() is
// float arithmetic with a saturating float->long conversion, Float.coerceIn
// passes NaN through. This TU is compiled with -ffp-contract=off (see
// CMakeLists): it ports strict-IEEE Kotlin float math.
//
// Verified by golden serialized-state diffs against the Kotlin originals
// (~/workspace/after-motion-dev/algebra-migration-golden/).
#pragma once

#include <cstdint>
#include <vector>

namespace am {

// ---- The algebra slice layout (shared with the Kotlin packer) --------------
//
// scalars[24]: 0 transformX, 1 transformY, 2 shapeOffsetX, 3 shapeOffsetY,
//   4 scalePercent, 5 shapeScaleX, 6 shapeScaleY, 7 rotationDeg, 8 transformZ,
//   9 rotation3dX, 10 rotation3dY, 11 cropWidthPercent, 12 cropHeightPercent,
//   13 cropHorizontalPercent, 14 cropVerticalPercent, 15 opacity, 16 speed,
//   17 strokeTrimStart, 18 strokeTrimEnd, 19 shapeCornerRadius,
//   20 gradX1, 21 gradY1, 22 gradX2, 23 gradY2.
// colorLongs[3]: 0 textColor, 1 gradColor1, 2 gradColor2.
// AnimProp ordinals (Kotlin enum order): 0 POSITION, 1 SCALE, 2 ROTATION,
//   3 POS_Z, 4 ROT_X, 5 ROT_Y, 6 FILL_COLOR, 7 GRAD_COLOR_1, 8 GRAD_COLOR_2,
//   9 CROP_SIZE, 10 CROP_POS, 11 OPACITY, 12 SPEED, 13 STROKE_TRIM,
//   14 SHAPE_CORNER_RADIUS, 15 GRAD_GEOMETRY. Colour props: 6, 7, 8.
// Keyframe vals layout (frame_eval packKeyframes): stride 6 per key
//   [a, b, easing.x1, easing.y1, easing.x2, easing.y2]; tags are a parallel
//   int array (0 user, 1 anim-in, 2 anim-out).

constexpr int kAlgebraPropCount = 16;
constexpr int kPropSpeed = 12;
constexpr int64_t kKeyframeTolMs = 40;  // KEYFRAME_TOL_MS in Keyframes.kt

/** Keyframes.propValues: the clip's stored value of one property. */
void algebraPropValues(const float* scalars, const int64_t* colorLongs,
                       int gradType, int prop, float out[2]);

/** Keyframes.withProp: writes one property's value into the scalar block. */
void algebraWithProp(float* scalars, int64_t* colorLongs, int gradType,
                     int prop, float a, float b);

/**
 * Keyframes.at: evaluates every keyframed property at [timeMs] into the
 * scalar block (in/out) and rebuilds pie/arc path segments when the clip is
 * a pie/arc. [propCounts] has kAlgebraPropCount entries, -1 = the prop is
 * absent from the keyframes map, >= 0 = present with that many keys;
 * [kfTimes]/[kfVals] are the present props' keys concatenated in ordinal
 * order. The pie/arc lists use the same key layout (tags never read).
 * [outSegments] receives frame_eval's packed segment stream when a rebuild
 * happened; [outChanged] reports whether anything was applied (the wrapper
 * returns `this` when nothing changed and no segments were rebuilt).
 */
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
                   bool& outChanged);

/** One keyframe list, flat (times / vals stride 6 / tags). */
struct KfList {
    std::vector<int64_t> times;
    std::vector<float> vals;
    std::vector<int32_t> tags;
};

/** Keyframes.withKeyframe on one list: replace on-tolerance, else insert + stable sort. */
void algebraWithKeyframe(const int64_t* times, const float* vals, const int32_t* tags, int n,
                         int64_t relMs, float a, float b, KfList& out);

/** Keyframes.withoutKeyframe on one list (index valid, checked by the caller). */
void algebraWithoutKeyframe(const int64_t* times, const float* vals, const int32_t* tags, int n,
                            int index, KfList& out, float& removedA, float& removedB);

/** Keyframes.withEasing on one list (index valid, checked by the caller). */
void algebraWithEasing(const int64_t* times, const float* vals, const int32_t* tags, int n,
                       int index, float x1, float y1, float x2, float y2, KfList& out);

/** AnimPresets.withoutClipAnim on one list: drops the keys carrying [dropTag]. */
void algebraFilterNotTag(const int64_t* times, const float* vals, const int32_t* tags, int n,
                         int32_t dropTag, KfList& out);

/**
 * AnimPresets tag shifts on one list's times: withTrimmedStartKeyframes
 * shifts the keys NOT carrying [tag] by [delta]; withEndMoved shifts the
 * keys carrying [tag]. [shiftTagged] picks which half moves. Wrapping adds.
 */
void algebraShiftKeyTimesByTag(const int64_t* times, const int32_t* tags, int n,
                               bool shiftTagged, int32_t tag, int64_t delta,
                               int64_t* outTimes);

/** AnimPresets.animSpanMs over a whole map's keys: max-min of [tag] times, 0 when none. */
int64_t algebraAnimSpan(const int64_t* times, const int32_t* tags, int n, int32_t tag);

/** AnimPresets.maxAnimMs: (durationMs - used) coerced at least 0, used = otherSpan + 50 when > 0. */
int64_t algebraMaxAnimMs(int64_t otherSpan, int64_t durationMs);

/** withShiftedKeyframes' per-time law: wrapping add. */
void algebraShiftTimes(const int64_t* times, int n, int64_t delta, int64_t* outTimes);

/** withScaledKeyframes' per-time law: (timeMs * factor).toLong(), JVM float semantics. */
void algebraScaleTimes(const int64_t* times, int n, float factor, int64_t* outTimes);

/** keyframeTimes: the distinct sorted union of all key times. */
void algebraKeyframeTimes(const int64_t* times, int n, std::vector<int64_t>& out);

/**
 * AnimPresets preset plan by catalogue index (AnimPresets.ALL order:
 * 0 fade, 1 slide, 2 slide_fade, 3 bounce, 4 zoom, 5 zoom_fade, 6 zoom_out,
 * 7 pop, 8 spin, 9 spin_zoom, 10 wipe). [slotTag] 1 = In, 2 = Out; [dir]
 * is the AnimDir ordinal (0 LEFT, 1 RIGHT, 2 UP, 3 DOWN). [base] is the
 * base clip's plan slice [scalePercent, transformX, transformY, rotationDeg,
 * cropWidthPercent, cropHeightPercent, cropHorizontalPercent,
 * cropVerticalPercent]. Output layout in [out]: [awayCount,
 * (prop, a, b) x awayCount, restCount, (prop, a, b) x restCount]; returns
 * the number of floats written. [out] must hold at least 11 floats.
 */
int algebraClipAnimPlan(int presetIndex, int slotTag, int dir, const float base[8],
                        float canvasW, float canvasH, float* out);

/**
 * withClipAnim's per-prop splice: keeps the existing keys outside
 * [winStart, winEnd] (strict), appends the two animation keys [mineTimes] /
 * [mineVals] (stride 6 each) carrying [mineTag], stable-sorts by time.
 */
void algebraAnimWindowSplice(const int64_t* times, const float* vals, const int32_t* tags, int n,
                             int64_t winStart, int64_t winEnd,
                             const int64_t mineTimes[2], const float mineVals[12],
                             int32_t mineTag, KfList& out);

}  // namespace am
