package com.example.model

import com.example.engine.FrameEvalNative

/**
 * The outline of Alight Motion's `.arc` shape: a circular arc of [radius] about the layer's
 * centre, from the start angle to the end angle, optionally closed by the straight chord back
 * to its start. This is a line-by-line port of the getPath / createArc / createSmallArc script
 * in AM's own shape definition (recovered from the public decompiled AM assets,
 * `assets/shapes/arc.xml`; copies in `after-motion-dev/hunt-staging/shape-defs-r2/`), in double
 * precision throughout, like the JavaScript original. The angle preparation is the pie def's
 * (see [PieOutline]): the authored angles are NEGATED into the script's frame (and offset by
 * +2000 turns), and the "diff-ratio wrap rule" pushes the lower negated angle a full turn up
 * when the authored difference is under one turn, so the arc is drawn the LONG way around —
 * an authored 91° -> 90° arc sweeps 359°, and equal start / end angles close the arc
 * completely (no outline at all). The arc itself is the same Riskus cubic-bezier approximation
 * the pie uses, at most 90° per segment; unlike the pie, no radii to the centre are added —
 * the only closing segment is the chord, and only when the `closed` switch is on.
 *
 * Because an `.arc` can keyframe its angles (the corpus's khanbh-scene-9 "Arc 1" animates
 * startAngle through ten keys), the outline is rebuilt from the evaluated angles per frame
 * (see [arcSegmentsAt]); [segments] is that rebuild.
 */
object ArcOutline {

    /** The arc's sweep in degrees for the authored angles — 0 when the wrap rule closes it. */
    fun sweepDegrees(startDeg: Double, endDeg: Double): Double =
        FrameEvalNative.nativePieArcSweepDegrees(startDeg, endDeg)

    /**
     * The arc outline for the authored angles at [radius], as [PathSeg]s in AM units (arc start
     * first, then one cubic per arc segment, and a close only when [closed]). Empty when the
     * wrap rule closes the arc at these angles — the layer draws nothing then, as in Alight.
     */
    fun segments(startDeg: Double, endDeg: Double, radius: Double, closed: Boolean): List<PathSeg> =
        FrameEvalNative.unpackSegments(
            FrameEvalNative.nativeArcSegments(startDeg, endDeg, radius, closed) ?: FloatArray(0)
        )

    /** The arc outline normalised to radius 1 — the basis an arc clip's stored segments use. */
    fun normalisedSegments(startDeg: Double, endDeg: Double, closed: Boolean): List<PathSeg> =
        segments(startDeg, endDeg, 1.0, closed)
}
