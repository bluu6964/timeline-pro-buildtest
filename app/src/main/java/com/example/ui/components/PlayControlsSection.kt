package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmBase
import com.example.ui.theme.RefCtrl
import com.example.ui.theme.RefCtrlDisabled
import com.example.ui.theme.RefLime
import com.example.ui.theme.RefOrange
import com.example.ui.util.bouncyClickable

/**
 * THE CONTROLS BAR (build 217 — Sarowar's HTML reference, copied exactly):
 * one slim row on the app background, padding 6 / 16, a #1A1A20 hairline
 * under it. LEFT group (gap 14): Undo, Redo, the "Full" outline badge.
 * CENTRE: Play / Pause. RIGHT group (gap 14): the snap toggle, the grid
 * toggle, and Add-Keyframe with its orange dot. Ctrl glyphs are #E4E4E7,
 * an active toggle turns LIME, a disabled one #45454D — except the snap
 * magnet, which follows Sarowar's own icon language: WHITE = on, GREY = off.
 *
 * Jobs (mapped to the app's real actions): the Full badge opens the
 * fullscreen player; Play toggles playback; the magnet flips snapping
 * (build 222 — it took the transform-box toggle's slot at Sarowar's
 * order; the selection-frame toggle lives in the timeline options
 * sheet's Frame tile now); the grid toggle drives the canvas thirds
 * grid; the shield adds a transform keyframe at the playhead for the
 * selected layer. Previous / next frame live in the timeline options
 * sheet behind the ruler's layer-count pill (the named Marker tool was
 * removed entirely in build 222). The preview-resolution cycle (the
 * build-216 "Full" chip's old job) lives there too, as the Preview tile.
 *
 * [showEditorControls] false = a chrome-only host (CropView, audit S-3):
 * the editor-only right group is omitted and the host's own wired
 * controls (previous / next / fullscreen, the resolution badge) render
 * in the same language instead — nothing the host wires is dropped.
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
    /** Text inside the outlined badge ("Full" in the editor; the preview-resolution label in chrome hosts). */
    resolutionLabel: String = "Full",
    /** Editor-only controls: false for chrome-only hosts (CropView) — they are omitted, not inert. */
    showEditorControls: Boolean = true,
    /** Kept for caller compatibility (build 214+): the tools disc that used these is gone. */
    toolsGridExpanded: Boolean = false,
    onToolsGridExpandedChange: (Boolean) -> Unit = {},
    toolsGridActive: Boolean = false,
    /**
     * Build 222: the snap toggle — the controls bar's first right-group
     * button (the transform-box toggle's old slot). White magnet = on,
     * grey = off.
     */
    snapOn: Boolean = false,
    onSnapClick: () -> Unit = {},
    /** Build 217: the canvas grid toggle of the controls bar. */
    gridOn: Boolean = false,
    onGridClick: () -> Unit = {},
    /** Build 217: add a transform keyframe at the playhead (selected layer). */
    onAddKeyframeClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBase)
            .testTag("controls_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left group: undo · redo · Full badge ──────────────
            CtrlButton(
                enabled = canUndo, onClick = onUndoClick,
                tag = "workspace_undo_button", description = "Undo"
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo, contentDescription = null,
                    tint = if (canUndo) RefCtrl else RefCtrlDisabled,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            CtrlButton(
                enabled = canRedo, onClick = onRedoClick,
                tag = "workspace_redo_button", description = "Redo"
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo, contentDescription = null,
                    tint = if (canRedo) RefCtrl else RefCtrlDisabled,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            // The "Full" outline badge (1.8 dp border, r6, 11 sp / 800):
            // in the editor it is the reference's fullscreen button; a
            // chrome host wires it to its preview-resolution cycle and
            // labels it with the real resolution state.
            CtrlButton(
                enabled = true,
                onClick = if (showEditorControls) onFullScreenClick else onResolutionClick,
                tag = if (showEditorControls) "fullscreen_button" else "preview_resolution_button",
                description = if (showEditorControls) "Full Screen" else "Preview resolution"
            ) {
                Box(
                    modifier = Modifier
                        .border(1.8.dp, RefCtrl, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showEditorControls) "Full" else resolutionLabel,
                        color = RefCtrl,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Centre: play / pause ──────────────────────────────
            CtrlButton(
                enabled = true, onClick = onPlayPauseClick,
                tag = "play_pause_button", description = if (isPlaying) "Pause" else "Play"
            ) {
                PlayGlyph(isPlaying)
            }

            Spacer(Modifier.weight(1f))

            if (showEditorControls) {
                // ── Right group: snap · grid · add-keyframe ──
                CtrlButton(
                    enabled = true, onClick = onSnapClick, active = snapOn,
                    tag = "workspace_snap_toggle", description = "Snap"
                ) {
                    MagnetGlyph(snapOn)
                }
                Spacer(Modifier.size(14.dp))
                CtrlButton(
                    enabled = true, onClick = onGridClick, active = gridOn,
                    tag = "workspace_grid_toggle", description = "Grid"
                ) {
                    GridToggleGlyph(gridOn)
                }
                Spacer(Modifier.size(14.dp))
                CtrlButton(
                    enabled = true, onClick = onAddKeyframeClick,
                    tag = "add_keyframe_button", description = "Add Keyframe"
                ) {
                    Box {
                        KeyframeShieldGlyph()
                        // The reference's orange dot (7 dp) at the icon's bottom-right.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(RefOrange)
                                .border(1.5.dp, AmBase, CircleShape)
                        )
                    }
                }
            } else {
                // ── Chrome host (CropView): its own wired controls ─
                CtrlButton(
                    enabled = true, onClick = onPreviousClick,
                    tag = "previous_button", description = "Previous"
                ) {
                    SkipGlyph(mirrored = true)
                }
                Spacer(Modifier.size(14.dp))
                CtrlButton(
                    enabled = true, onClick = onNextClick,
                    tag = "next_button", description = "Next"
                ) {
                    SkipGlyph(mirrored = false)
                }
                Spacer(Modifier.size(14.dp))
                CtrlButton(
                    enabled = true, onClick = onFullScreenClick,
                    tag = "fullscreen_button", description = "Full Screen"
                ) {
                    FrameGlyph()
                }
            }
        }
        // The bar's bottom hairline (#1A1A20 in the reference).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1A1A20))
        )
    }
}

/** One controls-bar button: a 34 dp touch box around a 21–24 dp glyph. */
@Composable
private fun CtrlButton(
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    description: String,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
            .bouncyClickable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ── Glyphs ───────────────────────────────────────────────────────────────

/** Soft rounded play triangle, or two bars while playing (24 dp, controls colour). */
@Composable
private fun PlayGlyph(isPlaying: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = RefCtrl
        if (isPlaying) {
            val bw = 4.4f * u
            val bh = 17f * u
            val top = (size.height - bh) / 2f
            drawRoundRect(c, Offset(size.width / 2f - 6.4f * u, top), Size(bw, bh), CornerRadius(1.8f * u))
            drawRoundRect(c, Offset(size.width / 2f + 2.0f * u, top), Size(bw, bh), CornerRadius(1.8f * u))
        } else {
            val r = 1.6f * u
            val w = 14.6f * u - 2 * r
            val h = 17.2f * u - 2 * r
            val x0 = size.width / 2f - 14.6f * u / 2f + r + 0.6f * u
            val y0 = (size.height - h) / 2f
            val p = Path().apply {
                moveTo(x0, y0); lineTo(x0 + w, y0 + h / 2f); lineTo(x0, y0 + h); close()
            }
            drawPath(p, c)
            drawPath(p, c, style = Stroke(width = 2 * r, join = StrokeJoin.Round))
        }
    }
}

/** Skip triangle + bar (previous / next) — chrome hosts only. */
@Composable
private fun SkipGlyph(mirrored: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val c = RefCtrl
        fun x(v: Float) = (if (mirrored) 24f - v else v) * u
        drawLine(c, Offset(x(5f), 5.5f * u), Offset(x(5f), 18.5f * u), 2.4f * u, StrokeCap.Round)
        val p = Path().apply {
            moveTo(x(9f), 6f * u); lineTo(x(19f), 12f * u); lineTo(x(9f), 18f * u); close()
        }
        drawPath(p, c)
        drawPath(p, c, style = Stroke(width = 1.6f * u, join = StrokeJoin.Round))
    }
}

/** Four corners + two overlapping layers — the fullscreen glyph (chrome hosts). */
@Composable
private fun FrameGlyph() {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val c = RefCtrl
        val a = 4.7f * u
        val b = 19.3f * u
        drawLine(c, Offset(a, a), Offset(b, a), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(a, b), Offset(b, b), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(a, a), Offset(a, b), 1.4f * u, StrokeCap.Round)
        drawLine(c, Offset(b, a), Offset(b, b), 1.4f * u, StrokeCap.Round)
        for (cx in listOf(a, b)) for (cy in listOf(a, b)) drawCircle(c, 1.95f * u, Offset(cx, cy))
        drawRoundRect(Color(0xFFA1A1AA), Offset(8.7f * u, 12.5f * u), Size(8.4f * u, 4.9f * u), CornerRadius(2.4f * u))
        drawRoundRect(AmBase, Offset(6.4f * u, 6.3f * u), Size(9.1f * u, 6.1f * u), CornerRadius(3f * u), style = Stroke(1.2f * u))
        drawRoundRect(c, Offset(7.0f * u, 6.9f * u), Size(8.0f * u, 5.0f * u), CornerRadius(2.5f * u))
    }
}

/** The snap magnet (build 222): a horseshoe magnet, WHITE while on, GREY while off (21 dp). */
@Composable
private fun MagnetGlyph(on: Boolean) {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val c = if (on) RefCtrl else RefCtrlDisabled
        val w = 3.6f * u
        // The U: a top semicircle joining two legs.
        drawArc(
            color = c,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(5.2f * u, 3.4f * u),
            size = Size(13.6f * u, 13.6f * u),
            style = Stroke(width = w, cap = StrokeCap.Butt)
        )
        drawLine(c, Offset(5.2f * u, 10.2f * u), Offset(5.2f * u, 19.6f * u), w, StrokeCap.Butt)
        drawLine(c, Offset(18.8f * u, 10.2f * u), Offset(18.8f * u, 19.6f * u), w, StrokeCap.Butt)
        // Pole tips: the legs' last stretch reads brighter while on.
        val tip = if (on) Color.White else c
        drawLine(tip, Offset(5.2f * u, 16.4f * u), Offset(5.2f * u, 19.6f * u), w, StrokeCap.Butt)
        drawLine(tip, Offset(18.8f * u, 16.4f * u), Offset(18.8f * u, 19.6f * u), w, StrokeCap.Butt)
    }
}

/** The reference's grid glyph: a rounded square divided in thirds both ways (21 dp). Lime while on. */
@Composable
private fun GridToggleGlyph(on: Boolean) {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val c = if (on) RefLime else RefCtrl
        drawRoundRect(
            c, Offset(4f * u, 4f * u), Size(16f * u, 16f * u),
            CornerRadius(3f * u), style = Stroke(1.8f * u, join = StrokeJoin.Round)
        )
        drawLine(c, Offset(9f * u, 4f * u), Offset(9f * u, 20f * u), 1.5f * u)
        drawLine(c, Offset(15f * u, 4f * u), Offset(15f * u, 20f * u), 1.5f * u)
        drawLine(c, Offset(4f * u, 9f * u), Offset(20f * u, 9f * u), 1.5f * u)
        drawLine(c, Offset(4f * u, 15f * u), Offset(20f * u, 15f * u), 1.5f * u)
    }
}

/** The reference's Add-Keyframe glyph: a filled shield with a plus cut out of it (21 dp). */
@Composable
private fun KeyframeShieldGlyph() {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val shield = Path().apply {
            moveTo(12f * u, 2.5f * u)
            lineTo(18.5f * u, 6f * u)
            lineTo(18.5f * u, 14.2f * u)
            cubicTo(18.5f * u, 17.5f * u, 15.8f * u, 19.9f * u, 12f * u, 21.5f * u)
            cubicTo(8.2f * u, 19.9f * u, 5.5f * u, 17.5f * u, 5.5f * u, 14.2f * u)
            lineTo(5.5f * u, 6f * u)
            close()
        }
        drawPath(shield, RefCtrl)
        drawLine(AmBase, Offset(12f * u, 8f * u), Offset(12f * u, 14f * u), 2f * u, StrokeCap.Round)
        drawLine(AmBase, Offset(9f * u, 11f * u), Offset(15f * u, 11f * u), 2f * u, StrokeCap.Round)
    }
}
