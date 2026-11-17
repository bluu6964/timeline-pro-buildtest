package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ProjectMeta
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
 * header (the red M mark + MaxCut), one large NEW PROJECT hero card,
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
    /** Save the project as an Alight Motion XML file (build 197; media referenced, not included). */
    onExportAlightXml: (ProjectMeta) -> Unit = {},
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
                .padding(horizontal = 16.dp)
                .testTag("projects_home")
        ) {
            // ── Brand header ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AmAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = com.example.ui.theme.AmInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) // build 216: ink on the lime brand tile
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MaxCut", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Projects", color = AmTextDim, fontSize = 12.sp, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).testTag("home_close_button")) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }

            // ── Search ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(AmPanelHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search projects",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(TextPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search projects", color = TextSecondary, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(14.dp))

            // ── New Project hero ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AmCornerCard))
                    .background(Color(0xFF18181B)) // build 216: the New Project hero is BLACK with lime + white
                    .clickable { onNew() }
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .testTag("home_new_project"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AmAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = com.example.ui.theme.AmInk, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("New Project", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Start a fresh edit", color = Color.White.copy(alpha = 0.75f), fontSize = 12.5.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))

            // ── Secondary actions ──────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                Text(
                    "No projects yet. Start one with New Project above.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else if (shownProjects.isEmpty()) {
                Text(
                    "No projects match your search.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shownProjects, key = { it.id }) { p ->
                        val isCurrent = p.id == currentId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AmCornerCard))
                                .background(AmPanelHigh) // build 216: pill-surface cards on the white home
                                .border(1.dp, com.example.ui.theme.AmHairline, RoundedCornerShape(AmCornerCard))
                                .clickable { onOpen(p.id) }
                                .testTag("project_card_${p.id}")
                        ) {
                            // Thumbnail block (no stored thumbnail exists:
                            // the project's initial on the raised surface,
                            // with the duration badge and the OPEN chip).
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(74.dp)
                                    .background(com.example.ui.theme.AmPanelElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.name.trim().firstOrNull()?.uppercase() ?: "M",
                                    color = AmTextDim,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .clip(CircleShape)
                                            .background(AmAccent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text("OPEN", color = com.example.ui.theme.AmInk, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(formatDuration(p.durationMs), color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 8.dp, bottom = 10.dp, end = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        color = TextPrimary, // build 216: the current project reads ink + bold, not lime text
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${dateFmt.format(Date(p.updatedAt))} · ${p.layerCount} layers",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { menuFor = p.id },
                                        modifier = Modifier.size(36.dp).testTag("project_menu_${p.id}")
                                    ) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Project actions", tint = TextSecondary, modifier = Modifier.size(20.dp))
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
                                            text = { Text("Export Alight XML", color = TextPrimary) },
                                            leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                                            onClick = { menuFor = null; onExportAlightXml(p) }
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
    }

    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = DialogFill,
            shape = RoundedCornerShape(AmCornerCard),
            title = { Text("Rename", color = HeaderText) },
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
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = DialogFill,
            shape = RoundedCornerShape(AmCornerCard),
            title = { Text("Delete project?", color = HeaderText) },
            text = { Text("\"${p.name}\" will be removed. This cannot be undone.", color = TextSecondary) },
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

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    // Same rule as the ruler clock: from one hour on, "H:MM:SS" instead of minutes past 59.
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
