package com.example

import com.example.model.EditorGeometry
import com.example.model.TextEditGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Build 266 — pins the Text editor screen's layout law
 * ([TextEditGeometry]): the top bar's anchors, the preview's frame
 * width, the segmented rows tiling their row exactly, the swatch /
 * preset strips' cumulative layout, and the chip strip's hug-content
 * widths (via the shared pill-strip law).
 */
class TextEditGeometryTest {

    private val densities = listOf(1f, 2f, 2.625f, 3.5f)
    private val eps = 0.01f

    @Test
    fun topBarAnchorsStayInsideTheBar() {
        for (d in densities) {
            val g = TextEditGeometry.TopBar
            // The back box sits inside the bar's height and padding.
            assertTrue(g.BACK_BOX_DP < g.H_DP)
            assertEquals(
                (TextEditGeometry.SCREEN_PAD_H_DP + g.BACK_BOX_DP) * d,
                g.backCentreXPx(d) + g.BACK_BOX_DP * d / 2f, eps
            )
            // The title starts right after the back box.
            assertEquals(
                g.backCentreXPx(d) + g.BACK_BOX_DP * d / 2f + 4f * d,
                g.titleLeftPx(d), eps
            )
            // The Done pill's right edge is the screen padding.
            val rowW = 360f * d
            val pillW = EditorGeometry.ScreenChrome.pillWPx(80f, d)
            assertEquals(
                rowW - TextEditGeometry.SCREEN_PAD_H_DP * d,
                g.doneCentreXPx(rowW, pillW, d) + pillW / 2f, eps
            )
            assertEquals(g.H_DP * d / 2f, g.centreYPx(d), eps)
        }
    }

    @Test
    fun previewFrameIsThePaddedScreenWidth() {
        for (d in densities) {
            val screenW = 360f * d
            val frame = TextEditGeometry.Preview.frameWidthPx(screenW, d)
            assertEquals(screenW - 2f * TextEditGeometry.SCREEN_PAD_H_DP * d, frame, eps)
            assertTrue("frame must stay positive", frame > 0f)
        }
    }

    @Test
    fun segmentedCellsTileTheRowExactly() {
        val g = TextEditGeometry.Segmented
        for (d in densities) {
            for (count in listOf(1, 2, 3, 4)) {
                val rowW = 328f * d
                val cellW = g.cellWPx(rowW, count, d)
                assertTrue("cell width positive (count=$count)", cellW > 0f)
                // First cell at 0; last cell's right edge is the row width.
                assertEquals(0f, g.cellLeftPx(0, rowW, count, d), eps)
                val lastRight = g.cellLeftPx(count - 1, rowW, count, d) + cellW
                assertEquals("cells tile the row (count=$count)", rowW, lastRight, eps)
                // Cells are ordered and never overlap.
                for (i in 1 until count) {
                    val prevRight = g.cellLeftPx(i - 1, rowW, count, d) + cellW
                    assertTrue(g.cellLeftPx(i, rowW, count, d) >= prevRight - eps)
                }
                // Centre is the cell's middle.
                assertEquals(
                    g.cellLeftPx(0, rowW, count, d) + cellW / 2f,
                    g.cellCentreXPx(0, rowW, count, d), eps
                )
            }
        }
    }

    @Test
    fun segmentedRunIsCentredInItsCell() {
        val g = TextEditGeometry.Segmented
        val d = 2f
        val rowW = 328f * d
        val cellW = g.cellWPx(rowW, 3, d)
        // Label-only run: the label's centre is the cell's centre.
        val labelAdv = 60f
        val left = g.runLeftPx(0f, cellW, labelAdv, hasIcon = false, density = d)
        assertEquals(cellW / 2f, left + labelAdv / 2f, eps)
        // Icon+label run: the whole run (icon + gap + label) is centred.
        val runW = labelAdv + (g.ICON_DP + g.ICON_GAP_DP) * d
        val left2 = g.runLeftPx(0f, cellW, labelAdv, hasIcon = true, density = d)
        assertEquals(cellW / 2f, left2 + runW / 2f, eps)
    }

    @Test
    fun swatchStripLayoutIsCumulative() {
        val g = TextEditGeometry.Swatches
        for (d in densities) {
            assertEquals(0f, g.contentWPx(0, d), eps)
            assertEquals(g.CELL_DP * d, g.contentWPx(1, d), eps)
            val n = 12
            // Last swatch's right edge is the content width.
            assertEquals(
                g.contentWPx(n, d),
                g.leftPx(n - 1, d) + g.CELL_DP * d, eps
            )
            // Centres march by cell + gap.
            for (i in 1 until n) {
                assertEquals(
                    (g.CELL_DP + g.GAP_DP) * d,
                    g.centreXPx(i, d) - g.centreXPx(i - 1, d), eps
                )
            }
        }
    }

    @Test
    fun presetStripLayoutIsCumulative() {
        val g = TextEditGeometry.Presets
        for (d in densities) {
            assertEquals(0f, g.contentWPx(0, d), eps)
            val n = 10
            assertEquals(
                g.contentWPx(n, d),
                g.leftPx(n - 1, d) + g.TILE_DP * d, eps
            )
            assertEquals(
                g.leftPx(2, d) + g.TILE_DP * d / 2f,
                g.centreXPx(2, d), eps
            )
        }
    }

    @Test
    fun chipStripWidthsAreHugContent() {
        val g = TextEditGeometry.Chips
        val d = 2.625f
        val advances = listOf(52f, 118f, 30f, 84f)
        val widths = advances.map { g.chipWPx(it, d) }
        // Each chip is exactly its advance + both paddings.
        advances.forEachIndexed { i, adv ->
            assertEquals(adv + 2f * g.PAD_H_DP * d, widths[i], eps)
        }
        // Lefts are cumulative with the strip gap; content width is
        // the last chip's right edge.
        val lefts = g.leftsPx(widths, d)
        assertEquals(0f, lefts[0], eps)
        for (i in 1 until widths.size) {
            assertEquals(lefts[i - 1] + widths[i - 1] + g.STRIP_GAP_DP * d, lefts[i], eps)
        }
        assertEquals(lefts.last() + widths.last(), g.contentWPx(widths, d), eps)
        assertTrue(abs(g.contentWPx(widths, d) - (widths.sum() + g.STRIP_GAP_DP * d * (widths.size - 1))) < eps)
    }
}
