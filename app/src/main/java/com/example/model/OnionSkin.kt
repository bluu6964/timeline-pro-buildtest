package com.example.model

import com.example.engine.ValueLawsNative

/**
 * ONION SKIN (build 190): while the editor is paused or scrubbing, the canvas also
 * shows ghost pictures of the scene at neighbouring frame times — the frame(s)
 * before the playhead tinted warm red, the frame(s) after it tinted green, each
 * step further out fainter than the one before — so animation spacing can be
 * judged without moving the playhead. The ghosts are the renderer's OWN frames
 * at those times (the same evaluation the playhead frame gets), colorized and
 * composited under the real frame; they exist only in the preview and can never
 * reach an export.
 *
 * This file is the pure plan: given the playhead, the project frame rate and
 * duration, how many ghosts per side and the base opacity, it decides WHICH
 * times are drawn, in which order, at what alpha and with which tint hue.
 * The engine (NativePreviewEngine) renders and composites exactly this list.
 */
data class OnionGhost(
    /** Project time of the ghost frame, ms — always inside [0, duration]. */
    val timeMs: Long,
    /** -1 = a frame BEFORE the playhead, +1 = a frame AFTER it. */
    val side: Int,
    /** 1 = the nearest ghost on its side. */
    val step: Int,
    /** Compositing alpha (0..1): the base opacity with the per-step falloff applied. */
    val alpha: Float,
    /** Tint hue (degrees) the ghost picture is colorized with: [OnionSkin.PREV_HUE_DEG] before, [OnionSkin.NEXT_HUE_DEG] after. */
    val hueDeg: Float
)

/** The editor's onion-skin settings: view state only — never written to the project file. */
data class OnionSkinSettings(
    val enabled: Boolean = false,
    /** Ghost frames per side, 1..[OnionSkin.MAX_PER_SIDE]. */
    val countPerSide: Int = OnionSkin.DEFAULT_COUNT,
    /** Alpha of the NEAREST ghost on each side; farther ghosts fade from it. */
    val opacity: Float = OnionSkin.DEFAULT_OPACITY
)

object OnionSkin {

    const val MAX_PER_SIDE = 3
    const val DEFAULT_COUNT = 2
    const val DEFAULT_OPACITY = 0.35f
    const val MIN_OPACITY = 0.05f
    const val MAX_OPACITY = 0.8f

    /** Previous frames read warm red — the 2D-animation onion-skin convention. */
    const val PREV_HUE_DEG = 0f

    /** Next frames read green — the convention's cool side. */
    const val NEXT_HUE_DEG = 120f

    /** The settings with every value inside its range (the settings UI writes through this). */
    fun normalize(s: OnionSkinSettings): OnionSkinSettings {
        // Native since build 247 (value_laws.cpp): the count clamp and the
        // NaN-opacity fallback (build 198) run there.
        val out = ValueLawsNative.nativeOnionNormalize(s.countPerSide, s.opacity)
        return s.copy(countPerSide = out[0].toInt(), opacity = out[1])
    }

    /** One frame step in ms at [fps]: [step] frames from the playhead. */
    fun frameStepMs(fps: Int, step: Int): Long = ValueLawsNative.nativeOnionFrameStepMs(fps, step)

    /**
     * The ghosts to draw for a playhead at [playheadMs], in DRAW ORDER: farthest
     * first, so a nearer ghost composites over the farther ones (at the same
     * distance the previous frame is drawn first and the next frame's ghost
     * lands on top of it). Ghosts that would fall outside [0, durationMs] are
     * dropped, never clamped onto the boundary — a ghost at the playhead's own
     * time would draw the real frame a second time. The alpha falls off
     * linearly: step k of [countPerSide] gets baseOpacity · (count − k + 1) / count,
     * so the nearest ghost carries the full base opacity. No ghosts at all when
     * the count is 0, the rate or duration is unusable, or the opacity is 0.
     */
    fun plan(
        playheadMs: Long,
        fps: Int,
        durationMs: Long,
        countPerSide: Int,
        baseOpacity: Float
    ): List<OnionGhost> {
        // Native since build 247 (value_laws.cpp) — the whole plan law,
        // including the draw-order sort, runs there.
        val blob = ValueLawsNative.decodeOnionPlan(
            ValueLawsNative.nativeOnionPlan(playheadMs, fps, durationMs, countPerSide, baseOpacity)
        )
        return List(blob.times.size) { i ->
            OnionGhost(blob.times[i], blob.sides[i], blob.steps[i], blob.alphas[i], blob.hues[i])
        }
    }
}
