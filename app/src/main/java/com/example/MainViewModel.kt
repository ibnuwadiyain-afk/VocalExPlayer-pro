package com.example

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SampleVideoGenerator
import com.example.data.db.AppDatabase
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
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

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingMessage = MutableStateFlow("Extracting vocals with AI…")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportResult = MutableStateFlow<com.example.export.ExportResult?>(null)
    val exportResult: StateFlow<com.example.export.ExportResult?> = _exportResult.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

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

    fun importSampleVideo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val sampleFile = SampleVideoGenerator.getOrCreateSampleVideo(context)
                val videoId = "sample_" + UUID.randomUUID().toString().substring(0, 6)

                val sampleVideo = VideoItemEntity(
                    id = videoId,
                    videoUri = sampleFile.absolutePath,
                    title = "Sample Vocal Track (UVR Demo)",
                    duration = 4000L,
                    status = VideoStatus.NEW
                )
                videoDao.insertVideo(sampleVideo)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error creating sample video", e)
            }
        }
    }

    fun startSeparation(video: VideoItemEntity, forceRecompute: Boolean = false) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.05f
            _processingMessage.value = "Extracting vocals with AI (${_customModelName.value})…"

            val result = separationPipeline.processVideo(
                video = video,
                customModelPath = _customModelPath.value,
                accelerationMode = _accelerationMode.value,
                forceRecompute = forceRecompute,
                onProgress = { progress ->
                    _processingProgress.value = progress
                    _processingMessage.value = "Extracting vocals with UVR AI (${_accelerationMode.value.displayName})… ${(progress * 100).toInt()}%"
                }
            )

            // Refresh selected video state
            val updated = videoDao.getVideoById(video.id)
            if (_selectedVideo.value?.id == video.id) {
                _selectedVideo.value = updated
            }

            _isProcessing.value = false
        }
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
