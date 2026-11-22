package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.example.model.FilmstripGeometry
import kotlin.math.max

/**
 * Build 259 — the timeline's filmstrip thumbnails: one strip bitmap per
 * media source (frame tiles sampled across the source's full range, per
 * [FilmstripGeometry]), shelf-packed into a shared atlas bitmap that the
 * native timeline renderer blits from (the TlLabelAtlas pattern, but for
 * pictures instead of text).
 *
 * Threading: all atlas state lives on the MAIN thread ([entryFor] is
 * called from the snapshot build; strip commits land there too). The
 * expensive part — MediaMetadataRetriever frame grabs / image decode —
 * runs on a single background thread and only PRODUCES a standalone
 * strip bitmap; it never touches the atlas. Nothing is ever decoded on
 * the render path: a clip whose strip is not ready draws its plain body
 * fill as the placeholder.
 *
 * The atlas never resets: when it fills up, further strips simply never
 * appear (their clips keep the placeholder). Indices therefore stay
 * valid for the renderer's uploaded copy for the engine's lifetime.
 */
internal class TlThumbAtlas(
    context: Context,
    private val density: Float
) {
    private val appContext = context.applicationContext
    val bitmap: Bitmap = Bitmap.createBitmap(
        FilmstripGeometry.ATLAS_W, FilmstripGeometry.ATLAS_H, Bitmap.Config.ARGB_8888
    )
    private val canvas = Canvas(bitmap)
    private val rects = ArrayList<Int>()  // x, y, w, h per entry
    private val cache = HashMap<String, Int>()
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private var shelfX = 0
    private var shelfY = 0
    private var shelfH = 0

    @Volatile private var closed = false
    private val thread = HandlerThread("am-tl-thumbs").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    /** Set by the engine: invoked (on the main thread) after a strip lands in the atlas. */
    var onStripReady: () -> Unit = {}

    val entryCount: Int get() = rects.size / 4

    fun rectArray(): IntArray = rects.toIntArray()

    private fun keyFor(uri: String, srcDurMs: Long, isImage: Boolean): String =
        if (isImage) "img|$uri" else "vid|$uri|$srcDurMs"

    /**
     * The atlas entry for this media's strip, or -1 while it is being
     * extracted (the caller draws the placeholder). Kicks the async
     * extraction on first sight. Main thread only.
     */
    fun entryFor(uri: String, srcDurMs: Long, isImage: Boolean): Int {
        if (closed) return -1
        val key = keyFor(uri, srcDurMs, isImage)
        cache[key]?.let { return it }
        if (key in pending || key in failed) return -1
        val stripH = FilmstripGeometry.stripHeightPx(density)
        val tiles = FilmstripGeometry.tileCount(srcDurMs, isImage, stripH)
        if (tiles <= 0) {
            failed.add(key)
            return -1
        }
        pending.add(key)
        worker.post { extract(key, uri, srcDurMs, isImage, stripH, tiles) }
        return -1
    }

    // ---- Background thread: build ONE standalone strip bitmap ----

    private fun extract(
        key: String, uri: String, srcDurMs: Long, isImage: Boolean,
        stripH: Int, tiles: Int
    ) {
        val stripW = FilmstripGeometry.stripWidthPx(tiles, stripH)
        val strip = try {
            val bmp = Bitmap.createBitmap(stripW, stripH, Bitmap.Config.ARGB_8888)
            val ok = if (isImage) fillImageStrip(bmp, uri, stripH, tiles)
            else fillVideoStrip(bmp, uri, srcDurMs, stripH, tiles)
            if (ok) bmp else {
                bmp.recycle()
                null
            }
        } catch (_: Throwable) {
            null
        }
        main.post {
            pending.remove(key)
            if (closed || strip == null) {
                strip?.recycle()
                if (strip == null) failed.add(key)
                return@post
            }
            commit(key, strip)
        }
    }

    /** Draw [frame] centre-cropped into the tile rect ([tileX], 0, tileW, stripH) of [strip]. */
    private fun drawTile(strip: Canvas, paint: Paint, frame: Bitmap, tileX: Int, tileW: Int, stripH: Int) {
        val fw = frame.width.toFloat()
        val fh = frame.height.toFloat()
        if (fw <= 0f || fh <= 0f) return
        val scale = max(tileW / fw, stripH / fh)
        val sw = tileW / scale
        val sh = stripH / scale
        val src = Rect(((fw - sw) / 2f).toInt(), ((fh - sh) / 2f).toInt(), ((fw + sw) / 2f).toInt(), ((fh + sh) / 2f).toInt())
        val dst = RectF(tileX.toFloat(), 0f, (tileX + tileW).toFloat(), stripH.toFloat())
        strip.drawBitmap(frame, src, dst, paint)
    }

    private fun fillVideoStrip(
        bmp: Bitmap, uri: String, srcDurMs: Long, stripH: Int, tiles: Int
    ): Boolean {
        val strip = Canvas(bmp)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val tileW = FilmstripGeometry.tileWidthPx(stripH)
        val retriever = MediaMetadataRetriever()
        var last: Bitmap? = null
        var drewAny = false
        try {
            retriever.setDataSource(appContext, Uri.parse(uri))
            for (i in 0 until tiles) {
                if (closed) return false
                val tUs = FilmstripGeometry.tileTimeMs(srcDurMs, tiles, i) * 1000L
                val frame = try {
                    retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (_: Throwable) {
                    null
                }
                val use = frame ?: last
                if (use != null) {
                    drawTile(strip, paint, use, i * tileW, tileW, stripH)
                    drewAny = true
                }
                if (frame != null) {
                    if (last !== frame) last?.recycle()
                    last = frame
                }
            }
        } finally {
            last?.recycle()
            try { retriever.release() } catch (_: Throwable) { }
        }
        return drewAny
    }

    private fun fillImageStrip(bmp: Bitmap, uri: String, stripH: Int, tiles: Int): Boolean {
        val parsed = Uri.parse(uri)
        // Bounds first, then a decode sampled down so the long side is at
        // most 1024 px — the tiles never show more than the strip height.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val still = appContext.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return false
        try {
            val strip = Canvas(bmp)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val tileW = FilmstripGeometry.tileWidthPx(stripH)
            for (i in 0 until tiles) drawTile(strip, paint, still, i * tileW, tileW, stripH)
        } finally {
            still.recycle()
        }
        return true
    }

    // ---- Main thread: pack a finished strip into the atlas ----

    private fun commit(key: String, strip: Bitmap) {
        val w = strip.width
        val h = strip.height
        if (shelfX + w > FilmstripGeometry.ATLAS_W) {
            shelfX = 0
            shelfY += shelfH
            shelfH = 0
        }
        if (shelfY + h > FilmstripGeometry.ATLAS_H || w > FilmstripGeometry.ATLAS_W) {
            // Atlas full: this strip (and any like it) keeps the placeholder.
            failed.add(key)
            strip.recycle()
            return
        }
        val x = shelfX
        val y = shelfY
        canvas.drawBitmap(strip, x.toFloat(), y.toFloat(), null)
        strip.recycle()
        rects.add(x); rects.add(y); rects.add(w); rects.add(h)
        cache[key] = entryCount - 1
        shelfX += w
        shelfH = max(shelfH, h)
        onStripReady()
    }

    fun release() {
        closed = true
        thread.quitSafely()
    }
}
