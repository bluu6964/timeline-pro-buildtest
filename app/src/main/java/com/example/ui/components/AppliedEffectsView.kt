package com.example.ui.components
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.R
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.AspectRatio
import com.example.model.ChromeGeometry
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.AmVioletDeep
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.IconActive
import com.example.ui.theme.RulerSubTickColor
import com.example.ui.theme.RulerTickColor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable
import java.util.UUID
import kotlin.math.roundToInt

enum class AppliedEffectsScreenMode {
    LIST,         // Shows Add effect button & applied list / empty text (Images 1 & 3)
    LIBRARY,      // Shows categories & grid of effects (Image 2)
    PARAMETERS    // Shows specific effect parameter sliders (Image 4)
}

data class EffectPreset(
    val id: String,
    val name: String,
    val category: String,
    val isPro: Boolean = false,
    val defaultIntensity: Float = 20f,
    val secondaryName: String = "Rotation",
    val defaultSecondary: Float = 45f,
    val secondaryUnit: String = "°",
    val tertiaryName: String = "Edge Hardness",
    val defaultTertiary: Float = 0f,
    val tertiaryUnit: String = "%"
)

class ActiveAppliedEffect(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    initialEnabled: Boolean = true,
    initialIntensity: Float = 20f,
    val secondaryName: String = "Rotation",
    initialSecondary: Float = 45f,
    val secondaryUnit: String = "°",
    val tertiaryName: String = "Edge Hardness",
    initialTertiary: Float = 0f,
    val tertiaryUnit: String = "%"
) {
    var isEnabled by mutableStateOf(initialEnabled)
    var intensity by mutableFloatStateOf(initialIntensity)
    var secondaryValue by mutableFloatStateOf(initialSecondary)
    var tertiaryValue by mutableFloatStateOf(initialTertiary)
}

@Composable
fun AppliedEffectsView(
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    onDismiss: () -> Unit,
    onEffectApplied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current screen mode: LIST (Applied Effects), LIBRARY (Add effect picker), PARAMETERS (sliders)
    var screenMode by remember { mutableStateOf(AppliedEffectsScreenMode.LIST) }

    // Applied effects list
    val appliedEffects = remember { mutableStateListOf<ActiveAppliedEffect>() }
    var selectedEffectForEditing by remember { mutableStateOf<ActiveAppliedEffect?>(null) }

    // Library category selection
    var selectedCategory by remember { mutableStateOf("Blur") }
    val categories = listOf("Blur", "Background", "Mask & Edges")

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var currentTimeMs by remember { mutableLongStateOf(10L) } // 00:00 01
    val totalTimeMs = 37000L // 00:37 00 matching screenshots

    val blurPresets = listOf(
        EffectPreset("gaussian_blur", "Gaussian Blur", "Blur", isPro = false, defaultIntensity = 25f, secondaryName = "Radius", defaultSecondary = 15f, secondaryUnit = "px"),
        EffectPreset("mask_blur", "Mask Blur", "Blur", isPro = true, defaultIntensity = 40f, secondaryName = "Feather", defaultSecondary = 20f, secondaryUnit = "%"),
        EffectPreset("directional_blur", "Directional Blur", "Blur", isPro = false, defaultIntensity = 20f, secondaryName = "Rotation", defaultSecondary = 45f, secondaryUnit = "°", tertiaryName = "Edge Hardness", defaultTertiary = 0f, tertiaryUnit = "%"),
        EffectPreset("tilt_shift", "Tilt-Shift", "Blur", isPro = true, defaultIntensity = 30f, secondaryName = "Position", defaultSecondary = 50f, secondaryUnit = "%"),
        EffectPreset("fast_box_blur", "Fast Box Blur", "Blur", isPro = false, defaultIntensity = 15f, secondaryName = "Iterations", defaultSecondary = 3f, secondaryUnit = ""),
        EffectPreset("iris_blur", "Iris Blur", "Blur", isPro = true, defaultIntensity = 35f, secondaryName = "Radius", defaultSecondary = 60f, secondaryUnit = "%"),
        EffectPreset("edge_aware_blur", "Edge-Aware Blur", "Blur", isPro = false, defaultIntensity = 25f, secondaryName = "Threshold", defaultSecondary = 40f, secondaryUnit = "%"),
        EffectPreset("radial_spin", "Radial Spin", "Blur", isPro = true, defaultIntensity = 30f, secondaryName = "Angle", defaultSecondary = 60f, secondaryUnit = "°")
    )

    val backgroundPresets = listOf(
        EffectPreset("bg_glow", "Ambient Glow", "Background", isPro = false, defaultIntensity = 30f, secondaryName = "Spread", defaultSecondary = 40f, secondaryUnit = "%"),
        EffectPreset("dark_vignette", "Dark Vignette", "Background", isPro = false, defaultIntensity = 45f, secondaryName = "Roundness", defaultSecondary = 50f, secondaryUnit = "%"),
        EffectPreset("neon_halo", "Neon Halo", "Background", isPro = true, defaultIntensity = 50f, secondaryName = "Color Tint", defaultSecondary = 180f, secondaryUnit = "°"),
        EffectPreset("bokeh_dust", "Bokeh Dust", "Background", isPro = true, defaultIntensity = 20f, secondaryName = "Density", defaultSecondary = 30f, secondaryUnit = "%")
    )

    val maskPresets = listOf(
        EffectPreset("edge_glow", "Edge Glow", "Mask & Edges", isPro = false, defaultIntensity = 35f, secondaryName = "Width", defaultSecondary = 10f, secondaryUnit = "px"),
        EffectPreset("cutout_fringe", "Cutout Fringe", "Mask & Edges", isPro = true, defaultIntensity = 25f, secondaryName = "Chroma Key", defaultSecondary = 15f, secondaryUnit = "%"),
        EffectPreset("alpha_feather", "Alpha Feather", "Mask & Edges", isPro = false, defaultIntensity = 20f, secondaryName = "Feather", defaultSecondary = 12f, secondaryUnit = "px"),
        EffectPreset("outline_stroke", "Outline Stroke", "Mask & Edges", isPro = true, defaultIntensity = 30f, secondaryName = "Thickness", defaultSecondary = 4f, secondaryUnit = "px")
    )

    val currentPresets = when (selectedCategory) {
        "Blur" -> blurPresets
        "Background" -> backgroundPresets
        else -> maskPresets
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // build 216: light screen
            .testTag("applied_effects_screen")
    ) {
        // 1. Header Bar (matching screenshot: Left down arrow button, Center Title)
        // Build 252 (stage 5): drawn in GL (AevHeaderGl).
        AevHeaderGl(
            title = if (screenMode == AppliedEffectsScreenMode.PARAMETERS && selectedEffectForEditing != null) {
                selectedEffectForEditing!!.name
            } else {
                "Applied Effects"
            },
            onBack = {
                when (screenMode) {
                    AppliedEffectsScreenMode.PARAMETERS -> {
                        // Go back to applied effects list
                        screenMode = AppliedEffectsScreenMode.LIST
                    }
                    AppliedEffectsScreenMode.LIBRARY -> {
                        // Go back to applied effects list
                        screenMode = AppliedEffectsScreenMode.LIST
                    }
                    AppliedEffectsScreenMode.LIST -> {
                        // Exit to main editor
                        onDismiss()
                    }
                }
            }
        )

        // 2. Canvas Area (STRICTLY LOCKED TO PROJECT ASPECT RATIO)
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

            // Locked Aspect-Ratio Video Container
            Box(
                modifier = Modifier
                    .size(width = frameW, height = frameH)
                    .clip(androidx.compose.ui.graphics.RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                // Build 252 (stage 5): the frame's canvas colour in GL.
                val aevFrameBgArgb = com.example.ui.theme.LocalCanvasColor.current.toArgb()
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = aevFrameBgArgb,
                    buildCmds = { w, h, _ ->
                        addRect(0f, 0f, w, h, aevFrameBgArgb)
                    }
                )
                // Background Video Clip with real-time blur when effect is active
                val activeBlur = appliedEffects.firstOrNull { it.isEnabled }
                val blurAmount = if (activeBlur != null) {
                    (activeBlur.intensity / 6f).dp
                } else if (screenMode == AppliedEffectsScreenMode.PARAMETERS && selectedEffectForEditing != null) {
                    (selectedEffectForEditing!!.intensity / 6f).dp
                } else 0.dp

                Image(
                    painter = painterResource(id = R.drawable.sample_portrait_1791121452621),
                    contentDescription = "Background frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurAmount > 0.dp) Modifier.blur(blurAmount) else Modifier)
                )

                // Darkening vignette overlay — Build 252 (stage 5): in GL.
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = "aev_vignette",
                    buildCmds = { w, h, _ ->
                        addRect(0f, 0f, w, h, Color.Black.copy(alpha = 0.25f).toArgb())
                    }
                )

                // Central glowing particles matching screenshot
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    drawKey = "applied_fx_particles",
                    buildCmds = { w, h, d ->
                        val cX = w / 2f
                        val cY = h / 2f
                        val dotsCount = 7
                        val radius = minOf(w, h) * 0.32f
                        val argb = AmAccent.copy(alpha = 0.85f).toArgb()
                        val dotR = 9f * d
                        for (i in 0 until dotsCount) {
                            val angle = Math.toRadians((i * 360.0 / dotsCount) - 90.0)
                            val x = (cX + radius * Math.cos(angle)).toFloat()
                            val y = (cY + radius * Math.sin(angle)).toFloat()
                            addCircle(x, y, dotR, argb)
                        }
                    },
                )
            }
        }

        // 3. Playback Controls & Timescale & Mini Layer Strip (matching screenshot)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            // Controls: Skip Prev, Play/Pause, Skip Next, Undo, Redo
            // Build 252 (stage 5): drawn in GL (AevTransportGl).
            AevTransportGl(
                isPlaying = isPlaying,
                onPrevious = { currentTimeMs = 0L },
                onPlayPause = { isPlaying = !isPlaying },
                onNext = { currentTimeMs = totalTimeMs },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // Timescale: 00:00 01 | 00:37 00 (matching screenshot)
            // Build 252 (stage 5): GL texts + native ticks (AevTimecodeGl).
            AevTimecodeGl(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )

            // Mini Layer Strip with video thumbnail and keyframe diamond button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .height(24.dp)
            ) {
                // Build 252 (stage 5): the strip surface + keyframe
                // diamond in GL; the thumbnail (a real bitmap) stays
                // an Image at its exact rect.
                AevStripGl(
                    modifier = Modifier.offset(x = 96.dp)
                )

                // Vertical center playhead line
                NativeVectorCanvas(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .align(Alignment.Center),
                    drawKey = "applied_fx_playhead",
                    buildCmds = { _, h, d ->
                        addLine(0f, 0f, 0f, h, 2f * d, Color.White.toArgb(), capRound = false)
                    },
                )
            }
        }

        // 4. Bottom Effects Section (Transitions based on screenMode)
        // Build 252 (stage 5): the section body (AmPanel, 16 dp top
        // corners, AmTextFaint handle) is drawn in GL.
        GlPanelBody(
            modifier = Modifier.fillMaxWidth(),
            cornerDp = 16f,
            fillArgb = AmPanel.toArgb(),
            handleArgb = AmTextFaint.toArgb(),
            handleTopDp = 6f,
            topOnlyCorners = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(start = 12.dp, end = 12.dp, top = 17.dp, bottom = 6.dp)
            ) {

                AnimatedContent(
                    targetState = screenMode,
                    transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(120, delayMillis = 30)) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(70))
                },
                    label = "effectsPanelTransition"
                ) { mode ->
                    when (mode) {
                        // Image 1 (empty) & Image 3 (applied list)
                        AppliedEffectsScreenMode.LIST -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Big prominent [ Add effect ] button (matching screenshots)
                                // Build 252 (stage 5): GL capsule + label.
                                AevAddButtonGl(
                                    onClick = { screenMode = AppliedEffectsScreenMode.LIBRARY }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                if (appliedEffects.isEmpty()) {
                                    // Empty state message (Image 1 matching screenshot)
                                    GlTextBlock(
                                        text = "See applied effects here, reorder them by dragging, and control their parameters",
                                        style = UiLabelStyle.ED_TEXT,
                                        colorArgb = AmTextDim.toArgb(),
                                        linePitchSp = 16f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp, horizontal = 14.dp)
                                    )
                                } else {
                                    // List of Applied Effects (Image 3 matching screenshot)
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(appliedEffects) { effect ->
                                            // Build 252 (stage 5): the row in GL (AevRowGl).
                                            AevRowGl(
                                                name = effect.name,
                                                enabled = effect.isEnabled,
                                                onOpen = {
                                                    selectedEffectForEditing = effect
                                                    screenMode = AppliedEffectsScreenMode.PARAMETERS
                                                },
                                                onToggle = { effect.isEnabled = !effect.isEnabled }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Image 2: Effect Library / Picker
                        AppliedEffectsScreenMode.LIBRARY -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Category Pill Tabs: [Blur] [Background] [Mask & Edges]
                                // Build 252 (stage 5): GL pill strip.
                                GlPillStrip(
                                    items = categories.map { cat ->
                                        GlPillItem(
                                            label = cat,
                                            selected = cat == selectedCategory,
                                            onClick = { selectedCategory = cat }
                                        )
                                    },
                                    labelStyle = { sel -> if (sel) UiLabelStyle.ED_TEXT_BOLD else UiLabelStyle.ED_TEXT_MED },
                                    textArgb = { sel -> if (sel) AmInk.toArgb() else AmTextPrimary.toArgb() },
                                    fillArgb = { sel -> if (sel) AmAccent.toArgb() else AmPanelElevated.toArgb() },
                                    borderArgb = { null },
                                    padHDp = 10f,
                                    padVDp = 5f,
                                    gapDp = 6f,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Grid of Effect Cards (matching Image 2)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(145.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(currentPresets) { preset ->
                                        // Build 252 (stage 5): the card in GL (AevPresetCardGl).
                                        AevPresetCardGl(
                                            preset = preset,
                                            onClick = {
                                                val newEffect = ActiveAppliedEffect(
                                                    name = preset.name,
                                                    initialIntensity = preset.defaultIntensity,
                                                    secondaryName = preset.secondaryName,
                                                    initialSecondary = preset.defaultSecondary,
                                                    secondaryUnit = preset.secondaryUnit,
                                                    tertiaryName = preset.tertiaryName,
                                                    initialTertiary = preset.defaultTertiary,
                                                    tertiaryUnit = preset.tertiaryUnit
                                                )
                                                appliedEffects.add(newEffect)
                                                selectedEffectForEditing = newEffect
                                                onEffectApplied(preset.name)
                                                // Go directly to applied effects list or parameter control
                                                screenMode = AppliedEffectsScreenMode.LIST
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Image 4: Parameter Control Screen (Sliders for Intensity, Rotation, Edge Hardness)
                        AppliedEffectsScreenMode.PARAMETERS -> {
                            val effect = selectedEffectForEditing
                            if (effect != null) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // 1. Intensity Slider
                                    RulerDialSlider(
                                        label = "Intensity",
                                        value = effect.intensity,
                                        onValueChange = { effect.intensity = it },
                                        min = 0f,
                                        max = 100f,
                                        unit = "%",
                                        labelWidth = 86.dp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 2. Secondary Slider (e.g. "Rotation" with 45°)
                                    RulerDialSlider(
                                        label = effect.secondaryName,
                                        value = effect.secondaryValue,
                                        onValueChange = { effect.secondaryValue = it },
                                        min = 0f,
                                        max = if (effect.secondaryUnit == "°") 360f else 100f,
                                        unit = effect.secondaryUnit,
                                        labelWidth = 86.dp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 3. Tertiary Slider (e.g. "Edge Hardness" with 0%)
                                    RulerDialSlider(
                                        label = effect.tertiaryName,
                                        value = effect.tertiaryValue,
                                        onValueChange = { effect.tertiaryValue = it },
                                        min = 0f,
                                        max = 100f,
                                        unit = effect.tertiaryUnit,
                                        labelWidth = 86.dp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Build 252 (stage 5): the legacy screen's header in GL — back box
 * (6 dp inset here) and the title centred on the whole width (the
 * two weight spacers + the 34 dp right spacer made it exact).
 */
@Composable
private fun AevHeaderGl(title: String, onBack: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val backPainter = rememberVectorPainter(Icons.Default.KeyboardArrowDown)
    val backPx = (22f * density).roundToInt()
    fun resolveHeader(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val back = atlas.resolveIcon("aev:back", backPx, backPx) { cv, x, y ->
            val b = rasterizePainterBitmap(backPainter, backPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val t = atlas.resolve(title, UiLabelStyle.SCREEN_TITLE)
        return back to t
    }
    var headerEntries = resolveHeader()
    if (atlas.overflowed) {
        atlas.reset()
        headerEntries = resolveHeader()
    }
    val (backEntry, titleEntry) = headerEntries
    val backScale = remember { androidx.compose.animation.core.Animatable(1f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(headerEntries, backScale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                if (backEntry != null) {
                    val cx = 23f * dd
                    setScaleAbout(cx, cy, backScale.value)
                    addText(
                        backEntry.index,
                        cx - backPx / 2f - pad, cy - backPx / 2f - pad,
                        0f, 0f, backEntry.rectW.toFloat(), backEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                if (titleEntry != null) {
                    val style = UiLabelStyle.SCREEN_TITLE
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        EditorGeometry.centredOriginPx(w / 2f, titleEntry.advance),
                        baseline, titleEntry.bearing, titleEntry.base, pad
                    )
                    addText(
                        titleEntry.index, dstX, dstY,
                        0f, 0f, titleEntry.rectW.toFloat(), titleEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
            }
        )
        ChromePressBox(
            backScale, 0.94f, onBack,
            Modifier
                .offset(x = 6.dp, y = 3.dp)
                .size(34.dp),
            tag = "effects_back_button",
            description = "Back"
        )
    }
}

/**
 * Build 252 (stage 5): the legacy screen's transport row in GL —
 * 30 dp lead, 118 dp centre group (23 dp play glyph), static
 * undo / redo glyphs.
 */
@Composable
private fun AevTransportGl(
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
            icon("aev:prev", prevPainter, smallPx),
            icon("aev:next", nextPainter, smallPx),
            icon("aev:play", playPainter, playPx),
            icon("aev:pause", pausePainter, playPx),
            icon("aev:undo", undoPainter, undoPx),
            icon("aev:redo", redoPainter, undoPx)
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
    val scales = remember { List(3) { androidx.compose.animation.core.Animatable(1f) } }

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
 * Build 252 (stage 5): the legacy screen's timecode row — its
 * hardcoded "00:00 01" / "00:37 00" strings in GL mono styles, the
 * native tick strip at its 0.38 / 0.45 weight slot.
 */
@Composable
private fun AevTimecodeGl(modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    fun resolveTc(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val cur = atlas.resolve("00:00 01", UiLabelStyle.TIMECODE_MONO)
        val sep = atlas.resolve("  |  ", UiLabelStyle.SHEET_DESC)
        val tot = atlas.resolve("00:37 00", UiLabelStyle.TIMECODE_MONO_MED)
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
            drawKey = "applied_fx_ruler",
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
 * Build 252 (stage 5): the legacy mini strip in GL — the raised
 * track and the keyframe diamond; the thumbnail stays an Image.
 */
@Composable
private fun AevStripGl(modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val addPainter = rememberVectorPainter(Icons.Default.Add)
    val addPx = (10f * density).roundToInt()
    fun resolveAdd() = atlas.resolveIcon("aev:add", addPx, addPx) { cv, x, y ->
        val b = rasterizePainterBitmap(addPainter, addPx, density)
        cv.drawBitmap(b, x, y, null)
        b.recycle()
    }
    var addEntry = resolveAdd()
    if (atlas.overflowed) {
        atlas.reset()
        addEntry = resolveAdd()
    }
    val addE = addEntry
    val diamondScale = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(
        modifier = modifier
            .width(96.dp)
            .height(22.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(addE, diamondScale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                addRect(0f, 0f, w, h, AmPanelElevated.toArgb())
                val dcx = 84f * dd
                val cy = h / 2f
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
                if (addE != null) {
                    addText(
                        addE.index,
                        dcx - addPx / 2f - pad, cy - addPx / 2f - pad,
                        0f, 0f, addE.rectW.toFloat(), addE.rectH.toFloat(),
                        tintArgb = Color.White.toArgb()
                    )
                }
                clearTransform()
            }
        )
        Image(
            painter = painterResource(id = R.drawable.sample_portrait_1791121452621),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset(x = 4.dp, y = 2.dp)
                .width(64.dp)
                .height(18.dp)
                .clip(RectangleShape)
        )
        ChromePressBox(
            diamondScale, 0.94f, {},
            Modifier
                .offset(x = 76.dp, y = 3.dp)
                .size(16.dp),
            description = "Keyframe"
        )
    }
}

/** Build 252 (stage 5): the wide Add effect capsule in GL. */
@Composable
private fun AevAddButtonGl(onClick: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    fun resolveAdd() = atlas.resolve("Add effect", UiLabelStyle.CHIP_BIG)
    var entry = resolveAdd()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveAdd()
    }
    val e = entry
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .testTag("add_effect_button")
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, AmPanelElevated.toArgb())
                if (e != null) {
                    val style = UiLabelStyle.CHIP_BIG
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
                        tintArgb = TextPrimary.toArgb()
                    )
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            scale, 0.96f, onClick,
            Modifier.fillMaxSize(),
            description = "Add effect"
        )
    }
}

/**
 * Build 252 (stage 5): one applied-effect row of the legacy list
 * in GL — r11 raised fill, dropdown chevron, name, eye, and the
 * six drag dots folded in from their canvas.
 */
@Composable
private fun AevRowGl(
    name: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val chevronPainter = rememberVectorPainter(Icons.Default.ArrowRight)
    val eyePainter = rememberVectorPainter(if (enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff)
    val chevronPx = (19f * density).roundToInt()
    val eyePx = (17f * density).roundToInt()
    fun resolveRow(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val n = atlas.resolve(name, UiLabelStyle.AEV_NAME)
        val chevron = atlas.resolveIcon("aevrow:chevron", chevronPx, chevronPx) { cv, x, y ->
            val b = rasterizePainterBitmap(chevronPainter, chevronPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val eye = atlas.resolveIcon("aevrow:eye:$enabled", eyePx, eyePx) { cv, x, y ->
            val b = rasterizePainterBitmap(eyePainter, eyePx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        return Triple(n, chevron, eye)
    }
    var rowEntries = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        rowEntries = resolveRow()
    }
    val (nameEntry, chevronEntry, eyeEntry) = rowEntries
    val chevronScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val nameScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val eyeScale = remember { androidx.compose.animation.core.Animatable(1f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val wPx = maxWidth.value * density
        val eyeLeft = wPx - 66f * density
        val shownNameEntry = run {
            val zone = eyeLeft - 38f * density
            if (nameEntry != null && nameEntry.advance > zone) {
                val shown = ChromeGeometry.ellipsize(name, zone) { atlas.measure(it, UiLabelStyle.AEV_NAME) }
                if (shown != null && shown != name) {
                    atlas.resolve(shown, UiLabelStyle.AEV_NAME) ?: nameEntry
                } else {
                    nameEntry
                }
            } else {
                nameEntry
            }
        }
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                shownNameEntry, chevronEntry, eyeEntry, enabled,
                chevronScale.value, nameScale.value, eyeScale.value, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                addRoundRect(w / 2f, cy, w, h, 11f * dd, AmPanelElevated.toArgb())
                if (chevronEntry != null) {
                    val cx = 21f * dd
                    setScaleAbout(cx, cy, chevronScale.value)
                    addText(
                        chevronEntry.index,
                        cx - chevronPx / 2f - pad, cy - chevronPx / 2f - pad,
                        0f, 0f, chevronEntry.rectW.toFloat(), chevronEntry.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                if (shownNameEntry != null) {
                    val style = UiLabelStyle.AEV_NAME
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(38f * dd, baseline, shownNameEntry.bearing, shownNameEntry.base, pad)
                    setScaleAbout((38f * dd + eyeLeft) / 2f, cy, nameScale.value)
                    addText(
                        shownNameEntry.index, dstX, dstY,
                        0f, 0f, shownNameEntry.rectW.toFloat(), shownNameEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                    clearTransform()
                }
                if (eyeEntry != null) {
                    val cx = w - 53f * dd
                    setScaleAbout(cx, cy, eyeScale.value)
                    addText(
                        eyeEntry.index,
                        cx - eyePx / 2f - pad, cy - eyePx / 2f - pad,
                        0f, 0f, eyeEntry.rectW.toFloat(), eyeEntry.rectH.toFloat(),
                        tintArgb = (if (enabled) IconActive else AmTextFaint).toArgb()
                    )
                    clearTransform()
                }
                // The drag dots (their own canvas before, folded in).
                val dotR = 1.1f * dd
                val spacingX = 4f * dd
                val spacingY = 3.8f * dd
                val dotPad = 1.5f * dd
                val dotsX = w - 27f * dd
                val dotsY = cy - 6f * dd
                val dotArgb = AmTextDim.toArgb()
                for (row in 0..2) {
                    for (col in 0..1) {
                        addCircle(dotsX + col * spacingX + dotPad, dotsY + row * spacingY + dotPad, dotR, dotArgb)
                    }
                }
            }
        )
        ChromePressBox(
            chevronScale, 0.94f, onOpen,
            Modifier
                .offset(x = 8.dp, y = 7.dp)
                .size(26.dp),
            tag = "effect_dropdown_button_$name",
            description = "Expand effect"
        )
        ChromePressBox(
            nameScale, 0.94f, onOpen,
            Modifier
                .offset(x = 38.dp)
                .width(((eyeLeft - 38f * density) / density).dp)
                .height(40.dp),
            description = name
        )
        ChromePressBox(
            eyeScale, 0.94f, onToggle,
            Modifier
                .offset(x = ((eyeLeft) / density).dp, y = 7.dp)
                .size(26.dp),
            description = "Toggle visibility"
        )
    }
}

/**
 * Build 252 (stage 5): one library preset card in GL — the card
 * surface + border and the pro star badge; the two-circle graphic
 * stays its native canvas, the name becomes a GL label. The whole
 * column press-scales as the Compose column did.
 */
@Composable
private fun AevPresetCardGl(preset: EffectPreset, onClick: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val starPainter = rememberVectorPainter(Icons.Default.Star)
    val starPx = (8f * density).roundToInt()
    fun resolveStar() = atlas.resolveIcon("aev:star", starPx, starPx) { cv, x, y ->
        val b = rasterizePainterBitmap(starPainter, starPx, density)
        cv.drawBitmap(b, x, y, null)
        b.recycle()
    }
    var starEntry = resolveStar()
    if (atlas.overflowed) {
        atlas.reset()
        starEntry = resolveStar()
    }
    val starE = starEntry
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("effect_preset_${preset.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(50.dp)) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = "applied_fx_card_bg",
                    buildCmds = { w, h, dd ->
                        addRoundRect(w / 2f, h / 2f, w, h, 10f * dd, AmCardSurface.toArgb())
                        addRoundRect(
                            w / 2f, h / 2f, w - 1f * dd, h - 1f * dd, 9.5f * dd,
                            0, 1f * dd, AmHairlineStrong.toArgb()
                        )
                    }
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.size(30.dp),
                        drawKey = "applied_fx_card_graphic",
                        buildCmds = { w, h, d ->
                            val cX = w / 2f
                            val cY = h / 2f
                            addCircle(cX, cY, 10f * d, Color(0xFFFF5252).toArgb())
                            addCircle(cX, cY, 5f * d, Color(0xFFABE41D).toArgb())
                        },
                    )
                }
                if (preset.isPro) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = atlas,
                        drawKey = listOf(starE, atlas.generation),
                        buildCmds = { w, h, dd ->
                            val cx = w - 8f * dd
                            val cy = 8f * dd
                            addCircle(cx, cy, 6f * dd, Color(0xFF00B0FF).toArgb())
                            if (starE != null) {
                                addText(
                                    starE.index,
                                    cx - starPx / 2f - pad, cy - starPx / 2f - pad,
                                    0f, 0f, starE.rectW.toFloat(), starE.rectH.toFloat(),
                                    tintArgb = Color.White.toArgb()
                                )
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            AevCardLabelGl(name = preset.name)
        }
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.matchParentSize(),
            description = preset.name
        )
    }
}

/**
 * Build 252 (stage 5): a preset card's name in GL — 9.5 sp medium
 * on an 11 sp pitch, centred, two lines max with ellipsis.
 */
@Composable
private fun AevCardLabelGl(name: String) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.AEV_TILE
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wPx = maxWidth.value * density
        fun resolveLabel(): List<UiLabelAtlas.Entry?> {
            val wrapped = PanelGeometry.wrapGreedy(name, wPx, Int.MAX_VALUE) { atlas.measure(it, style) }
            val lines = if (wrapped.size > 2) {
                val rest = name.substring(wrapped[0].length).trimStart()
                listOf(wrapped[0], ChromeGeometry.ellipsize(rest, wPx) { atlas.measure(it, style) })
            } else {
                wrapped
            }
            return lines.map { atlas.resolve(it, style) }
        }
        var labelEntries = resolveLabel()
        if (atlas.overflowed) {
            atlas.reset()
            labelEntries = resolveLabel()
        }
        val lines = labelEntries
        val linePitchPx = 11f * density * fontScale
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((linePitchPx * lines.size / density).dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(lines, atlas.generation),
                buildCmds = { w, _, _ ->
                    val fm = atlas.fontMetrics(style)
                    var lineTop = 0f
                    for (e in lines) {
                        if (e != null) {
                            val baseline = PanelGeometry.lineBaselinePx(lineTop, linePitchPx, fm.ascent, fm.descent)
                            val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                            val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                        lineTop += linePitchPx
                    }
                }
            )
        }
    }
}
