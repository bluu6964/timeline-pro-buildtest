package com.example.ui.components


import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.PanelGeometry
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmSheetShape
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipEffectsSheet(
    currentEffect: String?,
    onEffectSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val effects = listOf(
        "None",
        "VHS Glitch",
        "Cyber Neon",
        "RGB Split",
        "Cinema 90s",
        "Lens Flare",
        "Film Grain",
        "Dream Glow"
    )

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
                .padding(bottom = 16.dp)
        ) {
            Text("Video Effects", color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(effects) { effect ->
                    val isSelected = effect == (currentEffect ?: "None")
                    Column(
                        modifier = Modifier
                            .width(60.dp)
                            .bouncyClickable(onClick = {
                                onEffectSelect(effect)
                                onDismiss()
                            }),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isSelected) AmPillSelected else AmCardSurface,
                                    RoundedCornerShape(13.dp)
                                )
                                .clip(RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Rounded tile (build 207): selected = filled pill surface.
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isSelected) Color.White else AmTextDim, modifier = Modifier.size(21.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(effect, color = if (isSelected) TextPrimary else AmTextDim, fontSize = 9.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipReplaceSheet(
    onMediaSelected: (drawableResId: Int, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val mediaOptions = listOf(
        Triple(R.drawable.sample_portrait_1791121452621, "Portrait Vlog", "1080p 60fps"),
        Triple(R.drawable.sample_nature_1791121466670, "Nature Canopy", "4K 30fps"),
        Triple(R.drawable.sample_cyan_dots_1791121437975, "Cyan Graphic", "Alpha Overlay")
    )

    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        // Build 251 (UI migration stage 4): the title, row surfaces and
        // texts draw on one GL surface (PanelGeometry.Sheets); the tap
        // targets stay Compose (ChromePressBox, 0.94). The photo
        // thumbnails stay Compose Images over the rows — the atlas blit
        // reads only the texture's alpha (ui_gl_renderer), so a colour
        // photo cannot pass through it; the stage-1 overlay precedent.
        val g = PanelGeometry.Sheets
        val density = LocalDensity.current.density
        val atlas = rememberUiAtlas()
        val pad = UiLabelAtlas.PAD.toFloat()
        val titleH = atlas.textHeightPx(UiLabelStyle.SHEET_TITLE)
        val titleAscent = atlas.fontMetrics(UiLabelStyle.SHEET_TITLE).ascent
        val nameH = atlas.textHeightPx(UiLabelStyle.SHEET_ITEM_SEMI)
        val nameAscent = atlas.fontMetrics(UiLabelStyle.SHEET_ITEM_SEMI).ascent
        val descH = atlas.textHeightPx(UiLabelStyle.SHEET_DESC)
        val descAscent = atlas.fontMetrics(UiLabelStyle.SHEET_DESC).ascent
        val rowH = g.replaceRowHeightPx(nameH, descH, density)
        val contentH = g.replaceContentHeightPx(titleH, rowH, mediaOptions.size, density)

        fun resolveAll(): ReplaceEntries {
            val title = atlas.resolve("Replace Media", UiLabelStyle.SHEET_TITLE)
            val names = mediaOptions.map { atlas.resolve(it.second, UiLabelStyle.SHEET_ITEM_SEMI) }
            val descs = mediaOptions.map { atlas.resolve(it.third, UiLabelStyle.SHEET_DESC) }
            return ReplaceEntries(title, names, descs)
        }
        var entries = resolveAll()
        if (atlas.overflowed) {
            atlas.reset()
            entries = resolveAll()
        }
        val scales = remember { mediaOptions.map { Animatable(1f) } }
        val scaleValues = scales.map { it.value }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height((contentH / density).dp)
        ) {
            val sheetW = maxWidth.value * density
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(entries, scaleValues, atlas.generation),
                buildCmds = { w, _, dd ->
                    entries.title?.let { e ->
                        addText(
                            e.index,
                            g.COL_PAD_H_DP * dd + e.bearing - pad,
                            g.COL_PAD_TOP_DP * dd - titleAscent - e.base,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = TextPrimary.toArgb()
                        )
                    }
                    mediaOptions.forEachIndexed { i, _ ->
                        val top = g.replaceRowTopPx(i, titleH, rowH, dd)
                        val rowW = w - 2f * g.COL_PAD_H_DP * dd
                        setScaleAbout(g.COL_PAD_H_DP * dd + rowW / 2f, top + rowH / 2f, scales[i].value)
                        addRoundRect(
                            g.COL_PAD_H_DP * dd + rowW / 2f, top + rowH / 2f,
                            rowW, rowH, g.ROW_CORNER_DP * dd, AmCardSurface.toArgb()
                        )
                        val stackTop = top + (rowH - (nameH + descH)) / 2f
                        entries.names[i]?.let { e ->
                            addText(
                                e.index,
                                g.replaceTextLeftPx(dd) + e.bearing - pad,
                                stackTop - nameAscent - e.base,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                        entries.descs[i]?.let { e ->
                            addText(
                                e.index,
                                g.replaceTextLeftPx(dd) + e.bearing - pad,
                                stackTop + nameH - descAscent - e.base,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextSecondary.toArgb()
                            )
                        }
                        clearTransform()
                    }
                }
            )
            // The photo thumbnails (Compose overlays, see above).
            mediaOptions.forEachIndexed { i, (res, _, _) ->
                val top = g.replaceRowTopPx(i, titleH, rowH, density)
                val imgTop = top + (rowH - g.REPLACE_IMG_DP * density) / 2f
                Image(
                    painter = painterResource(id = res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(
                            x = (g.replaceImageLeftPx(density) / density).dp,
                            y = (imgTop / density).dp
                        )
                        .size(g.REPLACE_IMG_DP.dp)
                        .clip(RoundedCornerShape(g.REPLACE_IMG_CORNER_DP.dp))
                )
            }
            // Tap targets at the rows' rects, invisible.
            mediaOptions.forEachIndexed { i, (res, name, _) ->
                val top = g.replaceRowTopPx(i, titleH, rowH, density)
                ChromePressBox(
                    scales[i], 0.94f,
                    {
                        onMediaSelected(res, name)
                        onDismiss()
                    },
                    Modifier
                        .offset(x = g.COL_PAD_H_DP.dp, y = (top / density).dp)
                        .width(((sheetW - 2f * g.COL_PAD_H_DP * density) / density).dp)
                        .height((rowH / density).dp)
                )
            }
        }
    }
}

private data class ReplaceEntries(
    val title: UiLabelAtlas.Entry?,
    val names: List<UiLabelAtlas.Entry?>,
    val descs: List<UiLabelAtlas.Entry?>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipBlendSheet(
    currentBlend: String,
    onBlendSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val blendModes = listOf("Normal", "Screen", "Multiply", "Overlay", "Lighten", "Darken", "Color Dodge")

    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        // Build 251 (UI migration stage 4): the title, rows, mode texts
        // and the selected check draw on one GL surface
        // (PanelGeometry.Sheets); the tap targets stay Compose
        // (ChromePressBox, 0.94 — the rows' own bouncyClickable scale).
        val g = PanelGeometry.Sheets
        val density = LocalDensity.current.density
        val atlas = rememberUiAtlas()
        val pad = UiLabelAtlas.PAD.toFloat()
        val checkPainter = rememberVectorPainter(Icons.Default.Check)
        val titleH = atlas.textHeightPx(UiLabelStyle.SHEET_TITLE)
        val titleAscent = atlas.fontMetrics(UiLabelStyle.SHEET_TITLE).ascent
        val itemH = atlas.textHeightPx(UiLabelStyle.SHEET_ITEM)
        val itemAscent = atlas.fontMetrics(UiLabelStyle.SHEET_ITEM).ascent
        val rowH = g.blendRowHeightPx(itemH, density)
        val contentH = g.blendContentHeightPx(titleH, rowH, blendModes.size, density)

        fun resolveAll(): BlendEntries {
            val title = atlas.resolve("Blending Mode", UiLabelStyle.SHEET_TITLE)
            val modes = blendModes.map { mode ->
                atlas.resolve(
                    mode,
                    if (mode == currentBlend) UiLabelStyle.SHEET_ITEM_BOLD else UiLabelStyle.SHEET_ITEM
                )
            }
            val checkPx = (g.BLEND_CHECK_DP * density).roundToInt()
            val check = atlas.resolveIcon("sheet:check", checkPx, checkPx) { cv, x, y ->
                val b = rasterizePainterBitmap(checkPainter, checkPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
            return BlendEntries(title, modes, check)
        }
        var entries = resolveAll()
        if (atlas.overflowed) {
            atlas.reset()
            entries = resolveAll()
        }
        val scales = remember { blendModes.map { Animatable(1f) } }
        val scaleValues = scales.map { it.value }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height((contentH / density).dp)
        ) {
            val sheetW = maxWidth.value * density
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(entries, scaleValues, atlas.generation),
                buildCmds = { w, _, dd ->
                    entries.title?.let { e ->
                        addText(
                            e.index,
                            g.COL_PAD_H_DP * dd + e.bearing - pad,
                            g.COL_PAD_TOP_DP * dd - titleAscent - e.base,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = TextPrimary.toArgb()
                        )
                    }
                    blendModes.forEachIndexed { i, mode ->
                        val selected = mode == currentBlend
                        val top = g.blendRowTopPx(i, titleH, rowH, dd)
                        val rowW = w - 2f * g.COL_PAD_H_DP * dd
                        setScaleAbout(g.COL_PAD_H_DP * dd + rowW / 2f, top + rowH / 2f, scales[i].value)
                        addRoundRect(
                            g.COL_PAD_H_DP * dd + rowW / 2f, top + rowH / 2f,
                            rowW, rowH, g.ROW_CORNER_DP * dd,
                            (if (selected) AmPillSelected else AmCardSurface).toArgb()
                        )
                        entries.modes[i]?.let { e ->
                            addText(
                                e.index,
                                g.rowTextLeftPx(g.BLEND_ROW_PAD_H_DP, dd) + e.bearing - pad,
                                top + (rowH - itemH) / 2f - itemAscent - e.base,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                        if (selected) {
                            entries.check?.let { e ->
                                addText(
                                    e.index,
                                    g.blendCheckLeftPx(w, dd) - pad,
                                    top + (rowH - g.BLEND_CHECK_DP * dd) / 2f - pad,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = NeonCyan.toArgb()
                                )
                            }
                        }
                        clearTransform()
                    }
                }
            )
            // Tap targets at the rows' rects, invisible.
            blendModes.forEachIndexed { i, mode ->
                val top = g.blendRowTopPx(i, titleH, rowH, density)
                ChromePressBox(
                    scales[i], 0.94f,
                    {
                        onBlendSelected(mode)
                        onDismiss()
                    },
                    Modifier
                        .offset(x = g.COL_PAD_H_DP.dp, y = (top / density).dp)
                        .width(((sheetW - 2f * g.COL_PAD_H_DP * density) / density).dp)
                        .height((rowH / density).dp)
                )
            }
        }
    }
}

private data class BlendEntries(
    val title: UiLabelAtlas.Entry?,
    val modes: List<UiLabelAtlas.Entry?>,
    val check: UiLabelAtlas.Entry?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipCropSheet(
    onCropRatioSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cropRatios = listOf("Free", "1:1", "16:9", "9:16", "4:3", "21:9")

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
                .padding(bottom = 16.dp)
        ) {
            Text("Crop Ratio", color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cropRatios) { ratio ->
                    Box(
                        modifier = Modifier
                            .background(AmPanelHigh, CircleShape)
                            .clip(CircleShape)
                            .bouncyClickable(onClick = {
                                onCropRatioSelected(ratio)
                                onDismiss()
                            })
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ratio, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
