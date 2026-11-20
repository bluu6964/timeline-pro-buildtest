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
    const val CMD_CUBIC = 6
    const val CMD_ARC = 7
    const val CMD_FILL_POLY = 8
    const val CMD_DIMMER_HOLE = 9
    const val CMD_SHADER_QUAD = 10
    const val CMD_GRAD_RECT = 11

    const val QUAD_CHECKER = 0f
    const val QUAD_HUE_WHEEL = 1f
    const val QUAD_SV_BOX = 2f
    const val QUAD_COLOR_BAR = 3f
    const val QUAD_GRAD_TILE = 4f

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

    @JvmStatic external fun nativeDrawRulerDial(
        handle: Long, surfaceId: Int,
        value: Float, minVal: Float, maxVal: Float, step: Float,
        tickSpacingPx: Float, accentNeedle: Boolean, density: Float
    ): Boolean

    @JvmStatic external fun nativeDrawPositionPad(
        handle: Long, surfaceId: Int,
        xNorm: Float, yNorm: Float, dotArgb: Int, gridArgb: Int, density: Float
    ): Boolean

    @JvmStatic external fun nativeDrawCropOverlay(
        handle: Long, surfaceId: Int,
        lNorm: Float, tNorm: Float, rNorm: Float, bNorm: Float, density: Float
    ): Boolean

    @JvmStatic external fun nativeDrawWaveformStrip(
        handle: Long, surfaceId: Int,
        peaks: FloatArray, bgTopArgb: Int, bgBotArgb: Int, barArgb: Int, density: Float
    ): Boolean

    @JvmStatic external fun nativeDrawEasingTile(
        handle: Long, surfaceId: Int,
        x1: Float, y1: Float, x2: Float, y2: Float, strokeArgb: Int, density: Float
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

    fun addCubic(
        x0: Float, y0: Float, c1x: Float, c1y: Float,
        c2x: Float, c2y: Float, x1: Float, y1: Float,
        width: Float, argb: Int, capRound: Boolean = true
    ) {
        ensureFloats(11)
        ensureColors(1)
        fBuf[fLen++] = UiGlNative.CMD_CUBIC.toFloat()
        fBuf[fLen++] = x0
        fBuf[fLen++] = y0
        fBuf[fLen++] = c1x
        fBuf[fLen++] = c1y
        fBuf[fLen++] = c2x
        fBuf[fLen++] = c2y
        fBuf[fLen++] = x1
        fBuf[fLen++] = y1
        fBuf[fLen++] = width
        fBuf[fLen++] = if (capRound) 1f else 0f
        cBuf[cLen++] = argb
    }

    fun addArc(
        cx: Float, cy: Float, rx: Float, ry: Float,
        startDeg: Float, sweepDeg: Float,
        width: Float = 0f, fillArgb: Int = 0, strokeArgb: Int = 0,
        capRound: Boolean = true, closedPie: Boolean = false
    ) {
        ensureFloats(10)
        ensureColors(2)
        fBuf[fLen++] = UiGlNative.CMD_ARC.toFloat()
        fBuf[fLen++] = cx
        fBuf[fLen++] = cy
        fBuf[fLen++] = rx
        fBuf[fLen++] = ry
        fBuf[fLen++] = startDeg
        fBuf[fLen++] = sweepDeg
        fBuf[fLen++] = width
        fBuf[fLen++] = if (capRound) 1f else 0f
        fBuf[fLen++] = if (closedPie) 1f else 0f
        cBuf[cLen++] = fillArgb
        cBuf[cLen++] = strokeArgb
    }

    fun addFilledPolygon(xs: FloatArray, ys: FloatArray, count: Int, fillArgb: Int) {
        if (count < 3) return
        ensureFloats(2 + count * 2)
        ensureColors(1)
        fBuf[fLen++] = UiGlNative.CMD_FILL_POLY.toFloat()
        fBuf[fLen++] = count.toFloat()
        for (i in 0 until count) {
            fBuf[fLen++] = xs[i]
            fBuf[fLen++] = ys[i]
        }
        cBuf[cLen++] = fillArgb
    }

    fun addDimmerHole(
        width: Float, height: Float,
        holeL: Float, holeT: Float, holeR: Float, holeB: Float,
        dimArgb: Int
    ) {
        ensureFloats(7)
        ensureColors(1)
        fBuf[fLen++] = UiGlNative.CMD_DIMMER_HOLE.toFloat()
        fBuf[fLen++] = width
        fBuf[fLen++] = height
        fBuf[fLen++] = holeL
        fBuf[fLen++] = holeT
        fBuf[fLen++] = holeR
        fBuf[fLen++] = holeB
        cBuf[cLen++] = dimArgb
    }

    fun addShaderQuad(
        x: Float, y: Float, width: Float, height: Float, cornerRadius: Float, mode: Float,
        p0: Float = 0f, p1: Float = 0f, p2: Float = 0f, p3: Float = 0f, p4: Float = 0f, p5: Float = 0f,
        c0Argb: Int = 0, c1Argb: Int = 0
    ) {
        ensureFloats(13)
        ensureColors(2)
        fBuf[fLen++] = UiGlNative.CMD_SHADER_QUAD.toFloat()
        fBuf[fLen++] = x
        fBuf[fLen++] = y
        fBuf[fLen++] = width
        fBuf[fLen++] = height
        fBuf[fLen++] = cornerRadius
        fBuf[fLen++] = mode
        fBuf[fLen++] = p0
        fBuf[fLen++] = p1
        fBuf[fLen++] = p2
        fBuf[fLen++] = p3
        fBuf[fLen++] = p4
        fBuf[fLen++] = p5
        cBuf[cLen++] = c0Argb
        cBuf[cLen++] = c1Argb
    }

    fun addGradRect(
        x: Float, y: Float, width: Float, height: Float,
        vertical: Boolean, c0Argb: Int, c1Argb: Int
    ) {
        ensureFloats(6)
        ensureColors(2)
        fBuf[fLen++] = UiGlNative.CMD_GRAD_RECT.toFloat()
        fBuf[fLen++] = x
        fBuf[fLen++] = y
        fBuf[fLen++] = width
        fBuf[fLen++] = height
        fBuf[fLen++] = if (vertical) 1f else 0f
        cBuf[cLen++] = c0Argb
        cBuf[cLen++] = c1Argb
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
 * Uses ONE [GlThread] + ONE EGLContext across Scopes, Easing, Mask overlay and Selection frame,
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
        if (state == STATE_UNINIT) ensureReady()
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
        if (state == STATE_UNINIT) ensureReady()
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

    fun drawRulerDialOnGl(
        surfaceId: Int,
        value: Float, minVal: Float, maxVal: Float, step: Float,
        tickSpacingPx: Float, accentNeedle: Boolean, density: Float
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawRulerDial(
            hdl, surfaceId, value, minVal, maxVal, step, tickSpacingPx, accentNeedle, density
        )
    }

    fun drawPositionPadOnGl(
        surfaceId: Int,
        xNorm: Float, yNorm: Float,
        dotArgb: Int, gridArgb: Int, density: Float
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawPositionPad(hdl, surfaceId, xNorm, yNorm, dotArgb, gridArgb, density)
    }

    fun drawCropOverlayOnGl(
        surfaceId: Int,
        lNorm: Float, tNorm: Float, rNorm: Float, bNorm: Float, density: Float
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawCropOverlay(hdl, surfaceId, lNorm, tNorm, rNorm, bNorm, density)
    }

    fun drawWaveformStripOnGl(
        surfaceId: Int,
        peaks: FloatArray, bgTopArgb: Int, bgBotArgb: Int, barArgb: Int, density: Float
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawWaveformStrip(hdl, surfaceId, peaks, bgTopArgb, bgBotArgb, barArgb, density)
    }

    fun drawEasingTileOnGl(
        surfaceId: Int,
        x1: Float, y1: Float, x2: Float, y2: Float, strokeArgb: Int, density: Float
    ): Boolean {
        val hdl = handle
        if (hdl == 0L || surfaceId <= 0) return false
        return UiGlNative.nativeDrawEasingTile(hdl, surfaceId, x1, y1, x2, y2, strokeArgb, density)
    }
}
