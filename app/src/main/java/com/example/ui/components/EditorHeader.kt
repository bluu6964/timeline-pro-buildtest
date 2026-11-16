package com.example.ui.components
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatio
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCyan
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmSlateHeader
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.amBarBrush
import com.example.ui.util.bouncyClickable

/**
 * The APP BAR (build 214 — UI ARCHITECTURE REDESIGN): zone A of the editor
 * shell. One bar holding, left to right: the close X, the PROJECT NAME,
 * undo / redo (moved up from the transport bar — the app bar owns them
 * now), the save-frame icon, the resolution pill, and EXPORT as the
 * primary filled button (brand red, white content — the architecture's
 * one primary action). [aspectRatio] is kept for the callers; the ratio
 * itself is chosen in the sheet the resolution pill opens.
 */
@Composable
fun EditorHeader(
    height: Dp,
    aspectRatio: AspectRatio,
    onBackClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    /** The icon next to the close button: saves the current frame as a PNG. */
    onSaveFrameClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Text of the resolution pill, e.g. "1080P". */
    resolutionLabel: String = "1080P",
    onResolutionClick: () -> Unit = onAspectRatioClick,
    /** Build 214: the open project's name, shown at the bar's left. */
    projectName: String = "",
    /** Build 214: undo / redo live in the app bar now (they left the transport bar). */
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {}
) {
    val barH = (height - 4.dp).coerceAtMost(46.dp)
    val btnH = barH - 10.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBase)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barH)
                .glassSurface(CircleShape, fill = amBarBrush())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barH)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Close ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(btnH)
                        .semantics { contentDescription = "Back to projects" }
                        .bouncyClickable(pressedScale = 0.92f, onClick = onBackClick)
                        .testTag("header_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        val c = TextPrimary
                        val w = 2.7.dp.toPx()
                        val m = w / 2f
                        drawLine(c, Offset(m, m), Offset(size.width - m, size.height - m), w, StrokeCap.Round)
                        drawLine(c, Offset(size.width - m, m), Offset(m, size.height - m), w, StrokeCap.Round)
                    }
                }

                // ── Project name (the app bar's identity, left) ────
                Text(
                    projectName,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                        .testTag("header_project_name")
                )

                // ── Undo / redo (the app bar owns them, build 214) ──
                HeaderIconButton(
                    enabled = canUndo,
                    onClick = onUndoClick,
                    tag = "header_undo_button",
                    description = "Undo"
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo, contentDescription = null,
                        tint = if (canUndo) TextPrimary else AmTextDim.copy(alpha = 0.45f),
                        modifier = Modifier.size(19.dp)
                    )
                }
                HeaderIconButton(
                    enabled = canRedo,
                    onClick = onRedoClick,
                    tag = "header_redo_button",
                    description = "Redo"
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo, contentDescription = null,
                        tint = if (canRedo) TextPrimary else AmTextDim.copy(alpha = 0.45f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // ── Save frame: current frame -> PNG in the gallery ──
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(btnH)
                        .semantics { contentDescription = "Save frame as picture" }
                        .bouncyClickable(onClick = onSaveFrameClick)
                        .testTag("header_save_frame_button"),
                    contentAlignment = Alignment.Center
                ) {
                    SaveFrameGlyph()
                }

                // ── Resolution pill ────────────────────────────────
                Box(
                    modifier = Modifier
                        .height(btnH - 4.dp)
                        .semantics { contentDescription = "Preview resolution" }
                        .bouncyClickable(pressedScale = 0.95f, onClick = onResolutionClick)
                        .testTag("header_resolution_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(resolutionLabel, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Canvas(Modifier.size(width = 8.dp, height = 5.5.dp)) {
                            val p = Path().apply {
                                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
                            }
                            drawPath(p, TextPrimary)
                            drawPath(p, TextPrimary, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // ── Export: THE primary filled button (brand red) ──
                Box(
                    modifier = Modifier
                        .height(btnH)
                        .clip(CircleShape)
                        .background(AmAccentViolet)
                        .semantics { contentDescription = "Export" }
                        .bouncyClickable(pressedScale = 0.94f, onClick = onExportClick)
                        .testTag("header_export_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DownloadGlyph(Color.White)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(1.dp).height(13.dp).background(Color.White.copy(alpha = 0.35f)))
                        Spacer(Modifier.width(8.dp))
                        Text("Export", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One small icon button of the app bar (undo / redo). */
@Composable
private fun HeaderIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    description: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(36.dp)
            .semantics { contentDescription = description }
            .bouncyClickable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Camera (body, lens, top bump) - white, 28 dp: saves the current frame as a picture. */
@Composable
private fun SaveFrameGlyph() {
    Canvas(Modifier.size(28.dp)) {
        val u = size.width / 28f
        val c = AmCyan
        val st = Stroke(2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(3.5f * u, 9.5f * u)
            lineTo(8.2f * u, 9.5f * u); lineTo(10.2f * u, 6.2f * u); lineTo(17.8f * u, 6.2f * u); lineTo(19.8f * u, 9.5f * u)
            lineTo(24.5f * u, 9.5f * u)
            quadraticBezierTo(26f * u, 9.5f * u, 26f * u, 11f * u)
            lineTo(26f * u, 21.5f * u)
            quadraticBezierTo(26f * u, 23f * u, 24.5f * u, 23f * u)
            lineTo(3.5f * u, 23f * u)
            quadraticBezierTo(2f * u, 23f * u, 2f * u, 21.5f * u)
            lineTo(2f * u, 11f * u)
            quadraticBezierTo(2f * u, 9.5f * u, 3.5f * u, 9.5f * u)
            close()
        }
        drawPath(body, c, style = st)
        drawCircle(c, 4.6f * u, Offset(14f * u, 16.3f * u), style = st)
    }
}

/** Arrow down into a tray, 19 dp wide (build 214: drawn in the Export button's content colour). */
@Composable
private fun DownloadGlyph(tint: Color = AmInk) {
    Canvas(Modifier.size(20.dp)) {
        val u = size.width / 20f
        val st = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arrow = Path().apply {
            moveTo(10f * u, 1.6f * u); lineTo(10f * u, 11.8f * u)
            moveTo(5.6f * u, 7.6f * u); lineTo(10f * u, 12f * u); lineTo(14.4f * u, 7.6f * u)
        }
        drawPath(arrow, tint, style = st)
        val tray = Path().apply {
            moveTo(1.6f * u, 12.5f * u); lineTo(1.6f * u, 16.2f * u)
            quadraticBezierTo(1.6f * u, 18.4f * u, 3.8f * u, 18.4f * u)
            lineTo(16.2f * u, 18.4f * u)
            quadraticBezierTo(18.4f * u, 18.4f * u, 18.4f * u, 16.2f * u)
            lineTo(18.4f * u, 12.5f * u)
        }
        drawPath(tray, tint, style = st)
    }
}

/**
 * Header shown while a layer is selected (it replaces [EditorHeader]).
 *
 * Build 211 (Sarowar, with his Alight Motion reference): REDESIGNED to
 * the reference. Build 210 kept MaxCut's old two-line header (name +
 * "Video · 0:00–0:56" subtitle + colour dot + eye) and only added
 * buttons — rejected. This is a single-row dark SLATE bar
 * ([AmSlateHeader] — a slate grey-violet distinct from the editor
 * black) filling the header slot edge to edge, holding ONLY, left to
 * right: a back arrow (deselect); the layer's NAME alone — large
 * white, single line, ellipsised, with the reference's thin underline
 * beneath it (it reads as the editable field it is: tap = the rename
 * dialog, as before); then duplicate (overlapping squares — the
 * existing duplicate), delete (trash — the existing delete) and the
 * circled three-dots (the tools-panel toggle, white ring while open,
 * as in build 210). NO subtitle / type-duration line, NO colour dot,
 * NO eye: the visibility toggle is NOT lost — it lives on the
 * timeline's lane control (the eye on each layer's row), where it has
 * always been. The multi-select header is the same bar with back +
 * the count only (no underline, no icons — the count is not a field).
 * Same slot height as [EditorHeader] — the canvas never moves
 * (CANVAS LAW).
 */
@Composable
fun LayerHeader(
    height: Dp,
    name: String,
    onBack: () -> Unit,
    /** Name tap: rename the layer. Null = the name is not a rename affordance (multi-select header). */
    onRename: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Duplicate button (overlapping squares): duplicates the layer in place. */
    onDuplicate: (() -> Unit)? = null,
    /** Delete button (trash): deletes the layer (the caller's action carries its undo semantics). */
    onDelete: (() -> Unit)? = null,
    /** Circled three-dots button: toggles the selected layer's tools panel. */
    onToolsClick: (() -> Unit)? = null,
    /** Whether the tools panel is currently open (the dots button's ring state). */
    toolsPanelOpen: Boolean = false
) {
    // Reference icon zones are ~44 dp; never taller than the slot itself.
    val zone = minOf(44.dp, height)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmSlateHeader)
            .testTag("layer_header"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back: leave the layer (deselect) — the editor's own chevron.
            Box(
                modifier = Modifier
                    .width(zone)
                    .height(zone)
                    .semantics { contentDescription = "Back to layers" }
                    .bouncyClickable(pressedScale = 0.92f, onClick = onBack)
                    .testTag("layer_header_back"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val w = 2.7.dp.toPx()
                    val m = w / 2f
                    drawLine(TextPrimary, Offset(size.width - m, m), Offset(m, size.height / 2f), w, StrokeCap.Round)
                    drawLine(TextPrimary, Offset(m, size.height / 2f), Offset(size.width - m, size.height - m), w, StrokeCap.Round)
                }
            }
            // The name field: the name alone, large and white, with the
            // reference's underline beneath it. Tapping it renames (when
            // a rename action exists — the multi-select count is inert
            // and carries no underline: it is not a field).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 10.dp)
                    .then(
                        if (onRename != null) Modifier
                            .semantics { contentDescription = "Rename layer" }
                            .bouncyClickable(pressedScale = 0.98f, onClick = onRename)
                        else Modifier
                    )
                    .testTag("layer_header_name")
            ) {
                Text(
                    name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (onRename != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .height(1.dp)
                            .background(TextPrimary.copy(alpha = 0.45f))
                    )
                }
            }
            // The reference's right-side actions, in its order:
            // duplicate (overlapping squares), delete (trash), circled
            // three-dots (the tools panel toggle).
            if (onDuplicate != null) {
                Box(
                    modifier = Modifier
                        .width(zone)
                        .height(zone)
                        .semantics { contentDescription = "Duplicate layer" }
                        .bouncyClickable(pressedScale = 0.92f, onClick = onDuplicate)
                        .testTag("layer_header_duplicate"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .width(zone)
                        .height(zone)
                        .semantics { contentDescription = "Delete layer" }
                        .bouncyClickable(pressedScale = 0.92f, onClick = onDelete)
                        .testTag("layer_header_delete"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(21.dp))
                }
            }
            if (onToolsClick != null) {
                Box(
                    modifier = Modifier
                        .width(zone)
                        .height(zone)
                        .semantics { contentDescription = if (toolsPanelOpen) "Close layer tools" else "Layer tools" }
                        .bouncyClickable(pressedScale = 0.92f, onClick = onToolsClick)
                        .testTag("layer_header_tools"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(1.4.dp, if (toolsPanelOpen) TextPrimary else TextPrimary.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz, contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
