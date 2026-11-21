package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.UiLabelAtlas
import com.example.engine.UiLabelStyle
import com.example.engine.rememberUiAtlas
import com.example.model.ChromeGeometry
import com.example.model.EditorGeometry
import com.example.model.PanelGeometry
import com.example.model.ProjectMeta
import com.example.model.SheetGeometry
import kotlin.math.roundToInt
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmBase
import com.example.ui.theme.AmCardSurface
import com.example.ui.theme.AmCornerCard
import com.example.ui.theme.AmDanger
import com.example.ui.theme.AmPanel
import com.example.ui.theme.AmPanelHigh
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.HeaderText
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dialog surface: the flat panel token (was a hard-coded #111111). */
private val DialogFill = AmPanel

/**
 * HOME (build 214 — UI ARCHITECTURE REDESIGN): the project list is the
 * app's home surface, presented full-screen over the editor: a brand
 * header (the clapperboard logo + Timeline Pro), one large NEW PROJECT hero card,
 * then the RECENT section — rich project cards (thumbnail block, name,
 * modified date, duration, ⋯ menu) in a 2-column grid. All actions are
 * the same callbacks the old dialog carried (open / new / rename /
 * delete / export / export-Alight-XML / import / fix missing media);
 * the per-card ⋯ menu gathers what used to be four loose icon buttons.
 * (No settings destination exists in the app, so the brand header
 * carries no settings icon — no dead buttons.)
 */
@Composable
fun ProjectsDialog(
    projects: List<ProjectMeta>,
    currentId: String?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    /** Save the project as a portable .ampro file (media included). */
    onExport: (ProjectMeta) -> Unit,
    /** Pick a .ampro file and add it as a new project. */
    onImport: () -> Unit,
    /** Files of the OPEN project that only the exporting phone had (build 111); 0 hides the button. */
    missingMediaCount: Int = 0,
    /** Open the replace-missing-media step for the open project. */
    onFixMissingMedia: () -> Unit = {}
) {
    var renaming by remember { mutableStateOf<ProjectMeta?>(null) }
    var deleting by remember { mutableStateOf<ProjectMeta?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    // A local display filter — no callback or wiring changes.
    var query by remember { mutableStateOf("") }
    val shownProjects = if (query.isBlank()) projects
        else projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val dateFmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmBase)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .testTag("projects_home")
        ) {
            // ── Brand header ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Build 220: the brand tile is Sarowar's clapperboard
                // logo (the same artwork as the launcher icon), not the
                // old lime tile with an "M" letter.
                Image(
                    painter = painterResource(id = R.drawable.timeline_pro_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.width(9.dp))
                // Build 253 (stage 6): the brand texts draw in GL
                // (the logo image and the close button stay Compose).
                Column {
                    GlLabelLine("Timeline Pro", UiLabelStyle.SHEET_TITLE, TextPrimary.toArgb())
                    GlLabelLine("Projects", UiLabelStyle.TEXT_10, AmTextDim.toArgb())
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).testTag("home_close_button")) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(19.dp))
                }
            }

            // ── Search ─────────────────────────────────────────────
            // Stays Compose (build 253): a text-input surface — the
            // field, its placeholder and the IME never migrate.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(AmPanelHigh)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search projects",
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(TextPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search projects", color = TextSecondary, fontSize = 12.sp)
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(10.dp))

            // ── New Project hero ───────────────────────────────────
            // Build 253 (stage 6): the hero draws on one GL surface
            // (build 217: LIME with dark content, the reference's
            // primary); a transparent clickable overlay keeps the
            // ripple + testTag.
            val heroDensity0 = LocalDensity.current.density
            val heroAtlas0 = rememberUiAtlas()
            val heroStackDp = (heroAtlas0.textHeightPx(UiLabelStyle.TEXT_14_BOLD) +
                heroAtlas0.textHeightPx(UiLabelStyle.SHEET_DESC)) / heroDensity0
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((maxOf(36f, heroStackDp) + 24f).dp)
            ) {
                val heroPainter = rememberVectorPainter(Icons.Filled.Add)
                val heroDensity = LocalDensity.current.density
                val heroAtlas = rememberUiAtlas()
                val heroPad = UiLabelAtlas.PAD.toFloat()
                val heroTitleH = heroAtlas.textHeightPx(UiLabelStyle.TEXT_14_BOLD)
                val heroSubH = heroAtlas.textHeightPx(UiLabelStyle.SHEET_DESC)
                fun resolveHero(): Triple<UiLabelAtlas.Entry?, UiLabelAtlas.Entry?, UiLabelAtlas.Entry?> {
                    val t = heroAtlas.resolve("New Project", UiLabelStyle.TEXT_14_BOLD)
                    val s = heroAtlas.resolve("Start a fresh edit", UiLabelStyle.SHEET_DESC)
                    val i = heroAtlas.iconEntry(
                        "hero:add", heroPainter, (20f * heroDensity).roundToInt(), heroDensity
                    )
                    return Triple(t, s, i)
                }
                var heroEntries = resolveHero()
                if (heroAtlas.overflowed) {
                    heroAtlas.reset()
                    heroEntries = resolveHero()
                }
                val (heroTitleE, heroSubE, heroIconE) = heroEntries
                NativeVectorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearArgb = 0,
                    atlas = heroAtlas,
                    drawKey = listOf(heroTitleE, heroSubE, heroIconE, heroAtlas.generation),
                    buildCmds = { w, h, dd ->
                        addRoundRect(
                            w / 2f, h / 2f, w, h, AmCornerCard.value * dd,
                            com.example.ui.theme.RefLime.toArgb()
                        )
                        val circleCx = 32f * dd
                        val circleCy = h / 2f
                        addCircle(circleCx, circleCy, 18f * dd, com.example.ui.theme.AmInk.toArgb())
                        heroIconE?.let { e ->
                            addText(
                                e.index,
                                circleCx - e.rectW / 2f, circleCy - e.rectH / 2f,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = com.example.ui.theme.RefLime.toArgb()
                            )
                        }
                        val textX = 60f * dd
                        val stackH = heroTitleH + heroSubH
                        var top = (h - stackH) / 2f
                        heroTitleE?.let { e ->
                            val style = UiLabelStyle.TEXT_14_BOLD
                            val fm = heroAtlas.fontMetrics(style)
                            val baseline = PanelGeometry.lineBaselinePx(top, heroTitleH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(textX, baseline, e.bearing, e.base, heroPad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = com.example.ui.theme.RefLimeInk.toArgb()
                            )
                        }
                        top += heroTitleH
                        heroSubE?.let { e ->
                            val style = UiLabelStyle.SHEET_DESC
                            val fm = heroAtlas.fontMetrics(style)
                            val baseline = PanelGeometry.lineBaselinePx(top, heroSubH, fm.ascent, fm.descent)
                            val (dstX, dstY) = EditorGeometry.textDst(textX, baseline, e.bearing, e.base, heroPad)
                            addText(
                                e.index, dstX, dstY,
                                0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                                tintArgb = com.example.ui.theme.RefLimeInk.copy(alpha = 0.65f).toArgb()
                            )
                        }
                    }
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(AmCornerCard))
                        .clickable { onNew() }
                        .testTag("home_new_project")
                )
            }
            Spacer(Modifier.height(8.dp))

            // ── Secondary actions ──────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DialogSecondaryButton(text = "Import", onClick = onImport)
                if (missingMediaCount > 0) {
                    DialogSecondaryButton(
                        text = "Missing media ($missingMediaCount)",
                        onClick = onFixMissingMedia,
                        contentColor = Color(0xFFFFB74D)
                    )
                }
            }

            // ── Recent ─────────────────────────────────────────────
            PanelSectionLabel("Recent")

            if (projects.isEmpty()) {
                GlLabelLine(
                    "No projects yet. Start one with New Project above.",
                    UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                    Modifier.padding(vertical = 6.dp)
                )
            } else if (shownProjects.isEmpty()) {
                GlLabelLine(
                    "No projects match your search.",
                    UiLabelStyle.TEXT_11, TextSecondary.toArgb(),
                    Modifier.padding(vertical = 6.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shownProjects, key = { it.id }) { p ->
                        val isCurrent = p.id == currentId
                        // Build 253 (stage 6): the card draws on one GL
                        // surface (fill + ring, the thumbnail block, the
                        // initial, the OPEN + duration chips, name +
                        // meta). A transparent clickable overlay keeps
                        // the ripple + card tag; the ⋯ IconButton and
                        // its DropdownMenu stay Compose overlays (menus
                        // are platform popups).
                        ProjectCardGl(
                            name = p.name,
                            meta = "${dateFmt.format(Date(p.updatedAt))} · ${p.layerCount} layers",
                            duration = formatDuration(p.durationMs),
                            isCurrent = isCurrent,
                            onOpen = { onOpen(p.id) },
                            cardTag = "project_card_${p.id}"
                        ) {
                            Box {
                                IconButton(
                                    onClick = { menuFor = p.id },
                                    modifier = Modifier.size(30.dp).testTag("project_menu_${p.id}")
                                ) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Project actions", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = menuFor == p.id,
                                    onDismissRequest = { menuFor = null },
                                    modifier = Modifier.background(AmPanelHigh)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open", color = TextPrimary) },
                                        onClick = { menuFor = null; onOpen(p.id) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                                        onClick = { menuFor = null; renaming = p }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share project file", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                                        onClick = { menuFor = null; onExport(p) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = AmDanger) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = AmDanger, modifier = Modifier.size(18.dp)) },
                                        onClick = { menuFor = null; deleting = p }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AppAlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = DialogFill,
            shape = RoundedCornerShape(AmCornerCard),
            // Build 253: the title draws in GL; the field stays Compose.
            title = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { 32.sp.toDp() })
                ) {
                    GlTextLine(
                        "Rename", UiLabelStyle.DLG_TITLE, HeaderText.toArgb(),
                        Modifier.fillMaxSize()
                    )
                }
            },
            text = {
                PillTextField(value = text, onValueChange = { text = it.take(60) })
            },
            confirmButton = {
                DialogPrimaryButton(text = "OK", onClick = {
                    if (text.isNotBlank()) onRename(p.id, text)
                    renaming = null
                })
            },
            dismissButton = { DialogSecondaryButton(text = "Cancel", onClick = { renaming = null }) }
        )
    }

    deleting?.let { p ->
        AppAlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = DialogFill,
            shape = RoundedCornerShape(AmCornerCard),
            title = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { 32.sp.toDp() })
                ) {
                    GlTextLine(
                        "Delete project?", UiLabelStyle.DLG_TITLE, HeaderText.toArgb(),
                        Modifier.fillMaxSize()
                    )
                }
            },
            text = {
                GlTextBlock(
                    "\"${p.name}\" will be removed. This cannot be undone.",
                    UiLabelStyle.DLG_13, TextSecondary.toArgb(),
                    Modifier.fillMaxWidth(),
                    linePitchSp = 18f,
                    align = androidx.compose.ui.text.style.TextAlign.Start
                )
            },
            confirmButton = {
                DialogDangerButton(text = "Delete", onClick = {
                    onDelete(p.id)
                    deleting = null
                })
            },
            dismissButton = { DialogSecondaryButton(text = "Cancel", onClick = { deleting = null }) }
        )
    }
}

/**
 * Build 253 (stage 6): one project card on a single GL surface.
 * Thumbnail block (no stored thumbnail exists: the project's initial
 * on the raised surface, with the duration badge and the OPEN chip),
 * name + meta under it; the ⋯ menu is the caller's Compose overlay at
 * the body's top end (its build-252 spot: 2dp from the end, 6dp
 * below the thumbnail block).
 */
@Composable
private fun ProjectCardGl(
    name: String,
    meta: String,
    duration: String,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    cardTag: String,
    menu: @Composable () -> Unit
) {
    val g = SheetGeometry.ProjectCard
    val density = LocalDensity.current.density
    val atlas = rememberUiAtlas()
    val pad = UiLabelAtlas.PAD.toFloat()
    val nameStyle = if (isCurrent) UiLabelStyle.SHEET_ITEM_BOLD else UiLabelStyle.SHEET_ITEM_SEMI
    val nameH = atlas.textHeightPx(nameStyle)
    val metaH = atlas.textHeightPx(UiLabelStyle.TEXT_95_MED)
    val initialH = atlas.textHeightPx(UiLabelStyle.TEXT_22_XBOLD)
    val openH = atlas.textHeightPx(UiLabelStyle.CHIP_8_XBOLD)
    val durH = atlas.textHeightPx(UiLabelStyle.TEXT_9_SEMI)
    val initial = name.trim().firstOrNull()?.uppercase() ?: "M"

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wPx = maxWidth.value * density
        val textMaxW = wPx - 42f * density
        val nameShown = remember(name, textMaxW, nameStyle) {
            ChromeGeometry.ellipsize(name, textMaxW) { atlas.measure(it, nameStyle) }
        }
        val metaShown = remember(meta, textMaxW) {
            ChromeGeometry.ellipsize(meta, textMaxW) { atlas.measure(it, UiLabelStyle.TEXT_95_MED) }
        }
        fun resolveCard(): List<UiLabelAtlas.Entry?> {
            val i = atlas.resolve(initial, UiLabelStyle.TEXT_22_XBOLD)
            val n = atlas.resolve(nameShown, nameStyle)
            val m = atlas.resolve(metaShown, UiLabelStyle.TEXT_95_MED)
            val o = if (isCurrent) atlas.resolve("OPEN", UiLabelStyle.CHIP_8_XBOLD) else null
            val d = atlas.resolve(duration, UiLabelStyle.TEXT_9_SEMI)
            return listOf(i, n, m, o, d)
        }
        var entries = resolveCard()
        if (atlas.overflowed) {
            atlas.reset()
            entries = resolveCard()
        }
        val (initialE, nameE, metaE, openE, durE) = entries
        val cardH = g.cardHeightPx(nameH, metaH, density)

        Box(
            Modifier
                .fillMaxWidth()
                .height((cardH / density).dp)
        ) {
            NativeVectorCanvas(
                modifier = Modifier.fillMaxSize(),
                clearArgb = 0,
                atlas = atlas,
                drawKey = listOf(entries, isCurrent, atlas.generation),
                buildCmds = { w, h, dd ->
                    val corner = AmCornerCard.value * dd
                    addRoundRect(w / 2f, h / 2f, w, h, corner, AmPanelHigh.toArgb())
                    addRoundRect(
                        w / 2f, h / 2f, w - 1f * dd, h - 1f * dd, corner - 0.5f * dd,
                        0, 1f * dd, com.example.ui.theme.AmHairline.toArgb()
                    )
                    // Thumbnail block: top corners rounded, bottom square
                    // (a round-rect cap unioned with a plain rect).
                    val thumbH = g.THUMB_H_DP * dd
                    addRoundRect(
                        w / 2f, corner, w, corner * 2f, corner,
                        com.example.ui.theme.AmPanelElevated.toArgb()
                    )
                    addRect(0f, corner, w, thumbH - corner, com.example.ui.theme.AmPanelElevated.toArgb())
                    initialE?.let { e ->
                        val style = UiLabelStyle.TEXT_22_XBOLD
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(0f, thumbH, initialH)
                        val baseline = PanelGeometry.lineBaselinePx(top, initialH, fm.ascent, fm.descent)
                        val originX = EditorGeometry.centredOriginPx(w / 2f, e.advance)
                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = AmTextDim.toArgb()
                        )
                    }
                    openE?.let { e ->
                        val chipW = e.advance + 12f * dd
                        val chipH = openH + 3f * dd
                        val cx = 6f * dd + chipW / 2f
                        val cy = 6f * dd + chipH / 2f
                        addRoundRect(cx, cy, chipW, chipH, chipH / 2f, AmAccent.toArgb())
                        val style = UiLabelStyle.CHIP_8_XBOLD
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(cy - chipH / 2f, chipH, openH)
                        val baseline = PanelGeometry.lineBaselinePx(top, openH, fm.ascent, fm.descent)
                        val originX = EditorGeometry.centredOriginPx(cx, e.advance)
                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = com.example.ui.theme.AmInk.toArgb()
                        )
                    }
                    durE?.let { e ->
                        val chipW = e.advance + 12f * dd
                        val chipH = durH + 3f * dd
                        val cx = w - 6f * dd - chipW / 2f
                        val cy = thumbH - 6f * dd - chipH / 2f
                        addRoundRect(cx, cy, chipW, chipH, chipH / 2f, 0x8C000000.toInt())
                        val style = UiLabelStyle.TEXT_9_SEMI
                        val fm = atlas.fontMetrics(style)
                        val top = EditorGeometry.centredTextTopPx(cy - chipH / 2f, chipH, durH)
                        val baseline = PanelGeometry.lineBaselinePx(top, durH, fm.ascent, fm.descent)
                        val originX = EditorGeometry.centredOriginPx(cx, e.advance)
                        val (dstX, dstY) = EditorGeometry.textDst(originX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = 0xFFFFFFFF.toInt()
                        )
                    }
                    val textX = 10f * dd
                    var top = thumbH + g.PAD_TOP_DP * dd
                    nameE?.let { e ->
                        val fm = atlas.fontMetrics(nameStyle)
                        val baseline = PanelGeometry.lineBaselinePx(top, nameH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(textX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = TextPrimary.toArgb()
                        )
                    }
                    top += nameH
                    metaE?.let { e ->
                        val style = UiLabelStyle.TEXT_95_MED
                        val fm = atlas.fontMetrics(style)
                        val baseline = PanelGeometry.lineBaselinePx(top, metaH, fm.ascent, fm.descent)
                        val (dstX, dstY) = EditorGeometry.textDst(textX, baseline, e.bearing, e.base, pad)
                        addText(
                            e.index, dstX, dstY,
                            0f, 0f, e.rectW.toFloat(), e.rectH.toFloat(),
                            tintArgb = TextSecondary.toArgb()
                        )
                    }
                }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(AmCornerCard))
                    .clickable { onOpen() }
                    .testTag(cardTag)
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = (g.THUMB_H_DP + g.PAD_TOP_DP).dp)
            ) {
                menu()
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    // Same rule as the ruler clock: from one hour on, "H:MM:SS" instead of minutes past 59.
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
