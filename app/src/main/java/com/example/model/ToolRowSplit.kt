package com.example.model

import com.example.engine.ValueLawsNative

/**
 * Build 202 — the geometry law behind Sarowar's "clean the UI" order
 * (2026-11-15 ~10:04, scope corrected ~10:24): the middle section under the
 * canvas keeps only the primary tools visible; EVERYTHING else lives behind
 * ONE "More tools" button at the end of the play-controls transport row.
 *
 * The pieces this law sizes:
 *  - the clip tools panels' PRIMARY rows (ClipEditToolsPanel,
 *    AudioClipToolsPanel, ChainAddPanel): no scrolling, no per-panel More
 *    button — the first tools that FIT at the panel's width, in the existing
 *    order ([visibleCountForLabels] / [visibleCount]).
 *  - the ONE shared overflow panel over the transport row: a utility section
 *    (the build-201 utility row's IN/OUT, beat mark, marker, onion, scopes +
 *    the transport row's snap and canvas buttons, dissolved as rows in this
 *    build) and, while a clip is selected, the selected panel's overflow
 *    tools in their own section below it ([sharedPanelHeightDp]).
 *
 * Everything here is pure dp arithmetic so the harness pins the 360 dp
 * budgets; the UI renders with THESE constants, so the law and the pixels
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

    /** Back-button zone at a clip row's start: 8 pad + 46 button + 6 pad + 1 divider. */
    const val BACK_ZONE_DP = 61

    /** Horizontal padding of a clip tools row itself, each side. */
    const val CONTENT_PADDING_DP = 2

    /** Gap between tile boxes in a clip tools row. */
    const val GAP_DP = 4

    /** Total horizontal padding inside a label tile (4 dp each side). */
    const val TILE_PADDING_DP = 8

    /** Per-tile safety margin in the width model. */
    const val TILE_SAFETY_DP = 2

    /** Tile icon size; also the width floor term for very short labels. */
    const val ICON_DP = 23

    /** A tile never gets narrower than this (touch-target law, build 201). */
    const val TILE_MIN_WIDTH_DP = 40

    /** The shared panel's More tile label (the transport-row button is its glyph). */
    const val MORE_LABEL = "More"

    /** Conservative per-char label advance at 11 sp (see the header). */
    private const val CHAR_WIDTH_DP = 6.2

    /** Grid geometry (the shared panel's sections): fixed columns, tile height, gaps. */
    const val GRID_COLUMNS = 5
    const val GRID_TILE_HEIGHT_DP = 48
    const val GRID_GAP_DP = 4
    const val GRID_V_PADDING_DP = 6
    const val GRID_MAX_ROWS = 3

    /** The shared panel: vertical padding (top + bottom), the section divider block. */
    const val PANEL_V_PADDING_DP = 6
    const val PANEL_DIVIDER_DP = 13

    /** Tiles in the shared panel's utility section (IN, OUT, beat, marker, onion, scopes, snap, canvas). */
    const val UTILITY_TILE_COUNT = 8

    /** Rows of the selected panel's overflow shown inside the shared panel (it scrolls past that). */
    const val CLIP_SECTION_MAX_ROWS = 2

    // The dp laws are native since build 247 (value_laws.cpp); the label
    // length the width model reads is the string's UTF-16 length, exactly
    // Kotlin's String.length.

    /** Modelled width of one label tile (see the header for the model). */
    fun tileWidthDp(label: String): Int = ValueLawsNative.nativeToolTileWidth(label)

    /** Width available to tiles + gaps inside a row at [screenWidthDp]. */
    fun availableDp(
        screenWidthDp: Int,
        backZoneDp: Int = BACK_ZONE_DP,
        contentPaddingDp: Int = CONTENT_PADDING_DP
    ): Int = ValueLawsNative.nativeToolAvailableDp(screenWidthDp, backZoneDp, contentPaddingDp)

    /**
     * How many of [widthsDp] (in order) stay visible in a primary row: the
     * largest prefix whose widths + gaps fit [availableDp]. If everything
     * fits, all of them. The rest are the panel's overflow — they live in
     * the shared More panel, so no room is reserved for a button here.
     */
    fun visibleCount(widthsDp: List<Int>, availableDp: Int, gapDp: Int): Int =
        ValueLawsNative.nativeToolVisibleCount(widthsDp.toIntArray(), availableDp, gapDp)

    /** [visibleCount] for the label-measured panels (clip + audio tools). */
    fun visibleCountForLabels(labels: List<String>, screenWidthDp: Int): Int =
        visibleCount(labels.map(::tileWidthDp), availableDp(screenWidthDp), GAP_DP)

    /**
     * Height of a grid block of [itemCount] tiles at the shared geometry:
     * whole rows up to [maxRows] (the grid scrolls vertically past that),
     * plus row gaps and [vPaddingDp] of vertical padding on each side.
     */
    fun gridHeightDp(
        itemCount: Int,
        tileHeightDp: Int = GRID_TILE_HEIGHT_DP,
        gapDp: Int = GRID_GAP_DP,
        vPaddingDp: Int = GRID_V_PADDING_DP,
        maxRows: Int = GRID_MAX_ROWS,
        columns: Int = GRID_COLUMNS
    ): Int = ValueLawsNative.nativeToolGridHeight(itemCount, tileHeightDp, gapDp, vPaddingDp, maxRows, columns)

    /**
     * Total height of the shared More panel: its own vertical padding + the
     * utility section (always [UTILITY_TILE_COUNT] tiles = 2 rows) + — when
     * a clip is selected — the divider and the clip-overflow section
     * ([CLIP_SECTION_MAX_ROWS] rows shown, scrolling past that).
     * No clip overflow -> 112 dp; with any overflow -> up to 225 dp.
     */
    fun sharedPanelHeightDp(clipOverflowCount: Int, utilityCount: Int = UTILITY_TILE_COUNT): Int =
        ValueLawsNative.nativeToolSharedPanelHeight(clipOverflowCount, utilityCount)
}
