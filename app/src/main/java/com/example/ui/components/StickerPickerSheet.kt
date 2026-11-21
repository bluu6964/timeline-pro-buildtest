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
import androidx.compose.ui.graphics.toArgb
import com.example.engine.StickerLibrary
import com.example.engine.UiLabelStyle
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
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
        ) {
            // Build 253 (stage 6): the title and the category tabs
            // draw in GL (the tabs are a GlPillStrip; they used a plain
            // clickable, so pressScale = 1). The sticker grid itself
            // stays Compose: its tiles are bitmap thumbnails, which
            // the alpha-only atlas cannot carry (stages 4–5 limit).
            GlLabelLine("Stickers", UiLabelStyle.SHEET_TITLE_SEMI, 0xFFFFFFFF.toInt())
            Spacer(Modifier.height(8.dp))

            if (categories.isEmpty()) {
                GlTextBlock(
                    "No stickers found. Add folders to app/src/main/assets/stickers.",
                    UiLabelStyle.TEXT_12, 0xFFBBBBBB.toInt(),
                    Modifier.fillMaxWidth(),
                    align = androidx.compose.ui.text.style.TextAlign.Start
                )
                return@Column
            }

            // Category tabs — pill tab (build 207): selected = the filled pill.
            GlPillStrip(
                items = categories.map { cat ->
                    val label = cat.replaceFirstChar { it.uppercase() }
                    GlPillItem(
                        label = label,
                        selected = cat == selected,
                        tag = "",
                        description = label,
                        onClick = { selected = cat }
                    )
                },
                labelStyle = { if (it) UiLabelStyle.ED_TEXT_BOLD else UiLabelStyle.ED_TEXT_MED },
                textArgb = { if (it) 0xFFFFFFFF.toInt() else AmTextDim.toArgb() },
                fillArgb = { if (it) AmPillSelected.toArgb() else AmPanelHigh.toArgb() },
                borderArgb = { null },
                padHDp = 11f,
                padVDp = 5f,
                gapDp = 6f,
                modifier = Modifier.fillMaxWidth(),
                pressScale = 1.0f,
                scrollable = true
            )

            Spacer(Modifier.height(10.dp))

            if (stickerItems.isEmpty()) {
                GlLabelLine("This category is empty.", UiLabelStyle.TEXT_12, 0xFFBBBBBB.toInt())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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

/**
 * Build 253 (stage 6): STAYS Compose — the tile is a bitmap thumbnail
 * (a colour image), which the alpha-only label atlas cannot carry
 * (the stages 4–5 proven limit, same as the Replace-sheet photos).
 * The "ANIM" badge stays with the tile it annotates.
 */
@Composable
private fun StickerTile(item: StickerLibrary.Item, onClick: () -> Unit) {
    // Frame 0 is the thumbnail (cached by StickerLibrary).
    val thumb = remember(item.assetPath) { StickerLibrary.frame(item, 0)?.asImageBitmap() }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(9.dp))
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
                    .padding(5.dp),
                contentScale = ContentScale.Fit
            )
        }
        if (item.animated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("ANIM", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
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
