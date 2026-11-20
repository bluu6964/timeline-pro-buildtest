package com.example.ui.components
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiGlRuntime
import com.example.model.Easing
import com.example.ui.util.bouncyClickable
import kotlin.math.hypot

private val HandleColor = Color(0xFFEA1252)
private val TileBg = Color(0xFF2E3033)
private val TileBgSelected = Color(0xFF3F4144)
private val GridColor = Color(0xFF4A4C50)

/** The graph shows y from [Y_MIN] to [Y_MAX] so overshooting ("back") curves stay visible. */
private const val Y_MIN = -0.3f
private const val Y_MAX = 1.3f

/**
 * Easing curve editor (Alight Motion style) for the segment between two keyframes.
 * Left: the curve with two draggable control points and a vertical line for where the playhead is
 * inside the segment. Right: ready-made curves to tap.
 *
 * [onBegin] is called once when a change starts (one undo step), [onChange] on every change.
 */
@Composable
fun EasingEditor(
    easing: Easing,
    progress: Float,
    onBegin: () -> Unit,
    onChange: (Easing) -> Unit,
    modifier: Modifier = Modifier,
    /** false = only the curve graph (the caller shows its own ready-made curves). */
    showPresets: Boolean = true,
    /** Label of the vertical axis. */
    valueLabel: String = "Transform",
    /**
     * Delete-keyframe affordance (build 199, A5): when the host edits a real keyframe
     * segment, this shows a small "Delete keyframe" action over the graph — it deletes the
     * segment's START key (the key this editor's curve belongs to). Hosts that edit a value
     * without a single owning key leave it null and the editor is exactly as before.
     */
    onDeleteKeyframe: (() -> Unit)? = null
) {
    val curEasing by rememberUpdatedState(easing)
    val begin by rememberUpdatedState(onBegin)
    val change by rememberUpdatedState(onChange)

    Row(
        modifier = modifier.fillMaxSize().testTag("easing_editor"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- Curve graph ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
        val density = LocalDensity.current.density
        val padL = 22f * density
        val padB = 18f * density
        val padT = 4f * density
        val padR = 6f * density
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp)
        ) {
            val e = easing
            val p = progress
            NativeGlTextureHost(
                isOpaque = false,
                drawOnGl = { sid ->
                    UiGlRuntime.drawEasingGraphOnGl(
                        sid, e.x1, e.y1, e.x2, e.y2, p, density, padL, padT, padR, padB
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
            EasingAxisLabels(valueLabel = valueLabel, modifier = Modifier.fillMaxSize())
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val pL = 22.dp.toPx()
                        val pB = 18.dp.toPx()
                        val pT = 4.dp.toPx()
                        val pR = 6.dp.toPx()
                        fun plot() = androidx.compose.ui.geometry.Rect(
                            pL, pT, size.width - pR, size.height - pB
                        )
                        fun toX(px: Float): Float {
                            val r = plot()
                            return ((px - r.left) / r.width).coerceIn(0f, 1f)
                        }
                        fun toY(py: Float): Float {
                            val r = plot()
                            val v = Y_MIN + (r.bottom - py) / r.height * (Y_MAX - Y_MIN)
                            return v.coerceIn(Y_MIN, Y_MAX)
                        }
                        var active = 0 // 1 = first handle, 2 = second handle
                        detectDragGestures(
                            onDragStart = { pos ->
                                val r = plot()
                                val e = curEasing
                                fun sx(x: Float) = r.left + x * r.width
                                fun sy(y: Float) = r.bottom - (y - Y_MIN) / (Y_MAX - Y_MIN) * r.height
                                val d1 = hypot(pos.x - sx(e.x1), pos.y - sy(e.y1))
                                val d2 = hypot(pos.x - sx(e.x2), pos.y - sy(e.y2))
                                val reach = 44.dp.toPx()
                                active = when {
                                    d1 > reach && d2 > reach -> 0
                                    d1 <= d2 -> 1
                                    else -> 2
                                }
                                if (active != 0) begin()
                            },
                            onDrag = { change0, _ ->
                                if (active == 0) return@detectDragGestures
                                change0.consume()
                                val e = curEasing
                                val nx = toX(change0.position.x)
                                val ny = toY(change0.position.y)
                                change(if (active == 1) e.copy(x1 = nx, y1 = ny) else e.copy(x2 = nx, y2 = ny))
                            },
                            onDragEnd = { active = 0 },
                            onDragCancel = { active = 0 }
                        )
                    }
            )
        }
        if (onDeleteKeyframe != null) {
            androidx.compose.material3.Text(
                text = "Delete keyframe",
                color = com.example.ui.theme.AmDanger,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 26.dp, top = 6.dp)
                    .bouncyClickable { onDeleteKeyframe() }
                    .testTag("easing_delete_keyframe")
            )
        }
        }

        // ---- Ready-made curves ----
        if (showPresets) LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.width(120.dp).fillMaxHeight().padding(start = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            items(Easing.PRESETS) { (name, preset) ->
                val selected = preset.sameAs(easing)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) TileBgSelected else TileBg, RoundedCornerShape(9.dp))
                        .bouncyClickable(pressedScale = 0.92f, onClick = {
                            begin()
                            change(preset)
                        })
                        .testTag("easing_preset_$name"),
                    contentAlignment = Alignment.Center
                ) {
                    EasingTileCanvas(preset, Modifier.size(34.dp))
                }
            }
        }
    }
}

/** Static axis titles ("Time" & rotated [valueLabel]) over the GLES3 curve host; skips recomposition during drags. */
@Composable
private fun EasingAxisLabels(valueLabel: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val padL = 22.dp.toPx()
        val padB = 18.dp.toPx()
        val padT = 4.dp.toPx()
        val padR = 6.dp.toPx()
        val left = padL
        val top = padT
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        drawIntoCanvas { cv ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = 0xFF5B5E63.toInt()
                textSize = 12.dp.toPx()
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            cv.nativeCanvas.drawText("Time", left + w / 2f, size.height - 2.dp.toPx(), paint)
            cv.nativeCanvas.save()
            cv.nativeCanvas.rotate(-90f, 10.dp.toPx(), top + h / 2f)
            cv.nativeCanvas.drawText(valueLabel, 10.dp.toPx(), top + h / 2f + 4.dp.toPx(), paint)
            cv.nativeCanvas.restore()
        }
    }
}

/** Small picture of a curve (dashed box + curve + its two control points). */
@Composable
private fun EasingTileCanvas(easing: Easing, modifier: Modifier = Modifier) {
    val gridArgb = 0xFF3E4045.toInt()
    val handleArgb = 0xFFEA1252.toInt()
    NativeVectorCanvas(
        modifier = modifier,
        drawKey = listOf(easing.x1, easing.y1, easing.x2, easing.y2),
        buildCmds = { width, height, d ->
            val pad = 3f * d
            val w = width - 2f * pad
            val h = height - 2f * pad
            fun sx(x: Float) = pad + x * w
            fun sy(y: Float) = pad + (1f - y) * h
            val dashOn = 3f * d
            addPolyline(
                floatArrayOf(pad, pad + w, pad + w, pad),
                floatArrayOf(pad, pad, pad + h, pad + h),
                4, closed = true, width = 1.5f * d, argb = gridArgb, dashOn = dashOn, dashOff = dashOn, capRound = false
            )
            addCubic(
                sx(0f), sy(0f),
                sx(easing.x1), sy(easing.y1),
                sx(easing.x2), sy(easing.y2),
                sx(1f), sy(1f),
                width = 2f * d, argb = android.graphics.Color.WHITE, capRound = true
            )
            addCircle(sx(easing.x1), sy(easing.y1), 2.6f * d, handleArgb)
            addCircle(sx(easing.x2), sy(easing.y2), 2.6f * d, handleArgb)
        },
    )
}

/** The easing curve as a path; [map] turns a (time, progress) point into a pixel. */
private fun curvePath(e: Easing, steps: Int, map: (Float, Float) -> Offset): Path {
    val path = Path()
    for (i in 0..steps) {
        val (x, y) = e.pointAt(i / steps.toFloat())
        val o = map(x, y)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    return path
}

/** The little curve icon of the header: draws the given easing (or an S-curve when there is none). */
fun DrawScope.drawEasingIcon(easing: Easing?, color: Color, strokeWidth: Float) {
    val e = easing ?: Easing(0.4f, 0f, 0.6f, 1f)
    val pad = strokeWidth
    val w = size.width - 2 * pad
    val h = size.height - 2 * pad
    val path = curvePath(e, 24) { x, y ->
        Offset(pad + x * w, pad + (1f - y.coerceIn(-0.2f, 1.2f).let { (it + 0.2f) / 1.4f * 1f }) * h)
    }
    drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
