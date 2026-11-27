package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LibraryAssetEntity
import com.example.data.ProjectEntity
import com.example.data.StudioRepository
import com.example.engine.animation.AnimationPresetInfo
import com.example.engine.io.GlbExporter
import com.example.engine.io.ModelImporter
import com.example.engine.io.VideoExportRequest
import com.example.engine.io.VideoExporter
import com.example.engine.model.AnimationClipTrack
import com.example.engine.model.AssetCategory
import com.example.engine.model.BoneNode
import com.example.engine.model.CameraData
import com.example.engine.model.CameraPreset
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterCategory
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.EditSubMode
import com.example.engine.model.EditorMode
import com.example.engine.model.ExportFormat
import com.example.engine.model.ExportResolution
import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.KeyframeProperty
import com.example.engine.model.LightData
import com.example.engine.model.LightType
import com.example.engine.model.MaterialData
import com.example.engine.model.MeshEditingOps
import com.example.engine.model.NodeType
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.PrimitiveMeshFactory
import com.example.engine.model.RenderQuality
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.ShadingMode
import com.example.engine.model.SkeletonData
import com.example.engine.model.Transform3D
import com.example.engine.model.TransformAxis
import com.example.engine.model.TransformTool
import com.example.engine.model.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class StudioAppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudioRepository(application)

    val projects: StateFlow<List<ProjectEntity>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryAssets: StateFlow<List<LibraryAssetEntity>> = repository.libraryAssetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<SceneDocument>()
    private val redoStack = ArrayDeque<SceneDocument>()
    private var playbackJob: Job? = null
    private var autosaveJob: Job? = null
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    // --- Navigation & Hub ---

    fun selectHubTab(tab: HubTab) {
        _uiState.update { it.copy(hubTab = tab) }
    }

    fun openQuickStartScene() {
        viewModelScope.launch {
            val doc = repository.createQuickStartProject()
            openSceneInEditor(doc)
            notify("Quick-Start Rigged Studio Scene created")
        }
    }

    fun createNewBlankProject(name: String) {
        viewModelScope.launch {
            val doc = repository.createBlankProject(name)
            openSceneInEditor(doc)
            notify("Created \"$name\"")
        }
    }

    fun openProject(projectId: String, useRecovery: Boolean = false) {
        viewModelScope.launch {
            val doc = repository.loadProject(projectId, useRecovery)
            if (doc != null) {
                openSceneInEditor(doc)
                if (useRecovery) notify("Recovered autosave snapshot")
            } else {
                notify("Could not open project file")
            }
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            repository.renameProject(projectId, newName)
            if (_uiState.value.scene.projectId == projectId) {
                _uiState.update { it.copy(scene = it.scene.copy(projectName = newName.trim())) }
            }
            notify("Renamed project")
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            repository.duplicateProject(projectId)
            notify("Duplicated project")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            notify("Project deleted")
        }
    }

    fun exitEditorToHub() {
        stopPlayback()
        saveCurrentProject(silent = true)
        _uiState.update { it.copy(destination = AppDestination.HUB, activeModal = EditorModalSheet.NONE) }
    }

    private fun openSceneInEditor(doc: SceneDocument) {
        stopPlayback()
        undoStack.clear()
        redoStack.clear()
        val firstNode = doc.nodes.firstOrNull { it.nodeType == NodeType.CHARACTER } ?: doc.nodes.firstOrNull()
        _uiState.update {
            it.copy(
                destination = AppDestination.EDITOR,
                scene = doc,
                selectedNodeId = firstNode?.id,
                selectedBoneId = firstNode?.skeleton?.bones?.firstOrNull()?.id,
                selectedKeyframeId = null,
                editorMode = EditorMode.OBJECT,
                canUndo = false,
                canRedo = false,
                isDirty = false,
                lastSavedTime = doc.updatedAt,
                activeModal = EditorModalSheet.NONE,
                exportState = it.exportState.copy(
                    fps = doc.timeline.fps,
                    startFrame = 0,
                    endFrame = doc.timeline.totalFrames,
                    completedUri = null,
                    errorMessage = null
                )
            )
        }
    }

    // --- Persistence & Undo/Redo ---

    fun saveCurrentProject(silent: Boolean = false) {
        val doc = _uiState.value.scene
        if (doc.nodes.isEmpty() && _uiState.value.destination != AppDestination.EDITOR) return
        viewModelScope.launch {
            repository.saveProject(doc, isRecovery = false)
            _uiState.update { it.copy(isDirty = false, lastSavedTime = System.currentTimeMillis()) }
            if (!silent) notify("Project saved locally")
        }
    }

    private fun scheduleRecoveryAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(3500)
            if (_uiState.value.destination == AppDestination.EDITOR && _uiState.value.isDirty) {
                repository.saveProject(_uiState.value.scene, isRecovery = true)
            }
        }
    }

    private fun mutateScene(recordUndo: Boolean = true, transform: (SceneDocument) -> SceneDocument) {
        _uiState.update { state ->
            if (recordUndo) {
                undoStack.addLast(state.scene)
                if (undoStack.size > 30) undoStack.removeFirst()
                redoStack.clear()
            }
            val next = transform(state.scene)
            state.copy(
                scene = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                isDirty = true
            )
        }
        if (recordUndo) scheduleRecoveryAutosave()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        _uiState.update { state ->
            redoStack.addLast(state.scene)
            val prev = undoStack.removeLast()
            state.copy(
                scene = prev,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                isDirty = true
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        _uiState.update { state ->
            undoStack.addLast(state.scene)
            val next = redoStack.removeLast()
            state.copy(
                scene = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                isDirty = true
            )
        }
    }

    // --- Viewport & Camera Navigation ---

    fun orbitCamera(deltaYaw: Float, deltaPitch: Float) {
        mutateScene(recordUndo = false) { doc ->
            val cam = doc.viewportCamera
            doc.copy(
                viewportCamera = cam.copy(
                    yawDeg = (cam.yawDeg + deltaYaw) % 360f,
                    pitchDeg = (cam.pitchDeg + deltaPitch).coerceIn(-85f, 85f),
                    preset = CameraPreset.FREE
                )
            )
        }
    }

    fun panCamera(deltaX: Float, deltaY: Float) {
        mutateScene(recordUndo = false) { doc ->
            val cam = doc.viewportCamera
            val yawRad = cam.yawDeg * (Math.PI / 180.0).toFloat()
            val right = Vec3(cos(yawRad), 0f, -sin(yawRad))
            val move = right * (-deltaX * cam.distance * 0.4f) + Vec3(0f, deltaY * cam.distance * 0.4f, 0f)
            doc.copy(viewportCamera = cam.copy(target = cam.target + move))
        }
    }

    fun zoomCamera(factor: Float) {
        mutateScene(recordUndo = false) { doc ->
            val cam = doc.viewportCamera
            val nextDist = (cam.distance / factor.coerceIn(0.6f, 1.6f)).coerceIn(1.5f, 36f)
            doc.copy(viewportCamera = cam.copy(distance = nextDist))
        }
    }

    fun setCameraPreset(preset: CameraPreset) {
        mutateScene(recordUndo = false) { doc ->
            val cam = doc.viewportCamera
            val updated = when (preset) {
                CameraPreset.FRONT -> cam.copy(yawDeg = 0f, pitchDeg = 0f, preset = preset)
                CameraPreset.BACK -> cam.copy(yawDeg = 180f, pitchDeg = 0f, preset = preset)
                CameraPreset.LEFT -> cam.copy(yawDeg = -90f, pitchDeg = 0f, preset = preset)
                CameraPreset.RIGHT -> cam.copy(yawDeg = 90f, pitchDeg = 0f, preset = preset)
                CameraPreset.TOP -> cam.copy(yawDeg = 0f, pitchDeg = 85f, preset = preset)
                CameraPreset.BOTTOM -> cam.copy(yawDeg = 0f, pitchDeg = -85f, preset = preset)
                CameraPreset.FREE -> cam.copy(yawDeg = 32f, pitchDeg = 20f, preset = preset)
                CameraPreset.CAMERA_VIEW -> cam.copy(preset = preset)
            }
            doc.copy(viewportCamera = updated)
        }
    }

    fun toggleOrthographic() {
        mutateScene(recordUndo = false) { doc ->
            doc.copy(viewportCamera = doc.viewportCamera.copy(orthographic = !doc.viewportCamera.orthographic))
        }
    }

    fun focusSelectedObject() {
        val sel = selectedNode() ?: return
        mutateScene(recordUndo = false) { doc ->
            val center = sel.mesh?.computeLocalBounds()?.center ?: Vec3.ZERO
            doc.copy(
                viewportCamera = doc.viewportCamera.copy(
                    target = sel.transform.position + center,
                    distance = 4.6f,
                    preset = CameraPreset.FREE
                )
            )
        }
    }

    // --- Modes, Selection & Inspector ---

    fun setEditorMode(mode: EditorMode) = _uiState.update { it.copy(editorMode = mode) }
    fun setEditSubMode(sub: EditSubMode) = _uiState.update { it.copy(editSubMode = sub, selectedElementIndex = 0) }
    fun cycleSelectedEditElement(step: Int) = _uiState.update { state ->
        val mesh = state.scene.nodes.firstOrNull { it.id == state.selectedNodeId }?.mesh
        val limit = when (state.editSubMode) {
            EditSubMode.VERTEX -> (mesh?.vertexCount ?: 1).coerceAtLeast(1)
            EditSubMode.EDGE -> ((mesh?.triangleCount ?: 1) * 3).coerceAtLeast(1)
            EditSubMode.FACE -> (mesh?.triangleCount ?: 1).coerceAtLeast(1)
        }
        val next = ((state.selectedElementIndex + step) % limit + limit) % limit
        state.copy(selectedElementIndex = next)
    }

    fun setShadingMode(mode: ShadingMode) = _uiState.update { it.copy(shadingMode = mode) }
    fun setTransformTool(tool: TransformTool) = _uiState.update { it.copy(transformTool = tool) }
    fun setTransformAxis(axis: TransformAxis) = _uiState.update { it.copy(transformAxis = axis) }
    fun toggleGrid() = _uiState.update { it.copy(showGrid = !it.showGrid) }
    fun toggleSkeletonOverlay() = _uiState.update { it.copy(showSkeleton = !it.showSkeleton) }
    fun openInspector(tab: InspectorTab) = _uiState.update { it.copy(inspectorOpen = true, inspectorTab = tab) }
    fun closeInspector() = _uiState.update { it.copy(inspectorOpen = false) }
    fun openModal(modal: EditorModalSheet) = _uiState.update { it.copy(activeModal = modal) }
    fun closeModal() = _uiState.update { it.copy(activeModal = EditorModalSheet.NONE) }
    fun dismissImportReport() = _uiState.update { it.copy(importReport = null) }
    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    fun selectNode(nodeId: String?, boneId: String? = null) {
        _uiState.update { state ->
            val node = state.scene.nodes.firstOrNull { it.id == nodeId }
            state.copy(
                selectedNodeId = nodeId,
                selectedBoneId = boneId ?: node?.skeleton?.bones?.firstOrNull()?.id,
                selectedElementIndex = 0
            )
        }
    }

    fun selectBone(boneId: String) = _uiState.update { it.copy(selectedBoneId = boneId) }

    // --- Primitive Modeling & Object Operations ---

    fun addPrimitive(kind: PrimitiveKind) {
        val count = _uiState.value.scene.nodes.count { it.primitiveKind == kind } + 1
        val mesh = PrimitiveMeshFactory.createPrimitive(kind)
        val yOffset = if (kind == PrimitiveKind.PLANE || kind == PrimitiveKind.CIRCLE) 0.02f else 0.7f
        val node = SceneNode(
            name = "${kind.label} $count",
            nodeType = NodeType.MESH,
            primitiveKind = kind,
            transform = Transform3D(position = Vec3(0f, yOffset, 0f)),
            mesh = mesh,
            material = MaterialData(name = "${kind.label} Mat", baseColor = 0xFF6366F1)
        )
        mutateScene { it.copy(nodes = it.nodes + node) }
        selectNode(node.id)
        closeModal()
        notify("Added ${node.name}")
    }

    fun addLight(type: LightType) {
        val count = _uiState.value.scene.nodes.count { it.nodeType == NodeType.LIGHT } + 1
        val node = SceneNode(
            name = "${type.label} Light $count",
            nodeType = NodeType.LIGHT,
            transform = Transform3D(position = Vec3(2.2f, 3.6f, 2.2f)),
            light = LightData(lightType = type, color = 0xFFFFF8E7, intensity = 1.6f)
        )
        mutateScene { it.copy(nodes = it.nodes + node) }
        selectNode(node.id)
        notify("Added ${node.name}")
    }

    fun addCamera() {
        val count = _uiState.value.scene.nodes.count { it.nodeType == NodeType.CAMERA } + 1
        val node = SceneNode(
            name = "Camera $count",
            nodeType = NodeType.CAMERA,
            transform = Transform3D(position = Vec3(0f, 1.8f, 5.5f)),
            camera = CameraData(fov = 45f, isActive = true)
        )
        mutateScene { doc ->
            val updated = doc.nodes.map { n ->
                if (n.nodeType == NodeType.CAMERA && n.camera != null) n.copy(camera = n.camera.copy(isActive = false)) else n
            } + node
            doc.copy(nodes = updated)
        }
        selectNode(node.id)
        notify("Added ${node.name}")
    }

    fun duplicateSelectedNode() {
        val sel = selectedNode() ?: return
        val copy = sel.copy(
            id = UUID.randomUUID().toString(),
            name = "${sel.name} Copy",
            transform = sel.transform.copy(position = sel.transform.position + Vec3(0.75f, 0f, 0f))
        )
        mutateScene { it.copy(nodes = it.nodes + copy) }
        selectNode(copy.id)
        notify("Duplicated ${sel.name}")
    }

    fun deleteSelectedNode() {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene { doc ->
            doc.copy(
                nodes = doc.nodes.filterNot { it.id == selId }.map { n ->
                    if (n.parentId == selId) n.copy(parentId = null) else n
                },
                timeline = doc.timeline.copy(
                    keyframes = doc.timeline.keyframes.filterNot { it.targetNodeId == selId },
                    clips = doc.timeline.clips.filterNot { it.targetNodeId == selId }
                )
            )
        }
        selectNode(_uiState.value.scene.nodes.firstOrNull()?.id)
        notify("Deleted object")
    }

    fun setNodeParent(nodeId: String, parentId: String?) {
        if (nodeId == parentId) return
        if (parentId != null && wouldCreateHierarchyCycle(nodeId, parentId)) {
            notify("Cannot parent object to its own descendant")
            return
        }
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { if (it.id == nodeId) it.copy(parentId = parentId) else it })
        }
    }

    private fun wouldCreateHierarchyCycle(nodeId: String, candidateParentId: String): Boolean {
        val nodeMap = _uiState.value.scene.nodes.associateBy { it.id }
        var cursor: String? = candidateParentId
        val visited = HashSet<String>()
        while (cursor != null && visited.add(cursor)) {
            if (cursor == nodeId) return true
            cursor = nodeMap[cursor]?.parentId
        }
        return false
    }

    private fun isNodeLocked(nodeId: String): Boolean =
        _uiState.value.scene.timeline.lockedTrackNodeIds.contains(nodeId)

    fun updateSelectedTransform(transform: Transform3D) {
        val selId = _uiState.value.selectedNodeId ?: return
        if (isNodeLocked(selId)) {
            notify("Layer is locked on timeline")
            return
        }
        mutateScene(recordUndo = false) { doc ->
            val updatedNodes = doc.nodes.map { if (it.id == selId) it.copy(transform = transform) else it }
            var tl = doc.timeline
            if (tl.autoKey) {
                val frame = tl.currentFrame.toInt()
                tl = upsertKeyframeInTimeline(tl, selId, null, frame, KeyframeProperty.POSITION, transform.position)
                tl = upsertKeyframeInTimeline(tl, selId, null, frame, KeyframeProperty.ROTATION, transform.rotation)
                tl = upsertKeyframeInTimeline(tl, selId, null, frame, KeyframeProperty.SCALE, transform.scale)
            }
            doc.copy(nodes = updatedNodes, timeline = tl)
        }
    }

    fun updateSelectedMaterial(material: MaterialData) {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene(recordUndo = false) { doc ->
            doc.copy(nodes = doc.nodes.map { if (it.id == selId) it.copy(material = material) else it })
        }
    }

    fun importTextureForSelectedNode(uri: Uri) {
        val selId = _uiState.value.selectedNodeId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val texDir = File(ctx.filesDir, "textures").apply { mkdirs() }
            val outFile = File(texDir, "mat_${System.currentTimeMillis()}.png")
            val ok = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { input.copyTo(it) }
                }
                outFile.exists() && outFile.length() > 0
            }.getOrDefault(false)
            if (ok) {
                mutateScene { doc ->
                    doc.copy(nodes = doc.nodes.map { n ->
                        if (n.id == selId) n.copy(material = n.material.copy(texturePath = outFile.absolutePath)) else n
                    })
                }
                notify("Applied local texture to material")
            } else {
                notify("Unable to import texture image")
            }
        }
    }

    fun updateSelectedLight(light: LightData) {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene(recordUndo = false) { doc ->
            doc.copy(nodes = doc.nodes.map { if (it.id == selId) it.copy(light = light) else it })
        }
    }

    fun updateSelectedCamera(camera: CameraData) {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene(recordUndo = false) { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                when {
                    n.id == selId -> n.copy(camera = camera)
                    camera.isActive && n.nodeType == NodeType.CAMERA && n.camera != null ->
                        n.copy(camera = n.camera.copy(isActive = false))
                    else -> n
                }
            })
        }
    }

    // --- Real Edit Mode Mesh Operations ---

    fun applyEditSubElementDelta(delta: Vec3) {
        val selId = _uiState.value.selectedNodeId ?: return
        if (isNodeLocked(selId)) {
            notify("Layer is locked on timeline")
            return
        }
        val sub = _uiState.value.editSubMode
        val elemIdx = _uiState.value.selectedElementIndex
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.mesh != null) {
                    n.copy(mesh = MeshEditingOps.translateSubElement(n.mesh, sub, elemIdx, delta))
                } else n
            })
        }
    }

    fun extrudeSelectedFace(distance: Float = 0.28f) {
        val selId = _uiState.value.selectedNodeId ?: return
        val elemIdx = _uiState.value.selectedElementIndex
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.mesh != null) {
                    n.copy(mesh = MeshEditingOps.extrudeFace(n.mesh, elemIdx, distance))
                } else n
            })
        }
        notify("Extruded face #$elemIdx")
    }

    fun insetSelectedFace(factor: Float = 0.28f) {
        val selId = _uiState.value.selectedNodeId ?: return
        val elemIdx = _uiState.value.selectedElementIndex
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.mesh != null) {
                    n.copy(mesh = MeshEditingOps.insetFace(n.mesh, elemIdx, factor))
                } else n
            })
        }
        notify("Inset face #$elemIdx")
    }

    fun bevelSelectedMesh() {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.mesh != null) {
                    n.copy(mesh = MeshEditingOps.bevelMesh(n.mesh))
                } else n
            })
        }
        notify("Beveled mesh geometry")
    }

    fun subdivideSelectedMesh() {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.mesh != null) {
                    n.copy(mesh = MeshEditingOps.subdivideMesh(n.mesh))
                } else n
            })
        }
        notify("Subdivided mesh topology")
    }

    // --- Skeleton & Rigging ---

    fun attachHumanoidRigToSelected() {
        val selId = _uiState.value.selectedNodeId ?: return
        val skel = CharacterMeshBuilder.createDefaultHumanoidSkeleton(1.0f)
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId) {
                    val boundMesh = n.mesh?.let { MeshEditingOps.autoBindMeshToSkeleton(it, skel) }
                    n.copy(skeleton = skel, mesh = boundMesh, nodeType = NodeType.CHARACTER)
                } else n
            })
        }
        _uiState.update { it.copy(selectedBoneId = skel.bones.first().id, editorMode = EditorMode.POSE) }
        notify("Attached 11-bone Humanoid Rig & bound skin weights")
    }

    fun addChildBone(name: String) {
        val selId = _uiState.value.selectedNodeId ?: return
        val parentBoneId = _uiState.value.selectedBoneId
        val newBone = BoneNode(
            name = name.ifBlank { "Bone" },
            parentId = parentBoneId,
            localOffset = Vec3(0f, 0.35f, 0f),
            length = 0.38f
        )
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId) {
                    val bones = (n.skeleton?.bones ?: emptyList()) + newBone
                    val nextSkel = SkeletonData(bones)
                    val boundMesh = n.mesh?.let { MeshEditingOps.autoBindMeshToSkeleton(it, nextSkel) }
                    n.copy(skeleton = nextSkel, mesh = boundMesh)
                } else n
            })
        }
        selectBone(newBone.id)
        notify("Added bone ${newBone.name}")
    }

    fun renameSelectedBone(newName: String) {
        val selId = _uiState.value.selectedNodeId ?: return
        val bId = _uiState.value.selectedBoneId ?: return
        val clean = newName.trim().ifBlank { return }
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.skeleton != null) {
                    n.copy(skeleton = SkeletonData(n.skeleton.bones.map { if (it.id == bId) it.copy(name = clean) else it }))
                } else n
            })
        }
    }

    fun deleteSelectedBone() {
        val selId = _uiState.value.selectedNodeId ?: return
        val bId = _uiState.value.selectedBoneId ?: return
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.skeleton != null) {
                    val rem = n.skeleton.bones.filterNot { it.id == bId }.map { b ->
                        if (b.parentId == bId) b.copy(parentId = null) else b
                    }
                    n.copy(skeleton = SkeletonData(rem))
                } else n
            })
        }
        _uiState.update { it.copy(selectedBoneId = selectedNode()?.skeleton?.bones?.firstOrNull()?.id) }
    }

    fun updateSelectedBonePose(rotation: Vec3, length: Float? = null) {
        val selId = _uiState.value.selectedNodeId ?: return
        val bId = _uiState.value.selectedBoneId ?: return
        if (isNodeLocked(selId)) {
            notify("Layer is locked on timeline")
            return
        }
        mutateScene(recordUndo = false) { doc ->
            val updatedNodes = doc.nodes.map { n ->
                if (n.id == selId && n.skeleton != null) {
                    n.copy(skeleton = SkeletonData(n.skeleton.bones.map { b ->
                        if (b.id == bId) b.copy(poseRotation = rotation, length = length ?: b.length) else b
                    }))
                } else n
            }
            var tl = doc.timeline
            if (tl.autoKey) {
                tl = upsertKeyframeInTimeline(tl, selId, bId, tl.currentFrame.toInt(), KeyframeProperty.BONE_ROTATION, rotation)
            }
            doc.copy(nodes = updatedNodes, timeline = tl)
        }
    }

    fun resetSelectedSkeletonPose() {
        val selId = _uiState.value.selectedNodeId ?: return
        if (isNodeLocked(selId)) {
            notify("Layer is locked on timeline")
            return
        }
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.skeleton != null) {
                    n.copy(skeleton = SkeletonData(n.skeleton.bones.map { b -> b.copy(poseRotation = Vec3.ZERO) }))
                } else n
            })
        }
        notify("Reset armature to rest pose")
    }

    fun autoBindSelectedMesh() {
        val selId = _uiState.value.selectedNodeId ?: return
        mutateScene { doc ->
            doc.copy(nodes = doc.nodes.map { n ->
                if (n.id == selId && n.mesh != null && n.skeleton != null) {
                    n.copy(mesh = MeshEditingOps.autoBindMeshToSkeleton(n.mesh, n.skeleton))
                } else n
            })
        }
        notify("Recomputed smooth proximity bone weights")
    }

    // --- Timeline, Keyframes & Animation Clips ---

    fun togglePlayback() {
        if (_uiState.value.scene.timeline.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        playbackJob?.cancel()
        _uiState.update { s -> s.copy(scene = s.scene.copy(timeline = s.scene.timeline.copy(isPlaying = true))) }
        playbackJob = viewModelScope.launch {
            var lastNs = System.nanoTime()
            while (isActive) {
                val tl = _uiState.value.scene.timeline
                if (!tl.isPlaying) break
                val nowNs = System.nanoTime()
                val dtSec = ((nowNs - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                lastNs = nowNs
                var nextF = tl.currentFrame + dtSec * tl.fps
                if (nextF >= tl.totalFrames) {
                    if (tl.isLooping) {
                        nextF = 0f
                    } else {
                        nextF = tl.totalFrames.toFloat()
                        stopPlayback()
                    }
                }
                _uiState.update { s ->
                    s.copy(scene = s.scene.copy(timeline = s.scene.timeline.copy(currentFrame = nextF)))
                }
                delay((1000L / tl.fps.coerceIn(15, 60)).coerceAtLeast(16L))
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { s -> s.copy(scene = s.scene.copy(timeline = s.scene.timeline.copy(isPlaying = false))) }
    }

    fun scrubToFrame(frame: Float) {
        val clamped = frame.coerceIn(0f, _uiState.value.scene.timeline.totalFrames.toFloat())
        _uiState.update { s ->
            s.copy(scene = s.scene.copy(timeline = s.scene.timeline.copy(currentFrame = clamped)))
        }
    }

    fun stepFrame(delta: Int) {
        stopPlayback()
        scrubToFrame(_uiState.value.scene.timeline.currentFrame + delta)
    }

    fun toggleLoop() = mutateScene(recordUndo = false) { doc ->
        doc.copy(timeline = doc.timeline.copy(isLooping = !doc.timeline.isLooping))
    }

    fun toggleAutoKey() = mutateScene(recordUndo = false) { doc ->
        doc.copy(timeline = doc.timeline.copy(autoKey = !doc.timeline.autoKey))
    }

    fun adjustTimelineZoom(factor: Float) = mutateScene(recordUndo = false) { doc ->
        doc.copy(timeline = doc.timeline.copy(zoom = (doc.timeline.zoom * factor).coerceIn(0.5f, 3.0f)))
    }

    fun setTimelineDuration(totalFrames: Int, fps: Int) = mutateScene { doc ->
        doc.copy(timeline = doc.timeline.copy(totalFrames = totalFrames.coerceIn(30, 1200), fps = fps.coerceIn(15, 60)))
    }

    fun toggleTrackVisibility(nodeId: String) = mutateScene { doc ->
        val set = doc.timeline.hiddenTrackNodeIds.toMutableSet()
        if (!set.add(nodeId)) set.remove(nodeId)
        doc.copy(timeline = doc.timeline.copy(hiddenTrackNodeIds = set))
    }

    fun toggleTrackLock(nodeId: String) = mutateScene { doc ->
        val set = doc.timeline.lockedTrackNodeIds.toMutableSet()
        if (!set.add(nodeId)) set.remove(nodeId)
        doc.copy(timeline = doc.timeline.copy(lockedTrackNodeIds = set))
    }

    fun insertKeyframeAtCurrentFrame() {
        val node = selectedNode() ?: run {
            notify("Select an object or bone first")
            return
        }
        if (isNodeLocked(node.id)) {
            notify("Layer is locked on timeline")
            return
        }
        val frame = _uiState.value.scene.timeline.currentFrame.toInt()
        val mode = _uiState.value.editorMode
        val boneId = _uiState.value.selectedBoneId

        mutateScene { doc ->
            var tl = doc.timeline
            if (mode == EditorMode.POSE && boneId != null) {
                val bone = node.skeleton?.bones?.firstOrNull { it.id == boneId }
                if (bone != null) {
                    tl = upsertKeyframeInTimeline(tl, node.id, boneId, frame, KeyframeProperty.BONE_ROTATION, bone.poseRotation)
                }
            } else {
                tl = upsertKeyframeInTimeline(tl, node.id, null, frame, KeyframeProperty.POSITION, node.transform.position)
                tl = upsertKeyframeInTimeline(tl, node.id, null, frame, KeyframeProperty.ROTATION, node.transform.rotation)
                tl = upsertKeyframeInTimeline(tl, node.id, null, frame, KeyframeProperty.SCALE, node.transform.scale)
            }
            doc.copy(timeline = tl)
        }
        notify("Inserted keyframe at F$frame")
    }

    fun selectKeyframe(kfId: String?) = _uiState.update { it.copy(selectedKeyframeId = kfId) }

    fun deleteSelectedKeyframe() {
        val kfId = _uiState.value.selectedKeyframeId ?: return
        mutateScene { doc ->
            doc.copy(timeline = doc.timeline.copy(keyframes = doc.timeline.keyframes.filterNot { it.id == kfId }))
        }
        selectKeyframe(null)
        notify("Deleted keyframe")
    }

    fun duplicateSelectedKeyframe(offsetFrames: Int = 10) {
        val kf = _uiState.value.scene.timeline.keyframes.firstOrNull { it.id == _uiState.value.selectedKeyframeId } ?: return
        val targetF = (kf.frame + offsetFrames).coerceIn(0, _uiState.value.scene.timeline.totalFrames)
        val dup = kf.copy(id = UUID.randomUUID().toString(), frame = targetF)
        mutateScene { doc ->
            doc.copy(timeline = doc.timeline.copy(keyframes = doc.timeline.keyframes + dup))
        }
        selectKeyframe(dup.id)
        notify("Duplicated keyframe to F$targetF")
    }

    fun copySelectedKeyframe() {
        val kf = _uiState.value.scene.timeline.keyframes.firstOrNull { it.id == _uiState.value.selectedKeyframeId } ?: return
        _uiState.update { it.copy(copiedKeyframe = kf) }
        notify("Copied keyframe")
    }

    fun pasteKeyframeAtPlayhead() {
        val clipKf = _uiState.value.copiedKeyframe ?: return
        val frame = _uiState.value.scene.timeline.currentFrame.toInt()
        val pasted = clipKf.copy(id = UUID.randomUUID().toString(), frame = frame)
        mutateScene { doc ->
            doc.copy(timeline = doc.timeline.copy(keyframes = doc.timeline.keyframes + pasted))
        }
        selectKeyframe(pasted.id)
        notify("Pasted keyframe at F$frame")
    }

    fun moveSelectedKeyframe(newFrame: Int) {
        val kfId = _uiState.value.selectedKeyframeId ?: return
        val clamped = newFrame.coerceIn(0, _uiState.value.scene.timeline.totalFrames)
        mutateScene { doc ->
            doc.copy(timeline = doc.timeline.copy(keyframes = doc.timeline.keyframes.map {
                if (it.id == kfId) it.copy(frame = clamped) else it
            }))
        }
    }

    fun applyAnimationPresetToSelected(preset: AnimationPresetInfo) {
        val node = selectedNode() ?: _uiState.value.scene.nodes.firstOrNull { it.skeleton != null }
        if (node == null) {
            notify("Add a rigged character to apply skeletal animation")
            return
        }
        val start = _uiState.value.scene.timeline.currentFrame.toInt()
        val clip = AnimationClipTrack(
            targetNodeId = node.id,
            clipName = preset.name,
            presetId = preset.id,
            startFrame = start,
            durationFrames = preset.defaultDurationFrames,
            speed = 1.0f,
            loop = true,
            colorHex = preset.accentHex
        )
        mutateScene { doc ->
            // Ensure character has a skeleton if applying to a plain mesh
            val ensuredNodes = doc.nodes.map { n ->
                if (n.id == node.id && n.skeleton == null) {
                    val sk = CharacterMeshBuilder.createDefaultHumanoidSkeleton(1.0f)
                    n.copy(skeleton = sk, mesh = n.mesh?.let { MeshEditingOps.autoBindMeshToSkeleton(it, sk) })
                } else n
            }
            doc.copy(
                nodes = ensuredNodes,
                timeline = doc.timeline.copy(clips = doc.timeline.clips + clip)
            )
        }
        selectNode(node.id)
        closeModal()
        notify("Applied ${preset.name} to ${node.name}")
    }

    fun updateClip(clip: AnimationClipTrack) = mutateScene { doc ->
        doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.map { if (it.id == clip.id) clip else it }))
    }

    fun deleteClip(clipId: String) = mutateScene { doc ->
        doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.filterNot { it.id == clipId }))
    }

    // --- Library, Custom Character Creator & Blender Model Import ---

    fun addLibraryAssetToScene(asset: LibraryAssetEntity) {
        viewModelScope.launch {
            val node = repository.instantiateAssetNode(asset)
            if (_uiState.value.destination == AppDestination.HUB) {
                val doc = repository.createBlankProject("${asset.name} Scene")
                val updated = doc.copy(nodes = doc.nodes.filterNot { it.primitiveKind == PrimitiveKind.CUBE } + node)
                repository.saveProject(updated)
                openSceneInEditor(updated)
            } else {
                val offsetX = (_uiState.value.scene.nodes.count { it.nodeType == NodeType.CHARACTER } * 1.15f)
                val placed = node.copy(transform = node.transform.copy(position = Vec3(offsetX, 0f, 0f)))
                mutateScene { it.copy(nodes = it.nodes + placed) }
                selectNode(placed.id)
                closeModal()
            }
            notify("Added ${asset.name} to scene")
        }
    }

    fun togglePinAsset(asset: LibraryAssetEntity) {
        viewModelScope.launch { repository.togglePinAsset(asset) }
    }

    fun renameAsset(assetId: String, newName: String) {
        viewModelScope.launch { repository.renameAsset(assetId, newName) }
    }

    fun deleteAsset(asset: LibraryAssetEntity) {
        viewModelScope.launch {
            repository.deleteAsset(asset)
            notify("Removed ${asset.name}")
        }
    }

    fun importExternalModel(uri: Uri, addToCurrentScene: Boolean) {
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                ModelImporter.importFromUri(getApplication(), uri)
            }
            if (report.supported && report.importedNode != null) {
                repository.saveCustomNodeToLibrary(
                    node = report.importedNode,
                    assetCategory = if (report.boneCount > 0) AssetCategory.CHARACTERS else AssetCategory.MODELS,
                    characterCategory = CharacterCategory.BLENDER
                )
                if (addToCurrentScene && _uiState.value.destination == AppDestination.EDITOR) {
                    mutateScene { it.copy(nodes = it.nodes + report.importedNode) }
                    selectNode(report.importedNode.id)
                }
            }
            _uiState.update { it.copy(importReport = report) }
        }
    }

    fun updateCreatorSpec(transform: (CharacterBuildSpec) -> CharacterBuildSpec) {
        _uiState.update { it.copy(creatorSpec = transform(it.creatorSpec)) }
    }

    fun saveCustomCharacterFromCreator(openInScene: Boolean) {
        viewModelScope.launch {
            val spec = _uiState.value.creatorSpec
            val node = CharacterMeshBuilder.buildRiggedCharacterNode(spec)
            repository.saveCustomNodeToLibrary(
                node = node,
                assetCategory = AssetCategory.CHARACTERS,
                characterCategory = spec.category,
                costumePreset = spec.costume,
                secondaryColorHex = spec.secondaryColor,
                archetype = spec.bodyType
            )
            if (openInScene) {
                if (_uiState.value.destination == AppDestination.EDITOR) {
                    mutateScene { it.copy(nodes = it.nodes + node) }
                    selectNode(node.id)
                } else {
                    val doc = repository.createBlankProject("${spec.name} Stage")
                    val updated = doc.copy(nodes = doc.nodes.filterNot { it.primitiveKind == PrimitiveKind.CUBE } + node)
                    repository.saveProject(updated)
                    openSceneInEditor(updated)
                }
            }
            notify("Saved \"${spec.name}\" to local Character Library")
        }
    }

    // --- Offline Rendering & Export (MP4 & GLB) ---

    fun updateExportConfig(
        format: ExportFormat = _uiState.value.exportState.format,
        resolution: ExportResolution = _uiState.value.exportState.resolution,
        fps: Int = _uiState.value.exportState.fps,
        startFrame: Int = _uiState.value.exportState.startFrame,
        endFrame: Int = _uiState.value.exportState.endFrame,
        quality: RenderQuality = _uiState.value.exportState.quality
    ) {
        _uiState.update {
            it.copy(
                exportState = it.exportState.copy(
                    format = format,
                    resolution = resolution,
                    fps = fps,
                    startFrame = startFrame.coerceAtLeast(0),
                    endFrame = endFrame.coerceAtLeast(startFrame + 1),
                    quality = quality,
                    errorMessage = null
                )
            )
        }
    }

    fun startExport() {
        if (_uiState.value.exportState.isExporting) return
        stopPlayback()
        val cfg = _uiState.value.exportState
        val doc = _uiState.value.scene

        _uiState.update {
            it.copy(exportState = cfg.copy(isExporting = true, progress = 0f, completedUri = null, completedFileName = null, errorMessage = null))
        }

        exportJob = viewModelScope.launch {
            val result = when (cfg.format) {
                ExportFormat.MP4 -> VideoExporter.exportToMp4(
                    context = getApplication(),
                    scene = doc,
                    request = VideoExportRequest(cfg.resolution, cfg.fps, cfg.startFrame, cfg.endFrame, cfg.quality),
                    onProgress = { pct ->
                        _uiState.update { s -> s.copy(exportState = s.exportState.copy(progress = pct)) }
                    }
                )
                ExportFormat.GLB -> withContext(Dispatchers.IO) {
                    _uiState.update { s -> s.copy(exportState = s.exportState.copy(progress = 0.5f)) }
                    GlbExporter.exportSceneToGlb(getApplication(), doc)
                }
            }
            result.fold(
                onSuccess = { (uri, name) ->
                    _uiState.update {
                        it.copy(
                            exportState = it.exportState.copy(
                                isExporting = false,
                                progress = 1f,
                                completedUri = uri,
                                completedFileName = name
                            )
                        )
                    }
                    notify("Exported $name offline")
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            exportState = it.exportState.copy(
                                isExporting = false,
                                errorMessage = err.message ?: "Export failed on this device"
                            )
                        )
                    }
                }
            )
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(exportState = it.exportState.copy(isExporting = false, progress = 0f)) }
    }

    // --- Helpers ---

    private fun selectedNode(): SceneNode? =
        _uiState.value.scene.nodes.firstOrNull { it.id == _uiState.value.selectedNodeId }

    private fun upsertKeyframeInTimeline(
        tl: com.example.engine.model.TimelineState,
        nodeId: String,
        boneId: String?,
        frame: Int,
        prop: KeyframeProperty,
        value: Vec3
    ): com.example.engine.model.TimelineState {
        val filtered = tl.keyframes.filterNot {
            it.targetNodeId == nodeId && it.targetBoneId == boneId && it.frame == frame && it.property == prop
        }
        return tl.copy(
            keyframes = filtered + Keyframe(
                targetNodeId = nodeId,
                targetBoneId = boneId,
                frame = frame,
                property = prop,
                vec3Value = value
            )
        )
    }

    private fun notify(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }
}
