package com.example.engine

/** JNI bridge to native pyramidal LK circle motion tracker (lk_tracker.cpp). */
internal object LkTrackerNative {
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

    @JvmStatic external fun nativeCreate(maxPoints: Int): Long
    @JvmStatic external fun nativeDestroy(ptr: Long)
    @JvmStatic external fun nativeStart(
        ptr: Long,
        gray: FloatArray,
        w: Int,
        h: Int,
        cx: Float,
        cy: Float,
        radius: Float,
        stateOut: FloatArray
    ): Boolean

    @JvmStatic external fun nativeStep(
        ptr: Long,
        gray: FloatArray,
        w: Int,
        h: Int,
        stateOut: FloatArray
    ): Boolean

    @JvmStatic external fun nativePoints(ptr: Long, out: FloatArray): Int
}
