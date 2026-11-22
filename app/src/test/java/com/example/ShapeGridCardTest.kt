package com.example

import com.example.model.ShapeCatalog
import com.example.model.SheetGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 257 — the Add Shape card's flat-batch emission
 * ([SheetGeometry.ShapeGrid.cardPolygons]), the fix for the invisible
 * tile glyphs on device: the GL renderer flushes batches in a fixed
 * order (flat triangles before SDF), so the build-256 card's SDF
 * round-rect tile fill painted over its flat glyph polygons. These
 * tests pin the emission the card now feeds the command builder:
 * the tile outline FIRST (same batch, tile colour), then the shape's
 * own points inside the card rect in a colour that contrasts the tile,
 * holes punched after their body in the tile colour.
 *
 * The colours mirror the card's own constants in EditorSheets
 * (glyph 0xFFEDEDED; tile = AmPanelHigh 0xFF202026).
 */
class ShapeGridCardTest {

    private val g = SheetGeometry.ShapeGrid
    private val glyphArgb = 0xFFEDEDED.toInt()
    private val tileArgb = 0xFF202026.toInt()
    private val tilePx = 200f
    private val cornerPx = 36f

    private fun polys(pattern: String) =
        g.cardPolygons(ShapeCatalog.byPattern(pattern)!!, tilePx, cornerPx, glyphArgb, tileArgb)

    private fun bbox(p: SheetGeometry.ShapeGrid.Poly): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in p.xs.indices) {
            l = minOf(l, p.xs[i]); r = maxOf(r, p.xs[i])
            t = minOf(t, p.ys[i]); b = maxOf(b, p.ys[i])
        }
        return floatArrayOf(l, t, r, b)
    }

    @Test
    fun tileOutlineIsEmittedFirstInTileColourSpanningTheTile() {
        val tile = polys("SH_CIRCLE")[0]
        assertEquals(tileArgb, tile.argb)
        assertEquals(4 * (g.TILE_ARC_STEPS + 1), tile.xs.size)
        val bb = bbox(tile)
        assertEquals(0f, bb[0], 0.01f)
        assertEquals(0f, bb[1], 0.01f)
        assertEquals(tilePx, bb[2], 0.01f)
        assertEquals(tilePx, bb[3], 0.01f)
    }

    @Test
    fun circleGlyphSitsOnTheGlyphBandCentredInTheTile() {
        val glyph = polys("SH_CIRCLE")[1]
        assertEquals(glyphArgb, glyph.argb)
        // Unit ±1 maps to [0.06, 0.94] of the tile (GLYPH_HALF law).
        for (i in glyph.xs.indices) {
            assertTrue(glyph.xs[i] >= 11.9f && glyph.xs[i] <= 188.1f)
            assertTrue(glyph.ys[i] >= 11.9f && glyph.ys[i] <= 188.1f)
        }
        val bb = bbox(glyph)
        assertEquals(176f, bb[2] - bb[0], 4f)
        assertEquals(100f, (bb[0] + bb[2]) / 2f, 2f)
        assertEquals(100f, (bb[1] + bb[3]) / 2f, 2f)
    }

    @Test
    fun ringHoleIsPunchedAfterTheBodyInTileColourStrictlyInside() {
        val ps = polys("SH_RING")
        assertEquals(3, ps.size)
        assertEquals(glyphArgb, ps[1].argb)
        assertEquals(tileArgb, ps[2].argb)
        val body = bbox(ps[1])
        val hole = bbox(ps[2])
        assertTrue(hole[0] > body[0] && hole[1] > body[1])
        assertTrue(hole[2] < body[2] && hole[3] < body[3])
        // Body radius 0.97 of the glyph band, hole 0.58.
        assertEquals(176f * 0.97f, body[2] - body[0], 4f)
        assertEquals(176f * 0.58f, hole[2] - hole[0], 4f)
    }

    @Test
    fun star5GlyphAlternatesOuterAndInnerRadiiFromTheTop() {
        val glyph = polys("SH_STAR5")[1]
        assertEquals(glyphArgb, glyph.argb)
        assertEquals(10, glyph.xs.size)
        for (i in glyph.xs.indices) {
            val d = kotlin.math.hypot(glyph.xs[i] - 100f, glyph.ys[i] - 100f)
            val expected = if (i % 2 == 0) 88f else 88f * 0.45f
            assertEquals("vertex $i radius", expected, d, 1.5f)
        }
        // First vertex: the top outer point.
        assertEquals(100f, glyph.xs[0], 1.5f)
        assertEquals(12f, glyph.ys[0], 1.5f)
    }

    @Test
    fun glyphColourContrastsTheTileColour() {
        fun lum(argb: Int): Float {
            val r = (argb shr 16) and 0xFF
            val gg = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return (0.2126f * r + 0.7152f * gg + 0.0722f * b) / 255f
        }
        assertTrue(lum(glyphArgb) > 0.85f)
        assertTrue(lum(tileArgb) < 0.15f)
        assertTrue(lum(glyphArgb) - lum(tileArgb) > 0.75f)
    }

    @Test
    fun everyCatalogCardEmitsTileFirstAndKeepsGlyphPointsInCard() {
        for (def in ShapeCatalog.all) {
            val ps = g.cardPolygons(def, tilePx, cornerPx, glyphArgb, tileArgb)
            assertEquals(def.pattern, def.subs.size + 1, ps.size)
            assertEquals(def.pattern, tileArgb, ps[0].argb)
            var sawBody = false
            for (p in ps.drop(1)) {
                if (p.argb == glyphArgb) sawBody = true
                for (i in p.xs.indices) {
                    assertTrue(def.pattern, p.xs[i] >= 0f && p.xs[i] <= tilePx)
                    assertTrue(def.pattern, p.ys[i] >= 0f && p.ys[i] <= tilePx)
                }
            }
            assertTrue(def.pattern, sawBody)
        }
    }
}
