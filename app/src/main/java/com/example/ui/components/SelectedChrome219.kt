package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.VectorCmdBuilder
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.PanelGeometry
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
import kotlin.math.roundToInt
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
 * One header icon-button's INVISIBLE tap target (build 250: the glyph
 * is drawn on the header's GL surface; the pressed state is read by
 * the header from the same [interaction] source and applied there as
 * the reference's :active opacity 0.6). Same box as build 219's
 * SelIconBtn: r6 clip, no indication, 4 dp padding around the glyph
 * box, the original tag.
 */
@Composable
private fun SelTouchBtn(
    tag: String,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    glyphSize: Dp,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(ChromeGeometry.SelHeader.BTN_PAD_DP.dp)
            .then(
                if (description != null) Modifier.semantics {
                    contentDescription = description
                } else Modifier
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { Spacer(Modifier.size(glyphSize)) }
}

/**
 * The reference's Layers glyph: a dashed back rect, a filled front
 * rect, and the cyan target badge. Build 250: the build-219 canvas
 * body, ported command-for-command into the header's surface;
 * [alpha] is the pressed state (the reference's :active 0.6).
 */
private fun VectorCmdBuilder.glyphLayers219Body(w: Float, alpha: Float) {
    val u = w / 24f
    val strokeW = 1.7f * u
    val whiteArgb = Color.White.copy(alpha = alpha).toArgb()
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
        fillArgb = SelBg.copy(alpha = alpha).toArgb(), strokeWidth = strokeW, strokeArgb = whiteArgb
    )
    // Target badge
    val accentArgb = SelAccent.copy(alpha = alpha).toArgb()
    addCircle(19.5f * u, 6.5f * u, 3.6f * u, 0, 1.3f * u, accentArgb)
    addCircle(19.5f * u, 6.5f * u, 1.15f * u, accentArgb)
}

/** The reference's More glyph: a circle outline with three dots inside. */
private fun VectorCmdBuilder.glyphMore219Body(w: Float, alpha: Float) {
    val u = w / 24f
    addCircle(12f * u, 12f * u, 9.5f * u, 0, 1.7f * u, SelName.copy(alpha = alpha).toArgb())
    val whiteArgb = Color.White.copy(alpha = alpha).toArgb()
    for (dx in listOf(7f, 12f, 17f)) {
        addCircle(dx * u, 12f * u, 1.55f * u, whiteArgb)
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
 *
 * Build 250 (UI migration stage 3): the header is drawn on ONE native
 * GL surface — positions from [ChromeGeometry.SelHeader] (the
 * build-219 layout, host-tested), the name through the stage-2 atlas
 * (ellipsized with the atlas paint, the Android rule), the Material
 * arrows as atlas rasters, the Layers/More glyphs ported
 * command-for-command, the pressed state applied as the reference's
 * 0.6 alpha. Tap targets stay Compose with the original
 * tags/descriptions; the More menu stays Compose, anchored to the
 * More button's box. Behaviour and pixels are build 219's.
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
    val sg = ChromeGeometry.SelHeader
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val backPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack)
    val deletePainter = rememberVectorPainter(Icons.Default.DeleteOutline)

    val nameTextH = atlas.textHeightPx(UiLabelStyle.SEL_NAME)
    val nameAscent = atlas.fontMetrics(UiLabelStyle.SEL_NAME).ascent

    // The four buttons' pressed states (the reference's :active
    // opacity), read here and applied in the GL draw.
    val backInteraction = remember { MutableInteractionSource() }
    val backPressed by backInteraction.collectIsPressedAsState()
    val layersInteraction = remember { MutableInteractionSource() }
    val layersPressed by layersInteraction.collectIsPressedAsState()
    val deleteInteraction = remember { MutableInteractionSource() }
    val deletePressed by deleteInteraction.collectIsPressedAsState()
    val moreInteraction = remember { MutableInteractionSource() }
    val morePressed by moreInteraction.collectIsPressedAsState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(SelBg)
            .testTag("selected_header_219")
    ) {
        // Full-width bar: the constraints give the surface width up
        // front, so the name's ellipsized form is resolved against
        // the same law the draw uses.
        val slotWidthPx = maxWidth.value * density
        fun resolveAll(): SelHeaderEntries {
            val backPx = (sg.BTN_GLYPH_DP * density).roundToInt()
            val deletePx = (sg.DELETE_GLYPH_DP * density).roundToInt()
            val back = atlas.resolveIcon("sel:back", backPx, backPx) { cv, x, y ->
                val b = rasterizePainterBitmap(backPainter, backPx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
            val delete = atlas.resolveIcon("sel:delete", deletePx, deletePx) { cv, x, y ->
                val b = rasterizePainterBitmap(deletePainter, deletePx, density)
                cv.drawBitmap(b, x, y, null)
                b.recycle()
            }
            val cap = sg.columnWidthPx(slotWidthPx, density)
            val shown = ChromeGeometry.ellipsize(layerName, cap) { s ->
                atlas.measure(s, UiLabelStyle.SEL_NAME)
            }
            val nameEntry = atlas.resolve(shown, UiLabelStyle.SEL_NAME)
            return SelHeaderEntries(nameEntry, shown, back, delete)
        }
        var entries = resolveAll()
        if (atlas.overflowed) {
            atlas.reset()
            entries = resolveAll()
        }
        val pad = UiLabelAtlas.PAD.toFloat()
        val columnWDp = (sg.columnWidthPx(slotWidthPx, density) / density).dp
        val columnHDp = (sg.columnHeightPx(nameTextH, density) / density).dp

        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(
                entries, backPressed, layersPressed, deletePressed, morePressed,
                atlas.generation
            ),
            buildCmds = { w, h, dd ->
                // Back (white arrow; pressed = 0.6 alpha).
                entries.back?.let { e ->
                    addText(
                        e.index,
                        sg.glyphLeftPx(sg.backLeftPx(dd), dd) - pad,
                        sg.glyphTopPx(h, sg.BTN_GLYPH_DP, dd) - pad,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = Color.White.copy(alpha = if (backPressed) 0.6f else 1f).toArgb()
                    )
                }
                // Name + its full-column-width underline.
                entries.name?.let { e ->
                    val nameLeft = sg.nameBoxLeftPx(dd)
                    val colTop = sg.columnTopPx(h, nameTextH, dd)
                    addText(
                        e.index, nameLeft + e.bearing - pad, colTop - nameAscent - e.base,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = SelName.toArgb()
                    )
                    addRect(
                        nameLeft, sg.underlineTopPx(h, nameTextH, dd),
                        sg.columnWidthPx(w, dd), sg.UNDERLINE_H_DP * dd,
                        SelUnderline.toArgb()
                    )
                }
                // Layers glyph.
                setTranslate(
                    sg.glyphLeftPx(sg.layersLeftPx(w, dd), dd),
                    sg.glyphTopPx(h, sg.BTN_GLYPH_DP, dd)
                )
                glyphLayers219Body(sg.BTN_GLYPH_DP * dd, if (layersPressed) 0.6f else 1f)
                setTranslate(0f, 0f)
                // Delete (name-coloured trash outline).
                entries.delete?.let { e ->
                    addText(
                        e.index,
                        sg.glyphLeftPx(sg.deleteLeftPx(w, dd), dd) - pad,
                        sg.glyphTopPx(h, sg.DELETE_GLYPH_DP, dd) - pad,
                        0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                        tintArgb = SelName.copy(alpha = if (deletePressed) 0.6f else 1f).toArgb()
                    )
                }
                // More glyph.
                setTranslate(
                    sg.glyphLeftPx(sg.moreLeftPx(w, dd), dd),
                    sg.glyphTopPx(h, sg.BTN_GLYPH_DP, dd)
                )
                glyphMore219Body(sg.BTN_GLYPH_DP * dd, if (morePressed) 0.6f else 1f)
                setTranslate(0f, 0f)
            }
        )

        // Tap targets: the build-219 Row layout, invisible.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelTouchBtn(
                tag = "sel_header_back", interaction = backInteraction,
                onClick = onBack, glyphSize = sg.BTN_GLYPH_DP.dp, description = "Back"
            )
            // The name wrap: flexes, caps at the reference's 180 dp;
            // tap = rename (the plain clickable keeps its ripple over
            // the surface, as in build 219).
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(columnWDp)
                        .height(columnHDp)
                        .clickable(onClick = onRename)
                        .testTag("sel_header_name")
                )
            }
            SelTouchBtn(
                tag = "sel_header_layers", interaction = layersInteraction,
                onClick = onLayers, glyphSize = sg.BTN_GLYPH_DP.dp
            )
            SelTouchBtn(
                tag = "sel_header_delete", interaction = deleteInteraction,
                onClick = onDelete, glyphSize = sg.DELETE_GLYPH_DP.dp, description = "Delete layer"
            )
            Box {
                SelTouchBtn(
                    tag = "sel_header_more", interaction = moreInteraction,
                    onClick = { menuOpen = true }, glyphSize = sg.BTN_GLYPH_DP.dp
                )
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

private data class SelHeaderEntries(
    val name: UiLabelAtlas.Entry?,
    val shownName: String,
    val back: UiLabelAtlas.Entry?,
    val delete: UiLabelAtlas.Entry?
)

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
    val onClick: () -> Unit = {},
    /**
     * Build 260: an optional DRAWN glyph (Sarowar's Split / Trim
     * bracket family — BracketGlyphs.kt) that replaces [icon] in the
     * page's raster: drawn once into the UI atlas like the multi-select
     * bar's drawn glyphs (only alpha is read; the blit tint colours
     * it). [icon] stays as the non-null fallback identity.
     */
    val glyphDraw: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null
)

/** One pager row: its height and whether its labels use the small (9.5 sp) size. */
data class PagerRow219(
    val heightDp: Int,
    val smallLabels: Boolean,
    val tools: List<PagerTool219>
)

/**
 * Rasterize a pager icon, pre-rotated by [rotateDeg] (Flip V reuses the
 * Flip glyph at 90° — the old Compose Modifier.rotate, applied to the
 * raster so the GL blit stays axis-aligned; the icon box is square, so
 * the rotation is exact).
 */
private fun rasterizePagerIcon(
    painter: androidx.compose.ui.graphics.vector.VectorPainter,
    sizePx: Int,
    density: Float,
    rotateDeg: Float
): android.graphics.Bitmap {
    val src = rasterizePainterBitmap(painter, sizePx, density)
    if (rotateDeg == 0f) return src
    val out = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out)
    val m = android.graphics.Matrix()
    m.postRotate(rotateDeg, sizePx / 2f, sizePx / 2f)
    cv.drawBitmap(src, m, null)
    src.recycle()
    return out
}

/** One pager button's resolved placement on its page (page px). */
private class PagerBtnLayout(
    val tool: PagerTool219,
    val leftPx: Float,
    val topPx: Float,
    val wPx: Float,
    val hPx: Float,
    val iconSizeDp: Int,
    val labelGapDp: Int,
    val smallLabel: Boolean
)

private data class PagerBtnEntries(
    val icon: UiLabelAtlas.Entry?,
    val labels: List<UiLabelAtlas.Entry?>,
    val badge: UiLabelAtlas.Entry?
)

/**
 * Build 251 (UI migration stage 4): one pager page drawn on ONE native
 * GL surface — buttons, icons, auto-fit labels and badges from
 * [PanelGeometry.Pager] (the build-220 fit law, host-tested, measured
 * with the atlas paint so the cut points agree with the draw). The
 * page-level surface is what lets a badge overflow its button's top
 * edge exactly as the Compose badge did. Tap targets stay Compose:
 * invisible boxes at the buttons' rects with the plain clickable (the
 * ripple draws over the surface, the stage-3 header precedent).
 */
@Composable
private fun PagerPageGl(buttons: List<PagerBtnLayout>) {
    val g = PanelGeometry.Pager
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()

    val painters = buttons.map { b -> key(b.tool.id) { rememberVectorPainter(b.tool.icon) } }

    // Per-button fit + lines, computed once per snapshot.
    class Prep(
        val fit: PanelGeometry.Pager.Fit,
        val style: UiLabelStyle,
        val effIconDp: Int,
        val effGapDp: Int,
        val lineTexts: List<String>,
        val lineBoxPx: Float
    )
    val preps = buttons.map { b ->
        val innerW = g.innerWidthPx(b.wPx, b.tool.active, density)
        val fit = g.fit(b.tool.label, if (b.smallLabel) g.FIT_BASE_SMALL_SP else g.FIT_BASE_SP, innerW) { s, sp ->
            atlas.measure(s, UiLabelStyle.pagerLabel(sp))
        }
        val style = UiLabelStyle.pagerLabel(fit.sp)
        val lineTexts = g.labelLines(b.tool.label, fit, innerW) { s, sp ->
            atlas.measure(s, UiLabelStyle.pagerLabel(sp))
        }
        Prep(
            fit = fit,
            style = style,
            effIconDp = if (!fit.oneLine) minOf(b.iconSizeDp, 16) else b.iconSizeDp,
            effGapDp = if (!fit.oneLine) 1 else b.labelGapDp,
            lineTexts = lineTexts,
            lineBoxPx = fit.sp * g.LINE_HEIGHT_FACTOR * density * fontScale
        )
    }

    fun resolveAll(): List<PagerBtnEntries> = buttons.mapIndexed { i, b ->
        val prep = preps[i]
        val iconPx = (prep.effIconDp * density).roundToInt()
        val rot = b.tool.iconRotate.roundToInt()
        val glyphDraw = b.tool.glyphDraw
        val icon = if (glyphDraw != null) {
            // Build 260: a drawn glyph (the Split / Trim brackets)
            // rasterizes from its DrawScope draw, keyed by tool id.
            atlas.resolveIcon("pager-drawn:${b.tool.id}:${prep.effIconDp}", iconPx, iconPx) { cv, x, y ->
                val bmp = rasterizeDrawBitmap(iconPx, iconPx, density) { glyphDraw() }
                cv.drawBitmap(bmp, x, y, null)
                bmp.recycle()
            }
        } else {
            atlas.resolveIcon("pager-icon:${b.tool.icon.name}:${prep.effIconDp}:$rot", iconPx, iconPx) { cv, x, y ->
                val bmp = rasterizePagerIcon(painters[i], iconPx, density, b.tool.iconRotate)
                cv.drawBitmap(bmp, x, y, null)
                bmp.recycle()
            }
        }
        val labels = prep.lineTexts.map { atlas.resolve(it, prep.style) }
        val badge = b.tool.badge?.let { atlas.resolve(it, UiLabelStyle.PAGER_BADGE) }
        PagerBtnEntries(icon, labels, badge)
    }
    var entries = resolveAll()
    if (atlas.overflowed) {
        atlas.reset()
        entries = resolveAll()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NativeVectorCanvas(
            modifier = Modifier.fillMaxSize(),
            clearArgb = 0,
            atlas = atlas,
            drawKey = listOf(entries, atlas.generation),
            buildCmds = { _, _, dd ->
                buttons.forEachIndexed { i, b ->
                    val prep = preps[i]
                    val e = entries[i]
                    val cx = b.leftPx + b.wPx / 2f
                    val cy = b.topPx + b.hPx / 2f
                    // The button face (+ the active outline, the stroke
                    // centred on the face edge — the dock rule).
                    if (b.tool.active) {
                        addRoundRect(
                            cx, cy,
                            b.wPx - g.BORDER_DP * dd, b.hPx - g.BORDER_DP * dd,
                            (g.BTN_CORNER_DP - g.BORDER_DP / 2f) * dd,
                            SelBtnActive.toArgb(), g.BORDER_DP * dd, SelActiveOutline.toArgb()
                        )
                    } else {
                        addRoundRect(cx, cy, b.wPx, b.hPx, g.BTN_CORNER_DP * dd, SelBtn.toArgb())
                    }
                    // The icon over the label lines, the stack centred.
                    val fm = atlas.fontMetrics(prep.style)
                    val stackTop = g.stackTopPx(
                        b.topPx, b.hPx, b.smallLabel,
                        prep.effIconDp.toFloat(), prep.effGapDp.toFloat(),
                        prep.lineTexts.size, prep.lineBoxPx, dd
                    )
                    val iconTint = (if (b.tool.enabled) Color.White else SelDisabled).toArgb()
                    e.icon?.let { ic ->
                        addText(
                            ic.index,
                            b.leftPx + (b.wPx - prep.effIconDp * dd) / 2f - pad,
                            stackTop - pad,
                            0f, 0f, ic.rectW.toFloat(), ic.rectH.toFloat(),
                            tintArgb = iconTint
                        )
                    }
                    val labelTint = (if (b.tool.enabled) SelLabel else SelDisabled).toArgb()
                    val textBlockTop = stackTop + (prep.effIconDp + prep.effGapDp) * dd
                    e.labels.forEachIndexed { li, le ->
                        if (le == null) return@forEachIndexed
                        val baseline = PanelGeometry.lineBaselinePx(
                            textBlockTop + li * prep.lineBoxPx, prep.lineBoxPx,
                            fm.ascent, fm.descent
                        )
                        addText(
                            le.index,
                            b.leftPx + (b.wPx - le.advance) / 2f + le.bearing - pad,
                            baseline - le.base,
                            0f, 0f, le.rectW.toFloat(), le.rectH.toFloat(),
                            tintArgb = labelTint
                        )
                    }
                    // The badge (overflows the top-right, page surface).
                    if (b.tool.badge != null) {
                        e.badge?.let { be ->
                            val badgeW = g.badgeWidthPx(be.advance, dd)
                            val badgeH = g.badgeHeightPx(atlas.textHeightPx(UiLabelStyle.PAGER_BADGE), dd)
                            val badgeLeft = g.badgeLeftPx(b.leftPx + b.wPx, badgeW, dd)
                            val badgeTop = g.badgeTopPx(b.topPx, dd)
                            addRoundRect(
                                badgeLeft + badgeW / 2f, badgeTop + badgeH / 2f,
                                badgeW, badgeH, g.BADGE_CORNER_DP * dd, SelBadge.toArgb()
                            )
                            val bfm = atlas.fontMetrics(UiLabelStyle.PAGER_BADGE)
                            addText(
                                be.index,
                                badgeLeft + g.BADGE_PAD_H_DP * dd + be.bearing - pad,
                                badgeTop + g.BADGE_PAD_V_DP * dd - bfm.ascent - be.base,
                                0f, 0f, be.rectW.toFloat(), be.rectH.toFloat(),
                                tintArgb = SelBadgeText.toArgb()
                            )
                        }
                    }
                }
            }
        )
        // Tap targets at the buttons' rects, invisible (ripple over GL).
        buttons.forEach { b ->
            Box(
                modifier = Modifier
                    .offset(x = (b.leftPx / density).dp, y = (b.topPx / density).dp)
                    .size((b.wPx / density).dp, (b.hPx / density).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = b.tool.enabled, onClick = b.tool.onClick)
                    .testTag("pager_tool_${b.tool.id}")
            )
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
    val density = LocalDensity.current.density
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
            // Build 251: each page is one GL surface (PagerPageGl) laid
            // out by PanelGeometry.Pager — the same rows, heights and
            // button rects the Compose Columns/Rows produced.
            val g = PanelGeometry.Pager
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pageW = maxWidth.value * density
                val pageH = maxHeight.value * density
                val buttons = mutableListOf<PagerBtnLayout>()
                if (page == 2) {
                    val rows = PagerGrid.rows(page3)
                    if (rows.isNotEmpty()) {
                        val rowH = g.page3RowHeightPx(pageH, rows.size, density)
                        rows.forEachIndexed { rowIdx, rowTools ->
                            val top = g.PAGE_PAD_TOP_DP * density + rowIdx * (rowH + g.GAP_DP * density)
                            val btnW = g.buttonWidthPx(pageW, 3, density)
                            rowTools.forEachIndexed { colIdx, tool ->
                                if (tool != null) {
                                    buttons += PagerBtnLayout(
                                        tool = tool,
                                        leftPx = g.buttonLeftPx(colIdx, btnW, density),
                                        topPx = top,
                                        wPx = btnW,
                                        hPx = rowH,
                                        iconSizeDp = 20,
                                        labelGapDp = 5,
                                        smallLabel = false
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val rows = if (page == 0) page1 else page2
                    val heightsPx = rows.map { it.heightDp * density }
                    rows.forEachIndexed { rowIdx, row ->
                        val btnW = g.buttonWidthPx(pageW, row.tools.size, density)
                        val top = g.rowTopPx(rowIdx, heightsPx, density)
                        row.tools.forEachIndexed { colIdx, tool ->
                            buttons += PagerBtnLayout(
                                tool = tool,
                                leftPx = g.buttonLeftPx(colIdx, btnW, density),
                                topPx = top,
                                wPx = btnW,
                                hPx = row.heightDp * density,
                                iconSizeDp = when {
                                    row.heightDp <= 36 -> if (page == 0) 16 else 15
                                    page == 1 && rowIdx == 2 -> 19
                                    else -> 20
                                },
                                labelGapDp = if (row.heightDp <= 36) 2 else 3,
                                smallLabel = row.smallLabels
                            )
                        }
                    }
                }
                PagerPageGl(buttons)
            }
        }
        // Pagination dots: 5×5, active = 14 wide in the name colour.
        // Build 251: drawn on a small GL surface (PanelGeometry.Pager);
        // the tap boxes keep the dots' own rects and tags.
        val g = PanelGeometry.Pager
        val currentPage = pagerState.currentPage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((g.DOTS_TOP_DP + g.DOT_H_DP).dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                drawKey = currentPage,
                buildCmds = { w, _, dd ->
                    for (i in 0 until 3) {
                        val dotW = g.dotWidthPx(i, currentPage, dd)
                        val left = g.dotLeftPx(i, currentPage, w, dd)
                        addRoundRect(
                            left + dotW / 2f, (g.DOTS_TOP_DP + g.DOT_H_DP / 2f) * dd,
                            dotW, g.DOT_H_DP * dd, g.DOT_CORNER_DP * dd,
                            (if (i == currentPage) SelName else SelDot).toArgb()
                        )
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = g.DOTS_TOP_DP.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 3) {
                    Box(
                        modifier = Modifier
                            .width(g.dotWidthPx(i, currentPage, 1f).dp)
                            .height(g.DOT_H_DP.dp)
                            .clickable { scope.launch { pagerState.animateScrollToPage(i, animationSpec = amChromeSpec()) } }
                            .testTag("pager_dot_$i")
                    )
                }
            }
        }
    }
}
