package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolRowSplit
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.EditorBackground
import com.example.ui.util.bouncyClickable

/**
 * Build 202 — the shared pieces of the clip tools panels' "More tools"
 * layout: one bare tool tile (used by the collapsed row AND the expanded
 * grid, so the used-state language — white + bold = used, build 166 — is
 * identical in both) and the overflow grid itself. All geometry comes from
 * [ToolRowSplit], whose constants the harness pins against the 360 dp budget.
 */

/** One bare tool tile: icon over label, no box (boxless law, build 183). */
@Composable
fun ToolTile(
    tool: ClipToolItem,
    used: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .bouncyClickable(pressedScale = 0.88f, hoverScale = 1.08f, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = if (used) Color.White else AmTextDim,
                modifier = Modifier.size(ToolRowSplit.ICON_DP.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tool.label,
                color = if (used) Color.White else AmTextDim,
                fontSize = 11.sp,
                fontWeight = if (used) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The expanded "More tools" grid: the overflow tools in a fixed-column grid
 * of the same tiles. Rendered as an overlay directly above the panel's row
 * (the caller offsets it by -[height]); it carries the panel background and
 * swallows stray taps on its empty padding, so touches never fall through
 * to the timeline underneath. When the overflow runs past
 * [ToolRowSplit.GRID_MAX_ROWS] rows the grid scrolls vertically inside its
 * fixed height.
 */
@Composable
fun OverflowToolsGrid(
    tools: List<ClipToolItem>,
    usedTools: Set<String>,
    height: Dp,
    containerTag: String,
    tileTag: (String) -> String,
    onTool: (ClipToolItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(EditorBackground)
            .drawBehind {
                // Hairline separating the grid from the layer panel it covers.
                drawLine(AmHairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .testTag(containerTag)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(ToolRowSplit.GRID_COLUMNS),
            contentPadding = PaddingValues(
                horizontal = 8.dp,
                vertical = ToolRowSplit.GRID_V_PADDING_DP.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
            verticalArrangement = Arrangement.spacedBy(ToolRowSplit.GRID_GAP_DP.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tools, key = { it.id }) { tool ->
                ToolTile(
                    tool = tool,
                    used = tool.id in usedTools,
                    onClick = { onTool(tool) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ToolRowSplit.GRID_TILE_HEIGHT_DP.dp),
                    testTag = tileTag(tool.id)
                )
            }
        }
    }
}
