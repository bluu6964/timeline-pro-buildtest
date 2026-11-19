package com.example.model

import com.example.engine.ShapeBooleanNative
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Shape boolean operations (build 192 — production-push item 5): combine two shape
 * layers' outlines into a new PATH shape layer (Union / Subtract / Intersect / XOR).
 *
 * THE CORE works on flattened rings — lists of [BoolPt] in a shared coordinate space,
 * each ring an implicitly closed polygon (the last point joins back to the first; the
 * closing point is NOT repeated). A region is a list of rings read under the NON-ZERO
 * WINDING rule, because that is the rule the renderer fills shape outlines with:
 * `LayerPainter.drawShape` replays a PATH shape's segments into an
 * `android.graphics.Path` and fills it with the platform default fill type (WINDING =
 * non-zero — the painter's own comment: "The fill follows the path's own winding"), and
 * nothing anywhere in the app sets an even-odd fill for shapes. So a hole is a subpath
 * wound against its outer ring, a same-winding nested subpath is solid, and a
 * self-overlapping outline (a pentagram) fills its centre — and the boolean's
 * inside/outside test below is exactly that winding test, so what the user sees is
 * what gets combined.
 *
 * THE ALGORITHM is the segment-arrangement method: every edge of both regions is split
 * at every intersection (proper crossings, shared vertices, collinear overlaps — and
 * self-crossings inside one region, so a pentagram's edges are classified piecewise).
 * Each resulting sub-segment is classified by sampling the winding numbers of BOTH
 * original regions a hair to its left and right (direct point queries against the
 * untouched inputs, which is what makes coincident / shared edges classify cleanly);
 * a sub-segment is a result boundary iff the op's inside test differs across it, and
 * it is oriented so the result's interior lies on its LEFT (positive-area outer rings,
 * negative-area holes — the representation the non-zero fill renders back identically).
 * Kept segments are then stitched into rings by a face walk (at a vertex, the outgoing
 * edge whose direction is closest counter-clockwise-before the reversed incoming edge;
 * a walk that returns to its start vertex closes — two lobes touching at a point come
 * out as two rings, which render the same as one pinched ring).
 *
 * NUMERICAL HONESTY: all maths is Double. Intersections computed from a pair are
 * inserted into both edges as the SAME point instance (interned), and a crossing that
 * lands on an existing endpoint reuses that endpoint, so stitching joins match exactly
 * in the ordinary cases. Near-zero edges are collapsed before splitting and in the
 * output; collinear middle points are removed. A truly degenerate input (a zero-area
 * outline, a sliver result under the area floor) yields an empty region — an
 * acceptable, documented outcome; the core never emits a non-finite or self-evidently
 * corrupt ring (every emitted ring has >= 3 points and area above the floor).
 *
 * Deliberately NOT modelled (outline-level op): a shape's stroke (only its filled
 * region combines), its `drawing.progress` trim window, its own masks and its effects
 * — those paint over / gate the outline but are not the outline. An open PATH subpath
 * counts as its implicitly closed region — exactly what the fill paints for it, since
 * the platform fill closes open subpaths; a straight open line has ~zero area and so
 * reads as an empty region (ineligible), which is the honest answer for a stroke-only
 * path.
 */
enum class ShapeBooleanOp(val displayName: String) {
    UNION("Union"),
    SUBTRACT("Subtract"),
    INTERSECT("Intersect"),
    XOR("XOR");

    /** The op's inside test over (inside A, inside B). SUBTRACT is A − B: order matters. */
    fun inside(inA: Boolean, inB: Boolean): Boolean = when (this) {
        UNION -> inA || inB
        SUBTRACT -> inA && !inB
        INTERSECT -> inA && inB
        XOR -> inA != inB
    }
}

/** A point of the boolean core (Double; the model works in Float everywhere else). */
data class BoolPt(val x: Double, val y: Double) {
    operator fun plus(o: BoolPt) = BoolPt(x + o.x, y + o.y)
    operator fun minus(o: BoolPt) = BoolPt(x - o.x, y - o.y)
    operator fun times(s: Double) = BoolPt(x * s, y * s)
}

object ShapeBoolean {

    /**
     * The most edges (ring points, summed over BOTH regions) one combine may
     * be fed (build 198 hardening). [combine] splits every edge at every
     * intersection — O(E²) pair work — so past a few thousand edges a single
     * combine runs for seconds (measured in the harness JVM: ~0.6 s at 4,000
     * total edges, ~0.9 s at 6,000, and unbounded beyond). Callers refuse
     * over-budget pairs with [overBudget] and say so honestly instead of
     * freezing the editor; typical shapes are far under (a built-in kind is
     * <= 65 points; 4,000 edges is ~165 Pen curves across both shapes).
     */
    const val MAX_TOTAL_EDGES = 4000

    /** The edge count of a region: one edge per ring point (each ring is implicitly closed). */
    fun edgeCount(region: List<List<BoolPt>>): Int = region.sumOf { it.size }

    /** True when combining [a] and [b] would exceed the edge budget (see [MAX_TOTAL_EDGES]). */
    fun overBudget(a: List<List<BoolPt>>, b: List<List<BoolPt>>): Boolean =
        edgeCount(a) + edgeCount(b) > MAX_TOTAL_EDGES

    /** Signed area of one ring (positive = the winding that fills its interior with +1). */
    fun signedArea(ring: List<BoolPt>): Double {
        var s = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2.0
    }

    /** Total signed area of a region (outer rings positive, holes negative). */
    fun regionArea(region: List<List<BoolPt>>): Double = region.sumOf { signedArea(it) }

    /**
     * The non-zero winding number of [p] against [region]'s rings as authored (the
     * standard upward/downward crossing count; a point is inside iff this is != 0).
     */
    fun windingNumber(p: BoolPt, region: List<List<BoolPt>>): Int {
        if (region.isEmpty()) return 0
        val (sizes, coords) = ShapeBooleanNative.packRegion(region)
        return ShapeBooleanNative.nativeWindingNumber(p.x, p.y, sizes, coords)
    }

    /** Inside test under the renderer's rule (non-zero winding). */
    fun isInside(p: BoolPt, region: List<List<BoolPt>>): Boolean = windingNumber(p, region) != 0

    /**
     * Combines regions [a] and [b] under [op]. SUBTRACT is [a] − [b]. The result is a
     * list of rings oriented positive-outer / negative-hole; it may be empty (an
     * intersect of disjoint shapes, a subtract that removes everything, ...).
     */
    fun combine(
        a: List<List<BoolPt>>,
        b: List<List<BoolPt>>,
        op: ShapeBooleanOp
    ): List<List<BoolPt>> {
        val (sizesA, coordsA) = ShapeBooleanNative.packRegion(a)
        val (sizesB, coordsB) = ShapeBooleanNative.packRegion(b)
        val nativeOp = when (op) {
            ShapeBooleanOp.UNION -> ShapeBooleanNative.OP_UNION
            ShapeBooleanOp.SUBTRACT -> ShapeBooleanNative.OP_SUBTRACT
            ShapeBooleanOp.INTERSECT -> ShapeBooleanNative.OP_INTERSECT
            ShapeBooleanOp.XOR -> ShapeBooleanNative.OP_XOR
        }
        return ShapeBooleanNative.unpackRegion(
            ShapeBooleanNative.nativeCombine(sizesA, coordsA, sizesB, coordsB, nativeOp)
        )
    }
}

/**
 * A stored PATH outline as boolean rings: the segments are flattened at the model's
 * standard rate (24 samples per curve — the same points [flattenPathSegments] walks
 * and the shape's own fill draws through), split into subpaths at every MOVE. Every
 * subpath is one ring: an open subpath counts as its implicitly closed region, which
 * is what the platform fill paints for it. Points come back as Doubles in the stored
 * segments' own basis ([TimelineClip.pathSegments]: centred, the larger half extent
 * spanning [-1, 1]).
 */
fun flattenPathRings(segments: List<PathSeg>, stepsPerCurve: Int = 24): List<List<BoolPt>> {
    if (segments.isEmpty() || stepsPerCurve <= 0) return emptyList()
    val packed = ShapeBooleanNative.packSegments(segments)
    return ShapeBooleanNative.unpackRegion(
        ShapeBooleanNative.nativeFlattenPathRings(
            packed.ops, packed.offsets, packed.counts, packed.pts, stepsPerCurve
        )
    )
}

/** Samples of a full circle / ellipse outline (the primitives are analytic in the
 * renderers — no stored flattening exists to copy; 64 is the boolean's own sampling). */
private const val PRIMITIVE_CURVE_SAMPLES = 64

/** Samples of one rounded-rect corner arc. */
private const val CORNER_ARC_SAMPLES = 16

/**
 * The outline of a built-in shape kind in the shape box's own basis (the half box =
 * 1 on both axes — the [TimelineClip.pathSegments] basis), mirroring the geometry the
 * renderers draw: `shape_engine.cpp`'s SDF kinds (via `ShapeNative`'s kind map —
 * RECT / ROUNDED family / TRIANGLE / PENTAGON / STAR exact vertices, OVAL = ellipse
 * 1.2 x 0.75, everything else — CIRCLE and any unknown name — a circle, exactly the
 * painters' own fallback) and `LayerPainter`'s replay for the same names. Rounded
 * corners take the clip's own radii ([TimelineClip.shapeCornerRadii] at base 1 — the
 * per-axis box-unit radii), else the engine's built-in corner (16/65 of the half box,
 * 10/65 for WIDE_ROUNDED_RECT — the values `ShapeNative` passes when none is stored).
 * Rings come back wound positive (their interior fills +1).
 */
fun primitiveShapeRings(clip: TimelineClip): List<List<BoolPt>> {
    fun ringOf(pts: List<Pair<Double, Double>>): List<BoolPt> = pts.map { BoolPt(it.first, it.second) }
    fun ellipse(rx: Double, ry: Double): List<BoolPt> {
        val out = ArrayList<BoolPt>(PRIMITIVE_CURVE_SAMPLES)
        for (i in 0 until PRIMITIVE_CURVE_SAMPLES) {
            val t = i.toDouble() / PRIMITIVE_CURVE_SAMPLES * 2.0 * Math.PI
            out += BoolPt(rx * cos(t), ry * sin(t))
        }
        return out
    }
    return when ((clip.shapePattern ?: "CIRCLE").uppercase()) {
        "RECT", "RECTANGLE", "SQUARE" ->
            listOf(ringOf(listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)))
        "ROUNDED", "ROUNDED_SQUARE", "ROUNDED_RECT", "WIDE_ROUNDED_RECT" -> {
            val stored = clip.shapeCornerRadii(1f)
            val def = if ((clip.shapePattern ?: "").uppercase() == "WIDE_ROUNDED_RECT") 10.0 / 65.0 else 16.0 / 65.0
            val rx = (stored?.first?.toDouble() ?: def).coerceIn(0.0, 1.0)
            val ry = (stored?.second?.toDouble() ?: def).coerceIn(0.0, 1.0)
            if (rx <= 0.0 || ry <= 0.0) {
                listOf(ringOf(listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)))
            } else {
                val out = ArrayList<BoolPt>()
                // Corners in walk order (positive winding): top-right, bottom-right,
                // bottom-left, top-left — each a quarter ellipse around its centre.
                val corners = listOf(
                    Triple(1.0 - rx, -1.0 + ry, -Math.PI / 2.0),
                    Triple(1.0 - rx, 1.0 - ry, 0.0),
                    Triple(-1.0 + rx, 1.0 - ry, Math.PI / 2.0),
                    Triple(-1.0 + rx, -1.0 + ry, Math.PI)
                )
                for ((cx, cy, a0) in corners) {
                    for (i in 0..CORNER_ARC_SAMPLES) {
                        val t = a0 + (i.toDouble() / CORNER_ARC_SAMPLES) * (Math.PI / 2.0)
                        val p = BoolPt(cx + rx * cos(t), cy + ry * sin(t))
                        if (out.isEmpty() || out.last() != p) out += p
                    }
                }
                // The walk's final arc point is the first point again: drop the repeat.
                while (out.size > 1 && out.last() == out.first()) out.removeAt(out.size - 1)
                listOf(out)
            }
        }
        "OVAL", "ELLIPSE" -> listOf(ellipse(1.2, 0.75))
        "TRIANGLE" -> listOf(ringOf(listOf(0.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)))
        "PENTAGON" -> listOf((0 until 5).map { i ->
            val t = Math.toRadians(-90.0 + 72.0 * i)
            BoolPt(cos(t), sin(t))
        })
        "STAR" -> listOf((0 until 10).map { i ->
            val t = Math.toRadians(-90.0 + 36.0 * i)
            val r = if (i % 2 == 0) 1.0 else 0.45
            BoolPt(r * cos(t), r * sin(t))
        })
        else -> listOf(ellipse(1.0, 1.0)) // CIRCLE, and the painters' fallback for unknown kinds
    }
}

/**
 * This layer's filled region as boolean rings in the shape box's basis, or null when
 * the layer has no combinable region. The rules (see [ShapeBoolean]'s header): a
 * SHAPE layer with a fill (`fillMode != FILL_NONE` — a fill-less layer's only paint
 * is its stroke, which has no region); a PATH layer contributes [flattenPathRings] of
 * its stored segments, a built-in kind its [primitiveShapeRings]; and a crop window,
 * when one is set, cuts the region first (the painters clip the fill to the window in
 * the box's own space — the model's [cropVisibleRect] law at box 2 x 2, re-centred),
 * so the boolean sees the shape AS DRAWN. A region whose every ring is degenerate
 * (zero area — a straight open line, a fully cropped sliver) reads as null.
 *
 * The clip must be the layer AS EVALUATED at the playhead (keyframed corner radius,
 * a `.pie` / `.arc`'s rebuilt segments, ...) — the caller evaluates; this function
 * only reads the fields it is handed.
 */
fun TimelineClip.shapeBooleanRings(): List<List<BoolPt>>? {
    if (type != LayerType.SHAPE) return null
    if (fillMode == FILL_NONE) return null
    var rings = if ((shapePattern ?: "").uppercase() == "PATH") {
        if (pathSegments.isEmpty()) return null
        flattenPathRings(pathSegments)
    } else {
        primitiveShapeRings(this)
    }
    // Edge budget (build 198): past ShapeBoolean.MAX_TOTAL_EDGES the crop cut
    // below — and any pair combine this region would feed — is O(E^2) pair
    // work measured in seconds. The shape reads as having no combinable
    // region (the boolean row simply does not offer), never as a freeze.
    if (ShapeBoolean.edgeCount(rings) > ShapeBoolean.MAX_TOTAL_EDGES) return null
    val win = cropVisibleRect(2f, 2f)
    if (win != null && (win.width < 2f - 1e-4f || win.height < 2f - 1e-4f)) {
        val l = win.left.toDouble() - 1.0
        val t = win.top.toDouble() - 1.0
        val r = win.right.toDouble() - 1.0
        val b = win.bottom.toDouble() - 1.0
        val rect = listOf(BoolPt(l, t), BoolPt(r, t), BoolPt(r, b), BoolPt(l, b))
        rings = ShapeBoolean.combine(rings, listOf(rect), ShapeBooleanOp.INTERSECT)
    }
    val real = rings.filter { it.size >= 3 && abs(ShapeBoolean.signedArea(it)) > 1e-9 }
    return real.ifEmpty { null }
}

/**
 * Cheap stored-fields eligibility for the boolean row (the playhead evaluation in
 * the engine can still refuse — a pose placement, behind the camera, a `.pie` closed
 * at this instant): a visible, non-mask SHAPE layer whose stored outline holds a
 * filled region. The selection / timing gates live with the caller.
 */
val TimelineClip.isBooleanShapeCandidate: Boolean
    get() = isVisible && !isMask && shapeBooleanRings() != null
