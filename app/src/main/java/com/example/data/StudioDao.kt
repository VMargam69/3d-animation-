package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE projects SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameProject(id: String, newName: String, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM library_assets ORDER BY isPinned DESC, createdAt DESC")
    fun observeLibraryAssets(): Flow<List<LibraryAssetEntity>>

    @Query("SELECT COUNT(*) FROM library_assets")
    suspend fun countLibraryAssets(): Int

    @Query("SELECT id FROM library_assets")
    suspend fun getAllLibraryAssetIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryAsset(asset: LibraryAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryAssets(assets: List<LibraryAssetEntity>)

    @Query("UPDATE library_assets SET isPinned = :pinned WHERE id = :id")
    suspend fun setAssetPinned(id: String, pinned: Boolean)

    @Query("UPDATE library_assets SET name = :newName WHERE id = :id")
    suspend fun renameAsset(id: String, newName: String)

    @Query("DELETE FROM library_assets WHERE id = :id")
    suspend fun deleteAssetById(id: String)

    // --- Saved Pose Library ---
    @Query("SELECT * FROM saved_poses ORDER BY isBuiltIn ASC, createdAt DESC")
    fun observeSavedPoses(): Flow<List<SavedPoseEntity>>

    @Query("SELECT id FROM saved_poses")
    suspend fun getAllSavedPoseIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedPose(pose: SavedPoseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedPoses(poses: List<SavedPoseEntity>)

    @Query("DELETE FROM saved_poses WHERE id = :id")
    suspend fun deleteSavedPoseById(id: String)
}
