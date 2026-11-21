package com.example.ui.components

import com.example.ui.theme.AmCornerCard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Details
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FilterTiltShift
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.InvertColorsOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MonochromePhotos
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.ClipFx
import com.example.model.ClipFxLibrary
import com.example.model.FxDef
import com.example.model.FxLists
import com.example.model.FxTileArt
import com.example.model.TimelineClip
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable
import kotlin.math.roundToInt

/** The browser's three standing chips, ahead of the library categories. */
private const val FX_CHIP_ALL = "All"
private const val FX_CHIP_FAVORITES = "Favorites"
private const val FX_CHIP_RECENT = "Recent"

private enum class FxPanelMode { APPLIED, BROWSER, EDITOR }

/**
 * Effects panel — build 200 rebuilds its layout after Sarowar's reference video
 * (EFFECTS-LAYOUT-SPEC.md), in three states over the layer's effect stack:
 *
 * 1. **Applied Effects** (home): a wide "Add effect" button, then the applied
 *    effects as full-width rows — expand chevron + name on the left, eye
 *    (enable/disable) and a drag grip on the right; dragging the grip reorders
 *    the stack. Tapping a row opens its parameter editor. Empty state = the
 *    reference's helper text.
 * 2. **Effect browser** ("Add effect"): category chips (All / Favorites /
 *    Recent / the [ClipFxLibrary] categories; selected = white fill, black
 *    text) over a 4-column grid of procedural artwork tiles. Tapping a tile
 *    APPLIES the effect and selects the tile (white border + sliders badge);
 *    tapping the badge — or re-tapping the selected tile — opens the applied
 *    instance's parameter editor. While a tile is selected the screen shows
 *    the bookmark / info buttons at the preview's bottom-left (the panel
 *    reports the selection through [onBrowserSelectionChange]).
 * 3. **Parameter editor**: one ruler row per parameter — the shared
 *    [RulerDialSlider] with the reference's one borrowed accent, a magenta
 *    needle — plus the per-effect Reset and a Remove action.
 *
 * Nothing about the effect MODEL changed: every edit rides the existing
 * [onChange] funnel into `setClipEffects` (one undo step for the whole panel
 * session, pushed by the screen's beginAdjust; Controller fan-out included),
 * the 32-effect cap and its honest limit state stay, and effect values past
 * the declared params (Motion Blur's flags, keyframed tails) are preserved
 * verbatim by the slider writes, exactly as before.
 */
@Composable
fun ClipEffectsPanel(
    clip: TimelineClip?,
    onChange: (List<ClipFx>) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Chroma Key eyedropper: asks the screen to let the person move the ring over the picture. `onPicked` gets the colour
     * (0xAARRGGBB), `onCancel` is called when the pick is abandoned (back, panel closed, Cancel).
     */
    onPickColor: ((onPicked: (Long) -> Unit, onCancel: () -> Unit) -> Unit)? = null,
    /** Bookmarked effect ids (per-user, persisted by the screen; see [FxLists]). */
    favorites: List<String> = emptyList(),
    /** Most-recently-applied effect ids, newest first (persisted by the screen). */
    recent: List<String> = emptyList(),
    /** Records an application so the screen can push the id onto [recent]. */
    onApplied: (String) -> Unit = {},
    /** The screen's header title: "Applied Effects", or the edited effect's name in the editor. */
    onTitleChange: (String) -> Unit = {},
    /** The browser's selected effect id (null outside the browser) — drives the preview's bookmark / info buttons. */
    onBrowserSelectionChange: (String?) -> Unit = {}
) {
    if (clip == null) return
    val effects = clip.effects
    // The pick finishes later, so it must see the effect list of THEN, not the one of the moment of the tap.
    val latestEffects by rememberUpdatedState(effects)
    var mode by remember(clip.id) { mutableStateOf(FxPanelMode.APPLIED) }
    var chip by remember(clip.id) { mutableStateOf(FX_CHIP_ALL) }
    var browserSelDefId by remember(clip.id) { mutableStateOf<String?>(null) }
    var lastAppliedInstance by remember(clip.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingId by remember(clip.id) { mutableStateOf<String?>(null) }
    val full = effects.size >= ClipFxLibrary.MAX_EFFECTS

    val editingFx = effects.firstOrNull { it.id == editingId }
    val editingDef = editingFx?.let { ClipFxLibrary.def(it.type) }

    LaunchedEffect(mode, editingDef?.name) {
        onTitleChange(
            if (mode == FxPanelMode.EDITOR && editingDef != null) editingDef.name else "Applied Effects"
        )
    }
    LaunchedEffect(mode, browserSelDefId) {
        onBrowserSelectionChange(if (mode == FxPanelMode.BROWSER) browserSelDefId else null)
    }
    // The edited effect vanished (undo, remove): fall back to the applied list.
    LaunchedEffect(effects, mode) {
        if (mode == FxPanelMode.EDITOR && effects.none { it.id == editingId }) {
            editingId = null
            mode = FxPanelMode.APPLIED
        }
    }

    fun applyDef(def: FxDef) {
        if (latestEffects.size >= ClipFxLibrary.MAX_EFFECTS) return
        val fx = ClipFxLibrary.create(def.id) ?: return
        lastAppliedInstance = lastAppliedInstance + (def.id to fx.id)
        browserSelDefId = def.id
        onApplied(def.id)
        onChange(latestEffects + fx)
    }

    fun openEditorForInstance(fxId: String) {
        editingId = fxId
        mode = FxPanelMode.EDITOR
    }

    fun openEditorForDef(defId: String) {
        val inst = lastAppliedInstance[defId]?.let { id -> latestEffects.firstOrNull { it.id == id } }
            ?: latestEffects.lastOrNull { it.type == defId }
        if (inst != null) openEditorForInstance(inst.id)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("clip_effects_panel")
    ) {
        when (mode) {
            FxPanelMode.APPLIED -> {
                // ---- Add effect: the wide button; at the cap it turns into the honest limit state ----
                // Build 252 (stage 5): drawn in GL — capsule fill, the
                // centred label and the right-aligned count. (The
                // Compose button's press scaled only its texts; the
                // fill sat outside the graphicsLayer.)
                val addDensity = LocalDensity.current.density
                val addAtlas = rememberUiAtlas()
                val addPad = UiLabelAtlas.PAD.toFloat()
                val addLabel = if (full) "Effect limit reached" else "Add effect"
                val addCount = "${effects.size} / ${ClipFxLibrary.MAX_EFFECTS}"
                fun resolveAdd(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> =
                    addAtlas.resolve(addLabel, UiLabelStyle.FX_LABEL) to
                        addAtlas.resolve(addCount, UiLabelStyle.SHEET_DESC)
                var addEntries = resolveAdd()
                if (addAtlas.overflowed) {
                    addAtlas.reset()
                    addEntries = resolveAdd()
                }
                val (addLabelEntry, addCountEntry) = addEntries
                val addScale = remember { Animatable(1f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("fx_add_effect")
                ) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        atlas = addAtlas,
                        drawKey = listOf(addLabelEntry, addCountEntry, addScale.value, full, addAtlas.generation),
                        buildCmds = { w, h, dd ->
                            addRoundRect(
                                w / 2f, h / 2f, w, h, h / 2f,
                                (if (full) AmPanelHigh.copy(alpha = 0.45f) else AmPanelHigh).toArgb()
                            )
                            setScaleAbout(w / 2f, h / 2f, addScale.value)
                            fun centredText(e: UiLabelAtlas.Entry?, style: UiLabelStyle, originX: Float, tint: Int) {
                                if (e == null) return
                                val textH = addAtlas.textHeightPx(style)
                                val fm = addAtlas.fontMetrics(style)
                                val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                                val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                                val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, addPad)
                                addText(
                                    e.index, dstX, dstY,
                                    0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                    tintArgb = tint
                                )
                            }
                            if (addLabelEntry != null) {
                                centredText(
                                    addLabelEntry, UiLabelStyle.FX_LABEL,
                                    EditorGeometry.centredOriginPx(w / 2f, addLabelEntry.advance),
                                    (if (full) AmTextDim else TextPrimary).toArgb()
                                )
                            }
                            if (addCountEntry != null) {
                                centredText(
                                    addCountEntry, UiLabelStyle.SHEET_DESC,
                                    w - 12f * dd - addCountEntry.advance,
                                    AmTextDim.toArgb()
                                )
                            }
                            clearTransform()
                        }
                    )
                    if (!full) {
                        ChromePressBox(
                            addScale, 0.94f, { mode = FxPanelMode.BROWSER },
                            Modifier.fillMaxSize(),
                            description = addLabel
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (effects.isEmpty()) {
                    GlTextBlock(
                        text = "See applied effects here, reorder them by dragging, and control their parameters",
                        style = UiLabelStyle.ED_TEXT,
                        colorArgb = AmTextDim.toArgb(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    // ---- Applied rows with grip drag-reorder (writes the same list funnel) ----
                    var draggingId by remember(clip.id) { mutableStateOf<String?>(null) }
                    var dragAccum by remember(clip.id) { mutableFloatStateOf(0f) }
                    val rowStepPx = with(LocalDensity.current) { 45.dp.toPx() } // 40 dp row + 5 dp gap
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState, enabled = draggingId == null),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (fx in effects) {
                            val def = ClipFxLibrary.def(fx.type) ?: continue
                            AppliedFxRow(
                                def = def,
                                fx = fx,
                                isDragging = fx.id == draggingId,
                                dragOffsetPx = if (fx.id == draggingId) dragAccum else 0f,
                                onOpen = { openEditorForInstance(fx.id) },
                                onToggleEnabled = {
                                    onChange(latestEffects.map {
                                        if (it.id == fx.id) it.copy(enabled = !it.enabled) else it
                                    })
                                },
                                onDragStart = {
                                    draggingId = fx.id
                                    dragAccum = 0f
                                },
                                onDrag = { dy ->
                                    val list = latestEffects
                                    val idx = list.indexOfFirst { it.id == fx.id }
                                    if (idx >= 0) {
                                        dragAccum += dy
                                        if (dragAccum > rowStepPx && idx < list.lastIndex) {
                                            val next = list.toMutableList()
                                            next[idx] = list[idx + 1]
                                            next[idx + 1] = list[idx]
                                            onChange(next)
                                            dragAccum -= rowStepPx
                                        } else if (dragAccum < -rowStepPx && idx > 0) {
                                            val next = list.toMutableList()
                                            next[idx] = list[idx - 1]
                                            next[idx - 1] = list[idx]
                                            onChange(next)
                                            dragAccum += rowStepPx
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragAccum = 0f
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            FxPanelMode.BROWSER -> {
                // ---- Header row: collapse back to the applied list ----
                // Build 252 (stage 5): GL strip (FxHeaderRow).
                FxHeaderRow(
                    icon = Icons.Default.KeyboardArrowDown,
                    iconDescription = "Back to applied effects",
                    title = "Add effect",
                    titleStyle = UiLabelStyle.CHIP_BIG,
                    titleArgb = TextPrimary.toArgb(),
                    tag = "fx_browser_back",
                    trailingText = if (full) "Limit ${ClipFxLibrary.MAX_EFFECTS} reached" else null,
                    onBack = { mode = FxPanelMode.APPLIED }
                )
                Spacer(Modifier.height(6.dp))
                // ---- Category chips: selected = white fill / black text ----
                val chips = listOf(FX_CHIP_ALL, FX_CHIP_FAVORITES, FX_CHIP_RECENT) + ClipFxLibrary.categories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in chips) {
                        GlChip(
                            label = c,
                            selected = c == chip,
                            tag = "fx_category_$c",
                            onClick = { chip = c },
                            modifier = Modifier.padding(end = 6.dp),
                            padVDp = 5f,
                            styleOn = UiLabelStyle.ED_TEXT_BOLD,
                            styleOff = UiLabelStyle.ED_TEXT_SEMI,
                            fillOnArgb = Color.White.toArgb(),
                            fillOffArgb = AmPanelHigh.toArgb(),
                            textOnArgb = Color.Black.toArgb(),
                            textOffArgb = TextPrimary.toArgb()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val defs = when (chip) {
                    FX_CHIP_ALL -> ClipFxLibrary.all
                    FX_CHIP_FAVORITES -> FxLists.favoriteDefs(ClipFxLibrary.all, favorites)
                    FX_CHIP_RECENT -> FxLists.recentDefs(ClipFxLibrary.all, recent)
                    else -> ClipFxLibrary.all.filter { it.category == chip }
                }
                if (defs.isEmpty()) {
                    GlTextBlock(
                        text = when (chip) {
                            FX_CHIP_FAVORITES ->
                                "No favorites yet — tap an effect to select it, then the bookmark button by the preview to keep it here."
                            FX_CHIP_RECENT ->
                                "Nothing applied yet — effects you add will appear here, newest first."
                            else -> "No effects in this category."
                        },
                        style = UiLabelStyle.ED_TEXT,
                        colorArgb = AmTextDim.toArgb(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(defs, key = { it.id }) { def ->
                            val selected = def.id == browserSelDefId
                            FxGridTile(
                                def = def,
                                selected = selected,
                                dimmed = full && !selected,
                                onClick = {
                                    if (selected) openEditorForDef(def.id) else applyDef(def)
                                },
                                onBadgeClick = { openEditorForDef(def.id) }
                            )
                        }
                    }
                }
            }

            FxPanelMode.EDITOR -> {
                val fx = editingFx
                val def = editingDef
                if (fx == null || def == null) {
                    // The fallback effect above switches back to APPLIED; render nothing meanwhile.
                    Box(Modifier.fillMaxSize())
                } else {
                    // ---- Back row + Remove ----
                    // Build 252 (stage 5): GL header (FxHeaderRow).
                    FxHeaderRow(
                        icon = Icons.Default.KeyboardArrowLeft,
                        iconDescription = "Back to applied effects",
                        title = "Applied Effects",
                        titleStyle = UiLabelStyle.ED_TEXT_SEMI,
                        titleArgb = AmTextDim.toArgb(),
                        tag = "fx_editor_back",
                        pillIcon = Icons.Default.Delete,
                        pillLabel = "Remove",
                        pillTintArgb = AmDanger.toArgb(),
                        pillTag = "fx_remove_${fx.type}",
                        onPill = {
                            onChange(latestEffects.filter { it.id != fx.id })
                            editingId = null
                            mode = FxPanelMode.APPLIED
                        },
                        onBack = { mode = FxPanelMode.APPLIED }
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (fx.type == ClipFxLibrary.CHROMA_KEY && onPickColor != null) {
                            KeyPickerRow(
                                swatch = Color(
                                    ClipFxLibrary.keySwatchArgb(
                                        fx.values.getOrNull(0) ?: def.params[0].default,
                                        fx.values.getOrNull(1) ?: def.params[1].default,
                                        fx.values.getOrNull(5) ?: def.params[5].default
                                    ).toInt()
                                ),
                                onPick = {
                                    val id = fx.id
                                    val wasOn = fx.enabled
                                    // Show the picture without the key while picking, or the screen would already be see-through.
                                    onChange(latestEffects.map { if (it.id == id) it.copy(enabled = false) else it })
                                    onPickColor(
                                        { argb ->
                                            val key = ClipFxLibrary.keyFromRgb(argb)
                                            onChange(latestEffects.map {
                                                if (it.id != id) it else {
                                                    val vals = it.values.toMutableList()
                                                    while (vals.size < def.params.size) vals.add(def.params[vals.size].default)
                                                    vals[0] = key[0]
                                                    vals[1] = key[1]
                                                    vals[5] = key[2]
                                                    vals[6] = key[3]
                                                    it.copy(enabled = true, values = vals)
                                                }
                                            })
                                        },
                                        {
                                            onChange(latestEffects.map { if (it.id == id) it.copy(enabled = wasOn) else it })
                                        }
                                    )
                                }
                            )
                        }
                        def.params.forEachIndexed { idx, p ->
                            val value = (fx.values.getOrNull(idx) ?: p.default).coerceIn(p.min, p.max)
                            RulerDialSlider(
                                label = p.name,
                                labelWidth = 86.dp,
                                value = value,
                                onValueChange = { v ->
                                    // Keep values past the declared params (Motion Blur stores its
                                    // usePos / useScale / useAngle flags there); rebuilding from the
                                    // params alone would silently drop them.
                                    val vals = (0 until maxOf(def.params.size, fx.values.size)).map { i ->
                                        if (i == idx) v else (fx.values.getOrNull(i) ?: def.params.getOrNull(i)?.default ?: 0f)
                                    }
                                    onChange(effects.map { if (it.id == fx.id) it.copy(values = vals) else it })
                                },
                                min = p.min,
                                max = p.max,
                                unit = p.unit,
                                isPercentage = false,
                                step = p.step,
                                accentNeedle = true,
                                modifier = Modifier.testTag("fx_slider_${fx.type}_$idx")
                            )
                        }
                        Box(modifier = Modifier.padding(top = 4.dp)) {
                            FxPillButtonGl(
                                icon = Icons.Default.Refresh,
                                label = "Reset",
                                tintArgb = TextPrimary.toArgb(),
                                tag = "fx_reset_${fx.type}",
                                padHDp = 10f,
                                padVDp = 7f,
                                iconGapDp = 5f,
                                textStyle = UiLabelStyle.TEXT_11_SEMI,
                                onClick = {
                                    val vals = def.params.map { it.default } + fx.values.drop(def.params.size)
                                    onChange(effects.map { if (it.id == fx.id) it.copy(values = vals) else it })
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Build 252 (stage 5): an effects header row in GL — the back
 * cluster (18 dp icon + title, padded 4 dp, the whole cluster
 * press-scales as the Compose Row did) plus either a right-aligned
 * trailing text or the Remove pill (capsule fill static, its icon +
 * label press-scale, as its modifier chain had them).
 */
@Composable
private fun FxHeaderRow(
    icon: ImageVector,
    iconDescription: String,
    title: String,
    titleStyle: UiLabelStyle,
    titleArgb: Int,
    tag: String,
    trailingText: String? = null,
    pillIcon: ImageVector? = null,
    pillLabel: String? = null,
    pillTintArgb: Int = 0,
    pillTag: String? = null,
    onPill: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val iconPainter = rememberVectorPainter(icon)
    val pillPainter = pillIcon?.let { rememberVectorPainter(it) }
    val iconPx = (18f * density).roundToInt()
    val pillIconPx = (14f * density).roundToInt()
    fun resolveHeader(): List<UiLabelAtlas.Entry?> {
        val titleE = atlas.resolve(title, titleStyle)
        val trailE = trailingText?.let { atlas.resolve(it, UiLabelStyle.SHEET_DESC) }
        val iconE = atlas.resolveIcon("fxhdr:${icon.name}", iconPx, iconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(iconPainter, iconPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val pillLabelE = pillLabel?.let { atlas.resolve(it, UiLabelStyle.ED_TEXT_SEMI) }
        val pillIconE = if (pillPainter != null) {
            atlas.resolveIcon("fxhdr:pill:${pillIcon.name}", pillIconPx, pillIconPx) { cv, x, y ->
                val b = rasterizePainterBitmap(pillPainter, pillIconPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
        } else {
            null
        }
        return listOf(titleE, trailE, iconE, pillLabelE, pillIconE)
    }
    var headerEntries = resolveHeader()
    if (atlas.overflowed) {
        atlas.reset()
        headerEntries = resolveHeader()
    }
    val (titleEntry, trailEntry, iconEntry, pillLabelEntry, pillIconEntry) = headerEntries
    val backScale = remember { Animatable(1f) }
    val pillScale = remember { Animatable(1f) }
    val titleTextH = atlas.textHeightPx(titleStyle)
    val headerHDp = 8f + maxOf(18f, titleTextH / density)
    val clusterWPx = 30f * density + (titleEntry?.advance ?: 0f)
    val pillWPx = if (pillLabel != null) {
        36f * density + (pillLabelEntry?.advance ?: 0f)
    } else {
        0f
    }
    val pillHDp = maxOf(14f, atlas.textHeightPx(UiLabelStyle.ED_TEXT_SEMI) / density) + 10f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHDp.dp)
    ) {
        val wPx = maxWidth.value * density
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                headerEntries, backScale.value, pillScale.value, titleArgb, pillTintArgb, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                // Back cluster: icon at 4..22, title after a 4 dp gap.
                setScaleAbout(clusterWPx / 2f, cy, backScale.value)
                if (iconEntry != null) {
                    addText(
                        iconEntry.index,
                        4f * dd - pad, cy - iconPx / 2f - pad,
                        0f, 0f, iconEntry.rectW.toFloat(), iconEntry.rectH.toFloat(),
                        tintArgb = TextPrimary.toArgb()
                    )
                }
                if (titleEntry != null) {
                    val fm = atlas.fontMetrics(titleStyle)
                    val top = EditorGeometry.centredTextTopPx(0f, h, titleTextH)
                    val baseline = PanelGeometry.lineBaselinePx(top, titleTextH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(26f * dd, baseline, titleEntry.bearing, titleEntry.base, pad)
                    addText(
                        titleEntry.index, dstX, dstY,
                        0f, 0f, titleEntry.rectW.toFloat(), titleEntry.rectH.toFloat(),
                        tintArgb = titleArgb
                    )
                }
                clearTransform()
                if (trailEntry != null) {
                    val style = UiLabelStyle.SHEET_DESC
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(w - trailEntry.advance, baseline, trailEntry.bearing, trailEntry.base, pad)
                    addText(
                        trailEntry.index, dstX, dstY,
                        0f, 0f, trailEntry.rectW.toFloat(), trailEntry.rectH.toFloat(),
                        tintArgb = AmTextDim.toArgb()
                    )
                }
                if (pillLabel != null) {
                    val pillLeft = w - pillWPx
                    val pillH = maxOf(14f * dd, atlas.textHeightPx(UiLabelStyle.ED_TEXT_SEMI).toFloat()) + 10f * dd
                    addRoundRect(w - pillWPx / 2f, cy, pillWPx, pillH, pillH / 2f, AmPanelHigh.toArgb())
                    setScaleAbout(w - pillWPx / 2f, cy, pillScale.value)
                    if (pillIconEntry != null) {
                        addText(
                            pillIconEntry.index,
                            pillLeft + 9f * dd - pad, cy - pillIconPx / 2f - pad,
                            0f, 0f, pillIconEntry.rectW.toFloat(), pillIconEntry.rectH.toFloat(),
                            tintArgb = pillTintArgb
                        )
                    }
                    if (pillLabelEntry != null) {
                        val style = UiLabelStyle.ED_TEXT_SEMI
                        val textH = atlas.textHeightPx(style)
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                        val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(
                            pillLeft + 27f * dd, baseline, pillLabelEntry.bearing, pillLabelEntry.base, pad
                        )
                        addText(
                            pillLabelEntry.index, dstX, dstY,
                            0f, 0f, pillLabelEntry.rectW.toFloat(), pillLabelEntry.rectH.toFloat(),
                            tintArgb = pillTintArgb
                        )
                    }
                    clearTransform()
                }
            }
        )
        ChromePressBox(
            backScale, 0.94f, onBack,
            Modifier
                .width((clusterWPx / density).dp)
                .height(headerHDp.dp),
            tag = tag,
            description = iconDescription
        )
        if (pillLabel != null && onPill != null && pillTag != null) {
            ChromePressBox(
                pillScale, 0.94f, onPill,
                Modifier
                    .offset(
                        x = ((wPx - pillWPx) / density).dp,
                        y = ((headerHDp - pillHDp) / 2f).dp
                    )
                    .width((pillWPx / density).dp)
                    .height(pillHDp.dp),
                tag = pillTag,
                description = pillLabel
            )
        }
    }
}

/**
 * Build 252 (stage 5): a small AmPanelHigh capsule pill button in
 * GL (the Reset / Pick-from-picture pattern): static fill, the
 * 14 dp icon + label press-scale about the pill's centre.
 */
@Composable
private fun FxPillButtonGl(
    icon: ImageVector,
    label: String,
    tintArgb: Int,
    tag: String,
    padHDp: Float,
    padVDp: Float,
    iconGapDp: Float,
    textStyle: UiLabelStyle,
    onClick: () -> Unit
) {
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val painter = rememberVectorPainter(icon)
    val iconPx = (14f * density).roundToInt()
    fun resolvePill(): Pair<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
        val labelE = atlas.resolve(label, textStyle)
        val iconE = atlas.resolveIcon("fxpill:${icon.name}", iconPx, iconPx) { cv, x, y ->
            val b = rasterizePainterBitmap(painter, iconPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        return labelE to iconE
    }
    var pillEntries = resolvePill()
    if (atlas.overflowed) {
        atlas.reset()
        pillEntries = resolvePill()
    }
    val (labelEntry, iconEntry) = pillEntries
    val scale = remember { Animatable(1f) }
    val textH = atlas.textHeightPx(textStyle)
    val wDp = (padHDp * 2 + 14f + iconGapDp + (labelEntry?.advance ?: 0f) / density).dp
    val hDp = (maxOf(14f, textH / density) + padVDp * 2).dp
    Box(
        modifier = Modifier
            .width(wDp)
            .height(hDp)
    ) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(labelEntry, iconEntry, scale.value, tintArgb, atlas.generation),
            buildCmds = { w, h, dd ->
                addRoundRect(w / 2f, h / 2f, w, h, h / 2f, AmPanelHigh.toArgb())
                setScaleAbout(w / 2f, h / 2f, scale.value)
                if (iconEntry != null) {
                    addText(
                        iconEntry.index,
                        padHDp * dd - pad, h / 2f - iconPx / 2f - pad,
                        0f, 0f, iconEntry.rectW.toFloat(), iconEntry.rectH.toFloat(),
                        tintArgb = tintArgb
                    )
                }
                if (labelEntry != null) {
                    val fm = atlas.fontMetrics(textStyle)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(
                        (padHDp + 14f + iconGapDp) * dd, baseline, labelEntry.bearing, labelEntry.base, pad
                    )
                    addText(
                        labelEntry.index, dstX, dstY,
                        0f, 0f, labelEntry.rectW.toFloat(), labelEntry.rectH.toFloat(),
                        tintArgb = tintArgb
                    )
                }
                clearTransform()
            }
        )
        ChromePressBox(
            scale, 0.94f, onClick,
            Modifier.fillMaxSize(),
            tag = tag,
            description = label
        )
    }
}

/** One applied-effect row: chevron + name (tap = parameter editor), eye, drag grip. */
@Composable
private fun AppliedFxRow(
    def: FxDef,
    fx: ClipFx,
    isDragging: Boolean,
    dragOffsetPx: Float,
    onOpen: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    // Build 252 (stage 5): the row is drawn in GL — AmPanelHigh
    // r12 fill (+ the dragging hairline), chevron, name (ellipsized),
    // the "Off" tag, eye and grip glyphs. The left zone keeps its
    // 0.99 press (content only — the fill sat on the outer Row), the
    // eye its own press, and the grip keeps its exact drag gesture.
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val chevronPainter = rememberVectorPainter(Icons.Default.KeyboardArrowRight)
    val eyePainter = rememberVectorPainter(if (fx.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff)
    val gripPainter = rememberVectorPainter(Icons.Default.DragHandle)
    val chevronPx = (17f * density).roundToInt()
    val eyePx = (18f * density).roundToInt()
    val gripPx = (18f * density).roundToInt()
    fun resolveFxRow(): List<UiLabelAtlas.Entry?> {
        val name = atlas.resolve(def.name, UiLabelStyle.SHEET_ITEM_SEMI)
        val off = if (!fx.enabled) atlas.resolve("Off", UiLabelStyle.TEXT_10) else null
        val chevron = atlas.resolveIcon("fxrow:chevron", chevronPx, chevronPx) { cv, x, y ->
            val b = rasterizePainterBitmap(chevronPainter, chevronPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val eye = atlas.resolveIcon("fxrow:eye:${fx.enabled}", eyePx, eyePx) { cv, x, y ->
            val b = rasterizePainterBitmap(eyePainter, eyePx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        val grip = atlas.resolveIcon("fxrow:grip", gripPx, gripPx) { cv, x, y ->
            val b = rasterizePainterBitmap(gripPainter, gripPx, density)
            cv.drawBitmap(b, x, y, null)
            b.recycle()
        }
        return listOf(name, off, chevron, eye, grip)
    }
    var fxRowEntries = resolveFxRow()
    if (atlas.overflowed) {
        atlas.reset()
        fxRowEntries = resolveFxRow()
    }
    val (nameEntry, offEntry, chevronEntry, eyeEntry, gripEntry) = fxRowEntries
    val leftScale = remember { Animatable(1f) }
    val eyeScale = remember { Animatable(1f) }
    val nameArgb = (if (fx.enabled) TextPrimary else TextSecondary).toArgb()
    val eyeArgb = (if (fx.enabled) TextPrimary else TextSecondary).toArgb()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
            .zIndex(if (isDragging) 1f else 0f)
            .testTag("fx_row_${fx.type}")
    ) {
        val wPx = maxWidth.value * density
        val leftWPx = wPx - 68f * density
        // The name ellipsizes inside its zone (chevron + gaps +
        // the "Off" tag when disabled).
        val shownNameEntry = run {
            val zone = leftWPx - 10f * density - 23f * density -
                (if (!fx.enabled) 6f * density + (offEntry?.advance ?: 0f) else 0f)
            if (nameEntry != null && nameEntry.advance > zone) {
                val shown = ChromeGeometry.ellipsize(def.name, zone) { atlas.measure(it, UiLabelStyle.SHEET_ITEM_SEMI) }
                if (shown != null && shown != def.name) {
                    atlas.resolve(shown, UiLabelStyle.SHEET_ITEM_SEMI) ?: nameEntry
                } else {
                    nameEntry
                }
            } else {
                nameEntry
            }
        }
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                shownNameEntry, offEntry, chevronEntry, eyeEntry, gripEntry,
                leftScale.value, eyeScale.value, isDragging, nameArgb, eyeArgb, atlas.generation
            ),
            buildCmds = { w, h, dd ->
                val cy = h / 2f
                addRoundRect(
                    w / 2f, cy, w, h, 12f * dd,
                    AmPanelHigh.toArgb(),
                    if (isDragging) 1f * dd else 0f,
                    AmHairlineStrong.toArgb()
                )
                // Left press zone: chevron + name + "Off".
                setScaleAbout(leftWPx / 2f, cy, leftScale.value)
                if (chevronEntry != null) {
                    addText(
                        chevronEntry.index,
                        10f * dd - pad, cy - chevronPx / 2f - pad,
                        0f, 0f, chevronEntry.rectW.toFloat(), chevronEntry.rectH.toFloat(),
                        tintArgb = TextSecondary.toArgb()
                    )
                }
                var nameRight = 33f * dd
                if (shownNameEntry != null) {
                    val style = UiLabelStyle.SHEET_ITEM_SEMI
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(33f * dd, baseline, shownNameEntry.bearing, shownNameEntry.base, pad)
                    addText(
                        shownNameEntry.index, dstX, dstY,
                        0f, 0f, shownNameEntry.rectW.toFloat(), shownNameEntry.rectH.toFloat(),
                        tintArgb = nameArgb
                    )
                    nameRight += shownNameEntry.advance
                }
                if (offEntry != null) {
                    val style = UiLabelStyle.TEXT_10
                    val textH = atlas.textHeightPx(style)
                    val fm = atlas.fontMetrics(style)
                    val top = EditorGeometry.centredTextTopPx(0f, h, textH)
                    val baseline = PanelGeometry.lineBaselinePx(top, textH, fm.ascent, fm.descent)
                    val (dstX, dstY) = EditorGeometry.textDst(nameRight + 6f * dd, baseline, offEntry.bearing, offEntry.base, pad)
                    addText(
                        offEntry.index, dstX, dstY,
                        0f, 0f, offEntry.rectW.toFloat(), offEntry.rectH.toFloat(),
                        tintArgb = TextSecondary.toArgb()
                    )
                }
                clearTransform()
                if (eyeEntry != null) {
                    val cx = w - 51f * dd
                    setScaleAbout(cx, cy, eyeScale.value)
                    addText(
                        eyeEntry.index,
                        cx - eyePx / 2f - pad, cy - eyePx / 2f - pad,
                        0f, 0f, eyeEntry.rectW.toFloat(), eyeEntry.rectH.toFloat(),
                        tintArgb = eyeArgb
                    )
                    clearTransform()
                }
                if (gripEntry != null) {
                    addText(
                        gripEntry.index,
                        w - 17f * dd - gripPx / 2f - pad, cy - gripPx / 2f - pad,
                        0f, 0f, gripEntry.rectW.toFloat(), gripEntry.rectH.toFloat(),
                        tintArgb = TextSecondary.toArgb()
                    )
                }
            }
        )
        ChromePressBox(
            leftScale, 0.99f, onOpen,
            Modifier
                .width((leftWPx / density).dp)
                .fillMaxHeight(),
            description = def.name
        )
        ChromePressBox(
            eyeScale, 0.94f, onToggleEnabled,
            Modifier
                .offset(x = ((wPx - 68f * density) / density).dp, y = 3.dp)
                .size(34.dp),
            tag = "fx_toggle_${fx.type}",
            description = if (fx.enabled) "Turn off" else "Turn on"
        )
        Box(
            modifier = Modifier
                .offset(x = ((wPx - 34f * density) / density).dp, y = 3.dp)
                .size(34.dp)
                .pointerInput(fx.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        }
                    )
                }
                .testTag("fx_grip_${fx.type}")
        )
    }
}

/** One browser tile: procedural artwork + name; the selected tile gets the white border + sliders badge. */
@Composable
private fun FxGridTile(
    def: FxDef,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onBadgeClick: () -> Unit
) {
    val tileShape = RoundedCornerShape(8.dp) // the ARTWORK's corners (content, as in the reference)
    // Build 252 (stage 5): the tile's artwork stays its Compose
    // Canvas — procedural colour gradients + a star field cannot
    // pass the alpha-only atlas (documented stop, stage 4 law).
    // Everything around it migrates: the dim veil, the selected
    // border and badge, and the two-line label are drawn in GL.
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val tunePainter = rememberVectorPainter(Icons.Default.Tune)
    val tunePx = (11f * density).roundToInt()
    fun resolveBadge() = atlas.resolveIcon("fxtile:tune", tunePx, tunePx) { cv, x, y ->
        val b = rasterizePainterBitmap(tunePainter, tunePx, density)
        cv.drawBitmap(b, x, y, null)
        b.recycle()
    }
    var badgeEntry = resolveBadge()
    if (atlas.overflowed) {
        atlas.reset()
        badgeEntry = resolveBadge()
    }
    val badgeIcon = badgeEntry
    val tileScale = remember { Animatable(1f) }
    val badgeScale = remember { Animatable(1f) }

    Box(modifier = Modifier.testTag("fx_tile_${def.id}")) {
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = tileScale.value
                scaleY = tileScale.value
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(tileShape)
            ) {
                FxTileArtwork(def = def, modifier = Modifier.fillMaxSize())
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = atlas,
                    drawKey = listOf(badgeIcon, badgeScale.value, selected, dimmed, atlas.generation),
                    buildCmds = { w, h, dd ->
                        if (dimmed) {
                            addRoundRect(w / 2f, h / 2f, w, h, 8f * dd, Color.Black.copy(alpha = 0.45f).toArgb())
                        }
                        if (selected) {
                            addRoundRect(
                                w / 2f, h / 2f, w - 2f * dd, h - 2f * dd, 7f * dd,
                                0, 2f * dd, Color.White.toArgb()
                            )
                            val bcx = 12f * dd
                            val bcy = 12f * dd
                            addCircle(bcx, bcy, 9f * dd, Color.Black.copy(alpha = 0.55f).toArgb())
                            if (badgeIcon != null) {
                                setScaleAbout(bcx, bcy, badgeScale.value)
                                addText(
                                    badgeIcon.index,
                                    bcx - tunePx / 2f - pad, bcy - tunePx / 2f - pad,
                                    0f, 0f, badgeIcon.rectW.toFloat(), badgeIcon.rectH.toFloat(),
                                    tintArgb = Color.White.toArgb()
                                )
                                clearTransform()
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(3.dp))
            FxTileLabelGl(name = def.name, dimmed = dimmed)
        }
        ChromePressBox(
            tileScale, 0.94f, onClick,
            Modifier.matchParentSize(),
            description = def.name
        )
        if (selected) {
            ChromePressBox(
                badgeScale, 0.94f, onBadgeClick,
                Modifier
                    .offset(x = 3.dp, y = 3.dp)
                    .size(18.dp),
                tag = "fx_badge_${def.id}",
                description = "Edit parameters"
            )
        }
    }
}

/**
 * Build 252 (stage 5): a browser tile's name in GL — 9.5 sp on an
 * 11 sp line pitch, centred, at most two lines with the second
 * ellipsized (the Text's lineHeight / maxLines / Ellipsis contract).
 */
@Composable
private fun FxTileLabelGl(name: String, dimmed: Boolean) {
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val style = UiLabelStyle.TILE_LABEL
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wPx = maxWidth.value * density
        fun resolveLabel(): List<UiLabelAtlas.Entry?> {
            val wrapped = PanelGeometry.wrapGreedy(name, wPx, Int.MAX_VALUE) { atlas.measure(it, style) }
            val lines = if (wrapped.size > 2) {
                val rest = name.substring(wrapped[0].length).trimStart()
                listOf(wrapped[0], ChromeGeometry.ellipsize(rest, wPx) { atlas.measure(it, style) })
            } else {
                wrapped
            }
            return lines.map { atlas.resolve(it, style) }
        }
        var labelEntries = resolveLabel()
        if (atlas.overflowed) {
            atlas.reset()
            labelEntries = resolveLabel()
        }
        val lines = labelEntries
        val linePitchPx = 11f * density * fontScale
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((linePitchPx * lines.size / density).dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(lines, dimmed, atlas.generation),
                buildCmds = { w, _, _ ->
                    val fm = atlas.fontMetrics(style)
                    var lineTop = 0f
                    for (e in lines) {
                        if (e != null) {
                            val baseline = PanelGeometry.lineBaselinePx(lineTop, linePitchPx, fm.ascent, fm.descent)
                            val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                            val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = (if (dimmed) TextSecondary else TextPrimary).toArgb()
                            )
                        }
                        lineTop += linePitchPx
                    }
                }
            )
        }
    }
}

/** Category hues (deep-space family bases) for the procedural tile artwork. */
private data class FxPalette(val deepHue: Float, val glowHue: Float)

private fun fxPalette(category: String): FxPalette = when (category) {
    "Blur" -> FxPalette(258f, 285f)
    "Color" -> FxPalette(318f, 18f)
    "Light" -> FxPalette(38f, 52f)
    "Lens" -> FxPalette(205f, 225f)
    "Distort" -> FxPalette(278f, 145f)
    "Stylize" -> FxPalette(332f, 190f)
    "Texture" -> FxPalette(14f, 350f)
    "Keying" -> FxPalette(150f, 172f)
    "Backdrop" -> FxPalette(232f, 308f)
    else -> FxPalette(245f, 200f)
}

/**
 * A tile's artwork, drawn procedurally (zero assets, zero APK growth): a deep-space
 * gradient field in the category's hue family, three soft nebula blobs and a star
 * field placed by the effect's stable seed ([FxTileArt]), the category motif —
 * the effect's own library icon — in white at the centre. The per-effect hue
 * shift (±24°) keeps tiles of one category distinguishable, never blank or grey.
 */
@Composable
private fun FxTileArtwork(def: FxDef, modifier: Modifier = Modifier) {
    val palette = fxPalette(def.category)
    val seed = remember(def.id) { FxTileArt.seed(def.id) }
    val shift = remember(def.id) { FxTileArt.hueShiftDeg(def.id) }
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun hue(base: Float) = (base + shift + 360f) % 360f
            val deep = Color.hsv(hue(palette.deepHue), 0.82f, 0.13f)
            val mid = Color.hsv(hue(palette.glowHue), 0.72f, 0.34f)
            val glow = Color.hsv(hue(palette.glowHue + 24f), 0.88f, 0.92f)
            drawRect(brush = Brush.verticalGradient(listOf(mid, deep)))
            for (i in 0 until 3) {
                val cx = w * (0.15f + 0.7f * FxTileArt.unit(seed, i * 7 + 1))
                val cy = h * (0.15f + 0.7f * FxTileArt.unit(seed, i * 7 + 2))
                val r = size.minDimension * (0.30f + 0.35f * FxTileArt.unit(seed, i * 7 + 3))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    center = Offset(cx, cy),
                    radius = r
                )
            }
            for (i in 0 until 16) {
                val sx = w * FxTileArt.unit(seed, 100 + i * 3)
                val sy = h * FxTileArt.unit(seed, 101 + i * 3)
                val sr = (0.6f + FxTileArt.unit(seed, 102 + i * 3)) * 1.1.dp.toPx()
                drawCircle(
                    Color.White.copy(alpha = 0.30f + 0.45f * FxTileArt.unit(seed, 103 + i * 3)),
                    radius = sr,
                    center = Offset(sx, sy)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                fxIcon(def.id), contentDescription = null,
                tint = Color.White, modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * The browser's info sheet (build 200): the selected effect's name, category and the
 * library's own one-line description ([FxDef.blurb] — written for every def), plus its
 * parameter set. Rounded dialog on AmPanel ([AmCornerCard]) with a pill action
 * (build 209, the redesign language).
 */
@Composable
fun FxInfoDialog(def: FxDef, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.example.ui.theme.AmPanel,
        shape = RoundedCornerShape(AmCornerCard),
        title = {
            Text(def.name, color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(def.category, color = AmTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (def.blurb.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(def.blurb, color = TextPrimary, fontSize = 13.sp)
                }
                if (def.params.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Parameters: " + def.params.joinToString(", ") { it.name },
                        color = AmTextDim,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            DialogSecondaryButton(text = "Close", onClick = onDismiss)
        },
        modifier = Modifier.testTag("fx_info_dialog")
    )
}

/** Chroma Key: the key colour as a swatch and the button that starts the ring eyedropper on the picture. */
@Composable
private fun KeyPickerRow(swatch: Color, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Build 252 (stage 5): the swatch is a flat colour disc in
        // GL (colour + its white ring); the pill button is FxPillButtonGl.
        Box(
            modifier = Modifier
                .size(28.dp)
                .testTag("fx_key_swatch")
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                drawKey = swatch.toArgb(),
                buildCmds = { w, h, dd ->
                    addCircle(
                        w / 2f, h / 2f, w / 2f - 0.75f * dd,
                        swatch.toArgb(), 1.5f * dd, Color.White.copy(alpha = 0.6f).toArgb()
                    )
                }
            )
        }
        Spacer(Modifier.width(10.dp))
        // Pill button (build 209): the filled capsule of the redesign language.
        FxPillButtonGl(
            icon = Icons.Default.Colorize,
            label = "Pick from picture",
            tintArgb = TextPrimary.toArgb(),
            tag = "fx_key_pick",
            padHDp = 12f,
            padVDp = 7f,
            iconGapDp = 6f,
            textStyle = UiLabelStyle.ED_TEXT_SEMI,
            onClick = onPick
        )
    }
}

private fun fxIcon(id: String): ImageVector = when (id) {
    ClipFxLibrary.BLUR -> Icons.Default.BlurOn
    ClipFxLibrary.GLOW -> Icons.Default.LightMode
    ClipFxLibrary.RGB_SPLIT -> Icons.Default.Layers
    ClipFxLibrary.VIGNETTE -> Icons.Default.Vignette
    ClipFxLibrary.GRAIN -> Icons.Default.Grain
    ClipFxLibrary.CHROMA_KEY -> Icons.Default.Palette
    ClipFxLibrary.DIR_BLUR -> Icons.Default.BlurLinear
    ClipFxLibrary.RADIAL_BLUR -> Icons.Default.BlurCircular
    ClipFxLibrary.SHARPEN -> Icons.Default.Details
    ClipFxLibrary.EXPOSURE -> Icons.Default.Exposure
    ClipFxLibrary.BRIGHT_CONTRAST -> Icons.Default.Contrast
    ClipFxLibrary.HUE_SAT -> Icons.Default.ColorLens
    ClipFxLibrary.LEVELS -> Icons.Default.Tune
    ClipFxLibrary.COLOR_BALANCE -> Icons.Default.InvertColors
    ClipFxLibrary.TINT -> Icons.Default.Gradient
    ClipFxLibrary.POSTERIZE -> Icons.Default.FilterBAndW
    ClipFxLibrary.WAVE -> Icons.Default.Waves
    ClipFxLibrary.TWIRL -> Icons.Default.Autorenew
    ClipFxLibrary.BULGE -> Icons.Default.ZoomIn
    ClipFxLibrary.MIRROR -> Icons.Default.Flip
    ClipFxLibrary.KALEIDOSCOPE -> Icons.Default.FilterVintage
    ClipFxLibrary.TURBULENT -> Icons.Default.Shuffle
    ClipFxLibrary.MOSAIC -> Icons.Default.GridOn
    ClipFxLibrary.HALFTONE -> Icons.Default.Texture
    ClipFxLibrary.SCANLINES -> Icons.Default.Tv
    ClipFxLibrary.GLITCH -> Icons.Default.BrokenImage
    ClipFxLibrary.INVERT -> Icons.Default.InvertColorsOff
    ClipFxLibrary.BW -> Icons.Default.MonochromePhotos
    ClipFxLibrary.THRESHOLD -> Icons.Default.Adjust
    ClipFxLibrary.TEMPERATURE -> Icons.Default.Thermostat
    ClipFxLibrary.SHADOW_HIGHLIGHT -> Icons.Default.Brightness6
    ClipFxLibrary.LEAVE_COLOR -> Icons.Default.Opacity
    ClipFxLibrary.CINEMATIC -> Icons.Default.Movie
    ClipFxLibrary.SOLARIZE -> Icons.Default.WbSunny
    ClipFxLibrary.LENS_BLUR -> Icons.Default.Lens
    ClipFxLibrary.TILT_SHIFT -> Icons.Default.FilterTiltShift
    ClipFxLibrary.SURFACE_BLUR -> Icons.Default.Face
    ClipFxLibrary.LIGHT_RAYS -> Icons.Default.WbIncandescent
    ClipFxLibrary.RIPPLE -> Icons.Default.PanoramaFishEye
    ClipFxLibrary.LENS_DISTORT -> Icons.Default.CenterFocusStrong
    ClipFxLibrary.POLAR -> Icons.Default.DonutLarge
    ClipFxLibrary.TILE -> Icons.Default.Apps
    ClipFxLibrary.SHAKE -> Icons.Default.Vibration
    ClipFxLibrary.EMBOSS -> Icons.Default.Terrain
    ClipFxLibrary.FIND_EDGES -> Icons.Default.CropFree
    ClipFxLibrary.CARTOON -> Icons.Default.Brush
    ClipFxLibrary.SKETCH -> Icons.Default.Edit
    ClipFxLibrary.OIL -> Icons.Default.FormatPaint
    ClipFxLibrary.STROBE -> Icons.Default.FlashOn
    ClipFxLibrary.VHS -> Icons.Default.Videocam
    ClipFxLibrary.OLD_FILM -> Icons.Default.MovieFilter
    ClipFxLibrary.LIGHT_LEAK -> Icons.Default.WbTwilight
    ClipFxLibrary.LENS_FLARE -> Icons.Default.Flare
    ClipFxLibrary.COPY_BG -> Icons.Default.ContentCopy
    else -> Icons.Default.AutoAwesome
}
