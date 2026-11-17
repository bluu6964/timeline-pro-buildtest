package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolRowSplit
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmSpace
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.IconActive
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

/**
 * THE DOCK's primary toolbar, no-selection state (build 214 — UI
 * ARCHITECTURE REDESIGN): the permanent bottom bar of the editor. It is
 * ALWAYS visible (the panel stage opens above it, never instead of it)
 * and carries the PROJECT tools with icon + label: Add (the primary —
 * the brand-red disc; the old + disc's role lives here now), Media,
 * Text, Audio, Canvas, and More — which opens the universal-tools panel
 * in the stage (Range in/out, Beat mark, Marker, Onion, Scopes, Snap,
 * Canvas: the UNIVERSAL tools — they are not any layer's tools, so they
 * live here in the no-selection dock, never in a layer's panel).
 * With a selection, this same dock slot carries the selected layer's
 * primary tools instead (the clip panels render them).
 */
@Composable
fun BottomToolbar(
    height: Dp,
    onAddClick: () -> Unit,
    onMediaClick: () -> Unit,
    onTextClick: () -> Unit,
    onAudioClick: () -> Unit,
    onCanvasClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The universal panel is open in the stage (the More item reads selected). */
    moreActive: Boolean = false,
    /** Build 216: the Fx tile — opens the tools panel in the stage (as More does). */
    onFxClick: () -> Unit = {},
    /** Build 216: the Shapes tile — opens the Add Shape sheet. */
    onShapesClick: () -> Unit = {}
) {
    // Build 216 (Sarowar's HTML reference): the dock is a white,
    // horizontally scrolling row of 68 dp tiles (r17, pill surface, ink
    // icons) with the 11 sp muted label UNDER each tile. Active tile =
    // black with a lime icon and a dark bold label. Order follows the
    // reference: Media, Music, Fx, Text, Shapes, then build 214's
    // remaining dock tools (Canvas, More) and Add (the Add sheet stays
    // reachable — Null / Controller / Camera / Chain live there).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBarSurface)
            .testTag("dock_toolbar")
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(com.example.ui.theme.AmHairline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockTile("Media", Icons.Default.AddPhotoAlternate, "toolbar_media_button", onMediaClick)
            DockTile("Music", Icons.Default.MusicNote, "toolbar_audio_button", onAudioClick)
            DockTile("Fx", Icons.Default.AutoAwesome, "toolbar_fx_button", onFxClick, active = moreActive)
            DockTile("Text", Icons.Default.TextFields, "toolbar_text_button", onTextClick)
            DockTile("Shapes", Icons.Default.Category, "toolbar_shapes_button", onShapesClick)
            DockTile("Canvas", Icons.Default.AspectRatio, "toolbar_canvas_button", onCanvasClick)
            DockTile("More", Icons.Default.MoreHoriz, "toolbar_more_button", onMoreClick, active = moreActive)
            DockTile(
                "Add", Icons.Default.Add, "toolbar_add_button", onAddClick,
                tileColor = com.example.ui.theme.AmAccentViolet, iconTint = com.example.ui.theme.AmInk
            )
        }
    }
}

/** One dock tile (build 216): 68 dp r17 tile + the label under it. */
@Composable
private fun DockTile(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    active: Boolean = false,
    tileColor: Color? = null,
    iconTint: Color? = null
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    tileColor
                        ?: if (active) com.example.ui.theme.AmTextPrimary
                        else com.example.ui.theme.AmPanelHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconTint
                    ?: if (active) com.example.ui.theme.AmAccentViolet
                    else com.example.ui.theme.AmTextPrimary,
                modifier = Modifier.size(27.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) com.example.ui.theme.AmTextPrimary else AmTextDim,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Shown while a Chain layer is selected: only ways to put layers INTO the chain (the chain has no picture of its own, so
 * no Effects / Transform / Animations here). A back arrow leaves the chain, the bin deletes it with the layers inside.
 *
 * Build 202: the same primary-row split as the clip panels (model/ToolRowSplit)
 * — the row no longer scrolls and carries no More button; the overflow is
 * published to the one tools grid (a layout-flow sheet since build 206).
 */
@Composable
fun ChainAddPanel(
    height: Dp,
    onBack: () -> Unit,
    onMedia: () -> Unit,
    onText: () -> Unit,
    onShape: () -> Unit,
    onAudio: () -> Unit,
    onStickers: () -> Unit,
    onNull: () -> Unit,
    onController: () -> Unit,
    onCamera: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** The tools grid's bridge (build 202): this panel's overflow actions are published here. */
    morePanel: MorePanelState? = null,
    /**
     * Build 210 (TOOLS PANEL LAW): while the unified tools panel is open
     * it carries ALL of this panel's actions in its one scrolling list —
     * the panel publishes its full list and the footer bar renders only
     * the back button. False = build 202's split behaviour.
     * Build 211: the footer bar renders NOTHING while the panel is open
     * (the back strip is gone too — see the early return below).
     */
    toolsInGrid: Boolean = false
) {
    val actions = listOf(
        ChainAction("Media", Icons.Default.AddPhotoAlternate, "chain_media", onMedia),
        ChainAction("Text", Icons.Default.TextFields, "chain_text", onText),
        ChainAction("Shape", Icons.Default.Category, "chain_shape", onShape),
        ChainAction("Audio", Icons.Default.MusicNote, "chain_audio", onAudio),
        ChainAction("Stickers", Icons.Default.SentimentSatisfiedAlt, "chain_stickers", onStickers),
        ChainAction("Null", Icons.Default.CenterFocusWeak, "chain_null", onNull),
        ChainAction("Controller", Icons.Default.Tune, "chain_controller", onController),
        ChainAction("Camera", Icons.Default.Videocam, "chain_camera", onCamera),
        ChainAction("Delete", Icons.Default.Delete, "chain_delete", onDelete)
    )
    // Chain tiles are fixed-width (62 dp), so the split law runs on widths.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val visibleCount = ToolRowSplit.visibleCount(
        widthsDp = List(actions.size) { CHAIN_TILE_W_DP },
        availableDp = ToolRowSplit.availableDp(screenWidthDp, contentPaddingDp = CHAIN_CONTENT_PADDING_DP),
        gapDp = CHAIN_GAP_DP
    )
    val visibleActions = actions.take(visibleCount)
    val overflowActions = actions.drop(visibleCount)

    // Publish to the tools grid (build 202): a chain action's test tag
    // doubles as its tool id in the grid section. Build 210: while the
    // unified panel is open the FULL action list is published.
    PublishClipOverflow(
        morePanel = morePanel,
        section = when {
            toolsInGrid && actions.isNotEmpty() -> ClipOverflowSection(
                key = actions.joinToString(",") { it.testTag },
                tools = actions.map { ClipToolItem(it.testTag, it.label, it.icon) },
                usedTools = emptySet(),
                tileTag = { it },
                onTool = { item -> actions.firstOrNull { it.testTag == item.id }?.onClick() }
            )
            overflowActions.isEmpty() -> null
            else -> ClipOverflowSection(
                key = actions.joinToString(",") { it.testTag },
                tools = overflowActions.map { ClipToolItem(it.testTag, it.label, it.icon) },
                usedTools = emptySet(),
                tileTag = { it },
                onTool = { item -> overflowActions.firstOrNull { it.testTag == item.id }?.onClick() }
            )
        }
    )

    // Build 214 (THE DOCK): this panel IS the dock's primary toolbar
    // while a Chain is selected — it stays visible while the panel stage
    // is open above it (the stage carries the full action list; the
    // publish above still runs). Build 211's render-nothing state is
    // superseded by the dock architecture.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp)
            .background(AmBarSurface, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .padding(vertical = 5.dp)
            .testTag("chain_add_panel"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 8.dp, end = 6.dp)
                .size(46.dp)
                .bouncyClickable(pressedScale = 0.88f, onClick = onBack)
                .testTag("chain_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit chain", tint = IconActive, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.width(1.dp).height(32.dp).background(com.example.ui.theme.AmHairline))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = CHAIN_CONTENT_PADDING_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(CHAIN_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleActions.forEach { action ->
                ChainToolItem(action.label, action.icon, action.testTag, action.onClick)
            }
        }
    }
}

/** One action of [ChainAddPanel] (build 202: the row and the shared panel's clip section render from one list). */
private data class ChainAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

/** Chain tile geometry (fixed width by design — see [ChainToolItem]). */
private const val CHAIN_TILE_W_DP = 62
private const val CHAIN_GAP_DP = 6
private const val CHAIN_CONTENT_PADDING_DP = 8

/** A tool of [ChainAddPanel]: fixed width. Clean, consistent with main toolbar. */
@Composable
private fun ChainToolItem(label: String, icon: ImageVector, testTag: String, onClick: () -> Unit) {
    val destructive = label == "Delete"
    Box(
        modifier = Modifier
            .width(CHAIN_TILE_W_DP.dp)
            .fillMaxHeight()
            .bouncyClickable(pressedScale = 0.94f, hoverScale = 1.02f, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AmSpace.xxs, vertical = AmSpace.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (destructive) AmDanger else IconActive,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(AmSpace.xxs))
            Text(
                label,
                color = if (destructive) AmDanger else AmTextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
