package com.example.ui.components

import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBaseDeep
import com.example.ui.theme.AmInk
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.util.bouncyClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.ShapeCatalog
import com.example.model.SheetGeometry
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.model.AspectRatio
import com.example.model.LayerType
import com.example.model.PreviewResolution
import com.example.model.TimelineTrack
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmSheetShape
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.EditorBackground
import com.example.ui.theme.EditorSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

/** Export resolutions offered in the canvas sheet: label (parsed by the exporter) + short hint. */
val ExportResolutions = listOf(
    "180p" to "320 x 180",
    "360p" to "640 x 360",
    "480p SD" to "854 x 480",
    "720p HD" to "1280 x 720",
    "1080p FHD" to "1920 x 1080"
)

/** Export limits: short side in pixels (180p..1080p) and frame rate (24..60 fps). */
const val ExportMinResolution = 180
const val ExportMaxResolution = 1080
const val ExportMinFps = 24
const val ExportMaxFps = 60
val ExportFpsPresets = listOf(24, 25, 30, 50, 60)
val ExportQualities = listOf("Low", "Medium", "High (Recommended)", "Max")

/** "720p HD" / "540p" -> 720 / 540 (clamped to the allowed range). */
fun exportResolutionValue(label: String): Int =
    (Regex("\\d{3,4}").find(label)?.value?.toInt() ?: ExportMaxResolution)
        .coerceIn(ExportMinResolution, ExportMaxResolution)

/** Label for a short-side value: presets keep their name, anything else becomes e.g. "540p". */
fun exportResolutionLabel(value: Int): String {
    val v = value.coerceIn(ExportMinResolution, ExportMaxResolution)
    return ExportResolutions.firstOrNull { exportResolutionValue(it.first) == v }?.first ?: "${v}p"
}

private val CanvasSwatches = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF1C1D20, 0xFF8A8A8A, 0xFFFF4444, 0xFFF5B342,
    0xFFFAE764, 0xFF1FD91F, 0xFF18E2E2, 0xFF3F4DF2, 0xFFD73FDA, 0xFF5BB5D9
)

/**
 * Opened from the aspect-ratio button in the header: canvas shape, canvas (background)
 * colour and export resolution. Changes are applied live; swipe down to close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSheet(
    currentRatio: AspectRatio,
    originalAspect: Float,
    onRatioSelected: (AspectRatio) -> Unit,
    canvasColor: Long,
    onCanvasColor: (Long) -> Unit,
    resolution: String,
    onResolution: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        // Build 253 (UI migration stage 6): the sheet's cards, swatches
        // and chips draw on GL surfaces (SheetGeometry.Aspect); the
        // three LazyRows become horizontal scroll rows of per-item
        // surfaces (the stage-4 grid precedent — the sets are small
        // and fixed). Fills sat outside the press layer in Compose;
        // outlines, icons and labels press-scale via ChromePressBox.
        val g = SheetGeometry.Aspect
        val density = LocalDensity.current.density
        val atlas = rememberUiAtlas()
        val pad = UiLabelAtlas.PAD.toFloat()
        val palettePainter = rememberVectorPainter(Icons.Default.Palette)
        val ratioLabelH = atlas.textHeightPx(UiLabelStyle.TEXT_135_MED)
        val resLabelH = atlas.textHeightPx(UiLabelStyle.TEXT_13_MED)
        val resDetailH = atlas.textHeightPx(UiLabelStyle.SHEET_DESC)

        fun resolveAspect(): AspectEntries {
            val ratios = AspectRatio.values().map { atlas.resolve(it.label, UiLabelStyle.TEXT_135_MED) }
            val resLabels = ExportResolutions.map { atlas.resolve(it.first, UiLabelStyle.TEXT_13_MED) }
            val resDetails = ExportResolutions.map { atlas.resolve(it.second, UiLabelStyle.SHEET_DESC) }
            val palettePx = (19f * density).roundToInt()
            val palette = atlas.iconEntry("aspect:palette", palettePainter, palettePx, density)
            return AspectEntries(ratios, resLabels, resDetails, palette)
        }
        var entries = resolveAspect()
        if (atlas.overflowed) {
            atlas.reset()
            entries = resolveAspect()
        }
        val ratios = AspectRatio.values()
        val cardScales = remember { ratios.map { Animatable(1f) } }
        val cardScaleValues = cardScales.map { it.value }
        val customScale = remember { Animatable(1f) }
        val swatchScales = remember { CanvasSwatches.map { Animatable(1f) } }
        val swatchScaleValues = swatchScales.map { it.value }
        val chipScales = remember { ExportResolutions.map { Animatable(1f) } }
        val chipScaleValues = chipScales.map { it.value }
        val custom = CanvasSwatches.none { (it and 0xFFFFFFL) == (canvasColor and 0xFFFFFFL) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp)
                .testTag("aspect_ratio_sheet")
        ) {
            SheetTitle("Aspect Ratio")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ratios.forEachIndexed { i, ratio ->
                    val selected = ratio.label == currentRatio.label
                    val shape = if (ratio.isOriginal && originalAspect > 0.05f) originalAspect else ratio.ratioFloat
                    val tint = if (selected) 0xFF1C1D20.toInt() else 0xFFE5E5E7.toInt()
                    Box(
                        Modifier
                            .width(g.CARD_DP.dp)
                            .height(g.CARD_DP.dp)
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            clearArgb = 0,
                            atlas = atlas,
                            drawKey = listOf(entries.ratios[i], cardScaleValues[i], selected, shape, atlas.generation),
                            buildCmds = { w, h, dd ->
                                addRoundRect(
                                    w / 2f, h / 2f, w, h, g.CARD_CORNER_DP * dd,
                                    if (selected) 0xFFE8E4E4.toInt() else 0xFF3A3A3D.toInt()
                                )
                                setScaleAbout(w / 2f, h / 2f, cardScales[i].value)
                                val stackTop = g.stackTopPx(ratioLabelH, dd)
                                val (sw, sh) = g.shapeSizePx(shape, dd)
                                addRoundRect(
                                    w / 2f, stackTop + g.SHAPE_BOX_DP * dd / 2f,
                                    sw, sh, 2.5f * dd,
                                    0, 2f * dd, tint
                                )
                                entries.ratios[i]?.let { e ->
                                    val style = UiLabelStyle.TEXT_135_MED
                                    val fm = atlas.fontMetrics(style)
                                    val labelTop = stackTop + (g.SHAPE_BOX_DP + g.SHAPE_GAP_DP) * dd
                                    val baseline = PanelGeometry.lineBaselinePx(labelTop, ratioLabelH, fm.ascent, fm.descent)
                                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                                    addText(
                                        e.index, dstX, dstY,
                                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                        tintArgb = tint
                                    )
                                }
                                clearTransform()
                            }
                        )
                        ChromePressBox(
                            cardScales[i], 0.94f, { onRatioSelected(ratio) },
                            Modifier.fillMaxSize(),
                            tag = "aspect_${ratio.label}",
                            description = ratio.label
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SheetTitle("Canvas color")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(g.SWATCH_DP.dp)
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = atlas,
                        drawKey = listOf(entries.palette, customScale.value, custom, canvasColor, atlas.generation),
                        buildCmds = { w, h, dd ->
                            val bw = if (custom) 2.5f else 1f
                            addCircle(
                                w / 2f, h / 2f, w / 2f,
                                if (custom) Color(canvasColor).toArgb() else 0xFF3A3A3D.toInt()
                            )
                            addCircle(
                                w / 2f, h / 2f, w / 2f - bw * dd / 2f,
                                0, bw * dd,
                                if (custom) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt()
                            )
                            setScaleAbout(w / 2f, h / 2f, customScale.value)
                            entries.palette?.let { e ->
                                addText(
                                    e.index, (w - e.rectW) / 2f, (h - e.rectH) / 2f,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = 0xFFFFFFFF.toInt()
                                )
                            }
                            clearTransform()
                        }
                    )
                    ChromePressBox(
                        customScale, 0.9f, { showPicker = true },
                        Modifier.fillMaxSize(),
                        tag = "canvas_color_custom",
                        description = "Custom color"
                    )
                }
                CanvasSwatches.forEachIndexed { i, c ->
                    val selected = (c and 0xFFFFFFL) == (canvasColor and 0xFFFFFFL)
                    Box(
                        Modifier.size(g.SWATCH_DP.dp)
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            clearArgb = 0,
                            drawKey = listOf(selected, c),
                            buildCmds = { w, h, dd ->
                                val bw = if (selected) 2.5f else 1f
                                addCircle(w / 2f, h / 2f, w / 2f, Color(c).toArgb())
                                addCircle(
                                    w / 2f, h / 2f, w / 2f - bw * dd / 2f,
                                    0, bw * dd,
                                    if (selected) 0xFFFFFFFF.toInt() else 0x2EFFFFFF
                                )
                            }
                        )
                        ChromePressBox(
                            swatchScales[i], 0.88f, { onCanvasColor(c) },
                            Modifier.fillMaxSize(),
                            description = "Canvas color"
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SheetTitle("Resolution")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportResolutions.forEachIndexed { i, (label, detail) ->
                    val selected = label == resolution
                    val chipW = g.resChipWidthPx(
                        entries.resLabels[i]?.advance ?: 0f,
                        entries.resDetails[i]?.advance ?: 0f,
                        density
                    )
                    val chipH = g.resChipHeightPx(resLabelH, resDetailH, density)
                    Box(
                        Modifier
                            .width((chipW / density).dp)
                            .height((chipH / density).dp)
                    ) {
                        NativeVectorCanvas(
                            modifier = Modifier.fillMaxSize(),
                            clearArgb = 0,
                            atlas = atlas,
                            drawKey = listOf(entries.resLabels[i], entries.resDetails[i], chipScaleValues[i], selected, atlas.generation),
                            buildCmds = { w, h, dd ->
                                addRoundRect(
                                    w / 2f, h / 2f, w, h, 11f * dd,
                                    if (selected) 0xFFE8E4E4.toInt() else 0xFF3A3A3D.toInt()
                                )
                                setScaleAbout(w / 2f, h / 2f, chipScales[i].value)
                                entries.resLabels[i]?.let { e ->
                                    val style = UiLabelStyle.TEXT_13_MED
                                    val fm = atlas.fontMetrics(style)
                                    val top = g.CHIP_PAD_V_DP * dd
                                    val baseline = PanelGeometry.lineBaselinePx(top, resLabelH, fm.ascent, fm.descent)
                                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                                    addText(
                                        e.index, dstX, dstY,
                                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                        tintArgb = if (selected) 0xFF1C1D20.toInt() else 0xFFE5E5E7.toInt()
                                    )
                                }
                                entries.resDetails[i]?.let { e ->
                                    val style = UiLabelStyle.SHEET_DESC
                                    val fm = atlas.fontMetrics(style)
                                    val top = g.CHIP_PAD_V_DP * dd + resLabelH
                                    val baseline = PanelGeometry.lineBaselinePx(top, resDetailH, fm.ascent, fm.descent)
                                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                                    addText(
                                        e.index, dstX, dstY,
                                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                        tintArgb = if (selected) 0xFF55555A.toInt() else 0xFF9A9AA0.toInt()
                                    )
                                }
                                clearTransform()
                            }
                        )
                        ChromePressBox(
                            chipScales[i], 0.94f, { onResolution(label) },
                            Modifier.fillMaxSize(),
                            tag = "resolution_$label",
                            description = label
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = Color(canvasColor),
            onPick = { onCanvasColor(it.toArgb().toLong() and 0xFFFFFFFFL) },
            onDismiss = { showPicker = false }
        )
    }
}

/** Build 253: the sheet section title draws in GL (16 sp Bold). */
@Composable
private fun SheetTitle(text: String) {
    GlLabelLine(
        text = text,
        style = UiLabelStyle.TITLE_16,
        colorArgb = TextPrimary.toArgb(),
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
    )
}

private data class AspectEntries(
    val ratios: List<UiLabelAtlas.Entry?>,
    val resLabels: List<UiLabelAtlas.Entry?>,
    val resDetails: List<UiLabelAtlas.Entry?>,
    val palette: UiLabelAtlas.Entry?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionSheet(
    currentResolution: PreviewResolution,
    onResolutionSelected: (PreviewResolution) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        // Build 253 (stage 6): title and rows draw on GL surfaces
        // (SheetGeometry.ListRow — the sheets' shared row form); the
        // column's padding and the 4 dp row spacers are unchanged.
        val g = SheetGeometry.ListRow
        val density = LocalDensity.current.density
        val atlas = rememberUiAtlas()
        val pad = UiLabelAtlas.PAD.toFloat()
        val checkPainter = rememberVectorPainter(Icons.Default.Check)
        val titleH = atlas.textHeightPx(UiLabelStyle.SHEET_TITLE)
        val labelH = atlas.textHeightPx(UiLabelStyle.SEMI_125)
        val detailH = atlas.textHeightPx(UiLabelStyle.SHEET_DESC)
        val rowH = g.twoLineHeightPx(labelH, detailH, density)

        fun resolveRes(): ResEntries {
            val title = atlas.resolve("Preview Resolution", UiLabelStyle.SHEET_TITLE)
            val labels = PreviewResolution.values().map { atlas.resolve(it.label, UiLabelStyle.SEMI_125) }
            val details = PreviewResolution.values().map { atlas.resolve(it.detail, UiLabelStyle.SHEET_DESC) }
            val checkPx = (16f * density).roundToInt()
            val check = atlas.iconEntry("sheet:check16", checkPainter, checkPx, density)
            return ResEntries(title, labels, details, check)
        }
        var entries = resolveRes()
        if (atlas.overflowed) {
            atlas.reset()
            entries = resolveRes()
        }
        val resolutions = PreviewResolution.values()
        val rowScales = remember { resolutions.map { Animatable(1f) } }
        val rowScaleValues = rowScales.map { it.value }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 18.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((titleH / density).dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = atlas,
                    drawKey = listOf(entries.title, atlas.generation),
                    buildCmds = { _, _, _ ->
                        entries.title?.let { e ->
                            val style = UiLabelStyle.SHEET_TITLE
                            val fm = atlas.fontMetrics(style)
                            val baseline = PanelGeometry.lineBaselinePx(0f, titleH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(0f, baseline, e.bearing, e.base, pad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            resolutions.forEachIndexed { i, res ->
                val isSelected = res == currentResolution
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((rowH / density).dp)
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = atlas,
                        drawKey = listOf(entries.labels[i], entries.details[i], entries.check, rowScaleValues[i], isSelected, atlas.generation),
                        buildCmds = { w, h, dd ->
                            addRoundRect(
                                w / 2f, h / 2f, w, h, g.CORNER_DP * dd,
                                (if (isSelected) AmPillSelected else AmCardSurface).toArgb()
                            )
                            setScaleAbout(w / 2f, h / 2f, rowScales[i].value)
                            val stackTop = g.stackTopPx(h, labelH + detailH)
                            entries.labels[i]?.let { e ->
                                val style = UiLabelStyle.SEMI_125
                                val fm = atlas.fontMetrics(style)
                                val baseline = PanelGeometry.lineBaselinePx(stackTop, labelH, fm.ascent, fm.descent)
                                val (dstX, dstY) = EditorGeometry.textDst(11f * dd, baseline, e.bearing, e.base, pad)
                                addText(
                                    e.index, dstX, dstY,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = TextPrimary.toArgb()
                                )
                            }
                            entries.details[i]?.let { e ->
                                val style = UiLabelStyle.SHEET_DESC
                                val fm = atlas.fontMetrics(style)
                                val baseline = PanelGeometry.lineBaselinePx(stackTop + labelH, detailH, fm.ascent, fm.descent)
                                val (dstX, dstY) = EditorGeometry.textDst(11f * dd, baseline, e.bearing, e.base, pad)
                                addText(
                                    e.index, dstX, dstY,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = TextSecondary.toArgb()
                                )
                            }
                            if (isSelected) {
                                entries.check?.let { e ->
                                    addText(
                                        e.index,
                                        w - 11f * dd - e.rectW.toFloat() + pad,
                                        (h - e.rectH) / 2f,
                                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                        tintArgb = com.example.ui.theme.AmAccent.toArgb()
                                    )
                                }
                            }
                            clearTransform()
                        }
                    )
                    ChromePressBox(
                        rowScales[i], 0.94f,
                        {
                            onResolutionSelected(res)
                            onDismiss()
                        },
                        Modifier.fillMaxSize(),
                        description = res.label
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private data class ResEntries(
    val title: UiLabelAtlas.Entry?,
    val labels: List<UiLabelAtlas.Entry?>,
    val details: List<UiLabelAtlas.Entry?>,
    val check: UiLabelAtlas.Entry?
)

/** Full-screen dark export screen (badge logo with "AM", big % and remaining time). */
@Composable
fun ExportModalDialog(
    isExporting: Boolean,
    isCompleted: Boolean,
    progress: Float,
    onStartExport: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    resolutionLabel: String = "1080p FHD",
    frameRate: Int = 30,
    onResolutionChange: (String) -> Unit = {},
    onFrameRateChange: (Int) -> Unit = {},
    quality: String = "High (Recommended)",
    onQualityChange: (String) -> Unit = {},
    bitrateKbps: Int = 0,
    onBitrateChange: (Int) -> Unit = {},
    format: String = com.example.model.ExportFormats.MP4,
    onFormatChange: (String) -> Unit = {},
    gifFrameRate: Int = com.example.model.GifExport.DEFAULT_FPS,
    onGifFrameRateChange: (Int) -> Unit = {},
    savedToLabel: String = "Saved to Movies/Timeline Pro",
    /** Export range (build 199, A10): the set range as text ("0:12 – 0:20"), or null = whole project. */
    rangeLabel: String? = null,
    /** Clears the export range from inside the dialog. */
    onClearRange: (() -> Unit)? = null
) {
    // Time the current export started (reset every time a new export begins).
    var startedAt by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(isExporting) {
        if (isExporting) startedAt = android.os.SystemClock.elapsedRealtime()
    }
    val remainingMs: Long? = if (isExporting && progress > 0.03f) {
        val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
        (elapsed * (1f - progress) / progress).toLong()
    } else null

    Dialog(
        onDismissRequest = { if (isExporting) onCancel() else onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isExporting,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogMotion180(fadeOnly = true)
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Build 223: the unified editor background token.
                .background(com.example.ui.theme.AmBase)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isExporting && !isCompleted) {
                    ExportSettingsPage(
                        resolutionLabel = resolutionLabel,
                        frameRate = frameRate,
                        quality = quality,
                        bitrateKbps = bitrateKbps,
                        format = format,
                        gifFrameRate = gifFrameRate,
                        onResolutionChange = onResolutionChange,
                        onFrameRateChange = onFrameRateChange,
                        onQualityChange = onQualityChange,
                        onBitrateChange = onBitrateChange,
                        onFormatChange = onFormatChange,
                        onGifFrameRateChange = onGifFrameRateChange,
                        onStartExport = onStartExport,
                        rangeLabel = rangeLabel,
                        onClearRange = onClearRange
                    )
                } else {
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(14.dp))

                when {
                    isCompleted -> {
                        // Build 253 (stage 6): the complete page's texts
                        // and the Share pill draw in GL; Done is the
                        // (already GL) PillActionButton.
                        GlLabelLine(
                            "Export Complete!", UiLabelStyle.TEXT_22_BLACK, TextPrimary.toArgb(),
                            Modifier.fillMaxWidth(),
                            align = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        GlTextBlock(
                            savedToLabel, UiLabelStyle.TEXT_12_MED, TextSecondary.toArgb(),
                            Modifier.fillMaxWidth(),
                            align = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        // Pill actions (build 209, the redesign language): Done is the
                        // white pill, Share the filled grey pill — same row, same weights.
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            PillActionButton(
                                text = "Done",
                                onClick = onDismiss,
                                containerColor = AmAccentViolet, // build 216: Done = the lime primary
                                contentColor = AmInk,
                                modifier = Modifier.weight(1f).height(40.dp)
                            )
                            SharePillGl(
                                onClick = onShare,
                                modifier = Modifier.weight(1f).height(40.dp)
                            )
                        }
                    }
                    isExporting -> {
                        // Build 223: the progress ring in the unified language — a lime
                        // arc on the elevated-surface track, the % centred inside.
                        // Build 253: the % and the two lines under it draw in GL.
                        Box(
                            modifier = Modifier.size(134.dp).testTag("export_progress_ring"),
                            contentAlignment = Alignment.Center
                        ) {
                            NativeVectorCanvas(
                                modifier = Modifier.fillMaxSize(),
                                drawKey = progress,
                                buildCmds = { w, h, d ->
                                    val stroke = 8f * d
                                    val cx = w / 2f
                                    val cy = h / 2f
                                    val rx = (w - stroke) / 2f
                                    val ry = (h - stroke) / 2f
                                    addArc(
                                        cx = cx, cy = cy, rx = rx, ry = ry,
                                        startDeg = 0f, sweepDeg = 360f, width = stroke,
                                        strokeArgb = com.example.ui.theme.AmPanelElevated.toArgb(),
                                        capRound = false, closedPie = false
                                    )
                                    val sweep = progress.coerceIn(0f, 1f) * 360f
                                    if (sweep > 0.01f) {
                                        addArc(
                                            cx = cx, cy = cy, rx = rx, ry = ry,
                                            startDeg = -90f, sweepDeg = sweep, width = stroke,
                                            strokeArgb = com.example.ui.theme.AmAccent.toArgb(),
                                            capRound = false, closedPie = false
                                        )
                                    }
                                },
                            )
                            GlTextLine(
                                "${(progress * 100).toInt()}%",
                                UiLabelStyle.TEXT_30_BLACK,
                                TextPrimary.toArgb(),
                                Modifier.fillMaxSize(),
                                align = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        GlLabelLine(
                            "Remaining time approximately ${formatRemaining(remainingMs)}",
                            UiLabelStyle.TEXT_13_SEMI, TextPrimary.toArgb(),
                            Modifier.fillMaxWidth(),
                            align = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        GlTextBlock(
                            "Please do not exit the application or lock the screen",
                            UiLabelStyle.SEMI_125, TextSecondary.toArgb(),
                            Modifier.fillMaxWidth(),
                            align = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Bottom pill tab with X: cancels a running export, otherwise closes the screen.
            // Build 253: the tab draws in GL; a transparent clickable
            // overlay keeps the stock ripple, tag and semantics.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .width(74.dp)
                    .height(44.dp)
            ) {
                val closePainter = rememberVectorPainter(Icons.Default.Close)
                val density = LocalDensity.current.density
                val atlas = rememberUiAtlas()
                val closePx = (20f * density).roundToInt()
                fun resolveClose() =
                    atlas.iconEntry("export:close", closePainter, closePx, density)
                var closeE = resolveClose()
                if (atlas.overflowed) {
                    atlas.reset()
                    closeE = resolveClose()
                }
                val ce = closeE
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = atlas,
                    drawKey = listOf(ce, atlas.generation),
                    buildCmds = { w, h, _ ->
                        addRoundRect(
                            w / 2f, h / 2f, w, h, h / 2f,
                            com.example.ui.theme.AmPanelElevated.toArgb()
                        )
                        ce?.let { e ->
                            addText(
                                e.index,
                                w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                    }
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { if (isExporting) onCancel() else onDismiss() }
                        .testTag("export_close")
                        .semantics { contentDescription = "Close" }
                )
            }
        }
    }
}

/**
 * Build 253 (stage 6): the export-complete Share pill on one GL
 * surface — the AmPanelElevated capsule (unscaled: it sat outside
 * bouncyClickable's graphicsLayer) with the icon + label press-scaled
 * about the centre, exactly like the build-252 Row inside the pill.
 */
@Composable
private fun SharePillGl(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sharePainter = rememberVectorPainter(Icons.Default.Share)
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val textH = atlas.textHeightPx(UiLabelStyle.TEXT_125_BOLD)
    fun resolveShare(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val t = atlas.resolve("Share", UiLabelStyle.TEXT_125_BOLD)
        val iconPx = (14f * density).roundToInt()
        val i = atlas.iconEntry("export:share", sharePainter, iconPx, density)
        return t to i
    }
    var entries = resolveShare()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveShare()
    }
    val (textE, iconE) = entries
    val scale = remember { Animatable(1f) }

    Box(modifier = modifier) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(textE, iconE, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, com.example.ui.theme.AmPanelElevated.toArgb())
                setScaleAbout(w / 2f, h / 2f, scale.value)
                val adv = textE?.advance ?: 0f
                val iconW = iconE?.rectW?.toFloat() ?: 0f
                val totalW = iconW + 5f * dd + adv
                var x = (w - totalW) / 2f
                iconE?.let { e ->
                    addText(
                        e.index, x, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                    x += e.rectW + 5f * dd
                }
                textE?.let { e ->
                    val style = UiLabelStyle.TEXT_125_BOLD
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(x - pad, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            description = "Share"
        )
    }
}

private fun formatRemaining(ms: Long?): String {
    val total = ((ms ?: 0L) / 1000L).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

// Build 223: the export page speaks the unified token language — card values,
// slider and selected chips use AmAccent; unselected chips sit on AmPanelElevated.

/** Settings page: scrolling cards on top, "Export Now" pinned at the bottom (always visible). */
@Composable
private fun ColumnScope.ExportSettingsPage(
    resolutionLabel: String,
    frameRate: Int,
    quality: String,
    bitrateKbps: Int,
    format: String,
    gifFrameRate: Int,
    onResolutionChange: (String) -> Unit,
    onFrameRateChange: (Int) -> Unit,
    onQualityChange: (String) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onFormatChange: (String) -> Unit,
    onGifFrameRateChange: (Int) -> Unit,
    onStartExport: () -> Unit,
    rangeLabel: String? = null,
    onClearRange: (() -> Unit)? = null
) {
    val resValue = exportResolutionValue(resolutionLabel)
    val isGif = format == com.example.model.ExportFormats.GIF
    val isMp4 = format == com.example.model.ExportFormats.MP4
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        ExportBadge(modifier = Modifier.fillMaxWidth().height(92.dp))
        // Build 253: the page title draws in GL.
        GlLabelLine(
            "Export settings", UiLabelStyle.TITLE_17, TextPrimary.toArgb(),
            Modifier.padding(top = 2.dp)
        )

        ExportCard(
            "Format",
            when (format) {
                com.example.model.ExportFormats.GIF -> "GIF"
                com.example.model.ExportFormats.PNG_SEQUENCE -> "PNG"
                else -> "MP4"
            },
            when (format) {
                com.example.model.ExportFormats.GIF -> "Animated GIF, no sound, up to 720p"
                com.example.model.ExportFormats.PNG_SEQUENCE -> "One PNG per frame, no sound"
                else -> "H.264 video with sound"
            }
        ) {
            ExportChipRow {
                ExportChip("MP4", isMp4) { onFormatChange(com.example.model.ExportFormats.MP4) }
                ExportChip("GIF", isGif) { onFormatChange(com.example.model.ExportFormats.GIF) }
                ExportChip("PNG sequence", format == com.example.model.ExportFormats.PNG_SEQUENCE) {
                    onFormatChange(com.example.model.ExportFormats.PNG_SEQUENCE)
                }
            }
        }

        // Build 199 (A10): the export range, when set on the ruler — only this window is
        // exported. Clearing here returns to exporting the whole project.
        if (rangeLabel != null) {
            ExportCard("Range", rangeLabel, "Only this part of the project is exported") {
                ExportChipRow {
                    ExportChip("Clear range", false) { onClearRange?.invoke() }
                }
            }
        }

        ExportCard("Resolution", "${resValue}p", "180p – 1080p") {
            ExportSlider(
                value = resValue.toFloat(),
                range = ExportMinResolution.toFloat()..ExportMaxResolution.toFloat(),
                tag = "export_resolution_slider"
            ) { onResolutionChange(exportResolutionLabel(it.toInt())) }
            ExportChipRow {
                ExportResolutions.forEach { (label, _) ->
                    ExportChip(label.substringBefore(' '), exportResolutionValue(label) == resValue) {
                        onResolutionChange(label)
                    }
                }
            }
        }

        if (isGif) {
            ExportCard("Frame rate", "$gifFrameRate fps", "5 – 30 fps · lower = smaller file") {
                ExportSlider(
                    value = gifFrameRate.toFloat(),
                    range = com.example.model.GifExport.MIN_FPS.toFloat()..com.example.model.GifExport.MAX_FPS.toFloat(),
                    tag = "export_gif_fps_slider"
                ) { onGifFrameRateChange(it.toInt().coerceIn(com.example.model.GifExport.MIN_FPS, com.example.model.GifExport.MAX_FPS)) }
                ExportChipRow {
                    listOf(10, 12, 15, 20, 24, 30).forEach { f ->
                        ExportChip("$f", f == gifFrameRate) { onGifFrameRateChange(f) }
                    }
                }
            }
        } else {
            ExportCard("Frame rate", "$frameRate fps", "24 – 60 fps") {
                ExportSlider(
                    value = frameRate.toFloat(),
                    range = ExportMinFps.toFloat()..ExportMaxFps.toFloat(),
                    tag = "export_fps_slider"
                ) { onFrameRateChange(it.toInt().coerceIn(ExportMinFps, ExportMaxFps)) }
                ExportChipRow {
                    ExportFpsPresets.forEach { f ->
                        ExportChip("$f", f == frameRate) { onFrameRateChange(f) }
                    }
                }
            }
        }

        val custom = bitrateKbps > 0
        if (isMp4) ExportCard(
            "Quality",
            if (custom) "%.1f Mbps".format(bitrateKbps / 1000f) else quality.substringBefore(' '),
            if (custom) "Custom bitrate" else "Automatic bitrate"
        ) {
            ExportChipRow {
                ExportQualities.forEach { q ->
                    ExportChip(q.substringBefore(' '), !custom && q == quality) {
                        onBitrateChange(0); onQualityChange(q)
                    }
                }
                ExportChip("Custom", custom) { if (!custom) onBitrateChange(8000) }
            }
            if (custom) {
                ExportSlider(
                    value = bitrateKbps.toFloat(),
                    range = 500f..50_000f,
                    tag = "export_bitrate_slider"
                ) { onBitrateChange(((it / 100f).toInt() * 100).coerceIn(500, 50_000)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlLabelLine("0.5 Mbps", UiLabelStyle.TEXT_11, TextTertiary.toArgb())
                    GlLabelLine("50 Mbps", UiLabelStyle.TEXT_11, TextTertiary.toArgb())
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }

    Spacer(modifier = Modifier.height(8.dp))
    // The Export action: the one lime primary pill (DialogPrimaryButton).
    DialogPrimaryButton(
        text = "Export Now",
        onClick = onStartExport,
        modifier = Modifier.fillMaxWidth().height(42.dp),
        tag = "export_now"
    )
    // Room for the round "close" tab at the bottom of the screen.
    Spacer(modifier = Modifier.height(54.dp))
}

/**
 * Build 253 (stage 6): the export card is a GlPanelBody (the AmPanel
 * fill + AmHairline ring drawn in GL) with its header texts as GL
 * leaves; the card content (chips/sliders) is GL too.
 */
@Composable
private fun ExportCard(title: String, value: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
    GlPanelBody(
        modifier = Modifier.fillMaxWidth(),
        cornerDp = AmCornerCard.value,
        fillArgb = AmPanel.toArgb(),
        borderArgb = AmHairline.toArgb()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GlLabelLine(title, UiLabelStyle.TEXT_13_SEMI, TextPrimary.toArgb())
                    GlLabelLine(hint, UiLabelStyle.TEXT_10, TextTertiary.toArgb())
                }
                GlLabelLine(value, UiLabelStyle.TEXT_145_BOLD, com.example.ui.theme.AmAccent.toArgb())
            }
            content()
        }
    }
}

/** Build 253: the export slider is the stage-5 GlSlider with the lime palette. */
@Composable
private fun ExportSlider(value: Float, range: ClosedFloatingPointRange<Float>, tag: String, onChange: (Float) -> Unit) {
    GlSlider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        min = range.start,
        max = range.endInclusive,
        onValueChangeFinished = null,
        thumbArgb = com.example.ui.theme.AmAccent.toArgb(),
        activeArgb = com.example.ui.theme.AmAccent.toArgb(),
        inactiveArgb = com.example.ui.theme.AmPanelElevated.toArgb(),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun ExportChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

/** Build 253: the export chip is the stage-5 GlChip (30dp min height, 11dp side padding). */
@Composable
private fun ExportChip(text: String, selected: Boolean, onClick: () -> Unit) {
    GlChip(
        label = text,
        selected = selected,
        tag = "",
        onClick = onClick,
        padHDp = 11f,
        padVDp = 0f,
        pressScale = 0.94f,
        styleOn = UiLabelStyle.TEXT_11_SEMI,
        styleOff = UiLabelStyle.TEXT_11_SEMI,
        fillOnArgb = com.example.ui.theme.AmAccent.toArgb(),
        fillOffArgb = com.example.ui.theme.AmPanelElevated.toArgb(),
        textOnArgb = AmInk.toArgb(), // build 223: ink on the lime chip
        textOffArgb = TextPrimary.toArgb(),
        minHDp = 30f
    )
}


/**
 * Logo badge: a wavy seal in the unified surface colour with a ring of text and "TP"
 * in the middle, plus a big faint ring of text around it. Both rings turn slowly
 * (opposite directions). Build 223 restyled it onto the unified tokens and the
 * current app name (the build-220 rename had missed this Canvas-drawn artwork).
 *
 * Build 253 (stage 6): DELIBERATELY NOT migrated — the two rings are
 * text laid out on a circular path (nativeCanvas drawTextOnPath) under
 * continuous rotation; the GL command set has no text-on-path command
 * and the label atlas blits axis-aligned rects only, so the rings
 * cannot cross faithfully. The wavy seal itself is already drawn by
 * NativeVectorCanvas. Documented in the final remaining-UI inventory.
 */
@Composable
private fun ExportBadge(modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "badge")
    val outerRot by t.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(40000, easing = androidx.compose.animation.core.LinearEasing)
        ), label = "outer"
    )
    val innerRot by t.animateFloat(
        0f, -360f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(20000, easing = androidx.compose.animation.core.LinearEasing)
        ), label = "inner"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            drawKey = "export_badge_seal",
            buildCmds = { w, h, _ ->
                val cx = w / 2f
                val cy = h / 2f
                val badgeR = minOf(w, h) * 0.30f
                val lobes = 12
                val steps = 90
                val xs = FloatArray(steps)
                val ys = FloatArray(steps)
                for (i in 0 until steps) {
                    val a = (i / steps.toFloat()) * 2f * Math.PI.toFloat()
                    val r = badgeR * (1f + 0.075f * kotlin.math.cos(lobes * a))
                    xs[i] = cx + r * kotlin.math.cos(a)
                    ys[i] = cy + r * kotlin.math.sin(a)
                }
                addFilledPolygon(xs, ys, steps, com.example.ui.theme.AmPanelElevated.toArgb())
                val blobR = badgeR * 0.40f
                addCircle(
                    cx, cy, blobR,
                    fillArgb = com.example.ui.theme.AmAccent.toArgb(),
                    strokeWidth = badgeR * 0.03f,
                    strokeArgb = com.example.ui.theme.AmHairlineStrong.toArgb()
                )
            },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val badgeR = minOf(size.width, size.height) * 0.30f
            val outerR = minOf(size.width * 0.46f, size.height * 0.5f)
            drawRingText(
                text = "TIMELINE PRO • PROFESSIONAL VIDEO EDITOR • ",
                cx = cx, cy = cy, radius = outerR, textSizePx = outerR * 0.26f,
                color = com.example.ui.theme.AmTextFaint.copy(alpha = 0.55f), bold = true, rotationDeg = outerRot
            )
            drawRingText(
                text = "PROFESSIONAL VIDEO EDITOR • TIMELINE PRO • ",
                cx = cx, cy = cy, radius = badgeR * 0.80f, textSizePx = badgeR * 0.115f,
                color = com.example.ui.theme.AmTextDim, bold = true, rotationDeg = innerRot
            )
        }
        Text(
            text = "TP", color = AmInk, fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** Draws [text] repeated around a circle so it fills the whole circumference exactly. */
private fun DrawScope.drawRingText(
    text: String, cx: Float, cy: Float, radius: Float, textSizePx: Float,
    color: Color, bold: Boolean, rotationDeg: Float
) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = textSizePx
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val circumference = (2.0 * Math.PI * radius).toFloat()
    val unit = paint.measureText(text)
    val repeats = (circumference / unit).toInt().coerceAtLeast(1)
    val full = text.repeat(repeats)
    val natural = paint.measureText(full)
    paint.letterSpacing = ((circumference - natural) / full.length / textSizePx)
    val path = android.graphics.Path().apply {
        addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
    }
    drawIntoCanvas { c ->
        c.nativeCanvas.save()
        c.nativeCanvas.rotate(rotationDeg, cx, cy)
        c.nativeCanvas.drawTextOnPath(full, path, 0f, 0f, paint)
        c.nativeCanvas.restore()
    }
}

/**
 * Add Audio (build 203): where the new audio layer's sound comes from —
 * an audio file (the flow that existed: the system document picker), or
 * "Extract from video" (Sarowar's order 2026-11-15 ~10:42: pick a video and
 * its sound becomes the audio layer — see [com.example.model.ExtractAudio]).
 * Two plain full-width list rows in the standard bottom-sheet language; the
 * rows carry no fixed-width clusters, so nothing can overflow at 360 dp
 * (content width 360 − 2×20 sheet padding − 2×12 row padding = 296 dp for
 * icon + title/subtitle, which ellipsize as single lines).
 *
 * This composable REPLACES a dead namesake that listed four canned music
 * tracks ("Cinematic Ambient Beat" …) and was never called from anywhere —
 * the fake entries are gone, not carried over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAudioSheet(
    onAudioFile: () -> Unit,
    onExtractFromVideo: () -> Unit,
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
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 18.dp)
        ) {
            // Build 253: the title draws in GL.
            GlLabelLine("Add Audio", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
            Spacer(modifier = Modifier.height(10.dp))

            AddAudioRow(
                icon = Icons.Default.MusicNote,
                title = "Audio file",
                subtitle = "Pick a music or sound file",
                tag = "add_audio_file"
            ) {
                onAudioFile()
                onDismiss()
            }
            Spacer(modifier = Modifier.height(6.dp))
            AddAudioRow(
                icon = Icons.Default.Movie,
                title = "Extract from video",
                subtitle = "Use a video's sound as the audio layer",
                tag = "add_audio_extract_video"
            ) {
                onExtractFromVideo()
                onDismiss()
            }
        }
    }
}

/**
 * Build 253 (stage 6): one Add Audio row on a GL surface
 * (SheetGeometry.ListRow): card fill, the icon rasterized into the
 * atlas, the title/subtitle stack — one press target over it.
 */
@Composable
private fun AddAudioRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    val g = SheetGeometry.ListRow
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val painter = rememberVectorPainter(icon)
    val titleH = atlas.textHeightPx(UiLabelStyle.SHEET_ITEM_SEMI)
    val subH = atlas.textHeightPx(UiLabelStyle.SHEET_DESC)
    val rowH = g.twoLineHeightPx(titleH, subH, density)

    fun resolveRow(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val t = atlas.resolve(title, UiLabelStyle.SHEET_ITEM_SEMI)
        val s = atlas.resolve(subtitle, UiLabelStyle.SHEET_DESC)
        val iconPx = (19f * density).roundToInt()
        val ic = atlas.iconEntry("audio:$title", painter, iconPx, density)
        return Triple(t, s, ic)
    }
    var rowEntries = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        rowEntries = resolveRow()
    }
    val (titleE, subE, iconE) = rowEntries
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
            drawKey = listOf(titleE, subE, iconE, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, g.CORNER_DP * dd, AmCardSurface.toArgb())
                setScaleAbout(w / 2f, h / 2f, scale.value)
                iconE?.let { e ->
                    addText(
                        e.index,
                        12f * dd - pad, (h - e.rectH) / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                val stackTop = g.stackTopPx(h, titleH + subH)
                titleE?.let { e ->
                    val style = UiLabelStyle.SHEET_ITEM_SEMI
                    val fm = atlas.fontMetrics(style)
                    val baseline = PanelGeometry.lineBaselinePx(stackTop, titleH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(41f * dd, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                subE?.let { e ->
                    val style = UiLabelStyle.SHEET_DESC
                    val fm = atlas.fontMetrics(style)
                    val baseline = PanelGeometry.lineBaselinePx(stackTop + titleH, subH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(41f * dd, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextSecondary.toArgb()
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            tag = tag,
            description = title
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextSheet(
    onAddText: (text: String, color: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("আমার নতুন ভিডিও") }
    var selectedColor by remember { mutableStateOf(0xFFFFFFFF) }

    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 18.dp)
        ) {
            // Build 253: title + preset label draw in GL; the text
            // field itself stays Compose (text input never migrates).
            GlLabelLine("Add Text Layer", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
            Spacer(modifier = Modifier.height(8.dp))

            PillTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = "Title / Caption"
            )

            Spacer(modifier = Modifier.height(10.dp))

            GlLabelLine("Color Preset", UiLabelStyle.TEXT_11, TextSecondary.toArgb())
            Spacer(modifier = Modifier.height(6.dp))
            // The five preset discs on one GL surface; tap boxes over
            // each (the selected disc's white ring is drawn, not bordered).
            val presetColors = listOf(0xFFFFFFFF, 0xFF00F0FF, 0xFFFFEB3B, 0xFFFF4081, 0xFF00E676)
            val presetScales = remember { presetColors.map { Animatable(1f) } }
            val presetScaleValues = presetScales.map { it.value }
            Box(
                Modifier
                    .width((28 * presetColors.size + 8 * (presetColors.size - 1)).dp)
                    .height(28.dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    drawKey = listOf(presetScaleValues, selectedColor),
                    buildCmds = { _, _, dd ->
                        presetColors.forEachIndexed { i, colorValue ->
                            val cx = (i * 36 + 14) * dd
                            val cy = 14f * dd
                            // No press scale on the disc itself: in
                            // Compose its background/border sat outside
                            // bouncyClickable's graphicsLayer.
                            addCircle(cx, cy, 14f * dd, Color(colorValue).toArgb())
                            val bw = if (selectedColor == colorValue) 2f else 1f
                            addCircle(
                                cx, cy, 14f * dd - bw * dd / 2f,
                                0, bw * dd,
                                if (selectedColor == colorValue) 0xFFFFFFFF.toInt() else 0
                            )
                        }
                    }
                )
                presetColors.forEachIndexed { i, colorValue ->
                    ChromePressBox(
                        presetScales[i], 0.85f, { selectedColor = colorValue },
                        Modifier
                            .offset(x = (i * 36).dp)
                            .size(28.dp),
                        description = "Color preset"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Build 253: the violet pill draws in GL (GlVioletPill).
            GlVioletPill(
                label = "Add to Timeline",
                onClick = {
                    if (textInput.isNotBlank()) {
                        onAddText(textInput, selectedColor)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShapeSheet(
    onAddShape: (name: String, pattern: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Build 256: the picker is the 100-kind ShapeCatalog, filtered by category
    // chips (null = All). The build-253 7-kind picker is removed entirely.
    var category by remember { mutableStateOf<ShapeCatalog.Category?>(null) }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 14.dp)
        ) {
            // Build 253 (stage 6): the header (title, close disc,
            // hairline) draws on one GL surface; the close tap box
            // sits over its disc.
            val closePainter = rememberVectorPainter(Icons.Default.Close)
            val density = LocalDensity.current.density
            val atlas = rememberUiAtlas()
            val pad = UiLabelAtlas.PAD.toFloat()
            val titleH = atlas.textHeightPx(UiLabelStyle.SHEET_TITLE_SEMI)
            fun resolveHeader(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
                val t = atlas.resolve("Shapes", UiLabelStyle.SHEET_TITLE_SEMI)
                val closePx = (16f * density).roundToInt()
                val c = atlas.iconEntry("shape:close", closePainter, closePx, density)
                return t to c
            }
            var headerEntries = resolveHeader()
            if (atlas.overflowed) {
                atlas.reset()
                headerEntries = resolveHeader()
            }
            val (shapeTitleE, closeE) = headerEntries
            val closeScale = remember { Animatable(1f) }
            val headerH = maxOf(titleH / density, 28f) + 8f

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerH.dp + 1.dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = atlas,
                    drawKey = listOf(shapeTitleE, closeE, closeScale.value, atlas.generation),
                    buildCmds = { w, h, dd ->
                        val rowH = h - 1f * dd
                        shapeTitleE?.let { e ->
                            val style = UiLabelStyle.SHEET_TITLE_SEMI
                            val fm = atlas.fontMetrics(style)
                            val top = EditorGeometry.centredTextTopPx(0f, rowH, titleH)
                            val baseline = PanelGeometry.lineBaselinePx(top, titleH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(0f, baseline, e.bearing, e.base, pad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                        val discCx = w - 14f * dd
                        val discCy = rowH / 2f
                        addCircle(discCx, discCy, 14f * dd, AmPanelHigh.toArgb())
                        setScaleAbout(discCx, discCy, closeScale.value)
                        closeE?.let { e ->
                            addText(
                                e.index,
                                discCx - e.rectW / 2f, discCy - e.rectH / 2f,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextSecondary.toArgb()
                            )
                        }
                        clearTransform()
                        addRect(0f, rowH, w, 1f * dd, AmHairline.toArgb())
                    }
                )
                ChromePressBox(
                    closeScale, 0.88f, { onDismiss() },
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = ((headerH - 28f) / 2f).dp)
                        .size(28.dp),
                    description = "Close"
                )
            }
            Spacer(Modifier.height(10.dp))

            // Build 256: category chips — All + the catalog's eight categories.
            GlPillStrip(
                items = buildList {
                    add(GlPillItem(label = "All", selected = category == null, onClick = { category = null }))
                    for (c in ShapeCatalog.categories) {
                        add(GlPillItem(label = c.label, selected = category == c, onClick = { category = c }))
                    }
                },
                labelStyle = { sel -> if (sel) UiLabelStyle.ED_TEXT_BOLD else UiLabelStyle.ED_TEXT_MED },
                textArgb = { sel -> if (sel) AmInk.toArgb() else TextPrimary.toArgb() },
                fillArgb = { sel -> if (sel) AmAccent.toArgb() else AmPanelElevated.toArgb() },
                borderArgb = { sel -> if (sel) AmAccent.toArgb() else AmHairlineStrong.toArgb() },
                padHDp = 12f,
                padVDp = 6f,
                gapDp = 6f,
                minHDp = 32f,
                scrollable = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // The grid: 4 columns on the SheetGeometry.ShapeGrid law, bounded
            // height, every card drawn from its shape's own catalog points.
            LazyVerticalGrid(
                columns = GridCells.Fixed(SheetGeometry.ShapeGrid.COLUMNS),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 392.dp),
                horizontalArrangement = Arrangement.spacedBy(SheetGeometry.ShapeGrid.GAP_DP.dp),
                verticalArrangement = Arrangement.spacedBy(SheetGeometry.ShapeGrid.GAP_DP.dp)
            ) {
                items(ShapeCatalog.inCategory(category), key = { it.pattern }) { def ->
                    ShapeGridCard(
                        def = def,
                        onClick = { onAddShape(def.name, def.pattern); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Build 253: the footer note draws in GL.
            GlLabelLine(
                text = "Tap a shape to add it at the playhead",
                style = UiLabelStyle.TEXT_10,
                colorArgb = TextTertiary.toArgb(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                align = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Build 256: one catalog shape card on a single GL surface — tile fill +
 * hairline, the glyph drawn from the shape's OWN [ShapeCatalog] points
 * (the same points the canvas renders; a hole subpath is punched with
 * the tile colour), and the name wrapped by the greedy breaker to at
 * most two full-word lines under the tile. Card metrics are the
 * [SheetGeometry.ShapeGrid] law; the whole draw press-scales.
 * Build 257: tile + glyph emit as ONE flat-batch polygon list
 * ([SheetGeometry.ShapeGrid.cardPolygons]) — the renderer's fixed
 * batch order (flat before SDF) had the SDF tile fill painting over
 * the glyphs on device.
 */
@Composable
private fun ShapeGridCard(
    def: ShapeCatalog.Def,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = SheetGeometry.ShapeGrid
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val labelStyle = UiLabelStyle.TEXT_95_MED
    val labelH = atlas.textHeightPx(labelStyle)
    val scale = remember { Animatable(1f) }

    BoxWithConstraints(modifier = modifier) {
        // In the grid the cell IS the tile: card width == tile edge.
        val tilePx = maxWidth.value * density
        fun resolveLines(): List<UiLabelAtlas.Entry?> =
            PanelGeometry.wrapGreedy(def.name, tilePx, g.LABEL_LINES) { atlas.measure(it, labelStyle) }
                .map { atlas.resolve(it, labelStyle) }
        var lineEntries = resolveLines()
        if (atlas.overflowed) {
            atlas.reset()
            lineEntries = resolveLines()
        }
        val lines = lineEntries
        val cardH = g.cardHeightPx(tilePx, labelH, g.LABEL_LINES, density)
        Box(
            Modifier
                .width(maxWidth)
                .height((cardH / density).dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(lines, scale.value, def.pattern, atlas.generation),
                buildCmds = { w, h, dd ->
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    // Tile + glyph: ONE flat-batch emission in the
                    // ShapeGrid order (tile outline first, then the
                    // shape's own subpaths). The renderer flushes batches
                    // in a fixed order — flat triangles BEFORE SDF — so
                    // the build-256 SDF round-rect tile fill painted over
                    // the flat glyph polygons and the glyphs were
                    // invisible on device (build 257 fix).
                    val glyphArgb = Color(0xFFEDEDED).toArgb()
                    val tileArgb = AmPanelHigh.toArgb()
                    for (poly in g.cardPolygons(def, w, g.TILE_CORNER_DP * dd, glyphArgb, tileArgb)) {
                        addFilledPolygon(poly.xs, poly.ys, poly.xs.size, poly.argb)
                    }
                    // The inside hairline: an SDF stroke with a
                    // transparent fill — it flushes after the flat batch
                    // and frames the tile without covering the glyph.
                    addRoundRect(
                        w / 2f, w / 2f, w - 1f * dd, w - 1f * dd,
                        g.TILE_CORNER_DP * dd - 0.5f * dd,
                        0, 1f * dd, AmHairline.toArgb()
                    )
                    // The name: up to two centred lines under the tile.
                    val fm = atlas.fontMetrics(labelStyle)
                    lines.forEachIndexed { i, entry ->
                        entry?.let { e ->
                            val labelTop = w + g.LABEL_GAP_DP * dd + i * labelH
                            val baseline = PanelGeometry.lineBaselinePx(labelTop, labelH, fm.ascent, fm.descent)
                            val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                            val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextSecondary.toArgb()
                            )
                        }
                    }
                    clearTransform()
                }
            )
            ChromePressBox(
                scale, 0.94f, onClick,
                Modifier.fillMaxSize(),
                description = def.name
            )
        }
    }
}

private fun DrawScope.drawRegularPolygon(color: Color, points: Int, innerRatio: Float) {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = size.minDimension / 2f
    val total = if (innerRatio < 1f) points * 2 else points
    for (i in 0 until total) {
        val angle = Math.toRadians(i * 360.0 / total - 90.0)
        val radius = if (innerRatio < 1f && i % 2 == 1) outer * innerRatio else outer
        val x = cx + radius * kotlin.math.cos(angle).toFloat()
        val y = cy + radius * kotlin.math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/**
 * Build 261 — while the fullscreen player is open, its dialog window IS
 * the screen: full-size layout and the system bars hidden (immersive;
 * a swipe brings them back briefly). Everything is restored when the
 * player closes, so the editor behind it is exactly as it was. Before
 * build 261 the player sat in a plain dialog window: the status /
 * navigation bars stayed visible, the window was sized and inset by
 * the dialog system, and the project frame was fitted into that
 * smaller, offset box instead of the display the user calls fullscreen.
 */
@Composable
private fun FullscreenWindowEffect() {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?: (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (window == null) return@DisposableEffect onDispose { }
        window.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun FullscreenPlayerModal(
    aspectRatio: AspectRatio,
    currentPlayheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    /** The level being edited — the same tracks the inline canvas receives (its touch / overlay layer). */
    tracks: List<TimelineTrack>,
    /** Every layer of the project — the C++ picture and the empty-project check, as on the inline canvas. */
    nativeTracks: List<TimelineTrack>,
    /** The C++ preview engine, shared with the inline canvas (it draws on the newest surface, back on dismiss). */
    nativePreview: com.example.engine.NativePreviewEngine?,
    /** Live playhead reader (the screen's playheadProvider): the canvas follows playback, not the opening frame. */
    playheadMs: () -> Long,
    onTogglePlay: () -> Unit,
    /** Build 261: the fullscreen scrub bar's seek (the screen's viewModel.seekTo). */
    onSeekTo: (Long) -> Unit = {},
    onDismiss: () -> Unit,
    /** Onion-skin ghosts (build 190): the same settings the inline canvas receives, so the shared engine's state does not flip while the player is open. */
    onionSkin: com.example.model.OnionSkinSettings = com.example.model.OnionSkinSettings(),
    onionFps: Int = 30,
    onionDurationMs: Long = 0L
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Build 261: decorFitsSystemWindows = false — the player's content
        // (and with it the frame fit) uses the REAL display, not the
        // dialog's inset-shrunk box; FullscreenWindowEffect hides the
        // system bars while the player is open and restores them on exit.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        DialogMotion180(fadeOnly = true)
        FullscreenWindowEffect()
        Box(
            modifier = Modifier
                // Build 261: NO enterPop here — its graphicsLayer (scale /
                // translate) wrapped the preview's SurfaceView, and a
                // SurfaceView presented through a transformed layer is a
                // documented distortion hazard (the surface does not
                // follow the layer transform; the window fade from
                // DialogMotion180 is the player's only enter motion).
                .fillMaxSize()
                .background(AmBaseDeep),
            contentAlignment = Alignment.Center
        ) {
            // Fullscreen Canvas Area: the SAME project state as the inline canvas (this call used to pass
            // tracks = emptyList() and no engine, so the fullscreen preview always rendered an empty
            // project: the "No layers yet" hint over black, in a fixed 360 dp band). No height is
            // passed, so the canvas fills the dialog and the project frame is fitted to the full screen.
            CanvasArea(
                aspectRatio = aspectRatio,
                playheadMs = playheadMs,
                isPlaying = isPlaying,
                tracks = tracks,
                nativeTracks = nativeTracks,
                nativePreview = nativePreview,
                onTogglePlayPause = onTogglePlay,
                onionSkin = onionSkin,
                onionFps = onionFps,
                onionDurationMs = onionDurationMs,
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar Overlay
            // Build 253 (stage 6): both pills draw on GL surfaces;
            // the exit pill keeps a transparent clickable overlay
            // (stock ripple + the testTag).
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // Build 261: the window is edge-to-edge now (decorFits
                    // off), so the pills keep clear of the cutout / any
                    // visible system-bar area via the safe-drawing insets.
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fsDensity = LocalDensity.current.density
                val fsAtlas = rememberUiAtlas()
                val fsPad = UiLabelAtlas.PAD.toFloat()
                val timecodeText = "${formatTimeCode(currentPlayheadMs)} / ${formatTimeCode(totalDurationMs)}"
                val tcStyle = UiLabelStyle.ED_TEXT_SEMI
                val tcH = fsAtlas.textHeightPx(tcStyle)
                fun resolveTc() = fsAtlas.resolve(timecodeText, tcStyle)
                var tcE = resolveTc()
                val exitStyle = UiLabelStyle.SMALL_BOLD
                val exitH = fsAtlas.textHeightPx(exitStyle)
                fun resolveExit() = fsAtlas.resolve("Exit Fullscreen", exitStyle)
                var exitE = resolveExit()
                if (fsAtlas.overflowed) {
                    fsAtlas.reset()
                    tcE = resolveTc()
                    exitE = resolveExit()
                }
                val tcEntry = tcE
                val exitEntry = exitE
                val tcSize = SheetGeometry.FullscreenPill.timecodeSizePx(tcEntry?.advance ?: 0f, tcH, fsDensity)
                Box(
                    Modifier
                        .width((tcSize.first / fsDensity).dp)
                        .height((tcSize.second / fsDensity).dp)
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = fsAtlas,
                        drawKey = listOf(tcEntry, fsAtlas.generation),
                        buildCmds = { w, h, _ ->
                            addRoundRect(w / 2f, h / 2f, w, h, h / 2f, 0x80000000.toInt())
                            tcEntry?.let { e ->
                                val fm = fsAtlas.fontMetrics(tcStyle)
                                val top = EditorGeometry.centredTextTopPx(0f, h, tcH)
                                val baseline = PanelGeometry.lineBaselinePx(top, tcH, fm.ascent, fm.descent)
                                val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                                val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, fsPad)
                                addText(
                                    e.index, dstX, dstY,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = 0xFFFFFFFF.toInt()
                                )
                            }
                        }
                    )
                }
                // Build 217 (the reference's fullscreen overlay): the
                // "Exit Fullscreen" pill — dark glass fill, white-at-15%
                // border, r10 — at the top right.
                val exitSize = SheetGeometry.FullscreenPill.exitSizePx(exitEntry?.advance ?: 0f, exitH, fsDensity)
                Box(
                    Modifier
                        .width((exitSize.first / fsDensity).dp)
                        .height((exitSize.second / fsDensity).dp)
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = fsAtlas,
                        drawKey = listOf(exitEntry, fsAtlas.generation),
                        buildCmds = { w, h, dd ->
                            addRoundRect(
                                w / 2f, h / 2f, w, h,
                                SheetGeometry.FullscreenPill.EXIT_CORNER_DP * dd,
                                0xD9202026.toInt()
                            )
                            addRoundRect(
                                w / 2f, h / 2f, w - 1f * dd, h - 1f * dd,
                                SheetGeometry.FullscreenPill.EXIT_CORNER_DP * dd - 0.5f * dd,
                                0, 1f * dd, 0x26FFFFFF
                            )
                            exitEntry?.let { e ->
                                val fm = fsAtlas.fontMetrics(exitStyle)
                                val top = EditorGeometry.centredTextTopPx(0f, h, exitH)
                                val baseline = PanelGeometry.lineBaselinePx(top, exitH, fm.ascent, fm.descent)
                                val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                                val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, fsPad)
                                addText(
                                    e.index, dstX, dstY,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = 0xFFFFFFFF.toInt()
                                )
                            }
                        }
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .testTag("fullscreen_exit_button")
                            .semantics { contentDescription = "Exit Fullscreen" }
                    )
                }
            }

            // Bottom overlay — build 261: a scrub bar (new: the player
            // could not seek at all before build 261 — the only controls
            // were this disc, a read-only timecode and the exit pill) over
            // the play disc, the whole block inset-safe (gesture bar /
            // cutout) now that the window is edge-to-edge.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FullscreenScrubBar(
                    currentPlayheadMs = currentPlayheadMs,
                    totalDurationMs = totalDurationMs,
                    onSeekTo = onSeekTo
                )
                Spacer(Modifier.height(12.dp))
            // Play disc — build 253: the disc + glyph draw in GL. The
            // build-252 IconButton had no press scale (ripple only); the
            // press box uses 1.0 so only the tap remains.
            Box(
                modifier = Modifier
                    .size(44.dp)
            ) {
                val playPainter = rememberVectorPainter(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                )
                val density = LocalDensity.current.density
                val atlas = rememberUiAtlas()
                val iconPx = (28f * density).roundToInt()
                fun resolvePlay() = atlas.iconEntry(
                    if (isPlaying) "fs:pause" else "fs:play", playPainter, iconPx, density
                )
                var playE = resolvePlay()
                if (atlas.overflowed) {
                    atlas.reset()
                    playE = resolvePlay()
                }
                val pe = playE
                val playScale = remember { Animatable(1f) }
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = atlas,
                    drawKey = listOf(pe, isPlaying, atlas.generation),
                    buildCmds = { w, h, _ ->
                        addCircle(w / 2f, h / 2f, w / 2f, 0x80000000.toInt())
                        pe?.let { e ->
                            addText(
                                e.index,
                                w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = 0xFFFFFFFF.toInt()
                            )
                        }
                    }
                )
                ChromePressBox(
                    playScale, 1f, onTogglePlay,
                    Modifier.fillMaxSize(),
                    description = "Play/Pause"
                )
            }
            }
        }
    }
}

/**
 * Build 261 — the fullscreen player's seek bar. Before build 261 the
 * player had NO way to seek: the only controls were the play disc, a
 * read-only timecode and the exit pill, so any scrub meant leaving
 * fullscreen. The bar follows the playhead while playing; a tap seeks
 * at once; a drag previews the position and seeks on release. The
 * position mapping is model/ScrubLaw (host-pinned by FrameFitTest).
 */
@Composable
private fun FullscreenScrubBar(
    currentPlayheadMs: Long,
    totalDurationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val shown = scrubbing ?: com.example.model.ScrubLaw.fractionAt(currentPlayheadMs, totalDurationMs)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    val f = if (size.width > 0) offset.x / size.width else 0f
                    onSeekTo(com.example.model.ScrubLaw.msAt(f, totalDurationMs))
                }
            }
            .pointerInput(totalDurationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        scrubbing = if (size.width > 0) (offset.x / size.width).coerceIn(0f, 1f) else 0f
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        scrubbing = if (size.width > 0) (change.position.x / size.width).coerceIn(0f, 1f) else 0f
                    },
                    onDragEnd = {
                        scrubbing?.let { onSeekTo(com.example.model.ScrubLaw.msAt(it, totalDurationMs)) }
                        scrubbing = null
                    },
                    onDragCancel = { scrubbing = null }
                )
            }
            .testTag("fullscreen_scrub_bar")
            .semantics { contentDescription = "Seek" },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x40FFFFFF), RoundedCornerShape(2.dp))
        )
        // Played part.
        if (shown > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height(4.dp)
                    .background(AmAccent, RoundedCornerShape(2.dp))
            )
        }
        // Thumb.
        val thumbX = (maxWidth * shown - 6.dp).coerceIn(0.dp, (maxWidth - 12.dp).coerceAtLeast(0.dp))
        Box(
            Modifier
                .offset(x = thumbX)
                .size(12.dp)
                .background(Color.White, CircleShape)
        )
    }
}
