package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmHairline
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

/**
 * Build 214 — UI ARCHITECTURE REDESIGN: the DOCK and its PANEL STAGE.
 *
 * The dock is the editor's permanent bottom structure (it lives in the
 * lower zone, in normal layout flow — never an overlay over the canvas):
 * a context-aware PRIMARY TOOLBAR that is always visible (see
 * [BottomToolbar] for the no-selection project tools and the clip panels
 * for a selected layer's tools), and above it the PANEL STAGE: every
 * panel opens here with ONE consistent architecture —
 *
 *   ┌──────────────────────────────────────────────┐
 *   │ Title            layer name               ✕  │  [PanelStageHeader]
 *   ├──────────────────────────────────────────────┤
 *   │ SECTION LABEL                                │
 *   │ Label                              [control] │  [PanelRow]
 *   │ Label + value                                │  [PanelSliderRow]
 *   │ ─────────────── slider ───────────────       │
 *   └──────────────────────────────────────────────┘
 *
 * 16 dp padding, one scrolling column per panel (the panel's own content
 * scrolls; the header never does — it is the panel's chrome, not a tool
 * line: the NO STICKY TOOL LINES law governs tool/action rows, and every
 * action still lives inside the panel's scrolling content or the dock
 * toolbar below).
 */

/** The panel stage header's height — the screen's lower-zone math budgets it with the stage. */
const val PANEL_STAGE_HEADER_DP = 40

/** The stage's header row: panel title + the selected layer's name + close. */
@Composable
fun PanelStageHeader(
    title: String,
    layerName: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PANEL_STAGE_HEADER_DP.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (layerName != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    layerName,
                    color = AmTextDim,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "Close panel" }
                    .bouncyClickable(pressedScale = 0.9f, onClick = onClose)
                    .testTag("panel_stage_close"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = AmTextDim,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AmHairline)
        )
    }
}

/**
 * The panel stage container: the card surface with the architecture's
 * 24 dp top corners, the [PanelStageHeader], then the panel's content.
 * The host sizes it (its height comes out of the lower zone — CANVAS LAW).
 */
@Composable
fun PanelStage(
    title: String,
    layerName: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AmCardSurface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) // build 216: sheet radius 22
            .testTag("panel_stage")
    ) {
        PanelStageHeader(title = title, layerName = layerName, onClose = onClose)
        content()
    }
}

/** A grouped section's label inside a panel (small caps, dim). */
@Composable
fun PanelSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = AmTextDim,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

/** A full-width panel row: label on the left, the control on the right. */
@Composable
fun PanelRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        control()
    }
}

/** A slider row: label + value on one line, the slider full-width below it. */
@Composable
fun PanelSliderRow(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    slider: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                color = AmTextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        slider()
    }
}
