package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.example.engine.UiGlRuntime
import com.example.engine.VectorCmdBuilder
import com.example.engine.VectorCmdSnapshot
import com.example.model.ClipMask
import com.example.model.MaskFrame
import com.example.model.MaskHandle
import com.example.model.MaskLayerMap
import com.example.model.MaskShape
import com.example.model.Pt
import com.example.model.TimelineClip
import com.example.model.sourceOffsetMs
import com.example.model.hitHandle
import com.example.ui.theme.NeonCyan
import kotlin.math.hypot

/** Everything a gesture needs, rebuilt on every recomposition (the gesture reads the newest one). */
private class MaskCtx(
    val clip: TimelineClip,
    val mask: ClipMask?,
    val rel: Long,
    val aspect: Float,
    val geo: FrameGeo,
    val map: MaskLayerMap,
    val hitPx: Float,
    val pen: Boolean
) {
    /** The layer's source time at [rel] (a tracked mask follows the object of that video frame). */
    val srcMs: Long get() = clip.trimStartMs + clip.sourceOffsetMs(rel.coerceAtLeast(0L))

    fun toScreen(p: Pt): Offset {
        val q = map.pictureToBox(p.x / aspect, p.y)
        return geo.map(q.x, q.y)
    }

    fun toPicture(o: Offset): Pt {
        val q = geo.unmap(o)
        val pp = map.boxToPicture(q.x, q.y)
        return Pt(pp.x * aspect, pp.y)
    }

    /** Picture units per screen pixel (the picture is 1 high). */
    val unitsPerPx: Float
        get() {
            val a = toScreen(Pt(0f, 0f))
            val b = toScreen(Pt(0f, 1f))
            val d = hypot(b.x - a.x, b.y - a.y)
            return if (d > 1e-3f) 1f / d else 1f
        }
}

/**
 * Edits the selected mask on the canvas (shown while the Masks panel is open): the outline of every mask, and for the
 * selected one corner / edge handles (resize), a round handle above it (turn), dots on a pen path (move; tap an edge to
 * add a point), and dragging inside moves it. While [penPoints] is not null the next taps place the points of a new pen
 * path. Put it in the canvas frame overlay: it covers the frame and takes over the touches there.
 */
@Composable
fun MaskEditOverlay(
    /** The layer as drawn at the playhead (keyframes / tracking applied). */
    clip: TimelineClip,
    playheadMs: Long,
    selectedId: String?,
    selectedPoint: Int,
    onSelectPoint: (Int) -> Unit,
    /** Points placed so far while drawing a pen path (picture coordinates 0..1), or null when not drawing. */
    penPoints: List<Pair<Float, Float>>?,
    onPenAdd: (Float, Float) -> Unit,
    onMaskChange: (ClipMask) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    var frameW by remember { mutableFloatStateOf(1f) }
    var frameH by remember { mutableFloatStateOf(1f) }
    val box = mediaVisibleSize(clip, frameW, frameH)
    val geo = FrameGeo(
        clip.copy(isFlippedH = false, isFlippedV = false), box.width, box.height, frameW, frameH, 0.01f, frameW, frameH
    )
    val map = MaskLayerMap.of(clip, box.width, box.height)
    val aspect = maskPictureAspect(clip)
    val rel = playheadMs - clip.startMs
    val mask = clip.masks.firstOrNull { it.id == selectedId }
    val ctx = MaskCtx(clip, mask, rel, aspect, geo, map, 26f * density, penPoints != null)
    val ctxNow by rememberUpdatedState(ctx)
    val changeNow by rememberUpdatedState(onMaskChange)
    val penNow by rememberUpdatedState(onPenAdd)
    val pointNow by rememberUpdatedState(onSelectPoint)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                frameW = it.width.toFloat()
                frameH = it.height.toFloat()
            }
    ) {
        val snap = buildMaskCmds(ctx, selectedPoint, density, penPoints)
        NativeGlTextureHost(
            isOpaque = false,
            drawOnGl = { sid -> UiGlRuntime.drawVectorCommandsOnGl(sid, 0, snap) },
            modifier = Modifier.fillMaxSize()
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("mask_edit_overlay")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val c = ctxNow
                        val slop = viewConfiguration.touchSlop
                        val p0 = c.toPicture(down.position)

                        if (c.pen) {
                            // a tap places a point
                            var moved = false
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.first()
                                ch.consume()
                                if (hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > slop * 2f) moved = true
                                if (!ch.pressed) break
                            }
                            if (!moved) penNow(p0.x / c.aspect, p0.y)
                            return@awaitEachGesture
                        }

                        val m = c.mask ?: return@awaitEachGesture
                        val state0 = m.shownStateAt(c.rel, c.srcMs)
                        val frame = MaskFrame(m, state0, c.aspect)
                        val radius = c.hitPx * c.unitsPerPx
                        var kind = frame.hitHandle(p0, radius)
                        if (kind == MaskHandle.NONE && frame.containsPoint(p0, radius * 0.6f)) kind = MaskHandle.BODY
                        if (kind >= MaskHandle.POINT) pointNow(kind - MaskHandle.POINT)

                        var moved = false
                        if (kind != MaskHandle.NONE) down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.first()
                            if (kind != MaskHandle.NONE) ch.consume()
                            val d = hypot(ch.position.x - down.position.x, ch.position.y - down.position.y)
                            if (d > slop) moved = true
                            if (moved && kind != MaskHandle.NONE && ch.pressed) {
                                val p = c.toPicture(ch.position)
                                when {
                                    kind == MaskHandle.BODY ->
                                        changeNow(m.withShownStateAt(c.rel, c.srcMs, frame.movedBy(state0, p0, p)))
                                    kind == MaskHandle.ROTATE ->
                                        changeNow(m.withShownStateAt(c.rel, c.srcMs, frame.rotateTo(p)))
                                    kind >= MaskHandle.POINT ->
                                        changeNow(frame.dragPoint(kind - MaskHandle.POINT, p))
                                    kind >= MaskHandle.ROTATE -> {}
                                    kind >= MaskHandle.EDGE ->
                                        changeNow(m.withShownStateAt(c.rel, c.srcMs, frame.dragEdge(kind - MaskHandle.EDGE, p)))
                                    kind >= MaskHandle.CORNER ->
                                        changeNow(m.withShownStateAt(c.rel, c.srcMs, frame.dragCorner(kind - MaskHandle.CORNER, p)))
                                }
                            }
                            if (!ch.pressed) break
                        }
                        // a tap on an edge of a pen path adds a point there
                        if (!moved && kind == MaskHandle.NONE && m.shape == MaskShape.PATH) {
                            val ins = frame.insertPointNear(p0, radius)
                            if (ins != null) {
                                down.consume()
                                changeNow(ins.first)
                                pointNow(ins.second)
                            }
                        }
                    }
                }
        )
    }
}

private fun buildMaskCmds(
    ctx: MaskCtx,
    selectedPoint: Int,
    density: Float,
    penPoints: List<Pair<Float, Float>>?
): VectorCmdSnapshot {
    val b = VectorCmdBuilder(initialFloats = 512, initialColors = 64)
    val darkArgb = 0x73000000
    val whiteArgb = 0xFFFFFFFF.toInt()
    val whiteDimArgb = 0x8CFFFFFF.toInt()
    val strokeDarkArgb = 0xBF000000.toInt()
    val cyanArgb = NeonCyan.toArgb()

    for (m in ctx.clip.masks) {
        if (!m.enabled) continue
        val f = MaskFrame(m, m.shownStateAt(ctx.rel, ctx.srcMs), ctx.aspect)
        val sel = m.id == ctx.mask?.id
        val pts = f.outline()
        if (pts.size < 2) continue
        val outerW = (if (sel) 3.4f else 2.4f) * density
        val innerW = (if (sel) 1.5f else 1.0f) * density
        val innerCol = if (sel) whiteArgb else whiteDimArgb
        if (m.shape == MaskShape.MIRROR && pts.size >= 4) {
            val p0 = ctx.toScreen(pts[0])
            val p1 = ctx.toScreen(pts[1])
            val p2 = ctx.toScreen(pts[2])
            val p3 = ctx.toScreen(pts[3])
            b.addLine(p0.x, p0.y, p1.x, p1.y, outerW, darkArgb)
            b.addLine(p2.x, p2.y, p3.x, p3.y, outerW, darkArgb)
            b.addLine(p0.x, p0.y, p1.x, p1.y, innerW, innerCol)
            b.addLine(p2.x, p2.y, p3.x, p3.y, innerW, innerCol)
        } else {
            val xs = FloatArray(pts.size)
            val ys = FloatArray(pts.size)
            for (i in pts.indices) {
                val o = ctx.toScreen(pts[i])
                xs[i] = o.x
                ys[i] = o.y
            }
            b.addPolyline(xs, ys, pts.size, f.isOutlineClosed, outerW, darkArgb)
            b.addPolyline(xs, ys, pts.size, f.isOutlineClosed, innerW, innerCol)
        }
    }

    val m = ctx.mask
    if (m != null && m.enabled && !ctx.pen) {
        val f = MaskFrame(m, m.shownStateAt(ctx.rel, ctx.srcMs), ctx.aspect)
        if (m.shape != MaskShape.LINE) {
            val a = ctx.toScreen(f.edge(0))
            val rPos = ctx.toScreen(f.rotateHandle())
            b.addLine(a.x, a.y, rPos.x, rPos.y, 1.2f * density, whiteArgb)
        }
        val cs = ctx.toScreen(Pt(f.cx, f.cy))
        b.addCircle(cs.x, cs.y, 3.2f * density, darkArgb)
        b.addCircle(cs.x, cs.y, 2.0f * density, whiteArgb)
        for ((kind, pos) in f.handles()) {
            val o = ctx.toScreen(pos)
            val isPoint = kind >= MaskHandle.POINT
            val isSelPoint = isPoint && kind - MaskHandle.POINT == selectedPoint
            val r = (if (kind == MaskHandle.ROTATE) 7f else 5.5f) * density
            val fill = if (isSelPoint || kind == MaskHandle.ROTATE) cyanArgb else whiteArgb
            b.addCircle(o.x, o.y, r, fill, 1.4f * density, strokeDarkArgb)
        }
    }

    if (penPoints != null) {
        val pts = penPoints.map { ctx.toScreen(Pt(it.first * ctx.aspect, it.second)) }
        if (pts.size >= 2) {
            val xs = FloatArray(pts.size) { pts[it].x }
            val ys = FloatArray(pts.size) { pts[it].y }
            b.addPolyline(xs, ys, pts.size, false, 3.4f * density, darkArgb)
            b.addPolyline(xs, ys, pts.size, false, 1.5f * density, cyanArgb)
        }
        for ((i, o) in pts.withIndex()) {
            val fill = if (i == 0) cyanArgb else whiteArgb
            b.addCircle(o.x, o.y, 5.5f * density, fill, 1.4f * density, strokeDarkArgb)
        }
    }
    return b.build()
}
