package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.engine.VectorCmdBuilder

/**
 * Build 249 (UI migration stage 2): the dock tiles' glyph command builders.
 * Through build 248 each glyph was drawn by its own per-tile
 * NativeVectorCanvas (its own TextureView); the bodies below are those
 * canvases' command blocks copied expression-for-expression — same unit
 * grids, same colours — now packed into the dock's ONE GL surface by
 * [DockGlRow], which sets the builder's translate to the glyph box's
 * origin and passes the box width as [w].
 *
 * The three tiles whose glyph is a Material icon (music, sticker, camera)
 * have no builder here: their icons rasterize into the UI atlas instead
 * (they cannot be vector-drawn faithfully by hand).
 */

/**
 * The reference's media glyph OUTLINE: a stroked rounded rect (SDF batch).
 * Build 259: the play triangle is NOT drawn here any more. It was a
 * flat-batch filled polygon, and the renderer flushes the flat batch before
 * the SDF batch — the tile's own opaque SDF fill painted over it, so the
 * triangle never reached the screen. It is an atlas blit now (the atlas
 * flushes last), rasterized in DockSurface from the same 24-unit points:
 * (10.2, 9.3) / (15.2, 12) / (10.2, 14.7), ink 0xFFD4D4D8.
 */
internal fun VectorCmdBuilder.dockMediaGlyph(w: Float) {
    val u = w / 24f
    val cArgb = Color(0xFFD4D4D8).toArgb()
    addRoundRect(
        cx = 12f * u, cy = 12f * u,
        width = 18f * u, height = 14f * u,
        cornerRadius = 4f * u,
        fillArgb = 0,
        strokeWidth = 2f * u,
        strokeArgb = cArgb
    )
}

/**
 * The fx glyph's 8-point star outline (unit grid, radius 1). Build 259:
 * the two sparkles are atlas blits, not flat-batch polygons — same
 * flat-under-SDF cover-up as the play triangle (the Fx tile rendered
 * completely empty). DockSurface rasterizes this outline once and blits
 * it twice (small grey + big white) at [com.example.model.DockGeometry]'s
 * fxStarBlit placement.
 */
internal val DOCK_STAR_PTS = listOf(
    0f to -1f, 0.24f to -0.24f, 1f to 0f, 0.24f to 0.24f,
    0f to 1f, -0.24f to 0.24f, -1f to 0f, -0.24f to -0.24f
)

/** The reference's text glyph: T strokes. */
internal fun VectorCmdBuilder.dockTextGlyph(w: Float) {
    val u = w / 24f
    val cArgb = Color(0xFFD4D4D8).toArgb()
    val sw = 2.3f * u
    addLine(6f * u, 6f * u, 18f * u, 6f * u, sw, cArgb, capRound = true)
    addLine(12f * u, 6f * u, 12f * u, 18f * u, sw, cArgb, capRound = true)
    addLine(9.5f * u, 18f * u, 14.5f * u, 18f * u, sw, cArgb, capRound = true)
}

/** The reference's shapes glyph: a grey circle + a white rounded square. */
internal fun VectorCmdBuilder.dockShapesGlyph(w: Float) {
    val u = w / 25f
    addCircle(9.8f * u, 9.8f * u, 5.2f * u, Color(0xFF71717A).toArgb())
    addRoundRect(
        cx = (9.8f + 5.4f) * u, cy = (9.8f + 5.4f) * u,
        width = 10.8f * u, height = 10.8f * u,
        cornerRadius = 2.6f * u,
        fillArgb = Color(0xFFF4F4F5).toArgb()
    )
}

/** The reference's null glyph: a centre square + four corner squares. */
internal fun VectorCmdBuilder.dockNullGlyph(w: Float) {
    val u = w / 25f
    val cArgb = Color(0xFFE4E4E7).toArgb()
    val bgArgb = com.example.ui.theme.RefSurface.toArgb()
    addRoundRect(12.5f * u, 12.5f * u, 12f * u, 12f * u, 1.5f * u, cArgb)
    listOf(4f to 4f, 17f to 4f, 4f to 17f, 17f to 17f).forEach { pt ->
        addRoundRect(
            (pt.first + 2f) * u, (pt.second + 2f) * u,
            4f * u, 4f * u, 1f * u,
            fillArgb = bgArgb, strokeWidth = 1.5f * u, strokeArgb = cArgb
        )
    }
}

private val DOCK_VECTOR_PTS = listOf(5f to 19f, 10f to 7.5f, 15f to 14.5f, 20f to 6f)

/** Build 218: the Vector tile's glyph — a node polyline (the Pen tool). */
internal fun VectorCmdBuilder.dockVectorGlyph(w: Float) {
    val u = w / 25f
    val cArgb = Color(0xFFE4E4E7).toArgb()
    val xs = FloatArray(DOCK_VECTOR_PTS.size) { DOCK_VECTOR_PTS[it].first * u }
    val ys = FloatArray(DOCK_VECTOR_PTS.size) { DOCK_VECTOR_PTS[it].second * u }
    addPolyline(xs, ys, DOCK_VECTOR_PTS.size, false, 1.8f * u, cArgb, capRound = true)
    for (i in DOCK_VECTOR_PTS.indices) addCircle(xs[i], ys[i], 2f * u, cArgb)
}

/** Build 218: the Drawing tile's glyph — a freehand scribble stroke. */
internal fun VectorCmdBuilder.dockDrawingGlyph(w: Float) {
    val u = w / 25f
    val cArgb = Color(0xFFE4E4E7).toArgb()
    val sw = 2f * u
    addCubic(4f * u, 16f * u, 7f * u, 6f * u, 10.5f * u, 20f * u, 13.5f * u, 10.5f * u, sw, cArgb, capRound = true)
    addCubic(13.5f * u, 10.5f * u, 15.5f * u, 5.5f * u, 18f * u, 13f * u, 21f * u, 9.5f * u, sw, cArgb, capRound = true)
}

/** Build 218: the Group tile's glyph — two overlapping rounded squares. */
internal fun VectorCmdBuilder.dockGroupGlyph(w: Float) {
    val u = w / 25f
    val c = Color(0xFFE4E4E7)
    val sw = 1.9f * u
    addRoundRect(
        cx = (4f + 6.75f) * u, cy = (4f + 6.75f) * u,
        width = 13.5f * u, height = 13.5f * u,
        cornerRadius = 3f * u, fillArgb = 0, strokeWidth = sw, strokeArgb = c.copy(alpha = 0.55f).toArgb()
    )
    addRoundRect(
        cx = (8.5f + 6.25f) * u, cy = (8.5f + 6.25f) * u,
        width = 12.5f * u, height = 12.5f * u,
        cornerRadius = 3f * u, fillArgb = 0, strokeWidth = sw, strokeArgb = c.toArgb()
    )
}

/** Build 262: the 3D Space tile's glyph — an isometric cube wireframe (the space's mark). */
internal fun VectorCmdBuilder.dockSpaceGlyph(w: Float) {
    val u = w / 25f
    val cArgb = Color(0xFFE4E4E7).toArgb()
    val sw = 1.9f * u
    fun line(pts: List<Pair<Float, Float>>, closed: Boolean = false) {
        val xs = FloatArray(pts.size) { pts[it].first * u }
        val ys = FloatArray(pts.size) { pts[it].second * u }
        addPolyline(xs, ys, pts.size, closed, sw, cArgb, capRound = true)
    }
    // Outer hexagon of the cube.
    line(listOf(12.5f to 3f, 21f to 7.5f, 21f to 16.5f, 12.5f to 21f, 4f to 16.5f, 4f to 7.5f), closed = true)
    // The three edges meeting at the centre.
    line(listOf(4f to 7.5f, 12.5f to 12f, 21f to 7.5f))
    line(listOf(12.5f to 12f, 12.5f to 21f))
}

/** Build 218: the Chain tile's glyph — two interlocked rounded links. */
internal fun VectorCmdBuilder.dockChainGlyph(w: Float) {
    val u = w / 25f
    val cArgb = Color(0xFFE4E4E7).toArgb()
    val sw = 2f * u
    addRoundRect((3f + 5.25f) * u, (9.5f + 3.25f) * u, 10.5f * u, 6.5f * u, 3.25f * u, 0, sw, cArgb)
    addRoundRect((11.5f + 5.25f) * u, (9.5f + 3.25f) * u, 10.5f * u, 6.5f * u, 3.25f * u, 0, sw, cArgb)
}
