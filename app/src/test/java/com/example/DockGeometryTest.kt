package com.example

import com.example.model.DockGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 249 (UI migration stage 2) — pins the dock's layout law
 * ([DockGeometry]) against fixtures hand-derived from BottomToolbar's
 * build-248 Compose layout: content width, tile pitch, column centring,
 * glyph centring, the label baseline, the label box/clip rule (including
 * an over-wide label clipped at the column's right edge) and the
 * press-scale centre. Fixtures use density 2 so every expected value is
 * exact in float arithmetic.
 */
class DockGeometryTest {

    private val d = 2f
    private val labelH = 23f // a 9.5 sp label's layout height at density 2

    @Test
    fun contentWidthCoversTwelveTiles() {
        assertEquals(536f, DockGeometry.contentWidthDp(), 0f)
        assertEquals(96f, DockGeometry.contentWidthDp(2), 0f)
        assertEquals(0f, DockGeometry.contentWidthDp(0), 0f)
    }

    @Test
    fun tilePitchIsTilePlusGap() {
        assertEquals(16f, DockGeometry.tileLeftPx(0, d), 0f)
        assertEquals(280f, DockGeometry.tileLeftPx(3, d), 0f)
        assertEquals(984f, DockGeometry.tileLeftPx(11, d), 0f)
    }

    @Test
    fun columnIsCentredVertically() {
        assertEquals(99f, DockGeometry.columnHeightPx(labelH, d), 0f)
        assertEquals(20.5f, DockGeometry.columnTopPx(140f, labelH, d), 0f)
    }

    @Test
    fun glyphIsCentredInTileBox() {
        // 15 dp glyph in the first tile (left edge 16 px, column top 20.5 px).
        assertEquals(37f, DockGeometry.glyphLeftPx(16f, 15f, d), 0f)
        // 16 dp glyph box top.
        assertEquals(40.5f, DockGeometry.glyphTopPx(20.5f, 16f, d), 0f)
    }

    @Test
    fun labelBaselineSitsUnderTilePlusGap() {
        assertEquals(118.5f, DockGeometry.labelBaselinePx(20.5f, d, -22f), 0f)
    }

    @Test
    fun fittingLabelIsCentredAndFullyVisible() {
        // advance 40 px in a 72 px column: box centred, whole rect drawn.
        val (rectLeft, srcW) = DockGeometry.labelBlit(
            tileLeftPx = 16f, advancePx = 40f, bearingPx = 1f,
            padPx = 2f, rectWPx = 44f, density = d
        )
        assertEquals(31f, rectLeft, 0f)
        assertEquals(44f, srcW, 0f)
    }

    @Test
    fun overWideLabelStartsAtColumnLeftAndClipsAtRightEdge() {
        // "Video/Photo"-class label: advance 110 px > 72 px column.
        val (rectLeft, srcW) = DockGeometry.labelBlit(
            tileLeftPx = 16f, advancePx = 110f, bearingPx = 0f,
            padPx = 2f, rectWPx = 114f, density = d
        )
        assertEquals(14f, rectLeft, 0f)
        assertEquals(74f, srcW, 0f) // 14 + 74 = 88 = the column's right edge
    }

    @Test
    fun pressCentreIsColumnCentre() {
        assertEquals(52f, DockGeometry.pressCenterXPx(16f, d), 0f)
        assertEquals(70f, DockGeometry.pressCenterYPx(20.5f, labelH, d), 0f)
    }

    @Test
    fun glyphTableMatchesBuild248Sizes() {
        assertEquals(12, DockGeometry.GLYPH_DP.size)
        assertEquals(15f, DockGeometry.GLYPH_DP[0], 0f)  // media
        assertEquals(16f, DockGeometry.GLYPH_DP[2], 0f)  // fx
        assertEquals(15f, DockGeometry.GLYPH_DP[9], 0f)  // camera (icon)
        assertEquals(16f, DockGeometry.GLYPH_DP[11], 0f) // chain
    }
}
