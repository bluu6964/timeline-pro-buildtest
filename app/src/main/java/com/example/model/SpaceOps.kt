package com.example.model

/**
 * Build 262 — the 3D Space's membership law (Sarowar's feature; see
 * [LayerType.SPACE3D]). Pure functions over the lanes, mirroring [GroupOps]:
 *
 *  - A layer is INSIDE a space when its [TimelineClip.spaceId] names a SPACE3D
 *    layer in the same project. Membership is by that field alone — unlike a
 *    Group, a space child carries NO parent link to the space (ParentRig must
 *    never compose it; the space's own camera places it, see SpaceRig).
 *  - [scopeTracks] is the engines' scoping: the top level (and every group
 *    level) has the space children REMOVED (they are drawn inside their
 *    space's own pass), and a space's level holds exactly its children. The
 *    timeline UI keeps using [GroupOps.scopeTracks] — on screen the children
 *    stay on their own lanes like group children do.
 *  - A clip carries at most one container: spaceId and groupId are mutually
 *    exclusive (the editor enforces it; [GroupOps.canGroup] refuses a space
 *    child, [canBeChild] refuses a grouped clip).
 */
object SpaceOps {

    /** Ids of every 3D Space layer in [tracks]. */
    fun spaceIdsOf(tracks: List<TimelineTrack>): Set<String> =
        tracks.flatMapTo(HashSet()) { lane -> lane.clips.filter { it.type == LayerType.SPACE3D }.map { it.id } }

    /** Every 3D Space layer in [tracks], in lane order. */
    fun spacesOf(tracks: List<TimelineTrack>): List<TimelineClip> =
        tracks.flatMap { lane -> lane.clips.filter { it.type == LayerType.SPACE3D } }

    /** The space layer [id], or null. */
    fun spaceOf(tracks: List<TimelineTrack>, id: String): TimelineClip? =
        tracks.firstNotNullOfOrNull { lane -> lane.clips.firstOrNull { it.id == id && it.type == LayerType.SPACE3D } }

    /** The layers inside the space [spaceId], in lane order. */
    fun childrenOf(tracks: List<TimelineTrack>, spaceId: String): List<TimelineClip> =
        tracks.flatMap { lane -> lane.clips.filter { it.spaceId == spaceId } }

    /** The space [c] is drawn inside: its spaceId when that space exists in [tracks], else null. */
    fun scopeOf(c: TimelineClip, spaceIds: Set<String>): String? =
        c.spaceId?.takeIf { it in spaceIds }

    /**
     * Which layers a VISUAL layer type may live inside a space: every layer
     * with a picture (Sarowar's settled scope — text, shape, video, image /
     * photo, sticker, overlay). Audio has no visual and stays out; Null /
     * Camera / Chain / Group / Controller / Space are containers or helpers,
     * never children.
     */
    fun canBeChild(c: TimelineClip): Boolean = when (c.type) {
        LayerType.VIDEO, LayerType.SHAPE, LayerType.OVERLAY, LayerType.STICKER, LayerType.TEXT -> true
        else -> false
    }

    /**
     * The extrusion depth (stored units) that applies to [child] inside
     * [space]: the child's own override when set (>= 0), else the space's
     * default. Only TEXT / SHAPE children extrude; everything else gets 0.
     */
    fun extrudeFor(space: TimelineClip, child: TimelineClip): Float {
        if (child.type != LayerType.TEXT && child.type != LayerType.SHAPE) return 0f
        val own = child.spaceExtrudeSelf
        return if (own >= 0f) own else space.spaceExtrude.coerceAtLeast(0f)
    }

    /** The flat-shaded wall colour of an extruded child: its face colour darkened by [WALL_SHADE]. */
    fun wallColorArgb(faceArgb: Long): Long {
        val a = (faceArgb shr 24) and 0xFF
        val r = ((faceArgb shr 16) and 0xFF) * WALL_SHADE
        val g = ((faceArgb shr 8) and 0xFF) * WALL_SHADE
        val b = (faceArgb and 0xFF) * WALL_SHADE
        return (a shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /** The face colour an extruded child's walls shade from: text → its ink; shape → its fill (gradient → first colour; image / video fill → mid grey, v1). */
    fun faceColorOf(c: TimelineClip): Long = when {
        c.type == LayerType.TEXT -> c.textColor
        c.fillMode == FILL_GRADIENT -> c.gradColor1
        c.fillMode == FILL_SOLID -> c.textColor
        else -> 0xFF909090L
    }

    /** Fixed flat-shade factor of the extrusion side walls (v1: no lights, one honest shade). */
    const val WALL_SHADE = 0.55f

    /** Fixed darkening laid over an extruded child's back face (v1 flat shading). */
    const val BACK_SHADE_ALPHA = 0.45f

    /** The grid floor's cell count per side (20×20 cells, as briefed). */
    const val GRID_CELLS = 20

    /** The grid floor's height in the scene: this fraction of the frame height below the centre. */
    const val GROUND_Y_FRAC = 0.30f

    /**
     * The lanes of one render level, space-aware (the engines' scoping):
     *  - [scopeId] naming a SPACE → exactly that space's children.
     *  - otherwise → [GroupOps.scopeTracks] for that level with every space
     *    child removed (a space child is drawn only inside its space's pass).
     * Lanes left empty are dropped; lane order and clip order are preserved.
     */
    fun scopeTracks(tracks: List<TimelineTrack>, scopeId: String?): List<TimelineTrack> {
        val spaceIds = spaceIdsOf(tracks)
        if (scopeId != null && scopeId in spaceIds) {
            return tracks.mapNotNull { lane ->
                val kept = lane.clips.filter { it.spaceId == scopeId }
                if (kept.isEmpty()) null else lane.copy(clips = kept)
            }
        }
        val base = GroupOps.scopeTracks(tracks, scopeId)
        if (spaceIds.isEmpty()) return base
        return base.mapNotNull { lane ->
            val kept = lane.clips.filter { scopeOf(it, spaceIds) == null }
            if (kept.isEmpty()) null else lane.copy(clips = kept)
        }
    }
}
