package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmPanelElevated

/** Progress of the full-video cutout (RVM) with a Cancel button. */
@Composable
fun CutoutProgressBar(
    progress: Float,
    label: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Cancel"
) {
    val p = progress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AmCardSurface)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label ${(p * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text(actionLabel, color = Color.White, fontSize = 11.5.sp) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(AmPanelElevated)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(4.dp)
                    .background(AmAccentViolet)
            )
        }
    }
}
