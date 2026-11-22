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
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
import com.example.ui.theme.AmLime
import com.example.ui.theme.AmOrange
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

/** Practically unlimited: the rulers only need *some* end, so these are far beyond anything usable. */
private const val TRANSFORM_BIG = 100000f
private const val Z_RANGE_MIN = 5000f
private const val Z_RANGE_MAX = 1000f
private const val TRANSFORM_SCALE_MIN = 1f
private const val TRANSFORM_SCALE_MAX = 100000f

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
    /**
     * Build 255: the quick row's Flip chip — the model's real horizontal
     * flip (TimelineClip.isFlippedH, toggled by the caller). Null = the
     * caller offers no flip; the chip then becomes Reset instead.
     */
    onFlipHorizontal: (() -> Unit)? = null,
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

            // ---- Build 255: the card-grid panel ----
            // Every property is visible at once as a value card; the
            // ACTIVE property (the card last scrubbed / tapped, or
            // whose diamond was tapped) is what the header's easing
            // button edits — the old one-section-at-a-time model's
            // "open property", carried over.
            var activeProp by remember { mutableStateOf(AnimProp.POSITION) }
            var xyLinked by remember { mutableStateOf(false) }
            LaunchedEffect(clip?.id) {
                activeProp = if (activeTab == TransformTab.THREE_D) AnimProp.POS_Z else AnimProp.POSITION
                xyLinked = false
            }
            val relMs = currentPlayheadMs - (clip?.startMs ?: 0L)
            val insideClip = clip != null && currentPlayheadMs >= clip.startMs && currentPlayheadMs <= clip.endMs
            fun kfListOf(prop: AnimProp) = clip?.keyframes?.get(prop).orEmpty()
            fun kfOn(prop: AnimProp) = kfListOf(prop).indexOnTime(relMs) >= 0
            val kfList = kfListOf(activeProp)
            val segIdx = kfList.segmentIndexAt(relMs)
            // Easing only works between two keyframes of this property.
            val easingEnabled = segIdx >= 0
            var showEasing by remember { mutableStateOf(false) }
            LaunchedEffect(easingEnabled, activeProp) { if (!easingEnabled) showEasing = false }
            LaunchedEffect(activeProp) { showEasing = false }
            val segStart = if (segIdx >= 0) kfList[segIdx] else null
            val segEnd = if (segIdx >= 0) kfList[segIdx + 1] else null
            val visibleProps = if (activeTab == TransformTab.TWO_D)
                listOf(AnimProp.POSITION, AnimProp.SCALE, AnimProp.ROTATION)
            else listOf(AnimProp.POS_Z, AnimProp.ROT_X, AnimProp.ROT_Y)

            TransformPanelHeader(
                show3d = show3d,
                activeTab = activeTab,
                onTab = { tab ->
                    activeTab = tab
                    activeProp = if (tab == TransformTab.TWO_D) AnimProp.POSITION else AnimProp.POS_Z
                },
                showLink = activeTab == TransformTab.TWO_D,
                linked = xyLinked,
                onToggleLink = { xyLinked = !xyLinked },
                keyframeEnabled = insideClip,
                kfAllOn = visibleProps.all { kfOn(it) },
                kfAnyOn = visibleProps.any { kfOn(it) },
                onKeyframeAll = {
                    if (insideClip) {
                        val target = !visibleProps.all { kfOn(it) }
                        visibleProps.forEach { p -> if (kfOn(p) != target) onToggleKeyframe(p) }
                    }
                },
                easingEnabled = easingEnabled,
                easingOpen = showEasing && easingEnabled,
                easing = segStart?.easing,
                onEasing = { if (easingEnabled) showEasing = !showEasing },
                onClose = if (panelOnly) onDismiss else null
            )

            Spacer(modifier = Modifier.height(4.dp))

            // The easing editor replaces the card grid while it is open
            // (the app's real editor, editing the active property's
            // segment — unchanged from the pre-255 panel).
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
                        onChange = { e -> onEasingChange(activeProp, segStart.timeMs, e) },
                        onDeleteKeyframe = { onDeleteKeyframe(activeProp, segStart.timeMs) }
                    )
                }
            } else {
                // Build 255: the value-card grid. 2D unlinked = X / Y /
                // Scale / Rotate cards; 2D linked = the position pad
                // spanning both rows + Scale / Rotate; 3D = Z /
                // Rotate X / Rotate Y + the Reset 3D card. Every card
                // scrubs through the build-241 native ruler (RulerDial)
                // and writes the real field via emitTransform.
                val gridMod = if (panelOnly) Modifier.weight(1f).fillMaxWidth()
                    else Modifier.fillMaxWidth().height(108.dp)
                val gap = EditorGeometry.TransformPanel.GAP_DP.dp
                when (activeTab) {
                    TransformTab.TWO_D -> if (xyLinked) {
                        Row(modifier = gridMod, horizontalArrangement = Arrangement.spacedBy(gap)) {
                            TransformPadCard(
                                x = horizontal,
                                y = vertical,
                                active = activeProp == AnimProp.POSITION,
                                kfEnabled = insideClip,
                                kfHas = kfListOf(AnimProp.POSITION).isNotEmpty(),
                                kfOn = kfOn(AnimProp.POSITION),
                                kfTag = activeProp == AnimProp.POSITION,
                                onDiamond = {
                                    activeProp = AnimProp.POSITION
                                    if (insideClip) onToggleKeyframe(AnimProp.POSITION)
                                },
                                onActivate = { activeProp = AnimProp.POSITION },
                                onCentre = {
                                    horizontal = 0f
                                    vertical = 0f
                                    emitTransform()
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                PositionPad(
                                    x = horizontal,
                                    y = vertical,
                                    embedded = true,
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
                            }
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                TransformValueCard(
                                    label = "Scale",
                                    valueText = "${scale.roundToInt()}%",
                                    value = scale,
                                    min = TRANSFORM_SCALE_MIN,
                                    max = TRANSFORM_SCALE_MAX,
                                    active = activeProp == AnimProp.SCALE,
                                    kfEnabled = insideClip,
                                    kfHas = kfListOf(AnimProp.SCALE).isNotEmpty(),
                                    kfOn = kfOn(AnimProp.SCALE),
                                    kfTag = activeProp == AnimProp.SCALE,
                                    onScrub = { scale = it; emitTransform() },
                                    onDiamond = {
                                        activeProp = AnimProp.SCALE
                                        if (insideClip) onToggleKeyframe(AnimProp.SCALE)
                                    },
                                    onActivate = { activeProp = AnimProp.SCALE },
                                    onReset = { scale = 100f; emitTransform() },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                TransformValueCard(
                                    label = "Rotate",
                                    valueText = "${rotation.roundToInt()}°",
                                    value = rotation,
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    active = activeProp == AnimProp.ROTATION,
                                    kfEnabled = insideClip,
                                    kfHas = kfListOf(AnimProp.ROTATION).isNotEmpty(),
                                    kfOn = kfOn(AnimProp.ROTATION),
                                    kfTag = activeProp == AnimProp.ROTATION,
                                    onScrub = { rotation = it; emitTransform() },
                                    onDiamond = {
                                        activeProp = AnimProp.ROTATION
                                        if (insideClip) onToggleKeyframe(AnimProp.ROTATION)
                                    },
                                    onActivate = { activeProp = AnimProp.ROTATION },
                                    onReset = { rotation = 0f; emitTransform() },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = gridMod,
                            verticalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                TransformValueCard(
                                    label = "X",
                                    valueText = "${horizontal.roundToInt()}",
                                    value = horizontal,
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    active = activeProp == AnimProp.POSITION,
                                    kfEnabled = insideClip,
                                    kfHas = kfListOf(AnimProp.POSITION).isNotEmpty(),
                                    kfOn = kfOn(AnimProp.POSITION),
                                    kfTag = activeProp == AnimProp.POSITION,
                                    onScrub = { horizontal = it; emitTransform() },
                                    onDiamond = {
                                        activeProp = AnimProp.POSITION
                                        if (insideClip) onToggleKeyframe(AnimProp.POSITION)
                                    },
                                    onActivate = { activeProp = AnimProp.POSITION },
                                    onReset = { horizontal = 0f; emitTransform() },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                TransformValueCard(
                                    label = "Y",
                                    valueText = "${vertical.roundToInt()}",
                                    value = vertical,
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    active = activeProp == AnimProp.POSITION,
                                    kfEnabled = insideClip,
                                    kfHas = kfListOf(AnimProp.POSITION).isNotEmpty(),
                                    kfOn = kfOn(AnimProp.POSITION),
                                    kfTag = false,
                                    onScrub = { vertical = it; emitTransform() },
                                    onDiamond = {
                                        activeProp = AnimProp.POSITION
                                        if (insideClip) onToggleKeyframe(AnimProp.POSITION)
                                    },
                                    onActivate = { activeProp = AnimProp.POSITION },
                                    onReset = { vertical = 0f; emitTransform() },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                TransformValueCard(
                                    label = "Scale",
                                    valueText = "${scale.roundToInt()}%",
                                    value = scale,
                                    min = TRANSFORM_SCALE_MIN,
                                    max = TRANSFORM_SCALE_MAX,
                                    active = activeProp == AnimProp.SCALE,
                                    kfEnabled = insideClip,
                                    kfHas = kfListOf(AnimProp.SCALE).isNotEmpty(),
                                    kfOn = kfOn(AnimProp.SCALE),
                                    kfTag = activeProp == AnimProp.SCALE,
                                    onScrub = { scale = it; emitTransform() },
                                    onDiamond = {
                                        activeProp = AnimProp.SCALE
                                        if (insideClip) onToggleKeyframe(AnimProp.SCALE)
                                    },
                                    onActivate = { activeProp = AnimProp.SCALE },
                                    onReset = { scale = 100f; emitTransform() },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                TransformValueCard(
                                    label = "Rotate",
                                    valueText = "${rotation.roundToInt()}°",
                                    value = rotation,
                                    min = -TRANSFORM_BIG,
                                    max = TRANSFORM_BIG,
                                    active = activeProp == AnimProp.ROTATION,
                                    kfEnabled = insideClip,
                                    kfHas = kfListOf(AnimProp.ROTATION).isNotEmpty(),
                                    kfOn = kfOn(AnimProp.ROTATION),
                                    kfTag = activeProp == AnimProp.ROTATION,
                                    onScrub = { rotation = it; emitTransform() },
                                    onDiamond = {
                                        activeProp = AnimProp.ROTATION
                                        if (insideClip) onToggleKeyframe(AnimProp.ROTATION)
                                    },
                                    onActivate = { activeProp = AnimProp.ROTATION },
                                    onReset = { rotation = 0f; emitTransform() },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }

                    TransformTab.THREE_D -> Column(
                        modifier = gridMod,
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            TransformValueCard(
                                label = "Z depth",
                                valueText = "${posZ.roundToInt()}",
                                value = posZ,
                                // Depth: 0 = canvas plane, + comes toward the camera (gone from
                                // DEPTH_HIDE_Z), - goes away (smaller). More than this changes
                                // nothing visible.
                                min = -Z_RANGE_MIN,
                                max = Z_RANGE_MAX,
                                active = activeProp == AnimProp.POS_Z,
                                kfEnabled = insideClip,
                                kfHas = kfListOf(AnimProp.POS_Z).isNotEmpty(),
                                kfOn = kfOn(AnimProp.POS_Z),
                                kfTag = activeProp == AnimProp.POS_Z,
                                onScrub = { posZ = it; emitTransform() },
                                onDiamond = {
                                    activeProp = AnimProp.POS_Z
                                    if (insideClip) onToggleKeyframe(AnimProp.POS_Z)
                                },
                                onActivate = { activeProp = AnimProp.POS_Z },
                                onReset = { posZ = 0f; emitTransform() },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            TransformValueCard(
                                label = "Rotate X",
                                valueText = "${rot3dX.roundToInt()}°",
                                value = rot3dX,
                                min = -TRANSFORM_BIG,
                                max = TRANSFORM_BIG,
                                active = activeProp == AnimProp.ROT_X,
                                kfEnabled = insideClip,
                                kfHas = kfListOf(AnimProp.ROT_X).isNotEmpty(),
                                kfOn = kfOn(AnimProp.ROT_X),
                                kfTag = activeProp == AnimProp.ROT_X,
                                onScrub = { rot3dX = it; emitTransform() },
                                onDiamond = {
                                    activeProp = AnimProp.ROT_X
                                    if (insideClip) onToggleKeyframe(AnimProp.ROT_X)
                                },
                                onActivate = { activeProp = AnimProp.ROT_X },
                                onReset = { rot3dX = 0f; emitTransform() },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            TransformValueCard(
                                label = "Rotate Y",
                                valueText = "${rot3dY.roundToInt()}°",
                                value = rot3dY,
                                min = -TRANSFORM_BIG,
                                max = TRANSFORM_BIG,
                                active = activeProp == AnimProp.ROT_Y,
                                kfEnabled = insideClip,
                                kfHas = kfListOf(AnimProp.ROT_Y).isNotEmpty(),
                                kfOn = kfOn(AnimProp.ROT_Y),
                                kfTag = activeProp == AnimProp.ROT_Y,
                                onScrub = { rot3dY = it; emitTransform() },
                                onDiamond = {
                                    activeProp = AnimProp.ROT_Y
                                    if (insideClip) onToggleKeyframe(AnimProp.ROT_Y)
                                },
                                onActivate = { activeProp = AnimProp.ROT_Y },
                                onReset = { rot3dY = 0f; emitTransform() },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            TransformResetCard(
                                text = "Reset 3D",
                                onClick = {
                                    posZ = 0f
                                    rot3dX = 0f
                                    rot3dY = 0f
                                    emitTransform()
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick-action row (build 255): every chip writes real
            // transform fields through emitTransform (Flip rides the
            // caller's real isFlippedH toggle; without one the chip
            // becomes Reset, the panel's existing full reset).
            val chips = if (activeTab == TransformTab.TWO_D) listOf(
                TransformQuickChip("Center") {
                    horizontal = 0f
                    vertical = 0f
                    emitTransform()
                },
                if (onFlipHorizontal != null) TransformQuickChip(
                    "Flip",
                    active = clip?.isFlippedH == true
                ) { onFlipHorizontal() }
                else TransformQuickChip("Reset") { resetAll() },
                TransformQuickChip("100%") {
                    scale = 100f
                    emitTransform()
                },
                TransformQuickChip("-90°") {
                    rotation -= 90f
                    emitTransform()
                }
            ) else listOf(
                TransformQuickChip("Front") {
                    rot3dX = 0f
                    rot3dY = 0f
                    emitTransform()
                },
                TransformQuickChip("Top") {
                    rot3dX = -90f
                    emitTransform()
                },
                TransformQuickChip("Side") {
                    rot3dY = 90f
                    emitTransform()
                },
                TransformQuickChip("Reset 3D") {
                    posZ = 0f
                    rot3dX = 0f
                    rot3dY = 0f
                    emitTransform()
                }
            )
            TransformQuickRow(
                chips = chips,
                modifier = Modifier.padding(bottom = 4.dp)
            )

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
    /**
     * Build 255: hosted inside the transform card grid — the card
     * draws the background, so the pad drops its own fill + vertical
     * padding and only draws the (native) grid + dot.
     */
    embedded: Boolean = false,
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
            .then(if (embedded) Modifier else Modifier.padding(vertical = 2.dp)),
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
            // Build 255: embedded in a card, the card draws the fill.
            if (!embedded) NativeVectorCanvas(
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

// ────────────────────────────────────────────────────────────────────
// Build 255: the card-grid transform panel's pieces (header, value
// cards, position-pad card, reset card, quick row). All drawn in GL
// on the stage-5 patterns (EditorGeometry.TransformPanel law, atlas
// text, ChromePressBox tap boxes); the scrub itself is the build-241
// native RulerDial and the pad the build-241 native position pad.
// ────────────────────────────────────────────────────────────────────

private class TransformQuickChip(
    val label: String,
    val active: Boolean = false,
    val action: () -> Unit
)

/**
 * One keyframe diamond in the value header's three states (filled
 * orange on a keyframe, orange outline when the property is animated
 * but the playhead is elsewhere, plain + plus when unanimated),
 * with the disabled 0.35 alpha folded in.
 */
private fun com.example.engine.VectorCmdBuilder.drawKfDiamond(
    addEntry: UiLabelAtlas.Entry?,
    atlasPad: Float,
    cx: Float,
    cy: Float,
    density: Float,
    fillArgb: Int,
    kfOn: Boolean,
    kfHas: Boolean,
    kfEnabled: Boolean
) {
    val alphaMul = if (kfEnabled) 1f else 0.35f
    val pts = EditorGeometry.ValueHeader.diamondOutline(cx, cy, density)
    val xs = FloatArray(pts.size / 2) { pts[it * 2] }
    val ys = FloatArray(pts.size / 2) { pts[it * 2 + 1] }
    if (kfOn) {
        addFilledPolygon(xs, ys, xs.size, AmOrange.copy(alpha = alphaMul).toArgb())
        return
    }
    val fill = Color(fillArgb)
    addFilledPolygon(xs, ys, xs.size, fill.copy(alpha = fill.alpha * alphaMul).toArgb())
    if (kfHas) {
        addPolyline(
            xs, ys, xs.size, true,
            EditorGeometry.ValueHeader.DIAMOND_BORDER_DP * density,
            AmOrange.copy(alpha = alphaMul).toArgb()
        )
    }
    if (addEntry != null) {
        val addPx = EditorGeometry.ValueHeader.ADD_ICON_DP * density
        addText(
            addEntry.index,
            cx - addPx / 2f - atlasPad, cy - addPx / 2f - atlasPad,
            0f, 0f, addEntry.rectW.toFloat(), addEntry.rectH.toFloat(),
            tintArgb = Color.White.copy(alpha = alphaMul).toArgb()
        )
    }
}

/**
 * Build 255: a value card's tap behaviour — any press activates the
 * card's property, and a clean double tap resets it to its default.
 * Observe-only: the press is read in the Initial pass, the release
 * in the Final pass (so a release the diamond button consumed does
 * not count as a card tap), and nothing is ever consumed — the
 * ruler's drag and the diamond's click keep their own gestures.
 */
@Composable
private fun Modifier.transformCardGestures(
    onActivate: () -> Unit,
    onDoubleTap: () -> Unit
): Modifier {
    val activate by rememberUpdatedState(onActivate)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    return this.pointerInput(Unit) {
        var lastTapMs = 0L
        var lastTapPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            activate()
            var moved = false
            var consumedUp = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Final)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
                if (!c.pressed) {
                    consumedUp = c.isConsumed
                    break
                }
            }
            if (moved || consumedUp) {
                lastTapMs = 0L
                return@awaitEachGesture
            }
            val now = down.uptimeMillis
            val isDouble = now - lastTapMs <= viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastTapPos).getDistance() <= 64.dp.toPx()
            if (isDouble) {
                doubleTap()
                lastTapMs = 0L
            } else {
                lastTapMs = now
                lastTapPos = down.position
            }
        }
    }
}

/**
 * Build 255 header row: the 2D/3D segmented toggle on the left; on
 * the right the XY-link chip (2D only), keyframe-all (two diamonds),
 * the easing button (the build-241 native tile, as in the old value
 * header) and the close chevron. One GL surface; tap boxes above it.
 */
@Composable
private fun TransformPanelHeader(
    show3d: Boolean,
    activeTab: TransformTab,
    onTab: (TransformTab) -> Unit,
    showLink: Boolean,
    linked: Boolean,
    onToggleLink: () -> Unit,
    keyframeEnabled: Boolean,
    kfAllOn: Boolean,
    kfAnyOn: Boolean,
    onKeyframeAll: () -> Unit,
    easingEnabled: Boolean,
    easingOpen: Boolean,
    easing: Easing?,
    onEasing: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val g = EditorGeometry.TransformPanel
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val chevronPainter = rememberVectorPainter(Icons.Default.KeyboardArrowDown)
    val linkPainter = rememberVectorPainter(Icons.Default.Link)
    val chevronPx = (20f * density).roundToInt()
    val linkPx = (g.LINK_ICON_DP * density).roundToInt()

    fun resolveHeader(): List<UiLabelAtlas.Entry?> = listOf(
        if (onClose != null) atlas.resolveIcon("tfhead:close", chevronPx, chevronPx) { cv, x, y ->
            val b = rasterizePainterBitmap(chevronPainter, chevronPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        } else null,
        if (showLink) atlas.resolveIcon("tfhead:link", linkPx, linkPx) { cv, x, y ->
            val b = rasterizePainterBitmap(linkPainter, linkPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        } else null,
        if (showLink) atlas.resolve("XY", UiLabelStyle.TF_SEG) else null,
        if (show3d) atlas.resolve("2D", UiLabelStyle.TF_SEG) else null,
        if (show3d) atlas.resolve("3D", UiLabelStyle.TF_SEG) else null
    )
    var headerEntries = resolveHeader()
    if (atlas.overflowed) {
        atlas.reset()
        headerEntries = resolveHeader()
    }
    val (closeEntry, linkEntry, xyEntry, seg2Entry, seg3Entry) = headerEntries

    val segScales = remember { List(2) { androidx.compose.animation.core.Animatable(1f) } }
    val linkScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val kfAllScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val easingScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val closeScale = remember { androidx.compose.animation.core.Animatable(1f) }

    // Right-side boxes, indexed from the row's right edge.
    val closeIdx = if (onClose != null) 0 else -1
    val easingIdx = closeIdx + 1
    val kfAllIdx = easingIdx + 1
    val boxesFromRight = kfAllIdx + 1

    val iconColor = when {
        !easingEnabled -> AmTextFaint
        easingOpen -> AmLime
        else -> TextPrimary
    }
    val e = (if (easingEnabled) easing else null) ?: Easing(0.4f, 0f, 0.6f, 1f)

    Box(modifier = modifier.fillMaxWidth().height(g.HEADER_H_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                headerEntries, segScales.map { it.value }, linkScale.value,
                kfAllScale.value, easingScale.value, closeScale.value,
                show3d, activeTab, showLink, linked,
                keyframeEnabled, kfAllOn, kfAnyOn, easingOpen, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                val cy = g.headerCentreYPx(dd)
                // Segmented toggle: track, active pill, labels.
                if (show3d) {
                    val trackW = g.segTrackWPx(dd)
                    addRoundRect(
                        trackW / 2f, cy, trackW, h,
                        g.SEG_TRACK_CORNER_DP * dd, AmPanelHigh.toArgb()
                    )
                    TransformTab.values().forEachIndexed { i, tab ->
                        val cx = g.segCentreXPx(i, dd)
                        val entry = if (i == 0) seg2Entry else seg3Entry
                        setScaleAbout(cx, cy, segScales[i].value)
                        if (tab == activeTab) {
                            addRoundRect(
                                cx, cy, g.SEG_W_DP * dd, g.SEG_H_DP * dd,
                                g.SEG_CORNER_DP * dd, TextPrimary.toArgb()
                            )
                        }
                        if (entry != null) {
                            val style = UiLabelStyle.TF_SEG
                            val textH = atlas.textHeightPx(style)
                            val fm = atlas.fontMetrics(style)
                            val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                            val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                            val originX = EditorGeometry.centredOriginPx(cx, entry.advance)
                            val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, entry.bearing, entry.base, pad)
                            addText(
                                entry.index, dstX, dstY,
                                0f, 0f, entry.rectW.toFloat(), entry.rectH.toFloat(),
                                tintArgb = (if (tab == activeTab) AmBase else AmTextDim).toArgb()
                            )
                        }
                        clearTransform()
                    }
                }
                // XY-link chip (2D only).
                if (showLink && xyEntry != null) {
                    val chipW = g.linkChipWPx(xyEntry.advance, dd)
                    val chipLeft = g.linkChipLeftPx(w, chipW, boxesFromRight, dd)
                    val chipCx = chipLeft + chipW / 2f
                    setScaleAbout(chipCx, cy, linkScale.value)
                    addRoundRect(
                        chipCx, cy, chipW, g.SEG_H_DP * dd,
                        g.LINK_CORNER_DP * dd,
                        (if (linked) AmLime else AmPanelHigh).toArgb(),
                        if (linked) 0f else 1f * dd, AmHairline.toArgb()
                    )
                    val tint = (if (linked) AmBase else AmTextDim).toArgb()
                    if (linkEntry != null) {
                        addText(
                            linkEntry.index,
                            chipLeft + g.LINK_PAD_DP * dd - pad, cy - linkPx / 2f - pad,
                            0f, 0f, linkEntry.rectW.toFloat(), linkEntry.rectH.toFloat(),
                            tintArgb = tint
                        )
                    }
                    val style = UiLabelStyle.TF_SEG
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val labelX = chipLeft + (g.LINK_PAD_DP + g.LINK_ICON_DP + g.LINK_GAP_DP) * dd
                    val (dstX, dstY) = EditorGeometry.textDst(labelX, baseline, xyEntry.bearing, xyEntry.base, pad)
                    addText(
                        xyEntry.index, dstX, dstY,
                        0f, 0f, xyEntry.rectW.toFloat(), xyEntry.rectH.toFloat(),
                        tintArgb = tint
                    )
                    clearTransform()
                }
                // Keyframe-all: two overlapping diamonds.
                run {
                    val cx = g.rightBoxCentreXPx(w, kfAllIdx, dd)
                    val alphaMul = if (keyframeEnabled) 1f else 0.35f
                    setScaleAbout(cx, cy, kfAllScale.value)
                    val off = 5f * dd
                    for (dcx in listOf(cx - off, cx + off)) {
                        val pts = EditorGeometry.ValueHeader.diamondOutline(dcx, cy, dd)
                        val xs = FloatArray(pts.size / 2) { pts[it * 2] }
                        val ys = FloatArray(pts.size / 2) { pts[it * 2 + 1] }
                        if (kfAllOn) {
                            addFilledPolygon(xs, ys, xs.size, AmOrange.copy(alpha = alphaMul).toArgb())
                        } else {
                            addFilledPolygon(xs, ys, xs.size, AmPanelHigh.copy(alpha = alphaMul).toArgb())
                            addPolyline(
                                xs, ys, xs.size, true,
                                EditorGeometry.ValueHeader.DIAMOND_BORDER_DP * dd,
                                (if (kfAnyOn) AmOrange else AmTextDim).copy(alpha = alphaMul).toArgb()
                            )
                        }
                    }
                    clearTransform()
                }
                // The easing button's open-state circle.
                if (easingOpen) {
                    val ecx = g.rightBoxCentreXPx(w, easingIdx, dd)
                    setScaleAbout(ecx, cy, easingScale.value)
                    addCircle(ecx, cy, g.ICON_BOX_DP * dd / 2f, AmPanelHigh.toArgb())
                    clearTransform()
                }
                // Close chevron.
                if (onClose != null && closeEntry != null) {
                    val ccx = g.rightBoxCentreXPx(w, 0, dd)
                    setScaleAbout(ccx, cy, closeScale.value)
                    addText(
                        closeEntry.index,
                        ccx - chevronPx / 2f - pad, cy - chevronPx / 2f - pad,
                        0f, 0f, closeEntry.rectW.toFloat(), closeEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
            }
        )

        // The easing tile (native since build 241) over its box.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32 * easingIdx).dp)
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
                }
            )
        }

        // Tap boxes at the drawn boxes.
        if (show3d) {
            TransformTab.values().forEachIndexed { i, tab ->
                ChromePressBox(
                    segScales[i], 0.94f, { onTab(tab) },
                    Modifier
                        .offset(x = (g.segLeftPx(i, density) / density).dp, y = g.SEG_PAD_DP.dp)
                        .size(width = g.SEG_W_DP.dp, height = g.SEG_H_DP.dp),
                    description = tab.title
                )
            }
        }
        if (showLink && xyEntry != null) {
            val chipWDp = (g.linkChipWPx(xyEntry.advance, density) / density).dp
            ChromePressBox(
                linkScale, 0.94f, onToggleLink,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-32 * boxesFromRight).dp, y = g.SEG_PAD_DP.dp)
                    .size(width = chipWDp, height = g.SEG_H_DP.dp),
                description = "Link X and Y"
            )
        }
        ChromePressBox(
            kfAllScale, 0.94f, onKeyframeAll,
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32 * kfAllIdx).dp)
                .size(32.dp),
            description = "Keyframe all"
        )
        ChromePressBox(
            easingScale, 0.94f, { if (easingEnabled) onEasing() },
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32 * easingIdx).dp)
                .size(32.dp),
            tag = "transform_easing_button"
        )
        if (onClose != null) {
            ChromePressBox(
                closeScale, 0.94f, onClose,
                Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
                tag = "panel_back_button",
                description = "Back"
            )
        }
    }
}

/**
 * Build 255: one value card — keyframe diamond + label + big value
 * over the native ruler strip. Scrubbing the strip is the build-241
 * [RulerDial] (drag + fling, one tick = one unit); a double tap on
 * the card resets the property.
 */
@Composable
private fun TransformValueCard(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    active: Boolean,
    kfEnabled: Boolean,
    kfHas: Boolean,
    kfOn: Boolean,
    kfTag: Boolean,
    onScrub: (Float) -> Unit,
    onDiamond: () -> Unit,
    onActivate: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = EditorGeometry.TransformPanel
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val addPainter = rememberVectorPainter(Icons.Default.Add)
    val addPx = (EditorGeometry.ValueHeader.ADD_ICON_DP * density).roundToInt()

    fun resolveCard(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val add = atlas.resolveIcon("tfcard:add", addPx, addPx) { cv, x, y ->
            val b = rasterizePainterBitmap(addPainter, addPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        return Triple(
            add,
            atlas.resolve(label, UiLabelStyle.ED_LABEL),
            atlas.resolve(valueText, UiLabelStyle.TF_VALUE)
        )
    }
    var cardEntries = resolveCard()
    if (atlas.overflowed) {
        atlas.reset()
        cardEntries = resolveCard()
    }
    val (addEntry, labelEntry, valueEntry) = cardEntries
    val diamondScale = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(modifier = modifier.transformCardGestures(onActivate, onReset)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                cardEntries, active, kfEnabled, kfHas, kfOn,
                diamondScale.value, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CARD_CORNER_DP * dd,
                    AmPanelHigh.toArgb(),
                    (if (active) 1.5f else 1f) * dd,
                    (if (active) AmLime else AmHairline).toArgb()
                )
                val cy = g.topRowCentreYPx(dd)
                if (labelEntry != null) {
                    val style = UiLabelStyle.ED_LABEL
                    val fm = atlas.fontMetrics(style)
                    val baseline = PanelGeometry.lineBaselinePx(
                        g.CARD_TOP_PAD_DP * dd, g.TOP_ROW_H_DP * dd, fm.ascent, fm.descent
                    )
                    val (dstX, dstY) = EditorGeometry.textDst(
                        g.labelOriginXPx(dd), baseline, labelEntry.bearing, labelEntry.base, pad
                    )
                    addText(
                        labelEntry.index, dstX, dstY,
                        0f, 0f, labelEntry.rectW.toFloat(), labelEntry.rectH.toFloat(),
                        tintArgb = AmTextDim.toArgb()
                    )
                }
                if (valueEntry != null) {
                    val style = UiLabelStyle.TF_VALUE
                    val fm = atlas.fontMetrics(style)
                    val baseline = PanelGeometry.lineBaselinePx(
                        g.CARD_TOP_PAD_DP * dd, g.TOP_ROW_H_DP * dd, fm.ascent, fm.descent
                    )
                    val originX = g.valueOriginXPx(w, valueEntry.advance, dd)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        originX, baseline, valueEntry.bearing, valueEntry.base, pad
                    )
                    addText(
                        valueEntry.index, dstX, dstY,
                        0f, 0f, valueEntry.rectW.toFloat(), valueEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                setScaleAbout(g.diamondCentreXPx(dd), cy, diamondScale.value)
                drawKfDiamond(
                    addEntry, pad, g.diamondCentreXPx(dd), cy, dd,
                    AmPanel.toArgb(), kfOn, kfHas, kfEnabled
                )
                clearTransform()
            }
        )
        // The ruler strip: the native dial (drag scrub + fling).
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(g.STRIP_H_DP.dp)
        ) {
            RulerDial(
                value = value,
                onValueChange = onScrub,
                min = min,
                max = max,
                step = 1f,
                modifier = Modifier.fillMaxSize()
            )
        }
        ChromePressBox(
            diamondScale, 0.94f, onDiamond,
            Modifier
                .offset(x = g.CARD_PAD_DP.dp, y = g.CARD_TOP_PAD_DP.dp)
                .size(g.DIAMOND_BOX_DP.dp),
            tag = if (kfTag) "transform_keyframe_button" else null,
            description = "Keyframe"
        )
    }
}

/**
 * Build 255: the linked-XY card — the native position pad (build-184
 * snapper wiring lives in the caller's [padContent]) with the X/Y
 * readout at the top-left and the joint POSITION diamond at the
 * top-right. A double tap centres (X = Y = 0, written raw).
 */
@Composable
private fun TransformPadCard(
    x: Float,
    y: Float,
    active: Boolean,
    kfEnabled: Boolean,
    kfHas: Boolean,
    kfOn: Boolean,
    kfTag: Boolean,
    onDiamond: () -> Unit,
    onActivate: () -> Unit,
    onCentre: () -> Unit,
    modifier: Modifier = Modifier,
    padContent: @Composable () -> Unit
) {
    val g = EditorGeometry.TransformPanel
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val addPainter = rememberVectorPainter(Icons.Default.Add)
    val addPx = (EditorGeometry.ValueHeader.ADD_ICON_DP * density).roundToInt()
    val xText = "${x.roundToInt()}"
    val yText = "${y.roundToInt()}"

    fun resolvePad(): List<UiLabelAtlas.Entry?> = listOf(
        atlas.resolveIcon("tfcard:add", addPx, addPx) { cv, px, py ->
            val b = rasterizePainterBitmap(addPainter, addPx, density)
            cv.drawBitmap(b, px, py, null)
            b.recycle()
        },
        atlas.resolve("X", UiLabelStyle.TEXT_12),
        atlas.resolve(xText, UiLabelStyle.TEXT_12_MED),
        atlas.resolve("Y", UiLabelStyle.TEXT_12),
        atlas.resolve(yText, UiLabelStyle.TEXT_12_MED)
    )
    var padEntries = resolvePad()
    if (atlas.overflowed) {
        atlas.reset()
        padEntries = resolvePad()
    }
    val (addEntry, xLabelEntry, xValueEntry, yLabelEntry, yValueEntry) = padEntries
    val diamondScale = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(modifier = modifier.transformCardGestures(onActivate, onCentre)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(active),
            buildCmds = { w, h, dd ->
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CARD_CORNER_DP * dd,
                    AmPanelHigh.toArgb(),
                    (if (active) 1.5f else 1f) * dd,
                    (if (active) AmLime else AmHairline).toArgb()
                )
            }
        )
        padContent()
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(padEntries, kfEnabled, kfHas, kfOn, diamondScale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                // Readout: "X <v>  Y <v>" at the pad's top-left.
                val runs = listOf(
                    xLabelEntry to AmTextDim, xValueEntry to TextPrimary,
                    yLabelEntry to AmTextDim, yValueEntry to TextPrimary
                )
                if (runs.all { it.first != null }) {
                    val advances = runs.map { it.first!!.advance }
                    val lefts = g.readoutLeftsPx(advances, dd, g.READOUT_LEFT_DP * dd)
                    runs.forEachIndexed { i, (entry, tint) ->
                        val e = entry!!
                        val style = if (i % 2 == 0) UiLabelStyle.TEXT_12 else UiLabelStyle.TEXT_12_MED
                        val fm = atlas.fontMetrics(style)
                        val baseline = PanelGeometry.lineBaselinePx(
                            g.READOUT_TOP_DP * dd, atlas.textHeightPx(style).toFloat(), fm.ascent, fm.descent
                        )
                        val (dstX, dstY) = EditorGeometry.textDst(lefts[i], baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = tint.toArgb()
                        )
                    }
                }
                // Joint POSITION diamond, top-right.
                val dcx = g.padDiamondCentreXPx(w, dd)
                val dcy = g.padDiamondCentreYPx(dd)
                setScaleAbout(dcx, dcy, diamondScale.value)
                drawKfDiamond(addEntry, pad, dcx, dcy, dd, AmPanel.toArgb(), kfOn, kfHas, kfEnabled)
                clearTransform()
            }
        )
        ChromePressBox(
            diamondScale, 0.94f, onDiamond,
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-g.PAD_DIAMOND_INSET_DP).dp, y = g.PAD_DIAMOND_INSET_DP.dp)
                .size(g.DIAMOND_BOX_DP.dp),
            tag = if (kfTag) "transform_keyframe_button" else null,
            description = "Keyframe"
        )
    }
}

/** Build 255: the 3D grid's fourth cell — a centred Reset 3D action card. */
@Composable
private fun TransformResetCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = EditorGeometry.TransformPanel
    val atlas = rememberUiAtlas()
    fun resolveReset() = atlas.resolve(text, UiLabelStyle.SHEET_ITEM_MED)
    var entry = resolveReset()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveReset()
    }
    val e = entry
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    Box(modifier = modifier) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CARD_CORNER_DP * dd,
                    AmPanelHigh.toArgb(), 1f * dd, AmHairline.toArgb()
                )
                if (e != null) {
                    val style = UiLabelStyle.SHEET_ITEM_MED
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, UiLabelAtlas.PAD.toFloat())
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            description = text
        )
    }
}

/** Build 255: the quick-action row — equal chips, one GL surface each. */
@Composable
private fun TransformQuickRow(
    chips: List<TransformQuickChip>,
    modifier: Modifier = Modifier
) {
    val g = EditorGeometry.TransformPanel
    val atlas = rememberUiAtlas()
    fun resolveChips() = chips.map { atlas.resolve(it.label, UiLabelStyle.SHEET_ITEM_MED) }
    var chipEntries = resolveChips()
    if (atlas.overflowed) {
        atlas.reset()
        chipEntries = resolveChips()
    }
    val scales = remember(chips.size) { chips.map { androidx.compose.animation.core.Animatable(1f) } }
    Row(
        modifier = modifier.fillMaxWidth().height(g.QUICK_H_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(g.QUICK_GAP_DP.dp)
    ) {
        chips.forEachIndexed { i, chip ->
            val e = chipEntries.getOrNull(i)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = atlas,
                    drawKey = listOf(e, chip.active, scales[i].value, atlas.generation),
                    buildCmds = { w, h, dd ->
                        addRoundRect(
                            w / 2f, h / 2f, w, h, g.QUICK_CORNER_DP * dd,
                            AmPanelHigh.toArgb(), 1f * dd, AmHairline.toArgb()
                        )
                        if (e != null) {
                            val style = UiLabelStyle.SHEET_ITEM_MED
                            val textH = atlas.textHeightPx(style)
                            val fm = atlas.fontMetrics(style)
                            val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                            val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                            val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                            val (dstX, dstY) = EditorGeometry.textDst(
                                originX, baseline, e.bearing, e.base, UiLabelAtlas.PAD.toFloat()
                            )
                            setScaleAbout(w / 2f, h / 2f, scales[i].value)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = (if (chip.active) AmLime else TextPrimary).toArgb()
                            )
                            clearTransform()
                        }
                    }
                )
                ChromePressBox(
                    scales[i], 0.94f, chip.action,
                    Modifier.fillMaxSize(),
                    description = chip.label
                )
            }
        }
    }
}
