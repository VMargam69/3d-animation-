package com.example.engine.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.LibraryAssetEntity
import com.example.data.StudioRepository
import com.example.engine.model.MaterialData
import com.example.engine.model.MeshData
import com.example.engine.model.OrientationFilter
import com.example.engine.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure-Kotlin 3D software triangle rasterizer that renders genuine, studio-lit 3D thumbnails
 * from actual CharacterMeshBuilder / imported MeshData geometries and caches them in memory.
 */
object CharacterThumbnailRenderer {

    private val cache = LruCache<String, ImageBitmap>(64)

    fun invalidate(assetId: String? = null) {
        if (assetId == null) {
            cache.evictAll()
        } else {
            cache.snapshot().keys.filter { it.startsWith("$assetId:") }.forEach { cache.remove(it) }
        }
    }

    fun renderAssetThumbnail(
        asset: LibraryAssetEntity,
        widthPx: Int = 256,
        heightPx: Int = 220
    ): ImageBitmap? {
        val key = "${asset.id}:${asset.primaryColorHex}:${asset.secondaryColorHex}:${asset.orientationTag}:${asset.serializedNodePath}:${widthPx}x$heightPx"
        cache.get(key)?.let { return it }

        val pair = StudioRepository.buildPreviewMeshForAsset(asset) ?: return null
        val (mesh, material) = pair
        if (mesh.vertexCount == 0 || mesh.indices.size < 3) return null

        val yawDeg = when (asset.orientationTag) {
            OrientationFilter.FRONT.name -> 10f
            OrientationFilter.THREE_QUARTER.name -> 28f
            OrientationFilter.SIDE.name -> 56f
            OrientationFilter.RIG_360.name -> 22f
            else -> 26f
        }

        val bitmap = renderMeshToBitmap(
            mesh = mesh,
            material = material,
            accentHex = asset.secondaryColorHex,
            yawDeg = yawDeg,
            pitchDeg = 10f,
            width = widthPx,
            height = heightPx
        )
        val imageBitmap = bitmap.asImageBitmap()
        cache.put(key, imageBitmap)
        return imageBitmap
    }

    fun renderMeshToBitmap(
        mesh: MeshData,
        material: MaterialData,
        accentHex: Long = 0xFF6366F1L,
        yawDeg: Float = 24f,
        pitchDeg: Float = 10f,
        width: Int = 256,
        height: Int = 220
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val wf = width.toFloat()
        val hf = height.toFloat()

        // 1. Studio dark charcoal backdrop with subtle key glow
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(0xFF1A2030.toInt(), 0xFF111520.toInt(), 0xFF0B0E16.toInt()),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, bgPaint)

        val ar = ((accentHex shr 16) and 0xFF).toInt()
        val ag = ((accentHex shr 8) and 0xFF).toInt()
        val ab = (accentHex and 0xFF).toInt()
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.shader = RadialGradient(
            wf * 0.50f, hf * 0.44f, wf * 0.58f,
            intArrayOf(Color.argb(46, ar, ag, ab), Color.argb(18, 99, 102, 241), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, glowPaint)

        // Subtle perspective studio floor lines & soft contact shadow
        val floorY = hf * 0.87f
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4433415E
            strokeWidth = 1.2f
        }
        canvas.drawLine(wf * 0.08f, floorY, wf * 0.92f, floorY, gridPaint)
        for (i in -3..3) {
            canvas.drawLine(wf * 0.5f + i * wf * 0.11f, floorY, wf * 0.5f + i * wf * 0.22f, hf, gridPaint)
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                wf * 0.5f, floorY + hf * 0.01f, wf * 0.30f,
                intArrayOf(0xAA05070C.toInt(), 0x4405070C, Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.scale(1f, 0.30f, wf * 0.5f, floorY)
        canvas.drawCircle(wf * 0.5f, floorY, wf * 0.30f, shadowPaint)
        canvas.restore()

        // 2. Compute tight 3D AABB for perfect framing
        val vc = mesh.vertexCount
        val verts = mesh.vertices
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (i in 0 until vc) {
            val x = verts[i * 3]
            val y = verts[i * 3 + 1]
            val z = verts[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        val center = Vec3((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f)
        val sizeX = max(0.2f, maxX - minX)
        val sizeY = max(0.2f, maxY - minY)
        val sizeZ = max(0.2f, maxZ - minZ)
        val radius = sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ) * 0.5f

        val fovDeg = 34f
        val aspect = wf / hf
        val halfFovRad = (fovDeg * 0.5f) * PI.toFloat() / 180f
        val distH = (sizeY * 0.64f) / tan(halfFovRad)
        val distW = (max(sizeX, sizeZ) * 0.66f) / (tan(halfFovRad) * aspect)
        val dist = max(max(distH, distW), radius * 2.05f)

        val yawRad = yawDeg * PI.toFloat() / 180f
        val pitchRad = pitchDeg * PI.toFloat() / 180f
        val eye = Vec3(
            center.x + dist * cos(pitchRad) * sin(yawRad),
            center.y + dist * sin(pitchRad),
            center.z + dist * cos(pitchRad) * cos(yawRad)
        )
        val vm = buildLookAtMatrix(eye, center, Vec3(0f, 1f, 0f))
        val pm = buildPerspectiveMatrix(fovDeg, aspect, 0.1f, dist * 6f)

        // 3. Transform all vertices to view space & projected 2D screen space
        val vx = FloatArray(vc)
        val vy = FloatArray(vc)
        val vz = FloatArray(vc)
        val sx = FloatArray(vc)
        val sy = FloatArray(vc)

        for (i in 0 until vc) {
            val px = verts[i * 3]
            val py = verts[i * 3 + 1]
            val pz = verts[i * 3 + 2]

            val cx = vm[0] * px + vm[4] * py + vm[8] * pz + vm[12]
            val cy = vm[1] * px + vm[5] * py + vm[9] * pz + vm[13]
            val cz = vm[2] * px + vm[6] * py + vm[10] * pz + vm[14]
            vx[i] = cx; vy[i] = cy; vz[i] = cz

            val clipX = pm[0] * cx + pm[4] * cy + pm[8] * cz + pm[12]
            val clipY = pm[1] * cx + pm[5] * cy + pm[9] * cz + pm[13]
            val clipW = pm[3] * cx + pm[7] * cy + pm[11] * cz + pm[15]
            val invW = if (clipW > 0.0001f) 1f / clipW else 1f
            sx[i] = (0.5f + (clipX * invW) * 0.5f) * wf
            sy[i] = (0.5f - (clipY * invW) * 0.5f) * hf
        }

        // 4. Collect visible triangles with studio three-point shading
        val indices = mesh.indices
        val triCount = indices.size / 3
        val useVCol = mesh.useVertexColors && mesh.colors.size >= vc * 3
        val vCols = mesh.colors

        val baseR = ((material.baseColor shr 16) and 0xFF) / 255f
        val baseG = ((material.baseColor shr 8) and 0xFF) / 255f
        val baseB = (material.baseColor and 0xFF) / 255f
        val shininess = (2f + (1f - material.roughness.coerceIn(0.05f, 1f)) * 46f)
        val specStrength = 0.22f + material.metallic.coerceIn(0f, 1f) * 0.45f

        // Light directions in view space (normalized)
        val keyDir = Vec3(0.48f, 0.62f, 0.62f).normalized()
        val fillDir = Vec3(-0.62f, 0.18f, 0.48f).normalized()
        val rimDir = Vec3(-0.55f, 0.35f, -0.76f).normalized()

        val triIdx = IntArray(triCount)
        val triDepth = FloatArray(triCount)
        val triColor = IntArray(triCount)
        var visibleCount = 0

        for (t in 0 until triCount) {
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]
            if (i0 >= vc || i1 >= vc || i2 >= vc) continue

            val z0 = vz[i0]; val z1 = vz[i1]; val z2 = vz[i2]
            if (z0 > -0.05f || z1 > -0.05f || z2 > -0.05f) continue

            // View-space face normal for accurate perspective backface culling + shading
            val e1x = vx[i1] - vx[i0]; val e1y = vy[i1] - vy[i0]; val e1z = z1 - z0
            val e2x = vx[i2] - vx[i0]; val e2y = vy[i2] - vy[i0]; val e2z = z2 - z0
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            val nLen = sqrt(nx * nx + ny * ny + nz * nz)
            if (nLen < 1e-6f) continue

            // View vector from triangle center to camera (0,0,0) is (-cx, -cy, -cz)
            val cx = (vx[i0] + vx[i1] + vx[i2]) * 0.3333f
            val cy = (vy[i0] + vy[i1] + vy[i2]) * 0.3333f
            val cz = (z0 + z1 + z2) * 0.3333f
            val facing = nx * (-cx) + ny * (-cy) + nz * (-cz)
            if (facing <= 0f) continue

            val invN = 1f / nLen
            val fnx = nx * invN; val fny = ny * invN; val fnz = nz * invN

            // Average per-vertex color or fallback material baseColor
            val cr: Float; val cg: Float; val cb: Float
            if (useVCol) {
                cr = (vCols[i0 * 3] + vCols[i1 * 3] + vCols[i2 * 3]) * 0.3333f
                cg = (vCols[i0 * 3 + 1] + vCols[i1 * 3 + 1] + vCols[i2 * 3 + 1]) * 0.3333f
                cb = (vCols[i0 * 3 + 2] + vCols[i1 * 3 + 2] + vCols[i2 * 3 + 2]) * 0.3333f
            } else {
                cr = baseR; cg = baseG; cb = baseB
            }

            // Three-point studio illumination (ambient + warm key + cool fill + cyan Fresnel rim + specular)
            val nDotKey = max(0f, fnx * keyDir.x + fny * keyDir.y + fnz * keyDir.z)
            val nDotFill = max(0f, fnx * fillDir.x + fny * fillDir.y + fnz * fillDir.z)
            val nDotRim = max(0f, fnx * rimDir.x + fny * rimDir.y + fnz * rimDir.z)

            val vLen = sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-5f)
            val vdx = -cx / vLen; val vdy = -cy / vLen; val vdz = -cz / vLen
            val fresnel = (1f - max(0f, fnx * vdx + fny * vdy + fnz * vdz)).pow(2.4f)

            val hx = keyDir.x + vdx; val hy = keyDir.y + vdy; val hz = keyDir.z + vdz
            val hLen = sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(1e-5f)
            val spec = max(0f, (fnx * hx + fny * hy + fnz * hz) / hLen).pow(shininess) * specStrength

            val hemi = 0.24f + 0.14f * (fny * 0.5f + 0.5f)
            val diffR = hemi + nDotKey * 0.78f + nDotFill * 0.20f
            val diffG = hemi + nDotKey * 0.76f + nDotFill * 0.23f
            val diffB = hemi + nDotKey * 0.72f + nDotFill * 0.28f

            val rimGlow = fresnel * 0.28f + nDotRim * 0.14f
            val outR = (cr * diffR + spec + rimGlow * 0.18f).coerceIn(0f, 1f)
            val outG = (cg * diffG + spec + rimGlow * 0.75f).coerceIn(0f, 1f)
            val outB = (cb * diffB + spec + rimGlow * 0.92f).coerceIn(0f, 1f)

            triIdx[visibleCount] = t
            triDepth[visibleCount] = cz
            triColor[visibleCount] = Color.rgb((outR * 255f).toInt(), (outG * 255f).toInt(), (outB * 255f).toInt())
            visibleCount++
        }

        // 5. Sort back-to-front (ascending Z in right-handed camera space) using fast index quicksort
        val order = IntArray(visibleCount) { it }
        quickSortByDepth(order, triDepth, 0, visibleCount - 1)

        // 6. Rasterize sorted triangles with crisp anti-aliased fill+stroke
        val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 0.75f
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        for (k in 0 until visibleCount) {
            val slot = order[k]
            val t = triIdx[slot]
            val i0 = indices[t * 3]
            val i1 = indices[t * 3 + 1]
            val i2 = indices[t * 3 + 2]
            path.rewind()
            path.moveTo(sx[i0], sy[i0])
            path.lineTo(sx[i1], sy[i1])
            path.lineTo(sx[i2], sy[i2])
            path.close()
            triPaint.color = triColor[slot]
            canvas.drawPath(path, triPaint)
        }

        return bmp
    }

    private fun quickSortByDepth(order: IntArray, depth: FloatArray, low: Int, high: Int) {
        if (low >= high) return
        var i = low
        var j = high
        val pivot = depth[order[(low + high) ushr 1]]
        while (i <= j) {
            while (depth[order[i]] < pivot) i++
            while (depth[order[j]] > pivot) j--
            if (i <= j) {
                val tmp = order[i]
                order[i] = order[j]
                order[j] = tmp
                i++
                j--
            }
        }
        if (low < j) quickSortByDepth(order, depth, low, j)
        if (i < high) quickSortByDepth(order, depth, i, high)
    }

    private fun buildLookAtMatrix(eye: Vec3, target: Vec3, up: Vec3): FloatArray {
        val f = (target - eye).normalized()
        val s = f.cross(up).normalized()
        val u = s.cross(f)
        return floatArrayOf(
            s.x, u.x, -f.x, 0f,
            s.y, u.y, -f.y, 0f,
            s.z, u.z, -f.z, 0f,
            -s.dot(eye), -u.dot(eye), f.dot(eye), 1f
        )
    }

    private fun buildPerspectiveMatrix(fovDeg: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan((fovDeg * 0.5f) * PI.toFloat() / 180f)
        val rangeInv = 1f / (near - far)
        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) * rangeInv, -1f,
            0f, 0f, (2f * far * near) * rangeInv, 0f
        )
    }
}
