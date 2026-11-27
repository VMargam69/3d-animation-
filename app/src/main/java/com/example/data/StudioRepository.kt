package com.example.data

import android.content.Context
import com.example.engine.model.AccessoryKind
import com.example.engine.model.AnimationClipTrack
import com.example.engine.model.AssetCategory
import com.example.engine.model.CameraData
import com.example.engine.model.CharacterArchetype
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterCategory
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.CostumePreset
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
import com.example.engine.model.TimelineState
import com.example.engine.model.Transform3D
import com.example.engine.model.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class StudioRepository(private val context: Context) {
    private val dao = StudioDatabase.getInstance(context).studioDao()
    private val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
    private val assetsDir = File(context.filesDir, "library_nodes").apply { mkdirs() }

    val projectsFlow: Flow<List<ProjectEntity>> = dao.observeProjects()
    val libraryAssetsFlow: Flow<List<LibraryAssetEntity>> = dao.observeLibraryAssets()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val existingIds = dao.getAllLibraryAssetIds().toHashSet()
        val missing = buildBuiltInLibraryCatalog().filter { it.id !in existingIds }
        if (missing.isNotEmpty()) {
            dao.upsertLibraryAssets(missing)
        }
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
        val floor = SceneNode(
            name = "Stage Floor",
            nodeType = NodeType.MESH,
            primitiveKind = PrimitiveKind.PLANE,
            transform = Transform3D(position = Vec3(0f, 0f, 0f), scale = Vec3(2.2f, 1f, 2.2f)),
            mesh = PrimitiveMeshFactory.createPlane(3.2f, 3.2f, 4),
            material = MaterialData(name = "Charcoal Floor", baseColor = 0xFF1F2937, roughness = 0.85f, metallic = 0.05f)
        )
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
        val keyLight = SceneNode(
            name = "Key Light",
            nodeType = NodeType.LIGHT,
            transform = Transform3D(position = Vec3(3.2f, 4.5f, 3.6f)),
            light = LightData(LightType.POINT, 0xFFF8FAFC, 1.8f, 18f, true)
        )
        val rimLight = SceneNode(
            name = "Cyan Rim Light",
            nodeType = NodeType.LIGHT,
            transform = Transform3D(position = Vec3(-3.0f, 2.8f, -2.6f)),
            light = LightData(LightType.POINT, 0xFF22D3EE, 1.35f, 14f, false)
        )
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
            nodes = listOf(hero, floor, pedestalProp, keyLight, rimLight, mainCam),
            timeline = TimelineState(
                fps = 30,
                totalFrames = 120,
                currentFrame = 0f,
                isLooping = true,
                keyframes = kfs,
                clips = listOf(clip)
            )
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
            timeline = TimelineState(fps = 30, totalFrames = 120)
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
        // Build genuine 3D procedural node for built-in catalog entries
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
            AssetCategory.ENVIRONMENTS.name -> SceneNode(
                name = asset.name,
                nodeType = NodeType.MESH,
                primitiveKind = PrimitiveKind.PLANE,
                mesh = PrimitiveMeshFactory.createPlane(6f, 6f, 6),
                material = MaterialData(name = asset.name, baseColor = asset.primaryColorHex, roughness = 0.85f)
            )
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
        /**
         * Builds the genuine 3D mesh & material used to render real 3D shaded preview thumbnails
         * for built-in characters, custom creator characters, imported Blender models, and studio props.
         */
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
            vertexCount = 720,
            boneCount = bones,
            animationCount = 16,
            isPinned = pinned,
            isBuiltIn = true,
            sourceFormat = "Built-in 3D Rig",
            createdAt = now
        )

        return listOf(
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
            entry("asset_env_stage", "Studio Ground Plane", AssetCategory.ENVIRONMENTS, CharacterCategory.CUSTOM, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFF1E293B, 0xFF334155, 0),
            entry("asset_light_key", "Warm Studio Key Light", AssetCategory.LIGHTS, CharacterCategory.CUSTOM, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFFFFF1D0, 0xFFF59E0B, 0),
            entry("asset_cam_cine", "Cinematic 35mm Camera", AssetCategory.CAMERAS, CharacterCategory.CUSTOM, OrientationFilter.ALL, CharacterArchetype.PROP_SHRINE, CostumePreset.FORMAL, 0xFF38BDF8, 0xFF6366F1, 0)
        )
    }
}
