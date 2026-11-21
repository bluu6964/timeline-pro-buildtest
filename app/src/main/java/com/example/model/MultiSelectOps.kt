package com.example.model

import com.example.engine.ClipGraphNative
import com.example.engine.ModelLogicNative
import java.util.UUID

/** Which option group the multi-select bar shows (it depends on where the playhead is). */
enum class MultiBarMode {
    /** Playhead is inside at least one selected layer: delete-left / split / delete-right. */
    SPLIT,
    /** Playhead is before the layers: extend start to playhead / move start to playhead. */
    START,
    /** Playhead is after all layers: extend end to playhead / move end to playhead. */
    END
}

/** Every button of the multi-select bar. */
enum class MultiAction {
    DELETE_LEFT,
    SPLIT,
    DELETE_RIGHT,
    EXTEND_START,
    MOVE_START,
    EXTEND_END,
    MOVE_END,
    ALIGN_START,
    SEQUENCE,
    ALIGN_END,
    /** Puts the checked layers into one new Chain lane (side by side). */
    CHAIN,
    /** Real group: the checked layers follow one new Group layer (move / scale / turn / opacity together). */
    GROUP
}

/**
 * Multi-select operations (pure functions, no Android dependencies).
 * Each function returns the new lane list, or null when nothing changed
 * (so the ViewModel does not push an empty undo step).
 */
object MultiSelectOps {

    private const val MIN_LEN = 100L

    /**
     * Chain: the checked layers (not chains) move into ONE new lane with a Chain layer, at the place of the topmost checked
     * lane. They sit side by side in the order of their start; a layer that overlaps an earlier one in time goes right
     * behind it (the rule of a Chain lane, see [LaneLayout.placeInChain]).
     */
    private fun chainSelected(tracks: List<TimelineTrack>, ids: Set<String>): List<TimelineTrack>? {
        // The selection order, the insertion position and the chain window
        // are native since migration stage 2 (clip_graph); the lane
        // assembly stays the original's own ops.
        val pack = ClipGraphNative.packGraph(tracks)
        val selected = BooleanArray(pack.clips.size) { pack.clips[it].id in ids }
        val plan = ClipGraphNative.chainSelectedPlan(pack, selected)
        if (plan.selOrder.isEmpty()) return null
        val sel = plan.selOrder.map { pack.clips[it] }
        val selIds = sel.map { it.id }.toSet()
        val rest = tracks.map { lane -> lane.copy(clips = lane.clips.filter { it.id !in selIds }) }.filter { it.clips.isNotEmpty() }
        val chain = TimelineClip(
            id = UUID.randomUUID().toString(),
            name = "Chain",
            type = LayerType.CHAIN,
            startMs = plan.chainStart,
            durationMs = plan.chainDur,
            color = 0xFF2FB5A8
        )
        val withLane = rest.toMutableList().also { it.add(plan.insertAt.coerceIn(0, it.size), LaneLayout.newLane(listOf(chain))) }
        var out: List<TimelineTrack> = withLane
        for (c in sel) out = LaneLayout.placeInChain(out, chain.id, c.copy(chainId = null))
        return out
    }

    fun barMode(selected: List<TimelineClip>, playheadMs: Long): MultiBarMode {
        // The mode law is native since migration #3 (model_logic): 0 SPLIT, 1 START, 2 END.
        val starts = LongArray(selected.size) { selected[it].startMs }
        val ends = LongArray(selected.size) { selected[it].endMs }
        return when (ModelLogicNative.nativeBarMode(starts, ends, playheadMs)) {
            1 -> MultiBarMode.START
            2 -> MultiBarMode.END
            else -> MultiBarMode.SPLIT
        }
    }

    fun apply(
        tracks: List<TimelineTrack>,
        ids: Set<String>,
        action: MultiAction,
        playheadMs: Long
    ): List<TimelineTrack>? {
        val selected = tracks.flatMap { it.clips }.filter { it.id in ids }
        if (selected.isEmpty()) return null
        val p = playheadMs

        return when (action) {
            MultiAction.SPLIT -> split(tracks, ids, p)

            MultiAction.CHAIN -> chainSelected(tracks, ids)

            MultiAction.GROUP -> GroupOps.group(tracks, ids)?.tracks

            // Every other action's per-clip transform (guards, source
            // caps, speed floors, keyframe shift ops, the SEQUENCE cursor
            // and the ALIGN targets) is native since migration stage 2
            // (clip_graph); the edits are applied here with the original's
            // own copy() ops and its changed-by-equality rule.
            else -> applyNative(tracks, ids, action, p)
        }
    }

    private fun applyNative(
        tracks: List<TimelineTrack>,
        ids: Set<String>,
        action: MultiAction,
        p: Long
    ): List<TimelineTrack>? {
        val code = when (action) {
            MultiAction.DELETE_LEFT -> ClipGraphNative.MultiCode.DELETE_LEFT
            MultiAction.DELETE_RIGHT -> ClipGraphNative.MultiCode.DELETE_RIGHT
            MultiAction.EXTEND_START -> ClipGraphNative.MultiCode.EXTEND_START
            MultiAction.MOVE_START -> ClipGraphNative.MultiCode.MOVE_START
            MultiAction.EXTEND_END -> ClipGraphNative.MultiCode.EXTEND_END
            MultiAction.MOVE_END -> ClipGraphNative.MultiCode.MOVE_END
            MultiAction.ALIGN_START -> ClipGraphNative.MultiCode.ALIGN_START
            MultiAction.ALIGN_END -> ClipGraphNative.MultiCode.ALIGN_END
            MultiAction.SEQUENCE -> ClipGraphNative.MultiCode.SEQUENCE
            else -> return null
        }
        val pack = ClipGraphNative.packGraph(tracks)
        val selected = BooleanArray(pack.clips.size) { pack.clips[it].id in ids }
        val editByClip = ClipGraphNative.multiApplyPlan(pack, selected, code, p).associateBy { it.clip }
        var changed = false
        var idx = 0
        val out = tracks.map { lane ->
            lane.copy(clips = lane.clips.map { c ->
                val e = editByClip[idx++]
                if (e == null || e.code == 0) {
                    c
                } else {
                    val n = if (e.code == 3) {
                        c.copy(startMs = e.newStart)
                    } else {
                        var nc = c.copy(
                            startMs = e.newStart,
                            durationMs = e.newDur,
                            trimStartMs = e.newTrim,
                            fadeInMs = if (e.fadeMode == 1) 0L else c.fadeInMs,
                            fadeOutMs = if (e.fadeMode == 2) 0L else c.fadeOutMs
                        )
                        if (e.kfOp == 1) nc = nc.withTrimmedStartKeyframes(e.kfDelta)
                        else if (e.kfOp == 2) nc = nc.withEndMoved(e.kfDelta)
                        nc
                    }
                    if (n != c) changed = true
                    n
                }
            })
        }
        return if (changed) out else null
    }

    /** Splits every selected layer that the playhead is inside. The right halves get their own new lanes. */
    private fun split(tracks: List<TimelineTrack>, ids: Set<String>, p: Long): List<TimelineTrack>? {
        // Which layers split (and each second half's trim) is native since
        // migration stage 2 (clip_graph); the halves are built and placed
        // with the original's own ops.
        val pack = ClipGraphNative.packGraph(tracks)
        val selected = BooleanArray(pack.clips.size) { pack.clips[it].id in ids }
        val editByClip = ClipGraphNative.multiApplyPlan(
            pack, selected, ClipGraphNative.MultiCode.SPLIT, p
        ).associateBy { it.clip }
        val rightHalves = mutableListOf<TimelineClip>()
        var idx = 0
        val mapped = tracks.map { lane ->
            lane.copy(clips = lane.clips.map { c ->
                val e = editByClip[idx++]
                if (e != null && e.code == 2) {
                    val d1 = e.newDur
                    rightHalves += c.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${c.name} (Split)",
                        startMs = p,
                        durationMs = c.endMs - p,
                        trimStartMs = e.half2Trim,
                        fadeInMs = 0L
                    ).withShiftedKeyframes(-d1).withoutClipAnim(AnimSlot.IN)
                    c.copy(durationMs = d1, fadeOutMs = 0L).withoutClipAnim(AnimSlot.OUT)
                } else c
            })
        }
        if (rightHalves.isEmpty()) return null
        var result = LaneLayout.normalize(mapped)
        for (part in rightHalves) result = LaneLayout.place(result, part)
        return result
    }

    /** The outcome of [deleteChecked]: the new lanes plus the counts the caller reports. */
    data class DeleteCheckedResult(
        val tracks: List<TimelineTrack>,
        /** Layers actually removed (checked layers + their cascaded chain members / group descendants). */
        val deletedCount: Int,
        /** Layers inside the deletion set that were LOCKED and therefore spared (build 199, A1/A11). */
        val skippedLockedCount: Int
    )

    /**
     * Delete every checked layer at once (build 199, A11 — Sarowar's add-on: "multiple layer
     * একসাথে select করে delete"). The deletion set follows the single-layer delete's cascades
     * exactly: a checked Chain takes its chain members with it, a checked Group takes its
     * descendants; links pointing at a deleted layer (parent / controller / duck triggers)
     * are left dangling, which every consumer already treats as "no link" — the same orphan
     * convention the single delete relies on. LOCKED layers are never deleted: a locked layer
     * inside the set (checked itself, or reached through a cascade) is spared and counted in
     * [DeleteCheckedResult.skippedLockedCount] so the caller can say so. Returns null when no
     * checked layer exists; a result with deletedCount 0 means everything checked was locked
     * (the caller reports the skips and pushes no undo step).
     */
    fun deleteChecked(tracks: List<TimelineTrack>, ids: Set<String>): DeleteCheckedResult? {
        if (ids.isEmpty()) return null
        val all = tracks.flatMap { it.clips }
        val checked = all.filter { it.id in ids }
        if (checked.isEmpty()) return null
        // The deletion SET (checked + chain cascade + group descendants, minus
        // the locked spared inside it) is native since migration #3
        // (model_logic): clips cross as identity keys, never as objects. The
        // lane filtering + normalize below stay Kotlin, as before.
        val keys = HashMap<String, Int>()
        fun keyOf(s: String): Int = keys.getOrPut(s) { keys.size }
        val clipKeys = IntArray(all.size) { keyOf(all[it].id) }
        val chainKeys = IntArray(all.size) { c -> all[c].chainId?.let { keyOf(it) } ?: -1 }
        val groupKeys = IntArray(all.size) { c -> all[c].groupId?.let { keyOf(it) } ?: -1 }
        val typeCodes = IntArray(all.size) {
            when (all[it].type) {
                LayerType.CHAIN -> 1
                LayerType.GROUP -> 2
                else -> 0
            }
        }
        val locked = BooleanArray(all.size) { all[it].isLocked }
        val checkedArr = BooleanArray(all.size) { all[it].id in ids }
        val sparedOut = IntArray(1)
        val goneIdx = ModelLogicNative.nativeDeleteSet(
            clipKeys, chainKeys, groupKeys, typeCodes, locked, checkedArr, sparedOut
        )
        val sparedCount = sparedOut[0]
        if (goneIdx.isEmpty()) return DeleteCheckedResult(tracks, 0, sparedCount)
        val gone = goneIdx.mapTo(HashSet()) { all[it].id }
        val out = LaneLayout.normalize(
            tracks.map { lane -> lane.copy(clips = lane.clips.filter { it.id !in gone }) }
        )
        return DeleteCheckedResult(out, goneIdx.size, sparedCount)
    }
}
