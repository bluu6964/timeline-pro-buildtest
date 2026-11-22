package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.model.ShapeBooleanOp
import com.example.model.SpatialAction
import com.example.model.TimelineTrack
import com.example.model.keyframeTimes
import com.example.viewmodel.VideoEditorViewModel

/**
 * Build 224 fix round: the timeline slot (ruler + layer lanes, BOTH the
 * native-surface branch and the Compose branch) extracted verbatim out of
 * VideoEditorScreen — inlined there, the subtree pushed the screen's
 * composable lambda over the JVM 64 KB per-method limit
 * (MethodTooLargeException at compileDebugKotlin, Actions run 44125321301).
 * Pure move: same nodes, same order, same parameters; the only edits are
 * the four wirings where the region wrote screen-level state
 * (timelinePxPerMs, and the layer-pill click that flips selGridOpen /
 * toolsPanelManual), which now arrive as value + callback parameters.
 */
@Composable
fun TimelineSlot(
    nativeTimeline: Boolean,
    rulerH: Dp,
    layerPanelH: Dp,
    tracks: List<TimelineTrack>,
    shownTracks: List<TimelineTrack>,
    restrictedTracks: List<TimelineTrack>?,
    playheadProvider: () -> Long,
    totalDurationMs: Long,
    selectedClipId: String?,
    isPlaying: Boolean,
    isMasterMuted: Boolean,
    multiSelectIds: Set<String>,
    waveforms: Map<String, FloatArray>,
    beatMarks: List<Long>,
    exportInMs: Long?,
    exportOutMs: Long?,
    timelinePxPerMs: Float,
    onTimelinePxPerMsChange: (Float) -> Unit,
    toolsPanelOpen: Boolean,
    barActionsState: BarActionsState,
    comfortDensity: Boolean,
    onLayerPillClick: () -> Unit,
    onSpeedAction: (() -> Unit)?,
    onReverseAction: (() -> Unit)?,
    onReplaceAction: () -> Unit,
    onSpatialActionVal: (SpatialAction) -> Unit,
    onBooleanActionVal: (ShapeBooleanOp) -> Unit,
    onDeleteCheckedLayersAction: () -> Unit,
    editingGroupId: String?,
    viewModel: VideoEditorViewModel
) {
    // Build 224 (GRADUAL NATIVE MIGRATION, step 1): with the
    // switch ON, the ruler + lanes render on the C++ surface in
    // this same slot (ruler 44 dp + the layer panel's height);
    // the panel's non-drawing duties — build-211 publishing,
    // the selected-layer bar, the group bar — are composed over
    // it, unchanged. (Build 260: the multi-select bar is NOT one
    // of them any more — it lives in the screen's footer slot.)
    // OFF = the Compose ruler + panel in the else branch, exactly
    // as in build 223.
    val showActionBarNative = multiSelectIds.isEmpty() && selectedClipId != null
    if (nativeTimeline) {
        Box(Modifier.fillMaxWidth().height(rulerH + layerPanelH)) {
            com.example.ui.components.NativeTimelineSurface(
                tracks = restrictedTracks ?: shownTracks,
                playheadMs = playheadProvider,
                totalDurationMs = totalDurationMs,
                selectedClipId = selectedClipId,
                isPlaying = isPlaying,
                onSeekTo = { viewModel.seekTo(it) },
                onScrubStart = { viewModel.pause() },
                onClipSelect = { id -> if (multiSelectIds.isEmpty()) viewModel.selectClip(id) },
                onDeleteClipById = { viewModel.deleteClipById(it) },
                onMoveClipInTime = { id, delta -> viewModel.moveClipInTime(id, delta) },
                onBeginClipTimeDrag = { viewModel.beginAdjust() },
                onReorderLane = { from, to -> viewModel.reorderShownLane(from, to) },
                onTrimClipEdge = { id, left, d -> viewModel.trimClipEdge(id, left, d) },
                onToggleClipVisibility = { id -> viewModel.toggleClipVisibility(id) },
                onSetClipFades = { id, fi, fo -> viewModel.setClipFades(id, fi, fo) },
                pxPerMs = timelinePxPerMs,
                onPxPerMsChange = { onTimelinePxPerMsChange(it) },
                waveforms = waveforms,
                multiSelectIds = multiSelectIds,
                onLongPressLane = { id -> viewModel.enterMultiSelect(id) },
                onToggleMulti = { id -> viewModel.toggleMultiSelect(id) },
                beatMarksMs = beatMarks,
                exportInMs = exportInMs,
                exportOutMs = exportOutMs,
                keyframeTimesMs = selectedClipId?.let { sid ->
                    tracks.flatMap { it.clips }.firstOrNull { it.id == sid }?.let { c ->
                        c.keyframeTimes().map { t -> c.startMs + t }
                    }
                } ?: emptyList(),
                bottomPadDp = com.example.ui.components.timelineBottomPadDp(
                    toolsPanelOpen, multiSelectIds.isNotEmpty(), showActionBarNative
                ),
                comfortDensity = comfortDensity,
                onLayerPillClick = onLayerPillClick,
                modifier = Modifier.fillMaxSize()
            )
            com.example.ui.components.TimelineBarActionsPublisher(
                barActionsPanel = barActionsState,
                showActionBar = showActionBarNative,
                toolsPanelOpen = toolsPanelOpen,
                tracks = restrictedTracks ?: shownTracks,
                selectedClipId = selectedClipId,
                isMasterMuted = isMasterMuted,
                onToggleMasterAudio = { viewModel.toggleMasterAudio() },
                onSplitClip = { viewModel.splitSelectedClip() },
                onDuplicateClip = { viewModel.duplicateSelectedClip() },
                onSpeed = onSpeedAction,
                onReverse = onReverseAction,
                onFlipHorizontal = { viewModel.flipHorizontalSelectedClip() },
                onFlipVertical = { viewModel.flipVerticalSelectedClip() },
                onReplace = onReplaceAction,
                onDeleteClip = { viewModel.deleteSelectedClip() }
            )
            if (showActionBarNative && !toolsPanelOpen) {
                com.example.ui.components.TimelineSelectedBar(
                    tracks = restrictedTracks ?: shownTracks,
                    selectedClipId = selectedClipId,
                    isMasterMuted = isMasterMuted,
                    onToggleMasterAudio = { viewModel.toggleMasterAudio() },
                    onSplitClip = { viewModel.splitSelectedClip() },
                    onDuplicateClip = { viewModel.duplicateSelectedClip() },
                    onSpeed = onSpeedAction,
                    onReverse = onReverseAction,
                    onFlipHorizontal = { viewModel.flipHorizontalSelectedClip() },
                    onFlipVertical = { viewModel.flipVerticalSelectedClip() },
                    onReplace = onReplaceAction,
                    onDeleteClip = { viewModel.deleteSelectedClip() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            // Build 260: no multi-select overlay here any more — the
            // screen composes MultiBarHost in the FOOTER slot during
            // multi-select (it replaces the dock automatically), and
            // the host publishes the grid tiles from there too.
            val editingGroup = editingGroupId?.let { gid -> tracks.flatMap { it.clips }.firstOrNull { it.id == gid } }
            if (editingGroup != null) {
                com.example.ui.components.GroupEditBar(
                    name = editingGroup.name,
                    onExit = { viewModel.exitGroup() },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    } else {

    // 4. Timescale Section (1/30 of screen)
    TimescaleSection(
        height = rulerH, // build 217: the reference's 44 dp ruler
        playheadMs = playheadProvider,
        totalDurationMs = totalDurationMs,
        pxPerMs = timelinePxPerMs,
        beatMarksMs = beatMarks,
        exportInMs = exportInMs,
        exportOutMs = exportOutMs,
        // Build 217: the layer-count pill shows the REAL layer
        // count; its tap opens the timeline options (the tools
        // stage in its no-selection form — the universal tools
        // and the displaced functions live there now).
        layerCount = shownTracks.sumOf { it.clips.size },
        onLayerPillClick = onLayerPillClick,
        // The selected layer's keyframes as the reference's
        // orange diamonds on the ruler.
        keyframeTimesMs = selectedClipId?.let { sid ->
            tracks.flatMap { it.clips }.firstOrNull { it.id == sid }?.let { c ->
                c.keyframeTimes().map { t -> c.startMs + t }
            }
        } ?: emptyList()
    )

    // 5. Layer Panel (5/18 of screen). Inside a group (Edit Group) a green bar on top leads back out.
    Box(Modifier.fillMaxWidth().height(layerPanelH)) {
    TimelineLayerPanel(
        height = layerPanelH,
        // Build 210 (TOOLS PANEL LAW part 3): while the tools
        // panel is open over a selection, ONLY the selected
        // layers' lanes are passed — no other layer row exists
        // in the panel; the full list returns with the panel.
        tracks = restrictedTracks ?: shownTracks,
        playheadMs = playheadProvider,
        totalDurationMs = totalDurationMs,
        selectedClipId = selectedClipId,
        isMasterMuted = isMasterMuted,
        onSeekTo = { viewModel.seekTo(it) },
        onScrubStart = { viewModel.pause() },
        isPlaying = isPlaying,
        // While several layers are checked, taps/drags on layer bodies must not
        // switch to single selection (that would leave multi-select).
        onClipSelect = { id -> if (multiSelectIds.isEmpty()) viewModel.selectClip(id) },
        multiSelectIds = multiSelectIds,
        onLongPressLane = { id -> viewModel.enterMultiSelect(id) },
        onToggleMulti = { id -> viewModel.toggleMultiSelect(id) },
        onMultiAction = { viewModel.applyMultiAction(it) },
        // Build 199: the multi-select bar's Delete tile (A11 — one undo step,
        // locked layers skipped and reported) and All tile (A4), plus the audio
        // fade handles' funnel (A9 — the same setClipFades the Fade sheet uses).
        onDeleteCheckedLayers = onDeleteCheckedLayersAction,
        onSelectAllLayers = { viewModel.selectAllLayers() },
        onSetClipFades = { id, fi, fo -> viewModel.setClipFades(id, fi, fo) },
        // Canvas spatial align / distribute (build 188): the build-176/184
        // pattern run from the screen — the checked layers are rendered at the
        // playhead and measured through the SAME snapRectOf (cropped bounds
        // included since build 182), the pure SpatialAlign rule decides the
        // canvas-px deltas, each delta is converted back to stored units by
        // the layer's depth factor, and the batch lands through
        // viewModel.moveClipsBy as ONE undo step. Audio (no canvas presence)
        // and Chain (a container) never take part.
        onSpatialAction = onSpatialActionVal,
        // Shape booleans (build 192): the checked pair is evaluated AS
        // DRAWN at the playhead — each shape's outline goes through the
        // preview's own ParentRig into frame space (ShapeBooleanGeometry),
        // the pure winding core combines them (model/ShapeBoolean), and
        // the result is baked into a fresh PATH layer through the same
        // normalisation the Pen tool writes (VectorDraw.normalize), whose
        // identity placement draws it exactly where the geometry lies.
        // Subtract = FIRST-checked minus second-checked (the checked set
        // iterates in check order). The originals stay untouched; an
        // empty result (no overlap / fully cut away) only reports.
        onBooleanAction = onBooleanActionVal,
        onSplitClip = {
            viewModel.splitSelectedClip()
        },
        onDeleteClip = {
            viewModel.deleteSelectedClip()
        },
        // Build 218: the chain section's x buttons delete by id.
        onDeleteClipById = { viewModel.deleteClipById(it) },
        onDuplicateClip = {
            viewModel.duplicateSelectedClip()
        },
        onToggleMasterAudio = {
            viewModel.toggleMasterAudio()
        },
        onFlipHorizontal = { viewModel.flipHorizontalSelectedClip() },
        onFlipVertical = { viewModel.flipVerticalSelectedClip() },
        onReplace = onReplaceAction,
        onSpeed = onSpeedAction,
        onReverse = onReverseAction,
        waveforms = waveforms,
        onMoveClipInTime = { id, delta -> viewModel.moveClipInTime(id, delta) },
        onBeginClipTimeDrag = { viewModel.beginAdjust() },
        onReorderLane = { from, to -> viewModel.reorderShownLane(from, to) },
        onTrimClipEdge = { id, left, d -> viewModel.trimClipEdge(id, left, d) },
        onToggleClipVisibility = { id -> viewModel.toggleClipVisibility(id) },
        pxPerMs = timelinePxPerMs,
        onPxPerMsChange = { onTimelinePxPerMsChange(it) },
        // Build 211 (NO STICKY TOOL LINES): while the tools
        // panel is open the panel's bars do not render — they
        // publish their actions to the grid's head tiles.
        toolsPanelOpen = toolsPanelOpen,
        barActionsPanel = barActionsState,
        comfortDensity = comfortDensity
        // No onAddClick since build 145: the "+" moved into the bottom toolbar
        // (BottomToolbar's Add item opens the same AddLayerSheet).
    )
    val editingGroup = editingGroupId?.let { gid -> tracks.flatMap { it.clips }.firstOrNull { it.id == gid } }
    if (editingGroup != null) {
        com.example.ui.components.GroupEditBar(
            name = editingGroup.name,
            onExit = { viewModel.exitGroup() },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
    }
    } // build 224: end of the Compose-timeline else branch
}
