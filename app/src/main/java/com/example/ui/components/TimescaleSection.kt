package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectMarkers
import com.example.model.TIMELINE_PX_PER_MS
import com.example.model.TimelineMarker
import com.example.ui.theme.AmBaseDeep
import com.example.ui.theme.AmBarSurface
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
private val MinBarPitch = 8.dp
private val BeatMarkColor = AmCyanBright
/** Named markers (build 188): gold, so they never read as cyan beat marks or the orange playhead bars. */
private val MarkerColor = Color(0xFFFF7A1A) // build 216: named markers are orange dots
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
    /** Named markers (build 188): drawn as gold flags with their name where it fits. */
    markers: List<TimelineMarker> = emptyList(),
    /** Tap on a marker flag: jump the playhead to it. */
    onMarkerTap: ((TimelineMarker) -> Unit)? = null,
    /** Long-press on a marker flag: rename / delete it. */
    onMarkerLongPress: ((TimelineMarker) -> Unit)? = null,
    /** Export range points (build 199, A10): when both are set, the ranged span is banded on the ruler. */
    exportInMs: Long? = null,
    exportOutMs: Long? = null
) {
    val markersNow by rememberUpdatedState(markers)
    val markerTapNow by rememberUpdatedState(onMarkerTap)
    val markerLongNow by rememberUpdatedState(onMarkerLongPress)
    val pxPerMsNow by rememberUpdatedState(pxPerMs)
    val backInset = if (onBack != null) 44.dp else 0.dp
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
                    .width(44.dp)
                    .fillMaxHeight()
                    .bouncyClickable(onClick = onBack)
                    .testTag("panel_back_button"),
                contentAlignment = Alignment.Center
            ) {
                // BARE chevron (boxless order, build 183); the 44 dp hit area is unchanged.
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = IconActive,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── Scrolling bars ─────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onMarkerTap != null || onMarkerLongPress != null) {
                        Modifier.pointerInput(Unit) {
                            // Inverse of the draw mapping below: x -> project time.
                            // A tap / long-press near a marker flag acts on that marker;
                            // anywhere else the ruler stays inert, as before.
                            fun markerAt(pos: Offset): TimelineMarker? {
                                val list = markersNow
                                val pxPerMsPx = pxPerMsNow.dp.toPx()
                                if (list.isEmpty() || pxPerMsPx <= 0f) return null
                                val ms = playheadMs() + ((pos.x - size.width / 2f) / pxPerMsPx).toLong()
                                val tolMs = (20.dp.toPx() / pxPerMsPx).toLong().coerceAtLeast(1L)   // ±20 dp catch (build 201: was ±16)
                                return ProjectMarkers.nearest(list, ms, tolMs)
                            }
                            detectTapGestures(
                                onTap = { pos -> markerAt(pos)?.let { m -> markerTapNow?.invoke(m) } },
                                onLongPress = { pos -> markerAt(pos)?.let { m -> markerLongNow?.invoke(m) } }
                            )
                        }
                    } else Modifier
                )
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
            val tickTop = 3.dp.toPx()
            val tickW = 1.5.dp.toPx()
            val majorEvery = step * 5

            var t = firstTick
            while (t <= lastTick && t <= lastMs) {
                val x = centerPx + (t - currentMs) * pxPerMsPx
                if (x >= areaStart && x <= areaEnd) {
                    val major = t % majorEvery == 0L
                    drawLine(
                        color = if (major) RulerTickColor else RulerSubTickColor,
                        start = Offset(x, tickTop),
                        end = Offset(x, tickTop + (if (major) 14.dp.toPx() else 8.dp.toPx())),
                        strokeWidth = tickW
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
                        color = Color.Black.copy(alpha = 0.06f),
                        topLeft = Offset(bandL, 0f),
                        size = Size(bandR - bandL, size.height)
                    )
                }
                val edgeW = 1.6.dp.toPx()
                if (xIn >= areaStart && xIn <= areaEnd) {
                    drawLine(Color(0xFF18181B), Offset(xIn, 0f), Offset(xIn, size.height), edgeW)
                }
                if (xOut >= areaStart && xOut <= areaEnd) {
                    drawLine(Color(0xFF18181B), Offset(xOut, 0f), Offset(xOut, size.height), edgeW)
                }
            }

            // Beat marks: ink, taller than the ticks (BeatMarkColor).
            if (beatMarksMs.isNotEmpty()) {
                val markW = 3.dp.toPx()
                val markH = 20.dp.toPx()
                for (m in beatMarksMs) {
                    val x = centerPx + (m - currentMs) * pxPerMsPx
                    if (x >= areaStart && x <= areaEnd) {
                        drawRoundRect(
                            color = BeatMarkColor,
                            topLeft = Offset(x - markW / 2f, tickTop),
                            size = Size(markW, markH),
                            cornerRadius = CornerRadius(markW / 2f)
                        )
                    }
                }
            }

            // Named markers (build 216): an ORANGE DOT (7 dp) at the tick
            // line with the name beside it in orange where it fits. The
            // tap / long-press mapping above is unchanged.
            if (markers.isNotEmpty()) {
                val dotR = 3.5.dp.toPx()
                val dotY = tickTop + 7.dp.toPx()
                val namePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0xFF, 0x7A, 0x1A)
                    textSize = 9.dp.toPx()
                    isAntiAlias = true
                }
                for (mk in markers) {
                    val x = centerPx + (mk.timeMs - currentMs) * pxPerMsPx
                    if (x < areaStart || x > areaEnd) continue
                    drawCircle(color = MarkerColor, radius = dotR, center = Offset(x, dotY))
                    if (mk.name.isNotEmpty()) {
                        val nameX = x + dotR + 3.dp.toPx()
                        if (nameX + namePaint.measureText(mk.name) <= areaEnd) {
                            drawContext.canvas.nativeCanvas.drawText(
                                mk.name, nameX, dotY + 3.dp.toPx(), namePaint
                            )
                        }
                    }
                }
            }

            // The CENTRED playhead cap (build 216): a black nub (2.5 x 12 dp)
            // at the top centre with the LIME nub (5 x 7 dp) over its top —
            // the timecode text below it is a composable (see below), and
            // the line itself continues through the tracks in the layer
            // panel (PlayheadColor, now ink).
            drawRoundRect(
                color = Color(0xFF18181B),
                topLeft = Offset(centerPx - 1.25.dp.toPx(), 0f),
                size = Size(2.5.dp.toPx(), 12.dp.toPx()),
                cornerRadius = CornerRadius(1.25.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFC8F91F),
                topLeft = Offset(centerPx - 2.5.dp.toPx(), 0f),
                size = Size(5.dp.toPx(), 7.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        // ── Centred playhead timecode (build 216): 13.5 sp bold tabular,
        // directly under the cap. Only this tiny text recomposes while
        // the playhead moves. ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .testTag("playhead_time_pill"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatTimeCode(playheadMs()),
                color = TextPrimary,
                fontSize = 13.5.sp,
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
                .padding(end = 8.dp, bottom = 5.dp)
                .testTag("total_time_pill"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatClock(totalDurationMs),
                color = AmTextFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
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
