package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.example.engine.UiLabelStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign

/**
 * The freehand drawing tool (build 166, item 1): the canvas overlay that captures one
 * freehand stroke. Points are reported in frame fractions (0..1) — the input of
 * [com.example.model.VectorDraw.build], which turns the finished stroke into a PATH
 * shape layer. A new touch starts a new stroke (the previous one is replaced); the
 * stroke stays drawn while the user decides (Smooth / Close / Width in the panel).
 */
@Composable
fun DrawVectorOverlay(
    points: List<Pair<Float, Float>>,
    strokeWidthDp: Float,
    closed: Boolean,
    onStroke: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val strokeNow by rememberUpdatedState(onStroke)
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("draw_vector_overlay")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val first = down.changes.firstOrNull { it.pressed } ?: continue
                        var pts = listOf(
                            (first.position.x / size.width).coerceIn(0f, 1f) to
                                (first.position.y / size.height).coerceIn(0f, 1f)
                        )
                        strokeNow(pts)
                        first.consume()
                        var drawing = true
                        while (drawing) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.first()
                            if (ch.pressed) {
                                val fx = (ch.position.x / size.width).coerceIn(0f, 1f)
                                val fy = (ch.position.y / size.height).coerceIn(0f, 1f)
                                val last = pts.last()
                                if (hypot((fx - last.first) * size.width, (fy - last.second) * size.height) >= 3f) {
                                    pts = pts + (fx to fy)
                                    strokeNow(pts)
                                }
                                ch.consume()
                            } else {
                                drawing = false
                            }
                        }
                    }
                }
            }
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            drawKey = listOf(points, strokeWidthDp, closed),
            buildCmds = { w, h, d ->
                if (points.isNotEmpty()) {
                    val n = points.size
                    val xs = FloatArray(n) { points[it].first * w }
                    val ys = FloatArray(n) { points[it].second * h }
                    if (closed && n >= 3) {
                        addFilledPolygon(xs, ys, n, 0x40FFFFFF)
                    }
                    if (n >= 2) {
                        addPolyline(
                            xs, ys, n,
                            closed = closed && n >= 3,
                            width = (strokeWidthDp * d).coerceAtLeast(1f),
                            argb = android.graphics.Color.WHITE,
                            capRound = true
                        )
                    }
                }
            },
        )
    }
}

/**
 * The draw panel (same place as the Masks / Effects panels): Smooth and Closed-shape
 * switches, the stroke width, and Clear / Cancel / Done. Flat B/W like every panel.
 */
@Composable
fun DrawVectorPanel(
    smooth: Boolean,
    onSmooth: (Boolean) -> Unit,
    closed: Boolean,
    onClosed: (Boolean) -> Unit,
    widthDp: Float,
    onWidth: (Float) -> Unit,
    hasStroke: Boolean,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Build 252 (stage 5): the panel's texts, chips, width row
        // and buttons are drawn in GL. The chips are the GlassChip
        // pattern via GlChip (control-label styles, 0.95 press).
        GlTextBlock(
            text = "Draw on the picture — one stroke becomes a path layer you can move, colour and effect like any shape.",
            style = UiLabelStyle.SHEET_DESC,
            colorArgb = AmTextDim.toArgb(),
            align = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlChip(
                label = "Smooth",
                selected = smooth,
                tag = "draw_smooth_chip",
                onClick = { onSmooth(!smooth) },
                padVDp = 5f,
                pressScale = 0.95f,
                styleOn = UiLabelStyle.CONTROL_LABEL_BOLD,
                styleOff = UiLabelStyle.CONTROL_LABEL,
                textOffArgb = AmTextDim.toArgb()
            )
            GlChip(
                label = "Closed shape",
                selected = closed,
                tag = "draw_closed_chip",
                onClick = { onClosed(!closed) },
                padVDp = 5f,
                pressScale = 0.95f,
                styleOn = UiLabelStyle.CONTROL_LABEL_BOLD,
                styleOff = UiLabelStyle.CONTROL_LABEL,
                textOffArgb = AmTextDim.toArgb()
            )
        }
        Spacer(Modifier.height(4.dp))
        GlWidthRow(
            value = widthDp,
            onValueChange = onWidth,
            valueRange = 1f..30f,
            sliderTag = "draw_width_slider",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlTextButton("Clear", enabled = hasStroke, onClick = onClear)
            Spacer(Modifier.weight(1f))
            GlTextButton("Cancel", enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            GlDonePill(label = "Done", tag = "draw_done_button", onClick = onDone)
        }
        if (!hasStroke) {
            Spacer(Modifier.height(2.dp))
            GlTextBlock(
                text = "Nothing drawn yet",
                style = UiLabelStyle.TEXT_10,
                colorArgb = AmTextFaint.toArgb(),
                align = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
