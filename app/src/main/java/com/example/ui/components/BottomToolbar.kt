package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolRowSplit
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.EditorBackground
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(EditorBackground)
            .drawBehind {
                // Hairline separating the footer from the timeline (B/W language).
                drawLine(AmHairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The add-layer "+" lives here since build 145 (Sarowar: in the bottom toolbar with
        // the other tools, not up by the timeline): it opens the same AddLayerSheet as before.
        // Same five items, same order — the B/W pass only changes the look:
        // flat tiles, and Add (the bar's primary action) carries the one white badge.
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
            .bouncyClickable(pressedScale = 0.94f, hoverScale = 1.015f, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // BARE since Sarowar's boxless order 2026-11-14 (build 183): no tile under
        // the icon + label — the button is just its glyph and text.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        if (primary) {
            // The primary (Add) item's emphasis is its WHITE glyph now — the white
            // disc badge it sat on went with the boxless order (build 183).
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(21.dp))
            }
        } else {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = IconActive, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (primary) TextPrimary else AmTextDim,
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
 * Build 202: the same "More tools" split as the clip panels (model/ToolRowSplit)
 * — the row no longer scrolls; the first fixed-width tiles that fit stay
 * visible and the rest open as a grid above the row behind a More tile.
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
    modifier: Modifier = Modifier
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
        gapDp = CHAIN_GAP_DP,
        moreWidthDp = CHAIN_TILE_W_DP
    )
    val visibleActions = actions.take(visibleCount)
    val overflowActions = actions.drop(visibleCount)
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("chain_add_panel")
    ) {
        if (expanded && overflowActions.isNotEmpty()) {
            val gridH = ToolRowSplit.gridHeightDp(
                itemCount = overflowActions.size,
                tileHeightDp = CHAIN_TILE_H_DP,
                gapDp = CHAIN_GAP_DP
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-gridH).dp)
                    .fillMaxWidth()
                    .height(gridH.dp)
                    .background(EditorBackground)
                    .drawBehind {
                        drawLine(AmHairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .testTag("chain_more_grid")
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(ToolRowSplit.GRID_COLUMNS),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = ToolRowSplit.GRID_V_PADDING_DP.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(CHAIN_GAP_DP.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(overflowActions, key = { it.testTag }) { action ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CHAIN_TILE_H_DP.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ChainToolItem(action.label, action.icon, action.testTag, action.onClick)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorBackground)
                .padding(vertical = 5.dp),
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
                if (overflowActions.isNotEmpty()) {
                    ChainToolItem(
                        ToolRowSplit.MORE_LABEL, Icons.Default.Apps, "chain_more",
                        onClick = { expanded = !expanded },
                        active = expanded
                    )
                }
            }
        }
    }
}

/** One action of [ChainAddPanel] (build 202: the row and the More grid render from one list). */
private data class ChainAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

/** Chain tile geometry (fixed width by design — see [ChainToolItem]). */
private const val CHAIN_TILE_W_DP = 62
private const val CHAIN_TILE_H_DP = 62
private const val CHAIN_GAP_DP = 6
private const val CHAIN_CONTENT_PADDING_DP = 8

/** A tool of [ChainAddPanel]: fixed width. Bare like the toolbar's items (build 183). */
@Composable
private fun ChainToolItem(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    val destructive = label == "Delete"
    Box(
        modifier = Modifier
            .width(CHAIN_TILE_W_DP.dp)
            .fillMaxHeight()
            .bouncyClickable(pressedScale = 0.94f, hoverScale = 1.015f, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon, label,
                tint = when {
                    destructive -> AmDanger
                    active -> Color.White
                    else -> IconActive
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (active) Color.White else AmTextDim,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
        }
    }
}
