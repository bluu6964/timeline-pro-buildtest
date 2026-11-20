package com.example.engine

/**
 * JNI bridge for C++ UI widget math, gestures, and hit-testing (ui_widgets.cpp).
 * Falls back gracefully when native library isn't loaded (e.g. host JVM unit tests).
 */
object UiMathNative {
    const val NODE_HIT_NONE = 0
    const val NODE_HIT_ANCHOR = 1
    const val NODE_HIT_IN_HANDLE = 2
    const val NODE_HIT_OUT_HANDLE = 3
    const val NODE_HIT_EDGE = 4

    const val CROP_PRESET_FREE = 0
    const val CROP_PRESET_ORIGINAL = 1
    const val CROP_PRESET_PROJECT = 2
    const val CROP_PRESET_CUSTOM = 3

    val isAvailable: Boolean get() = try { DecoderPool.isAvailable } catch (_: Throwable) { false }

    @JvmStatic external fun nativeRulerQuantize(value: Float, minVal: Float, maxVal: Float, step: Float): Float
    @JvmStatic external fun nativeRulerDragDelta(
        pos: Float, dxPx: Float, tickPx: Float, minVal: Float, maxVal: Float, step: Float
    ): Float
    @JvmStatic external fun nativeRulerFlingEval(
        startVal: Float, velocityUnitsPerSec: Float, elapsedSec: Float,
        frictionMultiplier: Float, step: Float, minVal: Float, maxVal: Float,
        outValAndDone: FloatArray
    ): Boolean

    @JvmStatic external fun nativeCropLockK(
        presetKind: Int, customNum: Float, customDen: Float, srcAspect: Float, projAspect: Float
    ): Float
    @JvmStatic external fun nativeCropHitTest(
        px: Float, py: Float, l: Float, t: Float, r: Float, b: Float, tol: Float
    ): Int
    @JvmStatic external fun nativeCropDrag(
        mode: Int, dxNorm: Float, dyNorm: Float,
        l: Float, t: Float, r: Float, b: Float,
        lockK: Float, minSize: Float, outProps4: FloatArray
    )
    @JvmStatic external fun nativeCropResizeCenter(
        curW: Float, curH: Float, curL: Float, curT: Float, curR: Float, curB: Float,
        newW: Float, newH: Float, lockK: Float, minSize: Float, outProps4: FloatArray
    )

    @JvmStatic external fun nativePathFrameTransform(
        cx: Float, cy: Float, boxW: Float, boxH: Float, frameW: Float, frameH: Float,
        scalePct: Float, rotDeg: Float, depthX: Float, depthY: Float,
        flipH: Boolean, flipV: Boolean, pivotX: Float, pivotY: Float,
        inX: Float, inY: Float, inverse: Boolean, outXy2: FloatArray
    )

    @JvmStatic external fun nativeNodeHitTest(
        px: Float, py: Float,
        anchors: FloatArray?, ins: FloatArray?, hasIn: BooleanArray?,
        outs: FloatArray?, hasOut: BooleanArray?,
        anchorCount: Int, selectedIdx: Int, radiusPx: Float,
        segTypes: IntArray?, segPts: FloatArray?, segCount: Int,
        outResult4: FloatArray
    )

    @JvmStatic external fun nativePlanarHitTest(
        fx: Float, fy: Float, quad8: FloatArray, radiusNorm: Float
    ): Int
    @JvmStatic external fun nativePlanarMove(
        quad8: FloatArray, dx: Float, dy: Float, outQuad8: FloatArray
    )

    @JvmStatic external fun nativeFrameGeoContains(
        px: Float, py: Float,
        boxW: Float, boxH: Float, frameW: Float, frameH: Float,
        minScale: Float, pivotRefW: Float, pivotRefH: Float,
        visW: Float, visH: Float, visOffX: Float, visOffY: Float,
        scalePct: Float, rotDeg: Float, depthX: Float, depthY: Float,
        flipH: Boolean, flipV: Boolean, pivotX: Float, pivotY: Float
    ): Boolean

    @JvmStatic external fun nativeHsvToRgb(hDeg: Float, s: Float, v: Float, outRgb3: FloatArray)
    @JvmStatic external fun nativeRgbToHsv(r: Float, g: Float, b: Float, outHsv3: FloatArray)
}
