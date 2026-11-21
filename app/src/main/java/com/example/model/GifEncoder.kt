package com.example.model

import com.example.engine.GifNative
import com.example.engine.ValueLawsNative
import java.io.OutputStream
import kotlin.math.max

/**
 * Animated GIF89a export, pure Kotlin (build 189 — production-push queue item 2).
 *
 * Three pieces, all free of Android types so the harness can pin them:
 *  - [GifQuantizer]: one frame of RGBA pixels -> a palette of at most 256 colours
 *    (median cut over a 5-bit-per-channel histogram) + one palette index per pixel.
 *    Every histogram bin belongs to exactly one palette box, so mapping a frame to
 *    indices is a table lookup, never a nearest-colour search.
 *  - [GifLzw]: the GIF LZW compressor (clear codes, code-width growth to 12 bits,
 *    a clear + dictionary reset when the 4096-entry table fills).
 *  - [GifWriter]: the streaming GIF89a container over an [OutputStream] — header,
 *    NETSCAPE2.0 loop-forever block, then per frame a Graphic Control Extension
 *    (delay, no transparency) and an Image Descriptor carrying that frame's own
 *    local colour table.
 *
 * The rendered frames are opaque composites (the project canvas colour is behind
 * every layer, exactly like the MP4 export), so no transparency flag is written —
 * there is no alpha to honour, and inventing one would fake a transparent GIF.
 */

/** Median-cut colour quantization for one frame. Deterministic: same input, same palette. */
object GifQuantizer {

    /** [palette] entries are 0xRRGGBB, the size a power of two in 2..256; [indices] row-major, top row first. */
    class Result(val palette: IntArray, val indices: ByteArray)

    /**
     * Quantizes [rgba] (4 bytes per pixel, R,G,B,A — alpha ignored, frames are opaque).
     * [bottomUp] = the buffer's first row is the picture's BOTTOM row (GL readback order);
     * the returned indices are always top row first.
     */
    fun quantize(rgba: ByteArray, width: Int, height: Int, maxColors: Int = 256, bottomUp: Boolean = false): Result {
        val count = width * height
        require(width >= 1 && height >= 1) { "empty frame" }
        require(rgba.size >= count * 4) { "pixel buffer too small" }
        val limit = maxColors.coerceIn(2, 256)
        val indices = ByteArray(count)
        val palette = GifNative.nativeQuantize(rgba, width, height, limit, bottomUp, indices)
            ?: throw IllegalStateException("nativeQuantize returned null")
        return Result(palette, indices)
    }
}

/** The GIF LZW compressor: [indices] (unsigned bytes) -> the compressed data stream (no sub-block chunking). */
object GifLzw {

    fun compress(indices: ByteArray, minCodeSize: Int): ByteArray {
        val min = minCodeSize.coerceIn(2, 8)
        return GifNative.nativeLzwCompress(indices, min)
            ?: throw IllegalStateException("nativeLzwCompress returned null")
    }
}

/**
 * Streaming GIF89a writer. The header (with the loop-forever block) is written on
 * construction; [addFrame] appends one frame with its own local colour table;
 * [finish] writes the trailer and flushes (the caller still owns closing the stream).
 */
class GifWriter(private val out: OutputStream, val width: Int, val height: Int) {

    init {
        require(width in 1..65535 && height in 1..65535) { "bad GIF size ${width}x$height" }
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0x70) // no global colour table; 8-bit colour resolution
        out.write(0)    // background colour index (unused, no GCT)
        out.write(0)    // pixel aspect ratio
        // NETSCAPE2.0 looping extension, loop count 0 = forever.
        out.write(0x21); out.write(0xFF); out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(0x03); out.write(0x01); writeShort(0); out.write(0)
    }

    /**
     * One frame: [indices] row-major top-down (width × height entries), [palette]
     * 0xRRGGBB entries with a power-of-two size in 2..256 (as [GifQuantizer] returns),
     * [delayCs] the frame delay in hundredths of a second.
     */
    fun addFrame(indices: ByteArray, palette: IntArray, delayCs: Int) {
        require(indices.size == width * height) { "frame has ${indices.size} pixels, expected ${width * height}" }
        require(palette.size in 2..256 && (palette.size and (palette.size - 1)) == 0) {
            "palette size ${palette.size} is not a power of two in 2..256"
        }
        // Graphic Control Extension: disposal 1 (do not dispose), no transparency.
        out.write(0x21); out.write(0xF9); out.write(0x04)
        out.write(0x04)
        writeShort(delayCs.coerceIn(0, 65535))
        out.write(0); out.write(0)
        // Image Descriptor with a local colour table.
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        var log2 = 0
        while ((1 shl (log2 + 1)) < palette.size) log2++
        out.write(0x80 or log2) // LCT present, size = 2^(log2+1)
        for (rgb in palette) {
            out.write((rgb shr 16) and 0xFF)
            out.write((rgb shr 8) and 0xFF)
            out.write(rgb and 0xFF)
        }
        val minCodeSize = max(2, log2 + 1)
        out.write(minCodeSize)
        val data = GifLzw.compress(indices, minCodeSize)
        var off = 0
        while (off < data.size) {
            val n = minOf(255, data.size - off)
            out.write(n)
            out.write(data, off, n)
            off += n
        }
        out.write(0) // block terminator
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}

/** Sizes, delays and pixel conversion for the raster exports (GIF + PNG sequence), shared by the exporter and its options UI. */
object GifExport {

    /** GIF frame rate default (the export dialog's GIF rate starts here). */
    const val DEFAULT_FPS = 15
    const val MIN_FPS = 5
    const val MAX_FPS = 30

    /** Longest side of a GIF frame: past this, a phone GIF is mostly download time. */
    const val MAX_SIDE = 720

    /**
     * Frame cap so a long project cannot explode into a multi-hundred-MB GIF
     * (at the 15 fps default this is 100 seconds of project). The exporter refuses
     * past it with a message instead of producing the file.
     */
    const val MAX_FRAMES = 1500

    /** Frame delay in hundredths of a second for [fps], at least 2 (viewers force 0–1 up to ~100 ms). */
    fun delayCs(fps: Int): Int = ValueLawsNative.nativeGifDelayCs(fps)

    /** [srcW]×[srcH] scaled to fit [MAX_SIDE] on the longest side, both sides even and ≥ 2; unchanged when already small. */
    fun gifSize(srcW: Int, srcH: Int): Pair<Int, Int> {
        val out = ValueLawsNative.nativeGifSize(srcW, srcH)
        return out[0] to out[1]
    }

    /** GL readback (RGBA, bottom row first) -> ARGB ints, top row first, alpha forced opaque (the frame is a finished composite). */
    fun rgbaBottomUpToArgb(rgba: ByteArray, width: Int, height: Int): IntArray {
        if (width <= 0 || height <= 0) return IntArray(0)
        return GifNative.nativeRgbaBottomUpToArgb(rgba, width, height)
            ?: throw IllegalStateException("nativeRgbaBottomUpToArgb returned null")
    }
}
