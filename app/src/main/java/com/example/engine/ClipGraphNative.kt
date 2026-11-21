package com.example.engine

import com.example.model.AnimProp
import com.example.model.TimelineClip
import com.example.model.TimelineTrack

/**
 * JNI bridge for the C++ clip-GRAPH algebra (cpp/engine/clip_graph.{h,cpp},
 * migration stage 2, build 245): GroupOps' group / ungroup / duplicate /
 * splitGroup / fitGroups decisions, LaneLayout's normalize / fitChains /
 * reorder / placeInChain lane search, MultiSelectOps.apply's per-action
 * transforms, ControllerFanOut's fan-out plan, SoloLaw's flag plans, the
 * ClipMask key algebra, MotionTrackModel's TrackFollow math and the
 * ExtractAudio constructors' decisions.
 *
 * A whole project never crosses JNI either. [packGraph] marshals a
 * LIST-of-clips slice — the stage-1 algebra slice extended to a graph (see
 * the stage-2 addendum in CPP-MIGRATION-REMAINING-MAP.md): per clip, identity
 * KEYS (small ints assigned to id strings in encounter order; -1 = null, a
 * dangling link id still gets a key), the lane layout, and exactly the
 * fields these ops read. Strings, effects, keyframe values and mask states
 * never cross (fan-out reuses the stage-1 slice per clip). The native side
 * computes PLANS — which clips move where, with what numbers — and the
 * model wrappers rebuild the objects with the original code's own copy() /
 * list ops, minting UUIDs Kotlin-side in the plan's order.
 *
 * There is NO Kotlin copy of the migrated decisions any more — native is
 * the only path (the post-234 rule): a genuinely missing libam_engine.so
 * fails loudly at the first call. [isAvailable] stays a read-only probe
 * (no throwing init gate).
 */
object ClipGraphNative {
    init {
        // Trigger DecoderPool's library load on first touch (see ModelLogicNative).
        DecoderPool.isAvailable
    }

    val isAvailable: Boolean get() = try { DecoderPool.isAvailable } catch (_: Throwable) { false }

    // ---- the graph slice ----

    class GraphPackData(
        val clips: List<TimelineClip>,
        val laneOffsets: IntArray,
        val laneIndex: IntArray,
        val idKey: IntArray,
        val groupKey: IntArray,
        val chainKey: IntArray,
        val parentKey: IntArray,
        val controllerKey: IntArray,
        val keyToClip: IntArray,
        val type: IntArray,
        val startMs: LongArray,
        val durationMs: LongArray,
        val locked: BooleanArray,
        val visible: BooleanArray,
        val muted: BooleanArray,
        val groupFreeLength: BooleanArray,
        val fadeInMs: LongArray,
        val fadeOutMs: LongArray,
        val trimStartMs: LongArray,
        val speed: FloatArray,
        val hasSource: BooleanArray,
        val sourceDurationMs: LongArray,
        /** key -> the id string it stands for. */
        val keyToId: Array<String?>,
        /** id string -> its identity key. */
        val keyById: Map<String, Int>,
        /** clip id -> flat clip index (last wins, the associateBy convention). */
        val indexById: Map<String, Int>
    ) {
        val keyCount: Int get() = keyToClip.size

        /** The key of an id, or -2 when the id names nothing (matches no clip). */
        fun keyOf(id: String?): Int = if (id == null) -1 else (keyById[id] ?: -2)

        /** The id string for a plain key (< keyCount), else null. */
        fun idForKey(key: Int): String? = if (key < 0 || key >= keyCount) null else keyToId[key]
    }

    /** Packs [tracks] into the graph slice the native plans consume. */
    fun packGraph(tracks: List<TimelineTrack>): GraphPackData {
        val clips = tracks.flatMap { it.clips }
        val n = clips.size
        val keys = HashMap<String, Int>()
        fun keyOf(s: String?): Int = if (s == null) -1 else keys.getOrPut(s) { keys.size }
        val idKey = IntArray(n)
        val groupKey = IntArray(n)
        val chainKey = IntArray(n)
        val parentKey = IntArray(n)
        val controllerKey = IntArray(n)
        for ((i, c) in clips.withIndex()) {
            idKey[i] = keyOf(c.id)
            groupKey[i] = keyOf(c.groupId)
            chainKey[i] = keyOf(c.chainId)
            parentKey[i] = keyOf(c.parent?.id)
            controllerKey[i] = keyOf(c.controllerId)
        }
        val keyToClip = IntArray(keys.size) { -1 }
        val indexById = HashMap<String, Int>()
        for ((i, c) in clips.withIndex()) {
            keyToClip[idKey[i]] = i
            indexById[c.id] = i
        }
        val keyToId = arrayOfNulls<String>(keys.size)
        for ((id, k) in keys) keyToId[k] = id
        val laneOffsets = IntArray(tracks.size + 1)
        val laneIndex = IntArray(n)
        var k = 0
        for ((li, lane) in tracks.withIndex()) {
            laneOffsets[li] = k
            for (c in lane.clips) {
                laneIndex[k] = li
                k++
            }
        }
        laneOffsets[tracks.size] = k
        return GraphPackData(
            clips = clips,
            laneOffsets = laneOffsets,
            laneIndex = laneIndex,
            idKey = idKey,
            groupKey = groupKey,
            chainKey = chainKey,
            parentKey = parentKey,
            controllerKey = controllerKey,
            keyToClip = keyToClip,
            type = IntArray(n) { clips[it].type.ordinal },
            startMs = LongArray(n) { clips[it].startMs },
            durationMs = LongArray(n) { clips[it].durationMs },
            locked = BooleanArray(n) { clips[it].isLocked },
            visible = BooleanArray(n) { clips[it].isVisible },
            muted = BooleanArray(n) { clips[it].isMuted },
            groupFreeLength = BooleanArray(n) { clips[it].groupFreeLength },
            fadeInMs = LongArray(n) { clips[it].fadeInMs },
            fadeOutMs = LongArray(n) { clips[it].fadeOutMs },
            trimStartMs = LongArray(n) { clips[it].trimStartMs },
            speed = FloatArray(n) { clips[it].speed },
            hasSource = BooleanArray(n) { clips[it].uri != null && !clips[it].isImage },
            sourceDurationMs = LongArray(n) { clips[it].sourceDurationMs },
            keyToId = keyToId,
            keyById = keys,
            indexById = indexById
        )
    }

    // ---- plan result shapes ----

    class GroupPlanData(
        val mode: Int, val targetClip: Int, val startMs: Long, val durationMs: Long,
        val outerKey: Int, val topLaneIndex: Int, val groupCount: Int, val movers: IntArray
    )

    class DupEntryData(val groupKeyOut: Int, val parentKeyOut: Int, val controllerKeyOut: Int, val nameSuffix: Boolean)

    class SplitKidData(val clip: Int, val cls: Int, val d1: Long, val parentOut: Int, val halfKey: Int)

    class SplitPlanData(val ok: Boolean, val groupStartMs: Long, val groupFirstDurMs: Long, val kids: List<SplitKidData>)

    class RefitData(val clip: Int, val startMs: Long, val durationMs: Long, val trimDelta: Long, val endDelta: Long)

    class FitPassData(val changed: Boolean, val refits: List<RefitData>, val clearGroup: BooleanArray, val clearParent: BooleanArray)

    class NormalizeData(val keptPerLane: List<IntArray>, val overflow: IntArray, val clearChain: BooleanArray)

    class FitChainEntryData(val clip: Int, val startMs: Long, val durationMs: Long)

    class ChainSelData(val insertAt: Int, val chainStart: Long, val chainDur: Long, val selOrder: IntArray)

    class MultiEditData(
        val clip: Int, val code: Int, val newStart: Long, val newDur: Long, val newTrim: Long,
        val fadeMode: Int, val kfOp: Int, val kfDelta: Long, val half2Trim: Long
    )

    class FanEntryData(val prop: AnimProp, val keyframe: Boolean, val a: Float, val b: Float)

    class FanPlanData(
        val entries: List<FanEntryData>, val colourEntries: List<FanEntryData>,
        val rel: Long, val colourScalarsChanged: Boolean
    )

    class SoloPlanData(
        val soloVisible: BooleanArray, val soloMuted: BooleanArray, val soloCopy: BooleanArray,
        val restVisible: BooleanArray, val restMuted: BooleanArray, val restCopy: BooleanArray
    )

    // ---- composed helpers (pack spread + result decode) ----

    fun groupPlan(p: GraphPackData, selected: BooleanArray): GroupPlanData {
        val r = nativeGroupPlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs, selected
        )
        return GroupPlanData(
            r[0].toInt(), r[1].toInt(), r[2], r[3], r[4].toInt(), r[5].toInt(), r[6].toInt(),
            IntArray(r.size - 7) { r[7 + it].toInt() }
        )
    }

    fun ungroupPlan(p: GraphPackData, groupKey: Int): IntArray = nativeUngroupPlan(
        p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
        p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
        p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
        p.speed, p.hasSource, p.sourceDurationMs, groupKey
    )

    fun duplicatePlan(p: GraphPackData, originalOrder: IntArray, groupKey: Int): List<DupEntryData> {
        val r = nativeDuplicatePlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs, originalOrder, groupKey
        )
        return originalOrder.indices.map { j ->
            DupEntryData(r[j * 4], r[j * 4 + 1], r[j * 4 + 2], r[j * 4 + 3] != 0)
        }
    }

    fun splitGroupPlan(p: GraphPackData, groupKey: Int, playheadMs: Long): SplitPlanData {
        val r = nativeSplitGroupPlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs, groupKey, playheadMs
        )
        val kidCount = r[3].toInt()
        val kids = ArrayList<SplitKidData>(kidCount)
        var o = 4
        repeat(kidCount) {
            kids += SplitKidData(r[o].toInt(), r[o + 1].toInt(), r[o + 2], r[o + 3].toInt(), r[o + 4].toInt())
            o += 5
        }
        return SplitPlanData(r[0] != 0L, r[1], r[2], kids)
    }

    fun fitGroupsPass(p: GraphPackData): FitPassData {
        val r = nativeFitGroupsPass(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs
        )
        val refitCount = r[1].toInt()
        val refits = ArrayList<RefitData>(refitCount)
        var o = 2
        repeat(refitCount) {
            refits += RefitData(r[o].toInt(), r[o + 1], r[o + 2], r[o + 3], r[o + 4])
            o += 5
        }
        val n = p.clips.size
        val clearGroup = BooleanArray(n) { r[o + it * 2] != 0L }
        val clearParent = BooleanArray(n) { r[o + it * 2 + 1] != 0L }
        return FitPassData(r[0] != 0L, refits, clearGroup, clearParent)
    }

    fun normalizePlan(p: GraphPackData): NormalizeData {
        val r = nativeNormalizePlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs
        )
        val laneCount = r[0]
        var o = 1
        val counts = IntArray(laneCount) { r[o++] }
        val kept = ArrayList<IntArray>(laneCount)
        for (l in 0 until laneCount) {
            kept += IntArray(counts[l]) { r[o++] }
        }
        val overflowCount = r[o++]
        val overflow = IntArray(overflowCount) { r[o++] }
        val clearChain = BooleanArray(p.clips.size) { r[o + it] != 0 }
        return NormalizeData(kept, overflow, clearChain)
    }

    fun fitChains(p: GraphPackData): Pair<Boolean, List<FitChainEntryData>> {
        val r = nativeFitChains(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs
        )
        val count = r[1].toInt()
        val entries = ArrayList<FitChainEntryData>(count)
        var o = 2
        repeat(count) {
            entries += FitChainEntryData(r[o].toInt(), r[o + 1], r[o + 2])
            o += 3
        }
        return (r[0] != 0L) to entries
    }

    fun placeInChainLane(p: GraphPackData, chainKey: Int): Int = nativePlaceInChainLane(
        p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
        p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
        p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
        p.speed, p.hasSource, p.sourceDurationMs, chainKey
    )

    fun chainSelectedPlan(p: GraphPackData, selected: BooleanArray): ChainSelData {
        val r = nativeChainSelectedPlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs, selected
        )
        val count = r[0].toInt()
        return ChainSelData(
            r[1].toInt(), r[2], r[3],
            IntArray(count) { r[4 + it].toInt() }
        )
    }

    /** Native action codes for [multiApplyPlan] (NOT MultiAction ordinals). */
    object MultiCode {
        const val SPLIT = 0
        const val DELETE_LEFT = 1
        const val DELETE_RIGHT = 2
        const val EXTEND_START = 3
        const val MOVE_START = 4
        const val EXTEND_END = 5
        const val MOVE_END = 6
        const val ALIGN_START = 7
        const val ALIGN_END = 8
        const val SEQUENCE = 9
    }

    fun multiApplyPlan(p: GraphPackData, selected: BooleanArray, actionCode: Int, playheadMs: Long): List<MultiEditData> {
        val r = nativeMultiApplyPlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs, selected, actionCode, playheadMs
        )
        val count = r[0].toInt()
        val edits = ArrayList<MultiEditData>(count)
        var o = 1
        repeat(count) {
            edits += MultiEditData(
                r[o].toInt(), r[o + 1].toInt(), r[o + 2], r[o + 3], r[o + 4],
                r[o + 5].toInt(), r[o + 6].toInt(), r[o + 7], r[o + 8]
            )
            o += 9
        }
        return edits
    }

    fun connected(p: GraphPackData, controllerKey: Int): IntArray = nativeConnected(
        p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
        p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
        p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
        p.speed, p.hasSource, p.sourceDurationMs, controllerKey
    )

    fun soloPlan(
        p: GraphPackData, soloKey: Int,
        stateHas: BooleanArray, stateVisible: BooleanArray, stateMuted: BooleanArray
    ): SoloPlanData {
        val r = nativeSoloPlan(
            p.laneOffsets, p.laneIndex, p.idKey, p.groupKey, p.chainKey, p.parentKey,
            p.controllerKey, p.keyToClip, p.type, p.startMs, p.durationMs, p.locked,
            p.visible, p.muted, p.groupFreeLength, p.fadeInMs, p.fadeOutMs, p.trimStartMs,
            p.speed, p.hasSource, p.sourceDurationMs, soloKey, stateHas, stateVisible, stateMuted
        )
        val n = p.clips.size
        fun slice(from: Int) = BooleanArray(n) { r[from + it] != 0 }
        return SoloPlanData(slice(0), slice(n), slice(2 * n), slice(3 * n), slice(4 * n), slice(5 * n))
    }

    /** The fan-out plan for one connected layer; the three clips cross as stage-1 algebra slices. */
    fun fanPlan(before: TimelineClip, after: TimelineClip, layer: TimelineClip, timeMs: Long): FanPlanData {
        fun packKeysOf(c: TimelineClip): Triple<IntArray, LongArray, FloatArray> {
            val props = AnimProp.values()
            val counts = IntArray(props.size) { -1 }
            var total = 0
            for (prop in props) {
                val l = c.keyframes[prop] ?: continue
                counts[prop.ordinal] = l.size
                total += l.size
            }
            val times = LongArray(total)
            val vals = FloatArray(total * 6)
            var off = 0
            for (prop in props) {
                val l = c.keyframes[prop] ?: continue
                val (t, v, _) = ClipAlgebraNative.packKeys(l)
                t.copyInto(times, off)
                v.copyInto(vals, off * 6)
                off += l.size
            }
            return Triple(counts, times, vals)
        }
        val (bCounts, bTimes, bVals) = packKeysOf(before)
        val (aCounts, aTimes, aVals) = packKeysOf(after)
        val (lCounts, lTimes, lVals) = packKeysOf(layer)
        val relOut = LongArray(1)
        val r = nativeFanPlan(
            ClipAlgebraNative.packScalars(before), ClipAlgebraNative.packColorLongs(before), before.gradType,
            bCounts, bTimes, bVals, before.startMs,
            ClipAlgebraNative.packScalars(after), ClipAlgebraNative.packColorLongs(after), after.gradType,
            aCounts, aTimes, aVals, after.startMs,
            ClipAlgebraNative.packScalars(layer), ClipAlgebraNative.packColorLongs(layer), layer.gradType,
            lCounts, lTimes, lVals, layer.startMs, layer.durationMs,
            layer.type.ordinal, timeMs, relOut
        )
        val props = AnimProp.values()
        fun entries(from: Int, count: Int): List<FanEntryData> {
            val out = ArrayList<FanEntryData>(count)
            var o = from
            repeat(count) {
                out += FanEntryData(props[r[o].toInt()], r[o + 1].toInt() != 0, r[o + 2], r[o + 3])
                o += 4
            }
            return out
        }
        val mainCount = r[0].toInt()
        val colourCount = r[1].toInt()
        return FanPlanData(
            entries(3, mainCount),
            entries(3 + mainCount * 4, colourCount),
            relOut[0],
            r[2].toInt() != 0
        )
    }

    // ---- externs (fixed pack argument order, see clip_graph_jni.cpp) ----

    @JvmStatic external fun nativeGroupPlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, selected: BooleanArray
    ): LongArray

    @JvmStatic external fun nativeUngroupPlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, opGroupKey: Int
    ): IntArray

    @JvmStatic external fun nativeDuplicatePlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, originalOrder: IntArray, opGroupKey: Int
    ): IntArray

    @JvmStatic external fun nativeSplitGroupPlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, opGroupKey: Int, playheadMs: Long
    ): LongArray

    @JvmStatic external fun nativeFitGroupsPass(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray
    ): LongArray

    @JvmStatic external fun nativeNormalizePlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray
    ): IntArray

    @JvmStatic external fun nativeFitChains(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray
    ): LongArray

    @JvmStatic external fun nativeReorder(laneCount: Int, from: Int, to: Int): IntArray

    @JvmStatic external fun nativePlaceInChainLane(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, opChainKey: Int
    ): Int

    @JvmStatic external fun nativeChainSelectedPlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, selected: BooleanArray
    ): LongArray

    @JvmStatic external fun nativeMultiApplyPlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, selected: BooleanArray,
        action: Int, playheadMs: Long
    ): LongArray

    @JvmStatic external fun nativeFanPlan(
        beforeScalars: FloatArray, beforeColors: LongArray, beforeGradType: Int,
        beforeCounts: IntArray, beforeTimes: LongArray, beforeVals: FloatArray,
        beforeStart: Long,
        afterScalars: FloatArray, afterColors: LongArray, afterGradType: Int,
        afterCounts: IntArray, afterTimes: LongArray, afterVals: FloatArray,
        afterStart: Long,
        layerScalars: FloatArray, layerColors: LongArray, layerGradType: Int,
        layerCounts: IntArray, layerTimes: LongArray, layerVals: FloatArray,
        layerStart: Long, layerDur: Long, layerType: Int, timeMs: Long, relOut: LongArray
    ): FloatArray

    @JvmStatic external fun nativeConnected(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, opControllerKey: Int
    ): IntArray

    @JvmStatic external fun nativeSoloPlan(
        laneOffsets: IntArray, laneIndex: IntArray, idKey: IntArray, groupKey: IntArray,
        chainKey: IntArray, parentKey: IntArray, controllerKey: IntArray, keyToClip: IntArray,
        type: IntArray, startMs: LongArray, durationMs: LongArray, locked: BooleanArray,
        visible: BooleanArray, muted: BooleanArray, groupFreeLength: BooleanArray,
        fadeInMs: LongArray, fadeOutMs: LongArray, trimStartMs: LongArray, speed: FloatArray,
        hasSource: BooleanArray, sourceDurationMs: LongArray, soloKey: Int,
        stateHas: BooleanArray, stateVisible: BooleanArray, stateMuted: BooleanArray
    ): IntArray

    @JvmStatic external fun nativeMaskStateAt(
        base: FloatArray, times: LongArray, states: FloatArray, easings: FloatArray, t: Long
    ): FloatArray

    @JvmStatic external fun nativeMaskKeyIndexAt(times: LongArray, t: Long): Int

    @JvmStatic external fun nativeMaskLerp(a: FloatArray, b: FloatArray, t: Float): FloatArray

    @JvmStatic external fun nativeTrackIndexAt(times: LongArray, t: Long): Int

    @JvmStatic external fun nativeTrackRender(
        times: LongArray, dx: FloatArray, dy: FloatArray, rot: FloatArray?, scale: FloatArray?,
        tx: Float, ty: Float, rotDeg: Float, scalePercent: Float, ssx: Float, ssy: Float, t: Long
    ): FloatArray

    @JvmStatic external fun nativeTrackOffset(
        times: LongArray, dx: FloatArray, dy: FloatArray, t: Long
    ): FloatArray

    @JvmStatic external fun nativeDetachedKeepSpeed(propCounts: IntArray): Boolean

    @JvmStatic external fun nativeImportedStart(startMs: Long): Long
}
