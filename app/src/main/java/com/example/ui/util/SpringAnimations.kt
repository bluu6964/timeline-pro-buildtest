package com.example.ui.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Build 223 (Sarowar's order): THE standard animation spec for ALL UI
 * chrome — one fast, smooth tween (180 ms, FastOutSlowIn: quick start,
 * gentle landing), NO spring anywhere, so nothing overshoots or bounces.
 * Panels, dialogs, sheets, the tools pager, press feedback — every
 * chrome animation uses this. Video-content animation (keyframe easing
 * curves, layer animation playback, export) is NOT chrome and keeps its
 * own data. Helper names in this file keep their historical "spring"
 * wording for call-site compatibility; the specs are all this tween.
 */
fun <T> amChromeSpec(): FiniteAnimationSpec<T> =
    tween(durationMillis = 180, easing = FastOutSlowInEasing)

/**
 * Instant-feedback click with a fast, smooth press (Alight Motion / Apple feel).
 *
 * Why the old version felt laggy: `clickable` inside a scrolling parent (the timeline, toolbars)
 * waits ~100 ms before it reports "pressed", so the press animation started late. Here the finger-down
 * is read in the Initial pass, so the scale reacts on the very first touch frame. The scale is read in
 * graphicsLayer (draw phase), so animating it never recomposes anything.
 * A tap becomes a click only if the finger lifts without sliding past the touch slop and nobody
 * else (a scroll, a swipe) consumed the gesture, so swiping over a button never fires it.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    @Suppress("UNUSED_PARAMETER") hoverScale: Float = 1.025f,
    onClick: () -> Unit
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val latestOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = if (enabled) 1f else 0.45f
        }
        .semantics(mergeDescendants = false) {
            // Keeps accessibility (TalkBack) and UI tests (performClick) working.
            if (enabled) onClick(action = { latestOnClick(); true })
        }
        .pointerInput(enabled, pressedScale) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch {
                    scale.animateTo(pressedScale, amChromeSpec())
                }
                var clicked = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    if (!change.pressed) {
                        change.consume()
                        clicked = true
                        break
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                scope.launch {
                    scale.animateTo(1f, amChromeSpec())
                }
                if (clicked) latestOnClick()
            }
        }
}

fun Modifier.springPressFeedback(
    pressedScale: Float = 0.96f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = amChromeSpec(),
        label = "springPress"
    )

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                waitForUpOrCancellation()
                isPressed = false
            }
        }
}

fun Modifier.springHover(
    hoverScale: Float = 1.025f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) hoverScale else 1f,
        animationSpec = amChromeSpec(),
        label = "springHover"
    )

    this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Fast entrance for anything that appears (dialogs, panels, headers): fades in, rises a few dp and
 * scales up from 0.96 on the standard chrome tween (build 223: was a stiff spring), so it feels
 * instant but never pops or overshoots. [fromBottom] makes big bottom panels slide up further.
 * Runs once, in the draw phase only (no recomposition).
 */
fun Modifier.enterPop(fromBottom: Boolean = false): Modifier = composed {
    val progress = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, amChromeSpec())
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        val rise = if (fromBottom) 56.dp.toPx() else 14.dp.toPx()
        translationY = (1f - p) * rise
        if (!fromBottom) {
            val sc = 0.96f + 0.04f * p
            scaleX = sc
            scaleY = sc
        }
    }
}
