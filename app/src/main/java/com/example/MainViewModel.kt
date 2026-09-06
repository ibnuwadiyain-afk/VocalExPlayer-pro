package com.example

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import com.example.media.DownloadProgressState
import com.example.media.ExtractedVideoInfo
import com.example.media.SocialMediaVideoProcessor
import com.example.onnx.AudioSeparationPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val videoDao = db.videoDao()
    private val separationPipeline = AudioSeparationPipeline(application, videoDao)

    private val prefs = application.getSharedPreferences("model_prefs", android.content.Context.MODE_PRIVATE)

    private val _customModelPath = MutableStateFlow<String?>(prefs.getString("custom_model_path", null))
    val customModelPath: StateFlow<String?> = _customModelPath.asStateFlow()

    private val _customModelName = MutableStateFlow<String>(
        prefs.getString("custom_model_name", null) ?: "UVR-MDX-NET 1 (Built-in 29.7MB Neural Network)"
    )
    val customModelName: StateFlow<String> = _customModelName.asStateFlow()

    private val _modelInfoMessage = MutableStateFlow<String?>(null)
    val modelInfoMessage: StateFlow<String?> = _modelInfoMessage.asStateFlow()

    private val _accelerationMode = MutableStateFlow<com.example.onnx.AccelerationMode>(
        com.example.onnx.AccelerationMode.fromId(prefs.getString("acceleration_mode", com.example.onnx.AccelerationMode.TURBO.id))
    )
    val accelerationMode: StateFlow<com.example.onnx.AccelerationMode> = _accelerationMode.asStateFlow()

    val hardwareBackend: String
        get() = separationPipeline.activeHardwareBackend

    fun setAccelerationMode(mode: com.example.onnx.AccelerationMode) {
        _accelerationMode.value = mode
        prefs.edit().putString("acceleration_mode", mode.id).apply()
        _modelInfoMessage.value = "Acceleration set to: ${mode.displayName} (${mode.speedBadge})"
    }

    val videoList: StateFlow<List<VideoItemEntity>> = videoDao.getAllVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedVideo = MutableStateFlow<VideoItemEntity?>(null)
    val selectedVideo: StateFlow<VideoItemEntity?> = _selectedVideo.asStateFlow()

    // Delegate processing state & elapsed timer to singleton VocalProcessingManager
    val isProcessing: StateFlow<Boolean> = com.example.service.VocalProcessingManager.isProcessing
    val processingProgress: StateFlow<Float> = com.example.service.VocalProcessingManager.progress
    val processingMessage: StateFlow<String> = com.example.service.VocalProcessingManager.message
    val processingElapsedSeconds: StateFlow<Long> = com.example.service.VocalProcessingManager.elapsedSeconds
    val processingElapsedTimeFormatted: StateFlow<String> = com.example.service.VocalProcessingManager.elapsedTimeFormatted
    val processingEstimatedRemainingFormatted: StateFlow<String?> = com.example.service.VocalProcessingManager.estimatedRemainingFormatted
    val isBackgroundProtected: StateFlow<Boolean> = com.example.service.VocalProcessingManager.isBackgroundProtected
    val activeProcessingVideoId: StateFlow<String?> = com.example.service.VocalProcessingManager.activeVideoId
    val activeProcessingVideoTitle: StateFlow<String?> = com.example.service.VocalProcessingManager.activeVideoTitle

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportResult = MutableStateFlow<com.example.export.ExportResult?>(null)
    val exportResult: StateFlow<com.example.export.ExportResult?> = _exportResult.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    private val socialMediaProcessor = SocialMediaVideoProcessor(application)

    private val _downloadProgressState = MutableStateFlow<DownloadProgressState?>(null)
    val downloadProgressState: StateFlow<DownloadProgressState?> = _downloadProgressState.asStateFlow()

    private val _isDownloadingMedia = MutableStateFlow(false)
    val isDownloadingMedia: StateFlow<Boolean> = _isDownloadingMedia.asStateFlow()

    private val _downloadElapsedFormatted = MutableStateFlow("00:00")
    val downloadElapsedFormatted: StateFlow<String> = _downloadElapsedFormatted.asStateFlow()

    private val _downloadErrorMessage = MutableStateFlow<String?>(null)
    val downloadErrorMessage: StateFlow<String?> = _downloadErrorMessage.asStateFlow()

    private val _sharedMediaUrl = MutableStateFlow<String?>(null)
    val sharedMediaUrl: StateFlow<String?> = _sharedMediaUrl.asStateFlow()

    fun handleIncomingShareUrl(rawText: String) {
        val extractedUrl = socialMediaProcessor.extractUrlFromText(rawText) ?: rawText.trim()
        if (extractedUrl.isNotBlank()) {
            _sharedMediaUrl.value = extractedUrl
        }
    }

    fun clearSharedMediaUrl() {
        _sharedMediaUrl.value = null
    }

    fun processSocialMediaUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingMedia.value = true
            _downloadErrorMessage.value = null
            _downloadProgressState.value = DownloadProgressState.Resolving("Analyzing link…")
            _downloadElapsedFormatted.value = "00:00"

            val startDownloadTime = android.os.SystemClock.elapsedRealtime()
            val downloadTimerJob = viewModelScope.launch(Dispatchers.Default) {
                while (_isDownloadingMedia.value) {
                    val elapsed = (android.os.SystemClock.elapsedRealtime() - startDownloadTime) / 1000
                    _downloadElapsedFormatted.value = com.example.service.VocalProcessingManager.formatSeconds(elapsed)
                    kotlinx.coroutines.delay(500)
                }
            }

            val result = socialMediaProcessor.processAndDownload(url) { state ->
                _downloadProgressState.value = state
            }

            downloadTimerJob.cancel()

            result.onSuccess { info ->
                val videoId = UUID.randomUUID().toString().substring(0, 8)
                val newVideo = VideoItemEntity(
                    id = videoId,
                    title = info.title,
                    videoUri = info.file.toURI().toString(),
                    duration = info.durationMs,
                    status = VideoStatus.NEW,
                    dateAdded = System.currentTimeMillis()
                )
                videoDao.insertVideo(newVideo)
                _isDownloadingMedia.value = false
                _downloadProgressState.value = null
                _sharedMediaUrl.value = null
                withContext(Dispatchers.Main) {
                    selectVideo(newVideo)
                }
            }.onFailure { error ->
                _isDownloadingMedia.value = false
                _downloadErrorMessage.value = error.localizedMessage ?: "Failed to process video from link."
            }
        }
    }

    fun dismissDownloadDialog() {
        if (!_isDownloadingMedia.value) {
            _downloadProgressState.value = null
            _downloadErrorMessage.value = null
            _sharedMediaUrl.value = null
        }
    }

    fun clearDownloadError() {
        _downloadErrorMessage.value = null
    }

    fun exportVideo(video: VideoItemEntity) {
        val vocalPath = video.vocalUri
        if (vocalPath.isNullOrBlank() || !java.io.File(vocalPath).exists()) {
            _exportError.value = "Vocal track is not ready yet. Please ensure vocal separation finishes first."
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0.05f
            _exportError.value = null
            _exportResult.value = null

            val context = getApplication<Application>()
            val result = com.example.export.VideoVocalsExporter.exportVideoWithVocals(
                context = context,
                videoUriString = video.videoUri,
                vocalWavPath = vocalPath,
                outputTitle = video.title,
                onProgress = { progress ->
                    _exportProgress.value = progress
                }
            )

            result.onSuccess { res ->
                _exportResult.value = res
                _isExporting.value = false
            }.onFailure { err ->
                _exportError.value = err.localizedMessage ?: "Video export failed"
                _isExporting.value = false
            }
        }
    }

    fun dismissExportDialog() {
        _isExporting.value = false
        _exportResult.value = null
        _exportError.value = null
        _exportProgress.value = 0f
    }

    fun importCustomModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val modelsDir = File(context.filesDir, "custom_models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom_model.onnx"
                val dest = File(modelsDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }

                if (dest.exists() && dest.length() > 10_000L) {
                    val sizeMb = dest.length() / (1024 * 1024)
                    val name = "$fileName (${sizeMb} MB)"
                    prefs.edit()
                        .putString("custom_model_path", dest.absolutePath)
                        .putString("custom_model_name", name)
                        .apply()
                    _customModelPath.value = dest.absolutePath
                    _customModelName.value = name
                    _modelInfoMessage.value = "Active model updated: $name"
                    Log.i("MainViewModel", "Loaded custom model: ${dest.absolutePath} ($sizeMb MB)")
                } else {
                    _modelInfoMessage.value = "Selected file is invalid or too small to be an ONNX model."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to import custom model", e)
                _modelInfoMessage.value = "Failed to import model: ${e.localizedMessage}"
            }
        }
    }

    fun resetToBuiltInModel() {
        prefs.edit()
            .remove("custom_model_path")
            .remove("custom_model_name")
            .apply()
        _customModelPath.value = null
        _customModelName.value = "UVR-MDX-NET 1 (Built-in 29.7MB Neural Network)"
        _modelInfoMessage.value = "Reset to built-in UVR-MDX-NET 1 model."
    }

    fun clearModelInfoMessage() {
        _modelInfoMessage.value = null
    }

    fun selectVideo(video: VideoItemEntity) {
        _selectedVideo.value = video
        if (video.status == VideoStatus.NEW) {
            startSeparation(video)
        }
    }

    fun clearSelectedVideo() {
        _selectedVideo.value = null
    }

    fun importVideoFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 15_000L
                val titleMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)

                // Copy video to app internal storage for reliable access
                val videoDir = File(context.filesDir, "videos")
                if (!videoDir.exists()) videoDir.mkdirs()
                val videoId = UUID.randomUUID().toString().substring(0, 8)
                val localFile = File(videoDir, "video_$videoId.mp4")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val title = if (!titleMeta.isNullOrBlank()) {
                    titleMeta
                } else {
                    uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Video $videoId"
                }

                retriever.release()

                val newVideo = VideoItemEntity(
                    id = videoId,
                    videoUri = localFile.absolutePath,
                    title = title,
                    duration = durationMs,
                    status = VideoStatus.NEW
                )

                videoDao.insertVideo(newVideo)
                Log.i("MainViewModel", "Imported video $videoId: $title")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error importing video", e)
            }
        }
    }

    fun startSeparation(video: VideoItemEntity, forceRecompute: Boolean = false) {
        val context = getApplication<Application>()
        com.example.service.VocalProcessingManager.startSeparation(
            context = context,
            video = video,
            customModelPath = _customModelPath.value,
            accelerationMode = _accelerationMode.value,
            forceRecompute = forceRecompute,
            onComplete = {
                viewModelScope.launch {
                    val updated = videoDao.getVideoById(video.id)
                    if (_selectedVideo.value?.id == video.id) {
                        _selectedVideo.value = updated
                    }
                }
            }
        )
    }

    fun cancelSeparation() {
        val context = getApplication<Application>()
        com.example.service.VocalProcessingManager.cancelActiveProcessing(context)
    }

    fun retrySeparation(video: VideoItemEntity) {
        startSeparation(video, forceRecompute = true)
    }

    fun reprocessVideo(video: VideoItemEntity) {
        startSeparation(video, forceRecompute = true)
    }

    fun deleteVideo(video: VideoItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete vocal track if cached
            video.vocalUri?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            // Delete video if in internal files
            val vFile = File(video.videoUri)
            if (vFile.exists() && vFile.absolutePath.contains(getApplication<Application>().filesDir.absolutePath)) {
                vFile.delete()
            }
            videoDao.deleteById(video.id)
            if (_selectedVideo.value?.id == video.id) {
                withContext(Dispatchers.Main) {
                    _selectedVideo.value = null
                }
            }
        }
    }
}
