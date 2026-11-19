package com.example.model

import com.example.engine.FrameEvalNative
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The outline of Alight Motion's `.callout.roundrect` shape: a rounded rectangle with a
 * speech-bubble tail spliced into one of its four sides. This is a line-by-line port of the
 * getPath script in AM's own shape definition (recovered from the public decompiled AM assets,
 * `assets/shapes/`; copy in `after-motion-dev/hunt-staging/shape-defs/def_calloutrr.xml`), in
 * double precision throughout, like the JavaScript original. The def's body is node-for-node
 * the `.roundrect` def's (same eight anchors, same corner constant 0.552228474 — the handles
 * sit r·(1 − 0.552228474) from the corner along each edge, i.e. a handle's distance from its
 * own anchor is r·0.552228474); the callout adds four parameters (`size`, `tail`,
 * `cornerRadius`, `tailWidth`) and the tail insertion:
 *
 *  - the side is chosen by the tail vector alone: |tail.y| ≤ |tail.x| picks a vertical side
 *    (LEFT when tail.x < 0, else RIGHT), otherwise a horizontal one (TOP when tail.y < 0,
 *    else BOTTOM);
 *  - the tail's base centre along that side is the tail's cross-axis coordinate multiplied by
 *    the def's handleAttraction (3.5), then clamped so the whole base stays between the two
 *    corner arcs of the side: centre ∈ [−side + r + halfWidth, side − r − halfWidth], with
 *    halfWidth = min(tailWidth, the span between the arcs) / 2;
 *  - three PLAIN points are spliced into the node list at the side's fixed position (before
 *    node 1 for TOP, 3 for RIGHT, 5 for BOTTOM, 7 for LEFT): base point, the tail tip at the
 *    RAW tail point (never attracted or clamped), base point — in the outline's walking order;
 *  - when the clamped base lands exactly on the following anchor, the def's list holds that
 *    point twice in a row; the port keeps the duplicate verbatim.
 *
 * Node list → [PathSeg]s, the readings taken (the def returns AM path nodes, not segments):
 * an anchor's missing in/out handle is the anchor itself — a cubic whose handle collapses
 * onto an endpoint traces the straight chord to the other handle's locus, and the tail's
 * plain points lie exactly on the body's straight sides, so a step touching a plain point is
 * stored as a LINE and an anchor-to-anchor step as a CUBIC with the handles as written; the
 * locus is the def's under either reading of a plain point. The closing corner (last anchor
 * back to the first) is emitted as an explicit final CUBIC before the CLOSE, as the
 * `.multifoil` port does, because a stored CLOSE only joins its endpoints with a straight
 * line. The outline starts at the first node — the top edge's left corner end — and walks
 * clockwise on screen, the start build 120 settled for the roundrect family.
 */
object CalloutOutline {

    /** One node of the def's list: a bezier anchor ([ix]/[ox] null when the def writes no handle), or a plain tail point. */
    private class Node(
        val x: Double, val y: Double,
        val ix: Double?, val iy: Double?, val ox: Double?, val oy: Double?,
        val plain: Boolean
    )

    private fun anchor(
        x: Double, y: Double, i: Pair<Double, Double>?, o: Pair<Double, Double>?
    ) = Node(x, y, i?.first, i?.second, o?.first, o?.second, false)

    private fun plain(x: Double, y: Double) = Node(x, y, null, null, null, null, true)

    /**
     * The outline for half extents [width] / [height] (AM's `size`), tail point
     * ([tailX], [tailY]), [cornerRadius] and [tailWidth], as [PathSeg]s in AM units.
     * The caller guarantees positive half extents.
     */
    fun segments(
        width: Double, height: Double, tailX: Double, tailY: Double,
        cornerRadius: Double, tailWidth: Double
    ): List<PathSeg> =
        FrameEvalNative.unpackSegments(
            FrameEvalNative.nativeCalloutSegments(width, height, tailX, tailY, cornerRadius, tailWidth)
                ?: FloatArray(0)
        )

    /** One step of the walk: a LINE when either end is a plain tail point, else a CUBIC with the def's handles (a missing handle is its own anchor). */
    private fun step(prev: Node, cur: Node): PathSeg =
        if (prev.plain || cur.plain) {
            PathSeg(PATH_OP_LINE, listOf(cur.x.toFloat(), cur.y.toFloat()))
        } else {
            PathSeg(
                PATH_OP_CUBIC,
                listOf(
                    (prev.ox ?: prev.x).toFloat(), (prev.oy ?: prev.y).toFloat(),
                    (cur.ix ?: cur.x).toFloat(), (cur.iy ?: cur.y).toFloat(),
                    cur.x.toFloat(), cur.y.toFloat()
                )
            )
        }
}
