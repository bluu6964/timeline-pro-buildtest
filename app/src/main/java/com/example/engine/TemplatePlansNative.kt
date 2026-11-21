package com.example.engine

import com.example.model.AnimKey
import com.example.model.AnimProp
import com.example.model.AnimValue
import com.example.model.ClipFx
import com.example.model.Easing
import com.example.model.Keyframe
import com.example.model.LayerType
import com.example.model.ParentLink
import com.example.model.PieOutline
import com.example.model.TextAnimator
import com.example.model.TimelineClip

/**
 * JNI bridge for the C++ template builders (cpp/engine/template_plans.{h,cpp},
 * migration stage 3, build 246): the nine built-in template builders'
 * computations (Carousel, PopTitle, LogoReveal, WordPop, Genie, PieCounter,
 * DonutChart, SocialGrowth, LowerThird), their Spec law functions, and the
 * UserTemplateOps capture / instantiate / media-reference decisions.
 *
 * Following the stage-2 plan pattern, a builder's whole computation runs
 * natively and returns ONE flat construction plan (a little-endian byte
 * buffer): per clip — in lanesTopFirst order, group first — every field the
 * original builder set (fields it left at the TimelineClip defaults are
 * emitted as those defaults, so the record is complete), its keyframe tracks
 * in the original's insertion order, its pie key tracks + the stored wedge
 * angles, its effect type/values and its text animators. This wrapper
 * decodes the plan and assembles the TimelineClip graph: ClipFx instances
 * are constructed here (their default UUID mints here — exactly where the
 * originals minted it), and a pie layer's pathSegments come from the
 * existing PieOutline.normalisedSegments on the plan's stored angles,
 * exactly as the originals called it. The Insert role slicing (which clip is
 * the group / the bar / card 3's photo…) is positional and lives in each
 * model file's buildGroup wrapper.
 *
 * All strings cross as UTF-8 bytes (the templates carry Bengali sample
 * texts) — no lossy conversion anywhere.
 *
 * There is NO Kotlin copy of the migrated laws any more — native is the only
 * path (the post-234 rule): a genuinely missing libam_engine.so fails loudly
 * at the first call. [isAvailable] stays a read-only probe (no throwing
 * init gate).
 */
object TemplatePlansNative {
    init {
        // Trigger DecoderPool's library load on first touch (see ModelLogicNative).
        DecoderPool.isAvailable
    }

    val isAvailable: Boolean get() = try { DecoderPool.isAvailable } catch (_: Throwable) { false }

    private fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

    // ---- builders (each returns a construction plan; see decodePlan) ----

    fun carouselPlan(
        previewWidthPx: Float, previewHeightPx: Float, backgroundUri: String,
        cardUris: List<String>, insertAtMs: Long, idBase: String,
        cardCount: Int, durationMs: Long, clockwise: Boolean
    ): List<TimelineClip> = decodePlan(
        nativeCarouselPlan(
            previewWidthPx, previewHeightPx, utf8(backgroundUri),
            Array(cardUris.size) { utf8(cardUris[it]) },
            insertAtMs, utf8(idBase), cardCount, durationMs, clockwise
        )
    )

    fun popTitlePlan(previewWidthPx: Float, insertAtMs: Long, idBase: String): List<TimelineClip> =
        decodePlan(nativePopTitlePlan(previewWidthPx, insertAtMs, utf8(idBase)))

    fun logoRevealPlan(
        previewWidthPx: Float, logoUri: String, insertAtMs: Long, idBase: String
    ): List<TimelineClip> =
        decodePlan(nativeLogoRevealPlan(previewWidthPx, utf8(logoUri), insertAtMs, utf8(idBase)))

    fun wordPopPlan(previewWidthPx: Float, insertAtMs: Long, idBase: String): List<TimelineClip> =
        decodePlan(nativeWordPopPlan(previewWidthPx, insertAtMs, utf8(idBase)))

    fun geniePlan(
        previewWidthPx: Float, previewHeightPx: Float, avatarUris: List<String>,
        photoUris: List<String>, insertAtMs: Long, idBase: String
    ): List<TimelineClip> = decodePlan(
        nativeGeniePlan(
            previewWidthPx, previewHeightPx,
            Array(avatarUris.size) { utf8(avatarUris[it]) },
            Array(photoUris.size) { utf8(photoUris[it]) },
            insertAtMs, utf8(idBase)
        )
    )

    fun pieCounterPlan(
        previewWidthPx: Float, previewHeightPx: Float, insertAtMs: Long, idBase: String
    ): List<TimelineClip> =
        decodePlan(nativePieCounterPlan(previewWidthPx, previewHeightPx, insertAtMs, utf8(idBase)))

    fun donutPlan(
        previewWidthPx: Float, previewHeightPx: Float, insertAtMs: Long, idBase: String
    ): List<TimelineClip> =
        decodePlan(nativeDonutPlan(previewWidthPx, previewHeightPx, insertAtMs, utf8(idBase)))

    fun socialGrowthPlan(
        previewWidthPx: Float, previewHeightPx: Float, avatarUri: String,
        insertAtMs: Long, idBase: String
    ): List<TimelineClip> = decodePlan(
        nativeSocialGrowthPlan(previewWidthPx, previewHeightPx, utf8(avatarUri), insertAtMs, utf8(idBase))
    )

    fun lowerThirdPlan(
        previewWidthPx: Float, previewHeightPx: Float, insertAtMs: Long, idBase: String
    ): List<TimelineClip> =
        decodePlan(nativeLowerThirdPlan(previewWidthPx, previewHeightPx, insertAtMs, utf8(idBase)))

    // ---- the plan decoder ----

    private class Reader(private val buf: ByteArray) {
        private var pos = 0
        fun u8(): Int = buf[pos++].toInt() and 0xFF
        fun i32(): Int {
            var v = 0
            for (i in 0..3) v = v or ((buf[pos++].toInt() and 0xFF) shl (8 * i))
            return v
        }
        fun i64(): Long {
            var v = 0L
            for (i in 0..7) v = v or ((buf[pos++].toLong() and 0xFF) shl (8 * i))
            return v
        }
        fun f32(): Float = Float.fromBits(i32())
        fun f64(): Double = Double.fromBits(i64())
        fun str(): String {
            val n = i32()
            val s = String(buf, pos, n, Charsets.UTF_8)
            pos += n
            return s
        }
        fun key(): Keyframe = Keyframe(
            timeMs = i64(), a = f32(), b = f32(),
            easing = Easing(f32(), f32(), f32(), f32()), tag = i32()
        )
        fun keys(): List<Keyframe> {
            val n = i32()
            return List(n) { key() }
        }
        fun animators(): List<TextAnimator> {
            val n = i32()
            return List(n) {
                val effectId = str()
                val refSize = f32()
                val propCount = i32()
                val props = LinkedHashMap<String, AnimValue>(propCount)
                repeat(propCount) {
                    val name = str()
                    val type = str()
                    val valueCount = i32()
                    val value = List(valueCount) { f32() }
                    val keyCount = i32()
                    val keys = List(keyCount) {
                        val t = f32()
                        val vc = i32()
                        val v = List(vc) { f32() }
                        val easing = if (u8() != 0) Easing(f32(), f32(), f32(), f32()) else null
                        AnimKey(t, v, easing)
                    }
                    props[name] = AnimValue(type = type, value = value, keys = keys)
                }
                TextAnimator(effectId = effectId, props = props, refSize = refSize)
            }
        }
    }

    /** Decodes a standalone animator blob (the counterAnimator entries). */
    fun decodeAnimators(bytes: ByteArray): List<TextAnimator> = Reader(bytes).animators()

    fun decodePlan(bytes: ByteArray): List<TimelineClip> {
        val r = Reader(bytes)
        val version = r.i32()
        check(version == 1) { "template plan version $version" }
        val metaCount = r.i32()
        repeat(metaCount) { r.i32() }
        val clipCount = r.i32()
        return List(clipCount) { readClip(r) }
    }

    private fun readClip(r: Reader): TimelineClip {
        val flags = r.i32()
        val id = r.str()
        val name = r.str()
        val type = LayerType.values()[r.i32()]
        val startMs = r.i64()
        val durationMs = r.i64()
        val color = r.i64()
        val textColor = r.i64()
        val gradColor1 = r.i64()
        val gradColor2 = r.i64()
        val strokeColor = r.i64()
        val fillMode = r.i32()
        val fillImageMode = r.i32()
        val gradType = r.i32()
        val fontWeight = r.i32()
        val gradX1 = r.f32(); val gradY1 = r.f32(); val gradX2 = r.f32(); val gradY2 = r.f32()
        val shapeScaleX = r.f32(); val shapeScaleY = r.f32(); val shapeCornerRadius = r.f32()
        val strokeWidth = r.f32(); val strokeTrimStart = r.f32(); val strokeTrimEnd = r.f32()
        val transformX = r.f32(); val transformY = r.f32(); val rotationDeg = r.f32()
        val scalePercent = r.f32(); val opacity = r.f32(); val pieRadius = r.f32()
        val text = if (flags and 1 != 0) r.str() else null
        val fillImageUri = if (flags and 2 != 0) r.str() else null
        val shapePattern = if (flags and 4 != 0) r.str() else null
        val groupId = if (flags and 64 != 0) r.str() else null
        val controllerId = if (flags and 128 != 0) r.str() else null
        val parent = if (flags and 8 != 0) {
            ParentLink(
                id = r.str(), refX = r.f32(), refY = r.f32(), refScale = r.f32(), refRot = r.f32(),
                lockScale = flags and 16 != 0, lockRotate = flags and 32 != 0
            )
        } else null
        val trackCount = r.i32()
        val keyframes = LinkedHashMap<AnimProp, List<Keyframe>>(trackCount)
        repeat(trackCount) {
            val prop = AnimProp.values()[r.i32()]
            keyframes[prop] = r.keys()
        }
        val pieStartKeys = r.keys()
        val pieEndKeys = r.keys()
        val hasPieSegs = flags and 512 != 0
        val storedStart = if (hasPieSegs) r.f64() else 0.0
        val storedEnd = if (hasPieSegs) r.f64() else 0.0
        val fxCount = r.i32()
        val effects = List(fxCount) {
            val fxType = r.str()
            val n = r.i32()
            ClipFx(type = fxType, values = List(n) { r.f32() })
        }
        val textAnimators = r.animators()
        return TimelineClip(
            id = id, name = name, type = type, startMs = startMs, durationMs = durationMs,
            color = color, text = text, textColor = textColor, fillMode = fillMode,
            gradType = gradType, gradColor1 = gradColor1, gradColor2 = gradColor2,
            gradX1 = gradX1, gradY1 = gradY1, gradX2 = gradX2, gradY2 = gradY2,
            fillImageUri = fillImageUri, fillImageMode = fillImageMode,
            fontWeight = fontWeight, isItalic = flags and 256 != 0,
            shapePattern = shapePattern, shapeScaleX = shapeScaleX, shapeScaleY = shapeScaleY,
            shapeCornerRadius = shapeCornerRadius, strokeWidth = strokeWidth,
            strokeColor = strokeColor, strokeTrimStart = strokeTrimStart,
            strokeTrimEnd = strokeTrimEnd,
            pathSegments = if (hasPieSegs) {
                PieOutline.normalisedSegments(storedStart, storedEnd)
            } else emptyList(),
            pieStartKeys = pieStartKeys, pieEndKeys = pieEndKeys, pieRadius = pieRadius,
            transformX = transformX, transformY = transformY, rotationDeg = rotationDeg,
            scalePercent = scalePercent, opacity = opacity,
            keyframes = keyframes, effects = effects, textAnimators = textAnimators,
            groupId = groupId, parent = parent, controllerId = controllerId
        )
    }

    // ---- UserTemplateOps packs ----
    //
    // The key pack for a clip list: identity keys for id strings in encounter
    // order (the clips' own ids first, in list order, then each clip's link
    // ids in the field order parent, groupId, controllerId, maskTargetId,
    // trackFollow, cornerPin, planarRemove pin). keyToClip[key] is the index
    // of the clip carrying that id, or -1 when the id names no clip of the
    // list (outside / dangling). has[] carries 7 flags per clip in the order
    // parent, groupId, controllerId, maskTargetId, trackFollow, cornerPin,
    // planarRemove.

    class UserPack(
        val idKey: IntArray,
        val groupKey: IntArray,
        val parentKey: IntArray,
        val controllerKey: IntArray,
        val maskKey: IntArray,
        val tfKey: IntArray,
        val cpKey: IntArray,
        val prKey: IntArray,
        val keyToClip: IntArray,
        val has: BooleanArray,
        val prSelf: BooleanArray,
        val keyById: Map<String, Int>
    )

    fun packUserClips(clips: List<TimelineClip>): UserPack {
        val keyById = HashMap<String, Int>()
        val keyToClip = ArrayList<Int>()
        fun keyOf(id: String?, clipIndex: Int): Int {
            if (id == null) return -1
            keyById[id]?.let { k ->
                // A clip's own id visit refreshes the mapping: the original
                // instantiate builds its id map with associate{}, where the
                // LAST clip carrying an id wins.
                if (clipIndex >= 0) keyToClip[k] = clipIndex
                return k
            }
            val k = keyToClip.size
            keyById[id] = k
            keyToClip.add(clipIndex)
            return k
        }
        val n = clips.size
        val idKey = IntArray(n)
        for (i in 0 until n) idKey[i] = keyOf(clips[i].id, i)
        val groupKey = IntArray(n) { keyOf(clips[it].groupId, -1) }
        val parentKey = IntArray(n) { keyOf(clips[it].parent?.id, -1) }
        val controllerKey = IntArray(n) { keyOf(clips[it].controllerId, -1) }
        val maskKey = IntArray(n) { keyOf(clips[it].maskTargetId, -1) }
        val tfKey = IntArray(n) { keyOf(clips[it].trackFollow?.videoId, -1) }
        val cpKey = IntArray(n) { keyOf(clips[it].cornerPin?.videoId, -1) }
        val prKey = IntArray(n) { keyOf(clips[it].planarRemove?.pin?.videoId, -1) }
        val has = BooleanArray(n * 7)
        val prSelf = BooleanArray(n)
        for (i in 0 until n) {
            val c = clips[i]
            has[i * 7 + 0] = c.parent != null
            has[i * 7 + 1] = c.groupId != null
            has[i * 7 + 2] = c.controllerId != null
            has[i * 7 + 3] = c.maskTargetId != null
            has[i * 7 + 4] = c.trackFollow != null
            has[i * 7 + 5] = c.cornerPin != null
            has[i * 7 + 6] = c.planarRemove != null
            prSelf[i] = c.planarRemove?.pin?.videoId == c.id
        }
        return UserPack(
            idKey, groupKey, parentKey, controllerKey, maskKey, tfKey, cpKey, prKey,
            keyToClip.toIntArray(), has, prSelf, keyById
        )
    }

    fun userMediaRefs(clips: List<TimelineClip>): List<String> {
        val flat = Array(clips.size * 6) { idx ->
            val c = clips[idx / 6]
            val ref = when (idx % 6) {
                0 -> c.uri
                1 -> c.fillImageUri
                2 -> c.fillVideoUri
                3 -> c.planarRemove?.patchPath
                4 -> c.cutoutDir
                else -> c.faceBlurDir
            }
            utf8(ref ?: "")
        }
        val blob = nativeUserMediaRefs(flat, clips.size)
        val r = Reader(blob)
        val count = r.i32()
        return List(count) { r.str() }
    }

    fun userCapturePlan(pack: UserPack, groupId: String): IntArray =
        nativeUserCapturePlan(
            pack.idKey, pack.groupKey, pack.parentKey, pack.controllerKey, pack.maskKey,
            pack.tfKey, pack.cpKey, pack.prKey, pack.keyToClip, pack.has,
            pack.keyById[groupId] ?: -1, pack.prSelf
        )

    fun userInstantiatePlan(pack: UserPack, startMs: LongArray, insertAtMs: Long): LongArray =
        nativeUserInstantiatePlan(
            pack.groupKey, pack.parentKey, pack.controllerKey, pack.maskKey,
            pack.tfKey, pack.cpKey, pack.prKey, pack.keyToClip, pack.has, startMs, insertAtMs
        )

    // ---- Spec laws (used by the Spec objects' delegations) ----

    fun carouselProgress(x: Double): Double = nativeCarouselProgress(x)
    fun carouselRingRadius(cardCount: Int): Float = nativeCarouselRingRadius(cardCount)
    fun carouselFit(pw: Float, ph: Float, radius: Float): FloatArray =
        nativeCarouselFit(pw, ph, radius)
    fun carouselAngleDegAt(tMs: Long, cardIndex: Int, cardCount: Int, durationMs: Long, clockwise: Boolean): Double =
        nativeCarouselAngleDegAt(tMs, cardIndex, cardCount, durationMs, clockwise)
    fun carouselCenterSceneAt(tMs: Long, cardIndex: Int, cardCount: Int, durationMs: Long, clockwise: Boolean): FloatArray =
        nativeCarouselCenterSceneAt(tMs, cardIndex, cardCount, durationMs, clockwise)

    fun wordPopStartMs(index: Int): Long = nativeWordPopStartMs(index)
    fun wordPopAdvancePx(index: Int, pw: Float): Float = nativeWordPopAdvancePx(index, pw)
    fun wordPopTotalWidthPx(pw: Float): Float = nativeWordPopTotalWidthPx(pw)
    fun wordPopCentreXPx(index: Int, pw: Float): Float = nativeWordPopCentreXPx(index, pw)

    fun genieCardStartMs(index: Int): Long = nativeGenieCardStartMs(index)
    fun genieAvatarU(index: Int, pw: Float): Float = nativeGenieAvatarU(index, pw)
    fun genieScrollAt(relMs: Long, pw: Float): Float = nativeGenieScrollAt(relMs, pw)
    fun genieBarPoint(u: Float, v: Float, pw: Float, ph: Float): FloatArray =
        nativeGenieBarPoint(u, v, pw, ph)
    fun genieAvatarPosAt(index: Int, relMs: Long, pw: Float, ph: Float): FloatArray =
        nativeGenieAvatarPosAt(index, relMs, pw, ph)
    fun genieAvatarNamePosAt(index: Int, relMs: Long, pw: Float, ph: Float): FloatArray =
        nativeGenieAvatarNamePosAt(index, relMs, pw, ph)
    fun geniePetalOffset(k: Int, pw: Float): FloatArray = nativeGeniePetalOffset(k, pw)
    fun genieBarEndFlowerCentre(pw: Float, ph: Float): FloatArray =
        nativeGenieBarEndFlowerCentre(pw, ph)
    fun genieCardPoint(lx: Float, ly: Float, pw: Float, ph: Float): FloatArray =
        nativeGenieCardPoint(lx, ly, pw, ph)

    fun piePercentAt(relMs: Long): Float = nativePiePercentAt(relMs)
    fun pieCounterAnimator(): TextAnimator = decodeAnimators(nativePieCounterAnimator()).single()
    fun counterAnimator(keys: List<Pair<Float, Float>>): TextAnimator {
        val flat = FloatArray(keys.size * 2)
        keys.forEachIndexed { i, (t, v) -> flat[i * 2] = t; flat[i * 2 + 1] = v }
        return decodeAnimators(nativeCounterAnimator(flat)).single()
    }

    fun socialTilePos(index: Int, pw: Float, ph: Float): FloatArray =
        nativeSocialTilePos(index, pw, ph)
    fun donutLabelPos(index: Int, pw: Float, ph: Float): FloatArray =
        nativeDonutLabelPos(index, pw, ph)
    fun donutLegendY(index: Int, ph: Float): Float = nativeDonutLegendY(index, ph)
    fun lowerThirdLayout(pw: Float, ph: Float): FloatArray = nativeLowerThirdLayout(pw, ph)

    // ---- JNI ----

    private external fun nativeCarouselPlan(
        pw: Float, ph: Float, backgroundUri: ByteArray, cardUris: Array<ByteArray>,
        insertAtMs: Long, idBase: ByteArray, cardCount: Int, durationMs: Long, clockwise: Boolean
    ): ByteArray
    private external fun nativePopTitlePlan(pw: Float, insertAtMs: Long, idBase: ByteArray): ByteArray
    private external fun nativeLogoRevealPlan(
        pw: Float, logoUri: ByteArray, insertAtMs: Long, idBase: ByteArray
    ): ByteArray
    private external fun nativeWordPopPlan(pw: Float, insertAtMs: Long, idBase: ByteArray): ByteArray
    private external fun nativeGeniePlan(
        pw: Float, ph: Float, avatarUris: Array<ByteArray>, photoUris: Array<ByteArray>,
        insertAtMs: Long, idBase: ByteArray
    ): ByteArray
    private external fun nativePieCounterPlan(
        pw: Float, ph: Float, insertAtMs: Long, idBase: ByteArray
    ): ByteArray
    private external fun nativeDonutPlan(
        pw: Float, ph: Float, insertAtMs: Long, idBase: ByteArray
    ): ByteArray
    private external fun nativeSocialGrowthPlan(
        pw: Float, ph: Float, avatarUri: ByteArray, insertAtMs: Long, idBase: ByteArray
    ): ByteArray
    private external fun nativeLowerThirdPlan(
        pw: Float, ph: Float, insertAtMs: Long, idBase: ByteArray
    ): ByteArray

    private external fun nativeCarouselProgress(x: Double): Double
    private external fun nativeCarouselRingRadius(cardCount: Int): Float
    private external fun nativeCarouselFit(pw: Float, ph: Float, radius: Float): FloatArray
    private external fun nativeCarouselAngleDegAt(
        tMs: Long, cardIndex: Int, cardCount: Int, durationMs: Long, clockwise: Boolean
    ): Double
    private external fun nativeCarouselCenterSceneAt(
        tMs: Long, cardIndex: Int, cardCount: Int, durationMs: Long, clockwise: Boolean
    ): FloatArray

    private external fun nativeWordPopStartMs(index: Int): Long
    private external fun nativeWordPopAdvancePx(index: Int, pw: Float): Float
    private external fun nativeWordPopTotalWidthPx(pw: Float): Float
    private external fun nativeWordPopCentreXPx(index: Int, pw: Float): Float

    private external fun nativeGenieCardStartMs(index: Int): Long
    private external fun nativeGenieAvatarU(index: Int, pw: Float): Float
    private external fun nativeGenieScrollAt(relMs: Long, pw: Float): Float
    private external fun nativeGenieBarPoint(u: Float, v: Float, pw: Float, ph: Float): FloatArray
    private external fun nativeGenieAvatarPosAt(index: Int, relMs: Long, pw: Float, ph: Float): FloatArray
    private external fun nativeGenieAvatarNamePosAt(index: Int, relMs: Long, pw: Float, ph: Float): FloatArray
    private external fun nativeGeniePetalOffset(k: Int, pw: Float): FloatArray
    private external fun nativeGenieBarEndFlowerCentre(pw: Float, ph: Float): FloatArray
    private external fun nativeGenieCardPoint(lx: Float, ly: Float, pw: Float, ph: Float): FloatArray

    private external fun nativePiePercentAt(relMs: Long): Float
    private external fun nativePieCounterAnimator(): ByteArray
    private external fun nativeCounterAnimator(tvPairs: FloatArray): ByteArray

    private external fun nativeSocialTilePos(index: Int, pw: Float, ph: Float): FloatArray
    private external fun nativeDonutLabelPos(index: Int, pw: Float, ph: Float): FloatArray
    private external fun nativeDonutLegendY(index: Int, ph: Float): Float
    private external fun nativeLowerThirdLayout(pw: Float, ph: Float): FloatArray

    private external fun nativeUserMediaRefs(flatRefs: Array<ByteArray>, clipCount: Int): ByteArray
    private external fun nativeUserCapturePlan(
        idKey: IntArray, groupKey: IntArray, parentKey: IntArray, controllerKey: IntArray,
        maskKey: IntArray, tfKey: IntArray, cpKey: IntArray, prKey: IntArray,
        keyToClip: IntArray, has: BooleanArray, groupKeyOf: Int, prSelf: BooleanArray
    ): IntArray
    private external fun nativeUserInstantiatePlan(
        groupKey: IntArray, parentKey: IntArray, controllerKey: IntArray, maskKey: IntArray,
        tfKey: IntArray, cpKey: IntArray, prKey: IntArray, keyToClip: IntArray,
        has: BooleanArray, startMs: LongArray, insertAtMs: Long
    ): LongArray
}
