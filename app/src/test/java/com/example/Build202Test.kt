package com.example

import com.example.model.ToolRowSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 202 — "More tools" (Sarowar 2026-11-15 ~10:04, scope corrected
 * ~10:24): ALL tool buttons under the canvas live behind ONE More button at
 * the end of the play-controls transport row. The clip panels' primary rows
 * keep only the tools that fit (no scrolling, no per-panel More button);
 * the shared panel above the transport row holds the utility controls
 * (IN/OUT, beat, marker, onion, scopes, snap, canvas) plus the selected
 * panel's overflow tools in their own section.
 *
 * The pure piece pinned here is [ToolRowSplit]: the width model, the
 * visible/overflow split at the 360 dp budget for every panel shape the
 * footer slot renders, and the shared panel's height law. The Compose
 * layout itself is proven by the build's 360 dp proof renders
 * (ui-redesign/proof-more-tools.png), not by this suite.
 *
 * The label lists below are the panels' REAL orders (ClipEditToolsPanel /
 * AudioClipToolsPanel construction order), so a future tool added to a
 * panel without re-checking the budget fails here first.
 */
class Build202Test {

    /** A full visual layer's tools, in ClipEditToolsPanel's construction order (30). */
    private val videoLabels = listOf(
        "Effects", "Animations", "Color", "Transform", "Crop", "Blend",
        "Copy keyframes", "Paste keyframes", "Copy effects", "Paste effects",
        "Volume", "Masks", "Cutout", "Roto", "Track", "Remove", "Stabilize",
        "Captions", "Face AI", "Face swap", "Parent", "Save as Template",
        "Freeze", "Rename", "Lock", "Solo", "Label", "Clear keys",
        "Copy layer", "Paste layer"
    )

    /** A text layer's tools ("Edit" leads the split). */
    private val textLabels = listOf(
        "Edit", "Effects", "Animations", "Color", "Transform", "Crop", "Blend",
        "Copy keyframes", "Paste keyframes", "Copy effects", "Paste effects",
        "Masks", "Parent", "Save as Template", "Rename", "Lock", "Solo",
        "Label", "Clear keys", "Copy layer", "Paste layer"
    )

    /** A group layer's tools ("Edit group" leads, "Ungroup" follows Parent). */
    private val groupLabels = listOf(
        "Edit group", "Effects", "Animations", "Color", "Transform", "Crop",
        "Blend", "Copy keyframes", "Paste keyframes", "Copy effects",
        "Paste effects", "Masks", "Parent", "Ungroup", "Save as Template",
        "Rename", "Lock", "Solo", "Label", "Clear keys", "Copy layer",
        "Paste layer"
    )

    /** A PATH shape's tools ("Edit points" leads the split). */
    private val pathLabels = listOf(
        "Edit points", "Effects", "Animations", "Color", "Transform", "Crop",
        "Blend", "Copy keyframes", "Paste keyframes", "Copy effects",
        "Paste effects", "Masks", "Parent", "Save as Template", "Rename",
        "Lock", "Solo", "Label", "Clear keys", "Copy layer", "Paste layer"
    )

    /** A full audio layer's tools, in AudioClipToolsPanel's construction order (14). */
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
    fun split_videoLayer_fiveVisible() {
        val n = ToolRowSplit.visibleCountForLabels(videoLabels, 360)
        assertEquals(5, n) // Effects, Animations, Color, Transform, Crop
        assertEquals(25, videoLabels.size - n)
    }

    @Test
    fun split_textLayer_fiveVisible() {
        val n = ToolRowSplit.visibleCountForLabels(textLabels, 360)
        assertEquals(5, n) // Edit, Effects, Animations, Color, Transform
    }

    @Test
    fun split_groupLayer_fourVisible() {
        val n = ToolRowSplit.visibleCountForLabels(groupLabels, 360)
        assertEquals(4, n) // Edit group, Effects, Animations, Color
    }

    @Test
    fun split_pathShapeLayer_fourVisible() {
        val n = ToolRowSplit.visibleCountForLabels(pathLabels, 360)
        assertEquals(4, n) // Edit points, Effects, Animations, Color
    }

    @Test
    fun split_controllerSet_allFitNoOverflow() {
        // The Controller's five tools (panel order) fit whole: 54+72+41+66+40 + 4*4 = 289 <= 295.
        val labels = listOf("Effects", "Animations", "Color", "Transform", "Blend")
        assertEquals(labels.size, ToolRowSplit.visibleCountForLabels(labels, 360))
    }

    @Test
    fun split_nullCameraSet_allFitNoOverflow() {
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
    fun split_chainPanel_fourVisible() {
        // Fixed 62 dp tiles, gap 6, available 283: 62, 130, 198, 266 fit; the 5th (334) does not.
        val n = ToolRowSplit.visibleCount(
            widthsDp = List(9) { 62 },
            availableDp = ToolRowSplit.availableDp(360, contentPaddingDp = 8),
            gapDp = 6
        )
        assertEquals(4, n) // Media, Text, Shape, Audio
    }

    // ---------- split edges (plain prefix fit — nothing is reserved) ----------

    @Test
    fun split_edges() {
        assertEquals(0, ToolRowSplit.visibleCount(emptyList(), 295, 4))
        // Everything fits exactly -> all visible.
        assertEquals(2, ToolRowSplit.visibleCount(listOf(40, 40), 84, 4))
        // One tile wider than the row -> 0 visible (it lives in the panel).
        assertEquals(0, ToolRowSplit.visibleCount(listOf(200), 100, 4))
        // Exact fit counts; 1 dp over drops the last tile.
        assertEquals(3, ToolRowSplit.visibleCount(listOf(100, 100, 100), 308, 4))
        assertEquals(2, ToolRowSplit.visibleCount(listOf(100, 100, 101), 308, 4))
    }

    @Test
    fun split_widerScreen_neverShowsFewer() {
        val at320 = ToolRowSplit.visibleCountForLabels(videoLabels, 320)
        val at360 = ToolRowSplit.visibleCountForLabels(videoLabels, 360)
        val at480 = ToolRowSplit.visibleCountForLabels(videoLabels, 480)
        assertEquals(4, at320)
        assertEquals(5, at360)
        assertEquals(6, at480)
        assertTrue(at320 <= at360 && at360 <= at480)
    }

    // ---------- the grid + shared panel height laws ----------

    @Test
    fun gridHeight_rowsAndCap() {
        assertEquals(0, ToolRowSplit.gridHeightDp(0))
        assertEquals(60, ToolRowSplit.gridHeightDp(1))   // 1 row: 48 + 12 padding
        assertEquals(60, ToolRowSplit.gridHeightDp(5))   // still 1 row
        assertEquals(112, ToolRowSplit.gridHeightDp(9))  // 2 rows
        assertEquals(164, ToolRowSplit.gridHeightDp(25)) // capped at 3 rows (scrolls past)
        assertEquals(164, ToolRowSplit.gridHeightDp(26))
        // Chain geometry: 62 dp tiles, gap 6 -> 6 items = 2 rows = 142.
        assertEquals(142, ToolRowSplit.gridHeightDp(6, tileHeightDp = 62, gapDp = 6))
        // The shared panel's sections run pad-free: 8 utility tiles = 2 rows = 100.
        assertEquals(100, ToolRowSplit.gridHeightDp(8, vPaddingDp = 0))
    }

    @Test
    fun sharedPanelHeight_sections() {
        // Utility only: 12 panel padding + 100 utility grid.
        assertEquals(112, ToolRowSplit.sharedPanelHeightDp(0))
        // + divider 13 + one clip row 48.
        assertEquals(173, ToolRowSplit.sharedPanelHeightDp(3))
        // + divider 13 + the 2 shown clip rows 100 (scrolls past that).
        assertEquals(225, ToolRowSplit.sharedPanelHeightDp(9))
        assertEquals(225, ToolRowSplit.sharedPanelHeightDp(25))
        assertEquals(8, ToolRowSplit.UTILITY_TILE_COUNT)
    }
}
