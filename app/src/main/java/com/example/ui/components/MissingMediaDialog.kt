package com.example.ui.components

import com.example.ui.theme.AmCornerCard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.MissingMedia
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.HeaderCyan
import com.example.ui.theme.HeaderText
import com.example.ui.theme.TextSecondary

/** Dialog surface: the flat panel token (was a hard-coded #111111). */
private val DialogFill = AmPanel

/**
 * "Replace missing media" (build 111): the files an imported preset points at lived on the phone
 * that made it, so those spots are empty here. Each row offers the system picker for that one file;
 * a replaced row leaves the list (the screen recomputes it), and Done simply keeps the rest empty.
 */
@Composable
fun MissingMediaDialog(
    items: List<MissingMedia.Item>,
    onReplace: (MissingMedia.Item) -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogFill,
        shape = RoundedCornerShape(AmCornerCard),
        title = { Text("Replace missing media", color = HeaderText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This project points at files from the phone that made it, so those spots are " +
                        "empty. Pick your own file for each one, or press Done and leave them empty.",
                    color = TextSecondary, fontSize = 11.sp
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(items, key = { it.sourceUri }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmPanelHigh)
                                .border(1.dp, AmHairline, RoundedCornerShape(12.dp))
                                .padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.label,
                                    color = HeaderText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    item.where + " · needs a " + item.kind.pickerLabel +
                                        if (item.layerCount > 1) " · used by ${item.layerCount} layers" else "",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            DialogSecondaryButton(
                                text = "Replace",
                                onClick = { onReplace(item) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = "Done", onClick = onDismiss)
        }
    )
}
