package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.theme.AmTextDim
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TIMELINE_PX_PER_MS
import com.example.model.TimescaleGeometry
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmCyanBright
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.RulerSubTickColor
import com.example.ui.theme.RulerTickColor
import com.example.ui.theme.IconActive
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable
import java.util.Locale

// Geometry measured from the reference (dp):  [4] [pill 48] [5] [ bars ... ] [5] [pill] [4]
private val PillMargin = 4.dp
private val LeftPillWidth = 48.dp
private val PillGap = 5.dp
private val PillHeight = 19.dp
private val BarWidth = 4.dp
private val BarHeight = 16.5.dp
private val MinBarPitch = 7.dp
private val BeatMarkColor = AmCyanBright
/** Build 217: the reference orange — the selected layer's keyframe diamonds on the ruler. */
private val KeyframeDiamondColor = Color(0xFFF97316)
private val TickSteps = longArrayOf(200L, 500L, 1000L, 2000L, 5000L, 10_000L, 30_000L, 60_000L)

/**
 * Segmented ruler: rounded bars that scroll with the playhead (one bar per time step).
 * Bars before the playhead are orange, after it grey; the bar under the playhead is a mix and
 * both ends fade out. The current time sits in a pill on the left, the project length on the right.
 *
 * Build 248 (UI migration stage 1): the strip's drawing — ticks, export band,
 * beat marks, keyframe diamonds, the playhead needle and the bottom hairline —
 * is rasterized by the C++ GL vector renderer through [NativeVectorCanvas];
 * the geometry law lives in [TimescaleGeometry] (a line-for-line port of the
 * build-247 Compose Canvas block, harness-pinned). What stays in this thin
 * Compose host: layout, the back chevron and layer pill (tap targets), and
 * the three text runs (playhead timecode, total duration, pill label) as
 * overlays — UI GL text arrives with the atlas stage (see
 * CPP-UI-MIGRATION-MAP.md). The playhead is read in composition, exactly as
 * the build-247 timecode Text read it, so the GL strip redraws on the same
 * ticks the Canvas did.
 */
@Composable
fun TimescaleSection(
    height: Dp,
    playheadMs: () -> Long,
    totalDurationMs: Long,
    pxPerMs: Float = TIMELINE_PX_PER_MS,
    modifier: Modifier = Modifier,
    /** When set, a back chevron sits at the left end of the ruler. */
    onBack: (() -> Unit)? = null,
    /** Beat marks (project time, ms): drawn as cyan marks on the ruler. */
    beatMarksMs: List<Long> = emptyList(),
    /** Export range points (build 199, A10): when both are set, the ranged span is banded on the ruler. */
    exportInMs: Long? = null,
    exportOutMs: Long? = null,
    /**
     * Build 217: the layer-count pill at the ruler's left end (the
     * reference's pill, showing the REAL layer count). Null = no pill
     * (panel hosts). Its tap opens the timeline options sheet — the
     * universal tools live there now (Sarowar's no-added-buttons rule).
     */
    layerCount: Int? = null,
    onLayerPillClick: (() -> Unit)? = null,
    /** Build 217: keyframe times (project ms) of the selected layer — orange diamonds on the ruler. */
    keyframeTimesMs: List<Long> = emptyList()
) {
    val backInset = if (onBack != null) 36.dp else 0.dp
    // Read in composition (the build-247 timecode Text did the same): the
    // strip recomposes on every playhead tick, and the GL snapshot below is
    // rebuilt from this value.
    val currentMs = playheadMs()
    // The strip's colours, derived through the same Color.toArgb()
    // conversions the build-247 Canvas block used (alpha rounding identical).
    val tsColors = TimescaleGeometry.Colors(
        tickArgb = RulerTickColor.toArgb(),
        subTickArgb = RulerSubTickColor.toArgb(),
        bandArgb = Color.White.copy(alpha = 0.07f).toArgb(),
        bandEdgeArgb = Color.White.copy(alpha = 0.4f).toArgb(),
        beatArgb = Color.White.copy(alpha = 0.5f).toArgb(),
        diamondArgb = KeyframeDiamondColor.toArgb(),
        needleArgb = Color.White.toArgb(),
        hairlineArgb = AmHairline.toArgb()
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBarSurface) // build 214: the ruler strip shares the transport bar's surface — one merged slim bar
            .testTag("timescale_section")
    ) {
        // ── Scrolling bars, band, marks, needle, hairline (C++ GL) ────
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(
                currentMs, totalDurationMs, pxPerMs, backInset,
                beatMarksMs, exportInMs, exportOutMs, keyframeTimesMs, tsColors
            ),
            buildCmds = { w, h, density ->
                val ops = TimescaleGeometry.ops(
                    widthPx = w, heightPx = h, density = density,
                    currentMs = currentMs, pxPerMs = pxPerMs,
                    totalDurationMs = totalDurationMs,
                    backInsetDp = backInset.value,
                    beatMarksMs = beatMarksMs,
                    exportInMs = exportInMs, exportOutMs = exportOutMs,
                    keyframeTimesMs = keyframeTimesMs,
                    colors = tsColors
                )
                for (op in ops) {
                    when (op) {
                        is TimescaleGeometry.Op.Line ->
                            addLine(op.x0, op.y0, op.x1, op.y1, op.widthPx, op.argb, capRound = op.capRound)
                        is TimescaleGeometry.Op.Rect ->
                            addRect(op.x, op.y, op.w, op.h, op.argb)
                        is TimescaleGeometry.Op.RoundRect ->
                            addRoundRect(op.cx, op.cy, op.w, op.h, op.radiusPx, op.argb)
                        is TimescaleGeometry.Op.FillPoly ->
                            addFilledPolygon(op.xs, op.ys, op.xs.size, op.argb)
                    }
                }
            }
        )

        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(36.dp)
                    .fillMaxHeight()
                    .bouncyClickable(onClick = onBack)
                    .testTag("panel_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = IconActive,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 15.dp)
                .testTag("playhead_time_pill"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatTimeCode(currentMs),
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
            )
        }
        // The project length: small muted text at the ruler's right end.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 3.dp)
                .testTag("total_time_pill"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatClock(totalDurationMs),
                color = AmTextFaint,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        if (layerCount != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(com.example.ui.theme.RefPillBg)
                    .border(1.dp, com.example.ui.theme.RefPillBorder, androidx.compose.foundation.shape.CircleShape)
                    .bouncyClickable(pressedScale = 0.95f, onClick = { onLayerPillClick?.invoke() })
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .testTag("layer_count_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$layerCount Layers",
                    color = AmTextDim,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                Canvas(Modifier.size(9.dp)) {
                    val u = size.width / 24f
                    val c = AmTextDim
                    val p1 = Path().apply {
                        moveTo(7f * u, 15f * u); lineTo(12f * u, 20f * u); lineTo(17f * u, 15f * u)
                    }
                    val p2 = Path().apply {
                        moveTo(7f * u, 9f * u); lineTo(12f * u, 4f * u); lineTo(17f * u, 9f * u)
                    }
                    drawPath(p1, c, style = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(p2, c, style = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    return if (totalSeconds >= 3600L) {
        String.format(
            Locale.US, "%d:%02d:%02d",
            totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60
        )
    } else {
        String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

fun formatTimeCode(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    // CapCut-style: 00:01:06  (MM:SS:centiseconds)
    return String.format(Locale.US, "%02d:%02d:%02d", minutes, seconds, hundredths)
}
