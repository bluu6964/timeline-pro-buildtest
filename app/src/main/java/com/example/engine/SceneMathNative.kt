package com.example.engine

/** JNI bridge to native scene math suite (scene_math.cpp). */
internal object SceneMathNative {
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

    const val ACTION_ALIGN_LEFT = 0
    const val ACTION_ALIGN_CENTER_H = 1
    const val ACTION_ALIGN_RIGHT = 2
    const val ACTION_ALIGN_TOP = 3
    const val ACTION_ALIGN_CENTER_V = 4
    const val ACTION_ALIGN_BOTTOM = 5
    const val ACTION_DISTRIBUTE_H = 6
    const val ACTION_DISTRIBUTE_V = 7
    const val ACTION_CANVAS_CENTER_H = 8
    const val ACTION_CANVAS_CENTER_V = 9

    @JvmStatic external fun nativeCanvasSnap(
        cx: Float,
        cy: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        canvasW: Float,
        canvasH: Float,
        othersFlat: FloatArray,
        thresholdPx: Float,
        out6: FloatArray
    )

    @JvmStatic external fun nativeSpatialDeltas(
        rectsFlat: FloatArray,
        action: Int
    ): FloatArray

    @JvmStatic external fun nativeSpatialCanvasDeltas(
        rectsFlat: FloatArray,
        frameW: Float,
        frameH: Float,
        action: Int
    ): FloatArray

    @JvmStatic external fun nativeApplyParentToFp(
        fp: FloatArray,
        refX: Float,
        refY: Float,
        refScale: Float,
        refRot: Float,
        nx: Float,
        ny: Float,
        nowScale: Float,
        nowRot: Float,
        k: Float,
        lockScale: Boolean,
        lockRotate: Boolean,
        hiddenByGroup: Boolean
    )

    @JvmStatic external fun nativeFlattenPathSegments(
        ops: IntArray,
        offsets: IntArray,
        counts: IntArray,
        pts: FloatArray,
        stepsPerCurve: Int
    ): FloatArray

    @JvmStatic external fun nativeLayoutTextOnPath(
        xy: FloatArray,
        cum: FloatArray,
        total: Float,
        advances: FloatArray,
        startFrac: Float
    ): FloatArray

    @JvmStatic external fun nativeFlattenMaskControlPoints(
        ctrlXy: FloatArray,
        smooth: Boolean,
        maxPoints: Int
    ): FloatArray

    @JvmStatic external fun nativePointInPolygon(
        px: Float,
        py: Float,
        polyXy: FloatArray
    ): Boolean
}
