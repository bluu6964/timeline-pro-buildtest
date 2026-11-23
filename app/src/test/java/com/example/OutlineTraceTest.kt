package com.example

import com.example.model.OutlineTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Build 262 — the extrusion silhouette law ([OutlineTrace]): a filled rect
 * traces to ONE contour of the rect's area, a ring to TWO (outer + hole),
 * separate blobs to separate contours, and empty alpha to none.
 */
class OutlineTraceTest {
    private fun alpha(w: Int, h: Int, fill: (Int, Int) -> Boolean): IntArray {
        val a = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) if (fill(x, y)) a[y * w + x] = 255
        return a
    }

    @Test
    fun filledRectIsOneContourOfTheRectsArea() {
        // Rect x 5..24, y 5..14 → 20×10 px.
        val a = alpha(40, 30) { x, y -> x in 5..24 && y in 5..14 }
        val contours = OutlineTrace.trace(a, 40, 30, 0, 0, 40, 30)
        assertEquals(1, contours.size)
        val area = abs(OutlineTrace.signedArea(contours[0]))
        assertTrue("area $area ≈ 200", area in 170f..230f)
        // Every point sits on the rect's boundary band.
        val pts = contours[0]
        for (i in 0 until pts.size / 2) {
            val x = pts[i * 2]; val y = pts[i * 2 + 1]
            assertTrue("x $x on boundary", x in 4.4f..5.6f || x in 24.4f..25.6f || y in 4.4f..5.6f || y in 14.4f..15.6f)
        }
    }

    @Test
    fun ringIsTwoContours() {
        // 30×30 block with a 10×10 hole in the middle.
        val a = alpha(40, 40) { x, y ->
            (x in 5..34 && y in 5..34) && !(x in 15..24 && y in 15..24)
        }
        val contours = OutlineTrace.trace(a, 40, 40, 0, 0, 40, 40)
        assertEquals(2, contours.size)
        val outer = abs(OutlineTrace.signedArea(contours[0]))
        val hole = abs(OutlineTrace.signedArea(contours[1]))
        assertTrue("outer $outer ≈ 900", outer in 800f..1000f)
        assertTrue("hole $hole ≈ 100", hole in 70f..140f)
    }

    @Test
    fun twoBlobsAreTwoContours() {
        val a = alpha(60, 30) { x, y ->
            (x in 2..11 && y in 2..11) || (x in 40..51 && y in 15..26)
        }
        val contours = OutlineTrace.trace(a, 60, 30, 0, 0, 60, 30)
        assertEquals(2, contours.size)
    }

    @Test
    fun emptyAlphaIsNoContours() {
        val a = IntArray(20 * 20)
        assertTrue(OutlineTrace.trace(a, 20, 20, 0, 0, 20, 20).isEmpty())
    }

    @Test
    fun regionLimitsTheTrace() {
        val a = alpha(60, 30) { x, y ->
            (x in 2..11 && y in 2..11) || (x in 40..51 && y in 15..26)
        }
        // Only the left blob is inside the region.
        val contours = OutlineTrace.trace(a, 60, 30, 0, 0, 30, 30)
        assertEquals(1, contours.size)
    }

    @Test
    fun speckleIsDropped() {
        val a = alpha(30, 30) { x, y -> (x == 10 && y == 10) || (x in 15..24 && y in 15..24) }
        val contours = OutlineTrace.trace(a, 30, 30, 0, 0, 30, 30)
        assertEquals(1, contours.size)
    }
}
