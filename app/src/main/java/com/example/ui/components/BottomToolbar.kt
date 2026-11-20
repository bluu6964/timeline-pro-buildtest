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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
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
 * of tiles. A tile = a square box (surface fill, 1 dp white-at-4%
 * border) with its glyph, and the label UNDER it (11 sp/600, muted).
 * Build 239 (Sarowar): the tiles shrink 58 → 46 dp (r14 → r11, glyphs
 * scaled in proportion) so the dock is SHORTER and more timeline lanes
 * fit above it — same twelve tiles, same order, same layout otherwise.
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
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockTile("Video/Photo", "toolbar_media_button", onMediaClick) { DockMediaGlyph() }
            DockTile("Music", "toolbar_audio_button", onAudioClick) {
                Icon(
                    Icons.Default.MusicNote, contentDescription = null,
                    tint = com.example.ui.theme.RefCtrl, modifier = Modifier.size(15.dp)
                )
            }
            DockTile("Fx", "toolbar_fx_button", onFxClick) { DockFxGlyph() }
            DockTile("Text", "toolbar_text_button", onTextClick) { DockTextGlyph() }
            DockTile("Shapes", "toolbar_shapes_button", onShapesClick) { DockShapesGlyph() }
            DockTile("Sticker", "toolbar_sticker_button", onStickerClick) {
                Icon(
                    Icons.Default.SentimentSatisfiedAlt, contentDescription = null,
                    tint = com.example.ui.theme.RefCtrl, modifier = Modifier.size(15.dp)
                )
            }
            DockTile("Vector", "toolbar_vector_button", onVectorClick) { DockVectorGlyph() }
            DockTile("Drawing", "toolbar_drawing_button", onDrawingClick) { DockDrawingGlyph() }
            DockTile("Null", "toolbar_null_button", onNullClick) { DockNullGlyph() }
            DockTile("3D Camera", "toolbar_camera_button", onCameraClick) {
                Icon(
                    Icons.Default.Videocam, contentDescription = null,
                    tint = com.example.ui.theme.RefCtrl, modifier = Modifier.size(15.dp)
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
            .width(36.dp)
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(com.example.ui.theme.RefSurface)
                .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            glyph()
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = AmTextDim,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** The reference's media glyph: a rounded rect with a play triangle. */
@Composable
private fun DockMediaGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(15.dp),
        drawKey = "dock_media",
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val cArgb = Color(0xFFD4D4D8).toArgb()
            addRoundRect(
                cx = 12f * u, cy = 12f * u,
                width = 18f * u, height = 14f * u,
                cornerRadius = 4f * u,
                fillArgb = 0,
                strokeWidth = 2f * u,
                strokeArgb = cArgb
            )
            addFilledPolygon(
                floatArrayOf(10.2f * u, 15.2f * u, 10.2f * u),
                floatArrayOf(9.3f * u, 12f * u, 14.7f * u),
                3, cArgb
            )
        },
    )
}

/** The reference's fx glyph: a small grey sparkle and a big white one. */
@Composable
private fun DockFxGlyph() {
    val pts = remember {
        listOf(
            0f to -1f, 0.24f to -0.24f, 1f to 0f, 0.24f to 0.24f,
            0f to 1f, -0.24f to 0.24f, -1f to 0f, -0.24f to -0.24f
        )
    }
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_fx",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            fun addStar(cx: Float, cy: Float, r: Float, argb: Int) {
                val xs = FloatArray(pts.size) { cx + pts[it].first * r }
                val ys = FloatArray(pts.size) { cy + pts[it].second * r }
                addFilledPolygon(xs, ys, pts.size, argb)
            }
            addStar(8.5f * u, 8.5f * u, 5.5f * u, Color(0xFF71717A).toArgb())
            addStar(16f * u, 16f * u, 7.5f * u, Color(0xFFF4F4F5).toArgb())
        },
    )
}

/** The reference's text glyph: T strokes. */
@Composable
private fun DockTextGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(15.dp),
        drawKey = "dock_text",
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val cArgb = Color(0xFFD4D4D8).toArgb()
            val sw = 2.3f * u
            addLine(6f * u, 6f * u, 18f * u, 6f * u, sw, cArgb, capRound = true)
            addLine(12f * u, 6f * u, 12f * u, 18f * u, sw, cArgb, capRound = true)
            addLine(9.5f * u, 18f * u, 14.5f * u, 18f * u, sw, cArgb, capRound = true)
        },
    )
}

/** The reference's shapes glyph: a grey circle + a white rounded square. */
@Composable
private fun DockShapesGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_shapes",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            addCircle(9.8f * u, 9.8f * u, 5.2f * u, Color(0xFF71717A).toArgb())
            addRoundRect(
                cx = (9.8f + 5.4f) * u, cy = (9.8f + 5.4f) * u,
                width = 10.8f * u, height = 10.8f * u,
                cornerRadius = 2.6f * u,
                fillArgb = Color(0xFFF4F4F5).toArgb()
            )
        },
    )
}

/** The reference's null glyph: a centre square + four corner squares. */
@Composable
private fun DockNullGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_null",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            val cArgb = Color(0xFFE4E4E7).toArgb()
            val bgArgb = com.example.ui.theme.RefSurface.toArgb()
            addRoundRect(12.5f * u, 12.5f * u, 12f * u, 12f * u, 1.5f * u, cArgb)
            listOf(4f to 4f, 17f to 4f, 4f to 17f, 17f to 17f).forEach { pt ->
                addRoundRect(
                    (pt.first + 2f) * u, (pt.second + 2f) * u,
                    4f * u, 4f * u, 1f * u,
                    fillArgb = bgArgb, strokeWidth = 1.5f * u, strokeArgb = cArgb
                )
            }
        },
    )
}

/** Build 218: the Vector tile's glyph — a node polyline (the Pen tool). */
@Composable
private fun DockVectorGlyph() {
    val pts = remember { listOf(5f to 19f, 10f to 7.5f, 15f to 14.5f, 20f to 6f) }
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_vector",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            val cArgb = Color(0xFFE4E4E7).toArgb()
            val xs = FloatArray(pts.size) { pts[it].first * u }
            val ys = FloatArray(pts.size) { pts[it].second * u }
            addPolyline(xs, ys, pts.size, false, 1.8f * u, cArgb, capRound = true)
            for (i in pts.indices) addCircle(xs[i], ys[i], 2f * u, cArgb)
        },
    )
}

/** Build 218: the Drawing tile's glyph — a freehand scribble stroke. */
@Composable
private fun DockDrawingGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_drawing",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            val cArgb = Color(0xFFE4E4E7).toArgb()
            val sw = 2f * u
            addCubic(4f * u, 16f * u, 7f * u, 6f * u, 10.5f * u, 20f * u, 13.5f * u, 10.5f * u, sw, cArgb, capRound = true)
            addCubic(13.5f * u, 10.5f * u, 15.5f * u, 5.5f * u, 18f * u, 13f * u, 21f * u, 9.5f * u, sw, cArgb, capRound = true)
        },
    )
}

/** Build 218: the Group tile's glyph — two overlapping rounded squares. */
@Composable
private fun DockGroupGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_group",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            val c = Color(0xFFE4E4E7)
            val sw = 1.9f * u
            addRoundRect(
                cx = (4f + 6.75f) * u, cy = (4f + 6.75f) * u,
                width = 13.5f * u, height = 13.5f * u,
                cornerRadius = 3f * u, fillArgb = 0, strokeWidth = sw, strokeArgb = c.copy(alpha = 0.55f).toArgb()
            )
            addRoundRect(
                cx = (8.5f + 6.25f) * u, cy = (8.5f + 6.25f) * u,
                width = 12.5f * u, height = 12.5f * u,
                cornerRadius = 3f * u, fillArgb = 0, strokeWidth = sw, strokeArgb = c.toArgb()
            )
        },
    )
}

/** Build 218: the Chain tile's glyph — two interlocked rounded links. */
@Composable
private fun DockChainGlyph() {
    NativeVectorCanvas(
        modifier = Modifier.size(16.dp),
        drawKey = "dock_chain",
        buildCmds = { w, _, _ ->
            val u = w / 25f
            val cArgb = Color(0xFFE4E4E7).toArgb()
            val sw = 2f * u
            addRoundRect((3f + 5.25f) * u, (9.5f + 3.25f) * u, 10.5f * u, 6.5f * u, 3.25f * u, 0, sw, cArgb)
            addRoundRect((11.5f + 5.25f) * u, (9.5f + 3.25f) * u, 10.5f * u, 6.5f * u, 3.25f * u, 0, sw, cArgb)
        },
    )
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
            .padding(vertical = 3.dp)
            .background(AmBarSurface, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .padding(vertical = 4.dp)
            .testTag("chain_add_panel"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 4.dp)
                .size(36.dp)
                .bouncyClickable(pressedScale = 0.88f, onClick = onBack)
                .testTag("chain_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit chain", tint = IconActive, modifier = Modifier.size(19.dp))
        }
        Box(Modifier.width(1.dp).height(24.dp).background(com.example.ui.theme.AmHairline))
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
private const val CHAIN_TILE_W_DP = 50
private const val CHAIN_GAP_DP = 5
private const val CHAIN_CONTENT_PADDING_DP = 6

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
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (destructive) AmDanger else IconActive,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.height(AmSpace.xxs))
            Text(
                label,
                color = if (destructive) AmDanger else AmTextDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
