package com.example.ui.components
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.engine.UiGlRuntime
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.UiMathNative
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.AnimProp
import com.example.model.AspectRatio
import com.example.model.Easing
import com.example.model.indexOnTime
import com.example.model.segmentIndexAt
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmInk
import com.example.ui.theme.IconDisabled
import com.example.ui.theme.AmCornerPanel
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmVioletDeep
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.EditorBackground
import com.example.ui.theme.IconActive
import com.example.ui.theme.RulerSubTickColor
import com.example.ui.theme.RulerTickColor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable
import kotlin.math.roundToInt

val CROP_PRESETS = listOf("Free", "Original", "Project", "9:16", "16:9", "1:1", "4:5", "21:9", "4:3")

/** Same screen proportions as the main editor, so Crop looks like the other editor panels. */
class CropChrome(
    val headerH: Dp,
    val canvasH: Dp,
    val playControlsH: Dp,
    val timescaleH: Dp,
    val layerRow: @Composable () -> Unit,
    val onFullScreen: () -> Unit,
    val onResolution: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit
)

@Composable
fun CropView(
    clip: TimelineClip?,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    currentPlayheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    /** Player of each video layer (the edited layer has its own one). */
    /** C++ engine of the canvas: it draws the media layers of this editor on its own surface. */
    nativePreview: com.example.engine.NativePreviewEngine,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDismiss: () -> Unit,
    onApplyCrop: (
        cropWidth: Float,
        cropHeight: Float,
        cropHorizontal: Float,
        cropVertical: Float,
        preset: String
    ) -> Unit,
    modifier: Modifier = Modifier,
    chrome: CropChrome? = null,
    /** Diamond: add / remove a crop keyframe (size + position) at the playhead. */
    onToggleKeyframes: (List<AnimProp>) -> Unit = {},
    /** Easing editor: a change is about to start (one undo step) / the curve of the segment starting at the keyframe time changed. */
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> }
) {
    var activePreset by remember(clip?.id) { mutableStateOf(clip?.cropPreset ?: "Free") }

    var widthPercent by remember(clip?.id) { mutableFloatStateOf(clip?.cropWidthPercent ?: 100.0f) }
    var heightPercent by remember(clip?.id) { mutableFloatStateOf(clip?.cropHeightPercent ?: 100.0f) }
    var horizontalPercent by remember(clip?.id) { mutableFloatStateOf(clip?.cropHorizontalPercent ?: 50.0f) }
    var verticalPercent by remember(clip?.id) { mutableFloatStateOf(clip?.cropVerticalPercent ?: 50.0f) }

    // Keep local state in sync with the clip (covers clip switch and undo/redo).
    LaunchedEffect(
        clip?.id, clip?.cropWidthPercent, clip?.cropHeightPercent,
        clip?.cropHorizontalPercent, clip?.cropVerticalPercent, clip?.cropPreset
    ) {
        if (clip != null) {
            widthPercent = clip.cropWidthPercent
            heightPercent = clip.cropHeightPercent
            horizontalPercent = clip.cropHorizontalPercent
            verticalPercent = clip.cropVerticalPercent
            activePreset = clip.cropPreset
        }
    }

    // Shape and text layers are cropped on their own box (not on the whole canvas).
    val isShapeLayer = clip?.type == LayerType.SHAPE
    val isTextLayer = clip?.type == LayerType.TEXT
    var textBoxPx by remember(clip?.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val srcAspect = when {
        isShapeLayer -> 1f
        isTextLayer && textBoxPx.width > 0 && textBoxPx.height > 0 -> textBoxPx.width.toFloat() / textBoxPx.height
        isTextLayer -> 3f
        else -> clip?.sourceAspect?.takeIf { it > 0f } ?: aspectRatio.ratioFloat
    }
    val minSize = 0.1f

    // ---- Keyframe / easing state of the crop (box size + box position) ----
    val cropProps = remember { listOf(AnimProp.CROP_SIZE, AnimProp.CROP_POS) }
    val kfMap = clip?.keyframes.orEmpty()
    val relMs = currentPlayheadMs - (clip?.startMs ?: 0L)
    val insideClip = clip != null && currentPlayheadMs >= clip.startMs && currentPlayheadMs <= clip.endMs
    val onKeyframeNow = cropProps.any { kfMap[it].orEmpty().indexOnTime(relMs) >= 0 }
    val hasKeyframes = cropProps.any { kfMap[it].orEmpty().isNotEmpty() }
    val easingProp = cropProps.firstOrNull { kfMap[it].orEmpty().segmentIndexAt(relMs) >= 0 }
    val kfList = easingProp?.let { kfMap[it].orEmpty() }.orEmpty()
    val segIdx = if (easingProp != null) kfList.segmentIndexAt(relMs) else -1
    val easingEnabled = segIdx >= 0
    var showEasing by remember { mutableStateOf(false) }
    LaunchedEffect(easingEnabled) { if (!easingEnabled) showEasing = false }
    val segStart = if (segIdx >= 0) kfList[segIdx] else null
    val segEnd = if (segIdx >= 0) kfList[segIdx + 1] else null

    fun emitCrop() {
        onApplyCrop(widthPercent, heightPercent, horizontalPercent, verticalPercent, activePreset)
    }

    fun rl() = (1f - widthPercent / 100f) * horizontalPercent / 100f
    fun rt() = (1f - heightPercent / 100f) * verticalPercent / 100f
    fun rr() = rl() + widthPercent / 100f
    fun rb() = rt() + heightPercent / 100f

    fun setRect(l: Float, t: Float, r: Float, b: Float) {
        val w = (r - l).coerceIn(minSize, 1f)
        val h = (b - t).coerceIn(minSize, 1f)
        val nl = l.coerceIn(0f, 1f - w)
        val nt = t.coerceIn(0f, 1f - h)
        widthPercent = w * 100f
        heightPercent = h * 100f
        horizontalPercent = if (1f - w > 0.0001f) (nl / (1f - w) * 100f).coerceIn(0f, 100f) else 50f
        verticalPercent = if (1f - h > 0.0001f) (nt / (1f - h) * 100f).coerceIn(0f, 100f) else 50f
    }

    /** Fractional (of the source) width/height ratio the box must keep, or null for Free. */
    fun lockK(preset: String): Float? {
        val kind: Int
        var num = 0f
        var den = 0f
        when (preset) {
            "Free" -> return null
            "Original" -> kind = UiMathNative.CROP_PRESET_ORIGINAL
            "Project" -> kind = UiMathNative.CROP_PRESET_PROJECT
            else -> {
                val parts = preset.split(":")
                val x = parts.getOrNull(0)?.toFloatOrNull()
                val y = parts.getOrNull(1)?.toFloatOrNull()
                if (x == null || y == null || y <= 0f) return null
                kind = UiMathNative.CROP_PRESET_CUSTOM
                num = x
                den = y
            }
        }
        val k = UiMathNative.nativeCropLockK(kind, num, den, srcAspect, aspectRatio.ratioFloat)
        return if (k > 1e-5f) k else null
    }

    fun applyPresetRatio(preset: String) {
        activePreset = preset
        val k = lockK(preset)
        if (k != null) {
            val w: Float
            val h: Float
            if (k >= 1f) { w = 1f; h = 1f / k } else { h = 1f; w = k }
            setRect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
        }
        emitCrop()
    }

    /** Resize around the current center (used by the Width / Height sliders). */
    fun resizeAroundCenter(newW: Float?, newH: Float?) {
        val k = lockK(activePreset)
        val out = FloatArray(4)
        UiMathNative.nativeCropResizeCenter(
            rr() - rl(), rb() - rt(), rl(), rt(), rr(), rb(),
            newW ?: -1f, newH ?: -1f, k ?: 0f, minSize, out
        )
        widthPercent = out[0]
        heightPercent = out[1]
        horizontalPercent = out[2]
        verticalPercent = out[3]
        emitCrop()
    }

    // dx/dy are fractions of the source size. mode: 0=move, else bit flags 1=left 2=right 4=top 8=bottom
    fun dragCrop(mode: Int, dx: Float, dy: Float) {
        val k = lockK(activePreset)
        val out = FloatArray(4)
        UiMathNative.nativeCropDrag(mode, dx, dy, rl(), rt(), rr(), rb(), k ?: 0f, minSize, out)
        widthPercent = out[0]
        heightPercent = out[1]
        horizontalPercent = out[2]
        verticalPercent = out[3]
        emitCrop()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmBase) // build 223 (colour balance): the unified background (was build 216's white; the canvas keeps its own dim)
            .then(
                // Swipe down on the tools area (below the crop canvas) = back.
                if (chrome != null) Modifier.swipeDownToDismiss(
                    ignoreAbove = chrome.headerH + chrome.canvasH
                ) {
                    emitCrop()
                    onDismiss()
                } else Modifier
            )
            .testTag("crop_screen")
    ) {
        if (chrome != null) {
            TransformHeaderBar(
                height = chrome.headerH,
                title = "Crop",
                onReset = {
                    activePreset = "Free"
                    setRect(0f, 0f, 1f, 1f)
                    emitCrop()
                }
            )
        } else {
        // 1. Top Header: Down Arrow, "Crop", Done
        // Build 252 (stage 5): the standalone header is drawn in GL —
        // back icon, the centred title (its centre is the midpoint of
        // the free span, as the two weight spacers had it), and the
        // white Done pill (fill static, label press-scales).
        CropHeaderGl(
            onBack = {
                emitCrop()
                onDismiss()
            }
        )

        }

        // 2. Interactive crop area: the box sits on the media itself (not the canvas frame).
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (chrome != null) Modifier.height(chrome.canvasH) else Modifier.weight(1f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
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
            val frameRatio = frameW.value / frameH.value
            val density = androidx.compose.ui.platform.LocalDensity.current
            val contentW = when {
                isShapeLayer -> 140.dp
                isTextLayer && textBoxPx.width > 0 -> with(density) { textBoxPx.width.toDp() }
                isTextLayer -> 200.dp
                else -> if (srcAspect > frameRatio) frameW else frameH * srcAspect
            }
            val contentH = when {
                isShapeLayer -> 140.dp
                isTextLayer && textBoxPx.height > 0 -> with(density) { textBoxPx.height.toDp() }
                isTextLayer -> 66.dp
                else -> if (srcAspect > frameRatio) frameW / srcAspect else frameH
            }
            val padDp = 24.dp

            Box(
                modifier = Modifier.size(width = frameW, height = frameH),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                // Build 252 (stage 5): the frame's flat canvas colour +
                // its white 15% inner border are drawn in GL.
                val frameBgArgb = com.example.ui.theme.LocalCanvasColor.current.toArgb()
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = frameBgArgb,
                    buildCmds = { w, h, dd ->
                        addRect(0f, 0f, w, h, frameBgArgb)
                        val edge = Color.White.copy(alpha = 0.15f).toArgb()
                        val bw = 1f * dd
                        addRect(0f, 0f, w, bw, edge)
                        addRect(0f, h - bw, w, bw, edge)
                        addRect(0f, 0f, bw, h, edge)
                        addRect(w - bw, 0f, bw, h, edge)
                    }
                )
                // Background Layer (Video / Shape / Text Media)
                if (clip != null && clip.type == LayerType.VIDEO && clip.uri != null && !isShapeLayer && !isTextLayer) {
                    // The edited layer alone, uncropped and untransformed (the crop box works on it).
                    MediaLayersStack(
                        layers = listOf(
                            clip.copy(
                                blendMode = "Normal", opacity = 100f,
                                scalePercent = 100f, transformX = 0f, transformY = 0f, transformZ = 0f,
                                rotationDeg = 0f, rotation3dX = 0f, rotation3dY = 0f,
                                pivotX = 0.5f, pivotY = 0.5f,
                                cropWidthPercent = 100f, cropHeightPercent = 100f,
                                cropHorizontalPercent = 50f, cropVerticalPercent = 50f,
                                isFlippedH = false, isFlippedV = false,
                                // The C++ engine would animate / track it again; the crop box works on the plain layer.
                                keyframes = emptyMap(), trackFollow = null
                            )
                        ),
                        nativePreview = nativePreview,
                        isPlaying = isPlaying
                    )
                } else if (isTextLayer && clip != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .onSizeChanged { textBoxPx = it }
                    ) {
                        TextLayerContent(clip = clip.copy(cropWidthPercent = 100f, cropHeightPercent = 100f), frameWidth = frameW)
                    }
                } else if (clip?.type == LayerType.SHAPE) {
                    val rawColor = if (clip.textColor != 0L) clip.textColor else clip.color
                    val a = ((rawColor shr 24) and 0xFF).toInt()
                    val r = ((rawColor shr 16) and 0xFF).toInt()
                    val g = ((rawColor shr 8) and 0xFF).toInt()
                    val b = (rawColor and 0xFF).toInt()
                    val shapeColor = if (a == 0 && r == 0 && g == 0 && b == 0) Color(0xFFABE41D) else Color(r, g, b, if (a == 0) 255 else a)
                    val pattern = clip.shapePattern ?: "CIRCLE"
                    val fillBrush = clip.fillBrush()

                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .align(Alignment.Center)
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            drawKey = pattern to shapeColor.toArgb(),
                            buildCmds = { w, h, d ->
                                val colArgb = shapeColor.toArgb()
                                when (pattern) {
                                    "SQUARE" -> addRect(0f, 0f, w, h, colArgb)
                                    "ROUNDED_SQUARE", "ROUNDED_RECT" ->
                                        addRoundRect(w * 0.5f, h * 0.5f, w, h, 16f * d, colArgb)
                                    else -> addCircle(w * 0.5f, h * 0.5f, minOf(w, h) * 0.5f, colArgb)
                                }
                            },
                        )
                    }
                } else {
                    val baseArgb = AmBase.toArgb()
                    val crossArgb = Color.White.copy(alpha = 0.08f).toArgb()
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        drawKey = baseArgb,
                        buildCmds = { w, h, d ->
                            addRect(0f, 0f, w, h, baseArgb)
                            addLine(w * 0.5f, 0f, w * 0.5f, h, 1f * d, crossArgb, capRound = false)
                            addLine(0f, h * 0.5f, w, h * 0.5f, 1f * d, crossArgb, capRound = false)
                        },
                    )
                }

                }

                // Gesture + drawing layer (bigger than the media so edge handles stay touchable).
                Box(
                    modifier = Modifier
                        .requiredSize(width = contentW + padDp * 2, height = contentH + padDp * 2)
                        .testTag("crop_box")
                        .pointerInput(clip?.id) {
                            val pad = 24.dp.toPx()
                            val slop = 30.dp.toPx()
                            var mode = -1
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val cw = size.width - 2 * pad
                                    val ch = size.height - 2 * pad
                                    val l = pad + rl() * cw
                                    val r = pad + rr() * cw
                                    val t = pad + rt() * ch
                                    val b = pad + rb() * ch
                                    mode = UiMathNative.nativeCropHitTest(pos.x, pos.y, l, t, r, b, slop)
                                },
                                onDragEnd = { mode = -1 },
                                onDragCancel = { mode = -1 },
                                onDrag = { change, drag ->
                                    if (mode >= 0) {
                                        change.consume()
                                        val cw = size.width - 2 * pad
                                        val ch = size.height - 2 * pad
                                        dragCrop(mode, drag.x / cw, drag.y / ch)
                                    }
                                }
                            )
                        }
                ) {
                    NativeGlDirectCanvas(
                        modifier = Modifier.fillMaxSize(),
                        drawKey = listOf(widthPercent, heightPercent, horizontalPercent, verticalPercent),
                        drawOnGl = { sid, d ->
                            UiGlRuntime.drawCropOverlayOnGl(sid, rl(), rt(), rr(), rb(), d)
                        },
                    )
                }
            }
        }

        if (chrome != null) {
            PlayControlsSection(
                height = chrome.playControlsH,
                isPlaying = isPlaying,
                canUndo = canUndo,
                canRedo = canRedo,
                onFullScreenClick = chrome.onFullScreen,
                onResolutionClick = chrome.onResolution,
                onPreviousClick = chrome.onPrevious,
                onPlayPauseClick = onTogglePlay,
                onNextClick = chrome.onNext,
                onUndoClick = onUndo,
                onRedoClick = onRedo,
                // Build 201 (audit S-3): the crop surface wires none of the
                // editor-only controls — they are OMITTED here, not rendered
                // inert (until 200 they showed, and the snap magnet even
                // painted white/"on" while connected to nothing).
                showEditorControls = false
            )
            TimescaleSection(
                height = chrome.timescaleH,
                playheadMs = { currentPlayheadMs },
                totalDurationMs = totalDurationMs,
                onBack = {
                    emitCrop()
                    onDismiss()
                }
            )
            chrome.layerRow()
        } else {
        // 3. Playback Controls Row: Skip Prev, Play/Pause, Skip Next, Undo, Redo
        // Build 252 (stage 5): drawn in GL (CropTransportGl) with the
        // ScreenChrome transport spacing law.
        CropTransportGl(
            isPlaying = isPlaying,
            canUndo = canUndo,
            canRedo = canRedo,
            onPrevious = { onSeekTo(0L) },
            onPlayPause = onTogglePlay,
            onNext = { onSeekTo(totalDurationMs) },
            onUndo = onUndo,
            onRedo = onRedo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )
        // 4. Timescale: current | total with ruler ticks
        // Build 252 (stage 5): the texts are drawn in GL; the tick
        // strip (already native) keeps its weight-0.48 slot, placed
        // by the same weight law (CropTimecodeGl).
        CropTimecodeGl(
            currentText = formatTimeCode(currentPlayheadMs),
            totalText = formatTimeCode(totalDurationMs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // 5. Mini Layer Strip: Reset Button on Left, Timeline Strip on Right
        // Build 252 (stage 5): drawn in GL (CropMiniStripGl).
        CropMiniStripGl(
            onReset = {
                activePreset = "Free"
                setRect(0f, 0f, 1f, 1f)
                emitCrop()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )

        }

        // 6. Bottom Control Panel: Aspect Ratio Pills & Ruler Dial Sliders
        // Build 252 (stage 5): the panel body (AmPanel fill, top
        // corners, the AmPillSelected handle) is drawn in GL; the
        // inner Column keeps the corner clip for its scroll content.
        GlPanelBody(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (chrome != null) Modifier.weight(1f) else Modifier),
            cornerDp = AmCornerPanel.value,
            fillArgb = AmPanel.toArgb(),
            handleArgb = AmPillSelected.toArgb(),
            handleTopDp = 4f,
            topOnlyCorners = true
        ) {
        Column(
            modifier = Modifier
                .then(
                    if (chrome != null) Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    else Modifier.fillMaxWidth()
                )
                .clip(RoundedCornerShape(topStart = AmCornerPanel, topEnd = AmCornerPanel))
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)
        ) {

            // Top card: back + live value + keyframe / easing (same as the Transform panel)
            if (chrome != null) {
                TransformValueHeader(
                    valueText = "${widthPercent.roundToInt()}% × ${heightPercent.roundToInt()}%",
                    onBack = {
                        emitCrop()
                        onDismiss()
                    },
                    keyframeEnabled = insideClip,
                    hasKeyframes = hasKeyframes,
                    onKeyframeNow = onKeyframeNow,
                    onKeyframe = { onToggleKeyframes(cropProps) },
                    easingEnabled = easingEnabled,
                    easingOpen = showEasing && easingEnabled,
                    easing = segStart?.easing,
                    onEasing = { if (easingEnabled) showEasing = !showEasing }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (showEasing && segStart != null && segEnd != null) {
                Box(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                    EasingEditor(
                        easing = segStart.easing,
                        progress = ((relMs - segStart.timeMs).toFloat() /
                            (segEnd.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                        onBegin = onEasingBegin,
                        onChange = { e -> cropProps.forEach { onEasingChange(it, segStart.timeMs, e) } }
                    )
                }
            } else {
            // Aspect Ratio Preset Pills Row (Free, Original, Project, 9:16, 16:9, 1:1, etc.)
            // Build 252 (stage 5): GL pill strip.
            GlPillStrip(
                items = CROP_PRESETS.map { preset ->
                    GlPillItem(
                        label = preset,
                        selected = preset == activePreset,
                        onClick = { applyPresetRatio(preset) }
                    )
                },
                labelStyle = { sel -> if (sel) UiLabelStyle.ED_TEXT_BOLD else UiLabelStyle.ED_TEXT_MED },
                textArgb = { sel -> if (sel) AmInk.toArgb() else TextPrimary.toArgb() },
                fillArgb = { sel -> if (sel) AmAccent.toArgb() else AmPanelElevated.toArgb() },
                borderArgb = { sel -> if (sel) AmAccent.toArgb() else AmHairlineStrong.toArgb() },
                padHDp = 12f,
                padVDp = 6f,
                gapDp = 6f,
                minHDp = 32f,
                scrollable = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Fine-Tune Ruler Dial Sliders (Width, Height, Horizontal, Vertical)
            RulerDialSlider(
                label = "Width",
                value = widthPercent,
                onValueChange = {
                    resizeAroundCenter(it / 100f, null)
                },
                min = 10f,
                max = 100f,
                unit = "%",
                isPercentage = true
            )

            RulerDialSlider(
                label = "Height",
                value = heightPercent,
                onValueChange = {
                    resizeAroundCenter(null, it / 100f)
                },
                min = 10f,
                max = 100f,
                unit = "%",
                isPercentage = true
            )

            RulerDialSlider(
                label = "Horizontal",
                value = horizontalPercent,
                onValueChange = {
                    horizontalPercent = it
                    emitCrop()
                },
                min = 0f,
                max = 100f,
                unit = "%",
                isPercentage = true
            )

            RulerDialSlider(
                label = "Vertical",
                value = verticalPercent,
                onValueChange = {
                    verticalPercent = it
                    emitCrop()
                },
                min = 0f,
                max = 100f,
                unit = "%",
                isPercentage = true
            )
            }
        }
        }
    }
}

/**
 * Build 252 (stage 5): the standalone Crop header in GL — back
 * chevron box, the title centred on the free span (the two weight
 * spacers' midpoint), and the white Done pill (fill static, label
 * press-scales, as its modifier chain had them).
 */
@Composable
private fun CropHeaderGl(onBack: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val backPainter = rememberVectorPainter(Icons.Default.KeyboardArrowDown)
    val backPx = (22f * density).roundToInt()
    fun resolveHeader(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val back = atlas.resolveIcon("crop:back", backPx, backPx) { cv, x, y ->
            val b = rasterizePainterBitmap(backPainter, backPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val title = atlas.resolve("Crop", UiLabelStyle.SCREEN_TITLE)
        val done = atlas.resolve("Done", UiLabelStyle.SHEET_ITEM_BOLD)
        return Triple(back, title, done)
    }
    var headerEntries = resolveHeader()
    if (atlas.overflowed) {
        atlas.reset()
        headerEntries = resolveHeader()
    }
    val (backEntry, titleEntry, doneEntry) = headerEntries
    val backScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val doneScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val pillWPx = 24f * density + (doneEntry?.advance ?: 0f)
    val pillHPx = maxOf(32f * density, atlas.textHeightPx(UiLabelStyle.SHEET_ITEM_BOLD) + 12f * density)

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
            drawKey = listOf(headerEntries, backScale.value, doneScale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                if (backEntry != null) {
                    val cx = 27f * dd
                    setScaleAbout(cx, cy, backScale.value)
                    addText(
                        backEntry.index,
                        cx - backPx / 2f - pad, cy - backPx / 2f - pad,
                        0f, 0f, backEntry.rectW.toFloat(), backEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                val pillLeft = w - 10f * dd - pillWPx
                addRoundRect(
                    pillLeft + pillWPx / 2f, cy, pillWPx, pillHPx, pillHPx / 2f,
                    Color.White.toArgb()
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
                if (doneEntry != null) {
                    val style = UiLabelStyle.SHEET_ITEM_BOLD
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        EditorGeometry.centredOriginPx(pillLeft + pillWPx / 2f, doneEntry.advance),
                        baseline, doneEntry.bearing, doneEntry.base, pad
                    )
                    setScaleAbout(pillLeft + pillWPx / 2f, cy, doneScale.value)
                    addText(
                        doneEntry.index, dstX, dstY,
                        0f, 0f, doneEntry.rectW.toFloat(), doneEntry.rectH.toFloat(),
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
            tag = "crop_back_button",
            description = "Collapse Crop"
        )
        ChromePressBox(
            doneScale, 0.94f, onBack,
            Modifier
                .offset(
                    x = ((wPx - pillWPx) / density - 10f).dp,
                    y = ((42f * density - pillHPx) / 2f / density).dp
                )
                .width((pillWPx / density).dp)
                .height((pillHPx / density).dp),
            description = "Done"
        )
    }
}

/**
 * Build 252 (stage 5): the standalone Crop transport row in GL —
 * the ScreenChrome transport law (30 dp lead, the 118 dp centre
 * group, the 62 dp undo group, SpaceBetween's equal gaps).
 */
@Composable
private fun CropTransportGl(
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
    val playPx = (23f * density).roundToInt()
    val undoPx = (17f * density).roundToInt()
    fun resolveTransport(): List<UiLabelAtlas.Entry?> {
        fun icon(key: String, p: androidx.compose.ui.graphics.vector.VectorPainter, px: Int) =
            atlas.resolveIcon(key, px, px) { cv, x, y ->
                val b = rasterizePainterBitmap(p, px, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        return listOf(
            icon("crop:prev", prevPainter, smallPx),
            icon("crop:next", nextPainter, smallPx),
            icon("crop:play", playPainter, playPx),
            icon("crop:pause", pausePainter, playPx),
            icon("crop:undo", undoPainter, undoPx),
            icon("crop:redo", redoPainter, undoPx)
        )
    }
    var transportEntries = resolveTransport()
    if (atlas.overflowed) {
        atlas.reset()
        transportEntries = resolveTransport()
    }
    val prevEntry = transportEntries[0]
    val nextEntry = transportEntries[1]
    val playEntry = transportEntries[2]
    val pauseEntry = transportEntries[3]
    val undoEntry = transportEntries[4]
    val redoEntry = transportEntries[5]
    val scales = remember { List(5) { androidx.compose.animation.core.Animatable(1f) } }

    Box(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val wPx = maxWidth.value * density
            val gapPx = (wPx - (30f + 118f + 62f) * density) / 2f
            val centreLeft = 30f * density + gapPx
            val undoLeft = wPx - 62f * density
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(
                    transportEntries, scales.map { it.value }, isPlaying, canUndo, canRedo, atlas.generation
                ),
                buildCmds = { w, h, dd ->
                    val cy = h / 2f
                    fun blit(e: UiLabelAtlas.Entry?, cx: Float, px: Int, scale: Float, tint: Int) {
                        if (e == null) return
                        setScaleAbout(cx, cy, scale)
                        addText(
                            e.index,
                            cx - px / 2f - pad, cy - px / 2f - pad,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = tint
                        )
                        clearTransform()
                    }
                    blit(prevEntry, centreLeft + 15f * dd, smallPx, scales[0].value, IconActive.toArgb())
                    blit(
                        if (isPlaying) pauseEntry else playEntry,
                        centreLeft + 59f * dd, playPx, scales[1].value, IconActive.toArgb()
                    )
                    blit(nextEntry, centreLeft + 103f * dd, smallPx, scales[2].value, IconActive.toArgb())
                    blit(
                        undoEntry, undoLeft + 14f * dd, undoPx, scales[3].value,
                        (if (canUndo) IconActive else IconDisabled).toArgb()
                    )
                    blit(
                        redoEntry, undoLeft + 48f * dd, undoPx, scales[4].value,
                        (if (canRedo) IconActive else IconDisabled).toArgb()
                    )
                }
            )
            ChromePressBox(
                scales[0], 0.94f, onPrevious,
                Modifier
                    .offset(x = (centreLeft / density).dp, y = 2.dp)
                    .size(30.dp),
                description = "Previous"
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
            ChromePressBox(
                scales[3], 0.94f, onUndo,
                Modifier
                    .offset(x = (undoLeft / density).dp, y = 3.dp)
                    .size(28.dp),
                enabled = canUndo,
                description = "Undo"
            )
            ChromePressBox(
                scales[4], 0.94f, onRedo,
                Modifier
                    .offset(x = ((undoLeft + 34f * density) / density).dp, y = 3.dp)
                    .size(28.dp),
                enabled = canRedo,
                description = "Redo"
            )
        }
    }
}

/**
 * Build 252 (stage 5): the standalone Crop timecode row — current
 * (mono bold) | total (mono medium) in GL, the native tick strip
 * keeping its weight slot by the ScreenChrome timecode law.
 */
@Composable
private fun CropTimecodeGl(
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
        val spacerW = flex * (0.36f / 0.84f)
        val ticksW = flex * (0.48f / 0.84f)
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
        val majorTickArgb = RulerTickColor.toArgb()
        val subTickArgb = RulerSubTickColor.toArgb()
        NativeVectorCanvas(
            modifier = Modifier
                .offset(x = (ticksX / density).dp, y = ((rowHDp.value - 11f) / 2f).dp)
                .width((ticksW / density).dp)
                .height(11.dp),
            drawKey = majorTickArgb,
            buildCmds = { w, h, d ->
                val tickSpacing = 7f * d
                val count = (w / tickSpacing).toInt()
                for (i in 0..count) {
                    val isMajor = (i % 5 == 0)
                    val tx = i * tickSpacing
                    addLine(
                        tx, 0f, tx, if (isMajor) h else h * 0.5f,
                        1f * d, if (isMajor) majorTickArgb else subTickArgb, capRound = false
                    )
                }
            }
        )
    }
}

/**
 * Build 252 (stage 5): the standalone Crop mini strip in GL — the
 * reset disc, the strip surface, the keyframe diamond (a rotated
 * square with the upright Add) and the easing cubic.
 */
@Composable
private fun CropMiniStripGl(onReset: () -> Unit, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val refreshPainter = rememberVectorPainter(Icons.Default.Refresh)
    val addPainter = rememberVectorPainter(Icons.Default.Add)
    val refreshPx = (15f * density).roundToInt()
    val addPx = (11f * density).roundToInt()
    fun resolveStrip(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val refresh = atlas.resolveIcon("crop:refresh", refreshPx, refreshPx) { cv, x, y ->
            val b = rasterizePainterBitmap(refreshPainter, refreshPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val add = atlas.resolveIcon("crop:add", addPx, addPx) { cv, x, y ->
            val b = rasterizePainterBitmap(addPainter, addPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        return refresh to add
    }
    var stripEntries = resolveStrip()
    if (atlas.overflowed) {
        atlas.reset()
        stripEntries = resolveStrip()
    }
    val (refreshEntry, addEntry) = stripEntries
    val resetScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val diamondScale = remember { androidx.compose.animation.core.Animatable(1f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val wPx = maxWidth.value * density
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(stripEntries, resetScale.value, diamondScale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                // Reset disc.
                addCircle(
                    13f * dd, cy, 13f * dd - 0.5f * dd,
                    AmPanelElevated.toArgb(), 1f * dd, AmHairlineStrong.toArgb()
                )
                if (refreshEntry != null) {
                    setScaleAbout(13f * dd, cy, resetScale.value)
                    addText(
                        refreshEntry.index,
                        13f * dd - refreshPx / 2f - pad, cy - refreshPx / 2f - pad,
                        0f, 0f, refreshEntry.rectW.toFloat(), refreshEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                // Strip surface.
                val stripLeft = 32f * dd
                addRoundRect(
                    (stripLeft + w) / 2f, cy, w - stripLeft, 24f * dd, 8f * dd,
                    AmPanelElevated.toArgb()
                )
                addRoundRect(
                    (stripLeft + w) / 2f, cy, w - stripLeft - 1f * dd, 24f * dd - 1f * dd, 7.5f * dd,
                    0, 1f * dd, AmHairlineStrong.toArgb()
                )
                // Keyframe diamond + upright Add.
                val dcx = w - 37f * dd
                setScaleAbout(dcx, cy, diamondScale.value)
                val poly = EditorGeometry.roundedRectOutline(
                    dcx, cy, 16f * dd, 16f * dd, 0f, 0f, 0f, 0f, 45f, 1
                )
                val xs = FloatArray(poly.size / 2)
                val ys = FloatArray(poly.size / 2)
                for (i in xs.indices) {
                    xs[i] = poly[i * 2]
                    ys[i] = poly[i * 2 + 1]
                }
                addFilledPolygon(xs, ys, xs.size, AmVioletDeep.toArgb())
                if (addEntry != null) {
                    addText(
                        addEntry.index,
                        dcx - addPx / 2f - pad, cy - addPx / 2f - pad,
                        0f, 0f, addEntry.rectW.toFloat(), addEntry.rectH.toFloat(),
                        tintArgb = Color.White.toArgb()
                    )
                }
                clearTransform()
                // Easing cubic (the old 15 dp canvas at the strip's right).
                val cl = w - 21f * dd
                addCubic(
                    cl, cy + 7.5f * dd,
                    cl + 6f * dd, cy + 7.5f * dd,
                    cl + 9f * dd, cy - 7.5f * dd,
                    cl + 15f * dd, cy - 7.5f * dd,
                    1.8f * dd, 0xFF8E8E8E.toInt(), capRound = false
                )
            }
        )
        ChromePressBox(
            resetScale, 0.94f, onReset,
            Modifier
                .offset(y = 1.dp)
                .size(26.dp),
            description = "Reset Crop"
        )
        ChromePressBox(
            diamondScale, 0.94f, {},
            Modifier
                .offset(x = ((wPx - 45f * density) / density).dp, y = 6.dp)
                .size(16.dp),
            description = "Keyframe"
        )
    }
}

@Composable
private fun CropCircleHandle(modifier: Modifier = Modifier, sizeDp: Dp = 16.dp) {
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(AmAccent)
            .border(1.5.dp, Color.White, CircleShape)
    )
}
