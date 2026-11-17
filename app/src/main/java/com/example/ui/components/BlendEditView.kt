package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimProp
import com.example.model.BlendModes
import com.example.model.BlendSpec
import com.example.model.Easing
import com.example.model.TimelineClip
import com.example.model.indexOnTime
import com.example.model.segmentIndexAt
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmCornerPanel
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextPrimary
import com.example.ui.util.BlendThumbs
import com.example.ui.util.bouncyClickable
import kotlin.math.roundToInt

// Build 221 (Sarowar's HTML reference v5, ai_studio_code__48.html) gave this
// panel its own palette; build 223 (Sarowar's colour-balance order) moved it
// onto the app's unified tokens — layout and shapes unchanged, the panel's
// green is now the one lime accent.
private val BlendGreen = AmAccent
private val BlendSurface = AmPanel
private val BlendIconTint = AmTextPrimary
private val BlendMuted = AmTextDim
private val BlendTrack = AmPanelElevated
private val BlendInk = AmInk
private val BlendCheckInk = AmInk

/**
 * "Blending & Opacity" editor of a layer (build 221, Sarowar's HTML reference v5):
 * a horizontal carousel of the active category's blend modes (82 dp thumbnails with
 * REAL blend results, see [BlendThumbs]; the chosen mode's thumbnail turns circular,
 * dims and gains a green check badge), the opacity slider flanked by the keyframe
 * (left) and easing-graph (right) buttons, and the six category pills at the bottom.
 * The whole panel fits the lower zone with NO vertical scrolling (build 221 fit law):
 * 8 pad + 107 carousel + 10 + 17 slider header + 4 + 32 slider row + 10 + 37 pills
 * + 6 pad = 231 dp of content in the sheet's 230.9 dp at 360x800 — the reference's
 * generous 28/26 dp section gaps are compressed to 10 dp for exactly this reason.
 * Every change is applied live to the layer, so the canvas above shows it straight away.
 *
 * Inventory note (build 221): the old sheet's in-panel header row (back + value text +
 * diamond + easing button) and its reset row are gone BY DESIGN of the reference —
 * back lives in the overlay's timescale row (and swipe-down / Back), the value in the
 * slider header, the mode name under the selected thumbnail, the diamond and the graph
 * are the slider-row end buttons, and Reset blending lives in the overlay header bar
 * (non-pager state, same keyframe-dropping operation the old sheet reset ran) and in
 * the selected header's More menu (pager state, build 219).
 */
@Composable
fun BlendEditSheet(
    /** The layer as it looks at the playhead (keyframes applied). */
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    modifier: Modifier = Modifier,
    /** The stored layer (with its keyframes), used for the diamond and the easing curve. */
    rawClip: TimelineClip = clip,
    currentPlayheadMs: Long = 0L,
    /** Diamond: add / remove an opacity keyframe at the playhead. */
    onToggleKeyframes: (List<AnimProp>) -> Unit = {},
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> }
) {
    val opacityProps = remember { listOf(AnimProp.OPACITY) }
    val relMs = currentPlayheadMs - rawClip.startMs
    val insideClip = currentPlayheadMs >= rawClip.startMs && currentPlayheadMs <= rawClip.endMs
    val opKeys = rawClip.keyframes[AnimProp.OPACITY].orEmpty()
    val onKeyframeNow = opKeys.indexOnTime(relMs) >= 0
    val hasKeyframes = opKeys.isNotEmpty()
    val segIdx = opKeys.segmentIndexAt(relMs)
    val easingEnabled = segIdx >= 0
    var showEasing by remember { mutableStateOf(false) }
    LaunchedEffect(easingEnabled) { if (!easingEnabled) showEasing = false }
    val segStart = if (segIdx >= 0) opKeys[segIdx] else null
    val segEnd = if (segIdx >= 0) opKeys[segIdx + 1] else null
    val easingOpen = showEasing && easingEnabled && segStart != null && segEnd != null

    var category by remember(clip.id) {
        mutableStateOf(BlendModes.find(clip.blendMode).category)
    }
    val current = BlendModes.find(clip.blendMode)
    // An outside change of the mode (e.g. Reset blending) pulls the carousel to the
    // mode's own category; picking a mode or browsing categories never triggers this,
    // because the picked mode always sits inside the browsed category.
    LaunchedEffect(current.name) {
        if (BlendModes.inCategory(category).none { it.name == current.name }) {
            category = current.category
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmCornerPanel))
            .background(AmBase)
            // swallow touches so nothing underneath can be hit
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(top = 8.dp, bottom = 6.dp)
            .testTag("blend_edit_sheet")
    ) {
        if (easingOpen) {
            // The easing graph takes the carousel + slider-header zone (134 dp), so the
            // panel's total height — and the fit — is unchanged while it is open.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(134.dp)
                    .padding(horizontal = 12.dp)
            ) {
                EasingEditor(
                    easing = segStart!!.easing,
                    progress = ((relMs - segStart.timeMs).toFloat() /
                        (segEnd!!.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                    onBegin = onEasingBegin,
                    onChange = { e -> onEasingChange(AnimProp.OPACITY, segStart.timeMs, e) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(4.dp))
        } else {
            // ---- Mode carousel of the active category --------------------------------
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("blend_modes_row"),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(BlendModes.inCategory(category), key = { it.name }) { spec ->
                    BlendModeItem(
                        spec = spec,
                        selected = spec.name == current.name,
                        onClick = { onUpdate { it.copy(blendMode = spec.name) } }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Slider header: 0 — value — 100 ---------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("0", color = BlendMuted, fontSize = 12.sp)
                Text(
                    "${clip.opacity.roundToInt()}",
                    color = BlendGreen,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("blend_opacity_value")
                )
                Text("100", color = BlendMuted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))
        }

        // ---- Slider row: keyframe | opacity slider | graph ----------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyframeButton221(
                enabled = insideClip,
                hasKeyframes = hasKeyframes,
                onKeyframeNow = onKeyframeNow,
                onClick = { onToggleKeyframes(opacityProps) }
            )
            Spacer(Modifier.width(12.dp))
            BlendOpacitySlider(
                value = clip.opacity,
                onValueChange = { v -> onUpdate { it.copy(opacity = v) } },
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .testTag("blend_opacity_slider")
            )
            Spacer(Modifier.width(12.dp))
            GraphButton221(
                enabled = easingEnabled,
                open = easingOpen,
                easing = if (easingEnabled) segStart?.easing else null,
                onClick = { if (easingEnabled) showEasing = !showEasing }
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---- Category pills -----------------------------------------------------------
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(BlendModes.categories, key = { it }) { name ->
                val on = name == category
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) BlendGreen else BlendSurface)
                        .bouncyClickable(pressedScale = 0.95f) { category = name }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag("blend_tab_$name"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        color = if (on) BlendInk else BlendIconTint,
                        fontSize = 13.5.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// =====================================================================================
// One carousel item: 82 dp thumbnail (real blend result) + mode name under it.
// Selected: the thumbnail turns circular, dims to 62 % and gains the green check badge.
// =====================================================================================

@Composable
private fun BlendModeItem(
    spec: BlendSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    val thumb = remember(spec.name) { BlendThumbs.bitmapFor(spec) }
    Column(
        modifier = Modifier
            .width(82.dp)
            .bouncyClickable(pressedScale = 0.95f, onClick = onClick)
            .testTag("blend_mode_${spec.name}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(82.dp)) {
            val shape = if (selected) CircleShape else RoundedCornerShape(16.dp)
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = spec.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(AmCardSurface, shape)
            )
            if (selected) {
                // The reference dims the chosen thumbnail to 62 % brightness — a black
                // veil at 38 % over the (opaque) picture is exactly that multiply.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(BlendGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check, "Selected",
                        tint = BlendCheckInk, modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            spec.name,
            color = if (selected) Color.White else BlendMuted,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// =====================================================================================
// Opacity slider (build 221): 5 dp track, green fill, 20 dp thumb with the dark
// 3 dp border and the soft green ring of the reference; the thumb grows while dragged.
// =====================================================================================

@Composable
private fun BlendOpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestChange by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val thumbR = 10.dp.toPx()
                fun toValue(x: Float): Float {
                    val span = (size.width - 2f * thumbR).coerceAtLeast(1f)
                    return (((x - thumbR) / span).coerceIn(0f, 1f) * 100f).roundToInt().toFloat()
                }
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                dragging = true
                latestChange(toValue(down.position.x))
                try {
                    drag(down.id) { change ->
                        latestChange(toValue(change.position.x))
                        change.consume()
                    }
                } finally {
                    dragging = false
                }
            }
        }
    ) {
        val thumbR = 10.dp.toPx()
        val y = size.height / 2f
        val x = thumbR + (size.width - 2f * thumbR) * (value / 100f).coerceIn(0f, 1f)
        drawLine(
            color = BlendTrack,
            start = Offset(thumbR, y), end = Offset(size.width - thumbR, y),
            strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round
        )
        drawLine(
            color = BlendGreen,
            start = Offset(thumbR, y), end = Offset(x, y),
            strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round
        )
        val r = thumbR * if (dragging) 1.15f else 1f
        // soft outer ring (the reference's 1 px green glow line)
        drawCircle(
            color = BlendGreen.copy(alpha = 0.45f),
            radius = r + 0.5.dp.toPx(),
            center = Offset(x, y),
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(color = BlendInk, radius = r, center = Offset(x, y))
        drawCircle(color = BlendGreen, radius = r - 3.dp.toPx(), center = Offset(x, y))
    }
}

// =====================================================================================
// Slider-row end buttons (Sarowar's addition): keyframe diamond on the left,
// easing graph on the right — the app's standard keyframe / easing behaviour.
// =====================================================================================

@Composable
private fun KeyframeButton221(
    enabled: Boolean,
    hasKeyframes: Boolean,
    onKeyframeNow: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bouncyClickable { if (enabled) onClick() }
            .testTag("blend_keyframe_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val c = center
            val r = 8.5.dp.toPx()
            val corners = listOf(
                Offset(c.x, c.y - r), Offset(c.x + r, c.y),
                Offset(c.x, c.y + r), Offset(c.x - r, c.y)
            )
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(corners[0].x, corners[0].y)
                for (i in 1..3) lineTo(corners[i].x, corners[i].y)
                close()
            }
            when {
                onKeyframeNow -> drawPath(path, BlendGreen)
                hasKeyframes -> {
                    drawPath(path, BlendGreen, style = Stroke(width = 1.5.dp.toPx()))
                    drawPlus(c, BlendGreen)
                }
                else -> {
                    drawPath(path, BlendTrack)
                    drawPlus(c, BlendMuted)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlus(
    c: Offset,
    color: Color
) {
    val arm = 3.5.dp.toPx()
    drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.5.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.5.dp.toPx(), StrokeCap.Round)
}

@Composable
private fun GraphButton221(
    enabled: Boolean,
    open: Boolean,
    easing: Easing?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (open) BlendSurface else Color.Transparent)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bouncyClickable { if (enabled) onClick() }
            .testTag("blend_graph_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            drawEasingIcon(
                easing = easing,
                color = when {
                    !enabled -> BlendMuted
                    open -> BlendGreen
                    else -> Color.White
                },
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
