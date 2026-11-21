package com.example.model

/**
 * Build 248 (UI migration stage 1) — the ruler strip's drawing law, extracted
 * verbatim from TimescaleSection's Compose Canvas block (build 247) so the
 * strip can be rasterized by the C++ GL vector renderer (ui_gl_renderer)
 * instead of Compose Canvas. This object decides WHAT is drawn and WHERE, in
 * raw pixels; the composable only packs these ops into the native command
 * buffer and keeps the strip's three text runs (timecode, total duration,
 * layer-pill label) as Compose overlays in the thin host.
 *
 * Every formula mirrors the build-247 draw block expression for expression —
 * same step selection, same clipping window, same draw order — so the GL
 * strip lands pixel-equivalent geometry; only the rasterizer changes.
 *
 * Ops are in DRAW ORDER: ticks, export band, beat marks, keyframe diamonds,
 * the playhead needle, and the bottom hairline last (it was the topmost
 * sibling in the Compose layout).
 *
 * Build 249 (UI migration stage 2): the strip's timecode and total-duration
 * text runs move from Compose overlays into the GL stream as atlas blits
 * ([Op.Text]); [timecodeLayout] / [totalLayout] below reproduce the two
 * overlays' Compose placement (a TopCenter box under a 15 dp top padding;
 * a BottomEnd box with 6 dp end / 3 dp bottom padding) from the atlas
 * paints' own metrics. The layer-pill label stays a Compose overlay: the
 * pill is a composite (background, border, chevron, tap target) and only
 * its text moving would split one 19 dp pill across two renderers.
 */
object TimescaleGeometry {

    /** One primitive the strip draws, in pixels, colours as ARGB ints. */
    sealed interface Op {
        /** A straight segment; [capRound] false = the butt cap Compose's drawLine defaults to. */
        data class Line(
            val x0: Float, val y0: Float, val x1: Float, val y1: Float,
            val widthPx: Float, val argb: Int, val capRound: Boolean
        ) : Op

        /** A filled rect by its top-left corner. */
        data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val argb: Int) : Op

        /** A filled round rect by its CENTRE (the native command's own convention). */
        data class RoundRect(
            val cx: Float, val cy: Float, val w: Float, val h: Float,
            val radiusPx: Float, val argb: Int
        ) : Op

        /** A filled polygon (the keyframe diamonds). */
        data class FillPoly(val xs: FloatArray, val ys: FloatArray, val argb: Int) : Op

        /**
         * Build 249: one atlas blit — [entry]'s rect top-left lands at
         * ([dstX], [dstY]); the src sub-rect is entry-relative px;
         * [dstW]/[dstH] <= 0 draw at natural src size.
         */
        data class Text(
            val entry: Int, val dstX: Float, val dstY: Float,
            val srcDX: Float, val srcDY: Float, val srcW: Float, val srcH: Float,
            val dstW: Float, val dstH: Float, val argb: Int
        ) : Op
    }

    /**
     * The strip's colours as ARGB ints. The composable derives them from the
     * theme tokens through the same `Color.toArgb()` conversions the Compose
     * draw block used, so the alpha rounding (0.07 / 0.4 / 0.5 washes) is
     * bit-identical; the law itself is colour-agnostic.
     */
    data class Colors(
        val tickArgb: Int,
        val subTickArgb: Int,
        val bandArgb: Int,
        val bandEdgeArgb: Int,
        val beatArgb: Int,
        val diamondArgb: Int,
        val needleArgb: Int,
        val hairlineArgb: Int
    )

    /** Candidate tick steps (ms), smallest first — the build-247 table. */
    val TICK_STEPS_MS = longArrayOf(200L, 500L, 1000L, 2000L, 5000L, 10_000L, 30_000L, 60_000L)

    // Geometry constants (dp), measured from the reference / build 217–223.
    private const val AREA_INSET_DP = 6f
    private const val MIN_BAR_PITCH_DP = 7f
    private const val TICK_TOP_DP = 3f
    private const val MAJOR_TICK_LEN_DP = 13f
    private const val MINOR_TICK_LEN_DP = 6f
    private const val MAJOR_TICK_W_DP = 1.2f
    private const val MINOR_TICK_W_DP = 1f
    private const val BAND_EDGE_W_DP = 1.6f
    private const val BEAT_MARK_W_DP = 2.5f
    private const val BEAT_MARK_H_DP = 15f
    private const val DIAMOND_HALF_DP = 2.5f
    private const val DIAMOND_CY_DP = 10f
    private const val NEEDLE_TOP_DP = 2f
    private const val NEEDLE_W_DP = 2f
    private const val NEEDLE_H_DP = 11f
    private const val NEEDLE_R_DP = 1f
    private const val NEEDLE_LINE_TOP_DP = 29f
    private const val NEEDLE_LINE_W_DP = 2f
    private const val HAIRLINE_H_DP = 1f

    // Build 249: the two text overlays' Compose paddings (dp).
    private const val TIMECODE_TOP_DP = 15f
    private const val TOTAL_END_DP = 6f
    private const val TOTAL_BOTTOM_DP = 3f

    /** One timecode character's blit origin: digit-table index + draw-origin x (px). */
    data class CharBlit(val charIndex: Int, val originXPx: Float)

    /** The timecode's placement: the shared baseline (px) + one blit per character. */
    data class TimecodeLayout(val baselinePx: Float, val chars: List<CharBlit>)

    /**
     * Build 249: where the centred playhead timecode's characters land.
     * [advances] is the TIMECODE digit table (indices 0..9 = '0'..'9',
     * 10 = ':'); [ascentPx] is that paint's fontMetrics ascent (negative).
     * The Compose overlay was a TopCenter box with a 15 dp top padding, so
     * the text block is centred on the strip and its first baseline sits at
     * top − ascent.
     */
    fun timecodeLayout(
        widthPx: Float,
        density: Float,
        advances: FloatArray,
        ascentPx: Float,
        text: String
    ): TimecodeLayout {
        var totalW = 0f
        for (ch in text) totalW += advances[charIndexOf(ch)]
        var x = (widthPx - totalW) / 2f
        val chars = ArrayList<CharBlit>(text.length)
        for (ch in text) {
            val idx = charIndexOf(ch)
            chars += CharBlit(idx, x)
            x += advances[idx]
        }
        return TimecodeLayout(
            baselinePx = TIMECODE_TOP_DP * density - ascentPx,
            chars = chars
        )
    }

    /**
     * Build 249: where the total-duration text lands — the Compose overlay
     * was a BottomEnd box with 6 dp end / 3 dp bottom padding. Returns
     * (boxLeftPx, baselinePx): the text block's left edge (its draw origin)
     * and its baseline, from the TOTAL paint's [advancePx] / [descentPx].
     */
    fun totalLayout(
        widthPx: Float,
        heightPx: Float,
        density: Float,
        advancePx: Float,
        descentPx: Float
    ): Pair<Float, Float> {
        val boxLeft = widthPx - TOTAL_END_DP * density - advancePx
        val baseline = heightPx - TOTAL_BOTTOM_DP * density - descentPx
        return boxLeft to baseline
    }

    private fun charIndexOf(ch: Char): Int = if (ch == ':') 10 else ch - '0'

    /**
     * The strip's full draw list for one frame.
     *
     * [widthPx]/[heightPx] are the strip's pixel size; [density] converts the
     * dp constants exactly as Compose's `Dp.toPx()` did; [pxPerMs] is the
     * timeline scale in DP per ms (the draw block's `pxPerMs.dp.toPx()`);
     * [backInsetDp] is 36 when the host shows the back chevron, else 0.
     */
    fun ops(
        widthPx: Float,
        heightPx: Float,
        density: Float,
        currentMs: Long,
        pxPerMs: Float,
        totalDurationMs: Long,
        backInsetDp: Float,
        beatMarksMs: List<Long>,
        exportInMs: Long?,
        exportOutMs: Long?,
        keyframeTimesMs: List<Long>,
        colors: Colors
    ): List<Op> {
        val out = ArrayList<Op>()
        val w = widthPx
        val centerPx = w / 2f
        val pxPerMsPx = pxPerMs * density
        val areaStart = (backInsetDp + AREA_INSET_DP) * density
        val areaEnd = w - AREA_INSET_DP * density

        // Minor step: grows when zoomed out so ticks never crowd (~7 dp pitch).
        var step = TICK_STEPS_MS.last()
        for (s2 in TICK_STEPS_MS) {
            if (s2 * pxPerMsPx >= MIN_BAR_PITCH_DP * density) { step = s2; break }
        }
        val lastMs = if (totalDurationMs > 0L) totalDurationMs else Long.MAX_VALUE / 4
        val halfSpanMs = (centerPx / pxPerMsPx).toLong() + step
        val firstTick = (((currentMs - halfSpanMs).coerceAtLeast(0L)) / step) * step
        val lastTick = currentMs + halfSpanMs
        // Minor ticks are 1 x 6 dp, majors 1.2 x 13 dp, all from top 3 dp.
        val tickTop = TICK_TOP_DP * density
        val majorEvery = step * 5

        var t = firstTick
        while (t <= lastTick && t <= lastMs) {
            val x = centerPx + (t - currentMs) * pxPerMsPx
            if (x >= areaStart && x <= areaEnd) {
                val major = t % majorEvery == 0L
                out += Op.Line(
                    x0 = x, y0 = tickTop,
                    x1 = x, y1 = tickTop + (if (major) MAJOR_TICK_LEN_DP else MINOR_TICK_LEN_DP) * density,
                    widthPx = (if (major) MAJOR_TICK_W_DP else MINOR_TICK_W_DP) * density,
                    argb = if (major) colors.tickArgb else colors.subTickArgb,
                    capRound = false
                )
            }
            t += step
        }

        // Export range (build 199, A10): a soft wash with ink edges.
        if (exportInMs != null && exportOutMs != null && exportOutMs > exportInMs) {
            val xIn = centerPx + (exportInMs - currentMs) * pxPerMsPx
            val xOut = centerPx + (exportOutMs - currentMs) * pxPerMsPx
            val bandL = maxOf(xIn, areaStart)
            val bandR = minOf(xOut, areaEnd)
            if (bandR > bandL) {
                out += Op.Rect(bandL, 0f, bandR - bandL, heightPx, colors.bandArgb)
            }
            val edgeW = BAND_EDGE_W_DP * density
            if (xIn >= areaStart && xIn <= areaEnd) {
                out += Op.Line(xIn, 0f, xIn, heightPx, edgeW, colors.bandEdgeArgb, capRound = false)
            }
            if (xOut >= areaStart && xOut <= areaEnd) {
                out += Op.Line(xOut, 0f, xOut, heightPx, edgeW, colors.bandEdgeArgb, capRound = false)
            }
        }

        // Beat marks: white at 50%, taller than the ticks.
        if (beatMarksMs.isNotEmpty()) {
            val markW = BEAT_MARK_W_DP * density
            val markH = BEAT_MARK_H_DP * density
            for (m in beatMarksMs) {
                val x = centerPx + (m - currentMs) * pxPerMsPx
                if (x >= areaStart && x <= areaEnd) {
                    out += Op.RoundRect(
                        cx = x, cy = tickTop + markH / 2f,
                        w = markW, h = markH,
                        radiusPx = markW / 2f, argb = colors.beatArgb
                    )
                }
            }
        }

        // The selected layer's keyframes: orange diamonds on the ruler.
        if (keyframeTimesMs.isNotEmpty()) {
            val half = DIAMOND_HALF_DP * density
            val cy = DIAMOND_CY_DP * density
            for (kt in keyframeTimesMs) {
                val x = centerPx + (kt - currentMs) * pxPerMsPx
                if (x < areaStart || x > areaEnd) continue
                out += Op.FillPoly(
                    xs = floatArrayOf(x, x + half, x, x - half),
                    ys = floatArrayOf(cy - half, cy, cy + half, cy),
                    argb = colors.diamondArgb
                )
            }
        }

        // The CENTRED playhead (build 217): a white needle at the top centre,
        // and the 2 dp white line resuming below the timecode to the bottom.
        val needleW = NEEDLE_W_DP * density
        val needleH = NEEDLE_H_DP * density
        out += Op.RoundRect(
            cx = centerPx, cy = NEEDLE_TOP_DP * density + needleH / 2f,
            w = needleW, h = needleH,
            radiusPx = NEEDLE_R_DP * density, argb = colors.needleArgb
        )
        out += Op.Line(
            x0 = centerPx, y0 = NEEDLE_LINE_TOP_DP * density,
            x1 = centerPx, y1 = heightPx,
            widthPx = NEEDLE_LINE_W_DP * density, argb = colors.needleArgb,
            capRound = false
        )

        // The ruler row's bottom hairline (build 223: the family hairline token).
        out += Op.Rect(0f, heightPx - HAIRLINE_H_DP * density, w, HAIRLINE_H_DP * density, colors.hairlineArgb)

        return out
    }
}
