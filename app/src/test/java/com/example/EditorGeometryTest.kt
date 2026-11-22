package com.example

import com.example.model.EditorGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 252 (UI migration stage 5) — pins the value editors' layout
 * law ([EditorGeometry]) against fixtures hand-derived from the
 * build-251 Compose layouts and, for the stock slider, from the
 * material3 1.3.0 library sources: the RulerDialSlider row, the
 * shared value header, the editor screens' chrome, pill strips, the
 * colour swatch grid and the Material slider's drawn form + value
 * law. Fixtures use density 2 so every expected value is exact in
 * float arithmetic unless noted.
 */
class EditorGeometryTest {

    private val d = 2f

    // ── Shared text law ─────────────────────────────────────────────

    @Test
    fun textDstAppliesBearingAndPad() {
        // origin 100, bearing 3, pad 2 → ink lands at 103, rect at 101;
        // baseline 50, base 12 → rect top 38.
        val (x, y) = EditorGeometry.textDst(100f, 50f, 3f, 12f, 2f)
        assertEquals(101f, x, 0f)
        assertEquals(38f, y, 0f)
    }

    @Test
    fun centredOriginAndTextTop() {
        assertEquals(80f, EditorGeometry.centredOriginPx(100f, 40f), 0f)
        assertEquals(15f, EditorGeometry.centredTextTopPx(10f, 30f, 20f), 0f)
    }

    @Test
    fun roundedRectOutlineHasFourArcsWorthOfPoints() {
        val pts = EditorGeometry.roundedRectOutline(0f, 0f, 40f, 20f, 5f, 5f, 5f, 5f, segPerCorner = 4)
        assertEquals(32, pts.size)
        // First point: the top-left arc's start, on the left edge at
        // the corner centre's height: (−20, −10 + 5).
        assertEquals(-20f, pts[0], 0.001f)
        assertEquals(-5f, pts[1], 0.001f)
    }

    @Test
    fun roundedRectOutlineRotationMovesPoints() {
        val flat = EditorGeometry.roundedRectOutline(100f, 100f, 34f, 34f, 6f, 6f, 6f, 6f)
        val spun = EditorGeometry.roundedRectOutline(100f, 100f, 34f, 34f, 6f, 6f, 6f, 6f, rotDeg = 45f)
        // The first point is the top-left arc's start: (100 − 17, 100 − 17 + 6).
        assertEquals(83f, flat[0], 0.001f)
        assertEquals(89f, flat[1], 0.001f)
        // Rotated 45° about the centre: d = (−17, −11) →
        // (100 − 6·cos45, 100 − 28·cos45) ≈ (95.76, 80.20).
        assertEquals(95.76f, spun[0], 0.01f)
        assertEquals(80.20f, spun[1], 0.01f)
    }

    // ── SliderRow ───────────────────────────────────────────────────

    @Test
    fun sliderRowPillSitsAtTheRightEdge() {
        // Row 400 px wide at density 2: pill centre = 400 − 58.
        assertEquals(342f, EditorGeometry.SliderRow.pillCentreXPx(400f, d), 0f)
        assertEquals(36f, EditorGeometry.SliderRow.centreYPx(d), 0f)
    }

    // ── ValueHeader ─────────────────────────────────────────────────

    @Test
    fun valueHeaderValueCentresInTheWeightSpan() {
        // Row 800 px: back box 64, right boxes 128 → span [64, 672],
        // centre 368.
        assertEquals(368f, EditorGeometry.ValueHeader.valueCentreXPx(800f, d), 0f)
        // Diamond box spans [672, 736] → centre 704; easing [736, 800] → 768.
        assertEquals(704f, EditorGeometry.ValueHeader.diamondCentreXPx(800f, d), 0f)
        assertEquals(768f, EditorGeometry.ValueHeader.easingCentreXPx(800f, d), 0f)
        assertEquals(34f, EditorGeometry.ValueHeader.centreYPx(d), 0f)
    }

    @Test
    fun valueHeaderDiamondIsTheRotatedSquare() {
        val pts = EditorGeometry.ValueHeader.diamondOutline(100f, 50f, d)
        assertEquals(32, pts.size)
        // Every point stays inside the rotated square's half-diagonal
        // (34 px side → 34/√2 ≈ 24.04 px), and the outline reaches
        // well past the unrotated half side (17 px): the rounded
        // corner arcs peak at ≈ 15.56 + 6 ≈ 21.6 px from the centre.
        var maxR = 0f
        var i = 0
        while (i < pts.size) {
            val dx = pts[i] - 100f
            val dy = pts[i + 1] - 50f
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            if (r > maxR) maxR = r
            i += 2
        }
        assertTrue(maxR > 20f)
        assertTrue(maxR < 34f / kotlin.math.sqrt(2f))
    }

    // ── ScreenChrome ────────────────────────────────────────────────

    @Test
    fun screenChromePillSizesToItsLabel() {
        // Advance 60 px, density 2: width = 60 + 2×24 = 108.
        assertEquals(108f, EditorGeometry.ScreenChrome.pillWPx(60f, d), 0f)
        // Font box 30 px: 30 + 24 = 54 < 64 (32 dp min) → 64.
        assertEquals(64f, EditorGeometry.ScreenChrome.pillHPx(30f, d), 0f)
        // Font box 50 px: 50 + 24 = 74 > 64 → 74.
        assertEquals(74f, EditorGeometry.ScreenChrome.pillHPx(50f, d), 0f)
    }

    @Test
    fun screenChromeTitleCentresBetweenBackAndPill() {
        // Row 800, pill 108: span [(10+34)×2, 800 − 20 − 108] = [88, 672].
        assertEquals(380f, EditorGeometry.ScreenChrome.titleCentreXPx(800f, 108f, d), 0f)
    }

    @Test
    fun screenChromeTransportSpaceBetween() {
        // Row 800, density 2: content = 800 − 64 = 736; children =
        // 60 + 236 + 124 = 420; gaps = (736 − 420)/2 = 158.
        assertEquals(158f, EditorGeometry.ScreenChrome.spaceBetweenGapPx(800f, d), 0f)
        // Transport left = pad 32 + lead 60 + gap 158 = 250.
        assertEquals(250f, EditorGeometry.ScreenChrome.transportLeftPx(800f, d), 0f)
        // Undo left = 800 − 32 − 124 = 644.
        assertEquals(644f, EditorGeometry.ScreenChrome.undoLeftPx(800f, d), 0f)
        assertEquals(68f, EditorGeometry.ScreenChrome.rowHPx(d), 0f)
    }

    @Test
    fun screenChromeTimecodeWeightsShareTheFlex() {
        // Flex 840: lead = 840 × 0.36/0.84 = 360, ticks = 480
        // (the weight ratio is not exact in float — sub-pixel delta).
        assertEquals(360f, EditorGeometry.ScreenChrome.timecodeLeadPx(840f), 0.01f)
        assertEquals(480f, EditorGeometry.ScreenChrome.timecodeTicksWPx(840f), 0.01f)
    }

    // ── PillStrip ───────────────────────────────────────────────────

    @Test
    fun pillStripWidthsLeftsAndContent() {
        val widths = EditorGeometry.PillStrip.itemWidthsPx(listOf(40f, 60f), 12f, d)
        assertEquals(listOf(88f, 108f), widths)
        val lefts = EditorGeometry.PillStrip.itemLeftsPx(widths, 6f, d, startPx = 10f)
        assertEquals(listOf(10f, 110f), lefts)
        assertEquals(208f, EditorGeometry.PillStrip.contentWPx(widths, 6f, d), 0f)
        assertEquals(0f, EditorGeometry.PillStrip.contentWPx(emptyList(), 6f, d), 0f)
    }

    @Test
    fun pillStripHeightsHugOrHonourTheMinimum() {
        // Font box 30 + 2×6 dp padding (24) = 54.
        assertEquals(54f, EditorGeometry.PillStrip.itemHPx(30f, 6f, d), 0f)
        // With a 32 dp minimum (64 px) the same item is 64.
        assertEquals(64f, EditorGeometry.PillStrip.itemHPx(30f, 6f, 32f, d), 0f)
    }

    // ── SwatchGrid ──────────────────────────────────────────────────

    @Test
    fun swatchGridCellsSplitTheRowEvenly() {
        // Row 416 px, 4 cells, gaps 8: cell = (416 − 24)/4 = 98 (square).
        assertEquals(98f, EditorGeometry.SwatchGrid.cellWPx(416f, 4, d), 0f)
        assertEquals(212f, EditorGeometry.SwatchGrid.cellLeftPx(2, 416f, 4, d), 0f)
        // Row stride = cell + the 8 px spacer: row 1 starts at 106.
        assertEquals(106f, EditorGeometry.SwatchGrid.rowTopPx(1, 98f, d), 0f)
        // 3 rows: 3 × 106 = 318 (trailing spacer included).
        assertEquals(318f, EditorGeometry.SwatchGrid.gridHPx(3, 98f, d), 0f)
        assertEquals(0f, EditorGeometry.SwatchGrid.gridHPx(0, 98f, d), 0f)
    }

    // ── M3Slider ────────────────────────────────────────────────────

    @Test
    fun m3SliderTrackGeometry() {
        val g = EditorGeometry.M3Slider
        // Width 400 px, density 2, idle thumb 8 px: track [4, 396].
        assertEquals(8f, g.thumbWPx(false, d), 0f)
        assertEquals(4f, g.thumbWPx(true, d), 0f)
        assertEquals(4f, g.trackLeftPx(false, d), 0f)
        assertEquals(392f, g.trackWPx(400f, false, d), 0f)
        // Pressed: thumb 4 px → track [2, 398].
        assertEquals(2f, g.trackLeftPx(true, d), 0f)
        assertEquals(396f, g.trackWPx(400f, true, d), 0f)
        // End gap: idle 4 + 12 = 16; pressed 2 + 12 = 14.
        assertEquals(16f, g.endGapPx(false, d), 0f)
        assertEquals(14f, g.endGapPx(true, d), 0f)
        // Stop centre: 4 + 392 − 16 = 380.
        assertEquals(380f, g.stopCentreXPx(400f, false, d), 0f)
    }

    @Test
    fun m3SliderThumbPlacementRoundsLikeTheLibrary() {
        val g = EditorGeometry.M3Slider
        // Track 392 × fraction 0.5 = 196 exactly; 0.333 → 130.5 → 131
        // (the library rounds the thumb's placement to whole px).
        assertEquals(196, g.thumbLeftPx(400f, 0.5f, false, d))
        assertEquals(131, g.thumbLeftPx(400f, 0.333f, false, d))
        assertEquals(196f, g.valueEndPx(400f, 0.5f, false, d), 0f)
    }

    @Test
    fun m3SliderSegmentVisibilityRules() {
        val g = EditorGeometry.M3Slider
        // fraction 0: value end 0 → active not drawn (0 − 16 < 16),
        // inactive drawn.
        assertFalse(g.activeDrawn(400f, 0f, false, d))
        assertTrue(g.inactiveDrawn(400f, 0f, false, d))
        // fraction 1: value end 392 → inactive not drawn
        // (392 !< 392 − 16 − 16), active drawn.
        assertTrue(g.activeDrawn(400f, 1f, false, d))
        assertFalse(g.inactiveDrawn(400f, 1f, false, d))
        // fraction 0.5: both drawn.
        assertTrue(g.activeDrawn(400f, 0.5f, false, d))
        assertTrue(g.inactiveDrawn(400f, 0.5f, false, d))
    }

    // ── Transform panel (build 255: card-grid redesign) ─────────────

    @Test
    fun transformSegmentedGeometry() {
        val g = EditorGeometry.TransformPanel
        // Track: 2 segments of 54 dp + 2 dp pad on both sides.
        assertEquals(224f, g.segTrackWPx(d), 0f)
        assertEquals(4f, g.segLeftPx(0, d), 0f)
        assertEquals(112f, g.segLeftPx(1, d), 0f)
        assertEquals(58f, g.segCentreXPx(0, d), 0f)
        assertEquals(166f, g.segCentreXPx(1, d), 0f)
        // The second segment ends flush with the track's inner pad.
        assertEquals(
            g.segTrackWPx(d) - g.SEG_PAD_DP * d,
            g.segLeftPx(1, d) + g.SEG_W_DP * d, 0f
        )
        assertEquals(32f, g.headerCentreYPx(d), 0f)
    }

    @Test
    fun transformRightBoxGeometry() {
        val g = EditorGeometry.TransformPanel
        // 32 dp boxes flush right in a 400 px row: centres at 368 / 304 / 240.
        assertEquals(368f, g.rightBoxCentreXPx(400f, 0, d), 0f)
        assertEquals(304f, g.rightBoxCentreXPx(400f, 1, d), 0f)
        assertEquals(240f, g.rightBoxCentreXPx(400f, 2, d), 0f)
        // Link chip: 2×10 pad + 16 icon + 5 gap around a 30 px label.
        assertEquals(112f, g.linkChipWPx(30f, d), 0f)
        // With 3 boxes to its right, the chip's right edge is at 400 − 192.
        assertEquals(96f, g.linkChipLeftPx(400f, 112f, 3, d), 0f)
        assertEquals(
            400f - 3 * g.ICON_BOX_DP * d,
            g.linkChipLeftPx(400f, 112f, 3, d) + 112f, 0f
        )
    }

    @Test
    fun transformCardGridGeometry() {
        val g = EditorGeometry.TransformPanel
        // 400×200 px grid, 8 dp (16 px) gap: cells 192×92.
        assertEquals(192f, g.cellWPx(400f, d), 0f)
        assertEquals(92f, g.cellHPx(200f, d), 0f)
        assertEquals(0f, g.cellLeftPx(0, 400f, d), 0f)
        assertEquals(208f, g.cellLeftPx(1, 400f, d), 0f)
        assertEquals(0f, g.cellTopPx(0, 200f, d), 0f)
        assertEquals(108f, g.cellTopPx(1, 200f, d), 0f)
        // The second column / row ends flush with the grid's edge.
        assertEquals(400f, g.cellLeftPx(1, 400f, d) + g.cellWPx(400f, d), 0f)
        assertEquals(200f, g.cellTopPx(1, 200f, d) + g.cellHPx(200f, d), 0f)
        // Card internals: top-row centre, diamond centre, label origin,
        // right-aligned value origin, ruler strip top.
        assertEquals(34f, g.topRowCentreYPx(d), 0f)
        assertEquals(40f, g.diamondCentreXPx(d), 0f)
        assertEquals(68f, g.labelOriginXPx(d), 0f)
        assertEquals(128f, g.valueOriginXPx(192f, 40f, d), 0f)
        assertEquals(52f, g.stripTopPx(92f, d), 0f)
    }

    @Test
    fun transformPadReadoutGeometry() {
        val g = EditorGeometry.TransformPanel
        // Advances: "X"=10, "-27"=30, "Y"=10, "0"=10 at density 2:
        // gaps 3 dp (6 px) after labels, 6 dp (12 px) between groups.
        val lefts = g.readoutLeftsPx(listOf(10f, 30f, 10f, 10f), d, 20f)
        assertEquals(listOf(20f, 36f, 78f, 94f), lefts)
        // The joint diamond hugs the pad's top-right (6 dp inset).
        assertEquals(364f, g.padDiamondCentreXPx(400f, d), 0f)
        assertEquals(36f, g.padDiamondCentreYPx(d), 0f)
    }

    @Test
    fun transformQuickRowGeometry() {
        val g = EditorGeometry.TransformPanel
        // 400 px row, 4 chips, 8 dp gaps: (400 − 48) / 4 = 88 px each.
        assertEquals(88f, g.chipWPx(400f, 4, d), 0f)
        assertEquals(0f, g.chipLeftPx(0, 400f, 4, d), 0f)
        assertEquals(104f, g.chipLeftPx(1, 400f, 4, d), 0f)
        assertEquals(312f, g.chipLeftPx(3, 400f, 4, d), 0f)
        assertEquals(400f, g.chipLeftPx(3, 400f, 4, d) + g.chipWPx(400f, 4, d), 0f)
        assertEquals(0f, g.chipWPx(400f, 0, d), 0f)
    }

    @Test
    fun m3SliderValueLawRoundTripsAndClamps() {
        val g = EditorGeometry.M3Slider
        // Offsets live in [4, 396] for width 400 at density 2.
        assertEquals(4f, g.offsetOfValue(400f, 0f, 0f, 100f, false, d), 0f)
        assertEquals(396f, g.offsetOfValue(400f, 100f, 0f, 100f, false, d), 0f)
        assertEquals(200f, g.offsetOfValue(400f, 50f, 0f, 100f, false, d), 0f)
        assertEquals(50f, g.valueOfOffset(400f, 200f, 0f, 100f, false, d), 0f)
        // Out-of-range offsets clamp to the ends.
        assertEquals(0f, g.valueOfOffset(400f, -50f, 0f, 100f, false, d), 0f)
        assertEquals(100f, g.valueOfOffset(400f, 999f, 0f, 100f, false, d), 0f)
        // A non-zero-based range: 0.2..0.9 at the midpoint offset.
        assertEquals(0.55f, g.valueOfOffset(400f, 200f, 0.2f, 0.9f, false, d), 0.001f)
        // fractionOf coerces.
        assertEquals(0f, g.fractionOf(-5f, 0f, 100f), 0f)
        assertEquals(1f, g.fractionOf(105f, 0f, 100f), 0f)
        assertEquals(0f, g.fractionOf(5f, 10f, 10f), 0f)
    }
}
