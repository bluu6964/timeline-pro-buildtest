package com.example

import com.example.model.DockGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 249 (UI migration stage 2) — pins the dock's layout law
 * ([DockGeometry]) against fixtures hand-derived from BottomToolbar's
 * build-248 Compose layout: content width, tile pitch, column centring,
 * glyph centring, the label baseline and the press-scale centre.
 * Build 257 updates the fixtures to Sarowar's exact dock numbers —
 * tiles 36 → 44 dp, glyphs +40% (15 → 21, 16 → 22.4) — and replaces
 * the label box/clip rule with the auto-fit rule: an over-wide label
 * shrinks to fit its tile, it is never clipped mid-word again.
 * Fixtures use density 2 so every expected value is exact in float
 * arithmetic (except where a delta is noted).
 */
class DockGeometryTest {

    private val d = 2f
    private val labelH = 23f // a 9.5 sp label's layout height at density 2

    @Test
    fun contentWidthCoversTwelveTiles() {
        assertEquals(632f, DockGeometry.contentWidthDp(), 0f)
        assertEquals(112f, DockGeometry.contentWidthDp(2), 0f)
        assertEquals(0f, DockGeometry.contentWidthDp(0), 0f)
    }

    @Test
    fun tilePitchIsTilePlusGap() {
        assertEquals(16f, DockGeometry.tileLeftPx(0, d), 0f)
        assertEquals(328f, DockGeometry.tileLeftPx(3, d), 0f)
        assertEquals(1160f, DockGeometry.tileLeftPx(11, d), 0f)
    }

    @Test
    fun columnIsCentredVertically() {
        assertEquals(115f, DockGeometry.columnHeightPx(labelH, d), 0f)
        assertEquals(12.5f, DockGeometry.columnTopPx(140f, labelH, d), 0f)
    }

    @Test
    fun glyphIsCentredInTileBox() {
        // 21 dp glyph in the first tile (left edge 16 px, column top 12.5 px).
        assertEquals(39f, DockGeometry.glyphLeftPx(16f, 21f, d), 0f)
        // 22.4 dp glyph box top.
        assertEquals(34.1f, DockGeometry.glyphTopPx(12.5f, 22.4f, d), 0.001f)
    }

    @Test
    fun labelBaselineSitsUnderTilePlusGap() {
        assertEquals(126.5f, DockGeometry.labelBaselinePx(12.5f, d, -22f), 0f)
    }

    @Test
    fun fittingLabelIsCentredAndFullyVisible() {
        // advance 40 px in an 88 px column: scale 1, box centred, whole rect drawn.
        assertEquals(1f, DockGeometry.labelFitScale(40f, d), 0f)
        val (rectLeft, dstW, dstH) = DockGeometry.labelBlit(
            tileLeftPx = 16f, advancePx = 40f, bearingPx = 1f,
            padPx = 2f, rectWPx = 44f, rectHPx = 30f, density = d
        )
        assertEquals(39f, rectLeft, 0f)
        assertEquals(44f, dstW, 0f)
        assertEquals(30f, dstH, 0f)
    }

    @Test
    fun overWideLabelShrinksToFitInsteadOfClipping() {
        // "Video/Photo"-class label: advance 110 px > 88 px tile.
        assertEquals(0.8f, DockGeometry.labelFitScale(110f, d), 0.001f)
        val (rectLeft, dstW, dstH) = DockGeometry.labelBlit(
            tileLeftPx = 16f, advancePx = 110f, bearingPx = 0f,
            padPx = 2f, rectWPx = 114f, rectHPx = 30f, density = d
        )
        assertEquals(14.4f, rectLeft, 0.01f)
        assertEquals(91.2f, dstW, 0.01f) // the FULL rect, shrunk — nothing clipped
        assertEquals(24f, dstH, 0.01f)
    }

    @Test
    fun pressCentreIsColumnCentre() {
        assertEquals(60f, DockGeometry.pressCenterXPx(16f, d), 0f)
        assertEquals(70f, DockGeometry.pressCenterYPx(12.5f, labelH, d), 0f)
    }

    @Test
    fun fxStarEntryIsTheBigStarsDiameter() {
        // fx glyph box at density 2: 22.4 dp x 2 = 44.8 px; the big star's
        // diameter is 15/25 of the box = 26.88 px -> rasterized at 27 px.
        assertEquals(27, DockGeometry.fxStarEntryPx(44.8f))
        assertEquals(1, DockGeometry.fxStarEntryPx(0.5f)) // never zero-sized
    }

    @Test
    fun fxStarBlitsLandOnTheBuild248StarPositions() {
        // Glyph box: left 100, top 50, edge 44.8 px -> u = 44.8 / 25 = 1.792 px.
        val (sl, st, ss) = DockGeometry.fxStarBlit(
            100f, 50f, 44.8f,
            DockGeometry.FX_STAR_SMALL_C, DockGeometry.FX_STAR_SMALL_C, DockGeometry.FX_STAR_SMALL_R
        )
        // Small star: centre (8.5, 8.5) r 5.5 -> ink from (3, 3) u, size 11 u.
        assertEquals(100f + 3f * 1.792f, sl, 0.01f)
        assertEquals(50f + 3f * 1.792f, st, 0.01f)
        assertEquals(11f * 1.792f, ss, 0.01f)
        val (bl, bt, bs) = DockGeometry.fxStarBlit(
            100f, 50f, 44.8f,
            DockGeometry.FX_STAR_BIG_C, DockGeometry.FX_STAR_BIG_C, DockGeometry.FX_STAR_BIG_R
        )
        // Big star: centre (16, 16) r 7.5 -> ink from (8.5, 8.5) u, size 15 u;
        // its right edge lands at (16 + 7.5) = 23.5 units of the 25-unit grid.
        assertEquals(100f + 8.5f * 1.792f, bl, 0.01f)
        assertEquals(50f + 8.5f * 1.792f, bt, 0.01f)
        assertEquals(15f * 1.792f, bs, 0.01f)
        assertEquals(100f + 23.5f * 1.792f, bl + bs, 0.01f)
    }

    @Test
    fun glyphTableScalesBuild248SizesByFortyPercentTwice() {
        // Build 257: 15 -> 21, 16 -> 22.4 (x1.4). Build 260 (Sarowar's
        // order): x1.4 again -> 29.4 / 31.4.
        assertEquals(12, DockGeometry.GLYPH_DP.size)
        assertEquals(29.4f, DockGeometry.GLYPH_DP[0], 0.001f) // media: 21 x 1.4
        assertEquals(31.4f, DockGeometry.GLYPH_DP[2], 0.001f) // fx: 22.4 x 1.4
        assertEquals(29.4f, DockGeometry.GLYPH_DP[9], 0.001f) // camera (icon): 21 x 1.4
        assertEquals(31.4f, DockGeometry.GLYPH_DP[11], 0.001f) // chain: 22.4 x 1.4
    }
}
