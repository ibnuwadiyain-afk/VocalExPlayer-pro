package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VideoStatus {
    NEW,
    PROCESSING,
    READY,
    ERROR;

    companion object {
        fun fromString(value: String): VideoStatus {
            return when (value.lowercase()) {
                "processing" -> PROCESSING
                "ready" -> READY
                "error" -> ERROR
                else -> NEW
            }
        }
    }
}

@Entity(tableName = "videos")
data class VideoItemEntity(
    @PrimaryKey val id: String,
    val videoUri: String,
    val title: String,
    val duration: Long,
    val status: VideoStatus = VideoStatus.NEW,
    val vocalUri: String? = null,
    val errorMessage: String? = null,
    val progress: Float = 0f,
    val dateAdded: Long = System.currentTimeMillis()
)
