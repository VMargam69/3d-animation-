package com.example.engine.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.animation.EvaluatedScene
import com.example.engine.animation.IkSolver
import com.example.engine.model.Aabb3D
import com.example.engine.model.BackdropPreset
import com.example.engine.model.CameraPreset
import com.example.engine.model.EditSubMode
import com.example.engine.model.EditorMode
import com.example.engine.model.IkLimb
import com.example.engine.model.LightType
import com.example.engine.model.MaterialData
import com.example.engine.model.Math3D
import com.example.engine.model.MeshData
import com.example.engine.model.NodeType
import com.example.engine.model.SceneDocument
import com.example.engine.model.ShadingMode
import com.example.engine.model.SkeletonData
import com.example.engine.model.SkinPreset
import com.example.engine.model.TransformAxis
import com.example.engine.model.TransformTool
import com.example.engine.model.Vec3
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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
    val showGizmo: Boolean = true,
    val ikEnabled: Boolean = true,
    val selectedIkLimb: IkLimb? = null,
    val showOnionSkin: Boolean = false,
    val onFpsSample: ((Int) -> Unit)? = null
)

private class GpuMeshHandle(
    val version: Long,
    val vertexCount: Int,
    val indexCount: Int,
    val vaoId: Int,
    val vboIds: IntArray, // 0=pos,1=norm,2=uv,3=jIdx,4=jWgt,5=col,6=ibo
    var lastUsedFrame: Long
) {
    fun release() {
        GLES30.glDeleteBuffers(vboIds.size, vboIds, 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
    }
}

/**
 * High-performance OpenGL ES 3.0 Scene Renderer featuring:
 * - Persistent VAO + VBO + IBO GPU mesh caching (zero per-frame DirectBuffer allocations for meshes)
 * - Live procedural 3D Sky Dome backdrop (zero VBO overhead)
 * - Procedural GPU SkinPreset texture synthesis + warm subsurface skin lighting + Pro Toon/Cel shading
 * - Real-time 3D IK diamond handles & camera-plane drag unprojection
 * - Onion Skinning ghost silhouette pass (-6f cyan, +6f amber)
 */
class GlSceneRenderer : GLSurfaceView.Renderer {

    @Volatile
    var config: RenderConfig = RenderConfig()

    private var skyProgram = 0
    private var meshProgram = 0
    private var lineProgram = 0
    private var viewportW = 1
    private var viewportH = 1
    private var frameCounter = 0L

    // FPS tracking (zero allocation)
    private var fpsWindowStartNs = 0L
    private var fpsFrameAcc = 0

    // Cached uniform locations for skyProgram
    private var uSkyZenith = -1
    private var uSkyHorizon = -1
    private var uSkyGround = -1
    private var uSkySun = -1
    private var uSkyShift = -1

    // Cached uniform locations for meshProgram
    private var uModel = -1
    private var uView = -1
    private var uProjection = -1
    private var uCameraPos = -1
    private var uBaseColor = -1
    private var uRoughness = -1
    private var uMetallic = -1
    private var uOpacity = -1
    private var uSubsurface = -1
    private var uEmissionColor = -1
    private var uEmissionStrength = -1
    private var uUvScale = -1
    private var uHasTexture = -1
    private var uTexture = -1
    private var uShadingMode = -1
    private var uIsSelected = -1
    private var uUseVertexColors = -1
    private var uGhostColor = -1
    private var uGhostAlpha = -1
    private var uUseSkinning = -1
    private var uBoneMatrices = -1
    private var uLightCount = -1
    private var uLightPos = -1
    private var uLightColor = -1
    private var uLightIntensity = -1
    private var uLightType = -1

    // Cached uniform locations for lineProgram
    private var uLineViewProj = -1
    private var uLinePointSize = -1

    // Persistent GPU VAO/VBO caches
    private val gpuMeshCache = HashMap<String, GpuMeshHandle>()
    private val textureCache = HashMap<String, Int>()
    private var gridVao = 0
    private var gridVbo = 0
    private var gridVertexCount = 0

    // Reusable dynamic buffer & scratch arrays to prevent GC churn during 60fps playback
    private var dynamicLineVbo = 0
    private var dynamicLineBuffer: FloatBuffer = allocateDirectFloatBuffer(4096)
    private val lightPosArr = FloatArray(12)
    private val lightColorArr = FloatArray(12)
    private val lightIntArr = FloatArray(4)
    private val lightTypeArr = IntArray(4)
    private val tempViewMat = FloatArray(16)
    private val tempProjMat = FloatArray(16)
    private val tempViewProj = FloatArray(16)

    private val lastViewMatrix = Math3D.identity()
    private val lastProjMatrix = Math3D.identity()
    @Volatile
    private var lastCameraPos = Vec3(0f, 1.8f, 5.5f)

    private var initialized = false

    fun initGl() {
        if (initialized) return
        skyProgram = ShaderLibrary.compileProgram(ShaderLibrary.SKY_VERTEX_SHADER, ShaderLibrary.SKY_FRAGMENT_SHADER)
        meshProgram = ShaderLibrary.compileProgram(ShaderLibrary.MESH_VERTEX_SHADER, ShaderLibrary.MESH_FRAGMENT_SHADER)
        lineProgram = ShaderLibrary.compileProgram(ShaderLibrary.LINE_VERTEX_SHADER, ShaderLibrary.LINE_FRAGMENT_SHADER)

        // Cache sky uniforms
        uSkyZenith = GLES30.glGetUniformLocation(skyProgram, "uZenithColor")
        uSkyHorizon = GLES30.glGetUniformLocation(skyProgram, "uHorizonColor")
        uSkyGround = GLES30.glGetUniformLocation(skyProgram, "uGroundColor")
        uSkySun = GLES30.glGetUniformLocation(skyProgram, "uSunColor")
        uSkyShift = GLES30.glGetUniformLocation(skyProgram, "uHorizonShift")

        // Cache mesh uniforms
        uModel = GLES30.glGetUniformLocation(meshProgram, "uModel")
        uView = GLES30.glGetUniformLocation(meshProgram, "uView")
        uProjection = GLES30.glGetUniformLocation(meshProgram, "uProjection")
        uCameraPos = GLES30.glGetUniformLocation(meshProgram, "uCameraPos")
        uBaseColor = GLES30.glGetUniformLocation(meshProgram, "uBaseColor")
        uRoughness = GLES30.glGetUniformLocation(meshProgram, "uRoughness")
        uMetallic = GLES30.glGetUniformLocation(meshProgram, "uMetallic")
        uOpacity = GLES30.glGetUniformLocation(meshProgram, "uOpacity")
        uSubsurface = GLES30.glGetUniformLocation(meshProgram, "uSubsurface")
        uEmissionColor = GLES30.glGetUniformLocation(meshProgram, "uEmissionColor")
        uEmissionStrength = GLES30.glGetUniformLocation(meshProgram, "uEmissionStrength")
        uUvScale = GLES30.glGetUniformLocation(meshProgram, "uUvScale")
        uHasTexture = GLES30.glGetUniformLocation(meshProgram, "uHasTexture")
        uTexture = GLES30.glGetUniformLocation(meshProgram, "uTexture")
        uShadingMode = GLES30.glGetUniformLocation(meshProgram, "uShadingMode")
        uIsSelected = GLES30.glGetUniformLocation(meshProgram, "uIsSelected")
        uUseVertexColors = GLES30.glGetUniformLocation(meshProgram, "uUseVertexColors")
        uGhostColor = GLES30.glGetUniformLocation(meshProgram, "uGhostColor")
        uGhostAlpha = GLES30.glGetUniformLocation(meshProgram, "uGhostAlpha")
        uUseSkinning = GLES30.glGetUniformLocation(meshProgram, "uUseSkinning")
        uBoneMatrices = GLES30.glGetUniformLocation(meshProgram, "uBoneMatrices")
        uLightCount = GLES30.glGetUniformLocation(meshProgram, "uLightCount")
        uLightPos = GLES30.glGetUniformLocation(meshProgram, "uLightPos")
        uLightColor = GLES30.glGetUniformLocation(meshProgram, "uLightColor")
        uLightIntensity = GLES30.glGetUniformLocation(meshProgram, "uLightIntensity")
        uLightType = GLES30.glGetUniformLocation(meshProgram, "uLightType")

        uLineViewProj = GLES30.glGetUniformLocation(lineProgram, "uViewProj")
        uLinePointSize = GLES30.glGetUniformLocation(lineProgram, "uPointSize")

        initStaticGridVao()
        val dynBuf = IntArray(1)
        GLES30.glGenBuffers(1, dynBuf, 0)
        dynamicLineVbo = dynBuf[0]

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        initialized = true
    }

    fun releaseGl() {
        if (!initialized) return
        gpuMeshCache.values.forEach { it.release() }
        gpuMeshCache.clear()
        if (gridVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(gridVbo), 0)
        if (gridVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(gridVao), 0)
        if (dynamicLineVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(dynamicLineVbo), 0)
        if (skyProgram != 0) GLES30.glDeleteProgram(skyProgram)
        if (meshProgram != 0) GLES30.glDeleteProgram(meshProgram)
        if (lineProgram != 0) GLES30.glDeleteProgram(lineProgram)
        if (textureCache.isNotEmpty()) {
            val ids = textureCache.values.toIntArray()
            GLES30.glDeleteTextures(ids.size, ids, 0)
            textureCache.clear()
        }
        gridVao = 0
        gridVbo = 0
        dynamicLineVbo = 0
        skyProgram = 0
        meshProgram = 0
        lineProgram = 0
        initialized = false
    }

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        gpuMeshCache.clear()
        textureCache.clear()
        gridVao = 0
        gridVbo = 0
        dynamicLineVbo = 0
        skyProgram = 0
        meshProgram = 0
        lineProgram = 0
        initialized = false
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
        sampleFps(cfg)
    }

    private fun sampleFps(cfg: RenderConfig) {
        val cb = cfg.onFpsSample ?: return
        val now = System.nanoTime()
        if (fpsWindowStartNs == 0L) {
            fpsWindowStartNs = now
            fpsFrameAcc = 0
            return
        }
        fpsFrameAcc++
        val elapsedNs = now - fpsWindowStartNs
        if (elapsedNs >= 600_000_000L) {
            val fps = ((fpsFrameAcc * 1_000_000_000.0) / elapsedNs.toDouble()).toInt().coerceIn(1, 120)
            fpsWindowStartNs = now
            fpsFrameAcc = 0
            cb(fps)
        }
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
        frameCounter++

        GLES30.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        GLES30.glClearColor(0.055f, 0.063f, 0.086f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val camPos = computeCameraMatricesInto(cfg.scene, evaluated, aspect, forExport, tempViewMat, tempProjMat)
        lastCameraPos = camPos
        System.arraycopy(tempViewMat, 0, lastViewMatrix, 0, 16)
        System.arraycopy(tempProjMat, 0, lastProjMatrix, 0, 16)
        Matrix.multiplyMM(tempViewProj, 0, tempProjMat, 0, tempViewMat, 0)

        // 0. Live 3D Sky Dome Gradient Backdrop
        drawSkyBackdrop(cfg.scene.backdropPreset, cfg.scene.viewportCamera.pitchDeg)

        // 1. Floor Grid & World XYZ Axes (static GPU VAO, zero allocation)
        if (cfg.showGrid && !forExport && gridVao != 0) {
            GLES30.glUseProgram(lineProgram)
            GLES30.glUniformMatrix4fv(uLineViewProj, 1, false, tempViewProj, 0)
            GLES30.glUniform1f(uLinePointSize, 1.5f)
            GLES30.glLineWidth(1.5f)
            GLES30.glBindVertexArray(gridVao)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount)
            GLES30.glBindVertexArray(0)
        }

        // 2. Collect visible lights (up to 4) into pre-allocated scratch arrays
        var lightCount = 0
        for (ln in cfg.scene.nodes) {
            if (lightCount >= 4) break
            if (ln.nodeType != NodeType.LIGHT) continue
            val st = evaluated.nodeStates[ln.id]
            if (st?.visible != true) continue
            val p = st.transform.position
            lightPosArr[lightCount * 3] = p.x
            lightPosArr[lightCount * 3 + 1] = p.y
            lightPosArr[lightCount * 3 + 2] = p.z
            val colHex = ln.light?.color ?: 0xFFFFFFFF
            lightColorArr[lightCount * 3] = ((colHex shr 16) and 0xFF) / 255f
            lightColorArr[lightCount * 3 + 1] = ((colHex shr 8) and 0xFF) / 255f
            lightColorArr[lightCount * 3 + 2] = (colHex and 0xFF) / 255f
            lightIntArr[lightCount] = ln.light?.intensity ?: 1.4f
            lightTypeArr[lightCount] = when (ln.light?.lightType ?: LightType.POINT) {
                LightType.POINT -> 0
                LightType.DIRECTIONAL -> 1
                LightType.SPOT -> 2
            }
            lightCount++
        }

        // 3. Draw 3D Meshes & Skinned Characters using cached GPU VAOs
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(uView, 1, false, tempViewMat, 0)
        GLES30.glUniformMatrix4fv(uProjection, 1, false, tempProjMat, 0)
        GLES30.glUniform3f(uCameraPos, camPos.x, camPos.y, camPos.z)
        GLES30.glUniform1i(uLightCount, lightCount)
        GLES30.glUniform3fv(uLightPos, 4, lightPosArr, 0)
        GLES30.glUniform3fv(uLightColor, 4, lightColorArr, 0)
        GLES30.glUniform1fv(uLightIntensity, 4, lightIntArr, 0)
        GLES30.glUniform1iv(uLightType, 4, lightTypeArr, 0)

        val effectiveMode = if (forExport) {
            if (cfg.shadingMode == ShadingMode.TOON) ShadingMode.TOON else ShadingMode.MATERIAL
        } else {
            cfg.shadingMode
        }
        val shadingInt = when (effectiveMode) {
            ShadingMode.SOLID -> 0
            ShadingMode.MATERIAL -> 1
            ShadingMode.WIREFRAME -> 2
            ShadingMode.TOON -> 3
        }
        GLES30.glUniform1i(uShadingMode, shadingInt)
        GLES30.glUniform1f(uGhostAlpha, 0f)

        for (node in cfg.scene.nodes) {
            val state = evaluated.nodeStates[node.id] ?: continue
            if (!state.visible) continue
            val mesh = node.mesh ?: continue
            if (mesh.vertexCount == 0 || mesh.indices.isEmpty()) continue

            val isSelected = !forExport && node.id == cfg.selectedNodeId

            if (shadingInt != 2) {
                drawMeshSolidCached(
                    cacheKey = node.id,
                    mesh = mesh,
                    worldMatrix = state.worldMatrix,
                    material = node.material,
                    skeleton = node.skeleton,
                    bonePose = state.boneRotations,
                    isSelected = isSelected,
                    ghostAlpha = 0f,
                    ghostR = 0f,
                    ghostG = 0f,
                    ghostB = 0f
                )
            }

            if (shadingInt == 2 || (!forExport && isSelected && cfg.editorMode == EditorMode.EDIT)) {
                drawMeshWireframeOverlay(
                    mesh = mesh,
                    worldMatrix = state.worldMatrix,
                    skeleton = node.skeleton,
                    bonePose = state.boneRotations,
                    viewProj = tempViewProj,
                    isEditMode = !forExport && isSelected && cfg.editorMode == EditorMode.EDIT,
                    editSubMode = cfg.editSubMode,
                    selectedElementIndex = cfg.selectedElementIndex
                )
            }
        }

        // 3b. Pro Onion Skinning Ghost Pass (-6f Cyan, +6f Amber) for rigged characters
        if (!forExport && cfg.showOnionSkin && shadingInt != 2) {
            drawOnionSkinGhosts(cfg)
        }

        // Periodically prune GPU VAOs for deleted nodes (every 120 frames)
        if (frameCounter % 120L == 0L && gpuMeshCache.size > 24) {
            val it = gpuMeshCache.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (frameCounter - entry.value.lastUsedFrame > 120L) {
                    entry.value.release()
                    it.remove()
                }
            }
        }

        if (!forExport) {
            // 4. Light & Camera Viewport Markers
            drawHelpersForLightsAndCameras(cfg.scene, evaluated, cfg.selectedNodeId, tempViewProj)

            // 5. Skeleton Armatures + 3D IK Handles
            if (cfg.showSkeleton || cfg.editorMode == EditorMode.POSE) {
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                for (node in cfg.scene.nodes) {
                    val skel = node.skeleton ?: continue
                    val state = evaluated.nodeStates[node.id] ?: continue
                    if (!state.visible) continue
                    val highlightAll = node.id == cfg.selectedNodeId
                    drawSkeletonOverlay(skel, state.worldMatrix, state.boneRotations, cfg.selectedBoneId, highlightAll, tempViewProj)
                    if (cfg.ikEnabled && (highlightAll || cfg.editorMode == EditorMode.POSE)) {
                        drawIkHandlesOverlay(
                            skeleton = skel,
                            worldMatrix = state.worldMatrix,
                            bonePose = state.boneRotations,
                            selectedLimb = if (highlightAll) cfg.selectedIkLimb else null,
                            viewProj = tempViewProj
                        )
                    }
                }
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            }

            // 6. 3D Transform Gizmo on selected object
            if (cfg.showGizmo && cfg.selectedNodeId != null && cfg.editorMode == EditorMode.OBJECT) {
                val selState = evaluated.nodeStates[cfg.selectedNodeId]
                if (selState != null && selState.visible) {
                    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                    drawTransformGizmo(selState.transform.position, cfg.transformAxis, tempViewProj)
                    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                }
            }
        }
    }

    private fun drawSkyBackdrop(preset: BackdropPreset, cameraPitchDeg: Float) {
        if (skyProgram == 0) return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(skyProgram)
        setColorUniform(uSkyZenith, preset.zenithHex)
        setColorUniform(uSkyHorizon, preset.horizonHex)
        setColorUniform(uSkyGround, preset.groundHex)
        setColorUniform(uSkySun, preset.sunColorHex)
        val pitchShift = (cameraPitchDeg / 180f).coerceIn(-0.35f, 0.35f)
        GLES30.glUniform1f(uSkyShift, pitchShift)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawOnionSkinGhosts(cfg: RenderConfig) {
        val curFrame = cfg.scene.timeline.currentFrame
        val total = cfg.scene.timeline.totalFrames.toFloat().coerceAtLeast(1f)
        val prevFrame = (curFrame - 6f).coerceIn(0f, total)
        val nextFrame = (curFrame + 6f).coerceIn(0f, total)

        GLES30.glDepthMask(false)
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(uView, 1, false, tempViewMat, 0)
        GLES30.glUniformMatrix4fv(uProjection, 1, false, tempProjMat, 0)

        fun renderGhostAt(frame: Float, r: Float, g: Float, b: Float) {
            if (abs(frame - curFrame) < 0.4f) return
            val eval = AnimationEvaluator.evaluate(cfg.scene, frame)
            for (node in cfg.scene.nodes) {
                if (node.skeleton == null) continue
                val mesh = node.mesh ?: continue
                val st = eval.nodeStates[node.id] ?: continue
                if (!st.visible) continue
                drawMeshSolidCached(
                    cacheKey = node.id,
                    mesh = mesh,
                    worldMatrix = st.worldMatrix,
                    material = node.material,
                    skeleton = node.skeleton,
                    bonePose = st.boneRotations,
                    isSelected = false,
                    ghostAlpha = 0.28f,
                    ghostR = r,
                    ghostG = g,
                    ghostB = b
                )
            }
        }
        renderGhostAt(prevFrame, 0.13f, 0.83f, 0.93f) // Previous frame = Cyan
        renderGhostAt(nextFrame, 0.96f, 0.62f, 0.04f) // Next frame = Amber
        GLES30.glUniform1f(uGhostAlpha, 0f)
        GLES30.glDepthMask(true)
    }

    private fun setColorUniform(loc: Int, hex: Long) {
        GLES30.glUniform3f(
            loc,
            ((hex shr 16) and 0xFF) / 255f,
            ((hex shr 8) and 0xFF) / 255f,
            (hex and 0xFF) / 255f
        )
    }

    /**
     * Picks an interactive 3D IK handle (diamond) if touch lands within generous touch radius.
     */
    fun pickIkHandleAtScreenPoint(touchX: Float, touchY: Float): Pair<String, IkLimb>? {
        val cfg = config
        if (!cfg.ikEnabled) return null
        val evaluated = AnimationEvaluator.evaluate(cfg.scene, cfg.scene.timeline.currentFrame)
        val ray = Math3D.unprojectRay(touchX, touchY, viewportW, viewportH, lastViewMatrix, lastProjMatrix)

        val candidates = cfg.scene.nodes.filter { it.skeleton != null }
        val ordered = if (cfg.selectedNodeId != null) {
            candidates.sortedByDescending { it.id == cfg.selectedNodeId }
        } else candidates

        var bestHit: Pair<String, IkLimb>? = null
        var bestDist = Float.POSITIVE_INFINITY
        for (node in ordered) {
            val skel = node.skeleton ?: continue
            val state = evaluated.nodeStates[node.id] ?: continue
            if (!state.visible) continue
            val handles = IkSolver.computeIkHandleWorldPositions(skel, state.worldMatrix, state.boneRotations)
            for ((limb, worldPos) in handles) {
                val hit = Math3D.raySphereHitDistance(ray, worldPos, 0.26f)
                if (hit != null && hit < bestDist) {
                    bestDist = hit
                    bestHit = node.id to limb
                }
            }
        }
        return bestHit
    }

    fun getIkHandleWorldPos(nodeId: String, limb: IkLimb): Vec3? {
        val cfg = config
        val node = cfg.scene.nodes.firstOrNull { it.id == nodeId } ?: return null
        val skel = node.skeleton ?: return null
        val evaluated = AnimationEvaluator.evaluate(cfg.scene, cfg.scene.timeline.currentFrame)
        val state = evaluated.nodeStates[nodeId] ?: return null
        return IkSolver.computeIkHandleWorldPositions(skel, state.worldMatrix, state.boneRotations)[limb]
    }

    /**
     * Maps a 2D screen drag offset onto the camera-facing 3D plane passing through [anchorWorld],
     * giving natural 1:1 finger-to-limb IK manipulation from any orbit angle.
     */
    fun projectScreenDragToWorldPoint(anchorWorld: Vec3, deltaX: Float, deltaY: Float): Vec3 {
        val camPos = lastCameraPos
        val distToAnchor = (anchorWorld - camPos).length().coerceIn(0.8f, 30f)
        // Extract camera right & up basis vectors from row 0 & row 1 of column-major view matrix
        val right = Vec3(lastViewMatrix[0], lastViewMatrix[4], lastViewMatrix[8]).normalized()
        val up = Vec3(lastViewMatrix[1], lastViewMatrix[5], lastViewMatrix[9]).normalized()
        val scale = (distToAnchor * 0.85f) / viewportH.coerceAtLeast(1).toFloat()
        return anchorWorld + right * (deltaX * scale) + up * (-deltaY * scale)
    }

    /**
     * Unprojects a screen touch (x, y) into a 3D world ray and picks the nearest visible object or bone.
     */
    fun pickAtScreenPoint(touchX: Float, touchY: Float): Pair<String?, String?> {
        val cfg = config
        val evaluated = AnimationEvaluator.evaluate(cfg.scene, cfg.scene.timeline.currentFrame)
        val ray = Math3D.unprojectRay(touchX, touchY, viewportW, viewportH, lastViewMatrix, lastProjMatrix)

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
            // Skip selecting the huge background environment set when clicking near characters
            if (node.categoryTag == com.example.engine.model.Environment3DFactory.ENV_NODE_TAG) continue
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

    private fun computeCameraMatricesInto(
        scene: SceneDocument,
        evaluated: EvaluatedScene,
        aspect: Float,
        forExport: Boolean,
        viewMat: FloatArray,
        projMat: FloatArray
    ): Vec3 {
        val cam = scene.viewportCamera
        val activeSceneCam = scene.nodes.firstOrNull { it.nodeType == NodeType.CAMERA && (it.camera?.isActive == true) }
        if ((cam.preset == CameraPreset.CAMERA_VIEW || forExport) && activeSceneCam != null) {
            val camState = evaluated.nodeStates[activeSceneCam.id]
            val pos = camState?.transform?.position ?: activeSceneCam.transform.position
            val fov = (camState?.cameraFov ?: activeSceneCam.camera?.fov ?: 45f).coerceIn(18f, 105f)
            val target = cam.target
            Matrix.setLookAtM(viewMat, 0, pos.x, pos.y, pos.z, target.x, target.y, target.z, 0f, 1f, 0f)
            Matrix.perspectiveM(projMat, 0, fov, aspect, 0.1f, 150f)
            return pos
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
        return eye
    }

    private fun getOrUploadGpuMesh(cacheKey: String, mesh: MeshData): GpuMeshHandle {
        val existing = gpuMeshCache[cacheKey]
        if (existing != null &&
            existing.version == mesh.version &&
            existing.vertexCount == mesh.vertexCount &&
            existing.indexCount == mesh.indices.size
        ) {
            existing.lastUsedFrame = frameCounter
            return existing
        }
        existing?.release()

        val vaoArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        val vao = vaoArr[0]
        val vbos = IntArray(7)
        GLES30.glGenBuffers(7, vbos, 0)

        GLES30.glBindVertexArray(vao)

        fun uploadFloatAttr(vbo: Int, loc: Int, comps: Int, data: FloatArray) {
            val buf = allocateDirectFloatBuffer(data.size).apply { put(data); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, comps, GLES30.GL_FLOAT, false, 0, 0)
        }

        uploadFloatAttr(vbos[0], 0, 3, mesh.vertices)
        uploadFloatAttr(vbos[1], 1, 3, mesh.normals)
        uploadFloatAttr(vbos[2], 2, 2, mesh.uvs)
        val jIdxFloats = FloatArray(mesh.jointIndices.size) { mesh.jointIndices[it].toFloat() }
        uploadFloatAttr(vbos[3], 3, 4, jIdxFloats)
        uploadFloatAttr(vbos[4], 4, 4, mesh.jointWeights)
        val cArr = if (mesh.colors.size == mesh.vertices.size) mesh.colors else FloatArray(mesh.vertices.size) { 1f }
        uploadFloatAttr(vbos[5], 5, 3, cArr)

        val iBuf = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
            put(mesh.indices)
            position(0)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbos[6])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        val handle = GpuMeshHandle(
            version = mesh.version,
            vertexCount = mesh.vertexCount,
            indexCount = mesh.indices.size,
            vaoId = vao,
            vboIds = vbos,
            lastUsedFrame = frameCounter
        )
        gpuMeshCache[cacheKey] = handle
        return handle
    }

    private fun drawMeshSolidCached(
        cacheKey: String,
        mesh: MeshData,
        worldMatrix: FloatArray,
        material: MaterialData,
        skeleton: SkeletonData?,
        bonePose: Map<String, Vec3>,
        isSelected: Boolean,
        ghostAlpha: Float,
        ghostR: Float,
        ghostG: Float,
        ghostB: Float
    ) {
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(uModel, 1, false, worldMatrix, 0)

        val baseHex = material.baseColor
        val emHex = material.emissionColor
        GLES30.glUniform3f(
            uBaseColor,
            ((baseHex shr 16) and 0xFF) / 255f,
            ((baseHex shr 8) and 0xFF) / 255f,
            (baseHex and 0xFF) / 255f
        )
        GLES30.glUniform1f(uRoughness, material.roughness)
        GLES30.glUniform1f(uMetallic, material.metallic)
        GLES30.glUniform1f(uOpacity, material.opacity)
        GLES30.glUniform1f(uSubsurface, material.subsurface)
        GLES30.glUniform3f(
            uEmissionColor,
            ((emHex shr 16) and 0xFF) / 255f,
            ((emHex shr 8) and 0xFF) / 255f,
            (emHex and 0xFF) / 255f
        )
        GLES30.glUniform1f(uEmissionStrength, material.emissionStrength)
        GLES30.glUniform1f(uUvScale, material.uvScale)
        GLES30.glUniform1i(uIsSelected, if (isSelected) 1 else 0)
        GLES30.glUniform1i(uUseVertexColors, if (mesh.useVertexColors) 1 else 0)
        GLES30.glUniform3f(uGhostColor, ghostR, ghostG, ghostB)
        GLES30.glUniform1f(uGhostAlpha, ghostAlpha)

        val texId = if (ghostAlpha > 0.001f) 0 else getOrLoadMaterialTexture(material)
        if (texId != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glUniform1i(uTexture, 0)
            GLES30.glUniform1i(uHasTexture, 1)
        } else {
            GLES30.glUniform1i(uHasTexture, 0)
        }

        val useSkinning = skeleton != null && skeleton.bones.isNotEmpty()
        GLES30.glUniform1i(uUseSkinning, if (useSkinning) 1 else 0)
        if (useSkinning) {
            val palette = skeleton!!.computeSkinningPalette(bonePose)
            GLES30.glUniformMatrix4fv(uBoneMatrices, SkeletonData.MAX_BONES, false, palette, 0)
        }

        val gpuMesh = getOrUploadGpuMesh(cacheKey, mesh)
        GLES30.glBindVertexArray(gpuMesh.vaoId)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpuMesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
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

        val matSlice = FloatArray(16)
        val worldVerts = Array(mesh.vertexCount) { i ->
            var p = Vec3(mesh.vertices[i * 3], mesh.vertices[i * 3 + 1], mesh.vertices[i * 3 + 2])
            if (skinPalette != null) {
                val b0 = mesh.jointIndices[i * 4].coerceIn(0, SkeletonData.MAX_BONES - 1)
                System.arraycopy(skinPalette, b0 * 16, matSlice, 0, 16)
                p = Math3D.transformPoint(matSlice, p)
            }
            Math3D.transformPoint(worldMatrix, p)
        }

        val maxTris = mesh.triangleCount.coerceAtMost(2400)
        val floatCount = maxTris * 6 * 7
        val buf = ensureDynamicBuffer(floatCount)
        for (t in 0 until maxTris) {
            val i0 = mesh.indices[t * 3]; val i1 = mesh.indices[t * 3 + 1]; val i2 = mesh.indices[t * 3 + 2]
            val highlightFace = isEditMode && editSubMode == EditSubMode.FACE && t == (selectedElementIndex % maxTris.coerceAtLeast(1))
            val r = if (highlightFace) 0.13f else 0.39f
            val g = if (highlightFace) 0.83f else 0.40f
            val b = if (highlightFace) 0.93f else 0.95f
            val a = if (highlightFace) 1.0f else 0.65f
            putSeg(buf, worldVerts[i0], worldVerts[i1], r, g, b, a)
            putSeg(buf, worldVerts[i1], worldVerts[i2], r, g, b, a)
            putSeg(buf, worldVerts[i2], worldVerts[i0], r, g, b, a)
        }
        buf.position(0)
        drawDynamicLines(buf, maxTris * 6, viewProj, GLES30.GL_LINES, 1.5f)

        if (isEditMode) {
            val vc = worldVerts.size.coerceAtMost(1500)
            val ptBuf = ensureDynamicBuffer(vc * 7)
            val selV = selectedElementIndex % vc.coerceAtLeast(1)
            for (i in 0 until vc) {
                val p = worldVerts[i]
                val isSel = editSubMode == EditSubMode.VERTEX && i == selV
                ptBuf.put(p.x).put(p.y).put(p.z)
                ptBuf.put(if (isSel) 0.13f else 0.96f)
                ptBuf.put(if (isSel) 0.83f else 0.62f)
                ptBuf.put(if (isSel) 0.93f else 0.04f)
                ptBuf.put(1.0f)
            }
            ptBuf.position(0)
            drawDynamicLines(ptBuf, vc, viewProj, GLES30.GL_POINTS, 9.0f)
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
        val boneCount = skeleton.bones.size
        val lineBuf = ensureDynamicBuffer(boneCount * 10 * 7)
        var lineVerts = 0

        for (bone in skeleton.bones) {
            val (lh, lt) = joints[bone.id] ?: continue
            val h = Math3D.transformPoint(worldMatrix, lh)
            val t = Math3D.transformPoint(worldMatrix, lt)
            val isSel = bone.id == selectedBoneId
            val r = if (isSel) 0.13f else if (isNodeSelected) 0.96f else 0.60f
            val g = if (isSel) 0.83f else if (isNodeSelected) 0.62f else 0.65f
            val b = if (isSel) 0.93f else if (isNodeSelected) 0.04f else 0.80f

            val len = (t - h).length().coerceAtLeast(0.05f)
            val side = Vec3(0.042f * (len / 0.4f).coerceIn(0.6f, 1.4f), 0f, 0f)
            val mid = Vec3.lerp(h, t, 0.25f)
            val p1 = mid + side
            val p2 = mid - side

            putSeg(lineBuf, h, p1, r, g, b, 1f)
            putSeg(lineBuf, p1, t, r, g, b, 1f)
            putSeg(lineBuf, h, p2, r, g, b, 1f)
            putSeg(lineBuf, p2, t, r, g, b, 1f)
            putSeg(lineBuf, p1, p2, r, g, b, 1f)
            lineVerts += 10
        }
        lineBuf.position(0)
        drawDynamicLines(lineBuf, lineVerts, viewProj, GLES30.GL_LINES, 2.5f)

        val ptBuf = ensureDynamicBuffer(boneCount * 7)
        var ptVerts = 0
        for (bone in skeleton.bones) {
            val (lh, _) = joints[bone.id] ?: continue
            val h = Math3D.transformPoint(worldMatrix, lh)
            val isSel = bone.id == selectedBoneId
            val r = if (isSel) 0.13f else if (isNodeSelected) 0.96f else 0.60f
            val g = if (isSel) 0.83f else if (isNodeSelected) 0.62f else 0.65f
            val b = if (isSel) 0.93f else if (isNodeSelected) 0.04f else 0.80f
            ptBuf.put(h.x).put(h.y).put(h.z).put(r).put(g).put(b).put(1f)
            ptVerts++
        }
        ptBuf.position(0)
        drawDynamicLines(ptBuf, ptVerts, viewProj, GLES30.GL_POINTS, 12f)
    }

    /**
     * Draws glowing 3D octahedral IK target diamonds for Left/Right Hands (Cyan) & Left/Right Feet (Gold).
     */
    private fun drawIkHandlesOverlay(
        skeleton: SkeletonData,
        worldMatrix: FloatArray,
        bonePose: Map<String, Vec3>,
        selectedLimb: IkLimb?,
        viewProj: FloatArray
    ) {
        val handles = IkSolver.computeIkHandleWorldPositions(skeleton, worldMatrix, bonePose)
        if (handles.isEmpty()) return

        // Each octahedron has 12 edges (24 verts); selected limb draws an extra outer halo (24 more)
        val lineBuf = ensureDynamicBuffer(handles.size * 48 * 7)
        var verts = 0

        fun drawOcta(c: Vec3, s: Float, r: Float, g: Float, b: Float, a: Float) {
            val top = c + Vec3(0f, s, 0f)
            val bot = c + Vec3(0f, -s, 0f)
            val px = c + Vec3(s, 0f, 0f)
            val nx = c + Vec3(-s, 0f, 0f)
            val pz = c + Vec3(0f, 0f, s)
            val nz = c + Vec3(0f, 0f, -s)
            putSeg(lineBuf, top, px, r, g, b, a); putSeg(lineBuf, top, nx, r, g, b, a)
            putSeg(lineBuf, top, pz, r, g, b, a); putSeg(lineBuf, top, nz, r, g, b, a)
            putSeg(lineBuf, bot, px, r, g, b, a); putSeg(lineBuf, bot, nx, r, g, b, a)
            putSeg(lineBuf, bot, pz, r, g, b, a); putSeg(lineBuf, bot, nz, r, g, b, a)
            putSeg(lineBuf, px, pz, r, g, b, a); putSeg(lineBuf, pz, nx, r, g, b, a)
            putSeg(lineBuf, nx, nz, r, g, b, a); putSeg(lineBuf, nz, px, r, g, b, a)
            verts += 24
        }

        for ((limb, center) in handles) {
            val isSel = limb == selectedLimb
            val hex = limb.handleColorHex
            val r = ((hex shr 16) and 0xFF) / 255f
            val g = ((hex shr 8) and 0xFF) / 255f
            val b = (hex and 0xFF) / 255f
            drawOcta(center, if (isSel) 0.13f else 0.095f, r, g, b, 1.0f)
            if (isSel) {
                drawOcta(center, 0.18f, 1.0f, 1.0f, 1.0f, 0.92f)
            }
        }
        lineBuf.position(0)
        drawDynamicLines(lineBuf, verts, viewProj, GLES30.GL_LINES, 3.4f)

        // Bright center anchor dots
        val ptBuf = ensureDynamicBuffer(handles.size * 7)
        var ptCount = 0
        for ((limb, center) in handles) {
            val isSel = limb == selectedLimb
            val hex = limb.handleColorHex
            val r = if (isSel) 1f else ((hex shr 16) and 0xFF) / 255f
            val g = if (isSel) 1f else ((hex shr 8) and 0xFF) / 255f
            val b = if (isSel) 1f else (hex and 0xFF) / 255f
            ptBuf.put(center.x).put(center.y).put(center.z).put(r).put(g).put(b).put(1f)
            ptCount++
        }
        ptBuf.position(0)
        drawDynamicLines(ptBuf, ptCount, viewProj, GLES30.GL_POINTS, 18f)
    }

    private fun drawHelpersForLightsAndCameras(
        scene: SceneDocument,
        evaluated: EvaluatedScene,
        selectedNodeId: String?,
        viewProj: FloatArray
    ) {
        var helperVerts = 0
        for (node in scene.nodes) {
            if (node.nodeType == NodeType.LIGHT) helperVerts += 8
            else if (node.nodeType == NodeType.CAMERA) helperVerts += 16
        }
        if (helperVerts == 0) return

        val buf = ensureDynamicBuffer(helperVerts * 7)
        var written = 0
        for (node in scene.nodes) {
            val st = evaluated.nodeStates[node.id] ?: continue
            if (!st.visible) continue
            val p = st.transform.position
            val sel = node.id == selectedNodeId
            when (node.nodeType) {
                NodeType.LIGHT -> {
                    val r = if (sel) 0.13f else 0.96f
                    val g = if (sel) 0.83f else 0.62f
                    val b = if (sel) 0.93f else 0.04f
                    val s = 0.24f
                    putSeg(buf, p + Vec3(-s, 0f, 0f), p + Vec3(s, 0f, 0f), r, g, b, 1f)
                    putSeg(buf, p + Vec3(0f, -s, 0f), p + Vec3(0f, s, 0f), r, g, b, 1f)
                    putSeg(buf, p + Vec3(0f, 0f, -s), p + Vec3(0f, 0f, s), r, g, b, 1f)
                    putSeg(buf, p, Vec3(p.x, 0f, p.z), r * 0.5f, g * 0.5f, b * 0.5f, 1f)
                    written += 8
                }
                NodeType.CAMERA -> {
                    val r = if (sel) 0.13f else 0.65f
                    val g = if (sel) 0.83f else 0.72f
                    val b = if (sel) 0.93f else 0.90f
                    val f = Vec3(0f, 0f, -0.45f)
                    val c1 = p + f + Vec3(-0.22f, 0.14f, 0f)
                    val c2 = p + f + Vec3(0.22f, 0.14f, 0f)
                    val c3 = p + f + Vec3(0.22f, -0.14f, 0f)
                    val c4 = p + f + Vec3(-0.22f, -0.14f, 0f)
                    putSeg(buf, p, c1, r, g, b, 1f); putSeg(buf, p, c2, r, g, b, 1f)
                    putSeg(buf, p, c3, r, g, b, 1f); putSeg(buf, p, c4, r, g, b, 1f)
                    putSeg(buf, c1, c2, r, g, b, 1f); putSeg(buf, c2, c3, r, g, b, 1f)
                    putSeg(buf, c3, c4, r, g, b, 1f); putSeg(buf, c4, c1, r, g, b, 1f)
                    written += 16
                }
                else -> Unit
            }
        }
        if (written > 0) {
            buf.position(0)
            drawDynamicLines(buf, written, viewProj, GLES30.GL_LINES, 2.2f)
        }
    }

    private fun drawTransformGizmo(origin: Vec3, activeAxis: TransformAxis, viewProj: FloatArray) {
        val len = 0.95f
        val buf = ensureDynamicBuffer(6 * 7)
        fun axis(dir: Vec3, r: Float, g: Float, b: Float, bright: Boolean) {
            val boost = if (bright) 1.0f else 0.45f
            putSeg(buf, origin, origin + dir * len, r * boost, g * boost, b * boost, 1f)
        }
        axis(Vec3(1f, 0f, 0f), 0.94f, 0.27f, 0.27f, activeAxis == TransformAxis.ALL || activeAxis == TransformAxis.X)
        axis(Vec3(0f, 1f, 0f), 0.13f, 0.77f, 0.37f, activeAxis == TransformAxis.ALL || activeAxis == TransformAxis.Y)
        axis(Vec3(0f, 0f, 1f), 0.23f, 0.51f, 0.96f, activeAxis == TransformAxis.ALL || activeAxis == TransformAxis.Z)
        buf.position(0)
        drawDynamicLines(buf, 6, viewProj, GLES30.GL_LINES, 4.0f)
    }

    private fun drawDynamicLines(
        interleavedBuffer: FloatBuffer,
        vertexCount: Int,
        viewProj: FloatArray,
        mode: Int,
        widthOrPointSize: Float
    ) {
        if (vertexCount <= 0) return
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(uLineViewProj, 1, false, viewProj, 0)
        GLES30.glUniform1f(uLinePointSize, widthOrPointSize)
        if (mode == GLES30.GL_LINES) GLES30.glLineWidth(widthOrPointSize)

        val stride = 7 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicLineVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexCount * stride, interleavedBuffer, GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 3 * 4)

        GLES30.glDrawArrays(mode, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun initStaticGridVao() {
        val list = ArrayList<Float>()
        val half = 8
        fun addLine(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, r: Float, g: Float, b: Float, a: Float) {
            list.add(x1); list.add(y1); list.add(z1); list.add(r); list.add(g); list.add(b); list.add(a)
            list.add(x2); list.add(y2); list.add(z2); list.add(r); list.add(g); list.add(b); list.add(a)
        }
        for (i in -half..half) {
            if (i == 0) continue
            val alpha = if (abs(i) % 4 == 0) 0.42f else 0.18f
            addLine(i.toFloat(), 0f, -half.toFloat(), i.toFloat(), 0f, half.toFloat(), 0.25f, 0.30f, 0.42f, alpha)
            addLine(-half.toFloat(), 0f, i.toFloat(), half.toFloat(), 0f, i.toFloat(), 0.25f, 0.30f, 0.42f, alpha)
        }
        addLine(-half.toFloat(), 0.002f, 0f, half.toFloat(), 0.002f, 0f, 0.90f, 0.27f, 0.27f, 0.85f)
        addLine(0f, 0.002f, -half.toFloat(), 0f, 0.002f, half.toFloat(), 0.23f, 0.51f, 0.96f, 0.85f)
        addLine(0f, 0f, 0f, 0f, 1.5f, 0f, 0.13f, 0.77f, 0.37f, 0.55f)

        gridVertexCount = list.size / 7
        val arr = list.toFloatArray()
        val buf = allocateDirectFloatBuffer(arr.size).apply { put(arr); position(0) }

        val vaoArr = IntArray(1)
        val vboArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        gridVao = vaoArr[0]
        gridVbo = vboArr[0]

        val stride = 7 * 4
        GLES30.glBindVertexArray(gridVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, arr.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun getOrLoadMaterialTexture(material: MaterialData): Int {
        material.texturePath?.takeIf { it.isNotBlank() }?.let { path ->
            val id = getOrLoadTextureFile(path)
            if (id != 0) return id
        }
        val presetName = material.skinPreset ?: return 0
        val preset = runCatching { SkinPreset.valueOf(presetName) }.getOrNull() ?: return 0
        if (preset == SkinPreset.NONE) return 0
        val cacheKey = "skin_preset:${preset.name}"
        textureCache[cacheKey]?.let { return it }
        val bmp = SkinTextureGenerator.generateBitmap(preset) ?: return 0
        return uploadBitmapTexture(cacheKey, bmp)
    }

    private fun getOrLoadTextureFile(path: String): Int {
        textureCache[path]?.let { return it }
        val file = File(path)
        if (!file.exists()) return 0
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        return uploadBitmapTexture(path, bmp)
    }

    private fun uploadBitmapTexture(cacheKey: String, bmp: Bitmap): Int {
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
            textureCache[cacheKey] = texId
        }
        bmp.recycle()
        return texId
    }

    private fun putSeg(buf: FloatBuffer, a: Vec3, b: Vec3, r: Float, g: Float, bl: Float, al: Float) {
        buf.put(a.x).put(a.y).put(a.z).put(r).put(g).put(bl).put(al)
        buf.put(b.x).put(b.y).put(b.z).put(r).put(g).put(bl).put(al)
    }

    private fun ensureDynamicBuffer(floatCapacity: Int): FloatBuffer {
        if (dynamicLineBuffer.capacity() < floatCapacity) {
            dynamicLineBuffer = allocateDirectFloatBuffer(maxOf(floatCapacity, dynamicLineBuffer.capacity() * 2))
        }
        dynamicLineBuffer.clear()
        return dynamicLineBuffer
    }

    private fun allocateDirectFloatBuffer(floatCount: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floatCount.coerceAtLeast(16) * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
}
