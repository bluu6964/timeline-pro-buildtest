package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.PanelGeometry
import com.example.model.ToolRowSplit
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.IconActive
import kotlin.math.roundToInt

/**
 * Build 251 (UI migration stage 4): one tool of a clip/audio tools row,
 * the shape both panels build (id, label, icon, used flag, tap target).
 */
class ToolRowItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val used: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * The clip/audio tools row drawn on ONE native GL surface — positions
 * from [PanelGeometry.ToolRow] (the build-250 layout, host-tested),
 * icons rasterized into the stage-2 atlas, labels through the atlas.
 * A used tile is the lime pill with ink icon/label; the label is
 * ellipsized exactly where the old Compose Text ellipsized (a tile the
 * row coerced narrower than its natural width). Tap targets stay
 * Compose: invisible boxes at the law's rects with bouncyClickable's
 * contract (ChromePressBox, 0.88 — the tiles' and back button's own
 * press scale), the press scale shared into the GL draw. The panels
 * keep their tool lists, dispatch and overflow publishing; only the
 * rendering moved. [outerPadDp]/[rowPadDp] are the panels' own
 * paddings (clip 3/3, audio 2/2).
 */
@Composable
internal fun ToolsRowGl(
    items: List<ToolRowItem>,
    onBack: () -> Unit,
    backTestTag: String,
    backDescription: String,
    outerPadDp: Float,
    rowPadDp: Float,
    modifier: Modifier = Modifier
) {
    val g = PanelGeometry.ToolRow
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()

    // Stable painters per tool id (a tool id's icon never changes).
    val painters = items.map { item -> key(item.id) { rememberVectorPainter(item.icon) } }
    val backPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack)

    val iconPx = (g.TILE_ICON_DP * density).roundToInt()
    val backPx = (g.BACK_ICON_DP * density).roundToInt()

    fun resolveAll(): RowEntries {
        val back = atlas.resolveIcon("panel:back", backPx, backPx) { cv, x, y ->
            val b = rasterizePainterBitmap(backPainter, backPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val icons = items.mapIndexed { i, item ->
            atlas.resolveIcon("tool-icon:${item.icon.name}", iconPx, iconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(painters[i], iconPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        }
        val labels = items.map { item ->
            atlas.resolve(item.label, if (item.used) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL)
        }
        return RowEntries(back, icons, labels)
    }
    var entries = resolveAll()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveAll()
    }

    val backScale = remember { Animatable(1f) }
    val tileScales = remember(items.map { it.id }) { items.map { Animatable(1f) } }
    val scaleValues = listOf(backScale.value) + tileScales.map { it.value }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = maxWidth.value * density
        val heightPx = maxHeight.value * density
        val contentTop = g.contentTopPx(outerPadDp, rowPadDp, density)
        val contentH = g.contentHeightPx(heightPx, outerPadDp, rowPadDp, density)
        val centreY = contentTop + contentH / 2f
        val tilesLeft = g.tilesLeftPx(density)
        val gapPx = ToolRowSplit.GAP_DP * density
        val natural = items.mapIndexed { i, _ ->
            g.tileWidthPx(entries.labels[i]?.advance ?: 0f, density)
        }
        val boxes = g.layoutTilesPx(natural, tilesLeft, gapPx, widthPx)

        // Ellipsized label entries for tiles the row coerced (the old
        // Text's maxLines-1 ellipsis at the tile's inner width).
        val drawnLabels = items.mapIndexed { i, item ->
            val style = if (item.used) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL
            val e = entries.labels[i]
            if (e != null && boxes[i].widthPx < natural[i] - 0.5f) {
                val cap = boxes[i].widthPx - g.TILE_PAD_H_DP * 2f * density
                val cut = ChromeGeometry.ellipsize(item.label, cap) { atlas.measure(it, style) }
                if (cut != item.label) atlas.resolve(cut, style) ?: e else e
            } else e
        }

        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(entries, drawnLabels, boxes, scaleValues, atlas.generation),
            buildCmds = { w, h, dd ->
                // The bar.
                addRoundRect(
                    w / 2f, centreY, w, contentH,
                    g.BAR_CORNER_DP * dd, AmBarSurface.toArgb()
                )
                // The back icon, press-scaled about its centre.
                entries.back?.let { e ->
                    setScaleAbout(g.backCenterPx(dd), centreY, backScale.value)
                    addText(
                        e.index,
                        g.backIconLeftPx(dd) - pad,
                        g.backIconTopPx(contentTop, contentH, dd) - pad,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                    clearTransform()
                }
                // The hairline divider.
                addRect(
                    g.dividerLeftPx(dd), g.dividerTopPx(contentTop, contentH, dd),
                    g.DIVIDER_W_DP * dd, g.DIVIDER_H_DP * dd, AmHairline.toArgb()
                )
                // The tiles.
                items.forEachIndexed { i, item ->
                    val box = boxes[i]
                    val style = if (item.used) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL
                    val textH = atlas.textHeightPx(style)
                    val ascent = atlas.fontMetrics(style).ascent
                    val stackTop = g.stackTopPx(contentTop, contentH, textH, dd)
                    val tint = (if (item.used) AmInk else AmTextDim).toArgb()
                    setScaleAbout(box.leftPx + box.widthPx / 2f, centreY, tileScales[i].value)
                    if (item.used) {
                        addRoundRect(
                            box.leftPx + box.widthPx / 2f, centreY,
                            box.widthPx, g.pillHeightPx(textH, dd),
                            g.TILE_CORNER_DP * dd, AmPillSelected.toArgb()
                        )
                    }
                    entries.icons[i]?.let { e ->
                        addText(
                            e.index,
                            box.leftPx + (box.widthPx - g.TILE_ICON_DP * dd) / 2f - pad,
                            stackTop - pad,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = tint
                        )
                    }
                    drawnLabels[i]?.let { e ->
                        addText(
                            e.index,
                            box.leftPx + (box.widthPx - e.advance) / 2f + e.bearing - pad,
                            stackTop + (g.TILE_ICON_DP + g.TILE_GAP_DP) * dd - ascent - e.base,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = tint
                        )
                    }
                    clearTransform()
                }
            }
        )

        // Tap targets at the law's rects, invisible.
        val contentTopDp = (contentTop / density).dp
        val contentHDp = (contentH / density).dp
        ChromePressBox(
            backScale, 0.88f, onBack,
            Modifier
                .offset(
                    x = g.BACK_LEFT_DP.dp,
                    y = ((contentTop + (contentH - g.BACK_BOX_DP * density) / 2f) / density).dp
                )
                .size(g.BACK_BOX_DP.dp),
            tag = backTestTag,
            description = backDescription
        )
        items.forEachIndexed { i, item ->
            ChromePressBox(
                tileScales[i], 0.88f, item.onClick,
                Modifier
                    .offset(x = (boxes[i].leftPx / density).dp, y = contentTopDp)
                    .width((boxes[i].widthPx / density).dp)
                    .height(contentHDp),
                tag = item.testTag,
                description = item.label
            )
        }
    }
}

private data class RowEntries(
    val back: UiLabelAtlas.Entry?,
    val icons: List<UiLabelAtlas.Entry?>,
    val labels: List<UiLabelAtlas.Entry?>
)
