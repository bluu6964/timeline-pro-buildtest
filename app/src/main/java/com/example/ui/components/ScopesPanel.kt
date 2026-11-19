package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiGlRuntime
import com.example.model.ScopeData
import com.example.ui.theme.AmBase
import com.example.ui.theme.TextSecondary

/**
 * The video-scopes panel (build 196): RGB + luma histogram, luma waveform
 * and vectorscope of the composed preview frame at the playhead, computed by
 * the pure [ScopeStats] pass over the canvas surface's picture. Shown under
 * the play controls while the scopes toggle is on (its height comes out of
 * the canvas, like the onion-skin row — the sections below do not move).
 *
 * Chrome stays in the settled B/W language (dark plots, grey graticule, grey
 * labels); the TRACES carry data colours, classified like the onion-skin
 * tints and the marker gold — the histogram draws its channels in red /
 * green / blue with the luma as a white line, the waveform in the classic
 * pale-green phosphor, the vectorscope in a pale cyan phosphor with the six
 * saturated-colour targets as dim graticule crosses. An RGB parade is
 * deliberately not squeezed in: at phone width each of its three traces
 * would be ~35 dp tall and unreadable, so the panel ships the luma waveform
 * plus the full RGB histogram instead (build 196 notes).
 *
 * [data] is null when no frame could be read (surface gone / readback
 * failed): the cells then show their graticules and an honest "No frame" —
 * never a stale set of traces presented as current.
 */
val ScopesPanelHeight = 118.dp

private val PlotBg = Color(0xFF0D0D0D)
private val GridLine = Color(0xFF262626)
private val HistRed = Color(0xFFFF4444)
private val HistGreen = Color(0xFF51FF51)
private val HistBlue = Color(0xFF5599FF)
private val WaveTrace = Color(0xFFB7FFB7)
private val VecTrace = Color(0xFFA8FFF2)
private val VecTarget = Color(0xFF4A4A4A)

@Composable
fun ScopesPanel(data: ScopeData?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ScopesPanelHeight)
            .background(AmBase)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScopeCell("Histogram", Modifier.weight(1f)) { HistogramPlot(data) }
        ScopeCell("Waveform", Modifier.weight(1f)) { WaveformPlot(data) }
        ScopeCell("Vectorscope", Modifier.weight(1f)) { VectorscopePlot(data) }
    }
}

@Composable
private fun ScopeCell(label: String, modifier: Modifier = Modifier, plot: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 3.dp)) { plot() }
    }
}

/** The empty state shared by all three plots: graticule only, honestly labelled. */
@Composable
private fun EmptyPlot() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(PlotBg)
        drawRect(GridLine, style = Stroke(1f))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No frame", color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun HistogramPlot(data: ScopeData?) {
    if (data == null) {
        EmptyPlot()
        return
    }
    NativeGlTextureHost(
        isOpaque = true,
        drawOnGl = { sid -> UiGlRuntime.drawScopeHistogramOnGl(sid, data) },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun WaveformPlot(data: ScopeData?) {
    if (data == null) {
        EmptyPlot()
        return
    }
    NativeGlTextureHost(
        isOpaque = true,
        drawOnGl = { sid -> UiGlRuntime.drawScopeWaveformOnGl(sid, data) },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun VectorscopePlot(data: ScopeData?) {
    if (data == null) {
        EmptyPlot()
        return
    }
    NativeGlTextureHost(
        isOpaque = true,
        drawOnGl = { sid -> UiGlRuntime.drawScopeVectorscopeOnGl(sid, data) },
        modifier = Modifier.fillMaxSize()
    )
}
