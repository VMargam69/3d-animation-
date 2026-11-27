package com.example.engine.animation

import com.example.engine.model.IkLimb
import com.example.engine.model.Math3D
import com.example.engine.model.SkeletonData
import com.example.engine.model.Vec3
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class IkSolveResult(
    val limb: IkLimb,
    val upperBoneId: String,
    val lowerBoneId: String,
    val upperRotation: Vec3,
    val lowerRotation: Vec3,
    val achievedLocalTip: Vec3
)

/**
 * Real-time Analytical 2-Bone Inverse Kinematics (IK) Solver + Pose Mirroring & Blending utilities.
 * Solves humanoid arm (Shoulder -> Elbow -> Wrist) and leg (Hip -> Knee -> Ankle) chains
 * cleanly in pure Kotlin with anatomical pole-vector hints so elbows & knees bend naturally.
 */
object IkSolver {

    private const val RAD_TO_DEG = (180.0 / PI).toFloat()

    /**
     * Computes world-space positions of all available IK end-effector handles on a skeleton.
     */
    fun computeIkHandleWorldPositions(
        skeleton: SkeletonData,
        nodeWorldMatrix: FloatArray,
        poseOverrides: Map<String, Vec3> = emptyMap()
    ): Map<IkLimb, Vec3> {
        val boneIds = skeleton.bones.map { it.id }.toSet()
        val joints = skeleton.computeWorldJointPositions(poseOverrides)
        val out = LinkedHashMap<IkLimb, Vec3>()
        for (limb in IkLimb.entries) {
            if (limb.upperBoneId in boneIds && limb.lowerBoneId in boneIds) {
                val (_, localTip) = joints[limb.lowerBoneId] ?: continue
                out[limb] = Math3D.transformPoint(nodeWorldMatrix, localTip)
            }
        }
        return out
    }

    /**
     * Solves local Euler XYZ rotations for the upper and lower bones of [limb] so the lower bone's tip
     * reaches [targetWorldPos] in world space, respecting parent bone rotation and natural [poleAngleDeg].
     */
    fun solveLimbIk(
        skeleton: SkeletonData,
        nodeWorldMatrix: FloatArray,
        currentPose: Map<String, Vec3>,
        limb: IkLimb,
        targetWorldPos: Vec3,
        poleAngleDeg: Float = 0f
    ): IkSolveResult? {
        val byId = skeleton.bones.associateBy { it.id }
        val upperBone = byId[limb.upperBoneId] ?: return null
        val lowerBone = byId[limb.lowerBoneId] ?: return null

        // Convert world target into character-local space
        val invNodeMat = Math3D.invert(nodeWorldMatrix)
        val targetCharLocal = Math3D.transformPoint(invNodeMat, targetWorldPos)

        // Compute parent world matrix of the upper bone (spine for arms, hips for legs)
        val boneWorlds = skeleton.computeBoneWorldMatrices(currentPose)
        val parentMat = upperBone.parentId?.let { boneWorlds[it] } ?: Math3D.identity()
        val invParentMat = Math3D.invert(parentMat)

        // Transform target into upperBone's parent space, then subtract upperBone.localOffset -> vector from shoulder/hip pivot
        val targetInParent = Math3D.transformPoint(invParentMat, targetCharLocal)
        val rootToTarget = targetInParent - upperBone.localOffset

        val l1 = upperBone.length.coerceAtLeast(0.05f)
        val l2 = lowerBone.length.coerceAtLeast(0.05f)
        val minReach = kotlin.math.abs(l1 - l2) + 0.02f
        val maxReach = (l1 + l2) * 0.996f

        val rawDist = rootToTarget.length()
        val dir = if (rawDist > 1e-5f) rootToTarget / rawDist else Vec3(0f, -1f, 0f)
        val clampedDist = rawDist.coerceIn(minReach, maxReach)

        // Law of Cosines for triangle (l1, l2, clampedDist)
        val cosAlpha = ((l1 * l1 + clampedDist * clampedDist - l2 * l2) / (2f * l1 * clampedDist)).coerceIn(-1f, 1f)
        val alpha = acos(cosAlpha)

        val cosKneeInterior = ((l1 * l1 + l2 * l2 - clampedDist * clampedDist) / (2f * l1 * l2)).coerceIn(-1f, 1f)
        val bendRad = (PI.toFloat() - acos(cosKneeInterior)).coerceIn(0f, 2.55f) // up to ~146 deg anatomical flex

        // Build pole plane perpendicular to dir using limb's anatomical pole hint rotated by poleAngleDeg
        val poleRad = poleAngleDeg * (PI.toFloat() / 180f)
        val basePole = limb.poleHint.normalized()
        val rotPole = Vec3(
            basePole.x * cos(poleRad) - basePole.z * sin(poleRad),
            basePole.y,
            basePole.x * sin(poleRad) + basePole.z * cos(poleRad)
        )
        // Gram-Schmidt orthogonalize pole against dir
        val proj = dir * rotPole.dot(dir)
        var bendDir = (rotPole - proj)
        if (bendDir.length() < 1e-4f) {
            bendDir = Vec3(0f, 0f, if (limb == IkLimb.LEFT_FOOT || limb == IkLimb.RIGHT_FOOT) 1f else -1f)
            bendDir = (bendDir - dir * bendDir.dot(dir))
        }
        bendDir = bendDir.normalized()

        // Desired unit direction for upper bone (from pivot to elbow/knee)
        val upperDir = (dir * cos(alpha) + bendDir * sin(alpha)).normalized()

        // Rest direction of downward limb bones is (0, -1, 0).
        // Compute Euler XYZ (rx, ry, rz) matching Math3D.createTrsMatrix (Ry * Rx * Rz) that maps (0, -1, 0) -> upperDir.
        val upperRot = downwardDirToEuler(upperDir, bendDir, limb)

        // Compute lower bone hinge bend in upper bone's local frame.
        // Arms (elbows) fold forward (+X bends -Y toward +Z? Let's verify: Rx(+theta) * (0, -1, 0) = (0, -cos, -sin) -> -Z; Rx(-theta) -> +Z!)
        // Wait: in right-handed OpenGL (+X right, +Y up, +Z toward viewer):
        // Rx(+a) maps (0, -1, 0) -> (0, -cos(a), -sin(a)) [backward/-Z], while Rx(-a) maps (0, -1, 0) -> (0, -cos(a), +sin(a)) [forward/+Z].
        // Anatomical knee bends backward (-Z -> positive Rx), elbow bends forward (+Z -> negative Rx)!
        val isLeg = limb == IkLimb.LEFT_FOOT || limb == IkLimb.RIGHT_FOOT
        val bendDeg = bendRad * RAD_TO_DEG
        val lowerRot = if (isLeg) {
            Vec3(bendDeg.coerceIn(0f, 145f), 0f, 0f)
        } else {
            Vec3((-bendDeg).coerceIn(-145f, 0f), 0f, 0f)
        }

        // Verify achieved tip for caller telemetry
        val previewPose = currentPose.toMutableMap().apply {
            put(upperBone.id, upperRot)
            put(lowerBone.id, lowerRot)
        }
        val achievedTip = skeleton.computeWorldJointPositions(previewPose)[lowerBone.id]?.second ?: targetCharLocal

        return IkSolveResult(
            limb = limb,
            upperBoneId = upperBone.id,
            lowerBoneId = lowerBone.id,
            upperRotation = upperRot,
            lowerRotation = lowerRot,
            achievedLocalTip = achievedTip
        )
    }

    /**
     * Converts a desired downward limb unit direction [d] into clean Euler angles (rx, ry, rz) in degrees
     * under Math3D's rotation order (Ry * Rx * Rz) where rest bone axis is (0, -1, 0).
     */
    private fun downwardDirToEuler(d: Vec3, bendDir: Vec3, limb: IkLimb): Vec3 {
        // Under R = Ry(ry) * Rx(rx) * Rz(rz), applying R to (0, -1, 0):
        // First Rz(rz)*(0,-1,0) = (sin(rz), -cos(rz), 0)
        // Then Rx(rx)*(sin(rz), -cos(rz), 0) = (sin(rz), -cos(rx)*cos(rz), -sin(rx)*cos(rz))
        // So with ry = 0:
        //   dx = sin(rz)
        //   dy = -cos(rx)*cos(rz)
        //   dz = -sin(rx)*cos(rz)
        val clampedX = d.x.coerceIn(-0.995f, 0.995f)
        val rz = kotlin.math.asin(clampedX)
        val rx = atan2(-d.z, -d.y)

        // Subtle axial twist (ry) for natural shoulder/hip pole orientation
        val ryDeg = when (limb) {
            IkLimb.LEFT_HAND -> (bendDir.x * -18f).coerceIn(-25f, 25f)
            IkLimb.RIGHT_HAND -> (bendDir.x * -18f).coerceIn(-25f, 25f)
            else -> 0f
        }
        return Vec3(
            x = (rx * RAD_TO_DEG).coerceIn(-160f, 160f),
            y = ryDeg,
            z = (rz * RAD_TO_DEG).coerceIn(-155f, 155f)
        )
    }

    /**
     * Mirrors a character's bone pose Left <-> Right with symmetric Y/Z negation.
     */
    fun mirrorPose(pose: Map<String, Vec3>): Map<String, Vec3> {
        val swaps = mapOf(
            "l_upper_arm" to "r_upper_arm",
            "r_upper_arm" to "l_upper_arm",
            "l_forearm" to "r_forearm",
            "r_forearm" to "l_forearm",
            "l_thigh" to "r_thigh",
            "r_thigh" to "l_thigh",
            "l_shin" to "r_shin",
            "r_shin" to "l_shin"
        )
        val out = LinkedHashMap<String, Vec3>()
        for ((boneId, rot) in pose) {
            val targetId = swaps[boneId] ?: boneId
            out[targetId] = Vec3(rot.x, -rot.y, -rot.z)
        }
        return out
    }

    /**
     * Smoothly blends [basePose] toward [targetPose] by [weight] in [0f..1f].
     */
    fun blendPoses(basePose: Map<String, Vec3>, targetPose: Map<String, Vec3>, weight: Float): Map<String, Vec3> {
        val t = weight.coerceIn(0f, 1f)
        val keys = basePose.keys + targetPose.keys
        return keys.associateWith { k ->
            val a = basePose[k] ?: Vec3.ZERO
            val b = targetPose[k] ?: Vec3.ZERO
            Vec3.lerp(a, b, t)
        }
    }
}
