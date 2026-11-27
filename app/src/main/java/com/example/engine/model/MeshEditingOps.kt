package com.example.engine.model

import kotlin.math.pow
import kotlin.math.sqrt

object MeshEditingOps {

    fun recomputeNormals(mesh: MeshData): MeshData {
        val vc = mesh.vertexCount
        val acc = FloatArray(vc * 3)
        val tris = mesh.triangleCount
        for (t in 0 until tris) {
            val i0 = mesh.indices[t * 3]
            val i1 = mesh.indices[t * 3 + 1]
            val i2 = mesh.indices[t * 3 + 2]
            if (i0 >= vc || i1 >= vc || i2 >= vc) continue
            val v0 = Vec3(mesh.vertices[i0 * 3], mesh.vertices[i0 * 3 + 1], mesh.vertices[i0 * 3 + 2])
            val v1 = Vec3(mesh.vertices[i1 * 3], mesh.vertices[i1 * 3 + 1], mesh.vertices[i1 * 3 + 2])
            val v2 = Vec3(mesh.vertices[i2 * 3], mesh.vertices[i2 * 3 + 1], mesh.vertices[i2 * 3 + 2])
            val fn = (v1 - v0).cross(v2 - v0)
            for (idx in intArrayOf(i0, i1, i2)) {
                acc[idx * 3] += fn.x
                acc[idx * 3 + 1] += fn.y
                acc[idx * 3 + 2] += fn.z
            }
        }
        for (i in 0 until vc) {
            val nx = acc[i * 3]; val ny = acc[i * 3 + 1]; val nz = acc[i * 3 + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) {
                acc[i * 3] = nx / len
                acc[i * 3 + 1] = ny / len
                acc[i * 3 + 2] = nz / len
            } else {
                acc[i * 3] = 0f; acc[i * 3 + 1] = 1f; acc[i * 3 + 2] = 0f
            }
        }
        return mesh.copy(normals = acc).bumpVersion()
    }

    /**
     * Translates a single vertex, edge pair, or triangle face along delta (Edit Mode).
     */
    fun translateSubElement(
        mesh: MeshData,
        subMode: EditSubMode,
        elementIndex: Int,
        delta: Vec3
    ): MeshData {
        if (mesh.vertexCount == 0) return mesh
        val verts = mesh.vertices.copyOf()
        val affected = when (subMode) {
            EditSubMode.VERTEX -> setOf(elementIndex.coerceIn(0, mesh.vertexCount - 1))
            EditSubMode.EDGE -> {
                val tri = (elementIndex / 3).coerceIn(0, (mesh.triangleCount - 1).coerceAtLeast(0))
                val e = elementIndex % 3
                if (mesh.triangleCount == 0) emptySet()
                else setOf(mesh.indices[tri * 3 + e], mesh.indices[tri * 3 + ((e + 1) % 3)])
            }
            EditSubMode.FACE -> {
                val tri = elementIndex.coerceIn(0, (mesh.triangleCount - 1).coerceAtLeast(0))
                if (mesh.triangleCount == 0) emptySet()
                else setOf(mesh.indices[tri * 3], mesh.indices[tri * 3 + 1], mesh.indices[tri * 3 + 2])
            }
        }
        for (idx in affected) {
            verts[idx * 3] += delta.x
            verts[idx * 3 + 1] += delta.y
            verts[idx * 3 + 2] += delta.z
        }
        return recomputeNormals(mesh.copy(vertices = verts))
    }

    /**
     * Real 3D Extrude operation on a triangle face: clones face vertices along the face normal
     * and stitches 3 side quads (6 triangles) around the extruded rim.
     */
    fun extrudeFace(mesh: MeshData, faceIndex: Int, distance: Float = 0.28f): MeshData {
        if (mesh.triangleCount == 0) return mesh
        val f = faceIndex.coerceIn(0, mesh.triangleCount - 1)
        val i0 = mesh.indices[f * 3]
        val i1 = mesh.indices[f * 3 + 1]
        val i2 = mesh.indices[f * 3 + 2]

        val v0 = Vec3(mesh.vertices[i0 * 3], mesh.vertices[i0 * 3 + 1], mesh.vertices[i0 * 3 + 2])
        val v1 = Vec3(mesh.vertices[i1 * 3], mesh.vertices[i1 * 3 + 1], mesh.vertices[i1 * 3 + 2])
        val v2 = Vec3(mesh.vertices[i2 * 3], mesh.vertices[i2 * 3 + 1], mesh.vertices[i2 * 3 + 2])
        val normal = (v1 - v0).cross(v2 - v0).normalized()

        val oldVc = mesh.vertexCount
        val newVerts = mesh.vertices.toMutableList()
        val newUvs = mesh.uvs.toMutableList()
        val newJIdx = mesh.jointIndices.toMutableList()
        val newJWgt = mesh.jointWeights.toMutableList()
        val newCols = mesh.colors.toMutableList()
        val newIndices = mesh.indices.toMutableList()

        val srcIds = intArrayOf(i0, i1, i2)
        val srcPts = arrayOf(v0, v1, v2)
        val n0 = oldVc; val n1 = oldVc + 1; val n2 = oldVc + 2

        for (k in 0..2) {
            val extruded = srcPts[k] + normal * distance
            newVerts.add(extruded.x); newVerts.add(extruded.y); newVerts.add(extruded.z)
            newUvs.add(mesh.uvs[srcIds[k] * 2]); newUvs.add(mesh.uvs[srcIds[k] * 2 + 1])
            for (c in 0..2) newCols.add(mesh.colors.getOrElse(srcIds[k] * 3 + c) { 1f })
            for (b in 0..3) {
                newJIdx.add(mesh.jointIndices[srcIds[k] * 4 + b])
                newJWgt.add(mesh.jointWeights[srcIds[k] * 4 + b])
            }
        }

        // Replace original face with extruded cap
        newIndices[f * 3] = n0
        newIndices[f * 3 + 1] = n1
        newIndices[f * 3 + 2] = n2

        // Add 3 side quads (6 triangles)
        fun addQuad(a: Int, b: Int, c: Int, d: Int) {
            newIndices.add(a); newIndices.add(b); newIndices.add(c)
            newIndices.add(a); newIndices.add(c); newIndices.add(d)
        }
        addQuad(i0, i1, n1, n0)
        addQuad(i1, i2, n2, n1)
        addQuad(i2, i0, n0, n2)

        val rebuilt = MeshData(
            vertices = newVerts.toFloatArray(),
            normals = FloatArray(newVerts.size),
            uvs = newUvs.toFloatArray(),
            indices = newIndices.toIntArray(),
            jointIndices = newJIdx.toIntArray(),
            jointWeights = newJWgt.toFloatArray(),
            colors = newCols.toFloatArray(),
            useVertexColors = mesh.useVertexColors
        )
        return recomputeNormals(rebuilt)
    }

    /**
     * Real 3D Inset operation: pulls a new inner triangle toward the face centroid and bridges the frame.
     */
    fun insetFace(mesh: MeshData, faceIndex: Int, factor: Float = 0.28f): MeshData {
        if (mesh.triangleCount == 0) return mesh
        val f = faceIndex.coerceIn(0, mesh.triangleCount - 1)
        val i0 = mesh.indices[f * 3]
        val i1 = mesh.indices[f * 3 + 1]
        val i2 = mesh.indices[f * 3 + 2]

        val v0 = Vec3(mesh.vertices[i0 * 3], mesh.vertices[i0 * 3 + 1], mesh.vertices[i0 * 3 + 2])
        val v1 = Vec3(mesh.vertices[i1 * 3], mesh.vertices[i1 * 3 + 1], mesh.vertices[i1 * 3 + 2])
        val v2 = Vec3(mesh.vertices[i2 * 3], mesh.vertices[i2 * 3 + 1], mesh.vertices[i2 * 3 + 2])
        val center = (v0 + v1 + v2) / 3f

        val oldVc = mesh.vertexCount
        val newVerts = mesh.vertices.toMutableList()
        val newUvs = mesh.uvs.toMutableList()
        val newJIdx = mesh.jointIndices.toMutableList()
        val newJWgt = mesh.jointWeights.toMutableList()
        val newCols = mesh.colors.toMutableList()
        val newIndices = mesh.indices.toMutableList()

        val srcIds = intArrayOf(i0, i1, i2)
        val srcPts = arrayOf(v0, v1, v2)
        for (k in 0..2) {
            val insetPt = Vec3.lerp(srcPts[k], center, factor.coerceIn(0.1f, 0.7f))
            newVerts.add(insetPt.x); newVerts.add(insetPt.y); newVerts.add(insetPt.z)
            newUvs.add(mesh.uvs[srcIds[k] * 2]); newUvs.add(mesh.uvs[srcIds[k] * 2 + 1])
            for (c in 0..2) newCols.add(mesh.colors.getOrElse(srcIds[k] * 3 + c) { 1f })
            for (b in 0..3) {
                newJIdx.add(mesh.jointIndices[srcIds[k] * 4 + b])
                newJWgt.add(mesh.jointWeights[srcIds[k] * 4 + b])
            }
        }
        val n0 = oldVc; val n1 = oldVc + 1; val n2 = oldVc + 2
        newIndices[f * 3] = n0; newIndices[f * 3 + 1] = n1; newIndices[f * 3 + 2] = n2
        fun addQuad(a: Int, b: Int, c: Int, d: Int) {
            newIndices.add(a); newIndices.add(b); newIndices.add(c)
            newIndices.add(a); newIndices.add(c); newIndices.add(d)
        }
        addQuad(i0, i1, n1, n0)
        addQuad(i1, i2, n2, n1)
        addQuad(i2, i0, n0, n2)

        val rebuilt = MeshData(
            vertices = newVerts.toFloatArray(),
            normals = FloatArray(newVerts.size),
            uvs = newUvs.toFloatArray(),
            indices = newIndices.toIntArray(),
            jointIndices = newJIdx.toIntArray(),
            jointWeights = newJWgt.toFloatArray(),
            colors = newCols.toFloatArray(),
            useVertexColors = mesh.useVertexColors
        )
        return recomputeNormals(rebuilt)
    }

    /**
     * Real 1-to-4 midpoint Triangle Subdivision (capped for mobile responsiveness).
     */
    fun subdivideMesh(mesh: MeshData): MeshData {
        if (mesh.triangleCount == 0 || mesh.triangleCount > 6000) return mesh
        val verts = mesh.vertices.toMutableList()
        val uvs = mesh.uvs.toMutableList()
        val jIdx = mesh.jointIndices.toMutableList()
        val jWgt = mesh.jointWeights.toMutableList()
        val cols = mesh.colors.toMutableList()
        val newIndices = ArrayList<Int>(mesh.indices.size * 4)
        val midpointCache = HashMap<Long, Int>()

        fun getMidpoint(a: Int, b: Int): Int {
            val lo = minOf(a, b).toLong()
            val hi = maxOf(a, b).toLong()
            val key = (lo shl 32) or hi
            midpointCache[key]?.let { return it }

            val idx = verts.size / 3
            verts.add((mesh.vertices[a * 3] + mesh.vertices[b * 3]) * 0.5f)
            verts.add((mesh.vertices[a * 3 + 1] + mesh.vertices[b * 3 + 1]) * 0.5f)
            verts.add((mesh.vertices[a * 3 + 2] + mesh.vertices[b * 3 + 2]) * 0.5f)
            uvs.add((mesh.uvs[a * 2] + mesh.uvs[b * 2]) * 0.5f)
            uvs.add((mesh.uvs[a * 2 + 1] + mesh.uvs[b * 2 + 1]) * 0.5f)
            for (c in 0..2) {
                cols.add((mesh.colors.getOrElse(a * 3 + c) { 1f } + mesh.colors.getOrElse(b * 3 + c) { 1f }) * 0.5f)
            }
            for (k in 0..3) {
                jIdx.add(mesh.jointIndices[a * 4 + k])
                jWgt.add(mesh.jointWeights[a * 4 + k])
            }
            midpointCache[key] = idx
            return idx
        }

        for (t in 0 until mesh.triangleCount) {
            val i0 = mesh.indices[t * 3]
            val i1 = mesh.indices[t * 3 + 1]
            val i2 = mesh.indices[t * 3 + 2]
            val m01 = getMidpoint(i0, i1)
            val m12 = getMidpoint(i1, i2)
            val m20 = getMidpoint(i2, i0)
            newIndices.add(i0); newIndices.add(m01); newIndices.add(m20)
            newIndices.add(m01); newIndices.add(i1); newIndices.add(m12)
            newIndices.add(m20); newIndices.add(m12); newIndices.add(i2)
            newIndices.add(m01); newIndices.add(m12); newIndices.add(m20)
        }

        val rebuilt = MeshData(
            vertices = verts.toFloatArray(),
            normals = FloatArray(verts.size),
            uvs = uvs.toFloatArray(),
            indices = newIndices.toIntArray(),
            jointIndices = jIdx.toIntArray(),
            jointWeights = jWgt.toFloatArray(),
            colors = cols.toFloatArray(),
            useVertexColors = mesh.useVertexColors
        )
        return recomputeNormals(rebuilt)
    }

    /**
     * Smooth Bevel / Chamfer: subdivides once and applies gentle Laplacian corner softening.
     */
    fun bevelMesh(mesh: MeshData, strength: Float = 0.18f): MeshData {
        val sub = if (mesh.triangleCount < 1500) subdivideMesh(mesh) else mesh
        val vc = sub.vertexCount
        val adj = Array(vc) { HashSet<Int>() }
        for (t in 0 until sub.triangleCount) {
            val a = sub.indices[t * 3]; val b = sub.indices[t * 3 + 1]; val c = sub.indices[t * 3 + 2]
            adj[a].add(b); adj[a].add(c)
            adj[b].add(a); adj[b].add(c)
            adj[c].add(a); adj[c].add(b)
        }
        val outVerts = sub.vertices.copyOf()
        val s = strength.coerceIn(0.05f, 0.45f)
        for (i in 0 until vc) {
            val nbrs = adj[i]
            if (nbrs.isEmpty()) continue
            var ax = 0f; var ay = 0f; var az = 0f
            for (n in nbrs) {
                ax += sub.vertices[n * 3]
                ay += sub.vertices[n * 3 + 1]
                az += sub.vertices[n * 3 + 2]
            }
            val inv = 1f / nbrs.size
            outVerts[i * 3] = sub.vertices[i * 3] * (1f - s) + (ax * inv) * s
            outVerts[i * 3 + 1] = sub.vertices[i * 3 + 1] * (1f - s) + (ay * inv) * s
            outVerts[i * 3 + 2] = sub.vertices[i * 3 + 2] * (1f - s) + (az * inv) * s
        }
        return recomputeNormals(sub.copy(vertices = outVerts))
    }

    /**
     * Proximity-based Automatic Skinning: assigns each vertex up to 4 closest bones with normalized smooth weights.
     */
    fun autoBindMeshToSkeleton(mesh: MeshData, skeleton: SkeletonData): MeshData {
        if (skeleton.bones.isEmpty() || mesh.vertexCount == 0) return mesh
        val restPos = skeleton.computeWorldJointPositions(skeleton.bones.associate { it.id to Vec3.ZERO })
        val boneMidpoints = skeleton.bones.take(SkeletonData.MAX_BONES).mapIndexed { idx, b ->
            val (head, tail) = restPos[b.id] ?: (Vec3.ZERO to Vec3(0f, 0.3f, 0f))
            idx to ((head + tail) * 0.5f)
        }
        val vc = mesh.vertexCount
        val jIdx = IntArray(vc * 4)
        val jWgt = FloatArray(vc * 4)

        for (v in 0 until vc) {
            val p = Vec3(mesh.vertices[v * 3], mesh.vertices[v * 3 + 1], mesh.vertices[v * 3 + 2])
            val ranked = boneMidpoints.map { (idx, mid) ->
                val d = (p - mid).length().coerceAtLeast(0.02f)
                idx to (1f / d.pow(2.5f))
            }.sortedByDescending { it.second }.take(4)

            val sum = ranked.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1e-5f)
            ranked.forEachIndexed { slot, (bIdx, w) ->
                jIdx[v * 4 + slot] = bIdx
                jWgt[v * 4 + slot] = w / sum
            }
        }
        return mesh.copy(jointIndices = jIdx, jointWeights = jWgt).bumpVersion()
    }
}
