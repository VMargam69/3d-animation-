package com.example.data

import android.util.Base64
import com.example.engine.model.AnimationClipTrack
import com.example.engine.model.BoneNode
import com.example.engine.model.CameraData
import com.example.engine.model.CameraPreset
import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.KeyframeProperty
import com.example.engine.model.LightData
import com.example.engine.model.LightType
import com.example.engine.model.MaterialData
import com.example.engine.model.MeshData
import com.example.engine.model.NodeType
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.SkeletonData
import com.example.engine.model.TimelineState
import com.example.engine.model.Transform3D
import com.example.engine.model.Vec3
import com.example.engine.model.ViewportCameraState
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SceneSerializer {

    fun serializeScene(doc: SceneDocument): String {
        val root = JSONObject().apply {
            put("projectId", doc.projectId)
            put("projectName", doc.projectName)
            put("ambientColor", doc.ambientColor)
            put("ambientIntensity", doc.ambientIntensity.toDouble())
            put("updatedAt", doc.updatedAt)
            put("viewportCamera", JSONObject().apply {
                put("target", vec3ToJson(doc.viewportCamera.target))
                put("yawDeg", doc.viewportCamera.yawDeg.toDouble())
                put("pitchDeg", doc.viewportCamera.pitchDeg.toDouble())
                put("distance", doc.viewportCamera.distance.toDouble())
                put("orthographic", doc.viewportCamera.orthographic)
                put("preset", doc.viewportCamera.preset.name)
            })
            put("nodes", JSONArray().apply {
                doc.nodes.forEach { put(serializeNode(it)) }
            })
            put("timeline", JSONObject().apply {
                put("fps", doc.timeline.fps)
                put("totalFrames", doc.timeline.totalFrames)
                put("currentFrame", doc.timeline.currentFrame.toDouble())
                put("isLooping", doc.timeline.isLooping)
                put("autoKey", doc.timeline.autoKey)
                put("zoom", doc.timeline.zoom.toDouble())
                put("hiddenTrackNodeIds", JSONArray(doc.timeline.hiddenTrackNodeIds.toList()))
                put("lockedTrackNodeIds", JSONArray(doc.timeline.lockedTrackNodeIds.toList()))
                put("keyframes", JSONArray().apply {
                    doc.timeline.keyframes.forEach { kf ->
                        put(JSONObject().apply {
                            put("id", kf.id)
                            put("targetNodeId", kf.targetNodeId)
                            if (kf.targetBoneId != null) put("targetBoneId", kf.targetBoneId)
                            put("frame", kf.frame)
                            put("property", kf.property.name)
                            put("vec3Value", vec3ToJson(kf.vec3Value))
                            put("floatValue", kf.floatValue.toDouble())
                            put("boolValue", kf.boolValue)
                            put("interpolation", kf.interpolation.name)
                        })
                    }
                })
                put("clips", JSONArray().apply {
                    doc.timeline.clips.forEach { clip ->
                        put(JSONObject().apply {
                            put("id", clip.id)
                            put("targetNodeId", clip.targetNodeId)
                            put("clipName", clip.clipName)
                            put("presetId", clip.presetId)
                            put("startFrame", clip.startFrame)
                            put("durationFrames", clip.durationFrames)
                            put("speed", clip.speed.toDouble())
                            put("loop", clip.loop)
                            put("enabled", clip.enabled)
                            put("colorHex", clip.colorHex)
                        })
                    }
                })
            })
        }
        return root.toString()
    }

    fun deserializeScene(json: String): SceneDocument {
        val root = JSONObject(json)
        val camObj = root.optJSONObject("viewportCamera")
        val cam = if (camObj != null) {
            ViewportCameraState(
                target = jsonToVec3(camObj.optJSONArray("target"), Vec3(0f, 1f, 0f)),
                yawDeg = camObj.optDouble("yawDeg", 32.0).toFloat(),
                pitchDeg = camObj.optDouble("pitchDeg", 20.0).toFloat(),
                distance = camObj.optDouble("distance", 6.2).toFloat(),
                orthographic = camObj.optBoolean("orthographic", false),
                preset = runCatching { CameraPreset.valueOf(camObj.optString("preset", "FREE")) }.getOrDefault(CameraPreset.FREE)
            )
        } else ViewportCameraState()

        val nodes = mutableListOf<SceneNode>()
        val nodesArr = root.optJSONArray("nodes")
        if (nodesArr != null) {
            for (i in 0 until nodesArr.length()) {
                nodes += deserializeNode(nodesArr.getJSONObject(i))
            }
        }

        val tlObj = root.optJSONObject("timeline")
        val timeline = if (tlObj != null) {
            val hiddenSet = mutableSetOf<String>()
            tlObj.optJSONArray("hiddenTrackNodeIds")?.let { arr ->
                for (i in 0 until arr.length()) hiddenSet.add(arr.getString(i))
            }
            val lockedSet = mutableSetOf<String>()
            tlObj.optJSONArray("lockedTrackNodeIds")?.let { arr ->
                for (i in 0 until arr.length()) lockedSet.add(arr.getString(i))
            }
            val kfs = mutableListOf<Keyframe>()
            tlObj.optJSONArray("keyframes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    kfs += Keyframe(
                        id = o.optString("id"),
                        targetNodeId = o.optString("targetNodeId"),
                        targetBoneId = if (o.has("targetBoneId")) o.optString("targetBoneId") else null,
                        frame = o.optInt("frame", 0),
                        property = runCatching { KeyframeProperty.valueOf(o.optString("property")) }.getOrDefault(KeyframeProperty.POSITION),
                        vec3Value = jsonToVec3(o.optJSONArray("vec3Value"), Vec3.ZERO),
                        floatValue = o.optDouble("floatValue", 45.0).toFloat(),
                        boolValue = o.optBoolean("boolValue", true),
                        interpolation = runCatching { InterpolationType.valueOf(o.optString("interpolation")) }.getOrDefault(InterpolationType.EASE_IN_OUT)
                    )
                }
            }
            val clips = mutableListOf<AnimationClipTrack>()
            tlObj.optJSONArray("clips")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    clips += AnimationClipTrack(
                        id = o.optString("id"),
                        targetNodeId = o.optString("targetNodeId"),
                        clipName = o.optString("clipName", "Idle"),
                        presetId = o.optString("presetId", "idle"),
                        startFrame = o.optInt("startFrame", 0),
                        durationFrames = o.optInt("durationFrames", 60),
                        speed = o.optDouble("speed", 1.0).toFloat(),
                        loop = o.optBoolean("loop", true),
                        enabled = o.optBoolean("enabled", true),
                        colorHex = o.optLong("colorHex", 0xFF6366F1)
                    )
                }
            }
            TimelineState(
                fps = tlObj.optInt("fps", 30),
                totalFrames = tlObj.optInt("totalFrames", 150),
                currentFrame = tlObj.optDouble("currentFrame", 0.0).toFloat(),
                isPlaying = false,
                isLooping = tlObj.optBoolean("isLooping", true),
                autoKey = tlObj.optBoolean("autoKey", false),
                zoom = tlObj.optDouble("zoom", 1.0).toFloat(),
                keyframes = kfs,
                clips = clips,
                hiddenTrackNodeIds = hiddenSet,
                lockedTrackNodeIds = lockedSet
            )
        } else TimelineState()

        return SceneDocument(
            projectId = root.optString("projectId"),
            projectName = root.optString("projectName", "Untitled Scene"),
            nodes = nodes,
            timeline = timeline,
            viewportCamera = cam,
            ambientColor = root.optLong("ambientColor", 0xFF1A2035),
            ambientIntensity = root.optDouble("ambientIntensity", 0.35).toFloat(),
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun serializeNode(node: SceneNode): JSONObject = JSONObject().apply {
        put("id", node.id)
        if (node.parentId != null) put("parentId", node.parentId)
        put("name", node.name)
        put("nodeType", node.nodeType.name)
        put("primitiveKind", node.primitiveKind.name)
        put("visible", node.visible)
        put("locked", node.locked)
        put("categoryTag", node.categoryTag)
        if (node.sourceFormat != null) put("sourceFormat", node.sourceFormat)
        put("transform", JSONObject().apply {
            put("position", vec3ToJson(node.transform.position))
            put("rotation", vec3ToJson(node.transform.rotation))
            put("scale", vec3ToJson(node.transform.scale))
        })
        put("material", JSONObject().apply {
            put("name", node.material.name)
            put("baseColor", node.material.baseColor)
            put("roughness", node.material.roughness.toDouble())
            put("metallic", node.material.metallic.toDouble())
            put("opacity", node.material.opacity.toDouble())
            put("emissionColor", node.material.emissionColor)
            put("emissionStrength", node.material.emissionStrength.toDouble())
            if (node.material.texturePath != null) put("texturePath", node.material.texturePath)
            put("uvScale", node.material.uvScale.toDouble())
        })
        node.mesh?.let { m ->
            put("mesh", JSONObject().apply {
                put("v", encodeFloats(m.vertices))
                put("n", encodeFloats(m.normals))
                put("uv", encodeFloats(m.uvs))
                put("i", encodeInts(m.indices))
                put("ji", encodeInts(m.jointIndices))
                put("jw", encodeFloats(m.jointWeights))
                if (m.useVertexColors) {
                    put("c", encodeFloats(m.colors))
                    put("vc", true)
                }
            })
        }
        node.skeleton?.let { sk ->
            put("skeleton", JSONArray().apply {
                sk.bones.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("name", b.name)
                        if (b.parentId != null) put("parentId", b.parentId)
                        put("localOffset", vec3ToJson(b.localOffset))
                        put("length", b.length.toDouble())
                        put("poseRotation", vec3ToJson(b.poseRotation))
                    })
                }
            })
        }
        node.light?.let { l ->
            put("light", JSONObject().apply {
                put("lightType", l.lightType.name)
                put("color", l.color)
                put("intensity", l.intensity.toDouble())
                put("range", l.range.toDouble())
                put("castShadows", l.castShadows)
            })
        }
        node.camera?.let { c ->
            put("camera", JSONObject().apply {
                put("fov", c.fov.toDouble())
                put("nearClip", c.nearClip.toDouble())
                put("farClip", c.farClip.toDouble())
                put("isActive", c.isActive)
            })
        }
    }

    fun deserializeNode(o: JSONObject): SceneNode {
        val trObj = o.optJSONObject("transform")
        val transform = if (trObj != null) {
            Transform3D(
                position = jsonToVec3(trObj.optJSONArray("position"), Vec3.ZERO),
                rotation = jsonToVec3(trObj.optJSONArray("rotation"), Vec3.ZERO),
                scale = jsonToVec3(trObj.optJSONArray("scale"), Vec3.ONE)
            )
        } else Transform3D()

        val matObj = o.optJSONObject("material")
        val mat = if (matObj != null) {
            MaterialData(
                name = matObj.optString("name", "Studio Material"),
                baseColor = matObj.optLong("baseColor", 0xFF6366F1),
                roughness = matObj.optDouble("roughness", 0.45).toFloat(),
                metallic = matObj.optDouble("metallic", 0.15).toFloat(),
                opacity = matObj.optDouble("opacity", 1.0).toFloat(),
                emissionColor = matObj.optLong("emissionColor", 0xFF000000),
                emissionStrength = matObj.optDouble("emissionStrength", 0.0).toFloat(),
                texturePath = if (matObj.has("texturePath")) matObj.optString("texturePath") else null,
                uvScale = matObj.optDouble("uvScale", 1.0).toFloat()
            )
        } else MaterialData()

        val meshObj = o.optJSONObject("mesh")
        val mesh = if (meshObj != null) {
            val verts = decodeFloats(meshObj.optString("v"))
            val hasVc = meshObj.optBoolean("vc", false)
            val cols = if (hasVc && meshObj.has("c")) decodeFloats(meshObj.optString("c")) else FloatArray(verts.size) { 1f }
            MeshData(
                vertices = verts,
                normals = decodeFloats(meshObj.optString("n")),
                uvs = decodeFloats(meshObj.optString("uv")),
                indices = decodeInts(meshObj.optString("i")),
                jointIndices = decodeInts(meshObj.optString("ji")),
                jointWeights = decodeFloats(meshObj.optString("jw")),
                colors = if (cols.size == verts.size) cols else FloatArray(verts.size) { 1f },
                useVertexColors = hasVc && cols.size == verts.size
            )
        } else null

        val skelArr = o.optJSONArray("skeleton")
        val skeleton = if (skelArr != null) {
            val bones = mutableListOf<BoneNode>()
            for (i in 0 until skelArr.length()) {
                val bo = skelArr.getJSONObject(i)
                bones += BoneNode(
                    id = bo.optString("id"),
                    name = bo.optString("name"),
                    parentId = if (bo.has("parentId")) bo.optString("parentId") else null,
                    localOffset = jsonToVec3(bo.optJSONArray("localOffset"), Vec3.ZERO),
                    length = bo.optDouble("length", 0.4).toFloat(),
                    poseRotation = jsonToVec3(bo.optJSONArray("poseRotation"), Vec3.ZERO)
                )
            }
            SkeletonData(bones)
        } else null

        val lightObj = o.optJSONObject("light")
        val light = if (lightObj != null) {
            LightData(
                lightType = runCatching { LightType.valueOf(lightObj.optString("lightType")) }.getOrDefault(LightType.POINT),
                color = lightObj.optLong("color", 0xFFFFFFFF),
                intensity = lightObj.optDouble("intensity", 1.4).toFloat(),
                range = lightObj.optDouble("range", 16.0).toFloat(),
                castShadows = lightObj.optBoolean("castShadows", true)
            )
        } else null

        val camObj = o.optJSONObject("camera")
        val camera = if (camObj != null) {
            CameraData(
                fov = camObj.optDouble("fov", 45.0).toFloat(),
                nearClip = camObj.optDouble("nearClip", 0.1).toFloat(),
                farClip = camObj.optDouble("farClip", 120.0).toFloat(),
                isActive = camObj.optBoolean("isActive", true)
            )
        } else null

        return SceneNode(
            id = o.optString("id"),
            parentId = if (o.has("parentId")) o.optString("parentId") else null,
            name = o.optString("name", "Object"),
            nodeType = runCatching { NodeType.valueOf(o.optString("nodeType")) }.getOrDefault(NodeType.MESH),
            primitiveKind = runCatching { PrimitiveKind.valueOf(o.optString("primitiveKind")) }.getOrDefault(PrimitiveKind.CUSTOM),
            transform = transform,
            visible = o.optBoolean("visible", true),
            locked = o.optBoolean("locked", false),
            mesh = mesh,
            material = mat,
            skeleton = skeleton,
            light = light,
            camera = camera,
            categoryTag = o.optString("categoryTag", "CUSTOM"),
            sourceFormat = if (o.has("sourceFormat")) o.optString("sourceFormat") else null
        )
    }

    private fun vec3ToJson(v: Vec3) = JSONArray(listOf(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()))

    private fun jsonToVec3(arr: JSONArray?, fallback: Vec3): Vec3 {
        if (arr == null || arr.length() < 3) return fallback
        return Vec3(arr.optDouble(0, 0.0).toFloat(), arr.optDouble(1, 0.0).toFloat(), arr.optDouble(2, 0.0).toFloat())
    }

    private fun encodeFloats(arr: FloatArray): String {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putFloat(it) }
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP)
    }

    private fun decodeFloats(b64: String): FloatArray {
        if (b64.isEmpty()) return FloatArray(0)
        val raw = Base64.decode(b64, Base64.DEFAULT)
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(raw.size / 4) { bb.float }
    }

    private fun encodeInts(arr: IntArray): String {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putInt(it) }
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP)
    }

    private fun decodeInts(b64: String): IntArray {
        if (b64.isEmpty()) return IntArray(0)
        val raw = Base64.decode(b64, Base64.DEFAULT)
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(raw.size / 4) { bb.int }
    }
}
