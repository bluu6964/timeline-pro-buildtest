package com.example

import com.example.model.PagerGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 238 — regression guard for the page-3 pager crash. Build 237 shrank
 * page 3 of the selected-tools pager from 6 tools to 5 (Captions + Roto
 * tiles removed), and the fixed 2×3 grid in SelectedToolsPager219 read
 * index 5 of the 5-element list: ArrayIndexOutOfBoundsException
 * (length=5, index=5) on swiping to page 3.
 *
 * PagerGrid.rows must tolerate ANY tool count: every row has exactly
 * `columns` cells, the input order is preserved, and nulls (empty slots)
 * only ever pad the tail of the last row.
 */
class PagerGridTest {

    @Test
    fun fiveTools_twoRows_lastSlotEmpty() {
        // The exact build-237 page-3 list that crashed.
        val rows = PagerGrid.rows(
            listOf("Freeze", "Extract Audio", "Face Swap", "Reverse", "Replace")
        )
        assertEquals(2, rows.size)
        assertEquals(listOf("Freeze", "Extract Audio", "Face Swap"), rows[0])
        assertEquals(listOf("Reverse", "Replace", null), rows[1])
    }

    @Test
    fun sixTools_twoFullRows() {
        val rows = PagerGrid.rows((1..6).toList())
        assertEquals(2, rows.size)
        assertTrue(rows.all { row -> row.size == 3 && row.all { it != null } })
        assertEquals((1..6).toList(), rows.flatten())
    }

    @Test
    fun anySize0to8_fullWidthRows_orderPreserved_nullsOnlyTrail() {
        for (n in 0..8) {
            val items = (0 until n).toList()
            val rows = PagerGrid.rows(items)
            assertEquals("size $n row count", (n + 2) / 3, rows.size)
            rows.forEach { row -> assertEquals("size $n row width", 3, row.size) }
            assertEquals("size $n content", items, rows.flatten().filterNotNull())
            val flat = rows.flatten()
            val firstNull = flat.indexOf(null)
            if (firstNull >= 0) {
                assertTrue(
                    "size $n padding only trails",
                    flat.subList(firstNull, flat.size).all { it == null }
                )
            }
        }
    }

    @Test
    fun emptyList_noRows() {
        assertEquals(0, PagerGrid.rows(emptyList<String>()).size)
    }
}
