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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmBase
import com.example.ui.theme.IconDisabled
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

private val IconWhite = Color(0xFFF2F2F2)

/**
 * The transport bar, in the build-206 redesign language. All glyphs are
 * drawn here (no stock icons) so stroke weight and proportions match.
 *
 * Build 201 rebuilt this bar as two rows (audit B-1: the old one-Box layout
 * OVERLAPPED ~546 dp of controls in a 360 dp bar). Build 202 dissolved the
 * utility row into a "More tools" overlay panel above this row — and the
 * overlay arrived on Sarowar's phone as ONE CLIPPED ROW floating over the
 * canvas, its second row and the whole clip section unreachable.
 *
 * Build 206 therefore removes the overlay entirely:
 *   - the bar is ONE transport row floating as a pill (a capsule backdrop
 *     behind the unchanged cluster layout): [undo 44][redo 44][resolution
 *     44] · centred [play 44] · [fullscreen 44][tools 44] — left cluster
 *     ends at 142.5 dp, play spans 158–202 dp, right cluster starts at
 *     268 dp (360 dp budget, unchanged since build 201/202).
 *   - the tools button is now the references' round VIOLET disc (+ while
 *     closed, × while open) in the old More button's 44 dp zone — it opens
 *     the TOOLS GRID ([ToolsGridSheet]), a sheet in normal layout flow
 *     between the timeline and the footer, never an overlay. (The disc
 *     lives here, not beside the footer toolbar, so the footer panels keep
 *     their full width and their five primary tools stay visible — a
 *     footer-side button would have pushed Transform/Crop into the menu,
 *     the opposite of Sarowar's visibility complaint.)
 *   - the disc carries the old More glyph's honesty rule: a white ring
 *     while anything inside the grid is active (a range point set, a
 *     toggle on, a used clip tool — the screen passes the summary as
 *     [toolsGridActive]).
 *
 * [showEditorControls] false = a chrome-only host (CropView, audit S-3):
 * the tools disc is OMITTED entirely — never rendered inert.
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
    /** Editor-only controls (build 201): false for chrome-only hosts (CropView) — they are omitted, not inert. */
    showEditorControls: Boolean = true,
    /** The tools grid's open state (build 206) — hoisted by the screen. */
    toolsGridExpanded: Boolean = false,
    onToolsGridExpandedChange: (Boolean) -> Unit = {},
    /** Anything inside the tools grid is active (the screen's summary state). */
    toolsGridActive: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBase)
    ) {
        // ── The floating transport pill: a capsule backdrop behind the
        // cluster layout (the clusters keep their build-201 positions,
        // measured on this full-width box, so the budgets cannot drift). ──
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height((height - 6.dp).coerceAtLeast(34.dp))
                .background(AmBarSurface, CircleShape)
                .clip(CircleShape)
        )

        // ── Transport row: undo · redo · resolution | play | fullscreen · tools ──
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

            // ── Centre: play / pause — BARE white glyph; the 44 dp target
            // stays. It spans 158–202 dp at 360 dp wide; the side clusters
            // end at 142.5 / start at 268 — no overlap possible. ──
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
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp)
            ) {
                IconButton44(enabled = true, onClick = onFullScreenClick, tag = "fullscreen_button", description = "Full Screen") {
                    FrameGlyph()
                }
                if (showEditorControls) {
                    // THE tools button (build 206): the round violet disc
                    // opens the tools grid sheet in the screen's layout flow.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .semantics {
                                contentDescription = if (toolsGridExpanded) "Close tools" else "Tools"
                            }
                            .bouncyClickable(onClick = { onToolsGridExpandedChange(!toolsGridExpanded) })
                            .testTag("more_tools_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        ToolsGridDisc(expanded = toolsGridExpanded, active = toolsGridActive)
                    }
                }
            }
        }
    }
}

/**
 * The round violet tools button (build 206, from Sarowar's references):
 * a + while the grid is closed, a × while it is open. A white ring while
 * the grid is closed but something inside it is active — the honesty rule
 * the build-202 More glyph carried (snap defaults ON, so the ring is
 * expected on a fresh project; it is a state read, not a decoration).
 */
@Composable
private fun ToolsGridDisc(expanded: Boolean, active: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(
                if (active && !expanded) Modifier.border(2.dp, Color.White, CircleShape)
                else Modifier
            )
            .background(AmAccentViolet, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val u = size.width / 20f
            val c = Color.White
            val w = 2.4f * u
            if (expanded) {
                drawLine(c, Offset(5.2f * u, 5.2f * u), Offset(14.8f * u, 14.8f * u), w, StrokeCap.Round)
                drawLine(c, Offset(14.8f * u, 5.2f * u), Offset(5.2f * u, 14.8f * u), w, StrokeCap.Round)
            } else {
                drawLine(c, Offset(10f * u, 4.6f * u), Offset(10f * u, 15.4f * u), w, StrokeCap.Round)
                drawLine(c, Offset(4.6f * u, 10f * u), Offset(15.4f * u, 10f * u), w, StrokeCap.Round)
            }
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
