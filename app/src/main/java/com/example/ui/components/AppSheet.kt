package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.example.ui.util.amChromeSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Build 226 — the app's own bottom sheet; replaces material3's
 * ModalBottomSheet at every editor call site.
 *
 * Why it exists: material3 1.3's SheetState rides on an
 * AnchoredDraggableState whose animation spec is the library's own
 * spring and is NOT exposed as a parameter of
 * rememberModalBottomSheetState, so after build 223 put every app-level
 * spec on the one standard chrome tween ([amChromeSpec] — tween
 * 180 ms, FastOutSlowIn), the sheets still opened and closed on library
 * spring physics. Here the whole motion path — slide in, slide out,
 * drag settle — is [amChromeSpec]; there is no spring anywhere.
 *
 * Behaviour parity with the ModalBottomSheet it replaces:
 *  - scrim (black 32 %) tap dismisses; back press dismisses;
 *  - drag the sheet down to dismiss — from the drag handle, or by
 *    dragging scrollable content down once it is scrolled to its top
 *    (the nested-scroll hand-off the library sheet performs);
 *  - a short drag settles back, on the standard spec. Settle rule
 *    (this sheet's own, in place of the library's anchored-draggable
 *    thresholds): dismiss when the sheet is pulled past 35 % of its
 *    height, or the release velocity is a downward flick
 *    (> 800 px/s), or a fast downward fling reaches the content top
 *    (> 1500 px/s); otherwise settle back;
 *  - the scrim fades slightly as the sheet is pulled down, as the
 *    library sheet's did;
 *  - a caller removing the sheet from composition (its own state
 *    flip) drops it immediately — the same as removing a
 *    ModalBottomSheet from composition did. Animated exits run on
 *    the sheet's own dismiss paths (scrim, back, drag), which call
 *    [onDismissRequest] only after the exit motion finishes;
 *  - the panel carries imePadding so a text field inside (Add Text)
 *    stays above the keyboard, as it did inside the library sheet's
 *    dialog window.
 *
 * Layout is the library sheet's: full-width panel (capped at
 * 640 dp, centred) in [containerColor] / [shape], [dragHandle] on
 * top, then the [content] column.
 */
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color,
    shape: Shape,
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val transitionState = remember {
        MutableTransitionState(initialState = false).apply { targetState = true }
    }
    var entered by remember { mutableStateOf(false) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun dismiss() {
        if (transitionState.targetState) transitionState.targetState = false
    }

    fun settleOrDismiss(velocityYPxPerSec: Float) {
        val h = sheetHeightPx
        if ((h > 0 && dragOffsetPx > h * 0.35f) || velocityYPxPerSec > 800f) {
            dismiss()
        } else if (dragOffsetPx != 0f) {
            settleJob?.cancel()
            val from = dragOffsetPx
            settleJob = scope.launch {
                AnimationState(initialValue = from).animateTo(
                    targetValue = 0f,
                    animationSpec = amChromeSpec()
                ) { dragOffsetPx = value }
            }
        }
    }

    // Fire the caller's dismiss only once the exit motion has fully
    // finished (and only if the sheet was ever fully shown).
    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (transitionState.currentState) entered = true
        if (entered && !transitionState.targetState && !transitionState.currentState) {
            onDismissRequest()
        }
    }

    BackHandler(enabled = transitionState.targetState) { dismiss() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging up while the sheet is pulled down: the
                // sheet returns to rest before the content scrolls.
                if (available.y < 0f && dragOffsetPx > 0f) {
                    settleJob?.cancel()
                    val consumed = max(available.y, -dragOffsetPx)
                    dragOffsetPx += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Leftover downward drag — the content is already at
                // its top — pulls the sheet down instead.
                if (available.y > 0f && transitionState.targetState) {
                    settleJob?.cancel()
                    dragOffsetPx += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetPx > 0f) {
                    settleOrDismiss(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragOffsetPx > 0f) {
                    settleOrDismiss(available.y)
                    return available
                }
                // A fast downward fling that lands on content already
                // at its top dismisses, as the library sheet's did.
                if (available.y > 1500f && transitionState.targetState) {
                    dismiss()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val handleDraggable = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
        },
        onDragStarted = { settleJob?.cancel() },
        onDragStopped = { velocity -> settleOrDismiss(velocity) }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim.
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(amChromeSpec()),
            exit = fadeOut(amChromeSpec())
        ) {
            val dragFraction = if (sheetHeightPx > 0) {
                (dragOffsetPx / sheetHeightPx).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * (1f - dragFraction * 0.6f)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { dismiss() }
            )
        }

        // Panel.
        AnimatedVisibility(
            visibleState = transitionState,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(amChromeSpec()) { it },
            exit = slideOutVertically(amChromeSpec()) { it }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragOffsetPx.fastRoundToInt()) }
                    .clip(shape)
                    .background(containerColor)
                    .nestedScroll(nestedScrollConnection)
                    .imePadding()
                    .onSizeChanged { sheetHeightPx = it.height }
            ) {
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(handleDraggable),
                        contentAlignment = Alignment.Center
                    ) { dragHandle() }
                }
                content()
            }
        }
    }
}
