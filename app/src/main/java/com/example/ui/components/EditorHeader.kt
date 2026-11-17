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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmSlateHeader
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
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
    // Build 216: undo / redo moved DOWN to the workspace controls row —
    // at 360 dp the app bar (close, name, save-frame, resolution, export)
    // has no room for them, and the reference's bar carries none.
    /**
     * Build 216: the resolution pill's popover — the app's REAL export
     * resolutions (labels as stored in ExportSettings). Non-empty = the
     * pill opens this popover instead of [onResolutionClick]; the current
     * value (the raw stored label) gets the ✓. The mock's 2K / 4K are NOT
     * offered: the exporter caps at 1080p (ExportMaxResolution).
     */
    resolutionOptions: List<String> = emptyList(),
    currentResolution: String = "",
    onResolutionPick: (String) -> Unit = {}
) {
    // Build 216 (Sarowar's HTML reference): the bar is FLAT on white —
    // the glass pill is gone. Controls are 44 dp pill-surface buttons
    // (r13); the slot is ~44 dp, so a button is the slot height minus
    // breathing room, floored at the 40 dp touch minimum.
    val btnH = (height - 4.dp).coerceIn(40.dp, 44.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBase)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Close ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(btnH)
                        .clip(RoundedCornerShape(13.dp))
                        .background(AmPanelHigh)
                        .semantics { contentDescription = "Back to projects" }
                        .bouncyClickable(pressedScale = 0.92f, onClick = onBackClick)
                        .testTag("header_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(15.dp)) {
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
                        .padding(start = 10.dp, end = 4.dp)
                        .testTag("header_project_name")
                )

                // ── Save frame: current frame -> PNG in the gallery ──
                Box(
                    modifier = Modifier
                        .size(btnH)
                        .clip(RoundedCornerShape(13.dp))
                        .background(AmPanelHigh)
                        .semantics { contentDescription = "Save frame as picture" }
                        .bouncyClickable(onClick = onSaveFrameClick)
                        .testTag("header_save_frame_button"),
                    contentAlignment = Alignment.Center
                ) {
                    SaveFrameGlyph()
                }

                // ── Resolution pill (build 216: pill surface, r13, opens
                // the export-resolution popover) ──────────────────────
                var resMenuOpen by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .height(btnH)
                            .clip(RoundedCornerShape(13.dp))
                            .background(AmPanelHigh)
                            .semantics { contentDescription = "Export resolution" }
                            .bouncyClickable(pressedScale = 0.95f, onClick = {
                                if (resolutionOptions.isNotEmpty()) resMenuOpen = true else onResolutionClick()
                            })
                            .testTag("header_resolution_pill"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(resolutionLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.width(5.dp))
                            Canvas(Modifier.size(width = 8.dp, height = 5.5.dp)) {
                                val p = Path().apply {
                                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
                                }
                                drawPath(p, TextPrimary)
                                drawPath(p, TextPrimary, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = resMenuOpen,
                        onDismissRequest = { resMenuOpen = false },
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(14.dp))
                    ) {
                        resolutionOptions.forEach { option ->
                            val isCurrent = option == currentResolution
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.substringBefore(' ').uppercase(),
                                        color = TextPrimary, fontSize = 13.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                trailingIcon = if (isCurrent) {
                                    { Text("✓", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                } else null,
                                onClick = {
                                    resMenuOpen = false
                                    onResolutionPick(option)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // ── Export (build 216): the lime icon button, 44 dp, r13,
                // dark glyph — the reference's one lime control up here. ──
                Box(
                    modifier = Modifier
                        .size(btnH)
                        .clip(RoundedCornerShape(13.dp))
                        .background(AmAccentViolet)
                        .semantics { contentDescription = "Export" }
                        .bouncyClickable(pressedScale = 0.94f, onClick = onExportClick)
                        .testTag("header_export_button"),
                    contentAlignment = Alignment.Center
                ) {
                    DownloadGlyph(AmInk)
                }
            }
        }
}

/** Camera (body, lens, top bump) - white, 28 dp: saves the current frame as a picture. */
@Composable
private fun SaveFrameGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
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

/**
 * Build 216 — the CONTEXTUAL BAR (Sarowar's HTML reference): shown in the
 * lower zone directly above the dock while ONE layer is selected. White,
 * hairline on top: "Layer" + the layer's name on the left (tap = rename,
 * the build-211 header's rename affordance, kept reachable), then the
 * text actions Edit / Duplicate / Delete and a black Done pill
 * (deselect). Every action wires to the SAME callback the build-211/214
 * layer header used. It replaces that slate header in the header slot —
 * the app bar above now stays the project bar in every state.
 */
@Composable
fun LayerContextBar(
    name: String,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AmBase)
            .testTag("layer_context_bar")
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(com.example.ui.theme.AmHairline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(47.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Rename layer" }
                    .bouncyClickable(pressedScale = 0.98f, onClick = onRename)
                    .testTag("context_bar_name"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layer", color = AmTextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Text(
                    name, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            ContextBarTextButton("Edit", onEdit, "context_bar_edit")
            ContextBarTextButton("Duplicate", onDuplicate, "context_bar_duplicate")
            ContextBarTextButton("Delete", onDelete, "context_bar_delete", danger = true)
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
                    .background(TextPrimary)
                    .semantics { contentDescription = "Done" }
                    .bouncyClickable(pressedScale = 0.94f, onClick = onDone)
                    .testTag("context_bar_done"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Done", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** One plain text action of the contextual bar (13.5 sp medium ink; Delete carries the danger red). */
@Composable
private fun ContextBarTextButton(
    label: String,
    onClick: () -> Unit,
    tag: String,
    danger: Boolean = false
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .semantics { contentDescription = label }
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (danger) com.example.ui.theme.AmDanger else TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp)
        )
    }
}
