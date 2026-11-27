package com.example.data

import android.content.Context
import com.example.R
import com.example.engine.model.AccessoryKind
import com.example.engine.model.AnimationClipTrack
import com.example.engine.model.AssetCategory
import com.example.engine.model.BackdropPreset
import com.example.engine.model.CameraData
import com.example.engine.model.CharacterArchetype
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterCategory
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.CostumePreset
import com.example.engine.model.Environment3DFactory
import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.KeyframeProperty
import com.example.engine.model.LightData
import com.example.engine.model.LightType
import com.example.engine.model.MaterialData
import com.example.engine.model.NodeType
import com.example.engine.model.OrientationFilter
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.PrimitiveMeshFactory
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.SkinPreset
import com.example.engine.model.TimelineState
import com.example.engine.model.Transform3D
import com.example.engine.model.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class StarterTemplateInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val aspectLabel: String,
    val backdrop: BackdropPreset,
    val drawableResId: Int,
    val accentHex: Long
)

class StudioRepository(private val context: Context) {
    private val dao = StudioDatabase.getInstance(context).studioDao()
    private val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
    private val assetsDir = File(context.filesDir, "library_nodes").apply { mkdirs() }

    val projectsFlow: Flow<List<ProjectEntity>> = dao.observeProjects()
    val libraryAssetsFlow: Flow<List<LibraryAssetEntity>> = dao.observeLibraryAssets()
    val savedPosesFlow: Flow<List<SavedPoseEntity>> = dao.observeSavedPoses()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val existingAssetIds = dao.getAllLibraryAssetIds().toHashSet()
        val missingAssets = buildBuiltInLibraryCatalog().filter { it.id !in existingAssetIds }
        if (missingAssets.isNotEmpty()) {
            dao.upsertLibraryAssets(missingAssets)
        }
        val existingPoseIds = dao.getAllSavedPoseIds().toHashSet()
        val missingPoses = buildBuiltInPoseCatalog().filter { it.id !in existingPoseIds }
        if (missingPoses.isNotEmpty()) {
            dao.upsertSavedPoses(missingPoses)
        }
    }

    suspend fun saveCustomPose(name: String, category: String, pose: Map<String, Vec3>): SavedPoseEntity =
        withContext(Dispatchers.IO) {
            val entity = SavedPoseEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Custom Pose" },
                category = category.ifBlank { "Custom" },
                poseJson = SceneSerializer.serializePoseMap(pose),
                boneCount = pose.size,
                isBuiltIn = false,
                accentHex = 0xFF6366F1,
                createdAt = System.currentTimeMillis()
            )
            dao.upsertSavedPose(entity)
            entity
        }

    suspend fun deleteSavedPose(poseId: String) = withContext(Dispatchers.IO) {
        dao.deleteSavedPoseById(poseId)
    }

    suspend fun createProjectFromTemplate(templateId: String): SceneDocument = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val doc = when (templateId) {
            "tpl_village_sunset" -> {
                val aarav = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Aarav Festive",
                        category = CharacterCategory.DESI,
                        costume = CostumePreset.INDIAN_TRADITIONAL,
                        primaryColor = 0xFFDC2626,
                        secondaryColor = 0xFFF59E0B,
                        accessory = AccessoryKind.TURBAN,
                        styleId = "char_desi_hero"
                    )
                ).copy(transform = Transform3D(position = Vec3(0f, 0f, -1.8f)))
                val envSet = Environment3DFactory.buildEnvironmentSetNode(BackdropPreset.VILLAGE_SUNSET)!!
                val lights = Environment3DFactory.buildLightsForBackdrop(BackdropPreset.VILLAGE_SUNSET)
                val cam = SceneNode(name = "Village Cam", nodeType = NodeType.CAMERA, transform = Transform3D(Vec3(0.4f, 1.85f, 5.2f)), camera = CameraData(45f, isActive = true))
                val walkClip = AnimationClipTrack(targetNodeId = aarav.id, clipName = "Walk Cycle", presetId = "walk", startFrame = 0, durationFrames = 45, loop = true, colorHex = 0xFFF59E0B)
                val kfs = listOf(
                    Keyframe(targetNodeId = aarav.id, frame = 0, property = KeyframeProperty.POSITION, vec3Value = Vec3(0f, 0f, -2.2f), interpolation = InterpolationType.LINEAR),
                    Keyframe(targetNodeId = aarav.id, frame = 150, property = KeyframeProperty.POSITION, vec3Value = Vec3(0f, 0f, 1.4f), interpolation = InterpolationType.LINEAR)
                )
                SceneDocument(
                    projectId = id,
                    projectName = "Village Sunset Walk",
                    nodes = listOf(aarav, envSet) + lights + cam,
                    timeline = TimelineState(fps = 30, totalFrames = 150, keyframes = kfs, clips = listOf(walkClip)),
                    backdropPreset = BackdropPreset.VILLAGE_SUNSET
                )
            }
            "tpl_twilight_cafe" -> {
                val maya = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Maya Streetwear",
                        category = CharacterCategory.WESTERN,
                        costume = CostumePreset.FORMAL,
                        primaryColor = 0xFF1E222A,
                        secondaryColor = 0xFFE2E8F0,
                        styleId = "char_streetwear_maya"
                    )
                ).copy(transform = Transform3D(position = Vec3(-0.75f, 0f, 0.2f), rotation = Vec3(0f, 28f, 0f)))
                val alex = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Alex Explorer",
                        category = CharacterCategory.WESTERN,
                        costume = CostumePreset.FORMAL,
                        primaryColor = 0xFF334155,
                        secondaryColor = 0xFF6366F1,
                        styleId = "char_western_ranger"
                    )
                ).copy(transform = Transform3D(position = Vec3(0.80f, 0f, 0.1f), rotation = Vec3(0f, -32f, 0f)))
                val envSet = Environment3DFactory.buildEnvironmentSetNode(BackdropPreset.TWILIGHT_CITY)!!
                val lights = Environment3DFactory.buildLightsForBackdrop(BackdropPreset.TWILIGHT_CITY)
                val cam = SceneNode(name = "Café Cam", nodeType = NodeType.CAMERA, transform = Transform3D(Vec3(0f, 1.75f, 5.4f)), camera = CameraData(45f, isActive = true))
                val talkClip = AnimationClipTrack(targetNodeId = maya.id, clipName = "Talk Gesture", presetId = "talking", startFrame = 0, durationFrames = 50, loop = true, colorHex = 0xFF6366F1)
                val waveClip = AnimationClipTrack(targetNodeId = alex.id, clipName = "Friendly Wave", presetId = "waving", startFrame = 15, durationFrames = 55, loop = true, colorHex = 0xFF22D3EE)
                SceneDocument(
                    projectId = id,
                    projectName = "Twilight Café Dialogue",
                    nodes = listOf(maya, alex, envSet) + lights + cam,
                    timeline = TimelineState(fps = 30, totalFrames = 150, clips = listOf(talkClip, waveClip)),
                    backdropPreset = BackdropPreset.TWILIGHT_CITY
                )
            }
            "tpl_forest_meadow" -> {
                val panda = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Momo Red Panda",
                        category = CharacterCategory.ANIMALS,
                        bodyType = CharacterArchetype.QUADRUPED,
                        primaryColor = 0xFFC2410C,
                        secondaryColor = 0xFFFFF7ED,
                        styleId = "char_red_panda"
                    )
                ).copy(transform = Transform3D(position = Vec3(-0.65f, 0f, 0.25f), rotation = Vec3(0f, 22f, 0f)))
                val dancer = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Bima Meadow",
                        category = CharacterCategory.INDONESIAN,
                        costume = CostumePreset.MYTHOLOGICAL,
                        primaryColor = 0xFFD97706,
                        secondaryColor = 0xFF22D3EE,
                        styleId = "char_indo_dancer"
                    )
                ).copy(transform = Transform3D(position = Vec3(0.75f, 0f, -0.25f), rotation = Vec3(0f, -15f, 0f)))
                val envSet = Environment3DFactory.buildEnvironmentSetNode(BackdropPreset.FOREST_MEADOW)!!
                val lights = Environment3DFactory.buildLightsForBackdrop(BackdropPreset.FOREST_MEADOW)
                val cam = SceneNode(name = "Meadow Cam", nodeType = NodeType.CAMERA, transform = Transform3D(Vec3(0f, 1.8f, 5.5f)), camera = CameraData(45f, isActive = true))
                val danceClip = AnimationClipTrack(targetNodeId = dancer.id, clipName = "Groove Dance", presetId = "dance", startFrame = 0, durationFrames = 48, loop = true, colorHex = 0xFF10B981)
                val jumpClip = AnimationClipTrack(targetNodeId = panda.id, clipName = "Happy Hop", presetId = "jump", startFrame = 0, durationFrames = 40, loop = true, colorHex = 0xFFF59E0B)
                SceneDocument(
                    projectId = id,
                    projectName = "Forest Dance Reel",
                    nodes = listOf(dancer, panda, envSet) + lights + cam,
                    timeline = TimelineState(fps = 30, totalFrames = 150, clips = listOf(danceClip, jumpClip)),
                    backdropPreset = BackdropPreset.FOREST_MEADOW
                )
            }
            "tpl_cyber_runway" -> {
                val maya = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Maya Streetwear",
                        category = CharacterCategory.WESTERN,
                        costume = CostumePreset.FORMAL,
                        primaryColor = 0xFF1E222A,
                        secondaryColor = 0xFFE2E8F0,
                        styleId = "char_streetwear_maya"
                    )
                ).copy(
                    transform = Transform3D(position = Vec3(-0.6f, 0f, -0.5f)),
                    material = MaterialData(name = "Street Denim Skin", baseColor = 0xFFFFFFFF, roughness = 0.55f, metallic = 0.08f, skinPreset = SkinPreset.DENIM_STREETWEAR.name)
                )
                val bot = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Nova Cyber Bot",
                        category = CharacterCategory.LATEST,
                        costume = CostumePreset.ORIGINAL_HERO,
                        primaryColor = 0xFFF1F5F9,
                        secondaryColor = 0xFF22D3EE,
                        styleId = "char_cyber_bot"
                    )
                ).copy(transform = Transform3D(position = Vec3(0.95f, 0f, -1.1f), rotation = Vec3(0f, -20f, 0f)))
                val envSet = Environment3DFactory.buildEnvironmentSetNode(BackdropPreset.CYBER_NEON)!!
                val lights = Environment3DFactory.buildLightsForBackdrop(BackdropPreset.CYBER_NEON)
                val cam = SceneNode(name = "Runway Cam", nodeType = NodeType.CAMERA, transform = Transform3D(Vec3(0f, 1.75f, 5.2f)), camera = CameraData(44f, isActive = true))
                val walkClip = AnimationClipTrack(targetNodeId = maya.id, clipName = "Runway Walk", presetId = "walk", startFrame = 0, durationFrames = 44, loop = true, colorHex = 0xFF22D3EE)
                val hoverClip = AnimationClipTrack(targetNodeId = bot.id, clipName = "Cyber Spin", presetId = "spin", startFrame = 0, durationFrames = 50, loop = true, colorHex = 0xFF6366F1)
                SceneDocument(
                    projectId = id,
                    projectName = "Cyber Runway Showcase",
                    nodes = listOf(maya, bot, envSet) + lights + cam,
                    timeline = TimelineState(fps = 30, totalFrames = 150, clips = listOf(walkClip, hoverClip)),
                    backdropPreset = BackdropPreset.CYBER_NEON
                )
            }
            else -> { // tpl_royal_castle
                val knight = CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = "Kavi Royal Knight",
                        category = CharacterCategory.LATEST,
                        costume = CostumePreset.FANTASY,
                        primaryColor = 0xFF94A3B8,
                        secondaryColor = 0xFF6366F1,
                        styleId = "char_royal_knight"
                    )
                ).copy(transform = Transform3D(position = Vec3(0f, 0f, 0f)))
                val envSet = Environment3DFactory.buildEnvironmentSetNode(BackdropPreset.ROYAL_COURTYARD)!!
                val lights = Environment3DFactory.buildLightsForBackdrop(BackdropPreset.ROYAL_COURTYARD)
                val cam = SceneNode(name = "Castle Cam", nodeType = NodeType.CAMERA, transform = Transform3D(Vec3(0.6f, 1.85f, 5.0f)), camera = CameraData(45f, isActive = true))
                val combatClip = AnimationClipTrack(targetNodeId = knight.id, clipName = "Combat Guard", presetId = "combat", startFrame = 0, durationFrames = 46, loop = true, colorHex = 0xFFEF4444)
                val victoryClip = AnimationClipTrack(targetNodeId = knight.id, clipName = "Victory Cheer", presetId = "victory", startFrame = 60, durationFrames = 55, loop = false, colorHex = 0xFFF59E0B)
                SceneDocument(
                    projectId = id,
                    projectName = "Royal Castle Duel",
                    nodes = listOf(knight, envSet) + lights + cam,
                    timeline = TimelineState(fps = 30, totalFrames = 140, clips = listOf(combatClip, victoryClip)),
                    backdropPreset = BackdropPreset.ROYAL_COURTYARD
                )
            }
        }
        saveProject(doc, isRecovery = false)
        doc
    }

    suspend fun createQuickStartProject(): SceneDocument = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val hero = CharacterMeshBuilder.buildRiggedCharacterNode(
            CharacterBuildSpec(
                name = "Studio Hero Rig",
                category = CharacterCategory.DESI,
                costume = CostumePreset.ORIGINAL_HERO,
                primaryColor = 0xFF6366F1,
                secondaryColor = 0xFF22D3EE,
                accessory = AccessoryKind.CAPE
            )
        )
        val envStage = Environment3DFactory.buildEnvironmentSetNode(BackdropPreset.STUDIO_DARK)!!
        val pedestalProp = SceneNode(
            name = "Accent Torus",
            nodeType = NodeType.MESH,
            primitiveKind = PrimitiveKind.TORUS,
            transform = Transform3D(position = Vec3(-1.55f, 0.75f, -0.45f), rotation = Vec3(20f, 35f, 0f)),
            mesh = PrimitiveMeshFactory.createTorus(0.42f, 0.13f, 22, 12),
            material = MaterialData(
                name = "Cyan Energy",
                baseColor = 0xFF22D3EE,
                roughness = 0.25f,
                metallic = 0.75f,
                emissionColor = 0xFF22D3EE,
                emissionStrength = 0.18f
            )
        )
        val lights = Environment3DFactory.buildLightsForBackdrop(BackdropPreset.STUDIO_DARK)
        val mainCam = SceneNode(
            name = "Main Camera",
            nodeType = NodeType.CAMERA,
            transform = Transform3D(position = Vec3(0f, 1.75f, 5.4f)),
            camera = CameraData(fov = 45f, isActive = true)
        )

        val clip = AnimationClipTrack(
            targetNodeId = hero.id,
            clipName = "Friendly Wave",
            presetId = "waving",
            startFrame = 0,
            durationFrames = 60,
            speed = 1.0f,
            loop = true,
            colorHex = 0xFF6366F1
        )
        val kfs = listOf(
            Keyframe(targetNodeId = pedestalProp.id, frame = 0, property = KeyframeProperty.ROTATION, vec3Value = Vec3(20f, 0f, 0f), interpolation = InterpolationType.LINEAR),
            Keyframe(targetNodeId = pedestalProp.id, frame = 120, property = KeyframeProperty.ROTATION, vec3Value = Vec3(20f, 360f, 0f), interpolation = InterpolationType.LINEAR),
            Keyframe(targetNodeId = mainCam.id, frame = 0, property = KeyframeProperty.POSITION, vec3Value = Vec3(-0.6f, 1.7f, 5.4f)),
            Keyframe(targetNodeId = mainCam.id, frame = 120, property = KeyframeProperty.POSITION, vec3Value = Vec3(0.6f, 1.85f, 5.0f))
        )

        val doc = SceneDocument(
            projectId = id,
            projectName = "Quick-Start Studio Scene",
            nodes = listOf(hero, envStage, pedestalProp) + lights + mainCam,
            timeline = TimelineState(
                fps = 30,
                totalFrames = 120,
                currentFrame = 0f,
                isLooping = true,
                keyframes = kfs,
                clips = listOf(clip)
            ),
            backdropPreset = BackdropPreset.STUDIO_DARK
        )
        saveProject(doc, isRecovery = false)
        doc
    }

    suspend fun createBlankProject(title: String): SceneDocument = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val cube = SceneNode(
            name = "Studio Cube",
            nodeType = NodeType.MESH,
            primitiveKind = PrimitiveKind.CUBE,
            transform = Transform3D(position = Vec3(0f, 0.5f, 0f)),
            mesh = PrimitiveMeshFactory.createCube(1.0f),
            material = MaterialData(name = "Indigo Clay", baseColor = 0xFF6366F1, roughness = 0.45f, metallic = 0.15f)
        )
        val keyLight = SceneNode(
            name = "Key Light",
            nodeType = NodeType.LIGHT,
            transform = Transform3D(position = Vec3(3.0f, 4.2f, 3.2f)),
            light = LightData(LightType.POINT, 0xFFFFFFFF, 1.6f)
        )
        val cam = SceneNode(
            name = "Scene Camera",
            nodeType = NodeType.CAMERA,
            transform = Transform3D(position = Vec3(0f, 1.6f, 5.2f)),
            camera = CameraData(fov = 45f, isActive = true)
        )
        val doc = SceneDocument(
            projectId = id,
            projectName = title.ifBlank { "New 3D Project" },
            nodes = listOf(cube, keyLight, cam),
            timeline = TimelineState(fps = 30, totalFrames = 120),
            backdropPreset = BackdropPreset.STUDIO_DARK
        )
        saveProject(doc, isRecovery = false)
        doc
    }

    suspend fun saveProject(doc: SceneDocument, isRecovery: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = doc.copy(updatedAt = now)
        val file = if (isRecovery) File(projectsDir, "${doc.projectId}_recovery.json") else File(projectsDir, "${doc.projectId}.json")
        file.writeText(SceneSerializer.serializeScene(updated))

        val existing = dao.getProjectById(doc.projectId)
        val mainPath = File(projectsDir, "${doc.projectId}.json").absolutePath
        if (!isRecovery) {
            File(projectsDir, "${doc.projectId}_recovery.json").delete()
        }
        dao.upsertProject(
            ProjectEntity(
                id = doc.projectId,
                name = doc.projectName,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                fps = doc.timeline.fps,
                totalFrames = doc.timeline.totalFrames,
                objectCount = doc.nodes.size,
                keyframeCount = doc.timeline.keyframes.size + doc.timeline.clips.size,
                sceneFilePath = mainPath,
                hasRecoverySnapshot = isRecovery,
                accentHex = existing?.accentHex ?: 0xFF6366F1
            )
        )
    }

    suspend fun loadProject(projectId: String, useRecovery: Boolean = false): SceneDocument? = withContext(Dispatchers.IO) {
        val recoveryFile = File(projectsDir, "${projectId}_recovery.json")
        val mainFile = File(projectsDir, "$projectId.json")
        val target = if (useRecovery && recoveryFile.exists()) recoveryFile else mainFile
        if (!target.exists()) return@withContext null
        runCatching { SceneSerializer.deserializeScene(target.readText()) }.getOrNull()
    }

    suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim().ifBlank { return@withContext }
        dao.renameProject(projectId, trimmed, System.currentTimeMillis())
        loadProject(projectId)?.let { doc ->
            saveProject(doc.copy(projectName = trimmed), isRecovery = false)
        }
    }

    suspend fun duplicateProject(projectId: String) = withContext(Dispatchers.IO) {
        val doc = loadProject(projectId) ?: return@withContext
        val copyDoc = doc.copy(
            projectId = UUID.randomUUID().toString(),
            projectName = "${doc.projectName} Copy",
            updatedAt = System.currentTimeMillis()
        )
        saveProject(copyDoc, isRecovery = false)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        dao.deleteProjectById(projectId)
        File(projectsDir, "$projectId.json").delete()
        File(projectsDir, "${projectId}_recovery.json").delete()
    }

    suspend fun saveCustomNodeToLibrary(
        node: SceneNode,
        assetCategory: AssetCategory,
        characterCategory: CharacterCategory,
        costumePreset: CostumePreset = CostumePreset.ORIGINAL_HERO,
        secondaryColorHex: Long = 0xFF22D3EE,
        archetype: CharacterArchetype = CharacterArchetype.HUMANOID
    ): LibraryAssetEntity = withContext(Dispatchers.IO) {
        val assetId = UUID.randomUUID().toString()
        val nodeFile = File(assetsDir, "$assetId.json")
        nodeFile.writeText(SceneSerializer.serializeNode(node).toString())

        val entity = LibraryAssetEntity(
            id = assetId,
            name = node.name,
            assetCategory = assetCategory.name,
            characterCategory = characterCategory.name,
            orientationTag = OrientationFilter.RIG_360.name,
            archetype = if (node.sourceFormat != null) "IMPORTED" else archetype.name,
            costumePreset = costumePreset.name,
            primaryColorHex = node.material.baseColor,
            secondaryColorHex = secondaryColorHex,
            meshCount = if (node.mesh != null) 1 else 0,
            vertexCount = node.mesh?.vertexCount ?: 0,
            boneCount = node.skeleton?.bones?.size ?: 0,
            animationCount = 16,
            isPinned = false,
            isBuiltIn = false,
            sourceFormat = node.sourceFormat ?: "Custom 3D",
            serializedNodePath = nodeFile.absolutePath,
            createdAt = System.currentTimeMillis()
        )
        dao.upsertLibraryAsset(entity)
        entity
    }

    suspend fun instantiateAssetNode(asset: LibraryAssetEntity): SceneNode = withContext(Dispatchers.IO) {
        val savedPath = asset.serializedNodePath
        if (savedPath != null && File(savedPath).exists()) {
            val loaded = runCatching {
                SceneSerializer.deserializeNode(JSONObject(File(savedPath).readText()))
            }.getOrNull()
            if (loaded != null) {
                return@withContext loaded.copy(id = UUID.randomUUID().toString())
            }
        }
        when (asset.assetCategory) {
            AssetCategory.LIGHTS.name -> SceneNode(
                name = asset.name,
                nodeType = NodeType.LIGHT,
                transform = Transform3D(position = Vec3(2.2f, 3.6f, 2.5f)),
                light = LightData(LightType.POINT, asset.primaryColorHex, 1.6f)
            )
            AssetCategory.CAMERAS.name -> SceneNode(
                name = asset.name,
                nodeType = NodeType.CAMERA,
                transform = Transform3D(position = Vec3(0f, 1.6f, 5.2f)),
                camera = CameraData(fov = 45f, isActive = true)
            )
            AssetCategory.ENVIRONMENTS.name -> {
                val preset = when (asset.id) {
                    "asset_env_village" -> BackdropPreset.VILLAGE_SUNSET
                    "asset_env_city" -> BackdropPreset.TWILIGHT_CITY
                    "asset_env_forest" -> BackdropPreset.FOREST_MEADOW
                    "asset_env_cyber" -> BackdropPreset.CYBER_NEON
                    else -> BackdropPreset.STUDIO_DARK
                }
                Environment3DFactory.buildEnvironmentSetNode(preset)!!
            }
            else -> {
                val archetype = runCatching { CharacterArchetype.valueOf(asset.archetype) }.getOrDefault(CharacterArchetype.HUMANOID)
                val costume = runCatching { CostumePreset.valueOf(asset.costumePreset) }.getOrDefault(CostumePreset.ORIGINAL_HERO)
                val cat = runCatching { CharacterCategory.valueOf(asset.characterCategory) }.getOrDefault(CharacterCategory.CUSTOM)
                CharacterMeshBuilder.buildRiggedCharacterNode(
                    CharacterBuildSpec(
                        name = asset.name,
                        category = cat,
                        costume = costume,
                        bodyType = archetype,
                        primaryColor = asset.primaryColorHex,
                        secondaryColor = asset.secondaryColorHex,
                        accessory = costume.accessory,
                        styleId = asset.id
                    )
                )
            }
        }
    }

    companion object {
        val STARTER_TEMPLATES: List<StarterTemplateInfo> = listOf(
            StarterTemplateInfo(
                id = "tpl_village_sunset",
                title = "Village Sunset Walk",
                subtitle = "16:9 • Aarav walking along golden-hour 3D village lane",
                aspectLabel = "16:9",
                backdrop = BackdropPreset.VILLAGE_SUNSET,
                drawableResId = R.drawable.img_tpl_village_sunset_1795746105682,
                accentHex = 0xFFF59E0B
            ),
            StarterTemplateInfo(
                id = "tpl_twilight_cafe",
                title = "Twilight Café Dialogue",
                subtitle = "16:9 • Two rigged characters talking on twilight city street",
                aspectLabel = "16:9",
                backdrop = BackdropPreset.TWILIGHT_CITY,
                drawableResId = R.drawable.img_tpl_twilight_cafe_1795746109441,
                accentHex = 0xFF6366F1
            ),
            StarterTemplateInfo(
                id = "tpl_forest_meadow",
                title = "Forest Dance Reel",
                subtitle = "9:16 Vertical • Red Panda & Bima dancing in emerald meadow",
                aspectLabel = "9:16",
                backdrop = BackdropPreset.FOREST_MEADOW,
                drawableResId = R.drawable.img_tpl_forest_meadow_1795746109814,
                accentHex = 0xFF10B981
            ),
            StarterTemplateInfo(
                id = "tpl_cyber_runway",
                title = "Cyber Streetwear Stage",
                subtitle = "16:9 • Streetwear character & Nova Bot on neon runway",
                aspectLabel = "16:9",
                backdrop = BackdropPreset.CYBER_NEON,
                drawableResId = R.drawable.img_tpl_cyber_runway_1795746109811,
                accentHex = 0xFF22D3EE
            ),
            StarterTemplateInfo(
                id = "tpl_royal_castle",
                title = "Royal Castle Duel",
                subtitle = "16:9 • Kavi Royal Knight combat pose in dusk courtyard",
                aspectLabel = "16:9",
                backdrop = BackdropPreset.ROYAL_COURTYARD,
                drawableResId = R.drawable.img_tpl_royal_castle_1795746133990,
                accentHex = 0xFFEF4444
            )
        )

        fun buildPreviewMeshForAsset(asset: LibraryAssetEntity): Pair<com.example.engine.model.MeshData, MaterialData>? {
            val savedPath = asset.serializedNodePath
            if (savedPath != null && File(savedPath).exists()) {
                val loaded = runCatching {
                    SceneSerializer.deserializeNode(JSONObject(File(savedPath).readText()))
                }.getOrNull()
                if (loaded?.mesh != null) {
                    return loaded.mesh to loaded.material
                }
            }
            return when (asset.assetCategory) {
                AssetCategory.LIGHTS.name -> buildStudioLightPreviewMesh(asset.primaryColorHex, asset.secondaryColorHex) to
                    MaterialData(name = asset.name, baseColor = asset.primaryColorHex, roughness = 0.3f, metallic = 0.5f)
                AssetCategory.CAMERAS.name -> buildCinemaCameraPreviewMesh(asset.primaryColorHex, asset.secondaryColorHex) to
                    MaterialData(name = asset.name, baseColor = asset.primaryColorHex, roughness = 0.25f, metallic = 0.65f)
                AssetCategory.ENVIRONMENTS.name -> buildStageFloorPreviewMesh(asset.primaryColorHex, asset.secondaryColorHex) to
                    MaterialData(name = asset.name, baseColor = asset.primaryColorHex, roughness = 0.7f, metallic = 0.1f)
                else -> {
                    val archetype = runCatching { CharacterArchetype.valueOf(asset.archetype) }.getOrDefault(CharacterArchetype.HUMANOID)
                    val costume = runCatching { CostumePreset.valueOf(asset.costumePreset) }.getOrDefault(CostumePreset.ORIGINAL_HERO)
                    val cat = runCatching { CharacterCategory.valueOf(asset.characterCategory) }.getOrDefault(CharacterCategory.CUSTOM)
                    val node = CharacterMeshBuilder.buildRiggedCharacterNode(
                        CharacterBuildSpec(
                            name = asset.name,
                            category = cat,
                            costume = costume,
                            bodyType = archetype,
                            primaryColor = asset.primaryColorHex,
                            secondaryColor = asset.secondaryColorHex,
                            accessory = costume.accessory,
                            styleId = asset.id
                        )
                    )
                    node.mesh?.let { it to node.material }
                }
            }
        }

        private fun buildStudioLightPreviewMesh(glowHex: Long, accentHex: Long): com.example.engine.model.MeshData {
            val parts = listOf(
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCylinder(0.03f, 0.045f, 1.05f, 10), Vec3(0f, 0.55f, 0f)), 0, 0xFF334155L),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.025f, 0.025f, 0.55f, 8), Vec3(0f, 0f, 34f)), Vec3(-0.18f, 0.22f, 0.10f)), 0, 0xFF1E293BL),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.025f, 0.025f, 0.55f, 8), Vec3(0f, 0f, -34f)), Vec3(0.18f, 0.22f, 0.10f)), 0, 0xFF1E293BL),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.025f, 0.025f, 0.55f, 8), Vec3(-34f, 0f, 0f)), Vec3(0f, 0.22f, -0.20f)), 0, 0xFF1E293BL),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCube(1f, 0.56f, 0.44f, 0.28f), Vec3(0f, 1.24f, 0f)), 0, 0xFF1E293BL),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCube(1f, 0.48f, 0.36f, 0.05f), Vec3(0f, 1.24f, 0.15f)), 0, glowHex),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCylinder(0.06f, 0.06f, 0.08f, 10), Vec3(0f, 1.02f, 0f)), 0, accentHex)
            )
            return PrimitiveMeshFactory.mergeColoredMeshes(parts)
        }

        private fun buildCinemaCameraPreviewMesh(accentHex: Long, bodyAccentHex: Long): com.example.engine.model.MeshData {
            val parts = listOf(
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCube(1f, 0.44f, 0.40f, 0.72f), Vec3(0f, 0.60f, 0f)), 0, 0xFF1E293BL),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.15f, 0.12f, 0.36f, 14), Vec3(90f, 0f, 0f)), Vec3(0f, 0.60f, 0.52f)), 0, 0xFF334155L),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.12f, 0.12f, 0.06f, 14), Vec3(90f, 0f, 0f)), Vec3(0f, 0.60f, 0.70f)), 0, accentHex),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.18f, 0.18f, 0.12f, 14), Vec3(0f, 0f, 90f)), Vec3(0f, 0.96f, -0.14f)), 0, 0xFF334155L),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.18f, 0.18f, 0.12f, 14), Vec3(0f, 0f, 90f)), Vec3(0f, 0.96f, 0.22f)), 0, 0xFF334155L),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCube(1f, 0.22f, 0.16f, 0.04f), Vec3(0.24f, 0.62f, -0.12f)), 0, bodyAccentHex)
            )
            return PrimitiveMeshFactory.mergeColoredMeshes(parts)
        }

        private fun buildStageFloorPreviewMesh(baseHex: Long, trimHex: Long): com.example.engine.model.MeshData {
            val parts = listOf(
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCylinder(0.95f, 1.05f, 0.14f, 20), Vec3(0f, 0.07f, 0f)), 0, baseHex),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createTorus(0.96f, 0.032f, 24, 8), Vec3(0f, 0.14f, 0f)), 0, 0xFF22D3EEL),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCube(1f, 1.2f, 0.02f, 0.03f), Vec3(0f, 0.15f, 0f)), 0, trimHex),
                Triple(PrimitiveMeshFactory.translateMesh(PrimitiveMeshFactory.createCube(1f, 0.03f, 0.02f, 1.2f), Vec3(0f, 0.15f, 0f)), 0, trimHex)
            )
            return PrimitiveMeshFactory.mergeColoredMeshes(parts)
        }
    }

    suspend fun togglePinAsset(asset: LibraryAssetEntity) = withContext(Dispatchers.IO) {
        dao.setAssetPinned(asset.id, !asset.isPinned)
    }

    suspend fun renameAsset(assetId: String, newName: String) = withContext(Dispatchers.IO) {
        dao.renameAsset(assetId, newName.trim().ifBlank { return@withContext })
    }

    suspend fun deleteAsset(asset: LibraryAssetEntity) = withContext(Dispatchers.IO) {
        dao.deleteAssetById(asset.id)
        asset.serializedNodePath?.let { File(it).delete() }
    }

    private fun buildBuiltInPoseCatalog(): List<SavedPoseEntity> {
        fun pose(id: String, name: String, cat: String, accent: Long, map: Map<String, Vec3>) = SavedPoseEntity(
            id = id,
            name = name,
            category = cat,
            poseJson = SceneSerializer.serializePoseMap(map),
            boneCount = map.size,
            isBuiltIn = true,
            accentHex = accent
        )
        return listOf(
            pose("pose_t_pose", "T-Pose (Rig Bind)", "Core", 0xFF22D3EE, mapOf(
                "l_upper_arm" to Vec3(0f, 0f, -86f), "r_upper_arm" to Vec3(0f, 0f, 86f),
                "l_forearm" to Vec3.ZERO, "r_forearm" to Vec3.ZERO,
                "l_thigh" to Vec3.ZERO, "r_thigh" to Vec3.ZERO, "l_shin" to Vec3.ZERO, "r_shin" to Vec3.ZERO,
                "spine" to Vec3.ZERO, "head" to Vec3.ZERO
            )),
            pose("pose_a_pose", "A-Pose (Neutral)", "Core", 0xFF6366F1, mapOf(
                "l_upper_arm" to Vec3(0f, 0f, -38f), "r_upper_arm" to Vec3(0f, 0f, 38f),
                "l_forearm" to Vec3(-8f, 0f, 0f), "r_forearm" to Vec3(-8f, 0f, 0f),
                "l_thigh" to Vec3(0f, 0f, -5f), "r_thigh" to Vec3(0f, 0f, 5f)
            )),
            pose("pose_heroic", "Heroic Stance", "Action", 0xFFF59E0B, mapOf(
                "spine" to Vec3(-6f, 12f, 0f), "head" to Vec3(4f, -12f, 0f),
                "l_upper_arm" to Vec3(14f, 0f, -26f), "l_forearm" to Vec3(-42f, 0f, 18f),
                "r_upper_arm" to Vec3(-12f, 0f, 34f), "r_forearm" to Vec3(-56f, 0f, -24f),
                "l_thigh" to Vec3(-12f, 0f, -10f), "l_shin" to Vec3(14f, 0f, 0f),
                "r_thigh" to Vec3(10f, 0f, 10f), "r_shin" to Vec3(8f, 0f, 0f)
            )),
            pose("pose_wave", "Wave Hello", "Social", 0xFF10B981, mapOf(
                "spine" to Vec3(0f, -8f, -5f), "head" to Vec3(-4f, 10f, 6f),
                "r_upper_arm" to Vec3(-20f, 0f, 138f), "r_forearm" to Vec3(-18f, 0f, -32f),
                "l_upper_arm" to Vec3(8f, 0f, -14f), "l_forearm" to Vec3(-22f, 0f, 0f)
            )),
            pose("pose_run_stride", "Run Mid-Stride", "Action", 0xFFEF4444, mapOf(
                "spine" to Vec3(18f, -14f, 0f), "head" to Vec3(-12f, 10f, 0f),
                "l_upper_arm" to Vec3(-48f, 0f, -12f), "l_forearm" to Vec3(-72f, 0f, 0f),
                "r_upper_arm" to Vec3(46f, 0f, 12f), "r_forearm" to Vec3(-58f, 0f, 0f),
                "l_thigh" to Vec3(-48f, 0f, 0f), "l_shin" to Vec3(44f, 0f, 0f),
                "r_thigh" to Vec3(42f, 0f, 0f), "r_shin" to Vec3(68f, 0f, 0f)
            )),
            pose("pose_jump_apex", "Jump Apex", "Action", 0xFF38BDF8, mapOf(
                "spine" to Vec3(-10f, 0f, 0f), "head" to Vec3(-8f, 0f, 0f),
                "l_upper_arm" to Vec3(-32f, 0f, -128f), "l_forearm" to Vec3(-24f, 0f, 0f),
                "r_upper_arm" to Vec3(-32f, 0f, 128f), "r_forearm" to Vec3(-24f, 0f, 0f),
                "l_thigh" to Vec3(-42f, 0f, -10f), "l_shin" to Vec3(76f, 0f, 0f),
                "r_thigh" to Vec3(-26f, 0f, 10f), "r_shin" to Vec3(54f, 0f, 0f)
            )),
            pose("pose_combat_guard", "Combat Guard", "Combat", 0xFFEC4899, mapOf(
                "spine" to Vec3(12f, -26f, 0f), "head" to Vec3(-6f, 24f, 0f),
                "l_upper_arm" to Vec3(-56f, 22f, -18f), "l_forearm" to Vec3(-88f, 0f, 14f),
                "r_upper_arm" to Vec3(-42f, -18f, 24f), "r_forearm" to Vec3(-95f, 0f, -18f),
                "l_thigh" to Vec3(-28f, 0f, -12f), "l_shin" to Vec3(36f, 0f, 0f),
                "r_thigh" to Vec3(22f, 0f, 14f), "r_shin" to Vec3(24f, 0f, 0f)
            )),
            pose("pose_victory", "Victory Cheer", "Social", 0xFFFACC15, mapOf(
                "spine" to Vec3(-12f, 0f, 0f), "head" to Vec3(-14f, 0f, 0f),
                "l_upper_arm" to Vec3(-14f, 0f, -148f), "l_forearm" to Vec3(-18f, 0f, 16f),
                "r_upper_arm" to Vec3(-14f, 0f, 148f), "r_forearm" to Vec3(-18f, 0f, -16f),
                "l_thigh" to Vec3(-8f, 0f, -12f), "r_thigh" to Vec3(-8f, 0f, 12f)
            )),
            pose("pose_sit_relaxed", "Relaxed Sit", "Social", 0xFFA855F7, mapOf(
                "spine" to Vec3(6f, 0f, 0f), "head" to Vec3(-4f, 6f, 0f),
                "l_upper_arm" to Vec3(-24f, 0f, -14f), "l_forearm" to Vec3(-44f, 0f, 0f),
                "r_upper_arm" to Vec3(-24f, 0f, 14f), "r_forearm" to Vec3(-44f, 0f, 0f),
                "l_thigh" to Vec3(-86f, 0f, -6f), "l_shin" to Vec3(84f, 0f, 0f),
                "r_thigh" to Vec3(-86f, 0f, 6f), "r_shin" to Vec3(84f, 0f, 0f)
            )),
            pose("pose_deep_bow", "Respectful Bow", "Social", 0xFF14B8A6, mapOf(
                "spine" to Vec3(46f, 0f, 0f), "head" to Vec3(16f, 0f, 0f),
                "l_upper_arm" to Vec3(14f, 0f, -12f), "l_forearm" to Vec3(-24f, 0f, 18f),
                "r_upper_arm" to Vec3(14f, 0f, 12f), "r_forearm" to Vec3(-24f, 0f, -18f)
            ))
        )
    }

    private fun buildBuiltInLibraryCatalog(): List<LibraryAssetEntity> {
        val now = System.currentTimeMillis()
        fun entry(
            id: String,
            name: String,
            assetCat: AssetCategory,
            charCat: CharacterCategory,
            orient: OrientationFilter,
            archetype: CharacterArchetype,
            costume: CostumePreset,
            c1: Long,
            c2: Long,
            bones: Int,
            pinned: Boolean = false
        ) = LibraryAssetEntity(
            id = id,
            name = name,
            assetCategory = assetCat.name,
            characterCategory = charCat.name,
            orientationTag = orient.name,
            archetype = archetype.name,
            costumePreset = costume.name,
            primaryColorHex = c1,
            secondaryColorHex = c2,
            meshCount = 1,
            vertexCount = 780,
            boneCount = bones,
            animationCount = 16,
            isPinned = pinned,
            isBuiltIn = true,
            sourceFormat = "Built-in 3D Rig",
            createdAt = now
        )

        return listOf(
            entry("char_streetwear_maya", "Maya Streetwear", AssetCategory.CHARACTERS, CharacterCategory.LATEST, OrientationFilter.RIG_360, CharacterArchetype.HUMANOID, CostumePreset.FORMAL, 0xFF1E222A, 0xFFE2E8F0, 11, pinned = true),
            entry("char_desi_hero", "Aarav (Desi Festive)", AssetCategory.CHARACTERS, CharacterCategory.DESI, OrientationFilter.RIG_360, CharacterArchetype.HUMANOID, CostumePreset.INDIAN_TRADITIONAL, 0xFFDC2626, 0xFFF59E0B, 11, pinned = true),
            entry("char_cyber_bot", "Nova Cyber Bot", AssetCategory.CHARACTERS, CharacterCategory.LATEST, OrientationFilter.RIG_360, CharacterArchetype.HUMANOID, CostumePreset.ORIGINAL_HERO, 0xFFF1F5F9, 0xFF22D3EE, 11, pinned = true),
            entry("char_royal_knight", "Kavi Royal Knight", AssetCategory.CHARACTERS, CharacterCategory.LATEST, OrientationFilter.RIG_360, CharacterArchetype.HUMANOID, CostumePreset.FANTASY, 0xFF94A3B8, 0xFF6366F1, 11, pinned = true),
            entry("char_desi_police", "Inspector Vikram", AssetCategory.CHARACTERS, CharacterCategory.DESI, OrientationFilter.FRONT, CharacterArchetype.HUMANOID, CostumePreset.POLICE, 0xFF926E3C, 0xFFFACC15, 11),
            entry("char_western_ranger", "Alex Explorer", AssetCategory.CHARACTERS, CharacterCategory.WESTERN, OrientationFilter.THREE_QUARTER, CharacterArchetype.HUMANOID, CostumePreset.FORMAL, 0xFF334155, 0xFF6366F1, 11, pinned = true),
            entry("char_indo_dancer", "Bima Nusantara", AssetCategory.CHARACTERS, CharacterCategory.INDONESIAN, OrientationFilter.RIG_360, CharacterArchetype.HUMANOID, CostumePreset.MYTHOLOGICAL, 0xFFD97706, 0xFF22D3EE, 11),
            entry("char_village_farmer", "Raju Village Elder", AssetCategory.CHARACTERS, CharacterCategory.VILLAGE, OrientationFilter.SIDE, CharacterArchetype.HUMANOID, CostumePreset.VILLAGE, 0xFFB45309, 0xFFFDE68A, 11),
            entry("char_school_student", "Maya Student", AssetCategory.CHARACTERS, CharacterCategory.LATEST, OrientationFilter.FRONT, CharacterArchetype.HUMANOID, CostumePreset.SCHOOL_UNIFORM, 0xFF1D4ED8, 0xFFF1F5F9, 11),
            entry("char_doctor", "Dr. Sarah Medic", AssetCategory.CHARACTERS, CharacterCategory.WESTERN, OrientationFilter.THREE_QUARTER, CharacterArchetype.HUMANOID, CostumePreset.DOCTOR, 0xFFE2E8F0, 0xFF0EA5E9, 11),
            entry("char_red_panda", "Momo Red Panda", AssetCategory.CHARACTERS, CharacterCategory.ANIMALS, OrientationFilter.RIG_360, CharacterArchetype.QUADRUPED, CostumePreset.FANTASY, 0xFFC2410C, 0xFFFFF7ED, 6),
            entry("char_bird_eagle", "Sky Eagle Rig", AssetCategory.CHARACTERS, CharacterCategory.BIRDS, OrientationFilter.RIG_360, CharacterArchetype.BIRD, CostumePreset.FANTASY, 0xFF78350F, 0xFFF59E0B, 4),
            entry("char_animal_wolf", "Timber Wolf Rig", AssetCategory.CHARACTERS, CharacterCategory.ANIMALS, OrientationFilter.SIDE, CharacterArchetype.QUADRUPED, CostumePreset.VILLAGE, 0xFF64748B, 0xFFCBD5E1, 6),
            entry("char_vehicle_jeep", "Studio Offroad Jeep", AssetCategory.VEHICLES, CharacterCategory.VEHICLES, OrientationFilter.THREE_QUARTER, CharacterArchetype.VEHICLE, CostumePreset.HISTORICAL, 0xFF059669, 0xFF1F2937, 3),
            entry("char_rel_shrine", "Temple Bell Shrine", AssetCategory.PROPS, CharacterCategory.RELIGIOUS, OrientationFilter.FRONT, CharacterArchetype.PROP_SHRINE, CostumePreset.MYTHOLOGICAL, 0xFFD97706, 0xFFFEF3C7, 0),
            entry("char_horror_phantom", "Midnight Phantom", AssetCategory.CHARACTERS, CharacterCategory.HORROR, OrientationFilter.RIG_360, CharacterArchetype.FLOATING_SPIRIT, CostumePreset.FANTASY, 0xFF312E81, 0xFF22D3EE, 11),
            entry("char_blender_mannequin", "Studio Rig Mannequin", AssetCategory.SKELETONS, CharacterCategory.BLENDER, OrientationFilter.RIG_360, CharacterArchetype.HUMANOID, CostumePreset.ORIGINAL_HERO, 0xFF6366F1, 0xFF22D3EE, 11, pinned = true),
            entry("asset_env_stage", "3D Studio Cyclorama", AssetCategory.ENVIRONMENTS, CharacterCategory.CUSTOM, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFF1E293B, 0xFF22D3EE, 0),
            entry("asset_env_village", "3D Village Sunset Set", AssetCategory.ENVIRONMENTS, CharacterCategory.VILLAGE, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.VILLAGE, 0xFFC29B61, 0xFFF59E0B, 0),
            entry("asset_env_city", "3D Twilight City Set", AssetCategory.ENVIRONMENTS, CharacterCategory.WESTERN, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFF1E293B, 0xFF6366F1, 0),
            entry("asset_env_forest", "3D Forest Meadow Set", AssetCategory.ENVIRONMENTS, CharacterCategory.ANIMALS, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FANTASY, 0xFF2D6A31, 0xFF22C55E, 0),
            entry("asset_env_cyber", "3D Cyber Runway Set", AssetCategory.ENVIRONMENTS, CharacterCategory.LATEST, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.ORIGINAL_HERO, 0xFF0A0D18, 0xFF22D3EE, 0),
            entry("asset_light_key", "Warm Studio Key Light", AssetCategory.LIGHTS, CharacterCategory.CUSTOM, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFFFFF1D0, 0xFFF59E0B, 0),
            entry("asset_cam_cine", "Cinematic 35mm Camera", AssetCategory.CAMERAS, CharacterCategory.CUSTOM, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFF38BDF8, 0xFF6366F1, 0)
        )
    }
}
