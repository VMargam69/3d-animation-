package com.example.engine.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = if (abs(s) > 1e-6f) Vec3(x / s, y / s, z / s) else ZERO

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UP = Vec3(0f, 1f, 0f)

        fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 = Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        )
    }
}

data class Ray3D(val origin: Vec3, val direction: Vec3)

data class Aabb3D(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = (min + max) * 0.5f
    val radius: Float get() = ((max - min) * 0.5f).length().coerceAtLeast(0.25f)
}

object Math3D {
    const val DEG_TO_RAD = (PI / 180.0).toFloat()

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /**
     * Pure-Kotlin column-major TRS matrix matching OpenGL ES `T * Ry * Rx * Rz * S`.
     */
    fun createTrsMatrix(position: Vec3, rotationDeg: Vec3, scale: Vec3): FloatArray {
        val rx = rotationDeg.x * DEG_TO_RAD
        val ry = rotationDeg.y * DEG_TO_RAD
        val rz = rotationDeg.z * DEG_TO_RAD
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)

        return floatArrayOf(
            (cy * cz + sy * sx * sz) * scale.x,
            (cx * sz) * scale.x,
            (-sy * cz + cy * sx * sz) * scale.x,
            0f,

            (-cy * sz + sy * sx * cz) * scale.y,
            (cx * cz) * scale.y,
            (sy * sz + cy * sx * cz) * scale.y,
            0f,

            (sy * cx) * scale.z,
            (-sx) * scale.z,
            (cy * cx) * scale.z,
            0f,

            position.x,
            position.y,
            position.z,
            1f
        )
    }

    /**
     * Column-major 4x4 matrix multiplication `a * b`.
     */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0..3) {
            val c4 = col * 4
            val b0 = b[c4]
            val b1 = b[c4 + 1]
            val b2 = b[c4 + 2]
            val b3 = b[c4 + 3]
            out[c4]     = a[0] * b0 + a[4] * b1 + a[8]  * b2 + a[12] * b3
            out[c4 + 1] = a[1] * b0 + a[5] * b1 + a[9]  * b2 + a[13] * b3
            out[c4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[c4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
        return out
    }

    /**
     * Exact column-major 4x4 matrix inversion (returns identity if singular).
     */
    fun invert(m: FloatArray): FloatArray {
        val inv = FloatArray(16)
        val a00 = m[0]; val a01 = m[1]; val a02 = m[2]; val a03 = m[3]
        val a10 = m[4]; val a11 = m[5]; val a12 = m[6]; val a13 = m[7]
        val a20 = m[8]; val a21 = m[9]; val a22 = m[10]; val a23 = m[11]
        val a30 = m[12]; val a31 = m[13]; val a32 = m[14]; val a33 = m[15]

        val b00 = a00 * a11 - a01 * a10
        val b01 = a00 * a12 - a02 * a10
        val b02 = a00 * a13 - a03 * a10
        val b03 = a01 * a12 - a02 * a11
        val b04 = a01 * a13 - a03 * a11
        val b05 = a02 * a13 - a03 * a12
        val b06 = a20 * a31 - a21 * a30
        val b07 = a20 * a32 - a22 * a30
        val b08 = a20 * a33 - a23 * a30
        val b09 = a21 * a32 - a22 * a31
        val b10 = a21 * a33 - a23 * a31
        val b11 = a22 * a33 - a23 * a32

        val det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
        if (abs(det) < 1e-8f) return identity()
        val idet = 1f / det

        inv[0]  = ( a11 * b11 - a12 * b10 + a13 * b09) * idet
        inv[1]  = (-a01 * b11 + a02 * b10 - a03 * b09) * idet
        inv[2]  = ( a31 * b05 - a32 * b04 + a33 * b03) * idet
        inv[3]  = (-a21 * b05 + a22 * b04 - a23 * b03) * idet
        inv[4]  = (-a10 * b11 + a12 * b08 - a13 * b07) * idet
        inv[5]  = ( a00 * b11 - a02 * b08 + a03 * b07) * idet
        inv[6]  = (-a30 * b05 + a32 * b02 - a33 * b01) * idet
        inv[7]  = ( a20 * b05 - a22 * b02 + a23 * b01) * idet
        inv[8]  = ( a10 * b10 - a11 * b08 + a13 * b06) * idet
        inv[9]  = (-a00 * b10 + a01 * b08 - a03 * b06) * idet
        inv[10] = ( a30 * b04 - a31 * b02 + a33 * b00) * idet
        inv[11] = (-a20 * b04 + a21 * b02 - a23 * b00) * idet
        inv[12] = (-a10 * b09 + a11 * b07 - a12 * b06) * idet
        inv[13] = ( a00 * b09 - a01 * b07 + a02 * b06) * idet
        inv[14] = (-a30 * b03 + a31 * b01 - a32 * b00) * idet
        inv[15] = ( a20 * b03 - a21 * b01 + a22 * b00) * idet
        return inv
    }

    private fun multiplyMV(m: FloatArray, x: Float, y: Float, z: Float, w: Float): FloatArray = floatArrayOf(
        m[0] * x + m[4] * y + m[8]  * z + m[12] * w,
        m[1] * x + m[5] * y + m[9]  * z + m[13] * w,
        m[2] * x + m[6] * y + m[10] * z + m[14] * w,
        m[3] * x + m[7] * y + m[11] * z + m[15] * w
    )

    fun transformPoint(m: FloatArray, p: Vec3): Vec3 {
        val rx = m[0] * p.x + m[4] * p.y + m[8]  * p.z + m[12]
        val ry = m[1] * p.x + m[5] * p.y + m[9]  * p.z + m[13]
        val rz = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val rw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        val w = if (abs(rw) > 1e-6f) rw else 1f
        return Vec3(rx / w, ry / w, rz / w)
    }

    fun unprojectRay(
        touchX: Float,
        touchY: Float,
        viewportW: Int,
        viewportH: Int,
        viewMatrix: FloatArray,
        projMatrix: FloatArray
    ): Ray3D {
        if (viewportW <= 0 || viewportH <= 0) return Ray3D(Vec3.ZERO, Vec3(0f, 0f, -1f))
        val ndcX = (2f * touchX) / viewportW - 1f
        val ndcY = 1f - (2f * touchY) / viewportH
        val vp = multiply(projMatrix, viewMatrix)
        val invVp = invert(vp)

        val nearWorld = multiplyMV(invVp, ndcX, ndcY, -1f, 1f)
        val farWorld = multiplyMV(invVp, ndcX, ndcY, 1f, 1f)

        val nw = if (abs(nearWorld[3]) > 1e-6f) nearWorld[3] else 1f
        val fw = if (abs(farWorld[3]) > 1e-6f) farWorld[3] else 1f
        val origin = Vec3(nearWorld[0] / nw, nearWorld[1] / nw, nearWorld[2] / nw)
        val farPt = Vec3(farWorld[0] / fw, farWorld[1] / fw, farWorld[2] / fw)
        return Ray3D(origin, (farPt - origin).normalized())
    }

    fun raySphereHitDistance(ray: Ray3D, center: Vec3, radius: Float): Float? {
        val oc = ray.origin - center
        val b = oc.dot(ray.direction)
        val c = oc.dot(oc) - radius * radius
        val disc = b * b - c
        if (disc < 0f) return null
        val t = -b - sqrt(disc)
        return if (t >= 0f) t else {
            val t2 = -b + sqrt(disc)
            if (t2 >= 0f) t2 else null
        }
    }

    fun rayAabbHitDistance(ray: Ray3D, aabb: Aabb3D): Float? {
        var tMin = 0.001f
        var tMax = 10000f
        val mins = floatArrayOf(aabb.min.x, aabb.min.y, aabb.min.z)
        val maxs = floatArrayOf(aabb.max.x, aabb.max.y, aabb.max.z)
        val orig = floatArrayOf(ray.origin.x, ray.origin.y, ray.origin.z)
        val dir = floatArrayOf(ray.direction.x, ray.direction.y, ray.direction.z)
        for (i in 0..2) {
            if (abs(dir[i]) < 1e-6f) {
                if (orig[i] < mins[i] || orig[i] > maxs[i]) return null
            } else {
                var t1 = (mins[i] - orig[i]) / dir[i]
                var t2 = (maxs[i] - orig[i]) / dir[i]
                if (t1 > t2) {
                    val tmp = t1; t1 = t2; t2 = tmp
                }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                if (tMin > tMax) return null
            }
        }
        return tMin
    }

    fun orbitCameraPosition(target: Vec3, yawDeg: Float, pitchDeg: Float, distance: Float): Vec3 {
        val yawRad = yawDeg * DEG_TO_RAD
        val pitchRad = pitchDeg.coerceIn(-86f, 86f) * DEG_TO_RAD
        val cosPitch = cos(pitchRad)
        return Vec3(
            x = target.x + distance * cosPitch * sin(yawRad),
            y = target.y + distance * sin(pitchRad),
            z = target.z + distance * cosPitch * cos(yawRad)
        )
    }

    fun colorHexToRgbFloats(hex: Long): FloatArray {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }
}
