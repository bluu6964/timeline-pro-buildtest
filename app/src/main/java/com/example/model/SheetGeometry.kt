package com.example.model

import kotlin.math.max

/**
 * Build 253 (UI migration stage 6) — the sheets' & dialogs' layout
 * law: the shared pill atoms, the sheet drag handle, the Material
 * switch, the sheets' list rows and card grids, the missing-media
 * rows, the project cards and the fullscreen player's pills. Pure
 * arithmetic over caller-supplied text measures (the atlas's), in the
 * PanelGeometry/EditorGeometry tradition: every fixture in
 * SheetGeometryTest is hand-derived from the build-252 Compose
 * layouts this law replaces.
 */
object SheetGeometry {

    /**
     * The dialog / sheet action pill (PillComponents.PillActionButton):
     * a full capsule, at least 32 dp tall, label centred, hug width =
     * the label's advance + 14 dp of padding on each side. The fill
     * sat OUTSIDE bouncyClickable's graphicsLayer in Compose — only
     * the label press-scales.
     */
    object PillButton {
        const val MIN_H_DP = 32f
        const val PAD_H_DP = 14f
        const val PAD_V_DP = 7f

        fun heightPx(textHPx: Float, density: Float): Float =
            max(MIN_H_DP * density, textHPx + 2f * PAD_V_DP * density)

        fun hugWidthPx(advancePx: Float, density: Float): Float =
            advancePx + 2f * PAD_H_DP * density
    }

    /**
     * The white primary pill (GlassComponents.GradientPillButton):
     * hug width = advance + 12 dp a side, height = the label's font
     * box + 7 dp a side; the caller centres the hug pill in its box.
     */
    object WhitePill {
        const val PAD_H_DP = 12f
        const val PAD_V_DP = 7f

        fun widthPx(advancePx: Float, density: Float): Float =
            advancePx + 2f * PAD_H_DP * density

        fun heightPx(textHPx: Float, density: Float): Float =
            textHPx + 2f * PAD_V_DP * density
    }

    /**
     * The violet primary pill (PillComponents.VioletPillButton) with
     * the padding both build-252 call sites pass (14 / 9 dp).
     */
    object VioletPill {
        const val PAD_H_DP = 14f
        const val PAD_V_DP = 9f

        fun widthPx(advancePx: Float, density: Float): Float =
            advancePx + 2f * PAD_H_DP * density

        fun heightPx(textHPx: Float, density: Float): Float =
            textHPx + 2f * PAD_V_DP * density
    }

    /**
     * The bottom sheets' drag handle (PillComponents.SheetDragHandle):
     * a 28 x 3 dp capsule centred in a full-width strip with 7 dp
     * above and 4 dp below it.
     */
    object Handle {
        const val PILL_W_DP = 28f
        const val PILL_H_DP = 3f
        const val PAD_TOP_DP = 7f
        const val PAD_BOTTOM_DP = 4f

        fun totalHeightPx(density: Float): Float =
            (PAD_TOP_DP + PILL_H_DP + PAD_BOTTOM_DP) * density

        fun pillCentreYPx(density: Float): Float =
            (PAD_TOP_DP + PILL_H_DP / 2f) * density
    }

    /**
     * The Material 3 switch (material3 1.3, the audio sheets' form):
     * a 52 x 32 dp track; the thumb is a 16 dp disc when off and a
     * 24 dp disc when on, its outer edge 4 dp inside the track's end
     * on both sides. (The library's pressed 28 dp thumb and its
     * drag-to-toggle are gesture states, not resting geometry.)
     */
    object Switch {
        const val TRACK_W_DP = 52f
        const val TRACK_H_DP = 32f
        const val BORDER_W_DP = 2f
        const val THUMB_ON_DP = 24f
        const val THUMB_OFF_DP = 16f
        const val THUMB_EDGE_DP = 4f

        fun thumbCentreXPx(checked: Boolean, density: Float): Float =
            if (checked) {
                (TRACK_W_DP - THUMB_EDGE_DP - THUMB_ON_DP / 2f) * density
            } else {
                (THUMB_EDGE_DP + THUMB_OFF_DP / 2f) * density
            }

        fun thumbRadiusPx(checked: Boolean, density: Float): Float =
            (if (checked) THUMB_ON_DP else THUMB_OFF_DP) / 2f * density
    }

    /**
     * The sheets' two-line list rows (ResolutionSheet, ParentSheet,
     * AddAudioSheet share the form): 9 dp of padding above and below
     * a title + subtitle stack (or a single line). Text is centred
     * vertically as one stack.
     */
    object ListRow {
        const val PAD_V_DP = 9f
        const val CORNER_DP = 14f

        fun twoLineHeightPx(titleHPx: Float, descHPx: Float, density: Float): Float =
            titleHPx + descHPx + 2f * PAD_V_DP * density

        fun oneLineHeightPx(textHPx: Float, density: Float): Float =
            textHPx + 2f * PAD_V_DP * density

        /** The top of a text stack of [stackHPx] centred in a row of [rowHPx]. */
        fun stackTopPx(rowHPx: Float, stackHPx: Float): Float = (rowHPx - stackHPx) / 2f
    }

    /** The aspect-ratio sheet's cards, swatches and resolution chips. */
    object Aspect {
        const val CARD_DP = 78f
        const val CARD_CORNER_DP = 12f
        const val SHAPE_BOX_DP = 34f
        const val SHAPE_MAX_DP = 32f
        const val SHAPE_GAP_DP = 6f
        const val SWATCH_DP = 38f
        const val CHIP_PAD_H_DP = 13f
        const val CHIP_PAD_V_DP = 8f

        /**
         * The canvas outline's (width, height) inside its 32 dp box:
         * the long side is 32 dp, the short side follows the aspect —
         * the build-252 Box sizing rule verbatim.
         */
        fun shapeSizePx(aspect: Float, density: Float): Pair<Float, Float> {
            val m = SHAPE_MAX_DP * density
            return if (aspect >= 1f) m to m / aspect else m * aspect to m
        }

        /** The top of the (shape box + gap + label) stack centred in the 78 dp card. */
        fun stackTopPx(labelHPx: Float, density: Float): Float =
            (CARD_DP * density - (SHAPE_BOX_DP + SHAPE_GAP_DP) * density - labelHPx) / 2f

        /** A resolution chip: the wider of its two texts + 13 dp a side. */
        fun resChipWidthPx(labelAdvancePx: Float, detailAdvancePx: Float, density: Float): Float =
            max(labelAdvancePx, detailAdvancePx) + 2f * CHIP_PAD_H_DP * density

        fun resChipHeightPx(labelHPx: Float, detailHPx: Float, density: Float): Float =
            labelHPx + detailHPx + 2f * CHIP_PAD_V_DP * density
    }

    /** The add-shape sheet's picker cards: a square tile, a 4 dp gap, the name. */
    object ShapeCard {
        const val LABEL_GAP_DP = 4f
        const val GLYPH_FRAC = 0.48f
        const val TILE_CORNER_DP = 12f

        fun cardHeightPx(tileWPx: Float, labelHPx: Float, density: Float): Float =
            tileWPx + LABEL_GAP_DP * density + labelHPx
    }

    /** The add-layer sheet's tiles: icon 20, gap 4, label, 10 dp padding a side. */
    object AddTile {
        const val ICON_DP = 20f
        const val GAP_DP = 4f
        const val PAD_V_DP = 10f
        const val CORNER_DP = 14f

        fun heightPx(labelHPx: Float, density: Float): Float =
            (ICON_DP + GAP_DP + 2f * PAD_V_DP) * density + labelHPx
    }

    /**
     * The missing-media dialog's rows: 5 dp of padding top and
     * bottom around the taller of the (label + where) stack and the
     * Replace pill; texts start 10 dp in, the pill ends 5 dp in.
     */
    object MissingRow {
        const val PAD_V_DP = 5f
        const val TEXT_LEFT_DP = 10f
        const val BTN_RIGHT_DP = 5f
        const val CORNER_DP = 12f

        fun heightPx(labelHPx: Float, subHPx: Float, btnHPx: Float, density: Float): Float =
            max(labelHPx + subHPx, btnHPx) + 2f * PAD_V_DP * density
    }

    /**
     * The projects home's cards: a 58 dp thumbnail block over a body
     * row whose height is the taller of the (name + meta) stack and
     * the 30 dp menu button, plus 6 dp above and 7 dp below.
     */
    object ProjectCard {
        const val THUMB_H_DP = 58f
        const val MENU_DP = 30f
        const val PAD_TOP_DP = 6f
        const val PAD_BOTTOM_DP = 7f

        fun bodyHeightPx(nameHPx: Float, metaHPx: Float, density: Float): Float =
            max(nameHPx + metaHPx, MENU_DP * density) + (PAD_TOP_DP + PAD_BOTTOM_DP) * density

        fun cardHeightPx(nameHPx: Float, metaHPx: Float, density: Float): Float =
            THUMB_H_DP * density + bodyHeightPx(nameHPx, metaHPx, density)
    }

    /** The fullscreen player's two pills (timecode, exit). */
    object FullscreenPill {
        const val TIME_PAD_H_DP = 10f
        const val TIME_PAD_V_DP = 5f
        const val EXIT_PAD_H_DP = 11f
        const val EXIT_PAD_V_DP = 6f
        const val EXIT_CORNER_DP = 8f

        fun timecodeSizePx(advancePx: Float, textHPx: Float, density: Float): Pair<Float, Float> =
            (advancePx + 2f * TIME_PAD_H_DP * density) to (textHPx + 2f * TIME_PAD_V_DP * density)

        fun exitSizePx(advancePx: Float, textHPx: Float, density: Float): Pair<Float, Float> =
            (advancePx + 2f * EXIT_PAD_H_DP * density) to (textHPx + 2f * EXIT_PAD_V_DP * density)
    }
}
