package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.model.CropVisibleBox
import com.example.model.DragMath
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.model.cropVisibleBox
import com.example.model.depthFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Build 225 — the fixed full-canvas gesture layer for TEXT and SHAPE-family layers
 * (the drag-shake fix, NEXT-BUILD-LIST item 2). It replaces the per-layer handlers
 * that used to sit on each layer's own touch box (TextLayerView's inner box and
 * CanvasArea's shape touch box): those boxes move WITH the layer, so every drag
 * amount was measured in a coordinate space that had itself just moved — a feedback
 * loop that showed up on the phone as shake, worst on text and shapes. Media layers
 * were steadier because MediaGestureLayer already measures this way; text and
 * shapes now measure the same way.
 *
 * Everything is measured here, in canvas pixels, accumulated from the drag-start
 * position (model/DragMath — the stored position the old local-space conversion
 * also produced; DragMathTest pins the equivalence). During the drag, [onMove]
 * reports the proposed stored position and returns the position actually applied
 * (CanvasArea snaps it and feeds the drag preview — no keyframe commit and no
 * Controller fan-out per event). At drag end, [onCommit] performs the ONE real
 * commit with the last applied position, exactly the write the old handlers'
 * final event made.
 *
 * Targeting is the build-224 targeting, reproduced in canvas space with the same
 * geometry the selection frame uses (FrameGeo, incl. the crop-visible box — build
 * 182/186: a touch on a cropped-away part of a shape / text layer does not hit it
 * and falls through to whatever is beneath):
 * - candidates are the overlay clips in draw order; the LAST drawn (topmost)
 *   whose touch box contains the point wins;
 * - a STICKER on top blocks: stickers keep their own handlers untouched, so the
 *   gesture is left unconsumed for them;
 * - touches nothing consumes here fall through to the media gesture layer below,
 *   exactly as they did when the handlers lived on the layers;
 * - handlers drawn above this layer (selection-frame handles, gradient handles)
 *   keep priority the same way they had it: they consume the changes, and this
 *   layer's loop stops on the first consumed change.
 *
 * Tap rules are the build-224 ones: a tap selects a TEXT layer (the select fires
 * after the double-tap window, as the old detectTapGestures pair did), a double
 * tap opens Text edit, a drag on a text layer selects it at drag start; the
 * shape family has no tap action and a shape drag does not select.
 */
@Composable
internal fun CanvasDragLayer(
    /** The overlay clips as drawn (resolved at the playhead), bottom lane first — draw order. */
    clips: List<TimelineClip>,
    /** Measured text boxes by clip id (the same map the snapper and the frame read). */
    textSizes: Map<String, Size>,
    onSelect: (String) -> Unit,
    onDoubleTap: (String) -> Unit,
    /** Live move during a drag: the proposed stored position; returns the applied (snapped) one. */
    onMove: (id: String, x: Float, y: Float) -> Pair<Float, Float>,
    /** Drag end: the one real commit, with the last applied position. */
    onCommit: (id: String, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var fw by remember { mutableFloatStateOf(1f) }
    var fh by remember { mutableFloatStateOf(1f) }
    val clipsNow by rememberUpdatedState(clips)
    val sizesNow by rememberUpdatedState(textSizes)
    val selectNow by rememberUpdatedState(onSelect)
    val doubleTapNow by rememberUpdatedState(onDoubleTap)
    val moveNow by rememberUpdatedState(onMove)
    val commitNow by rememberUpdatedState(onCommit)
    val scope = rememberCoroutineScope()
    val pending = remember { PendingTapHolder() }

    Box(
        modifier = modifier
            .onSizeChanged { fw = it.width.toFloat(); fh = it.height.toFloat() }
            .pointerInput(Unit) {
                val doubleTapSlopPx = 100.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)

                    // A pending single tap (see below) is armed as a possible double
                    // tap when this press lands inside the tapped layer's touch box,
                    // in time and near the first press — the old per-layer tap
                    // detector only ever saw presses on its own box, so a press
                    // elsewhere never disturbs it. The pending tap is NOT cancelled
                    // yet: what this press turns into decides (see the branches).
                    var armedClipId: String? = null
                    val pt = pending.tap
                    if (pt != null) {
                        val ptClip = clipsNow.firstOrNull { it.id == pt.clipId }
                        val ptGeo = ptClip?.let { dragGeoFor(it, fw, fh, density, sizesNow) }
                        if (ptGeo != null && ptGeo.contains(down.position) &&
                            down.uptimeMillis - pt.upTimeMs <= viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - pt.downPosition).getDistance() <= doubleTapSlopPx
                        ) {
                            armedClipId = pt.clipId
                        }
                    }
                    val armed = armedClipId

                    // Hit test, topmost first. A sticker on top blocks the gesture
                    // (its own handlers, lower in the tree, take it from here).
                    var hit: TimelineClip? = null
                    var hitIsText = false
                    var blocked = false
                    for (clip in clipsNow.asReversed()) {
                        val geo = dragGeoFor(clip, fw, fh, density, sizesNow) ?: continue
                        if (!geo.contains(down.position)) continue
                        when (dragKindOf(clip)) {
                            DragKind.BLOCKER -> blocked = true
                            DragKind.TEXT -> { hit = clip; hitIsText = true }
                            DragKind.SHAPE_FAMILY -> { hit = clip; hitIsText = false }
                        }
                        break
                    }
                    if (blocked || hit == null) return@awaitEachGesture
                    val target = hit

                    var total = Offset.Zero
                    var dragging = false
                    var lastApplied: Pair<Float, Float>? = null
                    var released = false
                    var releaseTimeMs = 0L
                    val startX = target.transformX
                    val startY = target.transformY
                    val df = target.depthFactor
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        // A handler above (selection frame / gradient handles) took
                        // this gesture: stop, like the old handlers did. A drag in
                        // flight still commits below — the old handlers' writes up
                        // to this point stood, and the commit is that same write.
                        if (c.isConsumed) break
                        if (!c.pressed) {
                            released = event.type == PointerEventType.Release
                            releaseTimeMs = c.uptimeMillis
                            break
                        }
                        total += c.positionChange()
                        if (!dragging) {
                            if (total.getDistance() > slop) {
                                dragging = true
                                // A second press that drags kills the pending tap
                                // (the old detector saw the press on its box and
                                // never fired the tap once it turned into a drag).
                                if (armed != null && pending.tap?.clipId == armed) {
                                    pending.tap?.job?.cancel()
                                    pending.tap = null
                                }
                                if (hitIsText) selectNow(target.id)
                                val pos = DragMath.draggedPosition(startX, startY, total.x, total.y, df)
                                lastApplied = moveNow(target.id, pos.first, pos.second)
                                c.consume()
                            }
                        } else {
                            val pos = DragMath.draggedPosition(startX, startY, total.x, total.y, df)
                            lastApplied = moveNow(target.id, pos.first, pos.second)
                            c.consume()
                        }
                    }

                    if (dragging) {
                        // The one real commit of the whole drag (also on cancel:
                        // the old handlers' per-event writes stood at cancel too).
                        lastApplied?.let { commitNow(target.id, it.first, it.second) }
                    } else if (released) {
                        val quickTap = releaseTimeMs - down.uptimeMillis <= viewConfiguration.longPressTimeoutMillis
                        if (quickTap && armed != null && pending.tap?.clipId == armed) {
                            // This press landed inside the pending layer's touch box
                            // and tapped: that layer's old tap detector saw the same
                            // press on its own box and called it a double tap —
                            // whichever layer sits on top of the spot.
                            pending.tap?.job?.cancel()
                            pending.tap = null
                            doubleTapNow(armed)
                            if (hitIsText && target.id != armed) {
                                // The layer actually on top registered its own
                                // first tap too (its detector saw the press as well).
                                schedulePendingTap(pending, scope, target.id, down.position, releaseTimeMs, viewConfiguration.doubleTapTimeoutMillis, selectNow)
                            }
                        } else if (quickTap && hitIsText) {
                            schedulePendingTap(pending, scope, target.id, down.position, releaseTimeMs, viewConfiguration.doubleTapTimeoutMillis, selectNow)
                        } else if (!quickTap && armed != null && pending.tap?.clipId == armed) {
                            // A second press on the pending layer's box, held too
                            // long to be a tap: the pending tap dies with it.
                            pending.tap?.job?.cancel()
                            pending.tap = null
                        }
                    }
                }
            }
    )
}

/** Which of the canvas's three overlay touch behaviours a clip had in build 224. */
private enum class DragKind { TEXT, SHAPE_FAMILY, BLOCKER }

private fun dragKindOf(clip: TimelineClip): DragKind = when {
    clip.type == LayerType.STICKER -> DragKind.BLOCKER
    clip.type == LayerType.SHAPE || clip.type == LayerType.NULL ||
        clip.type == LayerType.GROUP || clip.type == LayerType.CONTROLLER -> DragKind.SHAPE_FAMILY
    else -> DragKind.TEXT
}

/**
 * The clip's touch box in canvas space — the same FrameGeo the selection frame
 * hugs: the 130 dp box for the shape family and stickers, the measured box for
 * text, each clipped to its crop-visible part where Crop clips the box in place
 * (shape / text; stickers are drawn uncropped, Null / Group / Controller have no
 * cropped picture). Null when the box cannot be known yet (an unmeasured text
 * layer — the old code had no touch box before the first measure either).
 */
private fun dragGeoFor(
    clip: TimelineClip,
    fw: Float,
    fh: Float,
    density: Float,
    textSizes: Map<String, Size>
): FrameGeo? {
    val box = 130f * density
    return when (dragKindOf(clip)) {
        DragKind.BLOCKER -> FrameGeo(clip, box, box, fw, fh, 0.01f)
        DragKind.SHAPE_FAMILY -> {
            // cropVisibleBox is null only for a degenerate box; the fallback is
            // the whole box, the old touch box's own fallback.
            val vis = if (clip.type == LayerType.SHAPE) {
                clip.cropVisibleBox(box, box) ?: CropVisibleBox(box, box, 0f, 0f)
            } else {
                CropVisibleBox(box, box, 0f, 0f)
            }
            FrameGeo(clip, box, box, fw, fh, 0.01f, 0f, 0f, vis.width, vis.height, vis.offX, vis.offY)
        }
        DragKind.TEXT -> {
            val size = textSizes[clip.id] ?: return null
            if (size.width <= 0f || size.height <= 0f) return null
            val vis = clip.cropVisibleBox(size.width, size.height)
                ?: CropVisibleBox(size.width, size.height, 0f, 0f)
            FrameGeo(clip, size.width, size.height, fw, fh, 0.01f, 0f, 0f, vis.width, vis.height, vis.offX, vis.offY)
        }
    }
}

/**
 * Registers a text tap as pending: it selects its layer only once the double-tap
 * window has passed with no second press on the layer (the old detectTapGestures
 * pair held its tap back the same way).
 */
private fun schedulePendingTap(
    pending: PendingTapHolder,
    scope: kotlinx.coroutines.CoroutineScope,
    clipId: String,
    downPosition: Offset,
    upTimeMs: Long,
    timeoutMs: Long,
    onSelect: (String) -> Unit
) {
    val job = scope.launch {
        delay(timeoutMs)
        if (pending.tap?.clipId == clipId && pending.tap?.upTimeMs == upTimeMs) {
            pending.tap = null
            onSelect(clipId)
        }
    }
    pending.tap = PendingTap(clipId, downPosition, upTimeMs, job)
}

/** A text tap waiting out its double-tap window before it may select its layer. */
private class PendingTap(
    val clipId: String,
    val downPosition: Offset,
    val upTimeMs: Long,
    val job: Job
)

private class PendingTapHolder {
    var tap: PendingTap? = null
}
