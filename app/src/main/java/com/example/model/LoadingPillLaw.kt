package com.example.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Build 272: the cute loading pill — Sarowar's HTML reference
 * (ai_studio_code__60.html: drawCuteIcon + drawSpinnerRing +
 * drawSinglePill) translated as a pure emission law.
 *
 * A dark pill (#171717, full radius) carries an animated mascot on
 * the left and a label + 3 staggered dots on the right. The law
 * emits vector commands into a [LoadingCmdSink]; the UI component
 * adapts the sink to the app's GL VectorCmdBuilder, and the host
 * tests drive it with a recording sink. All motion equations
 * (frequencies, amplitudes, blink / chomp / beat timings) are the
 * reference's, translated 1:1; shapes the vector canvas cannot
 * fill directly (rotated ellipses, beziers, the heart / bat /
 * ghost outlines) are polygon-sampled at emission time.
 */
enum class LoadingMascot(val ringArgb: Int) {
    BAT(0xFFFFFFFF.toInt()),
    AIBOT(0xFF3EE88F.toInt()),
    KITTY(0xFFF472B6.toInt()),
    DUCK(0xFF38BDF8.toInt()),
    BUNNY(0xFFC084FC.toInt()),
    ROCKET(0xFFFB923C.toInt()),
    HEART(0xFFFB7185.toInt()),
    CHOMPER(0xFFFACC15.toInt()),
    BUTTERFLY(0xFFA3E635.toInt()),
    UFO(0xFF22D3EE.toInt()),
    GHOST(0),
    PUMPKIN(0),
    FROG(0),
    PANDA(0),
    AXOLOTL(0),
    COFFEE(0),
    STAR(0),
    CLOUD(0),
    BEAR(0),
    BATTERY(0);

    /** True for mascots 1–10, which spin inside the reference's ring. */
    val hasRing: Boolean get() = ringArgb != 0
}

/** The vector-command sink the law emits into (GL adapter in the UI, recorder in tests). */
interface LoadingCmdSink {
    fun circle(cx: Float, cy: Float, r: Float, fillArgb: Int)
    fun roundRect(
        cx: Float, cy: Float, w: Float, h: Float, corner: Float,
        fillArgb: Int, strokeWidth: Float = 0f, strokeArgb: Int = 0
    )
    fun rect(x: Float, y: Float, w: Float, h: Float, fillArgb: Int)
    fun arc(
        cx: Float, cy: Float, rx: Float, ry: Float,
        startDeg: Float, sweepDeg: Float, width: Float,
        fillArgb: Int, strokeArgb: Int, capRound: Boolean, closedPie: Boolean
    )
    fun poly(xs: FloatArray, ys: FloatArray, fillArgb: Int)
    fun polyline(xs: FloatArray, ys: FloatArray, width: Float, argb: Int)
}

object LoadingPillLaw {

    /** The reference pill body colour (#171717). */
    val PILL_ARGB: Int = 0xFF171717.toInt()

    /** The label used when a surface has no operation label of its own. */
    const val DEFAULT_LABEL = "Loading"

    private const val PI_F = PI.toFloat()
    private const val DEG = 180f / PI_F

    // ── Pill layout (drawSinglePill proportions) ─────────────────

    /** The mascot's icon size for a pill of [heightPx] (the reference's 0.62 ratio). */
    fun iconSizePx(heightPx: Float): Float = heightPx * 0.62f

    private fun padPx(heightPx: Float): Float = heightPx * 0.36f
    private fun gapPx(heightPx: Float): Float = heightPx * 0.14f

    /** The icon centre's x inside a pill of [heightPx]. */
    fun iconCentreXPx(heightPx: Float): Float = padPx(heightPx) + iconSizePx(heightPx) / 2f

    /** The label's draw-origin x inside a pill of [heightPx]. */
    fun textXPx(heightPx: Float): Float =
        padPx(heightPx) + iconSizePx(heightPx) + gapPx(heightPx)

    /** The 3 dots' advance step (the reference's dotW * 0.95). */
    fun dotStepPx(dotAdvancePx: Float): Float = dotAdvancePx * 0.95f

    /** The centre x of dot [dot] (0..2) for the given label/dot advances. */
    fun dotCentreXPx(heightPx: Float, labelAdvancePx: Float, dotAdvancePx: Float, dot: Int): Float =
        textXPx(heightPx) + labelAdvancePx + dot * dotStepPx(dotAdvancePx) + dotAdvancePx / 2f

    /** The pill width for a label of [labelAdvancePx] and a '.' advance of [dotAdvancePx]. */
    fun pillWidthPx(heightPx: Float, labelAdvancePx: Float, dotAdvancePx: Float): Float =
        textXPx(heightPx) + labelAdvancePx + 2f * dotStepPx(dotAdvancePx) + dotAdvancePx + padPx(heightPx)

    // ── Dot stagger + ring phase (the reference's equations) ─────

    /** The reference's dot step: ((t * 2.1) % 3). */
    fun dotStep(tSec: Float): Float = (tSec * 2.1f) % 3f

    /** Dot [dot]'s opacity: 1 once the step reaches it, 0.25 before. */
    fun dotAlpha(tSec: Float, dot: Int): Float = if (dotStep(tSec) >= dot) 1f else 0.25f

    /** The spinner ring's rotation: t * 4.2 + sin(t * 2.1) * 0.45 (radians). */
    fun ringRotationRad(tSec: Float): Float = tSec * 4.2f + sin(tSec * 2.1f) * 0.45f

    /** The ring arc's sweep: 0.68π (122.4°). */
    const val RING_SWEEP_DEG: Float = 122.4f

    fun argbWithAlpha(argb: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (argb and 0x00FFFFFF)

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)
    private fun easeOutCubic(t: Float): Float = 1f - (1f - t).let { it * it * it }

    // ── Emission helpers ─────────────────────────────────────────

    private fun fillEllipse(s: LoadingCmdSink, cx: Float, cy: Float, rx: Float, ry: Float, argb: Int) {
        if (rx <= 0f || ry <= 0f) return
        s.arc(cx, cy, rx, ry, 0f, 360f, 0f, argb, 0, capRound = false, closedPie = true)
    }

    private fun strokeArc(
        s: LoadingCmdSink, cx: Float, cy: Float, rx: Float, ry: Float,
        startDeg: Float, sweepDeg: Float, width: Float, argb: Int
    ) {
        s.arc(cx, cy, rx, ry, startDeg, sweepDeg, width, 0, argb, capRound = true, closedPie = false)
    }

    /** A rotated ellipse as a polygon (the vector canvas has no rotated-ellipse fill). */
    private fun ellipsePoly(
        s: LoadingCmdSink, cx: Float, cy: Float, rx: Float, ry: Float,
        rotRad: Float, argb: Int, n: Int = 24
    ) {
        if (rx <= 0f || ry <= 0f) return
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val c = cos(rotRad)
        val sn = sin(rotRad)
        for (i in 0 until n) {
            val a = i * 2f * PI_F / n
            val ex = rx * cos(a)
            val ey = ry * sin(a)
            xs[i] = cx + ex * c - ey * sn
            ys[i] = cy + ex * sn + ey * c
        }
        s.poly(xs, ys, argb)
    }

    /** Rotates a local point by [rotRad] and translates it to ([tx], [ty]). */
    private fun rotPt(lx: Float, ly: Float, cosR: Float, sinR: Float, tx: Float, ty: Float): FloatArray =
        floatArrayOf(tx + lx * cosR - ly * sinR, ty + lx * sinR + ly * cosR)

    private fun polyOf(s: LoadingCmdSink, pts: List<FloatArray>, argb: Int) {
        if (pts.size < 3) return
        s.poly(
            FloatArray(pts.size) { pts[it][0] },
            FloatArray(pts.size) { pts[it][1] },
            argb
        )
    }

    /** A tiny path sampler for the reference's bezier outlines (bat / heart / ghost). */
    private class PathBuilder {
        private val xs = ArrayList<Float>()
        private val ys = ArrayList<Float>()
        fun moveTo(x: Float, y: Float) { xs.add(x); ys.add(y) }
        fun lineTo(x: Float, y: Float) { xs.add(x); ys.add(y) }
        fun quadTo(cx: Float, cy: Float, x: Float, y: Float, steps: Int = 10) {
            val x0 = xs.last(); val y0 = ys.last()
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                val g = 1f - f
                xs.add(g * g * x0 + 2f * g * f * cx + f * f * x)
                ys.add(g * g * y0 + 2f * g * f * cy + f * f * y)
            }
        }
        fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float, steps: Int = 14) {
            val x0 = xs.last(); val y0 = ys.last()
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                val g = 1f - f
                xs.add(g * g * g * x0 + 3f * g * g * f * c1x + 3f * g * f * f * c2x + f * f * f * x)
                ys.add(g * g * g * y0 + 3f * g * g * f * c1y + 3f * g * f * f * c2y + f * f * f * y)
            }
        }
        fun emit(s: LoadingCmdSink, argb: Int) {
            if (xs.size >= 3) s.poly(xs.toFloatArray(), ys.toFloatArray(), argb)
        }
    }

    /** drawSpinnerRing: faint full circle + a coloured 0.68π arc with the wobble rotation. */
    private fun ring(s: LoadingCmdSink, cx: Float, cy: Float, r: Float, t: Float, colorArgb: Int, dotted: Boolean) {
        val lw = max(2f, r * 0.14f)
        s.arc(cx, cy, r, r, 0f, 360f, lw, 0, 0x24FFFFFF, capRound = false, closedPie = false)
        val rotDeg = ringRotationRad(t) * DEG
        if (!dotted) {
            s.arc(cx, cy, r, r, rotDeg, RING_SWEEP_DEG, lw, 0, colorArgb, capRound = true, closedPie = false)
        } else {
            // The reference's setLineDash([r*0.25, r*0.25]) as segmented arcs:
            // each dash/gap spans 0.25 rad of arc.
            val stepDeg = 0.25f * DEG
            var a = 0f
            var on = true
            while (a < RING_SWEEP_DEG) {
                val seg = min(stepDeg, RING_SWEEP_DEG - a)
                if (on) s.arc(cx, cy, r, r, rotDeg + a, seg, lw, 0, colorArgb, capRound = true, closedPie = false)
                a += stepDeg
                on = !on
            }
        }
    }

    // ── The 20 mascots (drawCuteIcon, translated) ────────────────

    /**
     * Emits mascot [mascot] centred at ([cx], [cy]) inside a box of
     * [size] px at time [tSec]. [pillArgb] is the pill colour the
     * reference draws the mascots' dark details in.
     */
    fun emitMascot(
        s: LoadingCmdSink, mascot: LoadingMascot,
        cx: Float, cy: Float, size: Float, tSec: Float, pillArgb: Int
    ) {
        val r = size * 0.46f
        when (mascot) {
            LoadingMascot.BAT -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val flap = sin(tSec * 10.5f)
                val bw = r * 1.15f
                val bh = r * 0.50f
                val by = cy + flap * (r * 0.08f)
                val tipY = by - flap * bh * 0.72f
                val p = PathBuilder()
                p.moveTo(cx - bw * 0.12f, by - bh * 0.18f)
                p.lineTo(cx - bw * 0.09f, by - bh * 0.48f)
                p.lineTo(cx - bw * 0.03f, by - bh * 0.25f)
                p.lineTo(cx + bw * 0.03f, by - bh * 0.25f)
                p.lineTo(cx + bw * 0.09f, by - bh * 0.48f)
                p.lineTo(cx + bw * 0.12f, by - bh * 0.18f)
                p.quadTo(cx + bw * 0.38f, by - bh * 0.45f - flap * bh * 0.4f, cx + bw * 0.68f, tipY)
                p.quadTo(cx + bw * 0.46f, by + bh * 0.28f - flap * bh * 0.15f, cx + bw * 0.28f, by + bh * 0.15f)
                p.quadTo(cx + bw * 0.16f, by + bh * 0.36f, cx, by + bh * 0.30f)
                p.quadTo(cx - bw * 0.16f, by + bh * 0.36f, cx - bw * 0.28f, by + bh * 0.15f)
                p.quadTo(cx - bw * 0.46f, by + bh * 0.28f - flap * bh * 0.15f, cx - bw * 0.68f, tipY)
                p.quadTo(cx - bw * 0.38f, by - bh * 0.45f - flap * bh * 0.4f, cx - bw * 0.12f, by - bh * 0.18f)
                p.emit(s, 0xFFFFFFFF.toInt())
            }

            LoadingMascot.AIBOT -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val bob = sin(tSec * 5.0f) * (r * 0.08f)
                val hw = r * 1.05f
                val hh = r * 0.80f
                s.roundRect(cx, cy + bob, hw, hh, hh * 0.42f, 0xFF3EE88F.toInt())
                val blink = if ((tSec % 2.6f) > 2.45f) 0.2f else 1f
                val ew = r * 0.15f
                val eh = r * 0.26f * blink
                s.roundRect(cx - hw * 0.22f, cy + bob, ew, eh, ew / 2f, 0xFFFFFFFF.toInt())
                s.roundRect(cx + hw * 0.22f, cy + bob, ew, eh, ew / 2f, 0xFFFFFFFF.toInt())
            }

            LoadingMascot.KITTY -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val bob = sin(tSec * 3.5f) * (r * 0.06f)
                val ky = cy + bob + r * 0.08f
                val cream = 0xFFFFF1F2.toInt()
                polyOf(
                    s, listOf(
                        floatArrayOf(cx - r * 0.45f, ky - r * 0.12f),
                        floatArrayOf(cx - r * 0.48f, ky - r * 0.56f),
                        floatArrayOf(cx - r * 0.12f, ky - r * 0.34f)
                    ), cream
                )
                polyOf(
                    s, listOf(
                        floatArrayOf(cx + r * 0.45f, ky - r * 0.12f),
                        floatArrayOf(cx + r * 0.48f, ky - r * 0.56f),
                        floatArrayOf(cx + r * 0.12f, ky - r * 0.34f)
                    ), cream
                )
                fillEllipse(s, cx, ky, r * 0.52f, r * 0.40f, cream)
                s.circle(cx - r * 0.28f, ky + r * 0.08f, r * 0.07f, 0xFFFDA4AF.toInt())
                s.circle(cx + r * 0.28f, ky + r * 0.08f, r * 0.07f, 0xFFFDA4AF.toInt())
                val lw = max(1.5f, r * 0.07f)
                strokeArc(s, cx - r * 0.18f, ky - r * 0.02f, r * 0.09f, r * 0.09f, 18f, 144f, lw, pillArgb)
                strokeArc(s, cx + r * 0.18f, ky - r * 0.02f, r * 0.09f, r * 0.09f, 18f, 144f, lw, pillArgb)
            }

            LoadingMascot.DUCK -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val bob = sin(tSec * 6.0f) * (r * 0.09f)
                val tilt = sin(tSec * 3.0f) * 0.12f
                val c = cos(tilt)
                val sn = sin(tilt)
                val ty = cy + bob
                // Body: ellipse centred at local (0, r*0.14) in the tilted frame.
                val bodyC = rotPt(0f, r * 0.14f, c, sn, cx, ty)
                ellipsePoly(s, bodyC[0], bodyC[1], r * 0.46f, r * 0.32f, tilt, 0xFFFACC15.toInt())
                duckRest(s, cx, ty, r, c, sn, pillArgb)
            }

            LoadingMascot.BUNNY -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val hop = abs(sin(tSec * 5.2f)) * (r * 0.12f)
                val by = cy + r * 0.16f - hop
                val earWig = sin(tSec * 7.0f) * 0.15f
                val white = 0xFFFFFFFF.toInt()
                for (dir in floatArrayOf(-1f, 1f)) {
                    // Ear base at (cx + dir*r*0.18, by - r*0.30), rotated ∓0.15 ± wiggle.
                    val rot = (if (dir < 0f) -0.15f + earWig else 0.15f - earWig)
                    val bx = cx + dir * r * 0.18f
                    val byy = by - r * 0.30f
                    val cc = cos(rot)
                    val ss = sin(rot)
                    val centre = rotPt(0f, -r * 0.26f, cc, ss, bx, byy)
                    ellipsePoly(s, centre[0], centre[1], r * 0.12f, r * 0.30f, rot, white)
                }
                fillEllipse(s, cx, by, r * 0.44f, r * 0.34f, white)
                s.circle(cx, by + r * 0.05f, r * 0.06f, 0xFFF472B6.toInt())
                s.circle(cx - r * 0.16f, by - r * 0.04f, r * 0.055f, pillArgb)
                if (sin(tSec * 3.0f) > 0.4f) {
                    strokeArc(
                        s, cx + r * 0.16f, by - r * 0.02f, r * 0.065f, r * 0.065f,
                        180f, 180f, max(1.5f, r * 0.06f), pillArgb
                    )
                } else {
                    s.circle(cx + r * 0.16f, by - r * 0.04f, r * 0.055f, pillArgb)
                }
            }

            LoadingMascot.ROCKET -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val vib = sin(tSec * 22f) * (r * 0.03f)
                val tx = cx + vib
                val ty = cy - vib
                val rot = PI_F * 0.22f
                val c = cos(rot)
                val sn = sin(rot)
                val fLen = r * (0.28f + 0.14f * sin(tSec * 25f))
                polyOf(
                    s, listOf(
                        rotPt(-r * 0.14f, r * 0.30f, c, sn, tx, ty),
                        rotPt(0f, r * 0.30f + fLen, c, sn, tx, ty),
                        rotPt(r * 0.14f, r * 0.30f, c, sn, tx, ty)
                    ), 0xFFFACC15.toInt()
                )
                polyOf(
                    s, listOf(
                        rotPt(-r * 0.20f, r * 0.10f, c, sn, tx, ty),
                        rotPt(-r * 0.38f, r * 0.34f, c, sn, tx, ty),
                        rotPt(-r * 0.15f, r * 0.30f, c, sn, tx, ty)
                    ), 0xFFEF4444.toInt()
                )
                polyOf(
                    s, listOf(
                        rotPt(r * 0.20f, r * 0.10f, c, sn, tx, ty),
                        rotPt(r * 0.38f, r * 0.34f, c, sn, tx, ty),
                        rotPt(r * 0.15f, r * 0.30f, c, sn, tx, ty)
                    ), 0xFFEF4444.toInt()
                )
                ellipsePoly(s, tx, ty, r * 0.24f, r * 0.45f, rot, 0xFFFFFFFF.toInt())
                val win = rotPt(0f, -r * 0.08f, c, sn, tx, ty)
                s.circle(win[0], win[1], r * 0.11f, 0xFF38BDF8.toInt())
            }

            LoadingMascot.HEART -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val beat = 1f + Math.pow(max(0f, sin(tSec * 5.8f)).toDouble(), 4.0).toFloat() * 0.18f
                val oy = cy + r * 0.04f
                val ss = r * 0.56f * beat
                val p = PathBuilder()
                p.moveTo(cx, oy + ss * 0.72f)
                p.cubicTo(cx - ss * 1.15f, oy + ss * 0.1f, cx - ss * 0.85f, oy - ss * 0.88f, cx, oy - ss * 0.32f)
                p.cubicTo(cx + ss * 0.85f, oy - ss * 0.88f, cx + ss * 1.15f, oy + ss * 0.1f, cx, oy + ss * 0.72f)
                p.emit(s, 0xFFF43F5E.toInt())
                s.circle(cx - ss * 0.30f, oy - ss * 0.06f, ss * 0.09f, 0xFFFFFFFF.toInt())
                s.circle(cx + ss * 0.30f, oy - ss * 0.06f, ss * 0.09f, 0xFFFFFFFF.toInt())
            }

            LoadingMascot.CHOMPER -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = true)
                val mouth = abs(sin(tSec * 7.5f)) * 0.55f + 0.08f
                val mouthDeg = mouth * DEG
                s.arc(
                    cx, cy, r * 0.48f, r * 0.48f, mouthDeg, 360f - 2f * mouthDeg,
                    0f, 0xFFFACC15.toInt(), 0, capRound = false, closedPie = true
                )
                s.circle(cx + r * 0.06f, cy - r * 0.22f, r * 0.07f, pillArgb)
            }

            LoadingMascot.BUTTERFLY -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val flap = 0.45f + 0.55f * abs(sin(tSec * 9.0f))
                for (dir in floatArrayOf(-1f, 1f)) {
                    ellipsePoly(
                        s, cx + dir * r * 0.28f * flap, cy - r * 0.14f,
                        r * 0.30f * flap, r * 0.24f, dir * -0.4f, 0xFFF472B6.toInt()
                    )
                    ellipsePoly(
                        s, cx + dir * r * 0.22f * flap, cy + r * 0.16f,
                        r * 0.22f * flap, r * 0.18f, dir * 0.4f, 0xFFC084FC.toInt()
                    )
                }
                s.roundRect(cx, cy, r * 0.10f, r * 0.56f, r * 0.05f, 0xFFFFFFFF.toInt())
            }

            LoadingMascot.UFO -> {
                ring(s, cx, cy, r, tSec, mascot.ringArgb, dotted = false)
                val wob = sin(tSec * 4.5f) * 0.14f
                val bob = sin(tSec * 6.0f) * (r * 0.06f)
                val ty = cy + bob
                val c = cos(wob)
                val sn = sin(wob)
                val beamAlpha = 0.22f + 0.2f * sin(tSec * 8.0f)
                polyOf(
                    s, listOf(
                        rotPt(-r * 0.16f, r * 0.08f, c, sn, cx, ty),
                        rotPt(-r * 0.34f, r * 0.48f, c, sn, cx, ty),
                        rotPt(r * 0.34f, r * 0.48f, c, sn, cx, ty),
                        rotPt(r * 0.16f, r * 0.08f, c, sn, cx, ty)
                    ), argbWithAlpha(0xFF22D3EE.toInt(), beamAlpha)
                )
                // Dome: the reference's upper half-disc, sampled in the rotated frame.
                val dome = ArrayList<FloatArray>()
                val domeC = rotPt(0f, -r * 0.05f, c, sn, cx, ty)
                dome.add(domeC)
                for (i in 0..12) {
                    val a = PI_F + i * PI_F / 12f
                    val lp = rotPt(r * 0.25f * cos(a), -r * 0.05f + r * 0.25f * sin(a), c, sn, cx, ty)
                    dome.add(lp)
                }
                polyOf(s, dome, 0xFF38BDF8.toInt())
                // Saucer: ellipse at local (0, r*0.04) in the wobbling frame.
                ufoSaucer(s, cx, ty, r, c, sn)
            }

            LoadingMascot.GHOST -> {
                val fy = sin(tSec * 5.2f) * (size * 0.07f)
                val gx = cx + size * 0.08f
                val gy = cy + fy
                val w = size * 0.62f
                val h = size * 0.78f
                val d1 = 0.55f + 0.45f * sin(tSec * 8.0f)
                val d2 = 0.55f + 0.45f * sin(tSec * 8.0f + 1.8f)
                val lh = max(2f, size * 0.07f)
                val w1 = size * (0.14f + 0.08f * d1)
                val x1 = gx - w * 0.76f - w1
                s.roundRect(
                    x1 + w1 / 2f, gy - h * 0.12f, w1, lh, lh / 2f,
                    argbWithAlpha(0xFFFFFFFF.toInt(), 0.4f + 0.5f * d1)
                )
                val w2 = size * (0.12f + 0.06f * d2)
                val x2 = gx - w * 0.76f - w2
                s.roundRect(
                    x2 + w2 / 2f, gy + h * 0.10f, w2, lh, lh / 2f,
                    argbWithAlpha(0xFFFFFFFF.toInt(), 0.35f + 0.5f * d2)
                )
                val gr = w / 2f
                val topY = gy - h * 0.12f
                val botY = gy + h * 0.44f
                val wave = sin(tSec * 7.5f) * (h * 0.04f)
                val p = PathBuilder()
                p.moveTo(gx - gr, topY)
                for (i in 1..12) {
                    val a = PI_F + i * PI_F / 12f
                    p.lineTo(gx + gr * cos(a), topY + gr * sin(a))
                }
                p.lineTo(gx + gr, botY)
                val sw = w / 3f
                p.quadTo(gx + gr - sw * 0.5f, botY + h * 0.12f + wave, gx + gr - sw, botY)
                p.quadTo(gx + gr - sw * 1.5f, botY - h * 0.10f - wave, gx + gr - sw * 2f, botY)
                p.quadTo(gx - gr + sw * 0.5f, botY + h * 0.12f + wave, gx - gr, botY)
                p.emit(s, 0xFFFFFFFF.toInt())
                fillEllipse(s, gx - w * 0.16f, topY + h * 0.04f, w * 0.085f, w * 0.115f, pillArgb)
                fillEllipse(s, gx + w * 0.18f, topY + h * 0.04f, w * 0.085f, w * 0.115f, pillArgb)
            }

            LoadingMascot.PUMPKIN -> {
                val cycle = (tSec % 3.333f) / 3.333f
                val n = 9
                val cell = size / n
                val gap = max(1f, cell * 0.14f)
                val box = cell - gap
                val sx = cx - size / 2f
                val sy = cy - size / 2f
                for (row in 0 until n) {
                    for (col in 0 until n) {
                        val ch = PUMPKIN_GRID[row][col]
                        if (ch == '.') continue
                        val diag = (row + col) / ((n - 1) * 2f)
                        val sc = when {
                            cycle < 0.48f -> easeOutCubic(clamp01((cycle / 0.42f - diag * 0.72f) / 0.28f))
                            cycle < 0.72f -> 1f
                            else -> 1f - easeOutCubic(clamp01(((cycle - 0.72f) / 0.24f - diag * 0.65f) / 0.35f))
                        }
                        if (sc <= 0.03f) continue
                        val b = box * sc
                        s.rect(
                            sx + col * cell + (cell - b) / 2f,
                            sy + row * cell + (cell - b) / 2f,
                            b, b, PUMPKIN_COLORS[ch] ?: 0xFFFFFFFF.toInt()
                        )
                    }
                }
            }

            LoadingMascot.FROG -> {
                val phase = (tSec * 2.2f) % 1f
                val jump = sin(phase * PI_F) * (size * 0.18f)
                val squ = if (phase < 0.15f) 1f + (0.15f - phase) * 0.8f else 1f
                val fy = cy + size * 0.10f - jump
                val sy = 2f - squ
                fillEllipse(s, cx, fy, size * 0.38f * squ, size * 0.26f * sy, 0xFF4ADE80.toInt())
                fillEllipse(s, cx - size * 0.18f * squ, fy - size * 0.22f * sy, size * 0.12f * squ, size * 0.12f * sy, 0xFF4ADE80.toInt())
                fillEllipse(s, cx + size * 0.18f * squ, fy - size * 0.22f * sy, size * 0.12f * squ, size * 0.12f * sy, 0xFF4ADE80.toInt())
                fillEllipse(s, cx - size * 0.18f * squ, fy - size * 0.22f * sy, size * 0.075f * squ, size * 0.075f * sy, 0xFFFFFFFF.toInt())
                fillEllipse(s, cx + size * 0.18f * squ, fy - size * 0.22f * sy, size * 0.075f * squ, size * 0.075f * sy, 0xFFFFFFFF.toInt())
                s.circle(cx - size * 0.18f * squ, fy - size * 0.22f * sy, size * 0.04f * min(squ, sy), pillArgb)
                s.circle(cx + size * 0.18f * squ, fy - size * 0.22f * sy, size * 0.04f * min(squ, sy), pillArgb)
                s.circle(cx - size * 0.22f * squ, fy + size * 0.04f * sy, size * 0.055f * min(squ, sy), 0xFFF472B6.toInt())
                s.circle(cx + size * 0.22f * squ, fy + size * 0.04f * sy, size * 0.055f * min(squ, sy), 0xFFF472B6.toInt())
            }

            LoadingMascot.PANDA -> {
                val tilt = sin(tSec * 4.0f) * 0.22f
                val ty = cy + size * 0.04f
                val c = cos(tilt)
                val sn = sin(tilt)
                for (dir in floatArrayOf(-1f, 1f)) {
                    val ear = rotPt(dir * size * 0.28f, -size * 0.24f, c, sn, cx, ty)
                    s.circle(ear[0], ear[1], size * 0.13f, 0xFF334155.toInt())
                }
                ellipsePoly(s, cx, ty, size * 0.36f, size * 0.30f, tilt, 0xFFFFFFFF.toInt())
                for (dir in floatArrayOf(-1f, 1f)) {
                    val patch = rotPt(dir * size * 0.14f, -size * 0.02f, c, sn, cx, ty)
                    ellipsePoly(s, patch[0], patch[1], size * 0.09f, size * 0.11f, tilt + dir * 0.3f, 0xFF1E293B.toInt())
                    val hi = rotPt(dir * size * 0.13f, -size * 0.04f, c, sn, cx, ty)
                    s.circle(hi[0], hi[1], size * 0.032f, 0xFFFFFFFF.toInt())
                }
            }

            LoadingMascot.AXOLOTL -> {
                val squ = sin(tSec * 6.0f) * 0.08f
                val ty = cy + sin(tSec * 6.0f) * (size * 0.04f)
                val sxq = 1f + squ
                val syq = 1f - squ
                for (d in floatArrayOf(-1f, 1f)) {
                    for (a in floatArrayOf(-0.45f, 0f, 0.45f)) {
                        ellipsePoly(
                            s, cx + d * size * 0.36f * sxq, ty + a * size * 0.22f * syq,
                            size * 0.13f * sxq, size * 0.045f * syq, d * a * 0.6f, 0xFFF472B6.toInt()
                        )
                    }
                }
                fillEllipse(s, cx, ty, size * 0.35f * sxq, size * 0.27f * syq, 0xFFFBCFE8.toInt())
                val lw = max(1.5f, size * 0.035f)
                strokeArc(s, cx - size * 0.14f * sxq, ty - size * 0.02f * syq, size * 0.05f * sxq, size * 0.05f * syq, 180f, 180f, lw, pillArgb)
                strokeArc(s, cx + size * 0.14f * sxq, ty - size * 0.02f * syq, size * 0.05f * sxq, size * 0.05f * syq, 180f, 180f, lw, pillArgb)
            }

            LoadingMascot.COFFEE -> {
                val my = cy + size * 0.12f
                strokeArc(s, cx + size * 0.24f, my, size * 0.12f, size * 0.12f, -90f, 180f, max(2f, size * 0.06f), 0xFFFFFFFF.toInt())
                s.roundRect(cx - size * 0.01f, my + size * 0.02f, size * 0.50f, size * 0.44f, size * 0.10f, 0xFFFFFFFF.toInt())
                s.circle(cx - size * 0.09f, my + size * 0.01f, size * 0.035f, pillArgb)
                s.circle(cx + size * 0.07f, my + size * 0.01f, size * 0.035f, pillArgb)
                val steamW = max(1.5f, size * 0.045f)
                for (ox in floatArrayOf(-size * 0.10f, size * 0.08f)) {
                    val xs = FloatArray(11)
                    val ys = FloatArray(11)
                    for (i in 0..10) {
                        val f = i / 10f
                        xs[i] = cx + ox + sin(f * 2f * PI_F - tSec * 6.0f) * (size * 0.045f)
                        ys[i] = my - size * 0.26f - f * (size * 0.26f)
                    }
                    s.polyline(xs, ys, steamW, 0xBFFFFFFF.toInt())
                }
            }

            LoadingMascot.STAR -> {
                val rot = sin(tSec * 4.2f) * 0.25f
                val sc = 1f + sin(tSec * 6.0f) * 0.06f
                val c = cos(rot)
                val sn = sin(rot)
                val xs = FloatArray(10)
                val ys = FloatArray(10)
                for (i in 0 until 10) {
                    val rad = (if (i % 2 == 0) size * 0.42f else size * 0.20f) * sc
                    val ang = i * PI_F / 5f - PI_F / 2f
                    val lx = cos(ang) * rad
                    val ly = sin(ang) * rad
                    xs[i] = cx + lx * c - ly * sn
                    ys[i] = cy + lx * sn + ly * c
                }
                s.poly(xs, ys, 0xFFFACC15.toInt())
                for (dir in floatArrayOf(-1f, 1f)) {
                    val eye = rotPt(dir * size * 0.08f * sc, -size * 0.02f * sc, c, sn, cx, cy)
                    s.circle(eye[0], eye[1], size * 0.035f, pillArgb)
                }
            }

            LoadingMascot.CLOUD -> {
                val bob = sin(tSec * 4.0f) * (size * 0.04f)
                val cy0 = cy - size * 0.08f + bob
                for (i in 0..2) {
                    val p = (tSec * 1.6f + i * 0.33f) % 1f
                    val rx = cx + (i - 1) * (size * 0.18f)
                    val ry = cy0 + size * 0.18f + p * (size * 0.26f)
                    s.circle(rx, ry, size * 0.045f, argbWithAlpha(0xFF38BDF8.toInt(), 1f - p))
                }
                s.circle(cx - size * 0.16f, cy0 + size * 0.04f, size * 0.16f, 0xFFFFFFFF.toInt())
                s.circle(cx + size * 0.16f, cy0 + size * 0.04f, size * 0.16f, 0xFFFFFFFF.toInt())
                s.circle(cx, cy0 - size * 0.06f, size * 0.21f, 0xFFFFFFFF.toInt())
                s.circle(cx - size * 0.08f, cy0 + size * 0.02f, size * 0.032f, pillArgb)
                s.circle(cx + size * 0.08f, cy0 + size * 0.02f, size * 0.032f, pillArgb)
            }

            LoadingMascot.BEAR -> {
                val bop = abs(sin(tSec * 6.5f)) * (size * 0.08f)
                val tilt = sin(tSec * 3.25f) * 0.16f
                val tx = cx - size * 0.04f
                val ty = cy + size * 0.05f - bop
                val c = cos(tilt)
                val sn = sin(tilt)
                for (dir in floatArrayOf(-1f, 1f)) {
                    val ear = rotPt(dir * size * 0.24f, -size * 0.22f, c, sn, tx, ty)
                    s.circle(ear[0], ear[1], size * 0.11f, 0xFFD97706.toInt())
                }
                ellipsePoly(s, tx, ty, size * 0.33f, size * 0.28f, tilt, 0xFFF59E0B.toInt())
                ellipsePoly(s, tx, ty + size * 0.07f, size * 0.13f, size * 0.09f, tilt, 0xFFFDE68A.toInt())
                for (dir in floatArrayOf(-1f, 1f)) {
                    val eye = rotPt(dir * size * 0.11f, -size * 0.04f, c, sn, tx, ty)
                    s.circle(eye[0], eye[1], size * 0.035f, pillArgb)
                }
                // The reference's floating ♪, approximated with primitives
                // (notehead + stem + flag) at the same anchor and bob.
                val ny = cy - size * 0.22f - ((tSec * 1.2f) % 1f) * (size * 0.15f)
                val nx = cx + size * 0.26f
                val noteBlue = 0xFF38BDF8.toInt()
                ellipsePoly(s, nx, ny - size * 0.03f, size * 0.055f, size * 0.04f, -0.35f, noteBlue)
                s.polyline(
                    floatArrayOf(nx + size * 0.05f, nx + size * 0.05f),
                    floatArrayOf(ny - size * 0.03f, ny - size * 0.16f),
                    max(1.5f, size * 0.03f), noteBlue
                )
                polyOf(
                    s, listOf(
                        floatArrayOf(nx + size * 0.05f, ny - size * 0.16f),
                        floatArrayOf(nx + size * 0.12f, ny - size * 0.13f),
                        floatArrayOf(nx + size * 0.12f, ny - size * 0.06f),
                        floatArrayOf(nx + size * 0.05f, ny - size * 0.09f)
                    ), noteBlue
                )
            }

            LoadingMascot.BATTERY -> {
                val bw = size * 0.72f
                val bh = size * 0.42f
                val bx = cx - bw / 2f
                val by = cy - bh / 2f
                s.roundRect(cx, cy, bw, bh, size * 0.09f, 0, max(2f, size * 0.055f), 0xFFFFFFFF.toInt())
                s.roundRect(bx + bw + size * 0.035f, cy, size * 0.07f, bh * 0.44f, size * 0.03f, 0xFFFFFFFF.toInt())
                val bars = floor((tSec * 2.2f) % 4f).toInt()
                val barW = (bw - size * 0.16f) / 3f
                for (i in 0 until bars) {
                    val x = bx + size * 0.06f + i * (barW + size * 0.02f)
                    s.roundRect(
                        x + barW / 2f, cy, barW, bh - size * 0.12f, size * 0.03f,
                        0xFF4ADE80.toInt()
                    )
                }
            }
        }
    }

    /** The duck's parts after the body (kept separate so the rotated body centre stays exact). */
    private fun duckRest(
        s: LoadingCmdSink, cx: Float, ty: Float, r: Float,
        c: Float, sn: Float, pillArgb: Int
    ) {
        val head = rotPt(-r * 0.10f, -r * 0.18f, c, sn, cx, ty)
        s.circle(head[0], head[1], r * 0.28f, 0xFFFACC15.toInt())
        // Beak: rotated ellipse centred at local (-r*0.40, -r*0.14).
        val beakC = rotPt(-r * 0.40f, -r * 0.14f, c, sn, cx, ty)
        val tilt = kotlin.math.atan2(sn, c)
        ellipsePoly(s, beakC[0], beakC[1], r * 0.16f, r * 0.08f, tilt, 0xFFF97316.toInt())
        val eye = rotPt(-r * 0.18f, -r * 0.22f, c, sn, cx, ty)
        s.circle(eye[0], eye[1], r * 0.06f, pillArgb)
    }

    /** The UFO saucer at its local (0, r*0.04) offset, in the rotated frame. */
    private fun ufoSaucer(
        s: LoadingCmdSink, cx: Float, ty: Float, r: Float,
        c: Float, sn: Float
    ) {
        val centre = rotPt(0f, r * 0.04f, c, sn, cx, ty)
        val tilt = kotlin.math.atan2(sn, c)
        ellipsePoly(s, centre[0], centre[1], r * 0.48f, r * 0.14f, tilt, 0xFFE2E8F0.toInt())
    }

    // ── Pumpkin data (the reference's 9×9 pixel grid) ────────────

    private val PUMPKIN_GRID = arrayOf(
        "....G....", "..OOOOO..", ".OOLOLOO.", "OOYOOOYOO", "OOYOLOYOO",
        "OOOOOOOOO", "OOYOYOYOO", ".OYYYYYO.", "..OOOOO.."
    )

    private val PUMPKIN_COLORS = mapOf(
        'G' to 0xFF22C55E.toInt(),
        'O' to 0xFFEA580C.toInt(),
        'L' to 0xFFF97316.toInt(),
        'Y' to 0xFFFACC15.toInt()
    )
}

/**
 * Build 272: which mascot brands which real loading place in the
 * app (one distinct mascot per place). The places were found by
 * auditing the code for visible loading affordances; silent waits
 * (font download, filmstrip generation, autosave, project restore)
 * have no affordance and are deliberately not listed.
 */
object LoadingPillAssignments {
    /** Project file (.ampro) export — determinate dialog, pill brands the header, bar kept. */
    val PROJECT_EXPORT: LoadingMascot = LoadingMascot.BAT

    /** Project file (.ampro) import — indeterminate dialog, pill replaces the indicator. */
    val PROJECT_IMPORT: LoadingMascot = LoadingMascot.ROCKET

    /** Video export — determinate ring kept, pill brands the waiting header. */
    val VIDEO_EXPORT: LoadingMascot = LoadingMascot.BATTERY

    /** AI model first-use download (cutout RVM + face model) — bar kept, mascot chip. */
    val MODEL_DOWNLOAD: LoadingMascot = LoadingMascot.UFO

    /** Cutout (RVM) processing — bar kept, mascot chip. */
    val CUTOUT: LoadingMascot = LoadingMascot.CHOMPER

    /** Face find / swap processing — bar kept, mascot chip. */
    val FACE: LoadingMascot = LoadingMascot.PANDA

    /** Motion tracking / camera follow / planar remove — bar kept, mascot chip. */
    val TRACKING: LoadingMascot = LoadingMascot.GHOST

    /** Reverse video / sound rendering — bar kept, mascot chip. */
    val REVERSE: LoadingMascot = LoadingMascot.FROG

    /** Beat detection — bar kept, mascot chip. */
    val BEATS: LoadingMascot = LoadingMascot.BEAR

    /** All wired places, for the distinctness test. */
    val ALL: List<LoadingMascot> = listOf(
        PROJECT_EXPORT, PROJECT_IMPORT, VIDEO_EXPORT, MODEL_DOWNLOAD,
        CUTOUT, FACE, TRACKING, REVERSE, BEATS
    )
}
