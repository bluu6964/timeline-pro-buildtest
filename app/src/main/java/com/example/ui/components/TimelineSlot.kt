package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.model.TimelineTrack
import com.example.model.keyframeTimes
import com.example.viewmodel.VideoEditorViewModel

/**
 * The timeline slot (ruler + layer lanes) extracted out of VideoEditorScreen —
 * inlined there, the subtree pushed the screen's composable lambda over the
 * JVM 64 KB per-method limit (MethodTooLargeException at compileDebugKotlin,
 * Actions run 44125321301). The ruler + lanes render on the C++ surface
 * (NativeTimelineSurface); the panel's non-drawing duties — build-211
 * publishing, the selected-layer bar, the group bar — are composed over it.
 * (Build 260: the multi-select bar is NOT one of them — it lives in the
 * screen's footer slot.)
 *
 * Build 263: the build-224 A/B switch and its Compose timeline branch are
 * gone (Sarowar's order) — the native timeline is the only timeline.
 */
@Composable
fun TimelineSlot(
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
    editingGroupId: String?,
    viewModel: VideoEditorViewModel
) {
    val showActionBarNative = multiSelectIds.isEmpty() && selectedClipId != null
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
        // Build 260: no multi-select overlay here — the screen composes
        // MultiBarHost in the FOOTER slot during multi-select (it replaces
        // the dock automatically), and the host publishes the grid tiles
        // from there too.
        val editingGroup = editingGroupId?.let { gid -> tracks.flatMap { it.clips }.firstOrNull { it.id == gid } }
        if (editingGroup != null) {
            com.example.ui.components.GroupEditBar(
                name = editingGroup.name,
                onExit = { viewModel.exitGroup() },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
