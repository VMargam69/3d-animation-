package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val fps: Int,
    val totalFrames: Int,
    val objectCount: Int,
    val keyframeCount: Int,
    val sceneFilePath: String,
    val hasRecoverySnapshot: Boolean = false,
    val accentHex: Long = 0xFF6366F1
)

@Entity(tableName = "library_assets")
data class LibraryAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val assetCategory: String,     // AssetCategory.name
    val characterCategory: String, // CharacterCategory.name
    val orientationTag: String,    // OrientationFilter.name
    val archetype: String,         // CharacterArchetype.name or PRIMITIVE / IMPORTED
    val costumePreset: String,     // CostumePreset.name
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val meshCount: Int,
    val vertexCount: Int,
    val boneCount: Int,
    val animationCount: Int,
    val isPinned: Boolean = false,
    val isBuiltIn: Boolean = true,
    val sourceFormat: String = "3D Rig",
    val serializedNodePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_poses")
data class SavedPoseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,          // Core, Action, Social, Dance, Combat, Custom
    val poseJson: String,          // JSON map of boneId -> [rx, ry, rz]
    val boneCount: Int,
    val isBuiltIn: Boolean = false,
    val accentHex: Long = 0xFF22D3EE,
    val createdAt: Long = System.currentTimeMillis()
)
