package com.example.engine.animation

import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.KeyframeProperty
import com.example.engine.model.Math3D
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.Transform3D
import com.example.engine.model.Vec3

data class EvaluatedNodeState(
    val nodeId: String,
    val visible: Boolean,
    val transform: Transform3D,
    val worldMatrix: FloatArray,
    val boneRotations: Map<String, Vec3>,
    val cameraFov: Float
)

data class EvaluatedScene(
    val frame: Float,
    val nodeStates: Map<String, EvaluatedNodeState>
)

object AnimationEvaluator {

    fun evaluate(scene: SceneDocument, frame: Float = scene.timeline.currentFrame): EvaluatedScene {
        val timeline = scene.timeline
        val nodeMap = scene.nodes.associateBy { it.id }
        val localTransforms = HashMap<String, Transform3D>()
        val visibilities = HashMap<String, Boolean>()
        val bonePoseMaps = HashMap<String, Map<String, Vec3>>()
        val fovs = HashMap<String, Float>()

        val keyframesByNode = timeline.keyframes.groupBy { it.targetNodeId }
        val clipsByNode = timeline.clips.filter { it.enabled }.groupBy { it.targetNodeId }

        for (node in scene.nodes) {
            val layerHidden = timeline.hiddenTrackNodeIds.contains(node.id)
            val nodeKfs = keyframesByNode[node.id].orEmpty()

            var pos = interpolateVec3(nodeKfs, KeyframeProperty.POSITION, null, frame, node.transform.position)
            val rot = interpolateVec3(nodeKfs, KeyframeProperty.ROTATION, null, frame, node.transform.rotation)
            val scl = interpolateVec3(nodeKfs, KeyframeProperty.SCALE, null, frame, node.transform.scale)
            val kfVisible = interpolateBool(nodeKfs, frame, node.visible)
            val fov = interpolateFloat(nodeKfs, frame, node.camera?.fov ?: 45f)

            val boneRotations = LinkedHashMap<String, Vec3>()
            node.skeleton?.bones?.forEach { bone ->
                boneRotations[bone.id] = bone.poseRotation
            }

            // Apply active skeletal animation clips on this node
            val activeClips = clipsByNode[node.id].orEmpty()
            for (clip in activeClips) {
                val endFrame = clip.startFrame + clip.durationFrames
                if (frame >= clip.startFrame && (clip.loop || frame <= endFrame)) {
                    val localF = (frame - clip.startFrame) * clip.speed
                    val cycleLen = clip.durationFrames.coerceAtLeast(1).toFloat()
                    val phase = if (clip.loop) {
                        ((localF % cycleLen) + cycleLen) % cycleLen / cycleLen
                    } else {
                        (localF / cycleLen).coerceIn(0f, 1f)
                    }
                    val (hipDelta, presetBones) = AnimationPresets.evaluatePreset(clip.presetId, phase)
                    pos += hipDelta
                    presetBones.forEach { (bId, bRot) ->
                        val existing = boneRotations[bId] ?: Vec3.ZERO
                        boneRotations[bId] = existing + bRot
                    }
                }
            }

            // Override with explicit per-bone keyframes if authored
            node.skeleton?.bones?.forEach { bone ->
                val boneKfs = nodeKfs.filter { it.property == KeyframeProperty.BONE_ROTATION && it.targetBoneId == bone.id }
                if (boneKfs.isNotEmpty()) {
                    boneRotations[bone.id] = interpolateVec3(
                        boneKfs,
                        KeyframeProperty.BONE_ROTATION,
                        bone.id,
                        frame,
                        boneRotations[bone.id] ?: bone.poseRotation
                    )
                }
            }

            localTransforms[node.id] = Transform3D(pos, rot, scl)
            visibilities[node.id] = !layerHidden && kfVisible
            bonePoseMaps[node.id] = boneRotations
            fovs[node.id] = fov
        }

        // Compute world matrices respecting parent-child hierarchy (with cycle guard)
        val worldCache = HashMap<String, FloatArray>()
        val visiting = HashSet<String>()
        fun worldMatrixOf(node: SceneNode): FloatArray {
            worldCache[node.id]?.let { return it }
            val local = (localTransforms[node.id] ?: node.transform).toMatrix()
            if (!visiting.add(node.id)) return local
            val parent = node.parentId?.let { nodeMap[it] }
            val world = if (parent != null && parent.id != node.id) {
                Math3D.multiply(worldMatrixOf(parent), local)
            } else {
                local
            }
            visiting.remove(node.id)
            worldCache[node.id] = world
            return world
        }

        val result = LinkedHashMap<String, EvaluatedNodeState>()
        for (node in scene.nodes) {
            result[node.id] = EvaluatedNodeState(
                nodeId = node.id,
                visible = visibilities[node.id] ?: node.visible,
                transform = localTransforms[node.id] ?: node.transform,
                worldMatrix = worldMatrixOf(node),
                boneRotations = bonePoseMaps[node.id].orEmpty(),
                cameraFov = fovs[node.id] ?: 45f
            )
        }
        return EvaluatedScene(frame, result)
    }

    private fun interpolateVec3(
        kfs: List<Keyframe>,
        prop: KeyframeProperty,
        boneId: String?,
        frame: Float,
        defaultVal: Vec3
    ): Vec3 {
        val list = kfs.filter { it.property == prop && it.targetBoneId == boneId }.sortedBy { it.frame }
        if (list.isEmpty()) return defaultVal
        if (frame <= list.first().frame) return list.first().vec3Value
        if (frame >= list.last().frame) return list.last().vec3Value

        for (i in 0 until list.size - 1) {
            val a = list[i]
            val b = list[i + 1]
            if (frame >= a.frame && frame <= b.frame) {
                val span = (b.frame - a.frame).coerceAtLeast(1).toFloat()
                val rawT = ((frame - a.frame) / span).coerceIn(0f, 1f)
                val eased = ease(rawT, a.interpolation)
                return Vec3.lerp(a.vec3Value, b.vec3Value, eased)
            }
        }
        return defaultVal
    }

    private fun interpolateFloat(kfs: List<Keyframe>, frame: Float, defaultVal: Float): Float {
        val list = kfs.filter { it.property == KeyframeProperty.CAMERA_FOV }.sortedBy { it.frame }
        if (list.isEmpty()) return defaultVal
        if (frame <= list.first().frame) return list.first().floatValue
        if (frame >= list.last().frame) return list.last().floatValue
        for (i in 0 until list.size - 1) {
            val a = list[i]; val b = list[i + 1]
            if (frame >= a.frame && frame <= b.frame) {
                val rawT = ((frame - a.frame) / (b.frame - a.frame).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                val eased = ease(rawT, a.interpolation)
                return a.floatValue + (b.floatValue - a.floatValue) * eased
            }
        }
        return defaultVal
    }

    private fun interpolateBool(kfs: List<Keyframe>, frame: Float, defaultVal: Boolean): Boolean {
        val list = kfs.filter { it.property == KeyframeProperty.VISIBILITY }.sortedBy { it.frame }
        if (list.isEmpty()) return defaultVal
        var current = defaultVal
        for (kf in list) {
            if (frame >= kf.frame) current = kf.boolValue else break
        }
        return current
    }

    private fun ease(t: Float, type: InterpolationType): Float = when (type) {
        InterpolationType.LINEAR -> t
        InterpolationType.EASE_IN_OUT -> t * t * (3f - 2f * t)
        InterpolationType.STEP -> if (t < 1f) 0f else 1f
    }
}
