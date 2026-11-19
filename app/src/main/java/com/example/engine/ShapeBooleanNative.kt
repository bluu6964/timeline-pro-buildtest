package com.example.engine

import com.example.model.BoolPt
import com.example.model.PathSeg

/** JNI bridge to native double-precision polygon boolean engine (shape_boolean.cpp). */
internal object ShapeBooleanNative {
    val isAvailable: Boolean by lazy {
        try {
            DecoderPool.isAvailable
        } catch (_: Throwable) {
            false
        }
    }

    init {
        // Build 234: the native path is the only path (Kotlin fallbacks removed at
        // Sarowar's order). Touching this bridge loads libam_engine.so via
        // DecoderPool.isAvailable and fails loudly if it is genuinely absent.
        check(DecoderPool.isAvailable) { "libam_engine.so failed to load" }
    }

    const val OP_UNION = 0
    const val OP_SUBTRACT = 1
    const val OP_INTERSECT = 2
    const val OP_XOR = 3

    @JvmStatic external fun nativeCombine(
        sizesA: IntArray,
        coordsA: DoubleArray,
        sizesB: IntArray,
        coordsB: DoubleArray,
        op: Int
    ): DoubleArray

    @JvmStatic external fun nativeWindingNumber(
        px: Double,
        py: Double,
        sizes: IntArray,
        coords: DoubleArray
    ): Int

    @JvmStatic external fun nativeFlattenPathRings(
        ops: IntArray,
        offsets: IntArray,
        counts: IntArray,
        pts: FloatArray,
        stepsPerCurve: Int
    ): DoubleArray

    fun packRegion(region: List<List<BoolPt>>): Pair<IntArray, DoubleArray> {
        val sizes = IntArray(region.size)
        var totalPts = 0
        for (i in region.indices) {
            val sz = region[i].size
            sizes[i] = sz
            totalPts += sz
        }
        val coords = DoubleArray(totalPts * 2)
        var pos = 0
        for (ring in region) {
            for (p in ring) {
                coords[pos++] = p.x
                coords[pos++] = p.y
            }
        }
        return sizes to coords
    }

    fun unpackRegion(packed: DoubleArray): List<List<BoolPt>> {
        if (packed.isEmpty()) return emptyList()
        val ringCount = packed[0].toInt()
        if (ringCount <= 0 || packed.size < 1 + ringCount) return emptyList()
        val sizes = IntArray(ringCount) { packed[1 + it].toInt() }
        var pos = 1 + ringCount
        val out = ArrayList<List<BoolPt>>(ringCount)
        for (sz in sizes) {
            if (sz <= 0) continue
            if (pos + sz * 2 > packed.size) break
            val ring = ArrayList<BoolPt>(sz)
            for (i in 0 until sz) {
                val x = packed[pos++]
                val y = packed[pos++]
                ring += BoolPt(x, y)
            }
            out += ring
        }
        return out
    }

    class PackedSegments(
        val ops: IntArray,
        val offsets: IntArray,
        val counts: IntArray,
        val pts: FloatArray
    )

    fun packSegments(segments: List<PathSeg>): PackedSegments {
        val n = segments.size
        val ops = IntArray(n)
        val offsets = IntArray(n)
        val counts = IntArray(n)
        var total = 0
        for (i in 0 until n) {
            ops[i] = segments[i].op
            offsets[i] = total
            val c = segments[i].pts.size
            counts[i] = c
            total += c
        }
        val pts = FloatArray(total)
        var pos = 0
        for (seg in segments) {
            for (v in seg.pts) pts[pos++] = v
        }
        return PackedSegments(ops, offsets, counts, pts)
    }
}
