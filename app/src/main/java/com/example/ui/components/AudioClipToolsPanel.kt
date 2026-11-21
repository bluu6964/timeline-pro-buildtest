package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.example.model.ToolRowSplit

/** Tools shown while an AUDIO layer is selected: Split, Volume, Fade, Delete, Speed, Reverse, Duplicate. */
@Composable
fun AudioClipToolsPanel(
    height: Dp,
    onDeselect: () -> Unit,
    onVolume: () -> Unit,
    onFade: () -> Unit,
    onSpeed: () -> Unit,
    onReverse: () -> Unit,
    modifier: Modifier = Modifier,
    /** Detect beats: scans this layer's sound and drops beat marks on the ruler (build 193). */
    onDetectBeats: (() -> Unit)? = null,
    /** Ducking: this layer's volume lowers itself under another layer's loudness (build 194). */
    onDucking: (() -> Unit)? = null,
    /** Noise reduction: this layer's steady background noise is gated down (build 195). */
    onNoiseReduction: (() -> Unit)? = null,
    /** Rename the layer. */
    onRename: (() -> Unit)? = null,
    /** Lock / unlock this layer (build 199, A1). */
    onLockLayer: (() -> Unit)? = null,
    /** Solo this layer (build 199, A6). */
    onSoloLayer: (() -> Unit)? = null,
    /** Colour label picker for this layer (build 199, A2). */
    onLabelColor: (() -> Unit)? = null,
    /** Copy the WHOLE layer (build 199, A7). */
    onCopyLayer: (() -> Unit)? = null,
    /** Paste the layer clipboard as a new layer (build 199, A7). Only set while it holds a layer. */
    onPasteLayer: (() -> Unit)? = null,
    /**
     * Used-tool state (build 201, audit S-7): the SAME set the visual clip
     * panel gets — a tool in the set reads white + bold, the rest dim,
     * exactly ClipEditToolsPanel's language (build 166). Until 200 this panel
     * rendered every tool identically, so a locked / soloed / labelled audio
     * layer showed its state nowhere.
     */
    usedTools: Set<String> = emptySet(),
    /** The shared More panel's bridge (build 202): this panel's overflow tools are published here. */
    morePanel: MorePanelState? = null,
    /**
     * Build 210 (TOOLS PANEL LAW): while the unified tools panel is open
     * it carries ALL of this panel's tools in its one scrolling list —
     * the panel publishes its full list and the footer bar renders only
     * the back button. False = build 202's split behaviour.
     */
    toolsInGrid: Boolean = false
) {
    val tools = listOf(
        ClipToolItem("volume", "Volume", Icons.AutoMirrored.Filled.VolumeUp),
        ClipToolItem("fade", "Fade", Icons.Default.Tonality),
        ClipToolItem("speed", "Speed", Icons.Default.Speed),
        ClipToolItem("reverse", "Reverse", Icons.Default.FastRewind)
    ) + (if (onDetectBeats != null) listOf(ClipToolItem("beats", "Beats", Icons.Default.MusicNote))
    else emptyList()) + (if (onDucking != null) listOf(ClipToolItem("ducking", "Ducking", Icons.Default.TrendingDown))
    else emptyList()) + (if (onNoiseReduction != null) listOf(ClipToolItem("noise", "Noise", Icons.Default.GraphicEq))
    else emptyList()) + (if (onRename != null) listOf(ClipToolItem("rename", "Rename", Icons.Default.Edit))
    else emptyList()) + (if (onLockLayer != null) listOf(ClipToolItem("lock", "Lock", Icons.Default.Lock))
    else emptyList()) + (if (onSoloLayer != null) listOf(ClipToolItem("solo", "Solo", Icons.Default.Visibility))
    else emptyList()) + (if (onLabelColor != null) listOf(ClipToolItem("label", "Label", Icons.Default.Label))
    else emptyList()) + (if (onCopyLayer != null) listOf(ClipToolItem("copy_layer", "Copy layer", Icons.Default.FileCopy))
    else emptyList()) + (if (onPasteLayer != null) listOf(ClipToolItem("paste_layer", "Paste layer", Icons.Default.ContentPaste))
    else emptyList())

    // Build 202 — the same primary-row split as the visual panel
    // (model/ToolRowSplit): no scrolling, no More tile of its own; the
    // overflow is published to the one shared More panel (below).
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val visibleCount = ToolRowSplit.visibleCountForLabels(tools.map { it.label }, screenWidthDp)
    val visibleTools = tools.take(visibleCount)
    val overflowTools = tools.drop(visibleCount)

    fun dispatch(tool: ClipToolItem) {
        when (tool.id) {
            "volume" -> onVolume()
            "fade" -> onFade()
            "speed" -> onSpeed()
            "reverse" -> onReverse()
            "beats" -> onDetectBeats?.invoke()
            "ducking" -> onDucking?.invoke()
            "noise" -> onNoiseReduction?.invoke()
            "rename" -> onRename?.invoke()
            "lock" -> onLockLayer?.invoke()
            "solo" -> onSoloLayer?.invoke()
            "label" -> onLabelColor?.invoke()
            "copy_layer" -> onCopyLayer?.invoke()
            "paste_layer" -> onPasteLayer?.invoke()
        }
    }

    // Publish to the tools grid (build 202). Build 210: while the
    // unified panel is open the FULL list is published (primary first);
    // otherwise only the overflow.
    PublishClipOverflow(
        morePanel = morePanel,
        section = when {
            toolsInGrid && tools.isNotEmpty() -> ClipOverflowSection(
                key = tools.joinToString(",") { it.id },
                tools = tools,
                usedTools = usedTools,
                tileTag = { "audio_tool_$it" },
                onTool = { dispatch(it) }
            )
            overflowTools.isEmpty() -> null
            else -> ClipOverflowSection(
                key = tools.joinToString(",") { it.id },
                tools = overflowTools,
                usedTools = usedTools,
                tileTag = { "audio_tool_$it" },
                onTool = { dispatch(it) }
            )
        }
    )

    // Build 214 (THE DOCK): this panel IS the dock's primary toolbar
    // while an audio layer is selected — it stays visible while the
    // panel stage is open above it (the stage carries the full tool
    // list; the publish above still runs). Build 211's render-nothing
    // state is superseded by the dock architecture.

    // Build 251 (UI migration stage 4): the row is drawn on one native
    // GL surface (ToolsRowGl / PanelGeometry.ToolRow) — the build-206
    // rounded bar, back icon, divider and the visible tiles; the tap
    // targets stay Compose at the same rects with the same tags. The
    // ToolRowSplit budgets are untouched. Build 214 holds: the row
    // renders ALWAYS — it is the dock's primary toolbar for the
    // selected audio layer.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("audio_clip_tools_panel")
    ) {
        ToolsRowGl(
            items = visibleTools.map { tool ->
                ToolRowItem(
                    id = tool.id,
                    label = tool.label,
                    icon = tool.icon,
                    used = tool.id in usedTools,
                    testTag = "audio_tool_${tool.id}",
                    onClick = { dispatch(tool) }
                )
            },
            onBack = onDeselect,
            backTestTag = "audio_tools_back_button",
            backDescription = "Exit audio edit",
            outerPadDp = 2f,
            rowPadDp = 2f
        )
    }
}
