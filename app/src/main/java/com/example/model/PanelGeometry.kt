package com.example.model

import kotlin.math.max

/**
 * Build 251 (UI migration stage 4) — the tool panels family's layout
 * law, extracted from the build-250 Compose layouts so the clip/audio
 * tools rows, the tools grid, the selected-state pager (build 219),
 * the group edit bar and the clip sheets can be rasterized by the C++
 * GL renderer as one surface each (the stage-2/3 pattern), with the
 * Compose hosts keeping only layout-sized touch boxes, semantics and
 * state wiring.
 *
 * All functions work in raw pixels; [density] converts the dp constants
 * exactly as Compose's `Dp.toPx()` did. Text placement follows the
 * stage-2/3 law: a line's baseline sits at its layout box's top minus
 * the paint's ascent, with the font box centred in the line box when an
 * explicit line height applies ([lineBaselinePx]).
 */
object PanelGeometry {

    // ── Shared text law ─────────────────────────────────────────────

    /**
     * Baseline (px) of one line whose layout box starts at [boxTopPx]
     * and is [lineBoxPx] tall: the font box (descent − ascent) is
     * centred in the line box — Android's line-height rule, the same
     * placement [ChromeGeometry.baselinePx] gives when the line box is
     * exactly the font box.
     */
    fun lineBaselinePx(boxTopPx: Float, lineBoxPx: Float, ascentPx: Float, descentPx: Float): Float =
        boxTopPx + (lineBoxPx - (descentPx - ascentPx)) / 2f - ascentPx

    /**
     * Greedy word wrap, Android StaticLayout's rule (no hyphenation):
     * words are packed onto a line while they fit; a single word wider
     * than the line is broken mid-word at the last code point that
     * fits; content past [maxLines] lines is dropped (the layouts this
     * serves use TextOverflow.Clip). A line never keeps its trailing
     * break space. [measure] is the caller's paint advance.
     */
    fun wrapGreedy(
        text: String,
        maxWPx: Float,
        maxLines: Int,
        measure: (String) -> Float
    ): List<String> {
        if (maxLines <= 0) return emptyList()
        if (text.isEmpty()) return listOf("")
        if (measure(text) <= maxWPx) return listOf(text)
        val lines = ArrayList<String>(maxLines)
        var rest = text
        while (rest.isNotEmpty() && lines.size < maxLines) {
            if (measure(rest) <= maxWPx) {
                lines.add(rest)
                return lines
            }
            val (line, next) = breakLine(rest, maxWPx, measure)
            lines.add(line)
            rest = next
        }
        return lines
    }

    /** One greedy line of [s] plus the unconsumed remainder (see [wrapGreedy]). */
    private fun breakLine(s: String, maxWPx: Float, measure: (String) -> Float): Pair<String, String> {
        // Mid-word break when even the first word cannot fit alone.
        val firstSpace = s.indexOf(' ')
        val firstWord = if (firstSpace >= 0) s.substring(0, firstSpace) else s
        if (measure(firstWord) > maxWPx) {
            // The longest prefix that fits (always at least one code
            // point, so the wrap can never stall on an over-wide glyph).
            var end = 0
            var i = 0
            while (i < s.length) {
                val next = i + Character.charCount(Character.codePointAt(s, i))
                if (end > 0 && measure(s.substring(0, next)) > maxWPx) break
                end = next
                i = next
            }
            if (end == 0) end = Character.charCount(Character.codePointAt(s, 0))
            val rest = s.substring(end).trimStart(' ')
            return s.substring(0, end) to rest
        }
        // Word-boundary break: the longest run of whole words that fits.
        var bestEnd = firstWord.length
        var idx = firstSpace
        while (idx >= 0 && idx + 1 < s.length) {
            val nextSpace = s.indexOf(' ', idx + 1)
            val wordEnd = if (nextSpace >= 0) nextSpace else s.length
            if (measure(s.substring(0, wordEnd)) > maxWPx) break
            bestEnd = wordEnd
            idx = nextSpace
        }
        val rest = if (bestEnd < s.length) s.substring(bestEnd).trimStart(' ') else ""
        return s.substring(0, bestEnd) to rest
    }

    // ── Clip / audio tools rows (ClipEditToolsPanel, AudioClipToolsPanel) ──

    object ToolRow {
        const val BACK_BOX_DP = 36f
        const val BACK_LEFT_DP = 6f
        const val BACK_GAP_DP = 4f
        const val BACK_ICON_DP = 19f
        const val DIVIDER_W_DP = 1f
        const val DIVIDER_H_DP = 24f
        const val TILE_ICON_DP = 18f
        const val TILE_GAP_DP = 2f
        const val TILE_PAD_H_DP = 3f
        const val TILE_PAD_V_DP = 3f
        const val TILE_CORNER_DP = 10f
        const val BAR_CORNER_DP = 18f

        /** The divider's left edge: back box zone = 6 pad + 36 box + 4 pad. */
        fun dividerLeftPx(density: Float): Float =
            (BACK_LEFT_DP + BACK_BOX_DP + BACK_GAP_DP) * density

        /** The tiles row's content left edge: divider + the row's 2 dp content padding. */
        fun tilesLeftPx(density: Float): Float =
            dividerLeftPx(density) + (DIVIDER_W_DP + ToolRowSplit.CONTENT_PADDING_DP) * density

        /** The bar face's top: the outer box's vertical padding. */
        fun barTopPx(outerPadDp: Float, density: Float): Float = outerPadDp * density

        /** The row content's top: outer padding + the bar's own vertical padding. */
        fun contentTopPx(outerPadDp: Float, rowPadDp: Float, density: Float): Float =
            (outerPadDp + rowPadDp) * density

        /** The row content's height inside a panel of [panelHPx]. */
        fun contentHeightPx(panelHPx: Float, outerPadDp: Float, rowPadDp: Float, density: Float): Float =
            panelHPx - 2f * (outerPadDp + rowPadDp) * density

        /** One tile's width: the wider of icon / label advance, plus the column's 3+3 padding. */
        fun tileWidthPx(labelAdvancePx: Float, density: Float): Float =
            max(TILE_ICON_DP * density, labelAdvancePx) + 2f * TILE_PAD_H_DP * density

        /** One laid-out tile: its left edge and its (possibly row-coerced) width. */
        data class TileBox(val leftPx: Float, val widthPx: Float)

        /**
         * Lay [widthsPx] out from [startXPx] with [gapPx] between boxes,
         * the Compose Row rule: a tile that would cross [rightPx] is
         * coerced to the remaining width (never below 0) and later tiles
         * keep accumulating past the edge at width 0.
         */
        fun layoutTilesPx(
            widthsPx: List<Float>,
            startXPx: Float,
            gapPx: Float,
            rightPx: Float
        ): List<TileBox> {
            val out = ArrayList<TileBox>(widthsPx.size)
            var x = startXPx
            for (w in widthsPx) {
                val avail = rightPx - x
                val coerced = when {
                    avail <= 0f -> 0f
                    w > avail -> avail
                    else -> w
                }
                out.add(TileBox(x, coerced))
                x += coerced + gapPx
            }
            return out
        }

        /** The icon+label stack's height inside a tile. */
        fun stackHeightPx(labelHeightPx: Float, density: Float): Float =
            (TILE_ICON_DP + TILE_GAP_DP) * density + labelHeightPx

        /** The stack's top: the column is centred in the tile (content) box. */
        fun stackTopPx(contentTopPx: Float, contentHeightPx: Float, labelHeightPx: Float, density: Float): Float =
            contentTopPx + (contentHeightPx - stackHeightPx(labelHeightPx, density)) / 2f

        /** The used-pill's height: the stack plus the column's 3+3 vertical padding. */
        fun pillHeightPx(labelHeightPx: Float, density: Float): Float =
            stackHeightPx(labelHeightPx, density) + 2f * TILE_PAD_V_DP * density

        /** The back icon's left edge: centred in the 36 dp box at 6 dp. */
        fun backIconLeftPx(density: Float): Float =
            BACK_LEFT_DP * density + (BACK_BOX_DP - BACK_ICON_DP) * density / 2f

        /** The back icon's top: centred in the content box. */
        fun backIconTopPx(contentTopPx: Float, contentHeightPx: Float, density: Float): Float =
            contentTopPx + (contentHeightPx - BACK_ICON_DP * density) / 2f

        /** The back box's centre (the press-scale origin). */
        fun backCenterPx(contentTopPx: Float, contentHeightPx: Float, density: Float): Pair<Float, Float> =
            (BACK_LEFT_DP + BACK_BOX_DP / 2f) * density to
                (contentTopPx + contentHeightPx / 2f)

        /** The divider's top: the 24 dp hairline is centred in the content box. */
        fun dividerTopPx(contentTopPx: Float, contentHeightPx: Float, density: Float): Float =
            contentTopPx + (contentHeightPx - DIVIDER_H_DP * density) / 2f
    }

    // ── Tools grid (ToolsGridSheet) ─────────────────────────────────

    object Grid {
        const val TILE_W_DP = 56f
        const val TILE_H_DP = 48f
        const val GAP_DP = 6f
        const val PAD_H_DP = 8f
        const val TILE_PAD_DP = 3f
        const val GLYPH_DP = 18f
        const val GLYPH_GAP_DP = 1f
        const val CORNER_DP = 12f

        /** The scrolling content's full width for [count] tiles. */
        fun contentWidthPx(count: Int, density: Float): Float =
            (2f * PAD_H_DP + count * TILE_W_DP + max(0, count - 1) * GAP_DP) * density

        /** Tile [index]'s left edge inside the scrolling content. */
        fun tileLeftPx(index: Int, density: Float): Float =
            (PAD_H_DP + index * (TILE_W_DP + GAP_DP)) * density

        /** The 18 dp glyph box's left edge inside its tile. */
        fun glyphLeftPx(tileLeftPx: Float, density: Float): Float =
            tileLeftPx + (TILE_W_DP - GLYPH_DP) * density / 2f

        /** The label text's layout width inside a tile: 56 − 3 − 3. */
        fun labelWidthPx(density: Float): Float = (TILE_W_DP - 2f * TILE_PAD_DP) * density

        /**
         * The glyph+label stack's top inside a tile whose top is
         * [tileTopPx]: the column's content region is the tile minus
         * 3+3 vertical padding; the stack (glyph 18 + 1 gap + the
         * label's lines at [lineBoxPx] each) is centred in it.
         */
        fun stackTopPx(tileTopPx: Float, lines: Int, lineBoxPx: Float, density: Float): Float {
            val region = TILE_H_DP * density - 2f * TILE_PAD_DP * density
            val stack = GLYPH_DP * density + GLYPH_GAP_DP * density + lines * lineBoxPx
            return tileTopPx + TILE_PAD_DP * density + (region - stack) / 2f
        }
    }

    // ── Selected-state pager (SelectedToolsPager219, build 219) ─────

    object Pager {
        const val PAGE_PAD_H_DP = 12f
        const val PAGE_PAD_TOP_DP = 6f
        const val PAGE_PAD_BOTTOM_DP = 4f
        const val GAP_DP = 6f
        const val BTN_PAD_H_DP = 2f
        const val BTN_PAD_V_SMALL_DP = 2f
        const val BTN_PAD_V_BIG_DP = 3f
        const val BTN_CORNER_DP = 6f
        const val BORDER_DP = 1.5f
        /** The fit law reserves 3 dp for an active button's border (build 220's rule). */
        const val FIT_BORDER_DP = 3f
        const val FLOOR_SP = 7.5f
        const val STEP_SP = 0.5f
        const val LINE_HEIGHT_FACTOR = 1.15f
        const val BADGE_PAD_H_DP = 3f
        const val BADGE_PAD_V_DP = 1f
        const val BADGE_CORNER_DP = 3f
        const val BADGE_OFF_X_DP = 2f
        const val BADGE_OFF_Y_DP = 4f
        const val DOT_H_DP = 5f
        const val DOT_ACTIVE_W_DP = 14f
        const val DOT_IDLE_W_DP = 5f
        const val DOT_GAP_DP = 6f
        const val DOT_CORNER_DP = 2.5f
        const val DOTS_TOP_DP = 4f

        /** One button's width in a row of [count] equal (weight 1f) buttons. */
        fun buttonWidthPx(pageWPx: Float, count: Int, density: Float): Float =
            (pageWPx - 2f * PAGE_PAD_H_DP * density - (count - 1) * GAP_DP * density) / count

        /** Button [index]'s left edge in a row of equal buttons. */
        fun buttonLeftPx(index: Int, buttonWPx: Float, density: Float): Float =
            PAGE_PAD_H_DP * density + index * (buttonWPx + GAP_DP * density)

        /**
         * The label's fit width inside a button: the button minus the
         * column's 2+2 horizontal padding and — for an ACTIVE button —
         * the 3 dp the build-220 fit law reserves for its border.
         */
        fun innerWidthPx(buttonWPx: Float, active: Boolean, density: Float): Float =
            buttonWPx - 2f * BTN_PAD_H_DP * density -
                (if (active) FIT_BORDER_DP * density else 0f)

        /** The auto-fit outcome: the chosen size and whether the label stays on one line. */
        data class Fit(val sp: Float, val oneLine: Boolean)

        /**
         * Build 220's auto-fit law: shrink [label] from [baseSp] in
         * 0.5 sp steps down to the 7.5 sp floor until it fits [innerWPx]
         * on ONE line ([measure] is the atlas paint at the candidate
         * size, so the cut points agree with the rasterizer). A label
         * that still cannot fit at the floor wraps to two lines.
         */
        fun fit(label: String, baseSp: Float, innerWPx: Float, measure: (String, Float) -> Float): Fit {
            var sp = baseSp
            while (true) {
                if (measure(label, sp) <= innerWPx) return Fit(sp, oneLine = true)
                if (sp <= FLOOR_SP) return Fit(FLOOR_SP, oneLine = false)
                sp = max(FLOOR_SP, sp - STEP_SP)
            }
        }

        /** The label's laid-out lines at [fit]: one line, or the greedy two-line wrap. */
        fun labelLines(label: String, fit: Fit, innerWPx: Float, measure: (String, Float) -> Float): List<String> =
            if (fit.oneLine) listOf(label)
            else wrapGreedy(label, innerWPx, 2) { measure(it, fit.sp) }

        /** A page row's top: the page's top padding plus the rows and gaps above it. */
        fun rowTopPx(rowIndex: Int, rowHeightsPx: List<Float>, density: Float): Float {
            var top = PAGE_PAD_TOP_DP * density
            for (i in 0 until rowIndex) top += rowHeightsPx[i] + GAP_DP * density
            return top
        }

        /** Page 3's row height: the page's rows share the padded page height equally (weight 1f). */
        fun page3RowHeightPx(pageHPx: Float, rows: Int, density: Float): Float =
            (pageHPx - (PAGE_PAD_TOP_DP + PAGE_PAD_BOTTOM_DP) * density -
                (rows - 1) * GAP_DP * density) / rows

        /**
         * The icon+label stack's top inside a button: the column's
         * content region is the button minus its vertical padding
         * (2 small / 3 big); the stack (icon + gap + the label's lines
         * at [lineBoxPx] each) is centred in it. In the two-line case
         * the caller passes the shrunken icon/gap (16 / 1).
         */
        fun stackTopPx(
            buttonTopPx: Float, buttonHPx: Float, smallLabel: Boolean,
            iconDp: Float, gapDp: Float, lines: Int, lineBoxPx: Float, density: Float
        ): Float {
            val vPad = (if (smallLabel) BTN_PAD_V_SMALL_DP else BTN_PAD_V_BIG_DP) * density
            val region = buttonHPx - 2f * vPad
            val stack = iconDp * density + gapDp * density + lines * lineBoxPx
            return buttonTopPx + vPad + (region - stack) / 2f
        }

        /** The badge's width: text advance + 3+3 horizontal padding. */
        fun badgeWidthPx(advancePx: Float, density: Float): Float =
            advancePx + 2f * BADGE_PAD_H_DP * density

        /** The badge's height: the text's layout height + 1+1 vertical padding. */
        fun badgeHeightPx(textHeightPx: Float, density: Float): Float =
            textHeightPx + 2f * BADGE_PAD_V_DP * density

        /** The badge's left edge: its right edge sits 2 dp past the button's right edge. */
        fun badgeLeftPx(buttonRightPx: Float, badgeWPx: Float, density: Float): Float =
            buttonRightPx + BADGE_OFF_X_DP * density - badgeWPx

        /** The badge's top: 4 dp above the button's top. */
        fun badgeTopPx(buttonTopPx: Float, density: Float): Float =
            buttonTopPx - BADGE_OFF_Y_DP * density

        /** The dots row's total width: 14 + 5 + 5 with two 6 dp gaps. */
        fun dotsWidthPx(density: Float): Float =
            (DOT_ACTIVE_W_DP + 2f * DOT_IDLE_W_DP + 2f * DOT_GAP_DP) * density

        /** The dots row's left edge: the row is centred in the page width. */
        fun dotsLeftPx(pageWPx: Float, density: Float): Float =
            (pageWPx - dotsWidthPx(density)) / 2f

        /** Dot [index]'s left edge when [activeIndex] is the current page. */
        fun dotLeftPx(index: Int, activeIndex: Int, pageWPx: Float, density: Float): Float {
            var x = dotsLeftPx(pageWPx, density)
            for (i in 0 until index) {
                x += (if (i == activeIndex) DOT_ACTIVE_W_DP else DOT_IDLE_W_DP) * density +
                    DOT_GAP_DP * density
            }
            return x
        }

        /** Dot [index]'s width. */
        fun dotWidthPx(index: Int, activeIndex: Int, density: Float): Float =
            (if (index == activeIndex) DOT_ACTIVE_W_DP else DOT_IDLE_W_DP) * density
    }

    // ── Group edit bar (GroupEditBar) ───────────────────────────────

    object GroupBar {
        const val ICON_DP = 14f
        const val TEXT_W_DP = 84f
        const val PAD_H_DP = 8f
        const val PAD_V_DP = 4f
        const val BG_ARGB = 0xE63F8F43.toInt()

        /** The pill's content height: the taller of the 14 dp icon and the text line. */
        fun contentHeightPx(textHeightPx: Float, density: Float): Float =
            max(ICON_DP * density, textHeightPx)

        /** The surface's width: 8 pad + 14 icon + 84 text + 8 pad. */
        fun surfaceWidthPx(density: Float): Float =
            (2f * PAD_H_DP + ICON_DP + TEXT_W_DP) * density

        /** The surface's height: content + 4+4 vertical padding. */
        fun surfaceHeightPx(textHeightPx: Float, density: Float): Float =
            contentHeightPx(textHeightPx, density) + 2f * PAD_V_DP * density

        /** The icon's left edge. */
        fun iconLeftPx(density: Float): Float = PAD_H_DP * density

        /** The icon's top: centred in the content row. */
        fun iconTopPx(textHeightPx: Float, density: Float): Float =
            PAD_V_DP * density + (contentHeightPx(textHeightPx, density) - ICON_DP * density) / 2f

        /** The text column's left edge. */
        fun textLeftPx(density: Float): Float = (PAD_H_DP + ICON_DP) * density

        /** The text line's layout-box top: centred in the content row. */
        fun textBoxTopPx(textHeightPx: Float, density: Float): Float =
            PAD_V_DP * density + (contentHeightPx(textHeightPx, density) - textHeightPx) / 2f
    }

    // ── Clip sheets (ClipToolsSheets: blend + replace) ──────────────

    object Sheets {
        const val COL_PAD_H_DP = 14f
        const val COL_PAD_TOP_DP = 6f
        const val COL_PAD_BOTTOM_DP = 22f
        const val TITLE_GAP_DP = 10f
        const val BLEND_ROW_PAD_H_DP = 11f
        const val BLEND_ROW_PAD_V_DP = 8f
        const val BLEND_ROW_GAP_DP = 5f
        const val BLEND_CHECK_DP = 16f
        const val REPLACE_ROW_PAD_H_DP = 10f
        const val REPLACE_ROW_PAD_V_DP = 7f
        const val REPLACE_ROW_GAP_DP = 6f
        const val REPLACE_IMG_DP = 42f
        const val REPLACE_IMG_CORNER_DP = 9f
        const val REPLACE_TEXT_GAP_DP = 10f
        const val ROW_CORNER_DP = 14f

        /** A blend row's height: its 12 sp line plus 8+8 vertical padding. */
        fun blendRowHeightPx(textHeightPx: Float, density: Float): Float =
            textHeightPx + 2f * BLEND_ROW_PAD_V_DP * density

        /** Blend row [index]'s top inside the sheet content. */
        fun blendRowTopPx(index: Int, titleHeightPx: Float, rowHeightPx: Float, density: Float): Float =
            COL_PAD_TOP_DP * density + titleHeightPx + TITLE_GAP_DP * density +
                index * (rowHeightPx + BLEND_ROW_GAP_DP * density)

        /** The blend sheet's total content height (7 rows, a 5 dp spacer after every row). */
        fun blendContentHeightPx(titleHeightPx: Float, rowHeightPx: Float, rows: Int, density: Float): Float =
            COL_PAD_TOP_DP * density + titleHeightPx + TITLE_GAP_DP * density +
                rows * (rowHeightPx + BLEND_ROW_GAP_DP * density) + COL_PAD_BOTTOM_DP * density

        /** The check icon's left edge in a selected blend row of width [sheetWPx]. */
        fun blendCheckLeftPx(sheetWPx: Float, density: Float): Float =
            sheetWPx - COL_PAD_H_DP * density - BLEND_ROW_PAD_H_DP * density - BLEND_CHECK_DP * density

        /** A blend row's text left edge: column padding + row padding. */
        fun rowTextLeftPx(rowPadHDp: Float, density: Float): Float =
            (COL_PAD_H_DP + rowPadHDp) * density

        /** A replace row's height: the taller of the 42 dp image and the name+desc stack, plus 7+7. */
        fun replaceRowHeightPx(nameHeightPx: Float, descHeightPx: Float, density: Float): Float =
            max(REPLACE_IMG_DP * density, nameHeightPx + descHeightPx) +
                2f * REPLACE_ROW_PAD_V_DP * density

        /** Replace row [index]'s top inside the sheet content. */
        fun replaceRowTopPx(index: Int, titleHeightPx: Float, rowHeightPx: Float, density: Float): Float =
            COL_PAD_TOP_DP * density + titleHeightPx + TITLE_GAP_DP * density +
                index * (rowHeightPx + REPLACE_ROW_GAP_DP * density)

        /** The replace sheet's total content height (a 6 dp spacer after every row). */
        fun replaceContentHeightPx(titleHeightPx: Float, rowHeightPx: Float, rows: Int, density: Float): Float =
            COL_PAD_TOP_DP * density + titleHeightPx + TITLE_GAP_DP * density +
                rows * (rowHeightPx + REPLACE_ROW_GAP_DP * density) + COL_PAD_BOTTOM_DP * density

        /** The replace row's image left edge: column padding + row padding. */
        fun replaceImageLeftPx(density: Float): Float =
            (COL_PAD_H_DP + REPLACE_ROW_PAD_H_DP) * density

        /** The replace row's text column left edge: image + the 10 dp gap. */
        fun replaceTextLeftPx(density: Float): Float =
            replaceImageLeftPx(density) + (REPLACE_IMG_DP + REPLACE_TEXT_GAP_DP) * density
    }
}
