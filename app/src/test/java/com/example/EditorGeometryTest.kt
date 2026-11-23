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
    fun labelLineBoxHugsItsAdvance() {
        val g = EditorGeometry.LabelLine
        // Unpinned host: the box is exactly the drawn run's advance —
        // a Row sibling after the label keeps the row's other width
        // (build 267: the box previously took the full max width and
        // starved the Text editor's slider / swatch / pill rows).
        assertEquals(120f, g.boxWPx(120f, 0f, 600f), 0f)
        // An advance past the host's max clamps to the max (the
        // ellipsize case keeps today's full-width box).
        assertEquals(600f, g.boxWPx(900f, 0f, 600f), 0f)
        // A width-pinned host (fillMaxWidth / width(dp): min == max)
        // keeps exactly its pinned width.
        assertEquals(600f, g.boxWPx(120f, 600f, 600f), 0f)
        assertEquals(32f, g.boxWPx(120f, 32f, 32f), 0f)
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
        // Row 400 px wide at density 2: pill centre = 400 − 45 (build 257 scale).
        assertEquals(355f, EditorGeometry.SliderRow.pillCentreXPx(400f, d), 0f)
        assertEquals(28f, EditorGeometry.SliderRow.centreYPx(d), 0f)
    }

    // ── ValueHeader ─────────────────────────────────────────────────

    @Test
    fun valueHeaderValueCentresInTheWeightSpan() {
        // Row 800 px: back box 50, right boxes 100 → span [50, 700],
        // centre 375 (build 257 scale).
        assertEquals(375f, EditorGeometry.ValueHeader.valueCentreXPx(800f, d), 0f)
        // Diamond box spans [700, 750] → centre 725; easing [750, 800] → 775.
        assertEquals(725f, EditorGeometry.ValueHeader.diamondCentreXPx(800f, d), 0f)
        assertEquals(775f, EditorGeometry.ValueHeader.easingCentreXPx(800f, d), 0f)
        assertEquals(26.5f, EditorGeometry.ValueHeader.centreYPx(d), 0f)
    }

    @Test
    fun valueHeaderDiamondIsTheRotatedSquare() {
        val pts = EditorGeometry.ValueHeader.diamondOutline(100f, 50f, d)
        assertEquals(32, pts.size)
        // Every point stays inside the rotated square's half-diagonal
        // (26 px side → 26/√2 ≈ 18.38 px), and the outline reaches
        // well past the unrotated half side (13 px): the rounded
        // corner arcs peak at ≈ 11.31 + 5 ≈ 16.3 px from the centre
        // (build 257 scale: 13 dp diamond, 2.5 dp corners).
        var maxR = 0f
        var i = 0
        while (i < pts.size) {
            val dx = pts[i] - 100f
            val dy = pts[i + 1] - 50f
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            if (r > maxR) maxR = r
            i += 2
        }
        assertTrue(maxR > 15f)
        assertTrue(maxR < 26f / kotlin.math.sqrt(2f))
    }

    // ── ScreenChrome ────────────────────────────────────────────────

    @Test
    fun screenChromePillSizesToItsLabel() {
        // Advance 60 px, density 2: width = 60 + 2×19 = 98 (build 257 scale).
        assertEquals(98f, EditorGeometry.ScreenChrome.pillWPx(60f, d), 0f)
        // Font box 30 px: 30 + 18 = 48 < 50 (25 dp min) → 50.
        assertEquals(50f, EditorGeometry.ScreenChrome.pillHPx(30f, d), 0f)
        // Font box 50 px: 50 + 18 = 68 > 50 → 68.
        assertEquals(68f, EditorGeometry.ScreenChrome.pillHPx(50f, d), 0f)
    }

    @Test
    fun screenChromeTitleCentresBetweenBackAndPill() {
        // Row 800, pill 108: span [(8+26.5)×2, 800 − 16 − 108] = [69, 676] → 372.5.
        assertEquals(372.5f, EditorGeometry.ScreenChrome.titleCentreXPx(800f, 108f, d), 0f)
    }

    @Test
    fun screenChromeTransportSpaceBetween() {
        // Row 800, density 2: content = 800 − 50 = 750; children =
        // 47 + 185 + 97 = 329; gaps = (750 − 329)/2 = 210.5 (build 257 scale).
        assertEquals(210.5f, EditorGeometry.ScreenChrome.spaceBetweenGapPx(800f, d), 0f)
        // Transport left = pad 25 + lead 47 + gap 210.5 = 282.5.
        assertEquals(282.5f, EditorGeometry.ScreenChrome.transportLeftPx(800f, d), 0f)
        // Undo left = 800 − 25 − 97 = 678.
        assertEquals(678f, EditorGeometry.ScreenChrome.undoLeftPx(800f, d), 0f)
        assertEquals(53f, EditorGeometry.ScreenChrome.rowHPx(d), 0f)
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
        // Row 416 px, 4 cells, gaps 6: cell = (416 − 18)/4 = 99.5 (square).
        assertEquals(99.5f, EditorGeometry.SwatchGrid.cellWPx(416f, 4, d), 0f)
        assertEquals(211f, EditorGeometry.SwatchGrid.cellLeftPx(2, 416f, 4, d), 0f)
        // Row stride = cell + the 6 px spacer: row 1 starts at 105.5.
        assertEquals(105.5f, EditorGeometry.SwatchGrid.rowTopPx(1, 99.5f, d), 0f)
        // 3 rows: 3 × 105.5 = 316.5 (trailing spacer included).
        assertEquals(316.5f, EditorGeometry.SwatchGrid.gridHPx(3, 99.5f, d), 0f)
        assertEquals(0f, EditorGeometry.SwatchGrid.gridHPx(0, 99.5f, d), 0f)
    }

    // ── M3Slider ────────────────────────────────────────────────────

    @Test
    fun m3SliderTrackGeometry() {
        val g = EditorGeometry.M3Slider
        // Width 400 px, density 2, idle thumb 6 px: track [3, 397] (build 257 scale).
        assertEquals(6f, g.thumbWPx(false, d), 0f)
        assertEquals(3f, g.thumbWPx(true, d), 0f)
        assertEquals(3f, g.trackLeftPx(false, d), 0f)
        assertEquals(394f, g.trackWPx(400f, false, d), 0f)
        // Pressed: thumb 3 px → track [1.5, 398.5].
        assertEquals(1.5f, g.trackLeftPx(true, d), 0f)
        assertEquals(397f, g.trackWPx(400f, true, d), 0f)
        // End gap: idle 3 + 9 = 12; pressed 1.5 + 9 = 10.5.
        assertEquals(12f, g.endGapPx(false, d), 0f)
        assertEquals(10.5f, g.endGapPx(true, d), 0f)
        // Stop centre: 3 + 394 − 12 = 385.
        assertEquals(385f, g.stopCentreXPx(400f, false, d), 0f)
    }

    @Test
    fun m3SliderThumbPlacementRoundsLikeTheLibrary() {
        val g = EditorGeometry.M3Slider
        // Track 394 × fraction 0.5 = 197 exactly; 0.333 → 131.2 → 131
        // (the library rounds the thumb's placement to whole px).
        assertEquals(197, g.thumbLeftPx(400f, 0.5f, false, d))
        assertEquals(131, g.thumbLeftPx(400f, 0.333f, false, d))
        assertEquals(197f, g.valueEndPx(400f, 0.5f, false, d), 0f)
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

    @Test
    fun m3SliderValueLawRoundTripsAndClamps() {
        val g = EditorGeometry.M3Slider
        // Offsets live in [3, 397] for width 400 at density 2.
        assertEquals(3f, g.offsetOfValue(400f, 0f, 0f, 100f, false, d), 0f)
        assertEquals(397f, g.offsetOfValue(400f, 100f, 0f, 100f, false, d), 0f)
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
