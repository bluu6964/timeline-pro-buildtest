package com.example.engine

import com.example.model.Keyframe
import com.example.model.PathSeg

/**
 * JNI bridge for Step 2A native C++17 frame evaluation & parametric geometry
 * (cpp/engine/frame_eval.{h,cpp}, frame_eval_jni.cpp).
 *
 * Build 234: callers use the native path directly (the Kotlin fallbacks were
 * removed); JVM tests load a host-built libam_engine.so from the same sources.
 */
object FrameEvalNative {

    const val REPEAT_MODE_PLAIN = 0
    const val REPEAT_MODE_RADIAL = 1
    const val REPEAT_MODE_SCATTER = 2
    const val REPEAT_MODE_LINE = 3

    val isAvailable: Boolean by lazy {
        try {
            DecoderPool.isAvailable
        } catch (_: Throwable) {
            false
        }
    }

    init {
        // Build 234: the native path is the only path (Kotlin fallbacks removed at
        // Sarowar's order). Touching this bridge loads libam_engine.so via
        // DecoderPool.isAvailable and fails loudly if it is genuinely absent.
        check(DecoderPool.isAvailable) { "libam_engine.so failed to load" }
    }

    @JvmStatic
    external fun nativeEasingApply(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Float

    @JvmStatic
    external fun nativeKeyframeValueAt(
        timesMs: LongArray,
        vals: FloatArray,
        relMs: Long,
        isColor: Boolean,
        out2: FloatArray
    )

    @JvmStatic
    external fun nativeSpeedSourceOffsetMs(
        timesMs: LongArray,
        vals: FloatArray,
        relMs: Long
    ): Long

    @JvmStatic
    external fun nativeSpeedRampTable(
        timesMs: LongArray,
        vals: FloatArray,
        durationMs: Long
    ): FloatArray?

    @JvmStatic
    external fun nativeAmFxTrackValueAt(
        values: FloatArray,
        rel: Float,
        start: Int,
        out1: FloatArray
    ): Boolean

    @JvmStatic
    external fun nativeMotionBlurPos(
        p0x: Float, p0y: Float,
        p1x: Float, p1y: Float, p1Rot: Float,
        canvasHeightPx: Float, tune: Float,
        out2: FloatArray
    ): Boolean

    @JvmStatic
    external fun nativeMotionBlurRadial(
        p0Rot: Float, p0Scale: Float,
        p1Rot: Float, p1Scale: Float,
        tune: Float, useScale: Boolean, useAngle: Boolean,
        out2: FloatArray
    ): Boolean

    @JvmStatic
    external fun nativeMotionBlur4(
        p0x: Float, p0y: Float, p0Rot: Float, p0Scale: Float,
        p1x: Float, p1y: Float, p1Rot: Float, p1Scale: Float,
        canvasHeightPx: Float,
        usePos: Boolean, useScale: Boolean, useAngle: Boolean,
        out4: FloatArray
    ): Int

    @JvmStatic
    external fun nativeRepeatEval(
        mode: Int,
        fxVals: FloatArray,
        clipX: Float, clipY: Float, clipRot: Float, clipScale: Float, clipOp: Float,
        undoRotDeg: Float, outerScale: Float, outerRotDeg: Float,
        clipStartMs: Long, clipEndMs: Long, timeMs: Long,
        outAlt2: LongArray
    ): FloatArray?

    @JvmStatic
    external fun nativeGroupCopyOffset(
        anchorX: Float, anchorY: Float,
        dx: Float, dy: Float, rotDeg: Float, scaleMul: Float, k: Float,
        out2: FloatArray
    )

    @JvmStatic
    external fun nativeTextAnimValueAt(
        keyTimes: FloatArray,
        keyVals: FloatArray,
        dims: Int,
        keyHasEasing: ByteArray,
        keyEasings: FloatArray,
        stepOnly: Boolean,
        p: Float,
        outVals: FloatArray
    )

    @JvmStatic
    external fun nativeTextProgressVisibleRange(
        count: Int,
        start: Float,
        end: Float,
        out2: IntArray
    )

    @JvmStatic
    external fun nativeCounterSubstitute(
        text: String,
        offset: Double,
        scale: Double
    ): String?

    @JvmStatic
    external fun nativePieArcSweepDegrees(startDeg: Double, endDeg: Double): Double

    @JvmStatic
    external fun nativePieSegments(startDeg: Double, endDeg: Double, radius: Double): FloatArray?

    @JvmStatic
    external fun nativeArcSegments(
        startDeg: Double,
        endDeg: Double,
        radius: Double,
        closed: Boolean
    ): FloatArray?

    @JvmStatic
    external fun nativeCalloutSegments(
        width: Double,
        height: Double,
        tailX: Double,
        tailY: Double,
        cornerRadius: Double,
        tailWidth: Double
    ): FloatArray?

    @JvmStatic
    external fun nativeGradEndpoints(
        gradType: Int,
        cx: Float, cy: Float,
        angleDeg: Float, scale: Float,
        out4: FloatArray
    )

    @JvmStatic
    external fun nativeGradAngleScale(
        gradType: Int,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        out2: FloatArray
    )

    /** Packs a Kotlin [Keyframe] list into `(timesMs, valsStride6)` for native evaluation. */
    fun packKeyframes(keys: List<Keyframe>): Pair<LongArray, FloatArray> {
        val n = keys.size
        val times = LongArray(n)
        val vals = FloatArray(n * 6)
        for (i in 0 until n) {
            val k = keys[i]
            times[i] = k.timeMs
            val b = i * 6
            vals[b] = k.a
            vals[b + 1] = k.b
            vals[b + 2] = k.easing.x1
            vals[b + 3] = k.easing.y1
            vals[b + 4] = k.easing.x2
            vals[b + 5] = k.easing.y2
        }
        return times to vals
    }

    /** Unpacks a packed C++ segment stream `[op, ptCount, p0, p1, ...]` into [PathSeg]s. */
    fun unpackSegments(packed: FloatArray): List<PathSeg> {
        if (packed.isEmpty()) return emptyList()
        val out = ArrayList<PathSeg>(packed.size / 4 + 2)
        var i = 0
        while (i + 1 < packed.size) {
            val op = packed[i].toInt()
            val cnt = packed[i + 1].toInt().coerceAtLeast(0)
            i += 2
            if (i + cnt > packed.size) break
            val pts = if (cnt == 0) emptyList() else List(cnt) { j -> packed[i + j] }
            out += PathSeg(op, pts)
            i += cnt
        }
        return out
    }
}
