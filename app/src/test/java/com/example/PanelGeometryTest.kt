package com.example

import com.example.model.PanelGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 251 (UI migration stage 4) — pins the tool panels family's
 * layout law ([PanelGeometry]) against fixtures hand-derived from the
 * build-250 Compose layouts: the clip/audio rows' back zone, divider
 * and tile stack; the tools grid's scrolling content and tile stack;
 * the build-219 pager's auto-fit law, button stacks, badge and dots;
 * the group bar's pill; the clip sheets' rows. Fixtures use density 2
 * so every expected value is exact in float arithmetic. Text measures
 * are synthetic (10 px per char unless noted) — the law takes the
 * caller's measure, so the arithmetic is what is pinned.
 */
class PanelGeometryTest {

    private val d = 2f
    private val tenPx = { s: String -> s.length * 10f }

    // ── Shared text law ─────────────────────────────────────────────

    @Test
    fun lineBaselineCentresTheFontBox() {
        // lineBox == font box: baseline = top − ascent.
        assertEquals(125f, PanelGeometry.lineBaselinePx(100f, 30f, -25f, 5f), 0f)
        // lineBox 40 vs font box 30: 5 px of slack above the font box.
        assertEquals(130f, PanelGeometry.lineBaselinePx(100f, 40f, -25f, 5f), 0f)
    }

    @Test
    fun wrapKeepsFittingTextOnOneLine() {
        assertEquals(listOf("Effects"), PanelGeometry.wrapGreedy("Effects", 100f, 2, tenPx))
        assertEquals(listOf(""), PanelGeometry.wrapGreedy("", 100f, 2, tenPx))
    }

    @Test
    fun wrapBreaksAtWordBoundary() {
        // "Paste keyframes" = 150 px at 10 px/char; the word break wins.
        assertEquals(
            listOf("Paste", "keyframes"),
            PanelGeometry.wrapGreedy("Paste keyframes", 100f, 2, tenPx)
        )
    }

    @Test
    fun wrapBreaksALongWordMidWord() {
        // "keyframes" alone (90 px) in a 50 px box: 5 chars fit.
        assertEquals(
            listOf("keyfr", "ames"),
            PanelGeometry.wrapGreedy("keyframes", 50f, 2, tenPx)
        )
    }

    @Test
    fun wrapDropsContentPastMaxLines() {
        // 40 px lines: "one" / "two" fit; "three four" never render.
        assertEquals(
            listOf("one", "two"),
            PanelGeometry.wrapGreedy("one two three four", 40f, 2, tenPx)
        )
    }

    @Test
    fun wrapUnboundedMaxLinesReturnsEveryLine() {
        // Build 254 regression: maxLines = Int.MAX_VALUE (the
        // unbounded callers' value) must not pre-allocate by the cap —
        // the old ArrayList<String>(maxLines) tried to allocate
        // ~8.6 GB and OOM-crashed. 40 px lines at 10 px/char; every
        // word survives, nothing is dropped.
        assertEquals(
            listOf("one", "two", "thre", "e", "four", "five", "six"),
            PanelGeometry.wrapGreedy("one two three four five six", 40f, Int.MAX_VALUE, tenPx)
        )
    }

    @Test
    fun wrapUnboundedBreaksOneLongWordToTheEnd() {
        // One 18-char word, 50 px box: mid-word breaks until it ends.
        assertEquals(
            listOf("keyfr", "amesk", "eyfra", "mes"),
            PanelGeometry.wrapGreedy("keyframeskeyframes", 50f, Int.MAX_VALUE, tenPx)
        )
    }

    @Test
    fun wrapCrashShapeNarrowBoxLongParagraph() {
        // The phone crash's exact shape (build 252/253): a long
        // hint paragraph that does not fit its narrow box on one
        // line, wrapped unbounded by GlTextBlock.
        assertEquals(
            listOf("This panel", "has no", "editable", "values for", "the", "selected", "layer yet"),
            PanelGeometry.wrapGreedy(
                "This panel has no editable values for the selected layer yet",
                100f, Int.MAX_VALUE, tenPx
            )
        )
    }

    @Test
    fun wrapSecondLineBreaksAgain() {
        // The pager's floor case: line 2 re-breaks and the rest clips.
        val at75 = { s: String -> s.length * 15f }
        assertEquals(
            listOf("Save", "as"),
            PanelGeometry.wrapGreedy("Save as Template", 100f, 2, at75)
        )
    }

    // ── Tool rows ───────────────────────────────────────────────────

    @Test
    fun toolRowZonesMirrorThePanels() {
        val g = PanelGeometry.ToolRow
        assertEquals(92f, g.dividerLeftPx(d), 0f) // (6 + 36 + 4) * 2
        assertEquals(98f, g.tilesLeftPx(d), 0f) // + (1 + 2) * 2
        assertEquals(29f, g.backIconLeftPx(d), 0f) // 12 + (36 − 19)
        assertEquals(26f, g.dividerTopPx(0f, 100f, d), 0f) // (100 − 48) / 2
    }

    @Test
    fun toolTileWidthIsTheWiderOfIconAndLabel() {
        val g = PanelGeometry.ToolRow
        assertEquals(112f, g.tileWidthPx(100f, d), 0f) // label wins: 100 + 12
        assertEquals(48f, g.tileWidthPx(20f, d), 0f) // icon wins: 36 + 12
    }

    @Test
    fun toolTileStackAndPill() {
        val g = PanelGeometry.ToolRow
        // stack = (18 + 2) * 2 + 30 = 70, centred in 100 → top 15.
        assertEquals(15f, g.stackTopPx(0f, 100f, 30f, d), 0f)
        assertEquals(82f, g.pillHeightPx(30f, d), 0f) // 70 + 2 * 3 * 2
    }

    @Test
    fun toolTileLayoutCoercesTheCrossingTile() {
        val g = PanelGeometry.ToolRow
        val boxes = g.layoutTilesPx(listOf(100f, 100f, 100f), 0f, 8f, 250f)
        assertEquals(PanelGeometry.ToolRow.TileBox(0f, 100f), boxes[0])
        assertEquals(PanelGeometry.ToolRow.TileBox(108f, 100f), boxes[1])
        assertEquals(PanelGeometry.ToolRow.TileBox(216f, 34f), boxes[2])
    }

    // ── Tools grid ──────────────────────────────────────────────────

    @Test
    fun gridContentAndTileLefts() {
        val g = PanelGeometry.Grid
        assertEquals(392f, g.contentWidthPx(3, d), 0f) // (16 + 168 + 12) * 2
        assertEquals(264f, g.tileLeftPx(2, d), 0f) // (8 + 2 * 62) * 2
        assertEquals(302f, g.glyphLeftPx(264f, d), 0f) // + (56 − 18)
        assertEquals(100f, g.labelWidthPx(d), 0f)
    }

    @Test
    fun gridStackCentresInTheTile() {
        val g = PanelGeometry.Grid
        // region = 96 − 12 = 84; stack = 36 + 2 + 22 = 60; top = 6 + 12.
        assertEquals(18f, g.stackTopPx(0f, 1, 22f, d), 0f)
        // Two lines: stack = 36 + 2 + 44 = 82; top = 6 + 1.
        assertEquals(7f, g.stackTopPx(0f, 2, 22f, d), 0f)
    }

    // ── Pager ───────────────────────────────────────────────────────

    @Test
    fun pagerButtonAndInnerWidths() {
        val g = PanelGeometry.Pager
        assertEquals(159f, g.buttonWidthPx(720f, 4, d), 0f) // (720 − 48 − 36) / 4
        assertEquals(145f, g.innerWidthPx(159f, active = true, d), 0f) // − 8 − 6
        assertEquals(151f, g.innerWidthPx(159f, active = false, d), 0f)
    }

    @Test
    fun pagerFitStepsDownByHalfSp() {
        val g = PanelGeometry.Pager
        val measure = { s: String, sp: Float -> s.length * sp * 2f }
        // "Transform" (9): 180 @10 … 144 @8 ≤ 145 → stops at 8, one line.
        assertEquals(
            PanelGeometry.Pager.Fit(8f, true),
            g.fit("Transform", 10f, 145f, measure)
        )
        // "Save as Template" (16) never fits 100 px → floor, two lines.
        assertEquals(
            PanelGeometry.Pager.Fit(7.5f, false),
            g.fit("Save as Template", 10f, 100f, measure)
        )
    }

    @Test
    fun pagerRowsAndPage3Heights() {
        val g = PanelGeometry.Pager
        assertEquals(96f, g.rowTopPx(1, listOf(72f, 72f), d), 0f) // 12 + 72 + 12
        assertEquals(138f, g.page3RowHeightPx(308f, 2, d), 0f) // (308 − 20 − 12) / 2
    }

    @Test
    fun pagerStackBadgeAndDots() {
        val g = PanelGeometry.Pager
        // vPad 6; region 88; stack = 40 + 6 + 23 = 69; top = 6 + 9.5.
        assertEquals(15.5f, g.stackTopPx(0f, 100f, false, 20f, 3f, 1, 23f, d), 0f)
        assertEquals(52f, g.badgeWidthPx(40f, d), 0f) // 40 + 12
        assertEquals(24f, g.badgeHeightPx(20f, d), 0f) // 20 + 4
        assertEquals(152f, g.badgeLeftPx(200f, 52f, d), 0f) // 200 + 4 − 52
        assertEquals(92f, g.badgeTopPx(100f, d), 0f) // 100 − 8
        assertEquals(72f, g.dotsWidthPx(d), 0f)
        assertEquals(324f, g.dotsLeftPx(720f, d), 0f)
        assertEquals(364f, g.dotLeftPx(1, 0, 720f, d), 0f) // 324 + (14 + 6) * 2
        assertEquals(28f, g.dotWidthPx(0, 0, d), 0f)
        assertEquals(10f, g.dotWidthPx(1, 0, d), 0f)
    }

    // ── Group bar ───────────────────────────────────────────────────

    @Test
    fun groupBarPillGeometry() {
        val g = PanelGeometry.GroupBar
        assertEquals(228f, g.surfaceWidthPx(d), 0f) // 114 * 2
        assertEquals(46f, g.surfaceHeightPx(30f, d), 0f) // max(28, 30) + 16
        assertEquals(9f, g.iconTopPx(30f, d), 0f) // 8 + (30 − 28) / 2
        assertEquals(8f, g.textBoxTopPx(30f, d), 0f) // text is the tall side
        assertEquals(44f, g.textLeftPx(d), 0f)
    }

    // ── Sheets ──────────────────────────────────────────────────────

    @Test
    fun blendSheetRows() {
        val g = PanelGeometry.Sheets
        assertEquals(62f, g.blendRowHeightPx(30f, d), 0f) // 30 + 32
        assertEquals(144f, g.blendRowTopPx(1, 40f, 62f, d), 0f) // 12 + 40 + 20 + 72
        assertEquals(620f, g.blendContentHeightPx(40f, 62f, 7, d), 0f)
        assertEquals(638f, g.blendCheckLeftPx(720f, d), 0f) // 720 − 28 − 22 − 32
        assertEquals(50f, g.rowTextLeftPx(11f, d), 0f)
    }

    @Test
    fun replaceSheetRows() {
        val g = PanelGeometry.Sheets
        // Image (84) beats the text stack (56): row = 84 + 28.
        assertEquals(112f, g.replaceRowHeightPx(30f, 26f, d), 0f)
        assertEquals(488f, g.replaceContentHeightPx(40f, 112f, 3, d), 0f)
        assertEquals(48f, g.replaceImageLeftPx(d), 0f)
        assertEquals(152f, g.replaceTextLeftPx(d), 0f) // 48 + 52 * 2
    }
}
