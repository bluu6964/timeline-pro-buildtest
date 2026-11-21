package com.example.model

import com.example.engine.TemplatePlansNative

/**
 * The "Social Growth" template (Templates programme, reference commission batch 2,
 * 2026-11-13): Sarowar's uploaded reference video (720x1280, 30 fps, 9.92 s) — a
 * social-media growth promo on a black background: an "UPLOAD" pill is clicked and
 * morphs into a green progress ring that draws itself and earns a checkmark; the
 * title flips CREATE -> POST... -> ELEVATE (turning green at the end); a profile
 * header fades in (circular avatar slot, POSTS and FOLLOWERS counters that climb —
 * slowly at first, then fast) above a 3 x 4 grid of glowing green tiles whose
 * checkmarks pop in one by one. (The written batch-2 note described a different
 * video for this slot; the template follows the video that was actually
 * uploaded — see ROADMAP.)
 *
 * The counters are the app's own Count Up/Down animator (the `counter` text
 * animator, evaluated by CounterEval in both render paths) on plain "0" text
 * layers: the offset tracks carry the counts the video shows (posts 0 -> 59,
 * followers 0 -> 9716, with the same slow-then-fast pacing read off the frames).
 * The progress ring is a stroked circle whose STROKE_TRIM track draws it
 * clockwise from the top. Tiles are gradient-filled rounded rects; each
 * checkmark is two thin rotated rounded rects (a glyph check would risk the
 * emoji font on some phones and could not keep the neon green). The three
 * titles are separate text layers crossfading in sequence. Everything is an
 * ordinary layer in ONE group; no rig.
 *
 * DEVIATIONS from the video, honestly: the mouse cursor that clicks the button
 * is a screen-recording artefact and is not reproduced; the video's POST title
 * animates its dots (POST. / POST.. / POST...) — the template's middle title is
 * a static "POST..."; between the ring and the grid the video holds a single
 * centred tile for a beat — the template crossfades straight to the header +
 * grid; and at the tail the video's grid scrolls and re-pops its rows — the
 * template gives the tiles a soft second opacity wave instead of a scroll.
 */
object SocialGrowthSpec {
    const val DURATION_MS = 10000L

    const val BG = 0xFF000000L
    const val GREEN = 0xFF2EE66BL
    const val GREEN_BRIGHT = 0xFF4DFF7AL
    const val TITLE_GREEN = 0xFF3DFF6EL
    const val LABEL_GREY = 0xFFB9B9C0L

    // ---- titles ----
    val TITLES = listOf("CREATE", "POST...", "ELEVATE")
    const val TITLE_Y_FRAC = -0.345f       // of ph
    const val TITLE_FONT_FRAC = 0.052f

    // ---- upload pill + progress ring (the intro, centre frame) ----
    const val PILL_Y_FRAC = -0.06f         // of ph
    const val PILL_W_FRAC = 0.235f
    const val PILL_H_FRAC = 0.042f         // of ph
    const val RING_Y_FRAC = 0.04f          // of ph
    const val RING_D_FRAC = 0.185f

    // ---- header ----
    const val AVATAR_X_FRAC = -0.235f
    const val HEADER_Y_FRAC = -0.278f      // of ph
    const val AVATAR_D_FRAC = 0.088f
    const val POSTS_X_FRAC = -0.075f
    const val FOLLOWERS_X_FRAC = 0.115f
    const val COUNT_FONT_FRAC = 0.040f
    const val LABEL_FONT_FRAC = 0.020f
    const val LABEL_DY_FRAC = 0.036f       // of ph, number -> its label

    /** Counter keyframes (normalised t -> value) read off the reference frames. */
    val POSTS_KEYS = listOf(
        0.52f to 0f, 0.62f to 2f, 0.70f to 6f, 0.78f to 12f,
        0.85f to 24f, 0.92f to 37f, 1.0f to 59f
    )
    val FOLLOWERS_KEYS = listOf(
        0.52f to 0f, 0.62f to 3f, 0.70f to 13f, 0.78f to 77f,
        0.85f to 896f, 0.92f to 3938f, 1.0f to 9716f
    )

    // ---- tile grid: 3 x 4 ----
    const val COLS = 3
    const val ROWS = 4
    const val TILE_FRAC = 0.168f           // square, of pw
    const val TILE_PITCH_X_FRAC = 0.196f
    const val TILE_PITCH_Y_FRAC = 0.1105f  // of ph
    const val GRID_Y0_FRAC = -0.155f       // of ph, first row's centre
    const val TILE_IN_FIRST_MS = 5200L
    const val TILE_IN_STAGGER_MS = 130L
    const val TILE_IN_MS = 380L
    const val WAVE_FIRST_MS = 8600L
    const val WAVE_STAGGER_MS = 60L

    /** Tile [index]'s centre (row-major), stored px from the canvas centre. */
    fun tilePos(index: Int, pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.socialTilePos(index, pw, ph)
        return r[0] to r[1]
    }

    /** The Count Up/Down animator with [keys] as its offset track (scale fixed 1). */
    fun counterAnimator(keys: List<Pair<Float, Float>>): TextAnimator =
        TemplatePlansNative.counterAnimator(keys)

    val EASE_CUBIC_OUT = Easing(0.215f, 0.61f, 0.355f, 1f)
    val EASE_CUBIC_IN = Easing(0.55f, 0.055f, 0.675f, 0.19f)
    val EASE_CUBIC_IN_OUT = Easing(0.645f, 0.045f, 0.355f, 1f)
    val EASE_BACK_OUT = Easing(0.175f, 0.885f, 0.32f, 1.2f)
}

object SocialGrowthTemplate {

    /** What [buildGroup] hands back: the Group layer, the named parts, and the lanes. */
    data class Insert(
        val group: TimelineClip,
        val background: TimelineClip,
        val titles: List<TimelineClip>,
        val uploadPill: TimelineClip,
        val uploadText: TimelineClip,
        val ring: TimelineClip,
        /** The ring's checkmark: two thin rounded-rect layers. */
        val ringCheck: List<TimelineClip>,
        val avatar: TimelineClip,
        val avatarRing: TimelineClip,
        val avatarRingGreen: TimelineClip,
        val postsCount: TimelineClip,
        val postsLabel: TimelineClip,
        val followersCount: TimelineClip,
        val followersLabel: TimelineClip,
        val tiles: List<TimelineClip>,
        /** Each tile's checkmark: two thin rounded-rect layers per tile, same order as [tiles]. */
        val tileChecks: List<List<TimelineClip>>,
        /** Every layer inside the group in lane order (top lane first). */
        val children: List<TimelineClip>,
        /** Lanes to place on top of the project, top lane first: the group, then [children]. */
        val lanesTopFirst: List<TimelineClip>
    )

    /**
     * The template as ONE Group layer + its ordinary layers, ready to insert into
     * the project that is open. [avatarUri] feeds the profile picture slot (a
     * generated placeholder; replaced via Color -> Fill -> Image). The group
     * starts at [insertAtMs] and spans [SocialGrowthSpec.DURATION_MS].
     */
    fun buildGroup(
        previewWidthPx: Float,
        previewHeightPx: Float,
        avatarUri: String,
        insertAtMs: Long,
        idBase: String = "growth"
    ): Insert {
        // Native since migration stage 3: the construction is computed in
        // template_plans.cpp and comes back as one plan in lanesTopFirst
        // order — group, the three titles, followers label + count, posts
        // label + count, the green avatar ring, the grey ring, the avatar,
        // then per tile from the last to the first (check A, check B, tile),
        // the ring check pair, the ring, the upload label, the upload pill,
        // the background.
        val clips = TemplatePlansNative.socialGrowthPlan(
            previewWidthPx, previewHeightPx, avatarUri, insertAtMs, idBase
        )
        val group = clips[0]
        val titles = clips.subList(1, 4)
        val followersLabel = clips[4]
        val followersCount = clips[5]
        val postsLabel = clips[6]
        val postsCount = clips[7]
        val avatarRingGreen = clips[8]
        val avatarRing = clips[9]
        val avatar = clips[10]
        val tiles = (0 until 12).map { i -> clips[11 + (11 - i) * 3 + 2] }
        val tileChecks = (0 until 12).map { i ->
            listOf(clips[11 + (11 - i) * 3], clips[11 + (11 - i) * 3 + 1])
        }
        val ringCheck = listOf(clips[47], clips[48])
        val ring = clips[49]
        val uploadText = clips[50]
        val uploadPill = clips[51]
        val background = clips[52]
        val children = clips.subList(1, clips.size)
        val lanesTopFirst = clips
        return Insert(
            group, background, titles, uploadPill, uploadText, ring, ringCheck,
            avatar, avatarRing, avatarRingGreen, postsCount, postsLabel,
            followersCount, followersLabel, tiles, tileChecks, children, lanesTopFirst
        )
    }
}
