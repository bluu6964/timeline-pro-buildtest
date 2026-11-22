package com.example.ui.components
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.foundation.background
import com.example.engine.UiGlNative
import com.example.engine.UiMathNative
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimProp
import com.example.model.Easing
import com.example.model.at
import com.example.model.indexOnTime
import com.example.model.segmentIndexAt
import com.example.model.FILL_GRADIENT
import com.example.model.FILL_IMAGE
import com.example.model.IMG_FILL
import com.example.model.IMG_FIT
import com.example.model.IMG_STRETCH
import com.example.model.FILL_NONE
import com.example.model.FILL_SOLID
import com.example.model.GRAD_LINEAR
import com.example.model.GRAD_RADIAL
import com.example.model.GRAD_SWEEP
import com.example.model.TimelineClip
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCornerPanel
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmMint
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.amGlassBrush
import com.example.ui.util.bouncyClickable
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// B/W language: the sheet palette points at the shared monochrome design tokens
// (flat panel, raised chip tone, recessed base, white accent). Same geometry and layout.
private val CePanelBg = AmPanel
private val CeTabBg = AmPanelHigh
private val CeInnerBg = AmBase
private val CeAccent = AmMint

private val CeSwatches = listOf(
    0xFFFF4444, 0xFFF5B342, 0xFFFAE764, 0xFF1FD91F, 0xFF18E2E2, 0xFF3F4DF2, 0xFFD73FDA,
    0xFFFFFFFF, 0xFFBDBDBD, 0xFF8A8A8A, 0xFF474747, 0xFF000000, 0xFF5BB5D9, 0xFF96C99B
)

/**
 * "Color" editor of a shape or text layer (Alight Motion style): four tabs on top
 * (no fill / solid colour / gradient / image fill) and the matching editor below.
 * Every change is applied live to the layer, so the canvas above shows it straight away.
 */
@Composable
fun ColorEditSheet(
    /** The stored layer (with its keyframes); the sheet shows it as it looks at [currentPlayheadMs]. */
    rawClip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    /** Opens the device picture picker; the caller stores the chosen picture on the layer. */
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
    fullHeight: Dp? = null,
    /** Back button of the top card (same as the Transform panel); null = no back button. */
    onBack: (() -> Unit)? = null,
    currentPlayheadMs: Long = 0L,
    /** Diamond: add / remove a keyframe of these colour properties at the playhead. */
    onToggleKeyframes: (List<AnimProp>) -> Unit = {},
    /** Easing editor: a change is about to start (one undo step) / the curve of the segment starting at the keyframe time changed. */
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> }
) {
    // The layer as it looks at the playhead (colour keyframes applied).
    val clip = rawClip.at(currentPlayheadMs)
    // -1 = gradient overview, 0 / 1 = editing the colour of that gradient stop.
    var editingStop by remember(clip.id) { mutableIntStateOf(-1) }

    // Build 252 (UI migration stage 5): the sheet's glass body (flat
    // AmPanel fill + hairline edge) is drawn in GL behind the column;
    // the column keeps the touch-swallowing clickable and the tag.
    GlPanelBody(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullHeight != null) Modifier.height(fullHeight) else Modifier),
        cornerDp = AmCornerPanel.value,
        fillArgb = AmPanel.toArgb(),
        borderArgb = AmHairline.toArgb()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("color_edit_sheet")
    ) {
        // ---- Top card: back + live value + keyframe diamond + easing (same as the Transform panel) ----
        val headerText = when (clip.fillMode) {
            FILL_NONE -> "No fill"
            FILL_GRADIENT -> when (editingStop) {
                0 -> hexLabel(clip.gradColor1)
                1 -> hexLabel(clip.gradColor2)
                else -> "Gradient"
            }
            FILL_IMAGE -> "Image"
            else -> hexLabel(clip.textColor)
        }
        // Colour properties that are open right now (none = nothing to animate).
        val curProps: List<AnimProp> = when (clip.fillMode) {
            FILL_SOLID -> listOf(AnimProp.FILL_COLOR)
            FILL_GRADIENT -> when (editingStop) {
                0 -> listOf(AnimProp.GRAD_COLOR_1)
                1 -> listOf(AnimProp.GRAD_COLOR_2)
                else -> listOf(AnimProp.GRAD_COLOR_1, AnimProp.GRAD_COLOR_2)
            }
            else -> emptyList()
        }
        val relMs = currentPlayheadMs - rawClip.startMs
        val insideClip = currentPlayheadMs >= rawClip.startMs && currentPlayheadMs <= rawClip.endMs
        val onKeyframe = curProps.any { rawClip.keyframes[it].orEmpty().indexOnTime(relMs) >= 0 }
        val hasKeyframes = curProps.any { rawClip.keyframes[it].orEmpty().isNotEmpty() }
        // Easing works between two keyframes; with two properties (gradient overview) the first one that has a segment is shown.
        val easingProp = curProps.firstOrNull { rawClip.keyframes[it].orEmpty().segmentIndexAt(relMs) >= 0 }
        val kfList = easingProp?.let { rawClip.keyframes[it].orEmpty() }.orEmpty()
        val segIdx = if (easingProp != null) kfList.segmentIndexAt(relMs) else -1
        val easingEnabled = segIdx >= 0
        var showEasing by remember { mutableStateOf(false) }
        LaunchedEffect(easingEnabled, editingStop, clip.fillMode) { if (!easingEnabled) showEasing = false }
        LaunchedEffect(editingStop, clip.fillMode) { showEasing = false }
        val segStart = if (segIdx >= 0) kfList[segIdx] else null
        val segEnd = if (segIdx >= 0) kfList[segIdx + 1] else null

        TransformValueHeader(
            valueText = headerText,
            onBack = onBack,
            keyframeEnabled = insideClip && curProps.isNotEmpty(),
            hasKeyframes = hasKeyframes,
            onKeyframeNow = onKeyframe,
            onKeyframe = { onToggleKeyframes(curProps) },
            easingEnabled = easingEnabled,
            easingOpen = showEasing && easingEnabled,
            easing = segStart?.easing,
            onEasing = { if (easingEnabled) showEasing = !showEasing }
        )

        Spacer(Modifier.height(3.dp))

        if (showEasing && segStart != null && segEnd != null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EasingEditor(
                    easing = segStart.easing,
                    progress = ((relMs - segStart.timeMs).toFloat() /
                        (segEnd.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                    onBegin = onEasingBegin,
                    onChange = { e -> curProps.forEach { onEasingChange(it, segStart.timeMs, e) } }
                )
            }
        } else
        when (clip.fillMode) {
            FILL_NONE -> GlTextBlock(
                text = "No fill — the layer shows no colour.\nPick Solid or Gradient to color it again.",
                style = UiLabelStyle.SHEET_ITEM,
                colorArgb = TextSecondary.toArgb(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            FILL_GRADIENT -> {
                if (editingStop < 0) {
                    GradientOverview(
                        clip = clip,
                        onUpdate = onUpdate,
                        onEditStop = { editingStop = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    val stop = editingStop
                    CeIconTextRow(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Back to gradient",
                        text = if (stop == 0) "Gradient · start color" else "Gradient · end color",
                        iconDp = 15f,
                        iconTextGapDp = 5f,
                        padHDp = 8f,
                        padVDp = 4f,
                        pressScale = 0.92f,
                        tag = "color_gradient_back",
                        style = UiLabelStyle.ED_TEXT_MED,
                        iconTint = TextPrimary,
                        onClick = { editingStop = -1 }
                    )
                    Spacer(Modifier.height(6.dp))
                    ColorPickerPanel(
                        color = if (stop == 0) clip.gradColor1 else clip.gradColor2,
                        onColor = { c ->
                            onUpdate {
                                if (stop == 0) it.copy(gradColor1 = c) else it.copy(gradColor2 = c)
                            }
                        },
                        onEyedropper = onEyedropper,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            FILL_IMAGE -> ImageFillPanel(
                clip = clip,
                onUpdate = onUpdate,
                onPickImage = onPickImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            else -> ColorPickerPanel(
                color = clip.textColor,
                onColor = { c -> onUpdate { it.copy(textColor = c, fillMode = FILL_SOLID) } },
                onEyedropper = onEyedropper,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        Spacer(Modifier.height(6.dp))

        // ---- Bottom card: none / solid / gradient / image (smaller) ------------------
        // Build 252 (stage 5): the tab row is drawn in GL — the
        // CeTabBg bar, the selected tab's CeInnerBg fill, the icons
        // (accent when selected, white otherwise); the four weight-1
        // tap boxes keep the old tags and press scale.
        val tabDefs = listOf(
            FillTabDef(Icons.Default.Block, "No fill", FILL_NONE, "color_tab_none") {
                onUpdate { it.copy(fillMode = FILL_NONE) }
            },
            FillTabDef(Icons.Default.FormatColorFill, "Solid color", FILL_SOLID, "color_tab_solid") {
                onUpdate { it.copy(fillMode = FILL_SOLID) }
            },
            FillTabDef(Icons.Default.Gradient, "Gradient", FILL_GRADIENT, "color_tab_gradient") {
                editingStop = -1
                onUpdate { it.copy(fillMode = FILL_GRADIENT) }
            },
            FillTabDef(Icons.Default.Image, "Image fill", FILL_IMAGE, "color_tab_image") {
                if (clip.fillImageUri == null) onPickImage()
                else onUpdate { it.copy(fillMode = FILL_IMAGE) }
            }
        )
        val tabAtlas = rememberUiAtlas()
        val tabPad = UiLabelAtlas.PAD.toFloat()
        val tabDensity = androidx.compose.ui.platform.LocalDensity.current.density
        val tabPainters = tabDefs.map { def -> key(def.tag) { rememberVectorPainter(def.icon) } }
        val tabIconPx = (19f * tabDensity).roundToInt()
        fun resolveTabs() = tabDefs.mapIndexed { i, def ->
            tabAtlas.resolveIcon("filltab:${def.tag}", tabIconPx, tabIconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(tabPainters[i], tabIconPx, tabDensity)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        }
        var tabEntries = resolveTabs()
        if (tabAtlas.overflowed) {
            tabAtlas.reset()
            tabEntries = resolveTabs()
        }
        val tabScales = remember { tabDefs.map { androidx.compose.animation.core.Animatable(1f) } }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val tabW = maxWidth / 4
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = tabAtlas,
                drawKey = listOf(tabEntries, tabScales.map { it.value }, clip.fillMode, tabAtlas.generation),
                buildCmds = { w, h, _ ->
                    addRect(0f, 0f, w, h, CeTabBg.toArgb())
                    tabDefs.forEachIndexed { i, def ->
                        val selected = clip.fillMode == def.mode
                        val left = w / 4f * i
                        val cx = left + w / 8f
                        if (selected) addRect(left, 0f, w / 4f, h, CeInnerBg.toArgb())
                        val e = tabEntries[i] ?: return@forEachIndexed
                        setScaleAbout(cx, h / 2f, tabScales[i].value)
                        addText(
                            e.index,
                            cx - tabIconPx / 2f - tabPad, h / 2f - tabIconPx / 2f - tabPad,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = (if (selected) CeAccent else Color.White).toArgb()
                        )
                        clearTransform()
                    }
                }
            )
            tabDefs.forEachIndexed { i, def ->
                ChromePressBox(
                    tabScales[i], 0.94f, def.onClick,
                    Modifier
                        .offset(x = tabW * i)
                        .width(tabW)
                        .fillMaxHeight(),
                    tag = def.tag,
                    description = def.description
                )
            }
        }
    }
    }
}

/** One fill-mode tab of the colour sheet's bottom card (build 252: GL-drawn). */
private class FillTabDef(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val mode: Int,
    val tag: String,
    val onClick: () -> Unit
)

/**
 * Build 252 (stage 5): one hug-width icon + text row of the colour
 * sheet (gradient back, swap colours, choose picture) drawn in GL:
 * the flat CeTabBg behind, icon and label over it. As in the Compose
 * rows, the press scale covers the padded content (icon + text), not
 * the background, which sat outside bouncyClickable's graphicsLayer.
 */
@Composable
private fun CeIconTextRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    text: String,
    iconDp: Float,
    iconTextGapDp: Float,
    padHDp: Float,
    padVDp: Float,
    pressScale: Float,
    tag: String,
    style: UiLabelStyle,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val painter = rememberVectorPainter(icon)
    val iconPx = (iconDp * density).roundToInt()

    fun resolveRow(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val ie = atlas.resolveIcon("ce-row:$tag:${icon.name}", iconPx, iconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(painter, iconPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val te = atlas.resolve(text, style)
        return ie to te
    }
    var rowEntries = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        rowEntries = resolveRow()
    }
    val (iconEntry, textEntry) = rowEntries
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val textH = atlas.textHeightPx(style)
    val rowH = maxOf(iconDp * density, textH) + 2f * padVDp * density
    val rowW = (padHDp + iconTextGapDp + padHDp) * density + iconPx + (textEntry?.advance ?: 0f)

    Box(
        modifier = modifier
            .width((rowW / density).dp)
            .height((rowH / density).dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(iconEntry, textEntry, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                addRect(0f, 0f, w, h, CeTabBg.toArgb())
                setScaleAbout(w / 2f, h / 2f, scale.value)
                if (iconEntry != null) {
                    addText(
                        iconEntry.index,
                        padHDp * dd - pad, (h - iconPx) / 2f - pad,
                        0f, 0f, iconEntry.rectW.toFloat(), iconEntry.rectH.toFloat(),
                        tintArgb = iconTint.toArgb()
                    )
                }
                if (textEntry != null) {
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = (padHDp + iconTextGapDp) * dd + iconPx
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, textEntry.bearing, textEntry.base, pad)
                    addText(
                        textEntry.index, dstX, dstY,
                        0f, 0f, textEntry.rectW.toFloat(), textEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, pressScale, onClick,
            Modifier.fillMaxSize(),
            tag = tag,
            description = description
        )
    }
}

// =====================================================================================
// Gradient
// =====================================================================================

@Composable
private fun GradientOverview(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEditStop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c1 = Color(clip.gradColor1)
    val c2 = Color(clip.gradColor2)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gradient type: linear / radial / sweep
        // Build 252 (stage 5): the row's bar, selected fills and the
        // tiles' border rings are drawn in GL; the shader tiles stay
        // native canvases at their exact rects (26 dp box, 2 dp inset).
        val typeDefs = listOf(GRAD_LINEAR to "Linear", GRAD_RADIAL to "Radial", GRAD_SWEEP to "Sweep")
        val typeScales = remember { typeDefs.map { androidx.compose.animation.core.Animatable(1f) } }
        Box(modifier = Modifier.size(width = 174.dp, height = 40.dp)) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                drawKey = listOf(clip.gradType, typeScales.map { it.value }),
                buildCmds = { w, h, dd ->
                    addRect(0f, 0f, w, h, CeTabBg.toArgb())
                    typeDefs.forEachIndexed { i, (type, _) ->
                        val left = i * 58f * dd
                        val cx = left + 29f * dd
                        val cy = h / 2f
                        if (clip.gradType == type) {
                            addRect(left, 0f, 58f * dd, h, CeInnerBg.toArgb())
                        }
                        setScaleAbout(cx, cy, typeScales[i].value)
                        addRoundRect(
                            cx, cy, 26f * dd, 26f * dd, 5f * dd, 0,
                            1.75f * dd,
                            (if (clip.gradType == type) CeAccent else Color.White).toArgb()
                        )
                        clearTransform()
                    }
                }
            )
            typeDefs.forEachIndexed { i, (type, name) ->
                Box(
                    modifier = Modifier
                        .offset(x = (58 * i).dp)
                        .size(width = 58.dp, height = 40.dp)
                        .graphicsLayer {
                            scaleX = typeScales[i].value
                            scaleY = typeScales[i].value
                        },
                    contentAlignment = Alignment.Center
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier
                            .size(26.dp)
                            .padding(2.dp),
                        drawKey = Triple(type, clip.gradColor1, clip.gradColor2),
                        buildCmds = { w, h, d ->
                            val qMode = when (type) {
                                GRAD_RADIAL -> 1f
                                GRAD_SWEEP -> 2f
                                else -> 0f
                            }
                            addShaderQuad(
                                0f, 0f, w, h, 4f * d, UiGlNative.QUAD_GRAD_TILE,
                                p0 = qMode,
                                c0Argb = clip.gradColor1.toInt(),
                                c1Argb = clip.gradColor2.toInt()
                            )
                        },
                    )
                }
                ChromePressBox(
                    typeScales[i], 0.92f, { onUpdate { it.copy(gradType = type) } },
                    Modifier
                        .offset(x = (58 * i).dp)
                        .size(width = 58.dp, height = 40.dp),
                    tag = "gradient_type_$name",
                    description = name
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Gradient bar with its two colour stops (tap a stop to change its colour)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(3.dp)),
                drawKey = Pair(clip.gradColor1, clip.gradColor2),
                buildCmds = { w, h, d ->
                    addShaderQuad(
                        0f, 0f, w, h, 0f, UiGlNative.QUAD_CHECKER,
                        p0 = 7f * d,
                        c0Argb = 0xFF8A8A8A.toInt(),
                        c1Argb = 0xFFCFCFCF.toInt()
                    )
                    addGradRect(0f, 0f, w, h, vertical = false, c0Argb = clip.gradColor1.toInt(), c1Argb = clip.gradColor2.toInt())
                },
            )
            StopHandle(c1, "color_stop_start", Modifier.align(Alignment.CenterStart)) { onEditStop(0) }
            StopHandle(c2, "color_stop_end", Modifier.align(Alignment.CenterEnd)) { onEditStop(1) }
        }

        Spacer(Modifier.height(4.dp))
        GlTextBlock(
            text = "Tap a color box to change it.\nDrag the two points on the canvas to move the gradient.",
            style = UiLabelStyle.SHEET_DESC,
            colorArgb = TextSecondary.toArgb(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))
        CeIconTextRow(
            icon = Icons.Default.SwapHoriz,
            description = "Swap colors",
            text = "Swap colors",
            iconDp = 16f,
            iconTextGapDp = 6f,
            padHDp = 12f,
            padVDp = 7f,
            pressScale = 0.94f,
            tag = "gradient_swap",
            style = UiLabelStyle.SHEET_ITEM_MED,
            iconTint = Color.White,
            onClick = {
                onUpdate { it.copy(gradColor1 = it.gradColor2, gradColor2 = it.gradColor1) }
            }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StopHandle(color: Color, tag: String, modifier: Modifier, onClick: () -> Unit) {
    // Build 252 (stage 5): the handle's flat layers are drawn in GL —
    // the white 36 dp frame and the colour inset by 2.5 dp. (The old
    // chain also painted a grey layer under the colour box, fully
    // covered by it; the visible stack is frame + colour.)
    Box(
        modifier = modifier
            .size(36.dp)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick)
            .testTag(tag)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = color.toArgb(),
            buildCmds = { w, h, dd ->
                addRect(0f, 0f, w, h, Color.White.toArgb())
                val inset = 2.5f * dd
                addRect(inset, inset, w - 2f * inset, h - 2f * inset, color.toArgb())
            }
        )
    }
}

// =====================================================================================
// Solid colour picker: wheel view / swatch view / eyedropper
// =====================================================================================

@Composable
private fun ColorPickerPanel(
    color: Long,
    onColor: (Long) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableIntStateOf(0) } // 0 = wheel, 1 = swatches
    val onColorNow by rememberUpdatedState(onColor)

    // Build 252 (stage 5): the panel's CeInnerBg body is drawn in GL
    // (GlPanelBody, square corners — the old clip was RectangleShape).
    GlPanelBody(
        modifier = modifier,
        cornerDp = 0f,
        fillArgb = CeInnerBg.toArgb()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (view == 0) {
                    WheelPicker(color = color, onColor = { onColorNow(it) }, modifier = Modifier.fillMaxSize())
                } else {
                    SwatchGrid(color = color, onColor = { onColorNow(it) }, modifier = Modifier.fillMaxSize())
                }
            }
            ColorRailGl(
                view = view,
                onEyedropper = { onEyedropper { picked -> onColorNow(picked) } },
                onWheel = { view = 0 },
                onSwatches = { view = 1 }
            )
        }
    }
}

/**
 * Build 252 (stage 5): the colour picker's right rail (eyedropper /
 * wheel / swatches) drawn in GL: the three weight-1 buttons' fills
 * (CeTabBg, or AmMint at 16 % for the active view) and the two
 * rasterized icons; the wheel button's hue ring stays its own native
 * canvas over the surface. As in the Compose buttons, the press
 * scale covers the icon (the fill sat outside the graphicsLayer).
 */
@Composable
private fun ColorRailGl(
    view: Int,
    onEyedropper: () -> Unit,
    onWheel: () -> Unit,
    onSwatches: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val dropperPainter = rememberVectorPainter(Icons.Default.Colorize)
    val appsPainter = rememberVectorPainter(Icons.Default.Apps)
    val iconPx = (20f * density).roundToInt()

    fun resolveRail(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val dropper = atlas.resolveIcon("colorrail:colorize", iconPx, iconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(dropperPainter, iconPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val apps = atlas.resolveIcon("colorrail:apps", iconPx, iconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(appsPainter, iconPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        return dropper to apps
    }
    var railEntries = resolveRail()
    if (atlas.overflowed) {
        atlas.reset()
        railEntries = resolveRail()
    }
    val (dropperEntry, appsEntry) = railEntries
    val scales = remember { List(3) { androidx.compose.animation.core.Animatable(1f) } }
    val clicks = listOf(onEyedropper, onWheel, onSwatches)
    val tags = listOf("color_eyedropper", "color_view_wheel", "color_view_swatches")
    val descriptions = listOf("Pick from canvas", null, "Swatches")

    BoxWithConstraints(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
    ) {
        val thirdH = maxHeight / 3
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(dropperEntry, appsEntry, scales.map { it.value }, view, atlas.generation),
            buildCmds = { w, h, dd ->
                val third = h / 3f
                val inset = 2f * dd
                for (i in 0..2) {
                    val selected = (i == 1 && view == 0) || (i == 2 && view == 1)
                    addRect(
                        inset, third * i + inset, w - 2f * inset, third - 2f * inset,
                        (if (selected) AmMint.copy(alpha = 0.16f) else CeTabBg).toArgb()
                    )
                }
                fun blitIcon(e: UiLabelAtlas.Entry?, i: Int, tint: Color) {
                    if (e == null) return
                    val cx = w / 2f
                    val cy = third * i + third / 2f
                    setScaleAbout(cx, cy, scales[i].value)
                    addText(
                        e.index,
                        cx - iconPx / 2f - pad, cy - iconPx / 2f - pad,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = tint.toArgb()
                    )
                    clearTransform()
                }
                blitIcon(dropperEntry, 0, Color.White)
                blitIcon(appsEntry, 2, if (view == 1) CeAccent else Color.White)
            }
        )
        // The wheel button's hue ring (native) over the middle third.
        Box(
            modifier = Modifier
                .offset(y = thirdH)
                .fillMaxWidth()
                .height(thirdH)
                .graphicsLayer {
                    scaleX = scales[1].value
                    scaleY = scales[1].value
                },
            contentAlignment = Alignment.Center
        ) {
            NativeVectorCanvas(
                modifier = Modifier.size(22.dp),
                drawKey = view == 0,
                buildCmds = { w, h, d ->
                    val ringW = 5f * d
                    val r = minOf(w, h) * 0.5f - ringW * 0.5f
                    addShaderQuad(
                        0f, 0f, w, h, 0f, UiGlNative.QUAD_HUE_WHEEL,
                        p0 = r - ringW * 0.5f, p1 = r + ringW * 0.5f, p2 = 0f, p3 = 0f
                    )
                    if (view == 0) addCircle(w * 0.5f, h * 0.5f, 2.5f * d, CeAccent.toArgb())
                },
            )
        }
        for (i in 0..2) {
            ChromePressBox(
                scales[i], 0.92f, clicks[i],
                Modifier
                    .offset(x = 2.dp, y = thirdH * i + 2.dp)
                    .width(42.dp)
                    .height(thirdH - 4.dp),
                tag = tags[i],
                description = descriptions[i]
            )
        }
    }
}

@Composable
private fun SwatchGrid(color: Long, onColor: (Long) -> Unit, modifier: Modifier = Modifier) {
    val rgb = color and 0xFFFFFFL
    // Build 252 (stage 5): the header's colour fill + hex label and
    // the whole swatch grid are drawn in GL (EditorGeometry
    // .SwatchGrid); the header's checker stays its native canvas
    // under the translucent colour, exactly as the Compose stack had
    // it. Tap boxes keep the cells' press contract.
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val hexEntry = run {
        fun resolveHex() = atlas.resolve(hexLabel(color), UiLabelStyle.HEX_BIG)
        var e = resolveHex()
        if (atlas.overflowed) {
            atlas.reset()
            e = resolveHex()
        }
        e
    }
    val rows = CeSwatches.chunked(EditorGeometry.SwatchGrid.COLUMNS)
    val cellScales = remember { CeSwatches.map { androidx.compose.animation.core.Animatable(1f) } }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(6.dp)
    ) {
        // Header: current colour with its hex code and opacity
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RectangleShape)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                drawKey = 0,
                buildCmds = { w, h, d ->
                    addShaderQuad(
                        0f, 0f, w, h, 0f, UiGlNative.QUAD_CHECKER,
                        p0 = 7f * d, c0Argb = 0xFF8A8A8A.toInt(), c1Argb = 0xFFCFCFCF.toInt()
                    )
                },
            )
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(color, hexEntry, atlas.generation),
                buildCmds = { w, h, _ ->
                    addRect(0f, 0f, w, h, color.toInt())
                    if (hexEntry != null) {
                        val style = UiLabelStyle.HEX_BIG
                        val textH = atlas.textHeightPx(style)
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val originX = EditorGeometry.centredOriginPx(w / 2f, hexEntry.advance)
                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, hexEntry.bearing, hexEntry.base, pad)
                        addText(
                            hexEntry.index, dstX, dstY,
                            0f, 0f, hexEntry.rectW.toFloat(), hexEntry.rectH.toFloat(),
                            tintArgb = contrastOn(Color(color)).toArgb()
                        )
                    }
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val g = EditorGeometry.SwatchGrid
            val rowWPx = maxWidth.value * density
            val cellW = g.cellWPx(rowWPx, g.COLUMNS, density)
            val gridH = g.gridHPx(rows.size, cellW, density)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((gridH / density).dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = listOf(rgb, cellScales.map { it.value }),
                    buildCmds = { _, _, dd ->
                        CeSwatches.forEachIndexed { idx, c ->
                            val row = idx / g.COLUMNS
                            val col = idx % g.COLUMNS
                            val left = g.cellLeftPx(col, rowWPx, g.COLUMNS, dd)
                            val top = g.rowTopPx(row, cellW, dd)
                            val selected = (c and 0xFFFFFFL) == rgb
                            val cx = left + cellW / 2f
                            val cy = top + cellW / 2f
                            setScaleAbout(cx, cy, cellScales[idx].value)
                            addRoundRect(
                                cx, cy, cellW, cellW, g.CORNER_DP * dd,
                                c.toInt(),
                                (if (selected) 2f else 1f) * dd,
                                (if (selected) Color.White else Color.White.copy(alpha = 0.12f)).toArgb()
                            )
                            clearTransform()
                        }
                    }
                )
                CeSwatches.forEachIndexed { idx, c ->
                    val row = idx / g.COLUMNS
                    val col = idx % g.COLUMNS
                    ChromePressBox(
                        cellScales[idx], 0.85f, { onColor(c) },
                        Modifier
                            .offset(
                                x = (g.cellLeftPx(col, rowWPx, g.COLUMNS, density) / density).dp,
                                y = (g.rowTopPx(row, cellW, density) / density).dp
                            )
                            .size((cellW / density).dp),
                        description = hexLabel(c)
                    )
                }
            }
        }
    }
}

@Composable
private fun WheelPicker(color: Long, onColor: (Long) -> Unit, modifier: Modifier = Modifier) {
    val init = remember {
        colorToHsvNative(color.toInt())
    }
    var hue by remember { mutableFloatStateOf(init[0]) }
    var sat by remember { mutableFloatStateOf(init[1]) }
    var value by remember { mutableFloatStateOf(init[2]) }
    var alpha by remember { mutableFloatStateOf(((color shr 24) and 0xFF).toInt() / 255f) }
    var lastEmitted by remember { mutableStateOf(Long.MIN_VALUE) }
    val onColorNow by rememberUpdatedState(onColor)

    // The colour was changed from outside (swatch, eyedropper): follow it.
    LaunchedEffect(color) {
        if (color != lastEmitted) {
            val a = colorToHsvNative(color.toInt())
            hue = a[0]; sat = a[1]; value = a[2]
            alpha = ((color shr 24) and 0xFF).toInt() / 255f
        }
    }

    fun emit() {
        val argb = hsvToColorNative(
            (alpha * 255f).roundToInt().coerceIn(0, 255),
            hue.coerceIn(0f, 359.99f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)
        )
        val c = argb.toLong() and 0xFFFFFFFFL
        lastEmitted = c
        onColorNow(c)
    }

    val current = Color(
        hsvToColorNative(
            (alpha * 255f).roundToInt().coerceIn(0, 255),
            hue.coerceIn(0f, 359.99f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)
        )
    )
    val hueColor = Color(hsvToColorNative(255, hue.coerceIn(0f, 359.99f), 1f, 1f))

    BoxWithConstraints(modifier = modifier.padding(6.dp)) {
        // Two squares side by side (wheel + saturation/value) with the opacity bar underneath.
        val barH = 22.dp
        val gap = 8.dp
        val s = minOf((maxWidth - gap) / 2f, maxHeight - barH - gap).coerceAtLeast(64.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                // ---- Hue ring with the hex code in the middle ----
                Box(modifier = Modifier.size(s), contentAlignment = Alignment.Center) {
                    NativeVectorCanvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("color_hue_ring")
                            .pointerInput(Unit) {
                                detectTapGestures { o ->
                                    val ang = atan2(o.y - size.height / 2f, o.x - size.width / 2f)
                                    hue = ((Math.toDegrees(ang.toDouble()) + 360.0) % 360.0).toFloat()
                                    emit()
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val o = change.position
                                    val ang = atan2(o.y - size.height / 2f, o.x - size.width / 2f)
                                    hue = ((Math.toDegrees(ang.toDouble()) + 360.0) % 360.0).toFloat()
                                    emit()
                                }
                            },
                        drawKey = hue,
                        buildCmds = { w, h, d ->
                            val ringW = 14f * d
                            val r = minOf(w, h) * 0.5f - ringW * 0.5f
                            addShaderQuad(
                                0f, 0f, w, h, 0f, UiGlNative.QUAD_HUE_WHEEL,
                                p0 = r - ringW * 0.5f,
                                p1 = r + ringW * 0.5f,
                                p2 = hue,
                                p3 = 1f
                            )
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(s - 34.dp)
                            .clip(CircleShape)
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            drawKey = 0,
                            buildCmds = { w, h, d ->
                                addShaderQuad(
                                    0f, 0f, w, h, 0f, UiGlNative.QUAD_CHECKER,
                                    p0 = 7f * d, c0Argb = 0xFF8A8A8A.toInt(), c1Argb = 0xFFCFCFCF.toInt()
                                )
                            },
                        )
                        // Build 252 (stage 5): the current colour's disc
                        // (translucent over the checker, as the Compose
                        // background was) and the two hex lines in GL.
                        val wheelAtlas = rememberUiAtlas()
                        val wheelPad = UiLabelAtlas.PAD.toFloat()
                        fun resolveWheel() = hexLabel(current.toLongArgb(), twoLines = true)
                            .split('\n')
                            .map { wheelAtlas.resolve(it, UiLabelStyle.SMALL_MED) }
                        var wheelEntries = resolveWheel()
                        if (wheelAtlas.overflowed) {
                            wheelAtlas.reset()
                            wheelEntries = resolveWheel()
                        }
                        val wheelLines = wheelEntries
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            clearArgb = 0,
                            atlas = wheelAtlas,
                            drawKey = listOf(current.toArgb(), wheelLines, wheelAtlas.generation),
                            buildCmds = { w, h, _ ->
                                addCircle(w / 2f, h / 2f, w / 2f, current.toArgb())
                                val style = UiLabelStyle.SMALL_MED
                                val textH = wheelAtlas.textHeightPx(style)
                                val fm = wheelAtlas.fontMetrics(style)
                                var lineTop = (h - textH * wheelLines.size) / 2f
                                for (e in wheelLines) {
                                    if (e != null) {
                                        val baseline = PanelGeometry.lineBaselinePx(lineTop, textH, fm.ascent, fm.descent)
                                        val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, wheelPad)
                                        addText(
                                            e.index, dstX, dstY,
                                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                            tintArgb = contrastOn(current).toArgb()
                                        )
                                    }
                                    lineTop += textH
                                }
                            }
                        )
                    }
                }

                // ---- Saturation (x) / value (y) square ----
                NativeVectorCanvas(
                    modifier = Modifier
                        .size(s)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("color_sv_square")
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                        },
                    drawKey = Triple(hue, sat, value),
                    buildCmds = { w, h, d ->
                        addShaderQuad(
                            0f, 0f, w, h, 0f, UiGlNative.QUAD_SV_BOX,
                            p0 = hue, p1 = sat, p2 = value, p3 = 1f, p4 = 7f * d
                        )
                    },
                )
            }

            Spacer(Modifier.height(gap))

            // ---- Opacity bar ----
            NativeVectorCanvas(
                modifier = Modifier
                    .width(s * 2 + gap)
                    .height(barH)
                    .clip(RoundedCornerShape(barH / 2))
                    .testTag("color_alpha_bar")
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            alpha = (o.x / size.width).coerceIn(0f, 1f)
                            emit()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            alpha = (change.position.x / size.width).coerceIn(0f, 1f)
                            emit()
                        }
                    },
                drawKey = Pair(current.copy(alpha = 1f).toArgb(), alpha),
                buildCmds = { w, h, d ->
                    val solidArgb = current.copy(alpha = 1f).toArgb()
                    addShaderQuad(
                        0f, 0f, w, h, 0f, UiGlNative.QUAD_CHECKER,
                        p0 = 7f * d, c0Argb = 0xFF8A8A8A.toInt(), c1Argb = 0xFFCFCFCF.toInt()
                    )
                    addGradRect(0f, 0f, w, h, vertical = false, c0Argb = solidArgb and 0x00FFFFFF, c1Argb = solidArgb)
                    val r = h * 0.5f
                    val cx = (alpha * w).coerceIn(r, (w - r).coerceAtLeast(r))
                    addCircle(cx, r, r - 1f * d, 0, 3f * d, android.graphics.Color.WHITE)
                },
            )
        }
    }
}

private fun colorToHsvNative(argb: Int): FloatArray {
    val out = FloatArray(3)
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    UiMathNative.nativeRgbToHsv(r, g, b, out)
    return out
}

private fun hsvToColorNative(alpha255: Int, h: Float, s: Float, v: Float): Int {
    val rgb = FloatArray(3)
    UiMathNative.nativeHsvToRgb(h, s, v, rgb)
    val r = (rgb[0] * 255f).roundToInt().coerceIn(0, 255)
    val g = (rgb[1] * 255f).roundToInt().coerceIn(0, 255)
    val b = (rgb[2] * 255f).roundToInt().coerceIn(0, 255)
    return (alpha255.coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
}

// =====================================================================================
// Helpers
// =====================================================================================

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(area: Size) {
    val cell = 7.dp.toPx()
    var yi = 0
    var y = 0f
    while (y < area.height) {
        var xi = 0
        var x = 0f
        while (x < area.width) {
            drawRect(
                if ((xi + yi) % 2 == 0) Color(0xFF8A8A8A) else Color(0xFFCFCFCF),
                topLeft = Offset(x, y),
                size = Size(cell, cell)
            )
            x += cell; xi++
        }
        y += cell; yi++
    }
}

private fun Color.toLongArgb(): Long = this.toArgb().toLong() and 0xFFFFFFFFL

private fun hexLabel(argb: Long, twoLines: Boolean = false): String {
    val rgb = (argb and 0xFFFFFFL).toInt()
    val pct = (((argb shr 24) and 0xFF).toInt() / 255f * 100f).roundToInt()
    val sep = if (twoLines) "\n" else " "
    return String.format(Locale.US, "#%06X%s(%d%%)", rgb, sep, pct)
}

private fun contrastOn(c: Color): Color {
    // Over a see-through colour the checkerboard shows, so decide mostly on the colour itself.
    val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return if (lum > 0.62f && c.alpha > 0.5f) Color.Black else Color.White
}

/** Image fill: pick a picture from the device and choose Fill / Fit / Stretch. */
@Composable
private fun ImageFillPanel(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bmp = clip.fillImageUri?.let { com.example.engine.FillImages.peek(it) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Build 252 (stage 5): the preview's flat fill + hairline are
        // drawn in GL under the picture (a real bitmap — it stays a
        // Compose Image, the stage-4 atlas limit); the empty state's
        // icon is blitted into the same surface.
        val imgDensity = androidx.compose.ui.platform.LocalDensity.current.density
        val imgAtlas = rememberUiAtlas()
        val imgPad = UiLabelAtlas.PAD.toFloat()
        val imgPainter = rememberVectorPainter(Icons.Default.AddPhotoAlternate)
        val imgIconPx = (30f * imgDensity).roundToInt()
        fun resolveImgIcon() = imgAtlas.resolveIcon("fillimg:addphoto", imgIconPx, imgIconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(imgPainter, imgIconPx, imgDensity)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        var imgIconEntry = resolveImgIcon()
        if (imgAtlas.overflowed) {
            imgAtlas.reset()
            imgIconEntry = resolveImgIcon()
        }
        val imgIcon = imgIconEntry
        Box(
            modifier = Modifier
                .size(90.dp)
                .bouncyClickable(pressedScale = 0.95f, onClick = onPickImage)
                .testTag("fill_image_preview"),
            contentAlignment = Alignment.Center
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = imgAtlas,
                drawKey = listOf(bmp == null, imgIcon, imgAtlas.generation),
                buildCmds = { w, h, dd ->
                    addRoundRect(
                        w / 2f, h / 2f, w, h, 0f,
                        CeInnerBg.toArgb(), 1f * dd, AmHairline.toArgb()
                    )
                    if (bmp == null && imgIcon != null) {
                        addText(
                            imgIcon.index,
                            w / 2f - imgIconPx / 2f - imgPad, h / 2f - imgIconPx / 2f - imgPad,
                            0f, 0f, imgIcon.rectW.toFloat(), imgIcon.rectH.toFloat(),
                            tintArgb = Color.White.toArgb()
                        )
                    }
                }
            )
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Fill picture",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        CeIconTextRow(
            icon = Icons.Default.AddPhotoAlternate,
            description = "Choose picture",
            text = if (clip.fillImageUri == null) "Choose picture" else "Change picture",
            iconDp = 16f,
            iconTextGapDp = 6f,
            padHDp = 12f,
            padVDp = 7f,
            pressScale = 0.94f,
            tag = "fill_image_choose",
            style = UiLabelStyle.SHEET_ITEM_MED,
            iconTint = Color.White,
            onClick = onPickImage
        )
        Spacer(Modifier.height(8.dp))
        // The Fill / Fit / Stretch mode row in GL: bar, selected
        // fills, icons + labels; tap boxes keep the tags.
        val modeDefs = listOf(
            Triple(IMG_FILL, "Fill", Icons.Default.Fullscreen),
            Triple(IMG_FIT, "Fit", Icons.Default.FitScreen),
            Triple(IMG_STRETCH, "Stretch", Icons.Default.OpenInFull)
        )
        val modePainters = modeDefs.map { def -> key(def.second) { rememberVectorPainter(def.third) } }
        val modeIconPx = (19f * imgDensity).roundToInt()
        fun resolveModes(): Pair<List<UiLabelAtlas.Entry?>, List<UiLabelAtlas.Entry?>> {
            val icons = modeDefs.mapIndexed { i, def ->
                imgAtlas.resolveIcon("fillmode:${def.second}", modeIconPx, modeIconPx) { cv, x, y ->
                    val b = rasterizePainterBitmap(modePainters[i], modeIconPx, imgDensity)
                    cv.drawBitmap(b, x, y, null)
                    b.recycle()
                }
            }
            val labels = modeDefs.map { def ->
                imgAtlas.resolve(def.second, UiLabelStyle.SHEET_DESC)
            }
            return icons to labels
        }
        var modeEntries = resolveModes()
        if (imgAtlas.overflowed) {
            imgAtlas.reset()
            modeEntries = resolveModes()
        }
        val (modeIcons, modeLabels) = modeEntries
        val modeScales = remember { modeDefs.map { androidx.compose.animation.core.Animatable(1f) } }
        Box(modifier = Modifier.size(width = 210.dp, height = 46.dp)) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = imgAtlas,
                drawKey = listOf(modeIcons, modeLabels, modeScales.map { it.value }, clip.fillImageMode, imgAtlas.generation),
                buildCmds = { w, h, dd ->
                    addRect(0f, 0f, w, h, CeTabBg.toArgb())
                    modeDefs.forEachIndexed { i, def ->
                        val (mode, name, _) = def
                        val selected = clip.fillImageMode == mode
                        val left = i * 70f * dd
                        val cx = left + 35f * dd
                        if (selected) addRect(left, 0f, 70f * dd, h, CeInnerBg.toArgb())
                        val tint = (if (selected) CeAccent else Color.White).toArgb()
                        val labelTint = (if (selected) CeAccent else TextPrimary).toArgb()
                        setScaleAbout(cx, h / 2f, modeScales[i].value)
                        // Icon over label, the pair centred: icon 19,
                        // gap 2, label font box.
                        val labelStyle = UiLabelStyle.SHEET_DESC
                        val labelH = imgAtlas.textHeightPx(labelStyle)
                        val pairH = 19f * dd + 2f * dd + labelH
                        val iconTop = (h - pairH) / 2f
                        modeIcons[i]?.let { e ->
                            addText(
                                e.index,
                                cx - modeIconPx / 2f - imgPad, iconTop - imgPad,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = tint
                            )
                        }
                        modeLabels[i]?.let { e ->
                            val fm = imgAtlas.fontMetrics(labelStyle)
                            val labelTop = iconTop + 19f * dd + 2f * dd
                            val baseline = PanelGeometry.lineBaselinePx(labelTop, labelH, fm.ascent, fm.descent)
                            val originX = EditorGeometry.centredOriginPx(cx, e.advance)
                            val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, imgPad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = labelTint
                            )
                        }
                        clearTransform()
                    }
                }
            )
            modeDefs.forEachIndexed { i, def ->
                val (mode, name, _) = def
                ChromePressBox(
                    modeScales[i], 0.92f, { onUpdate { it.copy(fillImageMode = mode) } },
                    Modifier
                        .offset(x = (70 * i).dp)
                        .size(width = 70.dp, height = 46.dp),
                    tag = "fill_image_$name",
                    description = name
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
