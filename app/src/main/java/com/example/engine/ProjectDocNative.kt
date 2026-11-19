package com.example.engine

/**
 * JNI bridge for Step 2A native C++17 project document, atomic file storage,
 * off-heap bounded history ring buffer, lane chain solver, and XML helpers
 * (cpp/engine/project_doc.{h,cpp}, project_doc_jni.cpp).
 */
object ProjectDocNative {

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
    external fun nativeWriteAtomic(targetPath: String, bytes: ByteArray): Boolean

    @JvmStatic
    external fun nativeReadFile(path: String): ByteArray?

    @JvmStatic
    external fun nativeHistoryCreate(capacity: Int): Long

    @JvmStatic
    external fun nativeHistoryDestroy(handle: Long)

    @JvmStatic
    external fun nativeHistoryPush(handle: Long, blob: ByteArray)

    @JvmStatic
    external fun nativeHistoryPop(handle: Long): ByteArray?

    @JvmStatic
    external fun nativeHistorySize(handle: Long): Int

    @JvmStatic
    external fun nativeHistoryTotalBytes(handle: Long): Long

    @JvmStatic
    external fun nativeHistoryClear(handle: Long)

    @JvmStatic
    external fun nativeSolveChainStart(
        siblingStarts: LongArray,
        siblingEnds: LongArray,
        desiredStartMs: Long,
        durationMs: Long
    ): Long

    @JvmStatic
    external fun nativeXmlSanitize(s: String): String?

    @JvmStatic
    external fun nativeXmlEscAttr(s: String): String?

    @JvmStatic
    external fun nativeXmlEscText(s: String): String?

    @JvmStatic
    external fun nativeFormatCorpusFloat(v: Float): String?
}
