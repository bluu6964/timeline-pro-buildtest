package com.example

import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.model.TimelineSnapshotKey
import com.example.model.TimelineTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 264 — pins the timeline snapshot change key ([TimelineSnapshotKey]):
 * identical snapshot inputs must give identical keys (the engine then
 * skips the rebuild — the playback/scrub lag fix), and changing anything
 * the snapshot build reads must change the key (the timeline must never
 * go stale). The playhead is deliberately NOT an input: the native draw
 * reads it live per frame.
 */
class TimelineSnapshotKeyTest {

    private fun clip(
        id: String = "c1",
        startMs: Long = 1000L,
        durationMs: Long = 4000L,
        type: LayerType = LayerType.VIDEO,
        uri: String? = "content://media/1"
    ) = TimelineClip(
        id = id, name = "Clip $id", type = type,
        startMs = startMs, durationMs = durationMs, color = 0xFF0000FF, uri = uri
    )

    private fun key(
        tracks: List<TimelineTrack> = listOf(TimelineTrack("t1", "Lane", clips = listOf(clip()))),
        selectedClipId: String? = null,
        multiSelectIds: Set<String> = emptySet(),
        waveforms: Map<String, FloatArray> = mapOf("content://media/1" to floatArrayOf(0.1f, 0.5f, 0.2f)),
        totalMs: Long = 60_000L,
        beatMs: List<Long> = listOf(500L, 1500L),
        rulerKeysMs: List<Long> = emptyList(),
        exportInMs: Long? = null,
        exportOutMs: Long? = null,
        pxPerMs: Float = 0.045f,
        comfort: Boolean = false,
        bottomPadPx: Float = 12f
    ): Long = TimelineSnapshotKey.compute(
        tracks, selectedClipId, multiSelectIds, waveforms, totalMs,
        beatMs, rulerKeysMs, exportInMs, exportOutMs, pxPerMs, comfort, bottomPadPx
    )

    @Test
    fun identicalInputsGiveIdenticalKeys() {
        // Fresh track/clip instances with equal content (a re-emitted state
        // list), sharing the published waveform array — the app's pattern:
        // peaks publish once per uri and are never re-created per emission.
        val peaks = floatArrayOf(0.1f, 0.5f, 0.2f)
        val waves = mapOf("content://media/1" to peaks)
        assertEquals(key(waveforms = waves), key(waveforms = waves))
    }

    @Test
    fun republishedWaveformArrayChangesKey() {
        // A NEW peaks array instance for the same uri is a new publication
        // (a finished analysis) — the key changes even for equal content,
        // so the rebuilt snapshot picks the waveform up.
        val waves = mapOf("content://media/1" to floatArrayOf(0.1f, 0.5f, 0.2f))
        assertTrue(key() != key(waveforms = waves))
    }

    @Test
    fun sameInstancesGiveIdenticalKeys() {
        val tracks = listOf(TimelineTrack("t1", "Lane", clips = listOf(clip())))
        val waves = mapOf("content://media/1" to floatArrayOf(0.1f, 0.5f, 0.2f))
        assertEquals(key(tracks = tracks, waveforms = waves), key(tracks = tracks, waveforms = waves))
    }

    @Test
    fun clipGeometryChangeChangesKey() {
        val moved = listOf(TimelineTrack("t1", "Lane", clips = listOf(clip(startMs = 2000L))))
        assertTrue(key() != key(tracks = moved))
        val resized = listOf(TimelineTrack("t1", "Lane", clips = listOf(clip(durationMs = 5000L))))
        assertTrue(key() != key(tracks = resized))
    }

    @Test
    fun clipRenameChangesKey() {
        val renamed = listOf(
            TimelineTrack("t1", "Lane", clips = listOf(clip().copy(name = "Other")))
        )
        assertTrue(key() != key(tracks = renamed))
    }

    @Test
    fun clipAddedOrRemovedChangesKey() {
        val two = listOf(
            TimelineTrack("t1", "Lane", clips = listOf(clip(), clip(id = "c2", startMs = 6000L)))
        )
        assertTrue(key() != key(tracks = two))
        assertTrue(key() != key(tracks = emptyList()))
    }

    @Test
    fun selectionChangesKey() {
        assertTrue(key() != key(selectedClipId = "c1"))
        assertTrue(key() != key(multiSelectIds = setOf("c1")))
    }

    @Test
    fun zoomAndLayoutChangeKey() {
        assertTrue(key() != key(pxPerMs = 0.09f))
        assertTrue(key() != key(comfort = true))
        assertTrue(key() != key(bottomPadPx = 40f))
    }

    @Test
    fun rulerInputsChangeKey() {
        assertTrue(key() != key(totalMs = 30_000L))
        assertTrue(key() != key(beatMs = listOf(500L)))
        assertTrue(key() != key(rulerKeysMs = listOf(1000L)))
        assertTrue(key() != key(exportInMs = 100L, exportOutMs = 900L))
    }

    @Test
    fun waveformArrivalChangesKey() {
        // No waveform yet -> empty map.
        assertTrue(key() != key(waveforms = emptyMap()))
        // A finished analysis publishes a NEW array with different peaks.
        val done = mapOf("content://media/1" to floatArrayOf(0.9f, 0.5f, 0.2f))
        assertTrue(key() != key(waveforms = done))
        // A longer peaks array for the same uri.
        val longer = mapOf("content://media/1" to floatArrayOf(0.1f, 0.5f, 0.2f, 0.4f))
        assertTrue(key() != key(waveforms = longer))
    }
}
