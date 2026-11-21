package com.example.model

import com.example.engine.SceneMathNative
import com.example.engine.ValueLawsNative

/**
 * Text-on-path (build 191): a text layer attached to a PATH shape layer
 * ([TimelineClip.textPathLayerId]) lays its glyphs along that layer's outline — each glyph
 * sits on the curve at its arc-length position, rotated to the curve's tangent there.
 *
 * This file is the pure layout law, shared by every renderer of an attached text layer:
 *  - the C++ text engine (its `PathWalk` in text_layout.cpp implements the same walk over
 *    the same polyline — the numbers pinned here are the numbers it must reproduce),
 *  - the Canvas fallback in LayerPainter (its per-paragraph offsets come from [layout]).
 *
 * THE LAW
 *  - The path is a polyline: the stored segments flattened by [flattenPathSegments]
 *    (24 samples per curve — the model's standard flattening, the same one the importer's
 *    bounds and the Pen tool use; chord error at 24 samples is far below a pixel at text
 *    sizes). All distances are arc lengths along that polyline.
 *  - Glyphs (in the engine: shaped clusters) advance along the path in reading order by
 *    their advance widths — the advances already include the layer's letter spacing, so
 *    spacing composes with the path unchanged.
 *  - A glyph is drawn iff its ORIGIN's arc distance is < the path's total length: text
 *    longer than the path is clipped at the path's end (a glyph whose origin is inside
 *    is drawn whole, even when its ink crosses the end slightly).
 *  - The tangent at an exact joint between two segments is the OUTGOING segment's
 *    tangent (the segment the pen is about to travel), except at the very end of the
 *    path, where it is the last segment's.
 *  - [TimelineClip.textPathStart] is a fraction of the total length: the first glyph's
 *    origin sits at start x total. Distances past the end clamp onto the end point.
 *
 * No Android types — unit tested (TextOnPathTest).
 */
object TextOnPath {

    /** A point on the path at an arc distance: position + tangent angle (degrees, y down, 0 = +x). */
    data class PathPoint(val x: Float, val y: Float, val angleDeg: Float)

    /** One laid-out cluster: its anchor (the point at its start distance) + whether any of it is drawn. */
    data class Placement(
        val startDist: Float,
        val x: Float,
        val y: Float,
        val angleDeg: Float,
        val drawn: Boolean
    )

    /**
     * Arc-length table over a polyline (frame pixels, y down). Consecutive duplicate
     * points are dropped (they carry neither arc nor tangent). A table over fewer than
     * 2 distinct points has total 0 and places nothing.
     */
    class ArcTable(points: List<Pair<Float, Float>>) {
        val points: List<Pair<Float, Float>>
        internal val cum: FloatArray // cum[i] = arc length at points[i]; cum[0] = 0
        internal val xy: FloatArray
        val total: Float

        init {
            // The table build (dedupe + cumulative arc lengths) is native
            // since build 247 (value_laws.cpp); blob = [total, n, xy..., cum...].
            val flat = FloatArray(points.size * 2)
            for (i in points.indices) {
                flat[i * 2] = points[i].first
                flat[i * 2 + 1] = points[i].second
            }
            val blob = ValueLawsNative.nativeArcTableBuild(flat)
            total = blob[0]
            val n = blob[1].toInt()
            xy = blob.copyOfRange(2, 2 + n * 2)
            cum = blob.copyOfRange(2 + n * 2, 2 + n * 2 + n)
            this.points = pairsOf(xy)
        }

        /** The point at arc distance [d] (clamped to [0, total]); see the joint rule in the class doc. */
        fun pointAt(d: Float): PathPoint {
            // Native since build 247: the segment search (first segment whose
            // END is strictly past d — at an exact joint the outgoing segment)
            // and the tangent angle run there.
            val out = ValueLawsNative.nativeArcPointAt(xy, cum, total, d)
            return PathPoint(out[0], out[1], out[2])
        }
    }

    fun arcTable(points: List<Pair<Float, Float>>): ArcTable = ArcTable(points)

    /** The arc table of a stored outline: segments flattened at the model's standard rate. */
    fun arcTableOfSegments(segments: List<PathSeg>, stepsPerCurve: Int = 24): ArcTable =
        ArcTable(flattenPathSegments(segments, stepsPerCurve))

    /**
     * Lays [advances] (cluster advances, in the same units as the table — they already
     * include letter spacing) along [table], starting at [startFrac] x the total length.
     * One [Placement] per advance, in order; a cluster is drawn iff its start distance is
     * inside the path (the engine refines this to glyph origins — same rule, finer grain).
     */
    fun layout(advances: List<Float>, startFrac: Float, table: ArcTable): List<Placement> {
        if (advances.isEmpty()) return emptyList()
        val advArr = FloatArray(advances.size) { advances[it] }
        val packed = SceneMathNative.nativeLayoutTextOnPath(
            table.xy, table.cum, table.total, advArr, startFrac
        )
        val count = packed.size / 5
        val out = ArrayList<Placement>(count)
        for (i in 0 until count) {
            val b = i * 5
            out += Placement(
                startDist = packed[b],
                x = packed[b + 1],
                y = packed[b + 2],
                angleDeg = packed[b + 3],
                drawn = packed[b + 4] != 0f
            )
        }
        return out
    }
}

/**
 * True when this layer carries a drawable path outline a text layer can attach to: a SHAPE
 * layer whose stored segments are a Pen / freehand / imported path (shapePattern == "PATH").
 * Built-in kinds (circle, rect, ...) have parametric outlines, not stored segments, and are
 * not attachable in build 191.
 */
val TimelineClip.isPathBearing: Boolean
    get() = type == LayerType.SHAPE &&
        (shapePattern ?: "").uppercase() == "PATH" && pathSegments.isNotEmpty()
