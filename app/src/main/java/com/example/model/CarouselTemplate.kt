package com.example.model

import com.example.engine.TemplatePlansNative

/**
 * The Carousel template (Templates v1, build 142; in-project group model, build 143): 8 photo
 * cards on a rotating ring, matching Sarowar's reference video (720x450, 30 fps, 5.03 s) as
 * measured frame by frame on 2026-11-13 (frames + fit scripts in
 * ~/workspace/after-motion-dev/carousel-ref/):
 *
 *  - exactly ONE revolution per 5.0 s, seamless loop (reference frame 150 == frame 0);
 *  - direction clockwise on screen (a card travels left -> top -> right -> bottom);
 *  - angular law = cubic-bezier(0.78, 0.013, 0.194, 1.0) over the loop (least-squares fit on the
 *    tracked yellow card: RMS 0.38 deg; symmetric easings were 6-20x worse);
 *  - ring centre (364, 224) of 720x450, radius 133.2 px; cards 95x95 px, corner radius 14 px,
 *    upright at all times, constant scale (no depth, no breathing), never overlapping
 *    (adjacent centres 101.8 px apart > the 95 px card);
 *  - shadow: barely there in the reference (<= ~4% luminance dip within ~2 px of the bottom
 *    edge) — modelled as a soft dark twin layer 2.5 px below each card at 16% opacity;
 *  - background: vertical gradient (210,210,210) -> (147,146,151) with a 34 px grid lifted
 *    +8 luminance (vertical lines at x = 19 mod 34, horizontal at y = 18 mod 34).
 *
 * Build 143 (Sarowar's correction of the build-142 model): a template is NOT a separate
 * project. It lives inside the project like any layer — inserting it drops ONE Group layer
 * into the current timeline (see [buildGroup]) holding the whole composition as ordinary
 * children, authored in the template's own units in the scene-authoring convention the
 * project model uses (positions (px - centre) x previewW/720, shape box 260 scene px,
 * scalePercent = shapeScale x 100, corner = 65 x radius/half-extent). The children link to the group with
 * the REST reference pose, so the group's own transform IS the fit: [carouselFit] centres
 * the ring on the project canvas and scales it to fit, and ParentRig composes that onto
 * every child — the measured law runs in template units whatever the canvas, and moving /
 * scaling / rotating the one group layer moves the whole carousel. Because the Compose
 * preview and the native export both evaluate [TimelineClip.at] and compose parents through
 * the same ParentRig maths, they draw the identical motion.
 *
 * Build 144 (Sarowar's second correction, after inspecting build 143): the motion is NOT
 * baked into the children any more. Build 142/143 baked ~101 POSITION keys into each of the
 * 16 card/shadow layers — visually right, but the user had no single control over the
 * animation. The group therefore carries a rig layer on the ring centre holding the ENTIRE
 * motion as one ROTATION track: 0 -> 360 degrees over the 5 s loop, keyed every
 * [CarouselSpec.STEP_MS] with each segment carrying the renormalised slice of the measured
 * bezier — the ring's angular law, transferred verbatim to the rig layer's rotation.
 * Every card and shadow is parented to it (ParentRig) at its fixed t=0 ring pose with
 * NO keyframes of its own; the link's ref is the rig layer's rest pose (the ring centre)
 * and the link is rotation-LOCKED (the Parent Helper mechanism): ParentRig then swings
 * the layer's position around the centre by the rig's rotation while the layer's own
 * rotation never inherits it — the cards ride the ring and stay upright by rig
 * construction, and editing the rig layer's rotation track (values, timing, easing)
 * re-times / re-shapes the whole carousel at once.
 *
 * Build 145 (Sarowar's refinement of the driver, same day) briefly made the rig layer a
 * CONTROLLER that cascaded its values through ParentRig. Build 146 (his correction after
 * testing 145: "কন্ট্রোলার তো null er মতন আচরণ করছে" — the Controller behaved like a Null)
 * splits the two jobs by his principle "যেটার কাজ যেখানে সেটা সেখানে রাখবে" — each tool
 * stays where its job is, and nothing unneeded is kept:
 *  - the ORBIT is a transform-driving job, so it belongs to the NULL: the build-144 rig
 *    stands exactly as it was ("Carousel Null", one rotation track, rotation-locked cards).
 *  - the MULTI-EDIT job belongs to the CONTROLLER: the group also holds a "Carousel
 *    Controller", connected to the 16 card/shadow layers purely as their fan-out edit
 *    handle (see [com.example.model.ControllerFanOut]) — neutral values at insert, no
 *    keyframes, no parenting, no driving. Editing it (move / turn / scale / opacity /
 *    blend / effects) rewrites each connected card/shadow in its own place; the Null and
 *    the Background are not connected to it and are never touched by it.
 *
 * One deliberate nuance vs the build-142/143 bake: a shadow twin's 2.5 px offset rotates
 * WITH the ring (its local offset from the null is fixed, so the offset direction turns
 * and its distance from its card stays exactly 2.5 px), where the bake kept the offset
 * pointing straight down the screen. A screen-fixed offset cannot be expressed by a static
 * local pose under a rotating parent; the twin is a 16%-opacity, <=4%-luminance effect, and
 * the rotating offset is what a real Alight rig produces. Card centres, orientation, scale,
 * the background and the law itself are unchanged to the pixel.
 *
 * Each card is a SHAPE layer (ROUNDED_RECT) with an image fill — the app's existing replace
 * path (select card -> Color -> Fill -> Image) swaps the picture, exactly like any shape
 * fill.
 *
 * Build 154 (Templates v2 — the parameterised insert): the reference's numbers were the
 * template's only settings in builds 142-146; the insert now takes a [CarouselParams] —
 * card count 4-12, one revolution in 2-10 s, clockwise or counter-clockwise. The defaults
 * ARE the reference (8 cards, 5 s, clockwise) and a default insert is byte-identical to
 * build 146's. The geometry generalises by one law: the ring radius stays the measured
 * 133.2 while the cards fit it, and grows only when a count would crowd them —
 * r(N) = max(133.2, (95 x 1.04 / 2) / sin(pi / N)) — so adjacent centres always keep the
 * card size plus a 4% gap (the fit rule then scales the bigger ring into frame like any
 * other content). Everything else scales mechanically: phases spread over the full turn,
 * the null's single rotation track spans the chosen duration with the same measured
 * bezier (negated for counter-clockwise), the group spans the duration, and the
 * Controller's connection set is the 2 x cardCount cards/shadows.
 */
object CarouselSpec {
    const val SCENE_W = 720f
    const val SCENE_H = 450f
    const val CENTER_X = 364f
    const val CENTER_Y = 224f
    const val RADIUS = 133.2f
    const val CARD_SIZE = 95f
    const val CARD_CORNER = 14f
    const val CARD_COUNT = 8
    const val DURATION_MS = 5000L

    /** The insert-time settings' ranges (build 154): 4-12 cards, one turn in 2-10 s. */
    const val MIN_CARDS = 4
    const val MAX_CARDS = 12
    const val MIN_DURATION_MS = 2000L
    const val MAX_DURATION_MS = 10000L

    /** Ring clearance law: adjacent card centres keep the card size x this factor. */
    const val RING_GAP = 1.04f
    const val STEP_MS = 50L
    const val SHADOW_DY = 2.5f
    const val SHADOW_OPACITY = 16f

    /** The measured angular easing, cubic-bezier(0.78, 0.013, 0.194, 1.0). */
    const val EASE_X1 = 0.78
    const val EASE_Y1 = 0.013
    const val EASE_X2 = 0.194
    const val EASE_Y2 = 1.0

    val BG_TOP: Long = 0xFFD2D2D2L        // (210, 210, 210)
    val BG_BOTTOM: Long = 0xFF939297L     // (147, 146, 151)
    val CANVAS_COLOR: Long = 0xFFB2B2B4L  // gradient mid (178, 178, 180)
    const val GRID_STEP = 34f
    const val GRID_OFF_X = 19f
    const val GRID_OFF_Y = 18f
    const val GRID_LIFT = 8

    /** Solid colour under each card's picture (shown while / if the picture cannot load). */
    val CARD_TINTS: List<Long> = listOf(
        0xFFEF5350L, 0xFFFFA726L, 0xFFFFEE58L, 0xFF66BB6AL,
        0xFF42A5F5L, 0xFF7E57C2L, 0xFFEC407AL, 0xFF26A69AL
    )
}

/**
 * The Carousel's insert-time settings (build 154). The defaults are the reference video's
 * own numbers — 8 cards, one revolution per 5 s, clockwise — and a default insert builds
 * exactly the group builds 142-146 built.
 */
data class CarouselParams(
    val cardCount: Int = CarouselSpec.CARD_COUNT,
    val durationMs: Long = CarouselSpec.DURATION_MS,
    val clockwise: Boolean = true
) {
    init {
        require(cardCount in CarouselSpec.MIN_CARDS..CarouselSpec.MAX_CARDS) {
            "carousel card count $cardCount outside ${CarouselSpec.MIN_CARDS}..${CarouselSpec.MAX_CARDS}"
        }
        require(durationMs in CarouselSpec.MIN_DURATION_MS..CarouselSpec.MAX_DURATION_MS) {
            "carousel duration $durationMs outside ${CarouselSpec.MIN_DURATION_MS}..${CarouselSpec.MAX_DURATION_MS}"
        }
    }
}

object CarouselTemplate {

    // The measured bezier law, the ring geometry and the fit are native since
    // migration stage 3 (cpp/engine/template_plans.cpp, via
    // TemplatePlansNative); these delegations keep the public law API the
    // tests and the Templates dialog's preview read. The native side runs
    // the same Double bisection / de Casteljau slicing the Kotlin original
    // ran, and the stage-3 golden proves the outputs byte-identical.

    /** Loop progress 0..1 at loop fraction [x] 0..1, per the measured easing. */
    fun progress(x: Double): Double = TemplatePlansNative.carouselProgress(x)

    /**
     * The ring radius (scene px) for [cardCount] cards: the measured [CarouselSpec.RADIUS]
     * while the cards fit it, grown only when a count would crowd them — adjacent centres
     * sit a chord 2 r sin(pi / N) apart, and the chord must keep the card size x
     * [CarouselSpec.RING_GAP] (at N = 8 the chord is 101.8 > 95 x 1.04 = 98.8, so the
     * measured ring stands; N = 9 is the first count that grows it, N = 12 needs 190.9).
     */
    fun ringRadius(cardCount: Int): Float = TemplatePlansNative.carouselRingRadius(cardCount)

    /** Ring angle (degrees, screen-clockwise) of card [cardIndex] at loop time [tMs]. */
    fun angleDegAt(tMs: Long, cardIndex: Int, params: CarouselParams = CarouselParams()): Double =
        TemplatePlansNative.carouselAngleDegAt(
            tMs, cardIndex, params.cardCount, params.durationMs, params.clockwise
        )

    /** Card centre in reference scene pixels (720x450) at loop time [tMs]. */
    fun centerSceneAt(
        tMs: Long, cardIndex: Int, params: CarouselParams = CarouselParams()
    ): Pair<Float, Float> {
        val r = TemplatePlansNative.carouselCenterSceneAt(
            tMs, cardIndex, params.cardCount, params.durationMs, params.clockwise
        )
        return r[0] to r[1]
    }

    /**
     * The fit of the template onto a project canvas of [previewWidthPx] x [previewHeightPx]
     * stored units: the Group layer's (transformX, transformY, scalePercent / 100).
     *
     * The children are authored against the template frame (720x450 at [CarouselSpec]'s own
     * basis). ParentRig composes a child pose as groupPos + (childPos - refPos) x fit with
     * the link's ref at the REST pose (0, 0), so the ring centre — which sits at stored
     * ((364 - 360) k, (224 - 225) k) with k = previewWidthPx / 720 — lands on the canvas
     * centre when groupPos = -(ring centre) x fit, and the whole composition scales by the
     * fit. The fit fills 96% of the tighter canvas dimension with the CONTENT: the template
     * frame, or — for the grown rings of build 154's high card counts — the ring itself
     * (radius + half a card around the centre), whichever reaches further on each axis:
     * fit = 0.96 x min(canvasW / contentW, canvasH / contentH) / k. At the default radius
     * the frame dominates both axes (133.2 + 47.5 = 180.7 < 225 half-height) and the fit is
     * exactly builds 142-146's; a grown ring shrinks the fit just enough that it, not the
     * frame, fills the 96%.
     */
    fun carouselFit(
        previewWidthPx: Float, previewHeightPx: Float, radius: Float = CarouselSpec.RADIUS
    ): Triple<Float, Float, Float> {
        val r = TemplatePlansNative.carouselFit(previewWidthPx, previewHeightPx, radius)
        return Triple(r[0], r[1], r[2])
    }

    /** What [buildGroup] hands back: the Group layer, the rig's Null layer, the edit handle's
     *  Controller layer, its children, and the lanes to add. */
    data class Insert(
        val group: TimelineClip,
        /** The "Carousel Null" inside the group: the one layer carrying the whole motion. */
        val nullLayer: TimelineClip,
        /** The "Carousel Controller" inside the group: the fan-out edit handle the 16
         *  card/shadow layers are connected to (neutral at insert; it drives nothing). */
        val controller: TimelineClip,
        /** The layers inside the group in lane order (top lane first): the Null, the
         *  Controller, then per card the photo lane above its shadow lane, the Background
         *  last (build 142's track order for the 17 picture layers, with the two rig lanes
         *  on top of them). */
        val children: List<TimelineClip>,
        /** Lanes to place on top of the project, top lane first: the group, then [children]. */
        val lanesTopFirst: List<TimelineClip>
    )

    /**
     * The template as ONE Group layer + its children, ready to insert into the project that is
     * open (build 143 — the build-142 standalone-project builder is gone; build 144 — the
     * null rig; build 146 — the Controller alongside it as the edit handle). [previewWidthPx] /
     * [previewHeightPx] are the editor preview frame's size (the stored-units basis, like
     * the importer); [backgroundUri] / [cardUris] point at the generated background /
     * placeholder pictures (engine TemplateAssets); the group starts at [insertAtMs] (the
     * playhead) and spans [params.durationMs] (the template's 5 s at the defaults). Every call builds fresh clips under [idBase] —
     * inserting twice gives two independent carousels, and nothing shared is ever mutated.
     *
     * The rig (build 144, restored verbatim by build 146): the group's first child is a
     * Null layer on the ring centre carrying the ONLY keyframes of the composition — the
     * ROTATION track of [rotationKeys]. Cards and shadows link to it with a rotation-locked
     * [ParentLink] whose ref is the null's rest pose (the ring centre, scale 100, rotation
     * 0): ParentRig composes a card as group-fit ∘ (null pose) ∘ (card's fixed offset from
     * the centre, swung by the null's rotation), which is exactly build 143's baked world
     * path, and the lock keeps every card's own rotation at 0 while its position rides.
     * Beside it sits the "Carousel Controller" (build 146): an ordinary Controller layer of
     * the group, neutral (no keyframes, opacity 100, blend Normal, no effects), with the 16
     * cards/shadows CONNECTED to it ([TimelineClip.controllerId]) — their fan-out edit
     * handle and nothing more. The background stays a static child of the group, connected
     * to nothing, as before.
     */
    fun buildGroup(
        previewWidthPx: Float,
        previewHeightPx: Float,
        backgroundUri: String,
        cardUris: List<String>,
        insertAtMs: Long,
        idBase: String = "carousel",
        params: CarouselParams = CarouselParams()
    ): Insert {
        require(cardUris.size == params.cardCount) {
            "carousel needs ${params.cardCount} card pictures"
        }
        // Native since migration stage 3: the whole construction (the fit,
        // the null's sliced-bezier rotation track, the ring poses, the
        // Controller wiring) is computed in template_plans.cpp and comes back
        // as one construction plan in lanesTopFirst order — group, null,
        // controller, per card (card, shadow), background.
        val clips = TemplatePlansNative.carouselPlan(
            previewWidthPx, previewHeightPx, backgroundUri, cardUris,
            insertAtMs, idBase, params.cardCount, params.durationMs, params.clockwise
        )
        val group = clips[0]
        val nullClip = clips[1]
        val controllerClip = clips[2]
        val children = clips.subList(1, clips.size)
        val lanesTopFirst = clips
        return Insert(group, nullClip, controllerClip, children, lanesTopFirst)
    }
}
