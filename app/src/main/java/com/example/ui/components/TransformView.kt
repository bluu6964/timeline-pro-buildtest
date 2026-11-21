package com.example.ui.components
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.toArgb
import com.example.engine.UiGlRuntime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.AnimProp
import com.example.model.DEPTH_HIDE_Z
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.depthFactorOf
import com.example.model.AspectRatio
import com.example.model.Easing
import com.example.model.indexOnTime
import com.example.model.segmentIndexAt
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCornerPanel
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmMint
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.AmTickMajor
import com.example.ui.theme.AmTickMinor
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.EditorBackground
import com.example.ui.theme.IconActive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RulerSubTickColor
import com.example.ui.theme.RulerTickColor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.amGlassBrush
import com.example.ui.theme.amMintCyanBrush
import com.example.ui.util.bouncyClickable

private enum class Transform2DPart { POSITION, SCALE, ROTATION }
private enum class Transform3DPart { POSITION_Z, ROTATION_X, ROTATION_Y }

/** Practically unlimited: the rulers only need *some* end, so these are far beyond anything usable. */
private const val TRANSFORM_BIG = 100000f
private const val Z_RANGE_MIN = 5000f
private const val Z_RANGE_MAX = 1000f
private const val TRANSFORM_SCALE_MIN = 1f
private const val TRANSFORM_SCALE_MAX = 100000f

private class RailItem(val icon: ImageVector, val badge: String?, val description: String)

private val rail2d = listOf(
    RailItem(Icons.Default.OpenWith, null, "Position"),
    RailItem(Icons.Default.OpenInFull, null, "Scale"),
    RailItem(Icons.Default.RotateRight, null, "Rotation")
)
private val rail3d = listOf(
    RailItem(Icons.Default.OpenWith, "Z", "Position Z"),
    RailItem(Icons.Default.RotateRight, "X", "Rotation X"),
    RailItem(Icons.Default.RotateRight, "Y", "Rotation Y")
)

enum class TransformTab(val title: String) {
    TWO_D("2D"),
    THREE_D("3D")
}

/**
 * One snapped PositionPad proposal (build 184): the stored X / Y to write (the proposal
 * with the snap correction applied) and the guide lines to show on the canvas, in canvas
 * pixels — a null guide means no snap is active on that axis.
 */
data class PositionSnap(
    val x: Float,
    val y: Float,
    val guideX: Float?,
    val guideY: Float?
)

@Composable
fun TransformView(
    clip: TimelineClip?,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    currentPlayheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDismiss: () -> Unit,
    panelOnly: Boolean = false,
    /** false = no 2D / 3D switcher and no 3D properties (a Null object only has 2D move / scale / rotation). */
    show3d: Boolean = true,
    timescaleHeight: androidx.compose.ui.unit.Dp = 24.dp,
    layerRow: (@Composable () -> Unit)? = null,
    resetTick: Int = 0,
    /** Diamond button: add / remove a keyframe of this property at the playhead. */
    onToggleKeyframe: (AnimProp) -> Unit = {},
    /** Easing editor: a change is about to start (one undo step) / the curve of the segment that starts at the keyframe time changed. */
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> },
    /** Easing editor's delete affordance (build 199, A5): delete the segment-start keyframe of a property. */
    onDeleteKeyframe: (AnimProp, Long) -> Unit = { _, _ -> },
    /**
     * Magnetic snap for the PositionPad ONLY (build 184 — the canvas drag had it since
     * build 176, the Transform panel did not): given the pad's proposed stored X / Y,
     * returns the snapped position plus the canvas guide lines, or null when it has no
     * result (proposal then passes through raw). Scale / rotation / Z / pivot never pass
     * through it, and Reset writes its exact values through onApplyTransform directly.
     */
    positionSnapper: ((x: Float, y: Float) -> PositionSnap?)? = null,
    /** The snapper's guide lines, forwarded to the canvas; (null, null) clears them. */
    onSnapGuides: (guideX: Float?, guideY: Float?) -> Unit = { _, _ -> },
    onApplyTransform: (
        horizontal: Float,
        vertical: Float,
        scale: Float,
        rotation: Float,
        pivotX: Float,
        pivotY: Float,
        rot3dX: Float,
        rot3dY: Float,
        warp: Float,
        posZ: Float
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current active mode tab: 2D, 3D
    var activeTab by remember { mutableStateOf(TransformTab.TWO_D) }
    if (!show3d && activeTab != TransformTab.TWO_D) activeTab = TransformTab.TWO_D
    var part2d by remember { mutableStateOf(Transform2DPart.POSITION) }
    var part3d by remember { mutableStateOf(Transform3DPart.POSITION_Z) }

    // 2D Transform state
    var horizontal by remember(clip?.id) { mutableFloatStateOf(clip?.transformX ?: 0.0f) }
    var vertical by remember(clip?.id) { mutableFloatStateOf(clip?.transformY ?: 0.0f) }
    var scale by remember(clip?.id) { mutableFloatStateOf(clip?.scalePercent ?: 100.0f) }
    var rotation by remember(clip?.id) { mutableFloatStateOf(clip?.rotationDeg ?: 0.0f) }

    // Pivot state
    var pivotX by remember(clip?.id) { mutableFloatStateOf(clip?.pivotX ?: 0.5f) }
    var pivotY by remember(clip?.id) { mutableFloatStateOf(clip?.pivotY ?: 0.5f) }

    // 3D Transform state
    var rot3dX by remember(clip?.id) { mutableFloatStateOf(clip?.rotation3dX ?: 0.0f) }
    var rot3dY by remember(clip?.id) { mutableFloatStateOf(clip?.rotation3dY ?: 0.0f) }

    // Depth (Z) position
    var posZ by remember(clip?.id) { mutableFloatStateOf(clip?.transformZ ?: 0.0f) }

    // Warp state
    var warpDistort by remember(clip?.id) { mutableFloatStateOf(clip?.warpDistort ?: 0.0f) }

    // With keyframes the clip given here is the animated one at the playhead: follow it while the
    // playhead moves or a keyframe changes, so the rulers / pad always show the real current value.
    // Every change of the layer (also one made on the canvas by dragging / scaling / rotating it)
    // re-syncs the panel, not only keyframe changes.
    val animSyncKey: Any? = if (clip != null) {
        listOf(
            currentPlayheadMs, clip.keyframes,
            clip.transformX, clip.transformY, clip.scalePercent, clip.rotationDeg,
            clip.pivotX, clip.pivotY, clip.rotation3dX, clip.rotation3dY,
            clip.transformZ, clip.warpDistort
        )
    } else null
    LaunchedEffect(clip?.id, animSyncKey) {
        if (clip != null) {
            horizontal = clip.transformX
            vertical = clip.transformY
            scale = clip.scalePercent
            rotation = clip.rotationDeg
            pivotX = clip.pivotX
            pivotY = clip.pivotY
            rot3dX = clip.rotation3dX
            rot3dY = clip.rotation3dY
            warpDistort = clip.warpDistort
            posZ = clip.transformZ
        }
    }

    fun emitTransform() {
        onApplyTransform(
            horizontal,
            vertical,
            scale,
            rotation,
            pivotX,
            pivotY,
            rot3dX,
            rot3dY,
            warpDistort,
            posZ
        )
    }

    fun resetAll() {
        horizontal = 0.0f
        vertical = 0.0f
        scale = 100.0f
        rotation = 0.0f
        pivotX = 0.5f
        pivotY = 0.5f
        rot3dX = 0.0f
        rot3dY = 0.0f
        warpDistort = 0.0f
        posZ = 0.0f
        emitTransform()
    }

    androidx.compose.runtime.LaunchedEffect(resetTick) { if (resetTick > 0) resetAll() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (panelOnly) EditorBackground else AmBase)
            .testTag("transform_screen")
    ) {
        if (panelOnly) {
            // Play / undo-redo stays universal (main PlayControlsSection). Only timescale + layer strip here.
            TimescaleSection(
                height = timescaleHeight,
                playheadMs = { currentPlayheadMs },
                totalDurationMs = totalDurationMs
            )
            if (layerRow != null) layerRow() else TransformLayerStrip(
                clip = clip,
                currentPlayheadMs = currentPlayheadMs,
                totalDurationMs = totalDurationMs,
                onSeekTo = onSeekTo,
                onReset = { resetAll() }
            )
        }


        // 6. Bottom Transform Panel (matching screenshot)
        // Build 252 (stage 5): the panel body (flat AmPanel fill,
        // hairline edge, drag handle) is drawn in GL behind the
        // content; the layout is the old glassSurface column's.
        GlPanelBody(
            modifier = Modifier
                .fillMaxWidth()
                // In the editor the panel takes all the space under the layer strip.
                .then(if (panelOnly) Modifier.weight(1f) else Modifier),
            cornerDp = AmCornerPanel.value,
            fillArgb = AmPanel.toArgb(),
            borderArgb = AmHairline.toArgb(),
            handleArgb = AmHairlineStrong.toArgb(),
            handleTopDp = 4f
        ) {
        Column(
            modifier = Modifier
                .then(if (panelOnly) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Drag handle pill space (the pill itself is drawn in GL).
            Spacer(modifier = Modifier.height(5.dp))

            val headerText = when (activeTab) {
                TransformTab.TWO_D -> when (part2d) {
                    Transform2DPart.POSITION -> "X ${horizontal.roundToInt()}   Y ${vertical.roundToInt()}"
                    Transform2DPart.SCALE -> "${scale.roundToInt()}%"
                    Transform2DPart.ROTATION -> "${rotation.roundToInt()}°"
                }
                TransformTab.THREE_D -> when (part3d) {
                    Transform3DPart.POSITION_Z -> "Z ${posZ.roundToInt()}"
                    Transform3DPart.ROTATION_X -> "X ${rot3dX.roundToInt()}°"
                    Transform3DPart.ROTATION_Y -> "Y ${rot3dY.roundToInt()}°"
                }
            }
            // ---- Keyframe / easing state of the property that is open right now ----
            val curProp = when (activeTab) {
                TransformTab.TWO_D -> when (part2d) {
                    Transform2DPart.POSITION -> AnimProp.POSITION
                    Transform2DPart.SCALE -> AnimProp.SCALE
                    Transform2DPart.ROTATION -> AnimProp.ROTATION
                }
                TransformTab.THREE_D -> when (part3d) {
                    Transform3DPart.POSITION_Z -> AnimProp.POS_Z
                    Transform3DPart.ROTATION_X -> AnimProp.ROT_X
                    Transform3DPart.ROTATION_Y -> AnimProp.ROT_Y
                }
            }
            val relMs = currentPlayheadMs - (clip?.startMs ?: 0L)
            val kfList = clip?.keyframes?.get(curProp).orEmpty()
            val insideClip = clip != null && currentPlayheadMs >= clip.startMs && currentPlayheadMs <= clip.endMs
            val onKeyframe = kfList.indexOnTime(relMs) >= 0
            val segIdx = kfList.segmentIndexAt(relMs)
            // Easing only works between two keyframes of this property.
            val easingEnabled = segIdx >= 0
            var showEasing by remember { mutableStateOf(false) }
            LaunchedEffect(easingEnabled, curProp) { if (!easingEnabled) showEasing = false }
            LaunchedEffect(curProp) { showEasing = false }
            val segStart = if (segIdx >= 0) kfList[segIdx] else null
            val segEnd = if (segIdx >= 0) kfList[segIdx + 1] else null

            TransformValueHeader(
                valueText = headerText,
                onBack = if (panelOnly) onDismiss else null,
                keyframeEnabled = insideClip,
                hasKeyframes = kfList.isNotEmpty(),
                onKeyframeNow = onKeyframe,
                onKeyframe = { clip?.let { onToggleKeyframe(curProp) } },
                easingEnabled = easingEnabled,
                easingOpen = showEasing && easingEnabled,
                easing = segStart?.easing,
                onEasing = { if (easingEnabled) showEasing = !showEasing }
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Left icons = sections. Only the chosen section is shown, and it fills the free space.
            if (showEasing && segStart != null && segEnd != null) {
                Box(
                    modifier = if (panelOnly) Modifier.weight(1f).fillMaxWidth()
                    else Modifier.fillMaxWidth().height(94.dp)
                ) {
                    EasingEditor(
                        easing = segStart.easing,
                        progress = ((relMs - segStart.timeMs).toFloat() /
                            (segEnd.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                        onBegin = onEasingBegin,
                        onChange = { e -> onEasingChange(curProp, segStart.timeMs, e) },
                        onDeleteKeyframe = { onDeleteKeyframe(curProp, segStart.timeMs) }
                    )
                }
            } else AnimatedContent(
                targetState = activeTab,
                modifier = if (panelOnly) Modifier.weight(1f).fillMaxWidth() else Modifier,
                transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(120, delayMillis = 30)) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(70))
                },
                label = "transformTabTransition"
            ) { currentTab ->
                val areaMod = if (panelOnly) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().height(94.dp)
                when (currentTab) {
                    TransformTab.TWO_D -> Row(modifier = areaMod, verticalAlignment = Alignment.CenterVertically) {
                        IconRail(
                            items = rail2d,
                            selectedIndex = part2d.ordinal,
                            onSelect = { part2d = Transform2DPart.values()[it] }
                        )
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (part2d) {
                                Transform2DPart.POSITION -> PositionPad(
                                    x = horizontal,
                                    y = vertical,
                                    onChange = { nx, ny ->
                                        // Build 184: the pad's proposal passes through the
                                        // magnetic snapper (PositionPad branch ONLY — every
                                        // other part emits raw). The snapped value is what
                                        // emitTransform writes, so it also lands as the
                                        // playhead keyframe, exactly like a canvas drag.
                                        val snap = positionSnapper?.invoke(nx, ny)
                                        if (snap != null) {
                                            horizontal = snap.x
                                            vertical = snap.y
                                            onSnapGuides(snap.guideX, snap.guideY)
                                        } else {
                                            horizontal = nx
                                            vertical = ny
                                            if (positionSnapper != null) onSnapGuides(null, null)
                                        }
                                        emitTransform()
                                    },
                                    onEnd = { onSnapGuides(null, null) }
                                )
                                Transform2DPart.SCALE -> RulerDialSlider(
                                    label = "",
                                    labelWidth = 0.dp,
                                    showValue = false,
                                    value = scale,
                                    onValueChange = {
                                        scale = it
                                        emitTransform()
                                    },
                                    min = TRANSFORM_SCALE_MIN,
                                    max = TRANSFORM_SCALE_MAX,
                                    unit = "",
                                    isPercentage = true
                                )
                                Transform2DPart.ROTATION -> RulerDialSlider(
                                    label = "",
                                    labelWidth = 0.dp,
                                    showValue = false,
                                    value = rotation,
                                    onValueChange = {
                                        rotation = it
                                        emitTransform()
                                    },
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    unit = "°",
                                    isPercentage = false
                                )
                            }
                        }
                    }

                    TransformTab.THREE_D -> Row(modifier = areaMod, verticalAlignment = Alignment.CenterVertically) {
                        IconRail(
                            items = rail3d,
                            selectedIndex = part3d.ordinal,
                            onSelect = { part3d = Transform3DPart.values()[it] }
                        )
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (part3d) {
                                Transform3DPart.POSITION_Z -> RulerDialSlider(
                                    label = "",
                                    labelWidth = 0.dp,
                                    showValue = false,
                                    value = posZ,
                                    onValueChange = {
                                        posZ = it
                                        emitTransform()
                                    },
                                    // Depth: 0 = canvas plane, + comes toward the camera (gone from DEPTH_HIDE_Z),
                                    // - goes away (smaller). More than this changes nothing visible.
                                    min = -Z_RANGE_MIN,
                                    max = Z_RANGE_MAX,
                                    unit = "",
                                    isPercentage = false
                                )
                                Transform3DPart.ROTATION_X -> RulerDialSlider(
                                    label = "",
                                    labelWidth = 0.dp,
                                    showValue = false,
                                    value = rot3dX,
                                    onValueChange = {
                                        rot3dX = it
                                        emitTransform()
                                    },
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    unit = "°",
                                    isPercentage = false
                                )
                                Transform3DPart.ROTATION_Y -> RulerDialSlider(
                                    label = "",
                                    labelWidth = 0.dp,
                                    showValue = false,
                                    value = rot3dY,
                                    onValueChange = {
                                        rot3dY = it
                                        emitTransform()
                                    },
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    unit = "°",
                                    isPercentage = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom mode switcher tabs: [2D] [3D]
            // Build 252 (stage 5): the bare tab labels are drawn in GL
            // (white bold vs grey medium — the boxless build-183 look);
            // the two weight-1 tap boxes and the 6 dp gap are the old
            // row's geometry.
            if (show3d) {
                val tabAtlas = rememberUiAtlas()
                val tabPad = UiLabelAtlas.PAD.toFloat()
                val tabDensity = androidx.compose.ui.platform.LocalDensity.current.density
                fun resolveTabs() = TransformTab.values().map { tab ->
                    tabAtlas.resolve(
                        tab.title,
                        if (tab == activeTab) UiLabelStyle.SHEET_ITEM_BOLD else UiLabelStyle.SHEET_ITEM_MED
                    )
                }
                var tabEntries = resolveTabs()
                if (tabAtlas.overflowed) {
                    tabAtlas.reset()
                    tabEntries = resolveTabs()
                }
                val tabScales = remember { TransformTab.values().map { androidx.compose.animation.core.Animatable(1f) } }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .height(30.dp)
                ) {
                    val tabBoxW = (maxWidth - 6.dp) / 2
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = tabAtlas,
                        drawKey = listOf(tabEntries, tabScales.map { it.value }, tabAtlas.generation),
                        buildCmds = { w, h, dd ->
                            val gapPx = 6f * dd
                            val boxW = (w - gapPx) / 2f
                            TransformTab.values().forEachIndexed { i, tab ->
                                val left = i * (boxW + gapPx)
                                val e = tabEntries[i] ?: return@forEachIndexed
                                val style = if (tab == activeTab) {
                                    UiLabelStyle.SHEET_ITEM_BOLD
                                } else {
                                    UiLabelStyle.SHEET_ITEM_MED
                                }
                                val textH = tabAtlas.textHeightPx(style)
                                val fm = tabAtlas.fontMetrics(style)
                                val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                                val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                                val originX = EditorGeometry.centredOriginPx(left + boxW / 2f, e.advance)
                                val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, tabPad)
                                setScaleAbout(left + boxW / 2f, h / 2f, tabScales[i].value)
                                addText(
                                    e.index, dstX, dstY,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = (if (tab == activeTab) TextPrimary else AmTextDim).toArgb()
                                )
                                clearTransform()
                            }
                        }
                    )
                    TransformTab.values().forEachIndexed { i, tab ->
                        ChromePressBox(
                            tabScales[i], 0.94f, { activeTab = tab },
                            Modifier
                                .offset(x = (tabBoxW + 6.dp) * i)
                                .width(tabBoxW)
                                .fillMaxHeight(),
                            description = tab.title
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * 4-lobed Pink Crosshair Pivot Anchor Target exactly matching screenshot.
 */
@Composable
fun PivotTargetCrosshair(modifier: Modifier = Modifier) {
    val pinkArgb = AmMint.toArgb()
    NativeVectorCanvas(
        modifier = modifier.size(36.dp),
        drawKey = pinkArgb,
        buildCmds = { w, h, d ->
            val cx = w * 0.5f
            val cy = h * 0.5f
            val centerRadius = 3.5f * d
            val armLength = 7f * d
            val armWidth = 3f * d
            val gap = 4f * d
            addCircle(cx, cy, centerRadius, pinkArgb)
            addCircle(cx, cy, 1.2f * d, android.graphics.Color.WHITE)
            addRoundRect(cx, cy - gap - armLength * 0.5f, armWidth, armLength, armWidth * 0.5f, pinkArgb)
            addRoundRect(cx, cy + gap + armLength * 0.5f, armWidth, armLength, armWidth * 0.5f, pinkArgb)
            addRoundRect(cx - gap - armLength * 0.5f, cy, armLength, armWidth, armWidth * 0.5f, pinkArgb)
            addRoundRect(cx + gap + armLength * 0.5f, cy, armLength, armWidth, armWidth * 0.5f, pinkArgb)
        },
    )
}

/**
 * 2D position pad (Alight Motion style): put a finger on the pad and move it, the layer's X and Y
 * change together by the same distance the finger moves (1 px = 1 unit). Shows the live X / Y.
 */
@Composable
private fun PositionPad(
    x: Float,
    y: Float,
    onChange: (x: Float, y: Float) -> Unit,
    /** Called once when the finger leaves the pad (build 184: clears the snap guides). */
    onEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val curX by rememberUpdatedState(x)
    val curY by rememberUpdatedState(y)
    val change by rememberUpdatedState(onChange)
    val end by rememberUpdatedState(onEnd)
    val range = 100000f   // practically unlimited
    val visualRange = 500f // the dot shows a +-500 window, then stays at the edge
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var px = curX
                        var py = curY
                        var last = down.position
                        var lastX = curX
                        var lastY = curY
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            val d = c.position - last
                            last = c.position
                            px = (px + d.x).coerceIn(-range, range)
                            py = (py + d.y).coerceIn(-range, range)
                            val nx = px.roundToInt().toFloat()
                            val ny = py.roundToInt().toFloat()
                            if (nx != lastX || ny != lastY) {
                                lastX = nx
                                lastY = ny
                                change(nx, ny)
                            }
                        }
                        end()
                    }
                }
        ) {
            // Build 252 (stage 5): the pad's flat fill + hairline edge
            // are drawn in GL under the (already native) pad draw.
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                drawKey = Unit,
                buildCmds = { w, h, dd ->
                    addRoundRect(
                        w / 2f, h / 2f, w, h, 10f * dd,
                        AmPanel.toArgb(), 1f * dd, AmHairline.toArgb()
                    )
                }
            )
            val dotArgb = AmMint.toArgb()
            val gridArgb = AmTickMajor.toArgb()
            val xNorm = (0.5f + (x / visualRange) * 0.5f).coerceIn(0.04f, 0.96f)
            val yNorm = (0.5f + (y / visualRange) * 0.5f).coerceIn(0.04f, 0.96f)
            NativeGlDirectCanvas(
                modifier = Modifier.fillMaxSize(),
                drawKey = xNorm to yNorm,
                drawOnGl = { sid, d ->
                    UiGlRuntime.drawPositionPadOnGl(sid, xNorm, yNorm, dotArgb, gridArgb, d)
                },
            )
        }
    }
}

/** Left rail of icons: tap one to show only that section. [RailItem.badge] is a small axis letter. */
@Composable
private fun IconRail(
    items: List<RailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build 252 (stage 5): the bare rail icons + axis badges are drawn
    // in GL (white vs grey carries the state — the boxless build-183
    // look); the SpaceEvenly geometry and the 36×26 tap boxes are the
    // old column's.
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val painters = items.map { item -> key(item.icon.name) { rememberVectorPainter(item.icon) } }
    val iconPx = (18f * density).roundToInt()

    fun resolveRail(): Pair<List<UiLabelAtlas.Entry?>, List<UiLabelAtlas.Entry?>> {
        val icons = items.mapIndexed { i, item ->
            atlas.resolveIcon("rail:${item.icon.name}", iconPx, iconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(painters[i], iconPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        }
        val badges = items.map { item ->
            item.badge?.let { atlas.resolve(it, UiLabelStyle.ED_BADGE) }
        }
        return icons to badges
    }
    var railEntries = resolveRail()
    if (atlas.overflowed) {
        atlas.reset()
        railEntries = resolveRail()
    }
    val (iconEntries, badgeEntries) = railEntries
    val scales = remember(items.size) { items.map { androidx.compose.animation.core.Animatable(1f) } }

    BoxWithConstraints(modifier = modifier.width(36.dp).fillMaxHeight()) {
        val hPx = maxHeight.value * density
        val boxH = 26f * density
        val gap = if (items.isNotEmpty()) {
            (hPx - items.size * boxH) / (items.size + 1)
        } else 0f
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(iconEntries, badgeEntries, scales.map { it.value }, selectedIndex, hPx, atlas.generation),
            buildCmds = { w, _, dd ->
                items.forEachIndexed { i, item ->
                    val top = gap * (i + 1) + boxH * i
                    val cy = top + boxH / 2f
                    // BARE (boxless order, build 183): no tile behind
                    // the selected item — white vs grey carries it.
                    val tint = (if (i == selectedIndex) AmMint else AmTextDim).toArgb()
                    setScaleAbout(w / 2f, cy, scales[i].value)
                    iconEntries[i]?.let { e ->
                        addText(
                            e.index,
                            w / 2f - iconPx / 2f - pad, cy - iconPx / 2f - pad,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = tint
                        )
                    }
                    badgeEntries[i]?.let { e ->
                        val fm = atlas.fontMetrics(UiLabelStyle.ED_BADGE)
                        // BottomEnd with 2 dp end / 1 dp bottom padding:
                        // the layout box's bottom is the baseline +
                        // descent.
                        val baseline = top + boxH - 1f * dd - fm.descent
                        val originX = w - 2f * dd - e.advance
                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = tint
                        )
                    }
                    clearTransform()
                }
            }
        )
        items.forEachIndexed { i, item ->
            val topDp = (gap * (i + 1) + boxH * i) / density
            ChromePressBox(
                scales[i], 0.94f, { onSelect(i) },
                Modifier
                    .offset(y = topDp.dp)
                    .size(width = 36.dp, height = 26.dp),
                description = item.description
            )
        }
    }
}

/**
 * Top of the panel (Alight Motion style): back on the left, the live value in the middle,
 * keyframe diamond + easing curve on the right.
 *
 * Diamond: filled = the playhead sits on a keyframe (tap removes it), outlined = this property is
 * animated but there is no keyframe here, grey = not animated yet (tap adds a keyframe).
 * Easing: only usable between two keyframes; it shows the curve of that segment.
 */
@Composable
internal fun TransformValueHeader(
    valueText: String,
    onBack: (() -> Unit)?,
    keyframeEnabled: Boolean,
    hasKeyframes: Boolean,
    onKeyframeNow: Boolean,
    onKeyframe: () -> Unit,
    easingEnabled: Boolean,
    easingOpen: Boolean,
    easing: Easing?,
    onEasing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = AmMint
    // Build 252 (UI migration stage 5): the header is drawn on one GL
    // surface (EditorGeometry.ValueHeader) — back icon, the centred
    // monospace value, the rotated keyframe diamond and the easing
    // button's open-state circle. The easing tile itself stays the
    // build-241 native canvas, hosted over the surface with its press
    // scale on a graphicsLayer fed by the same Animatable the tap box
    // drives. Tap targets, tags and the disabled-diamond 0.35 alpha
    // (folded into the drawn colours) are unchanged.
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val backPainter = rememberVectorPainter(Icons.Default.KeyboardArrowDown)
    val addPainter = rememberVectorPainter(Icons.Default.Add)
    val backPx = (EditorGeometry.ValueHeader.BACK_ICON_DP * density).roundToInt()
    val addPx = (EditorGeometry.ValueHeader.ADD_ICON_DP * density).roundToInt()

    fun resolveHeader(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val back = atlas.resolveIcon("valueheader:back", backPx, backPx) { cv, x, y ->
            val b = rasterizePainterBitmap(backPainter, backPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val add = atlas.resolveIcon("valueheader:add", addPx, addPx) { cv, x, y ->
            val b = rasterizePainterBitmap(addPainter, addPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val value = atlas.resolve(valueText, UiLabelStyle.VALUE_HEADER_MONO)
        return Triple(back, add, value)
    }
    var headerEntries = resolveHeader()
    if (atlas.overflowed) {
        atlas.reset()
        headerEntries = resolveHeader()
    }
    val (backEntry, addEntry, valueEntry) = headerEntries

    val backScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val diamondScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val easingScale = remember { androidx.compose.animation.core.Animatable(1f) }

    val iconColor = when {
        !easingEnabled -> AmTextFaint
        easingOpen -> accent
        else -> TextPrimary
    }
    val e = (if (easingEnabled) easing else null) ?: Easing(0.4f, 0f, 0.6f, 1f)

    Box(modifier = modifier.fillMaxWidth().height(34.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                backEntry, addEntry, valueEntry,
                backScale.value, diamondScale.value, easingScale.value,
                onBack != null, keyframeEnabled, onKeyframeNow, hasKeyframes,
                easingOpen, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                val g = EditorGeometry.ValueHeader
                val cy = g.centreYPx(dd)
                val boxPx = g.BOX_DP * dd
                // Back icon, press-scaled about its box centre.
                if (onBack != null && backEntry != null) {
                    setScaleAbout(boxPx / 2f, cy, backScale.value)
                    addText(
                        backEntry.index,
                        boxPx / 2f - backPx / 2f - pad, cy - backPx / 2f - pad,
                        0f, 0f, backEntry.rectW.toFloat(), backEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                // The value, centred in the weight-1 span.
                if (valueEntry != null) {
                    val style = UiLabelStyle.VALUE_HEADER_MONO
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(g.valueCentreXPx(w, dd), valueEntry.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, valueEntry.bearing, valueEntry.base, pad)
                    addText(
                        valueEntry.index, dstX, dstY,
                        0f, 0f, valueEntry.rectW.toFloat(), valueEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                // The keyframe diamond: the rotated rounded square as a
                // polygon (+ its outline state), the plus blitted
                // upright (the Compose icon counter-rotated to the
                // same result). The disabled 0.35 alpha folds into the
                // drawn colours.
                val dcx = g.diamondCentreXPx(w, dd)
                val alphaMul = if (keyframeEnabled) 1f else 0.35f
                setScaleAbout(dcx, cy, diamondScale.value)
                val pts = g.diamondOutline(dcx, cy, dd)
                val xs = FloatArray(pts.size / 2) { pts[it * 2] }
                val ys = FloatArray(pts.size / 2) { pts[it * 2 + 1] }
                val fillColor = if (onKeyframeNow) accent else AmPanelHigh
                addFilledPolygon(
                    xs, ys, xs.size,
                    fillColor.copy(alpha = fillColor.alpha * alphaMul).toArgb()
                )
                if (hasKeyframes && !onKeyframeNow) {
                    addPolyline(
                        xs, ys, xs.size, true,
                        g.DIAMOND_BORDER_DP * dd,
                        accent.copy(alpha = accent.alpha * alphaMul).toArgb()
                    )
                }
                if (!onKeyframeNow && addEntry != null) {
                    addText(
                        addEntry.index,
                        dcx - addPx / 2f - pad, cy - addPx / 2f - pad,
                        0f, 0f, addEntry.rectW.toFloat(), addEntry.rectH.toFloat(),
                        tintArgb = Color.White.copy(alpha = alphaMul).toArgb()
                    )
                }
                clearTransform()
                // The easing button's open-state circle.
                if (easingOpen) {
                    val ecx = g.easingCentreXPx(w, dd)
                    setScaleAbout(ecx, cy, easingScale.value)
                    addCircle(ecx, cy, boxPx / 2f, AmPanelHigh.toArgb())
                    clearTransform()
                }
            }
        )

        // The easing tile (native since build 241) over the surface.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = 1.dp)
                .size(32.dp)
                .graphicsLayer {
                    scaleX = easingScale.value
                    scaleY = easingScale.value
                },
            contentAlignment = Alignment.Center
        ) {
            NativeGlDirectCanvas(
                modifier = Modifier.size(16.dp),
                drawKey = listOf(easingEnabled, easingOpen, e.x1, e.y1, e.x2, e.y2),
                drawOnGl = { sid, d ->
                    UiGlRuntime.drawEasingTileOnGl(sid, e.x1, e.y1, e.x2, e.y2, iconColor.toArgb(), d)
                },
            )
        }

        // Tap targets at the old boxes (32 dp, centred in the 34 dp row).
        if (onBack != null) {
            ChromePressBox(
                backScale, 0.94f, onBack,
                Modifier.offset(y = 1.dp).size(32.dp),
                tag = "panel_back_button",
                description = "Back"
            )
        }
        ChromePressBox(
            diamondScale, 0.94f, { if (keyframeEnabled) onKeyframe() },
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32).dp, y = 1.dp)
                .size(32.dp),
            tag = "transform_keyframe_button",
            description = if (!onKeyframeNow) "Keyframe" else null
        )
        ChromePressBox(
            easingScale, 0.94f, { if (easingEnabled) onEasing() },
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = 1.dp)
                .size(32.dp),
            tag = "transform_easing_button"
        )
    }
}
