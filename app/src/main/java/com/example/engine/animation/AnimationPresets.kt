package com.example.engine.animation

import com.example.engine.model.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class AnimationPresetInfo(
    val id: String,
    val name: String,
    val category: String,
    val defaultDurationFrames: Int,
    val accentHex: Long,
    val description: String
)

object AnimationPresets {
    val allPresets: List<AnimationPresetInfo> = listOf(
        AnimationPresetInfo("idle", "Idle Breathe", "Core", 60, 0xFF6366F1, "Subtle torso breathing & head sway"),
        AnimationPresetInfo("walk", "Walk Cycle", "Locomotion", 40, 0xFF22D3EE, "Natural bipedal stride & arm swing"),
        AnimationPresetInfo("jogging", "Jogging", "Locomotion", 32, 0xFF10B981, "Energetic mid-tempo jog cycle"),
        AnimationPresetInfo("running", "Sprint Run", "Locomotion", 24, 0xFFF59E0B, "Forward-lean fast running gait"),
        AnimationPresetInfo("sitting", "Sitting Pose", "Posture", 60, 0xFF8B5CF6, "Seated hip bend & relaxed arm rest"),
        AnimationPresetInfo("standing", "Standing Hero", "Posture", 60, 0xFF3B82F6, "Alert heroic stance with subtle weight shift"),
        AnimationPresetInfo("talking", "Talking Gesture", "Expressive", 48, 0xFFEC4899, "Animated conversational hand & head gestures"),
        AnimationPresetInfo("waving", "Friendly Wave", "Expressive", 45, 0xFF14B8A6, "Right-arm raised greeting wave"),
        AnimationPresetInfo("jumping", "Power Jump", "Action", 36, 0xFFEF4444, "Crouch anticipation, airborne arc & landing"),
        AnimationPresetInfo("dancing", "Rhythm Dance", "Action", 48, 0xFFA855F7, "Groovy rhythmic hips, shoulders & arms")
    )

    /**
     * Evaluates procedural skeletal bone Euler rotations (degrees) + root hip offset for a normalized cycle phase [0, 1).
     */
    fun evaluatePreset(presetId: String, normalizedPhase: Float): Pair<Vec3, Map<String, Vec3>> {
        val t = (normalizedPhase % 1f + 1f) % 1f
        val rad = t * 2f * PI.toFloat()
        val s = sin(rad)
        val c = cos(rad)
        val s2 = sin(rad * 2f)

        return when (presetId) {
            "idle" -> Vec3(0f, s * 0.025f, 0f) to mapOf(
                "spine" to Vec3(s * 2.5f, c * 2f, 0f),
                "head" to Vec3(c * 3f, s * 5f, 0f),
                "l_upper_arm" to Vec3(s * 3f, 0f, -6f - s * 2f),
                "r_upper_arm" to Vec3(-s * 3f, 0f, 6f + s * 2f),
                "l_forearm" to Vec3(-8f, 0f, 0f),
                "r_forearm" to Vec3(-8f, 0f, 0f)
            )
            "walk" -> Vec3(0f, abs(s) * 0.06f, 0f) to mapOf(
                "hips" to Vec3(0f, s * 6f, c * 3f),
                "spine" to Vec3(4f, -s * 8f, 0f),
                "head" to Vec3(-2f, s * 3f, 0f),
                "l_upper_arm" to Vec3(-s * 28f, 0f, -5f),
                "l_forearm" to Vec3(-15f - maxOf(0f, -s) * 18f, 0f, 0f),
                "r_upper_arm" to Vec3(s * 28f, 0f, 5f),
                "r_forearm" to Vec3(-15f - maxOf(0f, s) * 18f, 0f, 0f),
                "l_thigh" to Vec3(s * 32f, 0f, 0f),
                "l_shin" to Vec3(maxOf(0f, -c) * 38f, 0f, 0f),
                "r_thigh" to Vec3(-s * 32f, 0f, 0f),
                "r_shin" to Vec3(maxOf(0f, c) * 38f, 0f, 0f)
            )
            "jogging" -> Vec3(0f, abs(s) * 0.11f, 0f) to mapOf(
                "hips" to Vec3(5f, s * 8f, c * 4f),
                "spine" to Vec3(10f, -s * 12f, 0f),
                "head" to Vec3(-6f, 0f, 0f),
                "l_upper_arm" to Vec3(-s * 42f, 0f, -8f),
                "l_forearm" to Vec3(-52f, 0f, 0f),
                "r_upper_arm" to Vec3(s * 42f, 0f, 8f),
                "r_forearm" to Vec3(-52f, 0f, 0f),
                "l_thigh" to Vec3(s * 45f - 8f, 0f, 0f),
                "l_shin" to Vec3(maxOf(0f, -c) * 58f + 12f, 0f, 0f),
                "r_thigh" to Vec3(-s * 45f - 8f, 0f, 0f),
                "r_shin" to Vec3(maxOf(0f, c) * 58f + 12f, 0f, 0f)
            )
            "running" -> Vec3(0f, abs(s) * 0.16f, 0f) to mapOf(
                "hips" to Vec3(10f, s * 10f, c * 5f),
                "spine" to Vec3(18f, -s * 16f, 0f),
                "head" to Vec3(-12f, 0f, 0f),
                "l_upper_arm" to Vec3(-s * 64f, 0f, -10f),
                "l_forearm" to Vec3(-76f, 0f, 0f),
                "r_upper_arm" to Vec3(s * 64f, 0f, 10f),
                "r_forearm" to Vec3(-76f, 0f, 0f),
                "l_thigh" to Vec3(s * 62f - 10f, 0f, 0f),
                "l_shin" to Vec3(maxOf(0f, -c) * 78f + 18f, 0f, 0f),
                "r_thigh" to Vec3(-s * 62f - 10f, 0f, 0f),
                "r_shin" to Vec3(maxOf(0f, c) * 78f + 18f, 0f, 0f)
            )
            "sitting" -> Vec3(0f, -0.34f + s * 0.01f, 0f) to mapOf(
                "spine" to Vec3(4f + s * 2f, 0f, 0f),
                "head" to Vec3(s * 3f, c * 6f, 0f),
                "l_upper_arm" to Vec3(-26f, 0f, -10f),
                "l_forearm" to Vec3(-34f, 0f, 0f),
                "r_upper_arm" to Vec3(-26f, 0f, 10f),
                "r_forearm" to Vec3(-34f, 0f, 0f),
                "l_thigh" to Vec3(-84f, 0f, -4f),
                "l_shin" to Vec3(84f, 0f, 0f),
                "r_thigh" to Vec3(-84f, 0f, 4f),
                "r_shin" to Vec3(84f, 0f, 0f)
            )
            "standing" -> Vec3(0f, s * 0.015f, 0f) to mapOf(
                "hips" to Vec3(0f, c * 3f, s * 2f),
                "spine" to Vec3(-4f, -c * 3f, -s * 2f),
                "head" to Vec3(2f, s * 8f, 0f),
                "l_upper_arm" to Vec3(6f, 0f, -14f),
                "r_upper_arm" to Vec3(6f, 0f, 14f),
                "l_forearm" to Vec3(-18f, 0f, 8f),
                "r_forearm" to Vec3(-18f, 0f, -8f)
            )
            "talking" -> Vec3(0f, s2 * 0.02f, 0f) to mapOf(
                "spine" to Vec3(s * 4f, c * 6f, s2 * 3f),
                "head" to Vec3(s2 * 7f, s * 10f, c * 4f),
                "l_upper_arm" to Vec3(-36f + s * 14f, 0f, -16f),
                "l_forearm" to Vec3(-58f + c * 22f, 0f, 14f),
                "r_upper_arm" to Vec3(-42f - c * 16f, 0f, 18f),
                "r_forearm" to Vec3(-62f + s2 * 24f, 0f, -12f)
            )
            "waving" -> Vec3(0f, s * 0.02f, 0f) to mapOf(
                "spine" to Vec3(0f, -6f, -5f - s * 3f),
                "head" to Vec3(-4f, 8f, s * 6f),
                "l_upper_arm" to Vec3(4f, 0f, -10f),
                "r_upper_arm" to Vec3(-12f, 0f, 132f + s * 14f),
                "r_forearm" to Vec3(-18f, 0f, 22f + s2 * 28f)
            )
            "jumping" -> {
                val lift = if (t in 0.22f..0.78f) {
                    val u = (t - 0.22f) / 0.56f
                    sin(u * PI.toFloat()) * 0.68f
                } else {
                    -0.16f * sin((if (t < 0.22f) t / 0.22f else (1f - t) / 0.22f) * PI.toFloat())
                }
                val airborne = lift > 0.05f
                Vec3(0f, lift, 0f) to mapOf(
                    "spine" to Vec3(if (airborne) -10f else 16f, 0f, 0f),
                    "l_upper_arm" to Vec3(if (airborne) -120f else 28f, 0f, -16f),
                    "r_upper_arm" to Vec3(if (airborne) -120f else 28f, 0f, 16f),
                    "l_thigh" to Vec3(if (airborne) -48f else -28f, 0f, 0f),
                    "l_shin" to Vec3(if (airborne) 65f else 32f, 0f, 0f),
                    "r_thigh" to Vec3(if (airborne) -48f else -28f, 0f, 0f),
                    "r_shin" to Vec3(if (airborne) 65f else 32f, 0f, 0f)
                )
            }
            "dancing" -> Vec3(s * 0.08f, abs(s2) * 0.09f, 0f) to mapOf(
                "hips" to Vec3(0f, s * 18f, c * 10f),
                "spine" to Vec3(s2 * 8f, -s * 14f, -c * 10f),
                "head" to Vec3(s2 * 8f, c * 12f, s * 10f),
                "l_upper_arm" to Vec3(-52f + s * 35f, 0f, -45f - c * 28f),
                "l_forearm" to Vec3(-50f + s2 * 26f, 0f, 0f),
                "r_upper_arm" to Vec3(-52f - s * 35f, 0f, 45f + c * 28f),
                "r_forearm" to Vec3(-50f - s2 * 26f, 0f, 0f),
                "l_thigh" to Vec3(s * 24f - 10f, 0f, -8f),
                "l_shin" to Vec3(abs(c) * 28f, 0f, 0f),
                "r_thigh" to Vec3(-s * 24f - 10f, 0f, 8f),
                "r_shin" to Vec3(abs(s) * 28f, 0f, 0f)
            )
            else -> Vec3.ZERO to emptyMap()
        }
    }
}
