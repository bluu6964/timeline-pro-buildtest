package com.example.model

import com.example.engine.FrameEvalNative
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The gradient-fill geometry of Alight's `gradientoverlay2` — the def's own shader maths
 * (recovered build 120 from the public dump, boiniArun2006/am-xml
 * `assets/effects/gradientoverlay2.xml`): in layer space the gradient is centred at
 * C = (0.5 + offset.x / 500, 0.5 + offset.y / 500) (AM's effect point params run 500 to the
 * layer box, y down as stored); a linear gradient's t grows along (sin a, -cos a) — angle 0
 * points UP, 90 right — with t = 0 / 1 at C -/+ that direction x scale/2; a radial
 * gradient's t is twice the centre distance / scale; a sweep's t runs clockwise (screen)
 * from the (-cos a, -sin a) ray.
 *
 * The importer stores the geometry as the two end points (gradX1..gradY2) of the FIRST
 * angle/scale; a keyframed angle/scale rides [AnimProp.GRAD_GEOMETRY] (a = angle in
 * degrees, b = scale) and [TimelineClip.at] rebuilds the points per frame from the same
 * centre, so the fill animates exactly like the group op's keyframed angle/scale tails
 * (build 172). The centre is recovered from the stored points (linear: midpoint;
 * radial/sweep: first point), so a handle drag that moves the centre re-anchors the
 * animation with it. One implementation serves the importer and the runtime evaluation —
 * the two can never drift apart.
 */
object GradGeometry {

    /**
     * End points (x1, y1, x2, y2) in layer-norm units for a gradient of [gradType]
     * (GRAD_LINEAR / GRAD_RADIAL / GRAD_SWEEP) centred at ([cx], [cy]) with [angleDeg]
     * and [scale]. Unknown types fall back to linear, like the importer.
     */
    fun endpoints(gradType: Int, cx: Float, cy: Float, angleDeg: Float, scale: Float): FloatArray {
        val out = FloatArray(4)
        FrameEvalNative.nativeGradEndpoints(gradType, cx, cy, angleDeg, scale, out)
        return out
    }

    /** The centre of a stored gradient, recovered from its end points. */
    fun center(gradType: Int, x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> =
        if (gradType == GRAD_LINEAR) (x1 + x2) / 2f to (y1 + y2) / 2f else x1 to y1

    /**
     * (angle in degrees, scale) of a stored gradient, recovered from its end points — the
     * inverse of [endpoints]. The angle is normalised to [0, 360): a full turn is the same
     * geometry, so the stored points cannot tell 90 from 450. A degenerate (zero-length)
     * gradient reads as angle 0, scale 0.
     */
    fun angleScale(gradType: Int, x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
        val out = FloatArray(2)
        FrameEvalNative.nativeGradAngleScale(gradType, x1, y1, x2, y2, out)
        return out[0] to out[1]
    }
}
