package com.example.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * Build 257 — the Edit Shape screen's LIVE PREVIEW law.
 *
 * The sheet's old preview sat in a `weight(1f)` slot between the header
 * and the transport; in the full-screen Dialog that hosts the sheet the
 * slot collapses to zero height on device, so the screen showed no
 * canvas and no shape at all (Sarowar's 2026-11-22 phone report: he
 * edited Width / Height / Feather / Fill / Stroke blind). The preview
 * now lives in an explicitly sized slot between the transport block and
 * the tabs panel, and this law splits the screen height between that
 * slot and the panel's scrollable content — no `weight` anywhere, so
 * the split holds whatever the Dialog's measure pass does. Pure dp
 * arithmetic plus the two polygon generators the preview draws with
 * (their proportions are the C++ shape engine's own, see below).
 */
object ShapePreviewGeometry {

    /** The header row (ShapeHeaderGl is a fixed 42 dp box). */
    const val HEADER_DP = 42f

    /**
     * The transport block (transport row 34 + timecode row + the 24 dp
     * mini strip, with its vertical paddings) — measured from the
     * build-252 layout, rounded up.
     */
    const val TRANSPORT_DP = 88f

    /** The preview slot's own vertical padding (6 dp above + 6 below). */
    const val SLOT_PAD_V_DP = 12f

    /**
     * The tabs panel's fixed chrome: panel top padding + handle + the
     * tab strip + the gap above the save pill + the save pill + bottom
     * padding. Only the tab CONTENT between the strip and the pill is
     * flexible (it scrolls inside its budget).
     */
    const val PANEL_CHROME_DP = 128f

    const val PREVIEW_MIN_DP = 96f
    const val PREVIEW_MAX_DP = 200f
    const val PANEL_SCROLL_MIN_DP = 120f
    const val PANEL_SCROLL_MAX_DP = 264f

    /** Fallback screen height when the host offers an unbounded one. */
    const val DEFAULT_SCREEN_DP = 700f

    /**
     * The flexible split for a screen of [screenHDp]: (preview slot
     * height, panel scroll height) in dp. The preview takes 45 % of
     * what the fixed blocks leave, the panel content takes the rest;
     * both clamp to their min/max, so on a tall screen both sit at
     * their caps (slack pools below the panel, as before) and on a
     * short screen both sit at their floors (the root may then
     * overflow by the floors' excess — the degenerate case).
     */
    fun budget(screenHDp: Float): Pair<Float, Float> {
        val avail = screenHDp - HEADER_DP - TRANSPORT_DP - SLOT_PAD_V_DP - PANEL_CHROME_DP
        val preview = (avail * 0.45f).coerceIn(PREVIEW_MIN_DP, PREVIEW_MAX_DP)
        val panel = (avail - preview).coerceIn(PANEL_SCROLL_MIN_DP, PANEL_SCROLL_MAX_DP)
        return preview to panel
    }

    /**
     * The letterboxed frame (w, h) for the project [ratio] inside a
     * slot of [slotW] x [slotH] (any consistent unit) — the old
     * preview's inline law, kept identical.
     */
    fun frameSize(slotW: Float, slotH: Float, ratio: Float): Pair<Float, Float> =
        if (slotW / slotH > ratio) (slotH * ratio) to slotH else slotW to (slotW / ratio)

    /** The shape's box at 100 %: the sheet's long-standing 135 dp base. */
    const val BASE_SHAPE_DP = 135f

    /** The shape box size (dp) at [widthPercent] x [heightPercent]. */
    fun shapeBoxDp(widthPercent: Float, heightPercent: Float): Pair<Float, Float> =
        (BASE_SHAPE_DP * widthPercent / 100f) to (BASE_SHAPE_DP * heightPercent / 100f)

    /**
     * Regular [n]-gon vertices (flattened x, y pairs), first vertex at
     * the top, clockwise in screen space, inscribed in the ellipse of
     * radii ([rx], [ry]) about ([cx], [cy]) — the C++ shape engine's
     * construction (shape_engine.cpp `regularPolygon`: vertices at
     * i·360/n − 90 degrees, stretched per axis by the box scale).
     */
    fun polygonPoints(n: Int, cx: Float, cy: Float, rx: Float, ry: Float): FloatArray {
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = Math.toRadians(i * 360.0 / n - 90.0)
            out[i * 2] = cx + rx * cos(a).toFloat()
            out[i * 2 + 1] = cy + ry * sin(a).toFloat()
        }
        return out
    }

    /** The star's inner radius as a fraction of the outer (shape_engine.cpp). */
    const val STAR_INNER_RATIO = 0.45f

    /**
     * The 5-point star's 10 vertices (flattened x, y pairs): vertex i
     * at i·36° − 90°, outer vertices at full radius, inner at
     * [STAR_INNER_RATIO] — shape_engine.cpp `regularPolygon`, STAR case.
     */
    fun starPoints(cx: Float, cy: Float, rx: Float, ry: Float): FloatArray {
        val out = FloatArray(20)
        for (i in 0 until 10) {
            val a = Math.toRadians(i * 36.0 - 90.0)
            val r = if (i % 2 == 1) STAR_INNER_RATIO else 1f
            out[i * 2] = cx + rx * r * cos(a).toFloat()
            out[i * 2 + 1] = cy + ry * r * sin(a).toFloat()
        }
        return out
    }

    /**
     * The feather blur radius (px) for [featherPercent]: the sheet's
     * own preview law since the sheet was built (feather % / 8, in dp,
     * converted by [density]). Feather is a preview-only softening in
     * this sheet — the saved clip carries no feather field.
     */
    fun featherRadiusPx(featherPercent: Float, density: Float): Float =
        (featherPercent / 8f) * density
}
