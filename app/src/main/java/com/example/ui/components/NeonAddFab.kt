package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.NativeVectorCanvas
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmInk

/**
 * "Add" button (B/W language). It was a flat white disc with a black plus until
 * Sarowar's boxless order 2026-11-14 (build 183) — now BARE like every other
 * button: just the plus glyph, drawn white with a dark under-stroke so it stays
 * readable over bright timeline clips. The touch box ([size], 61 dp in the
 * original design reference) is unchanged.
 *
 * Build 253 (stage 6): the disc + plus draw on the GL canvas (the same
 * commands the build-252 Compose Canvas issued), press-scaled about
 * the centre at the FAB's 0.92.
 */
@Composable
fun NeonAddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val scale = remember { Animatable(1f) }
    Box(
        modifier = modifier
            .size(size)
            .testTag("add_layer_fab")
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = scale.value,
            buildCmds = { w, h, _ ->
                val d = w
                val u = d / 61f                      // 1 reference dp in px
                val cx = d / 2f
                val cy = h / 2f
                setScaleAbout(cx, cy, scale.value)
                // The accent disc with an ink plus (build 223: tokenized).
                addCircle(cx, cy, d / 2f - 1f * u, AmAccent.toArgb())
                val arm = 10.5f * u
                val th = 3.6f * u
                addLine(cx - arm, cy, cx + arm, cy, th, AmInk.toArgb(), capRound = true)
                addLine(cx, cy - arm, cx, cy + arm, th, AmInk.toArgb(), capRound = true)
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.92f, onClick,
            Modifier.fillMaxSize()
        )
    }
}
