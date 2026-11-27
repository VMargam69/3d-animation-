package com.example.engine.model

import android.opengl.Matrix
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

    fun identity(): FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    fun createTrsMatrix(position: Vec3, rotationDeg: Vec3, scale: Vec3): FloatArray {
        val m = identity()
        Matrix.translateM(m, 0, position.x, position.y, position.z)
        if (rotationDeg.y != 0f) Matrix.rotateM(m, 0, rotationDeg.y, 0f, 1f, 0f)
        if (rotationDeg.x != 0f) Matrix.rotateM(m, 0, rotationDeg.x, 1f, 0f, 0f)
        if (rotationDeg.z != 0f) Matrix.rotateM(m, 0, rotationDeg.z, 0f, 0f, 1f)
        Matrix.scaleM(m, 0, scale.x, scale.y, scale.z)
        return m
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, a, 0, b, 0)
        return out
    }

    fun invert(m: FloatArray): FloatArray {
        val out = FloatArray(16)
        if (!Matrix.invertM(out, 0, m, 0)) {
            Matrix.setIdentityM(out, 0)
        }
        return out
    }

    fun transformPoint(m: FloatArray, p: Vec3): Vec3 {
        val rhs = floatArrayOf(p.x, p.y, p.z, 1f)
        val res = FloatArray(4)
        Matrix.multiplyMV(res, 0, m, 0, rhs, 0)
        val w = if (abs(res[3]) > 1e-6f) res[3] else 1f
        return Vec3(res[0] / w, res[1] / w, res[2] / w)
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

        val nearNdc = floatArrayOf(ndcX, ndcY, -1f, 1f)
        val farNdc = floatArrayOf(ndcX, ndcY, 1f, 1f)
        val nearWorld = FloatArray(4)
        val farWorld = FloatArray(4)
        Matrix.multiplyMV(nearWorld, 0, invVp, 0, nearNdc, 0)
        Matrix.multiplyMV(farWorld, 0, invVp, 0, farNdc, 0)

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
