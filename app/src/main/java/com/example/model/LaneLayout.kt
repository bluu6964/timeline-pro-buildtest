package com.example.model

import com.example.engine.ClipGraphNative
import com.example.engine.ModelLogicNative
import com.example.engine.ProjectDocNative
import java.util.UUID

/**
 * Timeline lane rules — Alight Motion style (pure functions, no Android dependencies).
 *
 * - The timeline is a list of lanes. `tracks[0]` is the TOP lane (drawn above the others),
 *   the last item is the BOTTOM lane.
 * - **One layer per line**: each lane holds at most one clip. Layers never sit side by side.
 * - A new layer always gets its own new lane (created on top).
 * - Lanes only exist while they hold a clip.
 * - Vertical reorder changes stacking order (top = drawn on top in the canvas / export).
 */
object LaneLayout {

    fun newLane(clips: List<TimelineClip> = emptyList()): TimelineTrack = TimelineTrack(
        id = "lane_${UUID.randomUUID()}",
        name = "Layer",
        clips = clips
    )

    /**
     * Puts [clip] in its own exclusive lane on top (Alight Motion: 1 layer = 1 line).
     */
    fun place(tracks: List<TimelineTrack>, clip: TimelineClip): List<TimelineTrack> {
        return listOf(newLane(listOf(clip))) + tracks
    }

    /**
     * Puts [clip] in its own new lane DIRECTLY BELOW the lane holding
     * [anchorClipId] (one index later — tracks[0] is the top lane), keeping its
     * start and duration. The detached-audio law (build 203, [ExtractAudio]):
     * the extracted sound sits right under its picture. The clip itself is
     * placed as-is — a clip that belonged to a chain keeps no chain seat here;
     * extraction always lands on a standalone lane (when the anchor is a chain
     * member, that is the lane below the whole chain lane). An unknown anchor
     * falls back to [place] (a new lane on top).
     */
    fun placeBelow(tracks: List<TimelineTrack>, anchorClipId: String, clip: TimelineClip): List<TimelineTrack> {
        val li = tracks.indexOfFirst { lane -> lane.clips.any { it.id == anchorClipId } }
        if (li < 0) return place(tracks, clip)
        val out = tracks.toMutableList()
        out.add(li + 1, newLane(listOf(clip)))
        return out
    }

    /**
     * Puts every clip of [clips] in its own lane on top, in ONE step: the first clip ends up on the
     * top lane, the last one right above the old lanes. (Calling [place] in a loop copies the whole
     * lane list once per clip: 200 captions = 20,000 copies.)
     */
    fun placeAll(tracks: List<TimelineTrack>, clips: List<TimelineClip>): List<TimelineTrack> {
        if (clips.isEmpty()) return tracks
        val out = ArrayList<TimelineTrack>(clips.size + tracks.size)
        for (c in clips) out += newLane(listOf(c))
        out.addAll(tracks)
        return out
    }

    /**
     * Ensures every lane has at most one clip. Extra clips each get their own lane on top.
     * Empty lanes are removed.
     * Exception: a lane with a Chain layer keeps the chain and every layer whose chainId is the chain's id (they sit side
     * by side in that one lane); a layer with a stale chainId becomes a normal layer.
     */
    /**
     * True when [tracks] already satisfies the lane invariant [normalize] restores (build 199,
     * audit #2 item O3): no empty lanes, clips in start order, a plain lane holding exactly one
     * unchained clip, a chain lane holding exactly the chain clip followed by its members in
     * start order. An O(n) read-only check — when it passes, [normalize] returns its input
     * unchanged instead of rebuilding (and re-placing overflow through) the whole list, which
     * is what split/delete/duplicate paid 2–3× per action before build 199.
     */
    fun isNormalized(tracks: List<TimelineTrack>): Boolean {
        // The check is native since migration #3 (model_logic): per clip only
        // startMs / type / chainId cross, chainId resolved to the flat index
        // of the same-lane clip it names. The native side mirrors the
        // conditions below one for one: no empty lanes, clips in start order,
        // a plain lane holding exactly one unchained clip, a chain lane
        // holding exactly the chain followed by its members in start order.
        var total = 0
        for (lane in tracks) total += lane.clips.size
        val laneOffsets = IntArray(tracks.size + 1)
        val startMs = LongArray(total)
        val isChain = BooleanArray(total)
        val chainIdNull = BooleanArray(total)
        val chainIdTarget = IntArray(total) { -1 }
        var k = 0
        for ((li, lane) in tracks.withIndex()) {
            laneOffsets[li] = k
            val clips = lane.clips
            for ((j, c) in clips.withIndex()) {
                startMs[k] = c.startMs
                isChain[k] = c.type == LayerType.CHAIN
                chainIdNull[k] = c.chainId == null
                if (c.chainId != null) {
                    for ((m, o) in clips.withIndex()) {
                        if (o.id == c.chainId) { chainIdTarget[k] = k - j + m; break }
                    }
                }
                k++
            }
        }
        laneOffsets[tracks.size] = k
        return ModelLogicNative.nativeIsNormalized(laneOffsets, startMs, isChain, chainIdNull, chainIdTarget)
    }

    fun normalize(tracks: List<TimelineTrack>): List<TimelineTrack> {
        // Fast path (build 199, O3): already-normal input is returned as the SAME list, so the
        // repeated normalize calls inside one action (split/delete/duplicate pre- and
        // post-normalized) collapse to the first one's work. [isNormalized] mirrors the body
        // below condition for condition; the harness pins both the identity and that every
        // normalize output satisfies isNormalized.
        if (isNormalized(tracks)) return tracks
        // The plan (per-lane kept clips in start order with the chain first,
        // the overflow in processing order, the stale-chainId clears) is
        // native since migration stage 2 (clip_graph); the lane assembly
        // below is the original's own copy() / place() ops.
        val pack = ClipGraphNative.packGraph(tracks)
        val plan = ClipGraphNative.normalizePlan(pack)
        fun cleared(i: Int): TimelineClip {
            val c = pack.clips[i]
            return if (plan.clearChain[i]) c.copy(chainId = null) else c
        }
        val singles = mutableListOf<TimelineTrack>()
        for ((li, lane) in tracks.withIndex()) {
            val kept = plan.keptPerLane[li]
            if (kept.isEmpty()) continue
            singles += lane.copy(clips = kept.map { cleared(it) })
        }
        var result: List<TimelineTrack> = singles
        for (oi in plan.overflow) {
            result = place(result, cleared(oi))
        }
        return result
    }

    /** The layers inside the Chain [chainId] (not the chain itself). */
    fun childrenOf(tracks: List<TimelineTrack>, chainId: String): List<TimelineClip> =
        tracks.flatMap { it.clips }.filter { it.chainId == chainId }

    /**
     * Puts [clip] into the lane of the Chain [chainId], side by side with the layers already in it. The clip starts where it
     * wants to ([clip].startMs); when a layer is already there it goes right BEHIND it (after its end), still in this lane.
     * The chain then grows to cover its layers ([fitChains]). Without that chain: a normal new lane on top.
     */
    fun placeInChain(tracks: List<TimelineTrack>, chainId: String, clip: TimelineClip): List<TimelineTrack> {
        // The lane search is native since migration stage 2 (clip_graph);
        // the start solver stays with ProjectDocNative, as before.
        val pack = ClipGraphNative.packGraph(tracks)
        val li = ClipGraphNative.placeInChainLane(pack, pack.keyOf(chainId))
        if (li < 0) return place(tracks, clip.copy(chainId = null))
        val lane = tracks[li]
        val siblings = lane.clips.filter { it.chainId == chainId }
        val start = if (siblings.isEmpty()) {
            clip.startMs.coerceAtLeast(0L)
        } else {
            val starts = LongArray(siblings.size) { siblings[it].startMs }
            val ends = LongArray(siblings.size) { siblings[it].endMs }
            ProjectDocNative.nativeSolveChainStart(starts, ends, clip.startMs, clip.durationMs)
        }
        val placed = clip.copy(startMs = start, chainId = chainId)
        val out = tracks.toMutableList()
        out[li] = lane.copy(clips = lane.clips + placed)
        return fitChains(out)
    }

    /** Every Chain layer covers exactly its layers (first start .. last end). A chain without layers keeps its own length. */
    fun fitChains(tracks: List<TimelineTrack>): List<TimelineTrack> {
        // The per-chain window decisions are native since migration
        // stage 2 (clip_graph); an unchanged project returns its input.
        val pack = ClipGraphNative.packGraph(tracks)
        val (changed, entries) = ClipGraphNative.fitChains(pack)
        if (!changed) return tracks
        val byClip = entries.associateBy { it.clip }
        var idx = 0
        return tracks.map { lane ->
            lane.copy(clips = lane.clips.map { c ->
                val e = byClip[idx++]
                if (e != null) c.copy(startMs = e.startMs, durationMs = e.durationMs) else c
            })
        }
    }

    /** Moves the lane at [fromIndex] to [toIndex] (stacking order). Indices are clamped. */
    fun reorder(tracks: List<TimelineTrack>, fromIndex: Int, toIndex: Int): List<TimelineTrack> {
        // The clamp / identity decision is native since migration stage 2.
        val plan = ClipGraphNative.nativeReorder(tracks.size, fromIndex, toIndex)
        if (plan[0] != 0) return tracks
        val list = tracks.toMutableList()
        val item = list.removeAt(plan[1])
        list.add(plan[2], item)
        return list
    }

    /**
     * Moves [clipId] earlier/later on the timeline by [deltaMs].
     * Start is clamped to ≥ 0. Does not change duration.
     */
    fun shiftClipTime(
        tracks: List<TimelineTrack>,
        clipId: String,
        deltaMs: Long
    ): List<TimelineTrack> {
        return tracks.map { lane ->
            lane.copy(
                clips = lane.clips.map { c ->
                    if (c.id != clipId) c
                    else c.copy(startMs = (c.startMs + deltaMs).coerceAtLeast(0L))
                }
            )
        }
    }

    /** Sets absolute start time of [clipId] (ms ≥ 0). */
    fun setClipStart(
        tracks: List<TimelineTrack>,
        clipId: String,
        startMs: Long
    ): List<TimelineTrack> {
        return tracks.map { lane ->
            lane.copy(
                clips = lane.clips.map { c ->
                    if (c.id != clipId) c
                    else c.copy(startMs = startMs.coerceAtLeast(0L))
                }
            )
        }
    }
}
