package com.example.ui.components

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.NativeTimelineEngine
import com.example.engine.TL_ZONE_BAR
import com.example.engine.TL_ZONE_CHAIN_X
import com.example.engine.TL_ZONE_CHILD_X
import com.example.engine.TL_ZONE_EMPTY
import com.example.engine.TL_ZONE_EYE
import com.example.engine.TL_ZONE_FADE_L
import com.example.engine.TL_ZONE_FADE_R
import com.example.engine.TL_ZONE_PILL
import com.example.engine.TL_ZONE_RAIL
import com.example.engine.TL_ZONE_RULER
import com.example.engine.TL_ZONE_TRIM_L
import com.example.engine.TL_ZONE_TRIM_R
import com.example.engine.TlDragState
import com.example.engine.TlHit
import com.example.engine.TlSnapshotInput
import com.example.model.TIMELINE_PX_PER_MS
import com.example.model.TimelineTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Build 224 — the native timeline's Compose host: a SurfaceView in the exact
 * slot the Compose ruler + layer panel occupied, drawn by the C++ timeline
 * renderer (see engine/NativeTimeline.kt). All gestures are hit-tested
 * natively (the same layout math that draws) and performed through the
 * EXISTING ViewModel operations — the semantics mirror TimelineLayerPanel's
 * gesture owners one for one:
 *
 *  - tap a bar / rail cell: select (rail in multi mode: toggle check)
 *  - tap the eye: visibility toggle; tap the pill: the options sheet
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
    onLayerPillClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val engine = remember { NativeTimelineEngine(context) }
    val scope = rememberCoroutineScope()
    val machine = remember { TlGestureMachine() }
    val latestTracks = rememberUpdatedState(tracks)
    val latestPlayhead = rememberUpdatedState(playheadMs)

    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }

    SideEffect {
        machine.engine = engine
        machine.scope = scope
        machine.density = density
        machine.tracks = latestTracks.value
        machine.comfort = comfortDensity
        machine.pxPerMs = pxPerMs
        machine.multiSelectIds = multiSelectIds
        machine.totalMs = totalDurationMs
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
        machine.onLayerPillClick = onLayerPillClick

        engine.playheadOf = { latestPlayhead.value() }
        engine.scrollOf = { machine.clampedScrollY() }
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
            val vc = ViewConfiguration.get(ctx)
            machine.touchSlop = vc.scaledTouchSlop.toFloat()
            machine.longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
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

private const val MODE_IDLE = 0
private const val MODE_PENDING = 1
private const val MODE_SCRUB = 2
private const val MODE_SCROLL_Y = 3
private const val MODE_DRAG_BAR = 4
private const val MODE_TRIM = 5
private const val MODE_FADE = 6
private const val MODE_PINCH = 7
private const val MODE_DEAD = 8 // pinch finished / long-press consumed: ignore until all fingers lift

/**
 * The touch state machine. Runs entirely on the main thread (the view's
 * touch listener), holds NO Compose state, and redraws by asking the engine
 * for a frame — the surrounding Compose UI never recomposes for gestures.
 */
private class TlGestureMachine {
    var engine: NativeTimelineEngine? = null
    var scope: CoroutineScope? = null
    var density: Float = 1f
    var tracks: List<TimelineTrack> = emptyList()
    var comfort: Boolean = false
    var pxPerMs: Float = TIMELINE_PX_PER_MS
    var multiSelectIds: Set<String> = emptySet()
    var totalMs: Long = 0L
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
    var onLayerPillClick: () -> Unit = {}

    var touchSlop: Float = 8f
    var longPressTimeout: Long = 400L
    var viewW: Int = 0
    var viewH: Int = 0

    var scrollY: Float = 0f
    private var mode = MODE_IDLE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downHit: TlHit? = null
    private var downClipId: String? = null
    private var downPlayheadMs = 0L
    private var scrubPosMs = 0f
    private var dragAccX = 0f
    private var dragAccY = 0f
    private var msAcc = 0f
    private var lastSpan = 0f
    private var velocity: VelocityTracker? = null
    private var flingJob: Job? = null
    private var scrollFlingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private fun rulerHPx(): Float = 44f * density

    fun maxScrollY(): Float {
        val eng = engine ?: return 0f
        if (viewH <= 0) return 0f
        return maxOf(0f, eng.contentHeightPx() - (viewH - rulerHPx()))
    }

    fun clampedScrollY(): Float = scrollY.coerceIn(0f, maxScrollY())

    private fun clipIdAt(hit: TlHit?): String? {
        if (hit == null || hit.track < 0 || hit.clip < 0) return null
        return tracks.getOrNull(hit.track)?.clips?.getOrNull(hit.clip)?.id
    }

    private fun hitAt(x: Float, y: Float): TlHit? =
        engine?.hitTest(x, y, viewW, viewH, playheadOf(), clampedScrollY())

    private fun cancelLongPress() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun armLongPress() {
        cancelLongPress()
        val hit = downHit ?: return
        if (hit.zone != TL_ZONE_BAR && hit.zone != TL_ZONE_RAIL) return
        val r = Runnable {
            if (mode != MODE_PENDING) return@Runnable
            val clipId = downClipId ?: return@Runnable
            if (hit.zone == TL_ZONE_RAIL) {
                if (multiSelectIds.isNotEmpty()) onToggleMulti(clipId) else onLongPressLane(clipId)
                mode = MODE_DEAD
            } else {
                // detectDragGesturesAfterLongPress: select + open the undo step, then drag.
                onClipSelect(clipId)
                onBeginClipTimeDrag()
                dragAccX = 0f
                dragAccY = 0f
                mode = MODE_DRAG_BAR
                engine?.dragState = TlDragState(hit.track, hit.clip, 0f, 0f)
                engine?.requestFrame()
            }
        }
        longPressRunnable = r
        mainHandler.postDelayed(r, longPressTimeout)
    }

    fun onTouch(event: MotionEvent) {
        val eng = engine ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flingJob?.cancel(); flingJob = null
                scrollFlingJob?.cancel(); scrollFlingJob = null
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x; downY = event.y
                lastX = event.x; lastY = event.y
                downPlayheadMs = playheadOf()
                scrubPosMs = downPlayheadMs.toFloat()
                downHit = hitAt(event.x, event.y)
                downClipId = clipIdAt(downHit)
                dragAccX = 0f; dragAccY = 0f; msAcc = 0f
                mode = MODE_PENDING
                armLongPress()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    cancelLongPress()
                    if (mode == MODE_DRAG_BAR) {
                        eng.dragState = TlDragState()
                        eng.requestFrame()
                    }
                    mode = MODE_PINCH
                    lastSpan = spanOf(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (mode == MODE_PINCH) {
                    if (event.pointerCount >= 2) {
                        val span = spanOf(event)
                        if (lastSpan > 1f && span > 1f) {
                            val zoomChange = span / lastSpan
                            if (abs(zoomChange - 1f) > 0.001f) {
                                onPxPerMsChange(
                                    (pxPerMs * zoomChange).coerceIn(
                                        TIMELINE_PX_PER_MS * 0.25f,
                                        TIMELINE_PX_PER_MS * 8f
                                    )
                                )
                            }
                        }
                        lastSpan = span
                    }
                    return
                }
                val x = event.x
                val y = event.y
                val dx = x - lastX
                val dy = y - lastY
                when (mode) {
                    MODE_PENDING -> {
                        val totalDx = x - downX
                        val totalDy = y - downY
                        val zone = downHit?.zone ?: TL_ZONE_EMPTY
                        when {
                            zone == TL_ZONE_TRIM_L || zone == TL_ZONE_TRIM_R ||
                                zone == TL_ZONE_FADE_L || zone == TL_ZONE_FADE_R -> {
                                // detectHorizontalDragGestures: starts on horizontal slop.
                                if (abs(totalDx) > touchSlop && abs(totalDx) >= abs(totalDy)) {
                                    cancelLongPress()
                                    onBeginClipTimeDrag()
                                    msAcc = 0f
                                    mode = if (zone == TL_ZONE_TRIM_L || zone == TL_ZONE_TRIM_R) MODE_TRIM else MODE_FADE
                                    lastX = x; lastY = y
                                    applyTrimFade(dx)
                                }
                            }
                            abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.2f -> {
                                cancelLongPress()
                                mode = MODE_SCROLL_Y
                                scrollY = clampedScrollY()
                                applyScroll(dy)
                            }
                            abs(totalDx) > touchSlop || abs(totalDy) > touchSlop -> {
                                if (abs(totalDy) > abs(totalDx) * 1.2f) {
                                    cancelLongPress()
                                    mode = MODE_SCROLL_Y
                                    scrollY = clampedScrollY()
                                    applyScroll(dy)
                                } else {
                                    cancelLongPress()
                                    mode = MODE_SCRUB
                                    onScrubStart()
                                    lastX = downX // the slop distance counts, like the panel
                                    applyScrub(x - lastX)
                                    lastX = x
                                }
                            }
                        }
                    }
                    MODE_SCRUB -> {
                        applyScrub(dx)
                    }
                    MODE_SCROLL_Y -> applyScroll(dy)
                    MODE_DRAG_BAR -> {
                        dragAccX += dx
                        dragAccY += dy
                        val hit = downHit
                        val clipId = downClipId
                        if (hit != null && clipId != null) {
                            val clip = tracks.getOrNull(hit.track)?.clips?.getOrNull(hit.clip)
                            if (clip != null && clip.type != com.example.model.LayerType.CHAIN &&
                                abs(dx) >= abs(dy)
                            ) {
                                val deltaMs = (dx / density / pxPerMs).roundToLong()
                                if (deltaMs != 0L) {
                                    onMoveClipInTime(clipId, deltaMs)
                                    dragAccX -= (deltaMs * pxPerMs * density).toFloat()
                                }
                            }
                            eng.dragState = TlDragState(hit.track, hit.clip, dragAccX, dragAccY)
                            eng.requestFrame()
                        }
                    }
                    MODE_TRIM, MODE_FADE -> applyTrimFade(dx)
                }
                lastX = x; lastY = y
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == MODE_PINCH && event.pointerCount <= 2) mode = MODE_DEAD
            }

            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                when (mode) {
                    MODE_PENDING -> dispatchTap()
                    MODE_SCRUB -> flingScrub()
                    MODE_SCROLL_Y -> flingScroll()
                    MODE_DRAG_BAR -> {
                        val hit = downHit
                        if (hit != null && hit.track >= 0) {
                            val laneStepPx = laneStepPx(hit.track)
                            val steps = (dragAccY / laneStepPx).roundToInt()
                            if (steps != 0) {
                                val to = (hit.track + steps).coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                                if (to != hit.track) onReorderLane(hit.track, to)
                            }
                        }
                        eng.dragState = TlDragState()
                        eng.requestFrame()
                    }
                }
                mode = MODE_IDLE
                velocity?.recycle(); velocity = null
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (mode == MODE_DRAG_BAR) {
                    eng.dragState = TlDragState()
                    eng.requestFrame()
                }
                mode = MODE_IDLE
                velocity?.recycle(); velocity = null
            }
        }
    }

    private fun spanOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun laneStepPx(trackIndex: Int): Float {
        val track = tracks.getOrNull(trackIndex) ?: return 36f * density
        val hDp = track.clips.maxOfOrNull { laneHeightFor(it.type, comfort) }?.value ?: 24f
        return (hDp + 4f) * density
    }

    private fun applyScrub(dx: Float) {
        val deltaMs = -(dx / density) / pxPerMs
        scrubPosMs = (scrubPosMs + deltaMs).coerceIn(0f, totalMs.toFloat())
        onSeekTo(scrubPosMs.roundToLong())
    }

    private fun applyScroll(dy: Float) {
        scrollY = (scrollY - dy).coerceIn(0f, maxScrollY())
        engine?.requestFrame()
    }

    private fun applyTrimFade(dx: Float) {
        val hit = downHit ?: return
        val clipId = downClipId ?: return
        msAcc += dx / density / pxPerMs
        val whole = msAcc.toLong()
        if (whole == 0L) return
        msAcc -= whole
        when (hit.zone) {
            TL_ZONE_TRIM_L -> onTrimClipEdge(clipId, true, whole)
            TL_ZONE_TRIM_R -> onTrimClipEdge(clipId, false, whole)
            TL_ZONE_FADE_L, TL_ZONE_FADE_R -> {
                val clip = tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
                if (hit.zone == TL_ZONE_FADE_L) {
                    onSetClipFades(clipId, clip.fadeInMs + whole, clip.fadeOutMs)
                } else {
                    onSetClipFades(clipId, clip.fadeInMs, clip.fadeOutMs - whole)
                }
            }
        }
    }

    private fun dispatchTap() {
        val hit = downHit ?: return
        val clipId = downClipId
        when (hit.zone) {
            TL_ZONE_PILL -> onLayerPillClick()
            TL_ZONE_EYE -> {
                if (clipId == null) return
                if (multiSelectIds.isNotEmpty()) onToggleMulti(clipId) else onToggleClipVisibility(clipId)
            }
            TL_ZONE_RAIL -> {
                if (clipId == null) return
                if (multiSelectIds.isNotEmpty()) onToggleMulti(clipId) else onClipSelect(clipId)
            }
            TL_ZONE_BAR -> if (clipId != null) onClipSelect(clipId)
            TL_ZONE_CHAIN_X, TL_ZONE_CHILD_X -> if (clipId != null) onDeleteClipById(clipId)
            else -> Unit // ruler / empty / trim / fade: a tap does nothing
        }
    }

    private fun flingScrub() {
        val vt = velocity ?: return
        vt.computeCurrentVelocity(1000)
        val vx = vt.xVelocity
        if (abs(vx) <= 100f) return
        val startMs = scrubPosMs
        val velocityMsPerSec = -(vx / density) / pxPerMs
        val total = totalMs.toFloat()
        flingJob = scope?.launch {
            AnimationState(
                initialValue = startMs,
                initialVelocity = velocityMsPerSec
            ).animateDecay(
                exponentialDecay(
                    frictionMultiplier = 1f,
                    absVelocityThreshold = 20f
                )
            ) {
                val ms = value.coerceIn(0f, total)
                onSeekTo(ms.roundToLong())
                if (ms <= 0f || ms >= total) cancelAnimation()
            }
        }
    }

    private fun flingScroll() {
        val vt = velocity ?: return
        vt.computeCurrentVelocity(1000)
        val vy = vt.yVelocity
        if (abs(vy) <= 100f) return
        val start = clampedScrollY()
        val maxS = maxScrollY()
        scrollFlingJob = scope?.launch {
            AnimationState(
                initialValue = start,
                initialVelocity = -vy
            ).animateDecay(
                exponentialDecay(
                    frictionMultiplier = 1f,
                    absVelocityThreshold = 20f
                )
            ) {
                val v = value.coerceIn(0f, maxS)
                scrollY = v
                engine?.requestFrame()
                if (v <= 0f || v >= maxS) cancelAnimation()
            }
        }
    }
}
