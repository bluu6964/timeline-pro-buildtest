package com.example.engine

import android.util.Log
import android.view.Surface
import com.example.model.ScopeData
import com.example.model.ScopeStats
import java.util.Arrays

/**
 * Raw JNI bridge for the shared C++17 GLES3 2D UI & scope renderer (cpp/engine/ui_gl_renderer.cpp).
 * All methods except [isAvailable] must be invoked ONLY on the GL thread that called [nativeCreate].
 */
object UiGlNative {
    const val CMD_LINE = 1
    const val CMD_POLYLINE = 2
    const val CMD_CIRCLE = 3
    const val CMD_ROUND_RECT = 4
    const val CMD_RECT = 5

    val isAvailable: Boolean get() = DecoderPool.isAvailable

    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeAttachSurface(handle: Long, surface: Surface, w: Int, h: Int): Int
    @JvmStatic external fun nativeResizeSurface(handle: Long, surfaceId: Int, w: Int, h: Int)
    @JvmStatic external fun nativeDetachSurface(handle: Long, surfaceId: Int)

    @JvmStatic external fun nativeDrawScopeHistogram(
        handle: Long, surfaceId: Int,
        histR: IntArray, histG: IntArray, histB: IntArray, histY: IntArray
    ): Boolean

    @JvmStatic external fun nativeDrawScopeWaveform(
        handle: Long, surfaceId: Int,
        wave: IntArray, waveW: Int, waveH: Int, bins: Int
    ): Boolean

    @JvmStatic external fun nativeDrawScopeVectorscope(
        handle: Long, surfaceId: Int,
        vec: IntArray, grid: Int
    ): Boolean

    @JvmStatic external fun nativeDrawEasingGraph(
        handle: Long, surfaceId: Int,
        x1: Float, y1: Float, x2: Float, y2: Float,
        progress: Float, density: Float,
        padL: Float, padT: Float, padR: Float, padB: Float
    ): Boolean

    @JvmStatic external fun nativeDrawVectorCmds(
        handle: Long, surfaceId: Int,
        clearArgb: Int,
        cmds: FloatArray, cmdFloatCount: Int,
        colors: IntArray, colorCount: Int
    ): Boolean

    @JvmStatic external fun nativeSelfTestOffscreen(
        handle: Long, w: Int, h: Int, outCounts: IntArray
    ): Boolean
}

/** Immutable snapshot of packed 2D vector commands safe to hand off to [UiGlRuntime]'s GL thread. */
class VectorCmdSnapshot(
    val floats: FloatArray,
    val floatCount: Int,
    val colors: IntArray,
    val colorCount: Int
)

/**
 * Lightweight command-buffer packer for [UiGlNative.nativeDrawVectorCmds].
 * Builds a single flat FloatArray + IntArray (ARGB) per frame with zero intermediate Shape/Path objects.
 */
class VectorCmdBuilder(initialFloats: Int = 256, initialColors: Int = 64) {
    private var fBuf = FloatArray(initialFloats)
    private var fLen = 0
    private var cBuf = IntArray(initialColors)
    private var cLen = 0

    private fun ensureFloats(extra: Int) {
        val need = fLen + extra
        if (need <= fBuf.size) return
        var cap = fBuf.size * 2
        while (cap < need) cap *= 2
        fBuf = Arrays.copyOf(fBuf, cap)
    }

    private fun ensureColors(extra: Int) {
        val need = cLen + extra
        if (need <= cBuf.size) return
        var cap = cBuf.size * 2
        while (cap < need) cap *= 2
        cBuf = Arrays.copyOf(cBuf, cap)
    }

    fun addLine(
        x0: Float, y0: Float, x1: Float, y1: Float,
        width: Float, argb: Int,
        dashOn: Float = 0f, dashOff: Float = 0f, capRound: Boolean = true
    ) {
        ensureFloats(9)
        ensureColors(1)
        fBuf[fLen++] = UiGlNative.CMD_LINE.toFloat()
        fBuf[fLen++] = x0
        fBuf[fLen++] = y0
        fBuf[fLen++] = x1
        fBuf[fLen++] = y1
        fBuf[fLen++] = width
        fBuf[fLen++] = dashOn
        fBuf[fLen++] = dashOff
        fBuf[fLen++] = if (capRound) 1f else 0f
        cBuf[cLen++] = argb
    }

    fun addPolyline(
        xs: FloatArray, ys: FloatArray, count: Int,
        closed: Boolean, width: Float, argb: Int,
        dashOn: Float = 0f, dashOff: Float = 0f, capRound: Boolean = true
    ) {
        if (count < 2) return
        ensureFloats(7 + count * 2)
        ensureColors(1)
        fBuf[fLen++] = UiGlNative.CMD_POLYLINE.toFloat()
        fBuf[fLen++] = if (closed) 1f else 0f
        fBuf[fLen++] = width
        fBuf[fLen++] = dashOn
        fBuf[fLen++] = dashOff
        fBuf[fLen++] = if (capRound) 1f else 0f
        fBuf[fLen++] = count.toFloat()
        for (i in 0 until count) {
            fBuf[fLen++] = xs[i]
            fBuf[fLen++] = ys[i]
        }
        cBuf[cLen++] = argb
    }

    fun addCircle(
        cx: Float, cy: Float, radius: Float,
        fillArgb: Int, strokeWidth: Float = 0f, strokeArgb: Int = 0
    ) {
        ensureFloats(5)
        ensureColors(2)
        fBuf[fLen++] = UiGlNative.CMD_CIRCLE.toFloat()
        fBuf[fLen++] = cx
        fBuf[fLen++] = cy
        fBuf[fLen++] = radius
        fBuf[fLen++] = strokeWidth
        cBuf[cLen++] = fillArgb
        cBuf[cLen++] = strokeArgb
    }

    fun addRoundRect(
        cx: Float, cy: Float, width: Float, height: Float,
        cornerRadius: Float, fillArgb: Int,
        strokeWidth: Float = 0f, strokeArgb: Int = 0
    ) {
        ensureFloats(7)
        ensureColors(2)
        fBuf[fLen++] = UiGlNative.CMD_ROUND_RECT.toFloat()
        fBuf[fLen++] = cx
        fBuf[fLen++] = cy
        fBuf[fLen++] = width
        fBuf[fLen++] = height
        fBuf[fLen++] = cornerRadius
        fBuf[fLen++] = strokeWidth
        cBuf[cLen++] = fillArgb
        cBuf[cLen++] = strokeArgb
    }

    fun addRect(x: Float, y: Float, width: Float, height: Float, fillArgb: Int) {
        ensureFloats(5)
        ensureColors(1)
        fBuf[fLen++] = UiGlNative.CMD_RECT.toFloat()
        fBuf[fLen++] = x
        fBuf[fLen++] = y
        fBuf[fLen++] = width
        fBuf[fLen++] = height
        cBuf[cLen++] = fillArgb
    }

    fun build(): VectorCmdSnapshot = VectorCmdSnapshot(
        floats = if (fLen == 0) FloatArray(0) else Arrays.copyOf(fBuf, fLen),
        floatCount = fLen,
        colors = if (cLen == 0) IntArray(0) else Arrays.copyOf(cBuf, cLen),
        colorCount = cLen
    )
}

/**
 * Shared single-context GLES3 runtime backing all Step 3 hotspot UI TextureView surfaces.
 * Uses ONE [GlThread] + ONE EGLContext across Scopes, Easing, Mask overlay, Selection frame, and Roto brush,
 * keeping GPU memory minimal and avoiding EGL context churn. Falls back cleanly to Compose Canvas when
 * native GLES3 is unavailable (e.g. JVM unit tests or unsupported GPUs).
 */
object UiGlRuntime {
    private const val TAG = "AmUiGl"
    private const val STATE_UNINIT = 0
    private const val STATE_READY = 1
    private const val STATE_FAILED = -1

    @Volatile private var state = STATE_UNINIT
    @Volatile private var glThread: GlThread? = null
    @Volatile private var handle: Long = 0L

    val isSupported: Boolean
        get() {
            val s = state
            if (s != STATE_UNINIT) return s == STATE_READY
            return ensureReady()
        }

    @Synchronized
    private fun ensureReady(): Boolean {
        if (state != STATE_UNINIT) return state == STATE_READY
        if (!UiGlNative.isAvailable) {
            state = STATE_FAILED
            return false
        }
        return try {
            val thread = GlThread("am-ui-gl")
            val ptr = thread.call { UiGlNative.nativeCreate() }
            if (ptr != 0L) {
                glThread = thread
                handle = ptr
                state = STATE_READY
                true
            } else {
                thread.close()
                state = STATE_FAILED
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "UiGlRuntime init failed, using Compose fallback: ${t.message}")
            state = STATE_FAILED
            false
        }
    }

    fun post(block: () -> Unit) {
        val t = glThread ?: return
        if (state != STATE_READY) return
        t.post {
            try {
                block()
            } catch (e: Throwable) {
                Log.w(TAG, "UI GL task failed: ${e.message}")
            }
        }
    }

    fun callBlocking(block: () -> Unit) {
        val t = glThread ?: return
        if (state != STATE_READY) return
        try {
            t.call(block)
        } catch (e: Throwable) {
            Log.w(TAG, "UI GL sync task failed: ${e.message}")
        }
    }

    // ---- GL-thread-only methods (call from inside [post] / [callBlocking]) ----

    fun attachSurfaceOnGl(surface: Surface, w: Int, h: Int): Int {
        val hdl = handle
        if (hdl == 0L) return 0
        return UiGlNative.nativeAttachSurface(hdl, surface, w, h)
    }

    fun resizeSurfaceOnGl(surfaceId: Int, w: Int, h: Int) {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return
        UiGlNative.nativeResizeSurface(hdl, surfaceId, w, h)
    }

    fun detachSurfaceOnGl(surfaceId: Int) {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return
        UiGlNative.nativeDetachSurface(hdl, surfaceId)
    }

    fun drawScopeHistogramOnGl(surfaceId: Int, data: ScopeData): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawScopeHistogram(
            hdl, surfaceId, data.histR, data.histG, data.histB, data.histY
        )
    }

    fun drawScopeWaveformOnGl(surfaceId: Int, data: ScopeData): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawScopeWaveform(
            hdl, surfaceId, data.wave, data.width, data.height, ScopeStats.BINS
        )
    }

    fun drawScopeVectorscopeOnGl(surfaceId: Int, data: ScopeData): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawScopeVectorscope(
            hdl, surfaceId, data.vec, ScopeStats.VEC_GRID
        )
    }

    fun drawEasingGraphOnGl(
        surfaceId: Int,
        x1: Float, y1: Float, x2: Float, y2: Float,
        progress: Float, density: Float,
        padL: Float, padT: Float, padR: Float, padB: Float
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawEasingGraph(
            hdl, surfaceId, x1, y1, x2, y2, progress, density, padL, padT, padR, padB
        )
    }

    fun drawVectorCommandsOnGl(
        surfaceId: Int,
        clearArgb: Int,
        snapshot: VectorCmdSnapshot
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawVectorCmds(
            hdl, surfaceId, clearArgb,
            snapshot.floats, snapshot.floatCount,
            snapshot.colors, snapshot.colorCount
        )
    }
}
