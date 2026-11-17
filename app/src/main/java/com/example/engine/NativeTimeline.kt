package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.text.TextPaint
import android.text.TextUtils
import android.view.Choreographer
import android.view.Surface
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import com.example.model.keyframeTimes
import com.example.ui.components.barColorFor
import com.example.ui.components.barGlyphFor
import com.example.ui.components.formatClock
import com.example.ui.components.laneHeightFor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Build 224 — GRADUAL NATIVE MIGRATION, STEP 1: THE TIMELINE.
 *
 * The timeline's drawing lives in the C++ engine (cpp/engine/timeline_render.cpp)
 * on a SurfaceView; this file is the Kotlin side of the bridge. The Kotlin
 * model/ViewModel stays the single source of truth: on every data change the
 * composable hands the engine a plain snapshot (no strings cross JNI — labels
 * are indices into the glyph atlas below), and per frame the engine only
 * receives (playheadMs, scrollY, drag state). Gestures are hit-tested natively
 * against the same layout math that draws, and Kotlin performs the existing
 * ViewModel operations.
 *
 * TEXT: the engine's FreeType path needs Kotlin-computed bidi levels and
 * cannot shape Bengali/RTL UI labels, so labels are rasterized ONCE per
 * (text, style, width) by Android's own text stack into a shared atlas
 * bitmap (the same Roboto fonts Compose uses, so labels look identical);
 * native only blits. A reserved digit atlas serves the per-frame timecode.
 */
internal object TimelineNative {
    init {
        try {
            System.loadLibrary("am_engine")
        } catch (_: Throwable) {
            // DecoderPool loads the library too; a failure here surfaces on first use.
        }
    }

    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(h: Long)
    @JvmStatic external fun nativeSetWindow(h: Long, surface: Surface?)
    @JvmStatic external fun nativeClearWindow(h: Long)
    @JvmStatic external fun nativeSetAtlas(
        h: Long, bitmap: Bitmap, rects: IntArray, bases: FloatArray,
        digits: IntArray, digitAdv: FloatArray, digitBaseY: Float
    )
    @JvmStatic external fun nativeBeginSnapshot(
        h: Long, density: Float, pxPerMs: Float, rulerHPx: Float, railWPx: Float,
        topPadPx: Float, gapPx: Float, bottomPadPx: Float, totalMs: Long,
        layerCount: Int, pillLabel: Int, pillTextWPx: Float, totalLabel: Int,
        chainLabel: Int, comfort: Boolean, multiMode: Boolean,
        hasRange: Boolean, rangeInMs: Long, rangeOutMs: Long
    )
    @JvmStatic external fun nativeSetBeats(h: Long, beatMs: LongArray?, rulerKeysMs: LongArray?)
    @JvmStatic external fun nativeAddTrack(h: Long, heightPx: Float, chainClip: Int): Int
    @JvmStatic external fun nativeAddClip(
        h: Long, trackIdx: Int, startMs: Long, durMs: Long, kind: Int, color: Int,
        flags: Int, label: Int, chip: Int, badge: Int, trimStartMs: Long,
        speed: Float, volume: Float, fadeInMs: Long, fadeOutMs: Long,
        keysRelMs: LongArray?, peaks: FloatArray?
    )
    @JvmStatic external fun nativeEndSnapshot(h: Long)
    @JvmStatic external fun nativeContentHeight(h: Long): Float
    @JvmStatic external fun nativeDraw(
        h: Long, playheadMs: Long, scrollYPx: Float,
        dragTrack: Int, dragClip: Int, dragDx: Float, dragDy: Float
    ): Boolean
    @JvmStatic external fun nativeHitTest(
        h: Long, x: Float, y: Float, viewW: Int, viewH: Int,
        playheadMs: Long, scrollYPx: Float, out: IntArray
    ): Int
}

// Zones (mirror am::timeline::Zone in timeline_render.h).
internal const val TL_ZONE_NONE = 0
internal const val TL_ZONE_RULER = 1
internal const val TL_ZONE_EMPTY = 2
internal const val TL_ZONE_BAR = 3
internal const val TL_ZONE_RAIL = 4
internal const val TL_ZONE_EYE = 5
internal const val TL_ZONE_TRIM_L = 6
internal const val TL_ZONE_TRIM_R = 7
internal const val TL_ZONE_FADE_L = 8
internal const val TL_ZONE_FADE_R = 9
internal const val TL_ZONE_PILL = 10
internal const val TL_ZONE_CHAIN_X = 11
internal const val TL_ZONE_CHILD_X = 12

// Clip flags (mirror am::timeline::ClipFlag).
private const val TL_FLAG_SELECTED = 1 shl 0
private const val TL_FLAG_MULTI = 1 shl 1
private const val TL_FLAG_HIDDEN = 1 shl 2
private const val TL_FLAG_LOCKED = 1 shl 3
private const val TL_FLAG_FX = 1 shl 4
private const val TL_FLAG_CHAIN = 1 shl 5
private const val TL_FLAG_IN_CHAIN = 1 shl 6
private const val TL_FLAG_HAS_URI = 1 shl 7
private const val TL_FLAG_IMAGE = 1 shl 8
private const val TL_FLAG_MUTED = 1 shl 9
private const val TL_FLAG_GROUP_EDGE = 1 shl 10
private const val TL_FLAG_CONTROLLER = 1 shl 11
private const val TL_FLAG_VIDEO = 1 shl 12
private const val TL_FLAG_AUDIO = 1 shl 13

private fun glyphKindOf(clip: TimelineClip): Int = when (barGlyphFor(clip)) {
    "text" -> 1
    "shape" -> 2
    "fx" -> 3
    "group" -> 4
    "null" -> 5
    "sticker" -> 6
    "camera" -> 7
    "vector" -> 8
    "drawing" -> 9
    "video" -> 10
    "music" -> 11
    "chain" -> 12
    else -> 0
}

/** The label styles the native renderer blits (sizes/weights mirror the Compose bars). */
internal enum class TlLabelStyle(val sp: Float, val weight: Int, val italic: Boolean = false) {
    LABEL(13f, 600),
    LABEL_SM(12.5f, 600),
    CHIP(10f, 500),
    BADGE(10f, 700),
    PILL(11f, 700),
    TOTAL(11f, 500),
    DIGIT(12f, 700),
    FX(9f, 700, italic = true),
    CONTROLLER(12f, 700),
    EMPTY(12f, 500)
}

/**
 * The glyph atlas: every distinct label is rasterized once (white glyphs on
 * transparency; the native side tints at blit time) and referenced by index.
 * When the atlas fills up it is cleared and rebuilt — the engine detects the
 * reset and re-resolves every label of the current snapshot.
 */
internal class TlLabelAtlas(
    private val density: Float,
    private val fontScale: Float
) {
    val bitmap: Bitmap = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paints = HashMap<TlLabelStyle, TextPaint>()
    private val cache = HashMap<Triple<TlLabelStyle, String, Int>, Int>()
    private val rects = ArrayList<Int>()   // x, y, w, h per entry
    private val bases = ArrayList<Float>()
    private var shelfX = 0
    private var shelfY = 0
    private var shelfH = 0
    var overflowed = false
        private set

    val entryCount: Int get() = bases.size
    var digitIndices = IntArray(11) { -1 }
        private set
    var digitAdv = FloatArray(11)
        private set
    var digitBaseY = 0f
        private set

    private fun paint(style: TlLabelStyle): TextPaint = paints.getOrPut(style) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.textSize = style.sp * density * fontScale
        p.typeface = typefaceFor(style.weight, style.italic)
        p
    }

    private fun typefaceFor(weight: Int, italic: Boolean): Typeface {
        // NOTE: Typeface.create(String, Int, Boolean) does not resolve against
        // this project's compile setup (build 224 first Actions run) — use the
        // classic create(String, style) + create(Typeface, style) overloads.
        val family = when {
            weight >= 700 -> "sans-serif"
            weight >= 500 -> "sans-serif-medium"
            else -> "sans-serif"
        }
        val baseStyle = if (weight >= 700) Typeface.BOLD else Typeface.NORMAL
        val base = Typeface.create(family, baseStyle)
        return if (italic) Typeface.create(base, Typeface.ITALIC) else base
    }

    fun measure(text: String, style: TlLabelStyle): Float = paint(style).measureText(text)

    /** Rasterize the timecode digits once: '0'..'9' then ':'. */
    fun ensureDigits(rulerTopPadPx: Float) {
        if (digitIndices[0] >= 0) return
        val p = paint(TlLabelStyle.DIGIT)
        val chars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':')
        for ((i, ch) in chars.withIndex()) {
            val s = ch.toString()
            digitIndices[i] = rasterize(s, TlLabelStyle.DIGIT)
            digitAdv[i] = p.measureText(s)
        }
        digitBaseY = rulerTopPadPx + (-p.fontMetrics.ascent)
    }

    /**
     * The atlas index of [text] in [style], ellipsized to [maxWidthPx] first
     * (the same end-ellipsis the Compose bars apply). -1 when the text is
     * empty or the atlas is full (the caller then triggers a reset pass).
     */
    fun resolve(text: String, style: TlLabelStyle, maxWidthPx: Float): Int {
        if (text.isEmpty() || overflowed) return -1
        val p = paint(style)
        val shown = if (maxWidthPx > 0f && p.measureText(text) > maxWidthPx) {
            TextUtils.ellipsize(text, p, maxWidthPx, TextUtils.TruncateAt.END).toString()
        } else text
        if (shown.isEmpty()) return -1
        val key = Triple(style, shown, 0)
        cache[key]?.let { return it }
        val idx = rasterize(shown, style)
        if (idx >= 0) cache[key] = idx
        return idx
    }

    private fun rasterize(text: String, style: TlLabelStyle): Int {
        val p = paint(style)
        val bounds = Rect()
        p.getTextBounds(text, 0, text.length, bounds)
        val w = bounds.width() + 2 * PAD
        val h = bounds.height() + 2 * PAD
        if (w <= 0 || h <= 0) return -1
        if (shelfX + w > ATLAS_W) {
            shelfX = 0
            shelfY += shelfH
            shelfH = 0
        }
        if (shelfY + h > ATLAS_H || w > ATLAS_W) {
            overflowed = true
            return -1
        }
        val x = shelfX
        val y = shelfY
        // Draw with the ink box's top-left at (x + PAD, y + PAD).
        canvas.drawText(text, (x + PAD - bounds.left).toFloat(), (y + PAD - bounds.top).toFloat(), p)
        rects.add(x); rects.add(y); rects.add(w); rects.add(h)
        bases.add((PAD - bounds.top).toFloat())
        shelfX += w
        shelfH = maxOf(shelfH, h)
        return bases.size - 1
    }

    fun reset() {
        bitmap.eraseColor(Color.TRANSPARENT)
        cache.clear()
        rects.clear()
        bases.clear()
        shelfX = 0
        shelfY = 0
        shelfH = 0
        overflowed = false
        digitIndices = IntArray(11) { -1 }
    }

    fun rectArray(): IntArray = rects.toIntArray()
    fun baseArray(): FloatArray = bases.toFloatArray()

    companion object {
        private const val ATLAS_W = 2048
        private const val ATLAS_H = 1024
        private const val PAD = 2
    }
}

/** The drag state the render loop reads each frame (written by the gesture machine). */
internal data class TlDragState(
    val track: Int = -1,
    val clip: Int = -1,
    val dx: Float = 0f,
    val dy: Float = 0f
)

/** One hit-test answer: a TL_ZONE_* plus the (track, clip) it belongs to. */
internal data class TlHit(val zone: Int, val track: Int, val clip: Int)

/** Everything the snapshot builder needs for one update (data only, no callbacks). */
internal class TlSnapshotInput(
    val tracks: List<TimelineTrack>,
    val selectedClipId: String?,
    val multiSelectIds: Set<String>,
    val waveforms: Map<String, FloatArray>,
    val totalMs: Long,
    val beatMs: LongArray,
    val rulerKeysMs: LongArray,
    val exportInMs: Long?,
    val exportOutMs: Long?,
    val pxPerMs: Float,
    val comfort: Boolean,
    val bottomPadPx: Float
)

/**
 * Owns the native renderer handle, the render thread (a HandlerThread with
 * its own Choreographer, the NativePreviewEngine pattern) and the atlas.
 * Snapshot updates run on the caller's (main) thread; drawing runs on the
 * render thread at vsync while playing, or on request otherwise.
 */
internal class NativeTimelineEngine(context: Context) {
    private val appContext = context.applicationContext
    private val density: Float = appContext.resources.displayMetrics.density
    private val fontScale: Float = appContext.resources.configuration.fontScale
    private var handle: Long = TimelineNative.nativeCreate()
    private val thread = HandlerThread("am-timeline").apply { start() }
    private val handler = Handler(thread.looper)
    private var choreographer: Choreographer? = null
    private val framePending = AtomicBoolean(false)
    private val atlas = TlLabelAtlas(density, fontScale)
    private var uploadedEntryCount = -1

    @Volatile var playheadOf: () -> Long = { 0L }
    @Volatile var scrollOf: () -> Float = { 0f }
    @Volatile var dragState: TlDragState = TlDragState()
    @Volatile private var playing = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePending.set(false)
            val drag = dragState
            TimelineNative.nativeDraw(
                handle, playheadOf(), scrollOf(),
                drag.track, drag.clip, drag.dx, drag.dy
            )
            if (playing) requestFrame()
        }
    }

    fun requestFrame() {
        if (!framePending.compareAndSet(false, true)) return
        handler.post {
            val ch = choreographer ?: Choreographer.getInstance().also { choreographer = it }
            ch.postFrameCallback(frameCallback)
        }
    }

    fun setPlaying(value: Boolean) {
        playing = value
        if (value) requestFrame()
    }

    fun surfaceChanged(surface: Surface) {
        handler.post {
            TimelineNative.nativeSetWindow(handle, surface)
            requestFrame()
        }
    }

    fun surfaceDestroyed() {
        handler.post { TimelineNative.nativeClearWindow(handle) }
    }

    fun contentHeightPx(): Float = TimelineNative.nativeContentHeight(handle)

    fun hitTest(x: Float, y: Float, viewW: Int, viewH: Int, playheadMs: Long, scrollYPx: Float): TlHit {
        val out = IntArray(2)
        val zone = TimelineNative.nativeHitTest(handle, x, y, viewW, viewH, playheadMs, scrollYPx, out)
        return TlHit(zone, out[0], out[1])
    }

    /** Push a fresh data snapshot (called from the composition on data change). */
    fun update(input: TlSnapshotInput) {
        if (handle == 0L) return
        // Two passes at most: if the atlas overflows mid-resolve it is reset
        // and every label is resolved again against the empty atlas.
        var pass = 0
        while (true) {
            val ok = buildSnapshot(input)
            if (ok) break
            if (++pass > 1) break
            // The old snapshot's indices are invalid against a fresh atlas:
            // blank the surface snapshot first, then upload and rebuild.
            TimelineNative.nativeBeginSnapshot(
                handle, density, input.pxPerMs, 44f * density, 42f * density,
                4f * density, 4f * density, input.bottomPadPx, input.totalMs,
                0, -1, 0f, -1, -1, input.comfort, false, false, 0L, 0L
            )
            TimelineNative.nativeEndSnapshot(handle)
            atlas.reset()
            uploadAtlas()
        }
        requestFrame()
    }

    private fun uploadAtlas() {
        TimelineNative.nativeSetAtlas(
            handle, atlas.bitmap, atlas.rectArray(), atlas.baseArray(),
            atlas.digitIndices, atlas.digitAdv, atlas.digitBaseY
        )
        uploadedEntryCount = atlas.entryCount
    }

    /** The atlas bitmap is 8 MB — upload it only when entries were added or it was reset. */
    private fun uploadAtlasIfDirty() {
        if (atlas.entryCount != uploadedEntryCount) uploadAtlas()
    }

    /** Returns false when the atlas overflowed and the pass must restart. */
    private fun buildSnapshot(input: TlSnapshotInput): Boolean {
        val dpPx = density
        atlas.ensureDigits(22f * dpPx)
        val layerCount = input.tracks.sumOf { it.clips.size }
        val pillText = "$layerCount Layers"
        val pillIdx = atlas.resolve(pillText, TlLabelStyle.PILL, 0f)
        val pillW = atlas.measure(pillText, TlLabelStyle.PILL)
        val totalIdx = atlas.resolve(formatClock(input.totalMs), TlLabelStyle.TOTAL, 0f)
        val chainIdx = atlas.resolve("Chain", TlLabelStyle.EMPTY, 0f)
        val hasRange = input.exportInMs != null && input.exportOutMs != null &&
            input.exportOutMs > input.exportInMs

        // Resolve every clip's labels first (indices go stale if the atlas
        // resets, so resolution and upload happen before the JNI build).
        class ClipLabels(val label: Int, val chip: Int, val badge: Int)
        val perTrack = ArrayList<List<ClipLabels>>()
        var failed = atlas.overflowed
        for (track in input.tracks) {
            val chainClip = track.clips.firstOrNull { it.type == LayerType.CHAIN }
            val list = ArrayList<ClipLabels>()
            for (clip in track.clips) {
                val inChain = chainClip != null && clip.chainId == chainClip.id
                val barWidthPx = maxOf(
                    (clip.durationMs * input.pxPerMs * dpPx).toFloat(),
                    36f * dpPx
                )
                var label = -1
                var chip = -1
                var badge = -1
                when {
                    clip.type == LayerType.CHAIN -> Unit
                    clip.type == LayerType.CONTROLLER -> {
                        label = atlas.resolve(
                            clip.name, TlLabelStyle.CONTROLLER,
                            barWidthPx - 46f * dpPx
                        )
                    }
                    clip.type == LayerType.VIDEO || clip.type == LayerType.AUDIO -> {
                        if (!inChain) {
                            chip = atlas.resolve(clip.name, TlLabelStyle.CHIP, barWidthPx - 44f * dpPx)
                            badge = if (clip.type == LayerType.AUDIO) {
                                atlas.resolve(
                                    String.format("%.1fs", clip.durationMs / 1000.0),
                                    TlLabelStyle.BADGE, 0f
                                )
                            } else if (clip.effects.isNotEmpty()) {
                                atlas.resolve("fx", TlLabelStyle.FX, 0f)
                            } else -1
                        } else {
                            label = atlas.resolve(
                                clip.name, TlLabelStyle.LABEL_SM,
                                barWidthPx - 11f * dpPx - 24f * dpPx - 24f * dpPx
                            )
                        }
                    }
                    else -> {
                        val text = if (clip.type == LayerType.TEXT) {
                            clip.text?.takeIf { it.isNotBlank() } ?: clip.name
                        } else clip.name
                        val glyph = glyphKindOf(clip) != 0
                        val endPad = if (inChain) 24f else 40f
                        label = atlas.resolve(
                            text,
                            if (inChain) TlLabelStyle.LABEL_SM else TlLabelStyle.LABEL,
                            barWidthPx - 11f * dpPx - (if (glyph) 24f else 0f) * dpPx - endPad * dpPx
                        )
                    }
                }
                if (atlas.overflowed) failed = true
                list.add(ClipLabels(label, chip, badge))
            }
            perTrack.add(list)
        }
        if (failed) return false
        uploadAtlasIfDirty()

        TimelineNative.nativeBeginSnapshot(
            handle, density, input.pxPerMs, 44f * dpPx, 42f * dpPx,
            4f * dpPx, 4f * dpPx, input.bottomPadPx, input.totalMs,
            layerCount, pillIdx, pillW, totalIdx, chainIdx,
            input.comfort, input.multiSelectIds.isNotEmpty(),
            hasRange, input.exportInMs ?: 0L, input.exportOutMs ?: 0L
        )
        TimelineNative.nativeSetBeats(handle, input.beatMs, input.rulerKeysMs)
        input.tracks.forEachIndexed { ti, track ->
            val chainClipIdx = track.clips.indexOfFirst { it.type == LayerType.CHAIN }
            val chainClip = track.clips.getOrNull(chainClipIdx)
            val heightPx = (track.clips.maxOfOrNull { laneHeightFor(it.type, input.comfort) }?.value
                ?: 24f) * dpPx
            val nativeTrack = TimelineNative.nativeAddTrack(handle, heightPx, chainClipIdx)
            track.clips.forEachIndexed { ci, clip ->
                val inChain = chainClip != null && clip.chainId == chainClip.id
                var flags = 0
                if (clip.id == input.selectedClipId) flags = flags or TL_FLAG_SELECTED
                if (clip.id in input.multiSelectIds) flags = flags or TL_FLAG_MULTI
                if (!clip.isVisible) flags = flags or TL_FLAG_HIDDEN
                if (clip.isLocked) flags = flags or TL_FLAG_LOCKED
                if (clip.effects.isNotEmpty()) flags = flags or TL_FLAG_FX
                if (clip.type == LayerType.CHAIN) flags = flags or TL_FLAG_CHAIN
                if (inChain) flags = flags or TL_FLAG_IN_CHAIN
                if (clip.uri != null) flags = flags or TL_FLAG_HAS_URI
                if (clip.isImage) flags = flags or TL_FLAG_IMAGE
                if (clip.isMuted) flags = flags or TL_FLAG_MUTED
                if (clip.groupId != null) flags = flags or TL_FLAG_GROUP_EDGE
                if (clip.type == LayerType.CONTROLLER) flags = flags or TL_FLAG_CONTROLLER
                if (clip.type == LayerType.VIDEO) flags = flags or TL_FLAG_VIDEO
                if (clip.type == LayerType.AUDIO) flags = flags or TL_FLAG_AUDIO
                val labels = perTrack[ti][ci]
                TimelineNative.nativeAddClip(
                    handle, nativeTrack,
                    clip.startMs, clip.durationMs,
                    glyphKindOf(clip),
                    (barColorFor(clip).value shr 32).toInt(),
                    flags,
                    labels.label, labels.chip, labels.badge,
                    clip.trimStartMs, clip.speed, clip.volume,
                    clip.fadeInMs, clip.fadeOutMs,
                    if (clip.id == input.selectedClipId) clip.keyframeTimes().toLongArray() else null,
                    clip.uri?.let { input.waveforms[it] }
                )
            }
        }
        TimelineNative.nativeEndSnapshot(handle)
        return true
    }

    fun release() {
        handler.post {
            TimelineNative.nativeClearWindow(handle)
            if (handle != 0L) {
                TimelineNative.nativeDestroy(handle)
                handle = 0L
            }
        }
        thread.quitSafely()
    }
}
