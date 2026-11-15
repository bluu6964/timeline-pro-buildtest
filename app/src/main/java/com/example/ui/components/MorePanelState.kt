package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One clip panel's tools, as shown in the tools grid (build 202's shared
 * More panel; a layout-flow grid sheet since build 206; the panel's FULL
 * list while the unified tools panel is open, build 210 — otherwise just
 * its overflow). [key] identifies the tool set; [tileTag] maps a tool id
 * to its test tag (each panel keeps its own tag scheme); [onTool] is the
 * publishing panel's own dispatch, so a tap in the grid behaves exactly
 * like a tap in the panel did.
 */
class ClipOverflowSection(
    val key: String,
    val tools: List<ClipToolItem>,
    val usedTools: Set<String>,
    val tileTag: (String) -> String,
    val onTool: (ClipToolItem) -> Unit
)

/**
 * The bridge between the footer clip tools panels and the ONE tools grid
 * (Sarowar 2026-11-15 ~10:24: all the tool buttons under the canvas go
 * inside the one button; build 206 hosts them in the layout-flow
 * [ToolsGridSheet] instead of the build-202 overlay panel).
 *
 * A clip panel PUBLISHES its grid section here through a provider lambda
 * (registered in a DisposableEffect, so the exact instance is
 * unregistered when the panel leaves composition — no stale section, no
 * cross-panel clearing): its overflow while the tools panel is closed,
 * its full tool list while the panel is open (build 210 — in that state
 * the panel renders no tool row of its own). The provider reads the
 * panel's latest composition state, so the section always reflects the
 * panel's current tools / used set / dispatch. The screen reads
 * [clipSection] during composition; both state reads are snapshot-tracked.
 */
class MorePanelState {
    var clipSectionProvider by mutableStateOf<(() -> ClipOverflowSection?)?>(null)

    /** The currently selected panel's overflow section, or null when there is none. */
    fun clipSection(): ClipOverflowSection? = clipSectionProvider?.invoke()
}
