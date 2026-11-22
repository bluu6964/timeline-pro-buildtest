package com.example

import com.example.model.FilmstripGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 259 — pins the filmstrip law ([FilmstripGeometry]): strip sizing
 * at the lane-height cap, tile counts (one frame per second, capped),
 * centre sampling times, and the trim/speed window the native renderer
 * mirrors (the same numbers are pinned natively in timeline_selftest).
 */
class FilmstripGeometryTest {

    @Test
    fun stripHeightIsTheComfortLaneHeight() {
        assertEquals(34, FilmstripGeometry.stripHeightPx(1f))
        assertEquals(68, FilmstripGeometry.stripHeightPx(2f))
        assertEquals(92, FilmstripGeometry.stripHeightPx(2.7f)) // ceil(91.8)
        assertEquals(16, FilmstripGeometry.stripHeightPx(0.1f)) // floor clamp
    }

    @Test
    fun tileCountsFollowOneFramePerSecondWithCaps() {
        val h = 68 // density 2
        assertEquals(1, FilmstripGeometry.tileCount(400L, false, h))     // sub-second source
        assertEquals(1, FilmstripGeometry.tileCount(1000L, false, h))
        assertEquals(10, FilmstripGeometry.tileCount(10_000L, false, h))
        assertEquals(11, FilmstripGeometry.tileCount(10_001L, false, h))  // ceil
        assertEquals(30, FilmstripGeometry.tileCount(30_000L, false, h))  // atlas width cap: 2048/68 = 30
        assertEquals(32, FilmstripGeometry.tileCount(60_000L, false, 34)) // MAX_TILES at density 1
        assertEquals(0, FilmstripGeometry.tileCount(0L, false, h))        // no source, no strip
        assertEquals(6, FilmstripGeometry.tileCount(123_456L, true, h))   // photos: fixed repeats
        assertEquals(6, FilmstripGeometry.tileCount(0L, true, h))
    }

    @Test
    fun tileTimesAreSliceCentres() {
        // 10 s over 10 tiles: centres at 0.5 s, 1.5 s, ..., 9.5 s.
        assertEquals(500L, FilmstripGeometry.tileTimeMs(10_000L, 10, 0))
        assertEquals(1500L, FilmstripGeometry.tileTimeMs(10_000L, 10, 1))
        assertEquals(9500L, FilmstripGeometry.tileTimeMs(10_000L, 10, 9))
        // One tile samples the middle of the source.
        assertEquals(2000L, FilmstripGeometry.tileTimeMs(4000L, 1, 0))
        assertEquals(0L, FilmstripGeometry.tileTimeMs(0L, 4, 0))
    }

    @Test
    fun stripWidthIsTilesTimesTileWidth() {
        assertEquals(680, FilmstripGeometry.stripWidthPx(10, 68))
        assertEquals(0, FilmstripGeometry.stripWidthPx(0, 68))
    }

    @Test
    fun windowMapsTrimAndSpeedOntoTheStrip() {
        // Plain window: source 2 s..6 s of 10 s.
        var w = FilmstripGeometry.window(10_000L, 2000L, 4000L, 1f)
        assertEquals(0.2f, w.first, 0.0001f)
        assertEquals(0.6f, w.second, 0.0001f)
        // Speed 2: a 2 s bar consumes 4 s of source.
        w = FilmstripGeometry.window(10_000L, 2000L, 2000L, 2f)
        assertEquals(0.2f, w.first, 0.0001f)
        assertEquals(0.6f, w.second, 0.0001f)
        // Overrunning the source end clamps at 1.
        w = FilmstripGeometry.window(10_000L, 9000L, 4000L, 1f)
        assertEquals(0.9f, w.first, 0.0001f)
        assertEquals(1f, w.second, 0.0001f)
        // No source range: empty window.
        w = FilmstripGeometry.window(0L, 0L, 4000L, 1f)
        assertEquals(0f, w.first, 0f)
        assertEquals(0f, w.second, 0f)
    }
}
