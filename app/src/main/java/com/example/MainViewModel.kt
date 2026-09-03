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

    fun startSeparation(video: VideoItemEntity) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.05f
            _processingMessage.value = "Extracting vocals with AI…"

            val result = separationPipeline.processVideo(
                video = video,
                onProgress = { progress ->
                    _processingProgress.value = progress
                    _processingMessage.value = "Extracting vocals with AI… ${(progress * 100).toInt()}%"
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
        startSeparation(video)
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
