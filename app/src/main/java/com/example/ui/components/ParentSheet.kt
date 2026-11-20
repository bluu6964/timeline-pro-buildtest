package com.example.ui.components

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmSheetShape
import com.example.ui.theme.EditorSurfaceVariant
import com.example.ui.theme.IconActive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.bouncyClickable

/**
 * Parent picker: the layer follows the chosen parent layer (a Null object), or is CONNECTED
 * to the chosen Controller (build 146: a connection, not a parent — edits of the Controller
 * fan out onto the connected layers). [nulls] = (id, name) of the layers it may link to
 * (no loops), [currentId] = the link now (the parent, else the connection; null = none).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentSheet(
    layerName: String,
    nulls: List<Pair<String, String>>,
    currentId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp)
                .testTag("parent_sheet")
        ) {
            Text("Parent of $layerName", color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(
                "The layer follows the move, turn and size of its parent (a Null). A Controller is not a parent: the layer is only connected to it, and whatever is done to the Controller is done to the connected layers too, each in its own place.",
                color = TextSecondary, fontSize = 10.5.sp
            )
            Spacer(Modifier.height(10.dp))
            Row1("None", null, currentId == null) { onPick(null) }
            for ((id, name) in nulls) {
                Spacer(Modifier.height(6.dp))
                Row1(name, id, currentId == id) { onPick(id) }
            }
            if (nulls.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No Null or Controller layer yet. Add one using the Null / Controller buttons in the bottom toolbar.",
                    color = TextSecondary, fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun Row1(name: String, id: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) AmPillSelected else AmCardSurface,
                RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("parent_pick_${id ?: "none"}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (id != null) Icon(Icons.Default.CenterFocusWeak, null, tint = IconActive, modifier = Modifier.size(18.dp))
        Text(
            name,
            color = TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Default.Check, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
    }
}
