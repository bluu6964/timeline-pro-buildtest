package com.example

import com.example.model.TextPaintRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Build 212: the one text-paint rule (model/TextPaintRules.kt) that the Compose preview,
 * the Canvas painter and the native-engine parameters all share — the painted background
 * is exactly bgColor × bgOpacity (nothing at 0), and letter spacing is one em fraction
 * of the font size in every painter.
 */
class TextPaintRulesTest {

    @Test
    fun backgroundAtZeroOpacityPaintsNothing() {
        assertNull(TextPaintRules.bgArgb(0xFF336699, 0f))
        assertNull(TextPaintRules.bgArgb(0xFF336699, -5f))
        // A stored colour with its own alpha still paints nothing at slider 0.
        assertNull(TextPaintRules.bgArgb(0x80336699, 0f))
    }

    @Test
    fun backgroundAtFullOpacityIsTheStoredColour() {
        assertEquals(0xFF336699.toInt(), TextPaintRules.bgArgb(0xFF336699, 100f))
        // The stored colour's own alpha is kept (bgColor × bgOpacity, both factors count).
        assertEquals(0x80336699.toInt(), TextPaintRules.bgArgb(0x80336699, 100f))
    }

    @Test
    fun backgroundAlphaScalesWithTheSlider() {
        // Opaque colour at 50% -> alpha 128 (rounded), RGB untouched.
        assertEquals(0x80336699.toInt(), TextPaintRules.bgArgb(0xFF336699, 50f))
        // Translucent colour at 50% -> half of its own alpha (0x80 -> 0x40).
        assertEquals(0x40336699.toInt(), TextPaintRules.bgArgb(0x80336699, 50f))
        // Opacity above 100 clamps to the colour itself.
        assertEquals(0xFF336699.toInt(), TextPaintRules.bgArgb(0xFF336699, 140f))
    }

    @Test
    fun storedZeroAlphaByteReadsAsOpaque() {
        // Colour fields are ARGB; a 0x00 alpha byte is the "no alpha stored" form
        // (e.g. a 0xRRGGBB literal) and reads as opaque, like the painters' argb().
        assertEquals(0xFF336699.toInt(), TextPaintRules.bgArgb(0x00336699, 100f))
        assertEquals(0x80336699.toInt(), TextPaintRules.bgArgb(0x00336699, 50f))
    }

    // ── Build 271: the size law, the Case transform, the opacity fold ──

    @Test
    fun fontSizeLawIsElevenPercentTimesTheStoredMultiplier() {
        // 100% is the historical size: 11% of the frame width.
        assertEquals(110f, TextPaintRules.fontPx(1000f, 100f), 1e-4f)
        // The panel's Size slider: 20 px = 100%, so 12 px = 60% and
        // 44 px = 220%.
        assertEquals(66f, TextPaintRules.fontPx(1000f, 60f), 1e-4f)
        assertEquals(242f, TextPaintRules.fontPx(1000f, 220f), 1e-4f)
    }

    @Test
    fun caseTransformModes() {
        val s = "hello World 42x"
        assertEquals(s, TextPaintRules.caseTransform(s, 0))
        assertEquals("HELLO WORLD 42X", TextPaintRules.caseTransform(s, 1))
        assertEquals("hello world 42x", TextPaintRules.caseTransform(s, 2))
        // Capitalize: word starts uppercased, the rest left as typed
        // (the reference's \b\w rule) — "42x" keeps its lowercase x
        // because the word starts at the digit.
        assertEquals("Hello World 42x", TextPaintRules.caseTransform(s, 3))
        assertEquals("A B-C D", TextPaintRules.caseTransform("a b-c d", 3))
        // Unknown modes are the identity.
        assertEquals(s, TextPaintRules.caseTransform(s, 9))
    }

    @Test
    fun alphaFoldScalesTheAlphaByteOnly() {
        assertEquals(0xFF336699L, TextPaintRules.alphaFold(0xFF336699, 100f))
        assertEquals(0x80336699L, TextPaintRules.alphaFold(0xFF336699, 50f))
        assertEquals(0x00336699L, TextPaintRules.alphaFold(0xFF336699, 0f))
        // A stored translucent colour folds from its own alpha.
        assertEquals(0x40336699L, TextPaintRules.alphaFold(0x80336699, 50f))
        // A stored 0x00 alpha byte reads as opaque, like bgArgb.
        assertEquals(0x80336699L, TextPaintRules.alphaFold(0x00336699, 50f))
    }

    @Test
    fun letterSpacingIsOneFractionEverywhere() {
        assertEquals(0f, TextPaintRules.letterSpacingEm(0f), 0f)
        assertEquals(0.4f, TextPaintRules.letterSpacingEm(40f), 1e-6f)
        assertEquals(-0.1f, TextPaintRules.letterSpacingEm(-10f), 1e-6f)
        // The px form is the same fraction of the font size (native engine semantics).
        assertEquals(40f, TextPaintRules.letterSpacingPx(100f, 40f), 1e-4f)
        assertEquals(-12f, TextPaintRules.letterSpacingPx(120f, -10f), 1e-4f)
        assertEquals(
            TextPaintRules.letterSpacingPx(96f, 25f),
            96f * TextPaintRules.letterSpacingEm(25f),
            1e-4f
        )
    }
}
