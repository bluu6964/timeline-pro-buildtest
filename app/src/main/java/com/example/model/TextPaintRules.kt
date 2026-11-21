package com.example.model

import com.example.engine.ValueLawsNative

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
}
