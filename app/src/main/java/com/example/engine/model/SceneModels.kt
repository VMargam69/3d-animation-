package com.example.engine.model

import java.util.UUID

enum class EditorMode(val label: String) {
    OBJECT("Object"),
    EDIT("Edit"),
    POSE("Rig / Pose")
}

enum class ShadingMode(val label: String) {
    SOLID("Solid"),
    MATERIAL("Material"),
    WIREFRAME("Wire")
}

enum class TransformTool(val label: String) {
    SELECT("Select"),
    MOVE("Move"),
    ROTATE("Rotate"),
    SCALE("Scale")
}

enum class TransformAxis(val label: String) {
    ALL("Free"),
    X("X"),
    Y("Y"),
    Z("Z")
}

enum class EditSubMode(val label: String) {
    VERTEX("Vertex"),
    EDGE("Edge"),
    FACE("Face")
}

enum class CameraPreset(val label: String) {
    FREE("3D"),
    FRONT("Front"),
    BACK("Back"),
    LEFT("Left"),
    RIGHT("Right"),
    TOP("Top"),
    BOTTOM("Bottom"),
    CAMERA_VIEW("Cam")
}

enum class NodeType {
    MESH,
    CHARACTER,
    LIGHT,
    CAMERA
}

enum class PrimitiveKind(val label: String) {
    CUBE("Cube"),
    SPHERE("Sphere"),
    UV_SPHERE("UV Sphere"),
    CYLINDER("Cylinder"),
    CONE("Cone"),
    PLANE("Plane"),
    TORUS("Torus"),
    CAPSULE("Capsule"),
    CIRCLE("Circle"),
    CUSTOM("Custom Mesh")
}

enum class LightType(val label: String) {
    POINT("Point"),
    DIRECTIONAL("Directional"),
    SPOT("Spot")
}

enum class InterpolationType(val label: String) {
    LINEAR("Linear"),
    EASE_IN_OUT("Ease"),
    STEP("Step")
}

enum class KeyframeProperty(val label: String) {
    POSITION("Position"),
    ROTATION("Rotation"),
    SCALE("Scale"),
    VISIBILITY("Visibility"),
    CAMERA_FOV("Camera FOV"),
    BONE_ROTATION("Bone Rotation")
}

enum class CharacterCategory(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    LATEST("Latest"),
    DESI("Desi"),
    WESTERN("Western"),
    INDONESIAN("Indonesian"),
    VILLAGE("Village"),
    BIRDS("Birds"),
    ANIMALS("Animals"),
    VEHICLES("Vehicles"),
    RELIGIOUS("Religious"),
    HORROR("Horror"),
    CUSTOM("Custom"),
    BLENDER("Blender")
}

enum class OrientationFilter(val label: String) {
    ALL("All Angles"),
    FRONT("Front"),
    THREE_QUARTER("3/4 View"),
    SIDE("Side"),
    RIG_360("360° Rig")
}

enum class AssetCategory(val label: String) {
    ALL("All"),
    CHARACTERS("3D Characters"),
    MODELS("3D Models"),
    PROPS("Props"),
    ENVIRONMENTS("Environments"),
    VEHICLES("Vehicles"),
    COSTUMES("Clothing"),
    MATERIALS("Materials"),
    ANIMATIONS("Animations"),
    SKELETONS("Skeletons"),
    CAMERAS("Cameras"),
    LIGHTS("Lights")
}

enum class CostumePreset(
    val label: String,
    val primaryColor: Long,
    val secondaryColor: Long,
    val accentColor: Long,
    val accessory: AccessoryKind
) {
    FORMAL("Formal Suit", 0xFF1F2937, 0xFFF8FAFC, 0xFF6366F1, AccessoryKind.NONE),
    INDIAN_TRADITIONAL("Indian Traditional", 0xFFDC2626, 0xFFF59E0B, 0xFFFEF3C7, AccessoryKind.TURBAN),
    VILLAGE("Village Wear", 0xFFD97706, 0xFFFDE68A, 0xFF78350F, AccessoryKind.STAFF),
    SCHOOL_UNIFORM("School Uniform", 0xFF1E3A8A, 0xFFF1F5F9, 0xFFEF4444, AccessoryKind.BACKPACK),
    POLICE("Police Uniform", 0xFF926E3C, 0xFF451A03, 0xFFFACC15, AccessoryKind.CAP),
    DOCTOR("Doctor Coat", 0xFFF1F5F9, 0xFF0EA5E9, 0xFF22D3EE, AccessoryKind.GLASSES),
    HISTORICAL("Historical Armor", 0xFF64748B, 0xFF991B1B, 0xFFF59E0B, AccessoryKind.CAPE),
    MYTHOLOGICAL("Mythological", 0xFFF59E0B, 0xFFEA580C, 0xFF38BDF8, AccessoryKind.CROWN),
    FANTASY("Fantasy Mage", 0xFF4C1D95, 0xFF22D3EE, 0xFFA855F7, AccessoryKind.STAFF),
    ORIGINAL_HERO("Original Hero", 0xFF4338CA, 0xFF22D3EE, 0xFFEC4899, AccessoryKind.CAPE)
}

enum class AccessoryKind(val label: String) {
    NONE("None"),
    TURBAN("Turban"),
    CAP("Cap / Helmet"),
    CROWN("Crown"),
    CAPE("Cape"),
    BACKPACK("Backpack"),
    STAFF("Staff"),
    GLASSES("Visor / Glasses")
}

enum class ExportFormat(val label: String, val extension: String) {
    MP4("MP4 Video (H.264)", "mp4"),
    GLB("GLB 3D Scene (glTF 2.0)", "glb")
}

enum class RenderQuality(val label: String, val bitrateMbps: Int) {
    LOW("Low (Fast / Cool)", 3),
    MEDIUM("Medium (Balanced)", 6),
    HIGH("High (Sharp)", 12)
}

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    SD_480("480p (854×480)", 854, 480),
    HD_720("720p (1280×720)", 1280, 720),
    FHD_1080("1080p (1920×1080)", 1920, 1080)
}

data class Transform3D(
    val position: Vec3 = Vec3.ZERO,
    val rotation: Vec3 = Vec3.ZERO, // Euler XYZ in degrees
    val scale: Vec3 = Vec3.ONE
) {
    fun toMatrix(): FloatArray = Math3D.createTrsMatrix(position, rotation, scale)
}

data class MaterialData(
    val name: String = "Studio Material",
    val baseColor: Long = 0xFF6366F1,
    val roughness: Float = 0.45f,
    val metallic: Float = 0.15f,
    val opacity: Float = 1.0f,
    val emissionColor: Long = 0xFF000000,
    val emissionStrength: Float = 0.0f,
    val texturePath: String? = null,
    val uvScale: Float = 1.0f
)

data class MeshData(
    val vertices: FloatArray, // x,y,z per vertex
    val normals: FloatArray,  // nx,ny,nz per vertex
    val uvs: FloatArray,      // u,v per vertex
    val indices: IntArray,    // triangles (3 ints per face)
    val jointIndices: IntArray = IntArray(vertices.size / 3 * 4) { 0 },
    val jointWeights: FloatArray = FloatArray(vertices.size / 3 * 4) { idx -> if (idx % 4 == 0) 1f else 0f },
    val colors: FloatArray = FloatArray(vertices.size) { 1f }, // r,g,b per vertex
    val useVertexColors: Boolean = false,
    val version: Long = System.nanoTime()
) {
    val vertexCount: Int get() = vertices.size / 3
    val triangleCount: Int get() = indices.size / 3

    fun computeLocalBounds(): Aabb3D {
        if (vertexCount == 0) return Aabb3D(Vec3(-0.5f, -0.5f, -0.5f), Vec3(0.5f, 0.5f, 0.5f))
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (i in 0 until vertexCount) {
            val x = vertices[i * 3]
            val y = vertices[i * 3 + 1]
            val z = vertices[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        return Aabb3D(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
    }

    fun bumpVersion(): MeshData = copy(version = System.nanoTime())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshData) return false
        return version == other.version && vertices.size == other.vertices.size && indices.size == other.indices.size
    }

    override fun hashCode(): Int = version.hashCode()
}

data class BoneNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val localOffset: Vec3 = Vec3.ZERO, // Offset from parent head (or root origin)
    val length: Float = 0.45f,
    val poseRotation: Vec3 = Vec3.ZERO // Local Euler XYZ rotation in degrees
)

data class SkeletonData(
    val bones: List<BoneNode> = emptyList()
) {
    companion object {
        const val MAX_BONES = 32
    }

    fun computeWorldJointPositions(poseOverrides: Map<String, Vec3> = emptyMap()): Map<String, Pair<Vec3, Vec3>> {
        val worldMatrices = computeBoneWorldMatrices(poseOverrides)
        val result = mutableMapOf<String, Pair<Vec3, Vec3>>()
        for (bone in bones) {
            val mat = worldMatrices[bone.id] ?: Math3D.identity()
            val head = Math3D.transformPoint(mat, Vec3.ZERO)
            val tail = Math3D.transformPoint(mat, Vec3(0f, bone.length, 0f))
            result[bone.id] = head to tail
        }
        return result
    }

    fun computeBoneWorldMatrices(poseOverrides: Map<String, Vec3> = emptyMap()): Map<String, FloatArray> {
        val cache = LinkedHashMap<String, FloatArray>()
        val byId = bones.associateBy { it.id }

        fun eval(bone: BoneNode): FloatArray {
            cache[bone.id]?.let { return it }
            val rot = poseOverrides[bone.id] ?: bone.poseRotation
            val local = Math3D.createTrsMatrix(bone.localOffset, rot, Vec3.ONE)
            val parent = bone.parentId?.let { byId[it] }
            val world = if (parent != null) Math3D.multiply(eval(parent), local) else local
            cache[bone.id] = world
            return world
        }
        bones.forEach { eval(it) }
        return cache
    }

    fun computeSkinningPalette(poseOverrides: Map<String, Vec3> = emptyMap()): FloatArray {
        val palette = FloatArray(MAX_BONES * 16)
        for (i in 0 until MAX_BONES) {
            android.opengl.Matrix.setIdentityM(palette, i * 16)
        }
        if (bones.isEmpty()) return palette

        val restWorlds = computeBoneWorldMatrices(bones.associate { it.id to Vec3.ZERO })
        val poseWorlds = computeBoneWorldMatrices(poseOverrides)

        bones.take(MAX_BONES).forEachIndexed { idx, bone ->
            val invBind = Math3D.invert(restWorlds[bone.id] ?: Math3D.identity())
            val poseWorld = poseWorlds[bone.id] ?: Math3D.identity()
            val skinMat = Math3D.multiply(poseWorld, invBind)
            System.arraycopy(skinMat, 0, palette, idx * 16, 16)
        }
        return palette
    }
}

data class LightData(
    val lightType: LightType = LightType.POINT,
    val color: Long = 0xFFFFFFFF,
    val intensity: Float = 1.4f,
    val range: Float = 16f,
    val castShadows: Boolean = true
)

data class CameraData(
    val fov: Float = 45f,
    val nearClip: Float = 0.1f,
    val farClip: Float = 120f,
    val isActive: Boolean = true
)

data class SceneNode(
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    val name: String,
    val nodeType: NodeType,
    val primitiveKind: PrimitiveKind = PrimitiveKind.CUSTOM,
    val transform: Transform3D = Transform3D(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val mesh: MeshData? = null,
    val material: MaterialData = MaterialData(),
    val skeleton: SkeletonData? = null,
    val light: LightData? = null,
    val camera: CameraData? = null,
    val categoryTag: String = CharacterCategory.CUSTOM.name,
    val sourceFormat: String? = null
)

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val targetNodeId: String,
    val targetBoneId: String? = null,
    val frame: Int,
    val property: KeyframeProperty,
    val vec3Value: Vec3 = Vec3.ZERO,
    val floatValue: Float = 45f,
    val boolValue: Boolean = true,
    val interpolation: InterpolationType = InterpolationType.EASE_IN_OUT
)

data class AnimationClipTrack(
    val id: String = UUID.randomUUID().toString(),
    val targetNodeId: String,
    val clipName: String,
    val presetId: String,
    val startFrame: Int = 0,
    val durationFrames: Int = 60,
    val speed: Float = 1.0f,
    val loop: Boolean = true,
    val enabled: Boolean = true,
    val colorHex: Long = 0xFF6366F1
)

data class TimelineState(
    val fps: Int = 30,
    val totalFrames: Int = 150,
    val currentFrame: Float = 0f,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = true,
    val autoKey: Boolean = false,
    val zoom: Float = 1.0f,
    val keyframes: List<Keyframe> = emptyList(),
    val clips: List<AnimationClipTrack> = emptyList(),
    val hiddenTrackNodeIds: Set<String> = emptySet(),
    val lockedTrackNodeIds: Set<String> = emptySet()
)

data class ViewportCameraState(
    val target: Vec3 = Vec3(0f, 1.0f, 0f),
    val yawDeg: Float = 32f,
    val pitchDeg: Float = 20f,
    val distance: Float = 6.2f,
    val orthographic: Boolean = false,
    val preset: CameraPreset = CameraPreset.FREE
)

data class SceneDocument(
    val projectId: String = UUID.randomUUID().toString(),
    val projectName: String = "Untitled Scene",
    val nodes: List<SceneNode> = emptyList(),
    val timeline: TimelineState = TimelineState(),
    val viewportCamera: ViewportCameraState = ViewportCameraState(),
    val ambientColor: Long = 0xFF1A2035,
    val ambientIntensity: Float = 0.35f,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ImportInspectionReport(
    val fileName: String,
    val format: String,
    val supported: Boolean,
    val meshCount: Int = 0,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val materialCount: Int = 0,
    val textureCount: Int = 0,
    val boneCount: Int = 0,
    val animationCount: Int = 0,
    val notes: List<String> = emptyList(),
    val importedNode: SceneNode? = null
)
