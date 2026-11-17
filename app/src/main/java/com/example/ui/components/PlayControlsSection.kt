package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.IconDisabled
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

private val IconWhite = Color(0xFF18181B) // build 216: transport glyphs are ink on white

/**
 * The TRANSPORT BAR (build 214 — UI ARCHITECTURE REDESIGN): zone C's top
 * strip, merged with the ruler row directly below it (the timescale
 * section shares this bar's surface, so the two read as ONE slim bar:
 * transport strip + ruler strip; the ruler carries the time display —
 * the playhead time pill and the total-duration pill).
 *
 * Layout (one row, flat on [AmBarSurface] — the floating pill and the
 * tools disc are gone; the dock below owns tools now):
 *   [prev 40][play 44][next 40]  ·····  [resolution][marker][snap][fullscreen]
 * Undo / redo moved UP to the app bar (zone A) in this build; their
 * parameters are kept for caller compatibility and no longer rendered.
 * The + disc is replaced by the dock's primary toolbar (Add lives there);
 * the tools panel opens from the layer header's ⋯, the dock's More, or
 * automatically on selection, and closes from the panel stage's header.
 *
 * [showEditorControls] false = a chrome-only host (CropView, audit S-3):
 * the editor-only toggles (marker / snap) are OMITTED entirely — never
 * rendered inert.
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
    /** Text inside the outlined pill in the right cluster ("Full", "1/2", "1/4"). */
    resolutionLabel: String = "Full",
    /** Editor-only controls (build 201): false for chrome-only hosts (CropView) — they are omitted, not inert. */
    showEditorControls: Boolean = true,
    /** The tools panel's open state (build 206) — kept for caller compatibility; the disc that used it is gone (build 214). */
    toolsGridExpanded: Boolean = false,
    onToolsGridExpandedChange: (Boolean) -> Unit = {},
    /** Anything inside the tools panel is active — kept for caller compatibility (build 214). */
    toolsGridActive: Boolean = false,
    /** Build 214: the snap toggle lives in the transport bar now (white = on). */
    snapOn: Boolean = false,
    onSnapClick: () -> Unit = {},
    /** Build 214: the named-marker action lives in the transport bar now. */
    onMarkerClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBarSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Skip / play cluster ────────────────────────────────
            IconButton44(enabled = true, onClick = onPreviousClick, tag = "previous_button", description = "Previous") {
                SkipGlyph(mirrored = true)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
                    .bouncyClickable(pressedScale = 0.88f, onClick = onPlayPauseClick)
                    .testTag("play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                PlayGlyph(isPlaying)
            }
            IconButton44(enabled = true, onClick = onNextClick, tag = "next_button", description = "Next") {
                SkipGlyph(mirrored = false)
            }

            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

            // ── Right cluster: resolution · marker · snap · fullscreen ──
            // Build 216: the reference's "Full" badge — a bordered chip;
            // at Full (the reference's shown state) it is black with white
            // text, a reduced preview reads as the plain bordered chip.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "Preview resolution" }
                    .bouncyClickable(onClick = onResolutionClick)
                    .testTag("preview_resolution_button"),
                contentAlignment = Alignment.Center
            ) {
                val full = resolutionLabel == "Full"
                Box(
                    modifier = Modifier
                        .height(27.dp)
                        .then(
                            if (full) Modifier.background(TextPrimary, RoundedCornerShape(9.dp))
                            else Modifier.border(1.dp, Color(0xFFD8D8DC), RoundedCornerShape(9.dp))
                        )
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resolutionLabel,
                        color = if (full) Color.White else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            if (showEditorControls) {
                IconButton44(enabled = true, onClick = onMarkerClick, tag = "marker_button", description = "Marker") {
                    MarkerGlyph()
                }
                GlassIconButton(
                    onClick = onSnapClick,
                    description = "Snap",
                    enabled = true,
                    tag = "snap_button",
                    modifier = if (snapOn) Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(com.example.ui.theme.LimeWash)
                    else Modifier
                ) {
                    MagnetGlyph(snapOn)
                }
            }
            IconButton44(enabled = true, onClick = onFullScreenClick, tag = "fullscreen_button", description = "Full Screen") {
                FrameGlyph()
            }
        }
    }
}

/** Skip triangle + bar (previous / next). [mirrored] = previous. */
@Composable
private fun SkipGlyph(mirrored: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val c = IconWhite
        fun x(v: Float) = (if (mirrored) 24f - v else v) * u
        // bar
        drawLine(c, Offset(x(5f), 5.5f * u), Offset(x(5f), 18.5f * u), 2.4f * u, StrokeCap.Round)
        // triangle
        val p = Path().apply {
            moveTo(x(9f), 6f * u); lineTo(x(19f), 12f * u); lineTo(x(9f), 18f * u); close()
        }
        drawPath(p, c)
        drawPath(p, c, style = Stroke(width = 1.6f * u, join = StrokeJoin.Round))
    }
}

/** A flag on a pole: the named-marker action (same glyph family as the tools panel's). */
@Composable
private fun MarkerGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val c = IconWhite
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

/** U-magnet: the snap toggle. White = snapping on, grey = off. */
@Composable
private fun MagnetGlyph(on: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val c = if (on) IconWhite else IconDisabled
        val body = Path().apply {
            moveTo(6.6f * u, 4.5f * u)
            lineTo(6.6f * u, 12.5f * u)
            cubicTo(6.6f * u, 17.2f * u, 9.4f * u, 19.6f * u, 12f * u, 19.6f * u)
            cubicTo(14.6f * u, 19.6f * u, 17.4f * u, 17.2f * u, 17.4f * u, 12.5f * u)
            lineTo(17.4f * u, 4.5f * u)
        }
        drawPath(body, c, style = Stroke(width = 2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(c, Offset(4.6f * u, 4.5f * u), Offset(8.6f * u, 4.5f * u), 2.6f * u, StrokeCap.Round)
        drawLine(c, Offset(15.4f * u, 4.5f * u), Offset(19.4f * u, 4.5f * u), 2.6f * u, StrokeCap.Round)
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
    // The bar's standard small button is the shared glass disc (GlassComponents).
    GlassIconButton(
        onClick = onClick,
        description = description,
        enabled = enabled,
        tag = tag,
        content = content
    )
}

// ── Glyphs ───────────────────────────────────────────────────────────────

/** Soft rounded play triangle, or two bars while playing. */
@Composable
private fun PlayGlyph(isPlaying: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        if (isPlaying) {
            val bw = 4.4f * u
            val bh = 17f * u
            val top = (size.height - bh) / 2f
            drawRoundRect(IconWhite, Offset(size.width / 2f - 6.4f * u, top), Size(bw, bh), CornerRadius(1.8f * u))
            drawRoundRect(IconWhite, Offset(size.width / 2f + 2.0f * u, top), Size(bw, bh), CornerRadius(1.8f * u))
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
            drawPath(p, IconWhite)
            drawPath(p, IconWhite, style = Stroke(width = 2 * r, join = StrokeJoin.Round))
        }
    }
}

/** Four corner nodes joined by a frame, two overlapping rounded layers inside. */
@Composable
private fun FrameGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = IconWhite
        val a = 4.7f * u
        val b = 19.3f * u
        drawLine(c, Offset(a, a), Offset(b, a), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(a, b), Offset(b, b), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(a, a), Offset(a, b), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(b, a), Offset(b, b), 1.4f * u, StrokeCap.Round)
        for (cx in listOf(a, b)) for (cy in listOf(a, b)) drawCircle(c, 1.95f * u, Offset(cx, cy))
        // lower-right layer first, upper-left one on top with a thin black gap
        drawRoundRect(Color(0xFFD8D8DC), Offset(8.7f * u, 12.5f * u), Size(8.4f * u, 4.9f * u), CornerRadius(2.4f * u))
        drawRoundRect(Color.Black, Offset(6.4f * u, 6.3f * u), Size(9.1f * u, 6.1f * u), CornerRadius(3f * u), style = Stroke(1.2f * u))
        drawRoundRect(Color.White, Offset(7.0f * u, 6.9f * u), Size(8.0f * u, 5.0f * u), CornerRadius(2.5f * u))
    }
}

/**
 * Build 216 — the WORKSPACE CONTROLS row (Sarowar's HTML reference): a
 * slim white row between the preview stage and the transport bar holding
 * the two view toggles the reference puts at the left of its playback
 * row — the thirds GRID overlay on the stage and the transform-box
 * (selection frame) visibility. Both are view-only editor state: the
 * grid draws over the stage, the transform toggle hides the selection
 * frame's drawing (every gesture stays exactly as it was). Active =
 * light-lime wash (the reference's active toggle state). The reference's
 * preset pill is NOT here: the app's template entry lives in the Add
 * sheet, not in this area, so per the brief's own rule it is skipped
 * rather than invented.
 */
@Composable
fun WorkspaceControlsRow(
    gridOn: Boolean,
    onGridClick: () -> Unit,
    transformBoxOn: Boolean,
    onTransformBoxClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Build 216: undo / redo live at this row's right end (they left the app bar — no room at 360 dp). */
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(AmBarSurface)
            .padding(horizontal = 6.dp)
            .testTag("workspace_controls_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkspaceToggle(
            on = gridOn, onClick = onGridClick, tag = "workspace_grid_toggle", description = "Grid"
        ) {
            Canvas(Modifier.size(20.dp)) {
                val c = IconWhite
                val w = 1.6.dp.toPx()
                val third = size.width / 3f
                drawRect(c, style = Stroke(w))
                drawLine(c, Offset(third, 0f), Offset(third, size.height), w)
                drawLine(c, Offset(third * 2, 0f), Offset(third * 2, size.height), w)
                drawLine(c, Offset(0f, third), Offset(size.width, third), w)
                drawLine(c, Offset(0f, third * 2), Offset(size.width, third * 2), w)
            }
        }
        WorkspaceToggle(
            on = transformBoxOn, onClick = onTransformBoxClick, tag = "workspace_transform_toggle", description = "Transform box"
        ) {
            Canvas(Modifier.size(20.dp)) {
                val c = IconWhite
                val w = 1.6.dp.toPx()
                val inset = 2.dp.toPx()
                drawRect(
                    c,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(w)
                )
                val h = 4.5.dp.toPx()
                for ((cx, cy) in listOf(
                    inset to inset, size.width - inset to inset,
                    inset to size.height - inset, size.width - inset to size.height - inset
                )) {
                    drawRect(
                        IconWhite,
                        topLeft = Offset(cx - h / 2, cy - h / 2),
                        size = Size(h, h)
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        WorkspaceHistoryButton(
            enabled = canUndo, onClick = onUndoClick, tag = "workspace_undo_button", description = "Undo"
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo, contentDescription = null,
                tint = if (canUndo) IconWhite else IconDisabled,
                modifier = Modifier.size(19.dp)
            )
        }
        WorkspaceHistoryButton(
            enabled = canRedo, onClick = onRedoClick, tag = "workspace_redo_button", description = "Redo"
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Redo, contentDescription = null,
                tint = if (canRedo) IconWhite else IconDisabled,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/** One undo / redo button of the workspace row (40 dp target, bare glyph). */
@Composable
private fun WorkspaceHistoryButton(
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    description: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = description }
            .bouncyClickable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** One 40 dp workspace toggle: plain ink glyph; the light-lime wash marks it on. */
@Composable
private fun WorkspaceToggle(
    on: Boolean,
    onClick: () -> Unit,
    tag: String,
    description: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (on) Modifier.background(com.example.ui.theme.LimeWash) else Modifier)
            .semantics { contentDescription = description }
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}
