package com.example.model

data class ModelEntity(
    val id: String,
    val type: ModelType,
    val displayName: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sizeDisplay: String,
    val isBundled: Boolean = false,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadSpeedKbps: Long = 0L,
    val localPath: String? = null,
    val errorMessage: String? = null
)
