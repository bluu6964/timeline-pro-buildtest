package com.example.model

import com.example.engine.AudioAnalysisNative
import com.example.engine.ValueLawsNative

/**
 * Audio ducking (build 194): the pure sidechain law. One layer (the trigger —
 * typically a voice) automatically lowers another layer's volume (typically
 * music) while the trigger is loud, and lets it back up in the gaps.
 *
 * THE LAW, end to end:
 *  1. The trigger's sound is decoded exactly as it plays (engine
 *     AudioBeatDecode over the trigger's played window, build 193) and cut
 *     into [STEP_MS] steps; [peakEnvelope] keeps the peak |sample| per step —
 *     the trigger's amplitude envelope in SOURCE time.
 *  2. [timelineLevels] puts that envelope on the trigger's TIMELINE grid:
 *     each timeline step reads the source step the layer's own time law
 *     ([sourceOffsetMs] — trim / speed / speed-ramp, the build-193 inversion
 *     law) places at the step's centre, scaled by how loud the trigger is
 *     actually heard there (its mix volume x its fades, supplied by the
 *     caller). Envelope time is therefore always TIMELINE time, never raw
 *     file time.
 *  3. [targetGain] turns a level into the gain the ducked layer should sit
 *     at: 1 at or below [THRESHOLD], falling linearly (in level) to the
 *     amount's floor at full scale — the drop is proportional to how far
 *     over the threshold the trigger is, like a gentle compressor.
 *  4. [gainsForLevels] smooths the targets with a one-pole attack / release
 *     follower (attack = falling toward a lower gain, release = recovering):
 *     the per-step gain change is bounded by construction
 *     (|dg| <= (1 - floor) x the step coefficient), so there are no clicks
 *     or zipper noise.
 *  5. [curve] samples the smoothed gains over the DUCKED layer's span into
 *     breakpoints (the layer's own clock); the shared C++ mixer multiplies
 *     them into its per-sample gain next to volume and fades
 *     (volume x fade x duck), interpolating linearly between breakpoints —
 *     [gainAt] is that interpolation contract, mirrored 1:1 in
 *     audio_mixer.cpp. Because preview and export share the one mixer, both
 *     duck identically.
 *
 * The curve is computed LIVE from the trigger's current placement whenever
 * either layer changes (the preview caches by the layers' exact fields; the
 * exporter computes during setup) — nothing is baked into keyframes, and
 * this app has no volume keyframes to bake into: ducking multiplies the
 * layer's constant volume and fades without ever replacing them. A dangling
 * trigger (the layer was deleted), a trigger that makes no sound in the mix,
 * a silent trigger, or a 0 dB amount all yield NO curve (null), never a
 * stuck-low volume.
 *
 * The value laws live natively in value_laws.cpp since build 247 (migration
 * stage 4); [peakEnvelope], [gainsForLevels] and the curve builder were
 * already native (audio_analysis). Harness-pinned (DuckingTest drives
 * synthetic envelopes through every clause above).
 */
object Ducking {

    /** The envelope / gain control rate: one step per 10 ms of timeline. */
    const val STEP_MS = 10L

    /**
     * Trigger peak level at or below which nothing ducks (about -34 dBFS):
     * room tone and quiet breaths do not pump the music.
     */
    const val THRESHOLD = 0.02f

    /** The most the Amount slider offers (a 36 dB drop is effectively mute for music under voice). */
    const val MAX_AMOUNT_DB = 36f

    /** Gains this close to 1 count as "not ducking" (curve compression + the no-curve rule). */
    const val GAIN_EPS = 0.001f

    /** One breakpoint of a duck curve, on the ducked layer's own clock (ms from its start). */
    data class Point(val relMs: Long, val gain: Float)

    /**
     * The gain a full-scale trigger settles at for [amountDb] dB of drop (1 when amount <= 0).
     * Build 198: a NaN amount reads as NO ducking — `amountDb <= 0f` is false for NaN and
     * coerceAtMost passes NaN through, so an unsanitised NaN used to become a NaN floor
     * and from there a curve of NaN gains for the shared mixer.
     */
    fun floorGain(amountDb: Float): Float = ValueLawsNative.nativeDuckFloorGain(amountDb)

    /** Attack time constant (ms) for a smoothness 0..1: 2 ms (snappy) .. 50 ms (soft), exponential. */
    fun attackMs(smoothness: Float): Double = ValueLawsNative.nativeDuckAttackMs(smoothness)

    /** Release time constant (ms) for a smoothness 0..1: 60 ms (quick) .. 1000 ms (slow), exponential. */
    fun releaseMs(smoothness: Float): Double = ValueLawsNative.nativeDuckReleaseMs(smoothness)

    /**
     * The peak |sample| of mono PCM per [stepMs] step (the last, partial step
     * counts). Empty input -> empty envelope.
     */
    fun peakEnvelope(samples: FloatArray, sampleRate: Int, stepMs: Long = STEP_MS): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0) return FloatArray(0)
        return AudioAnalysisNative.nativeDuckPeakEnvelope(samples, sampleRate, stepMs)
    }

    /**
     * The ducked layer's target gain under a trigger [level] (a peak, >= 0):
     * 1 at or below [THRESHOLD], the floor at full scale (level >= 1),
     * linear in level between. [floor] is [floorGain] of the amount.
     */
    fun targetGain(level: Float, floor: Float): Float =
        ValueLawsNative.nativeDuckTargetGain(level, floor)

    /**
     * Smooths the per-step target gains of [levels] with the attack / release
     * follower, starting from gain 1. The result has one gain per input step.
     */
    fun gainsForLevels(
        levels: FloatArray,
        amountDb: Float,
        smoothness: Float,
        stepMs: Long = STEP_MS
    ): FloatArray {
        if (levels.isEmpty()) return FloatArray(0)
        return AudioAnalysisNative.nativeDuckGainsForLevels(levels, amountDb, smoothness, stepMs)
    }

    /**
     * The trigger's amplitude envelope on its own TIMELINE grid: one level
     * per [STEP_MS] step of the trigger's span, step k covering the trigger-
     * relative [k*STEP, (k+1)*STEP). The source step read for a timeline
     * step is the one the layer's own [sourceOffsetMs] law places at the
     * step's centre, so a trimmed / sped / ramped trigger envelopes exactly
     * where it is heard. [heardGainAt] (trigger-relative ms -> the trigger's
     * mix volume x fade there) scales the file's level into the heard level.
     * Steps past the decoded sound (a decode shorter than the played span)
     * read as silence.
     */
    fun timelineLevels(
        trigger: TimelineClip,
        samples: FloatArray,
        sampleRate: Int,
        heardGainAt: (Long) -> Float
    ): FloatArray {
        if (trigger.durationMs <= 0L) return FloatArray(0)
        val peaks = peakEnvelope(samples, sampleRate)
        val steps = ((trigger.durationMs + STEP_MS - 1L) / STEP_MS).toInt()
        val heard = FloatArray(steps) { k -> heardGainAt(k * STEP_MS) }
        return ValueLawsNative.nativeDuckTimelineLevels(peaks, trigger.durationMs, trigger.speed, heard)
    }

    /**
     * The duck curve of one ducked layer: breakpoints on the layer's own
     * clock covering [0, ducked.durationMs], or null when nothing in the
     * span actually ducks (silent / absent / non-overlapping trigger, or a
     * 0 dB amount) — the caller then passes no curve and the layer plays at
     * its plain volume. [levels] is the trigger's timeline envelope from
     * [timelineLevels] ([triggerStartMs] = where its step 0 sits).
     */
    fun curve(
        triggerStartMs: Long,
        levels: FloatArray,
        ducked: TimelineClip,
        amountDb: Float,
        smoothness: Float
    ): List<Point>? {
        if (!(amountDb > 0f) || ducked.durationMs <= 0L) return null
        val steps = ((ducked.durationMs + STEP_MS - 1L) / STEP_MS).toInt().coerceAtLeast(1)
        val cap = steps + 2
        val times = LongArray(cap)
        val gains = FloatArray(cap)
        val count = AudioAnalysisNative.nativeDuckBuildCurve(
            triggerStartMs, levels, ducked.startMs, ducked.durationMs,
            amountDb, smoothness, times, gains
        )
        if (count <= 0) return null
        return List(count) { i -> Point(times[i], gains[i]) }
    }

    /**
     * The gain of a duck [curve] at [relMs] on the ducked layer's clock:
     * linear between breakpoints, clamped to the end gains outside them, 1
     * for an empty curve. This is the exact contract the C++ mixer
     * implements (audio_mixer.cpp's duckGain) — keep the two in step.
     */
    fun gainAt(curve: List<Point>, relMs: Long): Float =
        ValueLawsNative.nativeDuckGainAt(
            LongArray(curve.size) { curve[it].relMs },
            FloatArray(curve.size) { curve[it].gain },
            relMs,
        )
}
