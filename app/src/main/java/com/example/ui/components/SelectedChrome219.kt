package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PagerGrid
import com.example.ui.theme.AmAccent
import com.example.ui.util.amChromeSpec
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmHairlineStrong
import com.example.ui.theme.AmInk
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelElevated
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextFaint
import com.example.ui.theme.AmTextPrimary
import com.example.ui.theme.AmVioletDeep
import kotlinx.coroutines.launch

// Build 219 (Sarowar's HTML reference v4, ai_studio_code__44.html):
// the SELECTED state's two surfaces — the header that replaces the top
// bar while a layer is selected, and the 3-page tool pager that
// replaces the add-dock. Geometry is the reference's own; build 223
// (Sarowar's colour-balance order) moved the colours OFF the
// reference's blue-grey palette onto the app's unified tokens —
// layout, order and behaviour unchanged.
private val SelBg = AmBase
private val SelBtn = AmPanel
private val SelBtnActive = AmPanelElevated
private val SelActiveOutline = AmVioletDeep
private val SelLabel = AmTextDim
private val SelDisabled = AmTextFaint
private val SelBadge = AmAccent
private val SelBadgeText = AmInk
private val SelName = AmTextPrimary
private val SelUnderline = AmHairlineStrong
private val SelAccent = AmAccent
private val SelDot = AmHairlineStrong

/** The pager's exact total height: page 154 + dots row 9 + bottom padding 7. */
const val SELECTED_PAGER_HEIGHT_DP = 170

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
    NativeVectorCanvas(
        modifier = modifier.size(19.dp),
        drawKey = "layers_glyph_219",
        buildCmds = { w, _, _ ->
            val u = w / 24f
            val strokeW = 1.7f * u
            val whiteArgb = Color.White.toArgb()
            // Back dashed rounded rect
            val l = 1.5f * u
            val t = 3.5f * u
            val s = 13f * u
            val r = 3f * u
            val pts = mutableListOf<Pair<Float, Float>>()
            fun addArcPts(cx: Float, cy: Float, startDeg: Float) {
                for (i in 0..4) {
                    val a = Math.toRadians((startDeg + i * 22.5))
                    pts.add((cx + r * kotlin.math.cos(a).toFloat()) to (cy + r * kotlin.math.sin(a).toFloat()))
                }
            }
            addArcPts(l + s - r, t + r, -90f)
            addArcPts(l + s - r, t + s - r, 0f)
            addArcPts(l + r, t + s - r, 90f)
            addArcPts(l + r, t + r, 180f)
            val xs = FloatArray(pts.size) { pts[it].first }
            val ys = FloatArray(pts.size) { pts[it].second }
            addPolyline(xs, ys, pts.size, true, strokeW, whiteArgb, dashOn = 3.2f * u, dashOff = 2.4f * u, capRound = false)
            // Front rect
            addRoundRect(
                (6.5f + 6.5f) * u, (8.5f + 6.5f) * u,
                13f * u, 13f * u, 3f * u,
                fillArgb = SelBg.toArgb(), strokeWidth = strokeW, strokeArgb = whiteArgb
            )
            // Target badge
            val accentArgb = SelAccent.toArgb()
            addCircle(19.5f * u, 6.5f * u, 3.6f * u, 0, 1.3f * u, accentArgb)
            addCircle(19.5f * u, 6.5f * u, 1.15f * u, accentArgb)
        },
    )
}

/** The reference's More glyph: a circle outline with three dots inside. */
@Composable
private fun MoreGlyph219(modifier: Modifier = Modifier) {
    NativeVectorCanvas(
        modifier = modifier.size(19.dp),
        drawKey = "more_glyph_219",
        buildCmds = { w, _, _ ->
            val u = w / 24f
            addCircle(12f * u, 12f * u, 9.5f * u, 0, 1.7f * u, SelName.toArgb())
            val whiteArgb = Color.White.toArgb()
            for (dx in listOf(7f, 12f, 17f)) {
                addCircle(dx * u, 12f * u, 1.55f * u, whiteArgb)
            }
        },
    )
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
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelIconBtn(tag = "sel_header_back", onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
            // The name wrap: flexes, caps at the reference's 180 dp,
            // 1 dp underline under the name; tap = rename.
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .clickable(onClick = onRename)
                        .testTag("sel_header_name")
                ) {
                    Text(
                        text = layerName,
                        color = SelName,
                        fontSize = 14.5.sp,
                        letterSpacing = 0.15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
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
                    modifier = Modifier.size(18.dp)
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
        text = { Text(label, fontSize = 12.5.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp)) },
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(if (tool.active) SelBtnActive else SelBtn)
                .then(
                    if (tool.active) Modifier.border(1.5.dp, SelActiveOutline, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .clickable(enabled = tool.enabled, onClick = tool.onClick)
        ) {
            // Build 220 (ITEM C — toolbar polish, Sarowar's phone
            // correction on 219): the label is MEASURED against the
            // button's real inner width and shrunk in 0.5 sp steps from
            // its base size (9.5 small rows / 11 big rows) down to an
            // 8 sp floor until it fits on ONE line; only a label that
            // still cannot fit wraps to two centred lines, and then the
            // icon/gap give up a few dp so the stack stays inside the
            // button. No label is ever ellipsised or clipped again.
            // includeFontPadding = false + an explicit line height make
            // the stack's height exact, and the small rows' vertical
            // padding drops 4 -> 2 dp: build 219's stack (22 icon + 2
            // gap + font-padded 9.5 sp label + 8 padding) overflowed
            // the 46 dp row and the labels rendered half-clipped.
            val density = LocalDensity.current
            val borderPx = if (tool.active) with(density) { 3.dp.roundToPx() } else 0
            val innerW = constraints.maxWidth - with(density) { 4.dp.roundToPx() } - borderPx
            val measurer = rememberTextMeasurer()
            val baseSp = if (smallLabel) 9f else 10f
            val floorSp = 7.5f
            var fitSp = baseSp
            var oneLine = false
            while (true) {
                val w = measurer.measure(
                    text = tool.label,
                    style = TextStyle(fontSize = fitSp.sp),
                    softWrap = false
                ).size.width
                if (w <= innerW.coerceAtLeast(1)) { oneLine = true; break }
                if (fitSp <= floorSp) break
                fitSp = (fitSp - 0.5f).coerceAtLeast(floorSp)
            }
            val twoLine = !oneLine
            val effIcon = if (twoLine) minOf(iconSizeDp, 16) else iconSizeDp
            val effGap = if (twoLine) 1 else labelGapDp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = if (smallLabel) 2.dp else 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    tool.icon,
                    contentDescription = tool.label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(effIcon.dp)
                        .then(if (tool.iconRotate != 0f) Modifier.rotate(tool.iconRotate) else Modifier)
                )
                Spacer(Modifier.height(effGap.dp))
                Text(
                    text = tool.label,
                    color = labelColor,
                    fontSize = fitSp.sp,
                    lineHeight = (fitSp * 1.15f).sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = if (twoLine) 2 else 1,
                    overflow = if (twoLine) TextOverflow.Clip else TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (tool.badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-4).dp)
                    .background(SelBadge, RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = tool.badge,
                    color = SelBadgeText,
                    fontSize = 8.sp,
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
 * are rows of .tool-btns; page 3 is the tall-button grid — rows of 3
 * derived from the tool list itself since build 238 (model/PagerGrid).
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
            .padding(bottom = 7.dp)
            .testTag("selected_tools_pager_219")
    ) {
        HorizontalPager(
            state = pagerState,
            // Build 223: the pager snaps with the standard chrome tween — no spring.
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = amChromeSpec()
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp)
        ) { page ->
            if (page == 2) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PagerGrid.rows(page3).forEach { rowTools ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowTools.forEach { tool ->
                                if (tool != null) {
                                    PagerToolBtn(
                                        tool = tool,
                                        iconSizeDp = 20,
                                        labelGapDp = 5,
                                        smallLabel = false,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val rows = if (page == 0) page1 else page2
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rows.forEachIndexed { rowIdx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(row.heightDp.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.tools.forEach { tool ->
                                PagerToolBtn(
                                    tool = tool,
                                    iconSizeDp = when {
                                        row.heightDp <= 36 -> if (page == 0) 16 else 15
                                        page == 1 && rowIdx == 2 -> 19
                                        else -> 20
                                    },
                                    labelGapDp = if (row.heightDp <= 36) 2 else 3,
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
        // Pagination dots: 5×5, active = 14 wide in the name colour.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 3) {
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .width(if (active) 14.dp else 5.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(if (active) SelName else SelDot)
                        .clickable { scope.launch { pagerState.animateScrollToPage(i, animationSpec = amChromeSpec()) } }
                        .testTag("pager_dot_$i")
                )
            }
        }
    }
}
