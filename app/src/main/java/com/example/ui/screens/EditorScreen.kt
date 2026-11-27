package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LibraryAssetEntity
import com.example.data.SavedPoseEntity
import com.example.engine.gl.RenderConfig
import com.example.engine.gl.ViewportSurface
import com.example.engine.model.CameraPreset
import com.example.engine.model.EditorMode
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.ShadingMode
import com.example.ui.components.CompactToolButton
import com.example.ui.components.StudioFilterChip
import com.example.ui.theme.CharcoalCanvas
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
import com.example.viewmodel.EditorModalSheet
import com.example.viewmodel.HubTab
import com.example.viewmodel.InspectorTab
import com.example.viewmodel.StudioAppViewModel
import com.example.viewmodel.StudioUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: StudioUiState,
    libraryAssets: List<LibraryAssetEntity>,
    savedPoses: List<SavedPoseEntity>,
    viewModel: StudioAppViewModel,
    onImportBlenderModel: () -> Unit,
    onPickTextureImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        when {
            state.activeModal != EditorModalSheet.NONE -> viewModel.closeModal()
            state.inspectorOpen -> viewModel.closeInspector()
            else -> viewModel.exitEditorToHub()
        }
    }

    val renderConfig = remember(
        state.scene,
        state.selectedNodeId,
        state.selectedBoneId,
        state.editorMode,
        state.editSubMode,
        state.selectedElementIndex,
        state.shadingMode,
        state.transformTool,
        state.transformAxis,
        state.showGrid,
        state.showSkeleton,
        state.ikEnabled,
        state.selectedIkLimb,
        state.showOnionSkin
    ) {
        RenderConfig(
            scene = state.scene,
            selectedNodeId = state.selectedNodeId,
            selectedBoneId = state.selectedBoneId,
            editorMode = state.editorMode,
            editSubMode = state.editSubMode,
            selectedElementIndex = state.selectedElementIndex,
            shadingMode = state.shadingMode,
            transformTool = state.transformTool,
            transformAxis = state.transformAxis,
            showGrid = state.showGrid,
            showSkeleton = state.showSkeleton,
            showGizmo = true,
            ikEnabled = state.ikEnabled,
            selectedIkLimb = state.selectedIkLimb,
            showOnionSkin = state.showOnionSkin,
            onFpsSample = viewModel::updateViewportFps
        )
    }

    val selectedNode = state.scene.nodes.firstOrNull { it.id == state.selectedNodeId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalCanvas)
    ) {
        // 1. Compact Top Studio Bar
        Surface(
            color = PanelNearBlack,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.exitEditorToHub() },
                        modifier = Modifier.size(38.dp).testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to projects", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.scene.projectName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.isDirty) "Unsaved changes • ${state.scene.backdropPreset.label}" else "Saved • ${state.scene.backdropPreset.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isDirty) KeyframeAmber else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo,
                        modifier = Modifier.size(34.dp).testTag("editor_undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", tint = if (state.canUndo) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo,
                        modifier = Modifier.size(34.dp).testTag("editor_redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo", tint = if (state.canRedo) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.saveCurrentProject() },
                        modifier = Modifier.size(34.dp).testTag("editor_save_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save project", tint = StudioCyan, modifier = Modifier.size(18.dp))
                    }
                    Surface(
                        onClick = { viewModel.openModal(EditorModalSheet.EXPORT) },
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricIndigo,
                        modifier = Modifier.height(32.dp).testTag("editor_export_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.MovieCreation, contentDescription = "Export", tint = TextPrimary, modifier = Modifier.size(15.dp))
                            Text("Export", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }
                }

                // Blender-style Mode + Shading (Material / Toon / Solid / Wire) + Projection Sub-Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalCanvas)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EditorMode.entries.forEach { mode ->
                        StudioFilterChip(
                            label = mode.label,
                            selected = state.editorMode == mode,
                            onClick = {
                                viewModel.setEditorMode(mode)
                                if (mode == EditorMode.EDIT) viewModel.openInspector(InspectorTab.MODEL)
                                if (mode == EditorMode.POSE) viewModel.openInspector(InspectorTab.RIG)
                            },
                            accentColor = if (mode == EditorMode.POSE) StudioCyan else ElectricIndigo,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Shading Cycle Pill (Solid -> Material -> Toon -> Wire)
                    Surface(
                        onClick = {
                            val next = ShadingMode.entries[(state.shadingMode.ordinal + 1) % ShadingMode.entries.size]
                            viewModel.setShadingMode(next)
                        },
                        shape = RoundedCornerShape(7.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, if (state.shadingMode == ShadingMode.TOON) KeyframeAmber else HairlineBorder),
                        modifier = Modifier.height(28.dp).testTag("shading_toggle_button")
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                state.shadingMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.shadingMode == ShadingMode.TOON) KeyframeAmber else StudioCyan
                            )
                        }
                    }
                    // Perspective / Ortho Toggle
                    Surface(
                        onClick = { viewModel.toggleOrthographic() },
                        shape = RoundedCornerShape(7.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.height(28.dp).testTag("projection_toggle_button")
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.scene.viewportCamera.orthographic) "Ortho" else "Persp",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. Central Interactive 3D Viewport + Adaptive Inspector
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWide = maxWidth >= 720.dp

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Genuine OpenGL ES 3.0 Surface with live 3D IK Handle Dragging
                    ViewportSurface(
                        renderConfig = renderConfig,
                        isPlaying = state.scene.timeline.isPlaying || state.turntableMode,
                        onOrbit = { dy, dp -> viewModel.orbitCamera(dy, dp) },
                        onPan = { dx, dy -> viewModel.panCamera(dx, dy) },
                        onZoom = { z -> viewModel.zoomCamera(z) },
                        onSelectNodeOrBone = { nId, bId -> viewModel.selectNode(nId, bId) },
                        onSelectIkLimb = { nId, limb -> viewModel.selectIkLimb(nId, limb) },
                        onDragIkHandle = { nId, limb, worldPos -> viewModel.solveIkHandleTarget(nId, limb, worldPos) },
                        onDoubleTapFocus = viewModel::focusSelectedObject
                    )

                    // Optional Safe-Frame & Rule-of-Thirds Cinematic Framing Guide Overlay
                    if (state.showSafeFrame) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val padX = size.width * 0.08f
                            val padY = size.height * 0.08f
                            drawRect(
                                color = StudioCyan.copy(alpha = 0.45f),
                                topLeft = Offset(padX, padY),
                                size = Size(size.width - padX * 2f, size.height - padY * 2f),
                                style = Stroke(width = 1.5f)
                            )
                            val thirdW = size.width / 3f
                            val thirdH = size.height / 3f
                            for (i in 1..2) {
                                drawLine(Color.White.copy(alpha = 0.22f), Offset(thirdW * i, 0f), Offset(thirdW * i, size.height), strokeWidth = 1f)
                                drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, thirdH * i), Offset(size.width, thirdH * i), strokeWidth = 1f)
                            }
                        }
                    }

                    // Top-Left Pro HUD Stats Pill (Selection + Live FPS + Tri Count + IK Status)
                    Surface(
                        color = PanelNearBlack.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = selectedNode?.name ?: "No Selection",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StudioCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = KeyframeAmber.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "${state.viewportFps} FPS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = KeyframeAmber,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            val stats = buildString {
                                append("${state.scene.nodes.size} objs")
                                selectedNode?.mesh?.let { append(" • ${it.triangleCount}t") }
                                if (state.ikEnabled && selectedNode?.skeleton != null) {
                                    append(" • IK:${state.selectedIkLimb?.shortLabel ?: "Ready"}")
                                }
                            }
                            Text(stats, style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = TextSecondary)
                        }
                    }

                    // Top-Right Camera Preset Strip + Pro Overlay Toggles
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(CameraPreset.FRONT, CameraPreset.RIGHT, CameraPreset.TOP, CameraPreset.CAMERA_VIEW).forEach { preset ->
                                val active = state.scene.viewportCamera.preset == preset
                                Surface(
                                    onClick = { viewModel.setCameraPreset(preset) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) ElectricIndigo else PanelNearBlack.copy(alpha = 0.82f),
                                    border = BorderStroke(1.dp, if (active) StudioCyan else HairlineBorder)
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(CameraPreset.BACK, CameraPreset.LEFT, CameraPreset.BOTTOM).forEach { preset ->
                                val active = state.scene.viewportCamera.preset == preset
                                Surface(
                                    onClick = { viewModel.setCameraPreset(preset) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) ElectricIndigo else PanelNearBlack.copy(alpha = 0.78f),
                                    border = BorderStroke(1.dp, HairlineBorder)
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                onClick = { viewModel.focusSelectedObject() },
                                shape = RoundedCornerShape(8.dp),
                                color = PanelNearBlack.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Focus selected", tint = StudioCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(
                                onClick = { viewModel.toggleGrid() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.showGrid) ElectricIndigo.copy(alpha = 0.35f) else PanelNearBlack.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.GridOn, contentDescription = "Toggle grid", tint = if (state.showGrid) StudioCyan else TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(
                                onClick = { viewModel.toggleOnionSkin() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.showOnionSkin) StudioCyan.copy(alpha = 0.32f) else PanelNearBlack.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, if (state.showOnionSkin) StudioCyan else HairlineBorder),
                                modifier = Modifier.size(32.dp).testTag("viewport_onion_skin_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Layers, contentDescription = "Toggle onion skinning", tint = if (state.showOnionSkin) StudioCyan else TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(
                                onClick = { viewModel.toggleIkMode() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.ikEnabled) KeyframeAmber.copy(alpha = 0.28f) else PanelNearBlack.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, if (state.ikEnabled) KeyframeAmber else HairlineBorder),
                                modifier = Modifier.height(32.dp).testTag("viewport_ik_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                    Text("IK", style = MaterialTheme.typography.labelSmall, color = if (state.ikEnabled) KeyframeAmber else TextMuted)
                                }
                            }
                        }
                    }

                    // Slide-over Inspector on Compact Phones
                    if (!isWide) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.inspectorOpen,
                            enter = slideInHorizontally { it },
                            exit = slideOutHorizontally { it },
                            modifier = Modifier.align(Alignment.CenterEnd).width(300.dp)
                        ) {
                            StudioInspectorPanel(
                                state = state,
                                onSelectTab = viewModel::openInspector,
                                onClose = viewModel::closeInspector,
                                onSelectNode = { viewModel.selectNode(it) },
                                onSetParent = viewModel::setNodeParent,
                                onFocusSelected = viewModel::focusSelectedObject,
                                onDuplicateSelected = viewModel::duplicateSelectedNode,
                                onDeleteSelected = viewModel::deleteSelectedNode,
                                onApplyBackdrop = viewModel::applyBackdropAnd3DSet,
                                onToggleOnionSkin = viewModel::toggleOnionSkin,
                                onToggleSafeFrame = viewModel::toggleSafeFrame,
                                onToggleTurntable = viewModel::toggleTurntable,
                                onUpdateTransform = viewModel::updateSelectedTransform,
                                onAddPrimitive = viewModel::addPrimitive,
                                onSetEditorMode = viewModel::setEditorMode,
                                onSetEditSubMode = viewModel::setEditSubMode,
                                onCycleEditElement = viewModel::cycleSelectedEditElement,
                                onEditSubDelta = viewModel::applyEditSubElementDelta,
                                onExtrudeFace = { viewModel.extrudeSelectedFace() },
                                onInsetFace = { viewModel.insetSelectedFace() },
                                onBevelMesh = viewModel::bevelSelectedMesh,
                                onSubdivideMesh = viewModel::subdivideSelectedMesh,
                                onUpdateMaterial = viewModel::updateSelectedMaterial,
                                onApplySkinPreset = viewModel::applySkinPresetToSelected,
                                onPickTextureImage = onPickTextureImage,
                                onAddLight = viewModel::addLight,
                                onUpdateLight = viewModel::updateSelectedLight,
                                onAddCamera = viewModel::addCamera,
                                onUpdateCamera = viewModel::updateSelectedCamera,
                                onAttachHumanoidRig = viewModel::attachHumanoidRigToSelected,
                                onSelectBone = viewModel::selectBone,
                                onAddChildBone = viewModel::addChildBone,
                                onRenameBone = viewModel::renameSelectedBone,
                                onDeleteBone = viewModel::deleteSelectedBone,
                                onUpdateBonePose = viewModel::updateSelectedBonePose,
                                onResetPose = viewModel::resetSelectedSkeletonPose,
                                onAutoBindWeights = viewModel::autoBindSelectedMesh,
                                onToggleIkMode = viewModel::toggleIkMode,
                                onSelectIkLimb = { limb -> viewModel.selectIkLimb(state.selectedNodeId, limb) },
                                onNudgeIkTarget = viewModel::nudgeSelectedIkTarget,
                                onSetIkPoleAngle = viewModel::setIkPoleAngle,
                                onToggleFootPin = viewModel::toggleFootPin,
                                onKeyIkLimb = viewModel::keyframeSelectedIkLimb,
                                onMirrorPose = { viewModel.mirrorSelectedPose(insertKeyframes = false) },
                                onOpenPoseLibrary = { viewModel.openModal(EditorModalSheet.POSE_LIBRARY) }
                            )
                        }
                    }
                }

                // Docked Side Inspector on Tablets & Landscape
                if (isWide) {
                    StudioInspectorPanel(
                        state = state,
                        onSelectTab = viewModel::openInspector,
                        onClose = viewModel::closeInspector,
                        onSelectNode = { viewModel.selectNode(it) },
                        onSetParent = viewModel::setNodeParent,
                        onFocusSelected = viewModel::focusSelectedObject,
                        onDuplicateSelected = viewModel::duplicateSelectedNode,
                        onDeleteSelected = viewModel::deleteSelectedNode,
                        onApplyBackdrop = viewModel::applyBackdropAnd3DSet,
                        onToggleOnionSkin = viewModel::toggleOnionSkin,
                        onToggleSafeFrame = viewModel::toggleSafeFrame,
                        onToggleTurntable = viewModel::toggleTurntable,
                        onUpdateTransform = viewModel::updateSelectedTransform,
                        onAddPrimitive = viewModel::addPrimitive,
                        onSetEditorMode = viewModel::setEditorMode,
                        onSetEditSubMode = viewModel::setEditSubMode,
                        onCycleEditElement = viewModel::cycleSelectedEditElement,
                        onEditSubDelta = viewModel::applyEditSubElementDelta,
                        onExtrudeFace = { viewModel.extrudeSelectedFace() },
                        onInsetFace = { viewModel.insetSelectedFace() },
                        onBevelMesh = viewModel::bevelSelectedMesh,
                        onSubdivideMesh = viewModel::subdivideSelectedMesh,
                        onUpdateMaterial = viewModel::updateSelectedMaterial,
                        onApplySkinPreset = viewModel::applySkinPresetToSelected,
                        onPickTextureImage = onPickTextureImage,
                        onAddLight = viewModel::addLight,
                        onUpdateLight = viewModel::updateSelectedLight,
                        onAddCamera = viewModel::addCamera,
                        onUpdateCamera = viewModel::updateSelectedCamera,
                        onAttachHumanoidRig = viewModel::attachHumanoidRigToSelected,
                        onSelectBone = viewModel::selectBone,
                        onAddChildBone = viewModel::addChildBone,
                        onRenameBone = viewModel::renameSelectedBone,
                        onDeleteBone = viewModel::deleteSelectedBone,
                        onUpdateBonePose = viewModel::updateSelectedBonePose,
                        onResetPose = viewModel::resetSelectedSkeletonPose,
                        onAutoBindWeights = viewModel::autoBindSelectedMesh,
                        onToggleIkMode = viewModel::toggleIkMode,
                        onSelectIkLimb = { limb -> viewModel.selectIkLimb(state.selectedNodeId, limb) },
                        onNudgeIkTarget = viewModel::nudgeSelectedIkTarget,
                        onSetIkPoleAngle = viewModel::setIkPoleAngle,
                        onToggleFootPin = viewModel::toggleFootPin,
                        onKeyIkLimb = viewModel::keyframeSelectedIkLimb,
                        onMirrorPose = { viewModel.mirrorSelectedPose(insertKeyframes = false) },
                        onOpenPoseLibrary = { viewModel.openModal(EditorModalSheet.POSE_LIBRARY) },
                        modifier = Modifier.width(325.dp)
                    )
                }
            }
        }

        // 3. AutoDraft-Style Compact Editing Toolbar Strip
        Surface(
            color = PanelNearBlack,
            border = BorderStroke(0.5.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.Groups,
                        label = "Chars",
                        onClick = { viewModel.openModal(EditorModalSheet.CHARACTER_LIBRARY) },
                        modifier = Modifier.testTag("toolbar_characters_button")
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.Animation,
                        label = "Anims",
                        onClick = { viewModel.openModal(EditorModalSheet.ANIMATION_LIBRARY) },
                        modifier = Modifier.testTag("toolbar_animations_button")
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.Bookmarks,
                        label = "Poses",
                        selected = state.activeModal == EditorModalSheet.POSE_LIBRARY,
                        accent = StudioCyan,
                        onClick = { viewModel.openModal(EditorModalSheet.POSE_LIBRARY) },
                        modifier = Modifier.testTag("toolbar_poses_button")
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.Landscape,
                        label = "3D BG",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.WORLD,
                        accent = StudioCyan,
                        onClick = { viewModel.openInspector(InspectorTab.WORLD) },
                        modifier = Modifier.testTag("toolbar_world_bg_button")
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.AccessibilityNew,
                        label = "Rig/IK",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.RIG,
                        accent = StudioCyan,
                        onClick = { viewModel.openInspector(InspectorTab.RIG) },
                        modifier = Modifier.testTag("toolbar_rig_ik_button")
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.Palette,
                        label = "Skin/Mat",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.MATERIAL,
                        onClick = { viewModel.openInspector(InspectorTab.MATERIAL) }
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.AddBox,
                        label = "Add 3D",
                        onClick = { viewModel.openModal(EditorModalSheet.ADD_PRIMITIVE) },
                        modifier = Modifier.testTag("toolbar_add_mesh_button")
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.OpenWith,
                        label = "Transform",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.TRANSFORM,
                        onClick = { viewModel.openInspector(InspectorTab.TRANSFORM) }
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.ViewInAr,
                        label = "Model",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.MODEL,
                        onClick = { viewModel.openInspector(InspectorTab.MODEL) }
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.Lightbulb,
                        label = "Lights",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.LIGHT_CAM,
                        onClick = { viewModel.openInspector(InspectorTab.LIGHT_CAM) }
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.AccountTree,
                        label = "Scene",
                        selected = state.inspectorOpen && state.inspectorTab == InspectorTab.OUTLINER,
                        onClick = { viewModel.openInspector(InspectorTab.OUTLINER) }
                    )
                }
                item {
                    CompactToolButton(
                        icon = Icons.Rounded.FileUpload,
                        label = "Import",
                        onClick = onImportBlenderModel
                    )
                }
            }
        }

        // 4. Pinned Bottom-Aligned Synchronized Horizontal Timeline
        BottomTimelinePanel(
            scene = state.scene,
            selectedNodeId = state.selectedNodeId,
            selectedKeyframeId = state.selectedKeyframeId,
            hasCopiedKeyframe = state.copiedKeyframe != null,
            onTogglePlay = viewModel::togglePlayback,
            onScrubFrame = viewModel::scrubToFrame,
            onStepFrame = viewModel::stepFrame,
            onToggleLoop = viewModel::toggleLoop,
            onToggleAutoKey = viewModel::toggleAutoKey,
            onInsertKeyframe = viewModel::insertKeyframeAtCurrentFrame,
            onSelectKeyframe = viewModel::selectKeyframe,
            onDuplicateKeyframe = { viewModel.duplicateSelectedKeyframe() },
            onCopyKeyframe = viewModel::copySelectedKeyframe,
            onPasteKeyframe = viewModel::pasteKeyframeAtPlayhead,
            onDeleteKeyframe = viewModel::deleteSelectedKeyframe,
            onMoveSelectedKeyframe = viewModel::moveSelectedKeyframe,
            onUpdateKeyframeInterpolation = viewModel::updateSelectedKeyframeInterpolation,
            onOpenPoseLibrary = { viewModel.openModal(EditorModalSheet.POSE_LIBRARY) },
            onToggleTrackVisibility = viewModel::toggleTrackVisibility,
            onToggleTrackLock = viewModel::toggleTrackLock,
            onSelectNode = { viewModel.selectNode(it) },
            onUpdateClip = viewModel::updateClip,
            onDeleteClip = viewModel::deleteClip,
            onZoomTimeline = viewModel::adjustTimelineZoom
        )
    }

    // Editor Modal Sheets
    when (state.activeModal) {
        EditorModalSheet.CHARACTER_LIBRARY -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = CharcoalCanvas
            ) {
                Box(modifier = Modifier.height(540.dp)) {
                    CharacterLibraryScreen(
                        assets = libraryAssets,
                        onAddAssetToScene = viewModel::addLibraryAssetToScene,
                        onTogglePin = viewModel::togglePinAsset,
                        onRenameAsset = viewModel::renameAsset,
                        onDeleteAsset = viewModel::deleteAsset,
                        onImportBlenderModel = onImportBlenderModel,
                        onOpenCreator = {
                            viewModel.closeModal()
                            viewModel.exitEditorToHub()
                            viewModel.selectHubTab(HubTab.CREATOR)
                        }
                    )
                }
            }
        }
        EditorModalSheet.ANIMATION_LIBRARY -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = CharcoalCanvas
            ) {
                AnimationLibraryPanel(onApplyPreset = viewModel::applyAnimationPresetToSelected)
            }
        }
        EditorModalSheet.POSE_LIBRARY -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = CharcoalCanvas
            ) {
                PoseLibrarySheet(
                    poses = savedPoses,
                    currentFrame = state.scene.timeline.currentFrame.toInt(),
                    poseBlendWeight = state.poseBlendWeight,
                    onChangeBlendWeight = viewModel::setPoseBlendWeight,
                    onSaveCurrentPose = viewModel::saveCurrentPoseToLibrary,
                    onApplyPose = { pose, weight, insertKey ->
                        viewModel.applySavedPose(pose, weight, insertKey)
                    },
                    onMirrorPose = { viewModel.mirrorSelectedPose(insertKeyframes = false) },
                    onDeletePose = viewModel::deleteSavedPose
                )
            }
        }
        EditorModalSheet.ADD_PRIMITIVE -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                containerColor = PanelNearBlack
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add 3D Primitive Mesh", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    val prims = PrimitiveKind.entries.filter { it != PrimitiveKind.CUSTOM }
                    prims.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { kind ->
                                Surface(
                                    onClick = { viewModel.addPrimitive(kind) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SurfaceCard,
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("add_primitive_${kind.name}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(kind.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        EditorModalSheet.EXPORT -> {
            ExportStudioDialog(
                state = state.exportState,
                totalFrames = state.scene.timeline.totalFrames,
                onUpdateConfig = viewModel::updateExportConfig,
                onStartExport = viewModel::startExport,
                onCancelExport = viewModel::cancelExport,
                onDismiss = viewModel::closeModal
            )
        }
        EditorModalSheet.BLENDER_GUIDE, EditorModalSheet.NONE -> Unit
    }
}
