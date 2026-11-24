package com.example

import com.example.model.PlaybackAdvanceLaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 273 — pins [PlaybackAdvanceLaw], the one decision both of the
 * preview's advance paths (the parallel decode batch and the synchronous
 * fallback) follow, so a layer shows the same frame whichever path ran:
 * the law is the pre-273 serial rule, extracted unchanged.
 */
class PlaybackAdvanceLawTest {

    private val f = 33_333L  // one 30 fps frame

    @Test
    fun `a layer that has shown nothing seeks patiently, playing or not`() {
        assertEquals(PlaybackAdvanceLaw.Action.SEEK_PATIENT, PlaybackAdvanceLaw.action(shown = false, playing = true, targetUs = 0L, ptsUs = 0L, frameUs = f))
        assertEquals(PlaybackAdvanceLaw.Action.SEEK_PATIENT, PlaybackAdvanceLaw.action(shown = false, playing = false, targetUs = 500_000L, ptsUs = 0L, frameUs = f))
    }

    @Test
    fun `paused or scrubbing always seeks, however close the target is`() {
        assertEquals(PlaybackAdvanceLaw.Action.SEEK, PlaybackAdvanceLaw.action(shown = true, playing = false, targetUs = f, ptsUs = 0L, frameUs = f))
        assertEquals(PlaybackAdvanceLaw.Action.SEEK, PlaybackAdvanceLaw.action(shown = true, playing = false, targetUs = 0L, ptsUs = 9_000_000L, frameUs = f))
    }

    @Test
    fun `playing close behind steps with next`() {
        // On the surface frame, one frame behind, and up to the catch-up limit: step.
        assertEquals(PlaybackAdvanceLaw.Action.NEXT, PlaybackAdvanceLaw.action(shown = true, playing = true, targetUs = 1_000_000L, ptsUs = 1_000_000L, frameUs = f))
        assertEquals(PlaybackAdvanceLaw.Action.NEXT, PlaybackAdvanceLaw.action(shown = true, playing = true, targetUs = 1_000_000L + f, ptsUs = 1_000_000L, frameUs = f))
        assertEquals(PlaybackAdvanceLaw.Action.NEXT, PlaybackAdvanceLaw.action(shown = true, playing = true, targetUs = PlaybackAdvanceLaw.MAX_CATCHUP_US, ptsUs = 0L, frameUs = f))
        // A little ahead of the surface frame (within two frames) still steps.
        assertEquals(PlaybackAdvanceLaw.Action.NEXT, PlaybackAdvanceLaw.action(shown = true, playing = true, targetUs = 0L, ptsUs = 2 * f, frameUs = f))
    }

    @Test
    fun `playing far from the surface frame seeks`() {
        // More than the catch-up limit behind.
        assertEquals(PlaybackAdvanceLaw.Action.SEEK, PlaybackAdvanceLaw.action(shown = true, playing = true, targetUs = PlaybackAdvanceLaw.MAX_CATCHUP_US + 1, ptsUs = 0L, frameUs = f))
        // More than two frames ahead (the playhead jumped backwards).
        assertEquals(PlaybackAdvanceLaw.Action.SEEK, PlaybackAdvanceLaw.action(shown = true, playing = true, targetUs = 0L, ptsUs = 2 * f + 1, frameUs = f))
    }

    @Test
    fun `stepping stops at the target frame and at the per-frame cap`() {
        assertFalse(PlaybackAdvanceLaw.shouldStep(targetUs = f - 1, ptsUs = 0L, frameUs = f, steps = 0))
        assertTrue(PlaybackAdvanceLaw.shouldStep(targetUs = f, ptsUs = 0L, frameUs = f, steps = 0))
        assertTrue(PlaybackAdvanceLaw.shouldStep(targetUs = 10 * f, ptsUs = 0L, frameUs = f, steps = PlaybackAdvanceLaw.MAX_STEPS - 1))
        assertFalse(PlaybackAdvanceLaw.shouldStep(targetUs = 10 * f, ptsUs = 0L, frameUs = f, steps = PlaybackAdvanceLaw.MAX_STEPS))
        assertEquals(3, PlaybackAdvanceLaw.MAX_STEPS)
        assertEquals(750_000L, PlaybackAdvanceLaw.MAX_CATCHUP_US)
    }
}
