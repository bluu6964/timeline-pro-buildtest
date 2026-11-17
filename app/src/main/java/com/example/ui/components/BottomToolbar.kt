package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolRowSplit
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AmAccentViolet
import com.example.ui.theme.AmBarSurface
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmSpace
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.IconActive
import com.example.ui.theme.TextPrimary
import com.example.ui.util.bouncyClickable

/**
 * The editor DOCK (build 217 — Sarowar's HTML reference, copied
 * exactly): a top hairline (#1E1E24), then a horizontally scrolling row
 * of tiles. A tile = a 58 dp square box (r14, surface fill, 1 dp
 * white-at-4% border) with its glyph, and the label UNDER it (11 sp/600,
 * muted).
 *
 * Build 218 (Sarowar's HTML reference v3): the dock is the ALL-LAYER add
 * dock — exactly TWELVE tiles in the reference's order: Video/Photo,
 * Music, Fx, Text, Shapes, Sticker, Vector, Drawing, Null, 3D Camera,
 * Group, Chain. Every tile performs its own add directly (build 217's
 * arrangement, where Video/Photo opened the Add-layer sheet, is
 * replaced); Fx keeps its job — it opens the effects stage, it is not a
 * layer add. Twelve tiles are wider than the screen, so the row scrolls,
 * exactly as the reference's dock does (its overflow-x: auto).
 */
@Composable
fun BottomToolbar(
    height: Dp,
    onMediaClick: () -> Unit,
    onAudioClick: () -> Unit,
    onFxClick: () -> Unit,
    onTextClick: () -> Unit,
    onShapesClick: () -> Unit,
    onNullClick: () -> Unit,
    modifier: Modifier = Modifier,
    fxActive: Boolean = false,
    /** Build 218: the new tiles' direct adds. */
    onStickerClick: () -> Unit = {},
    onVectorClick: () -> Unit = {},
    onDrawingClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onGroupClick: () -> Unit = {},
    onChainClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AmBarSurface)
            .testTag("dock_toolbar")
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AmCardSurface)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockTile("Video/Photo", "toolbar_media_button", onMediaClick) { DockMediaGlyph() }
            DockTile("Music", "toolbar_audio_button", onAudioClick) {
                Icon(
                    Icons.Default.MusicNote, contentDescription = null,
                    tint = com.example.ui.theme.RefCtrl, modifier = Modifier.size(24.dp)
                )
            }
            DockTile("Fx", "toolbar_fx_button", onFxClick) { DockFxGlyph() }
            DockTile("Text", "toolbar_text_button", onTextClick) { DockTextGlyph() }
            DockTile("Shapes", "toolbar_shapes_button", onShapesClick) { DockShapesGlyph() }
            DockTile("Sticker", "toolbar_sticker_button", onStickerClick) {
                Icon(
                    Icons.Default.SentimentSatisfiedAlt, contentDescription = null,
                    tint = com.example.ui.theme.RefCtrl, modifier = Modifier.size(24.dp)
                )
            }
            DockTile("Vector", "toolbar_vector_button", onVectorClick) { DockVectorGlyph() }
            DockTile("Drawing", "toolbar_drawing_button", onDrawingClick) { DockDrawingGlyph() }
            DockTile("Null", "toolbar_null_button", onNullClick) { DockNullGlyph() }
            DockTile("3D Camera", "toolbar_camera_button", onCameraClick) {
                Icon(
                    Icons.Default.Videocam, contentDescription = null,
                    tint = com.example.ui.theme.RefCtrl, modifier = Modifier.size(24.dp)
                )
            }
            DockTile("Group", "toolbar_group_button", onGroupClick) { DockGroupGlyph() }
            DockTile("Chain", "toolbar_chain_button", onChainClick) { DockChainGlyph() }
        }
    }
}

@Composable
private fun DockTile(
    label: String,
    tag: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(com.example.ui.theme.RefSurface)
                .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            glyph()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = AmTextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** The reference's media glyph: a rounded rect with a play triangle. */
@Composable
private fun DockMediaGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRoundRect(
            Color(0xFFD4D4D8),
            topLeft = Offset(3f * u, 5f * u),
            size = Size(18f * u, 14f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * u),
            style = Stroke(2f * u)
        )
        val tri = Path().apply {
            moveTo(10.2f * u, 9.3f * u)
            lineTo(15.2f * u, 12f * u)
            lineTo(10.2f * u, 14.7f * u)
            close()
        }
        drawPath(tri, Color(0xFFD4D4D8))
    }
}

/** The reference's fx glyph: a small grey sparkle and a big white one. */
@Composable
private fun DockFxGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        fun star(cx: Float, cy: Float, r: Float, col: Color) {
            val path = Path()
            val pts = listOf(
                0f to -1f, 0.24f to -0.24f, 1f to 0f, 0.24f to 0.24f,
                0f to 1f, -0.24f to 0.24f, -1f to 0f, -0.24f to -0.24f
            )
            pts.forEachIndexed { i, pt ->
                val x = cx + pt.first * r; val y = cy + pt.second * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); drawPath(path, col)
        }
        star(8.5f * u, 8.5f * u, 5.5f * u, Color(0xFF71717A))
        star(16f * u, 16f * u, 7.5f * u, Color(0xFFF4F4F5))
    }
}

/** The reference's text glyph: T strokes. */
@Composable
private fun DockTextGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val c = Color(0xFFD4D4D8)
        val w = 2.3f * u
        drawLine(c, Offset(6f * u, 6f * u), Offset(18f * u, 6f * u), w, StrokeCap.Round)
        drawLine(c, Offset(12f * u, 6f * u), Offset(12f * u, 18f * u), w, StrokeCap.Round)
        drawLine(c, Offset(9.5f * u, 18f * u), Offset(14.5f * u, 18f * u), w, StrokeCap.Round)
    }
}

/** The reference's shapes glyph: a grey circle + a white rounded square. */
@Composable
private fun DockShapesGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        drawCircle(Color(0xFF71717A), radius = 5.2f * u, center = Offset(9.8f * u, 9.8f * u))
        drawRoundRect(
            Color(0xFFF4F4F5),
            topLeft = Offset(9.8f * u, 9.8f * u),
            size = Size(10.8f * u, 10.8f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.6f * u)
        )
    }
}

/** The reference's null glyph: a centre square + four corner squares. */
@Composable
private fun DockNullGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        val c = Color(0xFFE4E4E7)
        drawRoundRect(
            c,
            topLeft = Offset(6.5f * u, 6.5f * u),
            size = Size(12f * u, 12f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * u)
        )
        listOf(4f to 4f, 17f to 4f, 4f to 17f, 17f to 17f).forEach { pt ->
            drawRoundRect(
                com.example.ui.theme.RefSurface,
                topLeft = Offset(pt.first * u, pt.second * u),
                size = Size(4f * u, 4f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u)
            )
            drawRoundRect(
                c,
                topLeft = Offset(pt.first * u, pt.second * u),
                size = Size(4f * u, 4f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u),
                style = Stroke(1.5f * u)
            )
        }
    }
}

/** Build 218: the Vector tile's glyph — a node polyline (the Pen tool). */
@Composable
private fun DockVectorGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        val c = Color(0xFFE4E4E7)
        val pts = listOf(5f to 19f, 10f to 7.5f, 15f to 14.5f, 20f to 6f)
        for (i in 0 until pts.size - 1) {
            drawLine(
                c, Offset(pts[i].first * u, pts[i].second * u),
                Offset(pts[i + 1].first * u, pts[i + 1].second * u),
                1.8f * u, StrokeCap.Round
            )
        }
        pts.forEach { pt -> drawCircle(c, radius = 2f * u, center = Offset(pt.first * u, pt.second * u)) }
    }
}

/** Build 218: the Drawing tile's glyph — a freehand scribble stroke. */
@Composable
private fun DockDrawingGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        val c = Color(0xFFE4E4E7)
        val p = Path().apply {
            moveTo(4f * u, 16f * u)
            cubicTo(7f * u, 6f * u, 10.5f * u, 20f * u, 13.5f * u, 10.5f * u)
            cubicTo(15.5f * u, 5.5f * u, 18f * u, 13f * u, 21f * u, 9.5f * u)
        }
        drawPath(p, c, style = Stroke(2f * u, cap = StrokeCap.Round))
    }
}

/** Build 218: the Group tile's glyph — two overlapping rounded squares. */
@Composable
private fun DockGroupGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        val c = Color(0xFFE4E4E7)
        drawRoundRect(
            c.copy(alpha = 0.55f),
            topLeft = Offset(4f * u, 4f * u),
            size = Size(13.5f * u, 13.5f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u),
            style = Stroke(1.9f * u)
        )
        drawRoundRect(
            c,
            topLeft = Offset(8.5f * u, 8.5f * u),
            size = Size(12.5f * u, 12.5f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u),
            style = Stroke(1.9f * u)
        )
    }
}

/** Build 218: the Chain tile's glyph — two interlocked rounded links. */
@Composable
private fun DockChainGlyph() {
    Canvas(Modifier.size(25.dp)) {
        val u = size.width / 25f
        val c = Color(0xFFE4E4E7)
        drawRoundRect(
            c,
            topLeft = Offset(3f * u, 9.5f * u),
            size = Size(10.5f * u, 6.5f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.25f * u),
            style = Stroke(2f * u)
        )
        drawRoundRect(
            c,
            topLeft = Offset(11.5f * u, 9.5f * u),
            size = Size(10.5f * u, 6.5f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.25f * u),
            style = Stroke(2f * u)
        )
    }
}

/**
 * Shown while a Chain layer is selected: only ways to put layers INTO the chain (the chain has no picture of its own, so
 * no Effects / Transform / Animations here). A back arrow leaves the chain, the bin deletes it with the layers inside.
 *
 * Build 202: the same primary-row split as the clip panels (model/ToolRowSplit)
 * — the row no longer scrolls and carries no More button; the overflow is
 * published to the one tools grid (a layout-flow sheet since build 206).
 */
@Composable
fun ChainAddPanel(
    height: Dp,
    onBack: () -> Unit,
    onMedia: () -> Unit,
    onText: () -> Unit,
    onShape: () -> Unit,
    onAudio: () -> Unit,
    onStickers: () -> Unit,
    onNull: () -> Unit,
    onController: () -> Unit,
    onCamera: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** The tools grid's bridge (build 202): this panel's overflow actions are published here. */
    morePanel: MorePanelState? = null,
    /**
     * Build 210 (TOOLS PANEL LAW): while the unified tools panel is open
     * it carries ALL of this panel's actions in its one scrolling list —
     * the panel publishes its full list and the footer bar renders only
     * the back button. False = build 202's split behaviour.
     * Build 211: the footer bar renders NOTHING while the panel is open
     * (the back strip is gone too — see the early return below).
     */
    toolsInGrid: Boolean = false
) {
    val actions = listOf(
        ChainAction("Media", Icons.Default.AddPhotoAlternate, "chain_media", onMedia),
        ChainAction("Text", Icons.Default.TextFields, "chain_text", onText),
        ChainAction("Shape", Icons.Default.Category, "chain_shape", onShape),
        ChainAction("Audio", Icons.Default.MusicNote, "chain_audio", onAudio),
        ChainAction("Stickers", Icons.Default.SentimentSatisfiedAlt, "chain_stickers", onStickers),
        ChainAction("Null", Icons.Default.CenterFocusWeak, "chain_null", onNull),
        ChainAction("Controller", Icons.Default.Tune, "chain_controller", onController),
        ChainAction("Camera", Icons.Default.Videocam, "chain_camera", onCamera),
        ChainAction("Delete", Icons.Default.Delete, "chain_delete", onDelete)
    )
    // Chain tiles are fixed-width (62 dp), so the split law runs on widths.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val visibleCount = ToolRowSplit.visibleCount(
        widthsDp = List(actions.size) { CHAIN_TILE_W_DP },
        availableDp = ToolRowSplit.availableDp(screenWidthDp, contentPaddingDp = CHAIN_CONTENT_PADDING_DP),
        gapDp = CHAIN_GAP_DP
    )
    val visibleActions = actions.take(visibleCount)
    val overflowActions = actions.drop(visibleCount)

    // Publish to the tools grid (build 202): a chain action's test tag
    // doubles as its tool id in the grid section. Build 210: while the
    // unified panel is open the FULL action list is published.
    PublishClipOverflow(
        morePanel = morePanel,
        section = when {
            toolsInGrid && actions.isNotEmpty() -> ClipOverflowSection(
                key = actions.joinToString(",") { it.testTag },
                tools = actions.map { ClipToolItem(it.testTag, it.label, it.icon) },
                usedTools = emptySet(),
                tileTag = { it },
                onTool = { item -> actions.firstOrNull { it.testTag == item.id }?.onClick() }
            )
            overflowActions.isEmpty() -> null
            else -> ClipOverflowSection(
                key = actions.joinToString(",") { it.testTag },
                tools = overflowActions.map { ClipToolItem(it.testTag, it.label, it.icon) },
                usedTools = emptySet(),
                tileTag = { it },
                onTool = { item -> overflowActions.firstOrNull { it.testTag == item.id }?.onClick() }
            )
        }
    )

    // Build 214 (THE DOCK): this panel IS the dock's primary toolbar
    // while a Chain is selected — it stays visible while the panel stage
    // is open above it (the stage carries the full action list; the
    // publish above still runs). Build 211's render-nothing state is
    // superseded by the dock architecture.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp)
            .background(AmBarSurface, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .padding(vertical = 5.dp)
            .testTag("chain_add_panel"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 8.dp, end = 6.dp)
                .size(46.dp)
                .bouncyClickable(pressedScale = 0.88f, onClick = onBack)
                .testTag("chain_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit chain", tint = IconActive, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.width(1.dp).height(32.dp).background(com.example.ui.theme.AmHairline))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = CHAIN_CONTENT_PADDING_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(CHAIN_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleActions.forEach { action ->
                ChainToolItem(action.label, action.icon, action.testTag, action.onClick)
            }
        }
    }
}

/** One action of [ChainAddPanel] (build 202: the row and the shared panel's clip section render from one list). */
private data class ChainAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

/** Chain tile geometry (fixed width by design — see [ChainToolItem]). */
private const val CHAIN_TILE_W_DP = 62
private const val CHAIN_GAP_DP = 6
private const val CHAIN_CONTENT_PADDING_DP = 8

/** A tool of [ChainAddPanel]: fixed width. Clean, consistent with main toolbar. */
@Composable
private fun ChainToolItem(label: String, icon: ImageVector, testTag: String, onClick: () -> Unit) {
    val destructive = label == "Delete"
    Box(
        modifier = Modifier
            .width(CHAIN_TILE_W_DP.dp)
            .fillMaxHeight()
            .bouncyClickable(pressedScale = 0.94f, hoverScale = 1.02f, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AmSpace.xxs, vertical = AmSpace.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (destructive) AmDanger else IconActive,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(AmSpace.xxs))
            Text(
                label,
                color = if (destructive) AmDanger else AmTextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
