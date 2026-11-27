package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LibraryAssetEntity
import com.example.engine.animation.AnimationPresetInfo
import com.example.engine.animation.AnimationPresets
import com.example.engine.model.CharacterCategory
import com.example.engine.model.OrientationFilter
import com.example.ui.components.Asset3DPreviewThumbnail
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioSearchBar
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CharacterLibraryScreen(
    assets: List<LibraryAssetEntity>,
    onAddAssetToScene: (LibraryAssetEntity) -> Unit,
    onTogglePin: (LibraryAssetEntity) -> Unit,
    onRenameAsset: (String, String) -> Unit,
    onDeleteAsset: (LibraryAssetEntity) -> Unit,
    onImportBlenderModel: () -> Unit,
    onOpenCreator: () -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CharacterCategory.ALL) }
    var selectedOrientation by remember { mutableStateOf(OrientationFilter.ALL) }
    var editingAsset by remember { mutableStateOf<LibraryAssetEntity?>(null) }
    var renameBuffer by remember { mutableStateOf("") }

    val filtered = remember(assets, search, selectedCategory, selectedOrientation) {
        assets.filter { asset ->
            val matchesQuery = search.isBlank() || asset.name.contains(search, ignoreCase = true) ||
                asset.characterCategory.contains(search, ignoreCase = true)
            val matchesCat = when (selectedCategory) {
                CharacterCategory.ALL -> true
                CharacterCategory.PINNED -> asset.isPinned
                CharacterCategory.LATEST -> true
                else -> asset.characterCategory == selectedCategory.name
            }
            val matchesOrient = selectedOrientation == OrientationFilter.ALL ||
                asset.orientationTag == selectedOrientation.name
            matchesQuery && matchesCat && matchesOrient
        }.let { list ->
            if (selectedCategory == CharacterCategory.LATEST) list.sortedByDescending { it.createdAt } else list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalCanvas)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioSearchBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Search 3D characters, rigs, Blender models...",
                modifier = Modifier.weight(1f),
                testTag = "character_search_input"
            )
            Surface(
                onClick = onImportBlenderModel,
                shape = RoundedCornerShape(10.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f)),
                modifier = Modifier.height(42.dp).testTag("char_lib_import_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "Import 3D", tint = StudioCyan, modifier = Modifier.size(18.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
            Surface(
                onClick = onOpenCreator,
                shape = RoundedCornerShape(10.dp),
                color = ElectricIndigo,
                modifier = Modifier.height(42.dp).testTag("char_lib_create_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create Custom Character", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Text("Create", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
        }

        // AutoDraft-style Category Filter Strip (All 13 categories)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CharacterCategory.entries) { cat ->
                StudioFilterChip(
                    label = cat.label,
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat }
                )
            }
        }

        // Orientation Filter Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(OrientationFilter.entries) { orient ->
                StudioFilterChip(
                    label = orient.label,
                    selected = selectedOrientation == orient,
                    onClick = { selectedOrientation = orient },
                    accentColor = StudioCyan
                )
            }
        }

        // Responsive 2-column AutoDraft-style Character Cards
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { asset ->
                CharacterLibraryCard(
                    asset = asset,
                    onAdd = { onAddAssetToScene(asset) },
                    onPin = { onTogglePin(asset) },
                    onEdit = {
                        editingAsset = asset
                        renameBuffer = asset.name
                    },
                    onDelete = if (!asset.isBuiltIn) { { onDeleteAsset(asset) } } else null
                )
            }
        }
    }

    editingAsset?.let { asset ->
        AlertDialog(
            onDismissRequest = { editingAsset = null },
            containerColor = PanelNearBlack,
            title = { Text("Character Properties", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameBuffer,
                        onValueChange = { renameBuffer = it },
                        label = { Text("Character Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Format: ${asset.sourceFormat} • Meshes: ${asset.meshCount} • Bones: ${asset.boneCount} • Vertices: ${asset.vertexCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCyan
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameAsset(asset.id, renameBuffer)
                        editingAsset = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingAsset = null }) { Text("Close", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun CharacterLibraryCard(
    asset: LibraryAssetEntity,
    onAdd: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (asset.isPinned) ElectricIndigo.copy(alpha = 0.6f) else HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .testTag("character_card_${asset.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(138.dp)) {
                Asset3DPreviewThumbnail(
                    asset = asset,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = PanelNearBlack.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        text = if (asset.boneCount > 0) "${asset.boneCount} Bones" else asset.sourceFormat,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = if (asset.boneCount > 0) StudioCyan else KeyframeAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(
                    onClick = onPin,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = if (asset.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin favorite",
                        tint = if (asset.isPinned) KeyframeAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val categoryLabel = remember(asset.characterCategory) {
                    CharacterCategory.entries.firstOrNull { it.name == asset.characterCategory }?.label
                        ?: asset.characterCategory.lowercase().replaceFirstChar { it.uppercase() }
                }
                val orientLabel = remember(asset.orientationTag) {
                    OrientationFilter.entries.firstOrNull { it.name == asset.orientationTag }?.label
                        ?: "360° Rig"
                }
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$categoryLabel • $orientLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onAdd,
                    shape = RoundedCornerShape(8.dp),
                    color = ElectricIndigo,
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Add 3D", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                }
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    color = PanelNearBlack,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit character", tint = TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }
                if (onDelete != null) {
                    Surface(
                        onClick = onDelete,
                        shape = RoundedCornerShape(8.dp),
                        color = PanelNearBlack,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete custom character", tint = DangerRed, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimationLibraryPanel(
    onApplyPreset: (AnimationPresetInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("All") }
    val categories = listOf("All", "Core", "Locomotion", "Posture", "Expressive", "Action")

    val presets = remember(search, selectedCat) {
        AnimationPresets.allPresets.filter { p ->
            (search.isBlank() || p.name.contains(search, true) || p.description.contains(search, true)) &&
                (selectedCat == "All" || p.category == selectedCat)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("3D Skeletal Animation Library", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        StudioSearchBar(
            query = search,
            onQueryChange = { search = it },
            placeholder = "Search Walk, Run, Wave, Dance, Jump...",
            testTag = "animation_search_input"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat ->
                StudioFilterChip(
                    label = cat,
                    selected = selectedCat == cat,
                    onClick = { selectedCat = cat },
                    accentColor = StudioCyan
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier.height(340.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                Surface(
                    onClick = { onApplyPreset(preset) },
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.testTag("anim_preset_${preset.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(preset.accentHex).copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Animation, contentDescription = null, tint = Color(preset.accentHex), modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${preset.defaultDurationFrames}F",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = StudioCyan
                            )
                        }
                        Text(preset.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(16.dp))
                            Text("Apply to Rig", style = MaterialTheme.typography.labelMedium, color = ElectricIndigo)
                        }
                    }
                }
            }
        }
    }
}
