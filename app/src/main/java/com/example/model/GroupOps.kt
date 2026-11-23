package com.example.model

import com.example.engine.ClipGraphNative
import com.example.engine.ModelLogicNative
import java.util.UUID

/**
 * Real grouping (Alight Motion style), pure functions without Android dependencies.
 *
 * A Group is ONE layer to the outside (Alight Motion style): in the timeline it is a single bar, and everything a layer
 * can do (move / scale / turn, opacity, blend mode, effects, masks, keyframes) works on the whole group. The renderer
 * draws the layers inside it into their own picture first and then puts that picture on the canvas like one layer.
 * "Edit Group" goes inside: only the layers of that group are shown in the timeline and on the canvas.
 *
 * Every layer inside has [TimelineClip.groupId] = the group's id and a [TimelineClip.parent] link to it, so the group's
 * move / scale / turn reaches the layers (the maths is the one of the Null parent, see ParentRig). The group's opacity,
 * blend mode, effects and masks are NOT handed down: they act on the finished picture of the group.
 *
 * This is NOT the same as a Chain ([LayerType.CHAIN]): a Chain only puts layers side by side in one timeline lane.
 */
object GroupOps {

    /** Position / scale % / rotation of a group at the moment of linking (what a [ParentLink] saves). */
    data class Pose(val x: Float, val y: Float, val scalePercent: Float, val rotationDeg: Float) {
        companion object {
            /** A group that was never moved: the pose of a brand new group. */
            val REST = Pose(0f, 0f, 100f, 0f)
        }
    }

    /** Result of [group]: the new lanes and the id of the group that holds the layers now. */
    data class Grouped(val tracks: List<TimelineTrack>, val groupId: String)

    /** Audio has no picture, a Camera is a view and a Chain is a lane container: none of them can sit in a group.
     *  Build 262: a layer inside a 3D Space cannot also sit in a group (one container per layer). */
    fun canGroup(c: TimelineClip): Boolean =
        c.type != LayerType.AUDIO && c.type != LayerType.CAMERA && c.type != LayerType.CHAIN && c.spaceId == null


    /** Ids of every Group layer in [tracks]. */
    fun groupIdsOf(tracks: List<TimelineTrack>): Set<String> =
        tracks.flatMapTo(HashSet()) { lane -> lane.clips.filter { it.type == LayerType.GROUP }.map { it.id } }

    /**
     * The group [c] is drawn inside: its groupId, or null when it is not inside a group (also when its group is not in
     * the list, e.g. a full-screen editor that shows only some layers).
     */
    fun scopeOf(c: TimelineClip, groupIds: Set<String>): String? = c.groupId?.takeIf { it in groupIds }

    /**
     * The lanes of one level: only the layers that sit directly inside [scopeId] (null = the top level, layers that are
     * in no group; a Group layer counts as one layer of its own level). Lanes left empty are dropped, the order is kept.
     * This is what the timeline and the canvas show, and what the renderer draws as one picture (see NativePreviewEngine).
     */
    fun scopeTracks(tracks: List<TimelineTrack>, scopeId: String?): List<TimelineTrack> {
        val ids = groupIdsOf(tracks)
        val scope = scopeId?.takeIf { it in ids }
        if (ids.isEmpty() && scope == null) return tracks
        val out = ArrayList<TimelineTrack>(tracks.size)
        for (lane in tracks) {
            if (lane.clips.all { scopeOf(it, ids) == scope }) { out += lane; continue }
            val kept = lane.clips.filter { scopeOf(it, ids) == scope }
            if (kept.isNotEmpty()) out += lane.copy(clips = kept)
        }
        return out
    }

    /** [editingId] when it is a Group layer that exists, else null (the top level). */
    fun validScope(tracks: List<TimelineTrack>, editingId: String?): String? =
        editingId?.takeIf { it in groupIdsOf(tracks) }

    /** Index in [tracks] of the lane that holds [laneId] (-1 = none). Used to turn a position in a shown level into the real one. */
    fun realLaneIndex(tracks: List<TimelineTrack>, laneId: String): Int = tracks.indexOfFirst { it.id == laneId }

    fun linkTo(groupId: String, pose: Pose): ParentLink =
        ParentLink(groupId, pose.x, pose.y, pose.scalePercent, pose.rotationDeg)

    /** The layers directly inside the group [groupId] (not the layers of a group inside it). */
    fun childrenOf(tracks: List<TimelineTrack>, groupId: String): List<TimelineClip> =
        tracks.flatMap { it.clips }.filter { it.groupId == groupId }

    /** All layers inside the group, also the ones in groups inside it. */
    fun descendantsOf(tracks: List<TimelineTrack>, groupId: String): List<TimelineClip> {
        val all = tracks.flatMap { it.clips }
        if (all.isEmpty()) return emptyList()
        // The traversal is native since migration #3 (model_logic): clips
        // cross as identity keys (an id string's key is its identity class),
        // the native side runs the same level-by-level BFS with the same
        // 16-level guard, and the indices map back to the clips here.
        val keys = HashMap<String, Int>()
        fun keyOf(s: String): Int = keys.getOrPut(s) { keys.size }
        val clipKeys = IntArray(all.size) { keyOf(all[it].id) }
        val groupKeys = IntArray(all.size) { c -> all[c].groupId?.let { keyOf(it) } ?: -1 }
        val isGroup = BooleanArray(all.size) { all[it].type == LayerType.GROUP }
        val idx = ModelLogicNative.nativeDescendants(clipKeys, groupKeys, isGroup, keyOf(groupId))
        return idx.map { all[it] }
    }

    /**
     * Groups the checked layers.
     * - Normal case: ONE new Group layer (own lane, right above the topmost checked layer); the checked layers get the
     *   group as parent and keep their own lanes. A checked Group becomes a group inside the new one.
     * - If exactly one existing Group is among the checked layers (and other layers too), the other layers go INTO that
     *   group instead of making a new one (this is how a layer is added to a group later).
     * A layer that already had a Null parent loses it (the group is its parent now).
     * [poseOf] = the pose of an existing group now (null = unknown, the layer then follows from the rest pose).
     * Returns null when there is nothing to group.
     */
    fun group(tracks: List<TimelineTrack>, ids: Set<String>, poseOf: (String) -> Pose? = { null }): Grouped? {
        // The decision (which mode, who moves, the new group's window /
        // outer group / lane) is native since migration stage 2
        // (clip_graph): clips cross as identity keys + the fields the law
        // reads; the assembly below is the original's own copy() / lane ops.
        val pack = ClipGraphNative.packGraph(tracks)
        val selected = BooleanArray(pack.clips.size) { pack.clips[it].id in ids }
        val plan = ClipGraphNative.groupPlan(pack, selected)
        if (plan.mode == 0) return null
        if (plan.mode == 1) {
            val target = pack.clips[plan.targetClip]
            val link = linkTo(target.id, poseOf(target.id) ?: Pose.REST)
            val movers = plan.movers.toSet()
            var idx = 0
            val out = tracks.map { lane ->
                lane.copy(clips = lane.clips.map { c ->
                    if (idx++ in movers) c.copy(groupId = target.id, parent = link) else c
                })
            }
            return Grouped(fitGroups(out), target.id)
        }
        val groupId = UUID.randomUUID().toString()
        val outerId = if (plan.outerKey >= 0) pack.idForKey(plan.outerKey) else null
        val group = TimelineClip(
            id = groupId,
            name = "Group ${plan.groupCount + 1}",
            type = LayerType.GROUP,
            startMs = plan.startMs,
            durationMs = plan.durationMs,
            color = 0xFF5BB85D,
            groupId = outerId,
            parent = outerId?.let { linkTo(it, poseOf(it) ?: Pose.REST) }
        )
        val link = linkTo(groupId, Pose.REST)
        val sel = BooleanArray(pack.clips.size) { selected[it] && canGroup(pack.clips[it]) }
        var idx = 0
        val moved = tracks.map { lane ->
            lane.copy(clips = lane.clips.map { c ->
                if (sel[idx++]) c.copy(groupId = groupId, parent = link) else c
            })
        }
        val out = moved.toMutableList()
        out.add(plan.topLaneIndex.coerceAtLeast(0), LaneLayout.newLane(listOf(group)))
        return Grouped(fitGroups(out), groupId)
    }

    /**
     * Takes the layers out of the group [groupId] and removes the group layer.
     * [bake] gets a layer of the group and returns it with the pose it shows now (so it does not jump); the default
     * keeps its own pose. Layers of a group that sits inside another group stay in that outer group
     * (linked to it with [poseOf] = the outer group's pose now).
     */
    fun ungroup(
        tracks: List<TimelineTrack>,
        groupId: String,
        bake: (TimelineClip) -> TimelineClip = { it },
        poseOf: (String) -> Pose? = { null }
    ): List<TimelineTrack> {
        val group = tracks.flatMap { it.clips }.firstOrNull { it.id == groupId && it.type == LayerType.GROUP } ?: return tracks
        // The drop / relink decision per clip is native since migration
        // stage 2 (clip_graph); bake + the link stay the caller's, as before.
        val pack = ClipGraphNative.packGraph(tracks)
        val codes = ClipGraphNative.ungroupPlan(pack, pack.keyOf(groupId))
        val outer = group.groupId
        val outerLink = outer?.let { linkTo(it, poseOf(it) ?: Pose.REST) }
        var idx = 0
        val out = tracks.map { lane ->
            lane.copy(clips = lane.clips.mapNotNull { c ->
                when (codes[idx++]) {
                    1 -> null
                    2 -> bake(c).copy(groupId = outer, parent = outerLink)
                    else -> c
                }
            })
        }
        return LaneLayout.normalize(out.filter { it.clips.isNotEmpty() })
    }

    /**
     * A copy of the group with copies of everything inside it (also groups inside it), in new lanes on top.
     * The copies are linked to the copied group. Returns the new lanes and the id of the new group.
     */
    fun duplicate(tracks: List<TimelineTrack>, groupId: String): Grouped? {
        val group = tracks.flatMap { it.clips }.firstOrNull { it.id == groupId && it.type == LayerType.GROUP } ?: return null
        val inside = descendantsOf(tracks, groupId).map { it.id }.toSet()
        val originals = tracks.flatMap { it.clips }.filter { it.id == groupId || it.id in inside }  // top lane first
        // The link remapping (which of the copies' group / parent /
        // controller links point at minted ids, which keep their target)
        // is native since migration stage 2 (clip_graph); the ids are
        // minted here, in originals order, exactly as the original did.
        val pack = ClipGraphNative.packGraph(tracks)
        val originalOrder = IntArray(originals.size) { pack.indexById.getValue(originals[it].id) }
        val plan = ClipGraphNative.duplicatePlan(pack, originalOrder, pack.keyOf(groupId))
        val mints = Array(originals.size) { UUID.randomUUID().toString() }
        fun idForExtended(key: Int): String? = when {
            key < 0 -> null
            key >= pack.keyCount -> mints[key - pack.keyCount]
            else -> pack.idForKey(key)
        }
        val copies = originals.mapIndexed { j, c ->
            val e = plan[j]
            c.copy(
                id = mints[j],
                name = if (e.nameSuffix) "${c.name} (Copy)" else c.name,
                groupId = idForExtended(e.groupKeyOut),
                parent = c.parent?.let { p ->
                    val nk = idForExtended(e.parentKeyOut)
                    if (nk != null) p.copy(id = nk) else p
                },
                // A Controller connection inside the group is remapped like the parent link:
                // the copy's layers connect to the copy's Controller, never the original's.
                controllerId = c.controllerId?.let { idForExtended(e.controllerKeyOut) ?: it },
                chainId = null
            )
        }
        val newLanes = copies.map { LaneLayout.newLane(listOf(it)) }
        return Grouped(newLanes + tracks, mints[originals.indexOfFirst { it === group }])
    }

    /**
     * Splits the group [groupId] at [playheadMs] into two groups: the first keeps the group's id and everything before the
     * playhead, the second (new id, copy of the group's own move / scale / turn, shifted in time) gets everything after it.
     * A layer inside that spans the playhead is cut in two like a normal Split; a layer that lies wholly after the playhead
     * just moves to the second group. The new layers sit in new lanes right above the lane they came from.
     * Returns null when it cannot be done: not a group, the playhead is within 500 ms of an edge, the group is empty, or it
     * holds another group (split that one first).
     */
    fun splitGroup(tracks: List<TimelineTrack>, groupId: String, playheadMs: Long): Grouped? {
        val all = tracks.flatMap { it.clips }
        val group = all.firstOrNull { it.id == groupId && it.type == LayerType.GROUP } ?: return null
        // Validity + the per-kid classification (stays / moves / splits,
        // with the split point and the relinked parent for everything that
        // lands in the second group) are native since migration stage 2
        // (clip_graph). The halves are built here with the original's own
        // ops — the second half's trim delta still comes from
        // sourceOffsetMs (stage 1), which integrates SPEED keyframes.
        val pack = ClipGraphNative.packGraph(tracks)
        val plan = ClipGraphNative.splitGroupPlan(pack, pack.keyOf(groupId), playheadMs)
        if (!plan.ok) return null

        val d1 = playheadMs - group.startMs
        val newGroupId = UUID.randomUUID().toString()
        val g1 = group.copy(durationMs = plan.groupFirstDurMs, fadeOutMs = 0L).withoutClipAnim(AnimSlot.OUT)
        val g2 = group.copy(
            id = newGroupId,
            name = "${group.name} (Split)",
            startMs = playheadMs,
            durationMs = group.endMs - playheadMs,
            fadeInMs = 0L
        ).withShiftedKeyframes(-d1).withoutClipAnim(AnimSlot.IN)

        val splitKids = plan.kids.filter { it.cls == 2 }
        val halfIds = HashMap<Int, String>()   // split kid's clip index -> its second half's id
        for (kid in splitKids) halfIds[kid.clip] = UUID.randomUUID().toString()
        fun idForExtended(key: Int): String? = when {
            key < 0 -> null
            key == pack.keyCount -> newGroupId
            key > pack.keyCount -> halfIds[splitKids[key - pack.keyCount - 1].clip]
            else -> pack.idForKey(key)
        }
        val kidByClip = plan.kids.associateBy { it.clip }

        /** A layer that now belongs to the second group: follows it, and follows the second half of a cut parent. */
        fun relink(c: TimelineClip, parentOut: Int): TimelineClip {
            val p = c.parent
            val np = when {
                p == null -> null
                else -> idForExtended(parentOut)?.let { p.copy(id = it) } ?: p
            }
            return c.copy(groupId = newGroupId, parent = np)
        }

        val out = ArrayList<TimelineTrack>(tracks.size + splitKids.size + 1)
        var idx = 0
        for (lane in tracks) {
            val above = ArrayList<TimelineClip>()
            var holdsGroup = false
            val clips = lane.clips.map { c ->
                val kid = kidByClip[idx++]
                when {
                    c.id == groupId -> { holdsGroup = true; g1 }
                    kid != null && kid.cls == 2 -> {
                        val dd1 = kid.d1
                        val second = c.copy(
                            id = halfIds.getValue(kid.clip),
                            name = "${c.name} (Split)",
                            startMs = playheadMs,
                            durationMs = c.endMs - playheadMs,
                            trimStartMs = c.trimStartMs + c.sourceOffsetMs(dd1),
                            fadeInMs = 0L,
                            chainId = null
                        ).withShiftedKeyframes(-dd1).withoutClipAnim(AnimSlot.IN)
                        above += relink(second, kid.parentOut)
                        c.copy(durationMs = dd1, fadeOutMs = 0L).withoutClipAnim(AnimSlot.OUT)
                    }
                    kid != null && kid.cls == 1 -> relink(c, kid.parentOut)
                    else -> c
                }
            }
            if (holdsGroup) out += LaneLayout.newLane(listOf(g2))
            for (a in above) out += LaneLayout.newLane(listOf(a))
            out += lane.copy(clips = clips)
        }
        return Grouped(LaneLayout.normalize(out), newGroupId)
    }

    /**
     * Every Group layer covers exactly the layers inside it (first start .. last end), also when they move. The group's
     * own keyframes stay where they were in project time. A layer whose group is gone becomes a normal layer again.
     * A group without layers keeps its own length, and so does a group whose edge the person trimmed
     * ([TimelineClip.groupFreeLength]): the layers inside are then cut at its ends.
     */
    fun fitGroups(tracks: List<TimelineTrack>): List<TimelineTrack> {
        // Each pass's decisions (stale-link clears + group refits, all read
        // from the pass's input snapshot) are native since migration
        // stage 2 (clip_graph); the pass loop and the copy()/keyframe-shift
        // application stay here, as in the original.
        var cur = tracks
        var pass = 0
        while (pass++ < 4) {
            val pack = ClipGraphNative.packGraph(cur)
            val plan = ClipGraphNative.fitGroupsPass(pack)
            if (!plan.changed) return cur
            val refitByClip = plan.refits.associateBy { it.clip }
            var idx = 0
            cur = cur.map { lane ->
                lane.copy(clips = lane.clips.map { c ->
                    val i = idx++
                    var r = c
                    // Stale link: the group of this layer does not exist (any more).
                    if (plan.clearGroup[i]) {
                        r = r.copy(groupId = null, parent = if (plan.clearParent[i]) null else r.parent)
                    }
                    // A group the person trimmed keeps its own length: the layers inside are cut at its ends.
                    val refit = refitByClip[i]
                    if (refit != null) {
                        r = r.copy(startMs = refit.startMs, durationMs = refit.durationMs)
                            .withTrimmedStartKeyframes(refit.trimDelta)
                            .withEndMoved(refit.endDelta)
                    }
                    r
                })
            }
        }
        return cur
    }
}
