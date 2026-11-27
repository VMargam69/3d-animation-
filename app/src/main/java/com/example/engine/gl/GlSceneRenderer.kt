package com.example.engine.gl

import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.animation.EvaluatedScene
import com.example.engine.model.Aabb3D
import com.example.engine.model.CameraPreset
import com.example.engine.model.EditSubMode
import com.example.engine.model.EditorMode
import com.example.engine.model.LightType
import com.example.engine.model.Math3D
import com.example.engine.model.MeshData
import com.example.engine.model.NodeType
import com.example.engine.model.SceneDocument
import com.example.engine.model.ShadingMode
import com.example.engine.model.SkeletonData
import com.example.engine.model.TransformAxis
import com.example.engine.model.TransformTool
import com.example.engine.model.Vec3
import com.example.engine.model.ViewportCameraState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

data class RenderConfig(
    val scene: SceneDocument = SceneDocument(),
    val selectedNodeId: String? = null,
    val selectedBoneId: String? = null,
    val editorMode: EditorMode = EditorMode.OBJECT,
    val editSubMode: EditSubMode = EditSubMode.FACE,
    val selectedElementIndex: Int = 0,
    val shadingMode: ShadingMode = ShadingMode.MATERIAL,
    val transformTool: TransformTool = TransformTool.MOVE,
    val transformAxis: TransformAxis = TransformAxis.ALL,
    val showGrid: Boolean = true,
    val showSkeleton: Boolean = true,
    val showGizmo: Boolean = true
)

class GlSceneRenderer : GLSurfaceView.Renderer {

    @Volatile
    var config: RenderConfig = RenderConfig()

    private var meshProgram = 0
    private var lineProgram = 0
    private var viewportW = 1
    private var viewportH = 1

    private val lastViewMatrix = Math3D.identity()
    private val lastProjMatrix = Math3D.identity()

    private val gridLineBuffer: FloatBuffer by lazy { buildGridAndAxesBuffer() }
    private var gridVertexCount = 0

    // Cached GPU textures mapped by file path
    private val textureCache = HashMap<String, Int>()

    // Reusable direct buffers for dynamic lines/gizmos
    private var initialized = false

    fun initGl() {
        if (initialized) return
        meshProgram = ShaderLibrary.compileProgram(ShaderLibrary.MESH_VERTEX_SHADER, ShaderLibrary.MESH_FRAGMENT_SHADER)
        lineProgram = ShaderLibrary.compileProgram(ShaderLibrary.LINE_VERTEX_SHADER, ShaderLibrary.LINE_FRAGMENT_SHADER)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        initialized = true
    }

    fun releaseGl() {
        if (!initialized) return
        if (meshProgram != 0) GLES30.glDeleteProgram(meshProgram)
        if (lineProgram != 0) GLES30.glDeleteProgram(lineProgram)
        if (textureCache.isNotEmpty()) {
            val ids = textureCache.values.toIntArray()
            GLES30.glDeleteTextures(ids.size, ids, 0)
            textureCache.clear()
        }
        meshProgram = 0
        lineProgram = 0
        initialized = false
    }

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        initialized = false
        textureCache.clear()
        initGl()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportW, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        val cfg = config
        val evaluated = AnimationEvaluator.evaluate(cfg.scene, cfg.scene.timeline.currentFrame)
        renderSceneFrame(
            width = viewportW,
            height = viewportH,
            cfg = cfg,
            evaluated = evaluated,
            forExport = false
        )
    }

    /**
     * Core frame draw routine shared by both live GLSurfaceView AND offscreen EGL MP4 Exporter.
     */
    fun renderSceneFrame(
        width: Int,
        height: Int,
        cfg: RenderConfig,
        evaluated: EvaluatedScene,
        forExport: Boolean
    ) {
        if (!initialized) initGl()
        GLES30.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        GLES30.glClearColor(0.055f, 0.063f, 0.086f, 1.0f) // #0E1015 dark charcoal
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val (camPos, viewMat, projMat) = computeCameraMatrices(cfg.scene, evaluated, aspect, forExport)
        System.arraycopy(viewMat, 0, lastViewMatrix, 0, 16)
        System.arraycopy(projMat, 0, lastProjMatrix, 0, 16)
        val viewProj = Math3D.multiply(projMat, viewMat)

        // 1. Floor Grid & World XYZ Axes (omitted during final video export unless requested)
        if (cfg.showGrid && !forExport) {
            drawColoredLines(gridLineBuffer, gridVertexCount, viewProj, GLES30.GL_LINES, 1.5f)
        }

        // 2. Collect visible lights (up to 4)
        val lightNodes = cfg.scene.nodes.filter {
            it.nodeType == NodeType.LIGHT && (evaluated.nodeStates[it.id]?.visible == true)
        }.take(4)

        val lightPosArr = FloatArray(12)
        val lightColorArr = FloatArray(12)
        val lightIntArr = FloatArray(4)
        val lightTypeArr = IntArray(4)
        lightNodes.forEachIndexed { i, ln ->
            val st = evaluated.nodeStates[ln.id]
            val p = st?.transform?.position ?: ln.transform.position
            lightPosArr[i * 3] = p.x; lightPosArr[i * 3 + 1] = p.y; lightPosArr[i * 3 + 2] = p.z
            val rgb = Math3D.colorHexToRgbFloats(ln.light?.color ?: 0xFFFFFFFF)
            lightColorArr[i * 3] = rgb[0]; lightColorArr[i * 3 + 1] = rgb[1]; lightColorArr[i * 3 + 2] = rgb[2]
            lightIntArr[i] = ln.light?.intensity ?: 1.4f
            lightTypeArr[i] = when (ln.light?.lightType ?: LightType.POINT) {
                LightType.POINT -> 0
                LightType.DIRECTIONAL -> 1
                LightType.SPOT -> 2
            }
        }

        // 3. Draw 3D Meshes & Skinned Characters
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram, "uView"), 1, false, viewMat, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram, "uProjection"), 1, false, projMat, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProgram, "uCameraPos"), camPos.x, camPos.y, camPos.z)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uLightCount"), lightNodes.size)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(meshProgram, "uLightPos"), 4, lightPosArr, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(meshProgram, "uLightColor"), 4, lightColorArr, 0)
        GLES30.glUniform1fv(GLES30.glGetUniformLocation(meshProgram, "uLightIntensity"), 4, lightIntArr, 0)
        GLES30.glUniform1iv(GLES30.glGetUniformLocation(meshProgram, "uLightType"), 4, lightTypeArr, 0)

        val shadingInt = when (if (forExport) ShadingMode.MATERIAL else cfg.shadingMode) {
            ShadingMode.SOLID -> 0
            ShadingMode.MATERIAL -> 1
            ShadingMode.WIREFRAME -> 2
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uShadingMode"), shadingInt)

        for (node in cfg.scene.nodes) {
            val state = evaluated.nodeStates[node.id] ?: continue
            if (!state.visible) continue
            val mesh = node.mesh ?: continue
            if (mesh.vertexCount == 0 || mesh.indices.isEmpty()) continue

            val isSelected = !forExport && node.id == cfg.selectedNodeId

            if (shadingInt != 2) {
                drawMeshSolid(
                    mesh = mesh,
                    worldMatrix = state.worldMatrix,
                    material = node.material,
                    skeleton = node.skeleton,
                    bonePose = state.boneRotations,
                    isSelected = isSelected
                )
            }

            // Wireframe overlay for Wireframe mode or Edit Mode selected mesh
            if (shadingInt == 2 || (!forExport && isSelected && cfg.editorMode == EditorMode.EDIT)) {
                drawMeshWireframeOverlay(
                    mesh = mesh,
                    worldMatrix = state.worldMatrix,
                    skeleton = node.skeleton,
                    bonePose = state.boneRotations,
                    viewProj = viewProj,
                    isEditMode = !forExport && isSelected && cfg.editorMode == EditorMode.EDIT,
                    editSubMode = cfg.editSubMode,
                    selectedElementIndex = cfg.selectedElementIndex
                )
            }
        }

        if (!forExport) {
            // 4. Draw Light & Camera Viewport Markers
            drawHelpersForLightsAndCameras(cfg.scene, evaluated, cfg.selectedNodeId, viewProj)

            // 5. Draw Skeleton Armatures (always in Pose mode, or when showSkeleton enabled)
            if (cfg.showSkeleton || cfg.editorMode == EditorMode.POSE) {
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                for (node in cfg.scene.nodes) {
                    val skel = node.skeleton ?: continue
                    val state = evaluated.nodeStates[node.id] ?: continue
                    if (!state.visible) continue
                    val highlightAll = node.id == cfg.selectedNodeId
                    drawSkeletonOverlay(skel, state.worldMatrix, state.boneRotations, cfg.selectedBoneId, highlightAll, viewProj)
                }
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            }

            // 6. Draw 3D Transform Gizmo on selected object
            if (cfg.showGizmo && cfg.selectedNodeId != null && cfg.editorMode == EditorMode.OBJECT) {
                val selState = evaluated.nodeStates[cfg.selectedNodeId]
                if (selState != null && selState.visible) {
                    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                    drawTransformGizmo(selState.transform.position, cfg.transformAxis, viewProj)
                    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                }
            }
        }
    }

    /**
     * Unprojects a screen touch (x, y) into a 3D world ray and picks the nearest visible object or bone.
     */
    fun pickAtScreenPoint(touchX: Float, touchY: Float): Pair<String?, String?> {
        val cfg = config
        val evaluated = AnimationEvaluator.evaluate(cfg.scene, cfg.scene.timeline.currentFrame)
        val ray = Math3D.unprojectRay(touchX, touchY, viewportW, viewportH, lastViewMatrix, lastProjMatrix)

        // In Pose mode, prioritize picking bones on selected rigged character
        if (cfg.editorMode == EditorMode.POSE && cfg.selectedNodeId != null) {
            val node = cfg.scene.nodes.firstOrNull { it.id == cfg.selectedNodeId }
            val skel = node?.skeleton
            val state = node?.let { evaluated.nodeStates[it.id] }
            if (skel != null && state != null) {
                val joints = skel.computeWorldJointPositions(state.boneRotations)
                var bestBone: String? = null
                var bestDist = Float.POSITIVE_INFINITY
                for (bone in skel.bones) {
                    val (lh, lt) = joints[bone.id] ?: continue
                    val wh = Math3D.transformPoint(state.worldMatrix, lh)
                    val wt = Math3D.transformPoint(state.worldMatrix, lt)
                    val mid = (wh + wt) * 0.5f
                    val hit = Math3D.raySphereHitDistance(ray, mid, (bone.length * 0.55f).coerceAtLeast(0.22f))
                    if (hit != null && hit < bestDist) {
                        bestDist = hit
                        bestBone = bone.id
                    }
                }
                if (bestBone != null) return node.id to bestBone
            }
        }

        var bestNodeId: String? = null
        var bestDist = Float.POSITIVE_INFINITY
        for (node in cfg.scene.nodes) {
            val state = evaluated.nodeStates[node.id] ?: continue
            if (!state.visible) continue
            val localBounds = node.mesh?.computeLocalBounds() ?: Aabb3D(Vec3(-0.35f, -0.35f, -0.35f), Vec3(0.35f, 0.35f, 0.35f))
            val worldMin = Math3D.transformPoint(state.worldMatrix, localBounds.min)
            val worldMax = Math3D.transformPoint(state.worldMatrix, localBounds.max)
            val aabb = Aabb3D(
                Vec3(minOf(worldMin.x, worldMax.x) - 0.12f, minOf(worldMin.y, worldMax.y) - 0.12f, minOf(worldMin.z, worldMax.z) - 0.12f),
                Vec3(maxOf(worldMin.x, worldMax.x) + 0.12f, maxOf(worldMin.y, worldMax.y) + 0.12f, maxOf(worldMin.z, worldMax.z) + 0.12f)
            )
            val hit = Math3D.rayAabbHitDistance(ray, aabb)
            if (hit != null && hit < bestDist) {
                bestDist = hit
                bestNodeId = node.id
            }
        }
        return bestNodeId to null
    }

    private fun computeCameraMatrices(
        scene: SceneDocument,
        evaluated: EvaluatedScene,
        aspect: Float,
        forExport: Boolean
    ): Triple<Vec3, FloatArray, FloatArray> {
        val cam = scene.viewportCamera
        val viewMat = Math3D.identity()
        val projMat = Math3D.identity()

        val activeSceneCam = scene.nodes.firstOrNull { it.nodeType == NodeType.CAMERA && (it.camera?.isActive == true) }
        if ((cam.preset == CameraPreset.CAMERA_VIEW || forExport) && activeSceneCam != null) {
            val camState = evaluated.nodeStates[activeSceneCam.id]
            val pos = camState?.transform?.position ?: activeSceneCam.transform.position
            val fov = (camState?.cameraFov ?: activeSceneCam.camera?.fov ?: 45f).coerceIn(18f, 105f)
            // Look at scene target or forward vector
            val target = cam.target
            Matrix.setLookAtM(viewMat, 0, pos.x, pos.y, pos.z, target.x, target.y, target.z, 0f, 1f, 0f)
            Matrix.perspectiveM(projMat, 0, fov, aspect, 0.1f, 150f)
            return Triple(pos, viewMat, projMat)
        }

        val (yaw, pitch) = when (cam.preset) {
            CameraPreset.FRONT -> 0f to 0f
            CameraPreset.BACK -> 180f to 0f
            CameraPreset.LEFT -> -90f to 0f
            CameraPreset.RIGHT -> 90f to 0f
            CameraPreset.TOP -> 0f to 85f
            CameraPreset.BOTTOM -> 0f to -85f
            CameraPreset.FREE, CameraPreset.CAMERA_VIEW -> cam.yawDeg to cam.pitchDeg
        }
        val eye = Math3D.orbitCameraPosition(cam.target, yaw, pitch, cam.distance.coerceIn(1.2f, 40f))
        Matrix.setLookAtM(viewMat, 0, eye.x, eye.y, eye.z, cam.target.x, cam.target.y, cam.target.z, 0f, 1f, 0f)

        if (cam.orthographic) {
            val halfH = cam.distance * 0.42f
            val halfW = halfH * aspect
            Matrix.orthoM(projMat, 0, -halfW, halfW, -halfH, halfH, 0.05f, 150f)
        } else {
            Matrix.perspectiveM(projMat, 0, 45f, aspect, 0.1f, 150f)
        }
        return Triple(eye, viewMat, projMat)
    }

    private fun drawMeshSolid(
        mesh: MeshData,
        worldMatrix: FloatArray,
        material: com.example.engine.model.MaterialData,
        skeleton: SkeletonData?,
        bonePose: Map<String, Vec3>,
        isSelected: Boolean
    ) {
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram, "uModel"), 1, false, worldMatrix, 0)

        val rgb = Math3D.colorHexToRgbFloats(material.baseColor)
        val emRgb = Math3D.colorHexToRgbFloats(material.emissionColor)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProgram, "uBaseColor"), rgb[0], rgb[1], rgb[2])
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProgram, "uRoughness"), material.roughness)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProgram, "uMetallic"), material.metallic)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProgram, "uOpacity"), material.opacity)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProgram, "uEmissionColor"), emRgb[0], emRgb[1], emRgb[2])
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProgram, "uEmissionStrength"), material.emissionStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProgram, "uUvScale"), material.uvScale)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uIsSelected"), if (isSelected) 1 else 0)

        val texId = material.texturePath?.let { getOrLoadTexture(it) } ?: 0
        if (texId != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uTexture"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uHasTexture"), 1)
        } else {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uHasTexture"), 0)
        }

        val useSkinning = skeleton != null && skeleton.bones.isNotEmpty()
        GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProgram, "uUseSkinning"), if (useSkinning) 1 else 0)
        if (useSkinning) {
            val palette = skeleton!!.computeSkinningPalette(bonePose)
            GLES30.glUniformMatrix4fv(
                GLES30.glGetUniformLocation(meshProgram, "uBoneMatrices"),
                SkeletonData.MAX_BONES,
                false,
                palette,
                0
            )
        }

        val vBuf = toFloatBuffer(mesh.vertices)
        val nBuf = toFloatBuffer(mesh.normals)
        val uvBuf = toFloatBuffer(mesh.uvs)
        val jIdxFloats = FloatArray(mesh.jointIndices.size) { mesh.jointIndices[it].toFloat() }
        val jIdxBuf = toFloatBuffer(jIdxFloats)
        val jWgtBuf = toFloatBuffer(mesh.jointWeights)
        val cArr = if (mesh.colors.size == mesh.vertices.size) mesh.colors else FloatArray(mesh.vertices.size) { 1f }
        val cBuf = toFloatBuffer(cArr)
        val iBuf = toIntBuffer(mesh.indices)

        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(meshProgram, "uUseVertexColors"),
            if (mesh.useVertexColors) 1 else 0
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vBuf)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, nBuf)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 0, uvBuf)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, 0, jIdxBuf)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, 0, jWgtBuf)
        GLES30.glEnableVertexAttribArray(5)
        GLES30.glVertexAttribPointer(5, 3, GLES30.GL_FLOAT, false, 0, cBuf)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indices.size, GLES30.GL_UNSIGNED_INT, iBuf)

        for (loc in 0..5) GLES30.glDisableVertexAttribArray(loc)
    }

    private fun drawMeshWireframeOverlay(
        mesh: MeshData,
        worldMatrix: FloatArray,
        skeleton: SkeletonData?,
        bonePose: Map<String, Vec3>,
        viewProj: FloatArray,
        isEditMode: Boolean,
        editSubMode: EditSubMode,
        selectedElementIndex: Int
    ) {
        val skinPalette = if (skeleton != null && skeleton.bones.isNotEmpty()) {
            skeleton.computeSkinningPalette(bonePose)
        } else null

        val worldVerts = Array(mesh.vertexCount) { i ->
            var p = Vec3(mesh.vertices[i * 3], mesh.vertices[i * 3 + 1], mesh.vertices[i * 3 + 2])
            if (skinPalette != null) {
                val b0 = mesh.jointIndices[i * 4].coerceIn(0, SkeletonData.MAX_BONES - 1)
                val mat = skinPalette.copyOfRange(b0 * 16, b0 * 16 + 16)
                p = Math3D.transformPoint(mat, p)
            }
            Math3D.transformPoint(worldMatrix, p)
        }

        val maxTris = mesh.triangleCount.coerceAtMost(2400)
        val lineData = FloatArray(maxTris * 6 * 7) // 3 edges * 2 pts * (xyz + rgba)
        var ptr = 0
        for (t in 0 until maxTris) {
            val i0 = mesh.indices[t * 3]; val i1 = mesh.indices[t * 3 + 1]; val i2 = mesh.indices[t * 3 + 2]
            val highlightFace = isEditMode && editSubMode == EditSubMode.FACE && t == (selectedElementIndex % maxTris.coerceAtLeast(1))
            val r = if (highlightFace) 0.13f else 0.39f
            val g = if (highlightFace) 0.83f else 0.40f
            val b = if (highlightFace) 0.93f else 0.95f
            val a = if (highlightFace) 1.0f else 0.65f
            val pairs = arrayOf(i0 to i1, i1 to i2, i2 to i0)
            for ((pa, pb) in pairs) {
                val va = worldVerts[pa]; val vb = worldVerts[pb]
                lineData[ptr++] = va.x; lineData[ptr++] = va.y; lineData[ptr++] = va.z
                lineData[ptr++] = r; lineData[ptr++] = g; lineData[ptr++] = b; lineData[ptr++] = a
                lineData[ptr++] = vb.x; lineData[ptr++] = vb.y; lineData[ptr++] = vb.z
                lineData[ptr++] = r; lineData[ptr++] = g; lineData[ptr++] = b; lineData[ptr++] = a
            }
        }
        drawColoredLines(toFloatBuffer(lineData), maxTris * 6, viewProj, GLES30.GL_LINES, 1.5f)

        // Draw vertex points in Edit Mode
        if (isEditMode) {
            val vc = worldVerts.size.coerceAtMost(1500)
            val ptData = FloatArray(vc * 7)
            var pp = 0
            val selV = selectedElementIndex % vc.coerceAtLeast(1)
            for (i in 0 until vc) {
                val p = worldVerts[i]
                val isSel = editSubMode == EditSubMode.VERTEX && i == selV
                ptData[pp++] = p.x; ptData[pp++] = p.y; ptData[pp++] = p.z
                ptData[pp++] = if (isSel) 0.13f else 0.96f
                ptData[pp++] = if (isSel) 0.83f else 0.62f
                ptData[pp++] = if (isSel) 0.93f else 0.04f
                ptData[pp++] = 1.0f
            }
            drawColoredLines(toFloatBuffer(ptData), vc, viewProj, GLES30.GL_POINTS, 9.0f)
        }
    }

    private fun drawSkeletonOverlay(
        skeleton: SkeletonData,
        worldMatrix: FloatArray,
        bonePose: Map<String, Vec3>,
        selectedBoneId: String?,
        isNodeSelected: Boolean,
        viewProj: FloatArray
    ) {
        if (skeleton.bones.isEmpty()) return
        val joints = skeleton.computeWorldJointPositions(bonePose)
        val lineFloats = ArrayList<Float>()
        val pointFloats = ArrayList<Float>()

        for (bone in skeleton.bones) {
            val (lh, lt) = joints[bone.id] ?: continue
            val h = Math3D.transformPoint(worldMatrix, lh)
            val t = Math3D.transformPoint(worldMatrix, lt)
            val isSel = bone.id == selectedBoneId
            val r = if (isSel) 0.13f else if (isNodeSelected) 0.96f else 0.60f
            val g = if (isSel) 0.83f else if (isNodeSelected) 0.62f else 0.65f
            val b = if (isSel) 0.93f else if (isNodeSelected) 0.04f else 0.80f

            // Main bone axis + diamond bone outline
            val dir = (t - h)
            val len = dir.length().coerceAtLeast(0.05f)
            val side = Vec3(0.045f * (len / 0.4f).coerceIn(0.6f, 1.5f), 0f, 0f)
            val mid = Vec3.lerp(h, t, 0.25f)
            val p1 = mid + side
            val p2 = mid - side

            fun addSeg(a: Vec3, bPt: Vec3) {
                lineFloats.add(a.x); lineFloats.add(a.y); lineFloats.add(a.z)
                lineFloats.add(r); lineFloats.add(g); lineFloats.add(b); lineFloats.add(1f)
                lineFloats.add(bPt.x); lineFloats.add(bPt.y); lineFloats.add(bPt.z)
                lineFloats.add(r); lineFloats.add(g); lineFloats.add(b); lineFloats.add(1f)
            }
            addSeg(h, p1); addSeg(p1, t)
            addSeg(h, p2); addSeg(p2, t)
            addSeg(p1, p2)

            pointFloats.add(h.x); pointFloats.add(h.y); pointFloats.add(h.z)
            pointFloats.add(r); pointFloats.add(g); pointFloats.add(b); pointFloats.add(1f)
        }
        drawColoredLines(toFloatBuffer(lineFloats.toFloatArray()), lineFloats.size / 7, viewProj, GLES30.GL_LINES, 2.5f)
        drawColoredLines(toFloatBuffer(pointFloats.toFloatArray()), pointFloats.size / 7, viewProj, GLES30.GL_POINTS, 12f)
    }

    private fun drawHelpersForLightsAndCameras(
        scene: SceneDocument,
        evaluated: EvaluatedScene,
        selectedNodeId: String?,
        viewProj: FloatArray
    ) {
        val segs = ArrayList<Float>()
        fun addSeg(a: Vec3, b: Vec3, r: Float, g: Float, bl: Float) {
            segs.add(a.x); segs.add(a.y); segs.add(a.z); segs.add(r); segs.add(g); segs.add(bl); segs.add(1f)
            segs.add(b.x); segs.add(b.y); segs.add(b.z); segs.add(r); segs.add(g); segs.add(bl); segs.add(1f)
        }
        for (node in scene.nodes) {
            val st = evaluated.nodeStates[node.id] ?: continue
            if (!st.visible) continue
            val p = st.transform.position
            val sel = node.id == selectedNodeId
            when (node.nodeType) {
                NodeType.LIGHT -> {
                    val c = if (sel) floatArrayOf(0.13f, 0.83f, 0.93f) else floatArrayOf(0.96f, 0.62f, 0.04f)
                    val s = 0.24f
                    addSeg(p + Vec3(-s, 0f, 0f), p + Vec3(s, 0f, 0f), c[0], c[1], c[2])
                    addSeg(p + Vec3(0f, -s, 0f), p + Vec3(0f, s, 0f), c[0], c[1], c[2])
                    addSeg(p + Vec3(0f, 0f, -s), p + Vec3(0f, 0f, s), c[0], c[1], c[2])
                    addSeg(p, Vec3(p.x, 0f, p.z), c[0] * 0.5f, c[1] * 0.5f, c[2] * 0.5f)
                }
                NodeType.CAMERA -> {
                    val c = if (sel) floatArrayOf(0.13f, 0.83f, 0.93f) else floatArrayOf(0.65f, 0.72f, 0.90f)
                    val f = Vec3(0f, 0f, -0.45f)
                    val c1 = p + f + Vec3(-0.22f, 0.14f, 0f)
                    val c2 = p + f + Vec3(0.22f, 0.14f, 0f)
                    val c3 = p + f + Vec3(0.22f, -0.14f, 0f)
                    val c4 = p + f + Vec3(-0.22f, -0.14f, 0f)
                    addSeg(p, c1, c[0], c[1], c[2]); addSeg(p, c2, c[0], c[1], c[2])
                    addSeg(p, c3, c[0], c[1], c[2]); addSeg(p, c4, c[0], c[1], c[2])
                    addSeg(c1, c2, c[0], c[1], c[2]); addSeg(c2, c3, c[0], c[1], c[2])
                    addSeg(c3, c4, c[0], c[1], c[2]); addSeg(c4, c1, c[0], c[1], c[2])
                }
                else -> Unit
            }
        }
        if (segs.isNotEmpty()) {
            drawColoredLines(toFloatBuffer(segs.toFloatArray()), segs.size / 7, viewProj, GLES30.GL_LINES, 2.2f)
        }
    }

    private fun drawTransformGizmo(origin: Vec3, activeAxis: TransformAxis, viewProj: FloatArray) {
        val len = 0.95f
        val segs = ArrayList<Float>()
        fun axis(dir: Vec3, r: Float, g: Float, b: Float, bright: Boolean) {
            val boost = if (bright) 1.0f else 0.45f
            val tip = origin + dir * len
            segs.add(origin.x); segs.add(origin.y); segs.add(origin.z)
            segs.add(r * boost); segs.add(g * boost); segs.add(b * boost); segs.add(1f)
            segs.add(tip.x); segs.add(tip.y); segs.add(tip.z)
            segs.add(r * boost); segs.add(g * boost); segs.add(b * boost); segs.add(1f)
        }
        axis(Vec3(1f, 0f, 0f), 0.94f, 0.27f, 0.27f, activeAxis == TransformAxis.ALL || activeAxis == TransformAxis.X)
        axis(Vec3(0f, 1f, 0f), 0.13f, 0.77f, 0.37f, activeAxis == TransformAxis.ALL || activeAxis == TransformAxis.Y)
        axis(Vec3(0f, 0f, 1f), 0.23f, 0.51f, 0.96f, activeAxis == TransformAxis.ALL || activeAxis == TransformAxis.Z)
        drawColoredLines(toFloatBuffer(segs.toFloatArray()), segs.size / 7, viewProj, GLES30.GL_LINES, 4.0f)
    }

    private fun drawColoredLines(
        interleavedBuffer: FloatBuffer,
        vertexCount: Int,
        viewProj: FloatArray,
        mode: Int,
        widthOrPointSize: Float
    ) {
        if (vertexCount <= 0) return
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(lineProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(lineProgram, "uPointSize"), widthOrPointSize)
        if (mode == GLES30.GL_LINES) GLES30.glLineWidth(widthOrPointSize)

        val stride = 7 * 4
        interleavedBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, interleavedBuffer)
        interleavedBuffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, interleavedBuffer)

        GLES30.glDrawArrays(mode, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun buildGridAndAxesBuffer(): FloatBuffer {
        val list = ArrayList<Float>()
        val half = 8
        fun addLine(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, r: Float, g: Float, b: Float, a: Float) {
            list.add(x1); list.add(y1); list.add(z1); list.add(r); list.add(g); list.add(b); list.add(a)
            list.add(x2); list.add(y2); list.add(z2); list.add(r); list.add(g); list.add(b); list.add(a)
        }
        for (i in -half..half) {
            if (i == 0) continue
            val alpha = if (abs(i) % 4 == 0) 0.40f else 0.18f
            addLine(i.toFloat(), 0f, -half.toFloat(), i.toFloat(), 0f, half.toFloat(), 0.25f, 0.30f, 0.42f, alpha)
            addLine(-half.toFloat(), 0f, i.toFloat(), half.toFloat(), 0f, i.toFloat(), 0.25f, 0.30f, 0.42f, alpha)
        }
        // World X (Red) and Z (Blue) primary grid lines
        addLine(-half.toFloat(), 0.002f, 0f, half.toFloat(), 0.002f, 0f, 0.90f, 0.27f, 0.27f, 0.85f)
        addLine(0f, 0.002f, -half.toFloat(), 0f, 0.002f, half.toFloat(), 0.23f, 0.51f, 0.96f, 0.85f)
        // Subtle Y vertical origin indicator
        addLine(0f, 0f, 0f, 0f, 1.5f, 0f, 0.13f, 0.77f, 0.37f, 0.55f)
        gridVertexCount = list.size / 7
        return toFloatBuffer(list.toFloatArray())
    }

    private fun getOrLoadTexture(path: String): Int {
        textureCache[path]?.let { return it }
        val file = File(path)
        if (!file.exists()) return 0
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texId = ids[0]
        if (texId != 0) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            textureCache[path] = texId
        }
        bmp.recycle()
        return texId
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(arr)
            position(0)
        }

    private fun toIntBuffer(arr: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
            put(arr)
            position(0)
        }
}
