package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.SheetGeometry
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmSheetShape
import com.example.ui.theme.IconActive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Parent picker: the layer follows the chosen parent layer (a Null object), or is CONNECTED
 * to the chosen Controller (build 146: a connection, not a parent — edits of the Controller
 * fan out onto the connected layers). [nulls] = (id, name) of the layers it may link to
 * (no loops), [currentId] = the link now (the parent, else the connection; null = none).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentSheet(
    layerName: String,
    nulls: List<Pair<String, String>>,
    currentId: String?,
    onPick: (String?) -> Unit,
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
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp)
                .testTag("parent_sheet")
        ) {
            // Build 253 (stage 6): the sheet's texts draw in GL.
            GlLabelLine("Parent of $layerName", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
            Spacer(Modifier.height(3.dp))
            GlTextBlock(
                "The layer follows the move, turn and size of its parent (a Null). A Controller is not a parent: the layer is only connected to it, and whatever is done to the Controller is done to the connected layers too, each in its own place.",
                UiLabelStyle.SHEET_DESC, TextSecondary.toArgb(),
                Modifier.fillMaxWidth(),
                align = androidx.compose.ui.text.style.TextAlign.Start
            )
            Spacer(Modifier.height(10.dp))
            Row1("None", null, currentId == null) { onPick(null) }
            for ((id, name) in nulls) {
                Spacer(Modifier.height(6.dp))
                Row1(name, id, currentId == id) { onPick(id) }
            }
            if (nulls.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                GlTextBlock(
                    "No Null or Controller layer yet. Add one using the Null / Controller buttons in the bottom toolbar.",
                    UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                    Modifier.fillMaxWidth(),
                    align = androidx.compose.ui.text.style.TextAlign.Start
                )
            }
        }
    }
}

/**
 * Build 253 (stage 6): one parent-picker row on a single GL surface —
 * the card fill (unscaled: it sat outside bouncyClickable's
 * graphicsLayer), the focus icon, the name and the selection check
 * press-scaled about the centre. Row height = the taller of the 18dp
 * icon and the name's font box, plus the 9dp vertical padding.
 */
@Composable
private fun Row1(name: String, id: String?, selected: Boolean, onClick: () -> Unit) {
    val focusPainter = rememberVectorPainter(Icons.Default.CenterFocusWeak)
    val checkPainter = rememberVectorPainter(Icons.Default.Check)
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val nameStyle = if (selected) UiLabelStyle.TEXT_125_BOLD else UiLabelStyle.TEXT_125
    val nameH = atlas.textHeightPx(nameStyle)
    fun resolveRow(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val n = atlas.resolve(name, nameStyle)
        val f = if (id != null) {
            atlas.iconEntry("parent:focus", focusPainter, (18f * density).roundToInt(), density)
        } else null
        val c = if (selected) {
            atlas.iconEntry("parent:check", checkPainter, (16f * density).roundToInt(), density)
        } else null
        return Triple(n, f, c)
    }
    var entries = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveRow()
    }
    val (nameE, focusE, checkE) = entries
    val rowH = maxOf(nameH, 18f * density) + 2f * SheetGeometry.ListRow.PAD_V_DP * density
    val scale = remember { Animatable(1f) }

    Box(
        Modifier
            .fillMaxWidth()
            .height((rowH / density).dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(nameE, focusE, checkE, scale.value, selected, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(
                    w / 2f, h / 2f, w, h, SheetGeometry.ListRow.CORNER_DP * dd,
                    (if (selected) AmPillSelected else AmCardSurface).toArgb()
                )
                setScaleAbout(w / 2f, h / 2f, scale.value)
                focusE?.let { e ->
                    addText(
                        e.index, 12f * dd - pad, (h - e.rectH) / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = IconActive.toArgb()
                    )
                }
                nameE?.let { e ->
                    val fm = atlas.fontMetrics(nameStyle)
                    val top = EditorGeometry.centredTextTopPx(0f, h, nameH)
                    val baseline = PanelGeometry.lineBaselinePx(top, nameH, fm.ascent, fm.descent)
                    val textX = (if (id != null) 40f else 12f) * dd
                    val (dstX, dstY) = EditorGeometry.textDst(textX, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                checkE?.let { e ->
                    addText(
                        e.index, w - 12f * dd - e.rectW + pad, (h - e.rectH) / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = NeonCyan.toArgb()
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            tag = "parent_pick_${id ?: "none"}",
            description = name
        )
    }
}
