package com.example.model

/**
 * Build 268 — the Text panel's Templates: designed one-tap looks as
 * DATA BUNDLES over the app's existing text machinery only. A bundle
 * sets the font (a catalogue face — system faces always exist, Google
 * faces download on first show like any Fonts-tab pick), the fill,
 * the stroke, the shadow and a background shape from the Bubbles set
 * (TEXT_BG_*) with its colour / padding / corner. Nothing here needs
 * a new renderer: every field is one the text engines already draw.
 *
 * Designs the engines CANNOT express are deliberately absent: no
 * torn-paper / texture backgrounds (no texture fill exists for text
 * backgrounds), no starburst container (no such background shape).
 *
 * Applying a bundle is one [TimelineClip] copy through the Text
 * panel's onUpdate, inside the edit session's single undo step
 * (beginTextEdit pushes it when the panel opens).
 */
data class TextTemplate(
    val name: String,
    val fontFamily: String,
    val fontWeight: Int,
    val fill: Long,
    val strokeThickness: Float = 0f,
    val strokeColor: Long = 0xFF000000,
    val shadowOpacity: Float = 0f,
    val shadowBlur: Float = 0f,
    val shadowDistance: Float = 0f,
    val shadowAngle: Float = 45f,
    val shadowColor: Long = 0xFF000000,
    val bgShape: Int = TEXT_BG_NONE,
    val bgColor: Long = 0xFF000000,
    val bgPadding: Float = 30f,
    val bgCorner: Float = 20f
) {
    fun applyTo(c: TimelineClip): TimelineClip = c.copy(
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        textColor = fill,
        fillMode = FILL_SOLID,
        textStrokeThickness = strokeThickness,
        textStrokeColor = strokeColor,
        shadowOpacity = shadowOpacity,
        shadowBlur = shadowBlur,
        shadowDistance = shadowDistance,
        shadowAngle = shadowAngle,
        shadowColor = shadowColor,
        bgShape = bgShape,
        bgColor = bgColor,
        bgOpacity = if (bgShape == TEXT_BG_NONE) 0f else 100f,
        bgPadding = bgPadding,
        bgCorner = bgCorner
    )
}

/** The starter set (the Templates tab's grid, after its Default tile). */
val TextTemplates: List<TextTemplate> = listOf(
    TextTemplate(
        name = "Price Tag",
        fontFamily = "Poppins", fontWeight = 800, fill = 0xFFFFFFFF,
        shadowOpacity = 50f, shadowBlur = 20f, shadowDistance = 8f,
        bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFE53935, bgPadding = 42f, bgCorner = 12f
    ),
    TextTemplate(
        name = "Ribbon",
        fontFamily = "Montserrat", fontWeight = 800, fill = 0xFFFFFFFF,
        bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFF7C4DFF, bgPadding = 55f, bgCorner = 0f
    ),
    TextTemplate(
        name = "Pill Label",
        fontFamily = "Poppins", fontWeight = 700, fill = 0xFF111111,
        bgShape = TEXT_BG_ROUNDED, bgColor = 0xFFD2FC32, bgPadding = 45f, bgCorner = 50f
    ),
    TextTemplate(
        name = "Note Card",
        fontFamily = "Caveat", fontWeight = 700, fill = 0xFF263238,
        shadowOpacity = 40f, shadowBlur = 25f, shadowDistance = 10f,
        bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFFFF9C4, bgPadding = 50f, bgCorner = 6f
    ),
    TextTemplate(
        name = "Subscribe",
        fontFamily = "Roboto", fontWeight = 700, fill = 0xFFFFFFFF,
        shadowOpacity = 45f, shadowBlur = 20f, shadowDistance = 8f,
        bgShape = TEXT_BG_ROUNDED, bgColor = 0xFFFF0000, bgPadding = 50f, bgCorner = 50f
    ),
    TextTemplate(
        name = "Journey Card",
        fontFamily = "Playfair Display", fontWeight = 700, fill = 0xFFFFFFFF,
        shadowOpacity = 60f, shadowBlur = 30f, shadowDistance = 10f,
        bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFF37474F, bgPadding = 45f, bgCorner = 14f
    ),
    TextTemplate(
        name = "Speech Bubble",
        fontFamily = "Nunito", fontWeight = 700, fill = 0xFF212121,
        shadowOpacity = 35f, shadowBlur = 20f, shadowDistance = 8f,
        bgShape = TEXT_BG_SPEECH, bgColor = 0xFFFFFFFF, bgPadding = 55f
    ),
    TextTemplate(
        name = "Thought Cloud",
        fontFamily = "Comfortaa", fontWeight = 700, fill = 0xFF37474F,
        shadowOpacity = 35f, shadowBlur = 20f, shadowDistance = 8f,
        bgShape = TEXT_BG_THOUGHT, bgColor = 0xFFE3F2FD, bgPadding = 60f
    ),
    TextTemplate(
        name = "Bold Outline",
        fontFamily = "Anton", fontWeight = 800, fill = 0xFFFFFFFF,
        strokeThickness = 70f, strokeColor = 0xFF000000,
        bgShape = TEXT_BG_NONE
    ),
    TextTemplate(
        name = "Neon Glow",
        fontFamily = "Poppins", fontWeight = 700, fill = 0xFF00E5FF,
        shadowOpacity = 100f, shadowBlur = 70f, shadowDistance = 0f, shadowColor = 0xFF00E5FF,
        bgShape = TEXT_BG_NONE
    ),
    TextTemplate(
        name = "Gold Serif",
        fontFamily = "Cinzel", fontWeight = 700, fill = 0xFFFFD54F,
        strokeThickness = 15f, strokeColor = 0xFF8D6E00,
        shadowOpacity = 50f, shadowBlur = 15f, shadowDistance = 6f,
        bgShape = TEXT_BG_NONE
    ),
    TextTemplate(
        name = "Bangla Fest",
        fontFamily = "Hind Siliguri", fontWeight = 700, fill = 0xFFFFFFFF,
        shadowOpacity = 45f, shadowBlur = 20f, shadowDistance = 8f,
        bgShape = TEXT_BG_ROUNDED, bgColor = 0xFF2E7D32, bgPadding = 45f, bgCorner = 50f
    )
)

/**
 * The Templates grid's first tile: the app's own plain default look
 * (a fresh [TimelineClip]'s text fields — Roboto, the default weight,
 * white fill, the default shadow, no background). Applying it undoes
 * a template / preset's styling without touching the text itself.
 */
fun defaultTextLook(c: TimelineClip): TimelineClip {
    val d = TimelineClip(id = "", name = "", type = LayerType.TEXT, startMs = 0, durationMs = 0, color = 0)
    return c.copy(
        fontFamily = d.fontFamily,
        fontWeight = d.fontWeight,
        textColor = d.textColor,
        fillMode = FILL_SOLID,
        textStrokeThickness = d.textStrokeThickness,
        textStrokeColor = d.textStrokeColor,
        shadowOpacity = d.shadowOpacity,
        shadowBlur = d.shadowBlur,
        shadowDistance = d.shadowDistance,
        shadowAngle = d.shadowAngle,
        shadowColor = d.shadowColor,
        bgShape = d.bgShape,
        bgColor = d.bgColor,
        bgOpacity = d.bgOpacity,
        bgPadding = d.bgPadding,
        bgCorner = d.bgCorner
    )
}
