package com.example.ui.components

import com.example.ui.theme.AmCornerCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.MissingMedia
import com.example.engine.NativeVectorCanvas
import com.example.engine.UiLabelStyle
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
        // Build 253 (stage 6): the dialog's slot contents draw in GL
        // (shell stays). Title = headlineSmall Bold in its 32sp line
        // box; body lines carry the dialog 18sp pitch (DLG_* styles).
        title = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { 32.sp.toDp() })
            ) {
                GlTextLine(
                    "Replace missing media", UiLabelStyle.DLG_TITLE_B, HeaderText.toArgb(),
                    Modifier.fillMaxSize()
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlTextBlock(
                    "This project points at files from the phone that made it, so those spots are " +
                        "empty. Pick your own file for each one, or press Done and leave them empty.",
                    UiLabelStyle.DLG_11, TextSecondary.toArgb(),
                    Modifier.fillMaxWidth(),
                    linePitchSp = 18f
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(items, key = { it.sourceUri }) { item ->
                        // One row: the AmPanelHigh fill + hairline ring
                        // on a GL surface behind the content row (the
                        // row's height is content-driven, as in 252).
                        Box(Modifier.fillMaxWidth()) {
                            NativeVectorCanvas(
                                modifier = Modifier.fillMaxSize(),
                                clearArgb = 0,
                                drawKey = Unit,
                                buildCmds = { w, h, dd ->
                                    addRoundRect(
                                        w / 2f, h / 2f, w, h, 12f * dd,
                                        AmPanelHigh.toArgb()
                                    )
                                    addRoundRect(
                                        w / 2f, h / 2f, w - 1f * dd, h - 1f * dd,
                                        12f * dd - 0.5f * dd,
                                        0, 1f * dd, AmHairline.toArgb()
                                    )
                                }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Dialog body lines sit in 18sp
                                    // line boxes (bodyMedium pitch).
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(with(LocalDensity.current) { 18.sp.toDp() })
                                    ) {
                                        GlTextLine(
                                            item.label,
                                            UiLabelStyle.DLG_125_SEMI, HeaderText.toArgb(),
                                            Modifier.fillMaxSize(),
                                            ellipsize = true
                                        )
                                    }
                                    GlTextBlock(
                                        item.where + " · needs a " + item.kind.pickerLabel +
                                            if (item.layerCount > 1) " · used by ${item.layerCount} layers" else "",
                                        UiLabelStyle.DLG_10, TextSecondary.toArgb(),
                                        Modifier.fillMaxWidth(),
                                        linePitchSp = 18f
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
            }
        },
        confirmButton = {
            DialogPrimaryButton(text = "Done", onClick = onDismiss)
        }
    )
}
