package com.example.engine

import com.example.model.AnimProp
import com.example.model.Easing
import com.example.model.Keyframe
import com.example.model.TimelineClip

/**
 * JNI bridge for the C++ TimelineClip keyframe algebra (cpp/engine/clip_algebra.{h,cpp},
 * migration stage 1, build 244): Keyframes.kt's at / withProp / propValues /
 * withKeyframe / withoutKeyframe / withEasing / shift / scale / keyframeTimes and
 * AnimPresets.kt's rebuilds.
 *
 * A TimelineClip never crosses JNI whole. The packers here marshal the clip's
 * ALGEBRA SLICE — every field the algebra reads or writes (see
 * CPP-MIGRATION-REMAINING-MAP.md §2): the fixed-order scalar block, the three
 * colour longs, the keyframes map in FrameEvalNative.packKeyframes' layout
 * (times + vals stride 6) extended with a parallel tags array, the pie/arc key
 * lists and the mask key times. Untouched fields never leave the Kotlin object.
 * The model wrappers rebuild results with the original code's own copy() / map
 * ops, so map order and reference-identity fast paths are Kotlin's own.
 *
 * There is NO Kotlin copy of the migrated algebra any more — native is the only
 * path (the post-234 rule): a genuinely missing libam_engine.so fails loudly at
 * the first call. [isAvailable] stays a read-only probe (no throwing init gate).
 */
object ClipAlgebraNative {
    init {
        // Trigger DecoderPool's library load on first touch (see ModelLogicNative).
        DecoderPool.isAvailable
    }

    val isAvailable: Boolean get() = try { DecoderPool.isAvailable } catch (_: Throwable) { false }

    // ---- scalar block ----

    /** The 24-float algebra slice of [c], in clip_algebra.h's fixed order. */
    fun packScalars(c: TimelineClip): FloatArray = floatArrayOf(
        c.transformX, c.transformY, c.shapeOffsetX, c.shapeOffsetY,
        c.scalePercent, c.shapeScaleX, c.shapeScaleY, c.rotationDeg,
        c.transformZ, c.rotation3dX, c.rotation3dY,
        c.cropWidthPercent, c.cropHeightPercent, c.cropHorizontalPercent, c.cropVerticalPercent,
        c.opacity, c.speed, c.strokeTrimStart, c.strokeTrimEnd, c.shapeCornerRadius,
        c.gradX1, c.gradY1, c.gradX2, c.gradY2
    )

    /** [textColor, gradColor1, gradColor2]. */
    fun packColorLongs(c: TimelineClip): LongArray = longArrayOf(c.textColor, c.gradColor1, c.gradColor2)

    /** [c] with the scalar block + colour longs applied (mechanical field copy, no decisions). */
    fun applyScalars(c: TimelineClip, sc: FloatArray, cl: LongArray): TimelineClip = c.copy(
        transformX = sc[0], transformY = sc[1], shapeOffsetX = sc[2], shapeOffsetY = sc[3],
        scalePercent = sc[4], shapeScaleX = sc[5], shapeScaleY = sc[6], rotationDeg = sc[7],
        transformZ = sc[8], rotation3dX = sc[9], rotation3dY = sc[10],
        cropWidthPercent = sc[11], cropHeightPercent = sc[12],
        cropHorizontalPercent = sc[13], cropVerticalPercent = sc[14],
        opacity = sc[15], speed = sc[16], strokeTrimStart = sc[17], strokeTrimEnd = sc[18],
        shapeCornerRadius = sc[19],
        gradX1 = sc[20], gradY1 = sc[21], gradX2 = sc[22], gradY2 = sc[23],
        textColor = cl[0], gradColor1 = cl[1], gradColor2 = cl[2]
    )

    // ---- keyframe lists ----

    /** (times, vals stride 6, tags) of one keyframe list — the packKeyframes layout + tags. */
    fun packKeys(list: List<Keyframe>): Triple<LongArray, FloatArray, IntArray> {
        val n = list.size
        val times = LongArray(n)
        val vals = FloatArray(n * 6)
        val tags = IntArray(n)
        for (i in 0 until n) {
            val k = list[i]
            times[i] = k.timeMs
            val b = i * 6
            vals[b] = k.a; vals[b + 1] = k.b
            vals[b + 2] = k.easing.x1; vals[b + 3] = k.easing.y1
            vals[b + 4] = k.easing.x2; vals[b + 5] = k.easing.y2
            tags[i] = k.tag
        }
        return Triple(times, vals, tags)
    }

    fun unpackKeys(times: LongArray, vals: FloatArray, tags: IntArray, n: Int): List<Keyframe> {
        if (n == 0) return emptyList()
        val out = ArrayList<Keyframe>(n)
        for (i in 0 until n) {
            val b = i * 6
            out += Keyframe(
                times[i], vals[b], vals[b + 1],
                Easing(vals[b + 2], vals[b + 3], vals[b + 4], vals[b + 5]),
                tags[i]
            )
        }
        return out
    }

    // ---- native ops ----

    @JvmStatic external fun nativePropValues(
        scalars: FloatArray, colorLongs: LongArray, gradType: Int, prop: Int, out2: FloatArray
    )

    /** Mutates [scalars] / [colorLongs] in place. */
    @JvmStatic external fun nativeWithProp(
        scalars: FloatArray, colorLongs: LongArray, gradType: Int, prop: Int, a: Float, b: Float
    )

    /**
     * The at() evaluation: mutates [scalars] / [colorLongs] in place, sets
     * changedOut[0], and returns the rebuilt pie/arc path segments in
     * frame_eval's packed stream format, or null when no rebuild happened.
     */
    @JvmStatic external fun nativeClipAt(
        startMs: Long, scalars: FloatArray, colorLongs: LongArray, gradType: Int,
        propCounts: IntArray, kfTimes: LongArray, kfVals: FloatArray,
        pieStartTimes: LongArray, pieStartVals: FloatArray,
        pieEndTimes: LongArray, pieEndVals: FloatArray,
        arcStartTimes: LongArray, arcStartVals: FloatArray,
        arcEndTimes: LongArray, arcEndVals: FloatArray,
        pieRadius: Float, arcRadius: Float, arcClosed: Boolean,
        timeMs: Long, changedOut: BooleanArray
    ): FloatArray?

    /** Out arrays sized for n+1 keys; returns the new key count. */
    @JvmStatic external fun nativeWithKeyframe(
        times: LongArray, vals: FloatArray, tags: IntArray, relMs: Long, a: Float, b: Float,
        outTimes: LongArray, outVals: FloatArray, outTags: IntArray
    ): Int

    /** Out arrays sized for n-1 keys; removedOut receives the removed key's (a, b). Returns the new count. */
    @JvmStatic external fun nativeWithoutKeyframe(
        times: LongArray, vals: FloatArray, tags: IntArray, index: Int,
        outTimes: LongArray, outVals: FloatArray, outTags: IntArray, removedOut: FloatArray
    ): Int

    /** Out arrays sized for n keys; returns n. */
    @JvmStatic external fun nativeWithEasing(
        times: LongArray, vals: FloatArray, tags: IntArray, index: Int,
        x1: Float, y1: Float, x2: Float, y2: Float,
        outTimes: LongArray, outVals: FloatArray, outTags: IntArray
    ): Int

    /** Out arrays sized for n keys; returns the kept count. */
    @JvmStatic external fun nativeFilterKeysNotTag(
        times: LongArray, vals: FloatArray, tags: IntArray, dropTag: Int,
        outTimes: LongArray, outVals: FloatArray, outTags: IntArray
    ): Int

    @JvmStatic external fun nativeShiftKeyTimesByTag(
        times: LongArray, tags: IntArray, shiftTagged: Boolean, tag: Int, delta: Long,
        outTimes: LongArray
    )

    @JvmStatic external fun nativeAnimSpan(times: LongArray, tags: IntArray, tag: Int): Long

    @JvmStatic external fun nativeMaxAnimMs(otherSpan: Long, durationMs: Long): Long

    @JvmStatic external fun nativeShiftTimes(times: LongArray, delta: Long, outTimes: LongArray)

    @JvmStatic external fun nativeScaleTimes(times: LongArray, factor: Float, outTimes: LongArray)

    /** The distinct sorted key times of the concatenated input. */
    @JvmStatic external fun nativeKeyframeTimes(times: LongArray): LongArray

    /**
     * One preset plan by catalogue index. [base] is the base clip's plan slice
     * [scalePercent, transformX, transformY, rotationDeg, cropWidthPercent,
     * cropHeightPercent, cropHorizontalPercent, cropVerticalPercent]. [out]
     * receives [awayCount, (prop, a, b) x awayCount, restCount, (prop, a, b) x
     * restCount]; returns the floats written.
     */
    @JvmStatic external fun nativeClipAnimPlan(
        presetIndex: Int, slotTag: Int, dir: Int, base: FloatArray,
        canvasW: Float, canvasH: Float, out: FloatArray
    ): Int

    /** Out arrays sized for n+2 keys; returns the new key count. */
    @JvmStatic external fun nativeAnimWindowSplice(
        times: LongArray, vals: FloatArray, tags: IntArray,
        winStart: Long, winEnd: Long, mineTimes: LongArray, mineVals: FloatArray, mineTag: Int,
        outTimes: LongArray, outVals: FloatArray, outTags: IntArray
    ): Int

    // ---- composed helpers used by the model wrappers ----

    /** withoutClipAnim's per-list filter: the keys NOT carrying [dropTag]. */
    fun filterKeysNotTag(list: List<Keyframe>, dropTag: Int): List<Keyframe> {
        if (list.isEmpty()) return list
        val (times, vals, tags) = packKeys(list)
        val outT = LongArray(list.size)
        val outV = FloatArray(list.size * 6)
        val outG = IntArray(list.size)
        val n = nativeFilterKeysNotTag(times, vals, tags, dropTag, outT, outV, outG)
        return unpackKeys(outT, outV, outG, n)
    }

    /** Tag-conditional time shift of one list (the AnimPresets trim / end-move laws). */
    fun shiftKeyTimesByTag(list: List<Keyframe>, shiftTagged: Boolean, tag: Int, delta: Long): List<Keyframe> {
        if (list.isEmpty()) return list
        val (times, vals, tags) = packKeys(list)
        val outT = LongArray(list.size)
        nativeShiftKeyTimesByTag(times, tags, shiftTagged, tag, delta, outT)
        return list.mapIndexed { i, k -> if (outT[i] == k.timeMs) k else k.copy(timeMs = outT[i]) }
    }

    /** One preset plan, decoded: away entries in catalogue order + the explicit rest replacements. */
    class ClipAnimPlanData(
        val away: List<Triple<AnimProp, Float, Float>>,
        val rest: Map<AnimProp, Pair<Float, Float>>
    )

    fun clipAnimPlan(
        presetIndex: Int, slotTag: Int, dir: Int, base: TimelineClip, canvasW: Float, canvasH: Float
    ): ClipAnimPlanData {
        val slice = floatArrayOf(
            base.scalePercent, base.transformX, base.transformY, base.rotationDeg,
            base.cropWidthPercent, base.cropHeightPercent,
            base.cropHorizontalPercent, base.cropVerticalPercent
        )
        val out = FloatArray(16)
        nativeClipAnimPlan(presetIndex, slotTag, dir, slice, canvasW, canvasH, out)
        val props = AnimProp.values()
        var i = 0
        val awayCount = out[i++].toInt()
        val away = ArrayList<Triple<AnimProp, Float, Float>>(awayCount)
        repeat(awayCount) {
            away += Triple(props[out[i].toInt()], out[i + 1], out[i + 2])
            i += 3
        }
        val restCount = out[i++].toInt()
        val rest = HashMap<AnimProp, Pair<Float, Float>>(restCount)
        repeat(restCount) {
            rest[props[out[i].toInt()]] = out[i + 1] to out[i + 2]
            i += 3
        }
        return ClipAnimPlanData(away, rest)
    }

    /** withClipAnim's per-prop splice: window keys replaced by the two animation keys. */
    fun animWindowSplice(
        list: List<Keyframe>, winStart: Long, winEnd: Long,
        mine: List<Keyframe>, mineTag: Int
    ): List<Keyframe> {
        val (times, vals, tags) = packKeys(list)
        val mineTimes = LongArray(2) { mine[it].timeMs }
        val mineVals = FloatArray(12)
        for (k in 0..1) {
            val key = mine[k]
            val b = k * 6
            mineVals[b] = key.a; mineVals[b + 1] = key.b
            mineVals[b + 2] = key.easing.x1; mineVals[b + 3] = key.easing.y1
            mineVals[b + 4] = key.easing.x2; mineVals[b + 5] = key.easing.y2
        }
        val outT = LongArray(list.size + 2)
        val outV = FloatArray((list.size + 2) * 6)
        val outG = IntArray(list.size + 2)
        val n = nativeAnimWindowSplice(
            times, vals, tags, winStart, winEnd, mineTimes, mineVals, mineTag, outT, outV, outG
        )
        return unpackKeys(outT, outV, outG, n)
    }
}
