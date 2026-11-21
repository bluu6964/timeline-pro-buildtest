package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.NativeVectorCanvas
import com.example.engine.UiLabelStyle
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmPanelElevated

/**
 * Progress of the full-video cutout (RVM) with a Cancel button.
 *
 * Build 253 (stage 6): the card body, the label and the progress bar
 * draw in GL. The Cancel TextButton STAYS a stock Material button
 * host (its ripple + button semantics are the platform's; documented
 * in the final remaining-UI inventory).
 */
@Composable
fun CutoutProgressBar(
    progress: Float,
    label: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Cancel"
) {
    val p = progress.coerceIn(0f, 1f)
    GlPanelBody(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        cornerDp = 12f,
        fillArgb = AmCardSurface.toArgb(),
        borderArgb = null
    ) {
        Column(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlLabelLine(
                    "$label ${(p * 100).toInt()}%",
                    UiLabelStyle.ED_TEXT, Color.White.toArgb(),
                    Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text(actionLabel, color = Color.White, fontSize = 11.5.sp) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = p,
                    buildCmds = { w, h, _ ->
                        addRoundRect(w / 2f, h / 2f, w, h, h / 2f, AmPanelElevated.toArgb())
                        val wp = w * p
                        if (wp >= h) {
                            // Left cap + straight body: the build-252
                            // fill was a square rect clipped by the
                            // capsule — rounded left, straight right.
                            addCircle(h / 2f, h / 2f, h / 2f, AmAccentViolet.toArgb())
                            addRect(h / 2f, 0f, wp - h / 2f, h, AmAccentViolet.toArgb())
                        } else if (wp > 0f) {
                            addRoundRect(wp / 2f, h / 2f, wp, h, wp / 2f, AmAccentViolet.toArgb())
                        }
                    }
                )
            }
        }
    }
}
