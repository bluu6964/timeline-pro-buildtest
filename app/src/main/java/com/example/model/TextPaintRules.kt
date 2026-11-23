package com.example.model

import com.example.engine.ValueLawsNative
import kotlin.math.roundToInt

/**
 * The ONE text-paint rule every text painter follows (build 212): the Compose preview
 * ([com.example.ui.components.TextLayerContent]), the Android Canvas painter
 * ([com.example.engine.LayerPainter]) and the native-engine parameters
 * ([com.example.engine.TextNative]) used to each keep a hand-copied version of the
 * background-fill and letter-spacing rules, and the copies had drifted — the Compose
 * painter multiplied the stored colour alpha by the Background opacity slider while
 * the Canvas / native painters discarded the stored alpha and used the slider alone.
 *
 * The rule (the Text edit Background card's contract): the painted background is
 * exactly `bgColor × bgOpacity` — the colour's own alpha (a stored alpha byte of 0
 * reads as opaque, the ARGB convention of the colour fields) scaled by the opacity
 * percent — and at opacity 0 NOTHING is painted. Letter spacing is one fraction of
 * the font size everywhere: an em fraction for the Paint / Compose painters, the
 * same fraction times the font size in px for the native engine.
 */
object TextPaintRules {

    /**
     * The background to paint as a packed ARGB int, or null when no background may be
     * painted at all (opacity 0, or an opacity so small the resulting alpha is 0).
     */
    fun bgArgb(bgColor: Long, bgOpacity: Float): Int? {
        // Native since build 247 (value_laws.cpp); an empty array is the null case.
        val out = ValueLawsNative.nativeTextBgArgb(bgColor, bgOpacity)
        return if (out.isEmpty()) null else out[0]
    }

    /** Letter spacing as an em fraction (Android Paint.letterSpacing / Compose Em semantics). */
    fun letterSpacingEm(letterSpacingPercent: Float): Float =
        ValueLawsNative.nativeTextLetterSpacingEm(letterSpacingPercent)

    /** Letter spacing in px for a font size in px (native engine semantics). */
    fun letterSpacingPx(fontPx: Float, letterSpacingPercent: Float): Float =
        ValueLawsNative.nativeTextLetterSpacingPx(fontPx, letterSpacingPercent)

    /**
     * Build 271: the ONE font-size law. The historical size is 11%
     * of the frame width; [TimelineClip.textSizePercent] multiplies
     * it (100 = the historical size). The panel's Size slider shows
     * the reference's 12–44 "px" scale mapped as percent = px × 5
     * (20 px = 100%). Every painter (native params, Canvas, Compose)
     * computes fontPx through here.
     */
    fun fontPx(frameW: Float, textSizePercent: Float): Float =
        frameW * 0.11f * (textSizePercent / 100f)

    /**
     * Build 271: the Case display transform (Styles → Case). Modes:
     * 0 = as typed, 1 = UPPER, 2 = lower, 3 = Capitalize Each Word
     * (the reference's \b\w rule: a word character at a word start is
     * uppercased, the rest of the word is left as typed). DISPLAY
     * ONLY — the stored text is never rewritten; every painter
     * transforms the string it hands to layout.
     */
    fun caseTransform(text: String, textCase: Int): String = when (textCase) {
        1 -> text.uppercase()
        2 -> text.lowercase()
        3 -> {
            val sb = StringBuilder(text.length)
            var wordStart = true
            for (ch in text) {
                val isWord = ch.isLetterOrDigit() || ch == '_'
                sb.append(if (wordStart && isWord) ch.uppercaseChar() else ch)
                wordStart = !isWord
            }
            sb.toString()
        }
        else -> text
    }

    /** An ARGB colour with its alpha folded by [percent] (0..100) —
     *  the fill-opacity fold every painter applies for
     *  [TimelineClip.textOpacity]. A stored 0x00 alpha byte reads as
     *  opaque, like [bgArgb]. */
    fun alphaFold(argb: Long, percent: Float): Long {
        val stored = ((argb ushr 24) and 0xFF).toInt()
        val a = if (stored == 0) 255 else stored
        val f = (percent / 100f).coerceIn(0f, 1f)
        val out = (a * f).roundToInt().coerceIn(0, 255)
        return (argb and 0x00FFFFFF) or (out.toLong() shl 24)
    }
}
