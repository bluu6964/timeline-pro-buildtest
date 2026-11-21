package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.engine.NativeVectorCanvas
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.OnionSkin
import com.example.model.OnionSkinSettings
import com.example.model.PanelGeometry
import com.example.ui.theme.AmBase
import com.example.ui.theme.IconDisabled
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * The onion-skin settings row (build 190): shown under the play controls while the
 * ghosts are on. Ghost count per side (1–3, a bare − / + stepper) and the nearest
 * ghost's opacity (a slider; farther ghosts fade from it by the plan's falloff).
 * Everything bare in the settled B/W language — states read as white vs grey.
 * The step between ghosts is always one frame at the project rate, so there is
 * no step control here.
 */
@Composable
fun OnionSkinRow(
    settings: OnionSkinSettings,
    onCountChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(AmBase)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Build 253 (stage 6): the row's texts and slider draw in GL
        // (the row's AmBase fill stays a plain background, as in 252).
        GlLabelLine("Ghosts", UiLabelStyle.TEXT_10_SEMI, TextSecondary.toArgb())
        Spacer(Modifier.width(4.dp))
        StepperButton(
            label = "−",
            description = "Fewer ghost frames",
            tag = "onion_count_minus",
            enabled = settings.countPerSide > 1,
            onClick = { onCountChange(settings.countPerSide - 1) }
        )
        GlTextLine(
            settings.countPerSide.toString(),
            UiLabelStyle.ED_TEXT_BOLD, TextPrimary.toArgb(),
            Modifier.width(16.dp),
            align = TextAlign.Center
        )
        StepperButton(
            label = "+",
            description = "More ghost frames",
            tag = "onion_count_plus",
            enabled = settings.countPerSide < OnionSkin.MAX_PER_SIDE,
            onClick = { onCountChange(settings.countPerSide + 1) }
        )
        Spacer(Modifier.width(14.dp))
        GlLabelLine("Opacity", UiLabelStyle.TEXT_10_SEMI, TextSecondary.toArgb())
        Spacer(Modifier.width(6.dp))
        GlSlider(
            value = settings.opacity,
            onValueChange = onOpacityChange,
            min = OnionSkin.MIN_OPACITY,
            max = OnionSkin.MAX_OPACITY,
            onValueChangeFinished = null,
            thumbArgb = Color.White.toArgb(),
            activeArgb = Color.White.toArgb(),
            inactiveArgb = 0xFF3A3A3A.toInt(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Ghost opacity" }
                .testTag("onion_opacity_slider")
        )
        Spacer(Modifier.width(6.dp))
        GlTextLine(
            "${(settings.opacity * 100f).roundToInt()}%",
            UiLabelStyle.TEXT_10_BOLD, TextPrimary.toArgb(),
            Modifier.width(30.dp),
            align = TextAlign.End
        )
    }
}

/**
 * A bare − / + stepper key: no box (build 183), grey when it can go no
 * further. Build 253: the glyph draws in GL; a disabled key keeps
 * bouncyClickable's 0.45 content alpha over the disabled colour.
 */
@Composable
private fun StepperButton(
    label: String,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val colorArgb = if (enabled) TextPrimary.toArgb()
        else IconDisabled.copy(alpha = 0.45f).toArgb()
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val textH = atlas.textHeightPx(UiLabelStyle.TEXT_14_BOLD)
    fun resolveGlyph() = atlas.resolve(label, UiLabelStyle.TEXT_14_BOLD)
    var glyphE = resolveGlyph()
    if (atlas.overflowed) {
        atlas.reset()
        glyphE = resolveGlyph()
    }
    val e = glyphE
    Box(
        modifier = Modifier.size(32.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, enabled, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                if (e != null) {
                    val style = UiLabelStyle.TEXT_14_BOLD
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    setScaleAbout(w / 2f, h / 2f, scale.value)
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
            enabled = enabled,
            tag = tag,
            description = description
        )
    }
}
