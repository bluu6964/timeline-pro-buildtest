package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MultiAction
import com.example.model.MultiBarMode
import com.example.model.ShapeBooleanOp
import com.example.model.SpatialAction
import com.example.ui.theme.AmCornerPanel
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.amGlassBrush
import com.example.ui.util.bouncyClickable

private val IconColor = AmTextPrimary // build 223 (colour balance): light glyphs — the bar surface is the dark AmPanel (build 216's white bar is gone; its ink glyphs were invisible on dark)

/**
 * Build 235: the compact bar's heights. The single row is 40 dp inside
 * 6 dp outer + 2 dp inner vertical padding = 56 dp total; the boolean
 * row adds 46 dp. The timeline's multi-select bottom reservation is
 * [MultiSelectBarHeightWithBoolean] + 4 dp (see timelineBottomPadDp),
 * so the last lane can always scroll fully clear of the bar.
 */
internal val MultiSelectBarBaseHeight = 44.dp
internal val MultiSelectBarBooleanRowHeight = 36.dp
internal val MultiSelectBarHeightWithBoolean = MultiSelectBarBaseHeight + MultiSelectBarBooleanRowHeight

/**
 * Bar shown at the bottom of the timeline while several layers are selected.
 *
 * Build 235 (Sarowar's report): the old four-row bar (mode group + time
 * align, spatial align, distribute / Chain / Group, booleans) stood
 * 142–188 dp tall over the lanes — in multi-select he could no longer
 * SEE or TAP the other lanes to add/remove layers. The on-timeline bar
 * is now ONE compact row — the checked count and the labelled All /
 * Delete tiles — plus, only when [showBooleanRow] (exactly two eligible
 * shape layers checked, build 192), the boolean row under it
 * (Union / Subtract / Intersect / XOR; Subtract reads first-checked
 * minus second-checked). Every other action the old bar carried (mode
 * group, time align, spatial align / distribute, Chain, Group) lives
 * in the tools grid as head tiles via [multiBarActionTiles] (build
 * 211) — open the tools panel during multi-select to reach them.
 */
@Composable
fun MultiSelectBar(
    modifier: Modifier = Modifier,
    /** How many layers are checked (the row's count label). */
    selectedCount: Int = 0,
    /** Show the boolean row (exactly two eligible shape layers are checked). */
    showBooleanRow: Boolean = false,
    /** A boolean tile of the boolean row. */
    onBooleanAction: (ShapeBooleanOp) -> Unit = {},
    /** Delete tile (build 199, A11): deletes ALL checked layers at once (one undo step; locked layers are skipped). */
    onDeleteChecked: () -> Unit = {},
    /** Select-all tile (build 199, A4): checks every layer of the current scope. */
    onSelectAll: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .glassSurface(RoundedCornerShape(AmCornerPanel), fill = amGlassBrush())
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$selectedCount layers",
                color = AmTextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniTile(label = "All", labelColor = AmTextPrimary.copy(alpha = 0.75f), onClick = onSelectAll) { drawSelectAllGlyph() }
                MiniTile(label = "Delete", labelColor = AmDanger, onClick = onDeleteChecked) { drawTrashGlyph() }
            }
        }
        if (showBooleanRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MultiSelectBarBooleanRowHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BooleanTile(ShapeBooleanOp.UNION, onBooleanAction)
                BooleanTile(ShapeBooleanOp.SUBTRACT, onBooleanAction)
                BooleanTile(ShapeBooleanOp.INTERSECT, onBooleanAction)
                BooleanTile(ShapeBooleanOp.XOR, onBooleanAction)
            }
        }
    }
}

/**
 * Build 211 — NO STICKY TOOL LINES: while the tools panel is open the
 * multi-select bar is not rendered; its actions live at the HEAD of the
 * tools grid's one scrolling list as these tiles instead — the bar's
 * own order (mode group, time-align, spatial, distribute / chain /
 * group / All / Delete, boolean row), its own drawn glyphs, its own
 * callbacks. Labels are the full words the bare glyphs never showed.
 */
@Composable
fun multiBarActionTiles(
    mode: MultiBarMode,
    selectedCount: Int,
    showBooleanRow: Boolean,
    onAction: (MultiAction) -> Unit,
    onSpatialAction: (SpatialAction) -> Unit,
    onBooleanAction: (ShapeBooleanOp) -> Unit,
    onDeleteChecked: () -> Unit,
    onSelectAll: () -> Unit
): List<BarActionTile> {
    val tiles = mutableListOf<BarActionTile>()
    fun glyph(w: androidx.compose.ui.unit.Dp = 24.dp, h: androidx.compose.ui.unit.Dp = 20.dp, draw: DrawScope.() -> Unit): @Composable () -> Unit =
        { Canvas(Modifier.size(w, h)) { draw() } }
    fun action(label: String, tag: String, a: MultiAction, draw: DrawScope.() -> Unit) {
        tiles += BarActionTile(label, tag, onClick = { onAction(a) }, glyph = glyph(draw = draw))
    }
    fun spatial(label: String, tag: String, a: SpatialAction, enabled: Boolean = true, draw: DrawScope.() -> Unit) {
        tiles += BarActionTile(label, tag, enabled = enabled, onClick = { onSpatialAction(a) }, glyph = glyph(draw = draw))
    }
    // Row 1, left group (playhead-dependent, exactly the bar's modes).
    when (mode) {
        MultiBarMode.SPLIT -> {
            action("Delete left", "multi_delete_left", MultiAction.DELETE_LEFT) { drawSplitIcon(dashLeft = true, dashRight = false) }
            action("Split", "multi_split", MultiAction.SPLIT) { drawSplitIcon(dashLeft = false, dashRight = false) }
            action("Delete right", "multi_delete_right", MultiAction.DELETE_RIGHT) { drawSplitIcon(dashLeft = false, dashRight = true) }
        }
        MultiBarMode.START -> {
            action("Extend start", "multi_extend_start", MultiAction.EXTEND_START) { drawExtendIcon(atStart = true) }
            action("Move start", "multi_move_start", MultiAction.MOVE_START) { drawMoveIcon(atStart = true) }
        }
        MultiBarMode.END -> {
            action("Extend end", "multi_extend_end", MultiAction.EXTEND_END) { drawExtendIcon(atStart = false) }
            action("Move end", "multi_move_end", MultiAction.MOVE_END) { drawMoveIcon(atStart = false) }
        }
    }
    // Row 1, right group: time align.
    action("Align start", "multi_align_start", MultiAction.ALIGN_START) { drawBars(BarsKind.LEFT) }
    action("Sequence", "multi_sequence", MultiAction.SEQUENCE) { drawBars(BarsKind.STAIR) }
    action("Align end", "multi_align_end", MultiAction.ALIGN_END) { drawBars(BarsKind.RIGHT) }
    // Row 2: canvas spatial align + canvas centre.
    spatial("Align left", "multi_align_left", SpatialAction.ALIGN_LEFT) { drawAlignGlyph(AlignGlyph.LEFT) }
    spatial("Centre H", "multi_centre_h", SpatialAction.ALIGN_CENTER_H) { drawAlignGlyph(AlignGlyph.CENTER_H) }
    spatial("Align right", "multi_align_right", SpatialAction.ALIGN_RIGHT) { drawAlignGlyph(AlignGlyph.RIGHT) }
    spatial("Align top", "multi_align_top", SpatialAction.ALIGN_TOP) { drawAlignGlyph(AlignGlyph.TOP) }
    spatial("Centre V", "multi_centre_v", SpatialAction.ALIGN_CENTER_V) { drawAlignGlyph(AlignGlyph.CENTER_V) }
    spatial("Align bottom", "multi_align_bottom", SpatialAction.ALIGN_BOTTOM) { drawAlignGlyph(AlignGlyph.BOTTOM) }
    spatial("Canvas centre H", "multi_canvas_centre_h", SpatialAction.CANVAS_CENTER_H) { drawCanvasCentreGlyph(horizontal = true) }
    spatial("Canvas centre V", "multi_canvas_centre_v", SpatialAction.CANVAS_CENTER_V) { drawCanvasCentreGlyph(horizontal = false) }
    // Row 3: distribute (needs >= 3), chain, group, All, Delete.
    spatial("Distribute H", "multi_distribute_h", SpatialAction.DISTRIBUTE_H, enabled = selectedCount >= 3) { drawDistributeGlyph(horizontal = true) }
    spatial("Distribute V", "multi_distribute_v", SpatialAction.DISTRIBUTE_V, enabled = selectedCount >= 3) { drawDistributeGlyph(horizontal = false) }
    action("Chain", "multi_chain", MultiAction.CHAIN) { drawChainIcon() }
    action("Group", "multi_group", MultiAction.GROUP) { drawGroupIcon() }
    tiles += BarActionTile("All", "multi_all", onClick = onSelectAll, glyph = glyph { drawSelectAllGlyph() })
    tiles += BarActionTile("Delete", "multi_delete", destructive = true, onClick = onDeleteChecked, glyph = glyph { drawTrashGlyph() })
    // Row 4: shape booleans (only when the bar would show them).
    if (showBooleanRow) {
        for (op in ShapeBooleanOp.values()) {
            tiles += BarActionTile(
                op.displayName, "multi_boolean_${op.name.lowercase()}",
                onClick = { onBooleanAction(op) },
                glyph = glyph(w = 28.dp, h = 20.dp) { drawBooleanGlyph(op) }
            )
        }
    }
    return tiles
}

/**
 * A boolean tile of the third row: a drawn Venn glyph over a small name (the ops are
 * not self-evident as bare icons on first sight; the name is part of the tile, in the
 * bar's bare B/W language — no box, no fill).
 */
@Composable
private fun BooleanTile(
    op: ShapeBooleanOp,
    onAction: (ShapeBooleanOp) -> Unit
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .bouncyClickable { onAction(op) }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 16.dp)) { drawBooleanGlyph(op) }
        Text(
            op.displayName,
            color = AmTextPrimary.copy(alpha = 0.75f),
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * A labelled mini-tile of the third row (build 199; moved in the build-201
 * regroup): Delete (A11) and Select-all (A4) — actions whose glyph alone would
 * be ambiguous get their name under the glyph, in the bar's bare language.
 * Delete carries the destructive red of the selected-layer bar.
 */
@Composable
private fun MiniTile(
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    draw: DrawScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .width(32.dp)
            .bouncyClickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 16.dp)) { draw() }
        Text(
            label,
            color = labelColor,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}



// ---------------------------------------------------------------------------------------------
// Icons (drawn, so they match the reference screenshots without extra assets)
// ---------------------------------------------------------------------------------------------

/** Chain: a dashed container with two layers side by side inside. */
private fun DrawScope.drawChainIcon() {
    val sw = 1.6.dp.toPx()
    drawRoundRect(
        color = IconColor,
        topLeft = Offset(sw, sw),
        size = Size(size.width - 2 * sw, size.height - 2 * sw),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = sw, pathEffect = dashed())
    )
    val h = size.height * 0.34f
    val top = (size.height - h) / 2f
    drawRoundRect(IconColor, Offset(size.width * 0.16f, top), Size(size.width * 0.28f, h), CornerRadius(1.5.dp.toPx()))
    drawRoundRect(IconColor, Offset(size.width * 0.54f, top), Size(size.width * 0.30f, h), CornerRadius(1.5.dp.toPx()))
}

/** Group: a solid frame with two layers inside (they move together). */
private fun DrawScope.drawGroupIcon() {
    val sw = 1.8.dp.toPx()
    drawRoundRect(
        color = IconColor,
        topLeft = Offset(sw, sw),
        size = Size(size.width - 2 * sw, size.height - 2 * sw),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = sw)
    )
    drawRoundRect(IconColor, Offset(size.width * 0.20f, size.height * 0.22f), Size(size.width * 0.36f, size.height * 0.30f), CornerRadius(1.5.dp.toPx()))
    drawRoundRect(IconColor, Offset(size.width * 0.40f, size.height * 0.50f), Size(size.width * 0.38f, size.height * 0.28f), CornerRadius(1.5.dp.toPx()))
}

private fun DrawScope.dashed() = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))

private fun DrawScope.outline(x0: Float, x1: Float, dash: Boolean) {
    val sw = 1.8.dp.toPx()
    val top = size.height * 0.2f
    val h = size.height * 0.6f
    drawRoundRect(
        color = IconColor,
        topLeft = Offset(x0, top),
        size = Size((x1 - x0).coerceAtLeast(1f), h),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = sw, pathEffect = if (dash) dashed() else null)
    )
}

private fun DrawScope.verticalLine(x: Float) {
    drawLine(
        color = IconColor,
        start = Offset(x, 1.dp.toPx()),
        end = Offset(x, size.height - 1.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/** delete-left / split / delete-right: playhead line with a box on each side. */
private fun DrawScope.drawSplitIcon(dashLeft: Boolean, dashRight: Boolean) {
    val cx = size.width / 2f
    val pad = 1.5.dp.toPx()
    val gap = 4.dp.toPx()
    outline(pad, cx - gap, dashLeft)
    outline(cx + gap, size.width - pad, dashRight)
    verticalLine(cx)
}

/** Extend start/end to the playhead: dashed (new) part + solid (old) part next to the line. */
private fun DrawScope.drawExtendIcon(atStart: Boolean) {
    val pad = 1.5.dp.toPx()
    val gap = 4.dp.toPx()
    val w = size.width
    if (atStart) {
        verticalLine(pad)
        outline(pad + gap, w * 0.52f, dash = true)
        outline(w * 0.52f, w - pad, dash = false)
    } else {
        verticalLine(w - pad)
        outline(pad, w * 0.48f, dash = false)
        outline(w * 0.48f, w - pad - gap, dash = true)
    }
}

/** Move start/end to the playhead: solid box and an arrow toward the line. */
private fun DrawScope.drawMoveIcon(atStart: Boolean) {
    val pad = 1.5.dp.toPx()
    val w = size.width
    val cy = size.height / 2f
    val sw = 1.8.dp.toPx()
    val head = 5.dp.toPx()
    if (atStart) {
        verticalLine(pad)
        outline(w * 0.42f, w - pad, dash = false)
        val x1 = pad + 1.dp.toPx()
        val x2 = w * 0.42f - 2.dp.toPx()
        drawLine(IconColor, Offset(x1, cy), Offset(x2, cy), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(IconColor, Offset(x1, cy), Offset(x1 + head, cy - head), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(IconColor, Offset(x1, cy), Offset(x1 + head, cy + head), strokeWidth = sw, cap = StrokeCap.Round)
    } else {
        verticalLine(w - pad)
        outline(pad, w * 0.58f, dash = false)
        val x1 = w * 0.58f + 2.dp.toPx()
        val x2 = w - pad - 1.dp.toPx()
        drawLine(IconColor, Offset(x1, cy), Offset(x2, cy), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(IconColor, Offset(x2, cy), Offset(x2 - head, cy - head), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(IconColor, Offset(x2, cy), Offset(x2 - head, cy + head), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

private enum class AlignGlyph { LEFT, CENTER_H, RIGHT, TOP, CENTER_V, BOTTOM }

/** Trash can in the destructive red (build 199, A11): lid, handle and slatted body. */
private fun DrawScope.drawTrashGlyph() {
    val w = size.width
    val h = size.height
    val sw = 1.7.dp.toPx()
    val c = AmDanger
    // Lid + handle.
    drawLine(c, Offset(w * 0.14f, h * 0.2f), Offset(w * 0.86f, h * 0.2f), sw, StrokeCap.Round)
    drawLine(c, Offset(w * 0.4f, h * 0.2f), Offset(w * 0.4f, h * 0.1f), sw, StrokeCap.Round)
    drawLine(c, Offset(w * 0.6f, h * 0.2f), Offset(w * 0.6f, h * 0.1f), sw, StrokeCap.Round)
    drawLine(c, Offset(w * 0.4f, h * 0.1f), Offset(w * 0.6f, h * 0.1f), sw, StrokeCap.Round)
    // Body: slightly tapered can.
    val body = Path().apply {
        moveTo(w * 0.24f, h * 0.32f)
        lineTo(w * 0.3f, h * 0.9f)
        lineTo(w * 0.7f, h * 0.9f)
        lineTo(w * 0.76f, h * 0.32f)
    }
    drawPath(body, c, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(c, Offset(w * 0.44f, h * 0.45f), Offset(w * 0.46f, h * 0.78f), sw, StrokeCap.Round)
    drawLine(c, Offset(w * 0.56f, h * 0.45f), Offset(w * 0.54f, h * 0.78f), sw, StrokeCap.Round)
}

/** Dashed selection square with a check (build 199, A4): select all layers. */
private fun DrawScope.drawSelectAllGlyph() {
    val w = size.width
    val h = size.height
    val sw = 1.6.dp.toPx()
    drawRoundRect(
        color = IconColor,
        topLeft = Offset(sw, sw),
        size = Size(w - 2 * sw, h - 2 * sw),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(width = sw, pathEffect = dashed())
    )
    val tick = Path().apply {
        moveTo(w * 0.3f, h * 0.52f)
        lineTo(w * 0.45f, h * 0.68f)
        lineTo(w * 0.72f, h * 0.34f)
    }
    drawPath(tick, IconColor, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/**
 * Canvas-centre glyphs (build 199, A8): a FRAME outline (not a selection guide) with one
 * bar centred in it — vertical bar for centre-H, horizontal bar for centre-V.
 */
private fun DrawScope.drawCanvasCentreGlyph(horizontal: Boolean) {
    val w = size.width
    val h = size.height
    val sw = 1.6.dp.toPx()
    drawRoundRect(
        color = IconColor,
        topLeft = Offset(sw, sw),
        size = Size(w - 2 * sw, h - 2 * sw),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(width = sw)
    )
    val cr = CornerRadius(1.5.dp.toPx())
    if (horizontal) {
        drawRoundRect(IconColor, Offset(w / 2f - w * 0.07f, h * 0.24f), Size(w * 0.14f, h * 0.52f), cr)
    } else {
        drawRoundRect(IconColor, Offset(w * 0.24f, h / 2f - h * 0.1f), Size(w * 0.52f, h * 0.2f), cr)
    }
}

/**
 * Spatial align glyphs: a thin guide line (the edge / centre everything moves to) with
 * three bars of different lengths against it — horizontal bars for the X actions,
 * vertical bars for the Y actions.
 */
private fun DrawScope.drawAlignGlyph(kind: AlignGlyph) {
    val w = size.width
    val h = size.height
    val sw = 1.6.dp.toPx()
    val cr = CornerRadius(1.5.dp.toPx())
    val lens = floatArrayOf(0.92f, 0.58f, 0.36f)
    when (kind) {
        AlignGlyph.LEFT, AlignGlyph.CENTER_H, AlignGlyph.RIGHT -> {
            val gx = when (kind) {
                AlignGlyph.LEFT -> 1.dp.toPx()
                AlignGlyph.RIGHT -> w - 1.dp.toPx()
                else -> w / 2f
            }
            drawLine(IconColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = sw, cap = StrokeCap.Round)
            val thick = h * 0.2f
            val ys = floatArrayOf(0f, h * 0.4f, h * 0.8f)
            for (i in 0..2) {
                val len = w * lens[i]
                val x = when (kind) {
                    AlignGlyph.LEFT -> gx + 2.dp.toPx()
                    AlignGlyph.RIGHT -> gx - 2.dp.toPx() - len
                    else -> gx - len / 2f
                }
                drawRoundRect(IconColor, Offset(x, ys[i]), Size(len, thick), cr)
            }
        }
        else -> {
            val gy = when (kind) {
                AlignGlyph.TOP -> 1.dp.toPx()
                AlignGlyph.BOTTOM -> h - 1.dp.toPx()
                else -> h / 2f
            }
            drawLine(IconColor, Offset(0f, gy), Offset(w, gy), strokeWidth = sw, cap = StrokeCap.Round)
            val thick = w * 0.2f
            val xs = floatArrayOf(0f, w * 0.4f, w * 0.8f)
            for (i in 0..2) {
                val len = h * lens[i]
                val y = when (kind) {
                    AlignGlyph.TOP -> gy + 2.dp.toPx()
                    AlignGlyph.BOTTOM -> gy - 2.dp.toPx() - len
                    else -> gy - len / 2f
                }
                drawRoundRect(IconColor, Offset(xs[i], y), Size(thick, len), cr)
            }
        }
    }
}

/** Distribute glyphs: three equal blocks spaced evenly between two end ticks. */
private fun DrawScope.drawDistributeGlyph(horizontal: Boolean) {
    val w = size.width
    val h = size.height
    val sw = 1.6.dp.toPx()
    val cr = CornerRadius(1.5.dp.toPx())
    if (horizontal) {
        drawLine(IconColor, Offset(0f, 0f), Offset(0f, h), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(IconColor, Offset(w, 0f), Offset(w, h), strokeWidth = sw, cap = StrokeCap.Round)
        val bw = w * 0.18f
        val bh = h * 0.52f
        val top = (h - bh) / 2f
        for (cx in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            drawRoundRect(IconColor, Offset(w * cx - bw / 2f, top), Size(bw, bh), cr)
        }
    } else {
        drawLine(IconColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(IconColor, Offset(0f, h), Offset(w, h), strokeWidth = sw, cap = StrokeCap.Round)
        val bw = w * 0.52f
        val bh = h * 0.18f
        val left = (w - bw) / 2f
        for (cy in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            drawRoundRect(IconColor, Offset(left, h * cy - bh / 2f), Size(bw, bh), cr)
        }
    }
}

/**
 * Boolean glyphs: two overlapping circles read as the op's Venn diagram. The glyph
 * paths use the Compose path ops (an icon convenience only — the feature's own core
 * is [com.example.model.ShapeBoolean]). Subtract cuts the RIGHT circle out of the
 * left (first-checked minus second-checked) and leaves the cut circle as a dim
 * outline so the direction stays readable.
 */
private fun DrawScope.drawBooleanGlyph(op: ShapeBooleanOp) {
    val w = size.width
    val h = size.height
    val r = minOf(w, h) * 0.34f
    fun circle(cx: Float, cy: Float): Path = Path().apply {
        addOval(Rect(Offset(cx - r, cy - r), Size(2f * r, 2f * r)))
    }
    val p1 = circle(w * 0.36f, h * 0.5f)
    val p2 = circle(w * 0.64f, h * 0.5f)
    val sw = 1.4.dp.toPx()
    when (op) {
        ShapeBooleanOp.UNION -> {
            drawPath(p1, IconColor)
            drawPath(p2, IconColor)
        }
        ShapeBooleanOp.SUBTRACT -> {
            val cut = Path().apply { op(p1, p2, PathOperation.Difference) }
            drawPath(cut, IconColor)
            drawPath(p2, IconColor.copy(alpha = 0.45f), style = Stroke(width = sw))
        }
        ShapeBooleanOp.INTERSECT -> {
            drawPath(p1, IconColor.copy(alpha = 0.45f), style = Stroke(width = sw))
            drawPath(p2, IconColor.copy(alpha = 0.45f), style = Stroke(width = sw))
            val lens = Path().apply { op(p1, p2, PathOperation.Intersect) }
            drawPath(lens, IconColor)
        }
        ShapeBooleanOp.XOR -> {
            val x = Path().apply { op(p1, p2, PathOperation.Xor) }
            drawPath(x, IconColor)
        }
    }
}

private enum class BarsKind { LEFT, STAIR, RIGHT }

/** Three little bars: left-aligned, staircase, right-aligned. */
private fun DrawScope.drawBars(kind: BarsKind) {
    val w = size.width
    val h = size.height
    val barH = h * 0.22f
    val ys = floatArrayOf(0f, h * 0.39f, h * 0.78f)
    val lens = floatArrayOf(1f, 0.62f, 0.38f)
    for (i in 0..2) {
        val len = w * lens[i]
        val x = when (kind) {
            BarsKind.LEFT -> 0f
            BarsKind.RIGHT -> w - len
            BarsKind.STAIR -> when (i) { 0 -> w * 0.45f; 1 -> w * 0.2f; else -> 0f }
        }
        val drawLen = if (kind == BarsKind.STAIR) w * floatArrayOf(0.55f, 0.6f, 0.4f)[i] else len
        drawRoundRect(
            color = IconColor,
            topLeft = Offset(x, ys[i]),
            size = Size(drawLen, barH),
            cornerRadius = CornerRadius(1.5.dp.toPx())
        )
    }
}
