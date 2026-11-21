package com.example.model

import com.example.engine.FrameEvalNative

/**
 * The outline of Alight Motion's `.pie` shape: a wedge — an arc of [radius] about the layer's
 * centre, closed by two straight radii to the centre point. This is a line-by-line port of the
 * getPath / createArc / createSmallArc script in AM's own shape definition (recovered from the
 * public decompiled AM assets, `assets/shapes/pie.xml`; copy in
 * `after-motion-dev/hunt-staging/shape-defs/def_pie.xml`), in double precision throughout, like
 * the JavaScript original — including its angle conventions:
 *
 *  - the authored angles are NEGATED into the script's frame (and offset by +2000 turns, so the
 *    mod operations below always see positive values), which puts an authored angle θ at the
 *    on-screen point (r·cos θ, −r·sin θ): angles grow counterclockwise on screen, 0° at +x,
 *    90° at the top — the same frame the `.poly` / `.star` ports build in;
 *  - the "diff-ratio wrap rule": with Δ = |start − end| (in turns, mod 2), when Δ < 1 the LOWER
 *    of the two negated angles is pushed a full turn up, so the arc is drawn the LONG way around —
 *    the drawn sweep is 360° − Δ, starting at the LARGER authored angle's point and running in
 *    the direction of increasing authored angle. In other words the wedge is the disc MINUS the
 *    slice from the smaller angle up to the larger. When Δ ≥ 1 the upper angle is pushed up
 *    instead and the sweep is Δ − 360°. Δ = 0 and Δ = 360° both close the wedge completely
 *    (no arc at all — the discontinuity at Δ = 0 is the script's own: an ε difference still
 *    sweeps nearly the whole disc);
 *  - the arc is a Riskus cubic-bezier approximation, at most 90° per segment.
 *
 * Because every corpus `.pie` keyframes both angles, the wedge is rebuilt from the evaluated
 * angles per frame (see [pieSegmentsAt]); [segments] is that rebuild.
 */
object PieOutline {

    /** The arc's sweep in degrees for the authored angles — 0 when the wrap rule closes the wedge. */
    fun sweepDegrees(startDeg: Double, endDeg: Double): Double =
        FrameEvalNative.nativePieArcSweepDegrees(startDeg, endDeg)

    /**
     * The wedge outline for the authored angles at [radius], as [PathSeg]s in AM units (arc start
     * first, then one cubic per arc segment, a line to the centre, closed). Empty when the wrap
     * rule closes the wedge at these angles — the layer draws nothing then, as in Alight.
     */
    fun segments(startDeg: Double, endDeg: Double, radius: Double): List<PathSeg> =
        FrameEvalNative.unpackSegments(
            FrameEvalNative.nativePieSegments(startDeg, endDeg, radius) ?: FloatArray(0)
        )

    /** The wedge outline normalised to radius 1 — the basis a pie clip's stored segments use. */
    fun normalisedSegments(startDeg: Double, endDeg: Double): List<PathSeg> =
        segments(startDeg, endDeg, 1.0)
}
