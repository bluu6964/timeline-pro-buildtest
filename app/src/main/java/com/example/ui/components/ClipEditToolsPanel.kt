package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.example.model.ToolRowSplit

data class ClipToolItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun ClipEditToolsPanel(
    height: Dp,
    onDeselect: () -> Unit,
    onEffects: () -> Unit,
    onAnimations: () -> Unit,
    onColor: () -> Unit,
    onTransform: () -> Unit,
    onCrop: () -> Unit,
    onBlend: () -> Unit,
    modifier: Modifier = Modifier,
    /** Video layers with sound: volume (0-200 %) and mute of this layer. */
    onVolume: (() -> Unit)? = null,
    /** Cutout (RVM): keeps the person, removes the background. */
    onCutout: (() -> Unit)? = null,
    /** Motion tracking (video layers only): follow an object with a circle. */
    onTrack: (() -> Unit)? = null,
    /** Remove (video layers only): covers a flaw on a flat surface with a clean patch (planar track, Mocha-style). */
    onRemove: (() -> Unit)? = null,
    /** Stabilize (video layers only): holds a flat surface still (planar track, Mocha-style). */
    onStabilize: (() -> Unit)? = null,
    /** Face AI (video layers): face tracking tools, first one = blur on face / eyes / nose / lips. */
    onFaceAi: (() -> Unit)? = null,
    onFaceSwap: (() -> Unit)? = null,
    /** Masks (every picture layer: video / photo, shape, text, sticker, group). */
    onMasks: (() -> Unit)? = null,
    /** Only set for text layers: adds an "Edit" tool that opens Text edit. */
    onEditText: (() -> Unit)? = null,
    /** Only set for shape layers (build 173): adds an "Edit shape" tool that opens the shape editor. */
    onEditShape: (() -> Unit)? = null,
    /** Only set for PATH shape layers (build 177): adds an "Edit points" tool that opens node editing on the canvas. */
    onEditPoints: (() -> Unit)? = null,
    /** Parent: pick a Null object this layer follows. */
    onParent: (() -> Unit)? = null,
    /** Only set for a real Group layer: takes the layers out of the group again. */
    onUngroup: (() -> Unit)? = null,
    /** Only set for a real Group layer: saves the whole group as a reusable user template (Templates v2). */
    onSaveTemplate: (() -> Unit)? = null,
    /** Only set for a real Group layer: goes inside it to edit its layers one by one (Edit Group). */
    onEditGroup: (() -> Unit)? = null,
    /** Rename the layer. */
    onRename: (() -> Unit)? = null,
    /** Copy this layer's keyframes to the in-app clipboard (build 176). */
    onCopyKeyframes: (() -> Unit)? = null,
    /** Paste the clipboard's keyframes onto this layer at the playhead (build 176). Only set while the clipboard holds keyframes. */
    onPasteKeyframes: (() -> Unit)? = null,
    /** Copy this layer's effect stack to the in-app clipboard (build 176). */
    onCopyEffects: (() -> Unit)? = null,
    /** Paste the clipboard's effect stack onto this layer, replacing its stack (build 176). Only set while the clipboard holds effects. */
    onPasteEffects: (() -> Unit)? = null,
    /** Video layers: freeze frame (holds the picture at the playhead). */
    onFreeze: (() -> Unit)? = null,
    /** Video layers with a file (build 203): extract audio — the layer's sound becomes its own audio layer below it. */
    onExtractAudio: (() -> Unit)? = null,
    /** Lock / unlock this layer (build 199, A1): a locked layer cannot be grabbed or trimmed. */
    onLockLayer: (() -> Unit)? = null,
    /** Solo this layer (build 199, A6): hides + mutes every other layer until tapped again. Used-state = this layer is the solo. */
    onSoloLayer: (() -> Unit)? = null,
    /** Colour label picker for this layer (build 199, A2). Used-state = a non-default label colour. */
    onLabelColor: (() -> Unit)? = null,
    /** Clear ALL keyframes of this layer (build 199, A5). Only set while the layer has keyframes. */
    onClearKeyframes: (() -> Unit)? = null,
    /** Copy the WHOLE layer to the layer clipboard (build 199, A7). */
    onCopyLayer: (() -> Unit)? = null,
    /** Paste the layer clipboard as a new layer (build 199, A7). Only set while the clipboard holds a layer. */
    onPasteLayer: (() -> Unit)? = null,
    /**
     * Tools already USED on this layer (build 166, item 5 — the rule is
     * [com.example.model.UsedTools.usedToolsOf]): a used tool reads WHITE (white icon,
     * bold white label, white tile outline), an unused one stays grey, so what is
     * applied to the layer is visible at a glance.
     */
    usedTools: Set<String> = emptySet(),
    /**
     * Null / 3D Camera layers have no picture, so only the tools that do something for them are shown (like the audio panel):
     * when set, tools whose id is not in this set are left out. null = every tool the layer type offers.
     */
    onlyTools: Set<String>? = null,
    /**
     * The tools grid's bridge (build 202; a layout-flow sheet since build
     * 206): this panel's overflow tools are published here and shown in the
     * grid. Null = nowhere to publish (the overflow is simply not shown —
     * every in-app caller passes the bridge).
     */
    morePanel: MorePanelState? = null,
    /**
     * Build 210 (TOOLS PANEL LAW): while the unified tools panel is open,
     * it is the ONE scrolling home for ALL of this panel's tools — the
     * panel publishes its full tool list (primary + overflow, in order)
     * and its footer bar renders only the back button (the fixed primary
     * row is not rendered anywhere). False = build 202's split behaviour.
     * Build 211: the footer bar renders NOTHING while the panel is open
     * (the back strip is gone too — see the early return below).
     */
    toolsInGrid: Boolean = false
) {
    val allTools = (if (onEditGroup != null) listOf(ClipToolItem("edit_group", "Edit group", Icons.Default.FolderOpen))
    else emptyList()) + (if (onEditText != null) listOf(ClipToolItem("edit_text", "Edit", Icons.Default.Edit))
    else emptyList()) + (if (onEditShape != null) listOf(ClipToolItem("edit_shape", "Edit shape", Icons.Default.Edit))
    else emptyList()) + (if (onEditPoints != null) listOf(ClipToolItem("edit_points", "Edit points", Icons.Default.Polyline))
    else emptyList()) + listOf(
        ClipToolItem("effects", "Effects", Icons.Default.AutoAwesome),
        ClipToolItem("animations", "Animations", Icons.Default.Animation),
        ClipToolItem("color", "Color", Icons.Default.Palette),
        ClipToolItem("transform", "Transform", Icons.Default.OpenWith),
        ClipToolItem("crop", "Crop", Icons.Default.Crop),
        ClipToolItem("blend", "Blend", Icons.Default.Layers)
    ) + (if (onCopyKeyframes != null) listOf(ClipToolItem("copy_keyframes", "Copy keyframes", Icons.Default.ContentCopy))
    else emptyList()) + (if (onPasteKeyframes != null) listOf(ClipToolItem("paste_keyframes", "Paste keyframes", Icons.Default.ContentPaste))
    else emptyList()) + (if (onCopyEffects != null) listOf(ClipToolItem("copy_effects", "Copy effects", Icons.Default.CopyAll))
    else emptyList()) + (if (onPasteEffects != null) listOf(ClipToolItem("paste_effects", "Paste effects", Icons.Default.ContentPaste))
    else emptyList()) + (if (onVolume != null) listOf(ClipToolItem("volume", "Volume", Icons.AutoMirrored.Filled.VolumeUp))
    else emptyList()) + (if (onMasks != null) listOf(ClipToolItem("masks", "Masks", Icons.Default.Tonality))
    else emptyList()) + (if (onCutout != null) listOf(ClipToolItem("cutout", "Cutout", Icons.Default.Person))
    else emptyList()) + (if (onTrack != null) listOf(ClipToolItem("track", "Track", Icons.Default.GpsFixed))
    else emptyList()) + (if (onRemove != null) listOf(ClipToolItem("remove", "Remove", Icons.Default.AutoFixHigh))
    else emptyList()) + (if (onStabilize != null) listOf(ClipToolItem("stabilize", "Stabilize", Icons.Default.CenterFocusStrong))
    else emptyList()) + (if (onFaceAi != null) listOf(ClipToolItem("face_ai", "Face AI", Icons.Default.Face))
    else emptyList()) + (if (onFaceSwap != null) listOf(ClipToolItem("face_swap", "Face swap", Icons.Default.SwapHoriz))
    else emptyList()) + (if (onParent != null) listOf(ClipToolItem("parent", "Parent", Icons.Default.AccountTree))
    else emptyList()) + (if (onUngroup != null) listOf(ClipToolItem("ungroup", "Ungroup", Icons.Default.LayersClear))
    else emptyList()) + (if (onSaveTemplate != null) listOf(ClipToolItem("save_template", "Save as Template", Icons.Default.Save))
    else emptyList()) + (if (onFreeze != null) listOf(ClipToolItem("freeze", "Freeze", Icons.Default.Image))
    else emptyList()) + (if (onExtractAudio != null) listOf(ClipToolItem("extract_audio", "Extract audio", Icons.Default.Audiotrack))
    else emptyList()) + (if (onRename != null) listOf(ClipToolItem("rename", "Rename", Icons.Default.Edit))
    else emptyList()) + (if (onLockLayer != null) listOf(ClipToolItem("lock", "Lock", Icons.Default.Lock))
    else emptyList()) + (if (onSoloLayer != null) listOf(ClipToolItem("solo", "Solo", Icons.Default.Visibility))
    else emptyList()) + (if (onLabelColor != null) listOf(ClipToolItem("label", "Label", Icons.Default.Label))
    else emptyList()) + (if (onClearKeyframes != null) listOf(ClipToolItem("clear_keyframes", "Clear keys", Icons.Default.ClearAll))
    else emptyList()) + (if (onCopyLayer != null) listOf(ClipToolItem("copy_layer", "Copy layer", Icons.Default.FileCopy))
    else emptyList()) + (if (onPasteLayer != null) listOf(ClipToolItem("paste_layer", "Paste layer", Icons.Default.ContentPaste))
    else emptyList())
    val tools = if (onlyTools == null) allTools else allTools.filter { it.id in onlyTools }

    // Build 202 — the primary row (Sarowar 2026-11-15, scope corrected
    // ~10:24): the row no longer scrolls and carries NO More button of its
    // own. The first tools that FIT at this width stay visible (existing
    // order, budget = model/ToolRowSplit, harness-pinned); the overflow is
    // PUBLISHED to the one tools grid (see PublishClipOverflow below). If
    // everything fits (Null / Camera / Controller tool sets), nothing is
    // published.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val visibleCount = ToolRowSplit.visibleCountForLabels(tools.map { it.label }, screenWidthDp)
    val visibleTools = tools.take(visibleCount)
    val overflowTools = tools.drop(visibleCount)

    fun dispatch(tool: ClipToolItem) {
        when (tool.id) {
            "edit_text" -> onEditText?.invoke()
            "edit_shape" -> onEditShape?.invoke()
            "edit_points" -> onEditPoints?.invoke()
            "effects" -> onEffects()
            "animations" -> onAnimations()
            "color" -> onColor()
            "transform" -> onTransform()
            "crop" -> onCrop()
            "blend" -> onBlend()
            "copy_keyframes" -> onCopyKeyframes?.invoke()
            "paste_keyframes" -> onPasteKeyframes?.invoke()
            "copy_effects" -> onCopyEffects?.invoke()
            "paste_effects" -> onPasteEffects?.invoke()
            "cutout" -> onCutout?.invoke()
            "track" -> onTrack?.invoke()
            "remove" -> onRemove?.invoke()
            "stabilize" -> onStabilize?.invoke()
            "face_ai" -> onFaceAi?.invoke()
            "face_swap" -> onFaceSwap?.invoke()
            "volume" -> onVolume?.invoke()
            "masks" -> onMasks?.invoke()
            "parent" -> onParent?.invoke()
            "ungroup" -> onUngroup?.invoke()
            "save_template" -> onSaveTemplate?.invoke()
            "edit_group" -> onEditGroup?.invoke()
            "freeze" -> onFreeze?.invoke()
            "extract_audio" -> onExtractAudio?.invoke()
            "rename" -> onRename?.invoke()
            "lock" -> onLockLayer?.invoke()
            "solo" -> onSoloLayer?.invoke()
            "label" -> onLabelColor?.invoke()
            "clear_keyframes" -> onClearKeyframes?.invoke()
            "copy_layer" -> onCopyLayer?.invoke()
            "paste_layer" -> onPasteLayer?.invoke()
        }
    }

    // Publish to the tools grid (build 202): the section carries THIS
    // panel's dispatch, so a tap in the grid behaves exactly like a tap
    // in the panel. Build 210: while the unified panel is open the FULL
    // list is published (primary first, then the overflow — one list);
    // otherwise only the overflow, and null when everything fits or no
    // bridge was given.
    PublishClipOverflow(
        morePanel = morePanel,
        section = when {
            toolsInGrid && tools.isNotEmpty() -> ClipOverflowSection(
                key = tools.joinToString(",") { it.id },
                tools = tools,
                usedTools = usedTools,
                tileTag = { "clip_tool_$it" },
                onTool = { dispatch(it) }
            )
            overflowTools.isEmpty() -> null
            else -> ClipOverflowSection(
                key = tools.joinToString(",") { it.id },
                tools = overflowTools,
                usedTools = usedTools,
                tileTag = { "clip_tool_$it" },
                onTool = { dispatch(it) }
            )
        }
    )

    // Build 214 (THE DOCK): this panel IS the dock's primary toolbar
    // while a layer is selected — it stays visible while the panel
    // stage is open above it (the stage carries the full tool list; the
    // publish above still runs). Build 211's render-nothing state is
    // superseded by the dock architecture: the primary tools are always
    // visible with icon + label (the visibility law).

    // Build 251 (UI migration stage 4): the row is drawn on one native
    // GL surface (ToolsRowGl / PanelGeometry.ToolRow) — bar, back icon,
    // divider and the visible tiles; the tap targets stay Compose at
    // the same rects with the same tags. The split above (ToolRowSplit)
    // still decides which tools are visible; the overflow still lives
    // in the panel stage's tool list. Build 214 holds: the row renders
    // ALWAYS — it is the dock's primary toolbar for the selected layer.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("clip_edit_tools_panel")
    ) {
        ToolsRowGl(
            items = visibleTools.map { tool ->
                ToolRowItem(
                    id = tool.id,
                    label = tool.label,
                    icon = tool.icon,
                    used = tool.id in usedTools,
                    testTag = "clip_tool_${tool.id}",
                    onClick = { dispatch(tool) }
                )
            },
            onBack = onDeselect,
            backTestTag = "clip_tools_back_button",
            backDescription = "Exit Clip Edit",
            outerPadDp = 3f,
            rowPadDp = 3f
        )
    }
}
