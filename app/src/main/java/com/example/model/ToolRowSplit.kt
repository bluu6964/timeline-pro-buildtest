package com.example.model

import kotlin.math.ceil

/**
 * Build 202 — the split law behind the clip tools panels' "More tools" button
 * (Sarowar 2026-11-15: "Middle এ canvas এর নিচে একসাথে এত tool না রেখে — একটা
 * button থাকবে More tools, সেখানে click করলে বাকি tool গুলো show করবে").
 * The panels in the footer slot under the canvas (ClipEditToolsPanel,
 * AudioClipToolsPanel, ChainAddPanel) used to be one long horizontally
 * scrolling row — ~29 tools on a full video layer, the newest tools at the
 * far end of the scroll (TOOLS-AUDIT-2). Now the collapsed row shows only
 * the first tools that FIT, in the existing order, and the rest live behind
 * one "More" tile that opens them as a grid above the row.
 *
 * Everything here is pure dp arithmetic so the harness pins the 360 dp
 * budget; the panels render with THESE constants, so the law and the pixels
 * cannot drift apart.
 *
 * Width model for a label tile: the tile is bare (boxless law) — a 23 dp
 * icon over an 11 sp label, 4 dp padding each side. The label advance is
 * modelled at 6.2 dp per char: deliberately conservative (Roboto at 11 sp
 * measures ~5.4 dp/char regular, ~5.8 bold — used tools render BOLD,
 * build 166), plus a 2 dp safety per tile and a 40 dp floor (the build-201
 * touch-target law: a tile never gets narrower than 40 dp; its height is
 * the full panel height).
 */
object ToolRowSplit {

    /** Back-button zone at the row's start: 8 pad + 46 button + 6 pad + 1 divider. */
    const val BACK_ZONE_DP = 61

    /** Horizontal padding of the tools row itself, each side. */
    const val CONTENT_PADDING_DP = 2

    /** Gap between tile boxes in the collapsed row (and before the More tile). */
    const val GAP_DP = 4

    /** Total horizontal padding inside a label tile (4 dp each side). */
    const val TILE_PADDING_DP = 8

    /** Per-tile safety margin in the width model. */
    const val TILE_SAFETY_DP = 2

    /** Tile icon size; also the width floor term for very short labels. */
    const val ICON_DP = 23

    /** A tile never gets narrower than this (touch-target law, build 201). */
    const val TILE_MIN_WIDTH_DP = 40

    /** The More tile's label. */
    const val MORE_LABEL = "More"

    /** Conservative per-char label advance at 11 sp (see the header). */
    private const val CHAR_WIDTH_DP = 6.2

    /** The expanded grid: fixed columns, tile height, gaps, vertical padding, visible row cap. */
    const val GRID_COLUMNS = 5
    const val GRID_TILE_HEIGHT_DP = 48
    const val GRID_GAP_DP = 4
    const val GRID_V_PADDING_DP = 6
    const val GRID_MAX_ROWS = 3

    /** Modelled width of one label tile (see the header for the model). */
    fun tileWidthDp(label: String): Int {
        val text = ceil(label.length * CHAR_WIDTH_DP).toInt()
        return maxOf(TILE_MIN_WIDTH_DP, TILE_PADDING_DP + maxOf(ICON_DP, text) + TILE_SAFETY_DP)
    }

    /** Width available to tiles + gaps inside the row at [screenWidthDp]. */
    fun availableDp(
        screenWidthDp: Int,
        backZoneDp: Int = BACK_ZONE_DP,
        contentPaddingDp: Int = CONTENT_PADDING_DP
    ): Int = screenWidthDp - backZoneDp - 2 * contentPaddingDp

    /**
     * How many of [widthsDp] (in order) stay visible in the collapsed row.
     * If everything fits, all of them (and no More tile is shown at all).
     * Otherwise the count is the largest prefix that fits WITH the More tile
     * and its gap reserved after it.
     */
    fun visibleCount(widthsDp: List<Int>, availableDp: Int, gapDp: Int, moreWidthDp: Int): Int {
        if (widthsDp.isEmpty()) return 0
        val allWidth = widthsDp.sum() + gapDp * (widthsDp.size - 1)
        if (allWidth <= availableDp) return widthsDp.size
        var used = 0
        var n = 0
        while (n < widthsDp.size) {
            val add = widthsDp[n] + if (n == 0) 0 else gapDp
            if (used + add + gapDp + moreWidthDp > availableDp) break
            used += add
            n++
        }
        return n
    }

    /** [visibleCount] for the label-measured panels (clip + audio tools). */
    fun visibleCountForLabels(labels: List<String>, screenWidthDp: Int): Int =
        visibleCount(labels.map(::tileWidthDp), availableDp(screenWidthDp), GAP_DP, tileWidthDp(MORE_LABEL))

    /**
     * Height of the expanded grid overlay for [itemCount] tiles: whole rows
     * up to [maxRows] (the grid scrolls vertically past that), plus the row
     * gaps and the container's vertical padding.
     */
    fun gridHeightDp(
        itemCount: Int,
        tileHeightDp: Int = GRID_TILE_HEIGHT_DP,
        gapDp: Int = GRID_GAP_DP,
        vPaddingDp: Int = GRID_V_PADDING_DP,
        maxRows: Int = GRID_MAX_ROWS,
        columns: Int = GRID_COLUMNS
    ): Int {
        if (itemCount <= 0) return 0
        val rows = minOf((itemCount + columns - 1) / columns, maxRows)
        return rows * tileHeightDp + (rows - 1) * gapDp + 2 * vPaddingDp
    }
}
