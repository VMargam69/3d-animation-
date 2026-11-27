package com.example

import com.example.engine.animation.AnimationPresets
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.MeshEditingOps
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.PrimitiveMeshFactory
import com.example.engine.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val hero = CharacterMeshBuilder.buildRiggedCharacterNode(CharacterBuildSpec(name = "Test Rig"))
        assertTrue((hero.skeleton?.bones?.size ?: 0) >= 11)
        assertTrue((hero.mesh?.vertexCount ?: 0) > 100)

        for (preset in AnimationPresets.allPresets) {
            val (hipDelta, bonePose) = AnimationPresets.evaluatePreset(preset.id, 0.25f)
            assertTrue("Preset ${preset.id} should drive bones or root motion", bonePose.isNotEmpty() || hipDelta != Vec3.ZERO)
        }
    }

    @Test
    fun allCharacterArchetypesAndBuiltInStylesBuildMultiColorMeshes() {
        val styleIds = listOf(
            "char_desi_hero", "char_desi_police", "char_western_ranger",
            "char_indo_dancer", "char_village_farmer", "char_school_student",
            "char_doctor", "char_blender_mannequin"
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
        for (arch in com.example.engine.model.CharacterArchetype.entries) {
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
}
