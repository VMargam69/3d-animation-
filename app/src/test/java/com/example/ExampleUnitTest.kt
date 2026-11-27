package com.example

import com.example.data.SceneSerializer
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.animation.AnimationPresets
import com.example.engine.animation.IkSolver
import com.example.engine.io.GlbExporter
import com.example.engine.io.ModelImporter
import com.example.engine.model.BackdropPreset
import com.example.engine.model.CharacterArchetype
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.Environment3DFactory
import com.example.engine.model.IkLimb
import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.KeyframeProperty
import com.example.engine.model.Math3D
import com.example.engine.model.MeshEditingOps
import com.example.engine.model.NodeType
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.PrimitiveMeshFactory
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.TimelineState
import com.example.engine.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

    @Test
    fun primitiveMeshFactoryGeneratesAllPrimitives() {
        val kinds = PrimitiveKind.entries.filter { it != PrimitiveKind.CUSTOM }
        for (kind in kinds) {
            val mesh = PrimitiveMeshFactory.createPrimitive(kind)
            assertTrue("Expected vertices for $kind", mesh.vertexCount >= 3)
            assertTrue("Expected triangles for $kind", mesh.triangleCount >= 1)
            assertEquals(mesh.vertexCount * 3, mesh.normals.size)
            assertEquals(mesh.vertexCount * 2, mesh.uvs.size)
        }
    }

    @Test
    fun editModeOperationsMutateReal3dTopology() {
        val cube = PrimitiveMeshFactory.createPrimitive(PrimitiveKind.CUBE)
        assertEquals(24, cube.vertexCount)
        assertEquals(12, cube.triangleCount)

        val extruded = MeshEditingOps.extrudeFace(cube, 0, 0.35f)
        assertEquals(27, extruded.vertexCount)
        assertEquals(18, extruded.triangleCount)

        val inset = MeshEditingOps.insetFace(cube, 0, 0.25f)
        assertEquals(27, inset.vertexCount)

        val subdivided = MeshEditingOps.subdivideMesh(cube)
        assertEquals(48, subdivided.triangleCount)

        val beveled = MeshEditingOps.bevelMesh(cube, 0.2f)
        assertEquals(48, beveled.triangleCount)
    }

    @Test
    fun riggedCharacterAndAnimationPresetsEvaluateCleanly() {
        val hero = CharacterMeshBuilder.buildRiggedCharacterNode(
            CharacterBuildSpec(name = "Test Rig", skinColorOverride = 0xFFD99B6EL)
        )
        assertTrue((hero.skeleton?.bones?.size ?: 0) >= 11)
        assertTrue((hero.mesh?.vertexCount ?: 0) > 100)

        assertEquals(16, AnimationPresets.allPresets.size)
        for (preset in AnimationPresets.allPresets) {
            val (hipDelta, bonePose) = AnimationPresets.evaluatePreset(preset.id, 0.25f)
            assertTrue("Preset ${preset.id} should drive bones or root motion", bonePose.isNotEmpty() || hipDelta != Vec3.ZERO)
            assertTrue("Preset ${preset.id} hipDelta must be finite", hipDelta.x.isFinite() && hipDelta.y.isFinite() && hipDelta.z.isFinite())
        }
    }

    @Test
    fun allCharacterArchetypesAndBuiltInStylesBuildMultiColorMeshes() {
        val styleIds = listOf(
            "char_streetwear_maya",
            "char_desi_hero", "char_cyber_bot", "char_royal_knight",
            "char_desi_police", "char_western_ranger", "char_indo_dancer",
            "char_village_farmer", "char_school_student", "char_doctor",
            "char_blender_mannequin"
        )
        for (sid in styleIds) {
            val node = CharacterMeshBuilder.buildRiggedCharacterNode(
                CharacterBuildSpec(name = sid, styleId = sid)
            )
            val mesh = requireNotNull(node.mesh)
            assertTrue("$sid should use vertex colors", mesh.useVertexColors)
            assertEquals("$sid colors array length mismatch", mesh.vertexCount * 3, mesh.colors.size)
            assertTrue("$sid should have rich sculpted geometry", mesh.triangleCount >= 400)
        }

        val redPanda = CharacterMeshBuilder.buildRiggedCharacterNode(
            CharacterBuildSpec(name = "Momo Red Panda", bodyType = CharacterArchetype.QUADRUPED, styleId = "char_red_panda")
        )
        val rpMesh = requireNotNull(redPanda.mesh)
        assertTrue(rpMesh.useVertexColors)
        assertTrue((redPanda.skeleton?.bones?.size ?: 0) == 6)
        assertTrue(rpMesh.triangleCount >= 300)

        for (arch in CharacterArchetype.entries) {
            val node = CharacterMeshBuilder.buildRiggedCharacterNode(
                CharacterBuildSpec(name = arch.name, bodyType = arch)
            )
            val mesh = requireNotNull(node.mesh)
            assertTrue("$arch should use vertex colors", mesh.useVertexColors)
            assertEquals("$arch colors array length mismatch", mesh.vertexCount * 3, mesh.colors.size)
            assertEquals("$arch normals array length mismatch", mesh.vertexCount * 3, mesh.normals.size)
            assertTrue("$arch should have triangles", mesh.triangleCount >= 100)
        }
    }

    @Test
    fun inverseKinematicsSolverHandlesAllFourLimbsAndPoseMirrorBlend() {
        val maya = CharacterMeshBuilder.buildRiggedCharacterNode(
            CharacterBuildSpec(name = "Maya", styleId = "char_streetwear_maya")
        )
        val skel = requireNotNull(maya.skeleton)
        val worldMat = Math3D.identity()
        val handles = IkSolver.computeIkHandleWorldPositions(skel, worldMat)
        assertEquals(4, handles.size)

        for (limb in IkLimb.entries) {
            val startTip = requireNotNull(handles[limb])
            val target = startTip + Vec3(0.18f, 0.25f, 0.22f)
            val res = IkSolver.solveLimbIk(skel, worldMat, emptyMap(), limb, target, poleAngleDeg = 10f)
            assertNotNull("IK solve failed for $limb", res)
            val r = res!!
            assertTrue(r.upperRotation.x.isFinite() && r.upperRotation.y.isFinite() && r.upperRotation.z.isFinite())
            assertTrue(r.lowerRotation.x.isFinite() && r.lowerRotation.y.isFinite() && r.lowerRotation.z.isFinite())
        }

        val pose = mapOf(
            "l_upper_arm" to Vec3(-40f, 10f, 25f),
            "r_upper_arm" to Vec3(5f, -5f, -10f)
        )
        val mirrored = IkSolver.mirrorPose(pose)
        assertEquals(-40f, mirrored["r_upper_arm"]!!.x, 0.01f)
        assertEquals(-25f, mirrored["r_upper_arm"]!!.z, 0.01f)

        val blended = IkSolver.blendPoses(emptyMap(), pose, 0.5f)
        assertEquals(-20f, blended["l_upper_arm"]!!.x, 0.01f)

        val roundtrip = SceneSerializer.deserializePoseMap(SceneSerializer.serializePoseMap(pose))
        assertEquals(-40f, roundtrip["l_upper_arm"]!!.x, 0.01f)
    }

    @Test
    fun multiFormatBlenderExportAndImportRoundTripAllFormats() {
        val hero = CharacterMeshBuilder.buildRiggedCharacterNode(
            CharacterBuildSpec(name = "Maya Streetwear", styleId = "char_streetwear_maya")
        )
        val scene = SceneDocument(projectName = "ExportTest", nodes = listOf(hero))

        // 1. OBJ round-trip
        val objStr = GlbExporter.serializeSceneToObjString(scene)
        assertTrue(objStr.contains("v "))
        val objReport = ModelImporter.importFromBytes(null, "test_maya.obj", objStr.toByteArray())
        assertTrue("OBJ import failed", objReport.supported && objReport.triangleCount > 100)

        // 2. FBX ASCII round-trip
        val fbxStr = GlbExporter.serializeSceneToFbxString(scene)
        assertTrue(fbxStr.contains("FBXHeaderExtension"))
        val fbxReport = ModelImporter.importFromBytes(null, "test_maya.fbx", fbxStr.toByteArray())
        assertTrue("FBX ASCII import failed", fbxReport.supported && fbxReport.triangleCount > 100)

        // 3. PLY round-trip
        val plyStr = GlbExporter.serializeSceneToPlyString(scene)
        assertTrue(plyStr.startsWith("ply"))
        val plyReport = ModelImporter.importFromBytes(null, "test_maya.ply", plyStr.toByteArray())
        assertTrue("PLY import failed", plyReport.supported && plyReport.triangleCount > 100)

        // 4. DAE (Collada) round-trip
        val daeStr = GlbExporter.serializeSceneToDaeString(scene)
        assertTrue(daeStr.contains("<COLLADA"))
        val daeReport = ModelImporter.importFromBytes(null, "test_maya.dae", daeStr.toByteArray())
        assertTrue("DAE import failed", daeReport.supported && daeReport.triangleCount > 100)

        // 5. STL Binary round-trip
        val stlBytes = GlbExporter.serializeSceneToStlBytes(scene)
        assertTrue(stlBytes.size > 84)
        val stlReport = ModelImporter.importFromBytes(null, "test_maya.stl", stlBytes)
        assertTrue("STL import failed", stlReport.supported && stlReport.triangleCount > 100)

        // 6. ZIP bundle (.fbm.zip) containing OBJ + MTL
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("character.obj"))
            zos.write(objStr.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("character.mtl"))
            zos.write("newmtl skin\nKd 0.90 0.72 0.58\n".toByteArray())
            zos.closeEntry()
        }
        val zipReport = ModelImporter.importFromBytes(null, "Woman_01.fbm.zip", baos.toByteArray())
        assertTrue("ZIP bundle import failed", zipReport.supported && zipReport.triangleCount > 100)
    }

    @Test
    fun all3DEnvironmentSetsAndProEasingCurvesEvaluateCleanly() {
        for (backdrop in BackdropPreset.entries) {
            val envNode = Environment3DFactory.buildEnvironmentSetNode(backdrop)
            assertNotNull("Expected 3D set for $backdrop", envNode)
            val mesh = requireNotNull(envNode?.mesh)
            assertTrue("Expected triangles for $backdrop", mesh.triangleCount >= 40)
            assertTrue(Environment3DFactory.buildLightsForBackdrop(backdrop).isNotEmpty())
        }

        val cube = SceneNode(id = "c1", name = "Cube", nodeType = NodeType.MESH, mesh = PrimitiveMeshFactory.createCube())
        for (curve in InterpolationType.entries) {
            val kfs = listOf(
                Keyframe(targetNodeId = "c1", frame = 0, property = KeyframeProperty.POSITION, vec3Value = Vec3(0f, 0f, 0f), interpolation = curve),
                Keyframe(targetNodeId = "c1", frame = 20, property = KeyframeProperty.POSITION, vec3Value = Vec3(4f, 2f, 0f), interpolation = curve)
            )
            val doc = SceneDocument(nodes = listOf(cube), timeline = TimelineState(keyframes = kfs))
            val mid = AnimationEvaluator.evaluate(doc, 10f).nodeStates["c1"]!!.transform.position
            assertTrue("Curve $curve produced non-finite value", mid.x.isFinite() && mid.y.isFinite())
        }
    }

    @Test
    fun animationEvaluatorHandlesCyclicParentHierarchySafely() {
        val a = SceneNode(id = "node_a", name = "A", nodeType = NodeType.MESH, parentId = "node_b")
        val b = SceneNode(id = "node_b", name = "B", nodeType = NodeType.MESH, parentId = "node_a")
        val doc = SceneDocument(nodes = listOf(a, b))
        val eval = AnimationEvaluator.evaluate(doc, 0f)
        assertEquals(2, eval.nodeStates.size)
        assertTrue(eval.nodeStates["node_a"]!!.worldMatrix.all { it.isFinite() })
        assertTrue(eval.nodeStates["node_b"]!!.worldMatrix.all { it.isFinite() })
    }
}
