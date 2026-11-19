package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.model.NoiseReduction
import com.example.model.TimelineClip
import com.example.model.sourceOffsetMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Audio noise reduction (build 195): turns a layer's noise-reduction settings
 * into the per-bin noise profile the shared C++ mixer's gate consumes — the
 * ONE computation both sound paths use (the preview through
 * [NativeAudioPreview]'s cache, the exporter during setup), so preview and
 * export are cleaned identically by construction.
 *
 * The profile depends ONLY on the layer's own sound: its file and the source
 * window it plays ([TimelineClip.trimStartMs] .. + its played source span,
 * the layer's own time law). It is estimated from a streaming decode at the
 * mixer's 48 kHz ([NoiseProfileDecode]) framed exactly as the gate frames
 * its stream (first frame at window sample 0, [NoiseReduction.HOP] grid,
 * zero-padded tail), accumulated in C++ (build 234: the STFT + log-histogram
 * percentile live in AudioAnalysisNative; there is no Kotlin accumulator
 * path anymore). Volume, fades and strength do NOT feed the profile: the
 * gate sits inside the source chain, ahead of the mixer's gain stage, and
 * sees the same raw decoded level the profile was estimated from.
 *
 * Any failure along the way (no file, no sound, an over-long span, a decode
 * error) yields null = no processing, never a broken mix. A played span
 * over [AudioBeatDecode.MAX_SPAN_MS] is honestly left unprocessed rather
 * than decoded whole (the ducking route's cap).
 */
internal object NoiseProfile {

    /**
     * Everything a profile depends on, for the preview's cache: the layer's
     * file + played window (trim and the span its own time law — base speed
     * + SPEED keys — produces). Any edit to any of these recomputes; the
     * strength is NOT part of it (the same profile serves every strength).
     */
    data class Key(
        val clipId: String,
        val uri: String,
        val trimStartMs: Long,
        val spanMs: Long
    )

    /** The cache key for [clip]'s profile, or null when it cannot be processed. */
    fun keyFor(clip: TimelineClip): Key? {
        // !(x > 0) also refuses a NaN strength (build 198): with no key, no profile is
        // computed and both mix paths pass strength 0 — a NaN can never reach the gate.
        if (!clip.nrEnabled || !(clip.nrStrength > 0f)) return null
        val uri = clip.uri ?: return null
        val spanMs = clip.sourceOffsetMs(clip.durationMs)
        if (spanMs <= 0L) return null
        return Key(clip.id, uri, clip.trimStartMs, spanMs)
    }

    /**
     * Computes [clip]'s noise profile ([NoiseReduction.BINS] magnitudes), or
     * null when it cannot be computed. Suspending: the decode runs on
     * [NoiseProfileDecode]'s own dispatcher.
     */
    suspend fun compute(context: Context, clip: TimelineClip): FloatArray? {
        val key = keyFor(clip) ?: return null
        if (key.spanMs > AudioBeatDecode.MAX_SPAN_MS) return null

        // Primary path: decode + STFT + log-histogram percentile entirely in C++
        // via NDK AudioFileSource.
        val nativeFd = withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(key.uri), "r")?.use { pfd ->
                    AudioAnalysisNative.nativeComputeNoiseProfileFd(
                        pfd.fd, pfd.statSize, key.trimStartMs, key.spanMs
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        coroutineContext.ensureActive()
        if (nativeFd != null && nativeFd.size == NoiseReduction.BINS) return nativeFd

        // Secondary path when ContentResolver cannot expose a seekable fd:
        // decode with MediaCodec and run STFT + histogram accumulation in C++.
        val accHandle = AudioAnalysisNative.nativeNoiseAccCreate()
        if (accHandle == 0L) return null
        try {
            NoiseProfileDecode.decodeWindow(context, key.uri, key.trimStartMs, key.spanMs) { chunk ->
                AudioAnalysisNative.nativeNoiseAccPushMono(accHandle, chunk)
            }
            val out = FloatArray(NoiseReduction.BINS)
            val frames = AudioAnalysisNative.nativeNoiseAccFinish(accHandle, out)
            return if (frames > 0) out else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoiseProfileDecode.Stopped) {
            return null
        } catch (_: Exception) {
            return null
        } finally {
            AudioAnalysisNative.nativeNoiseAccDestroy(accHandle)
        }
    }
}
