package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.TextEffectIds
import com.example.engine.TextRevealValues
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.FILL_SOLID
import com.example.model.LayerType
import com.example.model.PanelGeometry
import com.example.model.TextEditGeometry
import com.example.model.TimelineClip
import com.example.ui.text.FontCatalog
import com.example.ui.text.FontEntry
import com.example.ui.text.FontRepository
import com.example.ui.text.rememberFontFamily
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Build 266 — the Text editor, redesigned (Sarowar's order: the whole
 * Text section UI changes, "simple and clean", and it moves to C++
 * like the rest of the app). The old build-213 sheet (a section
 * toolbar switching nine tab panels) is replaced by ONE calm
 * full-screen surface drawn by the C++ GL UI path (the stage-2…6
 * pattern: layout law in [TextEditGeometry], GL surfaces via
 * [NativeVectorCanvas] + the shared label atlas, Compose keeping
 * layout, touch boxes, semantics and state wiring):
 *
 *   top bar (back / "Text" / lime Done) → preview block (the text
 *   itself, large, in its real look) → the typing field → one flat
 *   scrolling list of full-width rows grouped Font / Style / Color /
 *   Alignment / Spacing / Stroke / Shadow / Background / Reveal /
 *   Path. Every control of the old sheet survives with its label and
 *   its value wiring (the audit map is in the build-266 report).
 *
 * Hosted islands, by platform law / proven limits only: the typing
 * field and the font search field (Android IME — text input never
 * moves), the preview block's and preset tiles' text (the app's
 * exact multi-style text painter [TextLayerContent] — the GL atlas
 * blits a single flat style and cannot reproduce stroke / shadow /
 * gradient / background-box text; the build-251 atlas limit), and
 * the colour picker dialog (already a hosted dialog app-wide).
 * Changes apply live, exactly as the old sheet's did — Back and Done
 * both simply leave the screen (no discard semantics existed).
 */
@Composable
fun TextEditScreen(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** PATH-bearing shape layers of the project the text can attach to (build 191). */
    pathLayers: List<TimelineClip> = emptyList()
) {
    var text by remember(clip.id) { mutableStateOf(clip.text.orEmpty()) }
    val onText: (String) -> Unit = { v ->
        text = v
        onUpdate { it.copy(text = v, name = if (v.isBlank()) "Text" else "Text: ${v.take(24)}") }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmBase)
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        TextTopBar(onBack = onClose, onDone = onClose)
        Spacer(Modifier.height(TextEditGeometry.GAP_AFTER_BAR_DP.dp))
        TextPreviewBlock(clip)
        Spacer(Modifier.height(TextEditGeometry.GAP_AFTER_PREVIEW_DP.dp))
        TextBoxField(
            text, onText,
            Modifier.padding(horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp)
        )
        Spacer(Modifier.height(TextEditGeometry.GAP_AFTER_FIELD_DP.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp)
        ) {
            TextGroup("Font", first = true) {
                FontSection(clip, onUpdate)
            }
            TextGroup("Style") {
                GlPresetStrip(clip, onUpdate)
                Spacer(Modifier.height(10.dp))
                GlSegmentedRow(
                    listOf(
                        SegmentItem("Italic", null, clip.isItalic) {
                            onUpdate { it.copy(isItalic = !it.isItalic) }
                        },
                        SegmentItem("Underline", null, clip.isUnderline) {
                            onUpdate { it.copy(isUnderline = !it.isUnderline) }
                        },
                        SegmentItem("Strikethrough", null, clip.isStrikethrough) {
                            onUpdate { it.copy(isStrikethrough = !it.isStrikethrough) }
                        }
                    )
                )
            }
            TextGroup("Color") {
                GlColorRow(
                    label = "Color",
                    selected = clip.textColor,
                    onSelect = { c -> onUpdate { it.copy(textColor = c, fillMode = FILL_SOLID) } },
                    onEyedropper = onEyedropper
                )
            }
            TextGroup("Alignment") {
                GlSegmentedRow(
                    listOf(
                        SegmentItem("Left", Icons.Default.FormatAlignLeft, clip.textAlign == 0) {
                            onUpdate { it.copy(textAlign = 0) }
                        },
                        SegmentItem("Center", Icons.Default.FormatAlignCenter, clip.textAlign == 1) {
                            onUpdate { it.copy(textAlign = 1) }
                        },
                        SegmentItem("Right", Icons.Default.FormatAlignRight, clip.textAlign == 2) {
                            onUpdate { it.copy(textAlign = 2) }
                        }
                    )
                )
            }
            TextGroup("Spacing") {
                GlTextSliderRow("Letter space", clip.letterSpacing, -10f, 40f, "%") { v ->
                    onUpdate { it.copy(letterSpacing = v) }
                }
                GlTextSliderRow("Line height", clip.lineHeightPercent, 80f, 200f, "%") { v ->
                    onUpdate { it.copy(lineHeightPercent = v) }
                }
            }
            TextGroup("Stroke") {
                GlTextSliderRow("Thickness", clip.textStrokeThickness, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(textStrokeThickness = v) }
                }
                GlColorRow(
                    label = "Color",
                    selected = clip.textStrokeColor,
                    onSelect = { c -> onUpdate { it.copy(textStrokeColor = c) } },
                    onEyedropper = onEyedropper
                )
            }
            TextGroup("Shadow") {
                GlTextSliderRow("Opacity", clip.shadowOpacity, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(shadowOpacity = v) }
                }
                GlTextSliderRow("Blur", clip.shadowBlur, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(shadowBlur = v) }
                }
                GlTextSliderRow("Distance", clip.shadowDistance, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(shadowDistance = v) }
                }
                GlTextSliderRow("Angle", clip.shadowAngle, 0f, 360f, "°") { v ->
                    onUpdate { it.copy(shadowAngle = v) }
                }
                GlColorRow(
                    label = "Color",
                    selected = clip.shadowColor,
                    onSelect = { c -> onUpdate { it.copy(shadowColor = c) } },
                    onEyedropper = onEyedropper
                )
            }
            TextGroup("Background") {
                GlTextSliderRow("Opacity", clip.bgOpacity, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(bgOpacity = v) }
                }
                GlTextSliderRow("Corner", clip.bgCorner, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(bgCorner = v) }
                }
                GlTextSliderRow("Padding", clip.bgPadding, 0f, 100f, "%") { v ->
                    onUpdate { it.copy(bgPadding = v) }
                }
                GlColorRow(
                    label = "Color",
                    selected = clip.bgColor,
                    onSelect = { c ->
                        // Picking a color for a hidden background turns it on (old rule).
                        onUpdate {
                            it.copy(bgColor = c, bgOpacity = if (it.bgOpacity == 0f) 100f else it.bgOpacity)
                        }
                    },
                    onEyedropper = onEyedropper
                )
            }
            TextGroup("Reveal") {
                RevealSection(clip, onUpdate)
            }
            TextGroup("Path") {
                PathSection(clip, pathLayers, onUpdate)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Colours (the build-217 tokens, as ARGB for the GL draws) ───────

private val TxtSurfaceArgb = AmPanel.toArgb()
private val TxtAccentArgb = AmAccent.toArgb()
private val TxtInkArgb = AmInk.toArgb()
private val TxtWhiteArgb = TextPrimary.toArgb()
private val TxtDimArgb = AmTextDim.toArgb()
private val TxtSliderInactiveArgb = 0xFF3A3A3A.toInt()

// ── Chrome: top bar, preview, group headers ────────────────────────

@Composable
private fun TextTopBar(onBack: () -> Unit, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TextEditGeometry.TopBar.H_DP.dp)
            .padding(horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlBackButton(onBack)
        Spacer(Modifier.width(4.dp))
        GlLabelLine("Text", UiLabelStyle.HEADER_NAME, TxtWhiteArgb)
        Spacer(Modifier.weight(1f))
        GlVioletPill(label = "Done", onClick = onDone, tag = "text_edit_done")
    }
}

@Composable
private fun GlBackButton(onClick: () -> Unit) {
    val g = TextEditGeometry.TopBar
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val painter = rememberVectorPainter(Icons.Default.ArrowBack)
    fun resolveIcon(): UiLabelAtlas.Entry? =
        atlas.iconEntry("txted:back", painter, (g.BACK_ICON_DP * density).roundToInt(), density)
    var entry = resolveIcon()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveIcon()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.BACK_BOX_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                if (e != null) {
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    addText(
                        e.index, w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            scale, 0.9f, onClick,
            Modifier.fillMaxSize(),
            description = "Back"
        )
    }
}

/**
 * The preview block: a quiet surface carrying the text itself, drawn
 * by the app's exact text painter (the hosted island — see the
 * screen's doc), centred and clipped to the surface.
 */
@Composable
private fun TextPreviewBlock(clip: TimelineClip) {
    val g = TextEditGeometry.Preview
    BoxWithConstraints(
        modifier = Modifier
            .padding(horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp)
            .fillMaxWidth()
            .height(g.H_DP.dp)
            .clip(RoundedCornerShape(g.CORNER_DP.dp))
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = g,
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, g.CORNER_DP * dd, TxtSurfaceArgb)
            }
        )
        TextLayerContent(
            clip = clip,
            frameWidth = maxWidth,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun TextGroup(title: String, first: Boolean = false, content: @Composable () -> Unit) {
    if (!first) Spacer(Modifier.height(TextEditGeometry.GroupHeader.GAP_BEFORE_DP.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TextEditGeometry.GroupHeader.H_DP.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        GlLabelLine(title, UiLabelStyle.TEXT_11_SEMI, TxtDimArgb)
    }
    Spacer(Modifier.height(TextEditGeometry.GroupHeader.GAP_AFTER_DP.dp))
    content()
}

/** A wrapped note line in GL (the sections' explanatory texts). */
@Composable
private fun GlNoteText(text: String) {
    GlTextBlock(
        text = text,
        style = UiLabelStyle.ED_TEXT,
        colorArgb = TxtDimArgb,
        modifier = Modifier.fillMaxWidth(),
        align = TextAlign.Start
    )
}

// ── Hosted text fields (Android IME — text input never moves) ─────

/** The typing field: one clean hosted field under the preview. */
@Composable
private fun TextBoxField(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text("Type text", color = AmTextDim, fontSize = 14.sp) },
        maxLines = 3,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
        shape = RoundedCornerShape(AmCornerCard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AmAccent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** The font search field (hosted input, same IME law). */
@Composable
private fun FontSearchField(query: String, onQuery: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search Fonts", color = AmTextDim, fontSize = 14.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
        shape = RoundedCornerShape(AmCornerCard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AmAccent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Slider rows ────────────────────────────────────────────────────

/**
 * One slider row of the flat list: label (advance-sized, the
 * GlWidthRow pattern), the app's standard GL slider, and the value
 * readout formatted exactly as the old ruler slider's ("%d%s").
 */
@Composable
private fun GlTextSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    val g = TextEditGeometry.SliderRow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(g.H_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlLabelLine(label, UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
        Spacer(Modifier.width(g.LABEL_GAP_DP.dp))
        GlSlider(
            value = value.coerceIn(min, max),
            onValueChange = onValueChange,
            min = min,
            max = max,
            onValueChangeFinished = null,
            thumbArgb = TxtWhiteArgb,
            activeArgb = TxtWhiteArgb,
            inactiveArgb = TxtSliderInactiveArgb,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(g.VALUE_GAP_DP.dp))
        GlLabelLine(
            String.format(Locale.US, "%d%s", value.toInt(), unit),
            UiLabelStyle.TEXT_13_BOLD, TxtWhiteArgb
        )
    }
}

// ── Segmented rows (style toggles, alignment) ─────────────────────

private class SegmentItem(
    val label: String,
    val icon: ImageVector?,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * One segmented row on ONE GL surface: equal cells per
 * [TextEditGeometry.Segmented], selected = the lime fill with ink
 * content, one press box per cell.
 */
@Composable
private fun GlSegmentedRow(items: List<SegmentItem>, modifier: Modifier = Modifier) {
    val g = TextEditGeometry.Segmented
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val painters = items.map { item -> item.icon?.let { rememberVectorPainter(it) } }
    fun resolveAll(): List<Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?>> =
        items.mapIndexed { i, item ->
            val t = atlas.resolve(item.label, UiLabelStyle.TEXT_13_SEMI)
            val p = painters[i]
            val ic = if (p != null) {
                atlas.iconEntry(
                    "txtseg:${item.label}", p,
                    (g.ICON_DP * density).roundToInt(), density
                )
            } else null
            t to ic
        }
    var entries = resolveAll()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveAll()
    }
    val resolved = entries
    val scales = remember(items.map { it.label }) { items.map { Animatable(1f) } }
    val scaleValues = scales.map { it.value }
    BoxWithConstraints(modifier.fillMaxWidth().height(g.H_DP.dp)) {
        val rowWPx = maxWidth.value * density
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(resolved, scaleValues, rowWPx, atlas.generation),
            buildCmds = { w, h, dd ->
                val style = UiLabelStyle.TEXT_13_SEMI
                val textH = atlas.textHeightPx(style)
                val fm = atlas.fontMetrics(style)
                items.forEachIndexed { i, item ->
                    val cellW = g.cellWPx(w, items.size, dd)
                    val cellL = g.cellLeftPx(i, w, items.size, dd)
                    val cx = cellL + cellW / 2f
                    setScaleAbout(cx, h / 2f, scaleValues[i])
                    addRoundRect(
                        cx, h / 2f, cellW, h, g.CORNER_DP * dd,
                        if (item.selected) TxtAccentArgb else TxtSurfaceArgb
                    )
                    val (te, ie) = resolved[i]
                    val ink = if (item.selected) TxtInkArgb else TxtWhiteArgb
                    var x = g.runLeftPx(cellL, cellW, te?.advance ?: 0f, ie != null, dd)
                    if (ie != null) {
                        addText(
                            ie.index, x - pad, h / 2f - ie.rectH / 2f,
                            0f, 0f, ie.rectW.toFloat(), ie.rectH.toFloat(),
                            tintArgb = ink
                        )
                        x += (g.ICON_DP + g.ICON_GAP_DP) * dd
                    }
                    if (te != null) {
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(x, baseline, te.bearing, te.base, pad)
                        addText(
                            te.index, dstX, dstY,
                            0f, 0f, te.rectW.toFloat(), te.rectH.toFloat(),
                            tintArgb = ink
                        )
                    }
                    clearTransform()
                }
            }
        )
        items.forEachIndexed { i, item ->
            ChromePressBox(
                scales[i], 0.94f, item.onClick,
                Modifier
                    .offset(
                        x = (g.cellLeftPx(i, rowWPx, items.size, density) / density).dp,
                        y = 0.dp
                    )
                    .width((g.cellWPx(rowWPx, items.size, density) / density).dp)
                    .height(g.H_DP.dp),
                description = item.label
            )
        }
    }
}

// ── Small GL atoms: hug chip button, action row ───────────────────

/** A hug-content capsule button in GL (the old SheetChip's form). */
@Composable
private fun GlTextChipButton(label: String, onClick: () -> Unit) {
    val g = TextEditGeometry.Chips
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.TEXT_13_SEMI
    fun resolveChip() = atlas.resolve(label, style)
    var entry = resolveChip()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveChip()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    val wDp = if (e != null) (g.chipWPx(e.advance, density) / density).dp else 0.dp
    Box(Modifier.width(wDp).height(g.H_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, TxtSurfaceArgb)
                if (e != null) {
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.92f, onClick,
            Modifier.fillMaxSize(),
            description = label
        )
    }
}

/** A full-width action row in GL (the Path section's rows). */
@Composable
private fun GlActionRow(label: String, onClick: () -> Unit) {
    val g = TextEditGeometry.ActionRow
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.TEXT_13_MED
    fun resolveRow() = atlas.resolve(label, style)
    var entry = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveRow()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.fillMaxWidth().height(g.H_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, TxtSurfaceArgb)
                if (e != null) {
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        g.PAD_H_DP * density, baseline, e.bearing, e.base, pad
                    )
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.97f, onClick,
            Modifier.fillMaxSize(),
            description = label
        )
    }
}

// ── Colour rows ────────────────────────────────────────────────────

private val Swatches = listOf(
    0xFF9C27B0, 0xFFE91E63, 0xFFF44336, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000,
    0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF00E5FF, 0xFF2196F3, 0xFF3F51B5
)

/**
 * One colour row of the flat list (the old TextColorRow, GL-drawn):
 * the label, a scrolling strip of swatches (selected = lime
 * outline), then the custom-colour tile (opens the picker dialog)
 * and the eyedropper tile (pick from the canvas) pinned on the
 * right. The row follows the text section's own strip form — the
 * Color editor's grid has no eyedropper / custom tiles to pin.
 */
@Composable
private fun GlColorRow(
    label: String,
    selected: Long,
    onSelect: (Long) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    val g = TextEditGeometry.Swatches
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = Swatches.none { it == selected }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(g.ROW_H_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlLabelLine(label, UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
        Spacer(Modifier.width(g.LABEL_GAP_DP.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(g.GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Swatches.forEach { c ->
                GlSwatchTile(argb = c, selected = c == selected) { onSelect(c) }
            }
        }
        Spacer(Modifier.width(g.PIN_GAP_DP.dp))
        GlPaletteTile(selected = selected, isCustom = isCustom) { showPicker = true }
        Spacer(Modifier.width(g.GAP_DP.dp))
        GlEyedropperTile { onEyedropper(onSelect) }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = Color(selected),
            onPick = { onSelect(it.toArgb().toLong() and 0xFFFFFFFFL) },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun GlSwatchTile(argb: Long, selected: Boolean, onClick: () -> Unit) {
    val g = TextEditGeometry.Swatches
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.CELL_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(argb, selected, scale.value),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CORNER_DP * dd, argb.toInt(),
                    if (selected) 2f * dd else 0f,
                    if (selected) TxtAccentArgb else 0
                )
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.85f, onClick,
            Modifier.fillMaxSize(),
            description = "Color"
        )
    }
}

@Composable
private fun GlPaletteTile(selected: Long, isCustom: Boolean, onClick: () -> Unit) {
    val g = TextEditGeometry.Swatches
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val painter = rememberVectorPainter(Icons.Default.Palette)
    fun resolveIcon(): UiLabelAtlas.Entry? =
        atlas.iconEntry("txted:palette", painter, (17f * density).roundToInt(), density)
    var entry = resolveIcon()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveIcon()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.CELL_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, selected, isCustom, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CORNER_DP * dd,
                    if (isCustom) selected.toInt() else 0,
                    1.5f * dd, 0xFFFFFFFF.toInt()
                )
                if (e != null) {
                    addText(
                        e.index, w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.88f, onClick,
            Modifier.fillMaxSize(),
            description = "Custom color"
        )
    }
}

@Composable
private fun GlEyedropperTile(onClick: () -> Unit) {
    val g = TextEditGeometry.Swatches
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val painter = rememberVectorPainter(Icons.Default.Colorize)
    fun resolveIcon(): UiLabelAtlas.Entry? =
        atlas.iconEntry("txted:eyedropper", painter, (17f * density).roundToInt(), density)
    var entry = resolveIcon()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveIcon()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.CELL_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, g.CORNER_DP * dd, TxtSurfaceArgb)
                if (e != null) {
                    addText(
                        e.index, w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.88f, onClick,
            Modifier.fillMaxSize(),
            description = "Pick from canvas"
        )
    }
}

// ── Font section ───────────────────────────────────────────────────

/**
 * The Font group: the search field (hosted), the Add font chip +
 * the horizontally scrolling strip of font chips (each name in its
 * own face, selected = lime outline), the device-only note, and the
 * font weight pill row — the old Fonts tab + weight row, flattened.
 */
@Composable
private fun FontSection(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var userFonts by remember { mutableStateOf(FontRepository.userFonts(context)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = FontRepository.importUserFont(context, uri)
            if (name != null) {
                userFonts = FontRepository.userFonts(context)
                onUpdate { it.copy(fontFamily = name) }
            }
        }
    }

    val fonts = remember(query, userFonts) {
        val all = userFonts.map { FontEntry(it, FontCatalog.USER_CATEGORY) } + FontCatalog.all
        all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    FontSearchField(query) { query = it }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlTextChipButton("+ Add font") {
            picker.launch(
                arrayOf(
                    "font/ttf", "font/otf", "font/sfnt",
                    "application/x-font-ttf", "application/x-font-otf",
                    "application/octet-stream"
                )
            )
        }
        Spacer(Modifier.width(8.dp))
        GlFontChipStrip(
            fonts = fonts,
            selectedName = clip.fontFamily,
            onSelect = { name -> onUpdate { it.copy(fontFamily = name) } },
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(8.dp))
    // Build 201 (audit S-6): imported fonts live in this app's private
    // storage on THIS device — say so where the user adds them.
    GlNoteText("Fonts you add are stored on this device only — they don't travel with the project file.")
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlLabelLine("Font weight", UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
        Spacer(Modifier.width(TextEditGeometry.Chips.LABEL_GAP_DP.dp))
        GlPillStrip(
            items = listOf(200, 400, 500, 600, 700, 800, 900).map { w ->
                GlPillItem(
                    label = "$w",
                    selected = clip.fontWeight == w,
                    onClick = { onUpdate { it.copy(fontWeight = w) } }
                )
            },
            labelStyle = { UiLabelStyle.TEXT_13_BOLD },
            textArgb = { sel -> if (sel) TxtInkArgb else TxtWhiteArgb },
            fillArgb = { sel -> if (sel) TxtAccentArgb else TxtSurfaceArgb },
            borderArgb = { null },
            padHDp = 14f,
            padVDp = 8f,
            gapDp = 6f,
            minHDp = TextEditGeometry.Chips.H_DP,
            scrollable = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The font chip strip on ONE GL surface: hug-content chips per
 * [TextEditGeometry.Chips], each name rasterized in its own face
 * (see [rememberFontChipEntry]), selected = lime outline.
 */
@Composable
private fun GlFontChipStrip(
    fonts: List<FontEntry>,
    selectedName: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val g = TextEditGeometry.Chips
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val entries = fonts.map { f -> key(f.name) { rememberFontChipEntry(atlas, f.name) } }
    val widths = entries.map { g.chipWPx(it?.advance ?: 0f, density) }
    val lefts = g.leftsPx(widths, density)
    val contentW = g.contentWPx(widths, density)
    val scales = remember(fonts.map { it.name }) { fonts.map { Animatable(1f) } }
    val scaleValues = scales.map { it.value }
    Row(modifier.horizontalScroll(rememberScrollState())) {
        Box(
            Modifier
                .width((contentW / density).dp)
                .height(g.H_DP.dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(entries, scaleValues, widths, selectedName, atlas.generation),
                buildCmds = { _, h, dd ->
                    fonts.forEachIndexed { i, f ->
                        val chipW = widths[i]
                        val chipL = lefts[i]
                        val cx = chipL + chipW / 2f
                        val sel = f.name == selectedName
                        setScaleAbout(cx, h / 2f, scaleValues[i])
                        addRoundRect(
                            cx, h / 2f, chipW, h, g.CORNER_DP * dd, TxtSurfaceArgb,
                            if (sel) 2f * dd else 0f,
                            if (sel) TxtAccentArgb else 0
                        )
                        val e = entries[i]
                        if (e != null) {
                            addText(
                                e.index,
                                chipL + (chipW - e.rectW) / 2f, (h - e.rectH) / 2f,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TxtWhiteArgb
                            )
                        }
                        clearTransform()
                    }
                }
            )
            fonts.forEachIndexed { i, f ->
                ChromePressBox(
                    scales[i], 0.92f, { onSelect(f.name) },
                    Modifier
                        .offset(x = (lefts[i] / density).dp, y = 0.dp)
                        .width((widths[i] / density).dp)
                        .height(g.H_DP.dp),
                    description = f.name
                )
            }
        }
    }
}

/** File typefaces for the chip rasters, loaded once per font file. */
private val fontChipTypefaces = ConcurrentHashMap<String, Typeface>()

/** The typeface a font name's chip is rasterized in (its own face). */
private fun typefaceForFont(context: android.content.Context, name: String): Typeface =
    when (name) {
        "Roboto", "System", "" -> Typeface.DEFAULT
        "Serif" -> Typeface.SERIF
        "Monospace" -> Typeface.MONOSPACE
        "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
        else -> {
            val file = FontRepository.fileOrNull(context, name)
            if (file == null) {
                Typeface.DEFAULT
            } else {
                fontChipTypefaces.getOrPut(file.absolutePath) {
                    runCatching { Typeface.createFromFile(file) }.getOrNull() ?: Typeface.DEFAULT
                }
            }
        }
    }

/**
 * The atlas entry for one font chip: the font's name rasterized in
 * its own face by Android's text stack (the atlas pattern — native
 * only blits it). [rememberFontFamily] keeps the old behaviour: an
 * unseen Google font downloads when first shown; until its file is
 * here the name rasterizes in the default face, and the entry's key
 * flips when the real face arrives.
 */
@Composable
private fun rememberFontChipEntry(atlas: UiLabelAtlas, name: String): UiLabelAtlas.Entry? {
    val context = LocalContext.current
    rememberFontFamily(name)
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val ready = FontRepository.resolveNow(context, name) != null
    fun resolveFont(): UiLabelAtlas.Entry? {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = typefaceForFont(context, name)
        paint.textSize = TextEditGeometry.Chips.FONT_TEXT_SP * density * fontScale
        paint.color = android.graphics.Color.WHITE
        val adv = paint.measureText(name)
        if (adv <= 0f) return null
        val fm = paint.fontMetrics
        val inkH = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val key = "fontchip:$name:${if (ready) "ready" else "def"}:${paint.textSize}"
        return atlas.resolveIcon(key, ceil(adv).toInt().coerceAtLeast(1), inkH) { cv, x, y ->
            cv.drawText(name, x, y - fm.ascent, paint)
        }
    }
    var entry = resolveFont()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveFont()
    }
    return entry
}

// ── Style preset strip ─────────────────────────────────────────────

private val PreviewClip = TimelineClip(
    id = "preview", name = "", type = LayerType.TEXT, startMs = 0, durationMs = 1, color = 0,
    text = "Aa", fontWeight = 700, bgOpacity = 0f
)

/**
 * The Style group's preset tiles (the old Style tab's top half): a
 * scrolling strip of tiles, each carrying an exact "Aa" preview of
 * the preset (the hosted painter island — see the screen's doc);
 * the current preset carries the lime outline.
 */
@Composable
private fun GlPresetStrip(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val g = TextEditGeometry.Presets
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(g.GAP_DP.dp)
    ) {
        TextPresets.forEach { preset ->
            val isCurrent = clip.textColor == preset.fill &&
                clip.textStrokeThickness == preset.strokeThickness &&
                clip.shadowOpacity == preset.shadowOpacity &&
                (preset.shadowOpacity == 0f || clip.shadowBlur == preset.shadowBlur)
            GlPresetTile(preset = preset, isCurrent = isCurrent) {
                onUpdate { preset.applyTo(it) }
            }
        }
    }
}

@Composable
private fun GlPresetTile(preset: TextPreset, isCurrent: Boolean, onClick: () -> Unit) {
    val g = TextEditGeometry.Presets
    val scale = remember { Animatable(1f) }
    Box(
        Modifier
            .size(g.TILE_DP.dp)
            .clip(RoundedCornerShape(g.CORNER_DP.dp))
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(isCurrent, scale.value),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CORNER_DP * dd, TxtSurfaceArgb,
                    if (isCurrent) 2f * dd else 0f,
                    if (isCurrent) TxtAccentArgb else 0
                )
                clearTransform()
            }
        )
        TextLayerContent(
            clip = preset.applyTo(PreviewClip),
            frameWidth = 220.dp,
            modifier = Modifier.align(Alignment.Center)
        )
        ChromePressBox(
            scale, 0.93f, onClick,
            Modifier.fillMaxSize(),
            description = "Style preset"
        )
    }
}

// ── Reveal section ─────────────────────────────────────────────────

/**
 * Reveal group (typewriter window, Alight `textprogress`): a static
 * start/end window of the text that is shown. An imported keyframed
 * reveal is preserved — the sliders stay hidden while it is
 * animated, and only an explicit tap replaces it with a static
 * window frozen at the finished state (see TextRevealValues).
 */
@Composable
private fun RevealSection(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val reveal = clip.textAnimators.firstOrNull { it.effectId == TextEffectIds.TEXTPROGRESS }
    if (TextRevealValues.isKeyframed(reveal)) {
        GlNoteText("Animated reveal — the window is keyframed in Alight Motion, so it changes over time.")
        Spacer(Modifier.height(6.dp))
        GlTextChipButton("Use static window") {
            val (s, e) = TextRevealValues.windowAt(reveal, 1f)
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, s, e)) }
        }
    } else {
        val (revealStart, revealEnd) = TextRevealValues.windowAt(reveal, 0f)
        GlTextSliderRow("Reveal start", revealStart * 100f, 0f, 100f, "%") { v ->
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, v / 100f, revealEnd)) }
        }
        GlTextSliderRow("Reveal end", revealEnd * 100f, 0f, 100f, "%") { v ->
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, revealStart, v / 100f)) }
        }
    }
}

// ── Path section (text-on-path, build 191) ────────────────────────

/**
 * Path group: attach this text to a PATH shape layer — the glyphs
 * then follow that layer's outline, evaluated live. The text follows
 * the path even when the path layer is hidden; hiding is not
 * detaching. While attached, the Start offset slider moves the text
 * along the path (a share of the path's whole length); text longer
 * than the path is cut at the path's end.
 */
@Composable
private fun PathSection(
    clip: TimelineClip,
    pathLayers: List<TimelineClip>,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val g = TextEditGeometry.LabelRow
    val attachedId = clip.textPathLayerId
    val attached = pathLayers.firstOrNull { it.id == attachedId }
    when {
        attached != null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(g.H_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(g.LABEL_W_DP.dp)) {
                    GlLabelLine("Layer", UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
                }
                GlLabelLine(
                    attached.name, UiLabelStyle.TEXT_13_BOLD, TxtWhiteArgb,
                    modifier = Modifier.weight(1f),
                    ellipsize = true
                )
            }
            GlTextSliderRow("Start offset", clip.textPathStart * 100f, 0f, 100f, "%") { v ->
                onUpdate { it.copy(textPathStart = v / 100f) }
            }
            GlActionRow("Detach from path") { onUpdate { it.copy(textPathLayerId = null) } }
        }
        attachedId != null -> {
            GlNoteText("The path layer this text was attached to is gone.")
            Spacer(Modifier.height(6.dp))
            GlActionRow("Detach") { onUpdate { it.copy(textPathLayerId = null) } }
        }
        pathLayers.isEmpty() -> {
            GlNoteText("No path layer in this project yet. Draw one with the Pen tool first, then attach this text to it here.")
        }
        else -> {
            GlNoteText("Attach to a path layer — the text will follow its outline:")
            Spacer(Modifier.height(6.dp))
            pathLayers.forEach { layer ->
                GlActionRow(layer.name) { onUpdate { it.copy(textPathLayerId = layer.id) } }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
