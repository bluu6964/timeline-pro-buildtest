package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.example.engine.UiGlRuntime
import com.example.engine.UiLabelAtlas
import com.example.engine.VectorCmdBuilder

/**
 * Shared native C++ GLES3 vector canvas wrapper.
 * Builds the compact 2D command buffer and renders via C++ [UiGlRuntime] inside
 * a transparent [NativeGlTextureHost]. GL is the only render path — the Compose
 * Canvas fallback his tracket__6 tree shipped was removed at Sarowar's order
 * (same direction as the build-234 fallback removal).
 *
 * Build 249: when [atlas] is given, it is synced to the renderer on the GL
 * thread at the top of every draw, so CMD_TEXT commands in the stream can
 * reference its entries (the caller resolves its labels before building).
 */
@Composable
fun NativeVectorCanvas(
    modifier: Modifier = Modifier,
    clearArgb: Int = 0,
    drawKey: Any? = null,
    atlas: UiLabelAtlas? = null,
    buildCmds: VectorCmdBuilder.(w: Float, h: Float, density: Float) -> Unit
) {
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
                drawOnGl = { sid ->
                    if (atlas != null) UiGlRuntime.syncUiAtlasOnGl(atlas)
                    UiGlRuntime.drawVectorCommandsOnGl(sid, clearArgb, snap)
                },
                modifier = Modifier.fillMaxSize(),
                drawKey = drawKey?.let { Triple(it, pxW, pxH) }
            )
        }
    }
}

/**
 * Shared direct C++ GLES3 scene canvas for specialized native draw calls
 * (e.g., [UiGlRuntime.drawRulerDialOnGl], [UiGlRuntime.drawPositionPadOnGl], etc.).
 * GL is the only render path (no Compose Canvas fallback — see [NativeVectorCanvas]).
 */
@Composable
fun NativeGlDirectCanvas(
    modifier: Modifier = Modifier,
    drawKey: Any? = null,
    drawOnGl: (surfaceId: Int, density: Float) -> Unit
) {
    val density = LocalDensity.current.density
    NativeGlTextureHost(
        isOpaque = false,
        drawOnGl = { sid -> drawOnGl(sid, density) },
        modifier = modifier,
        drawKey = drawKey
    )
}
