package com.example

import com.example.model.LoadingCmdSink
import com.example.model.LoadingMascot
import com.example.model.LoadingPillAssignments
import com.example.model.LoadingPillLaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Build 272 — pins the cute loading pill's law ([LoadingPillLaw]):
 * the dot stagger, the ring rotation, the pill layout, every
 * mascot's emission (non-empty, finite, at several phases), and
 * the per-place mascot assignment's distinctness.
 */
class LoadingPillLawTest {

    /** A sink that records every emitted command's coordinates for validation. */
    private class RecordingSink : LoadingCmdSink {
        var commands = 0
        val coords = ArrayList<Float>()
        val colors = ArrayList<Int>()

        private fun rec(vararg v: Float) {
            commands++
            v.forEach { coords.add(it) }
        }

        override fun circle(cx: Float, cy: Float, r: Float, fillArgb: Int) {
            rec(cx, cy, r); colors.add(fillArgb)
        }

        override fun roundRect(
            cx: Float, cy: Float, w: Float, h: Float, corner: Float,
            fillArgb: Int, strokeWidth: Float, strokeArgb: Int
        ) {
            rec(cx, cy, w, h, corner, strokeWidth); colors.add(fillArgb); colors.add(strokeArgb)
        }

        override fun rect(x: Float, y: Float, w: Float, h: Float, fillArgb: Int) {
            rec(x, y, w, h); colors.add(fillArgb)
        }

        override fun arc(
            cx: Float, cy: Float, rx: Float, ry: Float,
            startDeg: Float, sweepDeg: Float, width: Float,
            fillArgb: Int, strokeArgb: Int, capRound: Boolean, closedPie: Boolean
        ) {
            rec(cx, cy, rx, ry, startDeg, sweepDeg, width); colors.add(fillArgb); colors.add(strokeArgb)
        }

        override fun poly(xs: FloatArray, ys: FloatArray, fillArgb: Int) {
            commands++
            xs.forEach { coords.add(it) }
            ys.forEach { coords.add(it) }
            colors.add(fillArgb)
        }

        override fun polyline(xs: FloatArray, ys: FloatArray, width: Float, argb: Int) {
            commands++
            xs.forEach { coords.add(it) }
            ys.forEach { coords.add(it) }
            coords.add(width)
            colors.add(argb)
        }
    }

    private val eps = 0.001f

    @Test
    fun dotStaggerFollowsTheReferenceStep() {
        // step = (t * 2.1) % 3; a dot is opaque once the step reaches it.
        assertEquals(1f, LoadingPillLaw.dotAlpha(0f, 0), eps)
        assertEquals(0.25f, LoadingPillLaw.dotAlpha(0f, 1), eps)
        assertEquals(0.25f, LoadingPillLaw.dotAlpha(0f, 2), eps)
        // t = 0.5 → step 1.05: dots 0 and 1 opaque.
        assertEquals(1f, LoadingPillLaw.dotAlpha(0.5f, 1), eps)
        assertEquals(0.25f, LoadingPillLaw.dotAlpha(0.5f, 2), eps)
        // t = 1.0 → step 2.1: all three opaque.
        assertEquals(1f, LoadingPillLaw.dotAlpha(1.0f, 2), eps)
        // t = 1.43 → step ≈ 0.003 (wrapped): back to dot 0 only.
        assertEquals(1f, LoadingPillLaw.dotAlpha(1.43f, 0), eps)
        assertEquals(0.25f, LoadingPillLaw.dotAlpha(1.43f, 1), eps)
        assertEquals(0.25f, LoadingPillLaw.dotAlpha(1.43f, 2), eps)
    }

    @Test
    fun ringRotationStaysInTheWobbleBandAndAdvances() {
        var prev = LoadingPillLaw.ringRotationRad(0f)
        assertEquals(0f, prev, eps)
        var t = 0.1f
        while (t <= 20f) {
            val rot = LoadingPillLaw.ringRotationRad(t)
            // rot = t*4.2 ± 0.45 (the sin wobble).
            assertTrue(rot >= t * 4.2f - 0.451f && rot <= t * 4.2f + 0.451f)
            // Net advance per 0.1s step stays positive (4.2/s dominates the wobble).
            assertTrue(rot > prev)
            prev = rot
            t += 0.1f
        }
        assertEquals(122.4f, LoadingPillLaw.RING_SWEEP_DEG, eps)
        // The sweep is 0.68π of a full turn.
        assertEquals(0.68f, LoadingPillLaw.RING_SWEEP_DEG / 360f * 2f, 0.01f)
    }

    @Test
    fun everyMascotEmitsFiniteCommandsAtManyPhases() {
        val phases = floatArrayOf(0f, 0.37f, 1.3f, 2.9f, 7.77f, 123.4f)
        // The pixel pumpkin's diagonal reveal leaves it empty at the very
        // start of each 3.333s cycle (as in the JS), so it gets mid-cycle
        // phases; its t=0 emptiness is pinned separately below.
        val pumpkinPhases = floatArrayOf(0.8f, 1.6f, 2.5f, 2.9f, 123.9f)
        for (mascot in LoadingMascot.values()) {
            val usePhases = if (mascot == LoadingMascot.PUMPKIN) pumpkinPhases else phases
            for (t in usePhases) {
                val sink = RecordingSink()
                LoadingPillLaw.emitMascot(sink, mascot, 100f, 60f, 62f, t, LoadingPillLaw.PILL_ARGB)
                assertTrue("$mascot at t=$t emitted nothing", sink.commands > 0)
                assertTrue("$mascot at t=$t emitted no colours", sink.colors.isNotEmpty())
                for (v in sink.coords) {
                    assertTrue("$mascot at t=$t emitted a non-finite coordinate", v.isFinite())
                }
            }
        }
        val atZero = RecordingSink()
        LoadingPillLaw.emitMascot(atZero, LoadingMascot.PUMPKIN, 100f, 60f, 62f, 0f, LoadingPillLaw.PILL_ARGB)
        assertEquals(0, atZero.commands)
    }

    @Test
    fun ringMascotsEmitTheRingAndPlainMascotsDoNot() {
        // Ring mascots (1–10) emit at least the faint circle + arc;
        // the chomper's dotted ring emits several arc segments.
        val chomper = RecordingSink()
        LoadingPillLaw.emitMascot(chomper, LoadingMascot.CHOMPER, 0f, 0f, 62f, 0.8f, LoadingPillLaw.PILL_ARGB)
        assertTrue(chomper.commands >= 8)
        for (m in LoadingMascot.values()) {
            assertEquals(m.ordinal < 10, m.hasRing)
        }
    }

    @Test
    fun pillLayoutTilesIconLabelAndDots() {
        val h = 100f
        val labelAdv = 120f
        val dotAdv = 10f
        val iconCx = LoadingPillLaw.iconCentreXPx(h)
        val textX = LoadingPillLaw.textXPx(h)
        val iconSize = LoadingPillLaw.iconSizePx(h)
        assertEquals(62f, iconSize, eps)
        // Icon sits fully inside the pill's left half; the label starts after it.
        assertTrue(iconCx - iconSize / 2f >= 0f)
        assertTrue(textX > iconCx + iconSize / 2f)
        // Dots march right and the last dot ends inside the pill width.
        val d0 = LoadingPillLaw.dotCentreXPx(h, labelAdv, dotAdv, 0)
        val d1 = LoadingPillLaw.dotCentreXPx(h, labelAdv, dotAdv, 1)
        val d2 = LoadingPillLaw.dotCentreXPx(h, labelAdv, dotAdv, 2)
        assertTrue(d0 > textX + labelAdv)
        assertTrue(d1 > d0 && d2 > d1)
        val w = LoadingPillLaw.pillWidthPx(h, labelAdv, dotAdv)
        assertTrue(d2 + dotAdv / 2f < w)
        // A longer label widens the pill by exactly its advance.
        val w2 = LoadingPillLaw.pillWidthPx(h, labelAdv + 40f, dotAdv)
        assertEquals(40f, w2 - w, eps)
        // Rotation band constant: 2π of phases covered by the wobble term.
        assertTrue(abs(LoadingPillLaw.ringRotationRad(PI.toFloat().toFloat()) - (PI * 4.2f).toFloat()) <= 0.451f)
    }

    @Test
    fun assignmentsGiveEveryWiredPlaceADistinctMascot() {
        val all = LoadingPillAssignments.ALL
        assertEquals(9, all.size)
        assertEquals(all.size, all.distinct().size)
        // The brief's example anchors.
        assertEquals(LoadingMascot.ROCKET, LoadingPillAssignments.PROJECT_IMPORT)
        assertEquals(LoadingMascot.BATTERY, LoadingPillAssignments.VIDEO_EXPORT)
        assertEquals(LoadingMascot.UFO, LoadingPillAssignments.MODEL_DOWNLOAD)
        assertEquals(LoadingMascot.CHOMPER, LoadingPillAssignments.CUTOUT)
        assertEquals(LoadingMascot.BEAR, LoadingPillAssignments.BEATS)
    }
}
