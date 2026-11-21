package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.SheetGeometry
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable

/**
 * Build 206 — the shared pill components of the frontend-redesign language
 * (Sarowar's references: floating pill bars, a selected item reading as a
 * filled pill with icon + label, one violet accent). Builds 207/208 reuse
 * these for the sheets, dialogs and remaining surfaces.
 */

/**
 * A floating pill bar: a full-capsule surface ([AmBarSurface]) holding a row
 * of items. The bar itself carries no outer margin — the host places it
 * (screen padding / section insets), so width budgets stay the host's.
 */
@Composable
fun FloatingPillBar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .background(AmBarSurface, CircleShape)
            .clip(CircleShape)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * One item of a pill toolbar: icon + label side by side. Selected = the
 * filled [AmPillSelected] capsule around icon + label (the references'
 * selected tab); unselected = bare, dim. The touch target is at least
 * 40 dp tall via the item padding.
 */
@Composable
fun PillToolItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    enabled: Boolean = true
) {
    var m = modifier
        .semantics { contentDescription = label }
        .bouncyClickable(enabled = enabled, onClick = onClick)
    if (testTag != null) m = m.testTag(testTag)
    Box(m, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .then(
                    if (selected) Modifier
                        .background(AmPillSelected, CircleShape)
                        .clip(CircleShape)
                    else Modifier
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) TextPrimary else AmTextDim,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = if (selected) TextPrimary else AmTextDim,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Build 207 — the centred drag-handle pill every bottom sheet carries:
 * a 36 x 5 dp capsule in the raised-surface grey, with breathing room above
 * and below. Pass it as an AppBottomSheet's `dragHandle`.
 *
 * Build 253 (UI migration stage 6): the capsule draws on a GL surface
 * (SheetGeometry.Handle); the strip's size and the pill's place in it
 * are unchanged.
 */
@Composable
fun SheetDragHandle() {
    val g = SheetGeometry.Handle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((g.PAD_TOP_DP + g.PILL_H_DP + g.PAD_BOTTOM_DP).dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = "sheet_drag_handle",
            buildCmds = { w, _, dd ->
                addRoundRect(
                    w / 2f, g.pillCentreYPx(dd),
                    g.PILL_W_DP * dd, g.PILL_H_DP * dd, g.PILL_H_DP * dd / 2f,
                    AmPillSelected.toArgb()
                )
            }
        )
    }
}

/**
 * Build 207 — the dialog / sheet action pill: a full capsule, at least
 * 40 dp tall, label centred. The colour pairing is the call site's choice
 * via the wrappers below (violet primary, ghost secondary, danger).
 *
 * Build 253 (UI migration stage 6): the pill draws on one GL surface
 * (SheetGeometry.PillButton — hug width, the 32 dp floor); the fill
 * sat outside bouncyClickable's graphicsLayer, so only the label
 * press-scales, via ChromePressBox. A caller's own width/height in
 * [modifier] still wins (the size is applied after it in the chain
 * only as the hug default).
 */
@Composable
fun PillActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null
) {
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.PILL_BTN
    fun resolveBtn() = atlas.resolve(text, style)
    var entry = resolveBtn()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveBtn()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    val textH = atlas.textHeightPx(style)
    val hDp = (SheetGeometry.PillButton.heightPx(textH, density) / density).dp
    val wDp = if (e != null) {
        (SheetGeometry.PillButton.hugWidthPx(e.advance, density) / density).dp
    } else {
        0.dp
    }
    // The build-252 disabled colours verbatim: the fill takes an
    // absolute 0.45 alpha copy; the label takes an absolute 0.55 copy
    // and bouncyClickable's graphicsLayer then multiplies the content
    // by a further 0.45 while disabled (0.55 * 0.45 = 0.2475).
    val fillArgb = (if (enabled) containerColor else containerColor.copy(alpha = 0.45f)).toArgb()
    val textArgb = (if (enabled) contentColor else contentColor.copy(alpha = 0.55f * 0.45f)).toArgb()
    Box(
        modifier
            .width(wDp)
            .height(hDp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, fillArgb, textArgb, atlas.generation),
            buildCmds = { w, h, _ ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, fillArgb)
                if (e != null) {
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = textArgb
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
            description = text
        )
    }
}

/** The primary dialog action: the violet pill, white label. */
@Composable
fun DialogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null
) = PillActionButton(text, onClick, AmAccentViolet, com.example.ui.theme.AmInk, modifier, enabled, tag) // build 216: ink on lime

/** The secondary dialog action: the filled selected-pill grey, white label. */
@Composable
fun DialogSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    contentColor: Color = TextPrimary
) = PillActionButton(text, onClick, AmPillSelected, contentColor, modifier, enabled, tag)

/** The destructive dialog action: red label on the ghost pill (red stays a semantic signal). */
@Composable
fun DialogDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null
) = PillActionButton(text, onClick, AmPillSelected, AmDanger, modifier, enabled, tag)

/**
 * Build 207 — the filled pill text field: a [AmPanelHigh] capsule holding a
 * single-line (by default) input, with an optional small label above it.
 * The Projects dialog's search pill (build 206) is this same construction;
 * dialogs use it in place of the old outlined field. Behaviour (value,
 * change callback, length caps at the call site) is unchanged.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    tag: String? = null
) {
    var m = modifier.fillMaxWidth()
    if (tag != null) m = m.testTag(tag)
    Column(m) {
        if (label != null) {
            Text(
                label,
                color = AmTextDim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 5.dp, bottom = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AmPanelHigh, CircleShape)
                .clip(CircleShape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(TextPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = TextSecondary, fontSize = 13.sp)
                    }
                    inner()
                }
            )
        }
    }
}

/**
 * The violet primary pill button (build 206) — REMOVED in build 253
 * (UI migration stage 6): both call sites (the shape editor's save
 * pill, the Add Text sheet's add pill) carried a single text label,
 * and now call [GlVioletPill], the GL-drawn form of the same pill.
 */
