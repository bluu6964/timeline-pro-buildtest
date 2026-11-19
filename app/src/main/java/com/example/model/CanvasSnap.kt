package com.example.model

import com.example.engine.SceneMathNative
import kotlin.math.abs

/**
 * Canvas snapping while a layer is dragged on the preview canvas (build 176 — Sarowar's
 * tool 3 from the missing-tools audit). Pure decision logic in canvas pixels: given the
 * dragged layer's axis-aligned rect, the canvas size and the other layers' rects, decide
 * the correction that snaps the layer and which guide lines to show.
 *
 * Per axis independently, the dragged rect's centre and two edges are matched against
 * the canvas centre, the canvas edges and every other rect's centre / edges; the closest
 * match inside [thresholdPx] wins (ties keep the earliest candidate: canvas centre first,
 * then canvas edges, then other layers — and the centre feature before the edges).
 */
data class SnapRect(
    val cx: Float,
    val cy: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    companion object {
        /** A rect from its centre and size (a zero size gives a centre-only rect). */
        fun of(cx: Float, cy: Float, w: Float, h: Float): SnapRect =
            SnapRect(cx, cy, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}

/** [dx] / [dy]: the correction to add to the dragged rect's centre. [guideX] / [guideY]: the canvas x / y of the active vertical / horizontal guide line, or null. */
data class SnapResult(val dx: Float, val dy: Float, val guideX: Float?, val guideY: Float?)

object CanvasSnap {

    fun snap(
        rect: SnapRect,
        canvasW: Float,
        canvasH: Float,
        others: List<SnapRect>,
        thresholdPx: Float
    ): SnapResult {
        val othersFlat = FloatArray(others.size * 6)
        for (i in others.indices) {
            val o = others[i]
            val b = i * 6
            othersFlat[b] = o.cx
            othersFlat[b + 1] = o.cy
            othersFlat[b + 2] = o.left
            othersFlat[b + 3] = o.top
            othersFlat[b + 4] = o.right
            othersFlat[b + 5] = o.bottom
        }
        val out = FloatArray(6)
        SceneMathNative.nativeCanvasSnap(
            rect.cx, rect.cy, rect.left, rect.top, rect.right, rect.bottom,
            canvasW, canvasH, othersFlat, thresholdPx, out
        )
        return SnapResult(
            dx = out[0],
            dy = out[1],
            guideX = if (out[2] != 0f) out[3] else null,
            guideY = if (out[4] != 0f) out[5] else null
        )
    }

}
