package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SceneSerializer
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.KeyframeProperty
import com.example.engine.model.MeshEditingOps
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.PrimitiveMeshFactory
import com.example.engine.model.SceneDocument
import com.example.engine.model.TimelineState
import com.example.engine.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DraftRig 3D", appName)
    }

    @Test
    fun `primitive mesh editing extrude inset and subdivide mutate real 3d geometry`() {
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
    }

    @Test
    fun `rigged character evaluates keyframes and serializes cleanly`() {
        val hero = CharacterMeshBuilder.buildRiggedCharacterNode(CharacterBuildSpec(name = "Test Rig"))
        assertTrue((hero.skeleton?.bones?.size ?: 0) >= 11)
        assertTrue((hero.mesh?.vertexCount ?: 0) > 100)

        val kfs = listOf(
            Keyframe(targetNodeId = hero.id, frame = 0, property = KeyframeProperty.POSITION, vec3Value = Vec3(0f, 0f, 0f), interpolation = InterpolationType.LINEAR),
            Keyframe(targetNodeId = hero.id, frame = 10, property = KeyframeProperty.POSITION, vec3Value = Vec3(10f, 0f, 0f), interpolation = InterpolationType.LINEAR)
        )
        val doc = SceneDocument(
            projectName = "Test Scene",
            nodes = listOf(hero),
            timeline = TimelineState(keyframes = kfs)
        )
        val midEval = AnimationEvaluator.evaluate(doc, 5f)
        assertEquals(5.0f, midEval.nodeStates[hero.id]!!.transform.position.x, 0.05f)

        val serialized = SceneSerializer.serializeScene(doc)
        val restored = SceneSerializer.deserializeScene(serialized)
        assertEquals("Test Scene", restored.projectName)
        assertEquals(1, restored.nodes.size)
        assertEquals(hero.mesh!!.vertexCount, restored.nodes.first().mesh!!.vertexCount)
        assertEquals(hero.skeleton!!.bones.size, restored.nodes.first().skeleton!!.bones.size)
    }
}
