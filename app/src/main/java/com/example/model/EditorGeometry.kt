package com.example.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Build 252 (UI migration stage 5) — the value editors' layout law,
 * extracted from the build-251 Compose layouts so the editors'
 * surrounding drawn surfaces (slider rows, the shared value header,
 * the editor screens' chrome, pill strips, swatch grids, effect rows,
 * the stock Material slider's drawn form) can be rasterized by the
 * C++ GL renderer (the stage-2/3/4 pattern), with the Compose hosts
 * keeping only layout, touch boxes, semantics and state wiring. The
 * editors' widgets themselves (ruler dial, position pad, crop overlay,
 * easing tile, colour wheels) were already native since build 241 and
 * do not move.
 *
 * All functions work in raw pixels; [density] converts the dp constants
 * exactly as Compose's `Dp.toPx()` did. Text placement follows the
 * stage-2/3 law: a line's baseline sits at its layout box's top minus
 * the paint's ascent, with the font box centred in the line box when an
 * explicit line height applies ([PanelGeometry.lineBaselinePx]).
 */
object EditorGeometry {

    /**
     * The atlas blit destination for a text run whose layout origin (the
     * pen position Compose's Text would use) is [originXPx] and whose
     * baseline is [baselinePx]: the entry rect carries PAD px of margin
     * around the ink, and the ink's left edge sits at origin + bearing.
     * Returns (dstX, dstY) for `VectorCmdBuilder.addText`.
     */
    fun textDst(originXPx: Float, baselinePx: Float, bearingPx: Float, basePx: Float, padPx: Float): Pair<Float, Float> =
        (originXPx + bearingPx - padPx) to (baselinePx - basePx)

    /** The layout origin x of a centred text run: centre − advance/2. */
    fun centredOriginPx(centrePx: Float, advancePx: Float): Float = centrePx - advancePx / 2f

    /** The top of a single-line text box centred in a box of [boxHPx]. */
    fun centredTextTopPx(boxTopPx: Float, boxHPx: Float, textHPx: Float): Float =
        boxTopPx + (boxHPx - textHPx) / 2f

    /**
     * The outline points (x, y pairs) of a rounded rect centred at
     * ([cx], [cy]) with per-corner radii, sampled at [segPerCorner]
     * points per corner arc — the GL renderer's round-rect command
     * takes one radius, so the Material slider's track (8 dp outer /
     * 2 dp inner corners) and the rotated keyframe diamond are drawn
     * as filled polygons / closed polylines through these points.
     * [rotDeg] rotates every point about the centre (the diamond).
     */
    fun roundedRectOutline(
        cx: Float, cy: Float, wPx: Float, hPx: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float,
        rotDeg: Float = 0f, segPerCorner: Int = 4
    ): FloatArray {
        val hw = wPx / 2f
        val hh = hPx / 2f
        // Corner centres + arc start angles, walking clockwise from the
        // top-left corner's top edge (screen coords, y down).
        val corners = arrayOf(
            floatArrayOf(cx - hw + rTL, cy - hh + rTL, rTL, 180f),
            floatArrayOf(cx + hw - rTR, cy - hh + rTR, rTR, 270f),
            floatArrayOf(cx + hw - rBR, cy + hh - rBR, rBR, 0f),
            floatArrayOf(cx - hw + rBL, cy + hh - rBL, rBL, 90f)
        )
        val rot = rotDeg * PI.toFloat() / 180f
        val cosR = cos(rot)
        val sinR = sin(rot)
        val out = FloatArray(segPerCorner * 4 * 2)
        var o = 0
        for ((ccx, ccy, r, startDeg) in corners) {
            for (s in 0 until segPerCorner) {
                val a = (startDeg + 90f * s / segPerCorner) * PI.toFloat() / 180f
                var px = ccx + r * cos(a)
                var py = ccy + r * sin(a)
                if (rotDeg != 0f) {
                    val dx = px - cx
                    val dy = py - cy
                    px = cx + dx * cosR - dy * sinR
                    py = cy + dx * sinR + dy * cosR
                }
                out[o++] = px
                out[o++] = py
            }
        }
        return out
    }

    // ── RulerDialSlider row ─────────────────────────────────────────

    object SliderRow {
        const val ROW_H_DP = 36f
        const val PILL_W_DP = 58f
        const val PILL_H_DP = 24f
        const val ICON_DP = 18f

        /** The value pill's centre x: the pill is the row's last element. */
        fun pillCentreXPx(rowWPx: Float, density: Float): Float = rowWPx - PILL_W_DP * density / 2f

        /** The row's vertical centre (pill and label share it). */
        fun centreYPx(density: Float): Float = ROW_H_DP * density / 2f
    }

    // ── The shared value header (Transform / Color / Crop) ──────────

    object ValueHeader {
        const val H_DP = 34f
        const val BOX_DP = 32f
        const val BACK_ICON_DP = 20f
        const val DIAMOND_DP = 17f
        const val DIAMOND_CORNER_DP = 3f
        const val DIAMOND_BORDER_DP = 1.5f
        const val ADD_ICON_DP = 11f
        const val EASING_TILE_DP = 16f

        /** The value text's centre: the middle of the weight-1 span
         *  between the back box and the two right boxes. */
        fun valueCentreXPx(rowWPx: Float, density: Float): Float =
            (BOX_DP * density + (rowWPx - 2f * BOX_DP * density)) / 2f

        /** The keyframe diamond box's centre (second from the right). */
        fun diamondCentreXPx(rowWPx: Float, density: Float): Float =
            rowWPx - BOX_DP * density - BOX_DP * density / 2f

        /** The easing box's centre (the row's last element). */
        fun easingCentreXPx(rowWPx: Float, density: Float): Float =
            rowWPx - BOX_DP * density / 2f

        /** The row's vertical centre. */
        fun centreYPx(density: Float): Float = H_DP * density / 2f

        /**
         * The keyframe diamond's outline: a [DIAMOND_DP] square with
         * [DIAMOND_CORNER_DP] corners rotated 45° (the Compose box's
         * graphicsLayer rotation), as polygon points around ([cx], [cy]).
         */
        fun diamondOutline(cx: Float, cy: Float, density: Float): FloatArray =
            roundedRectOutline(
                cx, cy, DIAMOND_DP * density, DIAMOND_DP * density,
                DIAMOND_CORNER_DP * density, DIAMOND_CORNER_DP * density,
                DIAMOND_CORNER_DP * density, DIAMOND_CORNER_DP * density,
                rotDeg = 45f
            )
    }

    // ── Editor screen chrome (Crop / EditShape / AppliedFx) ─────────

    object ScreenChrome {
        const val HEADER_H_DP = 42f
        const val HEADER_PAD_DP = 10f
        const val BACK_BOX_DP = 34f
        const val BACK_ICON_DP = 22f
        const val PILL_PAD_H_DP = 12f
        const val PILL_PAD_V_DP = 6f
        const val PILL_MIN_H_DP = 32f

        /** The header pill's width: label advance + horizontal padding. */
        fun pillWPx(advancePx: Float, density: Float): Float =
            advancePx + 2f * PILL_PAD_H_DP * density

        /** The header pill's height: at least [PILL_MIN_H_DP], else the
         *  label's font box + vertical padding (heightIn(min = 32)). */
        fun pillHPx(textHPx: Float, density: Float): Float =
            maxOf(PILL_MIN_H_DP * density, textHPx + 2f * PILL_PAD_V_DP * density)

        /**
         * The header title's centre x: the title sits between two
         * weight-1 spacers, so it centres in the span from the back
         * box's end to the pill's start (both inside the header's
         * horizontal padding).
         */
        fun titleCentreXPx(rowWPx: Float, pillWPx: Float, density: Float): Float {
            val left = (HEADER_PAD_DP + BACK_BOX_DP) * density
            val right = rowWPx - HEADER_PAD_DP * density - pillWPx
            return (left + right) / 2f
        }

        // Transport row (Crop / EditShape): horizontal padding, a lead
        // spacer, the prev/play/next group and the undo/redo group,
        // arranged SpaceBetween — the free space splits into two equal
        // gaps between the three children.
        const val TRANSPORT_PAD_DP = 16f
        const val LEAD_SPACER_DP = 30f
        const val PREV_NEXT_BOX_DP = 30f
        const val PLAY_BOX_DP = 34f
        const val GROUP_GAP_DP = 12f
        const val PREV_NEXT_ICON_DP = 19f
        const val PLAY_ICON_DP = 23f
        const val UNDO_BOX_DP = 28f
        const val UNDO_GAP_DP = 6f
        const val UNDO_ICON_DP = 17f

        /** The transport group's width: prev + gap + play + gap + next. */
        fun transportGroupWPx(density: Float): Float =
            (PREV_NEXT_BOX_DP + GROUP_GAP_DP + PLAY_BOX_DP + GROUP_GAP_DP + PREV_NEXT_BOX_DP) * density

        /** The undo/redo group's width: two boxes + the gap between them. */
        fun undoGroupWPx(density: Float): Float =
            (UNDO_BOX_DP * 2f + UNDO_GAP_DP) * density

        /** One SpaceBetween gap inside the padded content width. */
        fun spaceBetweenGapPx(rowWPx: Float, density: Float): Float =
            (rowWPx - 2f * TRANSPORT_PAD_DP * density - LEAD_SPACER_DP * density -
                transportGroupWPx(density) - undoGroupWPx(density)) / 2f

        /** The transport group's left edge. */
        fun transportLeftPx(rowWPx: Float, density: Float): Float =
            TRANSPORT_PAD_DP * density + LEAD_SPACER_DP * density +
                spaceBetweenGapPx(rowWPx, density)

        /** The undo/redo group's left edge (flush with the right padding). */
        fun undoLeftPx(rowWPx: Float, density: Float): Float =
            rowWPx - TRANSPORT_PAD_DP * density - undoGroupWPx(density)

        /** The transport row's height: the tallest box (play) — the
         *  row's own vertical padding is the host's, not the surface's. */
        fun rowHPx(density: Float): Float = PLAY_BOX_DP * density

        // Timecode row (Crop / EditShape): horizontal padding, a
        // weighted spacer, current timecode, separator, total timecode,
        // a fixed spacer, and the (already native) tick strip weighted.
        // The two weights share the width left after the fixed parts.
        const val TIMECODE_PAD_DP = 12f
        const val LEAD_WEIGHT = 0.36f
        const val TICKS_WEIGHT = 0.48f
        const val MID_SPACER_DP = 6f

        /** The lead spacer's width from its weight share of [flexWPx]. */
        fun timecodeLeadPx(flexWPx: Float): Float =
            flexWPx * (LEAD_WEIGHT / (LEAD_WEIGHT + TICKS_WEIGHT))

        /** The tick strip's width from its weight share of [flexWPx]. */
        fun timecodeTicksWPx(flexWPx: Float): Float =
            flexWPx * (TICKS_WEIGHT / (LEAD_WEIGHT + TICKS_WEIGHT))
    }

    // ── Pill / chip strips ──────────────────────────────────────────

    object PillStrip {
        /**
         * Item widths for a strip whose items size to their label:
         * advance + 2 × horizontal padding, in order.
         */
        fun itemWidthsPx(advances: List<Float>, padHDp: Float, density: Float): List<Float> =
            advances.map { it + 2f * padHDp * density }

        /** Item left edges: cumulative widths + [gapDp] between items, from [startPx]. */
        fun itemLeftsPx(widths: List<Float>, gapDp: Float, density: Float, startPx: Float = 0f): List<Float> {
            val out = ArrayList<Float>(widths.size)
            var x = startPx
            for (w in widths) {
                out.add(x)
                x += w + gapDp * density
            }
            return out
        }

        /** The strip's total content width (items + gaps, no trailing gap). */
        fun contentWPx(widths: List<Float>, gapDp: Float, density: Float): Float =
            if (widths.isEmpty()) 0f
            else widths.sum() + gapDp * density * (widths.size - 1)

        /** A hug-content item's height: the label's font box + vertical padding. */
        fun itemHPx(textHPx: Float, padVDp: Float, density: Float): Float =
            textHPx + 2f * padVDp * density

        /** A min-height item's height (heightIn(min) pills): the larger
         *  of the minimum and the hug-content height. */
        fun itemHPx(textHPx: Float, padVDp: Float, minHDp: Float, density: Float): Float =
            maxOf(minHDp * density, itemHPx(textHPx, padVDp, density))
    }

    // ── Colour swatch grid (ColorEditView) ──────────────────────────
    //
    // The swatches are square (weight-1, aspectRatio 1) cells in rows
    // of [COLUMNS], [GAP_DP] apart, with a [GAP_DP] spacer after every
    // row (including the last).

    object SwatchGrid {
        const val COLUMNS = 7
        const val GAP_DP = 4f
        const val CORNER_DP = 7f

        /**
         * One row's cell width: the row is split into [count] equal
         * weight-1 cells with [GAP_DP] between them. Cells are square,
         * so this is also the cell height.
         */
        fun cellWPx(rowWPx: Float, count: Int, density: Float): Float =
            if (count <= 0) 0f else (rowWPx - GAP_DP * density * (count - 1)) / count

        /** A cell's left edge within its row. */
        fun cellLeftPx(index: Int, rowWPx: Float, count: Int, density: Float): Float =
            index * (cellWPx(rowWPx, count, density) + GAP_DP * density)

        /** A row's top edge: square cells + the spacer after each row. */
        fun rowTopPx(rowIndex: Int, cellWPx: Float, density: Float): Float =
            rowIndex * (cellWPx + GAP_DP * density)

        /** The whole grid's height for [rows] rows (trailing spacer in). */
        fun gridHPx(rows: Int, cellWPx: Float, density: Float): Float =
            if (rows <= 0) 0f else rows * (cellWPx + GAP_DP * density)
    }

    // ── Transform panel (build 255: the card-grid redesign) ───────
    //
    // The header row (2D/3D segmented toggle, XY-link chip,
    // keyframe-all, easing, close), the 2×2 value-card grid (a card =
    // keyframe diamond + label + big value over a ruler strip; the
    // linked-XY position pad spanning both rows) and the quick-action
    // chip row. The Compose hosts lay the cells out with the same
    // arithmetic (equal weight-1 cells, [GAP_DP] between them); these
    // functions pin it for the GL draws and the host tests.

    object TransformPanel {
        // Header row.
        const val HEADER_H_DP = 32f
        const val SEG_PAD_DP = 2f
        const val SEG_W_DP = 54f
        const val SEG_H_DP = 28f
        const val SEG_TRACK_CORNER_DP = 9f
        const val SEG_CORNER_DP = 7f
        const val ICON_BOX_DP = 32f
        const val ICON_CORNER_DP = 8f
        const val LINK_PAD_DP = 10f
        const val LINK_ICON_DP = 16f
        const val LINK_GAP_DP = 5f
        const val LINK_CORNER_DP = 8f

        /** The segmented track's width: two segments + the pad around them. */
        fun segTrackWPx(density: Float): Float = (2f * SEG_W_DP + 2f * SEG_PAD_DP) * density

        /** One segment's left edge inside the track. */
        fun segLeftPx(index: Int, density: Float): Float = (SEG_PAD_DP + index * SEG_W_DP) * density

        /** One segment's centre x inside the track. */
        fun segCentreXPx(index: Int, density: Float): Float =
            segLeftPx(index, density) + SEG_W_DP * density / 2f

        /** The track's vertical centre = the header row's centre. */
        fun headerCentreYPx(density: Float): Float = HEADER_H_DP * density / 2f

        /**
         * The centre x of a right-side header box: the boxes are
         * [ICON_BOX_DP] wide, flush to the row's right edge, indexed
         * from the right (0 = the rightmost box).
         */
        fun rightBoxCentreXPx(rowWPx: Float, indexFromRight: Int, density: Float): Float =
            rowWPx - ICON_BOX_DP * density * (indexFromRight + 0.5f)

        /** The XY-link chip's width: pads + icon + gap + the label's advance. */
        fun linkChipWPx(labelAdvancePx: Float, density: Float): Float =
            (2f * LINK_PAD_DP + LINK_ICON_DP + LINK_GAP_DP) * density + labelAdvancePx

        /**
         * The link chip's left edge: its right edge sits just left of
         * the [boxesFromRight] right-side icon boxes.
         */
        fun linkChipLeftPx(rowWPx: Float, chipWPx: Float, boxesFromRight: Int, density: Float): Float =
            rowWPx - ICON_BOX_DP * density * boxesFromRight - chipWPx

        // Card grid.
        const val GAP_DP = 8f
        const val CARD_CORNER_DP = 10f
        const val CARD_PAD_DP = 8f
        const val CARD_TOP_PAD_DP = 5f
        const val TOP_ROW_H_DP = 24f
        const val DIAMOND_BOX_DP = 24f
        const val VALUE_END_PAD_DP = 12f
        const val STRIP_H_DP = 20f

        /** One grid cell's width: two equal columns, one gap between them. */
        fun cellWPx(gridWPx: Float, density: Float): Float =
            (gridWPx - GAP_DP * density) / 2f

        /** One grid cell's height: two equal rows, one gap between them. */
        fun cellHPx(gridHPx: Float, density: Float): Float =
            (gridHPx - GAP_DP * density) / 2f

        /** A cell's left edge (column 0 / 1). */
        fun cellLeftPx(col: Int, gridWPx: Float, density: Float): Float =
            col * (cellWPx(gridWPx, density) + GAP_DP * density)

        /** A cell's top edge (row 0 / 1). */
        fun cellTopPx(row: Int, gridHPx: Float, density: Float): Float =
            row * (cellHPx(gridHPx, density) + GAP_DP * density)

        /** The card top row's centre y (diamond, label and value share it). */
        fun topRowCentreYPx(density: Float): Float =
            (CARD_TOP_PAD_DP + TOP_ROW_H_DP / 2f) * density

        /** The diamond button's centre x inside a card. */
        fun diamondCentreXPx(density: Float): Float =
            (CARD_PAD_DP + DIAMOND_BOX_DP / 2f) * density

        /** The label's layout origin x: just right of the diamond box. */
        fun labelOriginXPx(density: Float): Float =
            (CARD_PAD_DP + DIAMOND_BOX_DP + 2f) * density

        /** The value's layout origin x: right-aligned to the end pad. */
        fun valueOriginXPx(cardWPx: Float, advancePx: Float, density: Float): Float =
            cardWPx - VALUE_END_PAD_DP * density - advancePx

        /** The ruler strip's top edge inside a card (the strip hugs the bottom). */
        fun stripTopPx(cardHPx: Float, density: Float): Float =
            cardHPx - STRIP_H_DP * density

        // Position pad readout ("X <v>  Y <v>", top-left inside the pad).
        const val READOUT_TOP_DP = 8f
        const val READOUT_LEFT_DP = 10f
        const val READOUT_LABEL_GAP_DP = 3f
        const val READOUT_GROUP_GAP_DP = 6f
        const val PAD_DIAMOND_INSET_DP = 6f

        /**
         * The readout runs' left edges: [advances] are the four runs
         * (label X, value X, label Y, value Y); a label gap follows
         * each axis label, a group gap follows the X value.
         */
        fun readoutLeftsPx(advances: List<Float>, density: Float, startPx: Float): List<Float> {
            val gaps = floatArrayOf(READOUT_LABEL_GAP_DP, READOUT_GROUP_GAP_DP, READOUT_LABEL_GAP_DP)
            val out = ArrayList<Float>(advances.size)
            var x = startPx
            for (i in advances.indices) {
                out.add(x)
                if (i < gaps.size) x += advances[i] + gaps[i] * density
            }
            return out
        }

        /** The pad's joint keyframe diamond centre x (inset from the top-right). */
        fun padDiamondCentreXPx(cardWPx: Float, density: Float): Float =
            cardWPx - (PAD_DIAMOND_INSET_DP + DIAMOND_BOX_DP / 2f) * density

        /** The pad's joint keyframe diamond centre y. */
        fun padDiamondCentreYPx(density: Float): Float =
            (PAD_DIAMOND_INSET_DP + DIAMOND_BOX_DP / 2f) * density

        // Quick-action row.
        const val QUICK_H_DP = 30f
        const val QUICK_GAP_DP = 8f
        const val QUICK_CORNER_DP = 8f

        /** One quick chip's width: [count] equal chips, gaps between them. */
        fun chipWPx(rowWPx: Float, count: Int, density: Float): Float =
            if (count <= 0) 0f else (rowWPx - QUICK_GAP_DP * density * (count - 1)) / count

        /** One quick chip's left edge. */
        fun chipLeftPx(index: Int, rowWPx: Float, count: Int, density: Float): Float =
            index * (chipWPx(rowWPx, count, density) + QUICK_GAP_DP * density)
    }

    // ── The stock Material 3 slider's drawn form ────────────────────
    //
    // Material3 1.3.0 (compose BOM 2024.09.00), read from the library's
    // own sources: the handle is a 4 dp × 44 dp capsule (2 dp wide
    // while pressed), the track is 16 dp tall with 8 dp outer / 2 dp
    // inner corner radii and a 6 dp thumb gap, a 4 dp stop indicator
    // sits at the track's end in the active colour, and the component
    // is at least 48 dp tall (minimum interactive size) with the
    // content centred. The value law: offsets live in
    // [thumbW/2, width − thumbW/2]; a press records pressOffset =
    // downX − offset(value); a tap applies it on lift (value from the
    // DOWN position); a drag's first delta is the movement past the
    // touch slop, so while dragging the value trails the finger by
    // exactly the slop.

    object M3Slider {
        const val HEIGHT_DP = 48f
        const val THUMB_W_DP = 4f
        const val THUMB_W_PRESSED_DP = 2f
        const val THUMB_H_DP = 44f
        const val TRACK_H_DP = 16f
        const val TRACK_OUTER_CORNER_DP = 8f
        const val TRACK_INNER_CORNER_DP = 2f
        const val TRACK_GAP_DP = 6f
        const val STOP_DP = 4f

        fun thumbWPx(pressed: Boolean, density: Float): Float =
            (if (pressed) THUMB_W_PRESSED_DP else THUMB_W_DP) * density

        /** The track's left edge: half the thumb width. */
        fun trackLeftPx(pressed: Boolean, density: Float): Float = thumbWPx(pressed, density) / 2f

        /** The track's width: the component width minus the thumb width. */
        fun trackWPx(widthPx: Float, pressed: Boolean, density: Float): Float =
            widthPx - thumbWPx(pressed, density)

        /** The value's fraction in [valueRange], coerced to 0..1. */
        fun fractionOf(value: Float, min: Float, max: Float): Float =
            if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

        /** The thumb's left edge: (track width × fraction), rounded to
         *  whole px as the library's Layout placement rounds it. */
        fun thumbLeftPx(widthPx: Float, fraction: Float, pressed: Boolean, density: Float): Int =
            (trackWPx(widthPx, pressed, density) * fraction).roundToInt()

        /** The active track's end (track-local px): track width × fraction. */
        fun valueEndPx(widthPx: Float, fraction: Float, pressed: Boolean, density: Float): Float =
            trackWPx(widthPx, pressed, density) * fraction

        /** The gap between the value point and each track segment. */
        fun endGapPx(pressed: Boolean, density: Float): Float =
            thumbWPx(pressed, density) / 2f + TRACK_GAP_DP * density

        /** Whether the active track segment is drawn at all (the
         *  library skips it when it is shorter than the outer corner). */
        fun activeDrawn(widthPx: Float, fraction: Float, pressed: Boolean, density: Float): Boolean =
            valueEndPx(widthPx, fraction, pressed, density) - endGapPx(pressed, density) >
                TRACK_OUTER_CORNER_DP * density

        /** Whether the inactive segment (and its stop indicator) is
         *  drawn: the value point must clear the gap + outer corner. */
        fun inactiveDrawn(widthPx: Float, fraction: Float, pressed: Boolean, density: Float): Boolean =
            valueEndPx(widthPx, fraction, pressed, density) <
                trackWPx(widthPx, pressed, density) - endGapPx(pressed, density) -
                TRACK_OUTER_CORNER_DP * density

        /** The stop indicator's centre x (component coords). */
        fun stopCentreXPx(widthPx: Float, pressed: Boolean, density: Float): Float =
            trackLeftPx(pressed, density) + trackWPx(widthPx, pressed, density) -
                TRACK_OUTER_CORNER_DP * density

        /** The px offset the library keeps for [value] (its rawOffset). */
        fun offsetOfValue(widthPx: Float, value: Float, min: Float, max: Float, pressed: Boolean, density: Float): Float {
            val minPx = trackLeftPx(pressed, density)
            val maxPx = widthPx - minPx
            return minPx + fractionOf(value, min, max) * (maxPx - minPx)
        }

        /** The user value for a px offset (the library's scale() —
         *  the fraction is coerced, so out-of-range offsets clamp). */
        fun valueOfOffset(widthPx: Float, offsetPx: Float, min: Float, max: Float, pressed: Boolean, density: Float): Float {
            val minPx = trackLeftPx(pressed, density)
            val maxPx = widthPx - minPx
            if (maxPx <= minPx) return min
            val f = ((offsetPx - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)
            return min + f * (max - min)
        }
    }
}
