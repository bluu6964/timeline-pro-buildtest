package com.example.model

import com.example.engine.TemplatePlansNative

/**
 * The "Donut Chart" template (Templates programme, reference commission batch 2,
 * 2026-11-13): Sarowar's uploaded reference video (720x1280, 30 fps, 11.47 s) — on a
 * black background a donut chart builds itself segment by segment: a red 50%
 * segment sweeps the left half, then 20% / 15% / 10% / 5% segments follow around
 * the ring, each with its percentage label outside the ring and a legend row
 * (coloured dot + "50% : Opportunistic Scams" style lines) appearing underneath;
 * thin white arc accents draw themselves around the ring while it builds. (The
 * written batch-2 note described a different video for this slot; the template
 * follows the video that was actually uploaded — see ROADMAP.)
 *
 * Each segment is a `.pie` wedge layer — the parametric shape the Alight importer
 * builds (see [PieOutline]) — with a two-stop gradient fill; the donut's hole is
 * one black circle over the wedges (the classic punch-out: the wedges stay whole
 * discs underneath, so each segment's sweep is a single angle track). Segment 1
 * grows counterclockwise from the top (the video fills the left half first); the
 * others grow clockwise. Both directions are the same wrap rule read two ways —
 * verified against [PieOutline.sweepDegrees] in the harness:
 *  - a CCW-grown segment spanning authored [A, B] keys its startAngle A-360 -> B-360
 *    with endAngle fixed at A;
 *  - a CW-grown segment spanning authored [A, B] keys its startAngle B -> A with
 *    endAngle fixed at B-360.
 * (Authored angles grow counterclockwise on screen, 0 at +x, 90 at the top.)
 * The endpoint cases (delta 0 / delta 360) draw nothing in Alight's rule, so a
 * segment starts exactly empty and ends exactly full — no epsilon needed here.
 * Everything is ordinary layers: the wedges are PATH shapes whose angle tracks
 * are keyframes; labels and legend lines are plain text the user retypes (their
 * own percentages should be retyped with the words); the timings are theirs to
 * retime. No rig.
 *
 * DEVIATIONS from the video, honestly: the legend lines type in letter by letter
 * in the video — here they fade/slide in (the app's typewriter is a text-progress
 * animator the template could carry, but a fade keeps every line a plain editable
 * text layer; the motion difference is small on screen). The video's build-up
 * restarts around 10 s (its own loop); the template builds once over ~6.3 s and
 * holds the finished chart to 10 s. The "0x" label at the top is kept as an
 * editable text layer, as it appears in the video.
 */
object DonutChartSpec {
    const val DURATION_MS = 10000L

    const val BG = 0xFF050506L

    /** Segment shares (percent) and authored spans (degrees, CCW from 90 = top). */
    val PERCENTS = listOf(50, 20, 15, 10, 5)
    /** Authored (A, B) per segment: seg 1 sweeps CCW (left half); segs 2-5 run CW down the right side. */
    val SPANS = listOf(
        90f to 270f,
        18f to 90f,
        -36f to 18f,
        -72f to -36f,
        -90f to -72f
    )
    /** Gradient stops per segment (inner -> outer tone of the same hue). */
    val GRADIENTS = listOf(
        0xFF8F0E1EL to 0xFFE8112DL,
        0xFFB33A12L to 0xFFF4652AL,
        0xFFC25E0AL to 0xFFF59A23L,
        0xFFA8B51BL to 0xFFD7DF23L,
        0xFF12B76AL to 0xFF2EE66BL
    )
    val LEGEND = listOf(
        "50% : Opportunistic Scams",
        "20% : Overly Ambitious",
        "15% : \"Too early\" solutions",
        "10% : Good Investments",
        "5% : The Next \"Amazons\""
    )
    /** Each segment's build window [start, end] in ms. */
    val WINDOWS = listOf(
        300L to 2000L,
        2000L to 3300L,
        3300L to 4300L,
        4300L to 5300L,
        5300L to 6300L
    )

    /** Donut centre: (0, [CY_FRAC] of ph). Outer radius [R_FRAC] of pw; the hole [HOLE_FRAC]. */
    const val CY_FRAC = -0.20f
    const val R_FRAC = 0.315f
    const val HOLE_FRAC = 0.375f
    const val LABEL_R_FRAC = 0.40f
    const val LABEL_FONT_FRAC = 0.034f
    const val LEGEND_Y0_FRAC = 0.115f      // of ph, first legend row
    const val LEGEND_PITCH_FRAC = 0.036f   // of ph
    const val LEGEND_FONT_FRAC = 0.030f
    const val DOT_D_FRAC = 0.020f
    const val TITLE_Y_FRAC = -0.435f       // of ph ("0x")
    const val TITLE_FONT_FRAC = 0.030f

    /** Frame position of segment [index]'s % label (its span's mid-angle at [LABEL_R_FRAC]). */
    fun labelPos(index: Int, pw: Float, ph: Float): Pair<Float, Float> {
        val r = TemplatePlansNative.donutLabelPos(index, pw, ph)
        return r[0] to r[1]
    }

    /** Legend row [index]'s centre y (stored px from the canvas centre). */
    fun legendY(index: Int, ph: Float): Float = TemplatePlansNative.donutLegendY(index, ph)

    val EASE_CUBIC_OUT = Easing(0.215f, 0.61f, 0.355f, 1f)
    val EASE_CUBIC_IN_OUT = Easing(0.645f, 0.045f, 0.355f, 1f)
    val EASE_BACK_OUT = Easing(0.175f, 0.885f, 0.32f, 1.2f)
}

object DonutChartTemplate {

    /** One donut segment's layers. */
    data class Segment(
        val wedge: TimelineClip,
        val label: TimelineClip,
        val dot: TimelineClip,
        val legendText: TimelineClip,
        val all: List<TimelineClip>
    )

    /** What [buildGroup] hands back: the Group layer, the named parts, and the lanes. */
    data class Insert(
        val group: TimelineClip,
        val background: TimelineClip,
        val title: TimelineClip,
        val hole: TimelineClip,
        val arcOuter: TimelineClip,
        val arcInner: TimelineClip,
        val segments: List<Segment>,
        /** Every layer inside the group in lane order (top lane first). */
        val children: List<TimelineClip>,
        /** Lanes to place on top of the project, top lane first: the group, then [children]. */
        val lanesTopFirst: List<TimelineClip>
    )

    /**
     * The template as ONE Group layer + its ordinary layers, ready to insert into
     * the project that is open. The group starts at [insertAtMs] and spans
     * [DonutChartSpec.DURATION_MS]; sizes ride [previewWidthPx] (the donut is
     * round on any canvas), vertical anchors ride [previewHeightPx].
     */
    fun buildGroup(
        previewWidthPx: Float,
        previewHeightPx: Float,
        insertAtMs: Long,
        idBase: String = "donut"
    ): Insert {
        // Native since migration stage 3: the construction is computed in
        // template_plans.cpp and comes back as one plan in lanesTopFirst
        // order — group, title, per segment (label, legend text, dot), the
        // two arcs, the hole, the five wedges, the background. Segment i's
        // parts sit at fixed offsets in that order.
        val clips = TemplatePlansNative.donutPlan(previewWidthPx, previewHeightPx, insertAtMs, idBase)
        val group = clips[0]
        val title = clips[1]
        val arcOuter = clips[17]
        val arcInner = clips[18]
        val hole = clips[19]
        val background = clips[25]
        val segments = (0 until 5).map { i ->
            val label = clips[2 + 3 * i]
            val legendText = clips[3 + 3 * i]
            val dot = clips[4 + 3 * i]
            val wedge = clips[20 + i]
            Segment(wedge, label, dot, legendText, listOf(label, legendText, dot, wedge))
        }
        val children = clips.subList(1, clips.size)
        val lanesTopFirst = clips
        return Insert(group, background, title, hole, arcOuter, arcInner, segments, children, lanesTopFirst)
    }
}
