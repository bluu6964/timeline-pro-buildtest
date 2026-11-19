package com.example.engine

/** JNI bridge to native GIF89a quantizer, LZW compressor, and RGBA-to-ARGB converter (gif_engine.cpp). */
internal object GifNative {
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

    @JvmStatic external fun nativeQuantize(
        rgba: ByteArray,
        width: Int,
        height: Int,
        maxColors: Int,
        bottomUp: Boolean,
        indicesOut: ByteArray
    ): IntArray?

    @JvmStatic external fun nativeLzwCompress(
        indices: ByteArray,
        minCodeSize: Int
    ): ByteArray?

    @JvmStatic external fun nativeRgbaBottomUpToArgb(
        rgba: ByteArray,
        width: Int,
        height: Int
    ): IntArray?
}
