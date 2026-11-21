package com.example.model

import com.example.engine.TemplatePlansNative

/**
 * The "Logo Reveal" template (Templates programme, logo-animation template #1): a logo
 * badge pops in at the top, the brand name rises in under it, and the composition then
 * HOLDS — a logo reveal is an end card, so there is deliberately no exit: the tail is
 * static and the user extends the group for as long as the reveal should stay on screen.
 *
 * Like the Carousel and the Pop Title, a template lives inside the project: inserting it
 * drops ONE Group layer into the current timeline at the playhead (see [buildGroup]) and
 * the user customises it in place with the ordinary tools. Like the Pop Title there is no
 * fixed internal scene and no rig — the composition is authored directly against the live
 * preview size, every size a fraction of the preview width (the text engine's font is 11%
 * of the frame width, so width is the unit that keeps the design proportional on any
 * canvas), and the group itself stays at the REST pose: moving / scaling / rotating the
 * one group layer moves the whole reveal. Per Sarowar's principle ("যেটার কাজ যেখানে
 * সেটা সেখানে রাখবে") only what has a job is kept — no Controller, no Null: the logo slot
 * shows the brand mark, the text layer names it, the keyframes perform.
 *
 * The slots (both ordinary layers):
 *  - the LOGO: a ROUNDED_RECT shape layer with an image fill (FILL_IMAGE / IMG_FILL) fed
 *    by a picture generated at run time (see
 *    [com.example.engine.TemplateAssets.logoBitmap] — abstract art of our own, never a
 *    real brand). It is a slot exactly like a Carousel card: tap it, then
 *    Color → Fill → Image and pick the real logo.
 *  - the BRAND NAME: a PLAIN text layer under the logo — the sample ("আপনার ব্র্যান্ড") is
 *    a normal [TimelineClip.text], so the app's existing Bengali shaping applies
 *    unchanged, and the animation rides on the layer's own keyframes: retyping the text
 *    keeps the animation working, because nothing about it depends on the words.
 *
 * The performance (3 s, all ordinary keyframes with the app's own easing presets):
 *  - logo, 0..500 ms: SCALE from 60% of its rest size to rest on the "Back out" preset
 *    (the curve overshoots to ~102% mid-flight and settles — the pop) and OPACITY
 *    0 -> 100 (Cubic out);
 *  - brand text, 350..800 ms: POSITION rises from [LogoRevealSpec.TEXT_RISE_FRAC] of the
 *    width below its rest place (Cubic out) as OPACITY fades 0 -> 100 (Cubic out) — it
 *    starts just as the logo is landing and settles just after;
 *  - 800..3000 ms: hold — every track is at its rest value, nothing moves, so stretching
 *    the group longer just holds the end card.
 * The logo's stored size uses the app's shape convention (the 130 dp box measured against
 * a 360 dp-wide canvas: box = previewWidth x 130/360 in stored units, the importer's own
 * basis), and its SCALE keyframes carry absolute scale percents against that box with a
 * zero height share — the uniform meaning, so the badge grows evenly on both axes.
 */
object LogoRevealSpec {
    const val DURATION_MS = 3000L
    /** The logo's pop (scale + fade) runs inside this window. */
    const val LOGO_ENTER_MS = 500L
    /** The brand text starts rising once the logo is landing… */
    const val TEXT_START_MS = 350L
    /** …and has fully settled by here; everything after is the static hold. */
    const val TEXT_END_MS = 800L

    /** The sample brand name. A normal text clip's text: retype it and the animation follows. */
    const val SAMPLE_TEXT = "আপনার ব্র্যান্ড"

    /** The logo badge's side, as a fraction of preview width (square). */
    const val LOGO_SIDE_FRAC = 0.28f
    /** The badge's centre sits this far above the canvas centre, as a fraction of width. */
    const val LOGO_Y_FRAC = -0.10f
    /** Badge corner radius as stored: 65 x (radius / half side) = 65 x 0.4 — the radius is
     *  40% of the half side (20% of the side), the classic rounded-badge look, in the same
     *  convention the Carousel's cards use. */
    const val LOGO_CORNER = 26f
    /** The logo's pop starts at this share of its rest scale. */
    const val ENTER_SCALE = 60f

    /** The brand text's centre sits this far below the canvas centre, as a fraction of width. */
    const val TEXT_Y_FRAC = 0.13f
    /** The text's entrance rise: how far below its rest place it starts, fraction of width. */
    const val TEXT_RISE_FRAC = 0.03f

    /** The app shape box in stored units is previewWidth x 130 / 360 (importer's basis). */
    const val BOX_NUM = 130f
    const val BOX_DEN = 360f

    // The app's own easing presets (Easing.PRESETS), by name.
    val EASE_BACK_OUT = Easing(0.175f, 0.885f, 0.32f, 1.2f)     // "Back out"
    val EASE_CUBIC_OUT = Easing(0.215f, 0.61f, 0.355f, 1f)      // "Cubic out"
}

object LogoRevealTemplate {

    /** What [buildGroup] hands back: the Group layer, its two children, and the lanes to add. */
    data class Insert(
        val group: TimelineClip,
        /** The logo slot: a shape layer whose image fill the user replaces with their logo. */
        val logoLayer: TimelineClip,
        /** The brand name: one ordinary TEXT layer carrying its own entrance. */
        val textLayer: TimelineClip,
        /** The layers inside the group in lane order (top lane first): the logo, then the name. */
        val children: List<TimelineClip>,
        /** Lanes to place on top of the project, top lane first: the group, then [children]. */
        val lanesTopFirst: List<TimelineClip>
    )

    /**
     * The template as ONE Group layer + its children, ready to insert into the project that
     * is open. [previewWidthPx] / [previewHeightPx] are the editor preview frame's size
     * (the stored-units basis, like the importer's); [logoUri] is the generated placeholder
     * picture for the logo slot; the group starts at [insertAtMs] (the playhead) and spans
     * [LogoRevealSpec.DURATION_MS]. Every call builds fresh clips under [idBase] —
     * inserting twice gives two independent reveals.
     *
     * The group stays at the REST pose and the children link to it with the REST reference
     * pose (the Carousel's convention): the group's own transform is then a pure user
     * control on top of the authored design, and ParentRig composes it onto both children.
     */
    fun buildGroup(
        previewWidthPx: Float,
        previewHeightPx: Float,
        logoUri: String,
        insertAtMs: Long,
        idBase: String = "logoreveal"
    ): Insert {
        // Native since migration stage 3: the construction is computed in
        // template_plans.cpp and comes back as one plan in lanesTopFirst
        // order — group, logo, text.
        val clips = TemplatePlansNative.logoRevealPlan(previewWidthPx, logoUri, insertAtMs, idBase)
        val group = clips[0]
        val logo = clips[1]
        val text = clips[2]
        val children = clips.subList(1, clips.size)
        val lanesTopFirst = clips
        return Insert(group, logo, text, children, lanesTopFirst)
    }
}
