package com.example.model

import com.example.engine.AudioAnalysisNative
import com.example.engine.ModelLogicNative
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Auto beat detection (build 193): finds the beats in a stretch of decoded mono
 * PCM and estimates the tempo, so the editor can drop beat marks on the ruler
 * for an audio layer in one tap (the marks themselves are the app's ordinary
 * beat marks — this file only computes times, it owns no store).
 *
 * THE DETECTOR is the classic spectral-flux onset detector (Bello et al.):
 * the signal is cut into Hann-windowed frames (75% overlap), each frame's
 * magnitude spectrum is measured with a radix-2 FFT, and the novelty of a
 * frame is the sum over bins of the POSITIVE magnitude growth since the
 * previous frame — a drum hit injects broadband energy at once, so its flux
 * spikes, while a steady tone's spectrum stands still and its flux stays at
 * zero. Peaks of the novelty curve are picked against an ADAPTIVE threshold
 * (the local median novelty, so a loud chorus and a quiet verse each get a
 * threshold in their own scale) plus a small absolute floor, and accepted
 * strongest-first under a minimum beat spacing of [MIN_BEAT_SPACING_MS].
 * Frame 0's novelty is its whole spectrum (the signal attacks from silence),
 * which is what puts a beat at the very start of a song that starts on one;
 * a steady tone therefore reports at most that single attack.
 *
 * The times this returns are in the DECODED WINDOW's own milliseconds
 * (sample 0 = 0). Turning a window time into a timeline time is the caller's
 * trim/speed mapping — see [timelineMsForSourceOffsets], the exact inverse
 * of the model's own [sourceOffsetMs] law.
 *
 * Pure Kotlin over plain arrays: no Android types, harness-pinned
 * (BeatDetectTest synthesises click tracks at known tempos).
 */
object BeatDetect {

    /** A detection result: beat times (ms in the decoded window, sorted) + the tempo estimate. */
    data class Result(val beatsMs: List<Long>, val bpm: Double?)

    /** Two beats are never closer than this (250 BPM is already beyond any real grid). */
    const val MIN_BEAT_SPACING_MS = 240L

    /** The slowest / fastest interval that counts towards the tempo estimate. */
    const val MIN_TEMPO_INTERVAL_MS = 240L
    const val MAX_TEMPO_INTERVAL_MS = 2000L

    /**
     * Detects the beats in [samples] (mono, roughly -1..1) at [sampleRate] Hz.
     * Empty / silent / too-short input yields an empty result, never a crash.
     */
    fun detect(samples: FloatArray, sampleRate: Int): Result {
        if (sampleRate <= 0 || samples.size < MIN_FRAME_SAMPLES) return Result(emptyList(), null)
        val bpmOut = DoubleArray(1)
        val nativeBeats = AudioAnalysisNative.nativeDetectBeats(samples, sampleRate, bpmOut)
            ?: return Result(emptyList(), null)
        val bpm = if (bpmOut[0] > 0.0) bpmOut[0] else null
        return Result(nativeBeats.toList(), bpm)
    }

    /**
     * The tempo of a beat list: 60000 / the MEDIAN inter-beat interval
     * (intervals outside [MIN_TEMPO_INTERVAL_MS]..[MAX_TEMPO_INTERVAL_MS] —
     * a doubled or missed beat — do not vote). Null when no interval votes.
     */
    fun estimateBpm(beatsMs: List<Long>): Double? {
        // Native since migration #3 (model_logic); NaN encodes "no estimate".
        val bpm = ModelLogicNative.nativeEstimateBpm(
            beatsMs.toLongArray(), MIN_TEMPO_INTERVAL_MS, MAX_TEMPO_INTERVAL_MS
        )
        return if (bpm.isNaN()) null else bpm
    }

    /**
     * Merges [detected] beat marks into [existing]: a detected mark within
     * [toleranceMs] of an existing one (or of a detected one already taken)
     * is a duplicate and drops out — running detection NEVER removes or moves
     * a mark the user placed by hand. Returns the merged sorted list; how
     * many were added is `result.size - existing.size`.
     */
    fun merge(existing: List<Long>, detected: List<Long>, toleranceMs: Long): List<Long> =
        // Native since migration #3 (model_logic).
        ModelLogicNative.nativeMergeBeats(existing.toLongArray(), detected.toLongArray(), toleranceMs).toList()

    /**
     * The inverse of [TimelineClip.sourceOffsetMs]: given a source offset (ms into the media,
     * what [sourceOffsetMs] returns for a layer-relative time), the layer-relative
     * timeline time (ms from the clip's start) at which the layer plays it.
     * Detected beats come out of the file in source time; this is what lands
     * them on the ruler at the moments the user actually hears them. A clip plays
     * at one constant speed (speed ramps were removed in build 237), so the inverse
     * is a plain division. Offsets outside the played span clamp to the clip's ends.
     */
    fun timelineMsForSourceOffsets(clip: TimelineClip, sourceOffsetsMs: List<Long>): List<Long> {
        if (sourceOffsetsMs.isEmpty()) return emptyList()
        // The mapping law is native since migration #3 (model_logic); the span
        // still comes from the model's own sourceOffsetMs law, and the native
        // side applies the speed coerceAtLeast(0.01) exactly as before.
        val span = clip.sourceOffsetMs(clip.durationMs)
        return ModelLogicNative.nativeTimelineMsForOffsets(
            clip.durationMs, clip.speed.toDouble(), span, sourceOffsetsMs.toLongArray()
        ).toList()
    }


    /** One offset through [timelineMsForSourceOffsets] (tests / one-off callers). */
    fun timelineMsForSourceOffset(clip: TimelineClip, sourceOffsetMs: Long): Long =
        timelineMsForSourceOffsets(clip, listOf(sourceOffsetMs)).first()

    /** The frame whose spectrum is frame [k]'s: its centre, in ms — where an onset inside it is reported. */
    private fun frameTimeMs(k: Int, hop: Int, frameSize: Int, sampleRate: Int): Long =
        ((k * hop + frameSize / 2) * 1000L) / sampleRate

    /** FFT size for a rate: the largest power of two up to ~1/16 s of audio (46 ms at 44.1 kHz). */
    private fun frameSizeFor(sampleRate: Int): Int {
        var size = 512
        while (size < 4096 && size * 2 <= sampleRate / 16) size *= 2
        return size
    }

    private fun median(values: FloatArray, count: Int): Float {
        val copy = values.copyOf(count)
        copy.sort()
        return if (count == 0) 0f else copy[count / 2]
    }

    /** Iterative radix-2 FFT (in place, [re]/[im] same length, a power of two). */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cwr = 1f
                var cwi = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cwr - im[i + k + len / 2] * cwi
                    val vi = re[i + k + len / 2] * cwi + im[i + k + len / 2] * cwr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val nwr = cwr * wr - cwi * wi
                    cwi = cwr * wi + cwi * wr
                    cwr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private const val MIN_FRAME_SAMPLES = 512

    /** Peaks must clear the local median by this factor (plus the floor) to count as onsets. */
    private const val MEDIAN_FACTOR = 1.5f

    /** Novelty floor (novelty is normalised so the loudest frame is 1): below this, dust. */
    private const val ABSOLUTE_FLOOR = 0.04f
}
