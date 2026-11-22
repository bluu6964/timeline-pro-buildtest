package com.example

import com.example.model.FrameFit
import com.example.model.ScrubLaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Build 261 — pins the frame-fit law ([FrameFit]): for every project
 * aspect × surface pair the fitted rect must lie inside the surface,
 * touch at least one pair of opposite edges, be centred, and preserve
 * the aspect within rounding — the letterbox guarantee the fullscreen
 * player (and the editor canvas / crop stage, which share the law)
 * depends on, since the C++ preview treats the surface as the canvas.
 * Also pins the fullscreen scrub mapping ([ScrubLaw]).
 */
class FrameFitTest {

    private val aspects = listOf(
        "9:16" to (9f / 16f),
        "16:9" to (16f / 9f),
        "1:1" to 1f,
        "4:5" to (4f / 5f),
        "3:4" to (3f / 4f),
        "21:9" to (21f / 9f)
    )

    private val surfaces = listOf(
        "portrait phone" to (1080f to 2400f),
        "landscape phone" to (2400f to 1080f),
        "square" to (1000f to 1000f),
        "4:3 tablet" to (1600f to 1200f),
        "3:4 tablet portrait" to (1200f to 1600f),
        "small dp surface" to (360f to 800f),
        "ultrawide monitor" to (3440f to 1440f)
    )

    private val eps = 0.6f // float rounding on the largest surface

    @Test
    fun everyAspectFitsEverySurfaceWithoutStretch() {
        for ((aName, ratio) in aspects) {
            for ((sName, s) in surfaces) {
                val (cw, ch) = s
                val r = FrameFit.fit(cw, ch, ratio)
                val where = "$aName in $sName"
                // Inside the container.
                assertTrue("$where: x >= 0 (${r.x})", r.x >= -eps)
                assertTrue("$where: y >= 0 (${r.y})", r.y >= -eps)
                assertTrue("$where: right edge inside (${r.x + r.w} <= $cw)", r.x + r.w <= cw + eps)
                assertTrue("$where: bottom edge inside (${r.y + r.h} <= $ch)", r.y + r.h <= ch + eps)
                assertTrue("$where: positive size", r.w > 0f && r.h > 0f)
                // Aspect preserved — never stretched.
                assertEquals("$where: aspect", ratio, r.w / r.h, ratio * 0.002f)
                // Touches at least one pair of opposite edges (it is the LARGEST fit).
                assertTrue(
                    "$where: touches an edge pair (w=${r.w}/$cw, h=${r.h}/$ch)",
                    abs(r.w - cw) <= eps || abs(r.h - ch) <= eps
                )
                // Centred on the slack axis.
                assertEquals("$where: centred x", (cw - r.w) / 2f, r.x, eps)
                assertEquals("$where: centred y", (ch - r.h) / 2f, r.y, eps)
            }
        }
    }

    @Test
    fun portraitProjectInPortraitSurfaceIsWidthBound() {
        // The reported case: 9:16 fullscreen on a portrait phone must be
        // as wide as the screen and letterboxed top/bottom — never wider
        // than the screen, never stretched.
        val r = FrameFit.fit(1080f, 2400f, 9f / 16f)
        assertEquals(1080f, r.w, eps)
        assertEquals(1920f, r.h, eps)
        assertEquals(0f, r.x, eps)
        assertEquals(240f, r.y, eps)
    }

    @Test
    fun landscapeProjectInPortraitSurfaceIsWidthBound() {
        val r = FrameFit.fit(1080f, 2400f, 16f / 9f)
        assertEquals(1080f, r.w, eps)
        assertEquals(607.5f, r.h, 0.1f)
        assertEquals(0f, r.x, eps)
    }

    @Test
    fun portraitProjectInLandscapeSurfaceIsHeightBound() {
        val r = FrameFit.fit(2400f, 1080f, 9f / 16f)
        assertEquals(1080f, r.h, eps)
        assertEquals(607.5f, r.w, 0.1f)
        assertEquals((2400f - 607.5f) / 2f, r.x, 0.1f)
        assertEquals(0f, r.y, eps)
    }

    @Test
    fun matchingAspectsFillExactly() {
        val r = FrameFit.fit(1600f, 900f, 16f / 9f)
        assertEquals(0f, r.x, eps)
        assertEquals(0f, r.y, eps)
        assertEquals(1600f, r.w, eps)
        assertEquals(900f, r.h, eps)
    }

    @Test
    fun degenerateInputsGiveAnEmptyRect() {
        assertEquals(FrameFit.Rect(0f, 0f, 0f, 0f), FrameFit.fit(0f, 100f, 1f))
        assertEquals(FrameFit.Rect(0f, 0f, 0f, 0f), FrameFit.fit(100f, 0f, 1f))
        assertEquals(FrameFit.Rect(0f, 0f, 0f, 0f), FrameFit.fit(-5f, 100f, 1f))
        assertEquals(FrameFit.Rect(0f, 0f, 0f, 0f), FrameFit.fit(100f, 100f, 0f))
        assertEquals(FrameFit.Rect(0f, 0f, 0f, 0f), FrameFit.fit(100f, 100f, -2f))
    }

    @Test
    fun scrubMappingRoundTripsAndClamps() {
        assertEquals(0.5f, ScrubLaw.fractionAt(500L, 1000L), 0.0001f)
        assertEquals(0f, ScrubLaw.fractionAt(0L, 1000L), 0.0001f)
        assertEquals(1f, ScrubLaw.fractionAt(1500L, 1000L), 0.0001f) // past the end clamps
        assertEquals(0f, ScrubLaw.fractionAt(10L, 0L), 0.0001f)       // no duration
        assertEquals(500L, ScrubLaw.msAt(0.5f, 1000L))
        assertEquals(0L, ScrubLaw.msAt(-0.25f, 1000L))
        assertEquals(1000L, ScrubLaw.msAt(1.25f, 1000L))
        assertEquals(0L, ScrubLaw.msAt(0.5f, 0L))
        // Round trip at an awkward duration (float rounding: within 1 ms).
        val total = 83_417L
        val back = ScrubLaw.msAt(ScrubLaw.fractionAt(41_708L, total), total)
        assertTrue("round trip within 1 ms ($back)", abs(back - 41_708L) <= 1L)
    }
}
