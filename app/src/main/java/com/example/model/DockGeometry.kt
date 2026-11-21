package com.example.model

/**
 * Build 249 (UI migration stage 2) — the editor dock's layout law, extracted
 * from BottomToolbar's Compose layout (build 248) so the whole dock — tile
 * backgrounds, borders, glyphs, labels — can be rasterized by the C++ GL
 * renderer as ONE surface instead of twelve tiles each carrying its own
 * glyph TextureView.
 *
 * The build-248 layout being mirrored: a horizontally scrolling row with
 * 8 dp horizontal padding and 8 dp gaps; each tile is a 36 dp column — a
 * 36 dp square box (corner 8 dp, 1 dp border) with the glyph centred, a
 * 2 dp spacer, then the label (9.5 sp SemiBold, single line) — and the
 * column is centred vertically in the dock's row area. The press scale
 * (bouncyClickable, 0.94) transforms the whole column about its centre.
 *
 * All functions work in raw pixels; [density] converts the dp constants
 * exactly as Compose's `Dp.toPx()` did. Label placement reproduces
 * Compose's single-line Text layout inside the fixed-width column: the
 * text box is min(advance, column width) wide and centred in the column,
 * ink starts at boxLeft + bearing, and ink past the column's right edge
 * is clipped (the paragraph's own clip) — which is what the two over-wide
 * labels ("Video/Photo", "3D Camera") do in build 248.
 */
object DockGeometry {

    const val TILE_COUNT = 12
    const val TILE_DP = 36f
    const val GAP_DP = 8f
    const val PAD_DP = 8f
    const val LABEL_GAP_DP = 2f
    const val CORNER_DP = 8f
    const val BORDER_DP = 1f

    /**
     * Glyph box size per tile (dp), in dock order — build 248's sizes:
     * the vector glyphs draw in a 15 or 16 dp box, the three Material
     * icons (music, sticker, camera) are 15 dp.
     * Order: media, music, fx, text, shapes, sticker, vector, drawing,
     * null, camera, group, chain.
     */
    val GLYPH_DP = floatArrayOf(15f, 15f, 16f, 15f, 16f, 15f, 16f, 16f, 16f, 15f, 16f, 16f)

    /** The scrolling content's total width (dp): both paddings + tiles + gaps. */
    fun contentWidthDp(tileCount: Int = TILE_COUNT): Float =
        if (tileCount <= 0) 0f else 2f * PAD_DP + tileCount * TILE_DP + (tileCount - 1) * GAP_DP

    /** Tile [index]'s left edge (px) inside the scrolling content. */
    fun tileLeftPx(index: Int, density: Float): Float =
        (PAD_DP + index * (TILE_DP + GAP_DP)) * density

    /** Tile column height (px): tile box + label gap + the label's layout height. */
    fun columnHeightPx(labelHeightPx: Float, density: Float): Float =
        (TILE_DP + LABEL_GAP_DP) * density + labelHeightPx

    /** The column's top edge (px): columns are centred vertically in the row area. */
    fun columnTopPx(surfaceHeightPx: Float, labelHeightPx: Float, density: Float): Float =
        (surfaceHeightPx - columnHeightPx(labelHeightPx, density)) / 2f

    /** Glyph box left edge (px): centred inside the 36 dp tile box. */
    fun glyphLeftPx(tileLeftPx: Float, glyphDp: Float, density: Float): Float =
        tileLeftPx + (TILE_DP - glyphDp) * density / 2f

    /** Glyph box top edge (px): centred inside the 36 dp tile box. */
    fun glyphTopPx(columnTopPx: Float, glyphDp: Float, density: Float): Float =
        columnTopPx + (TILE_DP - glyphDp) * density / 2f

    /** The label's baseline (px): its box starts under tile + gap; baseline = boxTop − ascent. */
    fun labelBaselinePx(columnTopPx: Float, density: Float, ascentPx: Float): Float =
        columnTopPx + (TILE_DP + LABEL_GAP_DP) * density - ascentPx

    /**
     * The label blit's horizontal placement. [advancePx] / [bearingPx] are
     * the atlas entry's measured advance and left bearing; [padPx] is the
     * atlas entry padding; [rectWPx] the entry rect's full width. Returns
     * (rectLeftPx, srcWPx): where the entry rect's left edge lands in
     * surface px, and how much of the rect is visible before the column's
     * right-edge clip.
     */
    fun labelBlit(tileLeftPx: Float, advancePx: Float, bearingPx: Float, padPx: Float, rectWPx: Float, density: Float): Pair<Float, Float> {
        val tileWPx = TILE_DP * density
        val boxW = minOf(advancePx, tileWPx)
        val boxLeft = tileLeftPx + (tileWPx - boxW) / 2f
        val rectLeft = boxLeft + bearingPx - padPx
        val srcW = minOf(rectWPx, tileLeftPx + tileWPx - rectLeft)
        return rectLeft to maxOf(0f, srcW)
    }

    /** Press-scale centre (px): the column's centre, bouncyClickable's transform origin. */
    fun pressCenterXPx(tileLeftPx: Float, density: Float): Float =
        tileLeftPx + TILE_DP * density / 2f

    fun pressCenterYPx(columnTopPx: Float, labelHeightPx: Float, density: Float): Float =
        columnTopPx + columnHeightPx(labelHeightPx, density) / 2f
}
