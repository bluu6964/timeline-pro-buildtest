package com.example.ui.util

import android.graphics.Bitmap
import com.example.model.BlendKind
import com.example.model.BlendSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Build 221: blend-mode thumbnails for the Blending & Opacity panel, computed with the
 * app's OWN blend maths — a line-by-line Kotlin port of the export shader's `blendFn`
 * (cpp/engine/renderer.cpp, kBlendFragment) and of its non-group compositing path, run
 * once per mode over a fixed, colourful sample pair (an HSV test-chart backdrop with a
 * grey ramp; a gradient disc + a translucent violet stripe as the source). Nothing is
 * approximated with brightness/contrast tweaks: the five Alight modes that have no
 * Compose equivalent (Linear Burn, Darker Color, Lighter Color, Soft Overlay, Diff)
 * use the shader's own formulas, exactly like every other mode.
 *
 * Matte modes follow the shader too: the cut factor m is computed from the layer's
 * premultiplied texel (luma uses Rec.709 weights over premultiplied rgb, as in the
 * shader — NOT the Compose preview filter, which reads straight rgb), and the result
 * is `mix(canvas, backdrop, m)`; the thumbnail's canvas stand-in is a grey checkerboard
 * (the universal "shows through" cue), the role uCanvas plays in the renderer.
 */
object BlendThumbs {

    /** Render resolution of one thumbnail (shown at 82 dp). */
    private const val N = 246

    private val cache = HashMap<Int, Bitmap>()

    /** The thumbnail bitmap of [spec]; computed once per mode, then cached. */
    @Synchronized
    fun bitmapFor(spec: BlendSpec): Bitmap =
        cache.getOrPut(spec.shaderId) { render(spec) }

    // ------------------------------------------------------------------
    // Sample pair (straight, non-premultiplied RGBA, channels 0..1)
    // ------------------------------------------------------------------

    private class Px(val r: Float, val g: Float, val b: Float, val a: Float)

    private fun hsv(hDeg: Float, s: Float, v: Float): Triple<Float, Float, Float> {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((hDeg / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            hDeg < 60f -> Triple(c, x, 0f)
            hDeg < 120f -> Triple(x, c, 0f)
            hDeg < 180f -> Triple(0f, c, x)
            hDeg < 240f -> Triple(0f, x, c)
            hDeg < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(r + m, g + m, b + m)
    }

    /** Backdrop: hue sweeps across, value falls downward; bottom strip = grey ramp. */
    private fun backdropAt(u: Float, v: Float): Px {
        if (v >= 0.86f) return Px(u, u, u, 1f)
        val (r, g, b) = hsv(u * 360f, 0.80f, 1f - 0.72f * (v / 0.86f))
        return Px(r, g, b, 1f)
    }

    /**
     * Source: a warm gradient disc (alpha 1) with a translucent violet diagonal stripe
     * (alpha 0.55) composited over it; transparent elsewhere. The alpha variety is what
     * makes the matte thumbnails read.
     */
    private fun sourceAt(u: Float, v: Float): Px {
        var r = 0f; var g = 0f; var b = 0f; var a = 0f
        val dx = u - 0.5f
        val dy = v - 0.44f
        if (dx * dx + dy * dy <= 0.30f * 0.30f) {
            val t = ((v - 0.14f) / 0.60f).coerceIn(0f, 1f)
            // #FFC98B (top) -> #EF4B2B (bottom)
            r = 1.00f + (0.937f - 1.00f) * t
            g = 0.788f + (0.294f - 0.788f) * t
            b = 0.545f + (0.169f - 0.545f) * t
            a = 1f
        }
        if (kotlin.math.abs(u - v) < 0.09f) {
            // #7C4DFF at alpha 0.55, source-over whatever the source already holds.
            val sa = 0.55f
            r = 0.486f * sa + r * a * (1f - sa)
            g = 0.302f * sa + g * a * (1f - sa)
            b = 1.000f * sa + b * a * (1f - sa)
            a = sa + a * (1f - sa)
        }
        // The disc/stripe maths above tracks premultiplied rgb for the stripe mix;
        // un-premultiply back to straight colour for the blend input.
        return if (a > 0.0001f) Px(r / a, g / a, b / a, a) else Px(0f, 0f, 0f, 0f)
    }

    // ------------------------------------------------------------------
    // The shader's maths, ported 1:1 (renderer.cpp kBlendFragment)
    // ------------------------------------------------------------------

    private fun lum(r: Float, g: Float, b: Float) = 0.3f * r + 0.59f * g + 0.11f * b

    private fun clipColor(r0: Float, g0: Float, b0: Float): Triple<Float, Float, Float> {
        var r = r0; var g = g0; var b = b0
        val l = lum(r, g, b)
        val n = min(min(r, g), b)
        val x = max(max(r, g), b)
        if (n < 0f) {
            val f = l / (l - n)
            r = l + (r - l) * f; g = l + (g - l) * f; b = l + (b - l) * f
        }
        if (x > 1f) {
            val f = (1f - l) / (x - l)
            r = l + (r - l) * f; g = l + (g - l) * f; b = l + (b - l) * f
        }
        return Triple(r, g, b)
    }

    private fun setLum(c: Triple<Float, Float, Float>, l: Float): Triple<Float, Float, Float> {
        val d = l - lum(c.first, c.second, c.third)
        return clipColor(c.first + d, c.second + d, c.third + d)
    }

    private fun sat(r: Float, g: Float, b: Float) = max(max(r, g), b) - min(min(r, g), b)

    private fun setSat(c: Triple<Float, Float, Float>, s: Float): Triple<Float, Float, Float> {
        val mx = max(max(c.first, c.second), c.third)
        val mn = min(min(c.first, c.second), c.third)
        if (mx <= mn) return Triple(0f, 0f, 0f)
        val f = s / (mx - mn)
        return Triple((c.first - mn) * f, (c.second - mn) * f, (c.third - mn) * f)
    }

    private fun overlayCh(b: Float, s: Float) =
        if (b <= 0.5f) 2f * b * s else 1f - 2f * (1f - b) * (1f - s)

    private fun softCh(b: Float, s: Float): Float {
        if (s <= 0.5f) return b - (1f - 2f * s) * b * (1f - b)
        val d = if (b <= 0.25f) ((16f * b - 12f) * b + 4f) * b else sqrt(b)
        return b + (2f * s - 1f) * (d - b)
    }

    private fun burnCh(b: Float, s: Float): Float {
        if (b >= 1f) return 1f
        if (s <= 0f) return 0f
        return 1f - min(1f, (1f - b) / s)
    }

    private fun softOverlayCh(b: Float, s: Float): Float {
        val t = if (b >= 0.5f) 1f else 0f // GLSL step(0.5, b)
        return t * (1f - (1f - b) * (1f - (s - 0.5f))) + (1f - t) * (b * (s + 0.5f))
    }

    private fun dodgeCh(b: Float, s: Float): Float {
        if (b <= 0f) return 0f
        if (s >= 1f) return 1f
        return min(1f, b / (1f - s))
    }

    /** blendFn(mode, backdrop, source) — straight colours, returns the blended colour. */
    private fun blendFn(mode: Int, b: Px, s: Px): Triple<Float, Float, Float> {
        fun ch(f: (Float, Float) -> Float) =
            Triple(f(b.r, s.r), f(b.g, s.g), f(b.b, s.b))
        return when (mode) {
            10 -> ch { x, y -> min(x, y) }                                    // Darken
            11 -> ch { x, y -> x * y }                                        // Multiply
            12 -> ch(::burnCh)                                                // Color Burn
            13 -> ch { x, y -> max(x + y - 1f, 0f) }                          // Linear Burn
            14 -> {                                                           // Darker Color
                val ls = sqrt(
                    (0.2126f * s.r) * (0.2126f * s.r) +
                        (0.7152f * s.g) * (0.7152f * s.g) +
                        (0.0722f * s.b) * (0.0722f * s.b)
                )
                val lb = sqrt(
                    (0.2126f * b.r) * (0.2126f * b.r) +
                        (0.7152f * b.g) * (0.7152f * b.g) +
                        (0.0722f * b.b) * (0.0722f * b.b)
                )
                if (ls > lb) Triple(b.r, b.g, b.b) else Triple(s.r, s.g, s.b)
            }
            20 -> ch { x, y -> max(x, y) }                                    // Lighten
            21 -> ch { x, y -> x + y - x * y }                                // Screen
            22 -> ch(::dodgeCh)                                               // Color Dodge
            23 -> ch { x, y -> min(x + y, 1f) }                               // Add
            24 -> {                                                           // Lighter Color
                val ls = sqrt(
                    (0.2126f * s.r) * (0.2126f * s.r) +
                        (0.7152f * s.g) * (0.7152f * s.g) +
                        (0.0722f * s.b) * (0.0722f * s.b)
                )
                val lb = sqrt(
                    (0.2126f * b.r) * (0.2126f * b.r) +
                        (0.7152f * b.g) * (0.7152f * b.g) +
                        (0.0722f * b.b) * (0.0722f * b.b)
                )
                if (ls > lb) Triple(s.r, s.g, s.b) else Triple(b.r, b.g, b.b)
            }
            30 -> ch(::overlayCh)                                             // Overlay
            31 -> ch(::softCh)                                                // Soft Light
            32 -> Triple(                                                     // Hard Light
                overlayCh(s.r, b.r), overlayCh(s.g, b.g), overlayCh(s.b, b.b)
            )
            33 -> ch(::softOverlayCh)                                         // Soft Overlay
            40 -> ch { x, y -> kotlin.math.abs(x - y) }                       // Difference
            41 -> ch { x, y -> x + y - 2f * x * y }                           // Exclusion
            42 -> ch { x, y -> max(x - y, 0f) }                               // Subtract
            43 -> ch { x, y -> kotlin.math.abs(x - y) }                       // Diff
            50 -> setLum(                                                     // Hue
                setSat(Triple(s.r, s.g, s.b), sat(b.r, b.g, b.b)),
                lum(b.r, b.g, b.b)
            )
            51 -> setLum(                                                     // Saturation
                setSat(Triple(b.r, b.g, b.b), sat(s.r, s.g, s.b)),
                lum(b.r, b.g, b.b)
            )
            52 -> setLum(Triple(s.r, s.g, s.b), lum(b.r, b.g, b.b))           // Color
            53 -> setLum(Triple(b.r, b.g, b.b), lum(s.r, s.g, s.b))           // Luminosity
            else -> Triple(s.r, s.g, s.b)                                     // Normal
        }
    }

    // ------------------------------------------------------------------
    // Compositing (the shader's non-group path at opacity 1)
    // ------------------------------------------------------------------

    private fun render(spec: BlendSpec): Bitmap {
        val pixels = IntArray(N * N)
        val cell = N / 8
        for (y in 0 until N) {
            val v = (y + 0.5f) / N
            for (x in 0 until N) {
                val u = (x + 0.5f) / N
                val bd = backdropAt(u, v)
                val src = sourceAt(u, v)
                val out: Triple<Float, Float, Float>
                if (spec.kind == BlendKind.MATTE) {
                    // The shader reads the PREMULTIPLIED layer texel for luma.
                    val lp = (src.r * src.a) * 0.2126f +
                        (src.g * src.a) * 0.7152f +
                        (src.b * src.a) * 0.0722f
                    val m = when (spec.shaderId) {
                        1 -> src.a              // Alpha Matte:  1 - (1 - a) = a
                        2 -> 1f - src.a         // Mask Invert:  1 - a
                        3 -> lp                 // Luma:         1 - 1 + lp
                        else -> 1f - lp         // Luma Invert:  1 - lp
                    }
                    val behind = if (((x / cell) + (y / cell)) % 2 == 0) 0.34f else 0.22f
                    out = Triple(
                        behind * (1f - m) + bd.r * m,
                        behind * (1f - m) + bd.g * m,
                        behind * (1f - m) + bd.b * m
                    )
                } else {
                    val a = src.a
                    val f = blendFn(spec.shaderId, bd, src)
                    out = Triple(
                        (1f - a) * bd.r + a * f.first,
                        (1f - a) * bd.g + a * f.second,
                        (1f - a) * bd.b + a * f.third
                    )
                }
                fun q(c: Float) = (c.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                pixels[y * N + x] =
                    (0xFF shl 24) or (q(out.first) shl 16) or
                        (q(out.second) shl 8) or q(out.third)
            }
        }
        val bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, N, 0, 0, N, N)
        return bmp
    }
}
