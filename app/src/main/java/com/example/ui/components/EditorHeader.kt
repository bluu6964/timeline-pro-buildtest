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
import androidx.compose.ui.draw.shadow
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
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Text of the resolution pill: the app's REAL current export resolution, e.g. "1080P". */
    resolutionLabel: String = "1080P",
    /**
     * Build 217: the resolution pill's popover lists the app's REAL export
     * resolutions (the stored ExportSettings labels); the current one is
     * the lime-tinted active item. The popover's position and style copy
     * Sarowar's HTML reference; the values are the app's own.
     */
    resolutionOptions: List<String> = emptyList(),
    currentResolution: String = "",
    onResolutionPick: (String) -> Unit = {},
    /** Build 217: the aspect-ratio popover lists the app's REAL canvas ratios; the current one is active. */
    ratioOptions: List<AspectRatio> = emptyList(),
    onRatioPick: (AspectRatio) -> Unit = {}
) {
    // Build 217 (Sarowar's HTML reference, copied exactly): the bar is
    // flat on the app background, padding 8 / 14. Left: ONLY the close
    // button (42 dp, r12, surface fill, white-4% border). Right group
    // (gap 10): the aspect-ratio icon button (42 dp), the resolution
    // pill (42 dp tall, r12, surface), and EXPORT as a 42 dp LIME icon
    // button with the lime glow. No project name, no save-frame button
    // (Save frame lives in the timeline options sheet now).
    val btnShape = RoundedCornerShape(12.dp)
    val btnBorder = Color.White.copy(alpha = 0.04f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBase)
            .padding(horizontal = 14.dp)
            .testTag("editor_top_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Close ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(btnShape)
                .background(com.example.ui.theme.RefSurface)
                .border(1.dp, btnBorder, btnShape)
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

        Spacer(Modifier.weight(1f))

        // ── Right group: aspect ratio · resolution · export ────
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aspect ratio (42 dp icon button + the ratio popover).
            var ratioMenuOpen by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(btnShape)
                        .background(com.example.ui.theme.RefSurface)
                        .border(1.dp, btnBorder, btnShape)
                        .semantics { contentDescription = "Aspect ratio" }
                        .bouncyClickable(pressedScale = 0.92f, onClick = {
                            if (ratioOptions.isNotEmpty()) ratioMenuOpen = true else onAspectRatioClick()
                        })
                        .testTag("header_ratio_button"),
                    contentAlignment = Alignment.Center
                ) {
                    RatioGlyph()
                }
                DropdownMenu(
                    expanded = ratioMenuOpen,
                    onDismissRequest = { ratioMenuOpen = false },
                    modifier = Modifier
                        .background(com.example.ui.theme.RefPopoverBg, RoundedCornerShape(14.dp))
                        .border(1.dp, com.example.ui.theme.RefPopoverBorder, RoundedCornerShape(14.dp))
                ) {
                    ratioOptions.forEach { option ->
                        val isCurrent = option.label == aspectRatio.label
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    color = if (isCurrent) com.example.ui.theme.RefLime else com.example.ui.theme.RefPopoverText,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                )
                            },
                            onClick = {
                                ratioMenuOpen = false
                                onRatioPick(option)
                            },
                            modifier = if (isCurrent) Modifier.background(
                                com.example.ui.theme.RefLime.copy(alpha = 0.14f), RoundedCornerShape(9.dp)
                            ) else Modifier,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Resolution pill (42 dp, r12) + the resolution popover.
            var resMenuOpen by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(btnShape)
                        .background(com.example.ui.theme.RefSurface)
                        .border(1.dp, btnBorder, btnShape)
                        .semantics { contentDescription = "Export resolution" }
                        .bouncyClickable(pressedScale = 0.95f, onClick = { resMenuOpen = true })
                        .testTag("header_resolution_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(resolutionLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Canvas(Modifier.size(10.dp)) {
                            val p = Path().apply {
                                val u = size.width / 12f
                                moveTo(3f * u, 4.5f * u); lineTo(9f * u, 4.5f * u); lineTo(6f * u, 8.5f * u); close()
                            }
                            drawPath(p, TextPrimary)
                        }
                    }
                }
                DropdownMenu(
                    expanded = resMenuOpen,
                    onDismissRequest = { resMenuOpen = false },
                    modifier = Modifier
                        .background(com.example.ui.theme.RefPopoverBg, RoundedCornerShape(14.dp))
                        .border(1.dp, com.example.ui.theme.RefPopoverBorder, RoundedCornerShape(14.dp))
                ) {
                    resolutionOptions.forEach { option ->
                        val isCurrent = option == currentResolution
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.substringBefore(' ').uppercase(),
                                    color = if (isCurrent) com.example.ui.theme.RefLime else com.example.ui.theme.RefPopoverText,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                )
                            },
                            onClick = {
                                resMenuOpen = false
                                onResolutionPick(option)
                            },
                            modifier = if (isCurrent) Modifier.background(
                                com.example.ui.theme.RefLime.copy(alpha = 0.14f), RoundedCornerShape(9.dp)
                            ) else Modifier,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Export: the 42 dp LIME button with its glow (the reference's one lime control up here).
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 7.dp, shape = btnShape, clip = false,
                        ambientColor = com.example.ui.theme.RefLime.copy(alpha = 0.25f),
                        spotColor = com.example.ui.theme.RefLime.copy(alpha = 0.25f)
                    )
                    .clip(btnShape)
                    .background(com.example.ui.theme.RefLime)
                    .semantics { contentDescription = "Export" }
                    .bouncyClickable(pressedScale = 0.94f, onClick = onExportClick)
                    .testTag("header_export_button"),
                contentAlignment = Alignment.Center
            ) {
                DownloadGlyph(com.example.ui.theme.RefLimeInk)
            }
        }
    }
}

/** The reference's aspect-ratio glyph: four frame corners, two dots and the crossed diagonals (21 dp). */
@Composable
private fun RatioGlyph() {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val c = TextPrimary
        val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun corner(points: List<Offset>) {
            val p = Path().apply {
                moveTo(points[0].x * u, points[0].y * u)
                for (pt in points.drop(1)) lineTo(pt.x * u, pt.y * u)
            }
            drawPath(p, c, style = st)
        }
        corner(listOf(Offset(7f, 3f), Offset(5f, 3f), Offset(3f, 5f), Offset(3f, 7f)))
        corner(listOf(Offset(17f, 3f), Offset(19f, 3f), Offset(21f, 5f), Offset(21f, 7f)))
        corner(listOf(Offset(7f, 21f), Offset(5f, 21f), Offset(3f, 19f), Offset(3f, 17f)))
        corner(listOf(Offset(17f, 21f), Offset(19f, 21f), Offset(21f, 19f), Offset(21f, 17f)))
        drawCircle(c, 1.8f * u, Offset(9.5f * u, 14.5f * u), style = st)
        drawCircle(c, 1.8f * u, Offset(14.5f * u, 14.5f * u), style = st)
        drawLine(c, Offset(10.8f * u, 13.2f * u), Offset(15f * u, 8f * u), 1.8f * u, StrokeCap.Round)
        drawLine(c, Offset(13.2f * u, 13.2f * u), Offset(9f * u, 8f * u), 1.8f * u, StrokeCap.Round)
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
 * the reference. Build 210 kept Timeline Pro's old two-line header (name +
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
