package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.TextEffectIds
import com.example.engine.TextRevealValues
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.AnimPreset
import com.example.model.AnimPresets
import com.example.model.AnimSlot
import com.example.model.EditorGeometry
import com.example.model.FILL_NONE
import com.example.model.FILL_SOLID
import com.example.model.LayerType
import com.example.model.PanelGeometry
import com.example.model.TEXT_BG_NONE
import com.example.model.TEXT_BG_RECTANGLE
import com.example.model.TEXT_BG_ROUNDED
import com.example.model.TEXT_BG_SPEECH
import com.example.model.TEXT_BG_SPEECH_SQ
import com.example.model.TEXT_BG_THOUGHT
import com.example.model.TextEditGeometry
import com.example.model.TextTemplates
import com.example.model.TimelineClip
import com.example.model.defaultTextLook
import com.example.model.textBgCornerApplies
import com.example.model.textBgShapeDef
import com.example.ui.text.FontCatalog
import com.example.ui.text.FontEntry
import com.example.ui.text.FontRepository
import com.example.ui.text.rememberFontFamily
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Build 268 — the Text editor as a DRAGGABLE BOTTOM PANEL (Sarowar's
 * order, from his CapCut reference videos): the editor canvas stays
 * visible and LIVE above (the text layer is selected on it; edits
 * apply live as always), and the controls live in a bottom panel
 * whose HEAD — grab pill + "Text" title + lime Done — drags
 * vertically. The drag law is [TextEditGeometry.Panel]: the height
 * follows the finger 1:1 and STAYS where it is released (no snap, no
 * detent); the range is clamped to the editor's lower zone (the
 * canvas's build-260 invariant height is never touched) and, while
 * the keyboard is up, to the space above it. The released height is
 * remembered by the host for the rest of the editor session.
 *
 * The build-266 preview block is GONE: the canvas is the preview.
 *
 * Inside: the hosted typing field, the tab row — Templates / Fonts /
 * Styles / Effects / Animations / Bubbles (the videos' structure) —
 * and the active tab's content. Every control of the 266/267 screen
 * survives in exactly one tab (the mapping is in the build-268
 * report); the 267-fixed GL components and the LabelLine hug law are
 * reused unchanged.
 *
 * Hosted islands, by platform law / proven limits only: the typing
 * field and the font search field (Android IME), the template /
 * effect cards' text (the app's exact multi-style painter
 * [TextLayerContent] — the GL atlas blits one flat style and cannot
 * reproduce stroke / shadow / gradient / background text), and the
 * colour picker dialog (already hosted app-wide).
 */
@Composable
fun TextEditPanel(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    onClose: () -> Unit,
    /** The session's remembered panel height in dp (null = first open). */
    heightDp: Float?,
    /** Stores the released height (dp) for the rest of the session. */
    onHeightChange: (Float) -> Unit,
    /** The lower zone's height in dp — the panel may never exceed it. */
    availableHeightDp: Float,
    /** The keyboard's height inside that zone in dp (0 = closed). */
    imeBottomDp: Float,
    /** Applies an animation preset through the Animations tool's own ViewModel action. */
    onAnimApply: (slot: AnimSlot, preset: AnimPreset?) -> Unit,
    /** Plays the applied animation of a slot, like the Animations tool does. */
    onAnimPreview: (slot: AnimSlot, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    /** PATH-bearing shape layers of the project the text can attach to (build 191). */
    pathLayers: List<TimelineClip> = emptyList()
) {
    var text by remember(clip.id) { mutableStateOf(clip.text.orEmpty()) }
    val onText: (String) -> Unit = { v ->
        text = v
        onUpdate { it.copy(text = v, name = if (v.isBlank()) "Text" else "Text: ${v.take(24)}") }
    }
    var tab by remember(clip.id) { mutableStateOf(TextTab.TEMPLATES) }

    val density = LocalDensity.current.density
    val pg = TextEditGeometry.Panel
    val minPx = pg.minHeightPx(density)
    val maxPx = pg.imeMaxHeightPx(availableHeightDp * density, imeBottomDp * density)
    val restingPx = pg.clampHeightPx(
        heightDp?.let { it * density }
            ?: pg.defaultHeightPx(availableHeightDp * density, density),
        minPx, maxPx
    )
    // The in-flight drag height: local so a drag move does not
    // recompose the whole editor screen; committed to the host (the
    // session memory) only on release — where it then stays.
    var dragPx by remember(clip.id) { mutableStateOf<Float?>(null) }
    val shownPx = dragPx?.let { pg.clampHeightPx(it, minPx, maxPx) } ?: restingPx

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height((shownPx / density).dp)
            .background(AmBase)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        PanelHead(
            onDone = onClose,
            onDrag = { deltaY ->
                dragPx = pg.draggedHeightPx(dragPx ?: shownPx, deltaY, minPx, maxPx)
            },
            onDragEnd = {
                dragPx?.let { onHeightChange(it / density) }
                dragPx = null
            }
        )
        TextBoxField(
            text, onText,
            Modifier.padding(horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp)
        )
        Spacer(Modifier.height(6.dp))
        TextTabRow(tab) { tab = it }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tab) {
                TextTab.TEMPLATES -> TemplatesTab(clip, onUpdate)
                TextTab.FONTS -> FontsTab(clip, onUpdate)
                TextTab.STYLES -> StylesTab(clip, onUpdate, onEyedropper, pathLayers)
                TextTab.EFFECTS -> EffectsTab(clip, onUpdate)
                TextTab.ANIMATIONS -> AnimationsTab(clip, onUpdate, onAnimApply, onAnimPreview)
                TextTab.BUBBLES -> BubblesTab(clip, onUpdate, onEyedropper)
            }
        }
    }
}

// ── Panel chrome: the draggable head + the tab row ────────────────

private enum class TextTab(val label: String) {
    TEMPLATES("Templates"), FONTS("Fonts"), STYLES("Styles"),
    EFFECTS("Effects"), ANIMATIONS("Animations"), BUBBLES("Bubbles")
}

/**
 * The panel's head: the grab pill on top, then "Text" + the lime
 * Done. The WHOLE strip is the drag surface (a tap on Done still
 * lands — the vertical-drag detector only takes over once the
 * finger actually moves vertically).
 */
@Composable
private fun PanelHead(
    onDone: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val g = TextEditGeometry.Panel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(g.HEAD_H_DP.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
    ) {
        NativeVectorCanvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .width(g.GRAB_W_DP.dp)
                .height(g.GRAB_H_DP.dp),
            clearArgb = 0,
            drawKey = g,
            buildCmds = { w, h, _ ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, TxtDimArgb)
            }
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TextEditGeometry.SCREEN_PAD_H_DP.dp,
                    end = TextEditGeometry.SCREEN_PAD_H_DP.dp,
                    top = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlLabelLine("Text", UiLabelStyle.HEADER_NAME, TxtWhiteArgb)
            Spacer(Modifier.weight(1f))
            GlVioletPill(label = "Done", onClick = onDone, tag = "text_edit_done")
        }
    }
}

/** The tab row: six hug pills on one scrolling GL strip. */
@Composable
private fun TextTabRow(selected: TextTab, onSelect: (TextTab) -> Unit) {
    GlPillStrip(
        items = TextTab.values().map { t ->
            GlPillItem(label = t.label, selected = t == selected) { onSelect(t) }
        },
        labelStyle = { sel -> if (sel) UiLabelStyle.TEXT_13_BOLD else UiLabelStyle.TEXT_13_SEMI },
        textArgb = { sel -> if (sel) TxtInkArgb else TxtWhiteArgb },
        fillArgb = { sel -> if (sel) TxtAccentArgb else TxtSurfaceArgb },
        borderArgb = { null },
        padHDp = 14f,
        padVDp = 8f,
        gapDp = 6f,
        minHDp = TextEditGeometry.Chips.H_DP,
        scrollable = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp)
    )
}

/** A tab's scrolling content column (the panel's own padding law). */
@Composable
private fun TabColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = TextEditGeometry.SCREEN_PAD_H_DP.dp,
                vertical = 10.dp
            )
    ) {
        content()
        Spacer(Modifier.height(18.dp))
    }
}

// ── Templates tab ──────────────────────────────────────────────────

/**
 * Templates: a 2-column grid of designed cards — the Default tile
 * first (the app's plain default look), then the [TextTemplates]
 * bundles. Each card renders the USER's actual text in the bundle's
 * look (the hosted [TextLayerContent] painter); tapping applies the
 * whole bundle through onUpdate, inside the session's one undo step.
 */
@Composable
private fun TemplatesTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    TabColumn {
        val cards: List<Pair<String, (TimelineClip) -> TimelineClip>> =
            listOf("Default" to { c: TimelineClip -> defaultTextLook(c) }) +
                TextTemplates.map { t -> t.name to { c: TimelineClip -> t.applyTo(c) } }
        cards.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(TextEditGeometry.Grid.GAP_DP.dp)) {
                rowCards.forEach { (name, apply) ->
                    TemplateCard(
                        name = name,
                        previewClip = apply(clip),
                        modifier = Modifier.weight(1f)
                    ) { onUpdate(apply) }
                }
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(TextEditGeometry.Grid.GAP_DP.dp))
        }
    }
}

@Composable
private fun TemplateCard(
    name: String,
    previewClip: TimelineClip,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    BoxWithConstraints(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(scale.value),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, 12f * dd, TxtSurfaceArgb)
                clearTransform()
            }
        )
        TextLayerContent(
            clip = previewClip,
            frameWidth = maxWidth,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 7.dp)
        ) {
            GlLabelLine(name, UiLabelStyle.TEXT_11_SEMI, TxtDimArgb)
        }
        ChromePressBox(
            scale, 0.96f, onClick,
            Modifier.fillMaxSize(),
            description = name
        )
    }
}

// ── Fonts tab ──────────────────────────────────────────────────────

/**
 * Fonts: the weight pill row at the top, then the search field
 * (hosted), Add font + import, the device-only note, and the font
 * GRID — 3 columns, each tile the font's name in its own face
 * (un-downloaded Google faces re-render on arrival, as the old
 * strip's chips did). CapCut's category chips are its cloud
 * catalogue and are not reproduced (no categories in our catalogue
 * UI — the search field covers finding a face).
 */
@Composable
private fun FontsTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var userFonts by remember { mutableStateOf(FontRepository.userFonts(context)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = FontRepository.importUserFont(context, uri)
            if (name != null) {
                userFonts = FontRepository.userFonts(context)
                onUpdate { it.copy(fontFamily = name) }
            }
        }
    }

    val fonts = remember(query, userFonts) {
        val all = userFonts.map { FontEntry(it, FontCatalog.USER_CATEGORY) } + FontCatalog.all
        all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    TabColumn {
        FontWeightRow(clip, onUpdate)
        Spacer(Modifier.height(10.dp))
        FontSearchField(query) { query = it }
        Spacer(Modifier.height(8.dp))
        GlTextChipButton("+ Add font") {
            picker.launch(
                arrayOf(
                    "font/ttf", "font/otf", "font/sfnt",
                    "application/x-font-ttf", "application/x-font-otf",
                    "application/octet-stream"
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        // Build 201 (audit S-6): imported fonts live in this app's private
        // storage on THIS device — say so where the user adds them.
        GlNoteText("Fonts you add are stored on this device only — they don't travel with the project file.")
        Spacer(Modifier.height(10.dp))
        fonts.chunked(3).forEach { rowFonts ->
            Row(horizontalArrangement = Arrangement.spacedBy(TextEditGeometry.Grid.GAP_DP.dp)) {
                rowFonts.forEach { f ->
                    FontGridTile(
                        name = f.name,
                        selected = clip.fontFamily == f.name,
                        modifier = Modifier.weight(1f)
                    ) { onUpdate { it.copy(fontFamily = f.name) } }
                }
                repeat(3 - rowFonts.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(TextEditGeometry.Grid.GAP_DP.dp))
        }
    }
}

/** The font weight pill row (the Fonts tab's top row). */
@Composable
private fun FontWeightRow(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlLabelLine("Font weight", UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
        Spacer(Modifier.width(TextEditGeometry.Chips.LABEL_GAP_DP.dp))
        GlPillStrip(
            items = listOf(200, 400, 500, 600, 700, 800, 900).map { w ->
                GlPillItem(
                    label = "$w",
                    selected = clip.fontWeight == w,
                    onClick = { onUpdate { it.copy(fontWeight = w) } }
                )
            },
            labelStyle = { UiLabelStyle.TEXT_13_BOLD },
            textArgb = { sel -> if (sel) TxtInkArgb else TxtWhiteArgb },
            fillArgb = { sel -> if (sel) TxtAccentArgb else TxtSurfaceArgb },
            borderArgb = { null },
            padHDp = 14f,
            padVDp = 8f,
            gapDp = 6f,
            minHDp = TextEditGeometry.Chips.H_DP,
            scrollable = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/** One Fonts-grid tile: the font's name rasterized in its own face. */
@Composable
private fun FontGridTile(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val g = TextEditGeometry.Segmented
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val entry = rememberFontChipEntry(atlas, name)
    val scale = remember { Animatable(1f) }
    Box(modifier.height(52.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(entry, selected, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CORNER_DP * dd, TxtSurfaceArgb,
                    if (selected) 2f * dd else 0f,
                    if (selected) TxtAccentArgb else 0
                )
                val e = entry
                if (e != null) {
                    addText(
                        e.index,
                        w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            description = name
        )
    }
}

// ── Styles tab ─────────────────────────────────────────────────────

private enum class StyleSection(val label: String) {
    BACKGROUND("Background"), STROKE("Stroke"), SHADOW("Shadow"),
    CURVE("Curve"), SPACING("Spacing"), FORMAT("Format")
}

/**
 * Styles (the videos' structure): (1) a quick fill row — None/reset
 * plus the standard colour set, tapping sets the fill colour; (2)
 * the sub-section chips Background / Stroke / Shadow / Curve /
 * Spacing / Format (the canonical order lives in
 * [TextEditGeometry.Styles.SECTION_LABELS], pinned by tests), each
 * holding its manual controls. CapCut's Glow
 * sub-section is NOT one of ours: the text engine has no separate
 * glow (the Shadow controls cover the look) — nothing is invented.
 */
@Composable
private fun StylesTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    pathLayers: List<TimelineClip>
) {
    var section by remember(clip.id) { mutableStateOf(StyleSection.FORMAT) }
    TabColumn {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(TextEditGeometry.Swatches.GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlTextChipButton("None") {
                onUpdate { it.copy(fillMode = FILL_NONE) }
            }
            Swatches.forEach { c ->
                GlSwatchTile(
                    argb = c,
                    selected = clip.fillMode != FILL_NONE && clip.textColor == c
                ) { onUpdate { it.copy(textColor = c, fillMode = FILL_SOLID) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlPillStrip(
            items = StyleSection.values().map { s ->
                GlPillItem(label = s.label, selected = s == section) { section = s }
            },
            labelStyle = { sel -> if (sel) UiLabelStyle.TEXT_13_BOLD else UiLabelStyle.TEXT_13_SEMI },
            textArgb = { sel -> if (sel) TxtInkArgb else TxtWhiteArgb },
            fillArgb = { sel -> if (sel) TxtAccentArgb else TxtSurfaceArgb },
            borderArgb = { null },
            padHDp = 14f,
            padVDp = 8f,
            gapDp = 6f,
            minHDp = TextEditGeometry.Chips.H_DP,
            scrollable = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        when (section) {
            StyleSection.BACKGROUND -> BackgroundControls(clip, onUpdate, onEyedropper)
            StyleSection.STROKE -> StrokeControls(clip, onUpdate, onEyedropper)
            StyleSection.SHADOW -> ShadowControls(clip, onUpdate, onEyedropper)
            StyleSection.CURVE -> PathSection(clip, pathLayers, onUpdate)
            StyleSection.SPACING -> {
                GlTextSliderRow("Letter space", clip.letterSpacing, -10f, 40f, "%") { v ->
                    onUpdate { it.copy(letterSpacing = v) }
                }
                GlTextSliderRow("Line height", clip.lineHeightPercent, 80f, 200f, "%") { v ->
                    onUpdate { it.copy(lineHeightPercent = v) }
                }
            }
            StyleSection.FORMAT -> {
                GlSegmentedRow(
                    listOf(
                        SegmentItem("Italic", null, clip.isItalic) {
                            onUpdate { it.copy(isItalic = !it.isItalic) }
                        },
                        SegmentItem("Underline", null, clip.isUnderline) {
                            onUpdate { it.copy(isUnderline = !it.isUnderline) }
                        },
                        SegmentItem("Strikethrough", null, clip.isStrikethrough) {
                            onUpdate { it.copy(isStrikethrough = !it.isStrikethrough) }
                        }
                    )
                )
                Spacer(Modifier.height(10.dp))
                GlSegmentedRow(
                    listOf(
                        SegmentItem("Left", Icons.Default.FormatAlignLeft, clip.textAlign == 0) {
                            onUpdate { it.copy(textAlign = 0) }
                        },
                        SegmentItem("Center", Icons.Default.FormatAlignCenter, clip.textAlign == 1) {
                            onUpdate { it.copy(textAlign = 1) }
                        },
                        SegmentItem("Right", Icons.Default.FormatAlignRight, clip.textAlign == 2) {
                            onUpdate { it.copy(textAlign = 2) }
                        }
                    )
                )
                Spacer(Modifier.height(10.dp))
                GlColorRow(
                    label = "Color",
                    selected = clip.textColor,
                    onSelect = { c -> onUpdate { it.copy(textColor = c, fillMode = FILL_SOLID) } },
                    onEyedropper = onEyedropper
                )
            }
        }
    }
}

/**
 * The Stroke controls (Styles → Stroke; the old Stroke group,
 * verbatim — build 269 restores the tab home build 268's
 * reorganisation dropped: the Thickness slider + the stroke
 * colour row over the same clip fields and update actions).
 */
@Composable
private fun StrokeControls(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    GlTextSliderRow("Thickness", clip.textStrokeThickness, 0f, 100f, "%") { v ->
        onUpdate { it.copy(textStrokeThickness = v) }
    }
    GlColorRow(
        label = "Color",
        selected = clip.textStrokeColor,
        onSelect = { c -> onUpdate { it.copy(textStrokeColor = c) } },
        onEyedropper = onEyedropper
    )
}

/** The Shadow controls (Styles → Shadow; the old Shadow group, verbatim). */
@Composable
private fun ShadowControls(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    GlTextSliderRow("Opacity", clip.shadowOpacity, 0f, 100f, "%") { v ->
        onUpdate { it.copy(shadowOpacity = v) }
    }
    GlTextSliderRow("Blur", clip.shadowBlur, 0f, 100f, "%") { v ->
        onUpdate { it.copy(shadowBlur = v) }
    }
    GlTextSliderRow("Distance", clip.shadowDistance, 0f, 100f, "%") { v ->
        onUpdate { it.copy(shadowDistance = v) }
    }
    GlTextSliderRow("Angle", clip.shadowAngle, 0f, 360f, "°") { v ->
        onUpdate { it.copy(shadowAngle = v) }
    }
    GlColorRow(
        label = "Color",
        selected = clip.shadowColor,
        onSelect = { c -> onUpdate { it.copy(shadowColor = c) } },
        onEyedropper = onEyedropper
    )
}

/**
 * The background controls — ONE source of truth, shown by both
 * Bubbles and Styles → Background (the same composable over the
 * same clip fields, never a copy with separate state): the colour
 * row, Opacity, Padding, and Corner. Corner exists only for the two
 * box shapes; for a bubble shape the slider is replaced by the
 * reason, in the UI as in the code ([textBgCornerApplies]).
 */
@Composable
private fun BackgroundControls(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    GlColorRow(
        label = "Color",
        selected = clip.bgColor,
        onSelect = { c ->
            // Picking a color for a hidden background turns it on (old
            // rule); with the shape at None it also restores the box,
            // or the picked colour would paint nothing at all.
            onUpdate {
                it.copy(
                    bgColor = c,
                    bgOpacity = if (it.bgOpacity == 0f) 100f else it.bgOpacity,
                    bgShape = if (it.bgShape == TEXT_BG_NONE) TEXT_BG_RECTANGLE else it.bgShape
                )
            }
        },
        onEyedropper = onEyedropper
    )
    GlTextSliderRow("Opacity", clip.bgOpacity, 0f, 100f, "%") { v ->
        onUpdate { it.copy(bgOpacity = v) }
    }
    GlTextSliderRow("Padding", clip.bgPadding, 0f, 100f, "%") { v ->
        onUpdate { it.copy(bgPadding = v) }
    }
    if (textBgCornerApplies(clip.bgShape)) {
        GlTextSliderRow("Corner", clip.bgCorner, 0f, 100f, "%") { v ->
            onUpdate { it.copy(bgCorner = v) }
        }
    } else {
        GlNoteText("Corner follows the bubble's own outline for this shape — choose Rectangle or Rounded in Bubbles to use the Corner slider.")
    }
}

// ── Effects tab ────────────────────────────────────────────────────

/**
 * Effects: the one-tap text-effect grid (the videos' "ART" grid) —
 * a None tile first, then the app's 10 style presets, each tile
 * rendering the user's actual text in that preset; tapping applies
 * it (the same applyTo / isCurrent rule as the old Style group,
 * which is where the presets lived before build 268). The text
 * layer's layer-FX are NOT here — they stay in the layer's own
 * Effects tool, as before.
 */
@Composable
private fun EffectsTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    TabColumn {
        val defaults = remember {
            TimelineClip(id = "", name = "", type = LayerType.TEXT, startMs = 0, durationMs = 0, color = 0)
        }
        val noneCurrent = clip.textColor == defaults.textColor &&
            clip.textStrokeThickness == defaults.textStrokeThickness &&
            clip.shadowOpacity == defaults.shadowOpacity &&
            clip.shadowBlur == defaults.shadowBlur
        val tiles: List<Pair<String, TextPreset?>> =
            listOf("None" to null) + TextPresets.mapIndexed { i, p -> "Preset ${i + 1}" to p }
        tiles.chunked(3).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(TextEditGeometry.Grid.GAP_DP.dp)) {
                rowTiles.forEach { (label, preset) ->
                    val isCurrent = if (preset == null) noneCurrent else
                        clip.textColor == preset.fill &&
                            clip.textStrokeThickness == preset.strokeThickness &&
                            clip.shadowOpacity == preset.shadowOpacity &&
                            (preset.shadowOpacity == 0f || clip.shadowBlur == preset.shadowBlur)
                    val preview = if (preset == null) {
                        clip.copy(
                            textColor = defaults.textColor, fillMode = FILL_SOLID,
                            textStrokeThickness = defaults.textStrokeThickness,
                            textStrokeColor = defaults.textStrokeColor,
                            shadowOpacity = defaults.shadowOpacity,
                            shadowBlur = defaults.shadowBlur,
                            shadowDistance = defaults.shadowDistance,
                            shadowColor = defaults.shadowColor
                        )
                    } else preset.applyTo(clip)
                    EffectTile(
                        previewClip = preview,
                        isCurrent = isCurrent,
                        label = label,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (preset == null) {
                            onUpdate {
                                it.copy(
                                    textColor = defaults.textColor, fillMode = FILL_SOLID,
                                    textStrokeThickness = defaults.textStrokeThickness,
                                    textStrokeColor = defaults.textStrokeColor,
                                    shadowOpacity = defaults.shadowOpacity,
                                    shadowBlur = defaults.shadowBlur,
                                    shadowDistance = defaults.shadowDistance,
                                    shadowColor = defaults.shadowColor
                                )
                            }
                        } else {
                            onUpdate { preset.applyTo(it) }
                        }
                    }
                }
                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(TextEditGeometry.Grid.GAP_DP.dp))
        }
    }
}

@Composable
private fun EffectTile(
    previewClip: TimelineClip,
    isCurrent: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    BoxWithConstraints(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(isCurrent, scale.value),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, 10f * dd, TxtSurfaceArgb,
                    if (isCurrent) 2f * dd else 0f,
                    if (isCurrent) TxtAccentArgb else 0
                )
                clearTransform()
            }
        )
        TextLayerContent(
            clip = previewClip,
            frameWidth = maxWidth,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            description = label
        )
    }
}

// ── Animations tab ─────────────────────────────────────────────────

private enum class AnimPhase(val label: String) { IN("In"), OUT("Out"), LOOP("Loop") }

/**
 * Animations: In / Out / Loop sub-tabs (the videos' structure) with
 * preset tiles — None first in In and Out — populated from the
 * [AnimPresets] catalogue by phase. The catalogue's presets are
 * one-shot entrance/exit animations (each carries an In and an Out
 * easing), so In and Out offer the whole catalogue for their slot;
 * there are NO looping presets in the app, so Loop honestly shows
 * none rather than fake tiles. Applying routes through the same
 * ViewModel action as the Animations tool (the host wires it), with
 * the preset's own default direction / length / curve. Below the
 * tiles: the text Reveal controls.
 */
@Composable
private fun AnimationsTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onAnimApply: (slot: AnimSlot, preset: AnimPreset?) -> Unit,
    onAnimPreview: (slot: AnimSlot, durationMs: Long) -> Unit
) {
    var phase by remember(clip.id) { mutableStateOf(AnimPhase.IN) }
    TabColumn {
        GlPillStrip(
            items = AnimPhase.values().map { p ->
                GlPillItem(label = p.label, selected = p == phase) { phase = p }
            },
            labelStyle = { sel -> if (sel) UiLabelStyle.TEXT_13_BOLD else UiLabelStyle.TEXT_13_SEMI },
            textArgb = { sel -> if (sel) TxtInkArgb else TxtWhiteArgb },
            fillArgb = { sel -> if (sel) TxtAccentArgb else TxtSurfaceArgb },
            borderArgb = { null },
            padHDp = 14f,
            padVDp = 8f,
            gapDp = 6f,
            minHDp = TextEditGeometry.Chips.H_DP,
            scrollable = false,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        when (phase) {
            AnimPhase.IN, AnimPhase.OUT -> {
                val slot = if (phase == AnimPhase.IN) AnimSlot.IN else AnimSlot.OUT
                val applied = if (slot == AnimSlot.IN) clip.animIn else clip.animOut
                GlPillStrip(
                    items = listOf(
                        GlPillItem(label = "None", selected = applied == null) {
                            onAnimApply(slot, null)
                        }
                    ) + AnimPresets.ALL.map { p ->
                        GlPillItem(label = p.label, selected = applied?.preset == p.id) {
                            onAnimApply(slot, p)
                            onAnimPreview(slot, p.defaultMs)
                        }
                    },
                    labelStyle = { sel -> if (sel) UiLabelStyle.TEXT_13_BOLD else UiLabelStyle.TEXT_13_SEMI },
                    textArgb = { sel -> if (sel) TxtInkArgb else TxtWhiteArgb },
                    fillArgb = { sel -> if (sel) TxtAccentArgb else TxtSurfaceArgb },
                    borderArgb = { null },
                    padHDp = 14f,
                    padVDp = 8f,
                    gapDp = 6f,
                    minHDp = TextEditGeometry.Chips.H_DP,
                    scrollable = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                GlNoteText("Tiles apply the preset with its default direction, length and curve — fine-tune them in the Animations tool.")
            }
            AnimPhase.LOOP -> {
                GlNoteText("No looping animations in the catalogue yet — the app's presets are one-shot In and Out animations, so this tab has no tiles rather than fake ones.")
            }
        }
        Spacer(Modifier.height(16.dp))
        GlLabelLine("Reveal", UiLabelStyle.TEXT_11_SEMI, TxtDimArgb)
        Spacer(Modifier.height(6.dp))
        RevealSection(clip, onUpdate)
    }
}

// ── Bubbles tab ────────────────────────────────────────────────────

/**
 * Bubbles: the background-shape gallery — a None tile first, then
 * Rectangle / Rounded rectangle / Speech bubble / Speech square /
 * Thought bubble, each tile a small rendered preview of the shape
 * (the catalog geometry itself for the bubbles). Tapping sets the
 * text background shape ([TimelineClip.bgShape]); the engine draws
 * it behind the text. Below the grid: the shared background
 * controls (the same ones Styles → Background shows).
 */
@Composable
private fun BubblesTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    TabColumn {
        val shapes = listOf(
            TEXT_BG_NONE to "None",
            TEXT_BG_RECTANGLE to "Rectangle",
            TEXT_BG_ROUNDED to "Rounded",
            TEXT_BG_SPEECH to "Speech bubble",
            TEXT_BG_SPEECH_SQ to "Speech square",
            TEXT_BG_THOUGHT to "Thought bubble"
        )
        shapes.chunked(3).forEach { rowShapes ->
            Row(horizontalArrangement = Arrangement.spacedBy(TextEditGeometry.Grid.GAP_DP.dp)) {
                rowShapes.forEach { (shape, label) ->
                    BubbleTile(
                        shape = shape,
                        label = label,
                        selected = clip.bgShape == shape,
                        modifier = Modifier.weight(1f)
                    ) {
                        onUpdate { c ->
                            if (shape == TEXT_BG_NONE) {
                                c.copy(bgShape = TEXT_BG_NONE, bgOpacity = 0f)
                            } else {
                                c.copy(
                                    bgShape = shape,
                                    bgOpacity = if (c.bgOpacity == 0f) 100f else c.bgOpacity
                                )
                            }
                        }
                    }
                }
                repeat(3 - rowShapes.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(TextEditGeometry.Grid.GAP_DP.dp))
        }
        Spacer(Modifier.height(6.dp))
        BackgroundControls(clip, onUpdate, onEyedropper)
    }
}

@Composable
private fun BubbleTile(
    shape: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val def = remember(shape) { textBgShapeDef(shape) }
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                drawKey = listOf(shape, selected, scale.value, def),
                buildCmds = { w, h, dd ->
                    setScaleAbout(w / 2f, h / 2f, scale.value)
                    addRoundRect(
                        w / 2f, h / 2f, w, h, 10f * dd, TxtSurfaceArgb,
                        if (selected) 2f * dd else 0f,
                        if (selected) TxtAccentArgb else 0
                    )
                    // The shape preview, centred in the tile.
                    val pw = 46f * dd
                    val ph = 30f * dd
                    val cx = w / 2f
                    val cy = h / 2f
                    when {
                        def != null -> {
                            for (sub in def.subs) {
                                val pts = sub.pts
                                if (pts.size < 6) continue
                                val n = pts.size / 2
                                val xs = FloatArray(n)
                                val ys = FloatArray(n)
                                for (i in 0 until n) {
                                    xs[i] = cx + pts[i * 2] / 2f * pw
                                    ys[i] = cy + pts[i * 2 + 1] / 2f * ph
                                }
                                addFilledPolygon(xs, ys, n, TxtWhiteArgb)
                            }
                        }
                        shape == TEXT_BG_RECTANGLE ->
                            addRoundRect(cx, cy, pw, ph, 3f * dd, TxtWhiteArgb)
                        shape == TEXT_BG_ROUNDED ->
                            addRoundRect(cx, cy, pw, ph, ph * 0.4f, TxtWhiteArgb)
                        else -> Unit // None: the empty tile is the preview.
                    }
                    clearTransform()
                }
            )
            ChromePressBox(
                scale, 0.94f, onClick,
                Modifier.fillMaxSize(),
                description = label
            )
        }
        Spacer(Modifier.height(4.dp))
        GlLabelLine(
            label, UiLabelStyle.TEXT_11_SEMI,
            if (selected) TxtWhiteArgb else TxtDimArgb
        )
    }
}

// ── Colours (the build-217 tokens, as ARGB for the GL draws) ───────

private val TxtSurfaceArgb = AmPanel.toArgb()
private val TxtAccentArgb = AmAccent.toArgb()
private val TxtInkArgb = AmInk.toArgb()
private val TxtWhiteArgb = TextPrimary.toArgb()
private val TxtDimArgb = AmTextDim.toArgb()
private val TxtSliderInactiveArgb = 0xFF3A3A3A.toInt()

// ── Chrome: top bar, preview, group headers ────────────────────────

/** A wrapped note line in GL (the sections' explanatory texts). */
@Composable
private fun GlNoteText(text: String) {
    GlTextBlock(
        text = text,
        style = UiLabelStyle.ED_TEXT,
        colorArgb = TxtDimArgb,
        modifier = Modifier.fillMaxWidth(),
        align = TextAlign.Start
    )
}

// ── Hosted text fields (Android IME — text input never moves) ─────

/** The typing field: one clean hosted field under the preview. */
@Composable
private fun TextBoxField(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text("Type text", color = AmTextDim, fontSize = 14.sp) },
        maxLines = 3,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
        shape = RoundedCornerShape(AmCornerCard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AmAccent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** The font search field (hosted input, same IME law). */
@Composable
private fun FontSearchField(query: String, onQuery: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search Fonts", color = AmTextDim, fontSize = 14.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
        shape = RoundedCornerShape(AmCornerCard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AmAccent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Slider rows ────────────────────────────────────────────────────

/**
 * One slider row of the flat list: label (advance-sized, the
 * GlWidthRow pattern), the app's standard GL slider, and the value
 * readout formatted exactly as the old ruler slider's ("%d%s").
 */
@Composable
private fun GlTextSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    val g = TextEditGeometry.SliderRow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(g.H_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlLabelLine(label, UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
        Spacer(Modifier.width(g.LABEL_GAP_DP.dp))
        GlSlider(
            value = value.coerceIn(min, max),
            onValueChange = onValueChange,
            min = min,
            max = max,
            onValueChangeFinished = null,
            thumbArgb = TxtWhiteArgb,
            activeArgb = TxtWhiteArgb,
            inactiveArgb = TxtSliderInactiveArgb,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(g.VALUE_GAP_DP.dp))
        GlLabelLine(
            String.format(Locale.US, "%d%s", value.toInt(), unit),
            UiLabelStyle.TEXT_13_BOLD, TxtWhiteArgb
        )
    }
}

// ── Segmented rows (style toggles, alignment) ─────────────────────

private class SegmentItem(
    val label: String,
    val icon: ImageVector?,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * One segmented row on ONE GL surface: equal cells per
 * [TextEditGeometry.Segmented], selected = the lime fill with ink
 * content, one press box per cell.
 */
@Composable
private fun GlSegmentedRow(items: List<SegmentItem>, modifier: Modifier = Modifier) {
    val g = TextEditGeometry.Segmented
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val painters = items.map { item -> item.icon?.let { rememberVectorPainter(it) } }
    fun resolveAll(): List<Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?>> =
        items.mapIndexed { i, item ->
            val t = atlas.resolve(item.label, UiLabelStyle.TEXT_13_SEMI)
            val p = painters[i]
            val ic = if (p != null) {
                atlas.iconEntry(
                    "txtseg:${item.label}", p,
                    (g.ICON_DP * density).roundToInt(), density
                )
            } else null
            t to ic
        }
    var entries = resolveAll()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveAll()
    }
    val resolved = entries
    val scales = remember(items.map { it.label }) { items.map { Animatable(1f) } }
    val scaleValues = scales.map { it.value }
    BoxWithConstraints(modifier.fillMaxWidth().height(g.H_DP.dp)) {
        val rowWPx = maxWidth.value * density
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(resolved, scaleValues, rowWPx, atlas.generation),
            buildCmds = { w, h, dd ->
                val style = UiLabelStyle.TEXT_13_SEMI
                val textH = atlas.textHeightPx(style)
                val fm = atlas.fontMetrics(style)
                items.forEachIndexed { i, item ->
                    val cellW = g.cellWPx(w, items.size, dd)
                    val cellL = g.cellLeftPx(i, w, items.size, dd)
                    val cx = cellL + cellW / 2f
                    setScaleAbout(cx, h / 2f, scaleValues[i])
                    addRoundRect(
                        cx, h / 2f, cellW, h, g.CORNER_DP * dd,
                        if (item.selected) TxtAccentArgb else TxtSurfaceArgb
                    )
                    val (te, ie) = resolved[i]
                    val ink = if (item.selected) TxtInkArgb else TxtWhiteArgb
                    var x = g.runLeftPx(cellL, cellW, te?.advance ?: 0f, ie != null, dd)
                    if (ie != null) {
                        addText(
                            ie.index, x - pad, h / 2f - ie.rectH / 2f,
                            0f, 0f, ie.rectW.toFloat(), ie.rectH.toFloat(),
                            tintArgb = ink
                        )
                        x += (g.ICON_DP + g.ICON_GAP_DP) * dd
                    }
                    if (te != null) {
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(x, baseline, te.bearing, te.base, pad)
                        addText(
                            te.index, dstX, dstY,
                            0f, 0f, te.rectW.toFloat(), te.rectH.toFloat(),
                            tintArgb = ink
                        )
                    }
                    clearTransform()
                }
            }
        )
        items.forEachIndexed { i, item ->
            ChromePressBox(
                scales[i], 0.94f, item.onClick,
                Modifier
                    .offset(
                        x = (g.cellLeftPx(i, rowWPx, items.size, density) / density).dp,
                        y = 0.dp
                    )
                    .width((g.cellWPx(rowWPx, items.size, density) / density).dp)
                    .height(g.H_DP.dp),
                description = item.label
            )
        }
    }
}

// ── Small GL atoms: hug chip button, action row ───────────────────

/** A hug-content capsule button in GL (the old SheetChip's form). */
@Composable
private fun GlTextChipButton(label: String, onClick: () -> Unit) {
    val g = TextEditGeometry.Chips
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.TEXT_13_SEMI
    fun resolveChip() = atlas.resolve(label, style)
    var entry = resolveChip()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveChip()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    val wDp = if (e != null) (g.chipWPx(e.advance, density) / density).dp else 0.dp
    Box(Modifier.width(wDp).height(g.H_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, TxtSurfaceArgb)
                if (e != null) {
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                    val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.92f, onClick,
            Modifier.fillMaxSize(),
            description = label
        )
    }
}

/** A full-width action row in GL (the Path section's rows). */
@Composable
private fun GlActionRow(label: String, onClick: () -> Unit) {
    val g = TextEditGeometry.ActionRow
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.TEXT_13_MED
    fun resolveRow() = atlas.resolve(label, style)
    var entry = resolveRow()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveRow()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.fillMaxWidth().height(g.H_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, _ ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, TxtSurfaceArgb)
                if (e != null) {
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        g.PAD_H_DP * density, baseline, e.bearing, e.base, pad
                    )
                    addText(
                        e.index, dstX, dstY,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.97f, onClick,
            Modifier.fillMaxSize(),
            description = label
        )
    }
}

// ── Colour rows ────────────────────────────────────────────────────

private val Swatches = listOf(
    0xFF9C27B0, 0xFFE91E63, 0xFFF44336, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000,
    0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF00E5FF, 0xFF2196F3, 0xFF3F51B5
)

/**
 * One colour row of the flat list (the old TextColorRow, GL-drawn):
 * the label, a scrolling strip of swatches (selected = lime
 * outline), then the custom-colour tile (opens the picker dialog)
 * and the eyedropper tile (pick from the canvas) pinned on the
 * right. The row follows the text section's own strip form — the
 * Color editor's grid has no eyedropper / custom tiles to pin.
 */
@Composable
private fun GlColorRow(
    label: String,
    selected: Long,
    onSelect: (Long) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    val g = TextEditGeometry.Swatches
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = Swatches.none { it == selected }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(g.ROW_H_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlLabelLine(label, UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
        Spacer(Modifier.width(g.LABEL_GAP_DP.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(g.GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Swatches.forEach { c ->
                GlSwatchTile(argb = c, selected = c == selected) { onSelect(c) }
            }
        }
        Spacer(Modifier.width(g.PIN_GAP_DP.dp))
        GlPaletteTile(selected = selected, isCustom = isCustom) { showPicker = true }
        Spacer(Modifier.width(g.GAP_DP.dp))
        GlEyedropperTile { onEyedropper(onSelect) }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = Color(selected),
            onPick = { onSelect(it.toArgb().toLong() and 0xFFFFFFFFL) },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun GlSwatchTile(argb: Long, selected: Boolean, onClick: () -> Unit) {
    val g = TextEditGeometry.Swatches
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.CELL_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            drawKey = listOf(argb, selected, scale.value),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CORNER_DP * dd, argb.toInt(),
                    if (selected) 2f * dd else 0f,
                    if (selected) TxtAccentArgb else 0
                )
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.85f, onClick,
            Modifier.fillMaxSize(),
            description = "Color"
        )
    }
}

@Composable
private fun GlPaletteTile(selected: Long, isCustom: Boolean, onClick: () -> Unit) {
    val g = TextEditGeometry.Swatches
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val painter = rememberVectorPainter(Icons.Default.Palette)
    fun resolveIcon(): UiLabelAtlas.Entry? =
        atlas.iconEntry("txted:palette", painter, (17f * density).roundToInt(), density)
    var entry = resolveIcon()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveIcon()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.CELL_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, selected, isCustom, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(
                    w / 2f, h / 2f, w, h, g.CORNER_DP * dd,
                    if (isCustom) selected.toInt() else 0,
                    1.5f * dd, 0xFFFFFFFF.toInt()
                )
                if (e != null) {
                    addText(
                        e.index, w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.88f, onClick,
            Modifier.fillMaxSize(),
            description = "Custom color"
        )
    }
}

@Composable
private fun GlEyedropperTile(onClick: () -> Unit) {
    val g = TextEditGeometry.Swatches
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val painter = rememberVectorPainter(Icons.Default.Colorize)
    fun resolveIcon(): UiLabelAtlas.Entry? =
        atlas.iconEntry("txted:eyedropper", painter, (17f * density).roundToInt(), density)
    var entry = resolveIcon()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveIcon()
    }
    val e = entry
    val scale = remember { Animatable(1f) }
    Box(Modifier.size(g.CELL_DP.dp)) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(e, scale.value, atlas.generation),
            buildCmds = { w, h, dd ->
                setScaleAbout(w / 2f, h / 2f, scale.value)
                addRoundRect(w / 2f, h / 2f, w, h, g.CORNER_DP * dd, TxtSurfaceArgb)
                if (e != null) {
                    addText(
                        e.index, w / 2f - e.rectW / 2f, h / 2f - e.rectH / 2f,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = TxtWhiteArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.88f, onClick,
            Modifier.fillMaxSize(),
            description = "Pick from canvas"
        )
    }
}

/** File typefaces for the chip rasters, loaded once per font file. */
private val fontChipTypefaces = ConcurrentHashMap<String, Typeface>()

/** The typeface a font name's chip is rasterized in (its own face). */
private fun typefaceForFont(context: android.content.Context, name: String): Typeface =
    when (name) {
        "Roboto", "System", "" -> Typeface.DEFAULT
        "Serif" -> Typeface.SERIF
        "Monospace" -> Typeface.MONOSPACE
        "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
        else -> {
            val file = FontRepository.fileOrNull(context, name)
            if (file == null) {
                Typeface.DEFAULT
            } else {
                fontChipTypefaces.getOrPut(file.absolutePath) {
                    runCatching { Typeface.createFromFile(file) }.getOrNull() ?: Typeface.DEFAULT
                }
            }
        }
    }

/**
 * The atlas entry for one font chip: the font's name rasterized in
 * its own face by Android's text stack (the atlas pattern — native
 * only blits it). [rememberFontFamily] keeps the old behaviour: an
 * unseen Google font downloads when first shown; until its file is
 * here the name rasterizes in the default face, and the entry's key
 * flips when the real face arrives.
 */
@Composable
private fun rememberFontChipEntry(atlas: UiLabelAtlas, name: String): UiLabelAtlas.Entry? {
    val context = LocalContext.current
    rememberFontFamily(name)
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val ready = FontRepository.resolveNow(context, name) != null
    fun resolveFont(): UiLabelAtlas.Entry? {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = typefaceForFont(context, name)
        paint.textSize = TextEditGeometry.Chips.FONT_TEXT_SP * density * fontScale
        paint.color = android.graphics.Color.WHITE
        val adv = paint.measureText(name)
        if (adv <= 0f) return null
        val fm = paint.fontMetrics
        val inkH = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val key = "fontchip:$name:${if (ready) "ready" else "def"}:${paint.textSize}"
        return atlas.resolveIcon(key, ceil(adv).toInt().coerceAtLeast(1), inkH) { cv, x, y ->
            cv.drawText(name, x, y - fm.ascent, paint)
        }
    }
    var entry = resolveFont()
    if (atlas.overflowed) {
        atlas.reset()
        entry = resolveFont()
    }
    return entry
}

// ── Reveal section ─────────────────────────────────────────────────

/**
 * Reveal group (typewriter window, Alight `textprogress`): a static
 * start/end window of the text that is shown. An imported keyframed
 * reveal is preserved — the sliders stay hidden while it is
 * animated, and only an explicit tap replaces it with a static
 * window frozen at the finished state (see TextRevealValues).
 */
@Composable
private fun RevealSection(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val reveal = clip.textAnimators.firstOrNull { it.effectId == TextEffectIds.TEXTPROGRESS }
    if (TextRevealValues.isKeyframed(reveal)) {
        GlNoteText("Animated reveal — the window is keyframed in Alight Motion, so it changes over time.")
        Spacer(Modifier.height(6.dp))
        GlTextChipButton("Use static window") {
            val (s, e) = TextRevealValues.windowAt(reveal, 1f)
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, s, e)) }
        }
    } else {
        val (revealStart, revealEnd) = TextRevealValues.windowAt(reveal, 0f)
        GlTextSliderRow("Reveal start", revealStart * 100f, 0f, 100f, "%") { v ->
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, v / 100f, revealEnd)) }
        }
        GlTextSliderRow("Reveal end", revealEnd * 100f, 0f, 100f, "%") { v ->
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, revealStart, v / 100f)) }
        }
    }
}

// ── Path section (text-on-path, build 191) ────────────────────────

/**
 * Path group: attach this text to a PATH shape layer — the glyphs
 * then follow that layer's outline, evaluated live. The text follows
 * the path even when the path layer is hidden; hiding is not
 * detaching. While attached, the Start offset slider moves the text
 * along the path (a share of the path's whole length); text longer
 * than the path is cut at the path's end.
 */
@Composable
private fun PathSection(
    clip: TimelineClip,
    pathLayers: List<TimelineClip>,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val g = TextEditGeometry.LabelRow
    val attachedId = clip.textPathLayerId
    val attached = pathLayers.firstOrNull { it.id == attachedId }
    when {
        attached != null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(g.H_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(g.LABEL_W_DP.dp)) {
                    GlLabelLine("Layer", UiLabelStyle.TEXT_13_MED, TxtWhiteArgb)
                }
                GlLabelLine(
                    attached.name, UiLabelStyle.TEXT_13_BOLD, TxtWhiteArgb,
                    modifier = Modifier.weight(1f),
                    ellipsize = true
                )
            }
            GlTextSliderRow("Start offset", clip.textPathStart * 100f, 0f, 100f, "%") { v ->
                onUpdate { it.copy(textPathStart = v / 100f) }
            }
            GlActionRow("Detach from path") { onUpdate { it.copy(textPathLayerId = null) } }
        }
        attachedId != null -> {
            GlNoteText("The path layer this text was attached to is gone.")
            Spacer(Modifier.height(6.dp))
            GlActionRow("Detach") { onUpdate { it.copy(textPathLayerId = null) } }
        }
        pathLayers.isEmpty() -> {
            GlNoteText("No path layer in this project yet. Draw one with the Pen tool first, then attach this text to it here.")
        }
        else -> {
            GlNoteText("Attach to a path layer — the text will follow its outline:")
            Spacer(Modifier.height(6.dp))
            pathLayers.forEach { layer ->
                GlActionRow(layer.name) { onUpdate { it.copy(textPathLayerId = layer.id) } }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
