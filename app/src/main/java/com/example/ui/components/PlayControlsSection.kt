package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolRowSplit
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmHairline
import com.example.ui.theme.IconDisabled
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

private val IconWhite = Color(0xFFF2F2F2)

/**
 * Top control bar, in the settled B/W language. All glyphs are drawn here (no
 * stock icons) so stroke weight and proportions match.
 *
 * Build 201 rebuilt this bar as two rows (audit B-1: the old one-Box layout
 * OVERLAPPED ~546 dp of controls in a 360 dp bar). Build 202 (Sarowar
 * 2026-11-15 ~10:24, correcting the first scoping of his clean-UI order)
 * dissolves the utility row again — differently: the bar is now ONE transport
 * row, and every other control of the middle section lives behind the ONE
 * "More tools" button at the transport row's end:
 *   transport row ([height]): [undo 44][redo 44][resolution 44] · centred
 *     [play 44] · [fullscreen 44][More 44] — left cluster ends at 142.5 dp,
 *     play spans 158–202 dp, right cluster starts at 268 dp (360 dp budget,
 *     proof: ui-redesign/proof-more-tools.png).
 *   the More panel (overlay above the row, [ToolRowSplit.sharedPanelHeightDp]:
 *     112 dp, 225 dp with a clip section): a utility section — IN/OUT, beat
 *     mark, marker, onion, scopes, snap, canvas (the old utility row + the
 *     transport row's snap/grid buttons) — and, while a clip is selected, the
 *     selected clip panel's overflow tools in their own section under a
 *     divider (published through [MorePanelState] by the footer panels).
 * Momentary entries (IN, OUT, beat, marker, canvas) close the panel on tap;
 * toggles (snap, onion, scopes) keep it open so the state change is visible.
 * The More glyph reads white while the panel is open OR any hidden control
 * is active (a range point set, a toggle on, a used clip tool — the screen
 * passes the clip half as [moreActive]).
 *
 * [showEditorControls] false = a chrome-only host (CropView, audit S-3): the
 * More button, its panel, and every control inside it are OMITTED entirely —
 * never rendered inert, never showing a false state.
 *
 * [onPreviousClick] / [onNextClick] are kept so the callers do not change; the reference design has no
 * previous / next buttons.
 */
@Composable
fun PlayControlsSection(
    height: Dp,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onFullScreenClick: () -> Unit,
    onResolutionClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Text inside the outlined pill next to redo ("Full", "1/2", "1/4"). */
    resolutionLabel: String = "Full",
    /** Canvas button (aspect / project format sheet) — in the More panel since build 202. */
    onGridClick: () -> Unit = {},
    /** Beat mark: puts a beat mark at the playhead. In the More panel since build 202. */
    onBeatMarkClick: () -> Unit = {},
    /** Marker: adds a NAMED marker at the playhead (or edits the one already there). In the More panel since build 202. */
    onMarkerClick: () -> Unit = {},
    /** Onion skin (build 190): toggles the ghost frames on the canvas. In the More panel since build 202. */
    onionSkinOn: Boolean = false,
    onOnionSkinClick: () -> Unit = {},
    /** Video scopes (build 196): toggles the scopes panel. In the More panel since build 202. */
    scopesOn: Boolean = false,
    onScopesClick: () -> Unit = {},
    /** Snap toggle (build 199, A3): canvas + transform-pad snapping. White = on. In the More panel since build 202. */
    snapOn: Boolean = true,
    onSnapClick: () -> Unit = {},
    /** Export range points (build 199, A10): IN/OUT set the range point at the playhead (tap again to clear). White = set. In the More panel since build 202. */
    exportInSet: Boolean = false,
    exportOutSet: Boolean = false,
    onExportInClick: () -> Unit = {},
    onExportOutClick: () -> Unit = {},
    /** Editor-only controls (build 201): false for chrome-only hosts (CropView) — they are omitted, not inert. */
    showEditorControls: Boolean = true,
    /** The shared More panel's open state (build 202) — hoisted by the screen. */
    moreExpanded: Boolean = false,
    onMoreExpandedChange: (Boolean) -> Unit = {},
    /** A hidden CLIP tool is in use (the screen's half of the More glyph's summary state). */
    moreActive: Boolean = false,
    /** The selected clip panel's overflow tools, published via [MorePanelState]; null = no clip section. */
    clipOverflow: ClipOverflowSection? = null
) {
    // The utility section's entries, in the old utility row's order with the
    // transport row's snap/canvas appended. Momentary entries close the
    // panel on tap; toggles keep it open (see the header).
    val utilityEntries = listOf(
        PanelEntry("Range in", exportInSet, "export_in_button", "Export range in point", closeOnTap = true, onClick = onExportInClick) {
            Text("IN", color = if (exportInSet) IconWhite else IconDisabled, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        },
        PanelEntry("Range out", exportOutSet, "export_out_button", "Export range out point", closeOnTap = true, onClick = onExportOutClick) {
            Text("OUT", color = if (exportOutSet) IconWhite else IconDisabled, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        },
        PanelEntry("Beat mark", false, "beat_mark_button", "Beat mark", closeOnTap = true, onClick = onBeatMarkClick) { BeatMarkGlyph() },
        PanelEntry("Marker", false, "marker_button", "Marker", closeOnTap = true, onClick = onMarkerClick) { MarkerGlyph() },
        PanelEntry("Onion", onionSkinOn, "onion_skin_button", "Onion skin", closeOnTap = false, onClick = onOnionSkinClick) { OnionGlyph(onionSkinOn) },
        PanelEntry("Scopes", scopesOn, "scopes_button", "Scopes", closeOnTap = false, onClick = onScopesClick) { ScopesGlyph(scopesOn) },
        PanelEntry("Snap", snapOn, "snap_button", "Snap", closeOnTap = false, onClick = onSnapClick) { MagnetGlyph(snapOn) },
        PanelEntry("Canvas", false, "grid_button", "Canvas", closeOnTap = true, onClick = onGridClick) { GridGlyph() }
    )
    // The More glyph's summary state: open, or anything hidden is active.
    val moreGlyphOn = moreExpanded || moreActive || exportInSet || exportOutSet ||
        snapOn || onionSkinOn || scopesOn

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBase)
    ) {
        // ── The shared More panel: an overlay directly above the transport
        // row (over the canvas's bottom edge), so nothing below moves. ──
        if (showEditorControls && moreExpanded) {
            val panelH = ToolRowSplit.sharedPanelHeightDp(clipOverflow?.tools?.size ?: 0)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-panelH).dp)
                    .fillMaxWidth()
                    .height(panelH.dp)
                    .background(AmBase)
                    .drawBehind {
                        drawLine(AmHairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                        drawLine(AmHairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .padding(vertical = ToolRowSplit.PANEL_V_PADDING_DP.dp)
                    .testTag("more_tools_panel")
            ) {
                // Utility section: always all 8 tiles (2 rows), never scrolls.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(ToolRowSplit.GRID_COLUMNS),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ToolRowSplit.gridHeightDp(utilityEntries.size, vPaddingDp = 0).dp)
                ) {
                    items(utilityEntries, key = { it.tag }) { entry ->
                        PanelTile(entry) {
                            entry.onClick()
                            if (entry.closeOnTap) onMoreExpandedChange(false)
                        }
                    }
                }
                // Clip section: the selected panel's overflow tools, in their
                // own section under a divider; 2 rows shown, scrolling past.
                if (clipOverflow != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .height(1.dp)
                            .background(AmHairline)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(ToolRowSplit.GRID_COLUMNS),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
                        verticalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                ToolRowSplit.gridHeightDp(
                                    clipOverflow.tools.size,
                                    vPaddingDp = 0,
                                    maxRows = ToolRowSplit.CLIP_SECTION_MAX_ROWS
                                ).dp
                            )
                    ) {
                        items(clipOverflow.tools, key = { it.id }) { tool ->
                            ToolTile(
                                tool = tool,
                                used = tool.id in clipOverflow.usedTools,
                                onClick = { clipOverflow.onTool(tool) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ToolRowSplit.GRID_TILE_HEIGHT_DP.dp),
                                testTag = clipOverflow.tileTag(tool.id)
                            )
                        }
                    }
                }
            }
        }

        // ── Transport row: undo · redo · resolution | play | fullscreen · More ──
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton44(enabled = canUndo, onClick = onUndoClick, tag = "undo_button", description = "Undo") {
                    UndoGlyph(if (canUndo) IconWhite else IconDisabled, mirrored = false)
                }
                IconButton44(enabled = canRedo, onClick = onRedoClick, tag = "redo_button", description = "Redo") {
                    UndoGlyph(if (canRedo) IconWhite else IconDisabled, mirrored = true)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Preview resolution" }
                        .bouncyClickable(onClick = onResolutionClick)
                        .testTag("preview_resolution_button"),
                    contentAlignment = Alignment.Center
                ) {
                    // BARE (boxless order, build 183): the resolution label with no chip surface.
                    Box(
                        modifier = Modifier
                            .size(width = 26.dp, height = 17.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resolutionLabel,
                            color = TextPrimary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            // ── Centre: play / pause — BARE white glyph (boxless order, build 183;
            // the 44 dp target stays). It spans 158–202 dp at 360 dp wide; the
            // side clusters end at 142.5 / start at 268 — no overlap possible. ──
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
                    .bouncyClickable(pressedScale = 0.88f, onClick = onPlayPauseClick)
                    .testTag("play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                PlayGlyph(isPlaying)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton44(enabled = true, onClick = onFullScreenClick, tag = "fullscreen_button", description = "Full Screen") {
                    FrameGlyph()
                }
                if (showEditorControls) {
                    // THE one More tools button for the whole middle section
                    // (build 202): opens the shared panel above the row.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "More tools" }
                            .bouncyClickable(onClick = { onMoreExpandedChange(!moreExpanded) })
                            .testTag("more_tools_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MoreGlyph(moreGlyphOn)
                    }
                }
            }
        }
    }
}

/** One entry of the shared More panel's utility section. */
private class PanelEntry(
    val label: String,
    val active: Boolean,
    val tag: String,
    val description: String,
    val closeOnTap: Boolean,
    val onClick: () -> Unit,
    val glyph: @Composable () -> Unit
)

/** A utility tile in the shared More panel: the bar's own glyph over an 11 sp label. */
@Composable
private fun PanelTile(entry: PanelEntry, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ToolRowSplit.GRID_TILE_HEIGHT_DP.dp)
            .semantics { contentDescription = entry.description }
            .bouncyClickable(pressedScale = 0.88f, hoverScale = 1.08f, onClick = onClick)
            .testTag(entry.tag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                entry.glyph()
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = entry.label,
                color = if (entry.active) IconWhite else IconDisabled,
                fontSize = 11.sp,
                fontWeight = if (entry.active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IconButton44(
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    description: String,
    content: @Composable () -> Unit
) {
    // The bar's standard small button is now the shared glass disc (GlassComponents).
    GlassIconButton(
        onClick = onClick,
        description = description,
        enabled = enabled,
        tag = tag,
        content = content
    )
}

// ── Glyphs ───────────────────────────────────────────────────────────────

/** Undo arrow (a U-turn with a chevron head). [mirrored] = redo. 24 x 24 dp box. */
@Composable
private fun UndoGlyph(color: Color, mirrored: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        fun x(v: Float) = (if (mirrored) 24f - v else v) * u
        fun y(v: Float) = v * u
        val head = Path().apply {
            moveTo(x(8.4f), y(5.1f)); lineTo(x(3.9f), y(9.0f)); lineTo(x(8.4f), y(13.0f))
        }
        val body = Path().apply {
            moveTo(x(3.9f), y(9.0f))
            lineTo(x(15.2f), y(9.0f))
            // half circle on the right, centre (15.2, 13.95)
            cubicTo(x(18.9f), y(9.0f), x(20.6f), y(11.6f), x(20.6f), y(13.95f))
            cubicTo(x(20.6f), y(16.5f), x(18.7f), y(18.9f), x(15.2f), y(18.9f))
            lineTo(x(10.8f), y(18.9f))
        }
        val st = Stroke(width = 2.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(head, color, style = st)
        drawPath(body, color, style = st)
    }
}

/** Soft rounded play triangle, or two bars while playing. */
@Composable
private fun PlayGlyph(isPlaying: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        if (isPlaying) {
            val bw = 4.4f * u
            val bh = 17f * u
            val top = (size.height - bh) / 2f
            drawRoundRect(Color.White, Offset(size.width / 2f - 6.4f * u, top), Size(bw, bh), CornerRadius(1.8f * u))
            drawRoundRect(Color.White, Offset(size.width / 2f + 2.0f * u, top), Size(bw, bh), CornerRadius(1.8f * u))
        } else {
            // 14.6 x 17.2 dp triangle with rounded corners (fill + round-joined stroke of the same colour)
            val r = 1.6f * u
            val w = 14.6f * u - 2 * r
            val h = 17.2f * u - 2 * r
            val x0 = size.width / 2f - 14.6f * u / 2f + r + 0.6f * u   // optical centre: triangle is heavier on the left
            val y0 = (size.height - h) / 2f
            val p = Path().apply {
                moveTo(x0, y0); lineTo(x0 + w, y0 + h / 2f); lineTo(x0, y0 + h); close()
            }
            drawPath(p, Color.White)
            drawPath(p, Color.White, style = Stroke(width = 2 * r, join = StrokeJoin.Round))
        }
    }
}

/** Four corner nodes joined by a frame, two overlapping rounded layers inside. */
@Composable
private fun FrameGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = Color(0xFFF2F2F2)
        val a = 4.7f * u
        val b = 19.3f * u
        drawLine(c, Offset(a, a), Offset(b, a), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(a, b), Offset(b, b), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(a, a), Offset(a, b), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(b, a), Offset(b, b), 1.4f * u, StrokeCap.Round)
        for (cx in listOf(a, b)) for (cy in listOf(a, b)) drawCircle(c, 1.95f * u, Offset(cx, cy))
        // lower-right layer first, upper-left one on top with a thin black gap
        drawRoundRect(Color(0xFFE9E9E9), Offset(8.7f * u, 12.5f * u), Size(8.4f * u, 4.9f * u), CornerRadius(2.4f * u))
        drawRoundRect(Color.Black, Offset(6.4f * u, 6.3f * u), Size(9.1f * u, 6.1f * u), CornerRadius(3f * u), style = Stroke(1.2f * u))
        drawRoundRect(Color(0xFFEFEFEF), Offset(7.0f * u, 6.9f * u), Size(8.0f * u, 5.0f * u), CornerRadius(2.5f * u))
    }
}

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
        drawRoundRect(c, Offset(left, top), Size(w, h), CornerRadius(4.6f * u), style = Stroke(2.2f * u))
        val sq = 2.3f * u
        val cx0 = size.width / 2f - 1.5f * 3.85f * u
        val cy0 = size.height / 2f - 4.4f * u
        for (r in 0..2) for (k in 0..3) {
            drawRoundRect(
                c,
                Offset(cx0 + k * 3.85f * u - sq / 2f, cy0 + r * 4.4f * u - sq / 2f),
                Size(sq, sq), CornerRadius(0.5f * u)
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
        drawRoundRect(c, Offset(8.6f * u, 3.4f * u), Size(12f * u, 12f * u), CornerRadius(2.6f * u), style = Stroke(1.8f * u))
        if (on) drawRoundRect(c, Offset(3.4f * u, 8.6f * u), Size(12f * u, 12f * u), CornerRadius(2.6f * u))
        else drawRoundRect(c, Offset(3.4f * u, 8.6f * u), Size(12f * u, 12f * u), CornerRadius(2.6f * u), style = Stroke(1.8f * u))
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

/**
 * A 2 x 2 grid of rounded squares: the ONE More tools button (build 202).
 * White while the panel is open or anything hidden is active, grey otherwise —
 * the toggle state law of this bar (the magnet's pattern).
 */
@Composable
private fun MoreGlyph(on: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = if (on) Color(0xFFF2F2F2) else IconDisabled
        val sq = 7.4f * u
        val gap = 3.4f * u
        val x0 = (size.width - (2 * sq + gap)) / 2f
        val y0 = (size.height - (2 * sq + gap)) / 2f
        for (r in 0..1) for (k in 0..1) {
            drawRoundRect(
                c,
                Offset(x0 + k * (sq + gap), y0 + r * (sq + gap)),
                Size(sq, sq), CornerRadius(1.8f * u)
            )
        }
    }
}
