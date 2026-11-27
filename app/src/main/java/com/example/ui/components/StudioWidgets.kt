package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LibraryAssetEntity
import com.example.engine.gl.CharacterThumbnailRenderer
import com.example.engine.model.ImportInspectionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.theme.AxisBlue
import com.example.ui.theme.AxisGreen
import com.example.ui.theme.AxisRed
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun StudioSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "studio_search_bar"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(StudioCyan),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun StudioFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = ElectricIndigo
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accentColor.copy(alpha = 0.22f) else SurfaceCard,
        border = BorderStroke(1.dp, if (selected) accentColor else HairlineBorder)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    accent: Color = ElectricIndigo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else SurfaceCard,
        border = BorderStroke(1.dp, if (selected) accent else HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) StudioCyan else TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AxisSliderRow(
    axisLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    axisColor: Color = when (axisLabel) {
        "X" -> AxisRed
        "Y" -> AxisGreen
        "Z" -> AxisBlue
        else -> StudioCyan
    },
    unit: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(axisColor.copy(alpha = 0.2f))
                .border(1.dp, axisColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = axisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = axisColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = axisColor,
                activeTrackColor = axisColor,
                inactiveTrackColor = HairlineBorder
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format(Locale.US, "%.2f%s", value, unit),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = TextPrimary,
            modifier = Modifier.width(54.dp)
        )
    }
}

val StudioColorSwatches = listOf(
    0xFF6366F1L, 0xFF22D3EEL, 0xFFEF4444L, 0xFFF59E0BL,
    0xFF10B981L, 0xFF3B82F6L, 0xFFA855F7L, 0xFFEC4899L,
    0xFFD97706L, 0xFF926E3CL, 0xFFE2E8F0L, 0xFF334155L
)

@Composable
fun ColorSwatchGrid(
    selectedHex: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioColorSwatches.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { hex ->
                    val isSel = (hex and 0xFFFFFF) == (selectedHex and 0xFFFFFF)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) StudioCyan else HairlineBorder,
                                shape = CircleShape
                            )
                            .clickable { onSelect(hex) }
                    )
                }
            }
        }
    }
}

/**
 * Genuine 3D studio-lit preview thumbnail rendered directly from each character or asset's real 3D mesh.
 */
@Composable
fun Asset3DPreviewThumbnail(
    asset: LibraryAssetEntity,
    modifier: Modifier = Modifier
) {
    val renderedBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = asset.id,
        key2 = asset.primaryColorHex,
        key3 = asset.orientationTag
    ) {
        value = withContext(Dispatchers.Default) {
            CharacterThumbnailRenderer.renderAssetThumbnail(
                asset = asset,
                widthPx = 280,
                heightPx = 240
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A2030), Color(0xFF0B0E16))
                )
            )
            .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        renderedBitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = "${asset.name} 3D Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ImportInspectionDialog(
    report: ImportInspectionReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelNearBlack,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = if (report.supported) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (report.supported) SuccessGreen else KeyframeAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (report.supported) "3D Model Imported (${report.format})" else "Format Guidance (${report.format})",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(report.fileName, style = MaterialTheme.typography.titleSmall, color = StudioCyan)
                if (report.supported) {
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Meshes: ${report.meshCount}  •  Vertices: ${report.vertexCount}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            Text("Triangles: ${report.triangleCount}  •  Materials: ${report.materialCount}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            Text("Skeleton Bones: ${report.boneCount}  •  Textures: ${report.textureCount}", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
                            if (report.animationCount > 0) {
                                Text("Detected Animation Clips: ${report.animationCount}", style = MaterialTheme.typography.labelSmall, color = KeyframeAmber)
                            }
                        }
                    }
                }
                report.notes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
fun BlenderWorkflowGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelNearBlack,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = StudioCyan) },
        title = { Text("Blender to DraftRig 3D Workflow", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "DraftRig 3D natively imports .glb, .gltf, .obj, and .stl completely offline. Because .blend stores desktop C-memory structs, export from Blender as glTF 2.0 (.glb) for best fidelity:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                val steps = listOf(
                    "1. In Blender, select your character mesh + armature rig.",
                    "2. Open File -> Export -> glTF 2.0 (.glb / .gltf).",
                    "3. Set Format to 'glTF Binary (.glb)' to embed textures.",
                    "4. Under Data -> Mesh: enable UVs, Normals, Apply Modifiers.",
                    "5. Under Animation: enable Skinning & Include All Bone Influences.",
                    "6. Copy the .glb to your Android device & tap Import 3D Model."
                )
                Surface(
                    color = CharcoalCanvas,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.forEach { s ->
                            Text(s, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = StudioCyan)
            }
        }
    )
}
