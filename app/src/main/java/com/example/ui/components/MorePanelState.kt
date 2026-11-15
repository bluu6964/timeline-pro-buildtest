package com.example.ui.components

import androidx.compose.runtime.Composable
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

/**
 * One action of the timeline's selected-layer bar / multi-select bar, as a
 * tile of the tools grid (build 211 — NO STICKY TOOL LINES: while the
 * tools panel is open those bars are not rendered anywhere; their actions
 * live at the HEAD of the grid's one scrolling list instead). [glyph] is
 * the tile's artwork composable (a Material icon or one of the bars' own
 * drawn glyphs); it sets its own tint. [destructive] reads the label in
 * the semantic red (Delete). [enabled] dims the tile and swallows its tap
 * (the multi bar's distribute tiles below three checked layers).
 */
class BarActionTile(
    val label: String,
    val tag: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val glyph: @Composable () -> Unit
)

/** The bar actions as one ordered section (the bars' own left-to-right, row-by-row order). */
class BarActionsSection(
    val key: String,
    val tiles: List<BarActionTile>
)

/**
 * The bridge between the timeline panel's bars (SelectedBar / MultiBar)
 * and the tools grid — the same provider pattern as [MorePanelState]:
 * the timeline panel publishes its bar actions here while the tools panel
 * is open (and only then), the screen reads [section] during composition
 * for the grid's tile count and content.
 */
class BarActionsState {
    var sectionProvider by mutableStateOf<(() -> BarActionsSection?)?>(null)

    /** The current bar-actions section, or null when the bars render themselves. */
    fun section(): BarActionsSection? = sectionProvider?.invoke()
}
