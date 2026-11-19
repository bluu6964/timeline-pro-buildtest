package com.example

import com.example.model.AnimProp
import com.example.model.Keyframe
import com.example.model.LayerType
import com.example.model.ProjectSnapshot
import com.example.model.SoloLaw
import com.example.model.SoloState
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 201 — the audit fix build. The pure piece pinned here: the solo session
 * laws (S-5, [SoloLaw] + the snapshot field). (The S-1 speed-ramp table tests
 * were removed with the ramp itself in build 237.)
 * The layout items (B-1 play-controls rebuild, B-5 multi-select regroup,
 * B-3/B-8 touch targets) are Compose geometry — proven by the build's 360 dp
 * budget table + proof renders, not by this suite.
 */
class Build201Test {

    private fun clip(
        id: String,
        durationMs: Long = 6000,
        speedKeys: List<Keyframe>? = null,
        visible: Boolean = true,
        muted: Boolean = false
    ) = TimelineClip(
        id = id, name = id, type = LayerType.AUDIO, startMs = 0, durationMs = durationMs,
        color = 0xFF000000, isVisible = visible, isMuted = muted,
        keyframes = if (speedKeys == null) emptyMap() else mapOf(AnimProp.SPEED to speedKeys)
    )

    private var laneSeq = 0
    private fun lane(vararg clips: TimelineClip) =
        TimelineTrack(id = "lane-${laneSeq++}", name = "L", clips = clips.toList())

    // ---------- S-5: SoloLaw + the snapshot field ----------

    private fun threeTracks(): List<TimelineTrack> = listOf(
        lane(clip("a", visible = true, muted = false), clip("b", visible = false, muted = true)),
        lane(clip("c", visible = true, muted = true))
    )

    @Test
    fun solo_capture_recordsEveryLayer() {
        val s = SoloLaw.capture(threeTracks(), "a")
        assertEquals("a", s.clipId)
        assertEquals(3, s.restore.size)
        assertEquals(false, s.restore.first { it.clipId == "b" }.isVisible)
        assertEquals(true, s.restore.first { it.clipId == "b" }.isMuted)
        assertEquals(true, s.restore.first { it.clipId == "c" }.isMuted)
    }

    @Test
    fun solo_soloed_forcesSoloVisible_hidesAndMutesOthers_keepsSoloMute() {
        // Solo a HIDDEN + MUTED layer: it becomes visible, its mute is NOT cleared.
        val tracks = listOf(lane(clip("a", visible = true), clip("b", visible = false, muted = true)))
        val out = SoloLaw.soloed(tracks, "b").flatMap { it.clips }
        val b = out.first { it.id == "b" }
        assertTrue(b.isVisible)
        assertTrue("the soloed layer's own mute is untouched", b.isMuted)
        val a = out.first { it.id == "a" }
        assertFalse(a.isVisible)
        assertTrue(a.isMuted)
    }

    @Test
    fun solo_restored_roundTrips() {
        val tracks = threeTracks()
        val s = SoloLaw.capture(tracks, "c")
        val back = SoloLaw.restored(SoloLaw.soloed(tracks, "c"), s)
        assertEquals(
            tracks.flatMap { it.clips }.map { it.id to (it.isVisible to it.isMuted) },
            back.flatMap { it.clips }.map { it.id to (it.isVisible to it.isMuted) }
        )
    }

    @Test
    fun solo_restored_keepsLayersAddedAfterCapture() {
        val tracks = threeTracks()
        val s = SoloLaw.capture(tracks, "a")
        val grown = SoloLaw.soloed(tracks, "a") + lane(clip("new", visible = true, muted = false))
        val newClip = SoloLaw.restored(grown, s).flatMap { it.clips }.first { it.id == "new" }
        assertTrue(newClip.isVisible)
        assertFalse(newClip.isMuted)
    }

    @Test
    fun solo_isValidFor_needsTheLayer() {
        val tracks = threeTracks()
        assertTrue(SoloLaw.isValidFor(SoloState("a", emptyList()), tracks))
        assertFalse(SoloLaw.isValidFor(SoloState("gone", emptyList()), tracks))
        assertFalse(SoloLaw.isValidFor(null, tracks))
    }

    @Test
    fun solo_snapshotField_defaultsNull_andCarriesTheSession() {
        assertNull(ProjectSnapshot().solo)
        val s = SoloState("a", emptyList())
        assertEquals(s, ProjectSnapshot(solo = s).solo)
    }
}
