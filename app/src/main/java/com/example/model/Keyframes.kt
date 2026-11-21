package com.example.model

import com.example.engine.ClipAlgebraNative
import com.example.engine.FrameEvalNative
import com.example.engine.ModelLogicNative
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Keyframe animation of the Transform properties (Alight Motion style).
 *
 * Every property keeps its own list of keyframes. A keyframe stores the value at a time that is
 * RELATIVE to the layer start, so moving the layer keeps the animation with it. Between two
 * keyframes the value is blended with the easing curve of the first one of the pair.
 *
 * As soon as a property has keyframes, the plain field on the clip (transformX, scalePercent, ...)
 * is only a fallback: what is drawn comes from [TimelineClip.at].
 */
@Serializable
enum class AnimProp(val isColor: Boolean = false) {
    POSITION, SCALE, ROTATION, POS_Z, ROT_X, ROT_Y,

    /** Colour keyframes (Color panel). The ARGB colour is packed exactly into [Keyframe.a] / [Keyframe.b]. */
    FILL_COLOR(true), GRAD_COLOR_1(true), GRAD_COLOR_2(true),

    /** Crop (Crop panel): box size (width, height in %) and box position (horizontal, vertical in %). */
    CROP_SIZE, CROP_POS,

    /** Layer opacity (Blend panel), 0..100. */
    OPACITY,

    /** Playback speed (0.1x..4x). Time-remaps the layer's media (see [sourceOffsetMs]). */
    SPEED,

    /** Stroke trim (draw-on): a = visible outline start, b = visible outline end, both 0..1 of the outline length. */
    STROKE_TRIM,

    /**
     * A rounded shape's corner radius in the stored dp (65 x rho, rho = the radius as a fraction
     * of the shape's smaller half extent — the [TimelineClip.shapeCornerRadius] unit; see
     * [TimelineClip.shapeCornerRadii]). Keyframed by the importer when Alight animates a shape's
     * `cornerRadius` (build 172); the painters read it through [TimelineClip.at] like every
     * other keyframed property, and the native preview evaluates it with the model's own sampler
     * (see NativeRenderAt, the pie / arc precedent).
     */
    SHAPE_CORNER_RADIUS,

    /**
     * A gradient fill's geometry (Alight `gradientoverlay2` angle / scale, build 173):
     * a = the gradient angle in degrees, b = the scale. The painters read the end points
     * through [TimelineClip.at] like every other keyframed property; the centre is recovered
     * from the stored points (see [GradGeometry]), so the animation pivots around the
     * gradient's own centre. The native preview evaluates it with the model's own sampler
     * (see NativeRenderAt, the corner-radius precedent). Importer-only: no editor writes
     * this property (the gradient handles edit the stored end points directly).
     */
    GRAD_GEOMETRY
}

/** ARGB colour -> (A,R) and (G,B) packed as two exact floats (each < 65536). Native since migration #3 (model_logic). */
fun packColor(argb: Long): Pair<Float, Float> {
    val out = ModelLogicNative.nativePackColor(argb)
    return out[0] to out[1]
}

fun unpackColor(pa: Float, pb: Float): Long = ModelLogicNative.nativeUnpackColor(pa, pb)

/** Playhead closer than this to a keyframe counts as "on" that keyframe. */
const val KEYFRAME_TOL_MS = 40L

/**
 * Cubic bezier easing, same as CSS cubic-bezier(x1, y1, x2, y2): the curve goes from (0,0) to (1,1)
 * and the two control points shape it. x is time (0..1), y is progress and may leave 0..1
 * (overshoot, "back" easings).
 */
@Serializable
data class Easing(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {

    val isLinear: Boolean
        get() = ModelLogicNative.nativeEasingIsLinear(x1, y1, x2, y2)

    /** Point of the curve for the bezier parameter [s] (0..1), used to draw it. Native since migration #3 (model_logic). */
    fun pointAt(s: Float): Pair<Float, Float> {
        val out = ModelLogicNative.nativeEasingPointAt(x1, y1, x2, y2, s)
        return out[0] to out[1]
    }

    /** Progress (0..1, may overshoot) for the time fraction [t] (0..1). */
    fun apply(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        return FrameEvalNative.nativeEasingApply(x1, y1, x2, y2, t)
    }

    fun sameAs(o: Easing, eps: Float = 0.01f): Boolean =
        ModelLogicNative.nativeEasingSameAs(x1, y1, x2, y2, o.x1, o.y1, o.x2, o.y2, eps)

    companion object {
        val LINEAR = Easing(1f / 3f, 1f / 3f, 2f / 3f, 2f / 3f)

        /** Curves offered in the easing editor (name, curve). */
        val PRESETS: List<Pair<String, Easing>> = listOf(
            "Linear" to LINEAR,
            "Ease in" to Easing(0.42f, 0f, 1f, 1f),
            "Ease out" to Easing(0f, 0f, 0.58f, 1f),
            "Ease in-out" to Easing(0.42f, 0f, 0.58f, 1f),
            "Sine in" to Easing(0.47f, 0f, 0.745f, 0.715f),
            "Sine out" to Easing(0.39f, 0.575f, 0.565f, 1f),
            "Sine in-out" to Easing(0.445f, 0.05f, 0.55f, 0.95f),
            "Quad in" to Easing(0.55f, 0.085f, 0.68f, 0.53f),
            "Quad out" to Easing(0.25f, 0.46f, 0.45f, 0.94f),
            "Quad in-out" to Easing(0.455f, 0.03f, 0.515f, 0.955f),
            "Cubic in" to Easing(0.55f, 0.055f, 0.675f, 0.19f),
            "Cubic out" to Easing(0.215f, 0.61f, 0.355f, 1f),
            "Cubic in-out" to Easing(0.645f, 0.045f, 0.355f, 1f),
            "Quart in-out" to Easing(0.77f, 0f, 0.175f, 1f),
            "Expo in" to Easing(0.95f, 0.05f, 0.795f, 0.035f),
            "Expo out" to Easing(0.19f, 1f, 0.22f, 1f),
            "Back in" to Easing(0.6f, -0.2f, 0.735f, 0.045f),
            "Back out" to Easing(0.175f, 0.885f, 0.32f, 1.2f),
            "Back in-out" to Easing(0.68f, -0.2f, 0.265f, 1.2f)
        )
    }
}

/**
 * One keyframe. [timeMs] is relative to the layer start. [a] is the value (X for Position),
 * [b] is the second value (Y for Position, otherwise unused). [easing] shapes the way from this
 * keyframe to the next one.
 * Tapping an animation (Animations panel) writes ordinary keyframes marked with a [tag].
 */
@Serializable
data class Keyframe(
    val timeMs: Long,
    val a: Float,
    val b: Float = 0f,
    val easing: Easing = Easing.LINEAR,
    /** 0 = made by the user, [ANIM_TAG_IN] / [ANIM_TAG_OUT] = part of an In / Out animation (see AnimPresets.kt). */
    val tag: Int = 0
)

/** Value of a keyframe list at [relMs]: held before the first / after the last, eased in between. */
fun List<Keyframe>.valueAt(relMs: Long): Pair<Float, Float> {
    val first = first()
    if (relMs <= first.timeMs) return first.a to first.b
    val last = last()
    if (relMs >= last.timeMs) return last.a to last.b
    val (times, vals) = FrameEvalNative.packKeyframes(this)
    val out = FloatArray(2)
    FrameEvalNative.nativeKeyframeValueAt(times, vals, relMs, false, out)
    return out[0] to out[1]
}

/**
 * Index of the keyframe that starts the segment the time is in (k[i] <= rel < k[i+1]),
 * or -1 when the time is outside every segment (before the first / at or after the last keyframe).
 */
fun List<Keyframe>.segmentIndexAt(relMs: Long): Int =
    ModelLogicNative.nativeSegmentIndexAt(LongArray(size) { this[it].timeMs }, relMs)

/** Index of the keyframe the time sits on (within [KEYFRAME_TOL_MS]), or -1. */
fun List<Keyframe>.indexOnTime(relMs: Long): Int =
    ModelLogicNative.nativeIndexOnTime(LongArray(size) { this[it].timeMs }, relMs, KEYFRAME_TOL_MS)

/**
 * Colour keyframes: same segment / easing logic as [valueAt], but every channel (A, R, G, B) is
 * blended on its own and the result is packed again (see [packColor]).
 */
fun List<Keyframe>.colorValueAt(relMs: Long): Pair<Float, Float> {
    val first = first()
    if (relMs <= first.timeMs) return first.a to first.b
    val last = last()
    if (relMs >= last.timeMs) return last.a to last.b
    val (times, vals) = FrameEvalNative.packKeyframes(this)
    val out = FloatArray(2)
    FrameEvalNative.nativeKeyframeValueAt(times, vals, relMs, true, out)
    return out[0] to out[1]
}

/**
 * The clip's stored value of property [p]. Native since migration stage 1
 * (clip_algebra): the dispatch lives in C++; this wrapper only marshals the
 * algebra slice (see ClipAlgebraNative). SCALE's `b` > 0 is a separate height
 * scale % (the Alight importer stores a shape's animated height there, see
 * composedSizeKeys); b = 0 keeps the uniform meaning.
 */
fun TimelineClip.propValues(p: AnimProp): Pair<Float, Float> {
    val out = FloatArray(2)
    ClipAlgebraNative.nativePropValues(
        ClipAlgebraNative.packScalars(this), ClipAlgebraNative.packColorLongs(this),
        gradType, p.ordinal, out
    )
    return out[0] to out[1]
}

/** The clip with property [p] set to (a, b). Native dispatch (clip_algebra), see [propValues]. */
fun TimelineClip.withProp(p: AnimProp, a: Float, b: Float): TimelineClip {
    val sc = ClipAlgebraNative.packScalars(this)
    val cl = ClipAlgebraNative.packColorLongs(this)
    ClipAlgebraNative.nativeWithProp(sc, cl, gradType, p.ordinal, a, b)
    return ClipAlgebraNative.applyScalars(this, sc, cl)
}

/** True for an imported `.pie` wedge: its outline is rebuilt per frame from its angle tracks. */
val TimelineClip.hasPie: Boolean
    get() = pieStartKeys.isNotEmpty() && pieEndKeys.isNotEmpty() && pieRadius > 0f

/**
 * The pie wedge's normalised outline at [relMs] (ms from the layer's start): both angle tracks
 * are sampled with the ordinary keyframe sampler (the segment easings ride along) and the
 * outline is rebuilt from the evaluated angles by [PieOutline]. Empty when the def's wrap rule
 * closes the wedge at that instant — the layer draws nothing then, exactly as in Alight.
 * A non-pie layer keeps its stored segments.
 */
fun TimelineClip.pieSegmentsAt(relMs: Long): List<PathSeg> {
    if (!hasPie) return pathSegments
    val s = pieStartKeys.valueAt(relMs).first
    val e = pieEndKeys.valueAt(relMs).first
    return PieOutline.normalisedSegments(s.toDouble(), e.toDouble())
}

/** True for an imported `.arc`: its outline is rebuilt per frame from its angle tracks. */
val TimelineClip.hasArc: Boolean
    get() = arcStartKeys.isNotEmpty() && arcEndKeys.isNotEmpty() && arcRadius > 0f

/**
 * The arc's normalised outline at [relMs] (ms from the layer's start): both angle tracks are
 * sampled with the ordinary keyframe sampler and the outline is rebuilt from the evaluated
 * angles by [ArcOutline]. Empty when the def's wrap rule closes the arc at that instant — the
 * layer draws nothing then, exactly as in Alight. A non-arc layer keeps its stored segments.
 */
fun TimelineClip.arcSegmentsAt(relMs: Long): List<PathSeg> {
    if (!hasArc) return pathSegments
    val s = arcStartKeys.valueAt(relMs).first
    val e = arcEndKeys.valueAt(relMs).first
    return ArcOutline.normalisedSegments(s.toDouble(), e.toDouble(), arcClosed)
}

/**
 * The layer as it looks at project time [timeMs]: every keyframed property is replaced by its
 * animated value. Layers without keyframes are returned as they are (no copy).
 * Preview, selection frame and export all draw from this.
 *
 * Native since migration stage 1 (clip_algebra): the evaluation loop, the
 * withProp dispatch and the pie/arc outline rebuild run in C++ over the
 * marshalled algebra slice; this wrapper keeps the identity fast path (a
 * clip nothing applies to is returned as it is). Build 237: stored SPEED
 * keyframes are never evaluated (the native loop skips them, like the
 * original) — a clip always plays at its constant base speed.
 */
fun TimelineClip.at(timeMs: Long): TimelineClip {
    val props = AnimProp.values()
    val counts = IntArray(props.size) { -1 }
    var total = 0
    for (p in props) {
        val l = keyframes[p] ?: continue
        counts[p.ordinal] = l.size
        total += l.size
    }
    val kfTimes = LongArray(total)
    val kfVals = FloatArray(total * 6)
    var off = 0
    for (p in props) {
        val l = keyframes[p] ?: continue
        val (t, v, _) = ClipAlgebraNative.packKeys(l)
        t.copyInto(kfTimes, off)
        v.copyInto(kfVals, off * 6)
        off += l.size
    }
    val (psT, psV, _) = ClipAlgebraNative.packKeys(pieStartKeys)
    val (peT, peV, _) = ClipAlgebraNative.packKeys(pieEndKeys)
    val (asT, asV, _) = ClipAlgebraNative.packKeys(arcStartKeys)
    val (aeT, aeV, _) = ClipAlgebraNative.packKeys(arcEndKeys)
    val sc = ClipAlgebraNative.packScalars(this)
    val cl = ClipAlgebraNative.packColorLongs(this)
    val changed = BooleanArray(1)
    val segs = ClipAlgebraNative.nativeClipAt(
        startMs, sc, cl, gradType, counts, kfTimes, kfVals,
        psT, psV, peT, peV, asT, asV, aeT, aeV,
        pieRadius, arcRadius, arcClosed, timeMs, changed
    )
    if (!changed[0] && segs == null) return this
    var c = this
    if (changed[0]) c = ClipAlgebraNative.applyScalars(c, sc, cl)
    if (segs != null) c = c.copy(pathSegments = FrameEvalNative.unpackSegments(segs))
    return c
}

/** Sets the value at [relMs]: replaces the keyframe the time sits on, otherwise inserts a new one. */
fun TimelineClip.withKeyframe(p: AnimProp, relMs: Long, a: Float, b: Float): TimelineClip {
    val list = keyframes[p].orEmpty()
    val (times, vals, tags) = ClipAlgebraNative.packKeys(list)
    val outT = LongArray(list.size + 1)
    val outV = FloatArray((list.size + 1) * 6)
    val outG = IntArray(list.size + 1)
    val n = ClipAlgebraNative.nativeWithKeyframe(times, vals, tags, relMs, a, b, outT, outV, outG)
    return copy(keyframes = keyframes + (p to ClipAlgebraNative.unpackKeys(outT, outV, outG, n)))
}

/**
 * Removes one keyframe. Removing the last one of a property ends its animation: the layer keeps
 * the value that keyframe had.
 */
fun TimelineClip.withoutKeyframe(p: AnimProp, index: Int): TimelineClip {
    val list = keyframes[p] ?: return this
    if (index !in list.indices) return this
    val (times, vals, tags) = ClipAlgebraNative.packKeys(list)
    val outT = LongArray(list.size - 1)
    val outV = FloatArray((list.size - 1) * 6)
    val outG = IntArray(list.size - 1)
    val removed = FloatArray(2)
    val n = ClipAlgebraNative.nativeWithoutKeyframe(times, vals, tags, index, outT, outV, outG, removed)
    return if (n == 0) {
        copy(keyframes = keyframes - p).withProp(p, removed[0], removed[1])
    } else {
        copy(keyframes = keyframes + (p to ClipAlgebraNative.unpackKeys(outT, outV, outG, n)))
    }
}

fun TimelineClip.withEasing(p: AnimProp, index: Int, easing: Easing): TimelineClip {
    val list = keyframes[p] ?: return this
    if (index !in list.indices) return this
    val (times, vals, tags) = ClipAlgebraNative.packKeys(list)
    val outT = LongArray(list.size)
    val outV = FloatArray(list.size * 6)
    val outG = IntArray(list.size)
    val n = ClipAlgebraNative.nativeWithEasing(
        times, vals, tags, index, easing.x1, easing.y1, easing.x2, easing.y2, outT, outV, outG
    )
    return copy(keyframes = keyframes + (p to ClipAlgebraNative.unpackKeys(outT, outV, outG, n)))
}

/**
 * Applies [transform] (one native time law) to every key time of the layer at
 * once — the keyframes map, the masks' keys and the pie/arc tracks — in one
 * concatenated marshalling, then rebuilds with the original copy() shape.
 */
private fun TimelineClip.mapAllKeyTimes(transform: (LongArray) -> LongArray): TimelineClip {
    val total = keyframes.values.sumOf { it.size } + masks.sumOf { it.keys.size } +
        pieStartKeys.size + pieEndKeys.size + arcStartKeys.size + arcEndKeys.size
    val all = LongArray(total)
    var o = 0
    for (l in keyframes.values) for (k in l) all[o++] = k.timeMs
    for (m in masks) for (k in m.keys) all[o++] = k.timeMs
    for (k in pieStartKeys) all[o++] = k.timeMs
    for (k in pieEndKeys) all[o++] = k.timeMs
    for (k in arcStartKeys) all[o++] = k.timeMs
    for (k in arcEndKeys) all[o++] = k.timeMs
    val out = transform(all)
    var i = 0
    fun take(n: Int): LongArray {
        val s = out.copyOfRange(i, i + n)
        i += n
        return s
    }
    return copy(
        keyframes = keyframes.mapValues { (_, l) ->
            val ts = take(l.size)
            l.mapIndexed { j, k -> k.copy(timeMs = ts[j]) }
        },
        masks = masks.map { m ->
            val ts = take(m.keys.size)
            if (m.keys.isEmpty()) m else m.copy(keys = m.keys.mapIndexed { j, k -> k.copy(timeMs = ts[j]) })
        },
        pieStartKeys = pieStartKeys.let { l -> val ts = take(l.size); l.mapIndexed { j, k -> k.copy(timeMs = ts[j]) } },
        pieEndKeys = pieEndKeys.let { l -> val ts = take(l.size); l.mapIndexed { j, k -> k.copy(timeMs = ts[j]) } },
        arcStartKeys = arcStartKeys.let { l -> val ts = take(l.size); l.mapIndexed { j, k -> k.copy(timeMs = ts[j]) } },
        arcEndKeys = arcEndKeys.let { l -> val ts = take(l.size); l.mapIndexed { j, k -> k.copy(timeMs = ts[j]) } }
    )
}

/** Moves every keyframe by [deltaMs] (the layer start moved: left trim / split). */
fun TimelineClip.withShiftedKeyframes(deltaMs: Long): TimelineClip {
    if ((keyframes.isEmpty() && masks.isEmpty() && !hasPie && !hasArc) || deltaMs == 0L) return this
    return mapAllKeyTimes { times ->
        LongArray(times.size).also { ClipAlgebraNative.nativeShiftTimes(times, deltaMs, it) }
    }
}

/** Stretches the keyframe times (speed change: the layer got longer / shorter). */
fun TimelineClip.withScaledKeyframes(factor: Float): TimelineClip {
    if ((keyframes.isEmpty() && masks.isEmpty() && !hasPie && !hasArc) || abs(factor - 1f) < 1e-4f) return this
    return mapAllKeyTimes { times ->
        LongArray(times.size).also { ClipAlgebraNative.nativeScaleTimes(times, factor, it) }
    }
}

/** Every distinct keyframe time of the layer (relative), for the diamonds on the layer strip. */
fun TimelineClip.keyframeTimes(): List<Long> {
    val total = keyframes.values.sumOf { it.size } + masks.sumOf { it.keys.size } +
        pieStartKeys.size + pieEndKeys.size + arcStartKeys.size + arcEndKeys.size
    if (total == 0) return emptyList()
    val all = LongArray(total)
    var o = 0
    for (l in keyframes.values) for (k in l) all[o++] = k.timeMs
    for (m in masks) for (k in m.keys) all[o++] = k.timeMs
    for (k in pieStartKeys) all[o++] = k.timeMs
    for (k in pieEndKeys) all[o++] = k.timeMs
    for (k in arcStartKeys) all[o++] = k.timeMs
    for (k in arcEndKeys) all[o++] = k.timeMs
    return ClipAlgebraNative.nativeKeyframeTimes(all).toList()
}

// =====================================================================================
// Speed: time remapping
// =====================================================================================

/** Allowed layer speed range. Source time advances exactly `speed` x timeline time. */
const val MIN_SPEED = 0.1f
const val MAX_SPEED = 4f

/**
 * How far into the source the layer has played after [relMs] of its own time: the clip plays
 * at ONE constant [TimelineClip.speed], so this is a plain multiplication. (Speed ramps were
 * removed in build 237: SPEED keyframes stored in older projects are ignored, so a ramped
 * clip now plays at its base speed.)
 */
fun TimelineClip.sourceOffsetMs(relMs: Long): Long = (relMs * speed).toLong()

/**
 * The speed the exporter resamples the audio by, so a sped-up / slowed-down layer's sound
 * follows its picture. One constant speed per clip since build 237 (speed ramps removed),
 * so this is simply the clip's base speed; the name stays for the audio path's call sites.
 */
val TimelineClip.exportSpeed: Float
    get() = speed

