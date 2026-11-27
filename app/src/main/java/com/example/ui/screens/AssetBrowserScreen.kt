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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LibraryAssetEntity
import com.example.engine.model.AssetCategory
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
fun AssetBrowserScreen(
    assets: List<LibraryAssetEntity>,
    onAddAssetToScene: (LibraryAssetEntity) -> Unit,
    onTogglePin: (LibraryAssetEntity) -> Unit,
    onDeleteAsset: (LibraryAssetEntity) -> Unit,
    onImportBlenderFile: () -> Unit,
    onOpenBlenderGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AssetCategory.ALL) }

    val filtered = remember(assets, query, selectedType) {
        assets.filter { a ->
            val matchQ = query.isBlank() || a.name.contains(query, true) || a.sourceFormat.contains(query, true)
            val matchT = selectedType == AssetCategory.ALL || a.assetCategory == selectedType.name
            matchQ && matchT
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
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search offline 3D assets, props, rigs...",
                modifier = Modifier.weight(1f),
                testTag = "asset_browser_search"
            )
            Surface(
                onClick = onImportBlenderFile,
                shape = RoundedCornerShape(10.dp),
                color = ElectricIndigo,
                modifier = Modifier.height(42.dp).testTag("asset_browser_import_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "Import Asset", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
            Surface(
                onClick = onOpenBlenderGuide,
                shape = RoundedCornerShape(10.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.HelpOutline, contentDescription = "Blender Guide", tint = StudioCyan, modifier = Modifier.size(18.dp))
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(AssetCategory.entries) { cat ->
                StudioFilterChip(
                    label = cat.label,
                    selected = selectedType == cat,
                    onClick = { selectedType = cat },
                    accentColor = ElectricIndigo
                )
            }
        }

        Text(
            text = "${filtered.size} offline 3D assets • Reusable across all local projects",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { asset ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddAssetToScene(asset) }
                        .testTag("asset_card_${asset.id}")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(114.dp)) {
                            Asset3DPreviewThumbnail(
                                asset = asset,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = PanelNearBlack.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                            ) {
                                Text(
                                    text = asset.sourceFormat,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = StudioCyan,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = { onTogglePin(asset) },
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (asset.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = "Pin asset",
                                    tint = if (asset.isPinned) KeyframeAmber else TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Text(
                            text = asset.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${asset.vertexCount}v • ${asset.boneCount}b",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (!asset.isBuiltIn) {
                                IconButton(onClick = { onDeleteAsset(asset) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete asset", tint = DangerRed, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
