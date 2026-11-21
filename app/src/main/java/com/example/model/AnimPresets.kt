package com.example.model

import com.example.engine.ClipAlgebraNative
import kotlinx.serialization.Serializable

/**
 * One-tap In / Out animations.
 *
 * An animation is NOT a separate effect: tapping one writes ordinary keyframes (with an easing curve)
 * into the layer, exactly like the keyframes the user would set by hand in Transform / Crop / Blend.
 * That is why every animation can be customised afterwards: move a diamond, change the easing curve,
 * add a keyframe in between - it is all the normal keyframe system.
 *
 * The keyframes of an animation carry a tag ([Keyframe.tag]) so the app knows which ones belong to
 * the In and which to the Out animation: tapping another animation replaces exactly those keyframes
 * and nothing the user made by hand outside the animation window.
 *
 * Animated properties: position, scale, rotation, opacity and crop, from any of the four directions.
 *
 * Since migration stage 1 (build 244) the preset PLANS — the "away" value of every property a
 * preset touches, plus the wipe's rest replacements — are computed in C++ (clip_algebra) by
 * catalogue index: the order of [AnimPresets.ALL] IS the native plan index. The rebuild
 * composition (strip the slot's old keys, evaluate the base frame, splice the plan's keys into
 * the window) stays here, built from the same native list ops as the rest of the algebra.
 */

/** [Keyframe.tag] values. 0 = a keyframe the user made. */
const val ANIM_TAG_USER = 0
const val ANIM_TAG_IN = 1
const val ANIM_TAG_OUT = 2

/** Shortest animation, and the gap that is kept between the In and the Out animation. */
const val ANIM_MIN_MS = 100L
const val ANIM_GAP_MS = 50L
const val ANIM_DEFAULT_MS = 500L

enum class AnimSlot(val tag: Int, val label: String) {
    IN(ANIM_TAG_IN, "In"),
    OUT(ANIM_TAG_OUT, "Out")
}

/**
 * Direction. In: where the layer comes FROM. Out: where it goes TO.
 * For spins it is the sense of rotation (Left / Up = counter-clockwise, Right / Down = clockwise).
 */
@Serializable
enum class AnimDir(val label: String, val arrow: String) {
    LEFT("Left", "←"),
    RIGHT("Right", "→"),
    UP("Up", "↑"),
    DOWN("Down", "↓")
}

/** What the layer currently has applied in a slot (the keyframes are the truth, this is for the UI). */
@Serializable
data class ClipAnim(
    val preset: String,
    val dir: AnimDir,
    val durationMs: Long,
    val easing: Easing
)

class AnimPreset(
    val id: String,
    val label: String,
    /** Directions the user can choose from; empty = the animation has no direction. */
    val dirs: List<AnimDir>,
    val defaultMs: Long,
    val easingIn: Easing,
    val easingOut: Easing
) {
    fun defaultEasing(slot: AnimSlot): Easing = if (slot == AnimSlot.IN) easingIn else easingOut
    fun defaultDir(): AnimDir = dirs.firstOrNull() ?: AnimDir.LEFT
}

object AnimPresets {

    private fun ease(name: String): Easing = Easing.PRESETS.first { it.first == name }.second

    private val ALL_DIRS = listOf(AnimDir.LEFT, AnimDir.RIGHT, AnimDir.UP, AnimDir.DOWN)
    private val SPIN_DIRS = listOf(AnimDir.RIGHT, AnimDir.LEFT)

    // The catalogue order is the native plan index in clip_algebra — append, never reorder.
    val ALL: List<AnimPreset> = listOf(
        AnimPreset("fade", "Fade", emptyList(), 500L, ease("Ease out"), ease("Ease in")),
        AnimPreset("slide", "Slide", ALL_DIRS, 500L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("slide_fade", "Slide Fade", ALL_DIRS, 600L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("bounce", "Bounce", ALL_DIRS, 700L, ease("Back out"), ease("Back in")),
        AnimPreset("zoom", "Zoom", emptyList(), 500L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("zoom_fade", "Zoom Fade", emptyList(), 600L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("zoom_out", "Zoom Out", emptyList(), 600L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("pop", "Pop", emptyList(), 600L, ease("Back out"), ease("Back in")),
        AnimPreset("spin", "Spin", SPIN_DIRS, 700L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("spin_zoom", "Spin Zoom", SPIN_DIRS, 800L, ease("Cubic out"), ease("Cubic in")),
        AnimPreset("wipe", "Wipe", ALL_DIRS, 600L, ease("Cubic out"), ease("Cubic in"))
    )

    fun byId(id: String?): AnimPreset? = ALL.firstOrNull { it.id == id }
}

// =====================================================================================
// Applying / removing
// =====================================================================================

/** Length (ms) of the keyframes of [slot] on the layer, 0 when the slot has none. */
fun TimelineClip.animSpanMs(slot: AnimSlot): Long {
    var total = 0
    for (l in keyframes.values) total += l.size
    if (total == 0) return 0L
    val times = LongArray(total)
    val tags = IntArray(total)
    var o = 0
    for (l in keyframes.values) for (k in l) {
        times[o] = k.timeMs
        tags[o] = k.tag
        o++
    }
    return ClipAlgebraNative.nativeAnimSpan(times, tags, slot.tag)
}

/** Longest the animation of [slot] may be: what is left of the layer after the other slot. */
fun TimelineClip.maxAnimMs(slot: AnimSlot): Long {
    val other = if (slot == AnimSlot.IN) AnimSlot.OUT else AnimSlot.IN
    return ClipAlgebraNative.nativeMaxAnimMs(animSpanMs(other), durationMs)
}

/** Removes the animation of [slot] (its keyframes only; the layer keeps its normal values). */
fun TimelineClip.withoutClipAnim(slot: AnimSlot): TimelineClip {
    val cleaned = keyframes
        .mapValues { (_, l) -> ClipAlgebraNative.filterKeysNotTag(l, slot.tag) }
        .filterValues { it.isNotEmpty() }
    return copy(
        keyframes = cleaned,
        animIn = if (slot == AnimSlot.IN) null else animIn,
        animOut = if (slot == AnimSlot.OUT) null else animOut
    )
}

/**
 * Applies a preset to the layer in one go: the animation of [slot] is replaced by keyframes that
 * make the layer come from (In) / go to (Out) the "away" state over [durationMs]. [easing] shapes the
 * curve (null = the preset's own curve). The canvas size is in preview pixels (Position units).
 */
fun TimelineClip.withClipAnim(
    slot: AnimSlot,
    preset: AnimPreset,
    dir: AnimDir,
    durationMs: Long,
    easing: Easing?,
    canvasW: Float,
    canvasH: Float
): TimelineClip {
    val c0 = withoutClipAnim(slot)
    val room = c0.maxAnimMs(slot)
    if (room < ANIM_MIN_MS) return c0
    val d = durationMs.coerceIn(ANIM_MIN_MS, room)
    val dur = c0.durationMs
    val anchor = if (slot == AnimSlot.IN) d else dur - d
    val base = c0.at(c0.startMs + anchor)
    val curve = easing ?: preset.defaultEasing(slot)
    val presetIndex = AnimPresets.ALL.indexOf(preset)
    check(presetIndex >= 0) {
        "withClipAnim: preset '${preset.id}' is not in the AnimPresets catalogue (native plans are by catalogue index)"
    }
    val plan = ClipAlgebraNative.clipAnimPlan(presetIndex, slot.tag, dir.ordinal, base, canvasW, canvasH)

    val winStart = if (slot == AnimSlot.IN) 0L else dur - d
    val winEnd = if (slot == AnimSlot.IN) d else dur

    var kfs = c0.keyframes
    for ((prop, awayA, awayB) in plan.away) {
        val rest = plan.rest[prop] ?: base.propValues(prop)
        val mine = if (slot == AnimSlot.IN) {
            listOf(
                Keyframe(0L, awayA, awayB, curve, slot.tag),
                Keyframe(d, rest.first, rest.second, Easing.LINEAR, slot.tag)
            )
        } else {
            listOf(
                Keyframe(dur - d, rest.first, rest.second, curve, slot.tag),
                Keyframe(dur, awayA, awayB, Easing.LINEAR, slot.tag)
            )
        }
        // Anything the user keyed inside the animation window is replaced by the animation.
        val next = ClipAlgebraNative.animWindowSplice(kfs[prop].orEmpty(), winStart, winEnd, mine, slot.tag)
        kfs = kfs + (prop to next)
    }
    val applied = ClipAnim(preset.id, dir, d, curve)
    return c0.copy(
        keyframes = kfs,
        animIn = if (slot == AnimSlot.IN) applied else c0.animIn,
        animOut = if (slot == AnimSlot.OUT) applied else c0.animOut
    )
}

/**
 * Layer start trimmed by [d] ms (positive = shorter). The In animation stays at the layer's start,
 * everything else (the Out animation and the user's keyframes) stays where it was in project time.
 */
fun TimelineClip.withTrimmedStartKeyframes(d: Long): TimelineClip {
    if (keyframes.isEmpty() || d == 0L) return this
    return copy(keyframes = keyframes.mapValues { (_, l) ->
        ClipAlgebraNative.shiftKeyTimesByTag(l, shiftTagged = false, tag = ANIM_TAG_IN, delta = -d)
    })
}

/** The layer got [delta] ms longer (shorter when negative) at its end: the Out animation follows the end. */
fun TimelineClip.withEndMoved(delta: Long): TimelineClip {
    if (keyframes.isEmpty() || delta == 0L) return this
    return copy(keyframes = keyframes.mapValues { (_, l) ->
        ClipAlgebraNative.shiftKeyTimesByTag(l, shiftTagged = true, tag = ANIM_TAG_OUT, delta = delta)
    })
}
