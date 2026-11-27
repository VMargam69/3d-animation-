package com.example.viewmodel

import android.net.Uri
import com.example.engine.model.CameraPreset
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.EditSubMode
import com.example.engine.model.EditorMode
import com.example.engine.model.ExportFormat
import com.example.engine.model.ExportResolution
import com.example.engine.model.ImportInspectionReport
import com.example.engine.model.Keyframe
import com.example.engine.model.RenderQuality
import com.example.engine.model.SceneDocument
import com.example.engine.model.ShadingMode
import com.example.engine.model.TransformAxis
import com.example.engine.model.TransformTool

enum class AppDestination {
    HUB,
    EDITOR
}

enum class HubTab(val label: String) {
    PROJECTS("Projects"),
    CHARACTERS("Characters"),
    ASSETS("Assets"),
    CREATOR("Creator")
}

enum class InspectorTab(val label: String) {
    OUTLINER("Scene"),
    TRANSFORM("Transform"),
    MODEL("Model"),
    MATERIAL("Material"),
    LIGHT_CAM("Light/Cam"),
    RIG("Rig / Pose")
}

enum class EditorModalSheet {
    NONE,
    CHARACTER_LIBRARY,
    ANIMATION_LIBRARY,
    ADD_PRIMITIVE,
    EXPORT,
    BLENDER_GUIDE
}

data class ExportUiState(
    val format: ExportFormat = ExportFormat.MP4,
    val resolution: ExportResolution = ExportResolution.HD_720,
    val fps: Int = 30,
    val startFrame: Int = 0,
    val endFrame: Int = 120,
    val quality: RenderQuality = RenderQuality.MEDIUM,
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val completedUri: Uri? = null,
    val completedFileName: String? = null,
    val errorMessage: String? = null
)

data class StudioUiState(
    val destination: AppDestination = AppDestination.HUB,
    val hubTab: HubTab = HubTab.PROJECTS,
    val scene: SceneDocument = SceneDocument(),
    val selectedNodeId: String? = null,
    val selectedBoneId: String? = null,
    val selectedKeyframeId: String? = null,
    val copiedKeyframe: Keyframe? = null,
    val editorMode: EditorMode = EditorMode.OBJECT,
    val editSubMode: EditSubMode = EditSubMode.FACE,
    val selectedElementIndex: Int = 0,
    val shadingMode: ShadingMode = ShadingMode.MATERIAL,
    val transformTool: TransformTool = TransformTool.MOVE,
    val transformAxis: TransformAxis = TransformAxis.ALL,
    val showGrid: Boolean = true,
    val showSkeleton: Boolean = true,
    val inspectorOpen: Boolean = false,
    val inspectorTab: InspectorTab = InspectorTab.TRANSFORM,
    val activeModal: EditorModalSheet = EditorModalSheet.NONE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isDirty: Boolean = false,
    val lastSavedTime: Long = 0L,
    val importReport: ImportInspectionReport? = null,
    val creatorSpec: CharacterBuildSpec = CharacterBuildSpec(name = "Custom Hero 01"),
    val exportState: ExportUiState = ExportUiState(),
    val toastMessage: String? = null
)
