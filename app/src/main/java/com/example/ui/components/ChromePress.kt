package com.example.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.util.amChromeSpec
import kotlinx.coroutines.launch

/**
 * Build 250 (UI migration stage 3): one invisible tap target of a
 * GL-drawn chrome surface — bouncyClickable's gesture contract mirrored
 * line for line (Initial-pass down, touch-slop and consumed checks,
 * click on a clean lift, semantics onClick for TalkBack/tests), except
 * the press [scale] Animatable is shared with the surface's GL draw
 * (applied there as a scale-about-centre transform) instead of driving
 * a graphicsLayer over Compose content. When [enabled] is false the
 * box is inert and carries only its tag/description, exactly as a
 * disabled bouncyClickable button behaves.
 */
@Composable
internal fun ChromePressBox(
    scale: Animatable<Float, AnimationVector1D>,
    pressedScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
    description: String? = null
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    var m = modifier
    if (description != null) {
        m = m.semantics { contentDescription = description }
    }
    if (enabled) {
        m = m
            .semantics(mergeDescendants = false) {
                onClick(action = { latestOnClick(); true })
            }
            .pointerInput(pressedScale) {
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
    if (tag != null) m = m.testTag(tag)
    Box(m)
}

/**
 * Rasterize a Material icon painter into a standalone bitmap (its own
 * size, transparent background) for the UI atlas — the stage-2 dock
 * pattern: the vector's paths carry the shape in their alpha; the
 * atlas tint supplies the colour at blit time.
 */
internal fun rasterizePainterBitmap(painter: VectorPainter, sizePx: Int, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    CanvasDrawScope().draw(
        density = Density(density),
        layoutDirection = LayoutDirection.Ltr,
        canvas = ComposeCanvas(bmp.asImageBitmap()),
        size = Size(sizePx.toFloat(), sizePx.toFloat())
    ) {
        with(painter) { draw(Size(sizePx.toFloat(), sizePx.toFloat())) }
    }
    // A fully transparent result means the painter draw failed — surface
    // it in the log instead of shipping a silently blank glyph.
    var anyAlpha = false
    val row = IntArray(sizePx)
    for (y in 0 until sizePx) {
        bmp.getPixels(row, 0, sizePx, 0, y, sizePx, 1)
        if (row.any { (it ushr 24) != 0 }) {
            anyAlpha = true
            break
        }
    }
    if (!anyAlpha) Log.w("ChromeRaster", "icon rasterization produced a blank bitmap (${sizePx}px)")
    return bmp
}

/**
 * Rasterize a Compose DrawScope glyph (the multi-select bar's drawn
 * icons) into a standalone bitmap of [widthPx] x [heightPx]: the same
 * draw code the Compose Canvas ran, executed once into the atlas.
 * Only the bitmap's alpha is read at blit time (the tint supplies the
 * colour), so partial-alpha strokes keep their weight.
 */
internal fun rasterizeDrawBitmap(
    widthPx: Int, heightPx: Int, density: Float,
    draw: DrawScope.() -> Unit
): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    CanvasDrawScope().draw(
        density = Density(density),
        layoutDirection = LayoutDirection.Ltr,
        canvas = ComposeCanvas(bmp.asImageBitmap()),
        size = Size(widthPx.toFloat(), heightPx.toFloat())
    ) { draw() }
    return bmp
}
