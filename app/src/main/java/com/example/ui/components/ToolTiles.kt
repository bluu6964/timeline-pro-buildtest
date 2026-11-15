package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolRowSplit
import com.example.ui.theme.AmTextDim
import com.example.ui.util.bouncyClickable

/**
 * Build 202 — the shared pieces of the "More tools" layout: one bare tool
 * tile (used by the clip panels' primary rows AND by the clip section of
 * the shared More panel, so the used-state language — white + bold = used,
 * build 166 — is identical in both), and the publish bridge a clip panel
 * uses to hand its overflow tools to the shared panel ([MorePanelState]).
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
 * Publishes [section] (this panel's overflow tools, or null when there are
 * none) to the shared More panel while this panel is composed. The provider
 * is registered/unregistered by identity, so a panel swap during the
 * footer's AnimatedContent transition can never clear the incoming panel's
 * section, and the section the play-controls row reads is always this
 * panel's LATEST one.
 */
@Composable
fun PublishClipOverflow(morePanel: MorePanelState?, section: ClipOverflowSection?) {
    val latestSection = rememberUpdatedState(section)
    DisposableEffect(morePanel) {
        val provider: () -> ClipOverflowSection? = { latestSection.value }
        if (morePanel != null) morePanel.clipSectionProvider = provider
        onDispose {
            if (morePanel != null && morePanel.clipSectionProvider === provider) {
                morePanel.clipSectionProvider = null
            }
        }
    }
}
