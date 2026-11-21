package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.model.LayerType
import com.example.model.at
import com.example.model.withClipAnim
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ANIM_MIN_MS
import com.example.model.AnimDir
import com.example.model.AnimPreset
import com.example.model.AnimPresets
import com.example.model.AnimSlot
import com.example.model.ClipAnim
import com.example.model.Easing
import com.example.model.TimelineClip
import com.example.model.maxAnimMs
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmCyan
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.EditorSurface
import com.example.ui.theme.EditorSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.amCyanVioletBrush
import com.example.ui.theme.amGlassBrush
import com.example.ui.util.bouncyClickable
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign

private const val MAX_SLIDER_MS = 4000L

private fun iconFor(presetId: String): ImageVector = when (presetId) {
    "fade" -> Icons.Default.Opacity
    "slide" -> Icons.Default.SwapHoriz
    "slide_fade" -> Icons.Default.Gradient
    "bounce" -> Icons.Default.SwapVert
    "zoom" -> Icons.Default.ZoomIn
    "zoom_fade" -> Icons.Default.ZoomOutMap
    "zoom_out" -> Icons.Default.ZoomOut
    "pop" -> Icons.Default.Star
    "spin" -> Icons.Default.RotateRight
    "spin_zoom" -> Icons.Default.Autorenew
    "wipe" -> Icons.Default.Crop
    else -> Icons.Default.Block
}

private enum class AnimSection(val label: String) { OPTIONS("Options"), CURVE("Curve") }

/**
 * Animations panel. It is a normal editor panel (same place and look as Transform / Crop / Color:
 * the timescale and the selected layer stay visible above it), NOT a big bottom sheet.
 *
 * In / Out tabs, a row of animations (tapping one applies it at once and plays it), then two sections:
 *  - Options: direction and length.
 *  - Curve: a small shape that plays the animation with the chosen curve (so you SEE how it moves),
 *    the draggable curve graph (a line shows where the shape is in time) and ready-made curves.
 *
 * [onApply] gets the preset (null = remove the animation of that slot). [onPreview] plays the
 * animation of that slot on the real layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipAnimationsPanel(
    clip: TimelineClip?,
    aspectRatio: Float = 16f / 9f,
    onApply: (slot: AnimSlot, preset: AnimPreset?, dir: AnimDir, durationMs: Long, easing: Easing?) -> Unit,
    onPreview: (slot: AnimSlot, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clip == null) return

    var slot by remember { mutableStateOf(AnimSlot.IN) }
    var section by remember { mutableStateOf(AnimSection.OPTIONS) }
    val applied: ClipAnim? = if (slot == AnimSlot.IN) clip.animIn else clip.animOut
    val appliedPreset = AnimPresets.byId(applied?.preset)

    // Local controls, restarted from what the layer has whenever the tab changes.
    var dir by remember(slot) { mutableStateOf(applied?.dir ?: AnimDir.LEFT) }
    var durMs by remember(slot) { mutableFloatStateOf((applied?.durationMs ?: appliedPreset?.defaultMs ?: 500L).toFloat()) }
    var easing by remember(slot) {
        mutableStateOf(
            applied?.easing?.takeIf { e -> appliedPreset == null || !e.sameAs(appliedPreset.defaultEasing(slot)) }
        )
    }

    val maxMs = clip.maxAnimMs(slot)
    val sliderMax = maxMs.coerceAtMost(MAX_SLIDER_MS).coerceAtLeast(ANIM_MIN_MS + 1L).toFloat()
    val canAnimate = maxMs >= ANIM_MIN_MS

    fun applyAnim(p: AnimPreset?, d: AnimDir, ms: Long, e: Easing?) = onApply(slot, p, d, ms, e)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("clip_animations_sheet")
    ) {
        // In / Out tabs + section switch on one line (keeps the panel low)
        // Build 252 (stage 5): the pills are GlChips.
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (s in AnimSlot.values()) {
                GlChip(
                    label = s.label,
                    selected = s == slot,
                    tag = "anim_tab_${s.name.lowercase()}",
                    onClick = { slot = s },
                    modifier = Modifier.padding(end = 6.dp),
                    padHDp = 12f,
                    styleOn = UiLabelStyle.ED_TEXT_BOLD,
                    styleOff = UiLabelStyle.ED_TEXT_SEMI,
                    textOffArgb = AmTextDim.toArgb()
                )
            }
            Spacer(Modifier.weight(1f))
            if (appliedPreset != null && canAnimate) {
                for (sec in AnimSection.values()) {
                    GlChip(
                        label = sec.label,
                        selected = sec == section,
                        tag = "anim_section_${sec.name.lowercase()}",
                        onClick = { section = sec },
                        modifier = Modifier.padding(start = 5.dp),
                        padHDp = 10f,
                        styleOn = UiLabelStyle.SECTION_BOLD,
                        styleOff = UiLabelStyle.SHEET_DESC,
                        textOffArgb = AmTextDim.toArgb()
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Animations: tap = applied — Build 252 (stage 5): GL strip.
        AnimTileStrip(
            appliedPresetId = applied?.preset,
            noneSelected = applied == null,
            onNone = { applyAnim(null, dir, durMs.toLong(), null) },
            onPreset = { p ->
                // A new animation starts from its own defaults (length, curve); the direction is kept
                // when the animation has it.
                val d = if (p.dirs.contains(dir)) dir else p.defaultDir()
                dir = d
                durMs = p.defaultMs.toFloat()
                easing = null
                applyAnim(p, d, p.defaultMs, null)
                onPreview(slot, p.defaultMs.coerceAtMost(maxMs.coerceAtLeast(ANIM_MIN_MS)))
            }
        )

        if (appliedPreset != null && canAnimate) {
            Spacer(Modifier.height(6.dp))
            if (section == AnimSection.OPTIONS) {
                // Direction
                if (appliedPreset.dirs.isNotEmpty()) {
                    GlTextBlock(
                        text = if (appliedPreset.id.startsWith("spin")) "Turn" else if (slot == AnimSlot.IN) "From" else "To",
                        style = UiLabelStyle.SHEET_DESC,
                        colorArgb = TextSecondary.toArgb(),
                        align = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(3.dp))
                    // Bare direction texts (no fill): GL text boxes
                    // with the old padding as their tap/press frame.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in appliedPreset.dirs) {
                            val on = d == dir
                            val spin = appliedPreset.id.startsWith("spin")
                            AnimDirTextGl(
                                text = if (spin) (if (d == AnimDir.RIGHT) "↻ Clockwise" else "↺ Counter")
                                else "${d.arrow} ${d.label}",
                                selected = on,
                                tag = "anim_dir_${d.name.lowercase()}",
                                onClick = {
                                    dir = d
                                    applyAnim(appliedPreset, d, durMs.toLong(), easing)
                                    onPreview(slot, durMs.toLong())
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Length — Build 252 (stage 5): GL header strip + the
                // shared GlSlider with this sheet's own colours.
                AnimLengthHeaderGl(durMs = durMs)
                GlSlider(
                    value = durMs.coerceIn(ANIM_MIN_MS.toFloat(), sliderMax),
                    onValueChange = {
                        durMs = it
                        applyAnim(appliedPreset, dir, it.roundToInt().toLong(), easing)
                    },
                    onValueChangeFinished = { onPreview(slot, durMs.toLong()) },
                    min = ANIM_MIN_MS.toFloat(),
                    max = sliderMax,
                    thumbArgb = AmCyan.toArgb(),
                    activeArgb = AmCyan.toArgb(),
                    inactiveArgb = EditorSurfaceVariant.toArgb(),
                    modifier = Modifier.testTag("anim_length_slider")
                )
            } else {
                // ---- Curve: shape preview + curve graph + ready-made curves ----
                val shownEasing = easing ?: appliedPreset.defaultEasing(slot)
                // 0..1 position inside the animation; the preview shape and the graph line share it.
                var t by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(slot, appliedPreset.id, dir, durMs) {
                    val animNs = (durMs.coerceIn(400f, 2500f) * 1_000_000f).toLong()
                    val holdNs = 500_000_000L
                    var t0 = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val el = now - t0
                        if (el >= animNs + holdNs) t0 = now
                        t = (el.toFloat() / animNs).coerceIn(0f, 1f)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimShapePreview(
                        preset = appliedPreset,
                        slot = slot,
                        dir = dir,
                        easing = shownEasing,
                        t = t,
                        aspectRatio = aspectRatio,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 3.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        EasingEditor(
                            easing = shownEasing,
                            progress = t,
                            onBegin = {},
                            onChange = { e ->
                                easing = e
                                applyAnim(appliedPreset, dir, durMs.toLong(), e)
                            },
                            showPresets = false,
                            valueLabel = "Move"
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CurveChipGl(label = "Auto", easing = null, selected = easing == null) {
                        easing = null
                        applyAnim(appliedPreset, dir, durMs.toLong(), null)
                        onPreview(slot, durMs.toLong())
                    }
                    Easing.PRESETS.forEach { (name, e) ->
                        CurveChipGl(label = name, easing = e, selected = easing?.sameAs(e) == true) {
                            easing = e
                            applyAnim(appliedPreset, dir, durMs.toLong(), e)
                            onPreview(slot, durMs.toLong())
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            GlTextBlock(
                text = "The animation is made of normal keyframes: edit them in Transform, Crop or Blend.",
                style = UiLabelStyle.TEXT_10,
                colorArgb = TextSecondary.toArgb(),
                align = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!canAnimate) {
            Spacer(Modifier.height(10.dp))
            GlTextBlock(
                text = "This layer is too short for another animation.",
                style = UiLabelStyle.SHEET_DESC,
                colorArgb = TextSecondary.toArgb(),
                align = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * A small canvas with a shape on which the animation is played, using the animation's own keyframe
 * code (so it looks exactly as the real layer will move) and the chosen easing curve.
 * [t] 0..1 = position inside the animation. In: arrives. Out: leaves.
 */
@Composable
private fun AnimShapePreview(
    preset: AnimPreset,
    slot: AnimSlot,
    dir: AnimDir,
    easing: Easing,
    t: Float,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    val ratio = aspectRatio.coerceIn(0.4f, 2.4f)
    // A 4 s dummy layer with the animation applied. The canvas is the unit of Position (px).
    val previewW = 320f
    val previewH = previewW / ratio
    val layerMs = 4000L
    val animMs = 1000L
    val dummy = remember(preset.id, slot, dir, easing, ratio) {
        TimelineClip(
            id = "anim_preview", name = "p", type = LayerType.SHAPE,
            startMs = 0L, durationMs = layerMs, color = 0xFF00E5FF
        ).withClipAnim(slot, preset, dir, animMs, easing, previewW, previewH)
    }
    val ms = if (slot == AnimSlot.IN) (t * animMs).toLong() else (layerMs - animMs + (t * animMs).toLong())
    val c = dummy.at(ms.coerceIn(0L, layerMs))

    // Build 252 (stage 5): the preview is drawn in GL. The glass
    // surface is the flat AmPanel + hairline (glassSurface's rule);
    // the shape is the sampled rounded-rect outline rotated by the
    // frame's rotation, clipped to the frame's crop rect by polygon
    // clipping in the unrotated frame (the GL builder has no clip
    // command — the intersection is computed exactly on the sampled
    // polygon), and the marker circle rides the same rotation.
    NativeVectorCanvas(
        modifier = modifier
            .aspectRatio(ratio, matchHeightConstraintsFirst = true),
        clearArgb = 0,
        drawKey = listOf(
            c.scalePercent, c.transformX, c.transformY, c.opacity, c.rotationDeg,
            c.cropWidthPercent, c.cropHeightPercent, c.cropHorizontalPercent, c.cropVerticalPercent
        ),
        buildCmds = { w, h, dd ->
            addRoundRect(w / 2f, h / 2f, w, h, 16f * dd, AmPanel.toArgb())
            addRoundRect(
                w / 2f, h / 2f, w - 1f * dd, h - 1f * dd, 15.5f * dd,
                0, 1f * dd, AmHairline.toArgb()
            )
            val k = w / previewW // preview px -> screen px
            val base = h * 0.5f
            val sc = (c.scalePercent / 100f).coerceAtLeast(0f)
            val side = base * sc
            if (side > 0f) {
                val cx = w / 2f + c.transformX * k
                val cy = h / 2f + c.transformY * k
                val alpha = (c.opacity / 100f).coerceIn(0f, 1f)
                val left = cx - side / 2f
                val top = cy - side / 2f
                // Unrotated shape outline about its own centre.
                val outline = EditorGeometry.roundedRectOutline(
                    0f, 0f, side, side,
                    side * 0.18f, side * 0.18f, side * 0.18f, side * 0.18f,
                    0f, 4
                )
                // Crop rect in the same unrotated frame.
                val cw = (c.cropWidthPercent / 100f).coerceIn(0f, 1f)
                val chh = (c.cropHeightPercent / 100f).coerceIn(0f, 1f)
                val cl = -side / 2f + (side - side * cw) * (c.cropHorizontalPercent / 100f)
                val ct = -side / 2f + (side - side * chh) * (c.cropVerticalPercent / 100f)
                val clipped = clipPolygonToRect(outline, cl, ct, cl + side * cw, ct + side * chh)
                if (clipped.size >= 6) {
                    val rot = Math.toRadians(c.rotationDeg.toDouble())
                    val cosR = kotlin.math.cos(rot).toFloat()
                    val sinR = kotlin.math.sin(rot).toFloat()
                    val xs = FloatArray(clipped.size / 2)
                    val ys = FloatArray(clipped.size / 2)
                    for (i in xs.indices) {
                        val px = clipped[i * 2]
                        val py = clipped[i * 2 + 1]
                        xs[i] = cx + px * cosR - py * sinR
                        ys[i] = cy + px * sinR + py * cosR
                    }
                    addFilledPolygon(xs, ys, xs.size, NeonCyan.copy(alpha = alpha).toArgb())
                    // The marker circle, sampled and clipped by the
                    // same crop rect (it sat inside the old clipRect).
                    val mx = -side / 2f + side * 0.28f
                    val my = -side / 2f + side * 0.28f
                    val mr = side * 0.12f
                    val circlePts = FloatArray(48)
                    for (i in 0 until 24) {
                        val a = (i / 24f) * (2f * Math.PI.toFloat())
                        circlePts[i * 2] = mx + kotlin.math.cos(a) * mr
                        circlePts[i * 2 + 1] = my + kotlin.math.sin(a) * mr
                    }
                    val clippedCircle = clipPolygonToRect(circlePts, cl, ct, cl + side * cw, ct + side * chh)
                    if (clippedCircle.size >= 6) {
                        val cxs = FloatArray(clippedCircle.size / 2)
                        val cys = FloatArray(clippedCircle.size / 2)
                        for (i in cxs.indices) {
                            val px = clippedCircle[i * 2]
                            val py = clippedCircle[i * 2 + 1]
                            cxs[i] = cx + px * cosR - py * sinR
                            cys[i] = cy + px * sinR + py * cosR
                        }
                        addFilledPolygon(
                            cxs, cys, cxs.size,
                            Color.Black.copy(alpha = 0.55f * alpha).toArgb()
                        )
                    }
                }
            }
        }
    )
}

/** Sutherland–Hodgman clip of an interleaved polygon to an axis-aligned rect. */
private fun clipPolygonToRect(pts: FloatArray, l: Float, t: Float, r: Float, b: Float): FloatArray {
    var poly = pts
    fun clipEdge(inside: (Float, Float) -> Boolean, intersect: (Float, Float, Float, Float) -> Pair<Float, Float>): FloatArray {
        if (poly.size < 2) return poly
        val out = ArrayList<Float>()
        var prevX = poly[poly.size - 2]
        var prevY = poly[poly.size - 1]
        var prevIn = inside(prevX, prevY)
        var i = 0
        while (i < poly.size) {
            val curX = poly[i]
            val curY = poly[i + 1]
            val curIn = inside(curX, curY)
            if (curIn) {
                if (!prevIn) {
                    val (ix, iy) = intersect(prevX, prevY, curX, curY)
                    out.add(ix); out.add(iy)
                }
                out.add(curX); out.add(curY)
            } else if (prevIn) {
                val (ix, iy) = intersect(prevX, prevY, curX, curY)
                out.add(ix); out.add(iy)
            }
            prevX = curX; prevY = curY; prevIn = curIn
            i += 2
        }
        return FloatArray(out.size) { out[it] }
    }
    poly = clipEdge({ x, _ -> x >= l }, { x1, y1, x2, y2 ->
        val f = if (x2 != x1) (l - x1) / (x2 - x1) else 0f
        l to (y1 + (y2 - y1) * f)
    })
    poly = clipEdge({ x, _ -> x <= r }, { x1, y1, x2, y2 ->
        val f = if (x2 != x1) (r - x1) / (x2 - x1) else 0f
        r to (y1 + (y2 - y1) * f)
    })
    poly = clipEdge({ _, y -> y >= t }, { x1, y1, x2, y2 ->
        val f = if (y2 != y1) (t - y1) / (y2 - y1) else 0f
        (x1 + (x2 - x1) * f) to t
    })
    poly = clipEdge({ _, y -> y <= b }, { x1, y1, x2, y2 ->
        val f = if (y2 != y1) (b - y1) / (y2 - y1) else 0f
        (x1 + (x2 - x1) * f) to b
    })
    return poly
}

/**
 * Build 252 (stage 5): the animation tile strip in GL — "None" plus
 * every preset as a 56 dp tile (44 dp r12 box, white icon when
 * selected / dim when not, 9.5 sp label) on one surface in a
 * horizontal scroll row, the LazyRow's place.
 */
@Composable
private fun AnimTileStrip(
    appliedPresetId: String?,
    noneSelected: Boolean,
    onNone: () -> Unit,
    onPreset: (AnimPreset) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val tiles = remember {
        listOf(Triple("None", Icons.Default.Block, "anim_none")) +
            AnimPresets.ALL.map { Triple(it.label, iconFor(it.id), "anim_${it.id}") }
    }
    val painters = tiles.map { t -> key(t.third) { rememberVectorPainter(t.second) } }
    val iconPx = (20f * density).roundToInt()
    fun resolveTiles(): Pair<List<UiLabelAtlas.Entry?>, List<UiLabelAtlas.Entry?>> {
        val icons = tiles.mapIndexed { i, t ->
            atlas.resolveIcon("animtile:${t.third}", iconPx, iconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(painters[i], iconPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        }
        val labels = tiles.map { t ->
            atlas.resolve(t.first, if (isAnimTileSelected(t.third, appliedPresetId, noneSelected)) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL)
        }
        return icons to labels
    }
    var tileEntries = resolveTiles()
    if (atlas.overflowed) {
        atlas.reset()
        tileEntries = resolveTiles()
    }
    val (iconEntries, labelEntries) = tileEntries
    val scales = remember { tiles.map { androidx.compose.animation.core.Animatable(1f) } }
    val labelTextH = atlas.textHeightPx(UiLabelStyle.TILE_LABEL)
    val stripH = (44f + 4f + labelTextH / density).dp

    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .width((tiles.size * 56 + (tiles.size - 1) * 8).dp)
                .height(stripH)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(iconEntries, labelEntries, scales.map { it.value }, appliedPresetId, noneSelected, atlas.generation),
                buildCmds = { _, _, dd ->
                    tiles.forEachIndexed { i, t ->
                        val selected = isAnimTileSelected(t.third, appliedPresetId, noneSelected)
                        val left = i * 64f * dd
                        val cx = left + 28f * dd
                        setScaleAbout(cx, stripH.value * dd / 2f, scales[i].value)
                        addRoundRect(
                            cx, 22f * dd, 44f * dd, 44f * dd, 12f * dd,
                            (if (selected) AmPillSelected else AmPanelHigh).toArgb()
                        )
                        iconEntries[i]?.let { e ->
                            addText(
                                e.index,
                                cx - iconPx / 2f - pad, 22f * dd - iconPx / 2f - pad,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = (if (selected) Color.White else AmTextDim).toArgb()
                            )
                        }
                        labelEntries[i]?.let { e ->
                            val style = if (selected) UiLabelStyle.TILE_LABEL_BOLD else UiLabelStyle.TILE_LABEL
                            val textH = atlas.textHeightPx(style)
                            val fm = atlas.fontMetrics(style)
                            val labelTop = 48f * dd
                            val baseline = PanelGeometry.lineBaselinePx(labelTop, textH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(
                                EditorGeometry.centredOriginPx(cx, e.advance),
                                baseline, e.bearing, e.base, pad
                            )
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = (if (selected) TextPrimary else AmTextDim).toArgb()
                            )
                        }
                        clearTransform()
                    }
                }
            )
            tiles.forEachIndexed { i, t ->
                ChromePressBox(
                    scales[i], 0.94f,
                    {
                        if (i == 0) {
                            onNone()
                        } else {
                            onPreset(AnimPresets.ALL[i - 1])
                        }
                    },
                    Modifier
                        .offset(x = (i * 64).dp)
                        .width(56.dp)
                        .height(stripH),
                    tag = t.third,
                    description = t.first
                )
            }
        }
    }
}

private fun isAnimTileSelected(tag: String, appliedPresetId: String?, noneSelected: Boolean): Boolean =
    if (tag == "anim_none") noneSelected else appliedPresetId != null && tag == "anim_$appliedPresetId"

/**
 * Build 252 (stage 5): one bare direction text in GL (hug width,
 * the old h10/v4 padding as its frame; the press scales the text,
 * all there is).
 */
@Composable
private fun AnimDirTextGl(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = if (selected) UiLabelStyle.ED_TEXT_BOLD else UiLabelStyle.ED_TEXT
    fun resolveDir() = atlas.resolve(text, style)
    var entry = resolveDir()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveDir()
    }
    val e = entry
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val wDp = ((e?.advance ?: 0f) / density + 20f).dp
    val hDp = (atlas.textHeightPx(style) / density + 8f).dp
    Box(
        modifier = Modifier
            .width(wDp)
            .height(hDp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, selected, atlas.generation),
            buildCmds = { w, h, _ ->
                if (e != null) {
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(10f * density, baseline, e.bearing, e.base, pad)
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = (if (selected) TextPrimary else AmTextDim).toArgb()
                    )
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            tag = tag,
            description = text
        )
    }
}

/** Build 252 (stage 5): the Length header strip — label left, seconds right. */
@Composable
private fun AnimLengthHeaderGl(durMs: Float) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val valueText = "%.2f s".format(durMs / 1000f)
    fun resolveLen(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> =
        atlas.resolve("Length", UiLabelStyle.SHEET_DESC) to atlas.resolve(valueText, UiLabelStyle.ED_TEXT)
    var lenEntries = resolveLen()
    if (atlas.overflowed) {
        atlas.reset()
        lenEntries = resolveLen()
    }
    val (labelEntry, valueEntry) = lenEntries
    val hDp = (atlas.textHeightPx(UiLabelStyle.ED_TEXT) / density).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(hDp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(labelEntry, valueEntry, atlas.generation),
            buildCmds = { w, h, _ ->
                fun line(e: UiLabelAtlas.Entry?, style: UiLabelStyle, originX: Float, tint: Int) {
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
                line(labelEntry, UiLabelStyle.SHEET_DESC, 0f, TextSecondary.toArgb())
                if (valueEntry != null) {
                    line(valueEntry, UiLabelStyle.ED_TEXT, w - valueEntry.advance, TextPrimary.toArgb())
                }
            }
        )
    }
}

/**
 * Build 252 (stage 5): a curve chip in GL — 50 dp r9 surface, the
 * "A" glyph or the easing's own curve polyline, and the 8.5 sp
 * label. (The fill sat outside the press layer; content scales.)
 */
@Composable
private fun CurveChipGl(label: String, easing: Easing?, selected: Boolean, onClick: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val labelStyle = if (selected) UiLabelStyle.CURVE_BOLD else UiLabelStyle.CURVE
    fun resolveChip(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val l = atlas.resolve(label, labelStyle)
        val a = if (easing == null) atlas.resolve("A", UiLabelStyle.CURVE_A) else null
        return l to a
    }
    var chipEntries = resolveChip()
    if (atlas.overflowed) {
        atlas.reset()
        chipEntries = resolveChip()
    }
    val (labelEntry, aEntry) = chipEntries
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val tint = (if (selected) Color.White else AmTextDim).toArgb()
    val hDp = (4f + 26f + atlas.textHeightPx(labelStyle) / density + 4f).dp
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(hDp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(labelEntry, aEntry, scale.value, selected, easing, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(
                    w / 2f, h / 2f, w, h, 9f * dd,
                    (if (selected) AmPillSelected else AmPanelHigh).toArgb()
                )
                setScaleAbout(w / 2f, h / 2f, scale.value)
                val glyphCx = w / 2f
                val glyphCy = 4f * dd + 13f * dd
                if (easing == null) {
                    if (aEntry != null) {
                        val style = UiLabelStyle.CURVE_A
                        val textH = atlas.textHeightPx(style)
                        val fm = atlas.fontMetrics(style)
                        val top = glyphCy - textH / 2f
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(
                            EditorGeometry.centredOriginPx(glyphCx, aEntry.advance),
                            baseline, aEntry.bearing, aEntry.base, pad
                        )
                        addText(
                            aEntry.index, dstX, dstY,
                            0f, 0f, aEntry.rectW.toFloat(), aEntry.rectH.toFloat(),
                            tintArgb = tint
                        )
                    }
                } else {
                    val padIn = 3f * dd
                    val gw = 26f * dd - 2 * padIn
                    val gh = 26f * dd - 2 * padIn
                    val left = glyphCx - 13f * dd + padIn
                    val top = glyphCy - 13f * dd + padIn
                    val pts = FloatArray(50)
                    for (i in 0..24) {
                        val (x, y) = easing.pointAt(i / 24f)
                        pts[i * 2] = left + x * gw
                        pts[i * 2 + 1] = top + gh - (y - (-0.3f)) / (1.3f - (-0.3f)) * gh
                    }
                    val pxs = FloatArray(25) { pts[it * 2] }
                    val pys = FloatArray(25) { pts[it * 2 + 1] }
                    addPolyline(pxs, pys, 25, false, 1.8f * dd, tint)
                }
                if (labelEntry != null) {
                    val textH = atlas.textHeightPx(labelStyle)
                    val fm = atlas.fontMetrics(labelStyle)
                    val top = 30f * dd
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        EditorGeometry.centredOriginPx(glyphCx, labelEntry.advance),
                        baseline, labelEntry.bearing, labelEntry.base, pad
                    )
                    addText(
                        labelEntry.index, dstX, dstY,
                        0f, 0f, labelEntry.rectW.toFloat(), labelEntry.rectH.toFloat(),
                        tintArgb = (if (selected) TextPrimary else AmTextDim).toArgb()
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            tag = null,
            description = label
        )
    }
}
