package com.example.engine

import com.example.model.DEPTH_D
import com.example.model.SpaceOps
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import com.example.model.depthFactorOf

/**
 * Build 262 — one 3D Space's camera and scene tools (Sarowar's feature; see
 * [com.example.model.LayerType.SPACE3D]). The mirror of [CameraRig] for a
 * single space: the camera is the SPACE CLIP'S OWN evaluated transform, read
 * exactly like a Camera layer's pose (transformX/Y = pan, transformZ =
 * dolly, scalePercent = zoom, rotation3dX = pitch, rotation3dY = yaw,
 * rotationDeg = roll) — so the space camera is keyframable through the
 * normal transform keyframes, and a project Camera layer never drives it.
 *
 * Children are placed with the same C++ pinhole projection as camera layers
 * (cpp/engine/camera.cpp): media as corner-pinned planes, text / shape /
 * sticker as upright pictures pinned on their projected corners, everything
 * depth-sorted by [stackKey]. On top of that this rig builds the scene's
 * own geometry: the grid floor (projected world points through
 * cameraPoints) and the extrusion of TEXT / SHAPE children (their traced
 * silhouette outline pushed along the child's own plane normal).
 *
 * Preview and export share this class, like CameraRig.
 */
internal class SpaceRig(val space: TimelineClip, private val parents: ParentRig) {

    val spaceId: String get() = space.id

    /** True when [c] sits inside this space. */
    fun affects(c: TimelineClip): Boolean = c.spaceId == space.id

    /** True when the space is up at [t] (visible and inside its span): its children are placed by the space camera then. */
    fun activeFor(c: TimelineClip, t: Long): Boolean =
        affects(c) && space.isVisible && space.isActiveAt(t)

    /** [panX, panY, dolly, zoom, rotX, rotY, roll] of the space's camera at [t] (positions in output px). */
    fun pose(t: Long, k: Float): FloatArray {
        val r = parents.adjusted(space, t)
        return floatArrayOf(
            r.transformX * k, r.transformY * k, r.transformZ * k, (r.scalePercent / 100f).coerceAtLeast(0.001f),
            r.rotation3dX, r.rotation3dY, r.rotationDeg
        )
    }

    private val inA = FloatArray(kCamIn)
    private val outA = FloatArray(8)

    private fun cast(cam: FloatArray): CameraRig.Seen {
        inA[17] = 1f
        inA[18] = cam[0]; inA[19] = cam[1]; inA[20] = cam[2]; inA[21] = cam[3]
        inA[22] = cam[4]; inA[23] = cam[5]; inA[24] = cam[6]
        return CameraRig.Seen(if (AnimNative.nativeCameraQuad(inA, outA)) outA.copyOf() else null)
    }

    /**
     * Stacking key of a space child (bigger = nearer = drawn later), the same
     * rule as [CameraRig.stackKey]: minus the centre distance from the space
     * camera. Null = the space is not up at [t] (the caller falls back).
     */
    @Synchronized
    fun stackKey(c: TimelineClip, t: Long): Float? {
        if (!activeFor(c, t)) return null
        val cam = pose(t, 1f)
        java.util.Arrays.fill(inA, 0f)
        inA[6] = c.transformX; inA[7] = c.transformY
        inA[8] = c.transformZ
        inA[11] = c.rotationDeg; inA[12] = c.rotation3dX; inA[13] = c.rotation3dY
        inA[16] = DEPTH_D
        inA[17] = 1f
        inA[18] = cam[0]; inA[19] = cam[1]; inA[20] = cam[2]; inA[21] = cam[3]
        inA[22] = cam[4]; inA[23] = cam[5]; inA[24] = cam[6]
        val d = AnimNative.nativeCameraDepth(inA)
        return if (d.isNaN()) CameraRig.BEHIND_KEY else -d
    }

    /** A video / photo child of the space: the same placement as [CameraRig.seeMedia], with the space's camera. */
    @Synchronized
    fun seeMedia(
        c: TimelineClip, t: Long, fp: FloatArray, z: Float, contentW: Int, contentH: Int, k: Float, canvasW: Int, canvasH: Int
    ): CameraRig.Seen? {
        if (!activeFor(c, t)) return null
        val cam = pose(t, k)
        val f = depthFactorOf(z)  // fp is after the depth factor; the camera does the depth itself
        java.util.Arrays.fill(inA, 0f)
        inA[0] = canvasW.toFloat(); inA[1] = canvasH.toFloat()
        inA[2] = contentW.toFloat(); inA[3] = contentH.toFloat()
        inA[4] = fp[8]; inA[5] = fp[9]
        inA[6] = fp[14] / f; inA[7] = fp[15] / f
        inA[8] = z * k
        inA[9] = fp[2] / f; inA[10] = fp[3] / f
        inA[11] = fp[4]; inA[12] = fp[10]; inA[13] = fp[11]
        inA[14] = fp[12]; inA[15] = fp[13]
        inA[16] = DEPTH_D * k
        return cast(cam)
    }

    /** The in[] of [seeOverlay] for [c] (its placement + the space's camera), shared by the quad and the points paths. */
    private fun overlayIn(c: TimelineClip, boxW: Float, boxH: Float, k: Float, canvasW: Int, canvasH: Int, t: Long) {
        val cam = pose(t, k)
        // Same placement maths as CameraRig.seeOverlay: the picture has the scale and flips
        // baked in (about the box centre); the pivot / base compensate for that.
        val s = (c.scalePercent / 100f).coerceAtLeast(0.01f)
        val fx = if (c.isFlippedH) -1f else 1f
        val fy = if (c.isFlippedV) -1f else 1f
        val px = (c.pivotX - 0.5f) * boxW
        val py = (c.pivotY - 0.5f) * boxH
        val dx = px * (1f - s * fx)
        val dy = py * (1f - s * fy)
        java.util.Arrays.fill(inA, 0f)
        inA[0] = canvasW.toFloat(); inA[1] = canvasH.toFloat()
        inA[2] = canvasW.toFloat(); inA[3] = canvasH.toFloat()
        inA[4] = 1f; inA[5] = 1f
        inA[6] = c.transformX * k + dx; inA[7] = c.transformY * k + dy
        inA[8] = c.transformZ * k
        inA[9] = 1f; inA[10] = 1f
        inA[11] = c.rotationDeg; inA[12] = c.rotation3dX; inA[13] = c.rotation3dY
        inA[14] = px - dx; inA[15] = py - dy
        inA[16] = DEPTH_D * k
        inA[17] = 1f
        inA[18] = cam[0]; inA[19] = cam[1]; inA[20] = cam[2]; inA[21] = cam[3]
        inA[22] = cam[4]; inA[23] = cam[5]; inA[24] = cam[6]
    }

    /** A text / shape / sticker child of the space: its upright picture pinned on the projected corners. */
    @Synchronized
    fun seeOverlay(
        c: TimelineClip, t: Long, boxW: Float, boxH: Float, k: Float, canvasW: Int, canvasH: Int
    ): CameraRig.Seen? {
        if (!activeFor(c, t)) return null
        overlayIn(c, boxW, boxH, k, canvasW, canvasH, t)
        return CameraRig.Seen(if (AnimNative.nativeCameraQuad(inA, outA)) outA.copyOf() else null)
    }

    /**
     * Arbitrary points of the child's picture plane through the space camera
     * (cameraPoints): [pts] = triples (qx, qy, qz) in output px — qx / qy from
     * the picture's centre, qz along the plane's own normal (+ = into the
     * screen, so an extrusion's back face sits at qz = +depth). Returns the
     * projected canvas points (pairs) and the per-point in-front flags.
     */
    @Synchronized
    fun projectOverlayPoints(
        c: TimelineClip, t: Long, boxW: Float, boxH: Float, k: Float, canvasW: Int, canvasH: Int, pts: FloatArray
    ): Pair<FloatArray, BooleanArray> {
        overlayIn(c, boxW, boxH, k, canvasW, canvasH, t)
        val n = pts.size / 3
        val out = FloatArray(n * 2)
        val ok = BooleanArray(n)
        AnimNative.nativeCameraPoints(inA, pts, out, ok)
        return out to ok
    }

    /**
     * The grid floor's quads for [NativeRenderer.drawQuads]: (verts, colors,
     * quadCount), or null when the grid is off or fully behind the camera.
     * The floor is a [SpaceOps.GRID_CELLS]×[SpaceOps.GRID_CELLS] square of
     * side spaceGridSize at the scene's ground height, projected with an
     * identity layer placement through the space camera — so the grid IS
     * part of the scene (Sarowar's order: preview and export alike). The two
     * axis lines (X red, Z blue, as in his reference) are thicker and stronger.
     */
    @Synchronized
    fun gridQuads(t: Long, k: Float, canvasW: Int, canvasH: Int): Triple<FloatArray, FloatArray, Int>? {
        if (!space.spaceGridOn) return null
        val cam = pose(t, k)
        java.util.Arrays.fill(inA, 0f)
        inA[0] = canvasW.toFloat(); inA[1] = canvasH.toFloat()
        inA[2] = canvasW.toFloat(); inA[3] = canvasH.toFloat()
        inA[4] = 1f; inA[5] = 1f
        inA[9] = 1f; inA[10] = 1f
        inA[16] = DEPTH_D * k
        inA[17] = 1f
        inA[18] = cam[0]; inA[19] = cam[1]; inA[20] = cam[2]; inA[21] = cam[3]
        inA[22] = cam[4]; inA[23] = cam[5]; inA[24] = cam[6]

        val s = space.spaceGridSize.coerceIn(100f, 20_000f) * k
        val gy = SpaceOps.GROUND_Y_FRAC * canvasH
        val cells = SpaceOps.GRID_CELLS
        val cell = s / cells
        val half = s / 2f
        val thGrid = 1.3f * k
        val thAxis = 3f * k
        // Line quads in world points (x, groundY, z): first the grid lines,
        // then the two axis lines. Colour index parallel to the quads.
        val world = ArrayList<FloatArray>()
        val kinds = ArrayList<Int>()  // 0 grid, 1 X axis, 2 Z axis
        for (i in 0..cells) {
            val at = -half + i * cell
            if (i != cells / 2) {
                world.add(floatArrayOf(at - thGrid, gy, -half, at + thGrid, gy, -half, at + thGrid, gy, half, at - thGrid, gy, half))
                kinds.add(0)
                world.add(floatArrayOf(-half, gy, at - thGrid, half, gy, at - thGrid, half, gy, at + thGrid, -half, gy, at + thGrid))
                kinds.add(0)
            }
        }
        // X axis: the line along X at z = 0 (red). Z axis: the line along Z at x = 0 (blue).
        world.add(floatArrayOf(-half, gy, -thAxis, half, gy, -thAxis, half, gy, thAxis, -half, gy, thAxis))
        kinds.add(1)
        world.add(floatArrayOf(-thAxis, gy, -half, thAxis, gy, -half, thAxis, gy, half, -thAxis, gy, half))
        kinds.add(2)

        val pts = FloatArray(world.size * 4 * 3)
        world.forEachIndexed { qi, quad ->
            for (c in 0 until 4) {
                pts[(qi * 4 + c) * 3] = quad[c * 3]
                pts[(qi * 4 + c) * 3 + 1] = quad[c * 3 + 1]
                pts[(qi * 4 + c) * 3 + 2] = quad[c * 3 + 2]
            }
        }
        val out = FloatArray(world.size * 4 * 2)
        val ok = BooleanArray(world.size * 4)
        AnimNative.nativeCameraPoints(inA, pts, out, ok)

        val verts = ArrayList<Float>()
        val colors = ArrayList<Float>()
        var count = 0
        for (qi in world.indices) {
            var all = true
            for (c in 0 until 4) if (!ok[qi * 4 + c]) { all = false; break }
            if (!all) continue
            for (c in 0 until 4) {
                verts.add(out[(qi * 4 + c) * 2])
                verts.add(out[(qi * 4 + c) * 2 + 1])
            }
            when (kinds[qi]) {
                1 -> { colors.add(1f); colors.add(0.24f); colors.add(0.24f); colors.add(0.85f) }
                2 -> { colors.add(0.25f); colors.add(0.47f); colors.add(1f); colors.add(0.85f) }
                else -> { colors.add(1f); colors.add(1f); colors.add(1f); colors.add(0.15f) }
            }
            count++
        }
        if (count == 0) return null
        return Triple(verts.toFloatArray(), colors.toFloatArray(), count)
    }

    /**
     * The extrusion WALL quads of a TEXT / SHAPE child for
     * [NativeRenderer.drawQuads]: one quad per outline segment, between the
     * front outline (the child's plane) and the back outline pushed
     * [depthProj] (stored units) along the plane's own normal. [outline] =
     * contours in the child's frame-sized picture px (see
     * [com.example.model.OutlineTrace]); the colour is the child's face
     * colour darkened by [SpaceOps.WALL_SHADE], at [opacity]. Null when
     * nothing survives (depth 0, empty outline, all behind the camera).
     */
    fun wallQuads(
        c: TimelineClip, t: Long, boxW: Float, boxH: Float, k: Float, canvasW: Int, canvasH: Int,
        outline: List<FloatArray>, depthProj: Float, opacity: Float
    ): Triple<FloatArray, FloatArray, Int>? {
        val depthPx = depthProj * k
        if (depthPx <= 0.01f || outline.isEmpty()) return null
        val total = outline.sumOf { it.size / 2 }
        if (total == 0) return null
        // Project every outline point twice: front (qz = 0) and back (qz = +depth).
        val pts = FloatArray(total * 2 * 3)
        var p = 0
        for (contour in outline) {
            for (i in 0 until contour.size / 2) {
                val qx = contour[i * 2] - canvasW / 2f
                val qy = contour[i * 2 + 1] - canvasH / 2f
                pts[p++] = qx; pts[p++] = qy; pts[p++] = 0f
                pts[p++] = qx; pts[p++] = qy; pts[p++] = depthPx
            }
        }
        val (out, ok) = projectOverlayPoints(c, t, boxW, boxH, k, canvasW, canvasH, pts)
        val wall = SpaceOps.wallColorArgb(SpaceOps.faceColorOf(c))
        val wr = ((wall shr 16) and 0xFF).toFloat() / 255f
        val wg = ((wall shr 8) and 0xFF).toFloat() / 255f
        val wb = (wall and 0xFF).toFloat() / 255f
        val wa = (((wall shr 24) and 0xFF).toFloat() / 255f) * opacity.coerceIn(0f, 1f)
        val verts = ArrayList<Float>()
        val colors = ArrayList<Float>()
        var count = 0
        var base = 0  // index of the contour's first point in the projected set
        for (contour in outline) {
            val n = contour.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                val fi = base + i * 2; val fj = base + j * 2
                val bi = fi + 1; val bj = fj + 1
                if (!ok[fi] || !ok[fj] || !ok[bi] || !ok[bj]) continue
                // Quad: front_i, front_j, back_j, back_i (perimeter order).
                verts.add(out[fi * 2]); verts.add(out[fi * 2 + 1])
                verts.add(out[fj * 2]); verts.add(out[fj * 2 + 1])
                verts.add(out[bj * 2]); verts.add(out[bj * 2 + 1])
                verts.add(out[bi * 2]); verts.add(out[bi * 2 + 1])
                colors.add(wr); colors.add(wg); colors.add(wb); colors.add(wa)
                count++
            }
            base += n * 2
        }
        if (count == 0) return null
        return Triple(verts.toFloatArray(), colors.toFloatArray(), count)
    }

    /**
     * The back-face pin quad of an extruded child: the picture's four corners
     * pushed [depthProj] (stored units) along the child's plane normal.
     * Null = a corner is behind the camera (the caller then draws the front
     * face alone, without extrusion).
     */
    fun backQuad(
        c: TimelineClip, t: Long, boxW: Float, boxH: Float, k: Float, canvasW: Int, canvasH: Int, depthProj: Float
    ): FloatArray? {
        val depthPx = depthProj * k
        if (depthPx <= 0.01f) return null
        val hw = canvasW / 2f; val hh = canvasH / 2f
        val pts = floatArrayOf(
            -hw, -hh, depthPx, hw, -hh, depthPx, hw, hh, depthPx, -hw, hh, depthPx
        )
        val (out, ok) = projectOverlayPoints(c, t, boxW, boxH, k, canvasW, canvasH, pts)
        if (ok.any { !it }) return null
        return out
    }

    companion object {
        private const val kCamIn = 25
    }
}

/**
 * Build 262 — the engines' one camera dispatcher: a clip inside a 3D Space
 * is placed by its space's [SpaceRig]; every other clip by the project
 * [CameraRig], exactly as before. When a project has no spaces this behaves
 * identically to CameraRig alone (every call delegates).
 */
internal class SceneCameras(tracks: List<TimelineTrack>, parents: ParentRig) {
    val cam = CameraRig(tracks, parents)
    private val rigs: List<SpaceRig> = SpaceOps.spacesOf(tracks).filter { it.isVisible }.map { SpaceRig(it, parents) }
    private val bySpaceId: Map<String, SpaceRig> = rigs.associateBy { it.spaceId }

    val hasCamera: Boolean get() = cam.hasCamera || rigs.isNotEmpty()
    val hasSpaces: Boolean get() = rigs.isNotEmpty()

    /** The rig that places [c], or null when the project camera (or nothing) places it. */
    fun rigFor(c: TimelineClip): SpaceRig? = c.spaceId?.let { bySpaceId[it] }

    /** The rig of the space layer [spaceClipId] itself (for drawing the space). */
    fun spaceRig(spaceClipId: String): SpaceRig? = bySpaceId[spaceClipId]

    fun affects(c: TimelineClip): Boolean = rigFor(c) != null || cam.affects(c)
    fun activeFor(c: TimelineClip, t: Long): Boolean = rigFor(c)?.activeFor(c, t) ?: cam.activeFor(c, t)
    fun stackKey(c: TimelineClip, t: Long): Float? = rigFor(c)?.stackKey(c, t) ?: cam.stackKey(c, t)

    fun seeMedia(
        c: TimelineClip, t: Long, fp: FloatArray, z: Float, contentW: Int, contentH: Int, k: Float, canvasW: Int, canvasH: Int
    ): CameraRig.Seen? = rigFor(c)?.seeMedia(c, t, fp, z, contentW, contentH, k, canvasW, canvasH)
        ?: cam.seeMedia(c, t, fp, z, contentW, contentH, k, canvasW, canvasH)

    fun seeOverlay(
        c: TimelineClip, t: Long, boxW: Float, boxH: Float, k: Float, canvasW: Int, canvasH: Int
    ): CameraRig.Seen? = rigFor(c)?.seeOverlay(c, t, boxW, boxH, k, canvasW, canvasH)
        ?: cam.seeOverlay(c, t, boxW, boxH, k, canvasW, canvasH)
}
