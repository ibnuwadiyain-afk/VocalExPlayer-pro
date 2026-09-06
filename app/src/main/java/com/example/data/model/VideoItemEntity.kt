package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val videoUri: String,
    val durationMs: Long = 0,
    val isSeparated: Boolean = false,
    val vocalsUri: String? = null,
    val accompanimentUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sourcePlatform: String? = null
)
