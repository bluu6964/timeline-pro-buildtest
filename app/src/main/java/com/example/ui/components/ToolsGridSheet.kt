package com.example.ui.components

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.IconDisabled
import com.example.ui.util.bouncyClickable

private val IconWhite = Color(0xFFF2F2F2)

/**
 * Build 206 — the TOOLS GRID: the one menu holding every tool that is not in
 * the bottom pill toolbar's primary row (Sarowar's visibility law,
 * 2026-11-15: no buried tools, everything visible in ONE grid).
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
 * Contents, in order: the 8 utility tools (the build-201 utility row's
 * IN/OUT, beat mark, marker, onion, scopes, snap, canvas — the same entries,
 * tags and semantics the More panel carried) and then the selected clip
 * panel's overflow tools, published through [MorePanelState] exactly as
 * before. The grid is 4 columns of icon + label tiles; every label is a
 * full word (two lines allowed, never truncated); past the viewport the
 * grid scrolls VERTICALLY inside the sheet — no horizontal scroll, no
 * clipped rows. Momentary entries (IN, OUT, beat, marker, canvas) close
 * the sheet on tap via [onCloseRequest]; toggles (snap, onion, scopes) and
 * clip tools keep it open, as the More panel did.
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

/** Tiles in the grid's utility section (IN, OUT, beat, marker, onion, scopes, snap, canvas). */
const val TOOLS_GRID_UTILITY_COUNT = 8

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
    val rows = minOf(
        (totalTiles + TOOLS_GRID_COLUMNS - 1) / TOOLS_GRID_COLUMNS,
        maxRows
    )
    return 2 * TOOLS_GRID_PAD_V_DP + rows * TOOLS_GRID_TILE_H_DP + (rows - 1) * TOOLS_GRID_GAP_DP
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
    /** Marker: adds a NAMED marker at the playhead (or edits the one already there). */
    onMarkerClick: () -> Unit,
    /** Onion skin (build 190): toggles the ghost frames on the canvas. White = on. */
    onionSkinOn: Boolean,
    onOnionSkinClick: () -> Unit,
    /** Video scopes (build 196): toggles the scopes panel. White = on. */
    scopesOn: Boolean,
    onScopesClick: () -> Unit,
    /** Snap toggle (build 199, A3): canvas + transform-pad snapping. White = on. */
    snapOn: Boolean,
    onSnapClick: () -> Unit,
    /** Canvas button: the aspect / project format sheet. */
    onCanvasClick: () -> Unit,
    /** The selected clip panel's overflow tools, published via [MorePanelState]; null = utility section only. */
    clipOverflow: ClipOverflowSection?,
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
        GridEntry("Marker", false, "marker_button", "Marker", closeOnTap = true, onClick = onMarkerClick) { MarkerGlyph() },
        GridEntry("Onion", onionSkinOn, "onion_skin_button", "Onion skin", closeOnTap = false, onClick = onOnionSkinClick) { OnionGlyph(onionSkinOn) },
        GridEntry("Scopes", scopesOn, "scopes_button", "Scopes", closeOnTap = false, onClick = onScopesClick) { ScopesGlyph(scopesOn) },
        GridEntry("Snap", snapOn, "snap_button", "Snap", closeOnTap = false, onClick = onSnapClick) { MagnetGlyph(snapOn) },
        GridEntry("Canvas", false, "grid_button", "Canvas", closeOnTap = true, onClick = onCanvasClick) { GridGlyph() }
    )
    // The selected panel's overflow tools ride after the utility entries,
    // in the panel's own order, dispatched by the panel itself.
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
    val entries = utilityEntries + clipEntries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(toolsGridHeightDp(entries.size, maxRows).dp)
            .background(AmCardSurface, RoundedCornerShape(AmCornerCard))
            .clip(RoundedCornerShape(AmCornerCard))
            .padding(vertical = TOOLS_GRID_PAD_V_DP.dp)
            .testTag("tools_grid_sheet")
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(TOOLS_GRID_COLUMNS),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(TOOLS_GRID_GAP_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TOOLS_GRID_GAP_DP.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(entries, key = { it.tag }) { entry ->
                GridTile(entry) {
                    entry.onClick()
                    if (entry.closeOnTap) onCloseRequest()
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
            .fillMaxWidth()
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
                color = if (entry.active) IconWhite else AmTextDim,
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

/** A flag on a pole (the named marker's ruler glyph): opens the marker name dialog. */
@Composable
private fun MarkerGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = Color(0xFFF2F2F2)
        drawLine(c, Offset(7f * u, 3.2f * u), Offset(7f * u, 20.8f * u), 2.2f * u, StrokeCap.Round)
        val flag = Path().apply {
            moveTo(7f * u, 3.6f * u)
            lineTo(19.5f * u, 7.2f * u)
            lineTo(7f * u, 10.8f * u)
            close()
        }
        drawPath(flag, c)
        drawPath(flag, c, style = Stroke(1.4f * u, join = StrokeJoin.Round))
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
        val cyan = Color(0xFFFFFFFF)
        val d = Path().apply {
            moveTo(10f * u, 3.2f * u); lineTo(16.6f * u, 10f * u); lineTo(10f * u, 16.8f * u); lineTo(3.4f * u, 10f * u); close()
        }
        drawPath(d, cyan)
        drawPath(d, cyan, style = Stroke(1.6f * u, join = StrokeJoin.Round))
        // plus, lower right
        val px = 17.4f * u
        val py = 17.4f * u
        val arm = 3.6f * u
        drawLine(Color.White, Offset(px - arm, py), Offset(px + arm, py), 2.1f * u, StrokeCap.Round)
        drawLine(Color.White, Offset(px, py - arm), Offset(px, py + arm), 2.1f * u, StrokeCap.Round)
    }
}
