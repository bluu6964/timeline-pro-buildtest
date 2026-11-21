package com.example

import com.example.model.SheetGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 253 (UI migration stage 6) — pins the sheets' & dialogs'
 * layout law ([SheetGeometry]) against fixtures hand-derived from
 * the build-252 Compose layouts: the pill atoms' hug sizes, the drag
 * handle, the Material switch, the sheets' list rows, the aspect
 * sheet's cards and chips, the shape/add-layer tiles, the
 * missing-media rows, the project cards and the fullscreen pills.
 * Fixtures use density 2 so every expected value is exact in float
 * arithmetic; text measures are synthetic caller inputs — the law's
 * arithmetic is what is pinned.
 */
class SheetGeometryTest {

    private val d = 2f

    // ── Pill atoms ──────────────────────────────────────────────────

    @Test
    fun pillButtonHeightFloorsAt32Dp() {
        // 12 sp label, font box 29 px at density 2: 29 + 28 = 57 < 64.
        assertEquals(64f, SheetGeometry.PillButton.heightPx(29f, d), 0f)
        // A tall label wins: 50 + 28 = 78.
        assertEquals(78f, SheetGeometry.PillButton.heightPx(50f, d), 0f)
    }

    @Test
    fun pillButtonHugWidthAddsPaddingBothSides() {
        // Advance 90 px + 2 * 14 dp * 2 = 146.
        assertEquals(146f, SheetGeometry.PillButton.hugWidthPx(90f, d), 0f)
    }

    @Test
    fun whitePillSizes() {
        // Advance 120, font box 30: width 120 + 48 = 168, height 30 + 28 = 58.
        assertEquals(168f, SheetGeometry.WhitePill.widthPx(120f, d), 0f)
        assertEquals(58f, SheetGeometry.WhitePill.heightPx(30f, d), 0f)
    }

    @Test
    fun violetPillSizes() {
        // Padding 14 / 9: width 120 + 56 = 176, height 30 + 36 = 66.
        assertEquals(176f, SheetGeometry.VioletPill.widthPx(120f, d), 0f)
        assertEquals(66f, SheetGeometry.VioletPill.heightPx(30f, d), 0f)
    }

    // ── Drag handle ─────────────────────────────────────────────────

    @Test
    fun handleStripIsFourteenDpTall() {
        // (7 + 3 + 4) dp * 2 = 28 px; the pill centre sits at (7 + 1.5) * 2 = 17.
        assertEquals(28f, SheetGeometry.Handle.totalHeightPx(d), 0f)
        assertEquals(17f, SheetGeometry.Handle.pillCentreYPx(d), 0f)
    }

    // ── Switch ──────────────────────────────────────────────────────

    @Test
    fun switchThumbCentres() {
        // Off: edge 4 dp + radius 8 dp = 12 dp -> 24 px.
        assertEquals(24f, SheetGeometry.Switch.thumbCentreXPx(false, d), 0f)
        // On: 52 - 4 - 12 = 36 dp -> 72 px; radii 16 / 24 px.
        assertEquals(72f, SheetGeometry.Switch.thumbCentreXPx(true, d), 0f)
        assertEquals(16f, SheetGeometry.Switch.thumbRadiusPx(false, d), 0f)
        assertEquals(24f, SheetGeometry.Switch.thumbRadiusPx(true, d), 0f)
    }

    // ── List rows ───────────────────────────────────────────────────

    @Test
    fun listRowHeights() {
        // Two lines (30 + 22) + 2 * 9 dp * 2 = 88.
        assertEquals(88f, SheetGeometry.ListRow.twoLineHeightPx(30f, 22f, d), 0f)
        // One line 30 + 36 = 66.
        assertEquals(66f, SheetGeometry.ListRow.oneLineHeightPx(30f, d), 0f)
        // A 52 px stack centred in an 88 px row starts at 18.
        assertEquals(18f, SheetGeometry.ListRow.stackTopPx(88f, 52f), 0f)
    }

    // ── Aspect sheet ────────────────────────────────────────────────

    @Test
    fun aspectShapeSizes() {
        // Landscape 16:9 -> 64 x 36 px; portrait 9:16 -> 36 x 64; square 64 x 64.
        assertEquals(64f to 36f, SheetGeometry.Aspect.shapeSizePx(16f / 9f, d))
        assertEquals(36f to 64f, SheetGeometry.Aspect.shapeSizePx(9f / 16f, d))
        assertEquals(64f to 64f, SheetGeometry.Aspect.shapeSizePx(1f, d))
    }

    @Test
    fun aspectStackTopCentresInCard() {
        // Card 156 px; stack = 68 (box) + 12 (gap) + 30 (label) = 110; top = 23.
        assertEquals(23f, SheetGeometry.Aspect.stackTopPx(30f, d), 0f)
    }

    @Test
    fun aspectResChipSizes() {
        // Label advance 80 beats detail 70: width 80 + 52 = 132.
        assertEquals(132f, SheetGeometry.Aspect.resChipWidthPx(80f, 70f, d), 0f)
        // Height: 28 + 22 + 32 = 82.
        assertEquals(82f, SheetGeometry.Aspect.resChipHeightPx(28f, 22f, d), 0f)
    }

    // ── Shape / add-layer tiles ─────────────────────────────────────

    @Test
    fun shapeCardHeight() {
        // Tile 100 px + gap 8 + label 20 = 128.
        assertEquals(128f, SheetGeometry.ShapeCard.cardHeightPx(100f, 20f, d), 0f)
    }

    @Test
    fun addTileHeight() {
        // (20 + 4 + 20) dp * 2 + label 22 = 110.
        assertEquals(110f, SheetGeometry.AddTile.heightPx(22f, d), 0f)
    }

    // ── Missing-media rows ──────────────────────────────────────────

    @Test
    fun missingRowHeightTakesTheTallerSide() {
        // Stack 30 + 20 = 50 beats the 64 px pill? No: 64 wins, + 20 pad = 84.
        assertEquals(84f, SheetGeometry.MissingRow.heightPx(30f, 20f, 64f, d), 0f)
        // Stack 60 + 20 = 80 beats a 40 px pill: 80 + 20 = 100.
        assertEquals(100f, SheetGeometry.MissingRow.heightPx(60f, 20f, 40f, d), 0f)
    }

    // ── Project cards ───────────────────────────────────────────────

    @Test
    fun projectCardHeights() {
        // Stack 28 + 20 = 48 beats the 60 px menu: body = 48... no,
        // menu 60 wins: body = 60 + 26 = 86; card = 116 + 86 = 202.
        assertEquals(86f, SheetGeometry.ProjectCard.bodyHeightPx(28f, 20f, d), 0f)
        assertEquals(202f, SheetGeometry.ProjectCard.cardHeightPx(28f, 20f, d), 0f)
        // A tall stack (40 + 30 = 70) beats the menu: body = 70 + 26 = 96.
        assertEquals(96f, SheetGeometry.ProjectCard.bodyHeightPx(40f, 30f, d), 0f)
    }

    // ── Fullscreen pills ────────────────────────────────────────────

    @Test
    fun fullscreenPillSizes() {
        // Timecode: advance 150 + 40 = 190 wide, text 24 + 20 = 44 high.
        assertEquals(190f to 44f, SheetGeometry.FullscreenPill.timecodeSizePx(150f, 24f, d))
        // Exit: advance 110 + 44 = 154 wide, text 22 + 24 = 46 high.
        assertEquals(154f to 46f, SheetGeometry.FullscreenPill.exitSizePx(110f, 22f, d))
    }
}
