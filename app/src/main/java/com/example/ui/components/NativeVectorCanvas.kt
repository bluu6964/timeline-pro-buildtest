package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.example.engine.UiGlRuntime
import com.example.engine.VectorCmdBuilder

/**
 * Shared native C++ GLES3 vector canvas wrapper.
 * When `libam_engine.so` and EGL are available (on Android), builds the compact 2D command buffer
 * and renders via C++ [UiGlRuntime] inside a transparent [NativeGlTextureHost].
 * On host JVM (where native `.so` isn't loaded), runs [fallbackDraw] in Compose Canvas.
 */
@Composable
fun NativeVectorCanvas(
    modifier: Modifier = Modifier,
    clearArgb: Int = 0,
    drawKey: Any? = null,
    buildCmds: VectorCmdBuilder.(w: Float, h: Float, density: Float) -> Unit,
    fallbackDraw: (DrawScope.() -> Unit)? = null
) {
    if (!UiGlRuntime.isSupported) {
        if (fallbackDraw != null) {
            Canvas(modifier = modifier, onDraw = fallbackDraw)
        } else {
            Box(modifier = modifier)
        }
        return
    }

    val density = LocalDensity.current.density
    var pxW by remember { mutableFloatStateOf(0f) }
    var pxH by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.onSizeChanged {
            pxW = it.width.toFloat()
            pxH = it.height.toFloat()
        }
    ) {
        if (pxW > 0f && pxH > 0f) {
            val snap = VectorCmdBuilder().apply { buildCmds(pxW, pxH, density) }.build()
            NativeGlTextureHost(
                isOpaque = false,
                drawOnGl = { sid -> UiGlRuntime.drawVectorCommandsOnGl(sid, clearArgb, snap) },
                modifier = Modifier.fillMaxSize(),
                drawKey = drawKey?.let { Triple(it, pxW, pxH) }
            )
        }
    }
}

/**
 * Shared direct C++ GLES3 scene canvas for specialized native draw calls
 * (e.g., [UiGlRuntime.drawRulerDialOnGl], [UiGlRuntime.drawPositionPadOnGl], etc.).
 */
@Composable
fun NativeGlDirectCanvas(
    modifier: Modifier = Modifier,
    drawKey: Any? = null,
    drawOnGl: (surfaceId: Int, density: Float) -> Unit,
    fallbackDraw: (DrawScope.() -> Unit)? = null
) {
    if (!UiGlRuntime.isSupported) {
        if (fallbackDraw != null) {
            Canvas(modifier = modifier, onDraw = fallbackDraw)
        } else {
            Box(modifier = modifier)
        }
        return
    }
    val density = LocalDensity.current.density
    NativeGlTextureHost(
        isOpaque = false,
        drawOnGl = { sid -> drawOnGl(sid, density) },
        modifier = modifier,
        drawKey = drawKey
    )
}
