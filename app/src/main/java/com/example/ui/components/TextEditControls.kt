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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.engine.UiGlNative
import com.example.engine.UiMathNative
import com.example.ui.util.enterPop
import com.example.ui.util.bouncyClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.TextPrimary
import java.util.Locale

/** Colors used by the Text edit panel (taken from the reference screenshots). */
internal val SheetBg = AmPanel // build 223 (colour balance): the sheet joins the unified dark surfaces (was build 216's white)
internal val FieldBg = AmPanelElevated // build 223: raised pill surface on the sheet
internal val ChipBg = AmBase // build 223: chips stay the darkest surface (their labels are white) on the sheet
internal val ChipSelectedBg = AmAccent // build 223: selected chip = the one lime (its label is ink)

private val Swatches = listOf(
    0xFF9C27B0, 0xFFE91E63, 0xFFF44336, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000,
    0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF00E5FF, 0xFF2196F3, 0xFF3F51B5
)

/**
 * "Color" row: scrollable swatches, then the palette button (full picker) and the eyedropper
 * (pick a color from the canvas) pinned on the right.
 */
@Composable
fun TextColorRow(
    selected: Long,
    onSelect: (Long) -> Unit,
    onEyedropper: ((Long) -> Unit) -> Unit,
    label: String = "Color",
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = Swatches.none { it == selected }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(Swatches) { c ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(c))
                        .then(
                            if (c == selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            else Modifier.border(1.dp, Color.Black.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        )
                        .bouncyClickable(pressedScale = 0.85f) { onSelect(c) }
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCustom) Color(selected) else Color.Transparent)
                .border(1.5.dp, Color.White.copy(alpha = if (isCustom) 1f else 0.85f), RoundedCornerShape(8.dp))
                .bouncyClickable(pressedScale = 0.88f) { showPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Palette, "Custom color", tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBg)
                .bouncyClickable(pressedScale = 0.88f) { onEyedropper(onSelect) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Colorize, "Pick from canvas", tint = TextPrimary, modifier = Modifier.size(17.dp))
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = Color(selected),
            onPick = { onSelect(it.toArgb().toLong() and 0xFFFFFFFFL) },
            onDismiss = { showPicker = false }
        )
    }
}

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
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(current)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "#%08X", current.toArgb()),
                    color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
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

/**
 * Ruler-style slider matching the reference ("Thickness |||||| 0%"): drag the ruler, one tick =
 * one unit, a quick swipe glides a little. Same behaviour as the other sliders ([RulerDial]).
 */
@Composable
fun TextRulerSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float = 0f,
    max: Float = 100f,
    unit: String = "%",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(88.dp))
        RulerDial(
            value = value,
            onValueChange = onValueChange,
            min = min,
            max = max,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Text(
            text = String.format(Locale.US, "%d%s", value.toInt(), unit),
            color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
    }
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
