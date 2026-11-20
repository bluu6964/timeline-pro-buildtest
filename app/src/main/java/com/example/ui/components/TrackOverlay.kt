package com.example.ui.components

import com.example.ui.util.bouncyClickable
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmCornerCard
import androidx.compose.ui.graphics.RectangleShape
import com.example.engine.UiMathNative
import com.example.engine.VectorCmdBuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The circle that selects the object to track, as fractions of the canvas frame:
 * [cx] of its width, [cy] of its height, [r] (radius) of its width.
 */
data class TrackCircle(val cx: Float = 0.5f, val cy: Float = 0.5f, val r: Float = 0.12f) {
    fun resized(factor: Float) = copy(r = (r * factor).coerceIn(MIN_R, MAX_R))

    companion object {
        const val MIN_R = 0.03f
        const val MAX_R = 0.45f
    }
}

/**
 * Drawn on top of the canvas frame while the user chooses what to track: tap to put the circle
 * somewhere, drag to move it, pinch (or the - / + buttons) to make it smaller / bigger.
 */
@Composable
fun BoxScope.TrackSetupOverlay(
    circle: TrackCircle,
    onCircle: (TrackCircle) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    /** The layer also turns / zooms with the object (off = it only moves). */
    followRotation: Boolean = false,
    onFollowRotation: (Boolean) -> Unit = {},
    followScale: Boolean = false,
    onFollowScale: (Boolean) -> Unit = {}
) {
    val circleNow by rememberUpdatedState(circle)
    val onCircleNow by rememberUpdatedState(onCircle)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = boxSize.width.toFloat()
                    val h = boxSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        onCircleNow(circleNow.copy(cx = (pos.x / w).coerceIn(0f, 1f), cy = (pos.y / h).coerceIn(0f, 1f)))
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val w = boxSize.width.toFloat()
                    val h = boxSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        val c = circleNow
                        onCircleNow(
                            c.copy(
                                cx = (c.cx + pan.x / w).coerceIn(0f, 1f),
                                cy = (c.cy + pan.y / h).coerceIn(0f, 1f),
                                r = (c.r * zoom).coerceIn(TrackCircle.MIN_R, TrackCircle.MAX_R)
                            )
                        )
                    }
                }
            }
            .testTag("track_setup_overlay")
    ) {
        NativeVectorCanvas(
            modifier = Modifier.matchParentSize(),
            drawKey = circle,
            buildCmds = { w, h, d ->
                addTrackCircleCmds(circle.cx * w, circle.cy * h, circle.r * w, d)
            },
        )
    }

    // cancel (top left)
    RoundButton(
        modifier = Modifier.align(Alignment.TopStart).padding(5.dp).testTag("track_cancel"),
        onClick = onCancel
    ) { Icon(Icons.Default.Close, contentDescription = "Cancel tracking", tint = Color.White, modifier = Modifier.size(15.dp)) }

    // start (top right)
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF4C8DFF))
            .clickable(onClick = onStart)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("track_start"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text("Track", color = Color.White, fontSize = 11.5.sp, modifier = Modifier.padding(start = 3.dp))
    }

    // turn / zoom switches (bottom right)
    Row(
        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FollowChip("Rotate", followRotation, { onFollowRotation(!followRotation) }, Modifier.testTag("track_follow_rotation"))
        Box(Modifier.size(5.dp))
        FollowChip("Scale", followScale, { onFollowScale(!followScale) }, Modifier.testTag("track_follow_scale"))
    }

    // smaller / bigger (bottom left)
    Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(
            modifier = Modifier.testTag("track_smaller"),
            onClick = { onCircleNow(circleNow.resized(0.85f)) }
        ) { Icon(Icons.Default.Remove, contentDescription = "Smaller circle", tint = Color.White, modifier = Modifier.size(15.dp)) }
        Box(Modifier.size(5.dp))
        RoundButton(
            modifier = Modifier.testTag("track_bigger"),
            onClick = { onCircleNow(circleNow.resized(1.18f)) }
        ) { Icon(Icons.Default.Add, contentDescription = "Bigger circle", tint = Color.White, modifier = Modifier.size(15.dp)) }
    }
}

/** Dashed white circle with a centre dot (setup and live tracking look the same). */
private fun DrawScope.drawTrackCircle(centre: Offset, radius: Float) {
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius, center = centre, style = Stroke(4.dp.toPx()))
    drawCircle(
        Color.White, radius = radius, center = centre,
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())))
    )
    drawCircle(Color.White, radius = 3.dp.toPx(), center = centre)
}

/**
 * What the tracker sees right now, as fractions of the canvas frame: circle centre ([cx] of the
 * width, [cy] of the height), radius [r] (of the width) and the followed corners [pts] as x, y, x, y ...
 */
class TrackLive(val cx: Float, val cy: Float, val r: Float, val pts: FloatArray)

private fun VectorCmdBuilder.addTrackCircleCmds(cx: Float, cy: Float, radius: Float, d: Float) {
    if (radius <= 0f) return
    val steps = 48
    val xs = FloatArray(steps)
    val ys = FloatArray(steps)
    for (i in 0 until steps) {
        val a = (i.toFloat() / steps) * (Math.PI * 2.0)
        xs[i] = cx + kotlin.math.cos(a).toFloat() * radius
        ys[i] = cy + kotlin.math.sin(a).toFloat() * radius
    }
    addPolyline(xs, ys, steps, closed = true, width = 4f * d, argb = 0x59000000)
    addPolyline(xs, ys, steps, closed = true, width = 2f * d, argb = android.graphics.Color.WHITE, dashOn = 10f * d, dashOff = 7f * d)
    addCircle(cx, cy, 3f * d, android.graphics.Color.WHITE)
}

/** Draws the moving circle and the followed corners (green) while a track runs. No touch handling. */
@Composable
fun BoxScope.TrackRunOverlay(live: TrackLive?) {
    NativeVectorCanvas(
        modifier = Modifier
            .matchParentSize()
            .testTag("track_run_overlay"),
        drawKey = live,
        buildCmds = { w, h, d ->
            val l = live ?: return@NativeVectorCanvas
            addTrackCircleCmds(l.cx * w, l.cy * h, l.r * w, d)
            var i = 0
            while (i + 1 < l.pts.size) {
                addCircle(l.pts[i] * w, l.pts[i + 1] * h, 2f * d, 0xFF39FF14.toInt())
                i += 2
            }
        },
    )
}

@Composable
private fun FollowChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Color(0xFF4C8DFF) else Color(0x99000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.5.sp)
    }
}

@Composable
private fun RoundButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xCC1B2230))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** A layer that can be attached to the tracked object. */
data class TrackTarget(
    val id: String,
    val label: String,
    /** A video / photo layer: it can also be pinned on a flat surface (planar track, Mocha-style). */
    val canPin: Boolean = false,
    /** It is already pinned (the pin can be removed). */
    val pinned: Boolean = false
)

/** Asks which layer should follow the tracked object. */
@Composable
fun TrackLayerPickDialog(
    targets: List<TrackTarget>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Planar track + corner pin for a video / photo layer (null = not offered). */
    onPickPin: ((String) -> Unit)? = null,
    onRemovePin: ((String) -> Unit)? = null,
    /** Camera track: the video itself moves so the tracked part stays in the middle of the screen (null = not offered). */
    onPickCamera: (() -> Unit)? = null,
    /** The video already has a track on itself (camera track): it can be removed. */
    onRemoveCamera: (() -> Unit)? = null
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AmCornerCard),
        title = { Text("Which layer should follow the object?") },
        text = {
            Column {
                if (onPickCamera != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(AmCardSurface, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .bouncyClickable(onClick = onPickCamera)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("track_camera")
                        ) {
                            Text("Camera track (keep it in the center)", color = TextPrimary, fontSize = 12.sp)
                        }
                        if (onRemoveCamera != null) {
                            DialogSecondaryButton(
                                text = "Remove",
                                onClick = onRemoveCamera,
                                tag = "track_camera_remove"
                            )
                        }
                    }
                    Text(
                        "Camera track needs no layer: the video moves so the part you circle stays exactly in the middle of the screen.",
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                if (targets.isEmpty()) {
                    Text(
                        "There is no other layer yet. Add a text, shape or sticker layer first, then press Track again.",
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        "Pick the layer that will move with the object. It follows the object frame by frame (no keyframes); an earlier track on that layer is replaced. \"Pin\" (video / photo layers) glues the layer onto a flat surface - a screen, poster or wall - with perspective, like Mocha's corner pin.",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        targets.forEach { t ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(AmCardSurface, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .bouncyClickable { onPick(t.id) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .testTag("track_target_" + t.id)
                                ) {
                                    Text(t.label + if (t.pinned) " (pinned)" else "", color = TextPrimary, fontSize = 12.sp)
                                }
                                if (t.canPin && onPickPin != null) {
                                    DialogSecondaryButton(
                                        text = "Pin",
                                        onClick = { onPickPin(t.id) },
                                        tag = "track_pin_" + t.id
                                    )
                                }
                                if (t.pinned && onRemovePin != null) {
                                    DialogSecondaryButton(
                                        text = "Unpin",
                                        onClick = { onRemovePin(t.id) },
                                        tag = "track_unpin_" + t.id
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { DialogSecondaryButton(text = "Close", onClick = onDismiss) }
    )
}

// ---- Planar track (Mocha-style corner pin) --------------------------------------------------------------------

/**
 * The 4 corners of the surface to track, as fractions of the canvas frame (x of its width, y of its height):
 * top-left, top-right, bottom-right, bottom-left.
 */
data class PlanarQuad(
    val c: List<Offset> = listOf(Offset(0.25f, 0.3f), Offset(0.75f, 0.3f), Offset(0.75f, 0.7f), Offset(0.25f, 0.7f))
) {
    fun withCorner(i: Int, p: Offset) = PlanarQuad(c.mapIndexed { k, o -> if (k == i) Offset(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f)) else o })

    fun toFloatArray(): FloatArray = FloatArray(8).also { arr ->
        for (i in 0 until 4) {
            val o = c.getOrElse(i) { Offset.Zero }
            arr[i * 2] = o.x
            arr[i * 2 + 1] = o.y
        }
    }

    fun moved(d: Offset): PlanarQuad {
        if (c.size >= 4) {
            val out = FloatArray(8)
            UiMathNative.nativePlanarMove(toFloatArray(), d.x, d.y, out)
            return PlanarQuad(List(4) { i -> Offset(out[i * 2], out[i * 2 + 1]) })
        }
        return this
    }

    /** Is the point inside (convex quad test)? */
    fun contains(p: Offset): Boolean {
        if (c.size >= 4) {
            return UiMathNative.nativePlanarHitTest(p.x, p.y, toFloatArray(), 0f) == 4
        }
        return false
    }
}

/** What the planar tracker sees right now, as fractions of the canvas frame: [quad] = x, y x4 (8 floats), [pts] = x, y, x, y ... */
class PlanarLive(val quad: FloatArray, val pts: FloatArray)

private fun DrawScope.drawPlanarQuad(q: List<Offset>, handles: Boolean, dim: Boolean = false) {
    if (q.size < 4) return
    val px = q.map { Offset(it.x * size.width, it.y * size.height) }
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(px[0].x, px[0].y)
        for (i in 1 until 4) lineTo(px[i].x, px[i].y)
        close()
    }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(5.dp.toPx()))
    drawPath(path, if (dim) Color(0xFF39FF14) else Color(0xFFFFD54F), style = Stroke(2.dp.toPx()))
    // a 3 x 3 grid so the perspective of the surface is visible
    fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    for (k in 1..2) {
        val t = k / 3f
        val col = Color.White.copy(alpha = 0.45f)
        drawLine(col, lerp(px[0], px[1], t), lerp(px[3], px[2], t), strokeWidth = 1.dp.toPx())
        drawLine(col, lerp(px[0], px[3], t), lerp(px[1], px[2], t), strokeWidth = 1.dp.toPx())
    }
    if (handles) {
        px.forEach {
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 11.dp.toPx(), center = it)
            drawCircle(Color(0xFFFFD54F), radius = 8.dp.toPx(), center = it, style = Stroke(2.5.dp.toPx()))
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = it)
        }
    }
}

private fun VectorCmdBuilder.addPlanarQuadCmds(
    q: List<Offset>, w: Float, h: Float, d: Float, handles: Boolean, dim: Boolean = false
) {
    if (q.size < 4) return
    val xs = FloatArray(4) { q[it].x * w }
    val ys = FloatArray(4) { q[it].y * h }
    addPolyline(xs, ys, 4, closed = true, width = 5f * d, argb = 0x59000000)
    addPolyline(xs, ys, 4, closed = true, width = 2f * d, argb = if (dim) 0xFF39FF14.toInt() else 0xFFFFD54F.toInt())
    fun lx(i: Int, j: Int, t: Float) = xs[i] + (xs[j] - xs[i]) * t
    fun ly(i: Int, j: Int, t: Float) = ys[i] + (ys[j] - ys[i]) * t
    val gridCol = 0x73FFFFFF
    for (k in 1..2) {
        val t = k / 3f
        addLine(lx(0, 1, t), ly(0, 1, t), lx(3, 2, t), ly(3, 2, t), 1f * d, gridCol)
        addLine(lx(0, 3, t), ly(0, 3, t), lx(1, 2, t), ly(1, 2, t), 1f * d, gridCol)
    }
    if (handles) {
        for (i in 0 until 4) {
            addCircle(xs[i], ys[i], 11f * d, 0x73000000)
            addCircle(xs[i], ys[i], 8f * d, 0, 2.5f * d, 0xFFFFD54F.toInt())
            addCircle(xs[i], ys[i], 2.5f * d, android.graphics.Color.WHITE)
        }
    }
}

/**
 * Drawn on top of the canvas frame while the user chooses the surface to pin a layer on: drag a corner handle to move
 * that corner (put the 4 corners on the 4 corners of the screen / poster), drag inside to move all four.
 */
@Composable
fun BoxScope.PlanarSetupOverlay(
    quad: PlanarQuad,
    onQuad: (PlanarQuad) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    /** Text of the blue button (top right) and of the hint (bottom). */
    startLabel: String = "Track",
    hint: String = "Drag the 4 corners onto the flat surface"
) {
    val quadNow by rememberUpdatedState(quad)
    val onQuadNow by rememberUpdatedState(onQuad)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                var grab = -1 // 0..3 = a corner, 4 = the whole quad, -1 = nothing
                val grabRadius = 40.dp.toPx()
                detectDragGestures(
                    onDragStart = { pos ->
                        val w = boxSize.width.toFloat()
                        val h = boxSize.height.toFloat()
                        grab = -1
                        if (w > 0f && h > 0f) {
                            if (quadNow.c.size >= 4) {
                                grab = UiMathNative.nativePlanarHitTest(
                                    pos.x / w, pos.y / h, quadNow.toFloatArray(), grabRadius / minOf(w, h)
                                )
                            }
                        }
                    },
                    onDrag = { change, amount ->
                        val w = boxSize.width.toFloat()
                        val h = boxSize.height.toFloat()
                        if (w > 0f && h > 0f && grab >= 0) {
                            change.consume()
                            val q = quadNow
                            if (grab == 4) {
                                onQuadNow(q.moved(Offset(amount.x / w, amount.y / h)))
                            } else {
                                val o = q.c[grab]
                                onQuadNow(q.withCorner(grab, Offset(o.x + amount.x / w, o.y + amount.y / h)))
                            }
                        }
                    }
                )
            }
            .testTag("planar_setup_overlay")
    ) {
        NativeVectorCanvas(
            modifier = Modifier.matchParentSize(),
            drawKey = quad,
            buildCmds = { w, h, d -> addPlanarQuadCmds(quad.c, w, h, d, handles = true) },
        )
    }

    RoundButton(
        modifier = Modifier.align(Alignment.TopStart).padding(5.dp).testTag("planar_cancel"),
        onClick = onCancel
    ) { Icon(Icons.Default.Close, contentDescription = "Cancel tracking", tint = Color.White, modifier = Modifier.size(15.dp)) }

    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF4C8DFF))
            .clickable(onClick = onStart)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("planar_start"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(startLabel, color = Color.White, fontSize = 11.5.sp, modifier = Modifier.padding(start = 3.dp))
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(hint, color = Color.White, fontSize = 10.5.sp)
    }
}

/** Draws the moving surface (4 corners) and the followed points (green) while a planar track runs. No touch handling. */
@Composable
fun BoxScope.PlanarRunOverlay(live: PlanarLive?) {
    NativeVectorCanvas(
        modifier = Modifier
            .matchParentSize()
            .testTag("planar_run_overlay"),
        drawKey = live,
        buildCmds = { w, h, d ->
            val l = live ?: return@NativeVectorCanvas
            if (l.quad.size >= 8) {
                addPlanarQuadCmds(List(4) { Offset(l.quad[2 * it], l.quad[2 * it + 1]) }, w, h, d, handles = false, dim = true)
            }
            var i = 0
            while (i + 1 < l.pts.size) {
                addCircle(l.pts[i] * w, l.pts[i + 1] * h, 2f * d, 0xFF39FF14.toInt())
                i += 2
            }
        },
    )
}

// ---- Remove (clean patch over a flaw on a flat surface) -----------------------------------------------------------

/**
 * What the user painted over the flaw: strokes (each a list of points, fractions of the canvas frame: x of its width, y of
 * its height) and the brush radius ([brush], a fraction of the frame's width).
 */
data class RemovePaint(val strokes: List<List<Offset>> = emptyList(), val brush: Float = 0.04f) {
    val isEmpty: Boolean get() = strokes.isEmpty()

    fun resized(factor: Float) = copy(brush = (brush * factor).coerceIn(MIN_BRUSH, MAX_BRUSH))

    fun begin(p: Offset) = copy(strokes = strokes + listOf(listOf(p)))

    fun extend(p: Offset): RemovePaint =
        if (strokes.isEmpty()) begin(p) else copy(strokes = strokes.dropLast(1) + listOf(strokes.last() + p))

    fun undone() = copy(strokes = strokes.dropLast(1))

    companion object {
        const val MIN_BRUSH = 0.008f
        const val MAX_BRUSH = 0.2f
    }
}

/**
 * Second step of Remove (the 4 corners are already on the surface): paint over the flaw with a finger. The brush size has
 * - / + buttons, "Undo" takes the last stroke back, "Clear" all. [useFrames] = also take what other frames show there.
 */
@Composable
fun BoxScope.RemoveMaskOverlay(
    paint: RemovePaint,
    onPaint: (RemovePaint) -> Unit,
    quad: PlanarQuad,
    useFrames: Boolean,
    onUseFrames: (Boolean) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val paintNow by rememberUpdatedState(paint)
    val onPaintNow by rememberUpdatedState(onPaint)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = boxSize.width.toFloat()
                    val h = boxSize.height.toFloat()
                    if (w > 0f && h > 0f) onPaintNow(paintNow.begin(Offset((pos.x / w).coerceIn(0f, 1f), (pos.y / h).coerceIn(0f, 1f))))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val w = boxSize.width.toFloat()
                        val h = boxSize.height.toFloat()
                        if (w > 0f && h > 0f) onPaintNow(paintNow.begin(Offset((pos.x / w).coerceIn(0f, 1f), (pos.y / h).coerceIn(0f, 1f))))
                    },
                    onDrag = { change, _ ->
                        val w = boxSize.width.toFloat()
                        val h = boxSize.height.toFloat()
                        if (w > 0f && h > 0f) {
                            change.consume()
                            onPaintNow(
                                paintNow.extend(
                                    Offset((change.position.x / w).coerceIn(0f, 1f), (change.position.y / h).coerceIn(0f, 1f))
                                )
                            )
                        }
                    }
                )
            }
            .testTag("remove_mask_overlay")
    ) {
        NativeVectorCanvas(
            modifier = Modifier.matchParentSize(),
            drawKey = Pair(quad, paint),
            buildCmds = { w, h, d ->
                addPlanarQuadCmds(quad.c, w, h, d, handles = false, dim = true)
                val col = 0x99FF3B5C.toInt()
                val r = paint.brush * w
                paint.strokes.forEach { s ->
                    if (s.isEmpty()) return@forEach
                    if (s.size == 1) {
                        addCircle(s[0].x * w, s[0].y * h, r, col)
                    } else {
                        val xs = FloatArray(s.size) { s[it].x * w }
                        val ys = FloatArray(s.size) { s[it].y * h }
                        addPolyline(xs, ys, s.size, closed = false, width = 2f * r, argb = col, capRound = true)
                    }
                }
            },
        )
    }

    // cancel and "back to the corners" (top left)
    Row(
        modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(modifier = Modifier.testTag("remove_cancel"), onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Box(Modifier.size(5.dp))
        FollowChip("Corners", false, onBack, Modifier.testTag("remove_back"))
    }

    // go (top right)
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF4C8DFF))
            .clickable(onClick = onStart)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("remove_start"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text("Remove", color = Color.White, fontSize = 11.5.sp, modifier = Modifier.padding(start = 3.dp))
    }

    // hint
    if (paint.isEmpty) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x99000000))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("Paint over the thing to remove", color = Color.White, fontSize = 10.5.sp)
        }
    }

    // brush size, undo, clear (bottom left)
    Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(modifier = Modifier.testTag("remove_smaller"), onClick = { onPaintNow(paintNow.resized(0.8f)) }) {
            Icon(Icons.Default.Remove, contentDescription = "Smaller brush", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Box(Modifier.size(5.dp))
        RoundButton(modifier = Modifier.testTag("remove_bigger"), onClick = { onPaintNow(paintNow.resized(1.25f)) }) {
            Icon(Icons.Default.Add, contentDescription = "Bigger brush", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Box(Modifier.size(5.dp))
        FollowChip("Undo", false, { onPaintNow(paintNow.undone()) }, Modifier.testTag("remove_undo"))
        Box(Modifier.size(5.dp))
        FollowChip("Clear", false, { onPaintNow(paintNow.copy(strokes = emptyList())) }, Modifier.testTag("remove_clear"))
    }

    // take what other frames show (bottom right)
    Row(
        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FollowChip("Other frames", useFrames, { onUseFrames(!useFrames) }, Modifier.testTag("remove_use_frames"))
    }
}
