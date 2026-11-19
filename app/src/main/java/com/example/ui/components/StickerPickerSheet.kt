package com.example.ui.components


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StickerLibrary
import com.example.model.at
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmPillSelected
import com.example.ui.theme.AmSheetShape
import com.example.ui.theme.AmTextDim

/**
 * Sticker picker: category tabs on top, a grid of thumbnails below. Static stickers are PNG
 * files, animated ones are marked "ANIM" (they play in the preview and in the export).
 * Tapping a sticker calls [onPick] and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(
    onPick: (StickerLibrary.Item) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = remember { StickerLibrary.categories() }
    var selected by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    val stickerItems = remember(selected) { if (selected.isEmpty()) emptyList() else StickerLibrary.items(selected) }

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
                .padding(bottom = 20.dp)
        ) {
            Text("Stickers", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            if (categories.isEmpty()) {
                Text(
                    "No stickers found. Add folders to app/src/main/assets/stickers.",
                    color = Color(0xFFBBBBBB),
                    fontSize = 14.sp
                )
                return@Column
            }

            // Category tabs
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val on = cat == selected
                    Box(
                        modifier = Modifier
                            .background(
                                if (on) AmPillSelected else AmPanelHigh,
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { selected = cat }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pill tab (build 207): selected = the filled pill.
                        Text(
                            cat.replaceFirstChar { it.uppercase() },
                            color = if (on) Color.White else AmTextDim,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (stickerItems.isEmpty()) {
                Text("This category is empty.", color = Color(0xFFBBBBBB), fontSize = 14.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stickerItems) { item ->
                        StickerTile(item) {
                            onPick(item)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerTile(item: StickerLibrary.Item, onClick: () -> Unit) {
    // Frame 0 is the thumbnail (cached by StickerLibrary).
    val thumb = remember(item.assetPath) { StickerLibrary.frame(item, 0)?.asImageBitmap() }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(AmPanelElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        thumb?.let {
            Image(
                bitmap = it,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit
            )
        }
        if (item.animated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("ANIM", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Speed of a sticker: how fast its animation plays (one constant speed, build 237). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSpeedSheet(
    clip: com.example.model.TimelineClip,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmPanel,
        shape = AmSheetShape,
        dragHandle = { SheetDragHandle() }
    ) {
        AudioSpeedSheet(
            speed = clip.speed,
            onSpeedChange = onSpeedChange,
            onDismiss = onDismiss
        )
    }
}
