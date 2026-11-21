package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bridge to the C++ value laws (value_laws.cpp, migration stage 4): the pure
 * decision/geometry laws that used to live in the model package — ducking
 * curves, the noise-reduction profile math, marker/onion/crop laws, the
 * vector-draw and pen path construction, mask-frame geometry, the clipboard
 * paste plan, the blend-mode table, the FX list/string laws and the small
 * panel value rules. Plain values and flat arrays cross; the string laws run
 * natively over the string's UTF-16 code units (Kotlin's Char sequence).
 * Loaded with the engine library like the other bridges; [isAvailable] is
 * the same DecoderPool probe — no throwing init gate.
 */
object ValueLawsNative {

    val isAvailable: Boolean
        get() = DecoderPool.isAvailable

    init {
        @Suppress("UNUSED_EXPRESSION")
        DecoderPool.isAvailable
    }

    // ---- Ducking ----
    external fun nativeDuckFloorGain(amountDb: Float): Float
    external fun nativeDuckAttackMs(smoothness: Float): Double
    external fun nativeDuckReleaseMs(smoothness: Float): Double
    external fun nativeDuckTargetGain(level: Float, floorGain: Float): Float
    external fun nativeDuckTimelineLevels(
        peaks: FloatArray,
        durationMs: Long,
        speed: Float,
        heard: FloatArray,
    ): FloatArray

    external fun nativeDuckGainAt(times: LongArray, gains: FloatArray, relMs: Long): Float

    // ---- NoiseReduction ----
    external fun nativeNoiseOverSubtraction(strength: Float): Float
    external fun nativeNoiseRawGain(magnitude: Float, noise: Float, strength: Float): Float
    external fun nativeNoiseSmoothFreq(raw: FloatArray): FloatArray
    external fun nativeNoiseStepCoef(tauMs: Double): Double
    external fun nativeNoisePercentile(values: FloatArray, percentile: Double): Float
    external fun nativeNoiseProfile(frames: FloatArray, frameCount: Int, bins: Int): FloatArray
    external fun nativeNoiseBucketOf(magnitude: Float): Int
    external fun nativeNoiseBucketValue(bucket: Int): Float
    external fun nativeNoiseAccumAdd(counts: IntArray, magnitudes: FloatArray)
    external fun nativeNoiseAccumProfile(counts: IntArray, frames: Int, bins: Int): FloatArray
    external fun nativeNoiseHann(i: Int): Float
    external fun nativeNoiseWindow(): FloatArray

    // ---- SpatialAlign ----
    external fun nativeSpatialAlignDeltas(rects: FloatArray, action: Int): FloatArray
    external fun nativeSpatialDistributeDeltas(rects: FloatArray, horizontal: Boolean): FloatArray

    // ---- ProjectMarkers ----
    external fun nativeMarkerNormalize(times: LongArray): IntArray
    external fun nativeMarkerNearest(times: LongArray, timeMs: Long, toleranceMs: Long): Int
    external fun nativeTrimRange(s: String): IntArray

    // ---- OnionSkin ----
    external fun nativeOnionNormalize(count: Int, opacity: Float): FloatArray
    external fun nativeOnionFrameStepMs(fps: Int, stepFrames: Int): Long
    external fun nativeOnionPlan(headMs: Long, fps: Int, durationMs: Long, count: Int, opacity: Float): ByteArray

    /** Decoded [nativeOnionPlan] blob: parallel arrays, one entry per ghost. */
    class OnionPlanBlob(
        val times: LongArray,
        val sides: IntArray,
        val steps: IntArray,
        val alphas: FloatArray,
        val hues: FloatArray,
    )

    fun decodeOnionPlan(blob: ByteArray): OnionPlanBlob {
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        val n = buf.int
        val times = LongArray(n)
        val sides = IntArray(n)
        val steps = IntArray(n)
        val alphas = FloatArray(n)
        val hues = FloatArray(n)
        for (i in 0 until n) {
            times[i] = buf.long
            sides[i] = buf.int
            steps[i] = buf.int
            alphas[i] = buf.float
            hues[i] = buf.float
        }
        return OnionPlanBlob(times, sides, steps, alphas, hues)
    }

    // ---- CropBounds ----
    external fun nativeCropVisibleBox(
        cropW: Float, cropH: Float, cropHPct: Float, cropVPct: Float, boxW: Float, boxH: Float,
    ): FloatArray

    external fun nativeCropVisibleRect(
        cropW: Float, cropH: Float, cropHPct: Float, cropVPct: Float, boxW: Float, boxH: Float,
    ): FloatArray

    // ---- GifExport ----
    external fun nativeGifDelayCs(fps: Int): Int
    external fun nativeGifSize(srcW: Int, srcH: Int): IntArray

    // ---- VectorDraw / PathEdit / PenPath ----
    // Anchor blob: [nSubs, (nAnchors, closed, 9 floats per anchor)*]; pen blob:
    // [nAnchors, 9 floats per anchor]. Per anchor: x, y, inX, inY, outX, outY,
    // hasIn, hasOut, smooth.
    external fun nativeVectorBuild(rawXy: FloatArray, aspect: Float, smooth: Boolean, close: Boolean): FloatArray
    external fun nativeVectorNormalize(
        ops: IntArray, counts: IntArray, pts: FloatArray, canvasW: Float, canvasH: Float, density: Float,
    ): FloatArray

    external fun nativePenBuild(
        penBlob: FloatArray, closed: Boolean, canvasW: Float, canvasH: Float, density: Float,
    ): FloatArray

    external fun nativePathFromSegments(ops: IntArray, counts: IntArray, pts: FloatArray): FloatArray
    external fun nativePathToSegments(pathBlob: FloatArray): FloatArray
    external fun nativePathMoveAnchor(pathBlob: FloatArray, sub: Int, idx: Int, x: Float, y: Float): FloatArray
    external fun nativePathMoveHandle(pathBlob: FloatArray, sub: Int, idx: Int, out: Boolean, x: Float, y: Float): FloatArray
    external fun nativePathSplitSegment(pathBlob: FloatArray, sub: Int, seg: Int, t: Float): FloatArray
    external fun nativePathDeleteAnchor(pathBlob: FloatArray, sub: Int, idx: Int): FloatArray
    external fun nativePenTap(penBlob: FloatArray, x: Float, y: Float): FloatArray
    external fun nativePenPull(penBlob: FloatArray, x: Float, y: Float): FloatArray
    external fun nativePenReleaseTap(penBlob: FloatArray): FloatArray
    external fun nativePenUndoPoint(penBlob: FloatArray): FloatArray

    /** Decoded normalize blob: [ok, offsetX, offsetY, scale, segCount, segs..., flatCount, flatXy...]. */
    class NormalizeBlob(
        val ok: Boolean,
        val offsetX: Float,
        val offsetY: Float,
        val scale: Float,
        val segStream: FloatArray,
        val flatFrame: FloatArray,
    )

    fun decodeNormalizeBlob(blob: FloatArray): NormalizeBlob {
        val segCount = blob[4].toInt()
        val segs = blob.copyOfRange(5, 5 + segCount)
        val flatCount = blob[5 + segCount].toInt()
        val flat = blob.copyOfRange(6 + segCount, 6 + segCount + flatCount * 2)
        return NormalizeBlob(blob[0] != 0f, blob[1], blob[2], blob[3], segs, flat)
    }

    // ---- MaskGeometry ----
    // Frame blob: [cx, cy, hw, hh, cosR, sinR, aspect].
    external fun nativeMaskFrameInit(
        sCx: Float, sCy: Float, sW: Float, sH: Float, sRot: Float, aspect: Float,
    ): FloatArray

    external fun nativeMaskToPicture(frame: FloatArray, lx: Float, ly: Float): FloatArray
    external fun nativeMaskToLocal(frame: FloatArray, px: Float, py: Float): FloatArray
    external fun nativeMaskHandles(
        frame: FloatArray, shape: Int, corner: Float, sides: Int, inner: Float, points: FloatArray,
    ): FloatArray

    external fun nativeMaskOutline(
        frame: FloatArray, shape: Int, corner: Float, sides: Int, inner: Float,
        points: FloatArray, smooth: Boolean,
    ): FloatArray

    external fun nativeMaskContains(
        frame: FloatArray, shape: Int, corner: Float, sides: Int, inner: Float,
        points: FloatArray, smooth: Boolean, px: Float, py: Float, lineTol: Float,
    ): Boolean

    external fun nativeMaskDragCorner(frame: FloatArray, i: Int, px: Float, py: Float): FloatArray
    external fun nativeMaskDragEdge(
        frame: FloatArray, shape: Int, state: FloatArray, i: Int, px: Float, py: Float,
    ): FloatArray

    external fun nativeMaskRotateTo(frame: FloatArray, px: Float, py: Float): Float
    external fun nativeMaskMovedBy(
        startCx: Float, startCy: Float, fromX: Float, fromY: Float, toX: Float, toY: Float, aspect: Float,
    ): FloatArray

    external fun nativeMaskDragPoint(
        frame: FloatArray, points: FloatArray, i: Int, px: Float, py: Float,
    ): FloatArray

    external fun nativeMaskInsertPointNear(
        frame: FloatArray, points: FloatArray, px: Float, py: Float, tol: Float,
    ): FloatArray

    external fun nativeMaskHitHandle(
        frame: FloatArray, shape: Int, corner: Float, sides: Int, inner: Float,
        points: FloatArray, px: Float, py: Float, radius: Float,
    ): Int

    external fun nativeDistToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float
    external fun nativeLayerMapOf(cropW: Float, cropH: Float, cropHPct: Float, cropVPct: Float): FloatArray
    external fun nativeLayerMapApply(map: FloatArray, toBox: Boolean, a: Float, b: Float): FloatArray
    external fun nativeMaskNeighbourKey(times: LongArray, relMs: Long, forward: Boolean, toleranceMs: Long): Int

    // ---- TextOnPath ----
    // Table blob: [total, cumCount, xy..., cum...].
    external fun nativeArcTableBuild(xy: FloatArray): FloatArray
    external fun nativeArcPointAt(xy: FloatArray, cum: FloatArray, total: Float, d: Float): FloatArray

    // ---- ClipClipboard ----
    external fun nativeClipboardEarliest(times: LongArray): Long
    external fun nativeClipboardPasteShift(earliest: Long, anchorMs: Long, durationMs: Long): Long
    external fun nativeClipboardMerge(
        tTimes: LongArray, tVals: FloatArray, tTags: IntArray,
        cTimes: LongArray, cVals: FloatArray, cTags: IntArray,
        shift: Long,
    ): ByteArray

    /** Decoded [nativeClipboardMerge] blob: the stage-1 key pack (times, vals stride 6, tags). */
    class KeysBlob(val times: LongArray, val vals: FloatArray, val tags: IntArray)

    fun decodeKeysBlob(blob: ByteArray): KeysBlob {
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        val n = buf.int
        val times = LongArray(n) { buf.long }
        val vals = FloatArray(n * 6) { buf.float }
        val tags = IntArray(n) { buf.int }
        return KeysBlob(times, vals, tags)
    }

    // ---- BlendModes table ----
    external fun nativeBlendNames(): Array<String>
    external fun nativeBlendCategories(): Array<String>
    external fun nativeBlendShaderIds(): IntArray
    external fun nativeBlendKinds(): IntArray
    external fun nativeBlendCategoryList(): Array<String>

    // ---- FxLists / FxTileArt (string laws over UTF-16 code units) ----
    external fun nativeFxParse(raw: String): String
    external fun nativeFxToggle(joined: String, id: String): String
    external fun nativeFxPushRecent(joined: String, id: String, cap: Int): String
    external fun nativeFxSeed(id: String): Int
    external fun nativeFxUnit(seed: Int, salt: Int): Float
    external fun nativeFxHueShift(id: String): Float

    // ---- ToolRowSplit ----
    external fun nativeToolTileWidth(label: String): Int
    external fun nativeToolAvailableDp(widthDp: Int, backButtonDp: Int, contentPaddingDp: Int): Int
    external fun nativeToolVisibleCount(widths: IntArray, availableDp: Int, gapDp: Int): Int
    external fun nativeToolGridHeight(count: Int, tileHeightDp: Int, gapDp: Int, vPaddingDp: Int, maxRows: Int, cols: Int): Int
    external fun nativeToolSharedPanelHeight(overflowHeightDp: Int, utilityHeightDp: Int): Int

    // ---- PagerGrid ----
    external fun nativePagerRowCounts(count: Int, columns: Int): IntArray

    // ---- UsedTools ----
    external fun nativeUsedToolsMask(
        effectsCount: Int,
        hasAnimations: Boolean,
        fillNotSolid: Boolean,
        textColor: Long,
        hasKeyframes: Boolean,
        tx: Float, ty: Float, rot: Float, scale: Float,
        rot3dX: Float, rot3dY: Float, tz: Float,
        cropW: Float, cropH: Float,
        blendNotNormal: Boolean,
        opacity: Float,
        masksCount: Int,
        volume: Float,
        muted: Boolean,
        hasCutout: Boolean,
        hasParent: Boolean,
    ): Int

    // ---- ShapeEditValues ----
    external fun nativeShapeCoerceCorner(dp: Float): Float
    external fun nativeShapeCoerceTrim(start: Float, end: Float): FloatArray

    // ---- TextPaintRules ----
    external fun nativeTextBgArgb(bgColor: Long, bgOpacity: Float): IntArray
    external fun nativeTextLetterSpacingEm(percent: Float): Float
    external fun nativeTextLetterSpacingPx(fontSizePx: Float, percent: Float): Float

    // ---- CanvasMark ----
    external fun nativeCanvasMark(typeOrdinal: Int, selected: Boolean): Int
}
