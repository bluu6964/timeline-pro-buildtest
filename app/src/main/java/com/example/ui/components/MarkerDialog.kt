package com.example.ui.components

import com.example.ui.theme.AmCornerCard

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.example.model.TimelineMarker
import com.example.ui.theme.AmPanel
import com.example.ui.theme.HeaderText
import com.example.ui.theme.TextSecondary

private val DialogFill = AmPanel

/**
 * The add / edit sheet of the named timeline markers (build 188). Build 207
 * (frontend redesign): the dialog keeps its 24 dp rounded container; the field
 * is the filled pill and the actions are pill buttons (violet confirm, red
 * delete, ghost cancel).
 *
 * Add mode ([marker] = null): the field starts with the suggested name; confirming calls
 * [onConfirm] with the typed name. Edit mode: the field starts with the marker's name,
 * confirming renames it, and the dialog additionally offers Delete (red) — the only other
 * way in is the ruler long-press, which opens this same dialog.
 */
@Composable
fun MarkerNameDialog(
    /** The marker being edited, or null when a new one is being added. */
    marker: TimelineMarker?,
    /** Playhead time the dialog is about (shown under the field), formatted by the caller. */
    timeLabel: String,
    /** Name pre-filled in add mode ("Marker 3"). */
    suggestedName: String,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var text by remember(marker?.id, suggestedName) { mutableStateOf(marker?.name ?: suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogFill,
        shape = RoundedCornerShape(AmCornerCard),
        title = { Text(if (marker == null) "Add marker" else "Marker", color = HeaderText) },
        text = {
            Column {
                PillTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = "Name"
                )
                Text(
                    text = "At $timeLabel",
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            DialogPrimaryButton(
                text = if (marker == null) "Add" else "Save",
                onClick = { onConfirm(text) }
            )
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                if (onDelete != null) {
                    DialogDangerButton(text = "Delete", onClick = onDelete)
                }
                DialogSecondaryButton(text = "Cancel", onClick = onDismiss)
            }
        }
    )
}
