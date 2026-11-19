package com.example.engine

/** JNI bridge to native audio analysis, DSP, waveform, noise profile, and PCM reversal (audio_analysis.cpp). */
internal object AudioAnalysisNative {
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

    @JvmStatic external fun nativeDetectBeats(
        samples: FloatArray,
        sampleRate: Int,
        bpmOut: DoubleArray
    ): LongArray?

    @JvmStatic external fun nativeDuckPeakEnvelope(
        samples: FloatArray,
        sampleRate: Int,
        stepMs: Long
    ): FloatArray

    @JvmStatic external fun nativeDuckGainsForLevels(
        levels: FloatArray,
        amountDb: Float,
        smoothness: Float,
        stepMs: Long
    ): FloatArray

    @JvmStatic external fun nativeDuckBuildCurve(
        triggerStartMs: Long,
        triggerLevels: FloatArray,
        duckedStartMs: Long,
        duckedDurationMs: Long,
        amountDb: Float,
        smoothness: Float,
        timesOut: LongArray,
        gainsOut: FloatArray
    ): Int

    @JvmStatic external fun nativeNoiseFrameMagnitudes(
        frame: FloatArray,
        outBins: FloatArray
    )

    @JvmStatic external fun nativeNoiseAccCreate(): Long
    @JvmStatic external fun nativeNoiseAccDestroy(handle: Long)
    @JvmStatic external fun nativeNoiseAccPushMono(handle: Long, samples: FloatArray)
    @JvmStatic external fun nativeNoiseAccFinish(handle: Long, outProfile: FloatArray): Int

    @JvmStatic external fun nativeNoiseProcessMono(
        input: FloatArray,
        profile: FloatArray,
        strength: Float
    ): FloatArray

    @JvmStatic external fun nativeComputeNoiseProfileFd(
        fd: Int,
        length: Long,
        startMs: Long,
        spanMs: Long
    ): FloatArray?

    @JvmStatic external fun nativeComputeWaveformFd(
        fd: Int,
        length: Long,
        durationMs: Long,
        bucketMs: Int
    ): FloatArray?

    @JvmStatic external fun nativeReversePcm16ToWav(
        pcmPath: String,
        wavPath: String,
        channels: Int,
        sampleRate: Int,
        frames: Long
    ): Boolean
}
