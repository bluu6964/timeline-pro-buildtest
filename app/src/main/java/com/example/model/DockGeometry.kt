package com.example.model

/**
 * Build 249 (UI migration stage 2) — the editor dock's layout law, extracted
 * from BottomToolbar's Compose layout (build 248) so the whole dock — tile
 * backgrounds, borders, glyphs, labels — can be rasterized by the C++ GL
 * renderer as ONE surface instead of twelve tiles each carrying its own
 * glyph TextureView.
 *
 * The build-248 layout being mirrored: a horizontally scrolling row with
 * 8 dp horizontal padding and 8 dp gaps; each tile is a square column —
 * the tile box (corner 8 dp, 1 dp border) with the glyph centred, a
 * 2 dp spacer, then the label (9.5 sp SemiBold, single line) — and the
 * column is centred vertically in the dock's row area. The press scale
 * (bouncyClickable, 0.94) transforms the whole column about its centre.
 *
 * Build 257 (Sarowar's order, his exact numbers): the tile grows from
 * build 240's compacted 36 dp to **44 dp** and every glyph grows **40%**
 * (15 → 21 dp, 16 → 22.4 dp). Labels are NEVER clipped any more: a
 * label whose advance exceeds the tile width is auto-fit — its atlas
 * blit shrinks by [labelFitScale] until the whole word fits (the two
 * over-wide labels are "Video/Photo" and "3D Camera").
 *
 * All functions work in raw pixels; [density] converts the dp constants
 * exactly as Compose's `Dp.toPx()` did.
 */
object DockGeometry {

    const val TILE_COUNT = 12
    const val TILE_DP = 44f
    const val GAP_DP = 8f
    const val PAD_DP = 8f
    const val LABEL_GAP_DP = 2f
    const val CORNER_DP = 8f
    const val BORDER_DP = 1f

    /** Floor for the label auto-fit scale (no current label reaches it). */
    const val LABEL_MIN_SCALE = 0.6f

    /**
     * Glyph box size per tile (dp), in dock order — build 248's sizes
     * (15 / 16 dp) scaled by build 257's ordered +40%: 21 / 22.4 dp.
     * The three Material icons (music, sticker, camera) are the 21 dp
     * entries.
     * Order: media, music, fx, text, shapes, sticker, vector, drawing,
     * null, camera, group, chain.
     */
    val GLYPH_DP = floatArrayOf(21f, 21f, 22.4f, 21f, 22.4f, 21f, 22.4f, 22.4f, 22.4f, 21f, 22.4f, 22.4f)

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

    /** Glyph box left edge (px): centred inside the tile box. */
    fun glyphLeftPx(tileLeftPx: Float, glyphDp: Float, density: Float): Float =
        tileLeftPx + (TILE_DP - glyphDp) * density / 2f

    /** Glyph box top edge (px): centred inside the tile box. */
    fun glyphTopPx(columnTopPx: Float, glyphDp: Float, density: Float): Float =
        columnTopPx + (TILE_DP - glyphDp) * density / 2f

    /** The label's baseline (px): its box starts under tile + gap; baseline = boxTop − ascent. */
    fun labelBaselinePx(columnTopPx: Float, density: Float, ascentPx: Float): Float =
        columnTopPx + (TILE_DP + LABEL_GAP_DP) * density - ascentPx

    /**
     * The label's auto-fit scale (build 257): 1 while the label's
     * advance fits the tile width, else tileWidth / advance, floored at
     * [LABEL_MIN_SCALE]. Replaces the build-248/249 clip rule — a dock
     * label is never cut off mid-word again ("Video/P…").
     */
    fun labelFitScale(advancePx: Float, density: Float): Float {
        val tileWPx = TILE_DP * density
        if (advancePx <= tileWPx) return 1f
        return maxOf(LABEL_MIN_SCALE, tileWPx / advancePx)
    }

    /**
     * The label blit's placement WITH auto-fit. [advancePx] / [bearingPx]
     * are the atlas entry's measured advance and left bearing; [padPx]
     * is the atlas entry padding; [rectWPx] / [rectHPx] the entry rect's
     * full size. Returns (rectLeftPx, dstWPx, dstHPx): where the entry
     * rect's left edge lands in surface px, and the rect's destination
     * size when the FULL rect (srcW = rectWPx, srcH = rectHPx) is blitted
     * at [labelFitScale] — the scaled advance centres in the tile, ink
     * starts at boxLeft + bearing·scale, the rect's padding scales with
     * the ink. The caller anchors the baseline the same way: dstY =
     * baseline − base·scale.
     */
    fun labelBlit(tileLeftPx: Float, advancePx: Float, bearingPx: Float, padPx: Float, rectWPx: Float, rectHPx: Float, density: Float): Triple<Float, Float, Float> {
        val tileWPx = TILE_DP * density
        val s = labelFitScale(advancePx, density)
        val boxLeft = tileLeftPx + (tileWPx - advancePx * s) / 2f
        val rectLeft = boxLeft + (bearingPx - padPx) * s
        return Triple(rectLeft, rectWPx * s, rectHPx * s)
    }

    /** Press-scale centre (px): the column's centre, bouncyClickable's transform origin. */
    fun pressCenterXPx(tileLeftPx: Float, density: Float): Float =
        tileLeftPx + TILE_DP * density / 2f

    fun pressCenterYPx(columnTopPx: Float, labelHeightPx: Float, density: Float): Float =
        columnTopPx + columnHeightPx(labelHeightPx, density) / 2f
}
