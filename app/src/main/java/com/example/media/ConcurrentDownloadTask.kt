package com.example.media

import com.example.data.model.VideoItemEntity
import java.io.File
import java.util.UUID

enum class DownloadTaskStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ConcurrentDownloadTask(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val url: String,
    val title: String = "Analyzing link...",
    val progress: Float = 0f,
    val speedKbps: Long = 0,
    val statusText: String = "Queued",
    val status: DownloadTaskStatus = DownloadTaskStatus.QUEUED,
    val resolutionOption: MediaResolutionOption? = null,
    val errorMessage: String? = null,
    val downloadedFile: File? = null,
    val videoEntity: VideoItemEntity? = null,
    val addedTime: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = status == DownloadTaskStatus.QUEUED ||
                status == DownloadTaskStatus.RESOLVING ||
                status == DownloadTaskStatus.DOWNLOADING
}
