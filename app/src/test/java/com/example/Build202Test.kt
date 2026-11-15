package com.example

import com.example.model.ToolRowSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 202 — "More tools" (Sarowar 2026-11-15: clean the tool rows under
 * the canvas; one More button shows the rest). The pure piece pinned here is
 * [ToolRowSplit]: the width model, the visible/overflow split at the 360 dp
 * budget for every panel shape the footer slot renders, and the expanded
 * grid's height law. The Compose layout itself is proven by the build's
 * 360 dp proof renders (ui-redesign/proof-more-tools.png), not by this suite.
 *
 * The label lists below are the panels' REAL orders (ClipEditToolsPanel /
 * AudioClipToolsPanel construction order), so a future tool added to a panel
 * without re-checking the budget fails here first.
 */
class Build202Test {

    /** A full video layer's tools, in ClipEditToolsPanel's construction order. */
    private val videoLabels = listOf(
        "Effects", "Animations", "Color", "Transform", "Crop", "Blend",
        "Copy keyframes", "Paste keyframes", "Copy effects", "Paste effects",
        "Volume", "Masks", "Cutout", "Roto", "Track", "Remove", "Stabilize",
        "Captions", "Face AI", "Face swap", "Parent", "Freeze", "Rename",
        "Lock", "Solo", "Label", "Clear keys", "Copy layer", "Paste layer"
    )

    /** A full audio layer's tools, in AudioClipToolsPanel's construction order. */
    private val audioLabels = listOf(
        "Volume", "Fade", "Speed", "Reverse", "Beats", "Ducking", "Noise",
        "Captions", "Rename", "Lock", "Solo", "Label", "Copy layer", "Paste layer"
    )

    // ---------- the width model ----------

    @Test
    fun tileWidth_modelValues() {
        // Floor: short labels never drop under the 40 dp touch-target floor.
        assertEquals(40, ToolRowSplit.tileWidthDp("More"))
        assertEquals(40, ToolRowSplit.tileWidthDp("Crop"))
        assertEquals(40, ToolRowSplit.tileWidthDp(""))
        // 8 padding + ceil(chars * 6.2) + 2 safety.
        assertEquals(54, ToolRowSplit.tileWidthDp("Effects"))       // 8 + 44 + 2
        assertEquals(72, ToolRowSplit.tileWidthDp("Animations"))    // 8 + 62 + 2
        assertEquals(66, ToolRowSplit.tileWidthDp("Transform"))     // 8 + 56 + 2
        assertEquals(79, ToolRowSplit.tileWidthDp("Edit points"))   // 8 + 69 + 2
        assertEquals(103, ToolRowSplit.tileWidthDp("Paste keyframes")) // 8 + 93 + 2
    }

    @Test
    fun available_at360_is295() {
        // 360 - 61 back zone - 2*2 content padding.
        assertEquals(295, ToolRowSplit.availableDp(360))
        // Chain geometry: content padding 8 per side.
        assertEquals(283, ToolRowSplit.availableDp(360, contentPaddingDp = 8))
    }

    // ---------- the split at 360 dp, per panel shape ----------

    @Test
    fun split_videoLayer_fourVisible() {
        val n = ToolRowSplit.visibleCountForLabels(videoLabels, 360)
        assertEquals(4, n) // Effects, Animations, Color, Transform
        assertEquals(25, videoLabels.size - n)
    }

    @Test
    fun split_textLayer_fourVisible() {
        val labels = listOf("Edit") + videoLabels
        assertEquals(4, ToolRowSplit.visibleCountForLabels(labels, 360))
    }

    @Test
    fun split_groupLayer_fourVisible() {
        // Edit group (72) + Effects + Animations + Color lands exactly on 295.
        val labels = listOf("Edit group") + videoLabels
        assertEquals(4, ToolRowSplit.visibleCountForLabels(labels, 360))
    }

    @Test
    fun split_pathShapeLayer_threeVisible() {
        // "Edit points" is the widest prefix tile: only 3 fit with More reserved.
        val labels = listOf("Edit points") + videoLabels
        assertEquals(3, ToolRowSplit.visibleCountForLabels(labels, 360))
    }

    @Test
    fun split_controllerSet_allFitNoMore() {
        // The Controller's five tools (panel order) fit whole: 274 + 4*4 = 290 <= 295.
        val labels = listOf("Effects", "Animations", "Color", "Transform", "Blend")
        assertEquals(labels.size, ToolRowSplit.visibleCountForLabels(labels, 360))
    }

    @Test
    fun split_nullCameraSet_allFitNoMore() {
        val labels = listOf("Transform", "Parent", "Rename")
        assertEquals(labels.size, ToolRowSplit.visibleCountForLabels(labels, 360))
    }

    @Test
    fun split_audioLayer_fiveVisible() {
        val n = ToolRowSplit.visibleCountForLabels(audioLabels, 360)
        assertEquals(5, n) // Volume, Fade, Speed, Reverse, Beats
        assertEquals(9, audioLabels.size - n)
    }

    @Test
    fun split_chainPanel_threeVisible() {
        // Fixed 62 dp tiles, gap 6, More = one more 62 dp tile, available 283.
        val n = ToolRowSplit.visibleCount(
            widthsDp = List(9) { 62 },
            availableDp = ToolRowSplit.availableDp(360, contentPaddingDp = 8),
            gapDp = 6,
            moreWidthDp = 62
        )
        assertEquals(3, n) // Media, Text, Shape
    }

    // ---------- split edges ----------

    @Test
    fun split_edges() {
        assertEquals(0, ToolRowSplit.visibleCount(emptyList(), 295, 4, 40))
        // Everything fits exactly -> all visible, no More reserved.
        assertEquals(2, ToolRowSplit.visibleCount(listOf(40, 40), 84, 4, 40))
        // One tile wider than the row -> 0 visible, More alone.
        assertEquals(0, ToolRowSplit.visibleCount(listOf(200), 100, 4, 40))
        // All-fit by the raw sum, but if a More HAD to be reserved the third
        // tile would push out: with 101 instead of 100 the sum overflows and
        // the split drops to 2 + More.
        assertEquals(3, ToolRowSplit.visibleCount(listOf(100, 100, 100), 308, 4, 40))
        assertEquals(2, ToolRowSplit.visibleCount(listOf(100, 100, 101), 308, 4, 40))
    }

    @Test
    fun split_widerScreen_neverShowsFewer() {
        val at320 = ToolRowSplit.visibleCountForLabels(videoLabels, 320)
        val at360 = ToolRowSplit.visibleCountForLabels(videoLabels, 360)
        val at480 = ToolRowSplit.visibleCountForLabels(videoLabels, 480)
        assertEquals(3, at320)
        assertEquals(4, at360)
        assertEquals(6, at480)
        assertTrue(at320 <= at360 && at360 <= at480)
    }

    // ---------- the grid height law ----------

    @Test
    fun gridHeight_rowsAndCap() {
        assertEquals(0, ToolRowSplit.gridHeightDp(0))
        assertEquals(60, ToolRowSplit.gridHeightDp(1))   // 1 row: 48 + 12 padding
        assertEquals(60, ToolRowSplit.gridHeightDp(5))   // still 1 row
        assertEquals(112, ToolRowSplit.gridHeightDp(9))  // audio overflow: 2 rows
        assertEquals(164, ToolRowSplit.gridHeightDp(25)) // video overflow: capped at 3 rows
        assertEquals(164, ToolRowSplit.gridHeightDp(26))
        // Chain geometry: 62 dp tiles, gap 6 -> 6 items = 2 rows = 142.
        assertEquals(142, ToolRowSplit.gridHeightDp(6, tileHeightDp = 62, gapDp = 6))
    }
}
