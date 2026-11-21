package com.example.ui.components

import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmSheetShape

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.SheetGeometry
import com.example.ui.theme.AmCyan
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import kotlin.math.roundToInt

/** Opened by the round "+" button and the add-layer icon: what kind of layer to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerSheet(
    onMedia: () -> Unit,
    onText: () -> Unit,
    onShape: () -> Unit,
    onAudio: () -> Unit,
    onSticker: () -> Unit,
    /** Ready-made compositions (build 143): opens the Templates picker. */
    onTemplates: () -> Unit = {},
    /** Freehand drawing (build 166): draw a stroke that becomes a shape layer. */
    onDraw: () -> Unit = {},
    /** Point-based Pen (build 177): tap places anchor points, drag pulls bezier handles — a vector path layer. */
    onPen: () -> Unit = {},
    /** Helper layers (build 214: they lived on the old bottom toolbar; the dock's Add sheet is their home now). */
    onNull: () -> Unit = {},
    onController: () -> Unit = {},
    onChain: () -> Unit = {},
    onCamera: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 18.dp)
        ) {
            // Build 253 (stage 6): the title draws in GL.
            GlLabelLine("Add layer", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Item("Media", Icons.Default.AddPhotoAlternate, "add_sheet_media") { onDismiss(); onMedia() }
                Item("Text", Icons.Default.TextFields, "add_sheet_text") { onDismiss(); onText() }
                Item("Shape", Icons.Default.Category, "add_sheet_shape") { onDismiss(); onShape() }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Item("Audio", Icons.Default.MusicNote, "add_sheet_audio") { onDismiss(); onAudio() }
                Item("Stickers", Icons.Default.SentimentSatisfiedAlt, "add_sheet_sticker") { onDismiss(); onSticker() }
                Item("Templates", Icons.Default.Layers, "add_sheet_templates") { onDismiss(); onTemplates() }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Item("Draw", Icons.Default.Brush, "add_sheet_draw") { onDismiss(); onDraw() }
                Item("Pen", Icons.Default.Polyline, "add_sheet_pen") { onDismiss(); onPen() }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(7.dp))
            // Build 214: the helper layers (they were the old toolbar's
            // Null / Controller / Chain / Camera items).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Item("Null", Icons.Default.CenterFocusWeak, "add_sheet_null") { onDismiss(); onNull() }
                Item("Controller", Icons.Default.Tune, "add_sheet_controller") { onDismiss(); onController() }
                Item("Chain", Icons.Default.ViewStream, "add_sheet_chain") { onDismiss(); onChain() }
                Item("Camera", Icons.Default.Videocam, "add_sheet_camera") { onDismiss(); onCamera() }
            }
        }
    }
}

/**
 * Build 253 (stage 6): one add-layer tile on a single GL surface — the
 * card fill (unscaled: it sat outside bouncyClickable's graphicsLayer)
 * with the icon + label press-scaled about the centre. A rounded card
 * tile (build 207 redesign): the same tile language as the tools grid.
 */
@Composable
private fun RowScope.Item(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    val g = SheetGeometry.AddTile
    val painter = rememberVectorPainter(icon)
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val labelH = atlas.textHeightPx(UiLabelStyle.SMALL_MED)
    fun resolveTile(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val t = atlas.resolve(label, UiLabelStyle.SMALL_MED)
        val i = atlas.iconEntry("addtile:$label", painter, (g.ICON_DP * density).roundToInt(), density)
        return t to i
    }
    var entries = resolveTile()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveTile()
    }
    val (labelE, iconE) = entries
    val tileH = g.heightPx(labelH, density)
    val scale = remember { Animatable(1f) }

    Box(
        Modifier
            .weight(1f)
            .height((tileH / density).dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(labelE, iconE, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, g.CORNER_DP * dd, AmCardSurface.toArgb())
                setScaleAbout(w / 2f, h / 2f, scale.value)
                iconE?.let { e ->
                    addText(
                        e.index,
                        w / 2f - e.rectW / 2f, g.PAD_V_DP * dd - pad,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = AmCyan.toArgb()
                    )
                }
                labelE?.let { e ->
                    val style = UiLabelStyle.SMALL_MED
                    val fm = atlas.fontMetrics(style)
                    val top = (g.PAD_V_DP + g.ICON_DP + g.GAP_DP) * dd
                    val baseline = PanelGeometry.lineBaselinePx(top, labelH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = AmTextDim.toArgb()
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            tag = tag,
            description = label
        )
    }
}
