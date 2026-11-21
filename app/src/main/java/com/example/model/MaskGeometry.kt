package com.example.model

import com.example.engine.SceneMathNative
import com.example.engine.ValueLawsNative
import kotlin.math.min

/**
 * The geometry behind editing a mask on the canvas (outline, handles, drag maths). Pure Kotlin, no Compose, so it is
 * unit tested (MaskGeometryTest). Everything here is in PICTURE UNITS: the picture is [aspect] wide and 1 high, origin
 * top-left, so a "square" is square on the screen whatever the picture's shape. The shader uses the same layout
 * (picture pixels = picture units * height).
 *
 * The frame/handle/outline/drag laws live natively in value_laws.cpp since build 247
 * (migration stage 4); this file keeps the types and the packing (a frame crosses as
 * [cx, cy, hw, hh, cosR, sinR, aspect], shapes by their [MaskShape] ints).
 */
data class Pt(val x: Float, val y: Float)

object MaskHandle {
    const val NONE = -1
    const val BODY = 0

    /** 10..13 = corners (top-left, top-right, bottom-right, bottom-left). */
    const val CORNER = 10

    /** 20..23 = edge middles (top, right, bottom, left). */
    const val EDGE = 20
    const val ROTATE = 30

    /** 100 + i = control point i of a pen path. */
    const val POINT = 100
}

/** A mask in picture units: centre, half size, rotation. */
class MaskFrame(val mask: ClipMask, val s: MaskState, val aspect: Float) {
    // The frame law (centre scaling, half-size floor, rotation trig) is native.
    private val frame: FloatArray =
        ValueLawsNative.nativeMaskFrameInit(s.cx, s.cy, s.w, s.h, s.rot, aspect)
    val cx: Float = frame[0]
    val cy: Float = frame[1]
    val hw: Float = frame[2]
    val hh: Float = frame[3]
    val cosR: Float = frame[4]
    val sinR: Float = frame[5]

    private fun pointsFlat(): FloatArray {
        val pts = mask.points
        val flat = FloatArray(pts.size * 2)
        for (i in pts.indices) {
            flat[i * 2] = pts[i].x
            flat[i * 2 + 1] = pts[i].y
        }
        return flat
    }

    fun toPicture(lx: Float, ly: Float): Pt {
        val out = ValueLawsNative.nativeMaskToPicture(frame, lx, ly)
        return Pt(out[0], out[1])
    }

    fun toLocal(p: Pt): Pt {
        val out = ValueLawsNative.nativeMaskToLocal(frame, p.x, p.y)
        return Pt(out[0], out[1])
    }

    /** 0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left. */
    fun corner(i: Int): Pt = toPicture(if (i == 0 || i == 3) -hw else hw, if (i == 0 || i == 1) -hh else hh)

    /** 0 = top, 1 = right, 2 = bottom, 3 = left. */
    fun edge(i: Int): Pt = when (i) {
        0 -> toPicture(0f, -hh)
        1 -> toPicture(hw, 0f)
        2 -> toPicture(0f, hh)
        else -> toPicture(-hw, 0f)
    }

    fun rotateHandle(): Pt =
        if (mask.shape == MaskShape.LINE) toPicture(0f, -ROTATE_OFFSET) else toPicture(0f, -hh - ROTATE_OFFSET)

    fun controlPoint(i: Int): Pt = toPicture(mask.points[i].x * hw * 2f, mask.points[i].y * hh * 2f)

    /** The handles of this shape and where they are (picture units). */
    fun handles(): List<Pair<Int, Pt>> {
        val flat = ValueLawsNative.nativeMaskHandles(
            frame, mask.shape, mask.corner, mask.sides, mask.inner, pointsFlat()
        )
        val out = ArrayList<Pair<Int, Pt>>(flat.size / 3)
        var i = 0
        while (i + 2 < flat.size) {
            out += flat[i].toInt() to Pt(flat[i + 1], flat[i + 2])
            i += 3
        }
        return out
    }

    /** Closed outline of the shape (picture units); a LINE / MIRROR gives its lines, see [isOutlineClosed]. */
    fun outline(): List<Pt> = pairsOf(
        ValueLawsNative.nativeMaskOutline(
            frame, mask.shape, mask.corner, mask.sides, mask.inner, pointsFlat(), mask.smooth
        )
    ).map { Pt(it.first, it.second) }

    val isOutlineClosed: Boolean get() = mask.shape != MaskShape.LINE && mask.shape != MaskShape.MIRROR

    /** True when [p] is on / inside the shape (a hit for "drag the whole mask"). */
    fun containsPoint(p: Pt, lineTol: Float): Boolean =
        ValueLawsNative.nativeMaskContains(
            frame, mask.shape, mask.corner, mask.sides, mask.inner, pointsFlat(), mask.smooth,
            p.x, p.y, lineTol
        )

    // ---- drags (every function returns the new state, nothing is mutated) ----

    /** Dragging corner [i]: the opposite corner stays where it is. */
    fun dragCorner(i: Int, p: Pt): MaskState {
        val out = ValueLawsNative.nativeMaskDragCorner(frame, i, p.x, p.y)
        return s.copy(cx = out[0], cy = out[1], w = out[2], h = out[3])
    }

    /** Dragging an edge middle: the opposite edge stays (a mirror band grows on both sides). */
    fun dragEdge(i: Int, p: Pt): MaskState {
        val out = ValueLawsNative.nativeMaskDragEdge(
            frame, mask.shape, floatArrayOf(s.cx, s.cy, s.w, s.h), i, p.x, p.y
        )
        return s.copy(cx = out[0], cy = out[1], w = out[2], h = out[3])
    }

    /** The rotate handle follows the finger: 0 deg = handle straight above the centre. Snaps to 45 deg steps near them. */
    fun rotateTo(p: Pt): MaskState =
        s.copy(rot = ValueLawsNative.nativeMaskRotateTo(frame, p.x, p.y))

    /** Moves the centre by the finger travel from [from] to [to] (picture units), starting at the state [start]. */
    fun movedBy(start: MaskState, from: Pt, to: Pt): MaskState {
        val out = ValueLawsNative.nativeMaskMovedBy(start.cx, start.cy, from.x, from.y, to.x, to.y, aspect)
        return start.copy(cx = out[0], cy = out[1])
    }

    /** Control point [i] to where the finger is. */
    fun dragPoint(i: Int, p: Pt): ClipMask {
        val out = ValueLawsNative.nativeMaskDragPoint(frame, pointsFlat(), i, p.x, p.y)
        if (out.isEmpty()) return mask
        return mask.copy(points = maskPointsOf(out))
    }

    /**
     * A tap near an edge of the control polygon inserts a point there. Returns the mask with the new point and its
     * index, or null when the tap is farther than [tol] from every edge or the path is full.
     */
    fun insertPointNear(p: Pt, tol: Float): Pair<ClipMask, Int>? {
        val blob = ValueLawsNative.nativeMaskInsertPointNear(frame, pointsFlat(), p.x, p.y, tol)
        if (blob.isEmpty()) return null
        val index = blob[0].toInt()
        return mask.copy(points = maskPointsOf(blob.copyOfRange(1, blob.size))) to index
    }

    companion object {
        const val MIN_HALF = 0.004f
        const val ROTATE_OFFSET = 0.09f
        private const val LINE_LEN = 20f
        private const val ARC_STEPS = 6
        private const val ELLIPSE_STEPS = 64
    }
}

/** A flat xy array -> mask control points. */
private fun maskPointsOf(xy: FloatArray): List<MaskPoint> =
    List(xy.size / 2) { MaskPoint(xy[it * 2], xy[it * 2 + 1]) }

/** Limits shared with [ClipMaskLibrary] (kept here so the geometry file has no other dependency). */
object MaskLimits {
    const val MIN_POINTS = ClipMaskLibrary.MIN_POINTS
    const val MAX_POINTS = ClipMaskLibrary.MAX_POINTS
}

/** Same flattening as am::maskFlattenPath (C++): closed Catmull-Rom curve or the plain polygon. Points in the mask's box. */
fun flattenControlPoints(c: List<MaskPoint>, smooth: Boolean): List<Pt> {
    val n = min(c.size, ClipMaskLibrary.MAX_POINTS)
    if (n < 3) return emptyList()
    val ctrlXy = FloatArray(n * 2)
    for (i in 0 until n) {
        ctrlXy[i * 2] = c[i].x
        ctrlXy[i * 2 + 1] = c[i].y
    }
    val flat = SceneMathNative.nativeFlattenMaskControlPoints(ctrlXy, smooth, ClipMaskLibrary.MAX_POINTS)
    val count = flat.size / 2
    val out = ArrayList<Pt>(count)
    for (i in 0 until count) {
        out += Pt(flat[i * 2], flat[i * 2 + 1])
    }
    return out
}

fun pointInPolygon(p: Pt, poly: List<Pt>): Boolean {
    if (poly.size < 3) return false
    val polyXy = FloatArray(poly.size * 2)
    for (i in poly.indices) {
        polyXy[i * 2] = poly[i].x
        polyXy[i * 2 + 1] = poly[i].y
    }
    return SceneMathNative.nativePointInPolygon(p.x, p.y, polyXy)
}

fun distToSegment(p: Pt, a: Pt, b: Pt): Float =
    ValueLawsNative.nativeDistToSegment(p.x, p.y, a.x, a.y, b.x, b.y)

/** Nearest handle to [p] within [radius] (picture units), else [MaskHandle.NONE]. Rotate / points win over corners. */
fun MaskFrame.hitHandle(p: Pt, radius: Float): Int =
    ValueLawsNative.nativeMaskHitHandle(
        frameArray(), mask.shape, mask.corner, mask.sides, mask.inner, pointsFlatArray(), p.x, p.y, radius
    )

/** The frame blob of this frame (for the extension functions in this file). */
private fun MaskFrame.frameArray(): FloatArray =
    floatArrayOf(cx, cy, hw, hh, cosR, sinR, aspect)

private fun MaskFrame.pointsFlatArray(): FloatArray {
    val pts = mask.points
    val flat = FloatArray(pts.size * 2)
    for (i in pts.indices) {
        flat[i * 2] = pts[i].x
        flat[i * 2 + 1] = pts[i].y
    }
    return flat
}

/**
 * Where the picture sits inside the box a layer is drawn in (crop, flip). Picture coordinates are fractions 0..1 of the
 * whole picture; box coordinates are in px from the centre of the visible box, like [com.example.ui.components.FrameGeo].
 */
class MaskLayerMap(
    private val cropL: Float,
    private val cropT: Float,
    private val cropW: Float,
    private val cropH: Float,
    private val boxW: Float,
    private val boxH: Float,
    private val flipH: Boolean,
    private val flipV: Boolean
) {
    // [cropL, cropT, cropW, cropH, boxW, boxH, flipH, flipV] — the native map layout.
    private val map: FloatArray =
        floatArrayOf(cropL, cropT, cropW, cropH, boxW, boxH, if (flipH) 1f else 0f, if (flipV) 1f else 0f)

    fun pictureToBox(u: Float, v: Float): Pt {
        val out = ValueLawsNative.nativeLayerMapApply(map, true, u, v)
        return Pt(out[0], out[1])
    }

    fun boxToPicture(qx: Float, qy: Float): Pt {
        val out = ValueLawsNative.nativeLayerMapApply(map, false, qx, qy)
        return Pt(out[0], out[1])
    }

    companion object {
        /** [clip] as drawn at the playhead (keyframes applied), [boxW] x [boxH] = its visible size on the canvas. */
        fun of(clip: TimelineClip, boxW: Float, boxH: Float): MaskLayerMap {
            val crop = ValueLawsNative.nativeLayerMapOf(
                clip.cropWidthPercent, clip.cropHeightPercent,
                clip.cropHorizontalPercent, clip.cropVerticalPercent
            )
            return MaskLayerMap(
                crop[0], crop[1], crop[2], crop[3],
                boxW.coerceAtLeast(1f), boxH.coerceAtLeast(1f), clip.isFlippedH, clip.isFlippedV
            )
        }
    }
}

/** Index of the next key after [relMs] (or the previous one when [forward] is false) among all keys of the mask, or -1. */
fun ClipMask.neighbourKey(relMs: Long, forward: Boolean): Int =
    ValueLawsNative.nativeMaskNeighbourKey(
        LongArray(keys.size) { keys[it].timeMs }, relMs, forward, KEYFRAME_TOL_MS
    )
