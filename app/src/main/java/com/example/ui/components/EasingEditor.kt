package com.example.ui.components
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiGlRuntime
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
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
            // Build 252 (stage 5): the delete action as a GL text box
            // at its old offset (26, 6); the press scales the text.
            val delDensity = LocalDensity.current.density
            val delAtlas = rememberUiAtlas()
            fun resolveDel() = delAtlas.resolve("Delete keyframe", UiLabelStyle.TEXT_10_SEMI)
            var delEntry = resolveDel()
            if (delAtlas.overflowed) {
                delAtlas.reset()
                delEntry = resolveDel()
            }
            val delE = delEntry
            val delScale = remember { androidx.compose.animation.core.Animatable(1f) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 26.dp, y = 6.dp)
                    .width(((delE?.advance ?: 0f) / delDensity).dp)
                    .height((delAtlas.textHeightPx(UiLabelStyle.TEXT_10_SEMI) / delDensity).dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = delAtlas,
                    drawKey = listOf(delE, delScale.value, delAtlas.generation),
                    buildCmds = { w, h, _ ->
                        if (delE != null) {
                            val style = UiLabelStyle.TEXT_10_SEMI
                            val textH = delAtlas.textHeightPx(style)
                            val fm = delAtlas.fontMetrics(style)
                            val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                            val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(0f, baseline, delE.bearing, delE.base, UiLabelAtlas.PAD.toFloat())
                            setScaleAbout(w / 2f, h / 2f, delScale.value)
                            addText(
                                delE.index, dstX, dstY,
                                0f, 0f, delE.rectW.toFloat(), delE.rectH.toFloat(),
                                tintArgb = com.example.ui.theme.AmDanger.toArgb()
                            )
                            clearTransform()
                        }
                    }
                )
                ChromePressBox(
                    delScale, 0.94f, { onDeleteKeyframe() },
                    Modifier.fillMaxSize(),
                    tag = "easing_delete_keyframe",
                    description = "Delete keyframe"
                )
            }
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
                // Build 252 (stage 5): the tile's background folds
                // into its GL canvas as the first command.
                val tileScale = remember { androidx.compose.animation.core.Animatable(1f) }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("easing_preset_$name")
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        drawKey = listOf(preset.x1, preset.y1, preset.x2, preset.y2, selected, tileScale.value),
                        buildCmds = { w, h, d ->
                            addRoundRect(
                                w / 2f, h / 2f, w, h, 9f * d,
                                (if (selected) TileBgSelected else TileBg).toArgb()
                            )
                            setScaleAbout(w / 2f, h / 2f, tileScale.value)
                            setTranslate(9f * d, 9f * d)
                            drawEasingTileCmds(preset, 34f * d, 34f * d, d)
                            clearTransform()
                        }
                    )
                    ChromePressBox(
                        tileScale, 0.92f, {
                            begin()
                            change(preset)
                        },
                        Modifier.fillMaxSize(),
                        description = name
                    )
                }
            }
        }
    }
}

/**
 * Static axis titles ("Time" & rotated [valueLabel]) over the GLES3
 * curve host. Build 252 (stage 5): drawn in GL — "Time" through the
 * text atlas; the rotated label is rasterized INTO an atlas icon
 * slot with the old paint (12 dp, fake bold, −90° about its centre)
 * and blitted unrotated at the rotated position (14 dp, plot centre).
 */
@Composable
private fun EasingAxisLabels(valueLabel: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val labelArgb = 0xFF5B5E63.toInt()
    val axisPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    axisPaint.textSize = 12f * density
    val valueAdv = axisPaint.measureText(valueLabel)
    val valueBoxH = kotlin.math.ceil(axisPaint.fontMetrics.descent - axisPaint.fontMetrics.ascent)
    val slotW = valueBoxH.toInt().coerceAtLeast(1)
    val slotH = kotlin.math.ceil(valueAdv).toInt().coerceAtLeast(1)
    fun resolveAxis(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val time = atlas.resolve("Time", UiLabelStyle.SHEET_ITEM_BOLD)
        val value = atlas.resolveIcon("easing:axis:$valueLabel:$slotW:$slotH", slotW, slotH) { cv, x, y ->
            cv.save()
            cv.translate(x + slotW / 2f, y + slotH / 2f)
            cv.rotate(-90f)
            val p = android.graphics.Paint().apply {
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                color = android.graphics.Color.WHITE
                textSize = 12f * density
            }
            val fm = p.fontMetrics
            cv.drawText(valueLabel, 0f, -(fm.ascent + fm.descent) / 2f, p)
            cv.restore()
        }
        return time to value
    }
    var axisEntries = resolveAxis()
    if (atlas.overflowed) {
        atlas.reset()
        axisEntries = resolveAxis()
    }
    val (timeEntry, valueEntry) = axisEntries

    NativeVectorCanvas(
        modifier = modifier,
        clearArgb = 0,
        atlas = atlas,
        drawKey = listOf(axisEntries, valueLabel, atlas.generation),
        buildCmds = { w, h, dd ->
            val padL = 22f * dd
            val padB = 18f * dd
            val padT = 4f * dd
            val padR = 6f * dd
            val plotW = w - padL - padR
            val plotH = h - padT - padB
            if (timeEntry != null) {
                val centreX = padL + plotW / 2f
                val baseline = h - 2f * dd
                val (dstX, dstY) = EditorGeometry.textDst(
                    centreX - timeEntry.advance / 2f, baseline,
                    timeEntry.bearing, timeEntry.base, pad
                )
                addText(
                    timeEntry.index, dstX, dstY,
                    0f, 0f, timeEntry.rectW.toFloat(), timeEntry.rectH.toFloat(),
                    tintArgb = labelArgb
                )
            }
            if (valueEntry != null) {
                addText(
                    valueEntry.index,
                    14f * dd - slotW / 2f - pad,
                    padT + plotH / 2f - slotH / 2f - pad,
                    0f, 0f, valueEntry.rectW.toFloat(), valueEntry.rectH.toFloat(),
                    tintArgb = labelArgb
                )
            }
        }
    )
}

/** Small picture of a curve (dashed box + curve + its two control points). */
@Composable
private fun EasingTileCanvas(easing: Easing, modifier: Modifier = Modifier) {
    NativeVectorCanvas(
        modifier = modifier,
        drawKey = listOf(easing.x1, easing.y1, easing.x2, easing.y2),
        buildCmds = { width, height, d ->
            drawEasingTileCmds(easing, width, height, d)
        },
    )
}

/** The tile's commands on the shared builder (also folded into the preset tiles). */
private fun com.example.engine.VectorCmdBuilder.drawEasingTileCmds(easing: Easing, width: Float, height: Float, d: Float) {
    val gridArgb = 0xFF3E4045.toInt()
    val handleArgb = 0xFFEA1252.toInt()
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
