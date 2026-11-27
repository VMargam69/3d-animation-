package com.example.engine.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CharacterBuildSpec(
    val name: String,
    val category: CharacterCategory = CharacterCategory.CUSTOM,
    val costume: CostumePreset = CostumePreset.ORIGINAL_HERO,
    val bodyType: CharacterArchetype = CharacterArchetype.HUMANOID,
    val heightScale: Float = 1.0f,
    val headScale: Float = 1.0f,
    val torsoWidth: Float = 1.0f,
    val limbThickness: Float = 1.0f,
    val primaryColor: Long = costume.primaryColor,
    val secondaryColor: Long = costume.secondaryColor,
    val accessory: AccessoryKind = costume.accessory,
    val roughness: Float = 0.45f,
    val metallic: Float = 0.15f,
    val styleId: String? = null
)

enum class CharacterArchetype {
    HUMANOID,
    BIRD,
    QUADRUPED,
    VEHICLE,
    FLOATING_SPIRIT,
    PROP_SHRINE
}

object PrimitiveMeshFactory {

    fun createPrimitive(kind: PrimitiveKind): MeshData = when (kind) {
        PrimitiveKind.CUBE -> createCube(1.0f)
        PrimitiveKind.SPHERE -> createUvSphere(0.65f, 16, 12)
        PrimitiveKind.UV_SPHERE -> createUvSphere(0.65f, 24, 18)
        PrimitiveKind.CYLINDER -> createCylinder(0.5f, 0.5f, 1.4f, 20)
        PrimitiveKind.CONE -> createCylinder(0.02f, 0.65f, 1.4f, 20)
        PrimitiveKind.PLANE -> createPlane(3.0f, 3.0f, 4)
        PrimitiveKind.TORUS -> createTorus(0.65f, 0.22f, 24, 14)
        PrimitiveKind.CAPSULE -> createCapsule(0.35f, 0.9f, 16)
        PrimitiveKind.CIRCLE -> createCircle(0.75f, 24)
        PrimitiveKind.CUSTOM -> createCube(1.0f)
    }

    fun createCube(size: Float = 1f, sx: Float = size, sy: Float = size, sz: Float = size): MeshData {
        val hx = sx * 0.5f; val hy = sy * 0.5f; val hz = sz * 0.5f
        val verts = floatArrayOf(
            // Front (+Z)
            -hx, -hy, hz,  hx, -hy, hz,  hx, hy, hz,  -hx, hy, hz,
            // Back (-Z)
            hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz,
            // Right (+X)
            hx, -hy, hz,   hx, -hy, -hz,  hx, hy, -hz,  hx, hy, hz,
            // Left (-X)
            -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz,
            // Top (+Y)
            -hx, hy, hz,   hx, hy, hz,   hx, hy, -hz, -hx, hy, -hz,
            // Bottom (-Y)
            -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz,
        )
        val norms = floatArrayOf(
            0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f,
            1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f,
            0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,
            0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f
        )
        val uvs = FloatArray(24 * 2)
        for (f in 0 until 6) {
            val b = f * 8
            uvs[b] = 0f; uvs[b + 1] = 1f
            uvs[b + 2] = 1f; uvs[b + 3] = 1f
            uvs[b + 4] = 1f; uvs[b + 5] = 0f
            uvs[b + 6] = 0f; uvs[b + 7] = 0f
        }
        val idx = IntArray(36)
        for (f in 0 until 6) {
            val vb = f * 4
            val ib = f * 6
            idx[ib] = vb; idx[ib + 1] = vb + 1; idx[ib + 2] = vb + 2
            idx[ib + 3] = vb; idx[ib + 4] = vb + 2; idx[ib + 5] = vb + 3
        }
        return MeshData(verts, norms, uvs, idx)
    }

    fun createUvSphere(radius: Float, slices: Int, stacks: Int): MeshData {
        val vList = ArrayList<Float>()
        val nList = ArrayList<Float>()
        val uvList = ArrayList<Float>()
        val iList = ArrayList<Int>()

        for (st in 0..stacks) {
            val v = st.toFloat() / stacks
            val phi = v * PI.toFloat()
            val y = cos(phi)
            val r = sin(phi)
            for (sl in 0..slices) {
                val u = sl.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val x = r * cos(theta)
                val z = r * sin(theta)
                vList.add(x * radius); vList.add(y * radius); vList.add(z * radius)
                nList.add(x); nList.add(y); nList.add(z)
                uvList.add(u); uvList.add(v)
            }
        }
        val row = slices + 1
        for (st in 0 until stacks) {
            for (sl in 0 until slices) {
                val a = st * row + sl
                val b = a + row
                val c = b + 1
                val d = a + 1
                iList.add(a); iList.add(b); iList.add(d)
                iList.add(b); iList.add(c); iList.add(d)
            }
        }
        return MeshData(vList.toFloatArray(), nList.toFloatArray(), uvList.toFloatArray(), iList.toIntArray())
    }

    fun createCylinder(topRadius: Float, bottomRadius: Float, height: Float, segments: Int): MeshData {
        val vList = ArrayList<Float>()
        val nList = ArrayList<Float>()
        val uvList = ArrayList<Float>()
        val iList = ArrayList<Int>()
        val halfH = height * 0.5f

        for (i in 0..segments) {
            val u = i.toFloat() / segments
            val angle = u * 2f * PI.toFloat()
            val cx = cos(angle)
            val sz = sin(angle)
            val slope = (bottomRadius - topRadius) / height
            val n = Vec3(cx, slope, sz).normalized()

            vList.add(cx * topRadius); vList.add(halfH); vList.add(sz * topRadius)
            nList.add(n.x); nList.add(n.y); nList.add(n.z)
            uvList.add(u); uvList.add(0f)

            vList.add(cx * bottomRadius); vList.add(-halfH); vList.add(sz * bottomRadius)
            nList.add(n.x); nList.add(n.y); nList.add(n.z)
            uvList.add(u); uvList.add(1f)
        }
        for (i in 0 until segments) {
            val base = i * 2
            iList.add(base); iList.add(base + 1); iList.add(base + 2)
            iList.add(base + 1); iList.add(base + 3); iList.add(base + 2)
        }

        // Top & bottom caps
        val topCenter = vList.size / 3
        vList.add(0f); vList.add(halfH); vList.add(0f)
        nList.add(0f); nList.add(1f); nList.add(0f)
        uvList.add(0.5f); uvList.add(0.5f)
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
            vList.add(cos(angle) * topRadius); vList.add(halfH); vList.add(sin(angle) * topRadius)
            nList.add(0f); nList.add(1f); nList.add(0f)
            uvList.add(0.5f + 0.5f * cos(angle)); uvList.add(0.5f + 0.5f * sin(angle))
        }
        for (i in 0 until segments) {
            iList.add(topCenter); iList.add(topCenter + 1 + i + 1); iList.add(topCenter + 1 + i)
        }

        val botCenter = vList.size / 3
        vList.add(0f); vList.add(-halfH); vList.add(0f)
        nList.add(0f); nList.add(-1f); nList.add(0f)
        uvList.add(0.5f); uvList.add(0.5f)
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
            vList.add(cos(angle) * bottomRadius); vList.add(-halfH); vList.add(sin(angle) * bottomRadius)
            nList.add(0f); nList.add(-1f); nList.add(0f)
            uvList.add(0.5f + 0.5f * cos(angle)); uvList.add(0.5f + 0.5f * sin(angle))
        }
        for (i in 0 until segments) {
            iList.add(botCenter); iList.add(botCenter + 1 + i); iList.add(botCenter + 1 + i + 1)
        }

        return MeshData(vList.toFloatArray(), nList.toFloatArray(), uvList.toFloatArray(), iList.toIntArray())
    }

    fun createPlane(width: Float, depth: Float, subdivisions: Int = 2): MeshData {
        val vList = ArrayList<Float>()
        val nList = ArrayList<Float>()
        val uvList = ArrayList<Float>()
        val iList = ArrayList<Int>()
        for (z in 0..subdivisions) {
            val v = z.toFloat() / subdivisions
            for (x in 0..subdivisions) {
                val u = x.toFloat() / subdivisions
                vList.add((u - 0.5f) * width); vList.add(0f); vList.add((v - 0.5f) * depth)
                nList.add(0f); nList.add(1f); nList.add(0f)
                uvList.add(u); uvList.add(v)
            }
        }
        val row = subdivisions + 1
        for (z in 0 until subdivisions) {
            for (x in 0 until subdivisions) {
                val a = z * row + x
                val b = a + row
                val c = b + 1
                val d = a + 1
                iList.add(a); iList.add(b); iList.add(d)
                iList.add(b); iList.add(c); iList.add(d)
            }
        }
        return MeshData(vList.toFloatArray(), nList.toFloatArray(), uvList.toFloatArray(), iList.toIntArray())
    }

    fun createTorus(majorRadius: Float, minorRadius: Float, majorSegs: Int, minorSegs: Int): MeshData {
        val vList = ArrayList<Float>()
        val nList = ArrayList<Float>()
        val uvList = ArrayList<Float>()
        val iList = ArrayList<Int>()

        for (i in 0..majorSegs) {
            val u = i.toFloat() / majorSegs
            val theta = u * 2f * PI.toFloat()
            val ct = cos(theta); val st = sin(theta)
            for (j in 0..minorSegs) {
                val v = j.toFloat() / minorSegs
                val phi = v * 2f * PI.toFloat()
                val cp = cos(phi); val sp = sin(phi)
                val r = majorRadius + minorRadius * cp
                vList.add(r * ct); vList.add(minorRadius * sp); vList.add(r * st)
                nList.add(cp * ct); nList.add(sp); nList.add(cp * st)
                uvList.add(u); uvList.add(v)
            }
        }
        val row = minorSegs + 1
        for (i in 0 until majorSegs) {
            for (j in 0 until minorSegs) {
                val a = i * row + j
                val b = (i + 1) * row + j
                val c = b + 1
                val d = a + 1
                iList.add(a); iList.add(b); iList.add(d)
                iList.add(b); iList.add(c); iList.add(d)
            }
        }
        return MeshData(vList.toFloatArray(), nList.toFloatArray(), uvList.toFloatArray(), iList.toIntArray())
    }

    fun createCapsule(radius: Float, cylinderHeight: Float, segments: Int): MeshData {
        val halfH = cylinderHeight * 0.5f
        val body = createCylinder(radius, radius, cylinderHeight, segments)
        val topSphere = translateMesh(createUvSphere(radius, segments, 6), Vec3(0f, halfH, 0f))
        val botSphere = translateMesh(createUvSphere(radius, segments, 6), Vec3(0f, -halfH, 0f))
        return mergeMeshes(listOf(body to 0, topSphere to 0, botSphere to 0))
    }

    fun createCircle(radius: Float, segments: Int): MeshData {
        val vList = ArrayList<Float>()
        val nList = ArrayList<Float>()
        val uvList = ArrayList<Float>()
        val iList = ArrayList<Int>()
        vList.add(0f); vList.add(0f); vList.add(0f)
        nList.add(0f); nList.add(1f); nList.add(0f)
        uvList.add(0.5f); uvList.add(0.5f)
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            vList.add(x); vList.add(0f); vList.add(z)
            nList.add(0f); nList.add(1f); nList.add(0f)
            uvList.add(0.5f + 0.5f * cos(angle)); uvList.add(0.5f + 0.5f * sin(angle))
        }
        for (i in 0 until segments) {
            iList.add(0); iList.add(i + 2); iList.add(i + 1)
        }
        return MeshData(vList.toFloatArray(), nList.toFloatArray(), uvList.toFloatArray(), iList.toIntArray())
    }

    fun translateMesh(mesh: MeshData, offset: Vec3): MeshData {
        val outVerts = mesh.vertices.copyOf()
        for (i in 0 until mesh.vertexCount) {
            outVerts[i * 3] += offset.x
            outVerts[i * 3 + 1] += offset.y
            outVerts[i * 3 + 2] += offset.z
        }
        return mesh.copy(vertices = outVerts).bumpVersion()
    }

    fun scaleMesh(mesh: MeshData, scale: Vec3): MeshData {
        val outVerts = mesh.vertices.copyOf()
        val outNorms = mesh.normals.copyOf()
        val ix = if (scale.x != 0f) 1f / scale.x else 1f
        val iy = if (scale.y != 0f) 1f / scale.y else 1f
        val iz = if (scale.z != 0f) 1f / scale.z else 1f
        for (i in 0 until mesh.vertexCount) {
            outVerts[i * 3] *= scale.x
            outVerts[i * 3 + 1] *= scale.y
            outVerts[i * 3 + 2] *= scale.z
            val n = Vec3(outNorms[i * 3] * ix, outNorms[i * 3 + 1] * iy, outNorms[i * 3 + 2] * iz).normalized()
            outNorms[i * 3] = n.x
            outNorms[i * 3 + 1] = n.y
            outNorms[i * 3 + 2] = n.z
        }
        return mesh.copy(vertices = outVerts, normals = outNorms).bumpVersion()
    }

    fun rotateMesh(mesh: MeshData, rotDeg: Vec3): MeshData {
        val rx = rotDeg.x * PI.toFloat() / 180f
        val ry = rotDeg.y * PI.toFloat() / 180f
        val rz = rotDeg.z * PI.toFloat() / 180f
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)

        fun rotVec(x: Float, y: Float, z: Float): Vec3 {
            // Apply Z, then X, then Y (matching TRS Y * X * Z)
            val x1 = x * cz - y * sz
            val y1 = x * sz + y * cz
            val z1 = z
            val y2 = y1 * cx - z1 * sx
            val z2 = y1 * sx + z1 * cx
            val x2 = x1
            val x3 = x2 * cy + z2 * sy
            val z3 = -x2 * sy + z2 * cy
            return Vec3(x3, y2, z3)
        }

        val outVerts = mesh.vertices.copyOf()
        val outNorms = mesh.normals.copyOf()
        for (i in 0 until mesh.vertexCount) {
            val v = rotVec(outVerts[i * 3], outVerts[i * 3 + 1], outVerts[i * 3 + 2])
            outVerts[i * 3] = v.x
            outVerts[i * 3 + 1] = v.y
            outVerts[i * 3 + 2] = v.z
            val n = rotVec(outNorms[i * 3], outNorms[i * 3 + 1], outNorms[i * 3 + 2]).normalized()
            outNorms[i * 3] = n.x
            outNorms[i * 3 + 1] = n.y
            outNorms[i * 3 + 2] = n.z
        }
        return mesh.copy(vertices = outVerts, normals = outNorms).bumpVersion()
    }

    fun mergeMeshes(partsWithBoneIndex: List<Pair<MeshData, Int>>): MeshData {
        var totalVerts = 0
        var totalIndices = 0
        for ((m, _) in partsWithBoneIndex) {
            totalVerts += m.vertexCount
            totalIndices += m.indices.size
        }
        val verts = FloatArray(totalVerts * 3)
        val norms = FloatArray(totalVerts * 3)
        val uvs = FloatArray(totalVerts * 2)
        val indices = IntArray(totalIndices)
        val jIdx = IntArray(totalVerts * 4)
        val jWgt = FloatArray(totalVerts * 4)

        var vOffset = 0
        var iOffset = 0
        for ((m, boneIdx) in partsWithBoneIndex) {
            val vc = m.vertexCount
            System.arraycopy(m.vertices, 0, verts, vOffset * 3, vc * 3)
            System.arraycopy(m.normals, 0, norms, vOffset * 3, vc * 3)
            System.arraycopy(m.uvs, 0, uvs, vOffset * 2, vc * 2)
            for (k in m.indices.indices) {
                indices[iOffset + k] = m.indices[k] + vOffset
            }
            val safeBone = boneIdx.coerceIn(0, SkeletonData.MAX_BONES - 1)
            for (v in 0 until vc) {
                val base = (vOffset + v) * 4
                jIdx[base] = safeBone
                jWgt[base] = 1f
            }
            vOffset += vc
            iOffset += m.indices.size
        }
        return MeshData(verts, norms, uvs, indices, jIdx, jWgt)
    }

    /**
     * Merges sculpted sub-meshes with per-part skeletal bone indices AND per-vertex RGB colors
     * so 3D characters render with distinct skin tones, faces, hair, layered costumes, and accessories.
     */
    fun mergeColoredMeshes(parts: List<Triple<MeshData, Int, Long>>): MeshData {
        var totalVerts = 0
        var totalIndices = 0
        for ((m, _, _) in parts) {
            totalVerts += m.vertexCount
            totalIndices += m.indices.size
        }
        val verts = FloatArray(totalVerts * 3)
        val norms = FloatArray(totalVerts * 3)
        val uvs = FloatArray(totalVerts * 2)
        val indices = IntArray(totalIndices)
        val jIdx = IntArray(totalVerts * 4)
        val jWgt = FloatArray(totalVerts * 4)
        val cols = FloatArray(totalVerts * 3)

        var vOffset = 0
        var iOffset = 0
        for ((m, boneIdx, colorHex) in parts) {
            val vc = m.vertexCount
            System.arraycopy(m.vertices, 0, verts, vOffset * 3, vc * 3)
            System.arraycopy(m.normals, 0, norms, vOffset * 3, vc * 3)
            System.arraycopy(m.uvs, 0, uvs, vOffset * 2, vc * 2)
            for (k in m.indices.indices) {
                indices[iOffset + k] = m.indices[k] + vOffset
            }
            val safeBone = boneIdx.coerceIn(0, SkeletonData.MAX_BONES - 1)
            val r = ((colorHex shr 16) and 0xFF) / 255f
            val g = ((colorHex shr 8) and 0xFF) / 255f
            val b = (colorHex and 0xFF) / 255f
            for (v in 0 until vc) {
                val jBase = (vOffset + v) * 4
                jIdx[jBase] = safeBone
                jWgt[jBase] = 1f
                val cBase = (vOffset + v) * 3
                cols[cBase] = r
                cols[cBase + 1] = g
                cols[cBase + 2] = b
            }
            vOffset += vc
            iOffset += m.indices.size
        }
        return MeshData(
            vertices = verts,
            normals = norms,
            uvs = uvs,
            indices = indices,
            jointIndices = jIdx,
            jointWeights = jWgt,
            colors = cols,
            useVertexColors = true
        )
    }
}

private data class HumanoidStyleProfile(
    val skin: Long,
    val hair: Long,
    val shirt: Long,
    val accent: Long,
    val pants: Long,
    val shoes: Long,
    val belt: Long = 0xFF2B211B,
    val eyeColor: Long = 0xFF1A1E2B,
    val isMannequin: Boolean = false,
    val isFemale: Boolean = false,
    val hasMustache: Boolean = false,
    val hasBeard: Boolean = false,
    val hasLongTunic: Boolean = false,
    val hasSkirt: Boolean = false,
    val hasLabCoat: Boolean = false,
    val hasJacketOpen: Boolean = false,
    val hasChestArmor: Boolean = false,
    val hasPoliceBelt: Boolean = false,
    val hasSash: Boolean = false,
    val hasTie: Boolean = false,
    val hasStethoscope: Boolean = false,
    val shortSleeves: Boolean = false
)

object CharacterMeshBuilder {

    fun buildRiggedCharacterNode(spec: CharacterBuildSpec): SceneNode {
        return when (spec.bodyType) {
            CharacterArchetype.HUMANOID -> buildHumanoid(spec)
            CharacterArchetype.BIRD -> buildBird(spec)
            CharacterArchetype.QUADRUPED -> buildQuadruped(spec)
            CharacterArchetype.VEHICLE -> buildVehicle(spec)
            CharacterArchetype.FLOATING_SPIRIT -> buildSpirit(spec)
            CharacterArchetype.PROP_SHRINE -> buildShrineProp(spec)
        }
    }

    fun createDefaultHumanoidSkeleton(h: Float = 1.0f): SkeletonData {
        val bones = listOf(
            BoneNode(id = "hips", name = "Hips", parentId = null, localOffset = Vec3(0f, 0.92f * h, 0f), length = 0.25f * h),
            BoneNode(id = "spine", name = "Spine", parentId = "hips", localOffset = Vec3(0f, 0.22f * h, 0f), length = 0.42f * h),
            BoneNode(id = "head", name = "Head", parentId = "spine", localOffset = Vec3(0f, 0.46f * h, 0f), length = 0.34f * h),
            BoneNode(id = "l_upper_arm", name = "Left Upper Arm", parentId = "spine", localOffset = Vec3(-0.34f, 0.36f * h, 0f), length = 0.34f * h),
            BoneNode(id = "l_forearm", name = "Left Forearm", parentId = "l_upper_arm", localOffset = Vec3(0f, -0.34f * h, 0f), length = 0.30f * h),
            BoneNode(id = "r_upper_arm", name = "Right Upper Arm", parentId = "spine", localOffset = Vec3(0.34f, 0.36f * h, 0f), length = 0.34f * h),
            BoneNode(id = "r_forearm", name = "Right Forearm", parentId = "r_upper_arm", localOffset = Vec3(0f, -0.34f * h, 0f), length = 0.30f * h),
            BoneNode(id = "l_thigh", name = "Left Thigh", parentId = "hips", localOffset = Vec3(-0.15f, -0.05f * h, 0f), length = 0.44f * h),
            BoneNode(id = "l_shin", name = "Left Shin", parentId = "l_thigh", localOffset = Vec3(0f, -0.44f * h, 0f), length = 0.43f * h),
            BoneNode(id = "r_thigh", name = "Right Thigh", parentId = "hips", localOffset = Vec3(0.15f, -0.05f * h, 0f), length = 0.44f * h),
            BoneNode(id = "r_shin", name = "Right Shin", parentId = "r_thigh", localOffset = Vec3(0f, -0.44f * h, 0f), length = 0.43f * h)
        )
        return SkeletonData(bones)
    }

    private fun resolveProfile(spec: CharacterBuildSpec): HumanoidStyleProfile {
        return when (spec.styleId) {
            "char_desi_hero" -> HumanoidStyleProfile(
                skin = 0xFFD99B6E, hair = 0xFF1C1614, shirt = spec.primaryColor, accent = spec.secondaryColor,
                pants = 0xFFF5EFE0, shoes = 0xFFB45309, belt = 0xFFF59E0B,
                hasMustache = true, hasLongTunic = true, hasSash = true
            )
            "char_desi_police" -> HumanoidStyleProfile(
                skin = 0xFFCC8D5E, hair = 0xFF1A1512, shirt = 0xFFA6814C, accent = 0xFFFACC15,
                pants = 0xFF8C6B3D, shoes = 0xFF2E1B0E, belt = 0xFF3E2212,
                hasMustache = true, hasPoliceBelt = true, shortSleeves = true
            )
            "char_western_ranger" -> HumanoidStyleProfile(
                skin = 0xFFEBC1A4, hair = 0xFF52331E, shirt = spec.primaryColor, accent = spec.secondaryColor,
                pants = 0xFF7B6B54, shoes = 0xFF3A2418, belt = 0xFF4A2E1B,
                hasJacketOpen = true
            )
            "char_indo_dancer" -> HumanoidStyleProfile(
                skin = 0xFFCC8C5A, hair = 0xFF1B1615, shirt = spec.primaryColor, accent = spec.secondaryColor,
                pants = 0xFF1E293B, shoes = 0xFFD97706, belt = 0xFFFACC15,
                hasChestArmor = true, hasSash = true, shortSleeves = true
            )
            "char_village_farmer" -> HumanoidStyleProfile(
                skin = 0xFFC28254, hair = 0xFFD1D5DB, shirt = spec.primaryColor, accent = 0xFFDC2626,
                pants = 0xFFFDF3D7, shoes = 0xFF5C3A21, belt = 0xFFB45309,
                hasMustache = true, hasBeard = true, hasLongTunic = true, hasSash = true, shortSleeves = true
            )
            "char_school_student" -> HumanoidStyleProfile(
                skin = 0xFFF1C6AA, hair = 0xFF2B1A14, shirt = spec.primaryColor, accent = 0xFFEF4444,
                pants = 0xFF1E3A8A, shoes = 0xFF1E2430, belt = 0xFF1E3A8A,
                isFemale = true, hasSkirt = true, hasTie = true
            )
            "char_doctor" -> HumanoidStyleProfile(
                skin = 0xFFF2C8AF, hair = 0xFF5C2D1B, shirt = 0xFFF1F5F9, accent = spec.secondaryColor,
                pants = 0xFF0284C7, shoes = 0xFFE2E8F0, belt = 0xFF0EA5E9,
                isFemale = true, hasLabCoat = true, hasStethoscope = true
            )
            "char_blender_mannequin" -> HumanoidStyleProfile(
                skin = 0xFF283044, hair = 0xFF22D3EE, shirt = spec.primaryColor, accent = spec.secondaryColor,
                pants = 0xFF1E2436, shoes = 0xFF312E81, belt = 0xFF22D3EE,
                eyeColor = 0xFF22D3EE, isMannequin = true, hasChestArmor = true
            )
            else -> when (spec.costume) {
                CostumePreset.INDIAN_TRADITIONAL -> HumanoidStyleProfile(
                    skin = 0xFFD99B6E, hair = 0xFF1E1815, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = 0xFFF5EFE0, shoes = 0xFFB45309, hasMustache = true, hasLongTunic = true, hasSash = true
                )
                CostumePreset.POLICE -> HumanoidStyleProfile(
                    skin = 0xFFCC8D5E, hair = 0xFF1A1512, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = darkenColor(spec.primaryColor, 0.82f), shoes = 0xFF2E1B0E, hasMustache = true, hasPoliceBelt = true, shortSleeves = true
                )
                CostumePreset.DOCTOR -> HumanoidStyleProfile(
                    skin = 0xFFF2C8AF, hair = 0xFF5A2D1C, shirt = 0xFFF1F5F9, accent = spec.secondaryColor,
                    pants = 0xFF0284C7, shoes = 0xFFE2E8F0, isFemale = true, hasLabCoat = true, hasStethoscope = true
                )
                CostumePreset.SCHOOL_UNIFORM -> HumanoidStyleProfile(
                    skin = 0xFFF1C6AA, hair = 0xFF2B1A14, shirt = spec.primaryColor, accent = 0xFFEF4444,
                    pants = 0xFF1E3A8A, shoes = 0xFF1E2430, isFemale = true, hasSkirt = true, hasTie = true
                )
                CostumePreset.VILLAGE -> HumanoidStyleProfile(
                    skin = 0xFFC28254, hair = 0xFF3B281E, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = 0xFFFDF3D7, shoes = 0xFF5C3A21, hasMustache = true, hasLongTunic = true, hasSash = true, shortSleeves = true
                )
                CostumePreset.MYTHOLOGICAL -> HumanoidStyleProfile(
                    skin = 0xFFCC8C5A, hair = 0xFF1B1615, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = 0xFF1E293B, shoes = 0xFFD97706, hasChestArmor = true, hasSash = true, shortSleeves = true
                )
                CostumePreset.FANTASY, CostumePreset.HISTORICAL -> HumanoidStyleProfile(
                    skin = 0xFFEAC0A2, hair = 0xFF3A2418, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = 0xFF282E3D, shoes = 0xFF3B2416, hasChestArmor = true
                )
                CostumePreset.FORMAL -> HumanoidStyleProfile(
                    skin = 0xFFEBC1A4, hair = 0xFF4A2E1B, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = 0xFF1F2937, shoes = 0xFF1E1B18, hasJacketOpen = true, hasTie = true
                )
                CostumePreset.ORIGINAL_HERO -> HumanoidStyleProfile(
                    skin = 0xFFE8BC9E, hair = 0xFF1E2230, shirt = spec.primaryColor, accent = spec.secondaryColor,
                    pants = 0xFF1E2436, shoes = 0xFF312E81, belt = spec.secondaryColor, hasChestArmor = true
                )
            }
        }
    }

    private fun buildHumanoid(spec: CharacterBuildSpec): SceneNode {
        val h = spec.heightScale.coerceIn(0.7f, 1.45f)
        val tw = spec.torsoWidth.coerceIn(0.75f, 1.35f)
        val hs = spec.headScale.coerceIn(0.75f, 1.35f)
        val lt = spec.limbThickness.coerceIn(0.75f, 1.35f)
        val skeleton = createDefaultHumanoidSkeleton(h)
        val p = resolveProfile(spec)

        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        fun addPart(mesh: MeshData, bone: Int, color: Long, pos: Vec3 = Vec3.ZERO, rot: Vec3 = Vec3.ZERO, scale: Vec3 = Vec3.ONE) {
            var m = mesh
            if (scale != Vec3.ONE) m = PrimitiveMeshFactory.scaleMesh(m, scale)
            if (rot != Vec3.ZERO) m = PrimitiveMeshFactory.rotateMesh(m, rot)
            if (pos != Vec3.ZERO) m = PrimitiveMeshFactory.translateMesh(m, pos)
            parts += Triple(m, bone, color)
        }

        // === 0: HIPS / PELVIS / BELT / TUNIC OR SKIRT ===
        val hipW = if (p.isFemale) 0.21f * tw else 0.22f * tw
        addPart(
            PrimitiveMeshFactory.createCylinder(hipW * 0.94f, hipW * 1.02f, 0.22f * h, 14),
            bone = 0, color = p.pants, pos = Vec3(0f, 0.92f * h, 0f), scale = Vec3(1.06f, 1f, 0.72f)
        )
        // Belt band + buckle
        addPart(
            PrimitiveMeshFactory.createCylinder(hipW * 1.02f, hipW * 1.02f, 0.065f * h, 14),
            bone = 0, color = p.belt, pos = Vec3(0f, 1.02f * h, 0f), scale = Vec3(1.06f, 1f, 0.74f)
        )
        addPart(
            PrimitiveMeshFactory.createCube(1f, 0.085f * tw, 0.068f * h, 0.05f),
            bone = 0, color = p.accent, pos = Vec3(0f, 1.02f * h, 0.155f * tw)
        )
        if (p.hasLongTunic) {
            // Flared Kurta / Sherwani skirt hem
            addPart(
                PrimitiveMeshFactory.createCylinder(hipW * 1.04f, hipW * 1.24f, 0.34f * h, 14),
                bone = 0, color = p.shirt, pos = Vec3(0f, 0.85f * h, 0f), scale = Vec3(1.08f, 1f, 0.78f)
            )
            // Gold bottom border trim
            addPart(
                PrimitiveMeshFactory.createCylinder(hipW * 1.24f, hipW * 1.26f, 0.045f * h, 14),
                bone = 0, color = p.accent, pos = Vec3(0f, 0.70f * h, 0f), scale = Vec3(1.08f, 1f, 0.79f)
            )
        } else if (p.hasSkirt) {
            addPart(
                PrimitiveMeshFactory.createCylinder(hipW * 1.02f, hipW * 1.34f, 0.34f * h, 16),
                bone = 0, color = p.pants, pos = Vec3(0f, 0.82f * h, 0f), scale = Vec3(1.05f, 1f, 0.86f)
            )
        }

        // === 1: SCULPTED TORSO / CHEST / COSTUME LAYERS ===
        val chestTop = (if (p.isFemale) 0.21f else 0.25f) * tw
        val chestBot = (if (p.isFemale) 0.16f else 0.19f) * tw
        addPart(
            PrimitiveMeshFactory.createCylinder(chestTop, chestBot, 0.48f * h, 14),
            bone = 1, color = p.shirt, pos = Vec3(0f, 1.30f * h, 0f), scale = Vec3(1.08f, 1f, 0.70f)
        )
        // Rounded shoulder yoke
        addPart(
            PrimitiveMeshFactory.createUvSphere(0.18f * tw, 12, 8),
            bone = 1, color = p.shirt, pos = Vec3(0f, 1.48f * h, 0f), scale = Vec3(1.62f, 0.56f, 0.82f)
        )
        // Collar ring
        addPart(
            PrimitiveMeshFactory.createCylinder(0.11f * tw, 0.13f * tw, 0.075f * h, 12),
            bone = 1, color = if (p.hasLabCoat || p.hasTie) 0xFFF8FAFC else p.accent,
            pos = Vec3(0f, 1.56f * h, 0f), scale = Vec3(1.0f, 1f, 0.92f)
        )

        // Costume-specific torso details
        if (p.hasLongTunic) {
            // Vertical front placket & ornamental buttons
            addPart(PrimitiveMeshFactory.createCube(1f, 0.06f * tw, 0.44f * h, 0.04f), 1, p.accent, Vec3(0f, 1.32f * h, 0.165f * tw))
            for (k in 0..2) {
                addPart(PrimitiveMeshFactory.createUvSphere(0.018f * tw, 8, 6), 1, 0xFFFDE68A, Vec3(0f, (1.44f - k * 0.10f) * h, 0.185f * tw))
            }
        }
        if (p.hasSash) {
            // Diagonal festive stole / dupatta / gamcha drape
            addPart(
                PrimitiveMeshFactory.createCube(1f, 0.11f * tw, 0.62f * h, 0.05f),
                bone = 1, color = p.accent, pos = Vec3(0.02f * tw, 1.31f * h, 0.155f * tw), rot = Vec3(0f, 0f, 34f)
            )
            addPart(
                PrimitiveMeshFactory.createCube(1f, 0.10f * tw, 0.46f * h, 0.05f),
                bone = 1, color = p.accent, pos = Vec3(-0.18f * tw, 1.18f * h, 0.12f * tw), rot = Vec3(0f, 0f, 4f)
            )
        }
        if (p.hasPoliceBelt) {
            // Sam Browne diagonal strap + chest pockets + gold star badge + shoulder epaulettes
            addPart(PrimitiveMeshFactory.createCube(1f, 0.055f * tw, 0.56f * h, 0.04f), 1, p.belt, Vec3(0f, 1.29f * h, 0.165f * tw), rot = Vec3(0f, 0f, -35f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.10f * tw, 0.10f * h, 0.03f), 1, darkenColor(p.shirt, 0.88f), Vec3(-0.11f * tw, 1.37f * h, 0.168f * tw))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.10f * tw, 0.10f * h, 0.03f), 1, darkenColor(p.shirt, 0.88f), Vec3(0.11f * tw, 1.37f * h, 0.168f * tw))
            addPart(PrimitiveMeshFactory.createUvSphere(0.026f * tw, 8, 6), 1, p.accent, Vec3(-0.11f * tw, 1.44f * h, 0.175f * tw))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.11f * tw, 0.03f * h, 0.07f), 1, 0xFFDC2626, Vec3(-0.22f * tw, 1.56f * h, 0f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.11f * tw, 0.03f * h, 0.07f), 1, 0xFFDC2626, Vec3(0.22f * tw, 1.56f * h, 0f))
        }
        if (p.hasJacketOpen) {
            // Inner contrasting shirt front panel + jacket lapels
            addPart(PrimitiveMeshFactory.createCube(1f, 0.15f * tw, 0.44f * h, 0.05f), 1, p.accent, Vec3(0f, 1.30f * h, 0.155f * tw))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.07f * tw, 0.28f * h, 0.04f), 1, lightenColor(p.shirt, 1.15f), Vec3(-0.10f * tw, 1.38f * h, 0.168f * tw), rot = Vec3(0f, 0f, 12f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.07f * tw, 0.28f * h, 0.04f), 1, lightenColor(p.shirt, 1.15f), Vec3(0.10f * tw, 1.38f * h, 0.168f * tw), rot = Vec3(0f, 0f, -12f))
        }
        if (p.hasLabCoat) {
            // Inner cyan scrubs V-panel + long coat flaps + badge
            addPart(PrimitiveMeshFactory.createCube(1f, 0.15f * tw, 0.44f * h, 0.05f), 1, p.accent, Vec3(0f, 1.30f * h, 0.15f * tw))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.14f * tw, 0.36f * h, 0.18f * tw), 0, 0xFFF1F5F9, Vec3(-0.12f * tw, 0.85f * h, 0.02f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.14f * tw, 0.36f * h, 0.18f * tw), 0, 0xFFF1F5F9, Vec3(0.12f * tw, 0.85f * h, 0.02f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.06f * tw, 0.08f * h, 0.02f), 1, 0xFF38BDF8, Vec3(-0.12f * tw, 1.38f * h, 0.175f * tw))
        }
        if (p.hasStethoscope) {
            // Draped stethoscope tube around neck + silver chestpiece
            addPart(PrimitiveMeshFactory.createTorus(0.12f * tw, 0.016f, 14, 8), 1, 0xFF1E293B, Vec3(0f, 1.50f * h, 0.04f), rot = Vec3(58f, 0f, 0f))
            addPart(PrimitiveMeshFactory.createCylinder(0.012f, 0.012f, 0.18f * h, 8), 1, 0xFF1E293B, Vec3(0.08f * tw, 1.36f * h, 0.175f * tw))
            addPart(PrimitiveMeshFactory.createCylinder(0.032f, 0.032f, 0.018f, 10), 1, 0xFFCBD5E1, Vec3(0.08f * tw, 1.26f * h, 0.182f * tw), rot = Vec3(90f, 0f, 0f))
        }
        if (p.hasTie) {
            addPart(PrimitiveMeshFactory.createCube(1f, 0.13f * tw, 0.42f * h, 0.045f), 1, 0xFFF8FAFC, Vec3(0f, 1.32f * h, 0.152f * tw))
            addPart(PrimitiveMeshFactory.createCylinder(0.022f, 0.045f, 0.26f * h, 6), 1, p.accent, Vec3(0f, 1.34f * h, 0.175f * tw))
        }
        if (p.hasChestArmor) {
            // Sculpted hero / royal chestplate + central emblem / core
            addPart(PrimitiveMeshFactory.createCube(1f, 0.36f * tw, 0.24f * h, 0.07f), 1, lightenColor(p.shirt, 1.12f), Vec3(0f, 1.39f * h, 0.145f * tw))
            addPart(PrimitiveMeshFactory.createUvSphere(0.055f * tw, 10, 8), 1, p.accent, Vec3(0f, 1.40f * h, 0.185f * tw), scale = Vec3(1.1f, 1.1f, 0.55f))
            // Shoulder pauldrons
            addPart(PrimitiveMeshFactory.createUvSphere(0.115f * lt, 10, 8), 3, p.accent, Vec3(-0.34f * tw, 1.50f * h, 0f), scale = Vec3(1.15f, 0.78f, 1.05f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.115f * lt, 10, 8), 5, p.accent, Vec3(0.34f * tw, 1.50f * h, 0f), scale = Vec3(1.15f, 0.78f, 1.05f))
        }

        // === 2: SCULPTED HEAD, FACE, EYES, BROWS, HAIR, FACIAL HAIR ===
        // Neck
        addPart(
            PrimitiveMeshFactory.createCylinder(0.068f * hs, 0.082f * hs, 0.14f * h, 10),
            bone = 2, color = p.skin, pos = Vec3(0f, 1.61f * h, 0f)
        )
        // Main cranium / face ellipsoid
        addPart(
            PrimitiveMeshFactory.createUvSphere(0.185f * hs, 16, 12),
            bone = 2, color = p.skin, pos = Vec3(0f, 1.81f * h, 0f), scale = Vec3(0.95f, 1.08f, 0.98f)
        )
        // Ears
        if (!p.isMannequin) {
            addPart(PrimitiveMeshFactory.createUvSphere(0.042f * hs, 8, 6), 2, p.skin, Vec3(-0.175f * hs, 1.80f * h, -0.01f * hs), scale = Vec3(0.45f, 1.1f, 0.75f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.042f * hs, 8, 6), 2, p.skin, Vec3(0.175f * hs, 1.80f * h, -0.01f * hs), scale = Vec3(0.45f, 1.1f, 0.75f))
        }

        if (p.isMannequin) {
            // Futuristic Studio Mannequin glowing visor + head crest
            addPart(PrimitiveMeshFactory.createCube(1f, 0.25f * hs, 0.072f * hs, 0.08f * hs), 2, 0xFF0F172A, Vec3(0f, 1.83f * h, 0.155f * hs))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.21f * hs, 0.036f * hs, 0.04f * hs), 2, 0xFF22D3EE, Vec3(0f, 1.83f * h, 0.182f * hs))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.045f * hs, 0.14f * hs, 0.28f * hs), 2, p.shirt, Vec3(0f, 1.98f * h, -0.01f * hs))
            addPart(PrimitiveMeshFactory.createCylinder(0.042f * hs, 0.042f * hs, 0.05f * hs, 10), 2, 0xFF22D3EE, Vec3(-0.18f * hs, 1.82f * h, 0f), rot = Vec3(0f, 0f, 90f))
            addPart(PrimitiveMeshFactory.createCylinder(0.042f * hs, 0.042f * hs, 0.05f * hs, 10), 2, 0xFF22D3EE, Vec3(0.18f * hs, 1.82f * h, 0f), rot = Vec3(0f, 0f, 90f))
        } else {
            // Expressive eyes (white sclera + dark iris/pupil)
            addPart(PrimitiveMeshFactory.createUvSphere(0.030f * hs, 8, 6), 2, 0xFFF8FAFC, Vec3(-0.062f * hs, 1.83f * h, 0.155f * hs), scale = Vec3(1.05f, 0.88f, 0.55f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.030f * hs, 8, 6), 2, 0xFFF8FAFC, Vec3(0.062f * hs, 1.83f * h, 0.155f * hs), scale = Vec3(1.05f, 0.88f, 0.55f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.017f * hs, 8, 6), 2, p.eyeColor, Vec3(-0.062f * hs, 1.83f * h, 0.172f * hs), scale = Vec3(1f, 1f, 0.6f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.017f * hs, 8, 6), 2, p.eyeColor, Vec3(0.062f * hs, 1.83f * h, 0.172f * hs), scale = Vec3(1f, 1f, 0.6f))
            // Eyebrows
            addPart(PrimitiveMeshFactory.createCube(1f, 0.062f * hs, 0.016f * hs, 0.025f * hs), 2, p.hair, Vec3(-0.064f * hs, 1.875f * h, 0.165f * hs), rot = Vec3(0f, 0f, 6f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.062f * hs, 0.016f * hs, 0.025f * hs), 2, p.hair, Vec3(0.064f * hs, 1.875f * h, 0.165f * hs), rot = Vec3(0f, 0f, -6f))
            // Sculpted nose bridge
            addPart(PrimitiveMeshFactory.createUvSphere(0.024f * hs, 8, 6), 2, darkenColor(p.skin, 0.93f), Vec3(0f, 1.79f * h, 0.178f * hs), scale = Vec3(0.85f, 1.25f, 1.15f))
            // Warm mouth / lips
            addPart(
                PrimitiveMeshFactory.createCube(1f, 0.068f * hs, 0.015f * hs, 0.022f * hs),
                bone = 2,
                color = if (p.isFemale) 0xFFD96B78 else darkenColor(p.skin, 0.72f),
                pos = Vec3(0f, 1.725f * h, 0.162f * hs)
            )
            // Mustache & Beard
            if (p.hasMustache) {
                addPart(PrimitiveMeshFactory.createCube(1f, 0.056f * hs, 0.022f * hs, 0.028f * hs), 2, p.hair, Vec3(-0.030f * hs, 1.752f * h, 0.174f * hs), rot = Vec3(0f, 0f, 12f))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.056f * hs, 0.022f * hs, 0.028f * hs), 2, p.hair, Vec3(0.030f * hs, 1.752f * h, 0.174f * hs), rot = Vec3(0f, 0f, -12f))
            }
            if (p.hasBeard) {
                addPart(PrimitiveMeshFactory.createUvSphere(0.115f * hs, 10, 8), 2, p.hair, Vec3(0f, 1.69f * h, 0.095f * hs), scale = Vec3(1.1f, 0.85f, 0.82f))
            }

            // Sculpted Hairstyles
            if (p.isFemale) {
                // Full sculpted hair cap + side locks + back bun/ponytail
                addPart(PrimitiveMeshFactory.createUvSphere(0.196f * hs, 14, 10), 2, p.hair, Vec3(0f, 1.855f * h, -0.022f * hs), scale = Vec3(1.02f, 1.02f, 1.02f))
                addPart(PrimitiveMeshFactory.createCapsule(0.055f * hs, 0.18f * h, 8), 2, p.hair, Vec3(-0.155f * hs, 1.76f * h, -0.02f * hs))
                addPart(PrimitiveMeshFactory.createCapsule(0.055f * hs, 0.18f * h, 8), 2, p.hair, Vec3(0.155f * hs, 1.76f * h, -0.02f * hs))
                addPart(PrimitiveMeshFactory.createUvSphere(0.095f * hs, 10, 8), 2, p.hair, Vec3(0f, 1.82f * h, -0.195f * hs))
                // Swept bangs
                addPart(PrimitiveMeshFactory.createCube(1f, 0.20f * hs, 0.065f * hs, 0.09f * hs), 2, p.hair, Vec3(-0.02f * hs, 1.935f * h, 0.115f * hs), rot = Vec3(0f, 0f, 8f))
            } else {
                // Layered masculine hair cap + sculpted top volume
                addPart(PrimitiveMeshFactory.createUvSphere(0.192f * hs, 14, 10), 2, p.hair, Vec3(0f, 1.86f * h, -0.028f * hs), scale = Vec3(1.0f, 0.96f, 0.98f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.145f * hs, 10, 8), 2, p.hair, Vec3(0.02f * hs, 1.94f * h, 0.03f * hs), scale = Vec3(1.15f, 0.56f, 1.05f))
            }
        }

        // === 3..6: ARMS, JOINT ACCENTS, HANDS ===
        val upperSleeveCol = p.shirt
        val forearmCol = if (p.shortSleeves) p.skin else if (p.isMannequin) 0xFF1E293B else p.shirt
        val handCol = if (p.isMannequin) p.accent else p.skin

        // Left Arm (3 = upper arm, 4 = forearm)
        addPart(PrimitiveMeshFactory.createUvSphere(0.082f * lt, 10, 8), 3, upperSleeveCol, Vec3(-0.33f * tw, 1.48f * h, 0f))
        addPart(PrimitiveMeshFactory.createCapsule(0.072f * lt, 0.24f * h, 10), 3, upperSleeveCol, Vec3(-0.34f * tw, 1.32f * h, 0f))
        addPart(PrimitiveMeshFactory.createCapsule(0.060f * lt, 0.24f * h, 10), 4, forearmCol, Vec3(-0.34f * tw, 0.99f * h, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.056f * lt, 8, 6), 4, handCol, Vec3(-0.34f * tw, 0.81f * h, 0.015f), scale = Vec3(0.85f, 1.15f, 0.95f))

        // Right Arm (5 = upper arm, 6 = forearm)
        addPart(PrimitiveMeshFactory.createUvSphere(0.082f * lt, 10, 8), 5, upperSleeveCol, Vec3(0.33f * tw, 1.48f * h, 0f))
        addPart(PrimitiveMeshFactory.createCapsule(0.072f * lt, 0.24f * h, 10), 5, upperSleeveCol, Vec3(0.34f * tw, 1.32f * h, 0f))
        addPart(PrimitiveMeshFactory.createCapsule(0.060f * lt, 0.24f * h, 10), 6, forearmCol, Vec3(0.34f * tw, 0.99f * h, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.056f * lt, 8, 6), 6, handCol, Vec3(0.34f * tw, 0.81f * h, 0.015f), scale = Vec3(0.85f, 1.15f, 0.95f))

        if (p.isMannequin) {
            // Glowing cyan elbow & wrist rings
            addPart(PrimitiveMeshFactory.createTorus(0.068f * lt, 0.016f, 12, 8), 4, 0xFF22D3EE, Vec3(-0.34f * tw, 1.15f * h, 0f))
            addPart(PrimitiveMeshFactory.createTorus(0.068f * lt, 0.016f, 12, 8), 6, 0xFF22D3EE, Vec3(0.34f * tw, 1.15f * h, 0f))
        } else if (p.hasChestArmor && !p.isMannequin) {
            // Royal wrist cuffs / armlets
            addPart(PrimitiveMeshFactory.createCylinder(0.066f * lt, 0.066f * lt, 0.065f * h, 10), 4, p.accent, Vec3(-0.34f * tw, 0.90f * h, 0f))
            addPart(PrimitiveMeshFactory.createCylinder(0.066f * lt, 0.066f * lt, 0.065f * h, 10), 6, p.accent, Vec3(0.34f * tw, 0.90f * h, 0f))
        }

        // === 7..10: LEGS, KNEES, SCULPTED SHOES/BOOTS ===
        val thighCol = if (p.hasSkirt) p.skin else p.pants
        val shinCol = if (p.hasSkirt) 0xFFF8FAFC else p.pants
        // Left Leg
        addPart(PrimitiveMeshFactory.createCapsule(0.092f * lt, 0.30f * h, 10), 7, thighCol, Vec3(-0.145f * tw, 0.64f * h, 0f))
        addPart(PrimitiveMeshFactory.createCapsule(0.075f * lt, 0.32f * h, 10), 8, shinCol, Vec3(-0.145f * tw, 0.25f * h, 0f))
        // Right Leg
        addPart(PrimitiveMeshFactory.createCapsule(0.092f * lt, 0.30f * h, 10), 9, thighCol, Vec3(0.145f * tw, 0.64f * h, 0f))
        addPart(PrimitiveMeshFactory.createCapsule(0.075f * lt, 0.32f * h, 10), 10, shinCol, Vec3(0.145f * tw, 0.25f * h, 0f))

        if (p.isMannequin) {
            addPart(PrimitiveMeshFactory.createTorus(0.084f * lt, 0.018f, 12, 8), 8, 0xFF22D3EE, Vec3(-0.145f * tw, 0.44f * h, 0f))
            addPart(PrimitiveMeshFactory.createTorus(0.084f * lt, 0.018f, 12, 8), 10, 0xFF22D3EE, Vec3(0.145f * tw, 0.44f * h, 0f))
        }

        // Sculpted Shoes / Boots (upper + rounded toe + dark sole)
        for ((xSign, bone) in listOf(-1f to 8, 1f to 10)) {
            val fx = xSign * 0.145f * tw
            addPart(PrimitiveMeshFactory.createCube(1f, 0.135f * lt, 0.085f * h, 0.20f * lt), bone, p.shoes, Vec3(fx, 0.055f * h, 0.035f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.068f * lt, 8, 6), bone, p.shoes, Vec3(fx, 0.048f * h, 0.135f * lt), scale = Vec3(1.0f, 0.68f, 1.1f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.142f * lt, 0.028f * h, 0.255f * lt), bone, 0xFF11141C, Vec3(fx, 0.015f * h, 0.055f))
        }

        // === ACCESSORIES ATTACHED TO REAL SKELETON BONES ===
        when (spec.accessory) {
            AccessoryKind.TURBAN -> {
                // Multi-layered festive Pagdi turban with golden band & Kalgi jewel crest
                addPart(PrimitiveMeshFactory.createTorus(0.165f * hs, 0.072f * hs, 16, 10), 2, p.shirt, Vec3(0f, 1.94f * h, -0.01f), rot = Vec3(-8f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createTorus(0.145f * hs, 0.055f * hs, 14, 8), 2, p.accent, Vec3(0f, 1.985f * h, -0.01f), rot = Vec3(10f, 0f, 14f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.155f * hs, 12, 8), 2, p.shirt, Vec3(0f, 2.00f * h, -0.01f), scale = Vec3(1.04f, 0.72f, 1.04f))
                // Golden jewel brooch & feather crest
                addPart(PrimitiveMeshFactory.createCylinder(0.012f, 0.045f * hs, 0.14f * h, 8), 2, 0xFFFACC15, Vec3(0f, 2.09f * h, 0.13f * hs), rot = Vec3(15f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.034f * hs, 8, 6), 2, 0xFF22D3EE, Vec3(0f, 2.01f * h, 0.165f * hs))
            }
            AccessoryKind.CAP -> {
                // Peaked uniform cap with dark glossy brim, crimson trim, gold badge
                addPart(PrimitiveMeshFactory.createCylinder(0.20f * hs, 0.18f * hs, 0.11f * h, 14), 2, p.shirt, Vec3(0f, 1.97f * h, -0.01f))
                addPart(PrimitiveMeshFactory.createCylinder(0.185f * hs, 0.185f * hs, 0.035f * h, 14), 2, 0xFFB91C1C, Vec3(0f, 1.925f * h, -0.01f))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.27f * hs, 0.025f * h, 0.17f * hs), 2, 0xFF181A22, Vec3(0f, 1.91f * h, 0.155f * hs), rot = Vec3(10f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.032f * hs, 8, 6), 2, 0xFFFACC15, Vec3(0f, 1.975f * h, 0.185f * hs))
                if (p.hasPoliceBelt) {
                    // Classic dark aviator sunglasses for Inspector Vikram
                    addPart(PrimitiveMeshFactory.createCube(1f, 0.082f * hs, 0.048f * hs, 0.025f * hs), 2, 0xFF0F172A, Vec3(-0.062f * hs, 1.83f * h, 0.184f * hs))
                    addPart(PrimitiveMeshFactory.createCube(1f, 0.082f * hs, 0.048f * hs, 0.025f * hs), 2, 0xFF0F172A, Vec3(0.062f * hs, 1.83f * h, 0.184f * hs))
                    addPart(PrimitiveMeshFactory.createCube(1f, 0.22f * hs, 0.012f * hs, 0.02f * hs), 2, 0xFFFACC15, Vec3(0f, 1.852f * h, 0.185f * hs))
                }
            }
            AccessoryKind.CROWN -> {
                // Ornate multi-tiered golden royal crown + cyan centerpiece + side wings
                addPart(PrimitiveMeshFactory.createCylinder(0.205f * hs, 0.185f * hs, 0.11f * h, 12), 2, 0xFFF59E0B, Vec3(0f, 1.97f * h, 0f))
                addPart(PrimitiveMeshFactory.createCylinder(0.145f * hs, 0.19f * hs, 0.11f * h, 10), 2, 0xFFD97706, Vec3(0f, 2.07f * h, 0f))
                addPart(PrimitiveMeshFactory.createCylinder(0.015f, 0.09f * hs, 0.14f * h, 8), 2, 0xFFFACC15, Vec3(0f, 2.18f * h, 0f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.042f * hs, 8, 6), 2, p.accent, Vec3(0f, 1.98f * h, 0.195f * hs))
                // Sculpted golden ear wings
                addPart(PrimitiveMeshFactory.createCube(1f, 0.10f * hs, 0.15f * h, 0.03f), 2, 0xFFFACC15, Vec3(-0.21f * hs, 1.94f * h, -0.02f), rot = Vec3(0f, 0f, 24f))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.10f * hs, 0.15f * h, 0.03f), 2, 0xFFFACC15, Vec3(0.21f * hs, 1.94f * h, -0.02f), rot = Vec3(0f, 0f, -24f))
            }
            AccessoryKind.CAPE -> {
                // Sweeping hero cape with glowing trim & shoulder clasps
                addPart(PrimitiveMeshFactory.createCube(1f, 0.56f * tw, 0.94f * h, 0.04f), 1, darkenColor(p.shirt, 0.78f), Vec3(0f, 1.08f * h, -0.20f * tw), rot = Vec3(8f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.58f * tw, 0.06f * h, 0.045f), 1, p.accent, Vec3(0f, 0.62f * h, -0.27f * tw), rot = Vec3(8f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.038f * tw, 8, 6), 1, p.accent, Vec3(-0.18f * tw, 1.52f * h, 0.12f * tw))
                addPart(PrimitiveMeshFactory.createUvSphere(0.038f * tw, 8, 6), 1, p.accent, Vec3(0.18f * tw, 1.52f * h, 0.12f * tw))
            }
            AccessoryKind.BACKPACK -> {
                // Detailed backpack with front pocket & shoulder straps
                addPart(PrimitiveMeshFactory.createCube(1f, 0.34f * tw, 0.42f * h, 0.18f), 1, p.accent, Vec3(0f, 1.32f * h, -0.21f * tw))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.26f * tw, 0.18f * h, 0.08f), 1, lightenColor(p.accent, 1.18f), Vec3(0f, 1.24f * h, -0.31f * tw))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.045f * tw, 0.38f * h, 0.24f * tw), 1, 0xFF1E293B, Vec3(-0.15f * tw, 1.38f * h, -0.02f))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.045f * tw, 0.38f * h, 0.24f * tw), 1, 0xFF1E293B, Vec3(0.15f * tw, 1.38f * h, -0.02f))
            }
            AccessoryKind.STAFF -> {
                // Carved wooden staff with brass bands & glowing amber finial
                addPart(PrimitiveMeshFactory.createCylinder(0.028f, 0.032f, 1.72f * h, 10), 6, 0xFF6B3E1E, Vec3(0.41f * tw, 0.88f * h, 0.12f))
                addPart(PrimitiveMeshFactory.createCylinder(0.038f, 0.038f, 0.08f * h, 10), 6, 0xFFF59E0B, Vec3(0.41f * tw, 1.66f * h, 0.12f))
                addPart(PrimitiveMeshFactory.createUvSphere(0.075f, 10, 8), 6, 0xFFFACC15, Vec3(0.41f * tw, 1.78f * h, 0.12f))
            }
            AccessoryKind.GLASSES -> {
                // Sleek dual-lens glasses with bridge
                addPart(PrimitiveMeshFactory.createTorus(0.040f * hs, 0.008f * hs, 12, 6), 2, 0xFF1E293B, Vec3(-0.062f * hs, 1.83f * h, 0.185f * hs), rot = Vec3(90f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createTorus(0.040f * hs, 0.008f * hs, 12, 6), 2, 0xFF1E293B, Vec3(0.062f * hs, 1.83f * h, 0.185f * hs), rot = Vec3(90f, 0f, 0f))
                addPart(PrimitiveMeshFactory.createCube(1f, 0.042f * hs, 0.010f * hs, 0.014f * hs), 2, 0xFF1E293B, Vec3(0f, 1.835f * h, 0.186f * hs))
            }
            AccessoryKind.NONE -> Unit
        }

        val mesh = PrimitiveMeshFactory.mergeColoredMeshes(parts)
        return SceneNode(
            name = spec.name,
            nodeType = NodeType.CHARACTER,
            primitiveKind = PrimitiveKind.CUSTOM,
            mesh = mesh,
            material = MaterialData(
                name = "${spec.costume.label} Mat",
                baseColor = spec.primaryColor,
                roughness = if (p.isMannequin) 0.28f else spec.roughness,
                metallic = if (p.isMannequin) 0.55f else spec.metallic
            ),
            skeleton = skeleton,
            categoryTag = spec.category.name
        )
    }

    private fun buildBird(spec: CharacterBuildSpec): SceneNode {
        val skeleton = SkeletonData(
            listOf(
                BoneNode("hips", "Body", null, Vec3(0f, 0.72f, 0f), 0.35f),
                BoneNode("head", "Head", "hips", Vec3(0f, 0.24f, 0.38f), 0.26f),
                BoneNode("l_upper_arm", "Left Wing", "hips", Vec3(-0.22f, 0.10f, 0f), 0.65f),
                BoneNode("r_upper_arm", "Right Wing", "hips", Vec3(0.22f, 0.10f, 0f), 0.65f)
            )
        )
        val featherMain = spec.primaryColor
        val featherDark = darkenColor(featherMain, 0.58f)
        val beakGold = spec.secondaryColor
        val headWhite = 0xFFF8FAFC

        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        fun addPart(mesh: MeshData, bone: Int, color: Long, pos: Vec3, rot: Vec3 = Vec3.ZERO, scale: Vec3 = Vec3.ONE) {
            var m = mesh
            if (scale != Vec3.ONE) m = PrimitiveMeshFactory.scaleMesh(m, scale)
            if (rot != Vec3.ZERO) m = PrimitiveMeshFactory.rotateMesh(m, rot)
            m = PrimitiveMeshFactory.translateMesh(m, pos)
            parts += Triple(m, bone, color)
        }

        // Sculpted eagle torso & breast
        addPart(PrimitiveMeshFactory.createUvSphere(0.29f, 14, 10), 0, featherMain, Vec3(0f, 0.72f, 0f), rot = Vec3(-12f, 0f, 0f), scale = Vec3(0.92f, 0.88f, 1.48f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.22f, 12, 8), 0, featherDark, Vec3(0f, 0.68f, 0.14f), scale = Vec3(0.88f, 0.80f, 0.95f))

        // White feathered neck ruff & eagle head
        addPart(PrimitiveMeshFactory.createCylinder(0.14f, 0.21f, 0.22f, 12), 1, headWhite, Vec3(0f, 0.88f, 0.28f), rot = Vec3(22f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.165f, 14, 10), 1, headWhite, Vec3(0f, 1.02f, 0.36f), scale = Vec3(0.92f, 0.96f, 1.12f))
        // Fierce eyes & brow ridge
        addPart(PrimitiveMeshFactory.createUvSphere(0.028f, 8, 6), 1, 0xFFFACC15, Vec3(-0.11f, 1.04f, 0.44f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.028f, 8, 6), 1, 0xFFFACC15, Vec3(0.11f, 1.04f, 0.44f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.016f, 6, 6), 1, 0xFF0F172A, Vec3(-0.12f, 1.04f, 0.46f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.016f, 6, 6), 1, 0xFF0F172A, Vec3(0.12f, 1.04f, 0.46f))
        // Hooked golden eagle beak
        addPart(PrimitiveMeshFactory.createCylinder(0.015f, 0.068f, 0.20f, 10), 1, beakGold, Vec3(0f, 1.00f, 0.56f), rot = Vec3(98f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.008f, 0.036f, 0.10f, 8), 1, beakGold, Vec3(0f, 0.95f, 0.65f), rot = Vec3(145f, 0f, 0f))

        // Swept layered feathered wings (left = 2, right = 3)
        for ((sign, bone) in listOf(-1f to 2, 1f to 3)) {
            // Inner wing coverts
            addPart(PrimitiveMeshFactory.createCube(1f, 0.56f, 0.075f, 0.38f), bone, featherMain, Vec3(sign * 0.44f, 0.84f, 0.02f), rot = Vec3(6f, sign * -12f, sign * 18f))
            // Outer sweeping wing section
            addPart(PrimitiveMeshFactory.createCube(1f, 0.52f, 0.055f, 0.30f), bone, featherDark, Vec3(sign * 0.88f, 0.94f, -0.06f), rot = Vec3(8f, sign * -22f, sign * 10f))
            // Fanned primary wingtip feathers
            for (f in 0..2) {
                addPart(
                    PrimitiveMeshFactory.createCube(1f, 0.28f, 0.032f, 0.08f),
                    bone, if (f == 1) beakGold else featherDark,
                    Vec3(sign * (1.16f + f * 0.04f), 0.96f - f * 0.03f, -0.02f - f * 0.09f),
                    rot = Vec3(0f, sign * (-28f - f * 10f), sign * 6f)
                )
            }
        }

        // Fanned white-tipped tail feathers
        for (t in -2..2) {
            addPart(
                PrimitiveMeshFactory.createCube(1f, 0.10f, 0.035f, 0.42f),
                0, if (t == 0) headWhite else featherMain,
                Vec3(t * 0.075f, 0.68f, -0.52f), rot = Vec3(-8f, t * 11f, 0f)
            )
        }

        // Golden legs & talons
        for (sign in listOf(-1f, 1f)) {
            addPart(PrimitiveMeshFactory.createCylinder(0.036f, 0.028f, 0.24f, 8), 0, beakGold, Vec3(sign * 0.11f, 0.40f, 0.02f))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.09f, 0.035f, 0.14f), 0, beakGold, Vec3(sign * 0.11f, 0.27f, 0.06f))
        }

        return SceneNode(
            name = spec.name,
            nodeType = NodeType.CHARACTER,
            mesh = PrimitiveMeshFactory.mergeColoredMeshes(parts),
            material = MaterialData(name = "${spec.name} Feather", baseColor = spec.primaryColor, roughness = 0.55f),
            skeleton = skeleton,
            categoryTag = spec.category.name
        )
    }

    private fun buildQuadruped(spec: CharacterBuildSpec): SceneNode {
        val skeleton = SkeletonData(
            listOf(
                BoneNode("hips", "Torso", null, Vec3(0f, 0.68f, 0f), 0.45f),
                BoneNode("head", "Head", "hips", Vec3(0f, 0.24f, 0.55f), 0.30f),
                BoneNode("l_thigh", "Front Left Leg", "hips", Vec3(-0.18f, -0.08f, 0.34f), 0.56f),
                BoneNode("r_thigh", "Front Right Leg", "hips", Vec3(0.18f, -0.08f, 0.34f), 0.56f),
                BoneNode("l_shin", "Back Left Leg", "hips", Vec3(-0.18f, -0.08f, -0.34f), 0.56f),
                BoneNode("r_shin", "Back Right Leg", "hips", Vec3(0.18f, -0.08f, -0.34f), 0.56f)
            )
        )
        val coatMain = spec.primaryColor
        val coatLight = spec.secondaryColor
        val coatDark = darkenColor(coatMain, 0.60f)

        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        fun addPart(mesh: MeshData, bone: Int, color: Long, pos: Vec3, rot: Vec3 = Vec3.ZERO, scale: Vec3 = Vec3.ONE) {
            var m = mesh
            if (scale != Vec3.ONE) m = PrimitiveMeshFactory.scaleMesh(m, scale)
            if (rot != Vec3.ZERO) m = PrimitiveMeshFactory.rotateMesh(m, rot)
            m = PrimitiveMeshFactory.translateMesh(m, pos)
            parts += Triple(m, bone, color)
        }

        // Muscular wolf ribcage, spine & hindquarters
        addPart(PrimitiveMeshFactory.createCapsule(0.21f, 0.56f, 12), 0, coatMain, Vec3(0f, 0.72f, 0f), rot = Vec3(90f, 0f, 0f), scale = Vec3(1.0f, 0.96f, 1.0f))
        // Fluffy lighter chest ruff & belly
        addPart(PrimitiveMeshFactory.createUvSphere(0.24f, 12, 8), 0, coatLight, Vec3(0f, 0.74f, 0.32f), scale = Vec3(1.02f, 1.04f, 0.92f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.16f, 10, 8), 0, coatLight, Vec3(0f, 0.59f, 0.02f), scale = Vec3(0.95f, 0.60f, 2.2f))

        // Sculpted wolf neck & head
        addPart(PrimitiveMeshFactory.createCylinder(0.14f, 0.19f, 0.24f, 10), 1, coatMain, Vec3(0f, 0.88f, 0.44f), rot = Vec3(26f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.175f, 14, 10), 1, coatMain, Vec3(0f, 0.99f, 0.56f), scale = Vec3(0.95f, 0.92f, 1.10f))
        // Cheek fur tufts
        addPart(PrimitiveMeshFactory.createUvSphere(0.095f, 8, 6), 1, coatLight, Vec3(-0.12f, 0.94f, 0.55f), scale = Vec3(0.85f, 0.75f, 1.0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.095f, 8, 6), 1, coatLight, Vec3(0.12f, 0.94f, 0.55f), scale = Vec3(0.85f, 0.75f, 1.0f))
        // Elongated muzzle + lower jaw + black nose
        addPart(PrimitiveMeshFactory.createCylinder(0.065f, 0.105f, 0.24f, 10), 1, coatLight, Vec3(0f, 0.95f, 0.76f), rot = Vec3(92f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.044f, 8, 6), 1, 0xFF0F172A, Vec3(0f, 0.97f, 0.88f))
        // Glowing amber wolf eyes
        addPart(PrimitiveMeshFactory.createUvSphere(0.028f, 8, 6), 1, 0xFFF59E0B, Vec3(-0.088f, 1.03f, 0.68f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.028f, 8, 6), 1, 0xFFF59E0B, Vec3(0.088f, 1.03f, 0.68f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.015f, 6, 6), 1, 0xFF0F172A, Vec3(-0.092f, 1.03f, 0.70f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.015f, 6, 6), 1, 0xFF0F172A, Vec3(0.092f, 1.03f, 0.70f))
        // Upright pointed ears
        for (sign in listOf(-1f, 1f)) {
            addPart(PrimitiveMeshFactory.createCylinder(0.01f, 0.065f, 0.19f, 8), 1, coatDark, Vec3(sign * 0.11f, 1.18f, 0.52f), rot = Vec3(-8f, 0f, sign * -12f))
            addPart(PrimitiveMeshFactory.createCylinder(0.008f, 0.038f, 0.12f, 6), 1, coatLight, Vec3(sign * 0.11f, 1.16f, 0.54f), rot = Vec3(-8f, 0f, sign * -12f))
        }

        // Four sculpted legs + paws
        val legSpecs = listOf(
            Triple(-0.16f, 0.34f, 2),
            Triple(0.16f, 0.34f, 3),
            Triple(-0.16f, -0.34f, 4),
            Triple(0.16f, -0.34f, 5)
        )
        for ((lx, lz, bone) in legSpecs) {
            addPart(PrimitiveMeshFactory.createCapsule(0.068f, 0.24f, 10), bone, coatMain, Vec3(lx, 0.48f, lz))
            addPart(PrimitiveMeshFactory.createCapsule(0.052f, 0.26f, 10), bone, coatLight, Vec3(lx, 0.20f, lz))
            addPart(PrimitiveMeshFactory.createUvSphere(0.064f, 8, 6), bone, coatLight, Vec3(lx, 0.045f, lz + 0.045f), scale = Vec3(0.95f, 0.65f, 1.35f))
        }

        // Bushy curved wolf tail with dark tip
        addPart(PrimitiveMeshFactory.createCapsule(0.075f, 0.30f, 10), 0, coatMain, Vec3(0f, 0.74f, -0.60f), rot = Vec3(58f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.02f, 0.078f, 0.20f, 10), 0, coatDark, Vec3(0f, 0.62f, -0.78f), rot = Vec3(122f, 0f, 0f))

        return SceneNode(
            name = spec.name,
            nodeType = NodeType.CHARACTER,
            mesh = PrimitiveMeshFactory.mergeColoredMeshes(parts),
            material = MaterialData(name = "${spec.name} Fur", baseColor = spec.primaryColor, roughness = 0.7f),
            skeleton = skeleton,
            categoryTag = spec.category.name
        )
    }

    private fun buildVehicle(spec: CharacterBuildSpec): SceneNode {
        val skeleton = SkeletonData(
            listOf(
                BoneNode("hips", "Chassis", null, Vec3(0f, 0.50f, 0f), 0.5f),
                BoneNode("l_thigh", "Front Wheels", "hips", Vec3(0f, -0.22f, 0.64f), 0.3f),
                BoneNode("r_thigh", "Rear Wheels", "hips", Vec3(0f, -0.22f, -0.64f), 0.3f)
            )
        )
        val bodyPaint = spec.primaryColor
        val darkTrim = spec.secondaryColor
        val glassCyan = 0xFF38BDF8
        val alloySilver = 0xFFCBD5E1
        val tireBlack = 0xFF111827

        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        fun addPart(mesh: MeshData, bone: Int, color: Long, pos: Vec3, rot: Vec3 = Vec3.ZERO, scale: Vec3 = Vec3.ONE) {
            var m = mesh
            if (scale != Vec3.ONE) m = PrimitiveMeshFactory.scaleMesh(m, scale)
            if (rot != Vec3.ZERO) m = PrimitiveMeshFactory.rotateMesh(m, rot)
            m = PrimitiveMeshFactory.translateMesh(m, pos)
            parts += Triple(m, bone, color)
        }

        // Main lower body tub & raised hood
        addPart(PrimitiveMeshFactory.createCube(1f, 1.12f, 0.42f, 1.98f), 0, bodyPaint, Vec3(0f, 0.56f, 0f))
        addPart(PrimitiveMeshFactory.createCube(1f, 0.96f, 0.14f, 0.78f), 0, lightenColor(bodyPaint, 1.08f), Vec3(0f, 0.80f, 0.55f))
        // Heavy bumpers, front bull bar & grille
        addPart(PrimitiveMeshFactory.createCube(1f, 1.24f, 0.15f, 0.20f), 0, darkTrim, Vec3(0f, 0.40f, 1.04f))
        addPart(PrimitiveMeshFactory.createCube(1f, 1.22f, 0.14f, 0.16f), 0, darkTrim, Vec3(0f, 0.40f, -1.03f))
        addPart(PrimitiveMeshFactory.createCube(1f, 0.72f, 0.26f, 0.06f), 0, 0xFF111827, Vec3(0f, 0.64f, 0.99f))
        // Glowing warm headlights & red taillights
        addPart(PrimitiveMeshFactory.createCylinder(0.08f, 0.08f, 0.06f, 12), 0, 0xFFFEF08A, Vec3(-0.38f, 0.66f, 1.00f), rot = Vec3(90f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.08f, 0.08f, 0.06f, 12), 0, 0xFFFEF08A, Vec3(0.38f, 0.66f, 1.00f), rot = Vec3(90f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createCube(1f, 0.18f, 0.10f, 0.05f), 0, 0xFFEF4444, Vec3(-0.40f, 0.62f, -1.00f))
        addPart(PrimitiveMeshFactory.createCube(1f, 0.18f, 0.10f, 0.05f), 0, 0xFFEF4444, Vec3(0.40f, 0.62f, -1.00f))

        // Cabin frame, roof rack & tinted cyan windshield
        addPart(PrimitiveMeshFactory.createCube(1f, 0.98f, 0.08f, 1.02f), 0, darkTrim, Vec3(0f, 1.24f, -0.26f))
        addPart(PrimitiveMeshFactory.createCube(1f, 0.90f, 0.36f, 0.07f), 0, glassCyan, Vec3(0f, 1.00f, 0.24f), rot = Vec3(-18f, 0f, 0f))
        // Roll-cage pillars
        for ((px, pz) in listOf(-0.46f to 0.20f, 0.46f to 0.20f, -0.46f to -0.72f, 0.46f to -0.72f)) {
            addPart(PrimitiveMeshFactory.createCylinder(0.036f, 0.036f, 0.48f, 8), 0, darkTrim, Vec3(px, 1.00f, pz))
        }
        // Interior seats
        addPart(PrimitiveMeshFactory.createCube(1f, 0.34f, 0.32f, 0.24f), 0, 0xFF334155, Vec3(-0.24f, 0.88f, -0.18f))
        addPart(PrimitiveMeshFactory.createCube(1f, 0.34f, 0.32f, 0.24f), 0, 0xFF334155, Vec3(0.24f, 0.88f, -0.18f))

        // Wheel fender flares
        for ((fx, fz) in listOf(-0.56f to 0.64f, 0.56f to 0.64f, -0.56f to -0.64f, 0.56f to -0.64f)) {
            addPart(PrimitiveMeshFactory.createCube(1f, 0.18f, 0.10f, 0.58f), 0, darkTrim, Vec3(fx, 0.64f, fz))
        }

        // 4 chunky offroad tires with silver alloy hubs
        val tire = PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createTorus(0.20f, 0.095f, 16, 10), Vec3(0f, 0f, 90f))
        val rim = PrimitiveMeshFactory.rotateMesh(PrimitiveMeshFactory.createCylinder(0.14f, 0.14f, 0.17f, 12), Vec3(0f, 0f, 90f))
        val wheelSpots = listOf(
            Triple(-0.58f, 0.64f, 1), Triple(0.58f, 0.64f, 1),
            Triple(-0.58f, -0.64f, 2), Triple(0.58f, -0.64f, 2)
        )
        for ((wx, wz, bone) in wheelSpots) {
            addPart(tire, bone, tireBlack, Vec3(wx, 0.29f, wz))
            addPart(rim, bone, alloySilver, Vec3(wx, 0.29f, wz))
        }
        // Rear mounted spare tire
        addPart(PrimitiveMeshFactory.createTorus(0.19f, 0.085f, 14, 8), 0, tireBlack, Vec3(0f, 0.72f, -1.08f), rot = Vec3(90f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.12f, 0.12f, 0.12f, 10), 0, alloySilver, Vec3(0f, 0.72f, -1.08f), rot = Vec3(90f, 0f, 0f))

        return SceneNode(
            name = spec.name,
            nodeType = NodeType.CHARACTER,
            mesh = PrimitiveMeshFactory.mergeColoredMeshes(parts),
            material = MaterialData(name = "Vehicle Paint", baseColor = spec.primaryColor, roughness = 0.25f, metallic = 0.65f),
            skeleton = skeleton,
            categoryTag = spec.category.name
        )
    }

    private fun buildSpirit(spec: CharacterBuildSpec): SceneNode {
        val skeleton = createDefaultHumanoidSkeleton(1.05f)
        val cloakMain = spec.primaryColor
        val cloakDark = darkenColor(cloakMain, 0.55f)
        val spectralGlow = spec.secondaryColor
        val voidBlack = 0xFF07090F

        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        fun addPart(mesh: MeshData, bone: Int, color: Long, pos: Vec3, rot: Vec3 = Vec3.ZERO, scale: Vec3 = Vec3.ONE) {
            var m = mesh
            if (scale != Vec3.ONE) m = PrimitiveMeshFactory.scaleMesh(m, scale)
            if (rot != Vec3.ZERO) m = PrimitiveMeshFactory.rotateMesh(m, rot)
            m = PrimitiveMeshFactory.translateMesh(m, pos)
            parts += Triple(m, bone, color)
        }

        // Flowing spectral robe + jagged floating hem layers
        addPart(PrimitiveMeshFactory.createCylinder(0.22f, 0.44f, 1.20f, 14), 1, cloakMain, Vec3(0f, 0.96f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.32f, 0.52f, 0.45f, 12), 0, cloakDark, Vec3(0f, 0.42f, 0f))
        // Tattered spectral ribbons around base
        for (i in 0..5) {
            val ang = i * 60f
            val rad = ang * PI.toFloat() / 180f
            addPart(
                PrimitiveMeshFactory.createCylinder(0.08f, 0.01f, 0.34f, 6),
                0, if (i % 2 == 0) spectralGlow else cloakDark,
                Vec3(cos(rad) * 0.36f, 0.20f, sin(rad) * 0.36f)
            )
        }
        // Glowing spectral chest core & shoulder mantle
        addPart(PrimitiveMeshFactory.createUvSphere(0.10f, 10, 8), 1, spectralGlow, Vec3(0f, 1.36f, 0.22f), scale = Vec3(0.95f, 1.25f, 0.55f))
        addPart(PrimitiveMeshFactory.createTorus(0.28f, 0.065f, 14, 8), 1, cloakDark, Vec3(0f, 1.54f, 0f), scale = Vec3(1.25f, 0.72f, 0.92f))

        // Deep shadowy cowl hood + pointed tip + void face + piercing cyan eyes
        addPart(PrimitiveMeshFactory.createUvSphere(0.255f, 14, 12), 2, cloakMain, Vec3(0f, 1.82f, -0.02f), scale = Vec3(1.06f, 1.15f, 1.18f))
        addPart(PrimitiveMeshFactory.createCylinder(0.015f, 0.16f, 0.28f, 10), 2, cloakDark, Vec3(0f, 1.98f, -0.18f), rot = Vec3(-42f, 0f, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.185f, 12, 10), 2, voidBlack, Vec3(0f, 1.80f, 0.08f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.036f, 8, 6), 2, spectralGlow, Vec3(-0.072f, 1.83f, 0.225f), scale = Vec3(1.25f, 0.65f, 0.7f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.036f, 8, 6), 2, spectralGlow, Vec3(0.072f, 1.83f, 0.225f), scale = Vec3(1.25f, 0.65f, 0.7f))

        // Reaching ghostly sleeved arms + glowing clawed hands
        for ((sign, bUpper, bFore) in listOf(Triple(-1f, 3, 4), Triple(1f, 5, 6))) {
            addPart(PrimitiveMeshFactory.createCapsule(0.085f, 0.30f, 10), bUpper, cloakMain, Vec3(sign * 0.36f, 1.38f, 0.08f), rot = Vec3(-22f, 0f, sign * -14f))
            addPart(PrimitiveMeshFactory.createCylinder(0.08f, 0.12f, 0.32f, 10), bFore, cloakDark, Vec3(sign * 0.40f, 1.14f, 0.26f), rot = Vec3(-52f, 0f, 0f))
            addPart(PrimitiveMeshFactory.createUvSphere(0.062f, 8, 6), bFore, spectralGlow, Vec3(sign * 0.40f, 1.06f, 0.42f))
        }

        return SceneNode(
            name = spec.name,
            nodeType = NodeType.CHARACTER,
            mesh = PrimitiveMeshFactory.mergeColoredMeshes(parts),
            material = MaterialData(
                name = "Ethereal Shroud",
                baseColor = spec.primaryColor,
                roughness = 0.32f,
                opacity = 0.90f,
                emissionColor = 0xFF22D3EE,
                emissionStrength = 0.25f
            ),
            skeleton = skeleton,
            categoryTag = spec.category.name
        )
    }

    private fun buildShrineProp(spec: CharacterBuildSpec): SceneNode {
        val goldMain = spec.primaryColor
        val stoneLight = 0xFFE5CFA7
        val stoneWarm = 0xFFC29E6B
        val terracotta = 0xFFB45309
        val brassBright = 0xFFFACC15

        val parts = mutableListOf<Triple<MeshData, Int, Long>>()
        fun addPart(mesh: MeshData, color: Long, pos: Vec3) {
            parts += Triple(PrimitiveMeshFactory.translateMesh(mesh, pos), 0, color)
        }

        // Stepped stone sanctuary base plinth
        addPart(PrimitiveMeshFactory.createCube(1f, 1.36f, 0.14f, 1.36f), stoneWarm, Vec3(0f, 0.07f, 0f))
        addPart(PrimitiveMeshFactory.createCube(1f, 1.16f, 0.12f, 1.16f), stoneLight, Vec3(0f, 0.20f, 0f))
        // Four carved pillars with bases and capitals
        for ((px, pz) in listOf(-0.42f to -0.42f, 0.42f to -0.42f, -0.42f to 0.42f, 0.42f to 0.42f)) {
            addPart(PrimitiveMeshFactory.createCube(1f, 0.22f, 0.10f, 0.22f), terracotta, Vec3(px, 0.31f, pz))
            addPart(PrimitiveMeshFactory.createCylinder(0.085f, 0.10f, 0.92f, 12), stoneLight, Vec3(px, 0.80f, pz))
            addPart(PrimitiveMeshFactory.createCube(1f, 0.24f, 0.10f, 0.24f), goldMain, Vec3(px, 1.30f, pz))
        }
        // Cornice beam & tiered carved Shikhara roof
        addPart(PrimitiveMeshFactory.createCube(1f, 1.24f, 0.12f, 1.24f), terracotta, Vec3(0f, 1.40f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.56f, 0.78f, 0.26f, 8), goldMain, Vec3(0f, 1.59f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.36f, 0.56f, 0.24f, 8), terracotta, Vec3(0f, 1.84f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.14f, 0.36f, 0.24f, 8), goldMain, Vec3(0f, 2.08f, 0f))
        // Golden Kalasha spire finial
        addPart(PrimitiveMeshFactory.createUvSphere(0.10f, 10, 8), brassBright, Vec3(0f, 2.26f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.01f, 0.05f, 0.20f, 8), brassBright, Vec3(0f, 2.40f, 0f))
        // Suspended ornate brass temple bell + chain + clapper
        addPart(PrimitiveMeshFactory.createCylinder(0.02f, 0.02f, 0.34f, 8), stoneWarm, Vec3(0f, 1.18f, 0f))
        addPart(PrimitiveMeshFactory.createCylinder(0.11f, 0.23f, 0.32f, 14), brassBright, Vec3(0f, 0.90f, 0f))
        addPart(PrimitiveMeshFactory.createUvSphere(0.055f, 8, 6), terracotta, Vec3(0f, 0.73f, 0f))

        return SceneNode(
            name = spec.name,
            nodeType = NodeType.MESH,
            mesh = PrimitiveMeshFactory.mergeColoredMeshes(parts),
            material = MaterialData(name = "Sacred Brass", baseColor = spec.primaryColor, roughness = 0.32f, metallic = 0.68f),
            categoryTag = spec.category.name
        )
    }

    private fun darkenColor(hex: Long, factor: Float): Long {
        val r = (((hex shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((hex shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((hex and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    private fun lightenColor(hex: Long, factor: Float): Long {
        val r = (((hex shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((hex shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((hex and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
