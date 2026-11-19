package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.engine.CanvasGestureNative
import com.example.model.CropVisibleBox
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.model.cropVisibleBox
import com.example.model.depthFactor
import com.example.model.depthX
import com.example.model.depthY
import com.example.model.effectiveScalePercent
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
 * position by the C++ gesture machine (cpp/engine/canvas_gesture.cpp — build 234
 * removed the Kotlin gesture path; the machine reproduces the stored position
 * the old local-space conversion produced). During the drag, [onMove]
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
    val nativeHolder = remember { NativeCanvasGestureHolder() }
    DisposableEffect(nativeHolder) {
        onDispose { nativeHolder.dispose() }
    }

    Box(
        modifier = modifier
            .onSizeChanged { fw = it.width.toFloat(); fh = it.height.toFloat() }
            .pointerInput(Unit) {
                val doubleTapSlopPx = 100.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)

                    val nativeHandle = nativeHolder.handle
                    if (nativeHandle != 0L) {
                        CanvasGestureNative.nativeSetConfig(
                            nativeHandle,
                            viewConfiguration.touchSlop,
                            doubleTapSlopPx,
                            viewConfiguration.doubleTapTimeoutMillis,
                            viewConfiguration.longPressTimeoutMillis
                        )
                        val snapshot = clipsNow
                        val sizesSnap = sizesNow
                        val count = snapshot.size
                        if (count == 0) return@awaitEachGesture
                        val kinds = IntArray(count)
                        val geom = FloatArray(count * CanvasGestureNative.ITEM_FLOAT_STRIDE)
                        packCanvasItems(snapshot, density, sizesSnap, kinds, geom)

                        val pId = nativeHolder.pendingClipId
                        val pendingIdx = if (pId != null && nativeHolder.pendingToken != 0L) {
                            snapshot.indexOfFirst { it.id == pId }
                        } else {
                            -1
                        }
                        val captured = CanvasGestureNative.nativeOnDown(
                            nativeHandle,
                            down.position.x,
                            down.position.y,
                            down.uptimeMillis,
                            fw,
                            fh,
                            pendingIdx,
                            kinds,
                            geom
                        )
                        if (!captured) return@awaitEachGesture

                        var lastApplied: Pair<Float, Float>? = null
                        var released = false
                        var releaseTimeMs = 0L
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id }
                            if (c == null || c.isConsumed) {
                                cancelled = true
                                break
                            }
                            if (!c.pressed) {
                                released = event.type == PointerEventType.Release
                                releaseTimeMs = c.uptimeMillis
                                break
                            }
                            val hMove = nativeHolder.handle
                            if (hMove == 0L) break
                            val delta = c.positionChange()
                            val evs = CanvasGestureNative.nativeOnMove(hMove, delta.x, delta.y)
                            var consume = false
                            var ei = 0
                            while (ei + 4 < evs.size) {
                                val type = evs[ei].toInt()
                                val idx = evs[ei + 1].toInt()
                                val x = evs[ei + 2].toFloat()
                                val y = evs[ei + 3].toFloat()
                                val tok = evs[ei + 4].toLong()
                                when (type) {
                                    CanvasGestureNative.EV_CANCEL_SINGLE_TAP -> {
                                        if (nativeHolder.pendingToken == tok) {
                                            nativeHolder.clearPending()
                                        }
                                    }
                                    CanvasGestureNative.EV_SELECT -> {
                                        if (idx in snapshot.indices) selectNow(snapshot[idx].id)
                                    }
                                    CanvasGestureNative.EV_MOVE -> {
                                        if (idx in snapshot.indices) {
                                            lastApplied = moveNow(snapshot[idx].id, x, y)
                                        }
                                    }
                                    CanvasGestureNative.EV_CONSUME -> consume = true
                                }
                                ei += CanvasGestureNative.EVENT_STRIDE
                            }
                            if (consume) c.consume()
                        }

                        val hEnd = nativeHolder.handle
                        if (hEnd != 0L) {
                            val endEvs = if (cancelled) {
                                CanvasGestureNative.nativeOnCancel(hEnd)
                            } else {
                                CanvasGestureNative.nativeOnUp(hEnd, releaseTimeMs, released)
                            }
                            var ei = 0
                            while (ei + 4 < endEvs.size) {
                                val type = endEvs[ei].toInt()
                                val idx = endEvs[ei + 1].toInt()
                                val tok = endEvs[ei + 4].toLong()
                                when (type) {
                                    CanvasGestureNative.EV_COMMIT -> {
                                        if (idx in snapshot.indices) {
                                            lastApplied?.let { commitNow(snapshot[idx].id, it.first, it.second) }
                                        }
                                    }
                                    CanvasGestureNative.EV_CANCEL_SINGLE_TAP -> {
                                        if (nativeHolder.pendingToken == tok) {
                                            nativeHolder.clearPending()
                                        }
                                    }
                                    CanvasGestureNative.EV_DOUBLE_TAP -> {
                                        if (idx in snapshot.indices) doubleTapNow(snapshot[idx].id)
                                    }
                                    CanvasGestureNative.EV_ARM_SINGLE_TAP -> {
                                        if (idx in snapshot.indices) {
                                            val clipId = snapshot[idx].id
                                            nativeHolder.pendingJob?.cancel()
                                            nativeHolder.pendingToken = tok
                                            nativeHolder.pendingClipId = clipId
                                            nativeHolder.pendingJob = scope.launch {
                                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                                val hTimeout = nativeHolder.handle
                                                if (hTimeout != 0L && nativeHolder.pendingToken == tok) {
                                                    val tEvs = CanvasGestureNative.nativeOnSingleTapTimeout(hTimeout, tok)
                                                    if (tEvs.size >= CanvasGestureNative.EVENT_STRIDE &&
                                                        tEvs[0].toInt() == CanvasGestureNative.EV_SELECT
                                                    ) {
                                                        nativeHolder.pendingJob = null
                                                        nativeHolder.pendingToken = 0L
                                                        nativeHolder.pendingClipId = null
                                                        selectNow(clipId)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                ei += CanvasGestureNative.EVENT_STRIDE
                            }
                        }
                        return@awaitEachGesture
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



private class NativeCanvasGestureHolder {
    var handle: Long = CanvasGestureNative.nativeCreate().also {
        check(it != 0L) { "native canvas gesture machine could not be created" }
    }
    var pendingToken: Long = 0L
    var pendingClipId: String? = null
    var pendingJob: Job? = null

    fun clearPending() {
        pendingJob?.cancel()
        pendingJob = null
        pendingToken = 0L
        pendingClipId = null
    }

    fun dispose() {
        clearPending()
        val h = handle
        if (h != 0L) {
            handle = 0L
            CanvasGestureNative.nativeDestroy(h)
        }
    }
}

private fun packCanvasItems(
    clips: List<TimelineClip>,
    density: Float,
    textSizes: Map<String, Size>,
    kindsOut: IntArray,
    geomOut: FloatArray
) {
    val box = 130f * density
    for (i in clips.indices) {
        val clip = clips[i]
        val b = i * CanvasGestureNative.ITEM_FLOAT_STRIDE
        var boxW = box
        var boxH = box
        val vis: CropVisibleBox
        val kind: Int
        when (dragKindOf(clip)) {
            DragKind.BLOCKER -> {
                kind = CanvasGestureNative.KIND_BLOCKER
                vis = CropVisibleBox(box, box, 0f, 0f)
            }
            DragKind.SHAPE_FAMILY -> {
                kind = CanvasGestureNative.KIND_SHAPE_FAMILY
                vis = if (clip.type == LayerType.SHAPE) {
                    clip.cropVisibleBox(box, box) ?: CropVisibleBox(box, box, 0f, 0f)
                } else {
                    CropVisibleBox(box, box, 0f, 0f)
                }
            }
            DragKind.TEXT -> {
                val size = textSizes[clip.id]
                if (size == null || size.width <= 0f || size.height <= 0f) {
                    kindsOut[i] = CanvasGestureNative.KIND_INVALID
                    continue
                }
                kind = CanvasGestureNative.KIND_TEXT
                boxW = size.width
                boxH = size.height
                vis = clip.cropVisibleBox(size.width, size.height)
                    ?: CropVisibleBox(size.width, size.height, 0f, 0f)
            }
        }
        kindsOut[i] = kind
        geomOut[b + 0] = boxW
        geomOut[b + 1] = boxH
        geomOut[b + 2] = vis.width
        geomOut[b + 3] = vis.height
        geomOut[b + 4] = vis.offX
        geomOut[b + 5] = vis.offY
        geomOut[b + 6] = (clip.effectiveScalePercent / 100f).coerceAtLeast(0.01f)
        geomOut[b + 7] = clip.rotationDeg
        geomOut[b + 8] = clip.pivotX
        geomOut[b + 9] = clip.pivotY
        geomOut[b + 10] = if (clip.isFlippedH) 1f else 0f
        geomOut[b + 11] = if (clip.isFlippedV) 1f else 0f
        geomOut[b + 12] = clip.depthX
        geomOut[b + 13] = clip.depthY
        geomOut[b + 14] = clip.transformX
        geomOut[b + 15] = clip.transformY
        geomOut[b + 16] = clip.depthFactor
    }
}
