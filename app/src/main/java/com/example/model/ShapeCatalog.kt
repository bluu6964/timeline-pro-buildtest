package com.example.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Build 256 — the shape catalog: exactly 100 shape kinds, replacing the old
 * 7-kind picker. Every kind is a real parametric outline in the shape box's
 * own basis (half extents = 1 on both axes, y DOWN — the basis
 * [TimelineClip.pathSegments] and the painters' local space use), stored as
 * one or more closed polygon subpaths. Curves are sampled into polygons at
 * construction; the sampled points ARE the shape — the same arrays feed
 * every renderer, so preview and export cannot drift apart:
 *
 *  - the C++ engine rasterizes them with its exact polygon distance
 *    (kind POLY in shape_engine.cpp, points passed through
 *    [com.example.engine.ShapeNative.nativeDrawShapePoly]);
 *  - the Canvas fallback ([com.example.engine.LayerPainter]) and the
 *    Compose previews replay the same points as paths;
 *  - the shape picker's glyphs are drawn from these very points.
 *
 * The fill rule is EVEN-ODD across subpaths everywhere (the C++ parity
 * test, `Path.fillType = EVEN_ODD` on the Kotlin side): a subpath flagged
 * [Sub.hole] is a cut-out (a ring's inner circle, a frame's window), and
 * disjoint subpaths (pause bars, medal ribbons) all fill. By construction
 * subpaths never overlap — overlapping regions would punch out under
 * even-odd — they may only touch or nest (holes).
 *
 * Generation is pure kotlin.math at fixed sample counts: the same input
 * gives the same points on every run and every platform (the points are
 * computed once in Kotlin and handed to the renderers, never regenerated
 * natively). Legacy patterns (CIRCLE, STAR, PATH, ...) are NOT in this
 * catalog: they keep their historical renderers, so old projects render
 * exactly as before; kinds created from the new panel carry `SH_` patterns.
 */
object ShapeCatalog {

    enum class Category(val label: String) {
        BASICS("Basics"),
        POLYGONS("Polygons"),
        STARS("Stars"),
        ARROWS("Arrows"),
        SYMBOLS("Symbols"),
        BADGES("Badges"),
        NATURE("Nature"),
        DECOR("Decor")
    }

    /** One closed outline: flattened x,y pairs in the unit basis. */
    class Sub(val pts: FloatArray, val hole: Boolean = false) {
        val count: Int get() = pts.size / 2
    }

    class Def(
        val pattern: String,
        val name: String,
        val category: Category,
        val subs: List<Sub>
    ) {
        /** All subpaths concatenated (unit basis) — the JNI / glyph feed. */
        val flat: FloatArray

        /** Points per subpath, in [subs] order. */
        val counts: IntArray

        val totalPoints: Int

        init {
            var total = 0
            for (s in subs) total += s.count
            totalPoints = total
            flat = FloatArray(total * 2)
            counts = IntArray(subs.size)
            var o = 0
            subs.forEachIndexed { i, s ->
                counts[i] = s.count
                s.pts.copyInto(flat, o)
                o += s.pts.size
            }
        }
    }

    val categories: List<Category> = Category.values().toList()

    val all: List<Def> = buildCatalog()

    private val byId: Map<String, Def> = all.associateBy { it.pattern }

    /** The catalog kind for a stored [TimelineClip.shapePattern], or null (legacy / PATH / unknown). */
    fun byPattern(pattern: String?): Def? =
        if (pattern == null) null else byId[pattern.uppercase()]

    fun inCategory(category: Category?): List<Def> =
        if (category == null) all else all.filter { it.category == category }
}

// ---------------------------------------------------------------------------
// Outline builders. Everything works on flat Double lists (x, y pairs) in the
// unit basis and converts once at the end. Walks run clockwise on screen
// (positive shoelace in y-down space), like the legacy kinds.
// ---------------------------------------------------------------------------

private fun pl(): ArrayList<Double> = ArrayList()

private fun ArrayList<Double>.p(x: Double, y: Double) {
    add(x); add(y)
}

private fun ArrayList<Double>.done(): FloatArray {
    // Drop consecutive duplicates (arc joints repeat the shared point) and a
    // closing repeat of the first point: subpaths are implicitly closed.
    val out = FloatArray(size)
    var n = 0
    var lx = Double.NaN
    var ly = Double.NaN
    var i = 0
    while (i + 1 < size) {
        val x = this[i]
        val y = this[i + 1]
        if (n == 0 || abs(x - lx) > 1e-9 || abs(y - ly) > 1e-9) {
            out[n++] = x.toFloat(); out[n++] = y.toFloat()
            lx = x; ly = y
        }
        i += 2
    }
    // A sampled closed curve ends a hair from its start (sin(2*PI) != 0 in
    // floating point): drop the last point when it lands on the first.
    if (n >= 4) {
        val dx = out[n - 2] - out[0]
        val dy = out[n - 1] - out[1]
        if (dx * dx + dy * dy <= 1e-10f) n -= 2
    }
    return out.copyOf(n)
}

private fun sub(flat: FloatArray, hole: Boolean = false): ShapeCatalog.Sub =
    ShapeCatalog.Sub(flat, hole)

private fun def(
    pattern: String,
    name: String,
    category: ShapeCatalog.Category,
    vararg subs: ShapeCatalog.Sub
): ShapeCatalog.Def = ShapeCatalog.Def(pattern, name, category, subs.toList())

private fun rad(deg: Double): Double = deg * PI / 180.0

/** Points of an elliptical arc, both ends included (joints dedupe later). */
private fun arc(
    l: ArrayList<Double>, cx: Double, cy: Double, rx: Double, ry: Double,
    a0Deg: Double, a1Deg: Double, steps: Int
) {
    for (i in 0..steps) {
        val a = rad(a0Deg + (a1Deg - a0Deg) * i / steps)
        l.p(cx + rx * cos(a), cy + ry * sin(a))
    }
}

/** A full ellipse as one closed subpath. */
private fun ellipseSub(
    rx: Double, ry: Double, cx: Double = 0.0, cy: Double = 0.0,
    hole: Boolean = false, steps: Int = 64
): ShapeCatalog.Sub {
    val l = pl()
    arc(l, cx, cy, rx, ry, 0.0, 360.0, steps)
    return sub(l.done(), hole)
}

/** Regular polygon, first vertex at [rotDeg] (default: tip up). */
private fun regPoly(n: Int, r: Double = 1.0, rotDeg: Double = -90.0, squashY: Double = 1.0): FloatArray {
    val l = pl()
    for (i in 0 until n) {
        val a = rad(rotDeg + i * 360.0 / n)
        l.p(r * cos(a), r * squashY * sin(a))
    }
    return l.done()
}

/** Star / burst outline: [points] outer tips alternating with inner valleys. */
private fun starPoly(points: Int, innerRatio: Double, rotDeg: Double = -90.0): FloatArray {
    val l = pl()
    val total = points * 2
    for (i in 0 until total) {
        val a = rad(rotDeg + i * 360.0 / total)
        val r = if (i % 2 == 0) 1.0 else innerRatio
        l.p(r * cos(a), r * sin(a))
    }
    return l.done()
}

/** Rounded rectangle centred at (cx, cy), half extents (hw, hh), corner radius r. */
private fun roundedRect(
    hw: Double, hh: Double, r: Double, cx: Double = 0.0, cy: Double = 0.0, seg: Int = 6
): FloatArray {
    val rr = min(r, min(hw, hh))
    val l = pl()
    // Walk: top edge -> TR arc -> right edge -> BR arc -> bottom -> BL arc -> left -> TL arc.
    arc(l, cx + hw - rr, cy - hh + rr, rr, rr, -90.0, 0.0, seg)
    arc(l, cx + hw - rr, cy + hh - rr, rr, rr, 0.0, 90.0, seg)
    arc(l, cx - hw + rr, cy + hh - rr, rr, rr, 90.0, 180.0, seg)
    arc(l, cx - hw + rr, cy - hh + rr, rr, rr, 180.0, 270.0, seg)
    return l.done()
}

/** Polar outline: radius as a function of the angle (degrees), one closed subpath. */
private fun polarSub(fn: (Double) -> Double, steps: Int = 128): FloatArray {
    val l = pl()
    for (i in 0 until steps) {
        val deg = i * 360.0 / steps
        val a = rad(deg)
        val r = fn(deg)
        l.p(r * cos(a), r * sin(a))
    }
    return l.done()
}

private fun quad(
    l: ArrayList<Double>, x0: Double, y0: Double, cx: Double, cy: Double,
    x1: Double, y1: Double, steps: Int = 14
) {
    for (i in 1..steps) {
        val t = i.toDouble() / steps
        val u = 1.0 - t
        l.p(
            u * u * x0 + 2.0 * u * t * cx + t * t * x1,
            u * u * y0 + 2.0 * u * t * cy + t * t * y1
        )
    }
}

private fun cubic(
    l: ArrayList<Double>, x0: Double, y0: Double, c1x: Double, c1y: Double,
    c2x: Double, c2y: Double, x1: Double, y1: Double, steps: Int = 16
) {
    for (i in 1..steps) {
        val t = i.toDouble() / steps
        val u = 1.0 - t
        l.p(
            u * u * u * x0 + 3.0 * u * u * t * c1x + 3.0 * u * t * t * c2x + t * t * t * x1,
            u * u * u * y0 + 3.0 * u * u * t * c1y + 3.0 * u * t * t * c2y + t * t * t * y1
        )
    }
}

/** Literal polygon from x,y pairs. */
private fun poly(vararg c: Double): FloatArray {
    val l = pl()
    var i = 0
    while (i + 1 < c.size) {
        l.p(c[i], c[i + 1]); i += 2
    }
    return l.done()
}

/** Rotate a flat outline by [deg] about the origin (screen-clockwise positive). */
private fun rot(flat: FloatArray, deg: Double): FloatArray {
    val a = rad(deg)
    val cs = cos(a)
    val sn = sin(a)
    val out = FloatArray(flat.size)
    var i = 0
    while (i + 1 < flat.size) {
        val x = flat[i].toDouble()
        val y = flat[i + 1].toDouble()
        out[i] = (x * cs - y * sn).toFloat()
        out[i + 1] = (x * sn + y * cs).toFloat()
        i += 2
    }
    return out
}

private fun mirrorX(flat: FloatArray): FloatArray {
    val out = FloatArray(flat.size)
    var i = 0
    while (i + 1 < flat.size) {
        out[i] = -flat[i]; out[i + 1] = flat[i + 1]; i += 2
    }
    return out
}

private fun xform(flat: FloatArray, cx: Double, cy: Double, s: Double): FloatArray {
    val out = FloatArray(flat.size)
    var i = 0
    while (i + 1 < flat.size) {
        out[i] = (cx + flat[i] * s).toFloat()
        out[i + 1] = (cy + flat[i + 1] * s).toFloat()
        i += 2
    }
    return out
}

/** Centre a sampled outline on its bbox and scale it uniformly to fit [target]. */
private fun normalizeFit(flat: FloatArray, target: Double = 0.97): FloatArray {
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    var i = 0
    while (i + 1 < flat.size) {
        minX = min(minX, flat[i].toDouble()); maxX = max(maxX, flat[i].toDouble())
        minY = min(minY, flat[i + 1].toDouble()); maxY = max(maxY, flat[i + 1].toDouble())
        i += 2
    }
    val cx = (minX + maxX) / 2.0
    val cy = (minY + maxY) / 2.0
    val half = max(maxX - minX, maxY - minY) / 2.0
    if (half <= 0.0) return flat
    val s = target / half
    val out = FloatArray(flat.size)
    i = 0
    while (i + 1 < flat.size) {
        out[i] = ((flat[i] - cx) * s).toFloat()
        out[i + 1] = ((flat[i + 1] - cy) * s).toFloat()
        i += 2
    }
    return out
}

// ---------------------------------------------------------------------------
// Composite shape builders.
// ---------------------------------------------------------------------------

/** The classic parametric heart, sampled and fitted into the box. */
private fun heartPts(scale: Double, cx: Double, cy: Double): FloatArray {
    val l = pl()
    val n = 72
    for (i in 0 until n) {
        val t = i.toDouble() / n * 2.0 * PI
        val s = sin(t)
        val x = 16.0 * s * s * s
        val y = 13.0 * cos(t) - 5.0 * cos(2.0 * t) - 2.0 * cos(3.0 * t) - cos(4.0 * t)
        l.p(x, -y) // formula is y-up; the box is y-down
    }
    return xform(normalizeFit(l.done(), 1.0), cx, cy, scale)
}

/**
 * Crescent: the outer disc (radius [r]) minus the overlapping disc of radius
 * [r2] whose centre sits [d] to the right. One outline: the outer major arc
 * plus the inner arc through the point of the inner circle nearest the
 * outer centre. In its own frame (outer centre at the origin).
 */
private fun crescentPts(r: Double, d: Double, r2: Double): FloatArray {
    val a = (r * r - r2 * r2 + d * d) / (2.0 * d)
    val h = sqrt(max(0.0, r * r - a * a))
    val alpha = kotlin.math.atan2(h, a) // half-angle of the intersection chord, from the outer centre
    val l = pl()
    // Outer arc: from the top intersection, the long way around the far side.
    val start = -alpha
    val sweep = 2.0 * PI - 2.0 * alpha
    val nOut = 56
    for (i in 0..nOut) {
        val t = start - sweep * i / nOut
        l.p(r * cos(t), r * sin(t))
    }
    // Inner arc: from the bottom intersection back to the top one, through
    // the inner circle's near point (angle PI from its own centre).
    val b0 = kotlin.math.atan2(h, a - d)
    val b1 = kotlin.math.atan2(-h, a - d) + 2.0 * PI
    val nIn = 40
    for (i in 1..nIn) {
        val t = b0 + (b1 - b0) * i / nIn
        l.p(d + r2 * cos(t), r2 * sin(t))
    }
    return l.done()
}

/**
 * Cloud silhouette: the union of [discs] (cx, cy, r triples), sampled as a
 * polar outline about [ox],[oy] (which must sit inside the union), with the
 * bottom flattened at [bottom]. In its own frame.
 */
private fun cloudPts(discs: List<Triple<Double, Double, Double>>, ox: Double, oy: Double, bottom: Double): FloatArray {
    val l = pl()
    val n = 128
    for (i in 0 until n) {
        val t = i.toDouble() / n * 2.0 * PI
        val dx = cos(t)
        val dy = sin(t)
        var best = 0.05
        for ((cx, cy, r) in discs) {
            val ex = cx - ox
            val ey = cy - oy
            val b = ex * dx + ey * dy
            val c = ex * ex + ey * ey - r * r
            val disc = b * b - c
            if (disc >= 0.0) {
                val tt = b + sqrt(disc)
                if (tt > best) best = tt
            }
        }
        val x = ox + best * dx
        val y = min(oy + best * dy, bottom)
        l.p(x, y)
    }
    return l.done()
}

/** Speech bubble: rounded box with a tail on its bottom edge. [r] = corner radius. */
private fun speechPts(r: Double): FloatArray {
    val hw = 0.92
    val top = -0.56
    val bottom = 0.52
    val cxr = hw - r
    val cyt = top + r
    val cyb = bottom - r
    val l = pl()
    l.p(-cxr, top)
    arc(l, cxr, cyt, r, r, -90.0, 0.0, 6)
    arc(l, cxr, cyb, r, r, 0.0, 90.0, 6)
    l.p(0.28, bottom)
    l.p(0.02, 0.94) // tail tip
    l.p(-0.24, bottom)
    arc(l, -cxr, cyb, r, r, 90.0, 180.0, 6)
    arc(l, -cxr, cyt, r, r, 180.0, 270.0, 6)
    return l.done()
}

/**
 * Spiral band: an Archimedean spiral centreline swept at a constant half
 * width. The centreline starts at r = 0.32 — an offset band folds over
 * itself wherever the centreline's radius of curvature drops under the
 * half width, so a spiral that starts near the centre cannot carry a band.
 */
private fun spiralPts(): FloatArray {
    val turns = 4.2 * PI
    val n = 150
    val cx = DoubleArray(n + 1)
    val cy = DoubleArray(n + 1)
    for (i in 0..n) {
        val t = turns * i / n
        val r = 0.32 + 0.145 * (t / PI)
        cx[i] = r * cos(t)
        cy[i] = r * sin(t)
    }
    val w = 0.075
    val l = pl()
    fun side(sign: Double, range: IntProgression) {
        for (i in range) {
            val i0 = max(0, i - 1)
            val i1 = min(n, i + 1)
            var tx = cx[i1] - cx[i0]
            var ty = cy[i1] - cy[i0]
            val len = sqrt(tx * tx + ty * ty)
            if (len > 0.0) {
                tx /= len; ty /= len
            }
            l.p(cx[i] - sign * ty * w, cy[i] + sign * tx * w)
        }
    }
    side(1.0, 0..n)
    side(-1.0, n downTo 0)
    return l.done()
}

/** Curved (return) arrow: an annular sweep with a tangent head at its end. */
private fun curvedArrowPts(): FloatArray {
    val rOut = 0.95
    val rIn = 0.52
    val a0 = 150.0
    val a1 = 390.0
    val l = pl()
    val n = 44
    for (i in 0..n) {
        val a = rad(a0 + (a1 - a0) * i / n)
        l.p(rOut * cos(a), rOut * sin(a))
    }
    val ea = rad(30.0)
    val ex = rOut * cos(ea)
    val ey = rOut * sin(ea)
    val rx = cos(ea)
    val ry = sin(ea)
    l.p(ex + rx * 0.22, ey + ry * 0.22) // head outer base
    l.p(ex - ry * 0.5, ey + rx * 0.5) // head tip (tangent direction)
    l.p(rIn * cos(ea) - rx * 0.18, rIn * sin(ea) - ry * 0.18) // head inner base
    for (i in n downTo 0) {
        val a = rad(a0 + (a1 - a0) * i / n)
        l.p(rIn * cos(a), rIn * sin(a))
    }
    return normalizeFit(l.done(), 0.97)
}

/** Butterfly: a bezier right wing mirrored to the left. */
private fun butterflyPts(): FloatArray {
    val r = pl()
    r.p(0.0, -0.2)
    cubic(r, 0.0, -0.2, 0.3, -0.85, 0.8, -1.0, 0.92, -0.5)
    cubic(r, 0.92, -0.5, 0.98, -0.2, 0.72, 0.05, 0.6, 0.28)
    cubic(r, 0.6, 0.28, 0.85, 0.45, 0.8, 0.75, 0.45, 0.82)
    cubic(r, 0.45, 0.82, 0.25, 0.8, 0.08, 0.6, 0.0, 0.5)
    val right = r.done()
    val out = FloatArray(right.size * 2 - 4)
    right.copyInto(out, 0)
    var o = right.size
    // Mirror of the right half, reversed, without repeating the two centre points.
    var i = right.size - 4
    while (i >= 2) {
        out[o++] = -right[i]
        out[o++] = right[i + 1]
        i -= 2
    }
    return normalizeFit(out.copyOf(o), 0.97)
}

private fun flamePts(): FloatArray {
    val l = pl()
    l.p(0.08, -0.98)
    cubic(l, 0.08, -0.98, -0.15, -0.55, -0.62, -0.25, -0.58, 0.35)
    cubic(l, -0.58, 0.35, -0.55, 0.75, -0.25, 0.92, 0.0, 0.95)
    cubic(l, 0.0, 0.95, 0.35, 0.9, 0.6, 0.55, 0.52, 0.1)
    cubic(l, 0.52, 0.1, 0.45, -0.25, 0.18, -0.5, 0.08, -0.98)
    return normalizeFit(l.done(), 0.97)
}

private fun leafPts(): FloatArray {
    val l = pl()
    l.p(0.8, -0.8)
    quad(l, 0.8, -0.8, 0.0, -0.85, -0.78, 0.78, 18)
    quad(l, -0.78, 0.78, -0.05, 0.85, 0.8, -0.8, 18)
    return normalizeFit(l.done(), 0.97)
}

private fun teardropPts(): FloatArray {
    val l = pl()
    l.p(0.0, -0.98)
    cubic(l, 0.0, -0.98, 0.35, -0.3, 0.8, 0.25, 0.78, 0.52)
    quad(l, 0.78, 0.52, 0.5, 0.95, 0.0, 0.95, 12)
    quad(l, 0.0, 0.95, -0.5, 0.95, -0.78, 0.52, 12)
    cubic(l, -0.78, 0.52, -0.8, 0.25, -0.35, -0.3, 0.0, -0.98)
    return l.done()
}

private fun shieldPts(): FloatArray {
    val l = pl()
    l.p(-0.78, -0.68)
    quad(l, -0.78, -0.68, 0.0, -0.5, 0.78, -0.68, 12)
    quad(l, 0.78, -0.68, 0.86, 0.05, 0.0, 0.97, 16)
    quad(l, 0.0, 0.97, -0.86, 0.05, -0.78, -0.68, 16)
    return l.done()
}

private fun mushroomPts(): FloatArray {
    val l = pl()
    arc(l, 0.0, -0.28, 0.92, 0.6, 180.0, 360.0, 28)
    quad(l, 0.92, -0.28, 0.8, 0.08, 0.3, 0.02, 10)
    l.p(0.36, 0.82)
    quad(l, 0.36, 0.82, 0.0, 0.94, -0.36, 0.82, 10)
    l.p(-0.3, 0.02)
    quad(l, -0.3, 0.02, -0.8, 0.08, -0.92, -0.28, 10)
    return l.done()
}

/**
 * One medal ribbon: hangs straight down from the medal's circle, its top
 * edge an arc of that circle (the vertical sides meet the circle exactly at
 * x = 0.38 and x = 0.10), so it touches the disc and never overlaps it.
 */
private fun medalRibbonPts(): FloatArray {
    val l = pl()
    arc(l, 0.0, -0.24, 0.74, 0.74, 59.2, 82.2, 4)
    l.p(0.10, 0.97)
    l.p(0.24, 0.83)
    l.p(0.38, 0.97)
    return l.done()
}

// ---------------------------------------------------------------------------
// The 100 kinds. Numbered per category in comments; ShapeCatalogTest pins
// the count, the unique patterns and the geometry invariants.
// ---------------------------------------------------------------------------

private fun buildCatalog(): List<ShapeCatalog.Def> {
    val b = ShapeCatalog.Category.BASICS
    val pg = ShapeCatalog.Category.POLYGONS
    val st = ShapeCatalog.Category.STARS
    val ar = ShapeCatalog.Category.ARROWS
    val sy = ShapeCatalog.Category.SYMBOLS
    val bg = ShapeCatalog.Category.BADGES
    val na = ShapeCatalog.Category.NATURE
    val de = ShapeCatalog.Category.DECOR

    val plusPts = poly(
        -0.34, -1.0, 0.34, -1.0, 0.34, -0.34, 1.0, -0.34, 1.0, 0.34, 0.34, 0.34,
        0.34, 1.0, -0.34, 1.0, -0.34, 0.34, -1.0, 0.34, -1.0, -0.34, -0.34, -0.34
    )
    val arrowRight = poly(
        -0.95, -0.26, 0.25, -0.26, 0.25, -0.58, 0.95, 0.0,
        0.25, 0.58, 0.25, 0.26, -0.95, 0.26
    )
    val chevronRight = poly(-0.55, -0.9, 0.45, 0.0, -0.55, 0.9, -0.55, 0.45, 0.0, 0.0, -0.55, -0.45)
    val playPts = poly(-0.5, -0.82, 0.88, 0.0, -0.5, 0.82)
    val pieSlice = pl().apply {
        p(0.0, 0.15)
        arc(this, 0.0, 0.15, 0.95, 0.95, -140.0, -10.0, 24)
    }.done()
    val ticket = pl().apply {
        p(-1.0, -0.62); p(1.0, -0.62); p(1.0, -0.22)
        arc(this, 1.0, 0.0, 0.22, 0.22, -90.0, -270.0, 12)
        p(1.0, 0.62); p(-1.0, 0.62); p(-1.0, 0.22)
        arc(this, -1.0, 0.0, 0.22, 0.22, 90.0, -90.0, 12)
    }.done()
    val semicircle = pl().apply { arc(this, 0.0, 0.0, 1.0, 1.0, 180.0, 360.0, 32) }.done()
    val quarter = pl().apply {
        p(-0.9, 0.9)
        arc(this, -0.9, 0.9, 1.8, 1.8, -90.0, 0.0, 24)
    }.done()
    val arcBand = pl().apply {
        arc(this, 0.0, 0.0, 0.95, 0.95, -160.0, -20.0, 28)
        arc(this, 0.0, 0.0, 0.6, 0.6, -20.0, -160.0, 28)
    }.done()
    val sparkle = pl().apply {
        p(0.0, -1.0)
        quad(this, 0.0, -1.0, 0.14, -0.14, 1.0, 0.0, 12)
        quad(this, 1.0, 0.0, 0.14, 0.14, 0.0, 1.0, 12)
        quad(this, 0.0, 1.0, -0.14, 0.14, -1.0, 0.0, 12)
        quad(this, -1.0, 0.0, -0.14, -0.14, 0.0, -1.0, 12)
    }.done()
    val asterisk = pl().apply {
        for (k in 0 until 6) {
            val base = -90.0 + 60.0 * k
            for ((off, r) in listOf(-30.0 to 0.30, -19.0 to 0.36, 0.0 to 1.0, 19.0 to 0.36)) {
                val a = rad(base + off)
                p(r * cos(a), r * sin(a))
            }
        }
    }.done()
    val waveTop = pl().apply {
        for (i in 0..40) {
            val x = -1.0 + 2.0 * i / 40
            p(x, 0.05 - 0.38 * sin((x + 1.0) * 2.4))
        }
        p(1.0, 0.9); p(-1.0, 0.9)
    }.done()
    val zigzag = pl().apply {
        val xs = listOf(-1.0, -0.75, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0)
        xs.forEachIndexed { i, x -> p(x, if (i % 2 == 0) -0.55 else -0.2) }
        xs.reversed().forEachIndexed { i, x -> p(x, if ((xs.size - 1 - i) % 2 == 0) 0.0 else 0.35) }
    }.done()
    val waveBand = pl().apply {
        for (i in 0..40) {
            val x = -1.0 + 2.0 * i / 40
            p(x, -0.25 + 0.22 * sin((x + 1.0) * 3.6))
        }
        for (i in 40 downTo 0) {
            val x = -1.0 + 2.0 * i / 40
            p(x, 0.17 + 0.22 * sin((x + 1.0) * 3.6))
        }
    }.done()
    val cloudRaw = cloudPts(
        listOf(
            Triple(-0.52, 0.28, 0.40), Triple(-0.13, -0.02, 0.56),
            Triple(0.28, 0.08, 0.47), Triple(0.60, 0.30, 0.34)
        ),
        0.0, 0.12, 0.62
    )
    val cloudFit = normalizeFit(cloudRaw, 0.97)
    val crescentFit = normalizeFit(crescentPts(1.0, 0.45, 0.82), 0.97)
    val cornersTl = poly(-1.0, -1.0, -0.48, -1.0, -0.48, -0.72, -0.72, -0.72, -0.72, -0.48, -1.0, -0.48)
    val leafFit = leafPts()

    return listOf(
        // ---- Basics (12) ----
        def("SH_CIRCLE", "Circle", b, ellipseSub(1.0, 1.0)), // 1
        def("SH_ELLIPSE", "Ellipse", b, ellipseSub(1.0, 0.72)), // 2
        def("SH_SQUARE", "Square", b, sub(poly(-1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0))), // 3
        def("SH_RECT", "Rectangle", b, sub(poly(-1.0, -0.68, 1.0, -0.68, 1.0, 0.68, -1.0, 0.68))), // 4
        def("SH_ROUNDED_SQUARE", "Rounded Square", b, sub(roundedRect(1.0, 1.0, 0.28))), // 5
        def("SH_ROUNDED_RECT", "Rounded Rect", b, sub(roundedRect(1.0, 0.68, 0.26))), // 6
        def("SH_PILL", "Pill", b, sub(roundedRect(1.0, 0.52, 0.52))), // 7
        def("SH_DIAMOND", "Diamond", b, sub(poly(0.0, -1.0, 1.0, 0.0, 0.0, 1.0, -1.0, 0.0))), // 8
        def("SH_TRIANGLE", "Triangle", b, sub(poly(0.0, -1.0, 1.0, 1.0, -1.0, 1.0))), // 9
        def("SH_RIGHT_TRIANGLE", "Right Triangle", b, sub(poly(-1.0, -1.0, 1.0, 1.0, -1.0, 1.0))), // 10
        def("SH_TRAPEZOID", "Trapezoid", b, sub(poly(-0.55, -0.72, 0.55, -0.72, 0.95, 0.72, -0.95, 0.72))), // 11
        def("SH_PARALLELOGRAM", "Parallelogram", b, sub(poly(-0.65, -0.72, 0.95, -0.72, 0.65, 0.72, -0.95, 0.72))), // 12

        // ---- Polygons (12) ----
        def("SH_PENTAGON", "Pentagon", pg, sub(regPoly(5))), // 13
        def("SH_HEXAGON", "Hexagon", pg, sub(regPoly(6, rotDeg = 0.0))), // 14
        def("SH_HEPTAGON", "Heptagon", pg, sub(regPoly(7))), // 15
        def("SH_OCTAGON", "Octagon", pg, sub(regPoly(8, rotDeg = -67.5))), // 16
        def("SH_NONAGON", "Nonagon", pg, sub(regPoly(9))), // 17
        def("SH_DECAGON", "Decagon", pg, sub(regPoly(10))), // 18
        def("SH_DODECAGON", "Dodecagon", pg, sub(regPoly(12))), // 19
        def("SH_PLUS", "Plus", pg, sub(plusPts)), // 20
        def("SH_HOME_PLATE", "Home Plate", pg, sub(poly(-0.95, -0.62, 0.3, -0.62, 0.95, 0.05, 0.3, 0.68, -0.95, 0.68))), // 21
        def("SH_PIE_SLICE", "Pie Slice", pg, sub(pieSlice)), // 22
        def(
            "SH_FRAME", "Frame", pg,
            sub(poly(-1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0)),
            sub(poly(-0.68, -0.68, 0.68, -0.68, 0.68, 0.68, -0.68, 0.68), hole = true)
        ), // 23
        def(
            "SH_CORNERS", "Corners", pg,
            sub(cornersTl), sub(mirrorX(cornersTl)), sub(rot(cornersTl, 180.0)), sub(mirrorX(rot(cornersTl, 180.0)))
        ), // 24

        // ---- Stars (12) ----
        def("SH_STAR4", "Star 4", st, sub(starPoly(4, 0.42))), // 25
        def("SH_STAR5", "Star", st, sub(starPoly(5, 0.45))), // 26
        def("SH_STAR6", "Star 6", st, sub(starPoly(6, 0.5))), // 27
        def("SH_STAR7", "Star 7", st, sub(starPoly(7, 0.52))), // 28
        def("SH_STAR8", "Star 8", st, sub(starPoly(8, 0.54))), // 29
        def("SH_STAR10", "Star 10", st, sub(starPoly(10, 0.58))), // 30
        def("SH_STAR12", "Star 12", st, sub(starPoly(12, 0.62))), // 31
        def("SH_SPARKLE", "Sparkle", st, sub(sparkle)), // 32
        def("SH_BURST8", "Burst 8", st, sub(starPoly(8, 0.78))), // 33
        def("SH_BURST12", "Burst 12", st, sub(starPoly(12, 0.82))), // 34
        def("SH_BURST16", "Burst 16", st, sub(starPoly(16, 0.85))), // 35
        def("SH_ASTERISK", "Asterisk", st, sub(asterisk)), // 36

        // ---- Arrows (12) ----
        def("SH_ARROW_RIGHT", "Arrow Right", ar, sub(arrowRight)), // 37
        def("SH_ARROW_LEFT", "Arrow Left", ar, sub(mirrorX(arrowRight))), // 38
        def("SH_ARROW_UP", "Arrow Up", ar, sub(rot(arrowRight, -90.0))), // 39
        def("SH_ARROW_DOWN", "Arrow Down", ar, sub(rot(arrowRight, 90.0))), // 40
        def("SH_ARROW_NE", "Arrow Up Right", ar, sub(rot(arrowRight, -45.0))), // 41
        def("SH_ARROW_SE", "Arrow Down Right", ar, sub(rot(arrowRight, 45.0))), // 42
        def("SH_CHEVRON_RIGHT", "Chevron Right", ar, sub(chevronRight)), // 43
        def("SH_CHEVRON_LEFT", "Chevron Left", ar, sub(mirrorX(chevronRight))), // 44
        def(
            "SH_DOUBLE_CHEVRON", "Double Chevron", ar,
            sub(xform(chevronRight, -0.45, 0.0, 0.75)), sub(xform(chevronRight, 0.45, 0.0, 0.75))
        ), // 45
        def(
            "SH_ARROW_LR", "Arrow Left Right", ar,
            sub(
                poly(
                    -0.95, 0.0, -0.4, -0.55, -0.4, -0.25, 0.4, -0.25, 0.4, -0.55,
                    0.95, 0.0, 0.4, 0.55, 0.4, 0.25, -0.4, 0.25, -0.4, 0.55
                )
            )
        ), // 46
        def(
            "SH_ARROW_UD", "Arrow Up Down", ar,
            sub(
                rot(
                    poly(
                        -0.95, 0.0, -0.4, -0.55, -0.4, -0.25, 0.4, -0.25, 0.4, -0.55,
                        0.95, 0.0, 0.4, 0.55, 0.4, 0.25, -0.4, 0.25, -0.4, 0.55
                    ), 90.0
                )
            )
        ), // 47
        def("SH_CURVED_ARROW", "Curved Arrow", ar, sub(curvedArrowPts())), // 48

        // ---- Symbols (14) ----
        def("SH_HEART", "Heart", sy, sub(heartPts(0.97, 0.0, 0.0))), // 49
        def("SH_RING", "Ring", sy, ellipseSub(0.97, 0.97), ellipseSub(0.58, 0.58, hole = true)), // 50
        def("SH_CRESCENT", "Crescent", sy, sub(crescentFit)), // 51
        def(
            "SH_CHECK", "Check", sy,
            sub(poly(-0.85, 0.0, -0.3, 0.55, 0.88, -0.6, 0.6, -0.85, -0.32, 0.13, -0.6, -0.25))
        ), // 52
        def("SH_X_MARK", "X Mark", sy, sub(rot(plusPts, 45.0))), // 53
        def(
            "SH_LIGHTNING", "Lightning", sy,
            sub(poly(0.28, -1.0, -0.42, 0.18, -0.05, 0.18, -0.28, 1.0, 0.45, -0.15, 0.08, -0.15))
        ), // 54
        def("SH_TEARDROP", "Teardrop", sy, sub(teardropPts())), // 55
        def("SH_FLAME", "Flame", sy, sub(flamePts())), // 56
        def("SH_PLAY", "Play", sy, sub(playPts)), // 57
        def(
            "SH_PAUSE", "Pause", sy,
            sub(roundedRect(0.24, 0.78, 0.12, cx = -0.42)), sub(roundedRect(0.24, 0.78, 0.12, cx = 0.42))
        ), // 58
        def(
            "SH_FAST_FORWARD", "Fast Forward", sy,
            sub(xform(playPts, -0.48, 0.0, 0.58)), sub(xform(playPts, 0.48, 0.0, 0.58))
        ), // 59
        def(
            "SH_REWIND", "Rewind", sy,
            sub(mirrorX(xform(playPts, -0.48, 0.0, 0.58))), sub(mirrorX(xform(playPts, 0.48, 0.0, 0.58)))
        ), // 60
        def("SH_PLUS_DISC", "Plus Disc", sy, ellipseSub(0.97, 0.97), sub(xform(plusPts, 0.0, 0.0, 0.52), hole = true)), // 61
        def(
            "SH_MINUS_DISC", "Minus Disc", sy,
            ellipseSub(0.97, 0.97),
            sub(poly(-0.55, -0.17, 0.55, -0.17, 0.55, 0.17, -0.55, 0.17), hole = true)
        ), // 62

        // ---- Badges (12) ----
        def("SH_SEAL", "Seal", bg, sub(polarSub({ d -> 0.88 + 0.12 * cos(rad(12.0 * d)) }, 144))), // 63
        def("SH_ROSETTE", "Rosette", bg, sub(polarSub({ d -> 0.85 + 0.15 * cos(rad(16.0 * d)) }, 160))), // 64
        def(
            "SH_MEDAL", "Medal", bg,
            ellipseSub(0.74, 0.74, cy = -0.24), sub(medalRibbonPts()), sub(mirrorX(medalRibbonPts()))
        ), // 65
        def(
            "SH_RIBBON", "Ribbon", bg,
            sub(
                poly(
                    -1.0, -0.28, -0.78, -0.28, -0.78, -0.48, 0.78, -0.48, 0.78, -0.28,
                    1.0, -0.28, 0.88, 0.0, 1.0, 0.28, 0.78, 0.28, 0.78, 0.48,
                    -0.78, 0.48, -0.78, 0.28, -1.0, 0.28, -0.88, 0.0
                )
            )
        ), // 66
        def(
            "SH_TAG", "Tag", bg,
            sub(poly(-0.95, -0.55, 0.35, -0.55, 0.95, 0.0, 0.35, 0.55, -0.95, 0.55)),
            ellipseSub(0.13, 0.13, cx = -0.62, hole = true)
        ), // 67
        def("SH_SHIELD", "Shield", bg, sub(shieldPts())), // 68
        def("SH_TICKET", "Ticket", bg, sub(ticket)), // 69
        def("SH_BOOKMARK", "Bookmark", bg, sub(poly(-0.55, -0.95, 0.55, -0.95, 0.55, 0.95, 0.0, 0.45, -0.55, 0.95))), // 70
        def(
            "SH_CROWN", "Crown", bg,
            sub(poly(-0.95, 0.75, -0.95, -0.55, -0.5, 0.05, 0.0, -0.75, 0.5, 0.05, 0.95, -0.55, 0.95, 0.75))
        ), // 71
        def("SH_GEM", "Gem", bg, sub(poly(-0.55, -0.55, 0.55, -0.55, 0.95, -0.05, 0.0, 0.9, -0.95, -0.05))), // 72
        def(
            "SH_HEX_BADGE", "Hex Badge", bg,
            sub(regPoly(6, rotDeg = 0.0)), sub(regPoly(6, r = 0.58, rotDeg = 0.0), hole = true)
        ), // 73
        def(
            "SH_STAR_BADGE", "Star Badge", bg,
            ellipseSub(0.97, 0.97), sub(xform(starPoly(5, 0.45), 0.0, 0.0, 0.58), hole = true)
        ), // 74

        // ---- Nature (12) ----
        def("SH_LEAF", "Leaf", na, sub(leafFit)), // 75
        def(
            "SH_CLOVER", "Clover", na,
            sub(polarSub({ d -> 0.32 + 0.68 * abs(cos(rad(2.0 * d))).pow(0.7) }, 128))
        ), // 76
        def("SH_FLOWER", "Flower", na, sub(polarSub({ d -> 0.62 + 0.38 * cos(rad(5.0 * d)) }, 140))), // 77
        def(
            "SH_DAISY", "Daisy", na,
            sub(polarSub({ d -> 0.3 + 0.7 * abs(cos(rad(4.0 * d))).pow(0.8) }, 160))
        ), // 78
        def("SH_CLOUD", "Cloud", na, sub(cloudFit)), // 79
        def(
            "SH_PINE", "Pine Tree", na,
            sub(
                poly(
                    0.0, -1.0, 0.34, -0.42, 0.16, -0.42, 0.5, 0.08, 0.24, 0.08, 0.72, 0.58,
                    0.16, 0.58, 0.16, 1.0, -0.16, 1.0, -0.16, 0.58, -0.72, 0.58, -0.24, 0.08,
                    -0.5, 0.08, -0.16, -0.42, -0.34, -0.42
                )
            )
        ), // 80
        def("SH_MUSHROOM", "Mushroom", na, sub(mushroomPts())), // 81
        def("SH_WAVE", "Wave", na, sub(waveTop)), // 82
        def(
            "SH_MOUNTAINS", "Mountains", na,
            sub(poly(-1.0, 0.85, -0.55, -0.15, -0.28, 0.28, 0.05, -0.5, 0.38, 0.05, 0.55, -0.2, 1.0, 0.85))
        ), // 83
        def(
            "SH_MOON_STAR", "Moon & Star", na,
            sub(xform(crescentFit, -0.18, 0.03, 0.82)), sub(xform(starPoly(5, 0.45), 0.58, -0.5, 0.26))
        ), // 84
        def(
            "SH_SPROUT", "Sprout", na,
            sub(poly(-0.055, 0.95, -0.015, 0.05, 0.055, 0.05, 0.03, 0.95)),
            sub(xform(rot(leafFit, -15.0), 0.46, -0.4, 0.42)),
            sub(xform(rot(mirrorX(leafFit), 15.0), -0.46, -0.4, 0.42))
        ), // 85
        def("SH_BUTTERFLY", "Butterfly", na, sub(butterflyPts())), // 86

        // ---- Decor (14) ----
        def("SH_SEMICIRCLE", "Semicircle", de, sub(semicircle)), // 87
        def("SH_QUARTER", "Quarter", de, sub(quarter)), // 88
        def("SH_ARC_BAND", "Arc Band", de, sub(arcBand)), // 89
        def("SH_SPEECH", "Speech Bubble", de, sub(speechPts(0.22))), // 90
        def("SH_SPEECH_SQ", "Speech Box", de, sub(speechPts(0.07))), // 91
        def(
            "SH_THOUGHT", "Thought Bubble", de,
            sub(xform(cloudFit, 0.06, -0.18, 0.88)),
            ellipseSub(0.13, 0.13, cx = -0.32, cy = 0.62, steps = 36),
            ellipseSub(0.08, 0.08, cx = -0.58, cy = 0.88, steps = 28)
        ), // 92
        def("SH_SPIRAL", "Spiral", de, sub(spiralPts())), // 93
        def("SH_ZIGZAG", "Zigzag", de, sub(zigzag)), // 94
        def("SH_WAVE_BAND", "Wave Band", de, sub(waveBand)), // 95
        def(
            "SH_GRID", "Grid", de,
            sub(roundedRect(0.4, 0.4, 0.1, cx = -0.48, cy = -0.48)), sub(roundedRect(0.4, 0.4, 0.1, cx = 0.48, cy = -0.48)),
            sub(roundedRect(0.4, 0.4, 0.1, cx = -0.48, cy = 0.48)), sub(roundedRect(0.4, 0.4, 0.1, cx = 0.48, cy = 0.48))
        ), // 96
        def(
            "SH_DOTS", "Dots", de,
            ellipseSub(0.34, 0.34, cx = -0.5, cy = -0.5, steps = 40), ellipseSub(0.34, 0.34, cx = 0.5, cy = -0.5, steps = 40),
            ellipseSub(0.34, 0.34, cx = -0.5, cy = 0.5, steps = 40), ellipseSub(0.34, 0.34, cx = 0.5, cy = 0.5, steps = 40)
        ), // 97
        def(
            "SH_DIAMOND_RING", "Diamond Ring", de,
            sub(poly(0.0, -1.0, 1.0, 0.0, 0.0, 1.0, -1.0, 0.0)),
            sub(poly(0.0, -0.55, 0.55, 0.0, 0.0, 0.55, -0.55, 0.0), hole = true)
        ), // 98
        def(
            "SH_HEART_RING", "Heart Ring", de,
            sub(heartPts(0.97, 0.0, 0.0)), sub(heartPts(0.5, 0.0, 0.02), hole = true)
        ), // 99
        def(
            "SH_TARGET", "Target", de,
            ellipseSub(0.97, 0.97), ellipseSub(0.62, 0.62, hole = true), ellipseSub(0.3, 0.3, steps = 48)
        ) // 100
    )
}
