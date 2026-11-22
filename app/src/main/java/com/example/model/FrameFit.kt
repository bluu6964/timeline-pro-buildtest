package com.example.model

/**
 * Build 261 — THE frame-fit law: how the project frame (aspect [ratio],
 * width / height) is fitted into a container (the editor's canvas strip,
 * the crop editor's stage, the fullscreen player's window).
 *
 * The C++ preview treats the output surface itself as the canvas
 * (Mat4::canvas over the surface size; layerModel fits each layer into
 * that canvas), so the picture's aspect is correct exactly when the
 * surface the engine draws on has the project frame's aspect. That makes
 * this fit the one place aspect integrity is decided: every surface that
 * shows the project frame takes its size from here — the largest rect of
 * the project's aspect that fits the container, centred (letterbox /
 * pillarbox, NEVER a stretch). Before build 261 the same math was inlined
 * in CanvasArea and again in CropView, untested; the fullscreen player
 * additionally sat in a plain dialog window that was not the real screen
 * (see FullscreenPlayerModal), so its container was whatever the dialog
 * system handed it.
 *
 * Pure floats, unit-agnostic (dp or px — the caller picks one unit and
 * stays in it). Degenerate inputs (non-positive container or ratio)
 * yield an empty rect at the origin rather than a NaN / negative size.
 */
object FrameFit {

    /** A fitted frame rect inside its container, in the caller's unit. */
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * The largest rect of aspect [ratio] (width / height) that fits
     * inside ([containerW] × [containerH]), centred. The rect always
     * lies inside the container, preserves the ratio exactly, and
     * touches at least one pair of opposite container edges (both pairs
     * when the aspects match).
     */
    fun fit(containerW: Float, containerH: Float, ratio: Float): Rect {
        if (containerW <= 0f || containerH <= 0f || ratio <= 0f) return Rect(0f, 0f, 0f, 0f)
        return if (containerW / containerH > ratio) {
            // Container is wider than the frame: height-bound (pillarbox).
            val w = containerH * ratio
            Rect((containerW - w) / 2f, 0f, w, containerH)
        } else {
            // Container is taller than (or exactly) the frame: width-bound (letterbox).
            val h = containerW / ratio
            Rect(0f, (containerH - h) / 2f, containerW, h)
        }
    }
}

/**
 * Build 261 — the fullscreen player's scrub law: the mapping between a
 * position along the seek bar and a time in the project. Kept pure (and
 * pinned by FrameFitTest) so the player's bar and any future host agree.
 */
object ScrubLaw {

    /** Playhead position as a 0..1 fraction of [totalMs] (0 when there is no duration). */
    fun fractionAt(ms: Long, totalMs: Long): Float =
        if (totalMs <= 0L) 0f else (ms.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    /** The time a 0..1 bar [fraction] seeks to in a [totalMs] project (clamped both ends). */
    fun msAt(fraction: Float, totalMs: Long): Long =
        if (totalMs <= 0L) 0L else (fraction.coerceIn(0f, 1f) * totalMs.toFloat()).toLong().coerceIn(0L, totalMs)
}
