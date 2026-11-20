package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.util.bouncyClickable

/**
 * The shared pieces of the tools layout: one tool tile (used by the clip
 * panels' primary rows AND by the tools grid, so the used-state language —
 * white + bold = used, build 166; a filled pill since build 206 — is
 * identical in both), and the publish bridge a clip panel uses to hand its
 * overflow tools to the tools grid ([MorePanelState]).
 */

/**
 * One tool tile: icon over label. Build 206 (redesign language): a USED
 * tool reads as a filled pill ([AmPillSelected]) behind icon + label — the
 * references' selected state — on top of the build-166 white + bold rule.
 * The background paints the tile's existing box (background before padding),
 * so the tile's footprint — and with it the ToolRowSplit width budgets —
 * is byte-identical to the bare tile's.
 */
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
                modifier = Modifier
                    .then(
                        if (used) Modifier.background(AmPillSelected, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = if (used) AmInk else AmTextDim, // used = ink on the lime wash
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.label,
                color = if (used) AmInk else AmTextDim,
                fontSize = 9.5.sp,
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
 * none) to the tools grid while this panel is composed. The provider
 * is registered/unregistered by identity, so a panel swap during the
 * footer's AnimatedContent transition can never clear the incoming panel's
 * section, and the section the screen reads is always this
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

/**
 * Publishes [section] (the timeline bars' actions as grid tiles, build
 * 211) to the tools grid while the publisher is composed — the same
 * identity discipline as [PublishClipOverflow], so a panel swap can
 * never clear the incoming publisher's section.
 */
@Composable
fun PublishBarActions(state: BarActionsState?, section: BarActionsSection?) {
    val latestSection = rememberUpdatedState(section)
    DisposableEffect(state) {
        val provider: () -> BarActionsSection? = { latestSection.value }
        if (state != null) state.sectionProvider = provider
        onDispose {
            if (state != null && state.sectionProvider === provider) {
                state.sectionProvider = null
            }
        }
    }
}
