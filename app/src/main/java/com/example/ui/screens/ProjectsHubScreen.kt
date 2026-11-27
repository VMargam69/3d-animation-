package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProjectEntity
import com.example.data.StarterTemplateInfo
import com.example.data.StudioRepository
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoDark
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsHubScreen(
    projects: List<ProjectEntity>,
    onQuickStart: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onCreateBlank: (String) -> Unit,
    onOpenProject: (String, Boolean) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onImportBlenderFile: () -> Unit,
    onOpenBlenderGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalCanvas),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick-Start Hero Banner
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.55f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    ElectricIndigoDark.copy(alpha = 0.38f),
                                    PanelNearBlack,
                                    Color(0xFF0E2530).copy(alpha = 0.45f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = StudioCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "PRO OFFLINE • IK RIG • OPENGL ES 3.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = onOpenBlenderGuide,
                                modifier = Modifier.size(36.dp).testTag("blender_guide_button")
                            ) {
                                Icon(Icons.Rounded.HelpOutline, contentDescription = "Blender Format Guide", tint = TextSecondary)
                            }
                        }

                        Text(
                            text = "Start Animating in 3D",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Pose with real-time 3D IK handles, stamp saved poses onto the timeline, switch 3D worlds, or import any Blender model.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onQuickStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("quick_start_scene_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quick-Start Scene", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showNewDialog = true },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("new_blank_project_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Blank", color = TextPrimary)
                            }
                        }

                        OutlinedButton(
                            onClick = onImportBlenderFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("hub_import_blender_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.50f))
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Import Blender 3D (.glb • .obj • .fbx • .blend • .fbm.zip • .stl)",
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Offline Starter Templates Carousel (with 3D Environments, Characters, Lights & Pre-built Timelines)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Offline Starter Templates",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Surface(
                        color = ElectricIndigo.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "3D BACKGROUNDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("starter_templates_carousel")
                ) {
                    items(StudioRepository.STARTER_TEMPLATES, key = { it.id }) { tpl ->
                        StarterTemplateCard(
                            template = tpl,
                            onClick = { onOpenTemplate(tpl.id) }
                        )
                    }
                }
            }
        }

        // Local Projects Section Header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Local Projects (${projects.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Saved on-device",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PanelNearBlack,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ViewInAr,
                            contentDescription = null,
                            tint = ElectricIndigo,
                            modifier = Modifier.size(38.dp)
                        )
                        Text("No local 3D scenes yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "Tap any Offline Starter Template above or Quick-Start Scene to launch a complete 3D stage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onOpen = { onOpenProject(project.id, false) },
                    onRecover = { onOpenProject(project.id, true) },
                    onRename = {
                        renameTarget = project
                        renameText = project.name
                    },
                    onDuplicate = { onDuplicateProject(project.id) },
                    onDelete = { onDeleteProject(project.id) }
                )
            }
        }
    }

    if (showNewDialog) {
        var newName by remember { mutableStateOf("Scene ${projects.size + 1}") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            containerColor = PanelNearBlack,
            title = { Text("New 3D Scene", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewDialog = false
                        onCreateBlank(newName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = PanelNearBlack,
            title = { Text("Rename Project", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameProject(target.id, renameText)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun StarterTemplateCard(
    template: StarterTemplateInfo,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .width(232.dp)
            .clickable(onClick = onClick)
            .testTag("starter_template_${template.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                Image(
                    painter = painterResource(id = template.drawableResId),
                    contentDescription = template.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, PanelNearBlack.copy(alpha = 0.65f))
                            )
                        )
                )
                Surface(
                    color = CharcoalCanvas.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text(
                        text = template.aspectLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = StudioCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = template.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectEntity,
    onOpen: () -> Unit,
    onRecover: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(project.updatedAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(project.updatedAt))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(project.accentHex).copy(alpha = 0.30f), CharcoalCanvas)
                        )
                    )
                    .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ViewInAr,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(30.dp).align(Alignment.Center)
                )
                Surface(
                    color = PanelNearBlack.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "${project.fps} FPS • ${project.totalFrames}F",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = StudioCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (project.hasRecoverySnapshot) {
                    Surface(
                        color = KeyframeAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "Autosave",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeyframeAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.objectCount} objs • ${project.keyframeCount} keys • $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Project options", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = SurfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Scene", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, tint = StudioCyan) },
                            onClick = { menuExpanded = false; onOpen() }
                        )
                        if (project.hasRecoverySnapshot) {
                            DropdownMenuItem(
                                text = { Text("Recover Autosave", color = KeyframeAmber) },
                                leadingIcon = { Icon(Icons.Rounded.Restore, null, tint = KeyframeAmber) },
                                onClick = { menuExpanded = false; onRecover() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = TextSecondary) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = TextSecondary) },
                            onClick = { menuExpanded = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = DangerRed) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = DangerRed) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
