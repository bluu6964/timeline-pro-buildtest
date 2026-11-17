package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.IconDisabled
import com.example.ui.util.bouncyClickable

private val IconWhite = Color(0xFFF2F2F2)

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
 * took its slot). The grid is
 * 4 columns of icon + label tiles; every label is a full word (two
 * lines allowed, never truncated); past the viewport the grid scrolls
 * VERTICALLY inside the sheet — no horizontal scroll, no clipped rows,
 * nothing pinned. Momentary entries (IN, OUT, beat, canvas)
 * close the sheet on tap via [onCloseRequest]; toggles (snap, onion,
 * scopes, frame) and clip tools keep it open, as the More panel did.
 *
 * Geometry (dp, 360 dp reference width): sheet padding 10 dp each side +
 * 3 gaps of 8 dp → tile width (360 − 16 sheet margin − 20 − 24) / 4 = 75 dp;
 * tile height 62 dp (24 dp glyph + two 13 dp label lines + padding) — every
 * target ≥ 40 dp.
 */
const val TOOLS_GRID_COLUMNS = 4
const val TOOLS_GRID_TILE_H_DP = 62
const val TOOLS_GRID_GAP_DP = 8
const val TOOLS_GRID_PAD_V_DP = 8
const val TOOLS_GRID_MAX_ROWS = 4

/** Tiles in the grid's utility section (IN, OUT, beat, onion, scopes, snap, frame, canvas + the displaced chrome). */
const val TOOLS_GRID_UTILITY_COUNT = 16 // build 217: + Prev frame, Next frame, Preview, Save frame, Master, Density; build 218: + Templates, Controller; build 222: Marker out, Frame in (count unchanged)

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
    val glyph: @Composable () -> Unit
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
        GridEntry("Range in", exportInSet, "export_in_button", "Export range in point", closeOnTap = true, onClick = onExportInClick) {
            Text("IN", color = if (exportInSet) IconWhite else IconDisabled, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        },
        GridEntry("Range out", exportOutSet, "export_out_button", "Export range out point", closeOnTap = true, onClick = onExportOutClick) {
            Text("OUT", color = if (exportOutSet) IconWhite else IconDisabled, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        },
        GridEntry("Beat mark", false, "beat_mark_button", "Beat mark", closeOnTap = true, onClick = onBeatMarkClick) { BeatMarkGlyph() },
        GridEntry("Onion", onionSkinOn, "onion_skin_button", "Onion skin", closeOnTap = false, onClick = onOnionSkinClick) { OnionGlyph(onionSkinOn) },
        GridEntry("Scopes", scopesOn, "scopes_button", "Scopes", closeOnTap = false, onClick = onScopesClick) { ScopesGlyph(scopesOn) },
        GridEntry("Snap", snapOn, "snap_button", "Snap", closeOnTap = false, onClick = onSnapClick) { MagnetGlyph(snapOn) },
        GridEntry("Frame", frameOn, "frame_button", "Selection frame", closeOnTap = false, onClick = onFrameClick) { FrameGlyph(frameOn) },
        GridEntry("Canvas", false, "grid_button", "Canvas", closeOnTap = true, onClick = onCanvasClick) { GridGlyph() },
        // Build 217: displaced chrome, housed in this sheet — frame
        // stepping (was the transport), the preview-resolution sheet
        // (was the transport's resolution button), Save frame (was the
        // top bar), the master mute (was the timeline's floating
        // speaker) and the lane-density toggle (the reference's own
        // layer-pill job, kept as a tile here).
        GridEntry("Prev frame", false, "prev_frame_button", "Previous frame", closeOnTap = false, onClick = onPrevFrameClick) {
            Icon(Icons.Default.SkipPrevious, null, tint = IconWhite, modifier = Modifier.size(23.dp))
        },
        GridEntry("Next frame", false, "next_frame_button", "Next frame", closeOnTap = false, onClick = onNextFrameClick) {
            Icon(Icons.Default.SkipNext, null, tint = IconWhite, modifier = Modifier.size(23.dp))
        },
        GridEntry("Preview", false, "preview_resolution_button", "Preview resolution", closeOnTap = true, onClick = onPreviewClick) {
            Text(previewLabel, color = IconWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        },
        GridEntry("Save frame", false, "save_frame_button", "Save current frame", closeOnTap = true, onClick = onSaveFrameClick) {
            Icon(Icons.Default.PhotoCamera, null, tint = IconWhite, modifier = Modifier.size(23.dp))
        },
        GridEntry("Master", masterMuted, "master_audio_button", "Master audio mute", closeOnTap = false, onClick = onMasterAudioClick) {
            Icon(
                if (masterMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                null,
                tint = if (masterMuted) IconWhite else AmTextDim,
                modifier = Modifier.size(23.dp)
            )
        },
        GridEntry("Density", densityComfort, "density_button", "Lane density (32 / 44 dp)", closeOnTap = false, onClick = onDensityClick) {
            Icon(
                Icons.Default.ViewStream, null,
                tint = if (densityComfort) IconWhite else AmTextDim,
                modifier = Modifier.size(23.dp)
            )
        },
        // Build 218: displaced by the 12-tile dock (no dock tile of their
        // own) — the Templates library and the Controller add live here.
        GridEntry("Templates", false, "templates_button", "Templates", closeOnTap = true, onClick = onTemplatesClick) {
            Icon(Icons.Default.Layers, null, tint = IconWhite, modifier = Modifier.size(23.dp))
        },
        GridEntry("Controller", false, "controller_button", "Add controller layer", closeOnTap = true, onClick = onControllerClick) {
            Icon(Icons.Default.Tune, null, tint = IconWhite, modifier = Modifier.size(23.dp))
        }
    )
    // The selected panel's tools ride FIRST, in the panel's own order,
    // dispatched by the panel itself (build 210: while this sheet is the
    // unified tools panel, the section carries the panel's FULL list —
    // primary tools first, then its overflow).
    val clipEntries = clipOverflow?.tools?.map { tool ->
        val used = tool.id in clipOverflow.usedTools
        GridEntry(
            label = tool.label,
            active = used,
            tag = clipOverflow.tileTag(tool.id),
            description = tool.label,
            closeOnTap = false,
            onClick = { clipOverflow.onTool(tool) }
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = if (used) IconWhite else AmTextDim,
                modifier = Modifier.size(23.dp)
            )
        }
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
            enabled = tile.enabled
        ) { tile.glyph() }
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(toolsGridHeightDp(entries.size, maxRows).dp)
            .background(AmCardSurface, RoundedCornerShape(AmCornerCard))
            .clip(RoundedCornerShape(AmCornerCard))
            .padding(vertical = TOOLS_GRID_PAD_V_DP.dp)
            .testTag("tools_grid_sheet")
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(TOOLS_GRID_GAP_DP.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            rowItems(entries, key = { it.tag }) { entry ->
                GridTile(entry) {
                    if (entry.enabled) {
                        entry.onClick()
                        if (entry.closeOnTap) onCloseRequest()
                    }
                }
            }
        }
    }
}

/**
 * A grid tile: a soft rounded surface (active = the selected-pill fill),
 * glyph over a full-word label. Labels wrap to two lines — never truncated.
 */
@Composable
private fun GridTile(entry: GridEntry, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(TOOLS_GRID_TILE_H_DP.dp)
            .semantics { contentDescription = entry.description }
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .testTag(entry.tag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (entry.active) AmPillSelected else AmPanelHigh,
                    RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .alpha(if (entry.enabled) 1f else 0.38f)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                entry.glyph()
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.label,
                color = when {
                    entry.destructive -> AmDanger
                    entry.active -> IconWhite
                    else -> AmTextDim
                },
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (entry.active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Glyphs (moved from the build-202 More panel, unchanged) ──────────────

/** Rounded outline with a 4 x 3 grid of small squares. */
@Composable
private fun GridGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = Color(0xFFF2F2F2)
        val w = 20.7f * u - 2.2f * u
        val h = 19f * u - 2.2f * u
        val left = (size.width - 20.7f * u) / 2f + 1.1f * u
        val top = (size.height - 19f * u) / 2f + 1.1f * u
        drawRoundRect(c, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(4.6f * u), style = Stroke(2.2f * u))
        val sq = 2.3f * u
        val cx0 = size.width / 2f - 1.5f * 3.85f * u
        val cy0 = size.height / 2f - 4.4f * u
        for (r in 0..2) for (k in 0..3) {
            drawRoundRect(
                c,
                Offset(cx0 + k * 3.85f * u - sq / 2f, cy0 + r * 4.4f * u - sq / 2f),
                androidx.compose.ui.geometry.Size(sq, sq), androidx.compose.ui.geometry.CornerRadius(0.5f * u)
            )
        }
    }
}

/**
 * The selection frame (build 222): a handled rectangle — the transform box
 * the canvas draws around the selected layer. White = on, grey = off —
 * the used-tool state law, like every other state in this bar.
 */
@Composable
private fun FrameGlyph(on: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = if (on) Color(0xFFF2F2F2) else IconDisabled
        drawRoundRect(c, Offset(5f * u, 5f * u), androidx.compose.ui.geometry.Size(14f * u, 14f * u), androidx.compose.ui.geometry.CornerRadius(1.6f * u), style = Stroke(1.7f * u))
        val sq = 3.4f * u
        for ((hx, hy) in listOf(5f to 5f, 19f to 5f, 5f to 19f, 19f to 19f)) {
            drawRoundRect(c, Offset(hx * u - sq / 2f, hy * u - sq / 2f), androidx.compose.ui.geometry.Size(sq, sq), androidx.compose.ui.geometry.CornerRadius(0.8f * u))
        }
    }
}

/**
 * Two overlapping frames (the onion-skin ghosts, build 190): the back frame is a bare
 * outline, the front frame is filled while the ghosts are on. White = on, grey = off —
 * the used-tool state law, like every other state in this bar.
 */
@Composable
private fun OnionGlyph(on: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = if (on) Color(0xFFF2F2F2) else IconDisabled
        drawRoundRect(c, Offset(8.6f * u, 3.4f * u), androidx.compose.ui.geometry.Size(12f * u, 12f * u), androidx.compose.ui.geometry.CornerRadius(2.6f * u), style = Stroke(1.8f * u))
        if (on) drawRoundRect(c, Offset(3.4f * u, 8.6f * u), androidx.compose.ui.geometry.Size(12f * u, 12f * u), androidx.compose.ui.geometry.CornerRadius(2.6f * u))
        else drawRoundRect(c, Offset(3.4f * u, 8.6f * u), androidx.compose.ui.geometry.Size(12f * u, 12f * u), androidx.compose.ui.geometry.CornerRadius(2.6f * u), style = Stroke(1.8f * u))
    }
}

/**
 * Four histogram bars on a baseline (the video scopes, build 196). White =
 * on, grey = off — the used-tool state law, like the onion glyph beside it.
 */
@Composable
private fun ScopesGlyph(on: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = if (on) Color(0xFFF2F2F2) else IconDisabled
        val base = 19.4f * u
        drawLine(c, Offset(3.4f * u, base), Offset(20.6f * u, base), 1.8f * u, StrokeCap.Round)
        val bars = listOf(6.5f to 8f, 10.6f to 13.5f, 14.7f to 10.5f, 18.8f to 15.5f)
        for ((x, top) in bars) {
            drawLine(c, Offset(x * u, base), Offset(x * u, (24f - top) * u), 2.6f * u, StrokeCap.Round)
        }
    }
}

/** U-magnet (build 199, A3): the snap toggle. White = snapping on, grey = off. */
@Composable
private fun MagnetGlyph(on: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = if (on) Color(0xFFF2F2F2) else IconDisabled
        val body = Path().apply {
            moveTo(6.6f * u, 4.5f * u)
            lineTo(6.6f * u, 12.5f * u)
            cubicTo(6.6f * u, 17.2f * u, 9.4f * u, 19.6f * u, 12f * u, 19.6f * u)
            cubicTo(14.6f * u, 19.6f * u, 17.4f * u, 17.2f * u, 17.4f * u, 12.5f * u)
            lineTo(17.4f * u, 4.5f * u)
        }
        drawPath(body, c, style = Stroke(width = 2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Pole caps: the two short bars across the magnet's feet.
        drawLine(c, Offset(4.6f * u, 4.5f * u), Offset(8.6f * u, 4.5f * u), 2.6f * u, StrokeCap.Round)
        drawLine(c, Offset(15.4f * u, 4.5f * u), Offset(19.4f * u, 4.5f * u), 2.6f * u, StrokeCap.Round)
    }
}

/** White diamond (the mark on the ruler) with a white plus beside it: adds a beat mark. */
@Composable
private fun BeatMarkGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val cyan = Color(0xFFF2F2F2) // build 217: light glyph on the dark panel
        val d = Path().apply {
            moveTo(10f * u, 3.2f * u); lineTo(16.6f * u, 10f * u); lineTo(10f * u, 16.8f * u); lineTo(3.4f * u, 10f * u); close()
        }
        drawPath(d, cyan)
        drawPath(d, cyan, style = Stroke(1.6f * u, join = StrokeJoin.Round))
        // plus, lower right
        val px = 17.4f * u
        val py = 17.4f * u
        val arm = 3.6f * u
        drawLine(Color(0xFFF2F2F2), Offset(px - arm, py), Offset(px + arm, py), 2.1f * u, StrokeCap.Round)
        drawLine(Color(0xFFF2F2F2), Offset(px, py - arm), Offset(px, py + arm), 2.1f * u, StrokeCap.Round)
    }
}
