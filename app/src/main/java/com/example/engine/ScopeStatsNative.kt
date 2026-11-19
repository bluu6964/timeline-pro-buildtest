package com.example.engine

/** JNI bridge to native BT.601 video scope statistics (scope_stats.cpp). */
internal object ScopeStatsNative {
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

    @JvmStatic external fun nativeDownscale(
        argb: IntArray,
        width: Int,
        height: Int,
        outSize: IntArray
    ): IntArray?

    @JvmStatic external fun nativeAnalyze(
        argb: IntArray,
        width: Int,
        height: Int,
        metaOut: IntArray,
        histR: IntArray,
        histG: IntArray,
        histB: IntArray,
        histY: IntArray,
        vec: IntArray
    ): IntArray?
}
