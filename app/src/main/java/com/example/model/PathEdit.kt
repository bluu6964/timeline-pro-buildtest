package com.example.model

import com.example.engine.ValueLawsNative

/**
 * Node editing of PATH outlines (build 177 — Sarowar's correction of 2026-11-14: the
 * build-166 Draw is FREEHAND; real vector drawing is POINT-based, "অন্য অ্যাপ এ যেরকম").
 *
 * A stored outline ([TimelineClip.pathSegments]: M / L / Q / C / Z ops with absolute
 * points) becomes a list of [PathAnchor]s — each anchor has a position and optional
 * in / out bezier handles — and back, losslessly:
 *
 * - L round-trips as L (an anchor pair with no handles on the joining sides).
 * - C round-trips as C (the start anchor's out handle + the end anchor's in handle).
 * - Q is converted to C on entry by exact degree elevation
 *   (c1 = p0 + 2/3 (q − p0), c2 = p3 + 2/3 (q − p3)) — the same curve, not an approximation.
 * - H / V / S / T never reach storage: the importer already absolutizes them
 *   (H/V → L, S → C, T → Q) when it parses the path data, so Q is the only op that
 *   needs converting here.
 *
 * A [PathAnchor.smooth] point keeps its two handles COLLINEAR: dragging one handle
 * swings the other to the opposite direction, keeping its own length (the pen tool
 * creates smooth points with symmetric handles; an imported point is detected smooth
 * when its handles already sit on one straight line through the anchor).
 *
 * Closing segments: a stored CLOSE with no handles on the joining sides is just the
 * op (the painters draw the straight line back to the start). A closing segment WITH
 * handles is stored as an explicit C whose end point is the first anchor's position,
 * followed by CLOSE; on entry that end point merges back into the first anchor (its
 * in handle is the curve's second control point), so the pair round-trips.
 *
 * Everything here is pure and coordinate-space agnostic: the pen builder works in
 * frame fractions, the node editor in stored segment units. Unit tested (PenPathTest).
 *
 * The conversion and edit laws live natively in value_laws.cpp since build 247
 * (migration stage 4); this file keeps the storage types and the anchor packing
 * (per anchor: x, y, in, out, hasIn, hasOut, smooth — nulls ride as flags).
 */
data class PathAnchor(
    val x: Float, val y: Float,
    /** Bezier handle on the side of the PREVIOUS anchor (null = no handle: corner). */
    val inX: Float? = null, val inY: Float? = null,
    /** Bezier handle on the side of the NEXT anchor (null = no handle: corner). */
    val outX: Float? = null, val outY: Float? = null,
    /** Smooth point: the two handles stay on one straight line through the anchor. */
    val smooth: Boolean = false
) {
    val hasIn: Boolean get() = inX != null && inY != null
    val hasOut: Boolean get() = outX != null && outY != null
}

/** One continuous outline: its anchors in order, plus whether the last joins back to the first. */
data class PathSubpath(
    val anchors: List<PathAnchor> = emptyList(),
    val closed: Boolean = false
)

/** A whole editable outline (a stored path can hold several subpaths, one per MOVE). */
data class EditablePath(
    val subpaths: List<PathSubpath> = emptyList()
)

// ------------------------------------------------------------------ anchor packing

/** Path -> the bridge blob: [nSubs, (nAnchors, closed, 9 floats per anchor)*]. */
internal fun packPath(path: EditablePath): FloatArray {
    var size = 1
    for (sp in path.subpaths) size += 2 + sp.anchors.size * 9
    val blob = FloatArray(size)
    var o = 0
    blob[o++] = path.subpaths.size.toFloat()
    for (sp in path.subpaths) {
        blob[o++] = sp.anchors.size.toFloat()
        blob[o++] = if (sp.closed) 1f else 0f
        for (a in sp.anchors) o = packAnchor(blob, o, a)
    }
    return blob
}

/** Pen anchors -> the bridge blob: [nAnchors, 9 floats per anchor]. */
internal fun packPen(anchors: List<PathAnchor>): FloatArray {
    val blob = FloatArray(1 + anchors.size * 9)
    blob[0] = anchors.size.toFloat()
    var o = 1
    for (a in anchors) o = packAnchor(blob, o, a)
    return blob
}

private fun packAnchor(blob: FloatArray, o: Int, a: PathAnchor): Int {
    blob[o] = a.x
    blob[o + 1] = a.y
    blob[o + 2] = a.inX ?: 0f
    blob[o + 3] = a.inY ?: 0f
    blob[o + 4] = a.outX ?: 0f
    blob[o + 5] = a.outY ?: 0f
    blob[o + 6] = if (a.hasIn) 1f else 0f
    blob[o + 7] = if (a.hasOut) 1f else 0f
    blob[o + 8] = if (a.smooth) 1f else 0f
    return o + 9
}

private fun unpackAnchor(blob: FloatArray, o: Int): PathAnchor {
    val hasIn = blob[o + 6] != 0f
    val hasOut = blob[o + 7] != 0f
    return PathAnchor(
        blob[o], blob[o + 1],
        inX = if (hasIn) blob[o + 2] else null, inY = if (hasIn) blob[o + 3] else null,
        outX = if (hasOut) blob[o + 4] else null, outY = if (hasOut) blob[o + 5] else null,
        smooth = blob[o + 8] != 0f
    )
}

/** The bridge blob (from offset [start]) -> path. */
internal fun unpackPath(blob: FloatArray, start: Int = 0): EditablePath {
    var o = start
    val nSubs = blob[o++].toInt()
    val subs = ArrayList<PathSubpath>(nSubs)
    for (s in 0 until nSubs) {
        val nA = blob[o++].toInt()
        val closed = blob[o++] != 0f
        val anchors = ArrayList<PathAnchor>(nA)
        for (i in 0 until nA) {
            anchors += unpackAnchor(blob, o)
            o += 9
        }
        subs += PathSubpath(anchors, closed)
    }
    return EditablePath(subs)
}

/** The pen blob -> anchors. */
internal fun unpackPen(blob: FloatArray): List<PathAnchor> {
    val n = blob[0].toInt()
    val out = ArrayList<PathAnchor>(n)
    var o = 1
    for (i in 0 until n) {
        out += unpackAnchor(blob, o)
        o += 9
    }
    return out
}

object PathEdit {

    // ------------------------------------------------------------------ segments <-> anchors

    /**
     * Parses stored [segments] into anchors. Malformed tails (a drawing op before any
     * MOVE) are tolerated by anchoring at the op's end point rather than dropping them.
     */
    fun fromSegments(segments: List<PathSeg>): EditablePath {
        val (ops, counts, pts) = packSegsFlat(segments)
        return unpackPath(ValueLawsNative.nativePathFromSegments(ops, counts, pts))
    }

    /** Writes anchors back to stored segments (M / L / C / Z only — never Q). */
    fun toSegments(path: EditablePath): List<PathSeg> =
        unpackSegStream(ValueLawsNative.nativePathToSegments(packPath(path)))

    // ------------------------------------------------------------------ edits

    /** Moves an anchor; its handles travel with it (the curve keeps its shape). */
    fun moveAnchor(path: EditablePath, sub: Int, idx: Int, x: Float, y: Float): EditablePath =
        unpackPath(ValueLawsNative.nativePathMoveAnchor(packPath(path), sub, idx, x, y))

    /**
     * Moves one handle of an anchor to ([x], [y]). On a smooth point the other handle
     * swings to stay collinear (opposite direction), keeping its own length; on a
     * corner point only the dragged handle moves.
     */
    fun moveHandle(path: EditablePath, sub: Int, idx: Int, out: Boolean, x: Float, y: Float): EditablePath =
        unpackPath(ValueLawsNative.nativePathMoveHandle(packPath(path), sub, idx, out, x, y))

    /**
     * Splits the segment that STARTS at anchor [seg] of subpath [sub] at fraction [t],
     * inserting a new anchor there (de Casteljau — the curve does not change shape).
     * On a closed subpath [seg] may be the last anchor (the closing segment). Returns
     * the new path and the inserted anchor's index (−1 when there is no such segment).
     * A split line stays two lines (the new anchor is a corner); a split curve gives
     * the new anchor smooth handles lying on the curve.
     */
    fun splitSegment(path: EditablePath, sub: Int, seg: Int, t: Float): Pair<EditablePath, Int> {
        val blob = ValueLawsNative.nativePathSplitSegment(packPath(path), sub, seg, t)
        return unpackPath(blob, 1) to blob[0].toInt()
    }

    /**
     * Removes an anchor; the two segments beside it merge into one (the neighbours'
     * handles now face each other). Refused when the subpath would drop below a
     * drawable minimum: 2 anchors for an open path, 3 for a closed one.
     */
    fun deleteAnchor(path: EditablePath, sub: Int, idx: Int): EditablePath =
        unpackPath(ValueLawsNative.nativePathDeleteAnchor(packPath(path), sub, idx))
}

/**
 * The point-based Pen tool's builder (build 177): the anchors the user places on the
 * canvas (frame fractions, like the freehand Draw's points) become a PATH shape layer
 * through the exact normalisation the freehand tool uses ([VectorDraw.normalize]), so
 * a pen layer is the same storage as a drawn or imported path and every existing tool
 * (fill, stroke, trim, masks, effects, keyframes) works on it unchanged.
 *
 * The interaction the model encodes: a TAP adds a corner anchor (straight segments);
 * a TAP-DRAG pulls symmetric bezier handles out of the new anchor (a smooth point,
 * curved segments); finishing with the path closed makes it a filled shape, open
 * leaves it stroke-only — the freehand tool's own rule.
 */
object PenPath {

    /** A plain tap: a corner anchor at ([x], [y]) (frame fractions). */
    fun tap(anchors: List<PathAnchor>, x: Float, y: Float): List<PathAnchor> =
        unpackPen(ValueLawsNative.nativePenTap(packPen(anchors), x, y))

    /**
     * The finger drags after a tap: the last anchor grows handles — the out handle at
     * the finger, the in handle mirrored symmetrically through the anchor.
     */
    fun pull(anchors: List<PathAnchor>, x: Float, y: Float): List<PathAnchor> =
        unpackPen(ValueLawsNative.nativePenPull(packPen(anchors), x, y))

    /** A tap that never turned into a drag: the last anchor keeps no handles. */
    fun releaseTap(anchors: List<PathAnchor>): List<PathAnchor> =
        unpackPen(ValueLawsNative.nativePenReleaseTap(packPen(anchors)))

    /** Removes the last placed anchor (the pen panel's Undo point). */
    fun undoPoint(anchors: List<PathAnchor>): List<PathAnchor> =
        unpackPen(ValueLawsNative.nativePenUndoPoint(packPen(anchors)))

    /**
     * Builds the layer geometry for the placed [anchors] (frame fractions) on a canvas
     * of [canvasW] x [canvasH] stored units. Null with fewer than two anchors or no extent.
     */
    fun build(
        anchors: List<PathAnchor>, closed: Boolean,
        canvasW: Float, canvasH: Float, density: Float
    ): VectorDraw.DrawnPath? {
        if (anchors.size < 2 || canvasW <= 0f || canvasH <= 0f) return null
        val blob = ValueLawsNative.decodeNormalizeBlob(
            ValueLawsNative.nativePenBuild(packPen(anchors), closed, canvasW, canvasH, density)
        )
        if (!blob.ok) return null
        return VectorDraw.DrawnPath(
            unpackSegStream(blob.segStream),
            blob.offsetX, blob.offsetY, blob.scale,
            pairsOf(blob.flatFrame)
        )
    }
}
