package com.example

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.VideoItemEntity
import com.example.export.VideoVocalsExporter
import com.example.media.DownloadProgressState
import com.example.media.SocialMediaVideoProcessor
import com.example.onnx.AccelerationMode
import com.example.service.VocalProcessingManager
import com.example.ui.components.ExportAudioChoice
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val db = AppDatabase.getDatabase(application)
    private val videoDao = db.videoDao()
    private val socialProcessor = SocialMediaVideoProcessor(application)
    private val videoExporter = VideoVocalsExporter(application)

    val videoList: StateFlow<List<VideoItemEntity>> = videoDao.getAllVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Foreground service / processing state delegated to VocalProcessingManager
    val isProcessing = VocalProcessingManager.isProcessing
    val progress = VocalProcessingManager.progressFlow
    val statusMessage = VocalProcessingManager.statusMessageFlow
    val activeVideoTitle = VocalProcessingManager.activeVideoTitle
    val elapsedSeconds = VocalProcessingManager.elapsedSeconds

    private val _accelerationMode = MutableStateFlow(AccelerationMode.AUTO)
    val accelerationMode = _accelerationMode.asStateFlow()

    private val _customModelPath = MutableStateFlow<String?>(null)
    val customModelPath = _customModelPath.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress = _exportProgress.asStateFlow()

    fun updateAccelerationMode(mode: AccelerationMode) {
        _accelerationMode.value = mode
    }

    fun startSeparation(video: VideoItemEntity) {
        val context = getApplication<Application>()
        VocalProcessingManager.startSeparation(
            context = context,
            video = video,
            customModelPath = _customModelPath.value,
            mode = _accelerationMode.value
        )
    }

    fun cancelSeparation() {
        val context = getApplication<Application>()
        VocalProcessingManager.cancelActiveSeparation(context)
    }

    fun deleteVideo(video: VideoItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete local video and separated audio files
            try {
                if (video.videoUri.startsWith("file://")) {
                    File(Uri.parse(video.videoUri).path ?: "").delete()
                }
                video.vocalsUri?.let { File(Uri.parse(it).path ?: "").delete() }
                video.accompanimentUri?.let { File(Uri.parse(it).path ?: "").delete() }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting video files: ${e.message}")
            }
            videoDao.deleteVideo(video)
        }
    }

    fun importVideoFromUri(sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val filename = getFileNameFromUri(context, sourceUri) ?: "Video_${System.currentTimeMillis()}.mp4"
                val destFile = File(context.filesDir, "videos/$filename").apply {
                    parentFile?.mkdirs()
                }

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val duration = retrieveVideoDuration(context, Uri.fromFile(destFile))
                val title = filename.substringBeforeLast(".")

                val entity = VideoItemEntity(
                    title = title,
                    videoUri = Uri.fromFile(destFile).toString(),
                    durationMs = duration,
                    sourcePlatform = "Local File"
                )
                val id = videoDao.insertVideo(entity)
                Log.i(TAG, "Imported video with id=$id, path=${destFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import video: ${e.message}", e)
            }
        }
    }

    fun processSocialMediaUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            socialProcessor.processAndDownload(url) { state ->
                when (state) {
                    is DownloadProgressState.Resolving -> Log.d(TAG, state.message)
                    is DownloadProgressState.Downloading -> Log.d(TAG, "Progress: ${(state.progress * 100).toInt()}%")
                    is DownloadProgressState.Completed -> {
                        val duration = retrieveVideoDuration(context, Uri.fromFile(state.file))
                        val entity = VideoItemEntity(
                            title = state.file.nameWithoutExtension,
                            videoUri = Uri.fromFile(state.file).toString(),
                            durationMs = duration,
                            sourcePlatform = socialProcessor.detectPlatform(url)
                        )
                        viewModelScope.launch(Dispatchers.IO) {
                            videoDao.insertVideo(entity)
                        }
                    }
                    is DownloadProgressState.Failed -> Log.e(TAG, "Download failed: ${state.error}")
                }
            }
        }
    }

    fun exportVideo(video: VideoItemEntity, choice: ExportAudioChoice, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            _exportProgress.value = 0f
            val context = getApplication<Application>()

            try {
                val audioUri = when (choice) {
                    ExportAudioChoice.VOCALS_ONLY -> video.vocalsUri?.let { Uri.parse(it) }
                    ExportAudioChoice.KARAOKE_ONLY -> video.accompanimentUri?.let { Uri.parse(it) }
                    ExportAudioChoice.ORIGINAL -> Uri.parse(video.videoUri)
                } ?: Uri.parse(video.videoUri)

                val exportDir = File(context.filesDir, "exported_videos").apply { mkdirs() }
                val exportFile = File(exportDir, "${video.title}_${choice.name.lowercase()}.mp4")

                videoExporter.exportMuxedVideo(
                    sourceVideoUri = Uri.parse(video.videoUri),
                    replacementAudioUri = audioUri,
                    outputFile = exportFile,
                    onProgress = { p -> _exportProgress.value = p }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Export error: ${e.message}", e)
            } finally {
                _isExporting.value = false
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun importCustomModel(onnxUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val destFile = File(context.filesDir, "models/custom_mdx_${System.currentTimeMillis()}.onnx").apply {
                    parentFile?.mkdirs()
                }
                context.contentResolver.openInputStream(onnxUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                _customModelPath.value = destFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import custom ONNX model: ${e.message}")
            }
        }
    }

    fun resetToBundledModel() {
        _customModelPath.value = null
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun retrieveVideoDuration(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
