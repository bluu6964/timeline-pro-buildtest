package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.VectorCmdBuilder
import com.example.engine.rememberUiAtlas
import com.example.model.PanelGeometry
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.IconDisabled
import kotlin.math.roundToInt

private val IconWhite = AmTextPrimary // build 223: tokenized (was a near-white literal)

/**
 * Build 206 — the TOOLS GRID: the one menu holding every tool under the
 * canvas (Sarowar's visibility law, 2026-11-15: no buried tools,
 * everything visible in ONE grid). Since build 210 it is the editor's
 * tools PANEL: it auto-shows on layer selection and also carries the
 * selected panel's primary tools (the footer's fixed row steps aside
 * while it is open).
 *
 * It REPLACES the build-202 More overlay panel. The overlay was anchored
 * over the canvas and arrived on Sarowar's phone as one clipped row — this
 * sheet instead lives in NORMAL LAYOUT FLOW in the editor's column, between
 * the timeline and the footer toolbar; its height is taken out of the
 * LOWER ZONE (the layer panel's share) by the screen's layout math — the
 * canvas never pays (CANVAS LAW, build 207; in build 206 the grid wrongly
 * came out of the canvas share and the canvas shrank on Sarowar's phone).
 * [toolsGridHeightDp] is the one geometry source both sides use, so the
 * sheet and the budget cannot drift.
 *
 * Contents, in order (build 210 — the TOOLS PANEL LAW: this sheet is
 * the ONE scrolling list for every tool under the canvas; build 211 —
 * NO STICKY TOOL LINES — completed it): FIRST the timeline bars'
 * actions (SelectedBar / MultiBar, published through [BarActionsState];
 * while the sheet is open those bars render nowhere else), then the
 * selected clip panel's tools — published through [MorePanelState];
 * while the sheet is open the section carries the panel's FULL list
 * (primary tools first, then its overflow), and the panel's own fixed
 * primary row is not rendered — then the utility tools (the build-201
 * utility row's IN/OUT, beat mark, onion, scopes, snap, frame, canvas
 * — the same entries, tags and semantics the More panel carried; the
 * named Marker was removed in build 222 and the selection-frame toggle
 * took its slot). Since build 215 the panel is ONE horizontally
 * scrolling row of icon + label tiles; every label is a full word (two
 * lines allowed, never truncated); past the viewport the row scrolls
 * inside the sheet — nothing pinned. Momentary entries (IN, OUT, beat,
 * canvas) close the sheet on tap via [onCloseRequest]; toggles (snap,
 * onion, scopes, frame) and clip tools keep it open, as the More panel
 * did.
 *
 * Build 251 (UI migration stage 4): the row is drawn on ONE native GL
 * surface — positions from [PanelGeometry.Grid] (host-tested), glyphs
 * and labels through the stage-2 atlas (the drawn utility glyphs keep
 * their exact vector command bodies, now emitted into the sheet's
 * stream; the bars' drawn glyphs arrive as [BarGlyph.Drawn] rasters,
 * the stage-3 pattern). The scrolling content (canvas + tap targets)
 * rides in one horizontally scrolling box, so the surface scrolls with
 * its targets; the tap targets are invisible Compose boxes at the
 * law's rects (ChromePressBox, 0.92). The sheet's flat background stays
 * on the host Column (the stage-3 precedent).
 */
const val TOOLS_GRID_COLUMNS = 4
const val TOOLS_GRID_TILE_H_DP = 48
const val TOOLS_GRID_GAP_DP = 6
const val TOOLS_GRID_PAD_V_DP = 5
const val TOOLS_GRID_MAX_ROWS = 4

/** Tiles in the grid's utility section (IN, OUT, beat, onion, scopes, snap, frame, canvas + the displaced chrome). */
const val TOOLS_GRID_UTILITY_COUNT = 17 // build 217: + Prev frame, Next frame, Preview, Save frame, Master, Density; build 218: + Templates, Controller; build 222: Marker out, Frame in (count unchanged); build 224: + Native timeline

/**
 * The sheet's exact height for [totalTiles] tiles: vertical padding + whole
 * rows up to [TOOLS_GRID_MAX_ROWS] (the grid scrolls vertically past that).
 * The screen's layout math budgets the lower zone with this same number.
 */
fun toolsGridHeightDp(totalTiles: Int): Int = toolsGridHeightDp(totalTiles, TOOLS_GRID_MAX_ROWS)

/**
 * The sheet's exact height for [totalTiles] tiles when the layout grants at
 * most [maxRows] visible rows (CANVAS LAW, build 207: when the lower zone
 * cannot fit the full grid, the grid shows fewer rows and scrolls
 * vertically in-sheet — no tool is lost, and the canvas is never touched).
 */
fun toolsGridHeightDp(totalTiles: Int, maxRows: Int): Int {
    if (totalTiles <= 0 || maxRows <= 0) return 0
    // Build 215: the tools panel is ONE horizontally scrolling row (it was a 4-column grid
    // of up to 4 rows); [maxRows] only decides whether the panel shows at all.
    return 2 * TOOLS_GRID_PAD_V_DP + TOOLS_GRID_TILE_H_DP
}

/** How many whole grid rows fit inside a height budget of [heightDp] dp. */
fun toolsGridRowsForHeight(heightDp: Int): Int {
    if (heightDp <= 0) return 0
    return ((heightDp - 2 * TOOLS_GRID_PAD_V_DP + TOOLS_GRID_GAP_DP) /
        (TOOLS_GRID_TILE_H_DP + TOOLS_GRID_GAP_DP)).coerceAtLeast(0)
}

/** A grid tile's glyph as data (build 251): drawn natively by the sheet. */
private sealed interface GridGlyph {
    /** One of the sheet's own vector glyphs (command bodies below). */
    class Vector(val kind: Kind, val on: Boolean) : GridGlyph {
        enum class Kind { GRID, FRAME, NATIVE_TIMELINE, ONION, SCOPES, MAGNET, BEAT }
    }

    /** A short text mark (IN / OUT / the preview label). */
    class TextGlyph(val text: String, val style: UiLabelStyle, val tint: Color) : GridGlyph

    /** A Material icon, rasterized into the atlas. */
    class IconGlyph(val icon: ImageVector, val tint: Color) : GridGlyph

    /** A timeline bar action's glyph (icon or drawn raster). */
    class Bar(val glyph: BarGlyph) : GridGlyph
}

/** One tile of the tools grid. */
private class GridEntry(
    val label: String,
    val active: Boolean,
    val tag: String,
    val description: String,
    val closeOnTap: Boolean,
    val onClick: () -> Unit,
    /** Delete & friends: the label reads in the semantic red (build 211, the folded bar actions). */
    val destructive: Boolean = false,
    /** A dimmed, inert tile (the multi bar's distribute actions below three checked layers). */
    val enabled: Boolean = true,
    val glyph: GridGlyph
)

@Composable
fun ToolsGridSheet(
    /** Export range points (build 199, A10): IN/OUT set the range point at the playhead (tap again to clear). White = set. */
    exportInSet: Boolean,
    exportOutSet: Boolean,
    onExportInClick: () -> Unit,
    onExportOutClick: () -> Unit,
    /** Beat mark: puts a beat mark at the playhead. */
    onBeatMarkClick: () -> Unit,
    /** Onion skin (build 190): toggles the ghost frames on the canvas. White = on. */
    onionSkinOn: Boolean,
    onOnionSkinClick: () -> Unit,
    /** Video scopes (build 196): toggles the scopes panel. White = on. */
    scopesOn: Boolean,
    onScopesClick: () -> Unit,
    /** Snap toggle (build 199, A3): canvas + transform-pad snapping. White = on. */
    snapOn: Boolean,
    onSnapClick: () -> Unit,
    /**
     * Selection frame toggle (build 222): the transform box's visibility on
     * the canvas. It lived in the controls bar until build 221; Sarowar gave
     * that slot to the snap toggle, so the frame toggle lives here now.
     * White = on.
     */
    frameOn: Boolean = true,
    onFrameClick: () -> Unit = {},
    /**
     * Native timeline toggle (build 224): the timeline renders on the C++
     * surface (ON, the default) or with the Compose panel (OFF — Sarowar's
     * A/B safety switch; it flips live, no editor restart). White = on.
     */
    nativeTimelineOn: Boolean = true,
    onNativeTimelineClick: () -> Unit = {},
    /** Canvas button: the aspect / project format sheet. */
    onCanvasClick: () -> Unit,
    /** The selected clip panel's tools, published via [MorePanelState] (full list while the panel is open, build 210); null = utility section only. */
    clipOverflow: ClipOverflowSection?,
    /**
     * Build 214 — ITEM 1 (Sarowar, 2026-11-16): the utility tools
     * (Range in/out, Beat mark, Onion, Scopes, Snap, Frame, Canvas) are
     * UNIVERSAL tools, not any layer's tools. They are included ONLY in
     * the no-selection (project) panel; a selected layer's panel carries
     * ONLY that layer's tools. The screen passes this from the selection
     * state.
     */
    includeUtilities: Boolean = true,
    /**
     * The timeline bars' actions (SelectedBar / MultiBar), published via
     * [BarActionsState] (build 211 — NO STICKY TOOL LINES: while this
     * sheet is open the bars render nowhere; their actions are this
     * list's HEAD tiles, in their bar order). Null = no bar section.
     */
    barActions: BarActionsSection? = null,
    // Build 217: the functions displaced by Sarowar's no-added-buttons
    // rule — they live HERE, inside the sheet the layer-count pill and
    // the Fx tile open (sheet content is not layout).
    onPrevFrameClick: () -> Unit = {},
    onNextFrameClick: () -> Unit = {},
    previewLabel: String = "Full",
    onPreviewClick: () -> Unit = {},
    onSaveFrameClick: () -> Unit = {},
    masterMuted: Boolean = false,
    onMasterAudioClick: () -> Unit = {},
    densityComfort: Boolean = false,
    onDensityClick: () -> Unit = {},
    // Build 218: the two adds the new 12-tile dock has no tile for —
    // Templates and Controller live HERE, inside this sheet (the old
    // AddLayerSheet's remaining entries are all dock tiles now).
    onTemplatesClick: () -> Unit = {},
    onControllerClick: () -> Unit = {},
    /** Momentary entries ask the host to close the sheet after their tap. */
    onCloseRequest: () -> Unit,
    /** Visible-row budget granted by the screen's lower-zone layout math (CANVAS LAW, build 207). */
    maxRows: Int = TOOLS_GRID_MAX_ROWS,
    modifier: Modifier = Modifier
) {
    // The utility section's entries, in the build-201 utility row's order
    // with the transport row's snap/canvas appended (build 202's order).
    val utilityEntries = listOf(
        GridEntry("Range in", exportInSet, "export_in_button", "Export range in point", closeOnTap = true, onClick = onExportInClick,
            glyph = GridGlyph.TextGlyph("IN", UiLabelStyle.GRID_MARK, if (exportInSet) IconWhite else IconDisabled)),
        GridEntry("Range out", exportOutSet, "export_out_button", "Export range out point", closeOnTap = true, onClick = onExportOutClick,
            glyph = GridGlyph.TextGlyph("OUT", UiLabelStyle.GRID_MARK, if (exportOutSet) IconWhite else IconDisabled)),
        GridEntry("Beat mark", false, "beat_mark_button", "Beat mark", closeOnTap = true, onClick = onBeatMarkClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.BEAT, on = false)),
        GridEntry("Onion", onionSkinOn, "onion_skin_button", "Onion skin", closeOnTap = false, onClick = onOnionSkinClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.ONION, on = onionSkinOn)),
        GridEntry("Scopes", scopesOn, "scopes_button", "Scopes", closeOnTap = false, onClick = onScopesClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.SCOPES, on = scopesOn)),
        GridEntry("Snap", snapOn, "snap_button", "Snap", closeOnTap = false, onClick = onSnapClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.MAGNET, on = snapOn)),
        GridEntry("Frame", frameOn, "frame_button", "Selection frame", closeOnTap = false, onClick = onFrameClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.FRAME, on = frameOn)),
        GridEntry("Native timeline", nativeTimelineOn, "native_timeline_button", "Native timeline renderer", closeOnTap = false, onClick = onNativeTimelineClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.NATIVE_TIMELINE, on = nativeTimelineOn)),
        GridEntry("Canvas", false, "grid_button", "Canvas", closeOnTap = true, onClick = onCanvasClick,
            glyph = GridGlyph.Vector(GridGlyph.Vector.Kind.GRID, on = false)),
        GridEntry("Prev frame", false, "prev_frame_button", "Previous frame", closeOnTap = false, onClick = onPrevFrameClick,
            glyph = GridGlyph.IconGlyph(Icons.Default.SkipPrevious, IconWhite)),
        GridEntry("Next frame", false, "next_frame_button", "Next frame", closeOnTap = false, onClick = onNextFrameClick,
            glyph = GridGlyph.IconGlyph(Icons.Default.SkipNext, IconWhite)),
        GridEntry("Preview", false, "preview_resolution_button", "Preview resolution", closeOnTap = true, onClick = onPreviewClick,
            glyph = GridGlyph.TextGlyph(previewLabel, UiLabelStyle.GRID_PREVIEW, IconWhite)),
        GridEntry("Save frame", false, "save_frame_button", "Save current frame", closeOnTap = true, onClick = onSaveFrameClick,
            glyph = GridGlyph.IconGlyph(Icons.Default.PhotoCamera, IconWhite)),
        GridEntry("Master", masterMuted, "master_audio_button", "Master audio mute", closeOnTap = false, onClick = onMasterAudioClick,
            glyph = GridGlyph.IconGlyph(
                if (masterMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                if (masterMuted) IconWhite else AmTextDim
            )),
        GridEntry("Density", densityComfort, "density_button", "Lane density (32 / 44 dp)", closeOnTap = false, onClick = onDensityClick,
            glyph = GridGlyph.IconGlyph(Icons.Default.ViewStream, if (densityComfort) IconWhite else AmTextDim)),
        GridEntry("Templates", false, "templates_button", "Templates", closeOnTap = true, onClick = onTemplatesClick,
            glyph = GridGlyph.IconGlyph(Icons.Default.Layers, IconWhite)),
        GridEntry("Controller", false, "controller_button", "Add controller layer", closeOnTap = true, onClick = onControllerClick,
            glyph = GridGlyph.IconGlyph(Icons.Default.Tune, IconWhite))
    )
    val clipEntries = clipOverflow?.tools?.map { tool ->
        val used = tool.id in clipOverflow.usedTools
        GridEntry(
            label = tool.label,
            active = used,
            tag = clipOverflow.tileTag(tool.id),
            description = tool.label,
            closeOnTap = false,
            onClick = { clipOverflow.onTool(tool) },
            glyph = GridGlyph.IconGlyph(tool.icon, if (used) IconWhite else AmTextDim)
        )
    } ?: emptyList()
    // The timeline bars' actions ride at the HEAD of the list (build
    // 211): while this sheet is open the SelectedBar / MultiBar are not
    // rendered anywhere — these tiles ARE those bars, in bar order,
    // with their callbacks, states and tags.
    val barEntries = barActions?.tiles?.map { tile ->
        GridEntry(
            label = tile.label,
            active = false,
            tag = tile.tag,
            description = tile.label,
            closeOnTap = false,
            onClick = tile.onClick,
            destructive = tile.destructive,
            enabled = tile.enabled,
            glyph = GridGlyph.Bar(tile.glyph)
        )
    } ?: emptyList()
    // ONE scrolling list (TOOLS PANEL LAW, build 210; completed by build
    // 211's no-sticky-lines law): bar actions first, then the clip
    // panel's tools, utility tools last — every tile in this single
    // grid scrolls together; nothing is pinned above or below it.
    // Build 215: the single row leads with the tools used most (Edit / Effects / Animations /
    // Color / Transform), then the bar actions (Delete last), then the other clip tools, then
    // the utility tools. Build 214 (ITEM 1): the utility section rides ONLY when
    // [includeUtilities] — the no-selection panel; a layer's panel is layer tools only.
    val primaryIds = setOf("edit_group", "edit_text", "edit_shape", "edit_points", "effects", "animations", "color", "transform")
    val primaryTags = clipOverflow?.tools?.filter { it.id in primaryIds }?.map { clipOverflow.tileTag(it.id) }?.toSet() ?: emptySet()
    val (primaryClip, restClip) = clipEntries.partition { it.tag in primaryTags }
    val entries = primaryClip + barEntries.sortedBy { it.destructive } + restClip +
        (if (includeUtilities) utilityEntries else emptyList())

    val g = PanelGeometry.Grid
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val lineBoxPx = 11f * density * fontScale

    // Stable painters per icon (an entry's icon never changes identity).
    val distinctIcons = entries.mapNotNull { e ->
        when (val gl = e.glyph) {
            is GridGlyph.IconGlyph -> gl.icon
            is GridGlyph.Bar -> (gl.glyph as? BarGlyph.Icon)?.icon
            else -> null
        }
    }.distinct()
    val painterByName = HashMap<String, androidx.compose.ui.graphics.vector.VectorPainter>()
    for (icon in distinctIcons) {
        painterByName[icon.name] = key(icon.name) { rememberVectorPainter(icon) }
    }

    val iconPx = (g.GLYPH_DP * density).roundToInt()
    fun resolveAll(): List<GridResolved> = entries.map { entry ->
        val labelStyle = if (entry.active) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL
        val lineTexts = PanelGeometry.wrapGreedy(entry.label, g.labelWidthPx(density), 2) {
            atlas.measure(it, labelStyle)
        }
        val labelLines = lineTexts.map { atlas.resolve(it, labelStyle) }
        val glyphEntry = when (val gl = entry.glyph) {
            is GridGlyph.IconGlyph -> painterByName[gl.icon.name]?.let { p ->
                atlas.resolveIcon("grid-icon:${gl.icon.name}", iconPx, iconPx) { cv, x, y ->
                    val b = rasterizePainterBitmap(p, iconPx, density)
                    cv.drawBitmap(b, x, y, null)
                    b.recycle()
                }
            }
            is GridGlyph.Bar -> when (val bg = gl.glyph) {
                is BarGlyph.Icon -> painterByName[bg.icon.name]?.let { p ->
                    atlas.resolveIcon("grid-icon:${bg.icon.name}", iconPx, iconPx) { cv, x, y ->
                        val b = rasterizePainterBitmap(p, iconPx, density)
                        cv.drawBitmap(b, x, y, null)
                        b.recycle()
                    }
                }
                is BarGlyph.Drawn -> {
                    val wPx = (bg.widthDp * density).roundToInt()
                    val hPx = (bg.heightDp * density).roundToInt()
                    atlas.resolveIcon("grid-drawn:${entry.tag}", wPx, hPx) { cv, x, y ->
                        val b = rasterizeDrawBitmap(wPx, hPx, density, bg.draw)
                        cv.drawBitmap(b, x, y, null)
                        b.recycle()
                    }
                }
            }
            is GridGlyph.TextGlyph -> atlas.resolve(gl.text, gl.style)
            is GridGlyph.Vector -> null
        }
        GridResolved(glyphEntry, labelLines)
    }
    var resolved = resolveAll()
    if (atlas.overflowed) {
        atlas.reset()
        resolved = resolveAll()
    }

    val scales = remember(entries.map { it.tag }) { entries.map { Animatable(1f) } }
    val scaleValues = scales.map { it.value }
    val scrollState = rememberScrollState()

    fun dim(argb: Int, f: Float): Int =
        if (f >= 1f) argb else Color(argb).copy(alpha = Color(argb).alpha * f).toArgb()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(toolsGridHeightDp(entries.size, maxRows).dp)
            .background(AmCardSurface, RoundedCornerShape(AmCornerCard))
            .clip(RoundedCornerShape(AmCornerCard))
            .padding(vertical = TOOLS_GRID_PAD_V_DP.dp)
            .testTag("tools_grid_sheet")
    ) {
        if (entries.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width((g.contentWidthPx(entries.size, density) / density).dp)
                        .fillMaxHeight()
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = atlas,
                        drawKey = listOf(resolved, scaleValues, atlas.generation),
                        buildCmds = { _, _, dd ->
                            entries.forEachIndexed { i, entry ->
                                val left = g.tileLeftPx(i, dd)
                                val tileW = g.TILE_W_DP * dd
                                val tileH = g.TILE_H_DP * dd
                                val dimF = if (entry.enabled) 1f else 0.38f
                                val labelStyle =
                                    if (entry.active) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL
                                val labelFm = atlas.fontMetrics(labelStyle)
                                val lines = resolved[i].labelLines
                                val stackTop = g.stackTopPx(0f, lines.size, lineBoxPx, dd)
                                setScaleAbout(left + tileW / 2f, tileH / 2f, scales[i].value)
                                addRoundRect(
                                    left + tileW / 2f, tileH / 2f, tileW, tileH,
                                    g.TILE_CORNER_DP * dd,
                                    dim((if (entry.active) AmPillSelected else AmPanelHigh).toArgb(), dimF)
                                )
                                // The glyph.
                                val glyphLeft = g.glyphLeftPx(left, dd)
                                when (val gl = entry.glyph) {
                                    is GridGlyph.Vector -> {
                                        // The canvas and beat glyphs are always
                                        // primary; the state glyphs read
                                        // primary when on, disabled-grey when off.
                                        val baseTint = when (gl.kind) {
                                            GridGlyph.Vector.Kind.GRID,
                                            GridGlyph.Vector.Kind.BEAT -> AmTextPrimary
                                            else -> if (gl.on) AmTextPrimary else IconDisabled
                                        }
                                        val tint = dim(baseTint.toArgb(), dimF)
                                        setTranslate(glyphLeft, stackTop)
                                        gridVectorGlyph(gl.kind, gl.on, g.GLYPH_DP * dd, tint)
                                        setTranslate(0f, 0f)
                                    }
                                    is GridGlyph.TextGlyph -> resolved[i].glyphEntry?.let { e ->
                                        val textH = atlas.textHeightPx(gl.style)
                                        val boxTop = stackTop + (g.GLYPH_DP * dd - textH) / 2f
                                        addText(
                                            e.index,
                                            glyphLeft + (g.GLYPH_DP * dd - e.advance) / 2f + e.bearing - pad,
                                            boxTop - atlas.fontMetrics(gl.style).ascent - e.base,
                                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                            tintArgb = dim(gl.tint.toArgb(), dimF)
                                        )
                                    }
                                    is GridGlyph.IconGlyph -> resolved[i].glyphEntry?.let { e ->
                                        addText(
                                            e.index, glyphLeft - pad, stackTop - pad,
                                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                            tintArgb = dim(gl.tint.toArgb(), dimF)
                                        )
                                    }
                                    is GridGlyph.Bar -> resolved[i].glyphEntry?.let { e ->
                                        when (val bg = gl.glyph) {
                                            is BarGlyph.Icon -> addText(
                                                e.index, glyphLeft - pad, stackTop - pad,
                                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                                tintArgb = dim(bg.tint.toArgb(), dimF)
                                            )
                                            is BarGlyph.Drawn -> addText(
                                                e.index,
                                                left + tileW / 2f - e.inkW / 2f - pad,
                                                stackTop + g.GLYPH_DP * dd / 2f - e.inkH / 2f - pad,
                                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                                tintArgb = dim(bg.tint.toArgb(), dimF)
                                            )
                                        }
                                    }
                                }
                                // The label lines (full words, up to two lines).
                                val labelTint = dim(
                                    when {
                                        entry.destructive -> AmDanger
                                        entry.active -> IconWhite
                                        else -> AmTextDim
                                    }.toArgb(), dimF
                                )
                                val centreX = left + tileW / 2f
                                val labelW = g.labelWidthPx(dd)
                                val textBlockTop = stackTop + (g.GLYPH_DP + g.GLYPH_GAP_DP) * dd
                                lines.forEachIndexed { li, e ->
                                    if (e == null) return@forEachIndexed
                                    val baseline = PanelGeometry.lineBaselinePx(
                                        textBlockTop + li * lineBoxPx, lineBoxPx,
                                        labelFm.ascent, labelFm.descent
                                    )
                                    if (e.advance <= labelW) {
                                        addText(
                                            e.index,
                                            centreX - e.advance / 2f + e.bearing - pad,
                                            baseline - e.base,
                                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                            tintArgb = labelTint
                                        )
                                    } else {
                                        // Centre-clipped (a single glyph wider
                                        // than the label box — the wrap law
                                        // breaks everything else to fit).
                                        val cut = (e.advance - labelW) / 2f
                                        var srcDX = pad - e.bearing + cut
                                        var dstX = centreX - e.advance / 2f + e.bearing - pad + cut
                                        if (srcDX < 0f) {
                                            dstX += srcDX
                                            srcDX = 0f
                                        }
                                        addText(
                                            e.index, dstX, baseline - e.base,
                                            srcDX, 0f, labelW, e.rectH.toFloat(),
                                            tintArgb = labelTint
                                        )
                                    }
                                }
                                clearTransform()
                            }
                        }
                    )
                    // Tap targets at the law's tile rects, invisible.
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = g.PAD_H_DP.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(g.GAP_DP.dp)
                    ) {
                        entries.forEachIndexed { i, entry ->
                            ChromePressBox(
                                scales[i], 0.92f,
                                {
                                    if (entry.enabled) {
                                        entry.onClick()
                                        if (entry.closeOnTap) onCloseRequest()
                                    }
                                },
                                Modifier
                                    .width(g.TILE_W_DP.dp)
                                    .height(g.TILE_H_DP.dp),
                                tag = entry.tag,
                                description = entry.description
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class GridResolved(
    val glyphEntry: UiLabelAtlas.Entry?,
    val labelLines: List<UiLabelAtlas.Entry?>
)

// ── Glyphs (moved from the build-202 More panel; build 251 emits the
// same commands into the sheet's GL stream, in a 24-unit space scaled
// by u = size/24, origin at the glyph box's top-left) ─────────────────

private fun VectorCmdBuilder.gridVectorGlyph(
    kind: GridGlyph.Vector.Kind, on: Boolean, sizePx: Float, cArgb: Int
) {
    val w = sizePx
    val h = sizePx
    val u = w / 24f
    when (kind) {
        GridGlyph.Vector.Kind.GRID -> {
            // Rounded outline with a 4 x 3 grid of small squares.
            val rw = 20.7f * u - 2.2f * u
            val rh = 19f * u - 2.2f * u
            val left = (w - 20.7f * u) / 2f + 1.1f * u
            val top = (h - 19f * u) / 2f + 1.1f * u
            addRoundRect(left + rw / 2f, top + rh / 2f, rw, rh, 4.6f * u, 0, 2.2f * u, cArgb)
            val sq = 2.3f * u
            val cx0 = w / 2f - 1.5f * 3.85f * u
            val cy0 = h / 2f - 4.4f * u
            for (r in 0..2) for (k in 0..3) {
                addRoundRect(cx0 + k * 3.85f * u, cy0 + r * 4.4f * u, sq, sq, 0.5f * u, cArgb)
            }
        }
        GridGlyph.Vector.Kind.FRAME -> {
            // The selection frame (build 222): a handled rectangle.
            addRoundRect(12f * u, 12f * u, 14f * u, 14f * u, 1.6f * u, 0, 1.7f * u, cArgb)
            val sq = 3.4f * u
            for ((hx, hy) in listOf(5f to 5f, 19f to 5f, 5f to 19f, 19f to 19f)) {
                addRoundRect(hx * u, hy * u, sq, sq, 0.8f * u, cArgb)
            }
        }
        GridGlyph.Vector.Kind.NATIVE_TIMELINE -> {
            // A ticked ruler over two lane bars (build 224).
            addLine(3f * u, 6f * u, 21f * u, 6f * u, 1.6f * u, cArgb, capRound = false)
            for (tx in listOf(6f, 12f, 18f)) {
                addLine(tx * u, 4f * u, tx * u, 8f * u, 1.6f * u, cArgb, capRound = false)
            }
            addRoundRect((3f + 6.5f) * u, (11f + 2.2f) * u, 13f * u, 4.4f * u, 2.2f * u, cArgb)
            addRoundRect((8f + 6.5f) * u, (17.6f + 2.2f) * u, 13f * u, 4.4f * u, 2.2f * u, cArgb)
        }
        GridGlyph.Vector.Kind.ONION -> {
            // Two overlapping frames (the onion-skin ghosts, build 190):
            // the back frame is a bare outline, the front frame is
            // filled while the ghosts are on.
            addRoundRect((8.6f + 6f) * u, (3.4f + 6f) * u, 12f * u, 12f * u, 2.6f * u, 0, 1.8f * u, cArgb)
            if (on) {
                addRoundRect((3.4f + 6f) * u, (8.6f + 6f) * u, 12f * u, 12f * u, 2.6f * u, cArgb)
            } else {
                addRoundRect((3.4f + 6f) * u, (8.6f + 6f) * u, 12f * u, 12f * u, 2.6f * u, 0, 1.8f * u, cArgb)
            }
        }
        GridGlyph.Vector.Kind.SCOPES -> {
            // Four histogram bars on a baseline (build 196).
            val base = 19.4f * u
            addLine(3.4f * u, base, 20.6f * u, base, 1.8f * u, cArgb, capRound = true)
            val bars = listOf(6.5f to 8f, 10.6f to 13.5f, 14.7f to 10.5f, 18.8f to 15.5f)
            for ((x, top) in bars) {
                addLine(x * u, base, x * u, (24f - top) * u, 2.6f * u, cArgb, capRound = true)
            }
        }
        GridGlyph.Vector.Kind.MAGNET -> {
            // U-magnet (build 199, A3): the snap toggle.
            val sw = 2.6f * u
            addLine(6.6f * u, 4.5f * u, 6.6f * u, 12.5f * u, sw, cArgb, capRound = true)
            addCubic(6.6f * u, 12.5f * u, 6.6f * u, 17.2f * u, 9.4f * u, 19.6f * u, 12f * u, 19.6f * u, sw, cArgb, capRound = true)
            addCubic(12f * u, 19.6f * u, 14.6f * u, 19.6f * u, 17.4f * u, 17.2f * u, 17.4f * u, 12.5f * u, sw, cArgb, capRound = true)
            addLine(17.4f * u, 12.5f * u, 17.4f * u, 4.5f * u, sw, cArgb, capRound = true)
            addLine(4.6f * u, 4.5f * u, 8.6f * u, 4.5f * u, sw, cArgb, capRound = true)
            addLine(15.4f * u, 4.5f * u, 19.4f * u, 4.5f * u, sw, cArgb, capRound = true)
        }
        GridGlyph.Vector.Kind.BEAT -> {
            // White diamond (the mark on the ruler) with a plus beside it.
            val xs = floatArrayOf(10f * u, 16.6f * u, 10f * u, 3.4f * u)
            val ys = floatArrayOf(3.2f * u, 10f * u, 16.8f * u, 10f * u)
            addFilledPolygon(xs, ys, 4, cArgb)
            addPolyline(xs, ys, 4, true, 1.6f * u, cArgb, capRound = true)
            val px = 17.4f * u
            val py = 17.4f * u
            val arm = 3.6f * u
            addLine(px - arm, py, px + arm, py, 2.1f * u, cArgb, capRound = true)
            addLine(px, py - arm, px, py + arm, 2.1f * u, cArgb, capRound = true)
        }
    }
}
