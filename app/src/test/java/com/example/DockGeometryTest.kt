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
    fun glyphTableScalesBuild248SizesByFortyPercent() {
        assertEquals(12, DockGeometry.GLYPH_DP.size)
        assertEquals(21f, DockGeometry.GLYPH_DP[0], 0f)    // media: 15 x 1.4
        assertEquals(22.4f, DockGeometry.GLYPH_DP[2], 0.001f) // fx: 16 x 1.4
        assertEquals(21f, DockGeometry.GLYPH_DP[9], 0f)    // camera (icon): 15 x 1.4
        assertEquals(22.4f, DockGeometry.GLYPH_DP[11], 0.001f) // chain: 16 x 1.4
    }
}
