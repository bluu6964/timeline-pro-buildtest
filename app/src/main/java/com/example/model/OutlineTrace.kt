package com.example.model

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Build 262 — silhouette tracing for 3D Space extrusion. Given a picture's
 * alpha channel, extract the outline contours of the opaque content inside a
 * region (marching squares over the pixel grid, segments chained into closed
 * polylines, then decimated). The contours are what SpaceRig extrudes: the
 * front face at the layer's plane, the back face pushed along the plane's own
 * normal, and one wall quad per outline segment between them.
 *
 * Pure and host-testable: coordinates are in the alpha array's pixel space
 * (pixel (x, y)'s centre is at (x, y); contour points sit on cell edges at
 * half-pixel positions). Holes come out as their own contours (opposite
 * winding), separate blobs as separate contours.
 */
object OutlineTrace {

    /**
     * Trace the content (alpha >= [threshold]) of [alpha] (row-major, [w]×[h])
     * inside the pixel rect ([x0], [y0])–([x1], [y1]) (inclusive-exclusive).
     * Returns closed contours as flat x,y arrays in pixel space, largest
     * first, with the total point count kept under [maxPoints] by decimation.
     * Contours smaller than ~3×3 px are dropped (speckle, not silhouette).
     */
    fun trace(
        alpha: IntArray, w: Int, h: Int,
        x0: Int, y0: Int, x1: Int, y1: Int,
        threshold: Int = 128, maxPoints: Int = 600
    ): List<FloatArray> {
        if (w <= 0 || h <= 0 || alpha.size < w * h) return emptyList()
        fun aAt(x: Int, y: Int): Int =
            if (x < 0 || y < 0 || x >= w || y >= h) 0 else alpha[y * w + x]

        // Marching squares: for each cell, the segments between cell-edge
        // midpoints. Edge points: T=(i+.5, j), R=(i+1, j+.5), B=(i+.5, j+1),
        // L=(i, j+.5) — keyed by doubled coordinates so they chain exactly.
        val segments = ArrayList<LongArray>()  // (fromKey, toKey) per segment
        fun key(px2: Int, py2: Int): Long = (px2.toLong() shl 32) or (py2.toLong() and 0xFFFFFFFFL)
        val iFrom = maxOf(-1, x0 - 1)
        val iTo = minOf(w - 1, x1)
        val jFrom = maxOf(-1, y0 - 1)
        val jTo = minOf(h - 1, y1)
        for (j in jFrom until jTo) {
            for (i in iFrom until iTo) {
                var case = 0
                if (aAt(i, j) >= threshold) case = case or 8          // TL
                if (aAt(i + 1, j) >= threshold) case = case or 4      // TR
                if (aAt(i + 1, j + 1) >= threshold) case = case or 2  // BR
                if (aAt(i, j + 1) >= threshold) case = case or 1      // BL
                if (case == 0 || case == 15) continue
                val t = key(2 * i + 1, 2 * j)
                val r = key(2 * i + 2, 2 * j + 1)
                val b = key(2 * i + 1, 2 * j + 2)
                val l = key(2 * i, 2 * j + 1)
                fun seg(p: Long, q: Long) { segments.add(longArrayOf(p, q)) }
                when (case) {
                    1 -> seg(l, b)
                    2 -> seg(b, r)
                    3 -> seg(l, r)
                    4 -> seg(t, r)
                    5 -> { seg(t, l); seg(b, r) }   // saddle: either pairing is a valid silhouette
                    6 -> seg(t, b)
                    7 -> seg(t, l)
                    8 -> seg(t, l)
                    9 -> seg(t, b)
                    10 -> { seg(t, r); seg(l, b) }  // saddle
                    11 -> seg(t, r)
                    12 -> seg(l, r)
                    13 -> seg(b, r)
                    14 -> seg(l, b)
                }
            }
        }
        if (segments.isEmpty()) return emptyList()

        // Chain the segments into closed polylines by their shared endpoints.
        // Direction-agnostic: a segment joins the chain from whichever of its
        // endpoints touches the chain's tail (the case table's directions are
        // not uniformly oriented, and for wall quads the winding is irrelevant).
        val byPoint = HashMap<Long, ArrayList<Int>>()
        segments.forEachIndexed { idx, s ->
            byPoint.getOrPut(s[0]) { ArrayList() }.add(idx)
            byPoint.getOrPut(s[1]) { ArrayList() }.add(idx)
        }
        val used = BooleanArray(segments.size)
        val contours = ArrayList<FloatArray>()
        for (s0 in segments.indices) {
            if (used[s0]) continue
            used[s0] = true
            val pts = ArrayList<Long>()
            pts.add(segments[s0][0])
            pts.add(segments[s0][1])
            var guard = segments.size + 1
            while (guard-- > 0) {
                val tail = pts[pts.size - 1]
                if (tail == pts[0] && pts.size > 2) break
                val next = byPoint[tail]?.firstOrNull { !used[it] } ?: break
                used[next] = true
                val ns = segments[next]
                pts.add(if (ns[0] == tail) ns[1] else ns[0])
            }
            if (pts.size < 4) continue
            if (pts[pts.size - 1] == pts[0]) pts.removeAt(pts.size - 1)
            val flat = FloatArray(pts.size * 2)
            pts.forEachIndexed { n, k ->
                flat[n * 2] = (k shr 32).toInt() / 2f
                flat[n * 2 + 1] = (k and 0xFFFFFFFFL).toInt() / 2f
            }
            if (abs(signedArea(flat)) < 9f) continue  // speckle
            contours.add(flat)
        }
        if (contours.isEmpty()) return emptyList()
        contours.sortByDescending { abs(signedArea(it)) }

        // Decimate: drop near-collinear points, with a growing tolerance
        // until the whole set fits the point budget.
        var eps = 0.75f
        var result = contours.map { simplify(it, eps) }
        while (result.sumOf { it.size } / 2 > maxPoints && eps < 8f) {
            eps *= 2f
            result = contours.map { simplify(it, eps) }
        }
        return result.filter { it.size >= 6 }
    }

    /** Shoelace signed area of a closed contour (flat x,y array). */
    fun signedArea(flat: FloatArray): Float {
        var sum = 0f
        val n = flat.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += flat[i * 2] * flat[j * 2 + 1] - flat[j * 2] * flat[i * 2 + 1]
        }
        return sum / 2f
    }

    /** One decimation pass: keep a point when it sits further than [eps] from the chord of its kept neighbours. */
    private fun simplify(flat: FloatArray, eps: Float): FloatArray {
        val n = flat.size / 2
        if (n <= 4) return flat
        val keep = BooleanArray(n) { true }
        var prev = 0
        for (i in 1 until n) {
            val next = (i + 1) % n
            val ax = flat[prev * 2]; val ay = flat[prev * 2 + 1]
            val bx = flat[next * 2]; val by = flat[next * 2 + 1]
            val px = flat[i * 2]; val py = flat[i * 2 + 1]
            val dx = bx - ax; val dy = by - ay
            val len = hypot(dx, dy)
            val dist = if (len < 0.001f) hypot(px - ax, py - ay)
            else abs((px - ax) * dy - (py - ay) * dx) / len
            if (dist < eps) keep[i] = false else prev = i
        }
        val kept = (0 until n).filter { keep[it] }
        if (kept.size < 4) return flat
        val out = FloatArray(kept.size * 2)
        kept.forEachIndexed { m, i -> out[m * 2] = flat[i * 2]; out[m * 2 + 1] = flat[i * 2 + 1] }
        return out
    }
}
