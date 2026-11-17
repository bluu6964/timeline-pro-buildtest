package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.R

/**
 * Build 226 — dialog motion on the app's standard spec.
 *
 * Compose dialogs (material3 AlertDialog and the raw full-screen
 * Dialogs) carry no enter/exit animation of their own in the Compose
 * layer: what moved on screen was the Android window animation of the
 * dialog window, owned by the platform theme — outside the app's
 * control, and not the standard chrome spec build 223 set (tween
 * 180 ms, FastOutSlowIn, no spring). These helpers point the dialog
 * window at the app's own window-animation styles, whose XML
 * animations are exactly that spec (180 ms, the platform's
 * fast_out_slow_in interpolator — the same curve as
 * FastOutSlowInEasing — fade, plus a gentle 0.92 scale for small
 * dialogs). XML window animations have no spring physics, and being
 * window-level they play on every dismissal path (button, scrim,
 * back press, programmatic removal alike), exactly as the platform
 * animation did.
 */

/**
 * Sets the current dialog window's enter/exit animations to the
 * app's 180 ms styles. Call as the first statement inside a raw
 * Dialog's content. [fadeOnly] is for full-screen dialogs (a scale
 * would look wrong at that size); small dialogs get fade + scale.
 */
@Composable
fun DialogMotion180(fadeOnly: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view as? DialogWindowProvider)?.window
                ?: (view.parent as? DialogWindowProvider)?.window
            window?.setWindowAnimations(
                if (fadeOnly) R.style.TimelinePro_DialogMotionFade
                else R.style.TimelinePro_DialogMotion
            )
        }
    }
}

/**
 * Build 226 — drop-in replacement for material3's AlertDialog with
 * the identical parameter surface and visuals (it renders the
 * material3 AlertDialog unchanged); the only difference is that the
 * dialog window's motion is the app's standard spec via
 * [DialogMotion180], applied from inside the confirm-button slot,
 * which is composed within the dialog window.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            DialogMotion180(fadeOnly = false)
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}
