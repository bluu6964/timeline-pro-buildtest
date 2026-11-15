package com.example.ui.components

import androidx.compose.foundation.background
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

@Composable
fun BottomToolbar(
    height: Dp,
    onAddClick: () -> Unit,
    onNullClick: () -> Unit,
    onControllerClick: () -> Unit,
    onChainClick: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Build 206: the toolbar floats as a rounded bar (the redesign's pill
    // language) — full width, items keep their weight(1f) shares.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp)
            .background(AmBarSurface, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .padding(horizontal = AmSpace.sm, vertical = AmSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(AmSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary "Add" sits first; remaining tools are secondary chrome.
        ToolbarItem("Add", Icons.Default.Add, "toolbar_add_button", onAddClick, primary = true)
        ToolbarItem("Null", Icons.Default.CenterFocusWeak, "toolbar_null_button", onNullClick)
        ToolbarItem("Controller", Icons.Default.Tune, "toolbar_controller_button", onControllerClick)
        ToolbarItem("Chain", Icons.Default.ViewStream, "toolbar_chain_button", onChainClick)
        ToolbarItem("3D Camera", Icons.Default.Videocam, "toolbar_camera_button", onCameraClick)
    }
}

@Composable
private fun RowScope.ToolbarItem(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Box(
        modifier = Modifier
            .weight(1f)
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
            // Primary action gets the violet disc (build 206: the accent
            // marks the primary action in the references).
            Box(
                modifier = Modifier
                    .size(if (primary) 40.dp else 36.dp)
                    .then(
                        if (primary) {
                            Modifier
                                .clip(CircleShape)
                                .background(AmAccentViolet)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (primary) AmTextPrimary else IconActive,
                    modifier = Modifier.size(if (primary) 22.dp else 20.dp)
                )
            }
            Spacer(Modifier.height(AmSpace.xxs))
            Text(
                label,
                color = if (primary) AmTextPrimary else AmTextDim,
                fontSize = 10.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
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

    // Build 211 (NO STICKY TOOL LINES): while the unified tools panel
    // is open this panel renders NOTHING at all — build 210 still left
    // its back + divider strip pinned below the grid. The strip is
    // gone (leaving the selection is the layer header's back arrow);
    // the publish above still runs, so the grid keeps the full list.
    if (toolsInGrid) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .testTag("chain_add_panel")
        )
        return
    }

    // Build 206: the panel floats as a rounded bar — full width, so the
    // ToolRowSplit budgets are untouched.
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
        Box(Modifier.width(1.dp).height(32.dp).background(Color(0xFF2C2C2C)))
        // Build 210: not rendered while the unified tools panel is open —
        // the actions live in its one scrolling list; only the back
        // button stays in the footer bar.
        if (!toolsInGrid) {
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
