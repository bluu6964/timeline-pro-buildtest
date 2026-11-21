package com.example

import com.example.model.ChromeGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 250 (UI migration stage 3) — pins the editor chrome's layout
 * law ([ChromeGeometry]) against fixtures hand-derived from the
 * build-249 Compose layouts: the controls bar's groups and badge, the
 * app header's right group, the layer header's zones and name column,
 * the selected header's 167 dp fixed sum and 180 dp name cap, the
 * multi-select bar's panel/tiles/SpaceEvenly boolean row, the shared
 * text placement and the ellipsis rule. Fixtures use density 2 so
 * every expected value is exact in float arithmetic.
 */
class ChromeGeometryTest {

    private val d = 2f

    // ── Shared text law ─────────────────────────────────────────────

    @Test
    fun baselineIsBoxTopMinusAscent() {
        assertEquals(130f, ChromeGeometry.baselinePx(100f, -30f), 0f)
        assertEquals(85f, ChromeGeometry.centeredBoxTopPx(100f, 30f), 0f)
    }

    @Test
    fun ellipsizeKeepsFittingText() {
        val measure = { s: String -> s.length * 10f }
        assertEquals("Layers", ChromeGeometry.ellipsize("Layers", 100f, measure))
        assertEquals("", ChromeGeometry.ellipsize("", 0f, measure))
    }

    @Test
    fun ellipsizeTruncatesToLongestFittingPrefix() {
        val measure = { s: String -> s.length * 10f }
        // cap 60: "…" costs 10, so 5 chars fit ("Layer…"), 6 do not.
        assertEquals("Layer…", ChromeGeometry.ellipsize("Layer name", 60f, measure))
        // cap below the ellipsis itself: the ellipsis is still returned.
        assertEquals("…", ChromeGeometry.ellipsize("Layer name", 5f, measure))
    }

    @Test
    fun ellipsizeNeverSplitsACombiningSequence() {
        // "a" + combining acute (U+0301) + "bcdef": the cut must not
        // strand the combining mark — prefixes are "a" + mark or longer.
        val text = "ábcdef"
        val measure = { s: String -> s.length * 10f }
        // cap 40: "…" (10) + 3 UTF-16 units = 40 — "a"+mark+"b" is a
        // valid cut (3 units); cutting after "a" alone (1 unit) would
        // also fit but is shorter, so the search lands on the longer.
        assertEquals("áb…", ChromeGeometry.ellipsize(text, 40f, measure))
    }

    // ── Controls bar ────────────────────────────────────────────────

    @Test
    fun controlsGroupsMirrorBuild249() {
        val c = ChromeGeometry.Controls
        // Bar 800x56 px: buttons (56 dp) centred → top 0.
        assertEquals(0f, c.btnTopPx(56f, d), 0f)
        assertEquals(24f, c.undoLeftPx(d), 0f)
        assertEquals(100f, c.redoLeftPx(d), 0f)
        assertEquals(176f, c.badgeLeftPx(d), 0f)
        assertEquals(372f, c.playLeftPx(800f, d), 0f) // (800 - 56) / 2
        assertEquals(568f, c.rightLeftPx(800f, d, 0), 0f) // 800 - 232
        assertEquals(644f, c.rightLeftPx(800f, d, 1), 0f)
        assertEquals(720f, c.rightLeftPx(800f, d, 2), 0f)
    }

    @Test
    fun controlsGlyphBoxesAreCentred() {
        val c = ChromeGeometry.Controls
        // 19 dp play glyph in a 28 dp button at density 2: inset 9 px.
        assertEquals(381f, c.glyphLeftPx(372f, 19f, d), 0f)
        assertEquals(9f, c.glyphTopPx(0f, 19f, d), 0f)
        // 17 dp glyph: inset 11 px.
        assertEquals(579f, c.glyphLeftPx(568f, 17f, d), 0f)
    }

    @Test
    fun controlsBadgeSizesFromTheLabel() {
        val c = ChromeGeometry.Controls
        // advance 40 px, text height 24 px at density 2.
        assertEquals(56f, c.badgeWidthPx(40f, d), 0f) // 40 + 8*2
        assertEquals(28f, c.badgeHeightPx(24f, d), 0f) // 24 + 2*2
        // Visual centred in the 56 px touch box at left 176.
        assertEquals(176f, c.badgeVisualLeftPx(40f, d), 0f) // 176 + (56-56)/2
        assertEquals(14f, c.badgeVisualTopPx(56f, 24f, d), 0f) // (56-28)/2
        assertEquals(16f, c.badgeTextTopPx(56f, 24f, d), 0f) // 14 + 1*2
    }

    @Test
    fun controlsDotSitsAtGlyphBottomEnd() {
        val c = ChromeGeometry.Controls
        // Keyframe glyph box: left 731, top 11, size 34 (17 dp at d=2) →
        // right 765, bottom 45; dot centre inset 5.5 px.
        assertEquals(759.5f, c.dotCenterXPx(765f, d), 0f)
        assertEquals(39.5f, c.dotCenterYPx(45f, d), 0f)
        assertEquals(5.5f, c.dotRadiusPx(d), 0f)
        assertEquals(4.5f, c.dotRingRadiusPx(d), 0f)
        assertEquals(54f, c.hairlineTopPx(56f, d), 0f)
    }

    // ── App header ──────────────────────────────────────────────────

    @Test
    fun headerRightGroupMirrorsBuild249() {
        val h = ChromeGeometry.Header
        // Bar 800x64 px (32 dp slot at d=2 → buttons fill it: top 0).
        assertEquals(0f, h.btnTopPx(64f, d), 0f)
        assertEquals(20f, h.closeLeftPx(d), 0f)
        assertEquals(716f, h.exportLeftPx(800f, d), 0f) // 800 - 42*2
        // Pill: advance 50 px → width 50 + 31*2 = 112.
        assertEquals(112f, h.pillWidthPx(50f, d), 0f)
        assertEquals(590f, h.pillLeftPx(800f, 112f, d), 0f) // 716 - 14 - 112
        assertEquals(512f, h.ratioLeftPx(590f, d), 0f) // 590 - 39*2
        assertEquals(610f, h.pillTextLeftPx(590f, d), 0f)
        assertEquals(668f, h.triLeftPx(590f, 50f, d), 0f) // 590 + 28 + 50
        assertEquals(24f, h.triTopPx(64f, d), 0f) // (32-8)/2 * 2
    }

    @Test
    fun headerFaceAndGlyphInsets() {
        val h = ChromeGeometry.Header
        assertEquals(62f, h.faceSizePx(d), 0f) // (32-1) * 2
        assertEquals(17f, h.faceRadiusPx(d), 0f) // (9-0.5) * 2
        // 16 dp glyph in a 32 dp button: inset 16 px at d=2.
        assertEquals(528f, h.glyphLeftPx(512f, 16f, d), 0f)
        assertEquals(16f, h.glyphTopPx(64f, 16f, d), 0f)
        // 12 dp close glyph: inset 20 px.
        assertEquals(40f, h.glyphLeftPx(20f, 12f, d), 0f)
    }

    // ── Layer header ────────────────────────────────────────────────

    @Test
    fun layerHeaderZonesAndColumn() {
        val l = ChromeGeometry.LayerHeader
        // Slot 96 px tall (48 dp) → zone = 36 dp = 72 px, top = 12.
        assertEquals(36f, l.zoneDp(48f), 0f)
        assertEquals(12f, l.zoneTopPx(96f, d), 0f)
        assertEquals(8f, l.backLeftPx(d), 0f)
        assertEquals(80f, l.slotLeftPx(d, 96f), 0f) // (4+36)*2
        // W=800, 3 icon zones: slot = 800 - 16 - 72*4 = 496.
        assertEquals(496f, l.slotWidthPx(800f, 96f, d, 3), 0f)
        assertEquals(84f, l.contentLeftPx(d, 96f), 0f)
        assertEquals(476f, l.contentWidthPx(800f, 96f, d, 3), 0f) // 496 - 20
        // Trailing zones start right after the slot.
        assertEquals(576f, l.zoneLeftPx(800f, 96f, d, 3, 0), 0f)
        assertEquals(648f, l.zoneLeftPx(800f, 96f, d, 3, 1), 0f)
        assertEquals(720f, l.zoneLeftPx(800f, 96f, d, 3, 2), 0f)
    }

    @Test
    fun layerHeaderColumnAndUnderline() {
        val l = ChromeGeometry.LayerHeader
        // Text height 40 px: renameable column = 40 + 3*2 = 46, top = 25.
        assertEquals(46f, l.columnHeightPx(40f, d, true), 0f)
        assertEquals(40f, l.columnHeightPx(40f, d, false), 0f)
        assertEquals(25f, l.columnTopPx(96f, 40f, d, true), 0f)
        assertEquals(69f, l.underlineTopPx(96f, 40f, d), 0f) // 25 + 40 + 4
        // Tools circle: (22 - 1.2) / 2 * 2 = 20.8 px radius.
        assertEquals(20.8f, l.toolsCircleRadiusPx(d), 0.001f)
    }

    @Test
    fun layerHeaderZoneShrinksToShortSlots() {
        val l = ChromeGeometry.LayerHeader
        assertEquals(30f, l.zoneDp(30f), 0f)
        assertEquals(0f, l.zoneTopPx(60f, d), 0f)
    }

    // ── Selected header (219) ───────────────────────────────────────

    @Test
    fun selHeaderFixedSumAndNameCap() {
        val s = ChromeGeometry.SelHeader
        assertEquals(20f, s.backLeftPx(d), 0f)
        assertEquals(94f, s.nameBoxLeftPx(d), 0f) // (10 + 27 + 10) * 2
        // W=800: name wrap = 800 - 167*2 = 466 → capped at 180 dp = 360.
        assertEquals(466f, s.nameBoxWidthPx(800f, d), 0f)
        assertEquals(360f, s.columnWidthPx(800f, d), 0f)
        assertEquals(580f, s.layersLeftPx(800f, d), 0f) // 94 + 466 + 20
        assertEquals(654f, s.deleteLeftPx(800f, d), 0f) // 580 + 37*2
        assertEquals(726f, s.moreLeftPx(800f, d), 0f) // 654 + 36*2
        // Narrow screen W=500: wrap = 500-334 = 166 < cap.
        assertEquals(166f, s.columnWidthPx(500f, d), 0f)
    }

    @Test
    fun selHeaderColumnAndButtons() {
        val s = ChromeGeometry.SelHeader
        // Slot 116 px (58 dp), text height 40: column = 46, top = 35.
        assertEquals(46f, s.columnHeightPx(40f, d), 0f)
        assertEquals(35f, s.columnTopPx(116f, 40f, d), 0f)
        assertEquals(79f, s.underlineTopPx(116f, 40f, d), 0f) // 35 + 40 + 4
        // Buttons: back/layers/more 27 dp (54 px) → top 31; delete 26 dp → 32.
        assertEquals(31f, s.btnTopPx(116f, 19f, d), 0f)
        assertEquals(32f, s.btnTopPx(116f, 18f, d), 0f)
        assertEquals(39f, s.glyphTopPx(116f, 19f, d), 0f) // (116 - 38) / 2
        assertEquals(102f, s.glyphLeftPx(94f, d), 0f) // + 4 dp
    }

    // ── Multi-select bar ────────────────────────────────────────────

    @Test
    fun multiPanelAndRows() {
        val m = ChromeGeometry.Multi
        assertEquals(44f, m.totalHeightDp(false), 0f)
        assertEquals(80f, m.totalHeightDp(true), 0f)
        assertEquals(12f, m.panelLeftPx(d), 0f)
        assertEquals(8f, m.panelTopPx(d), 0f)
        assertEquals(776f, m.panelWidthPx(800f, d), 0f)
        assertEquals(72f, m.panelHeightPx(false, d), 0f) // 88 - 16
        assertEquals(774f, m.panelFaceWidthPx(800f, d), 0f)
        assertEquals(31f, m.panelFaceRadiusPx(d), 0f) // (16 - 0.5) * 2
        assertEquals(32f, m.contentLeftPx(d), 0f)
        assertEquals(736f, m.contentWidthPx(800f, d), 0f)
        assertEquals(44f, m.row1CenterYPx(d), 0f)
        assertEquals(112f, m.boolRowCenterYPx(d), 0f)
    }

    @Test
    fun multiTilesAndLabels() {
        val m = ChromeGeometry.Multi
        // Label height 19 px (7.5 sp at d=2, rounded): tile = 40 + 19 = 59.
        assertEquals(59f, m.tileHeightPx(19f, d), 0f)
        assertEquals(14.5f, m.tileTopPx(44f, 19f, d), 0f)
        // Delete tile hugs the right edge: 32 + 736 - 64 = 704; All at 704 - 76.
        assertEquals(704f, m.deleteTileLeftPx(800f, d), 0f)
        assertEquals(628f, m.allTileLeftPx(800f, d), 0f)
        // Mini glyph (20 dp) centred in the 32 dp tile: +6 dp = 12 px.
        assertEquals(716f, m.glyphLeftPx(704f, 32f, 20f, d), 0f)
        assertEquals(18.5f, m.glyphTopPx(14.5f, d), 0f)
        assertEquals(50.5f, m.labelTopPx(14.5f, d), 0f)
        // Label advance 30 px in a 64 px tile: box left = tile + 17.
        assertEquals(721f, m.labelBoxLeftPx(704f, 32f, 30f, d), 0f)
    }

    @Test
    fun multiBooleanRowIsSpaceEvenly() {
        val m = ChromeGeometry.Multi
        // Content 736 px, tiles 4 * 96 = 384 → gap = (736-384)/5 = 70.4.
        assertEquals(70.4f, m.boolGapPx(800f, d), 0.001f)
        assertEquals(102.4f, m.boolTileLeftPx(800f, d, 0), 0.001f)
        assertEquals(268.8f, m.boolTileLeftPx(800f, d, 1), 0.001f)
        // Boolean glyph (22 dp) centred in the 48 dp tile: +13 dp = 26 px.
        assertEquals(128.4f, m.glyphLeftPx(102.4f, 48f, 22f, d), 0.001f)
    }
}
