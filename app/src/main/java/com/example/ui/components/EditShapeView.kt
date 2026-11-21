package com.example.ui.components

import com.example.ui.theme.AmCornerSheet
import com.example.ui.theme.AmCornerCard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.AspectRatio
import com.example.model.ShapeEditValues
import com.example.model.TimelineClip
import com.example.model.at
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.EditorBackground
import com.example.ui.theme.EditorSurface
import com.example.ui.theme.EditorSurfaceVariant
import com.example.ui.theme.IconActive
import com.example.ui.theme.IconDisabled
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlayheadColor
import com.example.ui.theme.RulerSubTickColor
import com.example.ui.theme.RulerTickColor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable

enum class ShapeType(val label: String) {
    WIDE_ROUNDED_RECT("Wide Rect"),
    SQUARE("Square"),
    ROUNDED_SQUARE("Rounded Square"),
    CIRCLE("Circle"),
    OVAL("Oval"),
    PENTAGON("Pentagon"),
    STAR("Star")
}

enum class ShapeEditTab(val title: String) {
    SHAPE("Shape"),
    FILL("Fill"),
    STROKE("Stroke"),
    SHADOW("Shadow"),
    GLOW("Glow")
}

@Composable
fun EditShapeView(
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    currentPlayheadMs: Long = 0L,
    totalDurationMs: Long = 5000L,
    isPlaying: Boolean = false,
    /** Media layers on the canvas now (bottom first) and the player of each video layer. */
    mediaLayers: List<TimelineClip> = emptyList(),
    /** C++ engine of the canvas: it draws the media layers of this editor on its own surface. */
    nativePreview: com.example.engine.NativePreviewEngine,
    onTogglePlay: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onDismiss: () -> Unit,
    onSaveShape: (
        shapeType: ShapeType,
        widthPercent: Float,
        heightPercent: Float,
        fillColor: Color,
        featherPercent: Float,
        offsetX: Float,
        offsetY: Float,
        strokeWidth: Float,
        strokeColor: Color,
        /** Corner radius in dp inside the 130 dp shape box (only used by the rounded rect shapes). */
        cornerRadiusDp: Float,
        /** Stroke trim window as fractions of the outline length (Alight `drawing.progress`). */
        trimStart: Float,
        trimEnd: Float
    ) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Editing an existing shape (build 173 — null = adding a new one): the sheet is prefilled
     * from it, the shape-type picker stays hidden (the type is fixed), and the header button
     * saves instead of adding. The fill colour is prefilled from the layer as it looks at
     * the playhead; every other field from the stored layer, so saving a keyframed layer
     * without touching a value writes no keyframe (see commitKeyframed).
     */
    initialClip: TimelineClip? = null,
    /**
     * The shape's corner radius is keyframed (build 173): the slider stays hidden and the
     * sheet says so, with a chip that replaces the animation by a static radius frozen at
     * the playhead — the build-86 text-reveal decision, same class.
     */
    keyframedCornerRadius: Boolean = false,
    /** Replaces the keyframed radius by a static one frozen at the playhead. */
    onUseStaticRadius: (() -> Unit)? = null,
    /**
     * The shape's stroke trim is keyframed (build 175): same class as the corner radius —
     * the Trim start/end sliders stay hidden and the sheet says so, with a chip that
     * replaces the animation by a static window frozen at the playhead.
     */
    keyframedTrim: Boolean = false,
    /** Replaces the keyframed trim by a static window frozen at the playhead. */
    onUseStaticTrim: (() -> Unit)? = null,
    /** Right button of the header: "Add" when adding, "Save" when editing. */
    saveLabel: String = "Add"
) {
    // Shape Parameters State — prefilled from the edited layer, or the add defaults.
    // The fill colour comes from the layer as it looks at the playhead (what the canvas
    // shows); the corner radius from the stored layer, so a keyframed radius that the
    // hidden slider cannot touch also cannot leak into a save.
    val editing = initialClip != null
    val atPlayhead = initialClip?.at(currentPlayheadMs)
    var selectedShapeType by remember(initialClip?.id) {
        mutableStateOf(initialClip?.let { shapeTypeOf(it.shapePattern) } ?: ShapeType.CIRCLE)
    }
    var activeTab by remember { mutableStateOf(ShapeEditTab.SHAPE) }

    var widthPercent by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.let { it.shapeScaleX * 100f } ?: 100f)
    }
    var heightPercent by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.let { it.shapeScaleY * 100f } ?: 100f)
    }
    var featherPercent by remember { mutableFloatStateOf(0.0f) }

    var fillColor by remember(initialClip?.id) {
        mutableStateOf(atPlayhead?.let { Color(it.textColor) } ?: Color.White)
    }
    var fillOpacity by remember(initialClip?.id) {
        mutableFloatStateOf(atPlayhead?.let { ((it.textColor shr 24) and 0xFF) / 255f * 100f } ?: 100f)
    }

    var strokeWidth by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.strokeWidth ?: 0f)
    }
    var strokeColor by remember(initialClip?.id) {
        mutableStateOf(initialClip?.let { Color(it.strokeColor) } ?: Color.White)
    }

    // New fields from the Alight import (builds 80–81): corner radius for the rounded rect shapes
    // (the preview's built-in corners: 12 dp wide rect, 20 dp rounded square) and the stroke trim
    // window (draw-on). The trim sliders only show while a stroke exists.
    var cornerRadiusDp by remember(initialClip?.id) {
        mutableFloatStateOf(
            initialClip?.let { if (it.shapeCornerRadius >= 0f) it.shapeCornerRadius else 12f } ?: 12f
        )
    }
    var trimStartPercent by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.let { it.strokeTrimStart * 100f } ?: 0f)
    }
    var trimEndPercent by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.let { it.strokeTrimEnd * 100f } ?: 100f)
    }

    var shadowBlur by remember { mutableFloatStateOf(0f) }
    var glowIntensity by remember { mutableFloatStateOf(0f) }

    // Canvas Interactive Shape Transformation
    var shapeOffsetX by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.shapeOffsetX ?: 0f)
    }
    var shapeOffsetY by remember(initialClip?.id) {
        mutableFloatStateOf(initialClip?.shapeOffsetY ?: 0f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmBase) // build 223 (colour balance): the unified background (was build 216's white)
            .testTag("edit_shape_screen")
    ) {
        // 1. Header: Left close button, Center "Edit shape" title, Right "Add" button
        // Build 252 (stage 5): drawn in GL (ShapeHeaderGl).
        ShapeHeaderGl(
            saveLabel = saveLabel,
            onBack = onDismiss,
            onSave = {
                onSaveShape(
                    selectedShapeType,
                    widthPercent,
                    heightPercent,
                    fillColor,
                    featherPercent,
                    shapeOffsetX,
                    shapeOffsetY,
                    strokeWidth,
                    strokeColor,
                    cornerRadiusDp,
                    trimStartPercent / 100f,
                    trimEndPercent / 100f
                )
                onDismiss()
            }
        )

        // 2. Video Preview Canvas with Shape & Coral Pink Bounding Box (Canvas ratio strictly locked)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 0.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            val targetRatio = aspectRatio.ratioFloat

            val (frameW, frameH) = remember(maxW, maxH, targetRatio) {
                if (maxW.value / maxH.value > targetRatio) {
                    (maxH * targetRatio) to maxH
                } else {
                    maxW to (maxW / targetRatio)
                }
            }

            // Centered video container strictly locked to project aspect ratio
            Box(
                modifier = Modifier
                    .size(width = frameW, height = frameH)
                    .clip(androidx.compose.ui.graphics.RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                // Build 252 (stage 5): the frame's flat canvas colour in GL.
                val shapeFrameBgArgb = com.example.ui.theme.LocalCanvasColor.current.toArgb()
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = shapeFrameBgArgb,
                    buildCmds = { w, h, _ ->
                        addRect(0f, 0f, w, h, shapeFrameBgArgb)
                    }
                )
                if (mediaLayers.isNotEmpty()) {
                    MediaLayersStack(
                        layers = mediaLayers,
                        nativePreview = nativePreview,
                        isPlaying = isPlaying
                    )
                } else {
                    // Studio canvas with subtle alignment grid
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        drawKey = "edit_shape_grid",
                        buildCmds = { w, h, d ->
                            addRect(0f, 0f, w, h, CanvasBackground.toArgb())
                            val lineArgb = Color.White.copy(alpha = 0.08f).toArgb()
                            addLine(w / 2f, 0f, w / 2f, h, 1f * d, lineArgb, capRound = false)
                            addLine(0f, h / 2f, w, h / 2f, 1f * d, lineArgb, capRound = false)
                        },
                    )
                }

                // Interactive Shape with Coral Bounding Box
                val baseShapeSize = 135.dp
                val shapeW = baseShapeSize * (widthPercent / 100f)
                val shapeH = baseShapeSize * (heightPercent / 100f)
                val pinkHandleColor = AmAccent // build 223: the handles carry the one lime accent

                Box(
                    modifier = Modifier
                        .offset(x = shapeOffsetX.dp, y = shapeOffsetY.dp)
                        .size(width = shapeW, height = shapeH)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                shapeOffsetX += dragAmount.x / 2.5f
                                shapeOffsetY += dragAmount.y / 2.5f
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Rendered Shape
                    val blurModifier = if (featherPercent > 0f) {
                        Modifier.blur((featherPercent / 8f).dp)
                    } else Modifier

                    NativeVectorCanvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier),
                        drawKey = listOf(
                            selectedShapeType, fillColor, fillOpacity,
                            strokeWidth, strokeColor, cornerRadiusDp,
                            trimStartPercent, trimEndPercent
                        ),
                        buildCmds = { w, h, d ->
                            val fillArgb = fillColor.copy(alpha = fillOpacity / 100f).toArgb()
                            val swPx = strokeWidth * d
                            val trimStart = (trimStartPercent / 100f).coerceIn(0f, 1f)
                            val trimEnd = (trimEndPercent / 100f).coerceIn(0f, 1f)
                            val hasFullStroke = strokeWidth > 0f && trimStart <= 0f && trimEnd >= 1f
                            val hasTrimmedStroke = strokeWidth > 0f && trimEnd > trimStart && !hasFullStroke
                            val strokeArgb = if (hasFullStroke) strokeColor.toArgb() else 0
                            val fullSw = if (hasFullStroke) swPx else 0f

                            var outlinePath: Path? = null
                            when (selectedShapeType) {
                                ShapeType.CIRCLE -> {
                                    addCircle(w / 2f, h / 2f, minOf(w, h) / 2f, fillArgb, fullSw, strokeArgb)
                                    if (hasTrimmedStroke) {
                                        outlinePath = Path().apply {
                                            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                                        }
                                    }
                                }
                                ShapeType.SQUARE, ShapeType.WIDE_ROUNDED_RECT -> {
                                    val cr = if (selectedShapeType == ShapeType.WIDE_ROUNDED_RECT) cornerRadiusDp * d else 0f
                                    addRoundRect(w / 2f, h / 2f, w, h, cr, fillArgb, fullSw, strokeArgb)
                                    if (hasTrimmedStroke) {
                                        outlinePath = Path().apply {
                                            addRoundRect(
                                                androidx.compose.ui.geometry.RoundRect(
                                                    androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                                                    androidx.compose.ui.geometry.CornerRadius(cr, cr)
                                                )
                                            )
                                        }
                                    }
                                }
                                ShapeType.ROUNDED_SQUARE -> {
                                    val cr = cornerRadiusDp * d
                                    addRoundRect(w / 2f, h / 2f, w, h, cr, fillArgb, fullSw, strokeArgb)
                                    if (hasTrimmedStroke) {
                                        outlinePath = Path().apply {
                                            addRoundRect(
                                                androidx.compose.ui.geometry.RoundRect(
                                                    androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                                                    androidx.compose.ui.geometry.CornerRadius(cr, cr)
                                                )
                                            )
                                        }
                                    }
                                }
                                ShapeType.OVAL -> {
                                    addArc(
                                        cx = w / 2f, cy = h / 2f, rx = w / 2f, ry = h / 2f,
                                        startDeg = 0f, sweepDeg = 360f,
                                        width = fullSw, fillArgb = fillArgb, strokeArgb = strokeArgb,
                                        capRound = true, closedPie = false
                                    )
                                    if (hasTrimmedStroke) {
                                        outlinePath = Path().apply {
                                            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                                        }
                                    }
                                }
                                ShapeType.PENTAGON, ShapeType.STAR -> {
                                    val r = minOf(w, h) / 2f
                                    val cX = w / 2f
                                    val cY = h / 2f
                                    val points = 5
                                    val xs = FloatArray(points)
                                    val ys = FloatArray(points)
                                    for (i in 0 until points) {
                                        val angle = Math.toRadians((i * 360.0 / points) - 90.0)
                                        xs[i] = (cX + r * Math.cos(angle)).toFloat()
                                        ys[i] = (cY + r * Math.sin(angle)).toFloat()
                                    }
                                    addFilledPolygon(xs, ys, points, fillArgb)
                                    if (hasFullStroke) {
                                        addPolyline(xs, ys, points, true, fullSw, strokeArgb, capRound = true)
                                    } else if (hasTrimmedStroke) {
                                        outlinePath = Path().apply {
                                            for (i in 0 until points) {
                                                if (i == 0) moveTo(xs[i], ys[i]) else lineTo(xs[i], ys[i])
                                            }
                                            close()
                                        }
                                    }
                                }
                            }

                            val outline = outlinePath
                            if (hasTrimmedStroke && outline != null) {
                                val pm = android.graphics.PathMeasure(outline.asAndroidPath(), false)
                                val totalLen = pm.length
                                if (totalLen > 0f) {
                                    val startD = totalLen * trimStart
                                    val endD = totalLen * trimEnd
                                    val samples = 72
                                    val xs = FloatArray(samples + 1)
                                    val ys = FloatArray(samples + 1)
                                    val pos = FloatArray(2)
                                    for (i in 0..samples) {
                                        val dist = startD + (endD - startD) * (i / samples.toFloat())
                                        pm.getPosTan(dist, pos, null)
                                        xs[i] = pos[0]
                                        ys[i] = pos[1]
                                    }
                                    addPolyline(xs, ys, samples + 1, false, swPx, strokeColor.toArgb(), capRound = false)
                                }
                            }
                        },
                    )

                    // Coral/Pink Bounding Box & 8 Drag Handles (matching screenshot)
                    // Build 252 (stage 5): border + handles drawn in GL
                    // (handle centres sit 5 dp in from the edges, where
                    // the aligned 10 dp boxes put them).
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        drawKey = pinkHandleColor.toArgb(),
                        buildCmds = { w, h, d ->
                            addRoundRect(
                                w / 2f, h / 2f, w - 1.5f * d, h - 1.5f * d, 1.25f * d,
                                0, 1.5f * d, Color.White.copy(alpha = 0.85f).toArgb()
                            )
                            val pink = pinkHandleColor.toArgb()
                            val centres = listOf(
                                5f to 5f, 0.5f to 5f, 1f to 5f, 1f to 0.5f,
                                1f to 1f, 0.5f to 1f, 5f to 1f, 5f to 0.5f
                            )
                            for ((fx, fy) in centres) {
                                val cx = when (fx) {
                                    0.5f -> w / 2f
                                    1f -> w - 5f * d
                                    else -> fx * d
                                }
                                val cy = when (fy) {
                                    0.5f -> h / 2f
                                    1f -> h - 5f * d
                                    else -> fy * d
                                }
                                addCircle(cx, cy, 5f * d - 0.5f * d, pink, 1f * d, Color.White.toArgb())
                            }
                        }
                    )

                    // Bottom-left Grid Anchor Button (matching screenshot)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-16).dp, y = 16.dp)
                            .size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            clearArgb = 0,
                            drawKey = pinkHandleColor,
                            buildCmds = { w, h, d ->
                                addCircle(w / 2f, h / 2f, w / 2f, Color.White.toArgb())
                                val dotRadius = 1.2f * d
                                val spacing = 4f * d
                                val pad = 5f * d
                                val argb = pinkHandleColor.toArgb()
                                for (r in 0..2) {
                                    for (c in 0..2) {
                                        addCircle(c * spacing + pad, r * spacing + pad, dotRadius, argb)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        // 3. Playback Controls & Timescale & Mini Layer Strip (matching screenshot)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            // Controls: Skip Prev, Play/Pause, Skip Next, Undo, Redo
            // Build 252 (stage 5): drawn in GL (ShapeTransportGl —
            // 34 dp lead here, static undo/redo glyphs).
            ShapeTransportGl(
                isPlaying = isPlaying,
                onPrevious = { onSeekTo(0L) },
                onPlayPause = onTogglePlay,
                onNext = { onSeekTo(totalDurationMs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // Timescale from actual project playhead and duration
            // Build 252 (stage 5): GL texts + the native tick strip
            // at its weight slot (ShapeTimecodeGl).
            ShapeTimecodeGl(
                currentText = formatTimeCode(currentPlayheadMs),
                totalText = formatTimeCode(totalDurationMs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp)
            )

            // Real-time Mini Layer Strip
            val progressFraction = if (totalDurationMs > 0L) (currentPlayheadMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .height(24.dp)
            ) {
                // Build 252 (stage 5): the track, colour dot and label in GL.
                ShapeStripGl(
                    fraction = progressFraction.coerceIn(0.2f, 1f),
                    dotArgb = fillColor.copy(alpha = fillOpacity / 100f).toArgb(),
                    label = selectedShapeType.label,
                    modifier = Modifier.fillMaxWidth()
                )

                // Vertical center playhead line
                NativeVectorCanvas(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .align(Alignment.Center),
                    drawKey = "edit_shape_playhead",
                    buildCmds = { _, h, d ->
                        addLine(0f, 0f, 0f, h, 1.5f * d, Color.White.toArgb(), capRound = false)
                    },
                )
            }
        }

        // 4. Bottom Shape Editor Panel (matching screenshot)
        // Build 252 (stage 5): the panel body (AmPanel, sheet
        // corners, the AmTextFaint handle) is drawn in GL; the tab
        // pills are a GlPillStrip (0.92 press here).
        GlPanelBody(
            modifier = Modifier.fillMaxWidth(),
            cornerDp = AmCornerSheet.value,
            fillArgb = AmPanel.toArgb(),
            handleArgb = AmTextFaint.toArgb(),
            handleTopDp = 6f,
            topOnlyCorners = true
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = AmCornerSheet, topEnd = AmCornerSheet))
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp)
        ) {
            // Tab Bar: [Shape] [Fill] [Stroke] [Shadow] [Glow]
            GlPillStrip(
                items = ShapeEditTab.values().map { tab ->
                    GlPillItem(
                        label = tab.title,
                        selected = tab == activeTab,
                        onClick = { activeTab = tab }
                    )
                },
                labelStyle = { sel -> if (sel) UiLabelStyle.ED_TEXT_BOLD else UiLabelStyle.ED_TEXT_MED },
                textArgb = { sel -> if (sel) AmInk.toArgb() else AmTextPrimary.toArgb() },
                fillArgb = { sel -> if (sel) AmAccent.toArgb() else AmPanelElevated.toArgb() },
                borderArgb = { null },
                padHDp = 10f,
                padVDp = 5f,
                gapDp = 6f,
                pressScale = 0.92f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Contents
            when (activeTab) {
                ShapeEditTab.SHAPE -> {
                    // Shape selection row: Label "Shape" + Row of shape cards (matching screenshot).
                    // Editing an existing shape the type is fixed, so the picker stays hidden.
                    if (!editing) {
                    // Build 252 (stage 5): the picker label + cards in
                    // GL; the cards' mini glyphs stay their native
                    // canvases at their exact rects.
                    ShapePickerGl(
                        selected = selectedShapeType,
                        onSelect = { shape ->
                            selectedShapeType = shape
                            // Each rounded shape starts at its built-in corner, as drawn before.
                            cornerRadiusDp = when (shape) {
                                ShapeType.WIDE_ROUNDED_RECT -> 12f
                                ShapeType.ROUNDED_SQUARE -> 20f
                                else -> cornerRadiusDp
                            }
                        }
                    )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Width Dial Slider (matching screenshot: Width, tick ruler, red needle, 100%)
                    RulerDialSlider(
                        label = "Width",
                        value = widthPercent,
                        onValueChange = { widthPercent = it },
                        min = 10f,
                        max = 250f,
                        isPercentage = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Height Dial Slider (matching screenshot: Height, tick ruler, red needle, 100%)
                    RulerDialSlider(
                        label = "Height",
                        value = heightPercent,
                        onValueChange = { heightPercent = it },
                        min = 10f,
                        max = 250f,
                        isPercentage = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Feather Dial Slider (matching screenshot: Feather, tick ruler, red needle, 0.0%)
                    RulerDialSlider(
                        label = "Feather",
                        value = featherPercent,
                        onValueChange = { featherPercent = it },
                        min = 0f,
                        max = 100f,
                        isPercentage = false
                    )

                    // Corner radius (Alight import, build 80): only the rounded rect shapes read it —
                    // the renderers ignore it for SQUARE, CIRCLE and the rest, so it stays hidden there.
                    if (selectedShapeType == ShapeType.WIDE_ROUNDED_RECT || selectedShapeType == ShapeType.ROUNDED_SQUARE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (keyframedCornerRadius) {
                            // Animated corner radius (build 173): the slider would silently mislead —
                            // the value changes over time — so it stays hidden, like the build-86
                            // text-reveal sliders, until an explicit tap freezes it at the playhead.
                            GlTextBlock(
                                text = "Animated corner radius — the radius is keyframed in Alight Motion, so it changes over time.",
                                style = UiLabelStyle.NOTE_15,
                                colorArgb = TextSecondary.toArgb(),
                                align = GlAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ShapeEditChipGl(label = "Use static radius", onClick = {
                                cornerRadiusDp = initialClip?.at(currentPlayheadMs)?.shapeCornerRadius
                                    ?: cornerRadiusDp
                                onUseStaticRadius?.invoke()
                            })
                        } else {
                            RulerDialSlider(
                                label = "Corner radius",
                                value = cornerRadiusDp,
                                onValueChange = { cornerRadiusDp = it },
                                min = 0f,
                                max = 65f,
                                unit = "dp",
                                isPercentage = false,
                                step = 1f
                            )
                        }
                    }
                }

                ShapeEditTab.FILL -> {
                    // Color Palette
                    GlTextBlock(
                        text = "Fill Color",
                        style = UiLabelStyle.ED_TEXT_MED,
                        colorArgb = TextPrimary.toArgb(),
                        align = GlAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShapeSwatchRowGl(
                        colors = listOf(Color.White, Color(0xFFABE41D), Color(0xFFABE41D), Color(0xFFFFEB3B), Color(0xFF00E676), Color(0xFF9C27B0), Color(0xFFFF5722), Color(0xFF1E2129)),
                        selected = fillColor,
                        circleDp = 28f,
                        onPick = { fillColor = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    RulerDialSlider(
                        label = "Opacity",
                        value = fillOpacity,
                        onValueChange = { fillOpacity = it },
                        min = 0f,
                        max = 100f,
                        isPercentage = true
                    )
                }

                ShapeEditTab.STROKE -> {
                    RulerDialSlider(
                        label = "Stroke",
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        min = 0f,
                        max = 30f,
                        isPercentage = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShapeSwatchRowGl(
                        colors = listOf(Color.White, Color(0xFFABE41D), Color(0xFFABE41D), Color(0xFFFFEB3B), Color.Black),
                        selected = strokeColor,
                        circleDp = 26f,
                        onPick = { strokeColor = it }
                    )
                    // Stroke trim (Alight `drawing.progress`, build 81): the visible window of the
                    // outline — 0–100 % draws the whole stroke, a narrower window draws it on.
                    // The sliders clamp against each other so start can never pass end.
                    if (strokeWidth > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (keyframedTrim) {
                            // Animated trim (build 175): the sliders would silently mislead — the
                            // window changes over time — so they stay hidden, like the build-173
                            // corner-radius slider, until an explicit tap freezes it at the playhead.
                            GlTextBlock(
                                text = "Animated stroke trim — the trim is keyframed in Alight Motion, so it changes over time.",
                                style = UiLabelStyle.SHEET_ITEM,
                                colorArgb = TextSecondary.toArgb(),
                                align = GlAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ShapeEditChipGl(label = "Use static trim", onClick = {
                                val (s, e) = initialClip?.let {
                                    ShapeEditValues.staticTrimAt(it, currentPlayheadMs)
                                } ?: (trimStartPercent / 100f to trimEndPercent / 100f)
                                trimStartPercent = s * 100f
                                trimEndPercent = e * 100f
                                onUseStaticTrim?.invoke()
                            })
                        } else {
                            RulerDialSlider(
                                label = "Trim start",
                                value = trimStartPercent,
                                onValueChange = { trimStartPercent = it.coerceIn(0f, trimEndPercent) },
                                min = 0f,
                                max = 100f,
                                isPercentage = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            RulerDialSlider(
                                label = "Trim end",
                                value = trimEndPercent,
                                onValueChange = { trimEndPercent = it.coerceIn(trimStartPercent, 100f) },
                                min = 0f,
                                max = 100f,
                                isPercentage = true
                            )
                        }
                    }
                }

                ShapeEditTab.SHADOW -> {
                    RulerDialSlider(
                        label = "Shadow",
                        value = shadowBlur,
                        onValueChange = { shadowBlur = it },
                        min = 0f,
                        max = 50f,
                        isPercentage = false
                    )
                }

                ShapeEditTab.GLOW -> {
                    RulerDialSlider(
                        label = "Glow",
                        value = glowIntensity,
                        onValueChange = { glowIntensity = it },
                        min = 0f,
                        max = 100f,
                        isPercentage = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // The violet primary pill (build 207 redesign).
            VioletPillButton(
                onClick = {
                    onSaveShape(
                        selectedShapeType,
                        widthPercent,
                        heightPercent,
                        fillColor,
                        featherPercent,
                        shapeOffsetX,
                        shapeOffsetY,
                        strokeWidth,
                        strokeColor,
                        cornerRadiusDp,
                        trimStartPercent / 100f,
                        trimEndPercent / 100f
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (editing) "Save Shape" else "Add Shape to Timeline",
                    color = com.example.ui.theme.AmInk, // build 216: ink on the lime pill
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
        }
    }
}

/**
 * The editor's shape type for a stored [TimelineClip.shapePattern] (build 173, edit
 * prefill): the importer's normalised names ("RECT" / "CIRCLE" / "ROUNDED_RECT") and the
 * app's own enum names. Anything else (PATH shapes, groups of paths) falls back to
 * SQUARE — the picker is hidden while editing, so the type only decides which sliders
 * the sheet shows, and the saved pattern is never rewritten.
 */
private fun shapeTypeOf(pattern: String?): ShapeType = when (pattern?.uppercase()) {
    "CIRCLE" -> ShapeType.CIRCLE
    "RECT", "SQUARE" -> ShapeType.SQUARE
    "ROUNDED_RECT", "ROUNDED_SQUARE" -> ShapeType.ROUNDED_SQUARE
    "WIDE_ROUNDED_RECT" -> ShapeType.WIDE_ROUNDED_RECT
    "OVAL" -> ShapeType.OVAL
    "PENTAGON" -> ShapeType.PENTAGON
    "STAR" -> ShapeType.STAR
    else -> ShapeType.SQUARE
}

/**
 * Build 252 (stage 5): the build-86 text-reveal sheet chip in GL —
 * AmBase capsule, white 13 sp bold label (the fill sat outside the
 * 0.9 press layer; the label scales).
 */
@Composable
private fun ShapeEditChipGl(label: String, onClick: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.CHIP_BIG
    fun resolveChip() = atlas.resolve(label, style)
    var entry = resolveChip()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveChip()
    }
    val e = entry
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    Box(
        modifier = Modifier
            .width(((e?.advance ?: 0f) / density + 32f).dp)
            .height(36.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, ChipBg.toArgb())
                if (e != null) {
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        EditorGeometry.centredOriginPx(w / 2f, e.advance),
                        baseline, e.bearing, e.base, pad
                    )
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = Color.White.toArgb()
                    )
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            scale, 0.9f, onClick,
            Modifier.fillMaxSize(),
            description = label
        )
    }
}

/**
 * Build 252 (stage 5): the shape editor's header in GL — Close box,
 * the span-centred "Edit shape" title, and the NeonCyan save pill
 * (fill static, label press-scales).
 */
@Composable
private fun ShapeHeaderGl(saveLabel: String, onBack: () -> Unit, onSave: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val closePainter = rememberVectorPainter(Icons.Default.Close)
    val closePx = (20f * density).roundToInt()
    fun resolveHeader(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val close = atlas.resolveIcon("shape:close", closePx, closePx) { cv, x, y ->
            val b = rasterizePainterBitmap(closePainter, closePx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val title = atlas.resolve("Edit shape", UiLabelStyle.SCREEN_TITLE)
        val save = atlas.resolve(saveLabel, UiLabelStyle.SHEET_ITEM_BOLD)
        return Triple(close, title, save)
    }
    var headerEntries = resolveHeader()
    if (atlas.overflowed) {
        atlas.reset()
        headerEntries = resolveHeader()
    }
    val (closeEntry, titleEntry, saveEntry) = headerEntries
    val backScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val saveScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val pillWPx = 22f * density + (saveEntry?.advance ?: 0f)
    val pillHPx = atlas.textHeightPx(UiLabelStyle.SHEET_ITEM_BOLD) + 10f * density

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val wPx = maxWidth.value * density
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(headerEntries, backScale.value, saveScale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                if (closeEntry != null) {
                    val cx = 27f * dd
                    setScaleAbout(cx, cy, backScale.value)
                    addText(
                        closeEntry.index,
                        cx - closePx / 2f - pad, cy - closePx / 2f - pad,
                        0f, 0f, closeEntry.rectW.toFloat(), closeEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                val pillLeft = w - 10f * dd - pillWPx
                addRoundRect(
                    pillLeft + pillWPx / 2f, cy, pillWPx, pillHPx, pillHPx / 2f,
                    NeonCyan.toArgb()
                )
                if (titleEntry != null) {
                    val style = UiLabelStyle.SCREEN_TITLE
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val centre = (44f * dd + pillLeft) / 2f
                    val (dstX, dstY) = EditorGeometry.textDst(
                        EditorGeometry.centredOriginPx(centre, titleEntry.advance),
                        baseline, titleEntry.bearing, titleEntry.base, pad
                    )
                    addText(
                        titleEntry.index, dstX, dstY,
                        0f, 0f, titleEntry.rectW.toFloat(), titleEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                if (saveEntry != null) {
                    val style = UiLabelStyle.SHEET_ITEM_BOLD
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        EditorGeometry.centredOriginPx(pillLeft + pillWPx / 2f, saveEntry.advance),
                        baseline, saveEntry.bearing, saveEntry.base, pad
                    )
                    setScaleAbout(pillLeft + pillWPx / 2f, cy, saveScale.value)
                    addText(
                        saveEntry.index, dstX, dstY,
                        0f, 0f, saveEntry.rectW.toFloat(), saveEntry.rectH.toFloat(),
                        tintArgb = EditorBackground.toArgb()
                    )
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            backScale, 0.94f, onBack,
            Modifier
                .offset(x = 10.dp, y = 4.dp)
                .size(34.dp),
            tag = "edit_shape_back_button",
            description = "Cancel"
        )
        ChromePressBox(
            saveScale, 0.94f, onSave,
            Modifier
                .offset(
                    x = ((wPx - pillWPx) / density - 10f).dp,
                    y = ((42f * density - pillHPx) / 2f / density).dp
                )
                .width((pillWPx / density).dp)
                .height((pillHPx / density).dp),
            description = saveLabel
        )
    }
}

/**
 * Build 252 (stage 5): the shape editor's transport row in GL —
 * 34 dp lead spacer, the 118 dp centre group (24 dp play glyph),
 * and the static undo / redo glyphs (not buttons here).
 */
@Composable
private fun ShapeTransportGl(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val prevPainter = rememberVectorPainter(Icons.Default.SkipPrevious)
    val nextPainter = rememberVectorPainter(Icons.Default.SkipNext)
    val playPainter = rememberVectorPainter(Icons.Default.PlayArrow)
    val pausePainter = rememberVectorPainter(Icons.Default.Pause)
    val undoPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Undo)
    val redoPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Redo)
    val smallPx = (19f * density).roundToInt()
    val playPx = (24f * density).roundToInt()
    val undoPx = (17f * density).roundToInt()
    fun resolveTransport(): List<UiLabelAtlas.Entry?> {
        fun icon(key: String, p: androidx.compose.ui.graphics.vector.VectorPainter, px: Int) =
            atlas.resolveIcon(key, px, px) { cv, x, y ->
                val b = rasterizePainterBitmap(p, px, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        return listOf(
            icon("shape:prev", prevPainter, smallPx),
            icon("shape:next", nextPainter, smallPx),
            icon("shape:play", playPainter, playPx),
            icon("shape:pause", pausePainter, playPx),
            icon("shape:undo", undoPainter, undoPx),
            icon("shape:redo", redoPainter, undoPx)
        )
    }
    var transportEntries = resolveTransport()
    if (atlas.overflowed) {
        atlas.reset()
        transportEntries = resolveTransport()
    }
    val (prevEntry, nextEntry, playEntry, pauseEntry, undoEntry, redoEntry) = transportEntries
    val scales = remember { List(3) { androidx.compose.animation.core.Animatable(1f) } }

    Box(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val wPx = maxWidth.value * density
            val gapPx = (wPx - (34f + 118f + 62f) * density) / 2f
            val centreLeft = 34f * density + gapPx
            val undoLeft = wPx - 62f * density
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(transportEntries, scales.map { it.value }, isPlaying, atlas.generation),
                buildCmds = { w, h, dd ->
                    val cy = h / 2f
                    fun blit(e: UiLabelAtlas.Entry?, cx: Float, px: Int, scale: Float?) {
                        if (e == null) return
                        if (scale != null) setScaleAbout(cx, cy, scale)
                        addText(
                            e.index,
                            cx - px / 2f - pad, cy - px / 2f - pad,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = IconActive.toArgb()
                        )
                        if (scale != null) clearTransform()
                    }
                    blit(prevEntry, centreLeft + 15f * dd, smallPx, scales[0].value)
                    blit(if (isPlaying) pauseEntry else playEntry, centreLeft + 59f * dd, playPx, scales[1].value)
                    blit(nextEntry, centreLeft + 103f * dd, smallPx, scales[2].value)
                    blit(undoEntry, undoLeft + 14f * dd, undoPx, null)
                    blit(redoEntry, undoLeft + 48f * dd, undoPx, null)
                }
            )
            ChromePressBox(
                scales[0], 0.94f, onPrevious,
                Modifier
                    .offset(x = (centreLeft / density).dp, y = 2.dp)
                    .size(30.dp),
                description = "Prev"
            )
            ChromePressBox(
                scales[1], 0.94f, onPlayPause,
                Modifier
                    .offset(x = ((centreLeft + 42f * density) / density).dp)
                    .size(34.dp),
                description = "Play/Pause"
            )
            ChromePressBox(
                scales[2], 0.94f, onNext,
                Modifier
                    .offset(x = ((centreLeft + 88f * density) / density).dp, y = 2.dp)
                    .size(30.dp),
                description = "Next"
            )
        }
    }
}

/**
 * Build 252 (stage 5): the shape editor's timecode row — GL texts,
 * the native tick strip at its 0.38 / 0.45 weight slot.
 */
@Composable
private fun ShapeTimecodeGl(
    currentText: String,
    totalText: String,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    fun resolveTc(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val cur = atlas.resolve(currentText, UiLabelStyle.TIMECODE_MONO)
        val sep = atlas.resolve("  |  ", UiLabelStyle.SHEET_DESC)
        val tot = atlas.resolve(totalText, UiLabelStyle.TIMECODE_MONO_MED)
        return Triple(cur, sep, tot)
    }
    var tcEntries = resolveTc()
    if (atlas.overflowed) {
        atlas.reset()
        tcEntries = resolveTc()
    }
    val (curEntry, sepEntry, totEntry) = tcEntries
    val rowHDp = (atlas.textHeightPx(UiLabelStyle.TIMECODE_MONO) / density).dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHDp)
    ) {
        val wPx = maxWidth.value * density
        val curAdv = curEntry?.advance ?: 0f
        val sepAdv = sepEntry?.advance ?: 0f
        val totAdv = totEntry?.advance ?: 0f
        val flex = wPx - curAdv - sepAdv - totAdv - 6f * density
        val spacerW = flex * (0.38f / 0.83f)
        val ticksW = flex * (0.45f / 0.83f)
        val ticksX = spacerW + curAdv + sepAdv + totAdv + 6f * density
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(tcEntries, atlas.generation),
            buildCmds = { w, h, _ ->
                fun line(e: UiLabelAtlas.Entry?, style: UiLabelStyle, originX: Float, tint: Int) {
                    if (e == null) return
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = tint
                    )
                }
                line(curEntry, UiLabelStyle.TIMECODE_MONO, spacerW, TextPrimary.toArgb())
                line(sepEntry, UiLabelStyle.SHEET_DESC, spacerW + curAdv, TextSecondary.toArgb())
                line(totEntry, UiLabelStyle.TIMECODE_MONO_MED, spacerW + curAdv + sepAdv, TextSecondary.toArgb())
            }
        )
        NativeVectorCanvas(
            modifier = Modifier
                .offset(x = (ticksX / density).dp, y = ((rowHDp.value - 11f) / 2f).dp)
                .width((ticksW / density).dp)
                .height(11.dp),
            drawKey = "edit_shape_ruler",
            buildCmds = { w, h, d ->
                val tickSpacing = 7f * d
                val count = (w / tickSpacing).toInt()
                val majorArgb = RulerTickColor.toArgb()
                val subArgb = RulerSubTickColor.toArgb()
                for (i in 0..count) {
                    val isMajor = (i % 5 == 0)
                    val x = i * tickSpacing
                    addLine(x, 0f, x, if (isMajor) h else h * 0.5f, 1f * d, if (isMajor) majorArgb else subArgb, capRound = false)
                }
            }
        )
    }
}

/**
 * Build 252 (stage 5): the shape editor's mini strip track in GL —
 * the raised track at the progress fraction's width, the flat
 * colour dot and the shape-type label.
 */
@Composable
private fun ShapeStripGl(
    fraction: Float,
    dotArgb: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    fun resolveStrip() = atlas.resolve(label, UiLabelStyle.SHAPE_STRIP_LABEL)
    var labelEntry = resolveStrip()
    if (atlas.overflowed) {
        atlas.reset()
        labelEntry = resolveStrip()
    }
    val e = labelEntry
    BoxWithConstraints(modifier = modifier.height(24.dp)) {
        val trackW = maxWidth.value * density * fraction
        NativeVectorCanvas(
            modifier = Modifier
                .width((trackW / density).dp)
                .height(22.dp),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, dotArgb, fraction, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, 6f * dd, AmPanelElevated.toArgb())
                addCircle(13f * dd, h / 2f, 7f * dd, dotArgb)
                if (e != null) {
                    val style = UiLabelStyle.SHAPE_STRIP_LABEL
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(25f * dd, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
            }
        )
    }
}

/**
 * Build 252 (stage 5): the shape-type picker in GL — the "Shape"
 * label and the seven 32 dp cards (fills + selection rings); the
 * cards' mini glyphs stay their native canvases at their rects,
 * press-scaling as the card content did.
 */
@Composable
private fun ShapePickerGl(selected: ShapeType, onSelect: (ShapeType) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    fun resolveLabel() = atlas.resolve("Shape", UiLabelStyle.SHEET_ITEM_MED)
    var labelEntry = resolveLabel()
    if (atlas.overflowed) {
        atlas.reset()
        labelEntry = resolveLabel()
    }
    val labelE = labelEntry
    val shapes = ShapeType.values()
    val scales = remember { shapes.map { androidx.compose.animation.core.Animatable(1f) } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(32.dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(labelE, atlas.generation),
                buildCmds = { w, h, _ ->
                    if (labelE != null) {
                        val style = UiLabelStyle.SHEET_ITEM_MED
                        val textH = atlas.textHeightPx(style)
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(0f, baseline, labelE.bearing, labelE.base, pad)
                        addText(
                            labelE.index, dstX, dstY,
                            0f, 0f, labelE.rectW.toFloat(), labelE.rectH.toFloat(),
                            tintArgb = TextPrimary.toArgb()
                        )
                    }
                }
            )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier
                    .width((shapes.size * 32 + (shapes.size - 1) * 6).dp)
                    .height(32.dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = selected,
                    buildCmds = { _, _, dd ->
                        shapes.forEachIndexed { i, shape ->
                            val isSel = shape == selected
                            val left = i * 38f * dd
                            val cx = left + 16f * dd
                            val cy = 16f * dd
                            addRoundRect(
                                cx, cy, 32f * dd, 32f * dd, 9f * dd,
                                (if (isSel) AmHairlineStrong else AmCardSurface).toArgb()
                            )
                            val bw = (if (isSel) 1.5f else 1f) * dd
                            addRoundRect(
                                cx, cy, 32f * dd - bw, 32f * dd - bw, 9f * dd - bw / 2f,
                                0, bw, (if (isSel) Color.White else AmTextFaint).toArgb()
                            )
                        }
                    }
                )
                shapes.forEachIndexed { i, shape ->
                    Box(
                        modifier = Modifier
                            .offset(x = (i * 38).dp)
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = scales[i].value
                                    scaleY = scales[i].value
                                },
                            drawKey = shape,
                            buildCmds = { w, h, _ ->
                                val cArgb = Color.White.toArgb()
                                when (shape) {
                                    ShapeType.CIRCLE, ShapeType.PENTAGON, ShapeType.STAR ->
                                        addCircle(w / 2f, h / 2f, minOf(w, h) / 2f, cArgb)
                                    ShapeType.SQUARE ->
                                        addRect(0f, 0f, w, h, cArgb)
                                    ShapeType.WIDE_ROUNDED_RECT ->
                                        addRoundRect(w / 2f, h / 2f, w, h, 4f, cArgb)
                                    ShapeType.ROUNDED_SQUARE ->
                                        addRoundRect(w / 2f, h / 2f, w, h, 8f, cArgb)
                                    ShapeType.OVAL ->
                                        addArc(w / 2f, h / 2f, w / 2f, h / 2f, 0f, 360f, fillArgb = cArgb)
                                }
                            },
                        )
                    }
                    ChromePressBox(
                        scales[i], 0.94f, { onSelect(shape) },
                        Modifier
                            .offset(x = (i * 38).dp)
                            .size(32.dp),
                        description = shape.label
                    )
                }
            }
        }
    }
}

/**
 * Build 252 (stage 5): a fill / stroke swatch row in GL — flat
 * colour circles with the white selection ring. (The Compose boxes
 * had no content after their press layer, so nothing scaled.)
 */
@Composable
private fun ShapeSwatchRowGl(
    colors: List<Color>,
    selected: Color,
    circleDp: Float,
    onPick: (Color) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scales = remember { colors.map { androidx.compose.animation.core.Animatable(1f) } }
    Box(
        modifier = Modifier
            .width((colors.size * circleDp + (colors.size - 1) * 8f).dp)
            .height(circleDp.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(selected, colors),
            buildCmds = { _, _, dd ->
                colors.forEachIndexed { i, col ->
                    val cx = (i * (circleDp + 8f) + circleDp / 2f) * dd
                    val cy = circleDp / 2f * dd
                    val r = circleDp / 2f * dd
                    if (col == selected) {
                        addCircle(cx, cy, r - 0.75f * dd, col.toArgb(), 1.5f * dd, Color.White.toArgb())
                    } else {
                        addCircle(cx, cy, r, col.toArgb())
                    }
                }
            }
        )
        colors.forEachIndexed { i, col ->
            ChromePressBox(
                scales[i], 0.94f, { onPick(col) },
                Modifier
                    .offset(x = (i * (circleDp + 8f)).dp)
                    .size(circleDp.dp),
                description = null
            )
        }
    }
}
