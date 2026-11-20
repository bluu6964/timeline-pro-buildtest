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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.theme.AmTextDim
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TIMELINE_PX_PER_MS
import com.example.ui.theme.AmBaseDeep
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmCyanBright
import com.example.ui.theme.AmOrange
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBarSurface) // build 214: the ruler strip shares the transport bar's surface — one merged slim bar
            .testTag("timescale_section")
    ) {
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

        // ── Scrolling bars ─────────────────────────────────────────────
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val currentMs = playheadMs()
            val w = size.width
            val centerPx = w / 2f
            val pxPerMsPx = pxPerMs.dp.toPx()
            // Build 216 (Sarowar's HTML reference): the ruler area is the
            // full width (the old left/right time pills are gone — the
            // playhead timecode sits CENTRED under the playhead cap, the
            // total length is small muted text at the right end).
            val areaStart = (backInset + 6.dp).toPx()
            val areaEnd = w - 6.dp.toPx()

            // Minor step: grows when zoomed out so ticks never crowd (~8 dp pitch).
            var step = TickSteps.last()
            for (s2 in TickSteps) {
                if (s2 * pxPerMsPx >= MinBarPitch.toPx()) { step = s2; break }
            }
            val lastMs = if (totalDurationMs > 0L) totalDurationMs else Long.MAX_VALUE / 4
            val halfSpanMs = (centerPx / pxPerMsPx).toLong() + step
            val firstTick = (((currentMs - halfSpanMs).coerceAtLeast(0L)) / step) * step
            val lastTick = currentMs + halfSpanMs
            // Build 217 (reference): minor ticks are 1 x 7 dp white at
            // 20%, majors 1.5 x 17 dp white at 34%, all from top 4 dp.
            val tickTop = 3.dp.toPx()
            val majorEvery = step * 5

            var t = firstTick
            while (t <= lastTick && t <= lastMs) {
                val x = centerPx + (t - currentMs) * pxPerMsPx
                if (x >= areaStart && x <= areaEnd) {
                    val major = t % majorEvery == 0L
                    drawLine(
                        color = if (major) RulerTickColor else RulerSubTickColor,
                        start = Offset(x, tickTop),
                        end = Offset(x, tickTop + (if (major) 13.dp.toPx() else 6.dp.toPx())),
                        strokeWidth = if (major) 1.2.dp.toPx() else 1.dp.toPx()
                    )
                }
                t += step
            }

            // Export range (build 199, A10): on white the band is a soft
            // black wash with ink edges (was white-on-black).
            if (exportInMs != null && exportOutMs != null && exportOutMs > exportInMs) {
                val xIn = centerPx + (exportInMs - currentMs) * pxPerMsPx
                val xOut = centerPx + (exportOutMs - currentMs) * pxPerMsPx
                val bandL = maxOf(xIn, areaStart)
                val bandR = minOf(xOut, areaEnd)
                if (bandR > bandL) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.07f),
                        topLeft = Offset(bandL, 0f),
                        size = Size(bandR - bandL, size.height)
                    )
                }
                val edgeW = 1.6.dp.toPx()
                if (xIn >= areaStart && xIn <= areaEnd) {
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(xIn, 0f), Offset(xIn, size.height), edgeW)
                }
                if (xOut >= areaStart && xOut <= areaEnd) {
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(xOut, 0f), Offset(xOut, size.height), edgeW)
                }
            }

            // Beat marks: white at 50%, taller than the ticks.
            if (beatMarksMs.isNotEmpty()) {
                val markW = 2.5.dp.toPx()
                val markH = 15.dp.toPx()
                for (m in beatMarksMs) {
                    val x = centerPx + (m - currentMs) * pxPerMsPx
                    if (x >= areaStart && x <= areaEnd) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.5f),
                            topLeft = Offset(x - markW / 2f, tickTop),
                            size = Size(markW, markH),
                            cornerRadius = CornerRadius(markW / 2f)
                        )
                    }
                }
            }

            if (keyframeTimesMs.isNotEmpty()) {
                val half = 2.5.dp.toPx()
                val cy = 10.dp.toPx()
                for (kt in keyframeTimesMs) {
                    val x = centerPx + (kt - currentMs) * pxPerMsPx
                    if (x < areaStart || x > areaEnd) continue
                    val d = Path().apply {
                        moveTo(x, cy - half)
                        lineTo(x + half, cy)
                        lineTo(x, cy + half)
                        lineTo(x - half, cy)
                        close()
                    }
                    drawPath(d, KeyframeDiamondColor)
                }
            }

            // The CENTRED playhead (build 217, the reference): a WHITE
            // needle (2 x 15 dp, rounded) at the top centre from top
            // 3 dp, and the 2 dp white line resuming BELOW the timecode
            // (top 39 dp) to the ruler's bottom — the layer panel
            // continues it through the tracks. Build 223 (Sarowar's
            // order): the lime square that sat at the needle's top-right
            // is REMOVED.
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(centerPx - 1.dp.toPx(), 2.dp.toPx()),
                size = Size(2.dp.toPx(), 11.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            drawLine(
                Color.White,
                Offset(centerPx, 29.dp.toPx()),
                Offset(centerPx, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 15.dp)
                .testTag("playhead_time_pill"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatTimeCode(playheadMs()),
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

        // The ruler row's bottom hairline (build 223: the family hairline token).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(AmHairline)
        )
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
