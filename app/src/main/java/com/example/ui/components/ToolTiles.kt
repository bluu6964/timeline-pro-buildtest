package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * The publish bridges of the tools layout: a clip panel hands its
 * overflow tools to the tools grid ([MorePanelState]) through
 * [PublishClipOverflow], and the timeline bars hand their actions
 * through [PublishBarActions].
 *
 * Build 251 (UI migration stage 4): the shared ToolTile composable is
 * gone — the clip/audio rows draw their tiles on the native GL surface
 * (ToolsRowGl) and the tools grid draws its own (ToolsGridSheet), both
 * from PanelGeometry, keeping the used-state language identical
 * (build 166 white + bold; a filled pill since build 206).
 */
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
