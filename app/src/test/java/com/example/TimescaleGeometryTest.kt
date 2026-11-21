package com.example

import com.example.model.TimescaleGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 248 (UI migration stage 1) — pins the ruler strip's drawing law
 * ([TimescaleGeometry]) against fixtures hand-derived from the build-247
 * Compose Canvas block it replaces: tick step selection and positions, the
 * export band's clipping, beat-mark and keyframe-diamond placement, the
 * centred playhead needle and the bottom hairline, in draw order.
 *
 * Fixture geometry uses density 2 and pxPerMs 0.125 (0.25 px/ms) so every
 * expected coordinate is exact in float arithmetic.
 */
class TimescaleGeometryTest {

    private val colors = TimescaleGeometry.Colors(
        tickArgb = 0x11111111,
        subTickArgb = 0x22222222,
        bandArgb = 0x33333333,
        bandEdgeArgb = 0x44444444,
        beatArgb = 0x55555555,
        diamondArgb = 0x66666666,
        needleArgb = 0x77777777,
        hairlineArgb = 0x10101010
    )

    private fun ops(
        currentMs: Long = 10_000L,
        totalDurationMs: Long = 60_000L,
        pxPerMs: Float = 0.125f,
        backInsetDp: Float = 0f,
        beatMarksMs: List<Long> = emptyList(),
        exportInMs: Long? = null,
        exportOutMs: Long? = null,
        keyframeTimesMs: List<Long> = emptyList()
    ): List<TimescaleGeometry.Op> = TimescaleGeometry.ops(
        widthPx = 800f, heightPx = 68f, density = 2f,
        currentMs = currentMs, pxPerMs = pxPerMs,
        totalDurationMs = totalDurationMs, backInsetDp = backInsetDp,
        beatMarksMs = beatMarksMs, exportInMs = exportInMs, exportOutMs = exportOutMs,
        keyframeTimesMs = keyframeTimesMs, colors = colors
    )

    private fun tickLines(ops: List<TimescaleGeometry.Op>): List<TimescaleGeometry.Op.Line> =
        ops.filterIsInstance<TimescaleGeometry.Op.Line>()
            .filter { it.y0 == 6f && it.x0 == it.x1 }

    @Test
    fun ticks_step200_windowClipped_majorsEveryFifth() {
        val result = ops()
        val ticks = tickLines(result)
        // Ticks at t = 8600..11400 step 200 inside the [12, 788] px window.
        assertEquals(15, ticks.size)
        assertEquals(3, ticks.count { it.argb == colors.tickArgb })
        assertEquals(12, ticks.count { it.argb == colors.subTickArgb })
        // First drawn tick: t=8600 -> x=50, a minor (1 dp wide, 6 dp long).
        assertEquals(
            TimescaleGeometry.Op.Line(50f, 6f, 50f, 18f, 2f, colors.subTickArgb, false),
            ticks.first()
        )
        // The playhead tick itself (t=10000) is a major: 1.2 dp wide, 13 dp long.
        assertEquals(
            TimescaleGeometry.Op.Line(400f, 6f, 400f, 32f, 2.4f, colors.tickArgb, false),
            ticks.first { it.x0 == 400f }
        )
        // Last drawn tick: t=11400 -> x=750.
        assertEquals(750f, ticks.last().x0, 0f)
        // Ticks + needle + needle line + hairline, nothing else.
        assertEquals(18, result.size)
    }

    @Test
    fun needleAndHairline_geometry() {
        val result = ops()
        val needle = result.filterIsInstance<TimescaleGeometry.Op.RoundRect>().single()
        // 2x11 dp round rect, top at 2 dp, centred on the playhead (x=400).
        assertEquals(
            TimescaleGeometry.Op.RoundRect(400f, 15f, 4f, 22f, 2f, colors.needleArgb),
            needle
        )
        val line = result.filterIsInstance<TimescaleGeometry.Op.Line>().single { it.argb == colors.needleArgb }
        // The 2 dp white line resumes at 29 dp and runs to the strip's bottom.
        assertEquals(
            TimescaleGeometry.Op.Line(400f, 58f, 400f, 68f, 4f, colors.needleArgb, false),
            line
        )
        val hairline = result.last()
        assertEquals(
            TimescaleGeometry.Op.Rect(0f, 66f, 800f, 2f, colors.hairlineArgb),
            hairline
        )
    }

    @Test
    fun band_beats_diamonds_drawOrderAndGeometry() {
        val result = ops(
            exportInMs = 9_000L, exportOutMs = 11_000L,
            beatMarksMs = listOf(9_500L, 10_500L),
            keyframeTimesMs = listOf(10_000L)
        )
        assertEquals(24, result.size)
        // After the 15 ticks: the band wash, then its two edges.
        assertEquals(
            TimescaleGeometry.Op.Rect(150f, 0f, 500f, 68f, colors.bandArgb),
            result[15]
        )
        assertEquals(
            TimescaleGeometry.Op.Line(150f, 0f, 150f, 68f, 3.2f, colors.bandEdgeArgb, false),
            result[16]
        )
        assertEquals(
            TimescaleGeometry.Op.Line(650f, 0f, 650f, 68f, 3.2f, colors.bandEdgeArgb, false),
            result[17]
        )
        // Beat marks: 2.5x15 dp round rects centred on their times (x=275, 525).
        assertEquals(
            TimescaleGeometry.Op.RoundRect(275f, 21f, 5f, 30f, 2.5f, colors.beatArgb),
            result[18]
        )
        assertEquals(
            TimescaleGeometry.Op.RoundRect(525f, 21f, 5f, 30f, 2.5f, colors.beatArgb),
            result[19]
        )
        // The keyframe diamond at the playhead: half 2.5 dp about (400, 20).
        val diamond = result[20] as TimescaleGeometry.Op.FillPoly
        assertTrue(diamond.xs.contentEquals(floatArrayOf(400f, 405f, 400f, 395f)))
        assertTrue(diamond.ys.contentEquals(floatArrayOf(15f, 20f, 25f, 20f)))
        assertEquals(colors.diamondArgb, diamond.argb)
    }

    @Test
    fun band_clippedToWindow_edgesOutsideAreDropped() {
        val result = ops(exportInMs = 0L, exportOutMs = 20_000L)
        val band = result.filterIsInstance<TimescaleGeometry.Op.Rect>()
            .single { it.argb == colors.bandArgb }
        // Both range points project outside the [12, 788] window: the wash is
        // clipped to it and neither edge line is drawn.
        assertEquals(TimescaleGeometry.Op.Rect(12f, 0f, 776f, 68f, colors.bandArgb), band)
        assertTrue(result.filterIsInstance<TimescaleGeometry.Op.Line>()
            .none { it.argb == colors.bandEdgeArgb })
        assertEquals(19, result.size)
    }

    @Test
    fun backInset_shiftsTheTickWindow() {
        val result = ops(backInsetDp = 36f)
        val ticks = tickLines(result)
        // Window starts at (36+6) dp = 84 px: first drawn tick is t=8800 (x=100).
        assertEquals(14, ticks.size)
        assertEquals(100f, ticks.first().x0, 0f)
    }

    @Test
    fun zoomedOut_stepGrowsTo10000() {
        val result = ops(currentMs = 0L, totalDurationMs = 3_600_000L, pxPerMs = 0.001f)
        val ticks = tickLines(result)
        // 0.002 px/ms: the first step with >= 7 dp pitch is 10 s; ticks at
        // t = 0..190000 land inside the window (20), majors every 50 s (4).
        assertEquals(20, ticks.size)
        assertEquals(4, ticks.count { it.argb == colors.tickArgb })
        assertEquals(400f, ticks.first().x0, 0f)
    }

    @Test
    fun playheadPastProjectEnd_noTicks() {
        val result = ops(currentMs = 70_000L)
        // Every tick time is past the project end: only needle + line + hairline.
        assertEquals(3, result.size)
        assertTrue(result.none { it is TimescaleGeometry.Op.Line && it.argb == colors.tickArgb })
    }

    @Test
    fun emptyProject_ticksRunToTheWindowEdge() {
        // totalDurationMs = 0 means "no project bound": ticks fill the window
        // exactly as in the plain fixture.
        val result = ops(totalDurationMs = 0L)
        assertEquals(15, tickLines(result).size)
        assertEquals(18, result.size)
    }
}
