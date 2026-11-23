package com.example.ui.components

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.NativeTimelineEngine
import com.example.engine.TL_EV_ANIMATE
import com.example.engine.TL_EV_ARM_LONG_PRESS
import com.example.engine.TL_EV_BEGIN
import com.example.engine.TL_EV_BEGIN_ADJUST
import com.example.engine.TL_EV_CANCEL_LONG_PRESS
import com.example.engine.TL_EV_CAPACITY
import com.example.engine.TL_EV_DELETE
import com.example.engine.TL_EV_ENTER_MULTI
import com.example.engine.TL_EV_FADE
import com.example.engine.TL_EV_MOVE_TIME
import com.example.engine.TL_EV_REDRAW
import com.example.engine.TL_EV_REORDER
import com.example.engine.TL_EV_SCRUB_START
import com.example.engine.TL_EV_SEEK
import com.example.engine.TL_EV_SELECT
import com.example.engine.TL_EV_TOGGLE_MULTI
import com.example.engine.TL_EV_TOGGLE_VISIBILITY
import com.example.engine.TL_EV_TRIM
import com.example.engine.TL_EV_ZOOM
import com.example.engine.TlSnapshotInput
import com.example.model.TimelineTrack

/**
 * Build 224 — the native timeline's Compose host: a SurfaceView in the exact
 * slot the Compose ruler + layer panel occupied, drawn by the C++ timeline
 * renderer (see engine/NativeTimeline.kt). All gestures are hit-tested
 * natively (the same layout math that draws) and performed through the
 * EXISTING ViewModel operations — the semantics mirror TimelineLayerPanel's
 * gesture owners one for one:
 *
 *  - tap a bar / rail cell: select (rail in multi mode: toggle check)
 *  - tap the eye: visibility toggle
 *  - tap a chain x / child x: the existing delete-by-id paths
 *  - horizontal swipe on ruler / lanes / a not-yet-long-pressed bar: scrub
 *    (seek, with the panel's fling decay), mostly-vertical swipe: lane scroll
 *  - long-press a bar, then drag: move in time (live) + lane reorder on release
 *  - drag a trim handle / fade knob: whole-ms steps through the same funnels
 *  - pinch: timeline zoom
 *
 * One deliberate difference: the Compose ruler was touch-inert (scrubbing
 * worked only on the lanes); here the ruler scrubs like the lanes, since it
 * is part of the same surface.
 */
@Composable
fun NativeTimelineSurface(
    tracks: List<TimelineTrack>,
    playheadMs: () -> Long,
    totalDurationMs: Long,
    selectedClipId: String?,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onClipSelect: (String?) -> Unit,
    onDeleteClipById: (String) -> Unit,
    onMoveClipInTime: (clipId: String, deltaMs: Long) -> Unit,
    onBeginClipTimeDrag: () -> Unit,
    onReorderLane: (fromIndex: Int, toIndex: Int) -> Unit,
    onTrimClipEdge: (clipId: String, isLeft: Boolean, deltaMs: Long) -> Unit,
    onToggleClipVisibility: (clipId: String) -> Unit,
    onSetClipFades: (clipId: String, fadeInMs: Long, fadeOutMs: Long) -> Unit,
    pxPerMs: Float,
    onPxPerMsChange: (Float) -> Unit,
    waveforms: Map<String, FloatArray>,
    multiSelectIds: Set<String>,
    onLongPressLane: (clipId: String) -> Unit,
    onToggleMulti: (clipId: String) -> Unit,
    beatMarksMs: List<Long>,
    exportInMs: Long?,
    exportOutMs: Long?,
    keyframeTimesMs: List<Long>,
    bottomPadDp: Float,
    comfortDensity: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val engine = remember { NativeTimelineEngine(context) }
    val machine = remember { TlGestureGlue() }
    val latestTracks = rememberUpdatedState(tracks)
    val latestPlayhead = rememberUpdatedState(playheadMs)

    DisposableEffect(Unit) {
        onDispose {
            machine.release()
            engine.release()
        }
    }

    // Build 232: redraw whenever the playhead moves. The timeline used to redraw only when the
    // composition re-ran (SideEffect below calls engine.update -> requestFrame). With a layer
    // selected something recomposes while scrubbing, so it looked fine; with NO layer selected
    // nothing recomposes, the canvas followed the seek but the timeline stayed still.
    // The playhead provider reads a Compose State, so snapshotFlow sees every change.
    androidx.compose.runtime.LaunchedEffect(engine) {
        androidx.compose.runtime.snapshotFlow { latestPlayhead.value() }
            .collect { engine.requestFrame() }
    }

    SideEffect {
        machine.engine = engine
        machine.tracks = latestTracks.value
        machine.playheadOf = { latestPlayhead.value() }
        machine.onSeekTo = onSeekTo
        machine.onScrubStart = onScrubStart
        machine.onClipSelect = onClipSelect
        machine.onDeleteClipById = onDeleteClipById
        machine.onMoveClipInTime = onMoveClipInTime
        machine.onBeginClipTimeDrag = onBeginClipTimeDrag
        machine.onReorderLane = onReorderLane
        machine.onTrimClipEdge = onTrimClipEdge
        machine.onToggleClipVisibility = onToggleClipVisibility
        machine.onSetClipFades = onSetClipFades
        machine.onPxPerMsChange = onPxPerMsChange
        machine.onLongPressLane = onLongPressLane
        machine.onToggleMulti = onToggleMulti

        engine.playheadOf = { latestPlayhead.value() }
        engine.update(
            TlSnapshotInput(
                tracks = tracks,
                selectedClipId = selectedClipId,
                multiSelectIds = multiSelectIds,
                waveforms = waveforms,
                totalMs = totalDurationMs,
                beatMs = beatMarksMs.toLongArray(),
                rulerKeysMs = keyframeTimesMs.toLongArray(),
                exportInMs = exportInMs,
                exportOutMs = exportOutMs,
                pxPerMs = pxPerMs,
                comfort = comfortDensity,
                bottomPadPx = bottomPadDp * density
            )
        )
        engine.setPlaying(isPlaying)
    }

    AndroidView(
        factory = { ctx ->
            val view = SurfaceView(ctx)
            // Build 230: the C++ renderer writes RGBA_8888; never leave the buffer format to the device default.
            view.holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
            val vc = ViewConfiguration.get(ctx)
            machine.touchSlop = vc.scaledTouchSlop.toFloat()
            machine.longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
            view.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    engine.surfaceChanged(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    machine.viewW = width
                    machine.viewH = height
                    engine.surfaceChanged(holder.surface)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    engine.surfaceDestroyed()
                }
            })
            view.setOnTouchListener { _, event ->
                machine.onTouch(event)
                true
            }
            view
        },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Build 233 — the gesture state machine moved to C++ (cpp/engine/timeline_gesture.cpp). This
 * class is only the glue: it hands each MotionEvent to the engine and performs the EVENTS the
 * engine returns through the existing ViewModel callbacks. Three jobs stay here because they
 * need Android: the long-press timer (Handler), the fling frame clock (Choreographer) and the
 * mapping of clip indices to clip ids (the project model lives in Kotlin). Everything else —
 * slop, scrub maths, lane scroll, drag, trim / fade accumulation, pinch, velocity, fling — is native.
 *
 * Runs entirely on the main thread (the view's touch listener).
 */
private class TlGestureGlue {
    var engine: NativeTimelineEngine? = null
    var tracks: List<TimelineTrack> = emptyList()
    var playheadOf: () -> Long = { 0L }
    var onSeekTo: (Long) -> Unit = {}
    var onScrubStart: () -> Unit = {}
    var onClipSelect: (String?) -> Unit = {}
    var onDeleteClipById: (String) -> Unit = {}
    var onMoveClipInTime: (String, Long) -> Unit = { _, _ -> }
    var onBeginClipTimeDrag: () -> Unit = {}
    var onReorderLane: (Int, Int) -> Unit = { _, _ -> }
    var onTrimClipEdge: (String, Boolean, Long) -> Unit = { _, _, _ -> }
    var onToggleClipVisibility: (String) -> Unit = {}
    var onSetClipFades: (String, Long, Long) -> Unit = { _, _, _ -> }
    var onPxPerMsChange: (Float) -> Unit = {}
    var onLongPressLane: (String) -> Unit = {}
    var onToggleMulti: (String) -> Unit = {}

    var touchSlop: Float = 8f
    var longPressMs: Long = 400L
    var viewW: Int = 0
    var viewH: Int = 0

    private val events = DoubleArray(TL_EV_CAPACITY * 3)
    private var configured = false
    private var gestureClipId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var tickPending = false

    private val tickCallback = Choreographer.FrameCallback { frameTimeNanos ->
        tickPending = false
        val eng = engine ?: return@FrameCallback
        dispatch(eng, eng.tick(frameTimeNanos, events))
    }

    fun release() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
        if (tickPending) {
            Choreographer.getInstance().removeFrameCallback(tickCallback)
            tickPending = false
        }
        engine = null
    }

    fun onTouch(event: MotionEvent) {
        val eng = engine ?: return
        if (!configured) {
            eng.configureGestures(touchSlop, longPressMs)
            configured = true
        }
        val pc = event.pointerCount
        val x1 = if (pc >= 2) event.getX(1) else 0f
        val y1 = if (pc >= 2) event.getY(1) else 0f
        val n = eng.touch(
            event.actionMasked, event.x, event.y, pc, x1, y1,
            event.eventTime, playheadOf(), viewW, viewH, events
        )
        dispatch(eng, n)
    }

    private fun clipIdAt(track: Int, clip: Int): String? {
        if (track < 0 || clip < 0) return null
        return tracks.getOrNull(track)?.clips?.getOrNull(clip)?.id
    }

    private fun dispatch(eng: NativeTimelineEngine, count: Int) {
        var redraw = false
        var animate = false
        for (i in 0 until count) {
            val type = events[i * 3].toInt()
            val a = events[i * 3 + 1]
            val b = events[i * 3 + 2]
            val id = gestureClipId
            when (type) {
                TL_EV_REDRAW -> redraw = true
                TL_EV_BEGIN -> gestureClipId = clipIdAt(a.toInt(), b.toInt())
                TL_EV_SCRUB_START -> onScrubStart()
                TL_EV_SEEK -> onSeekTo(a.toLong())
                TL_EV_SELECT -> if (id != null) onClipSelect(id)
                TL_EV_BEGIN_ADJUST -> onBeginClipTimeDrag()
                TL_EV_MOVE_TIME -> if (id != null) onMoveClipInTime(id, a.toLong())
                TL_EV_REORDER -> onReorderLane(a.toInt(), b.toInt())
                TL_EV_TRIM -> if (id != null) onTrimClipEdge(id, a != 0.0, b.toLong())
                TL_EV_FADE -> if (id != null) {
                    // The engine reports the raw whole-ms delta; apply it to the CURRENT fades.
                    val clip = tracks.flatMap { it.clips }.firstOrNull { it.id == id }
                    if (clip != null) {
                        val whole = b.toLong()
                        if (a != 0.0) onSetClipFades(id, clip.fadeInMs + whole, clip.fadeOutMs)
                        else onSetClipFades(id, clip.fadeInMs, clip.fadeOutMs - whole)
                    }
                }
                TL_EV_ZOOM -> onPxPerMsChange(a.toFloat())
                TL_EV_TOGGLE_VISIBILITY -> if (id != null) onToggleClipVisibility(id)
                TL_EV_TOGGLE_MULTI -> if (id != null) onToggleMulti(id)
                TL_EV_ENTER_MULTI -> if (id != null) onLongPressLane(id)
                TL_EV_DELETE -> if (id != null) onDeleteClipById(id)
                TL_EV_ARM_LONG_PRESS -> armLongPress(a.toLong())
                TL_EV_CANCEL_LONG_PRESS -> cancelLongPress()
                TL_EV_ANIMATE -> animate = true
            }
        }
        if (redraw) eng.requestFrame()
        if (animate && !tickPending) {
            tickPending = true
            Choreographer.getInstance().postFrameCallback(tickCallback)
        }
    }

    private fun armLongPress(delayMs: Long) {
        cancelLongPress()
        val r = Runnable {
            longPressRunnable = null
            val eng = engine ?: return@Runnable
            dispatch(eng, eng.longPress(events))
        }
        longPressRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }
}
