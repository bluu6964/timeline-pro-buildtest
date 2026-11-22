package com.example

import com.example.model.ShapePreviewGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Build 257 — pins the Edit Shape live preview's layout law
 * ([ShapePreviewGeometry]): the screen-height split between the
 * preview slot and the panel's scrollable content (explicit heights,
 * no `weight` — the old weighted slot collapsed to zero in the
 * full-screen Dialog), the letterbox frame law the old preview used
 * inline, the 135 dp shape box, and the polygon / star generators
 * whose proportions are the C++ shape engine's own (pentagon vertices
 * at i·72° − 90°, star inner radius 0.45).
 */
class ShapePreviewGeometryTest {

    private fun fixedSum(): Float =
        ShapePreviewGeometry.HEADER_DP + ShapePreviewGeometry.TRANSPORT_DP +
            ShapePreviewGeometry.SLOT_PAD_V_DP + ShapePreviewGeometry.PANEL_CHROME_DP

    @Test
    fun budgetOnTallScreenSitsAtBothCaps() {
        val (preview, panel) = ShapePreviewGeometry.budget(900f)
        assertEquals(ShapePreviewGeometry.PREVIEW_MAX_DP, preview, 0.001f)
        assertEquals(ShapePreviewGeometry.PANEL_SCROLL_MAX_DP, panel, 0.001f)
        // Slack pools below the panel: the total stays under the screen.
        assertTrue(fixedSum() + preview + panel <= 900f)
    }

    @Test
    fun budgetOnPhoneScreenSplitsTheRemainderExactly() {
        val (preview, panel) = ShapePreviewGeometry.budget(600f)
        val avail = 600f - fixedSum()
        assertEquals(avail * 0.45f, preview, 0.001f)
        assertEquals(avail - preview, panel, 0.001f)
        assertEquals(600f, fixedSum() + preview + panel, 0.001f)
    }

    @Test
    fun budgetOnShortScreenHoldsBothFloors() {
        val (preview, panel) = ShapePreviewGeometry.budget(480f)
        assertEquals(ShapePreviewGeometry.PREVIEW_MIN_DP, preview, 0.001f)
        assertEquals(ShapePreviewGeometry.PANEL_SCROLL_MIN_DP, panel, 0.001f)
    }

    @Test
    fun budgetPreviewShareIsMonotonicInScreenHeight() {
        var prev = 0f
        for (h in listOf(520f, 600f, 700f, 800f, 900f, 1100f)) {
            val (preview, _) = ShapePreviewGeometry.budget(h)
            assertTrue("preview shrank at $h", preview >= prev)
            prev = preview
        }
    }

    @Test
    fun frameSizeLetterboxesWideAndTallSlots() {
        val ratio = 16f / 9f
        // Slot wider than the project: height rules, width = h * ratio.
        val (w1, h1) = ShapePreviewGeometry.frameSize(400f, 200f, ratio)
        assertEquals(200f * ratio, w1, 0.01f)
        assertEquals(200f, h1, 0.001f)
        // Slot narrower: width rules, height = w / ratio.
        val (w2, h2) = ShapePreviewGeometry.frameSize(300f, 200f, ratio)
        assertEquals(300f, w2, 0.001f)
        assertEquals(300f / ratio, h2, 0.01f)
        // Portrait project in a landscape slot.
        val (w3, h3) = ShapePreviewGeometry.frameSize(400f, 200f, 9f / 16f)
        assertEquals(200f * (9f / 16f), w3, 0.01f)
        assertEquals(200f, h3, 0.001f)
    }

    @Test
    fun shapeBoxScalesThe135DpBase() {
        val (w1, h1) = ShapePreviewGeometry.shapeBoxDp(100f, 100f)
        assertEquals(135f, w1, 0.001f)
        assertEquals(135f, h1, 0.001f)
        val (w2, h2) = ShapePreviewGeometry.shapeBoxDp(71f, 71f)
        assertEquals(135f * 0.71f, w2, 0.001f)
        assertEquals(135f * 0.71f, h2, 0.001f)
        val (w3, h3) = ShapePreviewGeometry.shapeBoxDp(250f, 10f)
        assertEquals(337.5f, w3, 0.001f)
        assertEquals(13.5f, h3, 0.001f)
    }

    @Test
    fun pentagonStartsAtTheTopAndRunsClockwise() {
        val pts = ShapePreviewGeometry.polygonPoints(5, 0f, 0f, 100f, 100f)
        assertEquals(10, pts.size)
        // Vertex 0 at the top.
        assertEquals(0f, pts[0], 0.01f)
        assertEquals(-100f, pts[1], 0.01f)
        // Vertex 1 at -18 degrees: right and down (clockwise, y down).
        assertEquals(95.106f, pts[2], 0.01f)
        assertEquals(-30.902f, pts[3], 0.01f)
        // Every vertex on the circle.
        for (i in 0 until 5) {
            assertEquals(100f, hypot(pts[i * 2], pts[i * 2 + 1]), 0.01f)
        }
    }

    @Test
    fun starAlternatesOuterAndInnerAtTheEngineRatio() {
        val pts = ShapePreviewGeometry.starPoints(0f, 0f, 100f, 100f)
        assertEquals(20, pts.size)
        // Vertex 0: outer, at the top.
        assertEquals(0f, pts[0], 0.01f)
        assertEquals(-100f, pts[1], 0.01f)
        for (i in 0 until 10) {
            val r = hypot(pts[i * 2], pts[i * 2 + 1])
            val expected = if (i % 2 == 1) 45f else 100f
            assertEquals("vertex $i radius", expected, r, 0.01f)
        }
        assertEquals(0.45f, ShapePreviewGeometry.STAR_INNER_RATIO, 0.0001f)
    }

    @Test
    fun polygonStretchesPerAxisWithTheBox() {
        val pts = ShapePreviewGeometry.polygonPoints(5, 10f, 20f, 200f, 50f)
        assertEquals(10f, pts[0], 0.01f)
        assertEquals(-30f, pts[1], 0.01f)
    }

    @Test
    fun featherRadiusFollowsTheSheetLaw() {
        assertEquals(0f, ShapePreviewGeometry.featherRadiusPx(0f, 2f), 0.001f)
        assertEquals(20f, ShapePreviewGeometry.featherRadiusPx(80f, 2f), 0.001f)
        assertEquals(12.5f, ShapePreviewGeometry.featherRadiusPx(100f, 1f), 0.001f)
    }
}
