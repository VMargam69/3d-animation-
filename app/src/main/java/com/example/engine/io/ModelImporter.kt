package com.example.engine.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.engine.model.BoneNode
import com.example.engine.model.CharacterCategory
import com.example.engine.model.ImportInspectionReport
import com.example.engine.model.MaterialData
import com.example.engine.model.MeshData
import com.example.engine.model.MeshEditingOps
import com.example.engine.model.NodeType
import com.example.engine.model.PrimitiveKind
import com.example.engine.model.SceneNode
import com.example.engine.model.SkeletonData
import com.example.engine.model.Vec3
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

object ModelImporter {

    fun importFromUri(context: Context, uri: Uri): ImportInspectionReport {
        val fileName = resolveFileName(context, uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ImportInspectionReport(
                fileName = fileName,
                format = ext.uppercase(),
                supported = false,
                notes = listOf("Unable to read selected file from storage.")
            )

        return when (ext) {
            "glb" -> parseGlb(context, fileName, bytes)
            "gltf" -> parseGltfJson(context, fileName, bytes)
            "obj" -> parseObj(fileName, bytes)
            "stl" -> parseStl(fileName, bytes)
            "blend" -> ImportInspectionReport(
                fileName = fileName,
                format = "BLEND",
                supported = false,
                notes = listOf(
                    "Native .blend files store Blender's internal C-struct memory layout and cannot be parsed directly offline on Android.",
                    "Recommended Blender Workflow: Open in Blender -> File -> Export -> glTF 2.0 (.glb) -> Enable 'Include Selected/Visible', 'Apply Modifiers', 'UVs', 'Normals', 'Skinning', and 'Animations' -> Import the .glb here."
                )
            )
            "fbx", "dae", "usd", "usdz", "ply" -> ImportInspectionReport(
                fileName = fileName,
                format = ext.uppercase(),
                supported = false,
                notes = listOf(
                    "Direct .$ext import is not bundled in this lightweight offline APK to avoid native desktop SDK bloat.",
                    "Convert in Blender (File -> Import .$ext -> File -> Export -> glTF 2.0 .glb) to preserve meshes, PBR materials, textures, skeletons, skin weights, and animations."
                )
            )
            else -> {
                // Magic-header sniff in case SAF stripped extension
                if (bytes.size >= 12 && bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
                    bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()
                ) {
                    parseGlb(context, fileName, bytes)
                } else {
                    ImportInspectionReport(
                        fileName = fileName,
                        format = ext.uppercase().ifBlank { "UNKNOWN" },
                        supported = false,
                        notes = listOf("Unsupported file extension. Supported offline formats: .glb, .gltf, .obj, .stl.")
                    )
                }
            }
        }
    }

    private fun parseGlb(context: Context, fileName: String, bytes: ByteArray): ImportInspectionReport {
        if (bytes.size < 20) {
            return ImportInspectionReport(fileName, "GLB", false, notes = listOf("GLB file is truncated or invalid."))
        }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        if (magic != 0x46546C67) { // 'glTF'
            return ImportInspectionReport(fileName, "GLB", false, notes = listOf("Invalid GLB magic header."))
        }
        val version = bb.int
        bb.int // total length
        if (version != 2) {
            return ImportInspectionReport(fileName, "GLB", false, notes = listOf("Only glTF 2.0 binary (.glb) is supported (found v$version)."))
        }

        var jsonText: String? = null
        var binData: ByteArray? = null

        while (bb.remaining() >= 8) {
            val chunkLen = bb.int
            val chunkType = bb.int
            if (chunkLen < 0 || chunkLen > bb.remaining()) break
            val chunkBytes = ByteArray(chunkLen)
            bb.get(chunkBytes)
            when (chunkType) {
                0x4E4F534A -> jsonText = String(chunkBytes, Charsets.UTF_8) // JSON
                0x004E4942 -> binData = chunkBytes // BIN
            }
        }
        if (jsonText == null) {
            return ImportInspectionReport(fileName, "GLB", false, notes = listOf("GLB contains no JSON scene chunk."))
        }
        return buildFromGltf(context, fileName, "GLB", JSONObject(jsonText), binData ?: ByteArray(0))
    }

    private fun parseGltfJson(context: Context, fileName: String, bytes: ByteArray): ImportInspectionReport {
        val text = String(bytes, Charsets.UTF_8)
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return ImportInspectionReport(fileName, "GLTF", false, notes = listOf("Invalid glTF JSON file."))
        // Support embedded base64 data URI buffers
        var binData = ByteArray(0)
        val buffers = root.optJSONArray("buffers")
        if (buffers != null && buffers.length() > 0) {
            val uri = buffers.getJSONObject(0).optString("uri", "")
            if (uri.startsWith("data:") && uri.contains(",")) {
                val b64 = uri.substringAfter(",")
                binData = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            }
        }
        return buildFromGltf(context, fileName, "GLTF", root, binData)
    }

    private fun buildFromGltf(
        context: Context,
        fileName: String,
        format: String,
        root: JSONObject,
        bin: ByteArray
    ): ImportInspectionReport {
        val notes = mutableListOf<String>()
        val accessors = root.optJSONArray("accessors")
        val bufferViews = root.optJSONArray("bufferViews")
        val meshesJson = root.optJSONArray("meshes")
        val materialsJson = root.optJSONArray("materials")
        val nodesJson = root.optJSONArray("nodes")
        val skinsJson = root.optJSONArray("skins")
        val imagesJson = root.optJSONArray("images")
        val animationsJson = root.optJSONArray("animations")

        if (meshesJson == null || meshesJson.length() == 0 || accessors == null || bufferViews == null) {
            return ImportInspectionReport(fileName, format, false, notes = listOf("No geometry meshes found inside $format file."))
        }
        if (bin.isEmpty()) {
            return ImportInspectionReport(
                fileName,
                format,
                false,
                notes = listOf("External .bin buffer referenced by .gltf cannot be read via single-file pick. Export from Blender as self-contained .glb instead.")
            )
        }

        fun readAccessorFloats(accessorIndex: Int, components: Int): FloatArray? {
            if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
            val acc = accessors.getJSONObject(accessorIndex)
            val bvIdx = acc.optInt("bufferView", -1)
            if (bvIdx < 0 || bvIdx >= bufferViews.length()) return null
            val bv = bufferViews.getJSONObject(bvIdx)
            val offset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val stride = bv.optInt("byteStride", components * 4).coerceAtLeast(components * 4)
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", 5126) // 5126 = FLOAT
            val out = FloatArray(count * components)
            val bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                val base = offset + i * stride
                for (c in 0 until components) {
                    val pos = base + c * when (compType) {
                        5121 -> 1; 5123 -> 2; else -> 4
                    }
                    if (pos >= bin.size) continue
                    out[i * components + c] = when (compType) {
                        5126 -> if (pos + 4 <= bin.size) bb.getFloat(pos) else 0f
                        5121 -> (bin[pos].toInt() and 0xFF) / 255f
                        5123 -> if (pos + 2 <= bin.size) (bb.getShort(pos).toInt() and 0xFFFF) / 65535f else 0f
                        else -> 0f
                    }
                }
            }
            return out
        }

        fun readAccessorInts(accessorIndex: Int, components: Int = 1): IntArray? {
            if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
            val acc = accessors.getJSONObject(accessorIndex)
            val bvIdx = acc.optInt("bufferView", -1)
            if (bvIdx < 0 || bvIdx >= bufferViews.length()) return null
            val bv = bufferViews.getJSONObject(bvIdx)
            val offset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", 5123)
            val compBytes = when (compType) {
                5121 -> 1; 5123 -> 2; else -> 4
            }
            val stride = bv.optInt("byteStride", components * compBytes).coerceAtLeast(components * compBytes)
            val out = IntArray(count * components)
            val bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                val base = offset + i * stride
                for (c in 0 until components) {
                    val pos = base + c * compBytes
                    if (pos + compBytes > bin.size) continue
                    out[i * components + c] = when (compType) {
                        5121 -> bin[pos].toInt() and 0xFF
                        5123 -> bb.getShort(pos).toInt() and 0xFFFF
                        5125 -> bb.getInt(pos)
                        else -> 0
                    }
                }
            }
            return out
        }

        val allVerts = ArrayList<Float>()
        val allNorms = ArrayList<Float>()
        val allUvs = ArrayList<Float>()
        val allIndices = ArrayList<Int>()
        val allJIdx = ArrayList<Int>()
        val allJWgt = ArrayList<Float>()
        var importedPrimitives = 0

        for (m in 0 until meshesJson.length()) {
            val meshObj = meshesJson.getJSONObject(m)
            val prims = meshObj.optJSONArray("primitives") ?: continue
            for (p in 0 until prims.length()) {
                val prim = prims.getJSONObject(p)
                val attrs = prim.optJSONObject("attributes") ?: continue
                val posIdx = attrs.optInt("POSITION", -1)
                val positions = readAccessorFloats(posIdx, 3) ?: continue
                val vc = positions.size / 3
                val normals = readAccessorFloats(attrs.optInt("NORMAL", -1), 3) ?: FloatArray(vc * 3)
                val uvs = readAccessorFloats(attrs.optInt("TEXCOORD_0", -1), 2) ?: FloatArray(vc * 2)
                val joints = readAccessorInts(attrs.optInt("JOINTS_0", -1), 4) ?: IntArray(vc * 4)
                val weights = readAccessorFloats(attrs.optInt("WEIGHTS_0", -1), 4) ?: FloatArray(vc * 4) { idx -> if (idx % 4 == 0) 1f else 0f }
                val rawIndices = readAccessorInts(prim.optInt("indices", -1), 1) ?: IntArray(vc) { it }

                val baseV = allVerts.size / 3
                positions.forEach { allVerts.add(it) }
                normals.forEach { allNorms.add(it) }
                uvs.forEach { allUvs.add(it) }
                joints.forEach { allJIdx.add(it.coerceIn(0, SkeletonData.MAX_BONES - 1)) }
                weights.forEach { allJWgt.add(it) }
                rawIndices.forEach { allIndices.add(it + baseV) }
                importedPrimitives++
            }
        }

        if (allVerts.isEmpty()) {
            return ImportInspectionReport(fileName, format, false, notes = listOf("No valid vertex positions found in $format."))
        }

        // Normalize imported model scale into viewport-friendly ~2.0 unit height & seat on Y=0 ground
        val normVerts = normalizeAndGroundVertices(allVerts.toFloatArray())
        var finalMesh = MeshData(
            vertices = normVerts,
            normals = allNorms.toFloatArray(),
            uvs = allUvs.toFloatArray(),
            indices = allIndices.toIntArray(),
            jointIndices = allJIdx.toIntArray(),
            jointWeights = allJWgt.toFloatArray()
        )
        if (allNorms.all { it == 0f }) {
            finalMesh = MeshEditingOps.recomputeNormals(finalMesh)
        }

        // Extract primary PBR Material + embedded texture image if present
        var baseColor = 0xFF94A3B8L
        var roughness = 0.5f
        var metallic = 0.1f
        if (materialsJson != null && materialsJson.length() > 0) {
            val mat0 = materialsJson.getJSONObject(0)
            val pbr = mat0.optJSONObject("pbrMetallicRoughness")
            if (pbr != null) {
                roughness = pbr.optDouble("roughnessFactor", 0.5).toFloat().coerceIn(0.05f, 1f)
                metallic = pbr.optDouble("metallicFactor", 0.1).toFloat().coerceIn(0f, 1f)
                val bcf = pbr.optJSONArray("baseColorFactor")
                if (bcf != null && bcf.length() >= 3) {
                    val r = (bcf.getDouble(0) * 255).toInt().coerceIn(0, 255)
                    val g = (bcf.getDouble(1) * 255).toInt().coerceIn(0, 255)
                    val b = (bcf.getDouble(2) * 255).toInt().coerceIn(0, 255)
                    baseColor = (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
            }
        }

        var extractedTexturePath: String? = null
        var textureCount = 0
        if (imagesJson != null && imagesJson.length() > 0) {
            textureCount = imagesJson.length()
            val img0 = imagesJson.getJSONObject(0)
            val bvIdx = img0.optInt("bufferView", -1)
            if (bvIdx >= 0 && bvIdx < bufferViews.length()) {
                val bv = bufferViews.getJSONObject(bvIdx)
                val off = bv.optInt("byteOffset", 0)
                val len = bv.optInt("byteLength", 0)
                if (off >= 0 && len > 0 && off + len <= bin.size) {
                    val texDir = File(context.filesDir, "imported_textures").apply { mkdirs() }
                    val texFile = File(texDir, "tex_${System.currentTimeMillis()}.png")
                    texFile.writeBytes(bin.copyOfRange(off, off + len))
                    extractedTexturePath = texFile.absolutePath
                    notes += "Extracted embedded texture to local offline storage."
                }
            }
        }

        // Extract Skeleton Bones from glTF Skins + Nodes
        val bones = mutableListOf<BoneNode>()
        if (skinsJson != null && skinsJson.length() > 0 && nodesJson != null) {
            val skin0 = skinsJson.getJSONObject(0)
            val jointsArr = skin0.optJSONArray("joints")
            if (jointsArr != null) {
                val jointNodeToParent = HashMap<Int, Int>()
                for (n in 0 until nodesJson.length()) {
                    val ch = nodesJson.getJSONObject(n).optJSONArray("children") ?: continue
                    for (c in 0 until ch.length()) jointNodeToParent[ch.getInt(c)] = n
                }
                val jointSet = HashSet<Int>()
                for (j in 0 until jointsArr.length()) jointSet.add(jointsArr.getInt(j))

                for (j in 0 until min(jointsArr.length(), SkeletonData.MAX_BONES)) {
                    val nodeIdx = jointsArr.getInt(j)
                    val nObj = nodesJson.optJSONObject(nodeIdx) ?: continue
                    val bName = nObj.optString("name", "Bone_$j")
                    val trans = nObj.optJSONArray("translation")
                    val offset = if (trans != null && trans.length() >= 3) {
                        Vec3(trans.getDouble(0).toFloat(), trans.getDouble(1).toFloat(), trans.getDouble(2).toFloat())
                    } else Vec3(0f, 0.25f, 0f)
                    val pNode = jointNodeToParent[nodeIdx]
                    val parentId = if (pNode != null && jointSet.contains(pNode)) "bone_$pNode" else null
                    bones += BoneNode(
                        id = "bone_$nodeIdx",
                        name = bName,
                        parentId = parentId,
                        localOffset = offset,
                        length = offset.length().coerceIn(0.18f, 0.55f)
                    )
                }
                if (bones.isNotEmpty()) {
                    notes += "Preserved ${bones.size} armature bones and skinning weights."
                }
            }
        }

        val animCount = animationsJson?.length() ?: 0
        if (animCount > 0) {
            notes += "Detected $animCount glTF animation clip(s); rig bones are ready for timeline keyframing and built-in motion presets."
        }

        val cleanName = fileName.substringBeforeLast('.').ifBlank { "Imported Blender Model" }
        val node = SceneNode(
            name = cleanName,
            nodeType = if (bones.isNotEmpty()) NodeType.CHARACTER else NodeType.MESH,
            primitiveKind = PrimitiveKind.CUSTOM,
            mesh = finalMesh,
            material = MaterialData(
                name = "$cleanName Mat",
                baseColor = baseColor,
                roughness = roughness,
                metallic = metallic,
                texturePath = extractedTexturePath
            ),
            skeleton = if (bones.isNotEmpty()) SkeletonData(bones) else null,
            categoryTag = CharacterCategory.BLENDER.name,
            sourceFormat = format
        )

        return ImportInspectionReport(
            fileName = fileName,
            format = format,
            supported = true,
            meshCount = importedPrimitives,
            vertexCount = finalMesh.vertexCount,
            triangleCount = finalMesh.triangleCount,
            materialCount = materialsJson?.length() ?: 1,
            textureCount = textureCount,
            boneCount = bones.size,
            animationCount = animCount,
            notes = notes,
            importedNode = node
        )
    }

    private fun parseObj(fileName: String, bytes: ByteArray): ImportInspectionReport {
        val rawV = ArrayList<Vec3>()
        val rawVt = ArrayList<Pair<Float, Float>>()
        val rawVn = ArrayList<Vec3>()
        val outV = ArrayList<Float>()
        val outN = ArrayList<Float>()
        val outUv = ArrayList<Float>()
        val outI = ArrayList<Int>()

        val lines = String(bytes, Charsets.UTF_8).lineSequence()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> if (parts.size >= 4) {
                    rawV.add(Vec3(parts[1].toFloatOrNull() ?: 0f, parts[2].toFloatOrNull() ?: 0f, parts[3].toFloatOrNull() ?: 0f))
                }
                "vt" -> if (parts.size >= 3) {
                    rawVt.add((parts[1].toFloatOrNull() ?: 0f) to (parts[2].toFloatOrNull() ?: 0f))
                }
                "vn" -> if (parts.size >= 4) {
                    rawVn.add(Vec3(parts[1].toFloatOrNull() ?: 0f, parts[2].toFloatOrNull() ?: 1f, parts[3].toFloatOrNull() ?: 0f))
                }
                "f" -> if (parts.size >= 4) {
                    val faceVerts = mutableListOf<Int>()
                    for (k in 1 until parts.size) {
                        val comps = parts[k].split("/")
                        val vIdx = resolveObjIndex(comps[0].toIntOrNull() ?: 1, rawV.size)
                        val vtIdx = comps.getOrNull(1)?.toIntOrNull()?.let { resolveObjIndex(it, rawVt.size) } ?: -1
                        val vnIdx = comps.getOrNull(2)?.toIntOrNull()?.let { resolveObjIndex(it, rawVn.size) } ?: -1
                        val vp = rawV.getOrNull(vIdx) ?: Vec3.ZERO
                        val uv = rawVt.getOrNull(vtIdx) ?: (0f to 0f)
                        val vn = rawVn.getOrNull(vnIdx) ?: Vec3.ZERO
                        val idx = outV.size / 3
                        outV.add(vp.x); outV.add(vp.y); outV.add(vp.z)
                        outUv.add(uv.first); outUv.add(uv.second)
                        outN.add(vn.x); outN.add(vn.y); outN.add(vn.z)
                        faceVerts.add(idx)
                    }
                    for (t in 1 until faceVerts.size - 1) {
                        outI.add(faceVerts[0]); outI.add(faceVerts[t]); outI.add(faceVerts[t + 1])
                    }
                }
            }
        }

        if (outV.isEmpty() || outI.isEmpty()) {
            return ImportInspectionReport(fileName, "OBJ", false, notes = listOf("No triangle faces parsed from OBJ file."))
        }

        val normalized = normalizeAndGroundVertices(outV.toFloatArray())
        var mesh = MeshData(normalized, outN.toFloatArray(), outUv.toFloatArray(), outI.toIntArray())
        if (rawVn.isEmpty()) mesh = MeshEditingOps.recomputeNormals(mesh)

        val cleanName = fileName.substringBeforeLast('.').ifBlank { "Imported OBJ" }
        val node = SceneNode(
            name = cleanName,
            nodeType = NodeType.MESH,
            mesh = mesh,
            material = MaterialData(name = "$cleanName Mat", baseColor = 0xFF94A3B8),
            categoryTag = CharacterCategory.BLENDER.name,
            sourceFormat = "OBJ"
        )
        return ImportInspectionReport(
            fileName = fileName,
            format = "OBJ",
            supported = true,
            meshCount = 1,
            vertexCount = mesh.vertexCount,
            triangleCount = mesh.triangleCount,
            materialCount = 1,
            notes = listOf("OBJ static geometry imported cleanly. Use Pose/Rig panel -> Add Humanoid Rig + Auto-Bind to rig this mesh."),
            importedNode = node
        )
    }

    private fun parseStl(fileName: String, bytes: ByteArray): ImportInspectionReport {
        val outV = ArrayList<Float>()
        val outN = ArrayList<Float>()
        val outUv = ArrayList<Float>()
        val outI = ArrayList<Int>()

        val isAscii = bytes.size > 80 && String(bytes, 0, 5, Charsets.US_ASCII).equals("solid", ignoreCase = true) &&
            String(bytes, 0, min(bytes.size, 512), Charsets.US_ASCII).contains("facet")

        if (isAscii) {
            val text = String(bytes, Charsets.UTF_8)
            val vertRegex = Regex("vertex\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)")
            for (m in vertRegex.findAll(text)) {
                val idx = outV.size / 3
                outV.add(m.groupValues[1].toFloatOrNull() ?: 0f)
                outV.add(m.groupValues[2].toFloatOrNull() ?: 0f)
                outV.add(m.groupValues[3].toFloatOrNull() ?: 0f)
                outN.add(0f); outN.add(1f); outN.add(0f)
                outUv.add(0f); outUv.add(0f)
                outI.add(idx)
            }
        } else if (bytes.size >= 84) {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val triCount = bb.getInt(80).coerceIn(0, 120_000)
            var pos = 84
            for (t in 0 until triCount) {
                if (pos + 50 > bytes.size) break
                val nx = bb.getFloat(pos); val ny = bb.getFloat(pos + 4); val nz = bb.getFloat(pos + 8)
                pos += 12
                for (k in 0..2) {
                    val idx = outV.size / 3
                    outV.add(bb.getFloat(pos)); outV.add(bb.getFloat(pos + 4)); outV.add(bb.getFloat(pos + 8))
                    outN.add(nx); outN.add(ny); outN.add(nz)
                    outUv.add(0f); outUv.add(0f)
                    outI.add(idx)
                    pos += 12
                }
                pos += 2 // attribute byte count
            }
        }

        if (outV.isEmpty()) {
            return ImportInspectionReport(fileName, "STL", false, notes = listOf("Unable to parse triangles from STL file."))
        }
        val mesh = MeshEditingOps.recomputeNormals(
            MeshData(normalizeAndGroundVertices(outV.toFloatArray()), outN.toFloatArray(), outUv.toFloatArray(), outI.toIntArray())
        )
        val cleanName = fileName.substringBeforeLast('.').ifBlank { "Imported STL" }
        val node = SceneNode(
            name = cleanName,
            nodeType = NodeType.MESH,
            mesh = mesh,
            material = MaterialData(name = "$cleanName Mat", baseColor = 0xFF6366F1),
            categoryTag = CharacterCategory.BLENDER.name,
            sourceFormat = "STL"
        )
        return ImportInspectionReport(
            fileName = fileName,
            format = "STL",
            supported = true,
            meshCount = 1,
            vertexCount = mesh.vertexCount,
            triangleCount = mesh.triangleCount,
            materialCount = 1,
            notes = listOf("STL geometry imported and centered on ground plane."),
            importedNode = node
        )
    }

    private fun resolveObjIndex(raw: Int, count: Int): Int =
        if (raw > 0) raw - 1 else (count + raw).coerceAtLeast(0)

    private fun normalizeAndGroundVertices(verts: FloatArray): FloatArray {
        if (verts.isEmpty()) return verts
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (i in verts.indices step 3) {
            minX = min(minX, verts[i]); maxX = max(maxX, verts[i])
            minY = min(minY, verts[i + 1]); maxY = max(maxY, verts[i + 1])
            minZ = min(minZ, verts[i + 2]); maxZ = max(maxZ, verts[i + 2])
        }
        val cx = (minX + maxX) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val maxSpan = max(max(maxX - minX, maxY - minY), maxZ - minZ).coerceAtLeast(0.001f)
        val scale = if (maxSpan > 3.5f || maxSpan < 0.35f) 2.0f / maxSpan else 1.0f

        val out = FloatArray(verts.size)
        for (i in verts.indices step 3) {
            out[i] = (verts[i] - cx) * scale
            out[i + 1] = (verts[i + 1] - minY) * scale
            out[i + 2] = (verts[i + 2] - cz) * scale
        }
        return out
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "model.glb"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.glb"
    }
}
