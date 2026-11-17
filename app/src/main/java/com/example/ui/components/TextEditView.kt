package com.example.ui.components

import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmCornerSheet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.util.bouncyClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AlightXmlImporter
import com.example.engine.TextRevealValues
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.ui.text.FontCatalog
import com.example.ui.text.FontEntry
import com.example.ui.text.FontRepository
import com.example.ui.text.rememberFontFamily
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Bottom panel of "Text edit" (build 213 redesign; build 214 — the panel
 * stage's architecture): ONE small panel at a time, chosen from a
 * horizontally scrolling icon toolbar. Build 214 moves the switcher from
 * the sheet's bottom into its HEADER area (the panel architecture's
 * section switcher sits at the top, under the drag handle), and the
 * active tool's pill carries the brand red. Sections: Edit, Font, Style,
 * Stroke, Shadow, Box, Space, Reveal, Path. Edit = the text box + the
 * quick row (Italic / Underline / Strikethrough + Align). Every change
 * is applied to the layer at once, so the canvas above shows it live.
 *
 * While the keyboard is open the sheet shows ONLY the Edit panel (no toolbar), raised above the
 * keyboard — the canvas keeps its fixed height (canvas law); the space comes from the lower
 * zone, whose timescale + layer row the caller hides in that state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextEditSheet(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    /** PATH-bearing shape layers of the project the text can attach to (build 191). */
    pathLayers: List<TimelineClip> = emptyList()
) {
    var text by remember(clip.id) { mutableStateOf(clip.text.orEmpty()) }
    var tool by remember(clip.id) { mutableStateOf(TextTool.EDIT) }
    val imeVisible = WindowInsets.isImeVisible

    val onText: (String) -> Unit = { v ->
        text = v
        onUpdate { it.copy(text = v, name = if (v.isBlank()) "Text" else "Text: ${v.take(24)}") }
    }

    Column(
        modifier = modifier
            // Rises above the keyboard; while it is open the sheet is only the Edit panel.
            .imePadding()
            .clip(RoundedCornerShape(topStart = AmCornerSheet, topEnd = AmCornerSheet))
            .background(SheetBg)
    ) {
        if (imeVisible) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp)) {
                TextBoxField(text, onText)
                Spacer(Modifier.height(12.dp))
                TextQuickRow(clip, onUpdate)
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(AmTextFaint)
            )
            // Build 214: the section switcher sits in the panel's header
            // area (top), not pinned to the bottom.
            TextToolBar(selected = tool, onSelect = { tool = it })
            // One panel at a time; a fresh scroll state per tool so switching never keeps an old fold.
            key(tool) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (tool) {
                        TextTool.EDIT -> {
                            TextBoxField(text, onText)
                            Spacer(Modifier.height(4.dp))
                            TextQuickRow(clip, onUpdate)
                        }
                        TextTool.FONT -> {
                            FontsTab(clip, onUpdate)
                            FontWeightRow(clip, onUpdate)
                        }
                        TextTool.STYLE -> StyleCardContent(clip, onUpdate, onEyedropper)
                        TextTool.STROKE -> StrokeTab(clip, onUpdate, onEyedropper)
                        TextTool.SHADOW -> ShadowTab(clip, onUpdate, onEyedropper)
                        TextTool.BOX -> BackgroundTab(clip, onUpdate, onEyedropper)
                        TextTool.SPACE -> SpacingTab(clip, onUpdate)
                        TextTool.REVEAL -> RevealBlock(clip, onUpdate)
                        TextTool.PATH -> PathTab(clip, pathLayers, onUpdate)
                    }
                }
            }
        }
    }
}

/** The tools of the bottom toolbar, in display order. */
private enum class TextTool(val label: String, val icon: ImageVector) {
    EDIT("Edit", Icons.Default.Keyboard),
    FONT("Font", Icons.Default.TextFields),
    STYLE("Style", Icons.Default.Palette),
    STROKE("Stroke", Icons.Default.BorderColor),
    SHADOW("Shadow", Icons.Default.Layers),
    BOX("Box", Icons.Default.CropSquare),
    SPACE("Space", Icons.Default.FormatLineSpacing),
    REVEAL("Reveal", Icons.Default.Visibility),
    PATH("Path", Icons.Default.Timeline)
}

private val ToolbarBg = AmBase // build 223: tokenized (same value)
private val ToolAccent = AmAccentViolet // the one lime accent token
private val ToolIdle = AmTextDim // build 223: the family's idle grey (was build 216's on-white grey)

/** Section switcher (build 214: in the panel's header area): scrolls sideways, one 64 dp cell per tool, the active one in a brand-red pill. */
@Composable
private fun TextToolBar(selected: TextTool, onSelect: (TextTool) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolbarBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        TextTool.values().forEach { t ->
            val on = t == selected
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .bouncyClickable(pressedScale = 0.92f) { onSelect(t) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (on) ToolAccent else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = if (on) AmInk else ToolIdle, modifier = Modifier.size(22.dp)) // ink on the lime tab
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    t.label,
                    color = if (on) TextPrimary else ToolIdle,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** The text box of the Edit panel (tap-to-type; the layer's text updates on every keystroke). */
@Composable
private fun TextBoxField(text: String, onText: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    TextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text("Write Text", color = AmTextDim, fontSize = 18.sp) },
        leadingIcon = {
            Icon(Icons.Default.TextFields, null, tint = TextPrimary, modifier = Modifier.size(26.dp))
        },
        maxLines = 3,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
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
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(2.dp, Color(0xFFBDBDBD), RoundedCornerShape(AmCornerCard))
                else Modifier
            )
    )
}

/**
 * The quick row under the text box: Italic / Underline / Strikethrough toggles and the
 * Align buttons (moved here from the old Style / Spacing tabs — shown once, always visible).
 * At 360 dp the toggles get ~44 dp each and the align buttons 48 dp (all targets >= 40 dp).
 */
@Composable
private fun TextQuickRow(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StyleToggle("I", clip.isItalic, Modifier.weight(1f), fontStyle = FontStyle.Italic) {
                onUpdate { it.copy(isItalic = !it.isItalic) }
            }
            StyleToggle("U", clip.isUnderline, Modifier.weight(1f), decoration = TextDecoration.Underline) {
                onUpdate { it.copy(isUnderline = !it.isUnderline) }
            }
            StyleToggle("S", clip.isStrikethrough, Modifier.weight(1f), decoration = TextDecoration.LineThrough) {
                onUpdate { it.copy(isStrikethrough = !it.isStrikethrough) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlignButton(Icons.Default.FormatAlignLeft, clip.textAlign == 0, Modifier.width(48.dp)) {
                onUpdate { it.copy(textAlign = 0) }
            }
            AlignButton(Icons.Default.FormatAlignCenter, clip.textAlign == 1, Modifier.width(48.dp)) {
                onUpdate { it.copy(textAlign = 1) }
            }
            AlignButton(Icons.Default.FormatAlignRight, clip.textAlign == 2, Modifier.width(48.dp)) {
                onUpdate { it.copy(textAlign = 2) }
            }
        }
    }
}

@Composable
private fun SheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (selected) ChipSelectedBg else ChipBg)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content() else {
            Text(
                label,
                color = if (selected) AmInk else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontsTab(clip: TimelineClip, onUpdate: ((TimelineClip) -> TimelineClip) -> Unit) {
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

    val groups = remember(query, userFonts) {
        val all = userFonts.map { FontEntry(it, FontCatalog.USER_CATEGORY) } + FontCatalog.all
        all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .groupBy { it.category }
            .toList()
    }

    // Build 212: no scroll container of its own — the sheet's ONE scrolling column owns
    // scrolling (a nested same-axis scroll here was part of the old tab layout's fold).
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search Fonts", color = Color(0xFF8A8A8E), fontSize = 19.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFB0B0B4), modifier = Modifier.size(28.dp)) },
            singleLine = true,
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
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SheetChip(label = "System", selected = true, onClick = {})
            Box {
                SheetChip(
                    label = "Add font",
                    selected = false,
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "font/ttf", "font/otf", "font/sfnt",
                                "application/x-font-ttf", "application/x-font-otf",
                                "application/octet-stream"
                            )
                        )
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add font", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(11.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AmAccent)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Build 201 (audit S-6): imported fonts live in this app's private
        // storage on THIS device — say so where the user adds them.
        Text(
            text = "Fonts you add are stored on this device only — they don't travel with the project file.",
            color = TextSecondary,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (category, fonts) ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.height(46.dp).padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
                        Text(category, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    fonts.forEach { f ->
                        FontChip(
                            name = f.name,
                            selected = clip.fontFamily == f.name,
                            onClick = { onUpdate { it.copy(fontFamily = f.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val family = rememberFontFamily(name)
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ChipBg)
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(name, color = Color.White, fontSize = 20.sp, fontFamily = family, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Style
// ---------------------------------------------------------------------------------------------

private val PreviewClip = TimelineClip(
    id = "preview", name = "", type = LayerType.TEXT, startMs = 0, durationMs = 1, color = 0,
    text = "Aa", fontWeight = 700, bgOpacity = 0f
)

/** Style card: the preset tiles row + the fill colour row (the old Style tab's top half). */
@Composable
private fun StyleCardContent(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(TextPresets) { preset ->
            val isCurrent = clip.textColor == preset.fill &&
                clip.textStrokeThickness == preset.strokeThickness &&
                clip.shadowOpacity == preset.shadowOpacity &&
                (preset.shadowOpacity == 0f || clip.shadowBlur == preset.shadowBlur)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChipBg)
                    .then(if (isCurrent) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier)
                    .bouncyClickable(pressedScale = 0.93f) { onUpdate { preset.applyTo(it) } },
                contentAlignment = Alignment.Center
            ) {
                TextLayerContent(clip = preset.applyTo(PreviewClip), frameWidth = 290.dp)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    TextColorRow(
        selected = clip.textColor,
        onSelect = { c -> onUpdate { it.copy(textColor = c, fillMode = com.example.model.FILL_SOLID) } },
        onEyedropper = onEyedropper
    )
}

/** Font weight pill row (200–900) — lives at the bottom of the Font card (build 212). */
@Composable
private fun FontWeightRow(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Font weight", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(112.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listOf(200, 400, 500, 600, 700, 800, 900)) { w ->
                Box(
                    modifier = Modifier
                        .width(66.dp)
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(if (clip.fontWeight == w) ChipSelectedBg else ChipBg)
                        .bouncyClickable(pressedScale = 0.92f) { onUpdate { it.copy(fontWeight = w) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$w",
                        color = if (clip.fontWeight == w) AmInk else Color.White,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Reveal card (typewriter window, Alight `textprogress`, build 82 renders it): a static
 * start/end window of the text that is shown. An imported keyframed reveal is preserved —
 * the sliders stay hidden while it is animated, and only an explicit tap replaces it with
 * a static window frozen at the finished state (see TextRevealValues).
 */
@Composable
private fun RevealBlock(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val reveal = clip.textAnimators.firstOrNull { it.effectId == AlightXmlImporter.TEXTPROGRESS }
    if (TextRevealValues.isKeyframed(reveal)) {
        Text(
            "Animated reveal — the window is keyframed in Alight Motion, so it changes over time.",
            color = TextSecondary, fontSize = 15.sp
        )
        Spacer(Modifier.height(8.dp))
        SheetChip(label = "Use static window", selected = false, onClick = {
            val (s, e) = TextRevealValues.windowAt(reveal, 1f)
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, s, e)) }
        })
    } else {
        val (revealStart, revealEnd) = TextRevealValues.windowAt(reveal, 0f)
        TextRulerSlider("Reveal start", revealStart * 100f, { v ->
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, v / 100f, revealEnd)) }
        })
        TextRulerSlider("Reveal end", revealEnd * 100f, { v ->
            onUpdate { it.copy(textAnimators = TextRevealValues.withStaticWindow(it.textAnimators, revealStart, v / 100f)) }
        })
    }
}

@Composable
private fun StyleToggle(
    glyph: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    fontStyle: FontStyle = FontStyle.Normal,
    decoration: TextDecoration = TextDecoration.None,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(if (on) ChipSelectedBg else ChipBg)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            color = if (on) AmInk else Color.White,
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontStyle = fontStyle, textDecoration = decoration
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Stroke / Shadow / Background / Spacing
// ---------------------------------------------------------------------------------------------

@Composable
private fun StrokeTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    TextRulerSlider("Thickness", clip.textStrokeThickness, { v -> onUpdate { it.copy(textStrokeThickness = v) } })
    TextColorRow(clip.textStrokeColor, { c -> onUpdate { it.copy(textStrokeColor = c) } }, onEyedropper)
}

@Composable
private fun ShadowTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    TextRulerSlider("Opacity", clip.shadowOpacity, { v -> onUpdate { it.copy(shadowOpacity = v) } })
    TextRulerSlider("Blur", clip.shadowBlur, { v -> onUpdate { it.copy(shadowBlur = v) } })
    TextRulerSlider("Distance", clip.shadowDistance, { v -> onUpdate { it.copy(shadowDistance = v) } })
    TextRulerSlider("Angle", clip.shadowAngle, { v -> onUpdate { it.copy(shadowAngle = v) } }, max = 360f, unit = "°")
    TextColorRow(clip.shadowColor, { c -> onUpdate { it.copy(shadowColor = c) } }, onEyedropper)
}

@Composable
private fun BackgroundTab(
    clip: TimelineClip,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit
) {
    TextRulerSlider("Opacity", clip.bgOpacity, { v -> onUpdate { it.copy(bgOpacity = v) } })
    TextRulerSlider("Corner", clip.bgCorner, { v -> onUpdate { it.copy(bgCorner = v) } })
    TextRulerSlider("Padding", clip.bgPadding, { v -> onUpdate { it.copy(bgPadding = v) } })
    TextColorRow(clip.bgColor, { c ->
        // Picking a color for a hidden background turns it on so the change is visible.
        onUpdate { it.copy(bgColor = c, bgOpacity = if (it.bgOpacity == 0f) 100f else it.bgOpacity) }
    }, onEyedropper)
}

@Composable
private fun SpacingTab(clip: TimelineClip, onUpdate: ((TimelineClip) -> TimelineClip) -> Unit) {
    // Align is NOT here anymore: it lives in the quick row under the text box (build 212).
    TextRulerSlider("Letter space", clip.letterSpacing, { v -> onUpdate { it.copy(letterSpacing = v) } }, min = -10f, max = 40f)
    TextRulerSlider("Line height", clip.lineHeightPercent, { v -> onUpdate { it.copy(lineHeightPercent = v) } }, min = 80f, max = 200f)
}

@Composable
private fun AlignButton(icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(if (on) ChipSelectedBg else ChipBg)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (on) AmInk else Color.White, modifier = Modifier.size(26.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Path (text-on-path, build 191)
// ---------------------------------------------------------------------------------------------

/**
 * Attach this text to a PATH shape layer (Pen / freehand / imported path): the glyphs then
 * follow that layer's outline, evaluated live — move or animate the path layer and the text
 * rides it. The text follows the path even when the path layer is hidden; hiding is not
 * detaching. While attached, the Start slider moves the text along the path (a share of
 * the path's whole length); text longer than the path is cut at the path's end.
 */
@Composable
private fun PathTab(
    clip: TimelineClip,
    pathLayers: List<TimelineClip>,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val attachedId = clip.textPathLayerId
    val attached = pathLayers.firstOrNull { it.id == attachedId }
    when {
        attached != null -> {
            Row(modifier = Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Layer", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(112.dp))
                Text(attached.name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            TextRulerSlider("Start offset", clip.textPathStart * 100f,
                { v -> onUpdate { it.copy(textPathStart = v / 100f) } }, min = 0f, max = 100f)
            PathActionRow("Detach from path") { onUpdate { it.copy(textPathLayerId = null) } }
        }
        attachedId != null -> {
            Text("The path layer this text was attached to is gone.",
                color = TextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            PathActionRow("Detach") { onUpdate { it.copy(textPathLayerId = null) } }
        }
        pathLayers.isEmpty() -> {
            Text("No path layer in this project yet. Draw one with the Pen tool first, then attach this text to it here.",
                color = TextSecondary, fontSize = 15.sp)
        }
        else -> {
            Text("Attach to a path layer — the text will follow its outline:",
                color = TextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            pathLayers.forEach { layer ->
                PathActionRow(layer.name) { onUpdate { it.copy(textPathLayerId = layer.id) } }
            }
        }
    }
}

@Composable
private fun PathActionRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(ChipBg)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
