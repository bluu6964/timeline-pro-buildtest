package com.example.ui.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.UiGlRuntime
import java.util.concurrent.atomic.AtomicInteger

private class GlTextureHostState {
    /** Accessed only on [UiGlRuntime]'s GL thread. */
    var surfaceId: Int = 0
    /** Accessed only on the Android UI thread. */
    var androidSurface: Surface? = null
    /** Latest GL-thread draw callback (immutable state captured by caller). */
    @Volatile var latestDraw: ((Int) -> Unit)? = null
    /** Coalesces bursts of 120Hz drags / playback ticks so stale frames are skipped. */
    val drawGen = AtomicInteger(0)
}

/**
 * Reusable Compose host for a native GLES3 [TextureView] backed by the shared [UiGlRuntime].
 *
 * Uses [TextureView] (not SurfaceView) so embedded panels, transparent overlays above the preview
 * surface, and Dialog overlays preserve exact Compose Z-order, alpha compositing, and clipping.
 * Touch handling remains in sibling Compose layers so gestures and `testTag` semantics stay untouched.
 */
@Composable
fun NativeGlTextureHost(
    isOpaque: Boolean,
    drawOnGl: (surfaceId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember { GlTextureHostState() }

    SideEffect {
        state.latestDraw = drawOnGl
        val gen = state.drawGen.incrementAndGet()
        UiGlRuntime.post {
            if (state.drawGen.get() != gen) return@post
            val sid = state.surfaceId
            if (sid > 0) state.latestDraw?.invoke(sid)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            state.latestDraw = null
            state.drawGen.incrementAndGet()
            val prevSurf = state.androidSurface
            state.androidSurface = null
            UiGlRuntime.callBlocking {
                val sid = state.surfaceId
                if (sid > 0) {
                    UiGlRuntime.detachSurfaceOnGl(sid)
                    state.surfaceId = 0
                }
            }
            prevSurf?.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                this.isOpaque = isOpaque
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        val old = state.androidSurface
                        val surf = Surface(st)
                        state.androidSurface = surf
                        val gen = state.drawGen.incrementAndGet()
                        UiGlRuntime.post {
                            if (state.surfaceId > 0) {
                                UiGlRuntime.detachSurfaceOnGl(state.surfaceId)
                                state.surfaceId = 0
                            }
                            old?.release()
                            val sid = UiGlRuntime.attachSurfaceOnGl(surf, width, height)
                            state.surfaceId = sid
                            if (sid > 0 && state.drawGen.get() == gen) {
                                state.latestDraw?.invoke(sid)
                            } else if (sid > 0) {
                                // A newer SideEffect arrived while attaching; still draw the latest snapshot now.
                                state.latestDraw?.invoke(sid)
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        val gen = state.drawGen.incrementAndGet()
                        UiGlRuntime.post {
                            val sid = state.surfaceId
                            if (sid > 0) {
                                UiGlRuntime.resizeSurfaceOnGl(sid, width, height)
                                if (state.drawGen.get() == gen || state.latestDraw != null) {
                                    state.latestDraw?.invoke(sid)
                                }
                            }
                        }
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        state.drawGen.incrementAndGet()
                        val surf = state.androidSurface
                        state.androidSurface = null
                        UiGlRuntime.callBlocking {
                            val sid = state.surfaceId
                            if (sid > 0) {
                                UiGlRuntime.detachSurfaceOnGl(sid)
                                state.surfaceId = 0
                            }
                        }
                        surf?.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier
    )
}
