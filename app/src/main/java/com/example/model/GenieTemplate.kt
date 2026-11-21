package com.example.model

import com.example.engine.TemplatePlansNative

/**
 * The "Genie" template (Templates programme, reference commission 2026-11-13): Sarowar's
 * uploaded reference video (720x1280, 60 fps, 16.04 s) — a small white pill with a dark
 * flower mark sits centre-frame on black; a blue-white light streak smears it sideways
 * and it stretches into a long white bar, tilted about 20 degrees, as the background
 * crossfades to light grey; the bar carries a row of circular photo avatars with name
 * labels that scrolls slowly sideways; one by one (Liam, Noah, Emma, Chloe) a big tilted
 * profile card pops up — full-bleed photo, huge name, a dark "Genie suggests" pill with
 * an activity line and an Invite pill — while the featured person's avatar carries a
 * selection ring; at the end the cards are gone, the background fades back to black and
 * the bar shrinks back into the pill, so the piece loops. The full measured analysis is
 * ~/workspace/after-motion-dev/genie-ref/SPEC.md.
 *
 * Like the other templates, inserting it drops ONE Group layer into the current timeline
 * at the playhead (see [buildGroup]) and everything inside is ordinary layers the user
 * edits in place: photos are image-filled shapes replaced via Color -> Fill -> Image,
 * every label / name / activity line is a plain text layer, all motion is ordinary
 * keyframes. There is NO rig (no Controller, no Null — nothing here has their job), and
 * the group's own transform is a pure user control on top of the authored design.
 *
 * Geometry (fractions of the preview width [pw] unless noted; vertical anchors that the
 * video holds against the frame height ride [ph]): the pill is 0.203 x 0.098 of the
 * width at the exact frame centre (measured on the reference: bbox x 0.397..0.600,
 * y 0.472..0.527 of the 720x1280 frame, and the first and last frames' bboxes are
 * identical — the loop closes on the same pixels). The pill and the bar are ONE shape:
 * a rounded rect whose SCALE track runs (56.2%, 27.1%) -> (254.8%, 36.0%) — the corner
 * radius is rho = 1, so at pill size it reads as a capsule and at bar size as a
 * stadium-ended bar, exactly the video's morph. The bar settles at centre + 0.03 x ph,
 * tilted +20 degrees (the app's positive rotation is screen-clockwise: the bar's right
 * end sits lower, as in the video). Avatar positions are computed in the bar's own
 * tilted frame ([GenieSpec.barPoint]); the avatar circles themselves stay upright (the
 * faces in the video do not tilt) while their name labels carry the bar's tilt.
 *
 * DEVIATIONS from the video, honestly (also in ROADMAP): (1) the cards' 3D perspective
 * swing is approximated with real ROT_X / ROT_Y keyframes (the engine does support
 * keyframed 3D rotation end to end) plus the 2D tilt — each card element rotates about
 * its OWN centre, because the card's parts are independent layers sharing one track
 * design (a rigid per-card subgroup is not something the group model offers); (2) the
 * light streak is a radial-gradient shape carrying the panel's own Glow and Directional
 * Blur effects — the smear is an effect stack on an ordinary layer, not a bespoke
 * render; (3) the flower mark is built from 8 small ellipse petals (no font glyph is
 * guaranteed to carry the video's asterisk-flower on every phone) — the same petal
 * flower sits on the pill, travels (invisible) to the bar's right end while the bar is
 * up, and returns for the outro; (4) the video crossfades the background black -> grey
 * between about 1.8 s and 2.8 s and back at the tail — carried here by FILL_COLOR keys
 * on the background shape.
 */
object GenieSpec {
    const val DURATION_MS = 16000L

    // ---- timing (ms from the template's start) ----
    const val STRETCH_START_MS = 1000L
    const val STRETCH_END_MS = 2100L
    const val SCROLL_START_MS = 2600L
    const val SCROLL_END_MS = 13500L
    const val SHRINK_START_MS = 13800L
    const val SHRINK_END_MS = 15250L
    const val CARD_FIRST_MS = 3500L
    const val CARD_CYCLE_MS = 2500L
    const val CARD_ENTER_MS = 550L
    const val CARD_EXIT_START_MS = 2000L

    /** Card [index]'s window start (0 = Liam, 1 = Noah, 2 = Emma, 3 = Chloe). */
    fun cardStartMs(index: Int): Long = TemplatePlansNative.genieCardStartMs(index)

    // ---- colours ----
    const val BG_BLACK = 0xFF000000L
    const val BG_GREY = 0xFFE9E9ECL
    const val BAR_WHITE = 0xFFFFFFFFL
    const val BAR_SHADOW = 0xFF8E8E96L
    const val FLOWER_DARK = 0xFF202024L
    const val NAME_DARK = 0xFF2B2B30L
    const val SUGGEST_PILL = 0xB31E1E24L
    const val INVITE_PILL = 0x40FFFFFFL
    const val ACTIVITY_GREY = 0xFFD8D8DEL

    // ---- bar / pill geometry (fractions of pw; the bar centre rides ph) ----
    const val BAR_W_FRAC = 0.92f
    const val BAR_H_FRAC = 0.13f
    const val PILL_W_FRAC = 0.203f
    const val PILL_H_FRAC = 0.098f
    const val BAR_CY_FRAC = 0.03f          // of ph, below the canvas centre
    const val BAR_TILT_DEG = 20f
    const val BAR_CORNER = 65f             // rho = 1: capsule / stadium at any size

    // ---- avatars ----
    val NAMES = listOf("Me", "Liam", "Noah", "Emma", "Chloe", "Steph")
    const val AVATAR_D_FRAC = 0.115f
    const val AVATAR_STEP_FRAC = 0.138f
    const val NAME_V_FRAC = 0.088f         // name label centre below the bar axis
    const val NAME_FONT_FRAC = 0.030f
    const val SCROLL_FRAC = 0.138f   // one avatar step; the drift is centred on the row
    // (scroll runs -travel/2 -> +travel/2) so the row never slides off the bar's ends —
    // the video's row stays on the bar for the whole piece.
    const val AVATAR_IN_FIRST_MS = 1900L
    const val AVATAR_IN_STAGGER_MS = 90L
    const val AVATAR_IN_MS = 350L
    const val AVATARS_OUT_START_MS = 13800L
    const val AVATARS_OUT_END_MS = 14400L

    /** Avatar [index]'s bar-axis coordinate (u) at scroll 0, in stored px. */
    fun avatarU(index: Int, pw: Float): Float = TemplatePlansNative.genieAvatarU(index, pw)

    /** The row's scroll offset (px) at [relMs]: -SCROLL_FRAC/2 x pw until SCROLL_START,
     *  linear to +SCROLL_FRAC/2 x pw at SCROLL_END (the drift centred on the row). */
    fun scrollAt(relMs: Long, pw: Float): Float = TemplatePlansNative.genieScrollAt(relMs, pw)

    /**
     * Frame position (stored px from the canvas centre) of the bar-local point
     * ([u] along the bar's axis, [v] across it), in the bar's settled pose.
     */
    fun barPoint(u: Float, v: Float, pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.genieBarPoint(u, v, pw, ph)
        return r[0] to r[1]
    }

    /** Avatar [index]'s centre at [relMs] (scroll applied), in stored px from the canvas centre. */
    fun avatarPosAt(index: Int, relMs: Long, pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.genieAvatarPosAt(index, relMs, pw, ph)
        return r[0] to r[1]
    }

    /** Avatar [index]'s name-label centre at [relMs]. */
    fun avatarNamePosAt(index: Int, relMs: Long, pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.genieAvatarNamePosAt(index, relMs, pw, ph)
        return r[0] to r[1]
    }

    // ---- selection rings ----
    val RING_COLORS = listOf(0xFFE53935L, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL)
    const val RING_D_FRAC = 0.13f
    const val RING_STROKE_FRAC = 0.0075f   // of pw, rendered

    // ---- the flower mark (8 ellipse petals) ----
    const val PETALS = 8
    const val PETAL_RING_FRAC = 0.0145f    // petal centres' distance from the flower centre
    const val PETAL_W_FRAC = 0.013f
    const val PETAL_H_FRAC = 0.026f
    const val BAR_END_U_FRAC = 0.428f      // the flower's bar-local u at the bar's right end

    /** Petal [k]'s centre offset from the flower centre (flower untilted), stored px. */
    fun petalOffset(k: Int, pw: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.geniePetalOffset(k, pw)
        return r[0] to r[1]
    }

    /** The flower centre at the bar's right end (bar settled), stored px from the canvas centre. */
    fun barEndFlowerCentre(pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.genieBarEndFlowerCentre(pw, ph)
        return r[0] to r[1]
    }

    // ---- cards ----
    val CARD_TITLES = listOf("LIAM", "NOAH", "EMMA", "CHLOE")
    val CARD_NAMES = listOf("Liam", "Noah", "Emma", "Chloe")
    val ACTIVITIES = listOf(
        "Scenic walk together",
        "Birdwatching with Noah",
        "Picnic by the lake",
        "Eating tapas at The Ranch"
    )
    const val SUGGESTS = "Genie suggests"
    const val INVITE = "Invite"
    const val CARD_CX_FRAC = 0.02f         // of pw
    const val CARD_CY_FRAC = -0.05f        // of ph
    const val CARD_TILT_DEG = -16f
    const val PHOTO_W_FRAC = 0.60f         // of pw
    const val PHOTO_H_FRAC = 0.42f         // of ph
    const val PHOTO_CORNER_FRAC = 0.030f   // of pw (frame-space radius at rest)
    const val TITLE_FONT_FRAC = 0.150f
    const val TITLE_X_FRAC = -0.03f
    const val TITLE_Y_FRAC = -0.115f       // of ph
    const val PILL_CX_FRAC = -0.055f
    const val PILL_CY_FRAC = 0.150f        // of ph
    const val SUGGEST_W_FRAC = 0.44f
    const val SUGGEST_H_FRAC = 0.050f      // of ph
    const val FLOWER_CX_FRAC = -0.245f
    const val CARD_PETAL_RING_FRAC = 0.0095f
    const val CARD_PETAL_W_FRAC = 0.009f
    const val CARD_PETAL_H_FRAC = 0.018f
    const val TEXT_X_FRAC = -0.115f
    const val SUGGEST_FONT_FRAC = 0.0255f
    const val SUGGEST_Y_FRAC = 0.1375f     // of ph
    const val ACTIVITY_FONT_FRAC = 0.022f
    const val ACTIVITY_Y_FRAC = 0.1625f    // of ph
    const val INVITE_CX_FRAC = 0.212f
    const val INVITE_W_FRAC = 0.125f
    const val INVITE_FONT_FRAC = 0.026f

    /** A point in the card's local frame -> frame position (card settled at its tilt). */
    fun cardPoint(lx: Float, ly: Float, pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.genieCardPoint(lx, ly, pw, ph)
        return r[0] to r[1]
    }

    // The app's own easing presets (Easing.PRESETS), copied by value like the other templates.
    val EASE_CUBIC_OUT = Easing(0.215f, 0.61f, 0.355f, 1f)
    val EASE_CUBIC_IN = Easing(0.55f, 0.055f, 0.675f, 0.19f)
    val EASE_CUBIC_IN_OUT = Easing(0.645f, 0.045f, 0.355f, 1f)
    val EASE_BACK_OUT = Easing(0.175f, 0.885f, 0.32f, 1.2f)
}

object GenieTemplate {

    /** One profile card's layers (front-to-back inside the subtree as listed in [all]). */
    data class Card(
        val inviteText: TimelineClip,
        val invitePill: TimelineClip,
        val activityText: TimelineClip,
        val suggestsText: TimelineClip,
        val petals: List<TimelineClip>,
        val suggestPill: TimelineClip,
        val title: TimelineClip,
        val photo: TimelineClip,
        /** The subtree's layers in lane order (top lane first). */
        val all: List<TimelineClip>
    )

    /** What [buildGroup] hands back: the Group layer, the named parts, and the lanes. */
    data class Insert(
        val group: TimelineClip,
        val background: TimelineClip,
        val bar: TimelineClip,
        val barShadow: TimelineClip,
        val streak: TimelineClip,
        /** The pill/bar flower: 8 ellipse petals, one ordinary shape layer each. */
        val petals: List<TimelineClip>,
        /** The 6 circular photo slots, in bar order (Me, Liam, Noah, Emma, Chloe, Steph). */
        val avatars: List<TimelineClip>,
        /** The 6 name labels under the avatars, same order. */
        val avatarNames: List<TimelineClip>,
        /** The 4 selection rings (on Liam, Noah, Emma, Chloe), in card order. */
        val rings: List<TimelineClip>,
        /** The 4 profile cards (Liam, Noah, Emma, Chloe). */
        val cards: List<Card>,
        /** Every layer inside the group in lane order (top lane first). */
        val children: List<TimelineClip>,
        /** Lanes to place on top of the project, top lane first: the group, then [children]. */
        val lanesTopFirst: List<TimelineClip>
    )

    /**
     * The template as ONE Group layer + its 88 ordinary layers, ready to insert into the
     * project that is open. [previewWidthPx] / [previewHeightPx] are the editor preview
     * frame's size (the stored-units basis, like the importer's); the group starts at
     * [insertAtMs] (the playhead) and spans [GenieSpec.DURATION_MS]. [avatarUris] (6) and
     * [photoUris] (4) feed the slots' image fills (generated placeholders; the user
     * replaces them via Color -> Fill -> Image). Every call builds fresh clips under
     * [idBase] — inserting twice gives two independent pieces.
     */
    fun buildGroup(
        previewWidthPx: Float,
        previewHeightPx: Float,
        avatarUris: List<String>,
        photoUris: List<String>,
        insertAtMs: Long,
        idBase: String = "genie"
    ): Insert {
        require(avatarUris.size == GenieSpec.NAMES.size) { "genie needs ${GenieSpec.NAMES.size} avatar pictures" }
        require(photoUris.size == GenieSpec.CARD_TITLES.size) { "genie needs ${GenieSpec.CARD_TITLES.size} card photos" }
        // Native since migration stage 3: the whole construction (the bar
        // morph, the streak, the petals, the scrolling avatars, the rings,
        // the four cards) is computed in template_plans.cpp and comes back
        // as one plan in lanesTopFirst order — group, the four cards' 15
        // layers each (in Card.all order), the rings, the avatar names, the
        // avatars, the streak, the petals, the bar, its shadow, the
        // background.
        val clips = TemplatePlansNative.geniePlan(
            previewWidthPx, previewHeightPx, avatarUris, photoUris, insertAtMs, idBase
        )
        val group = clips[0]
        val cards = (0 until 4).map { i ->
            val all = clips.subList(1 + 15 * i, 16 + 15 * i)
            Card(
                inviteText = all[0], invitePill = all[1], activityText = all[2],
                suggestsText = all[3], petals = all.subList(4, 12), suggestPill = all[12],
                title = all[13], photo = all[14], all = all
            )
        }
        val rings = clips.subList(61, 65)
        val avatarNames = clips.subList(65, 71)
        val avatars = clips.subList(71, 77)
        val streak = clips[77]
        val petals = clips.subList(78, 86)
        val bar = clips[86]
        val barShadow = clips[87]
        val background = clips[88]
        val children = clips.subList(1, clips.size)
        val lanesTopFirst = clips
        return Insert(
            group, background, bar, barShadow, streak, petals, avatars, avatarNames,
            rings, cards, children, lanesTopFirst
        )
    }
}
