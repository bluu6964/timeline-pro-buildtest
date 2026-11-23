package com.example.model

/**
 * Build 266 — the Text editor screen's layout law (Sarowar's order:
 * the whole Text section UI redesigned, "simple and clean", drawn by
 * the C++ GL UI path like the rest of the app since builds 248–253).
 *
 * The screen is one calm full-screen surface: a top bar (back /
 * "Text" / Done), a preview block, the hosted typing field, and one
 * flat scrolling list of full-width rows grouped Font / Style /
 * Color / Alignment / Spacing / Stroke / Shadow / Background /
 * Reveal / Path. Every size the GL host draws comes from here; the
 * host keeps only layout, touch boxes, semantics and state wiring
 * (the stage-2/3/4/5 pattern). All functions work in raw pixels;
 * [density] converts the dp constants exactly as `Dp.toPx()` did.
 * Text placement inside a line box follows the shared law
 * ([EditorGeometry.centredTextTopPx] + [PanelGeometry.lineBaselinePx]).
 */
object TextEditGeometry {

    /** The screen's horizontal padding (every block and row). */
    const val SCREEN_PAD_H_DP = 16f

    // ── Top bar ─────────────────────────────────────────────────────

    object TopBar {
        const val H_DP = 52f

        /** The back arrow's square tap box. */
        const val BACK_BOX_DP = 36f

        /** The back arrow glyph's size inside its box. */
        const val BACK_ICON_DP = 20f

        /** The back box's centre x: padding + half the box. */
        fun backCentreXPx(density: Float): Float =
            (SCREEN_PAD_H_DP + BACK_BOX_DP / 2f) * density

        /** The title's left edge: padding + back box + the gap after it. */
        fun titleLeftPx(density: Float): Float =
            (SCREEN_PAD_H_DP + BACK_BOX_DP + 4f) * density

        /** The Done pill's centre x: its right edge is the screen padding. */
        fun doneCentreXPx(rowWPx: Float, pillWPx: Float, density: Float): Float =
            rowWPx - SCREEN_PAD_H_DP * density - pillWPx / 2f

        /** The Done pill's left edge. */
        fun doneLeftPx(rowWPx: Float, pillWPx: Float, density: Float): Float =
            doneCentreXPx(rowWPx, pillWPx, density) - pillWPx / 2f

        /**
         * The title's box width: the hug rule ([EditorGeometry.LabelLine])
         * — the title takes only its advance, so the bar's weighted
         * spacer, not the title, absorbs the free width and the Done
         * pill lands at the right edge. (Build 267: the title's box
         * previously spanned the whole bar and pushed Done out.)
         */
        fun titleWPx(titleAdvancePx: Float, rowWPx: Float): Float =
            EditorGeometry.LabelLine.boxWPx(titleAdvancePx, 0f, rowWPx)

        /** The bar's vertical centre (every element shares it). */
        fun centreYPx(density: Float): Float = H_DP * density / 2f
    }

    // ── Preview block ───────────────────────────────────────────────

    object Preview {
        const val H_DP = 132f
        const val CORNER_DP = 12f

        /**
         * The frame width handed to the text painter: the preview
         * surface spans the screen inside its horizontal padding, and
         * the painter's own sizes are fractions of this width (the
         * same rule the canvas uses).
         */
        fun frameWidthPx(screenWPx: Float, density: Float): Float =
            screenWPx - 2f * SCREEN_PAD_H_DP * density
    }

    /** Gap between the top bar and the preview block. */
    const val GAP_AFTER_BAR_DP = 4f

    /** Gap between the preview block and the typing field. */
    const val GAP_AFTER_PREVIEW_DP = 12f

    /** Gap between the typing field and the controls list. */
    const val GAP_AFTER_FIELD_DP = 10f

    // ── Group headers ───────────────────────────────────────────────

    object GroupHeader {
        /** The header line's box height. */
        const val H_DP = 26f

        /** Space above a header that follows another group. */
        const val GAP_BEFORE_DP = 22f

        /** Space below the header before its first row. */
        const val GAP_AFTER_DP = 6f
    }

    // ── Slider rows (label + the standard GL slider + value) ───────

    object SliderRow {
        const val H_DP = 44f

        /** Gap between the label and the slider. */
        const val LABEL_GAP_DP = 10f

        /** Gap between the slider and the value readout. */
        const val VALUE_GAP_DP = 10f

        /** The label box's width: the hug rule ([EditorGeometry.LabelLine]). */
        fun labelWPx(labelAdvancePx: Float, rowWPx: Float): Float =
            EditorGeometry.LabelLine.boxWPx(labelAdvancePx, 0f, rowWPx)

        /** The value readout's box width: the hug rule again. */
        fun valueWPx(valueAdvancePx: Float, rowWPx: Float): Float =
            EditorGeometry.LabelLine.boxWPx(valueAdvancePx, 0f, rowWPx)

        /** The slider's left edge: label box + the label gap. */
        fun sliderLeftPx(labelAdvancePx: Float, rowWPx: Float, density: Float): Float =
            labelWPx(labelAdvancePx, rowWPx) + LABEL_GAP_DP * density

        /**
         * The slider's width: the row minus the two hugged boxes and
         * the two gaps — the weighted span the host's Row leaves it.
         * This is the width build 266 lost (the label took the whole
         * row, leaving 0); it must stay positive for real labels.
         */
        fun sliderWPx(
            rowWPx: Float, labelAdvancePx: Float, valueAdvancePx: Float, density: Float
        ): Float =
            (rowWPx - labelWPx(labelAdvancePx, rowWPx) - LABEL_GAP_DP * density -
                VALUE_GAP_DP * density - valueWPx(valueAdvancePx, rowWPx)).coerceAtLeast(0f)

        /** The value readout's left edge. */
        fun valueLeftPx(
            rowWPx: Float, labelAdvancePx: Float, valueAdvancePx: Float, density: Float
        ): Float =
            sliderLeftPx(labelAdvancePx, rowWPx, density) +
                sliderWPx(rowWPx, labelAdvancePx, valueAdvancePx, density) +
                VALUE_GAP_DP * density
    }

    // ── Segmented rows (style toggles, alignment) ──────────────────
    //
    // Equal cells across the row's width with [GAP_DP] between them;
    // the cells tile the row exactly (first cell at 0, last cell's
    // right edge at the row width).

    object Segmented {
        const val H_DP = 40f
        const val GAP_DP = 6f
        const val CORNER_DP = 10f

        /** Icon size in an icon+label cell (alignment). */
        const val ICON_DP = 18f

        /** Gap between a cell's icon and its label. */
        const val ICON_GAP_DP = 5f

        /** One cell's width: the row split into [count] equal cells. */
        fun cellWPx(rowWPx: Float, count: Int, density: Float): Float =
            if (count <= 0) 0f else (rowWPx - GAP_DP * density * (count - 1)) / count

        /** A cell's left edge within its row. */
        fun cellLeftPx(index: Int, rowWPx: Float, count: Int, density: Float): Float =
            index * (cellWPx(rowWPx, count, density) + GAP_DP * density)

        /** A cell's centre x within its row. */
        fun cellCentreXPx(index: Int, rowWPx: Float, count: Int, density: Float): Float =
            cellLeftPx(index, rowWPx, count, density) + cellWPx(rowWPx, count, density) / 2f

        /**
         * The left edge of a centred icon+label run inside a cell:
         * the run is icon + gap + label, centred as one piece; with
         * no icon the run is the label alone.
         */
        fun runLeftPx(
            cellLeftPx: Float, cellWPx: Float,
            labelAdvancePx: Float, hasIcon: Boolean, density: Float
        ): Float {
            val runW = labelAdvancePx +
                if (hasIcon) (ICON_DP + ICON_GAP_DP) * density else 0f
            return cellLeftPx + (cellWPx - runW) / 2f
        }
    }

    // ── Colour rows (label + swatch strip + custom + eyedropper) ───

    object Swatches {
        /** One swatch tile (the old colour row's 34 dp square). */
        const val CELL_DP = 34f
        const val GAP_DP = 6f
        const val CORNER_DP = 8f

        /** The colour row's height (the old TextColorRow's 40 dp). */
        const val ROW_H_DP = 40f

        /** Gap between the strip and the pinned custom-colour tile. */
        const val PIN_GAP_DP = 6f

        /** Gap between the row's label and the swatch strip. */
        const val LABEL_GAP_DP = 10f

        /** The label box's width: the hug rule ([EditorGeometry.LabelLine]). */
        fun labelWPx(labelAdvancePx: Float, rowWPx: Float): Float =
            EditorGeometry.LabelLine.boxWPx(labelAdvancePx, 0f, rowWPx)

        /** The strip's left edge: label box + the label gap. */
        fun stripLeftPx(labelAdvancePx: Float, rowWPx: Float, density: Float): Float =
            labelWPx(labelAdvancePx, rowWPx) + LABEL_GAP_DP * density

        /**
         * The strip's width: the row minus the hugged label, the
         * gaps and the two pinned tiles (custom + eyedropper) — the
         * weighted span the host's Row leaves it. Build 266 lost it
         * to the label (0); it must stay positive for real labels.
         */
        fun stripWPx(rowWPx: Float, labelAdvancePx: Float, density: Float): Float =
            (rowWPx - stripLeftPx(labelAdvancePx, rowWPx, density) -
                PIN_GAP_DP * density - CELL_DP * density -
                GAP_DP * density - CELL_DP * density).coerceAtLeast(0f)

        /** The pinned custom-colour tile's left edge. */
        fun customLeftPx(rowWPx: Float, labelAdvancePx: Float, density: Float): Float =
            stripLeftPx(labelAdvancePx, rowWPx, density) +
                stripWPx(rowWPx, labelAdvancePx, density) + PIN_GAP_DP * density

        /** The eyedropper tile's left edge (its right edge is the row's). */
        fun eyedropperLeftPx(rowWPx: Float, labelAdvancePx: Float, density: Float): Float =
            customLeftPx(rowWPx, labelAdvancePx, density) + CELL_DP * density + GAP_DP * density

        /** A swatch's left edge inside the strip. */
        fun leftPx(index: Int, density: Float): Float =
            index * (CELL_DP + GAP_DP) * density

        /** A swatch's centre x inside the strip. */
        fun centreXPx(index: Int, density: Float): Float =
            leftPx(index, density) + CELL_DP * density / 2f

        /** The strip's total content width for [count] swatches. */
        fun contentWPx(count: Int, density: Float): Float =
            if (count <= 0) 0f
            else count * CELL_DP * density + (count - 1) * GAP_DP * density
    }

    // ── Chip strips (font chips, font-weight pills) ────────────────
    //
    // Hug-content chips in one horizontally scrolling strip: each
    // chip is its label's advance + horizontal padding, all chips
    // share one height, [STRIP_GAP_DP] apart. (The shared pill-strip
    // law, [EditorGeometry.PillStrip], does the arithmetic; these
    // are the text screen's constants for it.)

    object Chips {
        const val H_DP = 36f
        const val PAD_H_DP = 12f
        const val STRIP_GAP_DP = 8f
        const val CORNER_DP = 10f

        /** The font chip's text size (the old chip's 15 sp, in its own face). */
        const val FONT_TEXT_SP = 15f

        /** Gap between the font-weight row's label and its pill strip. */
        const val LABEL_GAP_DP = 10f

        /** One chip's width from its label's advance. */
        fun chipWPx(advancePx: Float, density: Float): Float =
            advancePx + 2f * PAD_H_DP * density

        /**
         * The pill strip's slot width in the font-weight row: the
         * row minus the hugged label box and the gap — the weighted
         * span the host's Row leaves the strip. Build 266 lost it to
         * the label (0); it must stay positive for the real label.
         */
        fun stripSlotWPx(rowWPx: Float, labelAdvancePx: Float, density: Float): Float =
            (rowWPx - EditorGeometry.LabelLine.boxWPx(labelAdvancePx, 0f, rowWPx) -
                LABEL_GAP_DP * density).coerceAtLeast(0f)

        /** Chip left edges: cumulative widths + gaps, from [startPx]. */
        fun leftsPx(widths: List<Float>, density: Float, startPx: Float = 0f): List<Float> =
            EditorGeometry.PillStrip.itemLeftsPx(widths, STRIP_GAP_DP, density, startPx)

        /** The strip's total content width (chips + gaps). */
        fun contentWPx(widths: List<Float>, density: Float): Float =
            EditorGeometry.PillStrip.contentWPx(widths, STRIP_GAP_DP, density)
    }

    // ── Style preset tiles ──────────────────────────────────────────

    object Presets {
        /** One preset tile (the old Style tab's 48 dp square). */
        const val TILE_DP = 48f
        const val GAP_DP = 10f
        const val CORNER_DP = 10f

        /** A tile's left edge inside the strip. */
        fun leftPx(index: Int, density: Float): Float =
            index * (TILE_DP + GAP_DP) * density

        /** A tile's centre x inside the strip. */
        fun centreXPx(index: Int, density: Float): Float =
            leftPx(index, density) + TILE_DP * density / 2f

        /** The strip's total content width for [count] tiles. */
        fun contentWPx(count: Int, density: Float): Float =
            if (count <= 0) 0f
            else count * TILE_DP * density + (count - 1) * GAP_DP * density
    }

    // ── Full-width action rows (Path attach / detach) ──────────────

    object ActionRow {
        const val H_DP = 44f

        /** The label's inset from the row's left edge. */
        const val PAD_H_DP = 14f
    }

    // ── Simple label/value rows (Path: "Layer" + the layer's name) ──

    object LabelRow {
        const val H_DP = 40f

        /** The label column's width (the old PathTab's 88 dp). */
        const val LABEL_W_DP = 88f
    }

    // ── Build 269: the Styles tab's sub-sections ───────────────────

    object Styles {
        /**
         * The Styles tab's sub-section chips, in display order —
         * the canonical list the host's StyleSection enum mirrors.
         * Build 268's tab reorganisation left the manual Stroke
         * group (Thickness slider + stroke colour row) with no home;
         * this list is pinned by TextEditGeometryTest so a
         * sub-section can never silently lose its place again.
         */
        val SECTION_LABELS: List<String> =
            listOf("Background", "Stroke", "Shadow", "Curve", "Spacing", "Format")
    }

    // ── Build 268: the draggable bottom panel ──────────────────────
    //
    // The Text editor is a bottom panel over the editor: the canvas
    // above stays visible and live, and the panel's HEAD (grab pill +
    // "Text" title + Done) drags vertically. Sarowar's rule, verbatim
    // in effect: drag up → the panel rises; drag down → it sinks; it
    // STAYS exactly where the finger releases it. There is NO snap
    // point, detent or spring anywhere in this law — [draggedHeightPx]
    // is a pure clamp of the finger's own delta, and the released
    // height is the height, remembered by the host for the session.

    object Panel {
        /** The head strip: grab-pill zone + the title / Done row. */
        const val HEAD_H_DP = 48f

        /** The grab pill the finger takes hold of. */
        const val GRAB_W_DP = 40f
        const val GRAB_H_DP = 4f

        /** The hosted typing field's budgeted height in the min law. */
        const val FIELD_H_DP = 56f

        /** The tab row under the typing field. */
        const val TAB_ROW_H_DP = 40f

        /** Minimum visible content under the tabs: about one row. */
        const val MIN_CONTENT_DP = 44f

        /** First-open height: this share of the available height. */
        const val DEFAULT_FRACTION = 0.62f

        /** The smallest panel: head + field + tab row + one row. */
        fun minHeightPx(density: Float): Float =
            (HEAD_H_DP + FIELD_H_DP + TAB_ROW_H_DP + MIN_CONTENT_DP) * density

        /**
         * The most room the panel may take: the height the host hands
         * it (the editor's lower zone — the canvas's invariant height
         * is never touched; the panel grows over the lower zone only).
         */
        fun maxHeightPx(availablePx: Float): Float = availablePx.coerceAtLeast(0f)

        /**
         * The max height while the keyboard is up: the drag range is
         * measured in the space above the keyboard, so the IME inset
         * comes off the available height.
         */
        fun imeMaxHeightPx(availablePx: Float, imeBottomPx: Float): Float =
            (availablePx - imeBottomPx).coerceAtLeast(0f)

        /**
         * The clamp both the drag and the remembered height go
         * through. When the range inverts (IME taller than the zone),
         * the minimum wins: head + field + one row stay visible.
         */
        fun clampHeightPx(heightPx: Float, minPx: Float, maxPx: Float): Float =
            heightPx.coerceIn(minPx, maxOf(maxPx, minPx))

        /**
         * One drag step: the finger moved [dragDeltaYPx] (down =
         * positive), so the panel height changes by exactly the
         * opposite — 1:1, no stepping, no rounding, NO SNAP. On
         * release nothing else runs: this value is the resting
         * height, and the host remembers it for the session.
         */
        fun draggedHeightPx(
            currentPx: Float, dragDeltaYPx: Float, minPx: Float, maxPx: Float
        ): Float = clampHeightPx(currentPx - dragDeltaYPx, minPx, maxPx)

        /** The first-open height, before any drag is remembered. */
        fun defaultHeightPx(availablePx: Float, density: Float): Float =
            clampHeightPx(
                availablePx * DEFAULT_FRACTION,
                minHeightPx(density), maxHeightPx(availablePx)
            )
    }

    // ── Build 268: tile grids (Templates / Fonts / Effects / Bubbles) ──
    //
    // Equal cells tiling the padded row width exactly, [GAP_DP]
    // apart, in row-major order — the same tiling discipline as
    // [Segmented], generalised to a grid.

    object Grid {
        const val GAP_DP = 8f

        /** One cell's width: the row split into [columns] cells. */
        fun cellWPx(rowWPx: Float, columns: Int, density: Float): Float =
            if (columns <= 0) 0f
            else (rowWPx - GAP_DP * density * (columns - 1)) / columns

        /** A cell's left edge (its column's slot). */
        fun cellLeftPx(index: Int, rowWPx: Float, columns: Int, density: Float): Float {
            if (columns <= 0) return 0f
            val col = index % columns
            return col * (cellWPx(rowWPx, columns, density) + GAP_DP * density)
        }

        /** Rows needed for [count] cells at [columns] per row. */
        fun rowCount(count: Int, columns: Int): Int =
            if (count <= 0 || columns <= 0) 0 else (count + columns - 1) / columns

        /** The last cell's right edge must be the row width (tiling). */
        fun lastCellRightPx(rowWPx: Float, columns: Int, density: Float): Float =
            if (columns <= 0) 0f
            else cellLeftPx(columns - 1, rowWPx, columns, density) +
                cellWPx(rowWPx, columns, density)
    }
}
