package com.example.ui.components

import com.example.ui.theme.AmCornerCard

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiLabelStyle
import com.example.ui.theme.AmPanel
import com.example.ui.theme.HeaderText
import com.example.ui.theme.TextSecondary

/**
 * The layer colour-label picker (build 199, A2). Build 207 (frontend redesign):
 * rounded swatches and pill actions in the dialog's 24 dp container. The colour it sets is the timeline label colour
 * itself ([com.example.model.TimelineClip.color] — the same value the timeline bars render),
 * NOT a second colour model: the palette is a set of label colours plus "Default", which
 * restores the colour the layer type is created with (model.defaultClipColor).
 */
@Composable
fun LayerLabelDialog(
    /** The layer's current label colour (packed ARGB, as stored on the layer). */
    currentColor: Long,
    /** The colour this layer type is created with — the Default choice. */
    defaultColor: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = RoundedCornerShape(AmCornerCard),
        // Build 253 (stage 6): the title, the swatch grid and the
        // note draw in GL (shell stays). Flat colour fills are exact
        // in GL — only atlas blits are alpha-only.
        title = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { 32.sp.toDp() })
            ) {
                GlTextLine(
                    "Layer label", UiLabelStyle.DLG_TITLE, HeaderText.toArgb(),
                    Modifier.fillMaxSize()
                )
            }
        },
        text = {
            Column {
                SwatchGridGl(currentColor, onPick)
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { 18.sp.toDp() })
                ) {
                    GlTextLine(
                        "The label colour tints the layer's timeline bar.",
                        UiLabelStyle.DLG_13, TextSecondary.toArgb(),
                        Modifier.fillMaxSize()
                    )
                }
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = "Default", onClick = { onPick(defaultColor) })
        },
        dismissButton = {
            DialogSecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

/**
 * Build 253 (stage 6): the whole 2×6 swatch grid on one GL surface —
 * each swatch a flat colour square (28dp, r8); the current one
 * carries the 1.5dp white edge (an inside stroke, as Compose's
 * border draws). In build 252 the fill + border sat outside
 * bouncyClickable's graphicsLayer, so a press scaled nothing; the
 * per-swatch press boxes keep the tap + semantics only.
 */
@Composable
private fun SwatchGridGl(currentColor: Long, onPick: (Long) -> Unit) {
    val scales = remember { LABEL_PALETTE.map { Animatable(1f) } }
    Box(
        Modifier
            .width(208.dp)
            .height(64.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = currentColor,
            buildCmds = { _, _, dd ->
                LABEL_PALETTE.forEachIndexed { i, c ->
                    val col = i % 6
                    val row = i / 6
                    val cx = (col * 36 + 14) * dd
                    val cy = (row * 36 + 14) * dd
                    addRoundRect(cx, cy, 28f * dd, 28f * dd, 8f * dd, Color(c).toArgb())
                    if (c == currentColor) {
                        addRoundRect(
                            cx, cy, 28f * dd - 1.5f * dd, 28f * dd - 1.5f * dd,
                            8f * dd - 0.75f * dd,
                            0, 1.5f * dd, 0xFFFFFFFF.toInt()
                        )
                    }
                }
            }
        )
        LABEL_PALETTE.forEachIndexed { i, c ->
            val col = i % 6
            val row = i / 6
            ChromePressBox(
                scales[i], 0.94f, { onPick(c) },
                Modifier
                    .offset(x = (col * 36).dp, y = (row * 36).dp)
                    .size(28.dp),
                description = "Label colour"
            )
        }
    }
}

/** Label colours offered beside Default: vivid, mutually distinct, readable as bar tints. */
private val LABEL_PALETTE: List<Long> = listOf(
    0xFFE53935, // red
    0xFFFB8C00, // orange
    0xFFFDD835, // yellow
    0xFF43A047, // green
    0xFF00ACC1, // teal-blue
    0xFF1E88E5, // blue
    0xFF8E24AA, // purple
    0xFFD81B60, // pink
    0xFF6D4C41, // brown
    0xFF757575, // grey
    0xFF2A4365, // video navy (a type default)
    0xFF3992CE  // text blue (a type default)
)
