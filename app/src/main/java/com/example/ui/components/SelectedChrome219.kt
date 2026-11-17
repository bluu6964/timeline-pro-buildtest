package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Build 219 (Sarowar's HTML reference v4, ai_studio_code__44.html):
// the SELECTED state's two surfaces — the header that replaces the top
// bar while a layer is selected, and the 3-page tool pager that
// replaces the add-dock. Colours and geometry are the reference's own
// values for these two surfaces only; everything else in the editor
// keeps build 218's finish.
private val SelBg = Color(0xFF12141D)
private val SelBtn = Color(0xFF212536)
private val SelBtnActive = Color(0xFF2A2F45)
private val SelActiveOutline = Color(0xFF596184)
private val SelLabel = Color(0xFF7E849D)
private val SelDisabled = Color(0xFF4D5266)
private val SelBadge = Color(0xFFE2A918)
private val SelBadgeText = Color(0xFF1A1A1A)
private val SelName = Color(0xFFEAECEF)
private val SelUnderline = Color(0xFF2A2E3F)
private val SelAccent = Color(0xFF1BD8B8)
private val SelDot = Color(0xFF353A4E)

/** The pager's exact total height: page 202 + dots row 10 + bottom padding 10. */
const val SELECTED_PAGER_HEIGHT_DP = 222

/**
 * One header icon-button in the reference's style: a plain white glyph,
 * 4 dp padding, r6, pressed = opacity 0.6 (the reference's :active).
 */
@Composable
private fun SelIconBtn(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .alpha(if (pressed) 0.6f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(4.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { glyph() }
}

/** The reference's Layers glyph: a dashed back rect, a filled front rect, and the cyan target badge. */
@Composable
private fun LayersGlyph219(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val u = size.width / 24f
        val strokeW = 1.7f * u
        // Back rect (dashed), offset up-left.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(1.5f * u, 3.5f * u),
            size = Size(13f * u, 13f * u),
            cornerRadius = CornerRadius(3f * u),
            style = Stroke(width = strokeW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.2f * u, 2.4f * u)))
        )
        // Front rect (filled with the bar colour, solid stroke), offset down-right.
        drawRoundRect(
            color = SelBg,
            topLeft = Offset(6.5f * u, 8.5f * u),
            size = Size(13f * u, 13f * u),
            cornerRadius = CornerRadius(3f * u)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(6.5f * u, 8.5f * u),
            size = Size(13f * u, 13f * u),
            cornerRadius = CornerRadius(3f * u),
            style = Stroke(width = strokeW)
        )
        // Cyan concentric target badge at the front rect's top-right.
        val c = Offset(19.5f * u, 6.5f * u)
        drawCircle(SelAccent, radius = 3.6f * u, center = c, style = Stroke(width = 1.3f * u))
        drawCircle(SelAccent, radius = 1.15f * u, center = c)
    }
}

/** The reference's More glyph: a circle outline with three dots inside. */
@Composable
private fun MoreGlyph219(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val u = size.width / 24f
        drawCircle(
            color = SelName,
            radius = 9.5f * u,
            center = Offset(12f * u, 12f * u),
            style = Stroke(width = 1.7f * u)
        )
        for (dx in listOf(7f, 12f, 17f)) {
            drawCircle(Color.White, radius = 1.55f * u, center = Offset(dx * u, 12f * u))
        }
    }
}

/**
 * The selected state's header (reference .top-bar): 54 dp design in the
 * screen's 58 dp header slot (the slot height feeds the canvas math —
 * CANVAS LAW — so the bar fills the slot and centres its content).
 * Left: Back (deselect) + the layer's real name, underlined, tap =
 * rename. Right: Layers (the all-tools grid), Delete, More (the layer
 * menu — duplicate / parent / lock / visibility / label / copy-paste /
 * resets live here in this state).
 */
@Composable
fun SelectedHeader219(
    height: Dp,
    layerName: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onLayers: () -> Unit,
    onDelete: () -> Unit,
    locked: Boolean,
    hidden: Boolean,
    canParent: Boolean,
    hasClipboard: Boolean,
    onDuplicate: () -> Unit,
    onParent: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleVisibility: () -> Unit,
    onLabel: () -> Unit,
    onCopyLayer: () -> Unit,
    onPasteLayer: (() -> Unit)?,
    onResetTransform: () -> Unit,
    onResetBlend: () -> Unit,
    onResetColor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(SelBg)
            .testTag("selected_header_219")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SelIconBtn(tag = "sel_header_back", onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            // The name wrap: flexes, caps at the reference's 210 dp,
            // 1 dp underline under the name; tap = rename.
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 210.dp)
                        .clickable(onClick = onRename)
                        .testTag("sel_header_name")
                ) {
                    Text(
                        text = layerName,
                        color = SelName,
                        fontSize = 17.sp,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SelUnderline)
                    )
                }
            }
            SelIconBtn(tag = "sel_header_layers", onClick = onLayers) {
                LayersGlyph219()
            }
            SelIconBtn(tag = "sel_header_delete", onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete layer",
                    tint = SelName,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box {
                SelIconBtn(tag = "sel_header_more", onClick = { menuOpen = true }) {
                    MoreGlyph219()
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SelMenuItem("Duplicate", Icons.Default.ContentCopy, "sel_menu_duplicate") { menuOpen = false; onDuplicate() }
                    SelMenuItem("Rename", Icons.Default.Edit, "sel_menu_rename") { menuOpen = false; onRename() }
                    if (canParent) SelMenuItem("Link parent", Icons.Default.Link, "sel_menu_parent") { menuOpen = false; onParent() }
                    SelMenuItem(
                        if (locked) "Unlock" else "Lock",
                        if (locked) Icons.Default.LockOpen else Icons.Default.Lock,
                        "sel_menu_lock"
                    ) { menuOpen = false; onToggleLock() }
                    SelMenuItem(
                        if (hidden) "Show layer" else "Hide layer",
                        if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        "sel_menu_visibility"
                    ) { menuOpen = false; onToggleVisibility() }
                    SelMenuItem("Label colour", Icons.Default.Label, "sel_menu_label") { menuOpen = false; onLabel() }
                    SelMenuItem("Copy layer", Icons.Default.FileCopy, "sel_menu_copy_layer") { menuOpen = false; onCopyLayer() }
                    if (hasClipboard && onPasteLayer != null) {
                        SelMenuItem("Paste layer", Icons.Default.ContentPaste, "sel_menu_paste_layer") { menuOpen = false; onPasteLayer() }
                    }
                    SelMenuItem("Reset transform", Icons.Default.RestartAlt, "sel_menu_reset_transform") { menuOpen = false; onResetTransform() }
                    SelMenuItem("Reset blending", Icons.Default.RestartAlt, "sel_menu_reset_blend") { menuOpen = false; onResetBlend() }
                    SelMenuItem("Reset colour", Icons.Default.RestartAlt, "sel_menu_reset_color") { menuOpen = false; onResetColor() }
                }
            }
        }
    }
}

/** One entry of the selected header's More menu. */
@Composable
private fun SelMenuItem(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        modifier = Modifier.testTag(tag)
    )
}

/** One tool in the selected state's pager (the reference's .tool-btn). */
data class PagerTool219(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val enabled: Boolean = true,
    val active: Boolean = false,
    /** Icon rotation in degrees (Flip V reuses the Flip glyph at 90°). */
    val iconRotate: Float = 0f,
    val onClick: () -> Unit = {}
)

/** One pager row: its height and whether its labels use the small (9.5 sp) size. */
data class PagerRow219(
    val heightDp: Int,
    val smallLabels: Boolean,
    val tools: List<PagerTool219>
)

@Composable
private fun PagerToolBtn(
    tool: PagerTool219,
    iconSizeDp: Int,
    labelGapDp: Int,
    smallLabel: Boolean,
    modifier: Modifier = Modifier
) {
    val iconTint = if (tool.enabled) Color.White else SelDisabled
    val labelColor = if (tool.enabled) SelLabel else SelDisabled
    Box(modifier = modifier.testTag("pager_tool_${tool.id}")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(if (tool.active) SelBtnActive else SelBtn)
                .then(
                    if (tool.active) Modifier.border(1.5.dp, SelActiveOutline, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .clickable(enabled = tool.enabled, onClick = tool.onClick)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                tool.icon,
                contentDescription = tool.label,
                tint = iconTint,
                modifier = Modifier
                    .size(iconSizeDp.dp)
                    .then(if (tool.iconRotate != 0f) Modifier.rotate(tool.iconRotate) else Modifier)
            )
            Spacer(Modifier.height(labelGapDp.dp))
            Text(
                text = tool.label,
                color = labelColor,
                fontSize = if (smallLabel) 9.5.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (tool.badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-6).dp)
                    .background(SelBadge, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = tool.badge,
                    color = SelBadgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

/**
 * The selected state's toolbar (reference .bottom-toolbar): a 3-page
 * horizontal pager (swipe with snap, one page per swipe) + the
 * pagination dots under it (tap a dot = jump to that page). Page 1/2
 * are rows of .tool-btns; page 3 is the 2×3 tall grid.
 */
@Composable
fun SelectedToolsPager219(
    page1: List<PagerRow219>,
    page2: List<PagerRow219>,
    page3: List<PagerTool219>,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SelBg)
            .padding(bottom = 10.dp)
            .testTag("selected_tools_pager_219")
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(202.dp)
        ) { page ->
            if (page == 2) {
                // Page 3: 2 rows × 3 columns of tall buttons (min 88 dp).
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    for (r in 0 until 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (c in 0 until 3) {
                                val tool = page3[r * 3 + c]
                                PagerToolBtn(
                                    tool = tool,
                                    iconSizeDp = 26,
                                    labelGapDp = 8,
                                    smallLabel = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            } else {
                val rows = if (page == 0) page1 else page2
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rows.forEachIndexed { rowIdx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(row.heightDp.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.tools.forEach { tool ->
                                PagerToolBtn(
                                    tool = tool,
                                    // The reference's icon sizes: page-1
                                    // small row 22, page-2 small row 18,
                                    // big rows 24 (page-2 row-4: 23).
                                    iconSizeDp = when {
                                        row.heightDp == 46 -> if (page == 0) 22 else 18
                                        page == 1 && rowIdx == 2 -> 23
                                        else -> 24
                                    },
                                    labelGapDp = if (row.heightDp == 46) 2 else 6,
                                    smallLabel = row.smallLabels,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
        // Pagination dots: 6×6, active = 18 wide in the name colour.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 3) {
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .width(if (active) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) SelName else SelDot)
                        .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                        .testTag("pager_dot_$i")
                )
            }
        }
    }
}
