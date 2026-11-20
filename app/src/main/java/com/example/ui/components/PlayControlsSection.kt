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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmHairline
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
                .padding(horizontal = 12.dp),
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
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            CtrlButton(
                enabled = canRedo, onClick = onRedoClick,
                tag = "workspace_redo_button", description = "Redo"
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo, contentDescription = null,
                    tint = if (canRedo) RefCtrl else RefCtrlDisabled,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            CtrlButton(
                enabled = true,
                onClick = if (showEditorControls) onFullScreenClick else onResolutionClick,
                tag = if (showEditorControls) "fullscreen_button" else "preview_resolution_button",
                description = if (showEditorControls) "Full Screen" else "Preview resolution"
            ) {
                Box(
                    modifier = Modifier
                        .border(1.5.dp, RefCtrl, RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showEditorControls) "Full" else resolutionLabel,
                        color = RefCtrl,
                        fontSize = 9.5.sp,
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
                Spacer(Modifier.size(10.dp))
                CtrlButton(
                    enabled = true, onClick = onGridClick, active = gridOn,
                    tag = "workspace_grid_toggle", description = "Grid"
                ) {
                    GridToggleGlyph(gridOn)
                }
                Spacer(Modifier.size(10.dp))
                CtrlButton(
                    enabled = true, onClick = onAddKeyframeClick,
                    tag = "add_keyframe_button", description = "Add Keyframe"
                ) {
                    Box {
                        KeyframeShieldGlyph()
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(5.5.dp)
                                .clip(CircleShape)
                                .background(RefOrange)
                                .border(1.dp, AmBase, CircleShape)
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
                Spacer(Modifier.size(10.dp))
                CtrlButton(
                    enabled = true, onClick = onNextClick,
                    tag = "next_button", description = "Next"
                ) {
                    SkipGlyph(mirrored = false)
                }
                Spacer(Modifier.size(10.dp))
                CtrlButton(
                    enabled = true, onClick = onFullScreenClick,
                    tag = "fullscreen_button", description = "Full Screen"
                ) {
                    FrameGlyph()
                }
            }
        }
        // The bar's bottom hairline (build 223: the family hairline token).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(AmHairline)
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
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = description }
            .bouncyClickable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ── Glyphs ───────────────────────────────────────────────────────────────

/** Soft rounded play triangle, or two bars while playing (19 dp, controls colour). */
@Composable
private fun PlayGlyph(isPlaying: Boolean) {
    NativeVectorCanvas(
        modifier = Modifier.size(19.dp),
        drawKey = isPlaying,
        buildCmds = { w, h, _ ->
            val u = w / 24f
            val cArgb = RefCtrl.toArgb()
            if (isPlaying) {
                val bw = 4.4f * u
                val bh = 17f * u
                val cy = h / 2f
                addRoundRect(w / 2f - 6.4f * u + bw / 2f, cy, bw, bh, 1.8f * u, cArgb)
                addRoundRect(w / 2f + 2.0f * u + bw / 2f, cy, bw, bh, 1.8f * u, cArgb)
            } else {
                val r = 1.6f * u
                val tw = 14.6f * u - 2 * r
                val th = 17.2f * u - 2 * r
                val x0 = w / 2f - 14.6f * u / 2f + r + 0.6f * u
                val y0 = (h - th) / 2f
                val xs = floatArrayOf(x0, x0 + tw, x0)
                val ys = floatArrayOf(y0, y0 + th / 2f, y0 + th)
                addFilledPolygon(xs, ys, 3, cArgb)
                addPolyline(xs, ys, 3, true, 2 * r, cArgb, capRound = true)
            }
        },
    )
}

/** Skip triangle + bar (previous / next) — chrome hosts only. */
@Composable
private fun SkipGlyph(mirrored: Boolean) {
    NativeVectorCanvas(
        modifier = Modifier.size(17.dp),
        drawKey = mirrored,
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val cArgb = RefCtrl.toArgb()
            fun x(v: Float) = (if (mirrored) 24f - v else v) * u
            addLine(x(5f), 5.5f * u, x(5f), 18.5f * u, 2.4f * u, cArgb, capRound = true)
            val xs = floatArrayOf(x(9f), x(19f), x(9f))
            val ys = floatArrayOf(6f * u, 12f * u, 18f * u)
            addFilledPolygon(xs, ys, 3, cArgb)
            addPolyline(xs, ys, 3, true, 1.6f * u, cArgb, capRound = true)
        },
    )
}

/** Four corners + two overlapping layers — the fullscreen glyph (chrome hosts). */
@Composable
private fun FrameGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(17.dp),
        drawKey = "ctrl_frame_glyph",
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val cArgb = RefCtrl.toArgb()
            val a = 4.7f * u
            val b = 19.3f * u
            addLine(a, a, b, a, 1.4f * u, cArgb, capRound = true)
            addLine(a, b, b, b, 1.4f * u, cArgb, capRound = true)
            addLine(a, a, a, b, 1.4f * u, cArgb, capRound = true)
            addLine(b, a, b, b, 1.4f * u, cArgb, capRound = true)
            for (cx in listOf(a, b)) for (cy in listOf(a, b)) addCircle(cx, cy, 1.95f * u, cArgb)
            addRoundRect((8.7f + 4.2f) * u, (12.5f + 2.45f) * u, 8.4f * u, 4.9f * u, 2.4f * u, Color(0xFFA1A1AA).toArgb())
            addRoundRect((7.0f + 4.0f) * u, (6.9f + 2.5f) * u, 8.0f * u, 5.0f * u, 2.5f * u, cArgb, 1.2f * u, AmBase.toArgb())
        },
    )
}

/** The snap magnet (build 222): a horseshoe magnet, WHITE while on, GREY while off (21 dp). */
@Composable
private fun MagnetGlyph(on: Boolean) {
    NativeVectorCanvas(
        modifier = Modifier.size(17.dp),
        drawKey = on,
        buildCmds = { width, _, _ ->
            val u = width / 24f
            val c = if (on) RefCtrl else RefCtrlDisabled
            val cArgb = c.toArgb()
            val sw = 3.6f * u
            addArc(
                cx = 12f * u, cy = 10.2f * u,
                rx = 6.8f * u, ry = 6.8f * u,
                startDeg = 180f, sweepDeg = 180f,
                width = sw, strokeArgb = cArgb, capRound = false, closedPie = false
            )
            addLine(5.2f * u, 10.2f * u, 5.2f * u, 19.6f * u, sw, cArgb, capRound = false)
            addLine(18.8f * u, 10.2f * u, 18.8f * u, 19.6f * u, sw, cArgb, capRound = false)
            val tipArgb = (if (on) Color.White else c).toArgb()
            addLine(5.2f * u, 16.4f * u, 5.2f * u, 19.6f * u, sw, tipArgb, capRound = false)
            addLine(18.8f * u, 16.4f * u, 18.8f * u, 19.6f * u, sw, tipArgb, capRound = false)
        },
    )
}

/** The reference's grid glyph: a rounded square divided in thirds both ways (21 dp). Lime while on. */
@Composable
private fun GridToggleGlyph(on: Boolean) {
    NativeVectorCanvas(
        modifier = Modifier.size(17.dp),
        drawKey = on,
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val cArgb = (if (on) RefLime else RefCtrl).toArgb()
            addRoundRect(12f * u, 12f * u, 16f * u, 16f * u, 3f * u, 0, 1.8f * u, cArgb)
            addLine(9f * u, 4f * u, 9f * u, 20f * u, 1.5f * u, cArgb, capRound = false)
            addLine(15f * u, 4f * u, 15f * u, 20f * u, 1.5f * u, cArgb, capRound = false)
            addLine(4f * u, 9f * u, 20f * u, 9f * u, 1.5f * u, cArgb, capRound = false)
            addLine(4f * u, 15f * u, 20f * u, 15f * u, 1.5f * u, cArgb, capRound = false)
        },
    )
}

/** The reference's Add-Keyframe glyph: a filled shield with a plus cut out of it (21 dp). */
@Composable
private fun KeyframeShieldGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(17.dp),
        drawKey = "keyframe_shield_glyph",
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val pts = mutableListOf<Pair<Float, Float>>()
            pts.add(12f * u to 2.5f * u)
            pts.add(18.5f * u to 6f * u)
            pts.add(18.5f * u to 14.2f * u)
            fun addCubicPts(x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, x1: Float, y1: Float) {
                for (i in 1..10) {
                    val t = i / 10f
                    val m = 1f - t
                    val px = m * m * m * x0 + 3f * m * m * t * c1x + 3f * m * t * t * c2x + t * t * t * x1
                    val py = m * m * m * y0 + 3f * m * m * t * c1y + 3f * m * t * t * c2y + t * t * t * y1
                    pts.add(px to py)
                }
            }
            addCubicPts(18.5f * u, 14.2f * u, 18.5f * u, 17.5f * u, 15.8f * u, 19.9f * u, 12f * u, 21.5f * u)
            addCubicPts(12f * u, 21.5f * u, 8.2f * u, 19.9f * u, 5.5f * u, 17.5f * u, 5.5f * u, 14.2f * u)
            pts.add(5.5f * u to 6f * u)
            val xs = FloatArray(pts.size) { pts[it].first }
            val ys = FloatArray(pts.size) { pts[it].second }
            addFilledPolygon(xs, ys, pts.size, RefCtrl.toArgb())
            val cutArgb = AmBase.toArgb()
            addLine(12f * u, 8f * u, 12f * u, 14f * u, 2f * u, cutArgb, capRound = true)
            addLine(9f * u, 11f * u, 15f * u, 11f * u, 2f * u, cutArgb, capRound = true)
        },
    )
}
