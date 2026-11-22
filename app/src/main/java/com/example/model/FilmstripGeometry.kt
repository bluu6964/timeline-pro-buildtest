package com.example.model

import kotlin.math.ceil

/**
 * Build 259 — the timeline filmstrip law: how a video / photo layer's
 * thumbnail strip is sized, sampled and windowed.
 *
 * A strip is ONE horizontal bitmap per media source, covering the source's
 * FULL range [0, sourceDurationMs] as [tileCount] square frame tiles,
 * centre-sampled at [tileTimeMs]. Strips live shelf-packed in a shared
 * atlas bitmap (engine/TimelineThumbs.kt); the native timeline renderer
 * blits the sub-window the bar currently shows — [window] — so trims and
 * speed changes only move the window, they never re-extract. A clip whose
 * strip is not ready yet keeps the plain body fill (with the build-224
 * fake cell shading) as its placeholder.
 *
 * Resolution is capped at the lane height: tiles are [stripHeightPx]
 * squares (the comfort lane height, 34 dp), never the source's pixels.
 */
object FilmstripGeometry {

    /** The shared thumbnail atlas (same footprint as the label atlas). */
    const val ATLAS_W = 2048
    const val ATLAS_H = 1024

    /** One frame tile per second of source, before the caps below. */
    const val TILE_MS = 1000L

    /** Hard cap on tiles per strip (very long sources sample coarser). */
    const val MAX_TILES = 32

    /** Photo strips repeat the still this many times across the bar. */
    const val IMAGE_TILES = 6

    /** The strip height (px): the comfort lane height (34 dp) at [density], clamped sane. */
    fun stripHeightPx(density: Float): Int =
        ceil(34.0 * density).toInt().coerceIn(16, 512)

    /** Tiles are squares of the strip height (frames are centre-cropped into them). */
    fun tileWidthPx(stripHeightPx: Int): Int = stripHeightPx

    /**
     * How many frame tiles [srcDurMs] of source gets: one per [TILE_MS],
     * at least 1, capped by [MAX_TILES] and by what fits the atlas width.
     * Photos always get [IMAGE_TILES] (the same still repeated).
     */
    fun tileCount(srcDurMs: Long, isImage: Boolean, stripHeightPx: Int): Int {
        if (isImage) return IMAGE_TILES
        if (srcDurMs <= 0L) return 0
        val byTime = (srcDurMs + TILE_MS - 1) / TILE_MS
        val byWidth = ATLAS_W / maxOf(1, tileWidthPx(stripHeightPx))
        return byTime.coerceIn(1L, minOf(MAX_TILES.toLong(), maxOf(1, byWidth).toLong())).toInt()
    }

    /** The strip's bitmap width (px) for [tiles] tiles. */
    fun stripWidthPx(tiles: Int, stripHeightPx: Int): Int = tiles * tileWidthPx(stripHeightPx)

    /** The source time (ms) tile [i] samples: the centre of its source slice. */
    fun tileTimeMs(srcDurMs: Long, tiles: Int, i: Int): Long {
        if (tiles <= 0 || srcDurMs <= 0L) return 0L
        return (2L * i + 1L) * srcDurMs / (2L * tiles)
    }

    /**
     * The strip window a bar shows, as fractions of the strip's width:
     * the source range [trimStartMs, trimStartMs + durMs × speed] mapped
     * onto [0, srcDurMs], clamped to the source. (0, 0) when there is no
     * source range to show. The C++ renderer computes the same window
     * (timeline_render.cpp thumbWindow, pinned by timeline_selftest).
     */
    fun window(srcDurMs: Long, trimStartMs: Long, durMs: Long, speed: Float): Pair<Float, Float> {
        if (srcDurMs <= 0L) return 0f to 0f
        val dur = srcDurMs.toDouble()
        val sp = if (speed > 0f) speed.toDouble() else 1.0
        val from = (trimStartMs.toDouble() / dur).coerceIn(0.0, 1.0)
        val to = ((trimStartMs.toDouble() + durMs.toDouble() * sp) / dur).coerceIn(from, 1.0)
        return from.toFloat() to to.toFloat()
    }
}
