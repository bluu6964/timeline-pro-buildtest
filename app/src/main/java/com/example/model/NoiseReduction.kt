package com.example.model

import com.example.engine.AudioAnalysisNative
import com.example.engine.ValueLawsNative

/**
 * Audio noise reduction (build 195): the pure spectral-gating law. A layer's
 * steady background noise (hiss, hum, fan noise) is estimated from the layer's
 * OWN sound and gated down per frequency bin, while the wanted signal —
 * whose bins sit well above that floor — passes near-unity.
 *
 * THE LAW, end to end:
 *  1. NOISE PROFILE. The clip's played window is decoded at the mixer's own
 *     48 kHz (engine NoiseProfileDecode, build 193's window discipline) and
 *     cut into STFT frames ([FFT_SIZE] Hann-windowed, [HOP] apart). The
 *     profile of a frequency bin is the [PROFILE_PERCENTILE] percentile of
 *     its magnitudes across the whole window — the level the bin returns to
 *     in its quietest decile IS the steady noise under it (a bin holding
 *     only noise reads its own floor; a bin holding signal reads the floor
 *     under the signal's quietest moments, the classic minimum-statistics
 *     trade-off). [noiseProfile] is the exact law over held frames;
 *     [ProfileAccumulator] is its streaming form (a per-bin log histogram —
 *     a 10-minute window is ~56k frames and cannot be held whole), agreeing
 *     with the exact law within one histogram bucket (~2.5%).
 *  2. GATE. Each frame's per-bin gain is power-domain spectral subtraction
 *     against the profile: g = sqrt(max(1 − (over·n/m)², [FLOOR_GAIN]²)),
 *     where m is the bin's magnitude, n its profile value and
 *     over = [MAX_OVER] × strength. A bin at its noise floor (m ≈ n) is
 *     driven to the floor gain at full strength; a bin well above it
 *     (m >> n) keeps g ≈ 1. Strength scales `over` linearly, so strength 0
 *     is an EXACT passthrough (every gain 1) — as is the effect being off.
 *     The floor gain (0.05 ≈ −26 dB) keeps a whisper of the noise bed: pure
 *     zero gains chop the residual into "musical noise".
 *  3. SMOOTHING. Raw per-bin gains jump around with the noise's own
 *     fluctuations, which is itself audible. Gains are therefore smoothed
 *     across frequency (a 3-tap [0.25, 0.5, 0.25]) and across time (a
 *     one-pole follower per bin: [ATTACK_MS] when the gain falls,
 *     [RELEASE_MS] when it recovers) — the same attack/release shape the
 *     ducking law uses, at audio-frame rate. The first frame's gains are
 *     taken directly, so a clip does not leak one loud frame of noise at
 *     its start.
 *  4. RECONSTRUCTION. Gated spectra are inverse-transformed and overlap-
 *     added with the same Hann window; the output is divided by the
 *     accumulated squared-window sum, so edges and stream boundaries stay
 *     exact and the output length equals the input length sample-for-sample.
 *
 * The C++ gate (engine/noise_gate.h, a FrameSource in the shared mixer's
 * per-clip source chain) implements this law 1:1 — same framing, window,
 * gains, smoothing and reconstruction; in stereo the gain of a bin comes
 * from the louder channel's magnitude and applies to both, so the stereo
 * image never wobbles. Because preview and export build their sources
 * through the same two factory chains into the ONE AudioMixer, both hear
 * the gate identically. The profile is computed from the clip's file and
 * played window only — never from its volume or fades (the gate sits
 * inside the source, ahead of the mixer's gain stage).
 *
 * The value laws live natively in value_laws.cpp since build 247 (migration
 * stage 4); the frame magnitudes and the mono reference processor were
 * already native (audio_analysis). Harness-pinned (NoiseReductionTest
 * drives synthetic tone + noise through every clause).
 */
object NoiseReduction {

    /** The shared mixer's rate (cpp kAudioRate): all framing is stated at 48 kHz. */
    const val SAMPLE_RATE = 48000

    /** STFT frame length (2048 ≈ 42.7 ms: fine frequency resolution for a steady floor). */
    const val FFT_SIZE = 2048

    /** Frame hop (512 = 75% overlap, the Hann overlap-add grid). */
    const val HOP = 512

    /** Magnitude bins of one frame (0..Nyquist). */
    const val BINS = FFT_SIZE / 2 + 1

    /** The profile takes this percentile of each bin's magnitudes over the window. */
    const val PROFILE_PERCENTILE = 0.10

    /** The lowest gain any bin is driven to (0.05 ≈ −26 dB): a whisper of bed, no musical noise. */
    const val FLOOR_GAIN = 0.05f

    /** Over-subtraction factor at strength 1 (strength scales it linearly to 0). */
    const val MAX_OVER = 4f

    /** Gain-fall time constant of the temporal smoother (ms). */
    const val ATTACK_MS = 10.0

    /** Gain-recovery time constant of the temporal smoother (ms). */
    const val RELEASE_MS = 80.0

    /**
     * The over-subtraction factor of a strength 0..1. Build 198: a NaN strength
     * (coerceIn passes NaN through) reads as 0 — no processing — never as a
     * NaN factor that would turn every gate gain NaN downstream.
     */
    fun overSubtraction(strength: Float): Float = ValueLawsNative.nativeNoiseOverSubtraction(strength)

    /**
     * One bin's raw (unsmoothed) gate gain for magnitude [magnitude] against
     * profile value [noise] at [strength]: 1 when strength <= 0 (exact
     * passthrough) or the bin towers over its floor; [FLOOR_GAIN] when the
     * bin sits at (or under) over × its floor. A silent bin ([magnitude] 0)
     * takes the floor gain — its phase is zero, so it contributes nothing.
     */
    fun rawGain(magnitude: Float, noise: Float, strength: Float): Float =
        ValueLawsNative.nativeNoiseRawGain(magnitude, noise, strength)

    /** The 3-tap frequency smoothing of one frame's raw gains (edges clamp). */
    fun smoothAcrossFrequency(raw: FloatArray): FloatArray =
        ValueLawsNative.nativeNoiseSmoothFreq(raw)

    /** One-pole coefficient per STFT frame for a time constant of [tauMs] ms. */
    fun stepCoef(tauMs: Double): Double = ValueLawsNative.nativeNoiseStepCoef(tauMs)

    /** The temporal smoother's attack coefficient (gain falling). */
    val attackCoef: Double = stepCoef(ATTACK_MS)

    /** The temporal smoother's release coefficient (gain recovering). */
    val releaseCoef: Double = stepCoef(RELEASE_MS)

    /**
     * The [p] percentile of [values] (they are copied + sorted; the element
     * at floor(p × (n − 1))). Empty input -> 0.
     */
    fun percentile(values: FloatArray, p: Double): Float =
        ValueLawsNative.nativeNoisePercentile(values, p)

    /**
     * The exact noise profile of a set of magnitude frames (one [BINS]
     * array per STFT frame): per bin, the [PROFILE_PERCENTILE] percentile
     * over the frames. No frames -> all zeros (silence gates to silence).
     */
    fun noiseProfile(frames: List<FloatArray>): FloatArray {
        if (frames.isEmpty()) return FloatArray(BINS)
        val flat = FloatArray(frames.size * BINS)
        for (f in frames.indices) frames[f].copyInto(flat, f * BINS)
        return ValueLawsNative.nativeNoiseProfile(flat, frames.size, BINS)
    }

    /**
     * The streaming form of [noiseProfile]: each bin keeps a log-spaced
     * histogram of the magnitudes it has seen (a whole played window's
     * frames cannot be held in memory), and [profile] reads the
     * [PROFILE_PERCENTILE] percentile off it — the bucket containing the
     * percentile rank, valued at the bucket's geometric centre. Agrees with
     * the exact law within one bucket (~2.5% of magnitude).
     */
    class ProfileAccumulator {
        // Flat [bin][bucket] counts — the native accumulator's own layout.
        private val counts = IntArray(BINS * BUCKETS)
        private var frames = 0

        /** How many magnitude frames have been added. */
        val frameCount: Int get() = frames

        fun add(magnitudes: FloatArray) {
            require(magnitudes.size == BINS) { "a magnitude frame has $BINS bins" }
            ValueLawsNative.nativeNoiseAccumAdd(counts, magnitudes)
            frames++
        }

        /** The profile of everything added so far (all zeros when nothing was). */
        fun profile(): FloatArray =
            ValueLawsNative.nativeNoiseAccumProfile(counts, frames, BINS)

        companion object {
            /** Histogram resolution: 256 log buckets over [MIN_MAG], [MAX_MAG]. */
            const val BUCKETS = 256
            const val MIN_MAG = 1e-5
            const val MAX_MAG = 2.0

            /** The bucket a magnitude falls into (<= 0 clamps into bucket 0). */
            fun bucketOf(magnitude: Float): Int = ValueLawsNative.nativeNoiseBucketOf(magnitude)

            /** The geometric centre of bucket [b] — the value a percentile read reports. */
            fun bucketValue(b: Int): Float = ValueLawsNative.nativeNoiseBucketValue(b)
        }
    }

    /** The periodic Hann window of the STFT (the TimeStretcher's window law). */
    fun hann(i: Int): Float = ValueLawsNative.nativeNoiseHann(i)

    /** [hann] over the whole frame, computed once (the profile route runs tens of thousands of frames). */
    val window: FloatArray by lazy { ValueLawsNative.nativeNoiseWindow() }

    /**
     * The magnitude spectrum ([BINS] values) of one mono frame of exactly
     * [FFT_SIZE] samples, Hann-windowed. The profile route's per-frame step.
     */
    fun frameMagnitudes(frame: FloatArray): FloatArray {
        require(frame.size == FFT_SIZE) { "a frame has $FFT_SIZE samples" }
        val out = FloatArray(BINS)
        AudioAnalysisNative.nativeNoiseFrameMagnitudes(frame, out)
        return out
    }

    /**
     * The whole-buffer mono reference processor: the gate law applied to a
     * complete signal, framed exactly as the streaming C++ gate frames its
     * stream (first frame at sample 0, hop grid, zero-padded tail), so the
     * harness pins the very numbers the phone produces. Output length ==
     * input length. Strength <= 0 or a malformed profile -> an exact copy
     * (passthrough).
     */
    class Processor(private val profile: FloatArray, private val strength: Float) {
        fun process(input: FloatArray): FloatArray {
            if (input.isEmpty()) return FloatArray(0)
            if (!(strength > 0f) || profile.size != BINS) return input.copyOf()   // NaN strength = passthrough (build 198)
            return AudioAnalysisNative.nativeNoiseProcessMono(input, profile, strength)
        }
    }
}
