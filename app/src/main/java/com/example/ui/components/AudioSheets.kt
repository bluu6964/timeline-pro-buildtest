package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.example.model.TimelineClip
import com.example.model.MIN_SPEED
import com.example.model.MAX_SPEED
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.engine.NativeVectorCanvas
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

// Build 253 (stage 6): the audio sheets draw in GL — their sliders
// are the stage-5 GlSlider with the settled B/W palette (build 201:
// white thumb + active track, 0xFF3A3A3A inactive), their switches
// are GlSwitch, and every text is a GL leaf.
private val AUDIO_THUMB_ARGB: Int = Color.White.toArgb()
private val AUDIO_ACTIVE_ARGB: Int = Color.White.toArgb()
private val AUDIO_INACTIVE_ARGB: Int = 0xFF3A3A3A.toInt()

/**
 * Build 253 (stage 6): one ducking trigger row — a full-width tap row
 * whose single ellipsized name line draws in GL and press-scales
 * about the row centre (the build-252 Text carried bouncyClickable +
 * 6dp vertical padding itself).
 */
@Composable
private fun AudioTriggerRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val style = if (selected) UiLabelStyle.TEXT_125_BOLD else UiLabelStyle.TEXT_125
    val colorArgb = if (selected) TextPrimary.toArgb() else TextSecondary.toArgb()
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val textH = atlas.textHeightPx(style)
    val scale = remember { Animatable(1f) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wPx = maxWidth.value * density
        val shown = remember(name, wPx, style) {
            ChromeGeometry.ellipsize(name, wPx) { atlas.measure(it, style) }
        }
        fun resolveName() = atlas.resolve(shown, style)
        var nameE = resolveName()
        if (atlas.overflowed) {
            atlas.reset()
            nameE = resolveName()
        }
        val e = nameE
        Box(
            Modifier
                .fillMaxWidth()
                .height(((textH + 12f * density) / density).dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(e, scale.value, atlas.generation),
                buildCmds = { w, h, _ ->
                    if (e != null) {
                        setScaleAbout(w / 2f, h / 2f, scale.value)
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(0f, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = colorArgb
                        )
                        clearTransform()
                    }
                }
            )
            ChromePressBox(
                scale, 0.94f, onClick,
                Modifier.fillMaxSize(),
                description = name
            )
        }
    }
}

/** Volume of one layer (audio or video), 0..200 %, plus a mute switch. Changes are applied live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioVolumeSheet(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    muted: Boolean = false,
    onMutedChange: ((Boolean) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlLabelLine("Volume", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
                GlLabelLine(
                    if (muted) "Muted" else "${(volume * 100f).roundToInt()}%",
                    UiLabelStyle.TEXT_13_BOLD, TextPrimary.toArgb()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            GlSlider(
                value = volume.coerceIn(0f, 2f),
                onValueChange = {
                    onVolumeChange(it)
                    // Moving the slider means "I want to hear it": leave mute.
                    if (muted) onMutedChange?.invoke(false)
                },
                min = 0f,
                max = 2f,
                onValueChangeFinished = null,
                thumbArgb = AUDIO_THUMB_ARGB,
                activeArgb = AUDIO_ACTIVE_ARGB,
                inactiveArgb = AUDIO_INACTIVE_ARGB,
                modifier = Modifier.fillMaxWidth()
            )
            if (onMutedChange != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlLabelLine("Mute this layer", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                    GlSwitch(
                        checked = muted,
                        onCheckedChange = onMutedChange,
                        description = "Mute this layer"
                    )
                }
            }
        }
    }
}

/** Fade in / fade out length of one audio layer. Changes are applied live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFadeSheet(
    fadeInMs: Long,
    fadeOutMs: Long,
    maxFadeMs: Long,
    onChange: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val maxFade = maxFadeMs.coerceIn(500L, 10_000L).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 16.dp)
        ) {
            GlLabelLine("Fade", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlLabelLine("Fade in", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                GlLabelLine(
                    String.format(Locale.US, "%.1fs", fadeInMs / 1000f),
                    UiLabelStyle.SHEET_ITEM_BOLD, TextPrimary.toArgb()
                )
            }
            GlSlider(
                value = fadeInMs.toFloat().coerceIn(0f, maxFade),
                onValueChange = { onChange(it.toLong(), fadeOutMs) },
                min = 0f,
                max = maxFade,
                onValueChangeFinished = null,
                thumbArgb = AUDIO_THUMB_ARGB,
                activeArgb = AUDIO_ACTIVE_ARGB,
                inactiveArgb = AUDIO_INACTIVE_ARGB,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlLabelLine("Fade out", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                GlLabelLine(
                    String.format(Locale.US, "%.1fs", fadeOutMs / 1000f),
                    UiLabelStyle.SHEET_ITEM_BOLD, TextPrimary.toArgb()
                )
            }
            GlSlider(
                value = fadeOutMs.toFloat().coerceIn(0f, maxFade),
                onValueChange = { onChange(fadeInMs, it.toLong()) },
                min = 0f,
                max = maxFade,
                onValueChangeFinished = null,
                thumbArgb = AUDIO_THUMB_ARGB,
                activeArgb = AUDIO_ACTIVE_ARGB,
                inactiveArgb = AUDIO_INACTIVE_ARGB,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Speed of one layer (0.1x..4x, pitch stays the same). One constant speed per layer:
 * a change sets the base speed (the layer gets shorter / longer on the timeline).
 * (Speed keyframes / ramps and smooth slow-mo were removed in build 237.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSpeedSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableFloatStateOf(speed) }
    LaunchedEffect(speed) { shown = speed }

    fun applySpeed(v: Float) {
        shown = v
        onSpeedChange(v)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TransformValueHeader(
            valueText = String.format(Locale.US, "%.2fx", shown),
            onBack = null,
            keyframeEnabled = false,
            hasKeyframes = false,
            onKeyframeNow = false,
            onKeyframe = {},
            easingEnabled = false,
            easingOpen = false,
            easing = null,
            onEasing = {}
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            GlSlider(
                value = shown,
                onValueChange = { v -> shown = v },
                min = MIN_SPEED,
                max = MAX_SPEED,
                onValueChangeFinished = { onSpeedChange(shown) },
                thumbArgb = AUDIO_THUMB_ARGB,
                activeArgb = AUDIO_ACTIVE_ARGB,
                inactiveArgb = AUDIO_INACTIVE_ARGB,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.1f, 0.5f, 1f, 2f, 4f).forEach { preset ->
                    GlChip(
                        label = String.format(Locale.US, "%.1fx", preset),
                        selected = false,
                        tag = "",
                        onClick = { applySpeed(preset) },
                        padHDp = 10f,
                        padVDp = 5f,
                        styleOn = UiLabelStyle.ED_TEXT,
                        styleOff = UiLabelStyle.ED_TEXT,
                        fillOnArgb = AmPanelHigh.toArgb(),
                        fillOffArgb = AmPanelHigh.toArgb(),
                        textOnArgb = TextPrimary.toArgb(),
                        textOffArgb = TextPrimary.toArgb()
                    )
                }
            }
        }
    }
}


/**
 * Ducking of one audio layer (build 194): while the chosen trigger layer is loud, this
 * layer's volume is lowered by up to the amount and let back up in the gaps. The settings
 * live on this layer; the gain itself is computed live from the trigger's sound (see
 * [com.example.model.Ducking]) — nothing is baked. Changes are applied live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDuckingSheet(
    clip: TimelineClip,
    triggers: List<TimelineClip>,
    onChange: (enabled: Boolean, triggerId: String?, amountDb: Float, smoothness: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val enabled = clip.duckEnabled
    val triggerId = clip.duckTriggerId
    val amountDb = clip.duckAmountDb
    val smoothness = clip.duckSmoothness
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlLabelLine("Ducking", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
                GlSwitch(
                    checked = enabled,
                    onCheckedChange = { onChange(it, triggerId, amountDb, smoothness) },
                    description = "Ducking"
                )
            }
            GlTextBlock(
                "Lower this layer's volume automatically while another layer is loud, " +
                    "and let it back up in the gaps.",
                UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                Modifier.fillMaxWidth()
            )
            if (triggers.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                GlTextBlock(
                    "No other layer with sound yet. Add a voice or music layer first, " +
                        "then pick it here as the trigger.",
                    UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb(),
                    Modifier.fillMaxWidth()
                )
            } else if (enabled) {
                Spacer(modifier = Modifier.height(10.dp))
                GlLabelLine("Duck under", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                Spacer(modifier = Modifier.height(4.dp))
                triggers.forEach { trigger ->
                    val selected = trigger.id == triggerId
                    AudioTriggerRow(
                        name = trigger.name,
                        selected = selected,
                        onClick = { onChange(true, trigger.id, amountDb, smoothness) }
                    )
                }
                if (triggerId != null && triggers.none { it.id == triggerId }) {
                    GlLabelLine(
                        "The trigger layer was removed — pick another layer above.",
                        UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                        Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GlLabelLine("Amount", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                    GlLabelLine(
                        "−${amountDb.roundToInt()} dB",
                        UiLabelStyle.SHEET_ITEM_BOLD, TextPrimary.toArgb()
                    )
                }
                GlSlider(
                    value = amountDb.coerceIn(0f, com.example.model.Ducking.MAX_AMOUNT_DB),
                    onValueChange = { onChange(true, triggerId, it, smoothness) },
                    min = 0f,
                    max = com.example.model.Ducking.MAX_AMOUNT_DB,
                    onValueChangeFinished = null,
                    thumbArgb = AUDIO_THUMB_ARGB,
                    activeArgb = AUDIO_ACTIVE_ARGB,
                    inactiveArgb = AUDIO_INACTIVE_ARGB,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GlLabelLine("Smoothness", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                    GlLabelLine(
                        "${(smoothness * 100f).roundToInt()}%",
                        UiLabelStyle.SHEET_ITEM_BOLD, TextPrimary.toArgb()
                    )
                }
                GlSlider(
                    value = smoothness.coerceIn(0f, 1f),
                    onValueChange = { onChange(true, triggerId, amountDb, it) },
                    min = 0f,
                    max = 1f,
                    onValueChangeFinished = null,
                    thumbArgb = AUDIO_THUMB_ARGB,
                    activeArgb = AUDIO_ACTIVE_ARGB,
                    inactiveArgb = AUDIO_INACTIVE_ARGB,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Noise reduction of one audio layer (build 195): the layer's steady background
 * noise (hiss, hum, fan noise) is gated down against a noise profile estimated
 * from the layer's own sound (see [com.example.model.NoiseReduction]). The two
 * settings live on this layer; the profile itself is computed live by the sound
 * paths — nothing is baked. Changes are applied live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioNoiseSheet(
    clip: TimelineClip,
    onChange: (enabled: Boolean, strength: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val enabled = clip.nrEnabled
    val strength = clip.nrStrength
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlLabelLine("Noise reduction", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
                GlSwitch(
                    checked = enabled,
                    onCheckedChange = { onChange(it, strength) },
                    description = "Noise reduction"
                )
            }
            GlTextBlock(
                "Remove the steady background noise of this layer — hiss, hum, fan " +
                    "noise — while keeping the voice or music on top of it.",
                UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                Modifier.fillMaxWidth()
            )
            if (enabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GlLabelLine("Strength", UiLabelStyle.SHEET_ITEM, TextSecondary.toArgb())
                    GlLabelLine(
                        "${(strength * 100f).roundToInt()}%",
                        UiLabelStyle.SHEET_ITEM_BOLD, TextPrimary.toArgb()
                    )
                }
                GlSlider(
                    value = strength.coerceIn(0f, 1f),
                    onValueChange = { onChange(true, it) },
                    min = 0f,
                    max = 1f,
                    onValueChangeFinished = null,
                    thumbArgb = AUDIO_THUMB_ARGB,
                    activeArgb = AUDIO_ACTIVE_ARGB,
                    inactiveArgb = AUDIO_INACTIVE_ARGB,
                    modifier = Modifier.fillMaxWidth()
                )
                GlTextBlock(
                    "The layer's sound is analysed the first time it plays and after " +
                        "its trim or speed changes — until the analysis lands, the layer " +
                        "plays unprocessed. Exports are always cleaned.",
                    UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}
