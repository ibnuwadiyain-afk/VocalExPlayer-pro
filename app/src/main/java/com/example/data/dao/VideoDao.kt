package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: String): VideoItemEntity?

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    fun observeVideoById(id: String): Flow<VideoItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoItemEntity)

    @Update
    suspend fun updateVideo(video: VideoItemEntity)

    @Query("UPDATE videos SET status = :status, progress = :progress, vocalUri = :vocalUri, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateSeparationStatus(
        id: String,
        status: VideoStatus,
        progress: Float,
        vocalUri: String?,
        errorMessage: String?
    )

    @Delete
    suspend fun deleteVideo(video: VideoItemEntity)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: String)
}
