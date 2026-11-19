package com.example.engine

/** JNI bridge to native Canvas touch hit-testing & drag/tap gesture FSM (canvas_gesture.cpp). */
internal object CanvasGestureNative {
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

    const val KIND_INVALID = -1
    const val KIND_TEXT = 0
    const val KIND_SHAPE_FAMILY = 1
    const val KIND_BLOCKER = 2

    const val EV_SELECT = 1
    const val EV_DOUBLE_TAP = 2
    const val EV_MOVE = 3
    const val EV_COMMIT = 4
    const val EV_ARM_SINGLE_TAP = 5
    const val EV_CANCEL_SINGLE_TAP = 6
    const val EV_CONSUME = 7

    const val ITEM_FLOAT_STRIDE = 17
    const val EVENT_STRIDE = 5

    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeSetConfig(
        handle: Long,
        touchSlopPx: Float,
        doubleTapSlopPx: Float,
        doubleTapTimeoutMs: Long,
        longPressTimeoutMs: Long
    )
    @JvmStatic external fun nativeOnDown(
        handle: Long,
        px: Float,
        py: Float,
        uptimeMs: Long,
        frameW: Float,
        frameH: Float,
        pendingCurrentIndex: Int,
        kinds: IntArray,
        geomFlat: FloatArray
    ): Boolean
    @JvmStatic external fun nativeOnMove(handle: Long, dx: Float, dy: Float): DoubleArray
    @JvmStatic external fun nativeOnUp(handle: Long, uptimeMs: Long, isRelease: Boolean): DoubleArray
    @JvmStatic external fun nativeOnCancel(handle: Long): DoubleArray
    @JvmStatic external fun nativeOnSingleTapTimeout(handle: Long, token: Long): DoubleArray
}
