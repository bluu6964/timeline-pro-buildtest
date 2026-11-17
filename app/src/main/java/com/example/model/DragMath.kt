package com.example.model

/**
 * Canvas drag accumulation (build 225 — the drag-shake fix, NEXT-BUILD-LIST item 2).
 *
 * A canvas drag is measured on a FIXED full-canvas gesture layer, in canvas pixels,
 * accumulated from the drag-start position — never on the layer's own moving touch box
 * (the build-224 text / shape handlers sat on the box that moves with the layer, so
 * every drag amount was measured in a coordinate space that had itself just moved:
 * a feedback loop that showed up on the phone as shake, worst on text and shapes).
 *
 * The stored position a drag writes is the layer's position BEFORE the depth factor
 * (the canvas draws it at stored x [depthFactor], see [depthX]), so the stored
 * position moves by the finger's canvas travel divided by the depth factor:
 *
 *     stored = start + totalCanvasDelta / depthFactor
 *
 * This is exactly what the old per-layer handlers computed the long way round: each
 * event's amount arrived in the layer's own (scaled, rotated, flipped) space and was
 * converted back with the layer's scale and rotation — the scale, rotation and flip
 * factors cancel algebraically, leaving the finger's canvas travel over the depth
 * factor. Measuring in canvas space from the start keeps the same end position with
 * no moving measurement frame. DragMathTest pins the equivalence.
 */
object DragMath {

    /**
     * The stored position of a layer being dragged on the canvas: [startX] / [startY]
     * are the layer's stored position when the drag began, [totalDx] / [totalDy] the
     * finger's TOTAL travel in canvas pixels since the drag began (not per-event
     * increments), and [depthFactor] the layer's [TimelineClip.depthFactor] (constant
     * during a position drag — it depends only on the layer's Z).
     */
    fun draggedPosition(
        startX: Float,
        startY: Float,
        totalDx: Float,
        totalDy: Float,
        depthFactor: Float
    ): Pair<Float, Float> {
        val df = depthFactor.coerceAtLeast(0.001f)
        return (startX + totalDx / df) to (startY + totalDy / df)
    }
}
