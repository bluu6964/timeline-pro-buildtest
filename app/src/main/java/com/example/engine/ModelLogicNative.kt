package com.example.engine

/**
 * JNI bridge for the C++ model logic (model_logic.cpp): the pure value
 * functions of the model/ package migrated in migration #3 — LaneLayout's
 * lane-invariant check, GroupOps' descendants traversal, MultiSelectOps'
 * bar mode and delete set, BeatDetect's tempo / merge / source-offset
 * mapping, ExportRange's range law, and the Keyframes colour packing,
 * Easing curve maths and keyframe-list searches.
 *
 * Only plain values cross this bridge (numbers and flat arrays of numbers /
 * identity keys): the Kotlin wrappers keep their public signatures and do
 * the object <-> array marshalling, so a TimelineClip never crosses JNI
 * (the ClipFx precedent). There is NO Kotlin copy of the migrated logic any
 * more — native is the only path, like the other post-234 bridges: a
 * genuinely missing libam_engine.so fails loudly at the first call.
 * [isAvailable] stays as a read-only probe (the UiMathNative precedent — no
 * throwing init gate, so a host JVM test that touches this object without
 * the library does not break in <init>).
 */
object ModelLogicNative {
    init {
        // Trigger DecoderPool's library load on first touch of this bridge
        // (it is the one place that calls System.loadLibrary), so a call can
        // never race it when a model function happens to be the first native
        // touch. Not a gate: when the library is genuinely absent this still
        // completes, [isAvailable] reports false, and the first external call
        // fails loudly — the post-234 rule.
        DecoderPool.isAvailable
    }

    val isAvailable: Boolean get() = try { DecoderPool.isAvailable } catch (_: Throwable) { false }

    // ---- Keyframes.kt: colour packing, Easing maths, keyframe searches ----

    /** [packed (A,R), packed (G,B)] for a 0xAARRGGBB colour. */
    @JvmStatic external fun nativePackColor(argb: Long): FloatArray
    @JvmStatic external fun nativeUnpackColor(pa: Float, pb: Float): Long
    @JvmStatic external fun nativeEasingIsLinear(x1: Float, y1: Float, x2: Float, y2: Float): Boolean
    /** [bx(s), by(s)] of the cubic bezier. */
    @JvmStatic external fun nativeEasingPointAt(x1: Float, y1: Float, x2: Float, y2: Float, s: Float): FloatArray
    @JvmStatic external fun nativeEasingSameAs(
        x1: Float, y1: Float, x2: Float, y2: Float,
        ox1: Float, oy1: Float, ox2: Float, oy2: Float, eps: Float
    ): Boolean
    @JvmStatic external fun nativeSegmentIndexAt(times: LongArray, relMs: Long): Int
    @JvmStatic external fun nativeIndexOnTime(times: LongArray, relMs: Long, tolMs: Long): Int

    // ---- BeatDetect.kt ----

    /** The tempo estimate, or NaN when there is none. */
    @JvmStatic external fun nativeEstimateBpm(beats: LongArray, minIntervalMs: Long, maxIntervalMs: Long): Double
    @JvmStatic external fun nativeMergeBeats(existing: LongArray, detected: LongArray, tolMs: Long): LongArray
    @JvmStatic external fun nativeTimelineMsForOffsets(
        durationMs: Long, speed: Double, spanMs: Long, offsets: LongArray
    ): LongArray

    // ---- ExportRange.kt ----

    /** [inMs, outMs] of the accepted range, or null when there is no usable range. */
    @JvmStatic external fun nativeExportRangeOf(
        inMs: Long, inSet: Boolean, outMs: Long, outSet: Boolean, totalMs: Long, minLenMs: Long
    ): LongArray?

    // ---- MultiSelectOps.kt ----

    /** Multi-bar mode: 0 = SPLIT, 1 = START, 2 = END. */
    @JvmStatic external fun nativeBarMode(starts: LongArray, ends: LongArray, playheadMs: Long): Int

    /**
     * The deleteChecked deletion set as ascending flat clip indices;
     * [sparedOut] (length >= 1) receives the spared-locked count. Clips are
     * addressed by identity keys (see model_logic.h); typeCodes 1 = CHAIN,
     * 2 = GROUP, 0 = other.
     */
    @JvmStatic external fun nativeDeleteSet(
        clipKeys: IntArray, chainKeys: IntArray, groupKeys: IntArray, typeCodes: IntArray,
        locked: BooleanArray, checked: BooleanArray, sparedOut: IntArray
    ): IntArray

    // ---- GroupOps.kt ----

    /** Descendant flat indices of the group whose id key is [rootKey], in discovery order. */
    @JvmStatic external fun nativeDescendants(
        clipKeys: IntArray, groupKeys: IntArray, isGroup: BooleanArray, rootKey: Int
    ): IntArray

    // ---- LaneLayout.kt ----

    /**
     * The isNormalized lane-invariant check. [laneOffsets] has laneCount+1
     * entries; per flat clip: [startMs], [isChain], [chainIdNull] and
     * [chainIdTarget] (flat index of the same-lane clip whose id equals the
     * clip's chainId, or -1).
     */
    @JvmStatic external fun nativeIsNormalized(
        laneOffsets: IntArray, startMs: LongArray, isChain: BooleanArray,
        chainIdNull: BooleanArray, chainIdTarget: IntArray
    ): Boolean
}
