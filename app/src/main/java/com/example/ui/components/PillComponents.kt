package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

/**
 * Build 206 — the shared pill components of the frontend-redesign language
 * (Sarowar's references: floating pill bars, a selected item reading as a
 * filled pill with icon + label, one violet accent). Builds 207/208 reuse
 * these for the sheets, dialogs and remaining surfaces.
 */

/**
 * A floating pill bar: a full-capsule surface ([AmBarSurface]) holding a row
 * of items. The bar itself carries no outer margin — the host places it
 * (screen padding / section insets), so width budgets stay the host's.
 */
@Composable
fun FloatingPillBar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .background(AmBarSurface, CircleShape)
            .clip(CircleShape)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * One item of a pill toolbar: icon + label side by side. Selected = the
 * filled [AmPillSelected] capsule around icon + label (the references'
 * selected tab); unselected = bare, dim. The touch target is at least
 * 40 dp tall via the item padding.
 */
@Composable
fun PillToolItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    enabled: Boolean = true
) {
    var m = modifier
        .semantics { contentDescription = label }
        .bouncyClickable(enabled = enabled, onClick = onClick)
    if (testTag != null) m = m.testTag(testTag)
    Box(m, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .then(
                    if (selected) Modifier
                        .background(AmPillSelected, CircleShape)
                        .clip(CircleShape)
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) TextPrimary else AmTextDim,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) TextPrimary else AmTextDim,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * The violet primary pill button (build 206): filled [AmAccentViolet],
 * white content — the references' primary action (New Project, the "+"
 * actions). Content colours are the call site's, and should be white.
 */
@Composable
fun VioletPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    var m = modifier
    if (tag != null) m = m.testTag(tag)
    Box(m, contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .background(AmAccentViolet, CircleShape)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
