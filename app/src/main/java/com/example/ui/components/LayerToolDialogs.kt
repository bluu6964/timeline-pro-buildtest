package com.example.ui.components

import com.example.ui.theme.AmCornerCard
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UiLabelStyle
import com.example.ui.theme.AmPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/** Asks for a new name of a layer. */
@Composable
fun RenameLayerDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = RoundedCornerShape(AmCornerCard),
        // Build 253 (stage 6): dialog titles draw in GL (headlineSmall
        // in its 32sp line box, DLG_TITLE); the field stays Compose.
        title = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { 32.sp.toDp() })
            ) {
                GlTextLine(
                    "Rename layer", UiLabelStyle.DLG_TITLE, TextPrimary.toArgb(),
                    Modifier.fillMaxSize()
                )
            }
        },
        text = {
            PillTextField(
                value = text,
                onValueChange = { text = it.take(60) }
            )
        },
        confirmButton = {
            DialogPrimaryButton(text = "OK", enabled = text.isNotBlank(), onClick = { onConfirm(text) })
        },
        dismissButton = {
            DialogSecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

/**
 * Asks for the name of a user template ("Save as Template", Templates v2): the name the
 * group is saved under in the Templates dialog's "My Templates" list, prefilled with the
 * group's own name.
 */
@Composable
fun SaveTemplateDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = RoundedCornerShape(AmCornerCard),
        title = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { 32.sp.toDp() })
            ) {
                GlTextLine(
                    "Save as Template", UiLabelStyle.DLG_TITLE, TextPrimary.toArgb(),
                    Modifier.fillMaxSize()
                )
            }
        },
        text = {
            Column {
                GlTextBlock(
                    "The whole group — every layer, keyframe and effect inside it — is saved " +
                        "under this name. You can add it to any project later from " +
                        "Add → Templates → My Templates.",
                    UiLabelStyle.DLG_115, TextSecondary.toArgb(),
                    Modifier.fillMaxWidth(),
                    linePitchSp = 18f
                )
                Spacer(Modifier.height(8.dp))
                PillTextField(
                    value = text,
                    onValueChange = { text = it.take(60) }
                )
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = "Save", enabled = text.isNotBlank(), onClick = { onConfirm(text) })
        },
        dismissButton = {
            DialogSecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}
