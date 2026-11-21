package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.PanelGeometry
import kotlin.math.roundToInt

/**
 * Shown on the timeline while the person is INSIDE a group (Edit Group): the layers on screen are the layers of that group.
 * Tap it to go back out (the group is then selected again and works as one layer).
 *
 * Build 251 (UI migration stage 4): the pill is drawn on a native GL
 * surface — geometry from [PanelGeometry.GroupBar] (host-tested), the
 * back arrow as an atlas raster, the name through the atlas (ellipsized
 * with the atlas paint at the old 84 dp box). The tap target stays a
 * Compose box with the pill clip (its ripple draws over the surface).
 */
@Composable
fun GroupEditBar(name: String, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val g = PanelGeometry.GroupBar
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val backPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack)

    val textH = atlas.textHeightPx(UiLabelStyle.GROUP_NAME)
    val ascent = atlas.fontMetrics(UiLabelStyle.GROUP_NAME).ascent
    val shown = ChromeGeometry.ellipsize("  $name", g.TEXT_W_DP * density) {
        atlas.measure(it, UiLabelStyle.GROUP_NAME)
    }
    fun resolveAll(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val iconPx = (g.ICON_DP * density).roundToInt()
        val back = atlas.resolveIcon("group:back", iconPx, iconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(backPainter, iconPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val nameEntry = atlas.resolve(shown, UiLabelStyle.GROUP_NAME)
        return back to nameEntry
    }
    var entries = resolveAll()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveAll()
    }
    val surfaceW = g.surfaceWidthPx(density)
    val surfaceH = g.surfaceHeightPx(textH, density)

    Box(modifier = modifier.padding(4.dp)) {
        Box(
            modifier = Modifier.size(
                (surfaceW / density).dp,
                (surfaceH / density).dp
            )
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(entries, atlas.generation),
                buildCmds = { w, h, dd ->
                    addRoundRect(w / 2f, h / 2f, w, h, h / 2f, g.BG_ARGB)
                    entries.first?.let { e ->
                        addText(
                            e.index,
                            g.iconLeftPx(dd) - pad,
                            g.iconTopPx(textH, dd) - pad,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = Color.White.toArgb()
                        )
                    }
                    entries.second?.let { e ->
                        addText(
                            e.index,
                            g.textLeftPx(dd) + e.bearing - pad,
                            g.textBoxTopPx(textH, dd) - ascent - e.base,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = Color.White.toArgb()
                        )
                    }
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable { onExit() }
                    .testTag("group_edit_bar")
            )
        }
    }
}
