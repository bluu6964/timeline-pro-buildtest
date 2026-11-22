package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Build 260 (Sarowar's reference images): the Split / Trim icon family.
 *
 * Split is two opposing brackets — `][` — a `]` and a `[` facing each
 * other across a centre gap: each bracket is a vertical stroke with a
 * short horizontal tick at its top and bottom ends pointing OUTWARD
 * (the bars stand near the centre, the ticks point away from it).
 *
 * Trim Left / Trim Right are the same pair plus a solid vertical CUT
 * LINE in the middle: the side that is trimmed away is drawn DASHED,
 * the kept side solid. Trim Left dashes the left bracket, Trim Right
 * the right one. (The multi-select bar's Delete left / Delete right
 * tiles are the same family — the playhead cut with the deleted side
 * dashed.)
 *
 * The dashes are drawn as separate line segments, never a path-effect
 * dash or a texture, so they survive every rasterization path the
 * glyphs take (atlas rasters for the GL surfaces, direct Canvas in the
 * Compose bars). Stroke weight 2 dp matches the sibling tool icons.
 */

/** Split: `][` — both brackets solid, no centre line. */
fun DrawScope.drawSplitGlyph(color: Color) =
    drawBracketPair(color, dashLeft = false, dashRight = false, centerLine = false)

/** Trim: centre cut line, the trimmed-away side's bracket dashed. */
fun DrawScope.drawTrimGlyph(color: Color, trimLeft: Boolean) =
    drawBracketPair(color, dashLeft = trimLeft, dashRight = !trimLeft, centerLine = true)

private fun DrawScope.drawBracketPair(
    color: Color,
    dashLeft: Boolean,
    dashRight: Boolean,
    centerLine: Boolean
) {
    val w = size.width
    val h = size.height
    val sw = 2.dp.toPx()
    val cx = w / 2f
    // The bars stand either side of the centre gap; the ticks run
    // outward from the bars to near the box edges.
    val barOff = w * 0.13f
    val tickEnd = w * 0.40f
    val top = h * 0.16f
    val bottom = h * 0.84f
    fun bracket(barX: Float, tickDir: Float, dashed: Boolean) {
        // Vertical bar.
        seg(Offset(barX, top), Offset(barX, bottom), color, sw, dashed)
        // Top + bottom ticks, pointing outward.
        seg(Offset(barX, top), Offset(barX + tickDir * tickEnd, top), color, sw, dashed)
        seg(Offset(barX, bottom), Offset(barX + tickDir * tickEnd, bottom), color, sw, dashed)
    }
    bracket(cx - barOff, -1f, dashLeft)
    bracket(cx + barOff, 1f, dashRight)
    if (centerLine) {
        drawLine(
            color, Offset(cx, h * 0.05f), Offset(cx, h * 0.95f),
            strokeWidth = sw, cap = StrokeCap.Round
        )
    }
}

/**
 * One stroke run: a plain line, or — when [dashed] — the same run cut
 * into separate dash segments (3 dp dash, 2.2 dp gap) so the dashed
 * look is geometry, not an effect.
 */
private fun DrawScope.seg(a: Offset, b: Offset, color: Color, sw: Float, dashed: Boolean) {
    if (!dashed) {
        drawLine(color, a, b, strokeWidth = sw, cap = StrokeCap.Round)
        return
    }
    val len = hypot(b.x - a.x, b.y - a.y)
    if (len <= 0f) return
    val dash = 3.dp.toPx()
    val gap = 2.2.dp.toPx()
    val ux = (b.x - a.x) / len
    val uy = (b.y - a.y) / len
    var t = 0f
    while (t < len) {
        val t2 = minOf(t + dash, len)
        drawLine(
            color,
            Offset(a.x + ux * t, a.y + uy * t),
            Offset(a.x + ux * t2, a.y + uy * t2),
            strokeWidth = sw, cap = StrokeCap.Round
        )
        t += dash + gap
    }
}
