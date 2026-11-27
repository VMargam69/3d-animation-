package com.example.engine.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.engine.animation.AnimationEvaluator
import com.example.engine.model.Math3D
import com.example.engine.model.SceneDocument
import com.example.engine.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GlbExporter {

    /**
     * Serializes the visible 3D scene meshes at the current frame into a valid glTF 2.0 Binary (.glb)
     * file saved to Downloads/DraftRig3D (MediaStore on Android 10+, app external files fallback).
     */
    fun exportSceneToGlb(context: Context, scene: SceneDocument): Result<Pair<Uri, String>> = runCatching {
        val evaluated = AnimationEvaluator.evaluate(scene, scene.timeline.currentFrame)
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

        for (node in scene.nodes) {
            val state = evaluated.nodeStates[node.id] ?: continue
            if (!state.visible) continue
            val mesh = node.mesh ?: continue
            if (mesh.vertexCount == 0 || mesh.indices.isEmpty()) continue

            // Bake world transform into exported vertices so scene matches viewport 1:1 in Blender
            val worldVerts = FloatArray(mesh.vertices.size)
            var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
            for (i in 0 until mesh.vertexCount) {
                val p = Math3D.transformPoint(
                    state.worldMatrix,
                    Vec3(mesh.vertices[i * 3], mesh.vertices[i * 3 + 1], mesh.vertices[i * 3 + 2])
                )
                worldVerts[i * 3] = p.x; worldVerts[i * 3 + 1] = p.y; worldVerts[i * 3 + 2] = p.z
                if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
                if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
            }

            val vBytes = floatsToLeBytes(worldVerts)
            val nBytes = floatsToLeBytes(mesh.normals)
            val uvBytes = floatsToLeBytes(mesh.uvs)
            val iBytes = intsToLeBytes(mesh.indices)

            val bvPos = appendToBin(vBytes, 34962)
            val bvNorm = appendToBin(nBytes, 34962)
            val bvUv = appendToBin(uvBytes, 34962)
            val bvIdx = appendToBin(iBytes, 34963)

            val accPos = accessors.length().also {
                accessors.put(JSONObject().apply {
                    put("bufferView", bvPos)
                    put("componentType", 5126)
                    put("count", mesh.vertexCount)
                    put("type", "VEC3")
                    put("min", JSONArray(listOf(minX.toDouble(), minY.toDouble(), minZ.toDouble())))
                    put("max", JSONArray(listOf(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())))
                })
            }
            val accNorm = accessors.length().also {
                accessors.put(JSONObject().apply {
                    put("bufferView", bvNorm)
                    put("componentType", 5126)
                    put("count", mesh.vertexCount)
                    put("type", "VEC3")
                })
            }
            val accUv = accessors.length().also {
                accessors.put(JSONObject().apply {
                    put("bufferView", bvUv)
                    put("componentType", 5126)
                    put("count", mesh.vertexCount)
                    put("type", "VEC2")
                })
            }
            val accIdx = accessors.length().also {
                accessors.put(JSONObject().apply {
                    put("bufferView", bvIdx)
                    put("componentType", 5125) // UNSIGNED_INT
                    put("count", mesh.indices.size)
                    put("type", "SCALAR")
                })
            }

            val rgb = Math3D.colorHexToRgbFloats(node.material.baseColor)
            val matIdx = materials.length().also {
                materials.put(JSONObject().apply {
                    put("name", node.material.name)
                    put("pbrMetallicRoughness", JSONObject().apply {
                        put("baseColorFactor", JSONArray(listOf(rgb[0].toDouble(), rgb[1].toDouble(), rgb[2].toDouble(), node.material.opacity.toDouble())))
                        put("metallicFactor", node.material.metallic.toDouble())
                        put("roughnessFactor", node.material.roughness.toDouble())
                    })
                })
            }

            val meshIdx = meshes.length().also {
                meshes.put(JSONObject().apply {
                    put("name", node.name)
                    put("primitives", JSONArray().put(JSONObject().apply {
                        put("attributes", JSONObject().apply {
                            put("POSITION", accPos)
                            put("NORMAL", accNorm)
                            put("TEXCOORD_0", accUv)
                        })
                        put("indices", accIdx)
                        put("material", matIdx)
                    }))
                })
            }

            val nodeIdx = nodes.length().also {
                nodes.put(JSONObject().apply {
                    put("name", node.name)
                    put("mesh", meshIdx)
                })
            }
            sceneNodeIndices.put(nodeIdx)
        }

        while (binStream.size() % 4 != 0) binStream.write(0)
        val binBytes = binStream.toByteArray()

        val gltfJson = JSONObject().apply {
            put("asset", JSONObject().apply {
                put("version", "2.0")
                put("generator", "DraftRig 3D Studio")
            })
            put("scene", 0)
            put("scenes", JSONArray().put(JSONObject().put("nodes", sceneNodeIndices)))
            put("nodes", nodes)
            put("meshes", meshes)
            put("materials", materials)
            put("accessors", accessors)
            put("bufferViews", bufferViews)
            put("buffers", JSONArray().put(JSONObject().put("byteLength", binBytes.size)))
        }.toString()

        var jsonBytes = gltfJson.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (jsonBytes.size % 4)) % 4
        if (jsonPad > 0) jsonBytes += ByteArray(jsonPad) { 0x20 } // space padding per glTF spec

        val totalLen = 12 + 8 + jsonBytes.size + 8 + binBytes.size
        val glbBuf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        glbBuf.putInt(0x46546C67) // glTF
        glbBuf.putInt(2)
        glbBuf.putInt(totalLen)
        glbBuf.putInt(jsonBytes.size)
        glbBuf.putInt(0x4E4F534A) // JSON
        glbBuf.put(jsonBytes)
        glbBuf.putInt(binBytes.size)
        glbBuf.putInt(0x004E4942) // BIN
        glbBuf.put(binBytes)

        val safeTitle = scene.projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "draftrig_scene" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.glb"
        val outUri = saveToDownloads(context, fileName, "model/gltf-binary", glbBuf.array())
        outUri to fileName
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
