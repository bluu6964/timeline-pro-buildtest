package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.ClipMask
import com.example.model.ClipMaskLibrary
import com.example.model.EditorGeometry
import com.example.model.LayerType
import com.example.model.MaskFrame
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MaskState
import com.example.model.PanelGeometry
import com.example.model.TimelineClip
import com.example.model.sourceOffsetMs
import com.example.model.neighbourKey
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign

/** Width / height of the layer's picture (the mask boxes are square in pixels, so the panel must know it). */
/**
 * The aspect (w / h) of the box a layer's masks are fractions of: a media layer's picture,
 * a shape layer's shape box (build 166 — masks on every picture layer), else the fallback.
 */
fun maskPictureAspect(clip: TimelineClip): Float = when {
    clip.sourceAspect > 0.05f -> clip.sourceAspect
    clip.type == LayerType.SHAPE && clip.shapeScaleX > 1e-4f && clip.shapeScaleY > 1e-4f ->
        (clip.shapeScaleX / clip.shapeScaleY).coerceIn(0.2f, 5f)
    else -> 16f / 9f
}

private val addTiles: List<Pair<String, String>> = listOf(
    "rect" to "Rectangle", "ellipse" to "Ellipse", "polygon" to "Polygon", "star" to "Star",
    "heart" to "Heart", "line" to "Line", "mirror" to "Mirror", "pen" to "Pen"
)

/**
 * Masks panel (same place and look as Effects). Top: tiles that add a mask. Then the masks of the layer (tap = select,
 * eye = on / off, copy, X = remove; the order is the order they are combined in). Below: the selected mask's mode,
 * invert, keyframe diamond and sliders. The shape itself is dragged on the canvas ([MaskEditOverlay]).
 * Edits of animated values (position, size, turn, feather, expansion, opacity) write a key at the playhead as soon as
 * the mask has one (diamond).
 */
@Composable
fun ClipMasksPanel(
    clip: TimelineClip,
    playheadMs: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    /** Pen drawing on the canvas: number of points placed so far, or -1 when not drawing. */
    penPoints: Int,
    onPenStart: () -> Unit,
    onPenUndo: () -> Unit,
    onPenDone: () -> Unit,
    onPenCancel: () -> Unit,
    /** Pen path edit: the control point selected on the canvas (-1 = none) and a request to delete it. */
    selectedPoint: Int,
    onDeletePoint: () -> Unit,
    /** Jump the playhead to a timeline time (previous / next key). */
    onSeekTo: (Long) -> Unit,
    onChange: (List<ClipMask>) -> Unit,
    /** "Track object" of the selected mask: the editor asks where the object is and follows it (id of the mask). */
    onTrackMask: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val masks = clip.masks
    val aspect = maskPictureAspect(clip)
    val rel = playheadMs - clip.startMs
    val selected = masks.firstOrNull { it.id == selectedId }

    fun replace(m: ClipMask) = onChange(masks.map { if (it.id == m.id) m else it })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("clip_masks_panel")
    ) {
        if (penPoints >= 0) {
            GlTextBlock(
                text = if (penPoints < 3) "Tap the picture to place points ($penPoints / ${ClipMaskLibrary.MAX_POINTS}, at least 3)"
                else "$penPoints points. Keep tapping, or press Done.",
                style = UiLabelStyle.ED_TEXT,
                colorArgb = TextPrimary.toArgb(),
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Start
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlChip("Undo point", false, tag = "mask_pen_undo", enabled = penPoints > 0, onClick = onPenUndo)
                GlChip("Done", true, tag = "mask_pen_done", enabled = penPoints >= 3, onClick = onPenDone)
                GlChip("Cancel", false, tag = "mask_pen_cancel", onClick = onPenCancel)
            }
            Spacer(Modifier.height(10.dp))
            return@Column
        }

        GlTextBlock(
            text = "Add a mask",
            style = UiLabelStyle.MICRO,
            colorArgb = AmTextDim.toArgb(),
            modifier = Modifier.fillMaxWidth(),
            align = TextAlign.Start
        )
        Spacer(Modifier.height(4.dp))
        MaskTileStrip(onTile = { id ->
            if (id == "pen") {
                if (masks.size < ClipMaskLibrary.MAX_MASKS) onPenStart()
            } else if (masks.size < ClipMaskLibrary.MAX_MASKS) {
                val m = when (id) {
                    "rect" -> ClipMaskLibrary.create(MaskShape.RECT, aspect)
                    "ellipse" -> ClipMaskLibrary.create(MaskShape.ELLIPSE, aspect)
                    "polygon" -> ClipMaskLibrary.create(MaskShape.POLYGON, aspect)
                    "star" -> ClipMaskLibrary.create(MaskShape.STAR, aspect)
                    "heart" -> ClipMaskLibrary.heart(aspect)
                    "line" -> ClipMaskLibrary.create(MaskShape.LINE, aspect)
                    else -> ClipMaskLibrary.create(MaskShape.MIRROR, aspect)
                }
                onSelect(m.id)
                onChange(masks + m)
            }
        })
        Spacer(Modifier.height(10.dp))

        GlTextBlock(
            text = "Masks (combined top to bottom)",
            style = UiLabelStyle.MICRO,
            colorArgb = AmTextDim.toArgb(),
            modifier = Modifier.fillMaxWidth(),
            align = TextAlign.Start
        )
        Spacer(Modifier.height(4.dp))
        if (masks.isEmpty()) {
            GlTextBlock(
                text = "Nothing yet. Tap a shape above, then drag it on the picture.",
                style = UiLabelStyle.ED_TEXT,
                colorArgb = TextSecondary.toArgb(),
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Start
            )
        }
        masks.forEachIndexed { idx, m ->
            val on = m.id == selectedId
            // BARE row (boxless order, build 183): no pill, no selection
            // border — selection reads from the name: white bold vs grey.
            // Build 252 (stage 5): the row is drawn in GL (MaskRowGl).
            MaskRowGl(
                name = "${idx + 1}. ${MaskShape.names[m.shape] ?: "Mask"}" + (if (m.isAnimated) "  ◆" else ""),
                modeLabel = (MaskMode.names.firstOrNull { it.first == m.mode }?.second ?: "") +
                    if (m.inverted) " (inv)" else "",
                selected = on,
                maskEnabled = m.enabled,
                onRowClick = { onSelect(if (on) null else m.id) },
                onToggle = { replace(m.copy(enabled = !m.enabled)) },
                onCopy = {
                    if (masks.size < ClipMaskLibrary.MAX_MASKS) {
                        val c = m.copy(id = java.util.UUID.randomUUID().toString(),
                            state = m.state.copy(cx = (m.state.cx + 0.04f).coerceAtMost(1f)),
                            keys = m.keys.map { k -> k.copy(state = k.state.copy(cx = (k.state.cx + 0.04f).coerceAtMost(1f))) })
                        onSelect(c.id)
                        onChange(masks.toMutableList().also { it.add(idx + 1, c) })
                    }
                },
                onRemove = {
                    if (selectedId == m.id) onSelect(null)
                    onChange(masks.filter { it.id != m.id })
                },
                rowTag = "mask_row_$idx",
                toggleTag = "mask_toggle_$idx",
                copyTag = "mask_copy_$idx",
                removeTag = "mask_remove_$idx"
            )
        }

        if (selected != null) {
            // a tracked mask is shown (and edited) where the object is: the sliders show the drawn values
            val src = clip.trimStartMs + clip.sourceOffsetMs(rel.coerceAtLeast(0L))
            val s = selected.shownStateAt(rel, src)
            val keyIdx = selected.keyIndexAt(rel)
            fun edit(ns: MaskState) = replace(selected.withShownStateAt(rel, src, ns))
            Spacer(Modifier.height(6.dp))
            GlTextBlock(
                text = "Combine",
                style = UiLabelStyle.MICRO,
                colorArgb = AmTextDim.toArgb(),
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Start
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MaskMode.names.forEach { (mode, label) ->
                    GlChip(label, selected.mode == mode, tag = "mask_mode_$mode", onClick = { replace(selected.copy(mode = mode)) })
                }
                GlChip("Invert", selected.inverted, tag = "mask_invert", onClick = { replace(selected.copy(inverted = !selected.inverted)) })
            }
            Spacer(Modifier.height(8.dp))

            // keyframes of this mask — Build 252 (stage 5): GL row.
            MaskAnimateRowGl(
                keysLabel = if (selected.isAnimated) "${selected.keys.size} keys" else "off",
                filled = keyIdx >= 0,
                active = selected.isAnimated,
                onPrev = {
                    val i = selected.neighbourKey(rel, false)
                    if (i >= 0) onSeekTo(clip.startMs + selected.keys[i].timeMs)
                },
                onToggle = { replace(selected.toggleKeyAt(rel)) },
                onNext = {
                    val i = selected.neighbourKey(rel, true)
                    if (i >= 0) onSeekTo(clip.startMs + selected.keys[i].timeMs)
                }
            )
            Spacer(Modifier.height(4.dp))

            // motion track: the mask follows an object of the video (place the mask on the object first)
            val tr = selected.track
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlTextBlock(
                    text = "Track",
                    style = UiLabelStyle.SHEET_DESC,
                    colorArgb = TextSecondary.toArgb(),
                    modifier = Modifier.width(66.dp),
                    align = TextAlign.Start
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlChip(if (tr == null) "Track object" else "Track again", tr != null, tag = "mask_track", onClick = { onTrackMask(selected.id) })
                    if (tr != null) {
                        GlChip("Rotate", tr.followRotation, tag = "mask_track_rot", onClick = { replace(selected.copy(track = tr.withFollow(!tr.followRotation, tr.followScale))) })
                        GlChip("Scale", tr.followScale, tag = "mask_track_scale", onClick = { replace(selected.copy(track = tr.withFollow(tr.followRotation, !tr.followScale))) })
                        GlChip("Clear", false, tag = "mask_track_clear", onClick = { replace(selected.copy(track = null)) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            MaskSlider("Feather", s.feather, 0f, 100f, "", "feather") { edit(s.copy(feather = it)) }
            MaskSlider("Expansion", s.expand, -100f, 100f, "", "expand") { edit(s.copy(expand = it)) }
            MaskSlider("Opacity", s.opacity, 0f, 100f, "%", "opacity") { edit(s.copy(opacity = it)) }
            if (selected.shape != MaskShape.LINE) {
                MaskSlider("Rotation", s.rot, -180f, 180f, "°", "rot") { edit(s.copy(rot = it)) }
            } else {
                MaskSlider("Angle", s.rot, -180f, 180f, "°", "rot") { edit(s.copy(rot = it)) }
            }
            if (selected.shape != MaskShape.LINE) {
                if (selected.shape != MaskShape.MIRROR) {
                    MaskSlider("Width", s.w * 100f, 1f, 200f, "%", "w") { edit(s.copy(w = it / 100f)) }
                }
                MaskSlider("Height", s.h * 100f, 1f, 200f, "%", "h") { edit(s.copy(h = it / 100f)) }
            }
            MaskSlider("Position X", s.cx * 100f, -50f, 150f, "%", "x") { edit(s.copy(cx = it / 100f)) }
            MaskSlider("Position Y", s.cy * 100f, -50f, 150f, "%", "y") { edit(s.copy(cy = it / 100f)) }
            when (selected.shape) {
                MaskShape.RECT -> MaskSlider("Corners", selected.corner, 0f, 100f, "%", "corner") { replace(selected.copy(corner = it)) }
                MaskShape.POLYGON -> MaskSlider("Sides", selected.sides.toFloat(), 3f, 12f, "", "sides") { replace(selected.copy(sides = it.toInt().coerceIn(3, 12))) }
                MaskShape.STAR -> {
                    MaskSlider("Points", selected.sides.toFloat(), 3f, 12f, "", "sides") { replace(selected.copy(sides = it.toInt().coerceIn(3, 12))) }
                    MaskSlider("Inner size", selected.inner, 5f, 100f, "%", "inner") { replace(selected.copy(inner = it)) }
                }
                MaskShape.PATH -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        GlChip("Smooth curve", selected.smooth, tag = "mask_smooth", onClick = { replace(selected.copy(smooth = !selected.smooth)) })
                        GlChip(
                            "Delete point", false,
                            enabled = selectedPoint in selected.points.indices && selected.points.size > ClipMaskLibrary.MIN_POINTS,
                            tag = "mask_delete_point", onClick = onDeletePoint
                        )
                    }
                    GlTextBlock(
                        text = "Drag the dots on the picture. Tap an edge to add a point, tap a dot to select it.",
                        style = UiLabelStyle.TEXT_11,
                        colorArgb = TextSecondary.toArgb(),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                        align = TextAlign.Start
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MaskSlider(label: String, value: Float, min: Float, max: Float, unit: String, tag: String, onValue: (Float) -> Unit) {
    // The same ruler slider as Transform / Effects.
    RulerDialSlider(
        label = label,
        labelWidth = 86.dp,
        value = value.coerceIn(min, max),
        onValueChange = onValue,
        min = min,
        max = max,
        unit = unit,
        isPercentage = false,
        modifier = Modifier.testTag("mask_slider_$tag")
    )
}

/** One glyph stroke in unit space: interleaved points, closed flag, stroke width (px in the 24 dp box), argb. */
private class GlyphSeg(val pts: FloatArray, val closed: Boolean, val strokeW: Float, val argb: Int)

/**
 * The add-tile glyphs as GL polylines — the same geometry the old
 * Compose Canvas drew: shape outlines through MaskFrame (so the tile
 * always matches the real outline), the line / mirror / pen glyphs
 * from their fixed fractions.
 */
private fun maskGlyphSegments(id: String): List<GlyphSeg> {
    val white = 0xFFFFFFFF.toInt()
    when (id) {
        "line" -> return listOf(
            GlyphSeg(floatArrayOf(0f, 0.5f, 1f, 0.5f), false, 2.6f, white),
            GlyphSeg(floatArrayOf(0.5f, 0.5f, 0.5f, 0.15f), false, 2.6f, 0x66FFFFFF)
        )
        "mirror" -> return listOf(
            GlyphSeg(floatArrayOf(0f, 0.3f, 1f, 0.3f), false, 2.6f, white),
            GlyphSeg(floatArrayOf(0f, 0.7f, 1f, 0.7f), false, 2.6f, white)
        )
        "pen" -> return listOf(
            GlyphSeg(
                floatArrayOf(0.15f, 0.8f, 0.3f, 0.2f, 0.7f, 0.35f, 0.85f, 0.85f),
                true, 2.4f, white
            )
        )
    }
    val shape = when (id) {
        "rect" -> MaskShape.RECT
        "ellipse" -> MaskShape.ELLIPSE
        "polygon" -> MaskShape.POLYGON
        "star" -> MaskShape.STAR
        else -> MaskShape.PATH
    }
    val m: ClipMask = when (id) {
        "heart" -> ClipMaskLibrary.heart(1f)
        else -> ClipMaskLibrary.create(shape, 1f)
    }
    val s = MaskState(cx = 0.5f, cy = 0.5f, w = 0.9f, h = 0.9f)
    val pts = MaskFrame(m, s, 1f).outline()
    if (pts.size < 2) return emptyList()
    val flat = FloatArray(pts.size * 2)
    pts.forEachIndexed { i, p ->
        flat[i * 2] = p.x
        flat[i * 2 + 1] = p.y
    }
    return listOf(GlyphSeg(flat, true, 2.4f, white))
}

/**
 * Build 252 (stage 5): the add-mask tile strip in GL — the eight
 * 56 dp tiles (42 dp glyph box, 3 dp gap, 9.5 sp label) on one
 * surface inside a horizontal scroll row, the LazyRow's place.
 */
@Composable
private fun MaskTileStrip(onTile: (String) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val labelStyle = UiLabelStyle.TILE_LABEL
    fun resolveTiles() = addTiles.map { atlas.resolve(it.second, labelStyle) }
    var labelEntries = resolveTiles()
    if (atlas.overflowed) {
        atlas.reset()
        labelEntries = resolveTiles()
    }
    val labels = labelEntries
    val segments = remember { addTiles.map { maskGlyphSegments(it.first) } }
    val scales = remember { addTiles.map { androidx.compose.animation.core.Animatable(1f) } }
    val stripH = (42f + 3f + atlas.textHeightPx(labelStyle) / density).dp
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .width((addTiles.size * 56 + (addTiles.size - 1) * 8).dp)
                .height(stripH)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(labels, scales.map { it.value }, atlas.generation),
                buildCmds = { _, _, dd ->
                    addTiles.forEachIndexed { i, (_, label) ->
                        val tileLeft = i * 64f * dd
                        val tileCx = tileLeft + 28f * dd
                        setScaleAbout(tileCx, stripH.value * dd / 2f, scales[i].value)
                        val glyphLeft = tileLeft + 16f * dd
                        val glyphTop = 9f * dd
                        val glyphSize = 24f * dd
                        for (seg in segments[i]) {
                            val mapped = FloatArray(seg.pts.size)
                            for (k in seg.pts.indices step 2) {
                                mapped[k] = glyphLeft + seg.pts[k] * glyphSize
                                mapped[k + 1] = glyphTop + seg.pts[k + 1] * glyphSize
                            }
                            val mxs = FloatArray(seg.pts.size / 2) { mapped[it * 2] }
                            val mys = FloatArray(seg.pts.size / 2) { mapped[it * 2 + 1] }
                            addPolyline(mxs, mys, seg.pts.size / 2, seg.closed, seg.strokeW, seg.argb)
                        }
                        labels[i]?.let { e ->
                            val textH = atlas.textHeightPx(labelStyle)
                            val fm = atlas.fontMetrics(labelStyle)
                            val labelTop = 45f * dd
                            val baseline = PanelGeometry.lineBaselinePx(labelTop, textH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(
                                EditorGeometry.centredOriginPx(tileCx, e.advance),
                                baseline, e.bearing, e.base, pad
                            )
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                        }
                        clearTransform()
                    }
                }
            )
            addTiles.forEachIndexed { i, (id, label) ->
                ChromePressBox(
                    scales[i], 0.94f, { onTile(id) },
                    Modifier
                        .offset(x = (i * 64).dp)
                        .width(56.dp)
                        .height(stripH),
                    tag = "mask_tile_$id",
                    description = label
                )
            }
        }
    }
}

/**
 * Build 252 (stage 5): one mask list row in GL — name (semibold dim,
 * or bold white when selected; grey when the mask is off), mode
 * label, and the eye / copy / delete icon buttons. The whole row
 * keeps its 0.99 press; the icons keep their own.
 */
@Composable
private fun MaskRowGl(
    name: String,
    modeLabel: String,
    selected: Boolean,
    maskEnabled: Boolean,
    onRowClick: () -> Unit,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    rowTag: String,
    toggleTag: String,
    copyTag: String,
    removeTag: String
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val nameStyle = if (selected) UiLabelStyle.SHEET_ITEM_BOLD else UiLabelStyle.SHEET_ITEM_SEMI
    val nameArgb = when {
        !maskEnabled -> TextSecondary.toArgb()
        selected -> TextPrimary.toArgb()
        else -> AmTextDim.toArgb()
    }
    val eyePainter = rememberVectorPainter(if (maskEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff)
    val copyPainter = rememberVectorPainter(Icons.Default.ContentCopy)
    val deletePainter = rememberVectorPainter(Icons.Default.Delete)
    val iconPx = (16f * density).roundToInt()
    fun resolveRow(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, List<UiLabelAtlas.Entry?>> {
        val n = atlas.resolve(name, nameStyle)
        val md = atlas.resolve(modeLabel, UiLabelStyle.TEXT_10)
        val icons = listOf(
            "maskrow:eye:$maskEnabled" to eyePainter,
            "maskrow:copy" to copyPainter,
            "maskrow:delete" to deletePainter
        ).map { (k, p) ->
            atlas.resolveIcon(k, iconPx, iconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(p, iconPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        }
        return Triple(n, md, icons)
    }
    var rowEntries = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        rowEntries = resolveRow()
    }
    val (nameEntry, modeEntry, iconEntries) = rowEntries
    val rowScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val iconScales = remember { List(3) { androidx.compose.animation.core.Animatable(1f) } }
    val iconClicks = listOf(onToggle, onCopy, onRemove)
    val iconTags = listOf(toggleTag, copyTag, removeTag)
    val iconDescriptions = listOf("Turn off / on", "Duplicate", "Remove")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wPx = maxWidth.value * density
            // The name ellipsizes to its zone (name weight-1 in the old
            // Row): zone = width - row padding - mode label - icons.
            val shownNameEntry = run {
                val modeAdv = modeEntry?.advance ?: 0f
                val maxW = wPx - 104f * density - modeAdv
                if (nameEntry != null && nameEntry.advance > maxW) {
                    val shown = ChromeGeometry.ellipsize(name, maxW) { atlas.measure(it, nameStyle) }
                    if (shown != null && shown != name) atlas.resolve(shown, nameStyle) ?: nameEntry else nameEntry
                } else {
                    nameEntry
                }
            }
            NativeVectorCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(
                    shownNameEntry, modeEntry, iconEntries, rowScale.value,
                    iconScales.map { it.value }, nameArgb, atlas.generation
                ),
                buildCmds = { w, h, dd ->
                    val cy = h / 2f
                    setScaleAbout(w / 2f, cy, rowScale.value)
                    val iconsLeft = w - 10f * dd - 84f * dd
                    val modeAdv = modeEntry?.advance ?: 0f
                    val modeLeft = iconsLeft - modeAdv
                    if (shownNameEntry != null) {
                        val style = nameStyle
                        val textH = atlas.textHeightPx(style)
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(10f * dd, baseline, shownNameEntry.bearing, shownNameEntry.base, pad)
                        addText(
                            shownNameEntry.index, dstX, dstY,
                            0f, 0f, shownNameEntry.rectW.toFloat(), shownNameEntry.rectH.toFloat(),
                            tintArgb = nameArgb
                        )
                    }
                    if (modeEntry != null) {
                        val style = UiLabelStyle.TEXT_10
                        val textH = atlas.textHeightPx(style)
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(modeLeft, baseline, modeEntry.bearing, modeEntry.base, pad)
                        addText(
                            modeEntry.index, dstX, dstY,
                            0f, 0f, modeEntry.rectW.toFloat(), modeEntry.rectH.toFloat(),
                            tintArgb = TextSecondary.toArgb()
                        )
                    }
                    iconEntries.forEachIndexed { i, e ->
                        if (e != null) {
                            val cx = iconsLeft + (i * 28f + 14f) * dd
                            setScaleAbout(cx, cy, iconScales[i].value)
                            addText(
                                e.index,
                                cx - iconPx / 2f - pad, cy - iconPx / 2f - pad,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = TextPrimary.toArgb()
                            )
                            clearTransform()
                            setScaleAbout(w / 2f, cy, rowScale.value)
                        }
                    }
                    clearTransform()
                }
            )
            ChromePressBox(
                rowScale, 0.99f, onRowClick,
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                tag = rowTag,
                description = name
            )
            for (i in 0..2) {
                ChromePressBox(
                    iconScales[i], 0.94f, iconClicks[i],
                    Modifier
                        .offset(
                            x = ((wPx - 94f * density + i * 28f * density) / density).dp,
                            y = 2.dp
                        )
                        .size(28.dp),
                    tag = iconTags[i],
                    description = iconDescriptions[i]
                )
            }
        }
    }
}

/**
 * Build 252 (stage 5): the mask keyframe row in GL — "Animate"
 * label, previous-key / next-key icon buttons, the keyframe diamond
 * (a 45°-rotated square, filled or stroked, amber when the mask is
 * animated) and the keys count.
 */
@Composable
private fun MaskAnimateRowGl(
    keysLabel: String,
    filled: Boolean,
    active: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val prevPainter = rememberVectorPainter(Icons.Default.SkipPrevious)
    val nextPainter = rememberVectorPainter(Icons.Default.SkipNext)
    val iconPx = (16f * density).roundToInt()
    fun resolveAnim(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, List<UiLabelAtlas.Entry?>> {
        val label = atlas.resolve("Animate", UiLabelStyle.SHEET_DESC)
        val keys = atlas.resolve(keysLabel, UiLabelStyle.TEXT_10)
        val icons = listOf("maskanim:prev" to prevPainter, "maskanim:next" to nextPainter).map { (k, p) ->
            atlas.resolveIcon(k, iconPx, iconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(p, iconPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        }
        return Triple(label, keys, icons)
    }
    var animEntries = resolveAnim()
    if (atlas.overflowed) {
        atlas.reset()
        animEntries = resolveAnim()
    }
    val (labelEntry, keysEntry, iconEntries) = animEntries
    val prevScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val diamondScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val nextScale = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                labelEntry, keysEntry, iconEntries, filled, active,
                prevScale.value, diamondScale.value, nextScale.value, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                fun textIn(e: UiLabelAtlas.Entry?, style: UiLabelStyle, originX: Float, tint: Int) {
                    if (e == null) return
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = tint
                    )
                }
                textIn(labelEntry, UiLabelStyle.SHEET_DESC, 0f, TextSecondary.toArgb())
                textIn(keysEntry, UiLabelStyle.TEXT_10, 158f * dd, TextSecondary.toArgb())
                val iconBoxes = listOf(66f to prevScale, 126f to nextScale)
                iconBoxes.forEachIndexed { i, (leftDp, sc) ->
                    val e = iconEntries[i] ?: return@forEachIndexed
                    val cx = (leftDp + 14f) * dd
                    setScaleAbout(cx, cy, sc.value)
                    addText(
                        e.index,
                        cx - iconPx / 2f - pad, cy - iconPx / 2f - pad,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                    clearTransform()
                }
                // The diamond: a square of side 16 x 0.62 dp rotated
                // 45 degrees about the 32 dp box's centre (110, cy).
                val dcx = 110f * dd
                val side = 16f * 0.62f * dd
                val poly = EditorGeometry.roundedRectOutline(dcx, cy, side, side, 0f, 0f, 0f, 0f, 45f, 1)
                val xs = FloatArray(poly.size / 2)
                val ys = FloatArray(poly.size / 2)
                for (k in xs.indices) {
                    xs[k] = poly[k * 2]
                    ys[k] = poly[k * 2 + 1]
                }
                val dArgb = (if (active) Color(0xFFFFC107) else Color(0xFF9E9E9E)).toArgb()
                setScaleAbout(dcx, cy, diamondScale.value)
                if (filled) {
                    addFilledPolygon(xs, ys, xs.size, dArgb)
                } else {
                    addPolyline(xs, ys, xs.size, true, 2.2f, dArgb)
                }
                clearTransform()
            }
        )
        ChromePressBox(
            prevScale, 0.94f, onPrev,
            Modifier
                .offset(x = 66.dp, y = 2.dp)
                .size(28.dp),
            tag = "mask_key_prev",
            description = "Previous key"
        )
        ChromePressBox(
            diamondScale, 0.94f, onToggle,
            Modifier
                .offset(x = 94.dp)
                .size(32.dp),
            tag = "mask_key_toggle",
            description = "Keyframe"
        )
        ChromePressBox(
            nextScale, 0.94f, onNext,
            Modifier
                .offset(x = 126.dp, y = 2.dp)
                .size(28.dp),
            tag = "mask_key_next",
            description = "Next key"
        )
    }
}
