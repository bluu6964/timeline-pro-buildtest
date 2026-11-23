package com.example.model

/**
 * Build 271: the reference text panel's catalog data — the Styles
 * "Aa" presets, the Effects tab's art effects, the Bubbles tab's
 * designs, the Templates / Fonts category assignments and the
 * Styles colour palettes. Every entry is a REAL bundle over
 * [TimelineClip] fields: applying one writes stored fields that all
 * three painters (native engine, Canvas, Compose) render in preview
 * AND export. Where the reference uses a capability the engine does
 * not have, the entry is an approximation and says so on the entry.
 *
 * Slider unit mappings (panel display ↔ stored field), all linear
 * through the painters' own px laws at the reference's 20 px = 100%
 * anchor (fontPx = frameW × 11% × textSizePercent/100):
 *  - Size:      display px 12..44   → textSizePercent = px × 5 (60..220)
 *  - Stroke:    display px 0.5..4   → textStrokeThickness = px × 12.5
 *               (stroke px = fontPx × t/100 × 0.4 = fontPx × px/20)
 *  - Glow:      display px 2..28    → glowRadius = px × 5
 *               (glow blur px = fontPx × r/100 = fontPx × px/20)
 *  - Shadow blur:   display px 0..20 → shadowBlur = px × 10
 *               (blur px = fontPx × b/100 × 0.5 = fontPx × px/20)
 *  - Shadow offset: display px 0..12 → shadowDistance = px × 12.5
 *               (distance px = fontPx × d/100 × 0.4 = fontPx × px/20)
 *  - Character: display px −2..14   → letterSpacing = px × 5
 *               (spacing px = fontPx × ls/100 = fontPx × px/20)
 *  - Line:      display × 0.9..2.2  → lineHeightPercent = × × 100
 * shadowBlur / shadowDistance / letterSpacing can exceed the old
 * sliders' documented 0..100 UI range at the top of the reference
 * scales; the painters accept the values (the range was a UI
 * convention, not a painter clamp) and the stored project keeps
 * them verbatim.
 */
object TextPanelCatalog {

    // ── Styles palettes (the reference's renderStyles lists) ─────

    /** Text colour row: wheel first, then these. */
    val TEXT_COLORS: List<Long> = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFF8E8E98, 0xFFD6C3A5, 0xFFFF3B30, 0xFFFF9500,
        0xFFFFCC00, 0xFF34C759, 0xFF18D8E0, 0xFF007AFF, 0xFFAF52DE
    )

    /** Stroke / Glow / Shadow rows: transparent first, wheel, then these. */
    val FX_COLORS: List<Long> = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759,
        0xFF18D8E0, 0xFF007AFF, 0xFFAF52DE
    )

    /** Background colour row (no wheel in the reference). */
    val BG_COLORS: List<Long> = listOf(
        0xFFFFE600, 0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFF18D8E0, 0xFF34C759
    )

    // ── Styles "Aa" presets (the reference's 6) ──────────────────

    class AaPreset(
        val label: String,
        val fill: Long,
        val stroke: Long?,
        val bg: Long?,
        val shadow: Long?,
        val applyTo: (TimelineClip) -> TimelineClip
    )

    val AA_PRESETS: List<AaPreset> = listOf(
        AaPreset("None", 0xFFFFFFFF, null, null, null) { c ->
            c.copy(
                fillMode = FILL_SOLID, textColor = 0xFFFFFFFF,
                textStrokeThickness = 0f, textStrokeColor = 0xFF000000,
                bgShape = TEXT_BG_RECTANGLE, bgOpacity = 0f,
                shadowColor = 0xFF000000, shadowOpacity = 60f, shadowBlur = 30f,
                shadowDistance = 10f, shadowAngle = 45f,
                glowColor = 0L, textEffect = "", bgBorderColor = 0L, bgBorderWidth = 0f
            )
        },
        AaPreset("Aa", 0xFFFFFFFF, 0xFF000000, null, null) { c ->
            c.copy(
                fillMode = FILL_SOLID, textColor = 0xFFFFFFFF,
                textStrokeThickness = 19f, textStrokeColor = 0xFF000000,
                bgOpacity = 0f, glowColor = 0L, textEffect = ""
            )
        },
        AaPreset("Aa", 0xFFFFE600, 0xFF000000, null, null) { c ->
            c.copy(
                fillMode = FILL_SOLID, textColor = 0xFFFFE600,
                textStrokeThickness = 19f, textStrokeColor = 0xFF000000,
                bgOpacity = 0f, glowColor = 0L, textEffect = ""
            )
        },
        AaPreset("Aa", 0xFFFFFFFF, 0xFFFF3B30, null, null) { c ->
            c.copy(
                fillMode = FILL_SOLID, textColor = 0xFFFFFFFF,
                textStrokeThickness = 19f, textStrokeColor = 0xFFFF3B30,
                bgOpacity = 0f, glowColor = 0L, textEffect = ""
            )
        },
        AaPreset("Aa", 0xFF000000, null, 0xFFFFE600, null) { c ->
            c.copy(
                fillMode = FILL_SOLID, textColor = 0xFF000000,
                textStrokeThickness = 0f,
                bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFFFE600, bgOpacity = 100f,
                bgBorderColor = 0L, bgBorderWidth = 0f,
                glowColor = 0L, textEffect = ""
            )
        },
        AaPreset("Aa", 0xFF18D8E0, null, null, 0xFF000000) { c ->
            c.copy(
                fillMode = FILL_SOLID, textColor = 0xFF18D8E0,
                textStrokeThickness = 0f, bgOpacity = 0f,
                shadowColor = 0xFF000000, shadowOpacity = 100f, shadowBlur = 0f,
                shadowDistance = 38f, shadowAngle = 45f,
                glowColor = 0L, textEffect = ""
            )
        }
    )

    // ── Effects tab: the reference's art effects ─────────────────
    //
    // Thumb parameters ride on the entry so the card's "ART" thumb
    // shows exactly what applying the entry writes.

    class ArtEffect(
        val id: String,
        val name: String,
        val cats: Set<String>,
        val fill: Long,
        val grad1: Long?,
        val grad2: Long?,
        val stroke: Long?,
        val strokeThickness: Float,
        val shadowColor: Long?,
        val shadowBlur: Float,
        val shadowDistance: Float,
        val glow: Long?,
        val glowRadius: Float,
        val applyTo: (TimelineClip) -> TimelineClip
    )

    private fun effectBase(c: TimelineClip): TimelineClip = c.copy(
        textStrokeThickness = 0f,
        shadowOpacity = 60f, shadowBlur = 30f, shadowDistance = 10f,
        shadowAngle = 45f, shadowColor = 0xFF000000,
        glowColor = 0L
    )

    val ART_EFFECTS: List<ArtEffect> = listOf(
        ArtEffect(
            "red-glow", "Red Glow", setOf("Trending", "Black Friday"),
            fill = 0xFFFFFFFF, grad1 = null, grad2 = null,
            stroke = null, strokeThickness = 0f,
            shadowColor = null, shadowBlur = 0f, shadowDistance = 0f,
            glow = 0xFFFF0000, glowRadius = 100f
        ) { c ->
            effectBase(c).copy(
                fillMode = FILL_SOLID, textColor = 0xFFFFFFFF,
                shadowOpacity = 0f, glowColor = 0xFFFF0000, glowRadius = 100f,
                textEffect = "red-glow"
            )
        },
        ArtEffect(
            "neon-cyan", "Neon Cyan", setOf("Trending", "NEW"),
            fill = 0xFFE6FFFF, grad1 = null, grad2 = null,
            stroke = null, strokeThickness = 0f,
            shadowColor = null, shadowBlur = 0f, shadowDistance = 0f,
            glow = 0xFF18D8E0, glowRadius = 90f
        ) { c ->
            effectBase(c).copy(
                fillMode = FILL_SOLID, textColor = 0xFFE6FFFF,
                shadowOpacity = 0f, glowColor = 0xFF18D8E0, glowRadius = 90f,
                textEffect = "neon-cyan"
            )
        },
        ArtEffect(
            "yellow-pop", "Comic Pop", setOf("Trending", "Classic"),
            fill = 0xFFFFE600, grad1 = null, grad2 = null,
            stroke = 0xFF000000, strokeThickness = 50f,
            shadowColor = 0xFF000000, shadowBlur = 0f, shadowDistance = 38f,
            glow = null, glowRadius = 0f
        ) { c ->
            effectBase(c).copy(
                fillMode = FILL_SOLID, textColor = 0xFFFFE600,
                textStrokeThickness = 50f, textStrokeColor = 0xFF000000,
                shadowColor = 0xFF000000, shadowOpacity = 100f, shadowBlur = 0f,
                shadowDistance = 38f, shadowAngle = 45f,
                textEffect = "yellow-pop"
            )
        },
        ArtEffect(
            // The reference layers TWO hard shadows (white at 2px,
            // dark at 4px); the engine has ONE shadow pass, so the
            // visible dark offset layer is kept and the white layer
            // is dropped — stated approximation.
            "pink-3d", "Pink 3D", setOf("NEW", "Trending"),
            fill = 0xFFFF70C8, grad1 = null, grad2 = null,
            stroke = null, strokeThickness = 0f,
            shadowColor = 0xFF181820, shadowBlur = 0f, shadowDistance = 50f,
            glow = null, glowRadius = 0f
        ) { c ->
            effectBase(c).copy(
                fillMode = FILL_SOLID, textColor = 0xFFFF70C8,
                shadowColor = 0xFF181820, shadowOpacity = 100f, shadowBlur = 0f,
                shadowDistance = 50f, shadowAngle = 45f,
                textEffect = "pink-3d"
            )
        },
        ArtEffect(
            // The reference gradient has 3 stops; the engine's
            // gradient fill is 2-stop (gradColor1 → gradColor2), so
            // Fire ships as #FFF700 → #FF4500 — stated approximation.
            "fire", "Fire", setOf("Black Friday", "NEW"),
            fill = 0xFFFFF700, grad1 = 0xFFFFF700, grad2 = 0xFFFF4500,
            stroke = null, strokeThickness = 0f,
            shadowColor = null, shadowBlur = 0f, shadowDistance = 0f,
            glow = null, glowRadius = 0f
        ) { c ->
            effectBase(c).copy(
                fillMode = FILL_GRADIENT, gradType = GRAD_LINEAR,
                gradColor1 = 0xFFFFF700, gradColor2 = 0xFFFF4500,
                gradX1 = 0.5f, gradY1 = 0f,
                gradX2 = 0.5f, gradY2 = 1f,
                shadowOpacity = 0f, textEffect = "fire"
            )
        },
        ArtEffect(
            // The reference gradient has 4 stops with a mid dark
            // band; 2-stop approximation #FFFFFF → #48505C.
            "chrome", "Chrome", setOf("Classic", "Trending"),
            fill = 0xFFFFFFFF, grad1 = 0xFFFFFFFF, grad2 = 0xFF48505C,
            stroke = null, strokeThickness = 0f,
            shadowColor = null, shadowBlur = 0f, shadowDistance = 0f,
            glow = null, glowRadius = 0f
        ) { c ->
            effectBase(c).copy(
                fillMode = FILL_GRADIENT, gradType = GRAD_LINEAR,
                gradColor1 = 0xFFFFFFFF, gradColor2 = 0xFF48505C,
                gradX1 = 0.5f, gradY1 = 0f,
                gradX2 = 0.5f, gradY2 = 1f,
                shadowOpacity = 0f, textEffect = "chrome"
            )
        }
    )

    // ── Bubbles tab: the reference's 8 designs ───────────────────

    class BubbleDesign(
        val id: String,
        val sample: String,
        /** null = the None design. */
        val applyTo: ((TimelineClip) -> TimelineClip)?,
        val matchShape: Int,
        val matchBg: Long,
        val matchBorder: Long
    )

    val BUBBLE_NONE = BubbleDesign("none", "", null, TEXT_BG_NONE, 0L, 0L)

    val BUBBLE_DESIGNS: List<BubbleDesign> = listOf(
        BubbleDesign("speech", "Good Morning", { c ->
            c.copy(
                bgShape = TEXT_BG_SPEECH, bgColor = 0xFFFFFFFF, bgOpacity = 100f,
                bgBorderColor = 0L, bgBorderWidth = 0f,
                fillMode = FILL_SOLID, textColor = 0xFF111111
            )
        }, TEXT_BG_SPEECH, 0xFFFFFFFF, 0L),
        BubbleDesign("bang", "BANG!", { c ->
            c.copy(
                bgShape = TEXT_BG_BANG, bgColor = 0xFFFFFFFF, bgOpacity = 100f,
                bgBorderColor = 0L, bgBorderWidth = 0f,
                fillMode = FILL_SOLID, textColor = 0xFF111111
            )
        }, TEXT_BG_BANG, 0xFFFFFFFF, 0L),
        BubbleDesign("neon", "Cyber Tag", { c ->
            c.copy(
                bgShape = TEXT_BG_ROUNDED, bgColor = 0xFF0C0E14, bgOpacity = 92f,
                bgCorner = 100f,
                bgBorderColor = 0xFF18D8E0, bgBorderWidth = 7.5f,
                fillMode = FILL_SOLID, textColor = 0xFF18D8E0
            )
        }, TEXT_BG_ROUNDED, 0xFF0C0E14, 0xFF18D8E0),
        BubbleDesign(
            // The reference tag carries a 5px gold LEFT bar; the
            // engine's bg border is a uniform outline, so the bar is
            // omitted (stated) — the cream tag + dark text carry the
            // design.
            "tag", "¥2300", { c ->
                c.copy(
                    bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFF6EAD4, bgOpacity = 100f,
                    bgCorner = 16f, bgBorderColor = 0L, bgBorderWidth = 0f,
                    fillMode = FILL_SOLID, textColor = 0xFF2B2118
                )
            }, TEXT_BG_RECTANGLE, 0xFFF6EAD4, 0L
        ),
        BubbleDesign(
            // The reference ribbon bg is an orange gradient; the bg
            // fill is flat-only, so flat #FF7A00 ships (stated).
            "ribbon", "Active day", { c ->
                c.copy(
                    bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFFF7A00, bgOpacity = 100f,
                    bgCorner = 16f, bgBorderColor = 0L, bgBorderWidth = 0f,
                    fillMode = FILL_SOLID, textColor = 0xFFFFFFFF
                )
            }, TEXT_BG_RECTANGLE, 0xFFFF7A00, 0L
        ),
        BubbleDesign("glass", "Glass Tag", { c ->
            c.copy(
                bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFFFFFFF, bgOpacity = 12f,
                bgCorner = 32f, bgBorderColor = 0x38FFFFFF, bgBorderWidth = 5f,
                fillMode = FILL_SOLID, textColor = 0xFFFFFFFF
            )
        }, TEXT_BG_RECTANGLE, 0xFFFFFFFF, 0x38FFFFFF),
        BubbleDesign("blue", "My Journey", { c ->
            c.copy(
                bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFD9F2FF, bgOpacity = 100f,
                bgCorner = 48f, bgBorderColor = 0xFF6EC1E4, bgBorderWidth = 7.5f,
                fillMode = FILL_SOLID, textColor = 0xFF0B3C5D
            )
        }, TEXT_BG_RECTANGLE, 0xFFD9F2FF, 0xFF6EC1E4),
        BubbleDesign("sale", "HOT SALE", { c ->
            c.copy(
                bgShape = TEXT_BG_RECTANGLE, bgColor = 0xFFFF2E54, bgOpacity = 100f,
                bgCorner = 32f, bgBorderColor = 0L, bgBorderWidth = 0f,
                fillMode = FILL_SOLID, textColor = 0xFFFFFFFF
            )
        }, TEXT_BG_RECTANGLE, 0xFFFF2E54, 0L)
    )

    /** Whether [clip] currently matches [design] (the tab's ring). */
    fun bubbleMatches(clip: TimelineClip, design: BubbleDesign): Boolean =
        if (design.id == "none") clip.bgShape == TEXT_BG_NONE || clip.bgOpacity <= 0f
        else clip.bgShape == design.matchShape &&
            clip.bgColor == design.matchBg &&
            clip.bgBorderColor == design.matchBorder

    // ── Templates categories (the reference's spirit) ────────────
    // Keyed by template name; the Default tile rides TRENDING+CLASSIC
    // like the reference's tpl-0.

    val TEMPLATE_CATS: Map<String, Set<String>> = mapOf(
        "Price Tag" to setOf("Black Friday", "Trending"),
        "Ribbon" to setOf("Trending"),
        "Pill Label" to setOf("NEW", "Trending"),
        "Note Card" to setOf("Classic"),
        "Subscribe" to setOf("Trending", "Black Friday"),
        "Journey Card" to setOf("Classic"),
        "Speech Bubble" to setOf("NEW"),
        "Thought Cloud" to setOf("NEW", "Classic"),
        "Bold Outline" to setOf("Trending", "NEW"),
        "Neon Glow" to setOf("Trending", "NEW"),
        "Gold Serif" to setOf("Classic"),
        "Bangla Fest" to setOf("NEW", "Trending")
    )
    val DEFAULT_TEMPLATE_CATS: Set<String> = setOf("Trending", "Classic")

    /** The reference's filter rule: the bookmark view shows the
     *  bookmarked set; a search query searches everything by name;
     *  otherwise the category must contain the sub-tab — except
     *  Trending, which shows all. */
    fun refFilter(
        cats: Set<String>, subTab: String, query: String, name: String, bookmarked: Boolean
    ): Boolean = when {
        query.isNotEmpty() -> name.contains(query, ignoreCase = true)
        subTab == "bookmark" -> bookmarked
        subTab == "Trending" -> true
        else -> cats.contains(subTab)
    }
}
