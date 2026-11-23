package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.engine.UiGlNative
import com.example.engine.UiLabelStyle
import com.example.engine.UiMathNative
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.util.enterPop
import java.util.Locale

/**
 * Build 266: the Text editor's remaining shared controls. The old
 * Compose colour row and ruler slider were replaced by the GL rows
 * in TextEditView.kt (the build-266 redesign); what stays here is
 * the full colour picker dialog (a hosted dialog app-wide — the
 * stage-6 pattern: its surfaces already draw in GL) and the window
 * pixel picker behind the colour rows' eyedropper.
 */

/** Colors used by the Text edit screens (taken from the reference screenshots). */
internal val SheetBg = AmPanel // build 223 (colour balance): the sheet joins the unified dark surfaces (was build 216's white)
internal val FieldBg = AmPanelElevated // build 223: raised pill surface on the sheet
internal val ChipBg = AmBase // build 223: chips stay the darkest surface (their labels are white) on the sheet — still used by EditShapeView

/** Full color picker: saturation/value square, hue bar, opacity bar and a hex readout. */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val hsv = remember(initial) {
        FloatArray(3).also {
            UiMathNative.nativeRgbToHsv(initial.red, initial.green, initial.blue, it)
        }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }

    val current = remember(hue, sat, value, alpha) {
        val rgb = FloatArray(3)
        UiMathNative.nativeHsvToRgb(hue, sat, value, rgb)
        Color(rgb[0], rgb[1], rgb[2], alpha)
    }
    val hueColor = remember(hue) {
        val rgb = FloatArray(3)
        UiMathNative.nativeHsvToRgb(hue, 1f, 1f, rgb)
        Color(rgb[0], rgb[1], rgb[2], 1f)
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogMotion180(fadeOnly = false)
        Column(
            modifier = Modifier
                .enterPop()
                .clip(RoundedCornerShape(AmCornerCard))
                .background(SheetBg)
                .padding(14.dp)
        ) {
            // Saturation (x) / value (y)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                NativeVectorCanvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            fun set(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                            }
                            detectTapGestures { set(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                    drawKey = Triple(hue, sat, value),
                    buildCmds = { w, h, d ->
                        addShaderQuad(
                            0f, 0f, w, h, 0f, UiGlNative.QUAD_SV_BOX,
                            p0 = hue, p1 = sat, p2 = value, p3 = 1f, p4 = 8f * d
                        )
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            // Hue
            PickerBar(
                fraction = hue / 360f,
                brush = Brush.horizontalGradient(
                    (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
                ),
                onFraction = { hue = (it * 360f).coerceIn(0f, 359.9f) },
                rainbowBar = true
            )
            Spacer(Modifier.height(9.dp))
            // Opacity
            PickerBar(
                fraction = alpha,
                brush = Brush.horizontalGradient(listOf(current.copy(alpha = 0f), current.copy(alpha = 1f))),
                onFraction = { alpha = it.coerceIn(0f, 1f) },
                checker = true,
                solidColorArgb = current.copy(alpha = 1f).toArgb()
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Build 253 (stage 6): the current-colour swatch and
                // the hex readout draw in GL (a flat colour fill is
                // exact in GL); the hex INPUT surfaces elsewhere stay
                // Compose by platform law.
                Box(Modifier.size(32.dp)) {
                    NativeVectorCanvas(
                        modifier = Modifier.fillMaxSize(),
                        clearArgb = 0,
                        drawKey = current.toArgb(),
                        buildCmds = { w, h, dd ->
                            addRoundRect(w / 2f, h / 2f, w, h, 8f * dd, current.toArgb())
                            addRoundRect(
                                w / 2f, h / 2f, w - 1f * dd, h - 1f * dd, 8f * dd - 0.5f * dd,
                                0, 1f * dd, 0x4DFFFFFF
                            )
                        }
                    )
                }
                Spacer(Modifier.width(10.dp))
                GlLabelLine(
                    String.format(Locale.US, "#%08X", current.toArgb()),
                    UiLabelStyle.TEXT_13_MED, TextPrimary.toArgb()
                )
                Spacer(Modifier.weight(1f))
                DialogSecondaryButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.width(6.dp))
                DialogPrimaryButton(
                    text = "OK",
                    onClick = { onPick(current); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun PickerBar(
    fraction: Float,
    brush: Brush,
    onFraction: (Float) -> Unit,
    checker: Boolean = false,
    rainbowBar: Boolean = false,
    solidColorArgb: Int = android.graphics.Color.WHITE
) {
    val update by rememberUpdatedState(onFraction)
    NativeVectorCanvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .pointerInput(Unit) { detectTapGestures { update((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        drawKey = Triple(fraction, rainbowBar, solidColorArgb),
        buildCmds = { w, h, d ->
            if (rainbowBar) {
                addShaderQuad(
                    0f, 0f, w, h, 0f, UiGlNative.QUAD_COLOR_BAR,
                    p0 = 1f, p4 = -1f
                )
            } else {
                if (checker) {
                    addShaderQuad(
                        0f, 0f, w, h, 0f, UiGlNative.QUAD_CHECKER,
                        p0 = 8f * d, c0Argb = 0xFF8A8A8A.toInt(), c1Argb = 0xFFCFCFCF.toInt()
                    )
                }
                addGradRect(0f, 0f, w, h, vertical = false, c0Argb = solidColorArgb and 0x00FFFFFF, c1Argb = solidColorArgb)
            }
            val r = h * 0.5f
            val cx = (fraction * w).coerceIn(r, (w - r).coerceAtLeast(r))
            addCircle(cx, r, r - 1f * d, 0, 3f * d, android.graphics.Color.WHITE)
        },
    )
}

/** Reads the color of one pixel from what is currently on screen (video included). */
fun pickColorFromWindow(context: Context, xWindow: Int, yWindow: Int, onResult: (Long?) -> Unit) {
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onResult(null)
        return
    }
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val rect = Rect(xWindow, yWindow, xWindow + 1, yWindow + 1)
    runCatching {
        PixelCopy.request(activity.window, rect, bitmap, { result ->
            onResult(
                if (result == PixelCopy.SUCCESS) (bitmap.getPixel(0, 0).toLong() and 0xFFFFFFFFL or 0xFF000000L)
                else null
            )
        }, Handler(Looper.getMainLooper()))
    }.onFailure { onResult(null) }
}
