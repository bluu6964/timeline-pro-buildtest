package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Build 202 (scope expansion) — one clip panel's overflow tools, as shown in
 * the clip section of the shared More panel. [key] identifies the tool set;
 * [tileTag] maps a tool id to its test tag (each panel keeps its own tag
 * scheme); [onTool] is the publishing panel's own dispatch, so a tap in the
 * shared panel behaves exactly like a tap in the panel's old grid did.
 */
class ClipOverflowSection(
    val key: String,
    val tools: List<ClipToolItem>,
    val usedTools: Set<String>,
    val tileTag: (String) -> String,
    val onTool: (ClipToolItem) -> Unit
)

/**
 * The bridge between the footer clip tools panels and the ONE shared
 * "More tools" panel over the play-controls row (Sarowar 2026-11-15 ~10:24:
 * all the tool buttons under the canvas go inside the one button).
 *
 * A clip panel does not render its overflow any more; it PUBLISHES it here
 * through a provider lambda (registered in a DisposableEffect, so the exact
 * instance is unregistered when the panel leaves composition — no stale
 * section, no cross-panel clearing). The provider reads the panel's latest
 * composition state, so the section always reflects the panel's current
 * tools / used set / dispatch. PlayControlsSection reads [clipSection]
 * during composition; both state reads are snapshot-tracked.
 */
class MorePanelState {
    var clipSectionProvider by mutableStateOf<(() -> ClipOverflowSection?)?>(null)

    /** The currently selected panel's overflow section, or null when there is none. */
    fun clipSection(): ClipOverflowSection? = clipSectionProvider?.invoke()
}
