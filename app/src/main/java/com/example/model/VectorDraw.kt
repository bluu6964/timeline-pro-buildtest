package com.example.model

import com.example.engine.ValueLawsNative

/**
 * The freehand drawing tool (build 166 — Sarowar's directive 2026-11-13 ~20:22, item 1;
 * named FREEHAND, not vector, since his correction of 2026-11-14: vector drawing is the
 * point-based Pen tool, [PenPath]): a freehand stroke drawn on the canvas becomes a real
 * PATH shape layer
 * ([TimelineClip.shapePattern] == "PATH" + [TimelineClip.pathSegments]) — the same
 * storage the Alight importer writes for a `<path>` shape, so the layer renders through
 * the exact same painters in preview and export and stays editable with the existing
 * tools (Transform, Color fill, stroke via the shape fields, Effects, masks, trim ...).
 *
 * This file is the pure geometry: raw stroke points in frame fractions in, the stored
 * (normalised) segments plus the layer placement out. No Compose, no Android — unit
 * tested (VectorDrawTest).
 *
 * Coordinates: the input points are fractions of the frame (x: 0..1 of the width,
 * y: 0..1 of the height). All distance / bounds maths runs in a UNIFORM space
 * (u = x * aspect, v = y, so one unit is one frame height on both axes) — the same
 * trick [com.example.model.ClipMaskLibrary.fromPenPoints] uses for pen masks and the
 * importer uses for path shapes: the stored segments are centred on the outline's
 * bounding box and divided by its LARGER half extent (a uniform divisor, so a flat
 * stroke is not squashed), and [DrawnPath.shapeScale] carries the true size back.
 *
 * The geometry laws live natively in value_laws.cpp since build 247 (migration
 * stage 4); this file keeps the storage types and the packing.
 */
object VectorDraw {

    /** Consecutive raw points closer than this (uniform units) are one point: a finger never holds still. */
    const val MIN_STEP = 0.0035f

    /** A stroke needs at least this many distinct points. */
    const val MIN_POINTS = 2

    /**
     * The built layer geometry.
     * [segments] = the stored outline (the [TimelineClip.pathSegments] basis: centred on
     * the bounding box, divided by its larger half extent — coordinates span [-1, 1] on
     * the larger axis). [offsetX] / [offsetY] = the layer position in stored units (the
     * drawing's centre folded like the importer's: frame centre = 0). [shapeScale] = the
     * shapeScaleX = shapeScaleY value; with scalePercent = shapeScale * 100 the writers'
     * invariant holds, and the painters replay the segments at the drawn size (the shape
     * box is 130 dp: half = 65 * density stored px, hence the divisor).
     */
    class DrawnPath(
        val segments: List<PathSeg>,
        val offsetX: Float,
        val offsetY: Float,
        val shapeScale: Float,
        /** The flattened outline in frame fractions (the drawing as the user made it; for tests / previews). */
        val flatFrame: List<Pair<Float, Float>>
    )

    /**
     * Builds the layer geometry for a stroke of [rawPoints] (frame fractions) on a canvas
     * of [canvasW] x [canvasH] stored units with the given display [density].
     * [smooth] runs the stroke through midpoint-quadratic smoothing (the classic
     * draw-a-curve-through-the-points scheme: the curve passes through the midpoints of
     * consecutive raw points and bends at the raw points); without it the segments are
     * straight lines. [close] appends a CLOSE op (a filled shape instead of an open line).
     * Null when the stroke has too few distinct points or no extent.
     */
    fun build(
        rawPoints: List<Pair<Float, Float>>,
        canvasW: Float, canvasH: Float, density: Float,
        smooth: Boolean, close: Boolean
    ): DrawnPath? {
        if (canvasW <= 0f || canvasH <= 0f) return null
        val aspect = canvasW / canvasH
        val rawXy = FloatArray(rawPoints.size * 2)
        for (i in rawPoints.indices) {
            rawXy[i * 2] = rawPoints[i].first
            rawXy[i * 2 + 1] = rawPoints[i].second
        }
        val stream = ValueLawsNative.nativeVectorBuild(rawXy, aspect, smooth, close)
        if (stream.isEmpty()) return null
        return normalize(unpackSegStream(stream), canvasW, canvasH, density)
    }

    /**
     * Normalises raw segments drawn in uniform space (u = x * aspect, v = y) onto their
     * bounding box and works out the layer placement — the law the importer's
     * normalisePathSegs writes and both painters replay. Shared by the freehand stroke
     * (above) and the point-based Pen tool ([PenPath.build]), so a path layer's storage
     * means the same thing however it was drawn. Null when the outline has no extent.
     */
    fun normalize(
        raw: List<PathSeg>,
        canvasW: Float, canvasH: Float, density: Float
    ): DrawnPath? {
        if (canvasW <= 0f || canvasH <= 0f) return null
        val (ops, counts, pts) = packSegsFlat(raw)
        val blob = ValueLawsNative.decodeNormalizeBlob(
            ValueLawsNative.nativeVectorNormalize(ops, counts, pts, canvasW, canvasH, density)
        )
        if (!blob.ok) return null
        return DrawnPath(
            unpackSegStream(blob.segStream),
            blob.offsetX, blob.offsetY, blob.scale,
            pairsOf(blob.flatFrame)
        )
    }
}

/** Segments -> the flat (ops, counts, points) pack the value-laws bridge takes. */
internal fun packSegsFlat(segments: List<PathSeg>): Triple<IntArray, IntArray, FloatArray> {
    val ops = IntArray(segments.size)
    val counts = IntArray(segments.size)
    var total = 0
    for (i in segments.indices) {
        ops[i] = segments[i].op
        counts[i] = segments[i].pts.size
        total += segments[i].pts.size
    }
    val pts = FloatArray(total)
    var o = 0
    for (seg in segments) {
        for (v in seg.pts) pts[o++] = v
    }
    return Triple(ops, counts, pts)
}

/** The packed segment stream ([op, count, points...] per segment) -> segments. */
internal fun unpackSegStream(stream: FloatArray): List<PathSeg> {
    val out = ArrayList<PathSeg>()
    var i = 0
    while (i + 1 < stream.size) {
        val op = stream[i].toInt()
        val count = stream[i + 1].toInt()
        val pts = ArrayList<Float>(count)
        for (j in 0 until count) pts += stream[i + 2 + j]
        out += PathSeg(op, pts)
        i += 2 + count
    }
    return out
}

/** A flat xy array -> point pairs. */
internal fun pairsOf(xy: FloatArray): List<Pair<Float, Float>> =
    List(xy.size / 2) { xy[it * 2] to xy[it * 2 + 1] }
