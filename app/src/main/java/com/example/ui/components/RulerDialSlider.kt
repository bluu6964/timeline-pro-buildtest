package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiGlRuntime
import com.example.engine.UiMathNative
import com.example.ui.theme.AmFxAccent
import com.example.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Distance between two ruler ticks. One tick = one [step] of value. */
private val RULER_TICK_SPACING = 8.dp

/** Standard fling feel (Compose default is 1f; a bit higher = a bit shorter glide for precise sliders). */
private const val RULER_FLING_FRICTION = 1.2f

/** Slower releases than this (px/s) just stop, so a careful adjustment never drifts afterwards. */
private const val RULER_MIN_FLING_VELOCITY = 350f

/**
 * The ruler itself: drag to change the value, **one tick = one step** (default 1 unit), and a quick
 * swipe keeps gliding for a natural distance. The ruler follows the finger. Values are always
 * whole steps.
 */
@Composable
fun RulerDial(
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    /**
     * Build 200: the Effects parameter editor's borrowed accent — the needle and
     * thumb disc renders in [AmFxAccent] lime (build 216; the needle itself is ink)
     * layout). Every other host leaves this false and keeps the white needle.
     */
    accentNeedle: Boolean = false
) {
    val currentValue by rememberUpdatedState(value)
    val currentMin by rememberUpdatedState(min)
    val currentMax by rememberUpdatedState(max)
    val currentStep by rememberUpdatedState(step)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()
    val flingJob = remember { arrayOfNulls<Job>(1) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                val velocityTracker = VelocityTracker()
                val tickPx = RULER_TICK_SPACING.toPx()
                val outFling = FloatArray(2)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flingJob[0]?.cancel() // touching the ruler stops any glide
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var lastX = down.position.x
                    var pos = currentValue.coerceIn(currentMin, currentMax)
                    var lastEmitted = pos

                    fun emit(p: Float) {
                        val q = UiMathNative.nativeRulerQuantize(p, currentMin, currentMax, currentStep)
                        if (q != lastEmitted) {
                            lastEmitted = q
                            currentOnValueChange(q)
                        }
                    }

                    // The ruler only takes the touch once the finger moves SIDEWAYS. A mostly vertical move is left alone, so a
                    // panel with several rulers (Effects) can still be scrolled by touching a ruler.
                    var horizontal = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val change = pressed[0]
                        if (!horizontal) {
                            val totalX = change.position.x - down.position.x
                            val totalY = change.position.y - down.position.y
                            if (abs(totalX) <= slop && abs(totalY) <= slop) continue  // not decided yet
                            if (abs(totalY) > abs(totalX)) return@awaitEachGesture   // vertical: not ours
                            horizontal = true
                        }
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - lastX
                        lastX = change.position.x
                        pos = UiMathNative.nativeRulerDragDelta(pos, dx, tickPx, currentMin, currentMax, currentStep)
                        emit(pos)
                    }

                    val vx = velocityTracker.calculateVelocity().x
                    if (abs(vx) > RULER_MIN_FLING_VELOCITY) {
                        val start = pos
                        val velocity = vx / tickPx * currentStep // value units per second
                        flingJob[0] = scope.launch {
                            val t0Ns = System.nanoTime()
                            while (true) {
                                val elapsedSec = androidx.compose.runtime.withFrameNanos { nowNs ->
                                    (nowNs - t0Ns) / 1_000_000_000f
                                }
                                UiMathNative.nativeRulerFlingEval(
                                    start, velocity, elapsedSec,
                                    RULER_FLING_FRICTION, currentStep, currentMin, currentMax,
                                    outFling
                                )
                                val q = outFling[0]
                                if (q != lastEmitted) {
                                    lastEmitted = q
                                    currentOnValueChange(q)
                                }
                                if (outFling[1] > 0.5f) break
                            }
                        }
                    }
                }
            }
    ) {
        NativeGlDirectCanvas(
            modifier = Modifier.fillMaxSize(),
            drawKey = listOf(value, min, max, step, accentNeedle),
            drawOnGl = { sid, density ->
                UiGlRuntime.drawRulerDialOnGl(
                    sid, value, min, max, step, RULER_TICK_SPACING.value * density, accentNeedle, density
                )
            },
        )
    }
}

@Composable
fun RulerDialSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float = 0f,
    max: Float = 200f,
    unit: String = "%",
    isPercentage: Boolean = true,
    labelWidth: Dp = 86.dp,
    step: Float = 1f,
    /** When set, this icon is shown on the left instead of the [label] text. */
    icon: ImageVector? = null,
    /** false = the caller shows the value somewhere else (e.g. a header above the slider). */
    showValue: Boolean = true,
    /** Build 200: magenta needle for the Effects parameter editor (see [RulerDial]). */
    accentNeedle: Boolean = false,
    modifier: Modifier = Modifier
) {
    // The readout must be able to show the value EXACTLY: the decimals follow the step
    // (0.01 / 0.05 / 0.25-stepped values exist — a flat "%.1f" showed 0.25 as "0.3").
    val fmt = when {
        step >= 1f -> "%.0f"
        abs(step * 10f - (step * 10f).roundToInt()) < 0.001f -> "%.1f"
        else -> "%.2f"
    }
    val displayValue = if (unit.isNotEmpty()) {
        String.format(Locale.US, fmt + "%s", value, unit)
    } else if (isPercentage) {
        String.format(Locale.US, fmt + "%%", value)
    } else {
        String.format(Locale.US, fmt, value)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Label on left (e.g., "Width", "Height", "Intensity", "Rotation", "Edge Hardness")
        if (icon != null) {
            Box(modifier = Modifier.width(labelWidth), contentAlignment = Alignment.CenterStart) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(labelWidth)
            )
        }

        // 2. Ruler with the center needle
        RulerDial(
            value = value,
            onValueChange = onValueChange,
            min = min,
            max = max,
            step = step,
            accentNeedle = accentNeedle,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // 3. Current value on the right (e.g., "20%", "45°", "0%") — in a small flat
        // grey pill (the readout format is build 160's step-exact logic, untouched).
        if (showValue) Box(
            modifier = Modifier
                .width(58.dp)
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            FlatPill(Modifier.matchParentSize())
            Text(
                text = displayValue,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
