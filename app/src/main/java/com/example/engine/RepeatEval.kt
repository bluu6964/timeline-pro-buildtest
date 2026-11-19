package com.example.engine

import com.example.model.ClipFx
import com.example.model.ClipFxLibrary
import com.example.model.TimelineClip
import com.example.model.unpackColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Alight Motion's repeat family — plain `repeat`, `repeat.radial`, `repeat.scatter`,
 * `repeat.line`: the layer drawn once per copy, the copies arranged in a row / on a ring /
 * scattered over a disc / along a line. Pure Kotlin (no Android classes) so it can be unit
 * tested; the C++ side needs no change (the unresolved types 56..58 and 227 are skipped by
 * fxParseOps: they sit above FX_MAX, like Motion Blur).
 *
 * What Alight stores for the plain repeat (all 3 plain-repeat instances in the corpus — on the
 * "Horizontal Lines" / "Vertical Lines" shapes — have angle 0, scale 1, alpha 1, time 0, so only
 * count + offset is pinned by real files; the rest is the plain reading of the parameter names, to
 * check on the phone comparison):
 * ```
 * <effect id="com.alightcreative.effects.repeat" locallyApplied="true">
 *   <property name="count" type="float" value="5.000000" />
 *   <property name="time" type="float" value="0.000000" />
 *   <property name="offset" type="vec2" value="0.000000,93.000000" />
 *   <property name="angle" type="float" value="0.000000" />
 *   <property name="scale" type="float" value="1.000000" />
 *   <property name="alpha" type="float" value="1.000000" />
 * </effect>
 * ```
 * The app stores it as the Repeat effect (see [ClipFxLibrary.REPEAT]): values =
 * [count, offsetX, offsetY, angle, scale %, alpha %] — the offset converted to preview px by the
 * importer, like every other position. `time` (a per-copy time shift) is NOT stored: no corpus
 * instance uses it, and the draw loops only have the layer as it is at the frame time.
 *
 * Copy i (0 = the layer itself): position + i * offset, rotation + i * angle, scale * scale^i,
 * opacity * alpha^i. The turn and the scale are about the layer's own centre (its pivot), the
 * offset is in scene space. A shape keeps its aspect: only the uniform [TimelineClip.scalePercent]
 * is multiplied, [TimelineClip.shapeScaleX] / `shapeScaleY` stay — the painters draw the size from
 * those (see `shapeDrawScaleX`).
 *
 * `repeat.radial` (the ST Intro "Line 1" layers) puts [count] copies on a ring and does NOT draw
 * the layer at the centre: copy i sits at angle startAngle + i * (sweep / count), turned by that
 * angle + `orientation`, scaled by `baseScale` * `scale`^i. Two of those readings are frame-measured
 * against Alight's own ST Intro export (2026-11-11), not guessed: the ring angles run CLOCKWISE
 * FROM STRAIGHT UP — the direction is (sin a, -cos a), which lands all four measured dashes of both
 * line layers on their copies to ~0.1 degrees, where the (cos, sin) reading leaves one copy in
 * four off by ~7 degrees — and the ring radius is TWICE the `radius` property (Alight's radius
 * behaves like its shape sizes, a half extent: the measured dash centroids sit 394..416 px from the
 * centre for radius 223, reachable only from a 446 px anchor; 223 px cannot reach them). The
 * copies' remaining offset from their anchors (the dash's own place on the line) comes from the
 * copy's rotation, so it follows automatically. Values: [count, radiusPx, startAngle, sweep,
 * orientation, baseScale %, alpha %, angle, scaleStep %, altColour...] (see [ClipFxLibrary]).
 *
 * `repeat.scatter` (the ST Intro "Circle 1" particles) puts [count] copies over a disc around the
 * layer. Alight places them from a pseudo-random field keyed by `scatterSeed`; that generator is
 * not public and the field cannot be read off the exported frames (the copies wander smoothly,
 * each on its own path, as `evolution` runs), so the field here is a deterministic hash field,
 * uniform over the disc, with per-copy scale spread between the effect's `scale` floor and 1 (the
 * measured dots span 0.16..0.86 of the base circle). What the field's SHAPE is was settled by
 * frame measurement against Alight's export (round 5, 2026-11-11): the disc is centred on the
 * LAYER — every copy stays within the written radius of the layer's position even with a large
 * `offset` written, so the offset reads as a translation of Alight's field (a different pattern,
 * not a moved centre) and is folded into the hash seed here — and the written radius is read in
 * height units: every copy stays within radius x sceneH / sceneW of the layer (1242 -> 699 px on
 * the 1920x1080 scene; the importer converts). The drift is measured too: each copy's angle turns
 * ONE way as evolution grows (three tracked copies, all the same sign, 0.2 .. 0.4 of a turn per
 * evolution unit) and its radius breathes between a tenth and the full disc radius (radii measured
 * growing x3 and shrinking x0.25 over 1.5 s). Positions therefore still differ from Alight's copy
 * for copy; counts, sizes, coverage and drift match, and the phone comparison can judge them. With
 * `colorAltCopies` the odd copies take the effect's fill colour (the measured dots alternate
 * white / lavender exactly so). Values: [count, radiusPx, offsetX, offsetY, seed, scaleFloor %,
 * alpha %, fieldAngle, evolution (t0, v0, t1, v1), altColour...].
 *
 * `repeat.line` (build 167, the round-4 banked port) puts [count] copies along a line through
 * the layer's position: copy i sits at layerPos + R(angle) * (position + i * offset), each copy
 * scaled by scale^i and faded by alpha^i, with NO extra per-copy rotation — the angle turns
 * the whole line, not the copies. Unlike the plain repeat, copy 0 is the layer itself ONLY when
 * `position` is (0, 0): the def default is (500, 0), so a panel-made line starts 500 px out.
 * Values: [count, positionX, positionY, offsetX, offsetY, angle, scale %, alpha %, phase,
 * altColour...] (see [ClipFxLibrary]). The family caveat applies in full: the window /
 * ordering props (start / end / phase / easeIn / easeOut / overlap / shape / invert /
 * randomOrder / seed / blend) have no counterpart in the copy expansion, and the stored
 * `phase` is never read here, because the window it belongs to is not reproduced. Keyframed
 * angle / scale / phase take their first value (the pass-2 rule): the draw loops run this
 * object on the STORED effect, never through AmFxEval, so no tail can carry the sweep. With
 * `colorAltCopies` the odd copies take the effect's fill colour, the radial / scatter rule.
 *
 * [undoRotationDeg] is for a layer drawn into its own upright picture (a pose / camera layer): the
 * renderer turns that picture by the layer's own rotation when it places it, so the copies are
 * placed by the offset undone by that turn — pass the rendered layer's rotationDeg there, 0
 * otherwise. It is exact for a centred pivot (the usual case); an off-centre pivot leaves the
 * copies shifted by the pivot error, which the phone comparison can measure.
 *
 * The overlay loops above draw copy CLIPS with the painters. The picture paths (a video / photo
 * texture, a Group layer's composed picture) cannot do that — they place ONE finished picture with
 * renderer pose parameters — so they expand the same arrangements into per-copy pose deltas
 * ([copyDeltas], see RepeatDraw.kt): the same texture drawn once per copy, posed by the delta.
 * The arrangements are additive in position / rotation and multiplicative in scale / opacity, so a
 * copy's delta against the layer's own pose does not depend on that pose; [copyDeltas] reads the
 * deltas off [copiesOf] run on a neutral-pose probe of the layer, which keeps the two expansions
 * in lockstep by construction. A Group layer's copies pose its frame-sized picture about the
 * group's own anchor ([groupCopyOffset]).
 *
 * Known limits (same family as Motion Blur): parent motion is already in the rendered clip the
 * copies are made from, so a repeated group member moves with its group; the copies are drawn
 * into the layer's own picture, so a layer with a repeat-family effect always gets one (see
 * `hasEffects`). The scatter evolution only knows its first and last keyframes (the importer
 * stores just those two). On the picture paths: a corner-pinned or camera-placed video / photo
 * keeps its single quad (the pin IS its pose, copies would stack on it); a Null parent's turn
 * does not turn a picture layer's copy offsets (they stay scene-space, as in the overlay
 * expansion); and the alternating-copy colour has no picture-path counterpart — a copy of a
 * photo or a group cannot be re-tinted per copy, so those copies keep the picture's colours.
 */
internal object RepeatEval {

    /** Largest copy count drawn: Alight allows hundreds, which would stall a frame. */
    const val MAX_COUNT = 50

    /** The layer's enabled plain Repeat effect, or null. */
    fun repeatOf(clip: TimelineClip): ClipFx? =
        clip.effects.firstOrNull { it.enabled && it.type == ClipFxLibrary.REPEAT }

    /** The layer's enabled repeat-family effect (plain / radial / scatter / line), or null. */
    fun anyRepeatOf(clip: TimelineClip): ClipFx? =
        clip.effects.firstOrNull {
            it.enabled && (it.type == ClipFxLibrary.REPEAT ||
                it.type == ClipFxLibrary.REPEAT_RADIAL ||
                it.type == ClipFxLibrary.REPEAT_SCATTER ||
                it.type == ClipFxLibrary.REPEAT_LINE)
        }

    /**
     * The ancestor similarity between a layer's own evaluated pose ([local] — what the render
     * loops' evaluation produces before parent composition) and its fully-composed pose
     * ([composed] — ParentRig's result): the (scale ratio, added rotation) to hand [copiesOf].
     * (1, 0) when there is nothing above the layer.
     */
    fun outerOf(local: TimelineClip, composed: TimelineClip): Pair<Float, Float> {
        val s = if (local.scalePercent > 0.01f) composed.scalePercent / local.scalePercent else 1f
        return s to (composed.rotationDeg - local.rotationDeg)
    }

    /** Copy count of a repeat-family effect: values[0], 1..[MAX_COUNT]. */
    fun countOf(fx: ClipFx): Int = (fx.values.getOrNull(0) ?: 1f).roundToInt().coerceIn(1, MAX_COUNT)

    /**
     * The layer drawn once per copy (for the plain repeat the first entry is always [clip] itself;
     * for the line it is the layer moved by R(angle) * position — the same only when position is (0, 0)).
     * No repeat-family effect, or a disabled one, returns just the layer. [timeMs] is the frame
     * time: only the scatter field reads it (its evolution); the other arrangements are static.
     *
     * [outerScale] / [outerRotDeg] = the similarity the layer's ANCESTORS apply on top of the
     * layer's own pose (the ratio / difference between the fully-composed pose the render loops
     * hand in and the layer's own evaluated pose). Alight places the copies inside the layer's
     * own scene, so ancestor transforms act on the copy offsets too: the position delta of every
     * copy is turned / scaled by them. The render loops used to add the offsets AFTER composing
     * the layer to the top level, in top-level units — inside the Graph preset's Group 1 (scale
     * ~0.75) the Vertical Lines' copies stepped the full 146 px instead of 109, and the last two
     * marched out of the card (Graph round 6, G4). Copy rotation / scale increments are local
     * and compose unchanged, so only the position deltas are transformed here.
     */
    fun copiesOf(
        clip: TimelineClip, undoRotationDeg: Float = 0f, timeMs: Long = Long.MIN_VALUE,
        outerScale: Float = 1f, outerRotDeg: Float = 0f
    ): List<TimelineClip> {
        val fx = anyRepeatOf(clip) ?: return listOf(clip)
        val mode = when (fx.type) {
            ClipFxLibrary.REPEAT_RADIAL -> FrameEvalNative.REPEAT_MODE_RADIAL
            ClipFxLibrary.REPEAT_SCATTER -> FrameEvalNative.REPEAT_MODE_SCATTER
            ClipFxLibrary.REPEAT_LINE -> FrameEvalNative.REPEAT_MODE_LINE
            else -> FrameEvalNative.REPEAT_MODE_PLAIN
        }
        val fxArr = FloatArray(fx.values.size) { fx.values[it] }
        val altOut = LongArray(2)
        val flat = FrameEvalNative.nativeRepeatEval(
            mode, fxArr,
            clip.transformX, clip.transformY, clip.rotationDeg, clip.scalePercent, clip.opacity,
            undoRotationDeg, outerScale, outerRotDeg,
            clip.startMs, clip.endMs, timeMs, altOut
        )
        if (flat == null || flat.size < 5) return listOf(clip)
        val count = flat.size / 5
        val hasAlt = altOut[0] != 0L
        val altColor = altOut[1]
        return List(count) { i ->
            if (mode == FrameEvalNative.REPEAT_MODE_PLAIN && i == 0) {
                clip
            } else {
                val b = i * 5
                var c = clip.copy(
                    transformX = flat[b],
                    transformY = flat[b + 1],
                    rotationDeg = flat[b + 2],
                    scalePercent = flat[b + 3],
                    opacity = flat[b + 4]
                )
                if (hasAlt && i % 2 == 1) c = c.copy(textColor = altColor)
                c
            }
        }
    }

    /**
     * One copy's pose against the layer's own pose, for the draw paths that place a finished
     * picture (a video / photo texture, a Group layer's composed picture): the copy sits
     * ([dx], [dy]) preview px away from the layer, turned [rotDeg] more, scaled by [scaleMul]
     * about its own anchor and faded by [opacityMul]. For the plain repeat the first delta is
     * the identity (the layer itself); radial / scatter have no centre copy; for the line the
     * first delta is R(angle) * position — the identity only when position is (0, 0).
     */
    data class CopyDelta(val dx: Float, val dy: Float, val rotDeg: Float, val scaleMul: Float, val opacityMul: Float)

    /**
     * The copies of [clip]'s repeat-family effect as pose deltas ([CopyDelta]), or null when the
     * layer has no enabled repeat-family effect. The deltas are read off [copiesOf] run on a
     * neutral-pose probe of the layer (position 0, no turn, scale / opacity 100): every
     * arrangement is additive in position / rotation and multiplicative in scale / opacity, so a
     * copy's delta is the same whatever pose the layer itself is in — including a keyframed one
     * the probe cannot see — and the picture paths stay in lockstep with the overlay path by
     * construction. [timeMs] is the frame time (only the scatter field reads it).
     */
    fun copyDeltas(clip: TimelineClip, timeMs: Long = Long.MIN_VALUE): List<CopyDelta>? {
        anyRepeatOf(clip) ?: return null
        val probe = clip.copy(
            transformX = 0f, transformY = 0f, rotationDeg = 0f, scalePercent = 100f, opacity = 100f
        )
        return copiesOf(probe, 0f, timeMs).map { c ->
            CopyDelta(
                dx = c.transformX, dy = c.transformY, rotDeg = c.rotationDeg,
                scaleMul = c.scalePercent / 100f, opacityMul = (c.opacity / 100f).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * Canvas-space offset for drawing a frame-sized group picture as one repeat copy: the group
     * picture is drawn with the renderer's plain 2D placement (offset = the picture centre's
     * canvas px from the canvas centre, turn / scale about that centre), and a copy must come out
     * as the picture transformed about the group's own anchor — the anchor point ([anchorX],
     * [anchorY], canvas px from the canvas centre) lands on anchor + the copy's offset, and every
     * other point follows by the copy's turn / scale about it:
     * offset = anchor + delta * [k] - R(rot) * S(scale) * anchor (the same folding the layer
     * anim uses for a layer's pivot). [k] = canvas px per preview px.
     */
    fun groupCopyOffset(anchorX: Float, anchorY: Float, d: CopyDelta, k: Float): Pair<Float, Float> {
        val out = FloatArray(2)
        FrameEvalNative.nativeGroupCopyOffset(anchorX, anchorY, d.dx, d.dy, d.rotDeg, d.scaleMul, k, out)
        return Pair(out[0], out[1])
    }

    /** A SplitMix-style integer hash to a uniform float in [0, 1): stable across runs and platforms. */
    private fun hash01(seed: Int, index: Int, salt: Int): Float {
        var z = seed.toLong() * -7046029254386353131L +
            index.toLong() * -4658895280553007687L + salt.toLong() * -7723592293113298319L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293113298319L
        z = z xor (z ushr 31)
        return ((z ushr 40) and 0xFFFFFFL).toFloat() / 16777216f
    }
}
