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
import com.example.R
import com.example.model.AspectRatio
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .bouncyClickable(onClick = {
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
                    })
                    .testTag("effects_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back",
                    tint = IconActive,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (screenMode == AppliedEffectsScreenMode.PARAMETERS && selectedEffectForEditing != null) {
                    selectedEffectForEditing!!.name
                } else {
                    "Applied Effects"
                },
                color = TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(34.dp))
        }

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
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .background(com.example.ui.theme.LocalCanvasColor.current),
                contentAlignment = Alignment.Center
            ) {
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

                // Darkening vignette overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(30.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable(onClick = { currentTimeMs = 0L }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = IconActive, modifier = Modifier.size(19.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .bouncyClickable(onClick = { isPlaying = !isPlaying }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = IconActive,
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable(onClick = { currentTimeMs = totalTimeMs }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = IconActive, modifier = Modifier.size(19.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = IconActive, modifier = Modifier.size(17.dp))
                    }
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = IconActive, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // Timescale: 00:00 01 | 00:37 00 (matching screenshot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(0.38f))
                Text(
                    text = "00:00 01",
                    color = TextPrimary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "  |  ", color = TextSecondary, fontSize = 10.5.sp)
                Text(
                    text = "00:37 00",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Ruler ticks
                NativeVectorCanvas(
                    modifier = Modifier
                        .weight(0.45f)
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
                    },
                )
            }

            // Mini Layer Strip with video thumbnail and keyframe diamond button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .height(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .offset(x = 96.dp)
                        .height(22.dp)
                        .clip(RectangleShape)
                        .background(AmPanelElevated) // build 223: raised surface on the dark strip
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sample_portrait_1791121452621),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(64.dp)
                            .height(18.dp)
                            .clip(RectangleShape)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Diamond/Keyframe + button (matching screenshot)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(AmVioletDeep, RectangleShape)
                            .bouncyClickable(onClick = {}),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Keyframe",
                            tint = Color.White,
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer { rotationZ = -45f }
                        )
                    }
                }

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(AmPanel) // build 223 (colour balance): the section joins the unified dark surfaces (was build 216's white)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top drag handle pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(28.dp)
                        .height(3.dp)
                        .background(AmTextFaint, CircleShape)
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(CircleShape)
                                        .background(AmPanelElevated, CircleShape) // build 223: raised surface button
                                        .bouncyClickable(
                                            pressedScale = 0.96f,
                                            onClick = { screenMode = AppliedEffectsScreenMode.LIBRARY }
                                        )
                                        .testTag("add_effect_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Add effect",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (appliedEffects.isEmpty()) {
                                    // Empty state message (Image 1 matching screenshot)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp, horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "See applied effects here, reorder them by dragging, and control their parameters",
                                            color = AmTextDim,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // List of Applied Effects (Image 3 matching screenshot)
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(appliedEffects) { effect ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(AmPanelElevated, RoundedCornerShape(11.dp)) // build 223
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Left dropdown arrow ▶ to open parameters
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .bouncyClickable(onClick = {
                                                            selectedEffectForEditing = effect
                                                            screenMode = AppliedEffectsScreenMode.PARAMETERS
                                                        })
                                                        .testTag("effect_dropdown_button_${effect.name}"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowRight,
                                                        contentDescription = "Expand effect",
                                                        tint = IconActive,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(4.dp))

                                                // Effect Name
                                                Text(
                                                    text = effect.name,
                                                    color = TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .bouncyClickable(onClick = {
                                                            selectedEffectForEditing = effect
                                                            screenMode = AppliedEffectsScreenMode.PARAMETERS
                                                        })
                                                )

                                                // Visibility Eye Toggle
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .bouncyClickable(onClick = {
                                                            effect.isEnabled = !effect.isEnabled
                                                        }),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (effect.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle visibility",
                                                        tint = if (effect.isEnabled) IconActive else AmTextFaint,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Drag reorder handle (6 dots ⠿)
                                                Box(
                                                    modifier = Modifier.size(26.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    NativeVectorCanvas(
                                                        modifier = Modifier.size(12.dp),
                                                        drawKey = "applied_fx_drag_dots",
                                                        buildCmds = { _, _, d ->
                                                            val dotR = 1.1f * d
                                                            val spacingX = 4f * d
                                                            val spacingY = 3.8f * d
                                                            val pad = 1.5f * d
                                                            val argb = AmTextDim.toArgb()
                                                            for (row in 0..2) {
                                                                for (col in 0..1) {
                                                                    addCircle(col * spacingX + pad, row * spacingY + pad, dotR, argb)
                                                                }
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Image 2: Effect Library / Picker
                        AppliedEffectsScreenMode.LIBRARY -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Category Pill Tabs: [Blur] [Background] [Mask & Edges]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    categories.forEach { cat ->
                                        val isSelected = cat == selectedCategory
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSelected) AmAccent else AmPanelElevated, CircleShape)
                                                .bouncyClickable(onClick = { selectedCategory = cat })
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cat,
                                                color = if (isSelected) AmInk else AmTextPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

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
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClickable(onClick = {
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
                                                })
                                                .testTag("effect_preset_${preset.id}"),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Effect Card with checkerboard & 3D Rocket/Blur Graphic + Star Badge
                                            Box(
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(AmCardSurface, RoundedCornerShape(10.dp))
                                                    .border(1.dp, AmHairlineStrong, RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                // Stylized Rocket / Graphic Canvas
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

                                                // Top-right Star badge if pro
                                                if (preset.isPro) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(2.dp)
                                                            .size(12.dp)
                                                            .background(Color(0xFF00B0FF), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(8.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))

                                            Text(
                                                text = preset.name,
                                                color = TextPrimary,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                lineHeight = 11.sp
                                            )
                                        }
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
