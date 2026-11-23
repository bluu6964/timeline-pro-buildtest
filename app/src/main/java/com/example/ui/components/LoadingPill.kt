package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.VectorCmdBuilder
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.LoadingCmdSink
import com.example.model.LoadingMascot
import com.example.model.LoadingPillLaw
import com.example.model.PanelGeometry

/**
 * Build 272: the cute loading pill (Sarowar's reference
 * ai_studio_code__60.html) as a reusable GL component — a dark
 * #171717 pill with an animated mascot, a label and 3 staggered
 * dots, drawn through the app's vector canvas per
 * [LoadingPillLaw]. Purely presentational: it exists exactly
 * while its host's operation is in flight (the host composes it
 * only then), adds no delays, and its frame clock runs only
 * while it is composed.
 */

/** Adapts the law's [LoadingCmdSink] to the GL [VectorCmdBuilder]. */
private class BuilderSink(private val b: VectorCmdBuilder) : LoadingCmdSink {
    override fun circle(cx: Float, cy: Float, r: Float, fillArgb: Int) =
        b.addCircle(cx, cy, r, fillArgb)

    override fun roundRect(
        cx: Float, cy: Float, w: Float, h: Float, corner: Float,
        fillArgb: Int, strokeWidth: Float, strokeArgb: Int
    ) = b.addRoundRect(cx, cy, w, h, corner, fillArgb, strokeWidth, strokeArgb)

    override fun rect(x: Float, y: Float, w: Float, h: Float, fillArgb: Int) =
        b.addRect(x, y, w, h, fillArgb)

    override fun arc(
        cx: Float, cy: Float, rx: Float, ry: Float,
        startDeg: Float, sweepDeg: Float, width: Float,
        fillArgb: Int, strokeArgb: Int, capRound: Boolean, closedPie: Boolean
    ) = b.addArc(cx, cy, rx, ry, startDeg, sweepDeg, width, fillArgb, strokeArgb, capRound, closedPie)

    override fun poly(xs: FloatArray, ys: FloatArray, fillArgb: Int) =
        b.addFilledPolygon(xs, ys, xs.size, fillArgb)

    override fun polyline(xs: FloatArray, ys: FloatArray, width: Float, argb: Int) =
        b.addPolyline(xs, ys, xs.size, closed = false, width = width, argb = argb)
}

/** Seconds since this clock was composed; ticks only while composed. */
@Composable
private fun rememberLoadingClockSec(): Float {
    var tSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            tSec = (now - t0) / 1_000_000_000f
        }
    }
    return tSec
}

private val PILL_LABEL_STYLE = UiLabelStyle.SHEET_TITLE_SEMI

/**
 * The full pill: mascot + [label] + the 3 staggered dots. Width
 * hugs the label per [LoadingPillLaw.pillWidthPx].
 */
@Composable
fun LoadingPill(
    mascot: LoadingMascot,
    label: String = LoadingPillLaw.DEFAULT_LABEL,
    modifier: Modifier = Modifier,
    heightDp: Float = 44f
) {
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val tSec = rememberLoadingClockSec()
    val heightPx = heightDp * density
    val labelAdvance = atlas.measure(label, PILL_LABEL_STYLE)
    val dotAdvance = atlas.measure(".", PILL_LABEL_STYLE)
    val widthPx = LoadingPillLaw.pillWidthPx(heightPx, labelAdvance, dotAdvance)

    fun resolveLabel(): UiLabelAtlas.Entry? = atlas.resolve(label, PILL_LABEL_STYLE)
    var entry = resolveLabel()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveLabel()
    }
    val e = entry
    val pad = UiLabelAtlas.PAD.toFloat()

    Box(modifier.width((widthPx / density).dp).height(heightDp.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.width((widthPx / density).dp).height(heightDp.dp),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(tSec, e, label, mascot, atlas.generation),
            buildCmds = { w, h, _ ->
                // The reference's soft shadow, as an offset underlay
                // (the vector canvas has no blur-shadow primitive).
                addRoundRect(w / 2f, h / 2f + h * 0.06f, w, h, h / 2f, 0x1C000000)
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, LoadingPillLaw.PILL_ARGB)
                val sink = BuilderSink(this)
                LoadingPillLaw.emitMascot(
                    sink, mascot,
                    LoadingPillLaw.iconCentreXPx(h), h / 2f,
                    LoadingPillLaw.iconSizePx(h), tSec, LoadingPillLaw.PILL_ARGB
                )
                // Label + dots.
                val textH = atlas.textHeightPx(PILL_LABEL_STYLE)
                val fm = atlas.fontMetrics(PILL_LABEL_STYLE)
                val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                if (e != null) {
                    val (dstX, dstY) = EditorGeometry.textDst(
                        LoadingPillLaw.textXPx(h), baseline, e.bearing, e.base, pad
                    )
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = 0xFFFFFFFF.toInt()
                    )
                }
                val dotR = dotAdvance * 0.16f
                for (d in 0..2) {
                    val alpha = LoadingPillLaw.dotAlpha(tSec, d)
                    addCircle(
                        LoadingPillLaw.dotCentreXPx(h, labelAdvance, dotAdvance, d),
                        baseline - dotR,
                        dotR,
                        LoadingPillLaw.argbWithAlpha(0xFFFFFFFF.toInt(), alpha)
                    )
                }
            }
        )
    }
}

/**
 * The compact form for bars that already carry their own label
 * (the cutout / face / tracking progress bars): a small dark
 * pill with only the animated mascot inside.
 */
@Composable
fun LoadingMascotChip(
    mascot: LoadingMascot,
    modifier: Modifier = Modifier,
    heightDp: Float = 26f,
    widthDp: Float = 42f
) {
    val tSec = rememberLoadingClockSec()
    Box(modifier.width(widthDp.dp).height(heightDp.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.width(widthDp.dp).height(heightDp.dp),
            clearArgb = 0,
            drawKey = listOf(tSec, mascot),
            buildCmds = { w, h, _ ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, LoadingPillLaw.PILL_ARGB)
                LoadingPillLaw.emitMascot(
                    BuilderSink(this), mascot,
                    w / 2f, h / 2f,
                    LoadingPillLaw.iconSizePx(h), tSec, LoadingPillLaw.PILL_ARGB
                )
            }
        )
    }
}
