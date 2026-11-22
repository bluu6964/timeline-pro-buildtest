package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.engine.UiGlRuntime
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.AnimProp
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.BlendKind
import com.example.model.BlendModes
import com.example.model.BlendSpec
import com.example.model.Easing
import com.example.model.TimelineClip
import com.example.model.indexOnTime
import com.example.model.segmentIndexAt
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmCornerPanel
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmPink
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextPrimary
import com.example.ui.util.BlendThumbs
import com.example.ui.util.bouncyClickable
import kotlin.math.roundToInt

// Build 221 (Sarowar's HTML reference v5, ai_studio_code__48.html) gave this
// panel its own palette; build 223 (Sarowar's colour-balance order) moved it
// onto the app's unified tokens — layout and shapes unchanged, the panel's
// green is now the one lime accent.
private val BlendGreen = AmAccent
private val BlendSurface = AmPanel
private val BlendIconTint = AmTextPrimary
private val BlendMuted = AmTextDim
private val BlendTrack = AmPanelElevated
private val BlendInk = AmInk
private val BlendCheckInk = AmInk

/**
 * "Blending & Opacity" editor of a layer (build 221, Sarowar's HTML reference v5;
 * build 258, Sarowar's Blending fixes): the OPACITY block (0 — value — 100 header
 * + the slider flanked by the keyframe and easing-graph buttons) sits at the TOP
 * of the panel, always visible; the category chips are pinned under it; the mode
 * area below is the active section's carousel (thumbnails with REAL blend
 * results, see [BlendThumbs]; the chosen mode's thumbnail turns circular, dims
 * and gains a green check badge) or the see-all GRID. The four MATTE modes are
 * not ordinary blends — they key off the layer below — so they live in their
 * own pink-outlined "Matte" section, never interleaved with the standard modes
 * ([PanelGeometry.BlendPanel] is the layout law). Mode names are NEVER clipped:
 * they wrap to two full lines under the thumbnail.
 * Every change is applied live to the layer, so the canvas above shows it straight away.
 *
 * Inventory note (build 221): the old sheet's in-panel header row (back + value text +
 * diamond + easing button) and its reset row are gone BY DESIGN of the reference —
 * back lives in the overlay's timescale row (and swipe-down / Back), the value in the
 * slider header, the mode name under the selected thumbnail, the diamond and the graph
 * are the slider-row end buttons, and Reset blending lives in the overlay header bar
 * (non-pager state, same keyframe-dropping operation the old sheet reset ran) and in
 * the selected header's More menu (pager state, build 219).
 */
@Composable
fun BlendEditSheet(
    /** The layer as it looks at the playhead (keyframes applied). */
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    modifier: Modifier = Modifier,
    /** The stored layer (with its keyframes), used for the diamond and the easing curve. */
    rawClip: TimelineClip = clip,
    currentPlayheadMs: Long = 0L,
    /** Diamond: add / remove an opacity keyframe at the playhead. */
    onToggleKeyframes: (List<AnimProp>) -> Unit = {},
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> }
) {
    val opacityProps = remember { listOf(AnimProp.OPACITY) }
    val relMs = currentPlayheadMs - rawClip.startMs
    val insideClip = currentPlayheadMs >= rawClip.startMs && currentPlayheadMs <= rawClip.endMs
    val opKeys = rawClip.keyframes[AnimProp.OPACITY].orEmpty()
    val onKeyframeNow = opKeys.indexOnTime(relMs) >= 0
    val hasKeyframes = opKeys.isNotEmpty()
    val segIdx = opKeys.segmentIndexAt(relMs)
    val easingEnabled = segIdx >= 0
    var showEasing by remember { mutableStateOf(false) }
    LaunchedEffect(easingEnabled) { if (!easingEnabled) showEasing = false }
    val segStart = if (segIdx >= 0) opKeys[segIdx] else null
    val segEnd = if (segIdx >= 0) opKeys[segIdx + 1] else null
    val easingOpen = showEasing && easingEnabled && segStart != null && segEnd != null

    val current = BlendModes.find(clip.blendMode)
    // Build 258: display sections. The native table files "Normal" under the
    // "Matte" category, but Normal is a STANDARD mode (kind NORMAL) — the
    // Matte section holds exactly the four kind-MATTE modes.
    val bp = PanelGeometry.BlendPanel
    val matteModes = remember { BlendModes.all.filter { it.kind == BlendKind.MATTE } }
    val standardModes = remember { BlendModes.all.filter { it.kind != BlendKind.MATTE } }
    val standardCategories = remember {
        BlendModes.categories.filter { it != PanelGeometry.BlendPanel.MATTE }
    }
    fun sectionOf(spec: BlendSpec): String = when {
        spec.kind == BlendKind.MATTE -> PanelGeometry.BlendPanel.MATTE
        spec.isNormal -> PanelGeometry.BlendPanel.ALL
        else -> spec.category
    }
    fun sectionModes(section: String): List<BlendSpec> = when (section) {
        PanelGeometry.BlendPanel.ALL -> standardModes
        PanelGeometry.BlendPanel.MATTE -> matteModes
        else -> standardModes.filter { it.category == section }
    }
    var section by remember(clip.id) { mutableStateOf(sectionOf(current)) }
    var gridView by remember(clip.id) { mutableStateOf(false) }
    // An outside change of the mode (e.g. Reset blending) pulls the panel to the
    // mode's own section; picking a mode or browsing sections never triggers
    // this, because the picked mode always sits inside the browsed section.
    LaunchedEffect(current.name) {
        if (sectionModes(section).none { it.name == current.name }) {
            section = sectionOf(current)
        }
    }
    // The see-all grid's sections: Normal alone under "Standard", then the
    // standard categories, then the separated Matte family last.
    val gridSections: List<Pair<String, List<BlendSpec>>> = remember {
        listOf("Standard" to standardModes.filter { it.isNormal }) +
            standardCategories.map { c ->
                c to standardModes.filter { it.category == c && !it.isNormal }
            } +
            listOf(PanelGeometry.BlendPanel.MATTE to matteModes)
    }
    val gridEntries: List<BlendGridEntry> = remember {
        gridSections.flatMap { (title, modes) ->
            listOf<BlendGridEntry>(
                BlendGridEntry.Header(title, title == PanelGeometry.BlendPanel.MATTE)
            ) + modes.map { BlendGridEntry.Mode(it) }
        }
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(section, gridView) {
        if (gridView) {
            val target = if (section == PanelGeometry.BlendPanel.ALL) 0 else
                PanelGeometry.BlendPanel.sectionStartIndex(
                    section, gridSections.map { it.first }, gridSections.map { it.second.size }
                )
            if (target >= 0) gridState.scrollToItem(target)
        }
    }

    // Build 252 (UI migration stage 5): the panel's flat AmBase body
    // is drawn in GL behind the column (the clip shape is unchanged);
    // the column keeps the touch-swallowing clickable and the tag.
    Box(modifier = modifier.fillMaxWidth()) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(AmCornerPanel)),
            clearArgb = 0,
            drawKey = Unit,
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, AmCornerPanel.value * dd, AmBase.toArgb())
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AmCornerPanel))
                // swallow touches so nothing underneath can be hit
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(top = 6.dp, bottom = 4.dp)
                .testTag("blend_edit_sheet")
        ) {
        // ---- Slider header: 0 — value — 100 ---------------------------------------
        // Build 258: the opacity block sits at the TOP of the panel and is
        // always visible — it no longer trades places with the easing
        // editor or sits under the mode carousel.
            // Build 252 (stage 5): the three runs are drawn in GL
            // (the value in the atlas's tabular-figures style); the
            // value's testTag rides an invisible box over its old
            // weight-1 span.
            val headerAtlas = rememberUiAtlas()
            val headerPad = UiLabelAtlas.PAD.toFloat()
            val headerDensity = androidx.compose.ui.platform.LocalDensity.current.density
            fun resolveHeader(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
                val zero = headerAtlas.resolve("0", UiLabelStyle.SHEET_DESC)
                val hundred = headerAtlas.resolve("100", UiLabelStyle.SHEET_DESC)
                val value = headerAtlas.resolve(
                    "${clip.opacity.roundToInt()}", UiLabelStyle.BLEND_VALUE
                )
                return Triple(zero, value, hundred)
            }
            var headerEntries = resolveHeader()
            if (headerAtlas.overflowed) {
                headerAtlas.reset()
                headerEntries = resolveHeader()
            }
            val (zeroEntry, valueEntry, hundredEntry) = headerEntries
            val headerH = headerAtlas.textHeightPx(UiLabelStyle.BLEND_VALUE)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height((headerH / headerDensity).dp)
            ) {
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = headerAtlas,
                    drawKey = listOf(zeroEntry, valueEntry, hundredEntry, headerAtlas.generation),
                    buildCmds = { w, h, _ ->
                        fun blit(e: UiLabelAtlas.Entry?, style: UiLabelStyle, originX: Float, argb: Int) {
                            if (e == null) return
                            val textH = headerAtlas.textHeightPx(style)
                            val fm = headerAtlas.fontMetrics(style)
                            val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                            val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, headerPad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = argb
                            )
                        }
                        blit(zeroEntry, UiLabelStyle.SHEET_DESC, 0f, BlendMuted.toArgb())
                        if (valueEntry != null) {
                            blit(
                                valueEntry, UiLabelStyle.BLEND_VALUE,
                                EditorGeometry.centredOriginPx(w / 2f, valueEntry.advance),
                                BlendGreen.toArgb()
                            )
                        }
                        if (hundredEntry != null) {
                            blit(
                                hundredEntry, UiLabelStyle.SHEET_DESC,
                                w - hundredEntry.advance, BlendMuted.toArgb()
                            )
                        }
                    }
                )
                // The value text's old weight-1 span, for its tag.
                val midLeftDp = ((zeroEntry?.advance ?: 0f) / headerDensity).dp
                val midWidthDp = maxWidth - midLeftDp -
                    ((hundredEntry?.advance ?: 0f) / headerDensity).dp
                Box(
                    modifier = Modifier
                        .offset(x = midLeftDp)
                        .width(midWidthDp)
                        .fillMaxHeight()
                        .testTag("blend_opacity_value")
                )
            }

            Spacer(Modifier.height(2.dp))

        // ---- Slider row: keyframe | opacity slider | graph ----------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyframeButton221(
                enabled = insideClip,
                hasKeyframes = hasKeyframes,
                onKeyframeNow = onKeyframeNow,
                onClick = { onToggleKeyframes(opacityProps) }
            )
            Spacer(Modifier.width(8.dp))
            BlendOpacitySlider(
                value = clip.opacity,
                onValueChange = { v -> onUpdate { it.copy(opacity = v) } },
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .testTag("blend_opacity_slider")
            )
            Spacer(Modifier.width(8.dp))
            GraphButton221(
                enabled = easingEnabled,
                open = easingOpen,
                easing = if (easingEnabled) segStart?.easing else null,
                onClick = { if (easingEnabled) showEasing = !showEasing }
            )
        }

        Spacer(Modifier.height(4.dp))

        // ---- Category chips + Grid/Carousel toggle (pinned under the opacity block) --
        // Build 258: the chips moved from the panel's bottom to directly
        // under the opacity slider, so section navigation is always in
        // reach. Labels: All + the table's categories with Matte last
        // ([PanelGeometry.BlendPanel.chipLabels]). Picking "All" also
        // switches to the grid — the see-all view.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlPillStrip(
                items = bp.chipLabels(BlendModes.categories).map { name ->
                    GlPillItem(
                        label = name,
                        selected = name == section,
                        tag = "blend_tab_$name",
                        onClick = {
                            section = name
                            if (name == bp.ALL) gridView = true
                        }
                    )
                },
                labelStyle = { on -> if (on) UiLabelStyle.ED_TEXT_SEMI else UiLabelStyle.ED_TEXT_MED },
                textArgb = { on -> (if (on) BlendInk else BlendIconTint).toArgb() },
                fillArgb = { on -> (if (on) BlendGreen else BlendSurface).toArgb() },
                borderArgb = { null },
                padHDp = 12f,
                padVDp = 6f,
                gapDp = 6f,
                modifier = Modifier.weight(1f),
                pressScale = 0.95f,
                scrollable = true,
                contentPadHDp = 12f
            )
            Spacer(Modifier.width(6.dp))
            GlPillStrip(
                items = listOf(
                    GlPillItem(
                        label = if (gridView) "Carousel" else "Grid",
                        selected = gridView,
                        tag = "blend_view_toggle",
                        onClick = { gridView = !gridView }
                    )
                ),
                labelStyle = { on -> if (on) UiLabelStyle.ED_TEXT_SEMI else UiLabelStyle.ED_TEXT_MED },
                textArgb = { on -> (if (on) BlendInk else BlendIconTint).toArgb() },
                fillArgb = { on -> (if (on) BlendGreen else BlendSurface).toArgb() },
                borderArgb = { null },
                padHDp = 12f,
                padVDp = 6f,
                gapDp = 6f,
                modifier = Modifier.padding(end = 12.dp),
                pressScale = 0.95f,
                scrollable = false
            )
        }

        Spacer(Modifier.height(6.dp))

        // ---- Mode area: easing editor / grid / Matte section / carousel ---------------
        if (easingOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .padding(horizontal = 10.dp)
            ) {
                EasingEditor(
                    easing = segStart!!.easing,
                    progress = ((relMs - segStart.timeMs).toFloat() /
                        (segEnd!!.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                    onBegin = onEasingBegin,
                    onChange = { e -> onEasingChange(AnimProp.OPACITY, segStart.timeMs, e) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(2.dp))
        } else if (gridView) {
            // See-all grid: every mode, grouped by section, Matte last
            // under its own header. Internal vertical scroll replaces
            // the endless horizontal carousel.
            LazyVerticalGrid(
                columns = GridCells.Fixed(bp.GRID_COLUMNS),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .testTag("blend_modes_grid"),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(bp.GRID_GAP_DP.dp),
                verticalArrangement = Arrangement.spacedBy(bp.SECTION_GAP_DP.dp)
            ) {
                gridEntries.forEach { entry ->
                    when (entry) {
                        is BlendGridEntry.Header -> item(
                            key = "blend_hdr_${entry.title}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            BlendSectionHeader(title = entry.title, matte = entry.matte)
                        }
                        is BlendGridEntry.Mode -> item(key = "blend_grid_${entry.spec.name}") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                BlendModeItem(
                                    spec = entry.spec,
                                    selected = entry.spec.name == current.name,
                                    matte = entry.spec.kind == BlendKind.MATTE,
                                    onClick = { onUpdate { it.copy(blendMode = entry.spec.name) } }
                                )
                            }
                        }
                    }
                }
            }
        } else if (section == bp.MATTE) {
            // The separated Matte family: its own filled, pink-headed
            // section — these modes key off the layer BELOW.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(BlendSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("blend_matte_section")
            ) {
                GlTextLine(
                    text = "Matte — uses the layer below",
                    style = UiLabelStyle.SECTION_BOLD,
                    colorArgb = AmPink.toArgb(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blend_modes_row"),
                    horizontalArrangement = Arrangement.spacedBy(bp.ITEM_GAP_DP.dp)
                ) {
                    items(matteModes, key = { it.name }) { spec ->
                        BlendModeItem(
                            spec = spec,
                            selected = spec.name == current.name,
                            matte = true,
                            onClick = { onUpdate { it.copy(blendMode = spec.name) } }
                        )
                    }
                }
            }
        } else {
            // ---- Mode carousel of the active section ----------------------------------
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("blend_modes_row"),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(bp.ITEM_GAP_DP.dp)
            ) {
                items(sectionModes(section), key = { it.name }) { spec ->
                    BlendModeItem(
                        spec = spec,
                        selected = spec.name == current.name,
                        matte = spec.kind == BlendKind.MATTE,
                        onClick = { onUpdate { it.copy(blendMode = spec.name) } }
                    )
                }
            }
        }
        }
    }
}

/** One see-all grid entry: a section header or a mode (build 258). */
private sealed interface BlendGridEntry {
    data class Header(val title: String, val matte: Boolean) : BlendGridEntry
    data class Mode(val spec: BlendSpec) : BlendGridEntry
}

/** A grid section header; the Matte header is pink on a filled band. */
@Composable
private fun BlendSectionHeader(title: String, matte: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelGeometry.BlendPanel.SECTION_HEADER_H_DP.dp)
            .then(
                if (matte) Modifier.background(BlendSurface, RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = if (matte) 6.dp else 0.dp)
    ) {
        GlTextLine(
            text = if (matte) "Matte — uses the layer below" else title,
            style = UiLabelStyle.SECTION_BOLD,
            colorArgb = (if (matte) AmPink else BlendMuted).toArgb(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

// =====================================================================================
// One mode item: thumbnail (real blend result) + the mode's FULL name under it.
// Selected: the thumbnail turns circular, dims to 62 % and gains the green check badge.
// Build 258: the name is never ellipsized — it wraps to two full lines at the
// item width ([PanelGeometry.BlendPanel.labelLines]); matte items carry a pink
// outline so the family reads as distinct even inside the grid.
// =====================================================================================

@Composable
private fun BlendModeItem(
    spec: BlendSpec,
    selected: Boolean,
    matte: Boolean = false,
    onClick: () -> Unit
) {
    val thumb = remember(spec.name) { BlendThumbs.bitmapFor(spec) }
    // Build 252 (stage 5): the thumbnail bitmap stays a Compose Image
    // (a real colour bitmap — the atlas blit is coverage×tint and
    // cannot carry it, the stage-4 limit); the selected state's veil
    // + check badge and the mode name are drawn in GL over it. The
    // item's press scale stays bouncyClickable's on the whole item.
    val g = PanelGeometry.BlendPanel
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val checkPainter = rememberVectorPainter(Icons.Default.Check)
    val checkPx = (13f * density).roundToInt()
    val nameStyle = if (selected) UiLabelStyle.TEXT_11_MED else UiLabelStyle.TEXT_11

    fun resolveItem(): Pair<UiLabelAtlas.Entry?, List<UiLabelAtlas.Entry?>> {
        val check = atlas.resolveIcon("blend:check", checkPx, checkPx) { cv, x, y ->
            val b = rasterizePainterBitmap(checkPainter, checkPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val lines = g.labelLines(spec.name, g.ITEM_W_DP * density) { atlas.measure(it, nameStyle) }
        return check to lines.map { atlas.resolve(it, nameStyle) }
    }
    var itemEntries = resolveItem()
    if (atlas.overflowed) {
        atlas.reset()
        itemEntries = resolveItem()
    }
    val (checkEntry, nameEntries) = itemEntries
    val nameTextH = atlas.textHeightPx(nameStyle)
    val thumbPx = g.THUMB_DP * density

    Box(
        modifier = Modifier
            .width(g.ITEM_W_DP.dp)
            .bouncyClickable(pressedScale = 0.95f, onClick = onClick)
            .testTag("blend_mode_${spec.name}")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(g.THUMB_DP.dp)) {
                val shape = if (selected) CircleShape else RoundedCornerShape(12.dp)
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = spec.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(AmCardSurface, shape)
                )
            }
            Spacer(Modifier.height(g.LABEL_GAP_DP.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((nameTextH * g.LABEL_LINES / density).dp)
            )
        }
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(checkEntry, nameEntries, selected, matte, atlas.generation),
            buildCmds = { w, _, dd ->
                val cx = w / 2f
                val cy = thumbPx / 2f
                if (matte) {
                    // The Matte family's distinct mark: a pink outline
                    // around the thumbnail (an SDF stroke with a
                    // transparent fill — nothing flat sits under it).
                    addRoundRect(
                        cx, cy, thumbPx, thumbPx, 12f * dd, 0,
                        1.5f * dd, AmPink.toArgb()
                    )
                }
                if (selected) {
                    // The reference dims the chosen thumbnail to 62 %
                    // brightness — a black veil at 38 % over the
                    // (opaque) picture is exactly that multiply.
                    addCircle(cx, cy, cy, Color.Black.copy(alpha = 0.38f).toArgb())
                    addCircle(cx, cy, 12f * dd, BlendGreen.toArgb())
                    if (checkEntry != null) {
                        addText(
                            checkEntry.index,
                            cx - checkPx / 2f - pad, cy - checkPx / 2f - pad,
                            0f, 0f, checkEntry.rectW.toFloat(), checkEntry.rectH.toFloat(),
                            tintArgb = BlendCheckInk.toArgb()
                        )
                    }
                }
                val fm = atlas.fontMetrics(nameStyle)
                nameEntries.forEachIndexed { i, e ->
                    if (e != null) {
                        val lineTop = thumbPx + g.LABEL_GAP_DP * dd + i * nameTextH
                        val baseline = PanelGeometry.lineBaselinePx(lineTop, nameTextH, fm.ascent, fm.descent)
                        val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = (if (selected) Color.White else BlendMuted).toArgb()
                        )
                    }
                }
            }
        )
    }
}

// =====================================================================================
// Opacity slider (build 221): 4 dp track, green fill, 15 dp thumb with the dark
// 2 dp border and the soft green ring of the reference; the thumb grows while dragged.
// =====================================================================================

@Composable
private fun BlendOpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestChange by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    val thumbColorArgb = BlendGreen.toArgb()
    val trackColorArgb = BlendTrack.toArgb()
    val glowColorArgb = BlendGreen.copy(alpha = 0.45f).toArgb()
    val inkColorArgb = BlendInk.toArgb()

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val thumbR = 7.5.dp.toPx()
                fun toValue(x: Float): Float {
                    val span = (size.width - 2f * thumbR).coerceAtLeast(1f)
                    return (((x - thumbR) / span).coerceIn(0f, 1f) * 100f).roundToInt().toFloat()
                }
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                dragging = true
                latestChange(toValue(down.position.x))
                try {
                    drag(down.id) { change ->
                        latestChange(toValue(change.position.x))
                        change.consume()
                    }
                } finally {
                    dragging = false
                }
            }
        }
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            drawKey = value to dragging,
            buildCmds = { w, h, d ->
                val thumbR = 7.5f * d
                val y = h * 0.5f
                val x = thumbR + (w - 2f * thumbR) * (value / 100f).coerceIn(0f, 1f)
                addLine(thumbR, y, w - thumbR, y, 4f * d, trackColorArgb, capRound = true)
                addLine(thumbR, y, x, y, 4f * d, thumbColorArgb, capRound = true)
                val r = thumbR * if (dragging) 1.15f else 1f
                addCircle(x, y, r + 0.5f * d, 0, 1f * d, glowColorArgb)
                addCircle(x, y, r, inkColorArgb)
                addCircle(x, y, r - 2f * d, thumbColorArgb)
            },
        )
    }
}

@Composable
private fun KeyframeButton221(
    enabled: Boolean,
    hasKeyframes: Boolean,
    onKeyframeNow: Boolean,
    onClick: () -> Unit
) {
    val greenArgb = BlendGreen.toArgb()
    val trackArgb = BlendTrack.toArgb()
    val mutedArgb = BlendMuted.toArgb()
    Box(
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bouncyClickable { if (enabled) onClick() }
            .testTag("blend_keyframe_button"),
        contentAlignment = Alignment.Center
    ) {
        NativeVectorCanvas(
            modifier = Modifier.size(26.dp),
            drawKey = onKeyframeNow to hasKeyframes,
            buildCmds = { w, h, d ->
                val cx = w * 0.5f
                val cy = h * 0.5f
                val r = 6.8f * d
                val xs = floatArrayOf(cx, cx + r, cx, cx - r)
                val ys = floatArrayOf(cy - r, cy, cy + r, cy)
                val arm = 2.8f * d
                when {
                    onKeyframeNow -> addFilledPolygon(xs, ys, 4, greenArgb)
                    hasKeyframes -> {
                        addPolyline(xs, ys, 4, true, 1.5f * d, greenArgb)
                        addLine(cx - arm, cy, cx + arm, cy, 1.4f * d, greenArgb)
                        addLine(cx, cy - arm, cx, cy + arm, 1.4f * d, greenArgb)
                    }
                    else -> {
                        addFilledPolygon(xs, ys, 4, trackArgb)
                        addLine(cx - arm, cy, cx + arm, cy, 1.4f * d, mutedArgb)
                        addLine(cx, cy - arm, cx, cy + arm, 1.4f * d, mutedArgb)
                    }
                }
            },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlus(
    c: Offset,
    color: Color
) {
    val arm = 2.8.dp.toPx()
    drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.4.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.4.dp.toPx(), StrokeCap.Round)
}

@Composable
private fun GraphButton221(
    enabled: Boolean,
    open: Boolean,
    easing: Easing?,
    onClick: () -> Unit
) {
    val iconColor = when {
        !enabled -> BlendMuted
        open -> BlendGreen
        else -> Color.White
    }
    val e = easing ?: Easing(0.4f, 0f, 0.6f, 1f)
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bouncyClickable { if (enabled) onClick() }
            .testTag("blend_graph_button"),
        contentAlignment = Alignment.Center
    ) {
        // Build 252 (stage 5): the open-state circle is drawn in GL
        // under the (already native) easing tile.
        if (open) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                drawKey = Unit,
                buildCmds = { w, h, _ ->
                    addCircle(w / 2f, h / 2f, w / 2f, BlendSurface.toArgb())
                }
            )
        }
        NativeGlDirectCanvas(
            modifier = Modifier.size(16.dp),
            drawKey = listOf(enabled, open, e.x1, e.y1, e.x2, e.y2),
            drawOnGl = { sid, d ->
                UiGlRuntime.drawEasingTileOnGl(sid, e.x1, e.y1, e.x2, e.y2, iconColor.toArgb(), d)
            },
        )
    }
}
