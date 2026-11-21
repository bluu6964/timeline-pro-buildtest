package com.example.model

import com.example.engine.SceneMathNative
import com.example.engine.ValueLawsNative

/**
 * One layer's axis-aligned bounds on the canvas, in canvas pixels — the same visible
 * bounds the snapping measures (build 188): the caller builds these from the build-176
 * `snapRectOf` rects, so a cropped shape / text layer aligns by its CROPPED bounds, a
 * media layer by its visible (fitted, cropped) picture, and a picture-less layer
 * (Null / Controller / Camera) by its centre point (a zero-size rect).
 */
data class AlignRect(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** The spatial actions of the multi-select bar (build 188: six aligns + two distributes; build 199: two canvas-centre actions). */
enum class SpatialAction {
    ALIGN_LEFT, ALIGN_CENTER_H, ALIGN_RIGHT,
    ALIGN_TOP, ALIGN_CENTER_V, ALIGN_BOTTOM,
    DISTRIBUTE_H, DISTRIBUTE_V,
    /** Build 199 (A8): centre on the FRAME, not the selection — see [SpatialAlign.canvasDeltas]. */
    CANVAS_CENTER_H, CANVAS_CENTER_V
}

/**
 * Canvas spatial align / distribute for multi-selected layers (build 188) — pure math in
 * canvas pixels: given the layers' rects and an action, the per-layer delta each layer
 * must move by. The caller converts a delta back to stored units (through the layer's
 * depth factor, the build-176/184 rule) and writes it through the ViewModel's transform
 * funnel, so keyframing / undo / Controller fan-out behave exactly like a manual move.
 *
 * Align reads against the selection's own bounding box: LEFT / TOP take the smallest
 * edge, RIGHT / BOTTOM the largest, and the two CENTRE actions the bounding box's
 * centre. Distribute spaces the layer CENTRES evenly between the two extreme centres
 * (which do not move) — equal centre spacing regardless of the layers' sizes.
 * Layers already in place get no entry, so an all-aligned selection is a no-op.
 */
object SpatialAlign {

    /**
     * The deltas for any [SpatialAction]; empty when the action cannot apply.
     * Build 198: a rect with ANY non-finite bound drops out first — one layer
     * with an extreme (infinite / NaN) evaluated bounds must not poison the
     * shared bounding box and fling every other selected layer with it.
     */
    private fun nativeActionOf(action: SpatialAction): Int = when (action) {
        SpatialAction.ALIGN_LEFT -> SceneMathNative.ACTION_ALIGN_LEFT
        SpatialAction.ALIGN_CENTER_H -> SceneMathNative.ACTION_ALIGN_CENTER_H
        SpatialAction.ALIGN_RIGHT -> SceneMathNative.ACTION_ALIGN_RIGHT
        SpatialAction.ALIGN_TOP -> SceneMathNative.ACTION_ALIGN_TOP
        SpatialAction.ALIGN_CENTER_V -> SceneMathNative.ACTION_ALIGN_CENTER_V
        SpatialAction.ALIGN_BOTTOM -> SceneMathNative.ACTION_ALIGN_BOTTOM
        SpatialAction.DISTRIBUTE_H -> SceneMathNative.ACTION_DISTRIBUTE_H
        SpatialAction.DISTRIBUTE_V -> SceneMathNative.ACTION_DISTRIBUTE_V
        SpatialAction.CANVAS_CENTER_H -> SceneMathNative.ACTION_CANVAS_CENTER_H
        SpatialAction.CANVAS_CENTER_V -> SceneMathNative.ACTION_CANVAS_CENTER_V
    }

    private fun packRects(rects: List<AlignRect>): FloatArray {
        val flat = FloatArray(rects.size * 4)
        for (i in rects.indices) {
            val r = rects[i]
            val b = i * 4
            flat[b] = r.left
            flat[b + 1] = r.top
            flat[b + 2] = r.right
            flat[b + 3] = r.bottom
        }
        return flat
    }

    private fun unpackDeltas(rects: List<AlignRect>, packed: FloatArray): Map<String, Pair<Float, Float>> {
        if (packed.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Pair<Float, Float>>(packed.size / 3)
        var i = 0
        while (i + 2 < packed.size) {
            val idx = packed[i].toInt()
            if (idx in rects.indices) {
                out[rects[idx].id] = packed[i + 1] to packed[i + 2]
            }
            i += 3
        }
        return out
    }

    fun deltas(rects: List<AlignRect>, action: SpatialAction): Map<String, Pair<Float, Float>> {
        // The canvas actions are frame-relative and need the frame size — they are served by
        // canvasDeltas below; deltas() has no frame and refuses them (build 199, A8).
        if (action == SpatialAction.CANVAS_CENTER_H || action == SpatialAction.CANVAS_CENTER_V) return emptyMap()
        if (rects.isEmpty()) return emptyMap()
        return unpackDeltas(rects, SceneMathNative.nativeSpatialDeltas(packRects(rects), nativeActionOf(action)))
    }

    /**
     * Canvas-relative centring (build 199, A8): the delta that puts each rect's centre on the
     * FRAME's centre line (horizontal centre for CANVAS_CENTER_H, vertical for
     * CANVAS_CENTER_V), in canvas pixels, against a frame of [frameW] x [frameH]. Unlike the
     * selection-relative aligns this works for a SINGLE layer too — centring one layer in the
     * frame is the common case. Non-finite rects drop out (the build-198 law); a degenerate
     * frame or a non-canvas action yields no deltas.
     */
    fun canvasDeltas(
        rects: List<AlignRect>,
        frameW: Float,
        frameH: Float,
        action: SpatialAction
    ): Map<String, Pair<Float, Float>> {
        if (action != SpatialAction.CANVAS_CENTER_H && action != SpatialAction.CANVAS_CENTER_V) return emptyMap()
        if (!frameW.isFinite() || !frameH.isFinite() || frameW <= 0f || frameH <= 0f) return emptyMap()
        if (rects.isEmpty()) return emptyMap()
        return unpackDeltas(
            rects,
            SceneMathNative.nativeSpatialCanvasDeltas(packRects(rects), frameW, frameH, nativeActionOf(action))
        )
    }

    /** Align needs at least 2 rects. (Native since build 247 — value_laws.cpp.) */
    fun alignDeltas(rects: List<AlignRect>, action: SpatialAction): Map<String, Pair<Float, Float>> {
        if (rects.size < 2) return emptyMap()
        return unpackDeltas(rects, ValueLawsNative.nativeSpatialAlignDeltas(packRects(rects), action.ordinal))
    }

    /** Distribute needs at least 3 rects and a non-zero span between the extreme centres. (Native since build 247.) */
    fun distributeDeltas(rects: List<AlignRect>, horizontal: Boolean): Map<String, Pair<Float, Float>> {
        if (rects.size < 3) return emptyMap()
        return unpackDeltas(rects, ValueLawsNative.nativeSpatialDistributeDeltas(packRects(rects), horizontal))
    }
}
