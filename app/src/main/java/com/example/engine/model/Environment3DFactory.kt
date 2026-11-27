package com.example.engine.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds complete, stylized 3D Background Environment Sets as single GPU-cached vertex-colored meshes
 * so switching 3D worlds runs at a locked 60fps with just 1 draw call per set.
 */
object Environment3DFactory {

    const val ENV_NODE_TAG = "ENVIRONMENT_3D_SET"

    fun buildEnvironmentSetNode(backdrop: BackdropPreset): SceneNode? {
        val mesh = when (backdrop) {
            BackdropPreset.STUDIO_DARK -> buildStudioCycloramaMesh()
            BackdropPreset.VILLAGE_SUNSET -> buildVillageLaneMesh()
            BackdropPreset.TWILIGHT_CITY -> buildTwilightCityMesh()
            BackdropPreset.FOREST_MEADOW -> buildForestMeadowMesh()
            BackdropPreset.CYBER_NEON -> buildCyberRunwayMesh()
            BackdropPreset.ROYAL_COURTYARD -> buildRoyalCourtyardMesh()
            BackdropPreset.DESERT_DUNES -> buildDesertDunesMesh()
            BackdropPreset.SPACE_ARENA -> buildSpaceArenaMesh()
        }
        return SceneNode(
            name = "3D Set: ${backdrop.label}",
            nodeType = NodeType.MESH,
            primitiveKind = PrimitiveKind.CUSTOM,
            transform = Transform3D(),
            mesh = mesh,
            material = MaterialData(
                name = "${backdrop.label} Environment",
                baseColor = 0xFFFFFFFF,
                roughness = if (backdrop == BackdropPreset.CYBER_NEON || backdrop == BackdropPreset.SPACE_ARENA) 0.35f else 0.82f,
                metallic = if (backdrop == BackdropPreset.CYBER_NEON || backdrop == BackdropPreset.SPACE_ARENA) 0.45f else 0.05f
            ),
            categoryTag = ENV_NODE_TAG
        )
    }

    fun buildLightsForBackdrop(backdrop: BackdropPreset): List<SceneNode> = when (backdrop) {
        BackdropPreset.VILLAGE_SUNSET -> listOf(
            SceneNode(name = "Golden Sun Key", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(4.5f, 4.2f, -3.5f)), light = LightData(LightType.POINT, 0xFFFFD580, 2.1f, 24f)),
            SceneNode(name = "Warm Fill Light", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.2f, 2.6f, 3.8f)), light = LightData(LightType.POINT, 0xFFFDBA74, 1.15f, 18f))
        )
        BackdropPreset.TWILIGHT_CITY -> listOf(
            SceneNode(name = "Café Warm Lamp", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(2.8f, 3.2f, 2.2f)), light = LightData(LightType.POINT, 0xFFFBBF24, 1.85f, 20f)),
            SceneNode(name = "Neon Cyan Rim", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.5f, 2.8f, -2.5f)), light = LightData(LightType.POINT, 0xFF22D3EE, 1.65f, 20f))
        )
        BackdropPreset.FOREST_MEADOW -> listOf(
            SceneNode(name = "Meadow Sunlight", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(3.8f, 5.5f, 3.2f)), light = LightData(LightType.POINT, 0xFFFEF9C3, 2.0f, 26f)),
            SceneNode(name = "Sky Bounce Fill", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.2f, 3.4f, -2.4f)), light = LightData(LightType.POINT, 0xFF93C5FD, 1.1f, 20f))
        )
        BackdropPreset.CYBER_NEON -> listOf(
            SceneNode(name = "Electric Indigo Spot", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(3.0f, 4.5f, 3.0f)), light = LightData(LightType.POINT, 0xFF818CF8, 2.0f, 22f)),
            SceneNode(name = "Cyber Cyan Rim", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.0f, 3.0f, -2.8f)), light = LightData(LightType.POINT, 0xFF22D3EE, 1.85f, 22f))
        )
        BackdropPreset.ROYAL_COURTYARD -> listOf(
            SceneNode(name = "Torchlight Key", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(3.2f, 3.6f, 2.8f)), light = LightData(LightType.POINT, 0xFFFB923C, 1.95f, 22f)),
            SceneNode(name = "Moonlight Rim", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.4f, 4.4f, -3.0f)), light = LightData(LightType.POINT, 0xFF93C5FD, 1.25f, 22f))
        )
        BackdropPreset.DESERT_DUNES -> listOf(
            SceneNode(name = "Desert Sun", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(4.5f, 5.2f, 3.0f)), light = LightData(LightType.POINT, 0xFFFFEDD5, 2.1f, 26f)),
            SceneNode(name = "Amber Bounce", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.0f, 2.2f, -2.5f)), light = LightData(LightType.POINT, 0xFFF59E0B, 1.15f, 18f))
        )
        BackdropPreset.SPACE_ARENA -> listOf(
            SceneNode(name = "Starlight Key", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(3.5f, 4.8f, 3.5f)), light = LightData(LightType.POINT, 0xFFE0F2FE, 1.9f, 24f)),
            SceneNode(name = "Nebula Rim", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.6f, 2.8f, -3.2f)), light = LightData(LightType.POINT, 0xFFA855F7, 1.6f, 22f))
        )
        BackdropPreset.STUDIO_DARK -> listOf(
            SceneNode(name = "Studio Key Light", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(3.2f, 4.5f, 3.6f)), light = LightData(LightType.POINT, 0xFFF8FAFC, 1.85f, 20f)),
            SceneNode(name = "Cyan Rim Light", nodeType = NodeType.LIGHT, transform = Transform3D(Vec3(-3.0f, 2.8f, -2.6f)), light = LightData(LightType.POINT, 0xFF22D3EE, 1.35f, 16f))
        )
    }

    private fun part(mesh: MeshData, pos: Vec3, color: Long, rot: Vec3 = Vec3.ZERO): Triple<MeshData, Int, Long> {
        val m = if (rot != Vec3.ZERO) PrimitiveMeshFactory.rotateMesh(mesh, rot) else mesh
        return Triple(PrimitiveMeshFactory.translateMesh(m, pos), 0, color)
    }

    private fun buildStudioCycloramaMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Circular studio turntable stage + glowing rim ring
        parts += part(PrimitiveMeshFactory.createCylinder(3.4f, 3.6f, 0.14f, 28), Vec3(0f, -0.07f, 0f), 0xFF1A202E)
        parts += part(PrimitiveMeshFactory.createTorus(3.42f, 0.045f, 32, 8), Vec3(0f, 0.01f, 0f), 0xFF22D3EE)
        parts += part(PrimitiveMeshFactory.createTorus(2.2f, 0.025f, 28, 6), Vec3(0f, 0.005f, 0f), 0xFF6366F1)
        // Curved studio backdrop light stands in rear
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.18f, 3.2f, 0.18f), Vec3(-3.6f, 1.6f, -3.4f), 0xFF1E293B)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.10f, 2.6f, 0.06f), Vec3(-3.55f, 1.65f, -3.30f), 0xFF6366F1)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.18f, 3.2f, 0.18f), Vec3(3.6f, 1.6f, -3.4f), 0xFF1E293B)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.10f, 2.6f, 0.06f), Vec3(3.55f, 1.65f, -3.30f), 0xFF22D3EE)
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildVillageLaneMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Warm sandy lane & lush verge
        parts += part(PrimitiveMeshFactory.createCube(1f, 14f, 0.12f, 14f), Vec3(0f, -0.06f, -1.5f), 0xFF6B8E23)
        parts += part(PrimitiveMeshFactory.createCube(1f, 3.6f, 0.14f, 13.5f), Vec3(0f, -0.055f, -1.5f), 0xFFC29B61)
        // Left & Right terracotta Indian village huts
        fun addHut(x: Float, z: Float, wallHex: Long) {
            parts += part(PrimitiveMeshFactory.createCube(1f, 2.2f, 1.7f, 2.0f), Vec3(x, 0.85f, z), wallHex)
            parts += part(PrimitiveMeshFactory.createCone(1.85f, 1.15f, 4), Vec3(x, 2.25f, z), 0xFFB45309, Vec3(0f, 45f, 0f))
            parts += part(PrimitiveMeshFactory.createCube(1f, 0.55f, 1.05f, 0.12f), Vec3(x + if (x < 0) 1.08f else -1.08f, 0.54f, z), 0xFF5C3A21)
        }
        addHut(-3.6f, -2.4f, 0xFFE6BE8A)
        addHut(-3.8f, -5.6f, 0xFFD99B66)
        addHut(3.7f, -2.8f, 0xFF60A5FA)
        addHut(3.8f, -6.0f, 0xFFE6BE8A)
        // Banyan trees with layered emerald canopy
        fun addTree(x: Float, z: Float, scale: Float) {
            parts += part(PrimitiveMeshFactory.createCylinder(0.24f * scale, 0.34f * scale, 2.2f * scale, 10), Vec3(x, 1.1f * scale, z), 0xFF5C3A21)
            parts += part(PrimitiveMeshFactory.createSphere(1.25f * scale, 10, 8), Vec3(x, 2.55f * scale, z), 0xFF2F6F28)
            parts += part(PrimitiveMeshFactory.createSphere(0.95f * scale, 10, 8), Vec3(x + 0.65f * scale, 2.2f * scale, z + 0.25f * scale), 0xFF3E8E35)
            parts += part(PrimitiveMeshFactory.createSphere(0.90f * scale, 10, 8), Vec3(x - 0.60f * scale, 2.15f * scale, z - 0.2f * scale), 0xFF25591F)
        }
        addTree(-3.2f, -0.2f, 1.05f)
        addTree(3.6f, -0.4f, 0.92f)
        addTree(-1.8f, -7.2f, 1.2f)
        // Warm low mud boundary walls
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.22f, 0.72f, 4.6f), Vec3(-2.15f, 0.36f, -3.2f), 0xFFB08968)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.22f, 0.72f, 4.6f), Vec3(2.15f, 0.36f, -3.2f), 0xFFB08968)
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildTwilightCityMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Wet asphalt boulevard + sidewalks
        parts += part(PrimitiveMeshFactory.createCube(1f, 14f, 0.12f, 14f), Vec3(0f, -0.06f, -2f), 0xFF141824)
        parts += part(PrimitiveMeshFactory.createCube(1f, 3.8f, 0.20f, 13.5f), Vec3(-4.2f, 0.04f, -2f), 0xFF2A3142)
        parts += part(PrimitiveMeshFactory.createCube(1f, 3.8f, 0.20f, 13.5f), Vec3(4.2f, 0.04f, -2f), 0xFF2A3142)
        // Glowing road stripe
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.14f, 0.02f, 10f), Vec3(0f, 0.01f, -2f), 0xFF22D3EE)
        // Cozy glowing corner café on right + neon buildings
        parts += part(PrimitiveMeshFactory.createCube(1f, 2.8f, 2.6f, 3.2f), Vec3(4.0f, 1.4f, -3.2f), 0xFF473026)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.15f, 1.35f, 2.4f), Vec3(2.58f, 1.15f, -3.2f), 0xFFFBBF24) // glowing window
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.35f, 0.35f, 2.7f), Vec3(2.55f, 2.15f, -3.2f), 0xFF22D3EE) // neon awning sign
        // Left & background skyscrapers
        val towers = listOf(
            Triple(Vec3(-4.2f, 2.4f, -3.0f), Vec3(2.4f, 4.8f, 2.6f), 0xFF1E293B),
            Triple(Vec3(-3.8f, 3.2f, -6.8f), Vec3(2.6f, 6.4f, 2.6f), 0xFF172038),
            Triple(Vec3(0.4f, 3.8f, -7.8f), Vec3(3.0f, 7.6f, 2.4f), 0xFF1E1B4B),
            Triple(Vec3(4.2f, 3.4f, -7.2f), Vec3(2.6f, 6.8f, 2.5f), 0xFF1E293B)
        )
        for ((p, s, col) in towers) {
            parts += part(PrimitiveMeshFactory.createCube(1f, s.x, s.y, s.z), p, col)
            parts += part(PrimitiveMeshFactory.createCube(1f, s.x * 0.72f, 0.16f, s.z * 0.72f), Vec3(p.x, p.y + s.y * 0.5f + 0.08f, p.z), 0xFF6366F1)
        }
        // Streetlamps with warm glowing lanterns
        for (sx in listOf(-2.45f, 2.45f)) {
            parts += part(PrimitiveMeshFactory.createCylinder(0.05f, 0.07f, 2.8f, 8), Vec3(sx, 1.4f, -0.8f), 0xFF334155)
            parts += part(PrimitiveMeshFactory.createSphere(0.18f, 10, 8), Vec3(sx, 2.9f, -0.8f), 0xFFFDE047)
        }
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildForestMeadowMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Emerald meadow & winding dirt trail
        parts += part(PrimitiveMeshFactory.createCube(1f, 15f, 0.12f, 15f), Vec3(0f, -0.06f, -2f), 0xFF2D6A31)
        parts += part(PrimitiveMeshFactory.createCylinder(1.25f, 1.35f, 0.13f, 16), Vec3(0f, -0.055f, 0f), 0xFFC5A375)
        parts += part(PrimitiveMeshFactory.createCube(1f, 1.8f, 0.13f, 9f), Vec3(0f, -0.055f, -4.2f), 0xFFC5A375)
        // Rolling background hills
        parts += part(PrimitiveMeshFactory.createSphere(3.4f, 12, 8), Vec3(-4.2f, -1.4f, -6.5f), 0xFF3B8A3E)
        parts += part(PrimitiveMeshFactory.createSphere(3.8f, 12, 8), Vec3(4.2f, -1.5f, -6.8f), 0xFF2F7732)
        // Stylized layered pine & oak trees
        val treeCoords = listOf(
            Triple(-3.4f, -2.0f, 1.1f), Triple(-4.2f, -4.8f, 1.3f),
            Triple(3.4f, -1.8f, 1.0f), Triple(4.1f, -4.5f, 1.25f),
            Triple(-1.8f, -6.8f, 1.15f), Triple(2.0f, -7.0f, 1.2f)
        )
        for ((tx, tz, sc) in treeCoords) {
            parts += part(PrimitiveMeshFactory.createCylinder(0.16f * sc, 0.24f * sc, 1.3f * sc, 8), Vec3(tx, 0.65f * sc, tz), 0xFF6D4C41)
            parts += part(PrimitiveMeshFactory.createCone(1.1f * sc, 1.6f * sc, 10), Vec3(tx, 1.85f * sc, tz), 0xFF15803D)
            parts += part(PrimitiveMeshFactory.createCone(0.85f * sc, 1.35f * sc, 10), Vec3(tx, 2.75f * sc, tz), 0xFF22C55E)
        }
        // Colorful wildflower clusters & mossy boulders
        val flowerColors = listOf(0xFFEF4444L, 0xFFFACC15L, 0xFF38BDF8L, 0xFFF472B6L)
        for (i in 0 until 8) {
            val angle = i * 0.78f
            val r = 2.2f + (i % 3) * 0.65f
            parts += part(PrimitiveMeshFactory.createSphere(0.14f, 8, 6), Vec3(cos(angle) * r, 0.12f, sin(angle) * r - 1.2f), flowerColors[i % flowerColors.size])
        }
        parts += part(PrimitiveMeshFactory.createSphere(0.42f, 8, 6), Vec3(-2.0f, 0.15f, -1.8f), 0xFF64748B)
        parts += part(PrimitiveMeshFactory.createSphere(0.32f, 8, 6), Vec3(2.2f, 0.10f, -2.6f), 0xFF475569)
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildCyberRunwayMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Dark obsidian cyber deck + glowing neon catwalk
        parts += part(PrimitiveMeshFactory.createCube(1f, 14f, 0.12f, 14f), Vec3(0f, -0.08f, -2f), 0xFF0A0D18)
        parts += part(PrimitiveMeshFactory.createCube(1f, 2.8f, 0.16f, 11f), Vec3(0f, -0.02f, -2f), 0xFF161D33)
        // Glowing cyan & electric indigo runway edge rails
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.09f, 0.09f, 11f), Vec3(-1.42f, 0.08f, -2f), 0xFF22D3EE)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.09f, 0.09f, 11f), Vec3(1.42f, 0.08f, -2f), 0xFF22D3EE)
        // Neon geometric portal arches down the runway
        for (z in listOf(-2.4f, -4.8f, -7.2f)) {
            parts += part(PrimitiveMeshFactory.createCube(1f, 0.24f, 3.6f, 0.24f), Vec3(-2.4f, 1.8f, z), 0xFF1E293B)
            parts += part(PrimitiveMeshFactory.createCube(1f, 0.24f, 3.6f, 0.24f), Vec3(2.4f, 1.8f, z), 0xFF1E293B)
            parts += part(PrimitiveMeshFactory.createCube(1f, 5.1f, 0.24f, 0.24f), Vec3(0f, 3.6f, z), 0xFF6366F1)
            parts += part(PrimitiveMeshFactory.createCube(1f, 0.08f, 3.3f, 0.08f), Vec3(-2.22f, 1.8f, z), 0xFF22D3EE)
            parts += part(PrimitiveMeshFactory.createCube(1f, 0.08f, 3.3f, 0.08f), Vec3(2.22f, 1.8f, z), 0xFFA855F7)
        }
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildRoyalCourtyardMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Stone flagstone courtyard + royal carpet runner
        parts += part(PrimitiveMeshFactory.createCube(1f, 14f, 0.14f, 14f), Vec3(0f, -0.07f, -2f), 0xFF3F3F46)
        parts += part(PrimitiveMeshFactory.createCube(1f, 2.2f, 0.04f, 10f), Vec3(0f, 0.01f, -2f), 0xFF991B1B)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.10f, 0.045f, 10f), Vec3(-1.05f, 0.015f, -2f), 0xFFF59E0B)
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.10f, 0.045f, 10f), Vec3(1.05f, 0.015f, -2f), 0xFFF59E0B)
        // Stone castle wall & battlements in rear
        parts += part(PrimitiveMeshFactory.createCube(1f, 11f, 3.6f, 0.8f), Vec3(0f, 1.8f, -6.2f), 0xFF52525B)
        for (bx in listOf(-4.4f, -2.2f, 0f, 2.2f, 4.4f)) {
            parts += part(PrimitiveMeshFactory.createCube(1f, 0.9f, 0.7f, 0.9f), Vec3(bx, 3.9f, -6.2f), 0xFF71717A)
        }
        // Flanking stone pillars with glowing warm torches & crimson banners
        for (px in listOf(-3.0f, 3.0f)) {
            for (pz in listOf(-1.6f, -4.4f)) {
                parts += part(PrimitiveMeshFactory.createCylinder(0.32f, 0.36f, 3.4f, 10), Vec3(px, 1.7f, pz), 0xFF64748B)
                parts += part(PrimitiveMeshFactory.createCube(1f, 0.45f, 1.5f, 0.06f), Vec3(px, 2.1f, pz + 0.36f), 0xFF991B1B)
                parts += part(PrimitiveMeshFactory.createSphere(0.18f, 8, 8), Vec3(px, 3.55f, pz), 0xFFFB923C)
            }
        }
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildDesertDunesMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        parts += part(PrimitiveMeshFactory.createCube(1f, 16f, 0.12f, 16f), Vec3(0f, -0.06f, -2f), 0xFFD99B52)
        parts += part(PrimitiveMeshFactory.createSphere(4.2f, 12, 8), Vec3(-4.5f, -2.0f, -5.8f), 0xFFE5AE67)
        parts += part(PrimitiveMeshFactory.createSphere(4.8f, 12, 8), Vec3(4.2f, -2.2f, -6.6f), 0xFFC88A40)
        // Ancient sandstone pyramid & obelisks
        parts += part(PrimitiveMeshFactory.createCone(3.4f, 4.2f, 4), Vec3(-1.5f, 2.1f, -8.2f), 0xFFB4783A, Vec3(0f, 45f, 0f))
        parts += part(PrimitiveMeshFactory.createCube(1f, 0.5f, 2.8f, 0.5f), Vec3(3.2f, 1.4f, -3.5f), 0xFFA16207)
        parts += part(PrimitiveMeshFactory.createCone(0.42f, 0.55f, 4), Vec3(3.2f, 3.05f, -3.5f), 0xFFF59E0B, Vec3(0f, 45f, 0f))
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }

    private fun buildSpaceArenaMesh(): MeshData {
        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        // Floating hexagonal starlight platform + glowing plasma rings
        parts += part(PrimitiveMeshFactory.createCylinder(3.6f, 3.0f, 0.24f, 6), Vec3(0f, -0.12f, 0f), 0xFF111827)
        parts += part(PrimitiveMeshFactory.createTorus(3.75f, 0.06f, 28, 8), Vec3(0f, 0.02f, 0f), 0xFF38BDF8)
        parts += part(PrimitiveMeshFactory.createTorus(5.2f, 0.08f, 36, 8), Vec3(0f, 1.4f, -2.5f), 0xFF6366F1, Vec3(68f, 18f, 0f))
        // Floating crystal monoliths & distant planetoid
        parts += part(PrimitiveMeshFactory.createSphere(1.4f, 14, 10), Vec3(-4.5f, 3.4f, -7.5f), 0xFF7C3AED)
        parts += part(PrimitiveMeshFactory.createTorus(2.0f, 0.09f, 24, 6), Vec3(-4.5f, 3.4f, -7.5f), 0xFF38BDF8, Vec3(72f, 22f, 0f))
        parts += part(PrimitiveMeshFactory.createCone(0.45f, 1.6f, 6), Vec3(3.6f, 1.5f, -3.6f), 0xFF22D3EE)
        return PrimitiveMeshFactory.mergeColoredMeshes(parts)
    }
}
