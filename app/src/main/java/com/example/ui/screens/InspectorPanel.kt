package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.engine.model.CameraData
import com.example.engine.model.EditSubMode
import com.example.engine.model.EditorMode
import com.example.engine.model.LightData
import com.example.engine.model.LightType
import com.example.engine.model.MaterialData
import com.example.engine.model.NodeType
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.Transform3D
import com.example.engine.model.Vec3
import com.example.ui.components.AxisSliderRow
import com.example.ui.components.ColorSwatchGrid
import com.example.ui.components.StudioFilterChip
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
import com.example.viewmodel.InspectorTab
import com.example.viewmodel.StudioUiState

@Composable
fun StudioInspectorPanel(
    state: StudioUiState,
    onSelectTab: (InspectorTab) -> Unit,
    onClose: () -> Unit,
    onSelectNode: (String) -> Unit,
    onSetParent: (String, String?) -> Unit,
    onFocusSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUpdateTransform: (Transform3D) -> Unit,
    onAddPrimitive: (PrimitiveKind) -> Unit,
    onSetEditorMode: (EditorMode) -> Unit,
    onSetEditSubMode: (EditSubMode) -> Unit,
    onCycleEditElement: (Int) -> Unit,
    onEditSubDelta: (Vec3) -> Unit,
    onExtrudeFace: () -> Unit,
    onInsetFace: () -> Unit,
    onBevelMesh: () -> Unit,
    onSubdivideMesh: () -> Unit,
    onUpdateMaterial: (MaterialData) -> Unit,
    onPickTextureImage: () -> Unit,
    onAddLight: (LightType) -> Unit,
    onUpdateLight: (LightData) -> Unit,
    onAddCamera: () -> Unit,
    onUpdateCamera: (CameraData) -> Unit,
    onAttachHumanoidRig: () -> Unit,
    onSelectBone: (String) -> Unit,
    onAddChildBone: (String) -> Unit,
    onRenameBone: (String) -> Unit,
    onDeleteBone: () -> Unit,
    onUpdateBonePose: (Vec3, Float?) -> Unit,
    onAutoBindWeights: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedNode = state.scene.nodes.firstOrNull { it.id == state.selectedNodeId }

    Surface(
        color = PanelNearBlack,
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
        modifier = modifier
            .fillMaxHeight()
            .testTag("studio_inspector_panel")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCanvas)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedNode?.name ?: "Scene Inspector",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Inspector", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // Inspector Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(InspectorTab.entries) { tab ->
                    StudioFilterChip(
                        label = tab.label,
                        selected = state.inspectorTab == tab,
                        onClick = { onSelectTab(tab) },
                        accentColor = ElectricIndigo
                    )
                }
            }

            HorizontalDivider(color = HairlineBorder)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (state.inspectorTab) {
                    InspectorTab.OUTLINER -> OutlinerInspectorContent(
                        scene = state.scene,
                        selectedNode = selectedNode,
                        onSelectNode = onSelectNode,
                        onSetParent = onSetParent,
                        onFocusSelected = onFocusSelected,
                        onDuplicateSelected = onDuplicateSelected,
                        onDeleteSelected = onDeleteSelected
                    )
                    InspectorTab.TRANSFORM -> TransformInspectorContent(
                        node = selectedNode,
                        onUpdateTransform = onUpdateTransform,
                        onDuplicate = onDuplicateSelected,
                        onDelete = onDeleteSelected
                    )
                    InspectorTab.MODEL -> ModelingInspectorContent(
                        node = selectedNode,
                        editorMode = state.editorMode,
                        editSubMode = state.editSubMode,
                        selectedElementIndex = state.selectedElementIndex,
                        onAddPrimitive = onAddPrimitive,
                        onSetEditorMode = onSetEditorMode,
                        onSetEditSubMode = onSetEditSubMode,
                        onCycleEditElement = onCycleEditElement,
                        onEditSubDelta = onEditSubDelta,
                        onExtrudeFace = onExtrudeFace,
                        onInsetFace = onInsetFace,
                        onBevelMesh = onBevelMesh,
                        onSubdivideMesh = onSubdivideMesh
                    )
                    InspectorTab.MATERIAL -> MaterialInspectorContent(
                        node = selectedNode,
                        onUpdateMaterial = onUpdateMaterial,
                        onPickTextureImage = onPickTextureImage
                    )
                    InspectorTab.LIGHT_CAM -> LightCameraInspectorContent(
                        node = selectedNode,
                        onAddLight = onAddLight,
                        onUpdateLight = onUpdateLight,
                        onAddCamera = onAddCamera,
                        onUpdateCamera = onUpdateCamera
                    )
                    InspectorTab.RIG -> RiggingInspectorContent(
                        node = selectedNode,
                        selectedBoneId = state.selectedBoneId,
                        onAttachHumanoidRig = onAttachHumanoidRig,
                        onSelectBone = onSelectBone,
                        onAddChildBone = onAddChildBone,
                        onRenameBone = onRenameBone,
                        onDeleteBone = onDeleteBone,
                        onUpdateBonePose = onUpdateBonePose,
                        onAutoBindWeights = onAutoBindWeights
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlinerInspectorContent(
    scene: SceneDocument,
    selectedNode: SceneNode?,
    onSelectNode: (String) -> Unit,
    onSetParent: (String, String?) -> Unit,
    onFocusSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Text("Scene Hierarchy (${scene.nodes.size})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    scene.nodes.forEach { node ->
        val isSel = node.id == selectedNode?.id
        Surface(
            color = if (isSel) ElectricIndigo.copy(alpha = 0.22f) else SurfaceCard,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (isSel) ElectricIndigo else HairlineBorder),
            modifier = Modifier.fillMaxWidth().clickable { onSelectNode(node.id) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (node.nodeType) {
                        NodeType.CHARACTER -> Icons.Rounded.AccessibilityNew
                        NodeType.LIGHT -> Icons.Rounded.Lightbulb
                        NodeType.CAMERA -> Icons.Rounded.Videocam
                        NodeType.MESH -> Icons.Rounded.ViewInAr
                    },
                    contentDescription = null,
                    tint = if (isSel) StudioCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                    val info = buildString {
                        append(node.nodeType.name)
                        node.mesh?.let { append(" • ${it.vertexCount}v/${it.triangleCount}t") }
                        node.skeleton?.let { append(" • ${it.bones.size}b") }
                    }
                    Text(info, style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = TextMuted)
                }
            }
        }
    }

    if (selectedNode != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onFocusSelected, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HairlineBorder)) {
                Icon(Icons.Rounded.CenterFocusStrong, null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Focus", color = TextPrimary)
            }
            OutlinedButton(onClick = onDuplicateSelected, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HairlineBorder)) {
                Icon(Icons.Rounded.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            OutlinedButton(onClick = onDeleteSelected, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = DangerRed, modifier = Modifier.size(16.dp))
            }
        }
        Text("Parent Object", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                StudioFilterChip("None (Root)", selectedNode.parentId == null, { onSetParent(selectedNode.id, null) })
            }
            items(scene.nodes.filter { it.id != selectedNode.id }) { cand ->
                StudioFilterChip(cand.name, selectedNode.parentId == cand.id, { onSetParent(selectedNode.id, cand.id) }, accentColor = StudioCyan)
            }
        }
    }
}

@Composable
private fun TransformInspectorContent(
    node: SceneNode?,
    onUpdateTransform: (Transform3D) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    if (node == null) {
        Text("Tap any 3D object in the viewport to edit its X, Y, Z transform.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        return
    }
    val tr = node.transform
    Text("Position (Meters)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    AxisSliderRow("X", tr.position.x, -8f..8f, { onUpdateTransform(tr.copy(position = tr.position.copy(x = it))) })
    AxisSliderRow("Y", tr.position.y, -4f..8f, { onUpdateTransform(tr.copy(position = tr.position.copy(y = it))) })
    AxisSliderRow("Z", tr.position.z, -8f..8f, { onUpdateTransform(tr.copy(position = tr.position.copy(z = it))) })

    Text("Rotation (Degrees)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    AxisSliderRow("X", tr.rotation.x, -180f..180f, { onUpdateTransform(tr.copy(rotation = tr.rotation.copy(x = it))) }, unit = "°")
    AxisSliderRow("Y", tr.rotation.y, -180f..180f, { onUpdateTransform(tr.copy(rotation = tr.rotation.copy(y = it))) }, unit = "°")
    AxisSliderRow("Z", tr.rotation.z, -180f..180f, { onUpdateTransform(tr.copy(rotation = tr.rotation.copy(z = it))) }, unit = "°")

    Text("Scale", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    AxisSliderRow("X", tr.scale.x, 0.1f..4f, { onUpdateTransform(tr.copy(scale = tr.scale.copy(x = it))) })
    AxisSliderRow("Y", tr.scale.y, 0.1f..4f, { onUpdateTransform(tr.copy(scale = tr.scale.copy(y = it))) })
    AxisSliderRow("Z", tr.scale.z, 0.1f..4f, { onUpdateTransform(tr.copy(scale = tr.scale.copy(z = it))) })

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onUpdateTransform(Transform3D()) },
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, HairlineBorder)
        ) { Text("Reset", color = StudioCyan) }
        OutlinedButton(onClick = onDuplicate, border = BorderStroke(1.dp, HairlineBorder)) {
            Icon(Icons.Rounded.ContentCopy, "Duplicate", tint = TextPrimary, modifier = Modifier.size(16.dp))
        }
        OutlinedButton(onClick = onDelete, border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))) {
            Icon(Icons.Rounded.DeleteOutline, "Delete", tint = DangerRed, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ModelingInspectorContent(
    node: SceneNode?,
    editorMode: EditorMode,
    editSubMode: EditSubMode,
    selectedElementIndex: Int,
    onAddPrimitive: (PrimitiveKind) -> Unit,
    onSetEditorMode: (EditorMode) -> Unit,
    onSetEditSubMode: (EditSubMode) -> Unit,
    onCycleEditElement: (Int) -> Unit,
    onEditSubDelta: (Vec3) -> Unit,
    onExtrudeFace: () -> Unit,
    onInsetFace: () -> Unit,
    onBevelMesh: () -> Unit,
    onSubdivideMesh: () -> Unit
) {
    Text("Add 3D Primitive", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    val prims = PrimitiveKind.entries.filter { it != PrimitiveKind.CUSTOM }
    prims.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { kind ->
                Surface(
                    onClick = { onAddPrimitive(kind) },
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(kind.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
                    }
                }
            }
        }
    }

    HorizontalDivider(color = HairlineBorder)
    Text("Mesh Topology Editing", style = MaterialTheme.typography.titleSmall, color = StudioCyan)

    if (node?.mesh == null) {
        Text("Select a 3D mesh object to enter Edit Mode and modify vertices, edges, and faces.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        return
    }

    if (editorMode != EditorMode.EDIT) {
        Button(
            onClick = { onSetEditorMode(EditorMode.EDIT) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
        ) { Text("Enter Edit Mode (${node.mesh.vertexCount}v / ${node.mesh.triangleCount}f)") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            EditSubMode.entries.forEach { sub ->
                StudioFilterChip(sub.label, editSubMode == sub, { onSetEditSubMode(sub) }, modifier = Modifier.weight(1f), accentColor = StudioCyan)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = { onCycleEditElement(-1) }, border = BorderStroke(1.dp, HairlineBorder)) { Text("Prev", color = TextPrimary) }
            Text("${editSubMode.label} #$selectedElementIndex", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = KeyframeAmber)
            OutlinedButton(onClick = { onCycleEditElement(1) }, border = BorderStroke(1.dp, HairlineBorder)) { Text("Next", color = TextPrimary) }
        }
        Text("Move Selected ${editSubMode.label}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onEditSubDelta(Vec3(0.12f, 0f, 0f)) }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HairlineBorder)) { Text("+X", color = TextPrimary) }
            OutlinedButton(onClick = { onEditSubDelta(Vec3(0f, 0.12f, 0f)) }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HairlineBorder)) { Text("+Y", color = TextPrimary) }
            OutlinedButton(onClick = { onEditSubDelta(Vec3(0f, 0f, 0.12f)) }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HairlineBorder)) { Text("+Z", color = TextPrimary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onExtrudeFace, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)) { Text("Extrude") }
            Button(onClick = onInsetFace, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)) { Text("Inset") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBevelMesh, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, StudioCyan)) { Text("Bevel", color = StudioCyan) }
            OutlinedButton(onClick = onSubdivideMesh, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, StudioCyan)) { Text("Subdivide", color = StudioCyan) }
        }
    }
}

@Composable
private fun MaterialInspectorContent(
    node: SceneNode?,
    onUpdateMaterial: (MaterialData) -> Unit,
    onPickTextureImage: () -> Unit
) {
    if (node == null) {
        Text("Select a 3D mesh to edit its PBR material and texture.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        return
    }
    val mat = node.material
    Text("Base Color", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    ColorSwatchGrid(selectedHex = mat.baseColor, onSelect = { onUpdateMaterial(mat.copy(baseColor = it)) })

    AxisSliderRow("Rg", mat.roughness, 0.05f..1f, { onUpdateMaterial(mat.copy(roughness = it)) })
    AxisSliderRow("Mt", mat.metallic, 0f..1f, { onUpdateMaterial(mat.copy(metallic = it)) })
    AxisSliderRow("Op", mat.opacity, 0.15f..1f, { onUpdateMaterial(mat.copy(opacity = it)) })
    AxisSliderRow("Em", mat.emissionStrength, 0f..1.5f, {
        val emCol = if (mat.emissionColor == 0xFF000000L && it > 0.01f) mat.baseColor else mat.emissionColor
        onUpdateMaterial(mat.copy(emissionStrength = it, emissionColor = emCol))
    })
    AxisSliderRow("UV", mat.uvScale, 0.25f..6f, { onUpdateMaterial(mat.copy(uvScale = it)) })

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onPickTextureImage,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
        ) {
            Icon(Icons.Rounded.Image, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Local Texture")
        }
        if (mat.texturePath != null) {
            OutlinedButton(
                onClick = { onUpdateMaterial(mat.copy(texturePath = null)) },
                border = BorderStroke(1.dp, DangerRed)
            ) { Text("Clear", color = DangerRed) }
        }
    }
}

@Composable
private fun LightCameraInspectorContent(
    node: SceneNode?,
    onAddLight: (LightType) -> Unit,
    onUpdateLight: (LightData) -> Unit,
    onAddCamera: () -> Unit,
    onUpdateCamera: (CameraData) -> Unit
) {
    Text("Add Lights & Cameras", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        LightType.entries.forEach { lt ->
            Surface(
                onClick = { onAddLight(lt) },
                shape = RoundedCornerShape(8.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+ ${lt.label}", style = MaterialTheme.typography.labelSmall, color = KeyframeAmber)
                }
            }
        }
    }
    OutlinedButton(
        onClick = onAddCamera,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, StudioCyan)
    ) {
        Icon(Icons.Rounded.Videocam, null, tint = StudioCyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add Scene Camera", color = TextPrimary)
    }

    HorizontalDivider(color = HairlineBorder)
    if (node?.light != null) {
        val l = node.light
        Text("Light Settings (${l.lightType.label})", style = MaterialTheme.typography.titleSmall, color = KeyframeAmber)
        ColorSwatchGrid(selectedHex = l.color, onSelect = { onUpdateLight(l.copy(color = it)) })
        AxisSliderRow("In", l.intensity, 0.1f..4.0f, { onUpdateLight(l.copy(intensity = it)) })
        AxisSliderRow("Rn", l.range, 2f..32f, { onUpdateLight(l.copy(range = it)) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Cast Shadows", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Switch(checked = l.castShadows, onCheckedChange = { onUpdateLight(l.copy(castShadows = it)) })
        }
    } else if (node?.camera != null) {
        val c = node.camera
        Text("Camera Settings", style = MaterialTheme.typography.titleSmall, color = StudioCyan)
        AxisSliderRow("FV", c.fov, 20f..95f, { onUpdateCamera(c.copy(fov = it)) }, unit = "°")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Active Render Camera", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Switch(checked = c.isActive, onCheckedChange = { onUpdateCamera(c.copy(isActive = it)) })
        }
    } else {
        Text("Select a Light or Camera in the viewport or Outliner to customize it.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun RiggingInspectorContent(
    node: SceneNode?,
    selectedBoneId: String?,
    onAttachHumanoidRig: () -> Unit,
    onSelectBone: (String) -> Unit,
    onAddChildBone: (String) -> Unit,
    onRenameBone: (String) -> Unit,
    onDeleteBone: () -> Unit,
    onUpdateBonePose: (Vec3, Float?) -> Unit,
    onAutoBindWeights: () -> Unit
) {
    if (node == null) {
        Text("Select a 3D character or mesh to rig and pose its skeleton.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        return
    }
    val skel = node.skeleton
    if (skel == null || skel.bones.isEmpty()) {
        Text("No skeleton attached to \"${node.name}\".", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Button(
            onClick = onAttachHumanoidRig,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
        ) { Text("Attach 11-Bone Humanoid Rig") }
        OutlinedButton(
            onClick = { onAddChildBone("RootBone") },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, StudioCyan)
        ) { Text("Create Custom Root Bone", color = StudioCyan) }
        return
    }

    Text("Armature Hierarchy (${skel.bones.size} Bones)", style = MaterialTheme.typography.titleSmall, color = StudioCyan)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(skel.bones, key = { it.id }) { b ->
            StudioFilterChip(b.name, b.id == selectedBoneId, { onSelectBone(b.id) }, accentColor = StudioCyan)
        }
    }

    val selBone = skel.bones.firstOrNull { it.id == selectedBoneId } ?: skel.bones.first()
    var boneNameInput by remember(selBone.id) { mutableStateOf(selBone.name) }

    OutlinedTextField(
        value = boneNameInput,
        onValueChange = {
            boneNameInput = it
            onRenameBone(it)
        },
        label = { Text("Selected Bone Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text("Bone Pose Rotation", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    AxisSliderRow("X", selBone.poseRotation.x, -160f..160f, { onUpdateBonePose(selBone.poseRotation.copy(x = it), null) }, unit = "°")
    AxisSliderRow("Y", selBone.poseRotation.y, -160f..160f, { onUpdateBonePose(selBone.poseRotation.copy(y = it), null) }, unit = "°")
    AxisSliderRow("Z", selBone.poseRotation.z, -160f..160f, { onUpdateBonePose(selBone.poseRotation.copy(z = it), null) }, unit = "°")
    AxisSliderRow("Ln", selBone.length, 0.15f..1.2f, { onUpdateBonePose(selBone.poseRotation, it) })

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onAddChildBone("Bone ${skel.bones.size + 1}") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Bone")
        }
        OutlinedButton(
            onClick = onDeleteBone,
            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.6f))
        ) { Icon(Icons.Rounded.DeleteOutline, "Delete bone", tint = DangerRed, modifier = Modifier.size(16.dp)) }
    }

    OutlinedButton(
        onClick = onAutoBindWeights,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, StudioCyan)
    ) { Text("Auto-Bind Mesh Weights to Skeleton", color = StudioCyan) }
}
