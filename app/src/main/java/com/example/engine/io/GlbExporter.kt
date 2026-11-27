package com.example.engine.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.model.ExportFormat
import com.example.engine.model.Math3D
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Multi-format Offline 3D Scene Exporter supporting all major Blender-compatible formats:
 * - GLB (glTF 2.0 Binary with PBR materials, UVs, normals, vertex colors)
 * - OBJ (Wavefront OBJ with groups, normals, UVs)
 * - FBX (Clean ASCII FBX 7.4 ready for Blender/Maya/Unity/Unreal import)
 * - STL (Binary little-endian STL for 3D printing & CAD)
 * - PLY (Stanford ASCII PLY with per-vertex RGB colors & normals)
 * - DAE (Collada 1.4.1 XML)
 */
object GlbExporter {

    private data class BakedNodeMesh(
        val node: SceneNode,
        val worldVerts: FloatArray,
        val normals: FloatArray,
        val uvs: FloatArray,
        val colors: FloatArray,
        val indices: IntArray,
        val min: Vec3,
        val max: Vec3
    )

    fun exportScene(context: Context, scene: SceneDocument, format: ExportFormat): Result<Pair<Uri, String>> = runCatching {
        val baked = bakeVisibleScene(scene)
        require(baked.isNotEmpty()) { "No visible 3D mesh objects in scene to export." }

        val payload: ByteArray = when (format) {
            ExportFormat.GLB -> buildGlbBytes(baked)
            ExportFormat.OBJ -> buildObjBytes(baked)
            ExportFormat.FBX -> buildFbxAsciiBytes(scene.projectName, baked)
            ExportFormat.STL -> buildStlBinaryBytes(baked)
            ExportFormat.PLY -> buildPlyAsciiBytes(baked)
            ExportFormat.DAE -> buildColladaDaeBytes(scene.projectName, baked)
            ExportFormat.MP4 -> error("Use VideoExporter for MP4 video export")
        }

        val safeTitle = scene.projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "draftrig_scene" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.${format.extension}"
        val outUri = saveToDownloads(context, fileName, format.mimeType, payload)
        outUri to fileName
    }

    fun exportSceneToGlb(context: Context, scene: SceneDocument): Result<Pair<Uri, String>> =
        exportScene(context, scene, ExportFormat.GLB)

    // Pure-Kotlin serializer entry points (also unit-testable on JVM!)
    fun serializeSceneToObjString(scene: SceneDocument): String = String(buildObjBytes(bakeVisibleScene(scene)), Charsets.UTF_8)
    fun serializeSceneToFbxString(scene: SceneDocument): String = String(buildFbxAsciiBytes(scene.projectName, bakeVisibleScene(scene)), Charsets.UTF_8)
    fun serializeSceneToPlyString(scene: SceneDocument): String = String(buildPlyAsciiBytes(bakeVisibleScene(scene)), Charsets.UTF_8)
    fun serializeSceneToDaeString(scene: SceneDocument): String = String(buildColladaDaeBytes(scene.projectName, bakeVisibleScene(scene)), Charsets.UTF_8)
    fun serializeSceneToStlBytes(scene: SceneDocument): ByteArray = buildStlBinaryBytes(bakeVisibleScene(scene))

    private fun bakeVisibleScene(scene: SceneDocument): List<BakedNodeMesh> {
        val evaluated = AnimationEvaluator.evaluate(scene, scene.timeline.currentFrame)
        val out = ArrayList<BakedNodeMesh>()
        for (node in scene.nodes) {
            val state = evaluated.nodeStates[node.id] ?: continue
            if (!state.visible) continue
            val mesh = node.mesh ?: continue
            if (mesh.vertexCount == 0 || mesh.indices.isEmpty()) continue

            val skinPalette = node.skeleton?.takeIf { it.bones.isNotEmpty() }?.computeSkinningPalette(state.boneRotations)
            val matSlice = FloatArray(16)
            val worldVerts = FloatArray(mesh.vertices.size)
            var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY

            for (i in 0 until mesh.vertexCount) {
                var localP = Vec3(mesh.vertices[i * 3], mesh.vertices[i * 3 + 1], mesh.vertices[i * 3 + 2])
                if (skinPalette != null) {
                    var sx = 0f; var sy = 0f; var sz = 0f
                    var totalW = 0f
                    for (k in 0..3) {
                        val w = mesh.jointWeights.getOrElse(i * 4 + k) { 0f }
                        if (w <= 0.0001f) continue
                        val jIdx = mesh.jointIndices.getOrElse(i * 4 + k) { 0 }.coerceIn(0, 31)
                        System.arraycopy(skinPalette, jIdx * 16, matSlice, 0, 16)
                        val tp = Math3D.transformPoint(matSlice, localP)
                        sx += tp.x * w; sy += tp.y * w; sz += tp.z * w
                        totalW += w
                    }
                    if (totalW > 0.001f) localP = Vec3(sx / totalW, sy / totalW, sz / totalW)
                }
                val p = Math3D.transformPoint(state.worldMatrix, localP)
                worldVerts[i * 3] = p.x; worldVerts[i * 3 + 1] = p.y; worldVerts[i * 3 + 2] = p.z
                if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
                if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
            }

            val rgb = Math3D.colorHexToRgbFloats(node.material.baseColor)
            val cols = if (mesh.useVertexColors && mesh.colors.size == mesh.vertices.size) {
                mesh.colors
            } else {
                FloatArray(mesh.vertices.size) { idx -> rgb[idx % 3] }
            }

            out += BakedNodeMesh(
                node = node,
                worldVerts = worldVerts,
                normals = mesh.normals,
                uvs = mesh.uvs,
                colors = cols,
                indices = mesh.indices,
                min = Vec3(minX, minY, minZ),
                max = Vec3(maxX, maxY, maxZ)
            )
        }
        return out
    }

    private fun buildGlbBytes(baked: List<BakedNodeMesh>): ByteArray {
        val binStream = ByteArrayOutputStream()
        val bufferViews = JSONArray()
        val accessors = JSONArray()
        val meshes = JSONArray()
        val materials = JSONArray()
        val nodes = JSONArray()
        val sceneNodeIndices = JSONArray()

        fun appendToBin(raw: ByteArray, target: Int?): Int {
            while (binStream.size() % 4 != 0) binStream.write(0)
            val offset = binStream.size()
            binStream.write(raw)
            val bv = JSONObject().apply {
                put("buffer", 0)
                put("byteOffset", offset)
                put("byteLength", raw.size)
                if (target != null) put("target", target)
            }
            val idx = bufferViews.length()
            bufferViews.put(bv)
            return idx
        }

        for (item in baked) {
            val vc = item.worldVerts.size / 3
            val bvPos = appendToBin(floatsToLeBytes(item.worldVerts), 34962)
            val bvNorm = appendToBin(floatsToLeBytes(item.normals), 34962)
            val bvUv = appendToBin(floatsToLeBytes(item.uvs), 34962)
            val bvCol = appendToBin(floatsToLeBytes(item.colors), 34962)
            val bvIdx = appendToBin(intsToLeBytes(item.indices), 34963)

            val accPos = accessors.length().also {
                accessors.put(JSONObject().apply {
                    put("bufferView", bvPos); put("componentType", 5126); put("count", vc); put("type", "VEC3")
                    put("min", JSONArray(listOf(item.min.x.toDouble(), item.min.y.toDouble(), item.min.z.toDouble())))
                    put("max", JSONArray(listOf(item.max.x.toDouble(), item.max.y.toDouble(), item.max.z.toDouble())))
                })
            }
            val accNorm = accessors.length().also {
                accessors.put(JSONObject().apply { put("bufferView", bvNorm); put("componentType", 5126); put("count", vc); put("type", "VEC3") })
            }
            val accUv = accessors.length().also {
                accessors.put(JSONObject().apply { put("bufferView", bvUv); put("componentType", 5126); put("count", vc); put("type", "VEC2") })
            }
            val accCol = accessors.length().also {
                accessors.put(JSONObject().apply { put("bufferView", bvCol); put("componentType", 5126); put("count", vc); put("type", "VEC3") })
            }
            val accIdx = accessors.length().also {
                accessors.put(JSONObject().apply { put("bufferView", bvIdx); put("componentType", 5125); put("count", item.indices.size); put("type", "SCALAR") })
            }

            val rgb = Math3D.colorHexToRgbFloats(item.node.material.baseColor)
            val matIdx = materials.length().also {
                materials.put(JSONObject().apply {
                    put("name", item.node.material.name)
                    put("pbrMetallicRoughness", JSONObject().apply {
                        put("baseColorFactor", JSONArray(listOf(rgb[0].toDouble(), rgb[1].toDouble(), rgb[2].toDouble(), item.node.material.opacity.toDouble())))
                        put("metallicFactor", item.node.material.metallic.toDouble())
                        put("roughnessFactor", item.node.material.roughness.toDouble())
                    })
                })
            }

            val meshIdx = meshes.length().also {
                meshes.put(JSONObject().apply {
                    put("name", item.node.name)
                    put("primitives", JSONArray().put(JSONObject().apply {
                        put("attributes", JSONObject().apply {
                            put("POSITION", accPos); put("NORMAL", accNorm); put("TEXCOORD_0", accUv); put("COLOR_0", accCol)
                        })
                        put("indices", accIdx)
                        put("material", matIdx)
                    }))
                })
            }

            val nodeIdx = nodes.length().also {
                nodes.put(JSONObject().apply { put("name", item.node.name); put("mesh", meshIdx) })
            }
            sceneNodeIndices.put(nodeIdx)
        }

        while (binStream.size() % 4 != 0) binStream.write(0)
        val binBytes = binStream.toByteArray()

        val gltfJson = JSONObject().apply {
            put("asset", JSONObject().apply { put("version", "2.0"); put("generator", "DraftRig 3D Pro Studio") })
            put("scene", 0)
            put("scenes", JSONArray().put(JSONObject().put("nodes", sceneNodeIndices)))
            put("nodes", nodes); put("meshes", meshes); put("materials", materials)
            put("accessors", accessors); put("bufferViews", bufferViews)
            put("buffers", JSONArray().put(JSONObject().put("byteLength", binBytes.size)))
        }.toString()

        var jsonBytes = gltfJson.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (jsonBytes.size % 4)) % 4
        if (jsonPad > 0) jsonBytes += ByteArray(jsonPad) { 0x20 }

        val totalLen = 12 + 8 + jsonBytes.size + 8 + binBytes.size
        val glbBuf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        glbBuf.putInt(0x46546C67); glbBuf.putInt(2); glbBuf.putInt(totalLen)
        glbBuf.putInt(jsonBytes.size); glbBuf.putInt(0x4E4F534A); glbBuf.put(jsonBytes)
        glbBuf.putInt(binBytes.size); glbBuf.putInt(0x004E4942); glbBuf.put(binBytes)
        return glbBuf.array()
    }

    private fun buildObjBytes(baked: List<BakedNodeMesh>): ByteArray {
        val sb = StringBuilder(65536)
        sb.append("# Exported by DraftRig 3D Studio (Blender-compatible Wavefront OBJ)\n")
        var vOffset = 1
        for (item in baked) {
            val safeName = item.node.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            sb.append("o ").append(safeName).append('\n')
            val vc = item.worldVerts.size / 3
            for (i in 0 until vc) {
                sb.append(String.format(
                    Locale.US, "v %.5f %.5f %.5f %.4f %.4f %.4f\n",
                    item.worldVerts[i * 3], item.worldVerts[i * 3 + 1], item.worldVerts[i * 3 + 2],
                    item.colors[i * 3], item.colors[i * 3 + 1], item.colors[i * 3 + 2]
                ))
            }
            for (i in 0 until vc) {
                val u = item.uvs.getOrElse(i * 2) { 0f }
                val v = 1f - item.uvs.getOrElse(i * 2 + 1) { 0f }
                sb.append(String.format(Locale.US, "vt %.5f %.5f\n", u, v))
            }
            for (i in 0 until vc) {
                sb.append(String.format(
                    Locale.US, "vn %.5f %.5f %.5f\n",
                    item.normals.getOrElse(i * 3) { 0f },
                    item.normals.getOrElse(i * 3 + 1) { 1f },
                    item.normals.getOrElse(i * 3 + 2) { 0f }
                ))
            }
            for (t in 0 until item.indices.size / 3) {
                val a = item.indices[t * 3] + vOffset
                val b = item.indices[t * 3 + 1] + vOffset
                val c = item.indices[t * 3 + 2] + vOffset
                sb.append("f $a/$a/$a $b/$b/$b $c/$c/$c\n")
            }
            vOffset += vc
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildFbxAsciiBytes(projectName: String, baked: List<BakedNodeMesh>): ByteArray {
        val sb = StringBuilder(65536)
        sb.append("; FBX 7.4.0 project file\n; Exported by DraftRig 3D Studio - Blender Compatible\n")
        sb.append("FBXHeaderExtension:  {\n\tFBXHeaderVersion: 1003\n\tFBXVersion: 7400\n\tCreator: \"DraftRig 3D Studio\"\n}\n\n")
        sb.append("Objects:  {\n")
        val connections = StringBuilder()

        baked.forEachIndexed { idx, item ->
            val geoId = 100000L + idx * 10L
            val modId = 200000L + idx * 10L
            val safeName = item.node.name.replace("\"", "'")
            sb.append("\tGeometry: $geoId, \"Geometry::$safeName\", \"Mesh\" {\n")
            sb.append("\t\tVertices: *${item.worldVerts.size} {\n\t\t\ta: ")
            item.worldVerts.forEachIndexed { vi, f ->
                if (vi > 0) sb.append(',')
                sb.append(String.format(Locale.US, "%.4f", f))
            }
            sb.append("\n\t\t}\n")
            sb.append("\t\tPolygonVertexIndex: *${item.indices.size} {\n\t\t\ta: ")
            for (i in item.indices.indices) {
                if (i > 0) sb.append(',')
                val raw = item.indices[i]
                // FBX ends each polygon with bitwise complement (-(idx+1))
                val out = if (i % 3 == 2) (raw xor -1) else raw
                sb.append(out)
            }
            sb.append("\n\t\t}\n")
            sb.append("\t\tGeometryVersion: 124\n")
            sb.append("\t}\n")
            sb.append("\tModel: $modId, \"Model::$safeName\", \"Mesh\" {\n\t\tVersion: 232\n\t\tShading: Y\n\t\tCulling: \"CullingOff\"\n\t}\n")
            connections.append("\tC: \"OO\",$geoId,$modId\n")
            connections.append("\tC: \"OO\",$modId,0\n")
        }
        sb.append("}\n\nConnections:  {\n").append(connections).append("}\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildStlBinaryBytes(baked: List<BakedNodeMesh>): ByteArray {
        val totalTris = baked.sumOf { it.indices.size / 3 }
        val bb = ByteBuffer.allocate(84 + totalTris * 50).order(ByteOrder.LITTLE_ENDIAN)
        val header = "DraftRig 3D Binary STL Export".toByteArray(Charsets.US_ASCII)
        bb.put(header)
        bb.position(80)
        bb.putInt(totalTris)

        for (item in baked) {
            for (t in 0 until item.indices.size / 3) {
                val i0 = item.indices[t * 3]
                val i1 = item.indices[t * 3 + 1]
                val i2 = item.indices[t * 3 + 2]
                val p0 = Vec3(item.worldVerts[i0 * 3], item.worldVerts[i0 * 3 + 1], item.worldVerts[i0 * 3 + 2])
                val p1 = Vec3(item.worldVerts[i1 * 3], item.worldVerts[i1 * 3 + 1], item.worldVerts[i1 * 3 + 2])
                val p2 = Vec3(item.worldVerts[i2 * 3], item.worldVerts[i2 * 3 + 1], item.worldVerts[i2 * 3 + 2])
                val n = (p1 - p0).cross(p2 - p0).normalized()
                bb.putFloat(n.x); bb.putFloat(n.y); bb.putFloat(n.z)
                bb.putFloat(p0.x); bb.putFloat(p0.y); bb.putFloat(p0.z)
                bb.putFloat(p1.x); bb.putFloat(p1.y); bb.putFloat(p1.z)
                bb.putFloat(p2.x); bb.putFloat(p2.y); bb.putFloat(p2.z)
                bb.putShort(0)
            }
        }
        return bb.array()
    }

    private fun buildPlyAsciiBytes(baked: List<BakedNodeMesh>): ByteArray {
        val totalVerts = baked.sumOf { it.worldVerts.size / 3 }
        val totalTris = baked.sumOf { it.indices.size / 3 }
        val sb = StringBuilder(65536)
        sb.append("ply\nformat ascii 1.0\ncomment Exported by DraftRig 3D Studio\n")
        sb.append("element vertex $totalVerts\n")
        sb.append("property float x\nproperty float y\nproperty float z\n")
        sb.append("property float nx\nproperty float ny\nproperty float nz\n")
        sb.append("property uchar red\nproperty uchar green\nproperty uchar blue\n")
        sb.append("element face $totalTris\nproperty list uchar int vertex_indices\nend_header\n")

        for (item in baked) {
            val vc = item.worldVerts.size / 3
            for (i in 0 until vc) {
                val r = (item.colors[i * 3] * 255f).toInt().coerceIn(0, 255)
                val g = (item.colors[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
                val b = (item.colors[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
                sb.append(String.format(
                    Locale.US, "%.5f %.5f %.5f %.4f %.4f %.4f %d %d %d\n",
                    item.worldVerts[i * 3], item.worldVerts[i * 3 + 1], item.worldVerts[i * 3 + 2],
                    item.normals.getOrElse(i * 3) { 0f },
                    item.normals.getOrElse(i * 3 + 1) { 1f },
                    item.normals.getOrElse(i * 3 + 2) { 0f },
                    r, g, b
                ))
            }
        }
        var vOff = 0
        for (item in baked) {
            for (t in 0 until item.indices.size / 3) {
                sb.append("3 ${item.indices[t * 3] + vOff} ${item.indices[t * 3 + 1] + vOff} ${item.indices[t * 3 + 2] + vOff}\n")
            }
            vOff += item.worldVerts.size / 3
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildColladaDaeBytes(projectName: String, baked: List<BakedNodeMesh>): ByteArray {
        val sb = StringBuilder(65536)
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<COLLADA xmlns=\"http://www.collada.org/2005/11/COLLADASchema\" version=\"1.4.1\">\n")
        sb.append("  <asset><contributor><authoring_tool>DraftRig 3D Studio</authoring_tool></contributor><up_axis>Y_UP</up_axis></asset>\n")
        sb.append("  <library_geometries>\n")
        baked.forEachIndexed { idx, item ->
            val gid = "geo_$idx"
            val vc = item.worldVerts.size / 3
            val tc = item.indices.size / 3
            sb.append("    <geometry id=\"$gid\" name=\"${item.node.name.replace("\"", "")}\">\n      <mesh>\n")
            sb.append("        <source id=\"${gid}_pos\"><float_array id=\"${gid}_pos_arr\" count=\"${item.worldVerts.size}\">")
            item.worldVerts.forEachIndexed { i, v -> if (i > 0) sb.append(' '); sb.append(String.format(Locale.US, "%.4f", v)) }
            sb.append("</float_array><technique_common><accessor source=\"#${gid}_pos_arr\" count=\"$vc\" stride=\"3\"><param name=\"X\" type=\"float\"/><param name=\"Y\" type=\"float\"/><param name=\"Z\" type=\"float\"/></accessor></technique_common></source>\n")
            sb.append("        <vertices id=\"${gid}_verts\"><input semantic=\"POSITION\" source=\"#${gid}_pos\"/></vertices>\n")
            sb.append("        <triangles count=\"$tc\"><input semantic=\"VERTEX\" source=\"#${gid}_verts\" offset=\"0\"/><p>")
            item.indices.forEachIndexed { i, v -> if (i > 0) sb.append(' '); sb.append(v) }
            sb.append("</p></triangles>\n      </mesh>\n    </geometry>\n")
        }
        sb.append("  </library_geometries>\n  <library_visual_scenes><visual_scene id=\"Scene\" name=\"${projectName.replace("\"", "")}\">\n")
        baked.forEachIndexed { idx, item ->
            sb.append("    <node id=\"node_$idx\" name=\"${item.node.name.replace("\"", "")}\"><instance_geometry url=\"#geo_$idx\"/></node>\n")
        }
        sb.append("  </visual_scene></library_visual_scenes>\n  <scene><instance_visual_scene url=\"#Scene\"/></scene>\n</COLLADA>\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun saveToDownloads(context: Context, fileName: String, mime: String, data: ByteArray): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DraftRig3D")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore download entry")
            context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            return uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DraftRig3D").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeBytes(data)
            return Uri.fromFile(file)
        }
    }

    private fun floatsToLeBytes(arr: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putFloat(it) }
        return bb.array()
    }

    private fun intsToLeBytes(arr: IntArray): ByteArray {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { bb.putInt(it) }
        return bb.array()
    }
}
