package com.example

import com.example.model.AnimProp
import com.example.model.ExtractAudio
import com.example.model.ImportedMedia
import com.example.model.Keyframe
import com.example.model.LaneLayout
import com.example.model.LayerType
import com.example.model.ProjectSnapshot
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import com.example.model.defaultClipColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 203 — extract audio (Sarowar 2026-11-15: detach from an existing
 * video layer + Add Audio → "Extract from video"). The pure pieces pinned
 * here: the construction law ([ExtractAudio] — the mix-preserving detach and
 * the imported-video layer) and the lane law ([LaneLayout.placeBelow]). The
 * ViewModel op, the panels and the pickers are wiring (Actions-compiled);
 * the actual decoding rides the shared C++ audio path that has always played
 * a video layer's own sound, so there is nothing new to pin below the model.
 */
class Build203Test {

    /** A trimmed, sped-up, faded video layer with picture baggage attached. */
    private fun videoSource(): TimelineClip = TimelineClip(
        id = "vid", name = "VID_1042", type = LayerType.VIDEO,
        startMs = 1500, durationMs = 4000, color = 0xFF2A4365,
        uri = "content://imports/VID_1042.mp4", mimeType = "video/mp4",
        sourceDurationMs = 9000, trimStartMs = 2000,
        volume = 0.8f, speed = 1.5f, fadeInMs = 300, fadeOutMs = 500,
        keyframes = mapOf(
            AnimProp.SPEED to listOf(Keyframe(0, 1f), Keyframe(2000, 2f)),
            AnimProp.OPACITY to listOf(Keyframe(0, 1f))
        ),
        effects = listOf(com.example.model.ClipFx(type = "vignette", values = listOf(1f))),
        chainId = "chain-1"
    )

    // ---------- the detach law ----------

    @Test
    fun detached_copiesTheSoundWindow() {
        val src = videoSource()
        val out = ExtractAudio.detachedLayer(src, "aud")
        assertEquals("aud", out.id)
        assertEquals(LayerType.AUDIO, out.type)
        assertEquals(src.uri, out.uri)
        assertEquals(src.mimeType, out.mimeType)
        assertFalse(out.isImage)
        assertEquals(src.startMs, out.startMs)
        assertEquals(src.durationMs, out.durationMs)
        assertEquals(src.trimStartMs, out.trimStartMs)
        assertEquals(src.sourceDurationMs, out.sourceDurationMs)
    }

    @Test
    fun detached_copiesTheSoundState() {
        val src = videoSource()
        val out = ExtractAudio.detachedLayer(src, "aud")
        assertEquals(src.volume, out.volume, 0f)
        assertEquals(src.fadeInMs, out.fadeInMs)
        assertEquals(src.fadeOutMs, out.fadeOutMs)
        assertEquals(src.speed, out.speed, 0f)
        assertEquals(src.isMuted, out.isMuted)
        assertEquals(src.isVisible, out.isVisible)
        // A muted + hidden source detaches to a muted + hidden layer: the
        // operation never changes what the project sounds like.
        val quiet = ExtractAudio.detachedLayer(src.copy(isMuted = true, isVisible = false), "aud2")
        assertTrue(quiet.isMuted)
        assertFalse(quiet.isVisible)
    }

    @Test
    fun detached_keepsOnlyTheSpeedKeyframes() {
        val src = videoSource()
        val out = ExtractAudio.detachedLayer(src, "aud")
        assertEquals(setOf(AnimProp.SPEED), out.keyframes.keys)
        assertEquals(src.keyframes[AnimProp.SPEED], out.keyframes[AnimProp.SPEED])
        // The stored SPEED keys ride along (inert since build 237 removed ramps:
        // both layers play at their constant base speed, so the detach still
        // cannot change what the project sounds like).
        // No speed keys on the source -> no keyframes at all on the layer.
        val plain = ExtractAudio.detachedLayer(src.copy(keyframes = emptyMap()), "aud3")
        assertTrue(plain.keyframes.isEmpty())
    }

    @Test
    fun detached_isAFreshStandaloneLayer() {
        val src = videoSource()
        val out = ExtractAudio.detachedLayer(src, "aud")
        assertEquals("VID_1042 (Audio)", out.name)
        assertEquals(defaultClipColor(LayerType.AUDIO), out.color)
        assertNull(out.chainId)
        assertNull(out.groupId)
        assertNull(out.parent)
        assertNull(out.controllerId)
        assertTrue(out.effects.isEmpty())
        assertTrue(out.masks.isEmpty())
        assertFalse(out.duckEnabled)
        assertFalse(out.nrEnabled)
    }

    // ---------- the lane law ----------

    private var laneSeq = 0
    private fun laneOf(clip: TimelineClip) =
        TimelineTrack(id = "lane-${laneSeq++}", name = "L", clips = listOf(clip))

    private fun bare(id: String, type: LayerType = LayerType.VIDEO) = TimelineClip(
        id = id, name = id, type = type, startMs = 0, durationMs = 1000, color = 0
    )

    @Test
    fun placeBelow_putsTheNewLaneDirectlyUnderTheAnchor() {
        val top = bare("top")
        val vid = bare("vid")
        val bottom = bare("bottom")
        val aud = bare("aud", LayerType.AUDIO)
        val tracks = listOf(laneOf(top), laneOf(vid), laneOf(bottom))
        val out = LaneLayout.placeBelow(tracks, "vid", aud)
        assertEquals(listOf("top", "vid", "aud", "bottom"), out.flatMap { it.clips }.map { it.id })
        // The result still satisfies the lane invariant (no re-flow needed).
        assertTrue(LaneLayout.isNormalized(out))
        assertEquals(out, LaneLayout.normalize(out))
    }

    @Test
    fun placeBelow_anchorAtTheBottom_appendsUnderIt() {
        val vid = bare("vid")
        val aud = bare("aud", LayerType.AUDIO)
        val out = LaneLayout.placeBelow(listOf(laneOf(vid)), "vid", aud)
        assertEquals(listOf("vid", "aud"), out.flatMap { it.clips }.map { it.id })
    }

    @Test
    fun placeBelow_unknownAnchor_fallsBackToTop() {
        val vid = bare("vid")
        val aud = bare("aud", LayerType.AUDIO)
        val out = LaneLayout.placeBelow(listOf(laneOf(vid)), "missing", aud)
        assertEquals(listOf("aud", "vid"), out.flatMap { it.clips }.map { it.id })
    }

    // ---------- the Add Audio -> Extract from video law ----------

    @Test
    fun imported_fullLengthAtThePlayhead() {
        val item = ImportedMedia(
            uri = "content://imports/clip.mp4", name = "clip.mp4",
            mimeType = "video/mp4", durationMs = 12345, isImage = false
        )
        val out = ExtractAudio.importedLayer(item, startMs = 2500, newId = "aud")
        assertEquals(LayerType.AUDIO, out.type)
        assertEquals("clip", out.name)
        assertEquals(2500, out.startMs)
        assertEquals(12345, out.durationMs)
        assertEquals(12345, out.sourceDurationMs)
        assertEquals(0, out.trimStartMs)
        assertEquals(item.uri, out.uri)
        assertEquals(item.mimeType, out.mimeType)
        assertEquals(defaultClipColor(LayerType.AUDIO), out.color)
        // A negative playhead (never in practice) clamps to the project start.
        assertEquals(0, ExtractAudio.importedLayer(item, startMs = -5, newId = "x").startMs)
    }

    @Test
    fun displayNameFor_stripsOnlyRealExtensions() {
        assertEquals("my_video", ExtractAudio.displayNameFor("my_video.mp4"))
        assertEquals("archive.tar", ExtractAudio.displayNameFor("archive.tar.mp4"))
        assertEquals("song", ExtractAudio.displayNameFor("song.webm"))
        assertEquals("noext", ExtractAudio.displayNameFor("noext"))
        assertEquals(".mp4", ExtractAudio.displayNameFor(".mp4"))
        assertEquals("a.verylongext", ExtractAudio.displayNameFor("a.verylongext"))
        assertEquals("odd.", ExtractAudio.displayNameFor("odd."))
    }

    // ---------- save / load ----------

    @Test
    fun snapshot_carriesTheExtractedLayerUnchanged() {
        // No new snapshot field exists for build 203: the extracted layer is a
        // plain TimelineClip in the tracks, so a snapshot built over the placed
        // lanes carries it field-for-field (the JSON layer is ProjectStore's,
        // unchanged — every field used already serialized before this build).
        val src = videoSource()
        val aud = ExtractAudio.detachedLayer(src, "aud")
        val tracks = LaneLayout.placeBelow(listOf(laneOf(src)), "vid", aud)
        val snap = ProjectSnapshot(tracks = tracks)
        val carried = snap.tracks.flatMap { it.clips }.first { it.id == "aud" }
        assertEquals(aud, carried)
        assertEquals(LayerType.AUDIO, carried.type)
        assertEquals(src.uri, carried.uri)
        assertEquals(src.trimStartMs, carried.trimStartMs)
    }
}
