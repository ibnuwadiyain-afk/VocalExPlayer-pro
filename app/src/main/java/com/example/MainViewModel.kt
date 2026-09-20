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
import com.example.media.ConcurrentDownloadTask
import com.example.media.DownloadProgressState
import com.example.media.DownloadTaskStatus
import com.example.media.ExtractedVideoInfo
import com.example.media.MediaResolutionOption
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
import java.util.concurrent.ConcurrentHashMap

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
    private var mediaDownloadJob: kotlinx.coroutines.Job? = null
    private var downloadTimerJob: kotlinx.coroutines.Job? = null

    // Concurrent multi-link download management
    private val downloadJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val _downloadTasks = MutableStateFlow<List<ConcurrentDownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<ConcurrentDownloadTask>> = _downloadTasks.asStateFlow()

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

    // Web Import Resolution Selection State
    data class WebImportResolutionState(
        val url: String,
        val isProbing: Boolean = false,
        val options: List<com.example.media.MediaResolutionOption> = emptyList(),
        val selectedOption: com.example.media.MediaResolutionOption? = null,
        val probeError: String? = null
    )

    private val _webImportResolutionState = MutableStateFlow<WebImportResolutionState?>(null)
    val webImportResolutionState: StateFlow<WebImportResolutionState?> = _webImportResolutionState.asStateFlow()

    // Internal Storage Folders access state
    private val _savedFolders = MutableStateFlow<List<com.example.storage.GrantedFolder>>(emptyList())
    val savedFolders: StateFlow<List<com.example.storage.GrantedFolder>> = _savedFolders.asStateFlow()

    private val _selectedFolder = MutableStateFlow<com.example.storage.GrantedFolder?>(null)
    val selectedFolder: StateFlow<com.example.storage.GrantedFolder?> = _selectedFolder.asStateFlow()

    private val _activeFolderMediaItems = MutableStateFlow<List<com.example.storage.StorageMediaItem>>(emptyList())
    val activeFolderMediaItems: StateFlow<List<com.example.storage.StorageMediaItem>> = _activeFolderMediaItems.asStateFlow()

    private val _isScanningFolder = MutableStateFlow(false)
    val isScanningFolder: StateFlow<Boolean> = _isScanningFolder.asStateFlow()

    init {
        loadSavedFolders()
    }

    fun loadSavedFolders() {
        val context = getApplication<Application>()
        val folders = com.example.storage.StorageFolderManager.getSavedFolders(context)
        _savedFolders.value = folders
        if (_selectedFolder.value == null && folders.isNotEmpty()) {
            selectAndScanFolder(folders.first())
        }
    }

    fun registerAndScanFolder(treeUri: Uri) {
        val context = getApplication<Application>()
        val folder = com.example.storage.StorageFolderManager.registerGrantedFolder(context, treeUri)
        _savedFolders.value = com.example.storage.StorageFolderManager.getSavedFolders(context)
        selectAndScanFolder(folder)
    }

    fun selectAndScanFolder(folder: com.example.storage.GrantedFolder) {
        _selectedFolder.value = folder
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningFolder.value = true
            val items = com.example.storage.StorageFolderManager.scanFolderForMedia(getApplication(), folder.uri)
            _activeFolderMediaItems.value = items
            _isScanningFolder.value = false
        }
    }

    fun refreshActiveFolder() {
        val current = _selectedFolder.value ?: return
        selectAndScanFolder(current)
    }

    fun removeSavedFolder(uriString: String) {
        val context = getApplication<Application>()
        com.example.storage.StorageFolderManager.removeSavedFolder(context, uriString)
        val updated = com.example.storage.StorageFolderManager.getSavedFolders(context)
        _savedFolders.value = updated
        if (_selectedFolder.value?.uriString == uriString) {
            val next = updated.firstOrNull()
            _selectedFolder.value = next
            if (next != null) {
                selectAndScanFolder(next)
            } else {
                _activeFolderMediaItems.value = emptyList()
            }
        }
    }

    fun handleIncomingShareUrl(rawText: String) {
        val extractedUrl = socialMediaProcessor.extractUrlFromText(rawText) ?: rawText.trim()
        if (extractedUrl.isNotBlank()) {
            _sharedMediaUrl.value = extractedUrl
        }
    }

    fun clearSharedMediaUrl() {
        _sharedMediaUrl.value = null
    }

    fun probeResolutionsForUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        _webImportResolutionState.value = WebImportResolutionState(url = trimmed, isProbing = true)
        _downloadErrorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolutions = socialMediaProcessor.fetchAvailableResolutions(trimmed)
                if (resolutions.isNotEmpty()) {
                    val defaultSelected = resolutions.firstOrNull { it.isRecommended } ?: resolutions.first()
                    _webImportResolutionState.value = WebImportResolutionState(
                        url = trimmed,
                        isProbing = false,
                        options = resolutions,
                        selectedOption = defaultSelected
                    )
                } else {
                    val standardOptions = listOf(
                        com.example.media.MediaResolutionOption("1080p", "1080p (Full HD)", 1080, "mp4"),
                        com.example.media.MediaResolutionOption("720p", "720p (HD)", 720, "mp4", isRecommended = true),
                        com.example.media.MediaResolutionOption("480p", "480p (SD)", 480, "mp4"),
                        com.example.media.MediaResolutionOption("360p", "360p (Fast)", 360, "mp4"),
                        com.example.media.MediaResolutionOption("audio", "Audio Only (MP3/M4A)", 0, "mp3")
                    )
                    _webImportResolutionState.value = WebImportResolutionState(
                        url = trimmed,
                        isProbing = false,
                        options = standardOptions,
                        selectedOption = standardOptions[1]
                    )
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Resolution probing failed for $trimmed, providing standard resolutions", e)
                val standardOptions = listOf(
                    com.example.media.MediaResolutionOption("1080p", "1080p (Full HD)", 1080, "mp4"),
                    com.example.media.MediaResolutionOption("720p", "720p (HD)", 720, "mp4", isRecommended = true),
                    com.example.media.MediaResolutionOption("480p", "480p (SD)", 480, "mp4"),
                    com.example.media.MediaResolutionOption("360p", "360p (Fast)", 360, "mp4"),
                    com.example.media.MediaResolutionOption("audio", "Audio Only (MP3/M4A)", 0, "mp3")
                )
                _webImportResolutionState.value = WebImportResolutionState(
                    url = trimmed,
                    isProbing = false,
                    options = standardOptions,
                    selectedOption = standardOptions[1]
                )
            }
        }
    }

    fun selectResolutionOption(option: com.example.media.MediaResolutionOption) {
        _webImportResolutionState.value = _webImportResolutionState.value?.copy(selectedOption = option)
    }

    fun confirmDownloadWithResolution() {
        val current = _webImportResolutionState.value ?: return
        val url = current.url
        val selectedOption = current.selectedOption
        _webImportResolutionState.value = null
        processSocialMediaUrl(url, selectedOption)
    }

    fun backToUrlInput() {
        _webImportResolutionState.value = null
    }

    fun parseUrlsFromInput(input: String): List<String> {
        val lines = input.split("\n", "\r", ",", " ", "\t").map { it.trim() }.filter { it.isNotBlank() }
        val urls = mutableListOf<String>()
        for (token in lines) {
            val extracted = socialMediaProcessor.extractUrlFromText(token) ?: token
            if (extracted.startsWith("http://", ignoreCase = true) ||
                extracted.startsWith("https://", ignoreCase = true) ||
                extracted.startsWith("rtsp://", ignoreCase = true)) {
                if (!urls.contains(extracted)) {
                    urls.add(extracted)
                }
            }
        }
        return urls
    }

    fun isLiveStreamUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.endsWith(".m3u8") ||
                clean.contains(".m3u8?") ||
                clean.endsWith(".mpd") ||
                clean.contains(".mpd?") ||
                clean.startsWith("rtsp://") ||
                clean.contains("/live") ||
                clean.contains("manifest") ||
                clean.contains(".m3u")
    }

    fun playLiveStream(url: String, customTitle: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return@launch

            val title = customTitle ?: when {
                trimmed.contains("nasa", ignoreCase = true) -> "NASA TV (Live Stream)"
                trimmed.contains("zeno", ignoreCase = true) -> "Radio Live Stream"
                trimmed.contains("tears", ignoreCase = true) -> "Tears of Steel HLS Stream"
                trimmed.contains("bipbop", ignoreCase = true) -> "Apple BipBop Test Stream"
                else -> "Live Stream (${trimmed.substringBefore('?').substringAfterLast('/').ifBlank { "Channel" }})"
            }

            val existing = videoDao.getVideoByUri(trimmed)
            val videoEntity = existing ?: VideoItemEntity(
                id = "live_" + UUID.randomUUID().toString().substring(0, 8),
                title = title,
                videoUri = trimmed,
                duration = 0L,
                status = VideoStatus.READY,
                dateAdded = System.currentTimeMillis()
            ).also {
                videoDao.insertVideo(it)
            }

            withContext(Dispatchers.Main) {
                _selectedVideo.value = videoEntity
                _modelInfoMessage.value = "Playing Live Stream: $title"
            }
        }
    }

    fun openAndPlayExternalMediaUri(uri: Uri) {
        val uriString = uri.toString()
        if (uriString.startsWith("http://", ignoreCase = true) ||
            uriString.startsWith("https://", ignoreCase = true) ||
            uriString.startsWith("rtsp://", ignoreCase = true)) {
            if (isLiveStreamUrl(uriString)) {
                playLiveStream(uriString)
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    val existing = videoDao.getVideoByUri(uriString)
                    if (existing != null) {
                        withContext(Dispatchers.Main) {
                            _selectedVideo.value = existing
                        }
                    } else {
                        val videoEntity = VideoItemEntity(
                            id = "ext_" + UUID.randomUUID().toString().substring(0, 8),
                            title = uri.lastPathSegment?.substringBefore('?') ?: "Network Stream",
                            videoUri = uriString,
                            duration = 0L,
                            status = VideoStatus.READY,
                            dateAdded = System.currentTimeMillis()
                        )
                        videoDao.insertVideo(videoEntity)
                        withContext(Dispatchers.Main) {
                            _selectedVideo.value = videoEntity
                        }
                    }
                }
            }
        } else {
            importMediaFromUri(uri)
        }
    }

    fun handleIncomingShareUrls(rawTexts: List<String>) {
        val allUrls = rawTexts.flatMap { parseUrlsFromInput(it) }.distinct()
        if (allUrls.size > 1) {
            startConcurrentDownloads(allUrls)
        } else if (allUrls.size == 1) {
            _sharedMediaUrl.value = allUrls.first()
        }
    }

    fun startConcurrentDownloads(
        urls: List<String>,
        targetResolution: com.example.media.MediaResolutionOption? = null
    ) {
        val validUrls = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (validUrls.isEmpty()) return

        _webImportResolutionState.value = null
        _downloadErrorMessage.value = null
        _isDownloadingMedia.value = true

        val newTasks = validUrls.map { url ->
            val existing = _downloadTasks.value.find { it.url == url && it.isActive }
            existing ?: ConcurrentDownloadTask(
                url = url,
                title = "Detecting ${url.substringBefore('?').substringAfterLast('/').ifBlank { "media" }}…",
                resolutionOption = targetResolution,
                status = DownloadTaskStatus.QUEUED
            )
        }

        // Add or update tasks list
        val currentTasks = _downloadTasks.value.toMutableList()
        for (task in newTasks) {
            val index = currentTasks.indexOfFirst { it.id == task.id }
            if (index >= 0) {
                currentTasks[index] = task
            } else {
                currentTasks.add(0, task)
            }
        }
        _downloadTasks.value = currentTasks

        // Launch concurrent download for each task
        for (task in newTasks) {
            if (downloadJobs.containsKey(task.id)) continue

            val job = viewModelScope.launch(Dispatchers.IO) {
                updateDownloadTask(task.id) {
                    it.copy(
                        status = DownloadTaskStatus.RESOLVING,
                        statusText = "Analyzing link…"
                    )
                }

                try {
                    val result = socialMediaProcessor.processAndDownload(task.url, task.resolutionOption) { progressState ->
                        when (progressState) {
                            is DownloadProgressState.Resolving -> {
                                updateDownloadTask(task.id) {
                                    it.copy(
                                        status = DownloadTaskStatus.RESOLVING,
                                        statusText = progressState.message
                                    )
                                }
                            }
                            is DownloadProgressState.Downloading -> {
                                updateDownloadTask(task.id) {
                                    it.copy(
                                        status = DownloadTaskStatus.DOWNLOADING,
                                        progress = progressState.progress,
                                        speedKbps = progressState.speedKbps,
                                        statusText = "${(progressState.progress * 100).toInt()}% • ${progressState.speedKbps} KB/s"
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                    result.onSuccess { info ->
                        val videoId = UUID.randomUUID().toString().substring(0, 8)
                        val newVideo = VideoItemEntity(
                            id = videoId,
                            title = info.title,
                            videoUri = Uri.fromFile(info.file).toString(),
                            duration = info.durationMs,
                            status = VideoStatus.NEW,
                            dateAdded = System.currentTimeMillis()
                        )
                        videoDao.insertVideo(newVideo)

                        updateDownloadTask(task.id) {
                            it.copy(
                                status = DownloadTaskStatus.COMPLETED,
                                progress = 1f,
                                title = info.title,
                                statusText = "Downloaded",
                                downloadedFile = info.file,
                                videoEntity = newVideo
                            )
                        }

                        _modelInfoMessage.value = "Downloaded \"${info.title}\" successfully!"
                        if (validUrls.size == 1 && _selectedVideo.value == null) {
                            withContext(Dispatchers.Main) {
                                selectVideo(newVideo, autoStartSeparation = false)
                            }
                        }
                    }.onFailure { error ->
                        val errorMsg = error.localizedMessage ?: "Failed to download media"
                        updateDownloadTask(task.id) {
                            it.copy(
                                status = DownloadTaskStatus.FAILED,
                                statusText = "Failed",
                                errorMessage = errorMsg
                            )
                        }
                        if (validUrls.size == 1) {
                            _downloadErrorMessage.value = errorMsg
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    updateDownloadTask(task.id) {
                        it.copy(
                            status = DownloadTaskStatus.CANCELLED,
                            statusText = "Cancelled"
                        )
                    }
                } catch (e: Exception) {
                    val errorMsg = e.localizedMessage ?: "Failed to download"
                    updateDownloadTask(task.id) {
                        it.copy(
                            status = DownloadTaskStatus.FAILED,
                            statusText = "Failed",
                            errorMessage = errorMsg
                        )
                    }
                } finally {
                    downloadJobs.remove(task.id)
                    syncGlobalDownloadState()
                }
            }
            downloadJobs[task.id] = job
        }

        syncGlobalDownloadState()
    }

    private fun updateDownloadTask(taskId: String, transform: (ConcurrentDownloadTask) -> ConcurrentDownloadTask) {
        val list = _downloadTasks.value.toMutableList()
        val index = list.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            list[index] = transform(list[index])
            _downloadTasks.value = list
            syncGlobalDownloadState()
        }
    }

    private fun syncGlobalDownloadState() {
        val tasks = _downloadTasks.value
        val hasActive = tasks.any { it.isActive }
        _isDownloadingMedia.value = hasActive

        val activeTask = tasks.firstOrNull { it.isActive }
        if (activeTask != null) {
            _downloadProgressState.value = if (activeTask.status == DownloadTaskStatus.DOWNLOADING) {
                DownloadProgressState.Downloading(activeTask.progress, activeTask.speedKbps)
            } else {
                DownloadProgressState.Resolving(activeTask.statusText)
            }
        } else {
            _downloadProgressState.value = null
        }
    }

    fun cancelDownloadTask(taskId: String) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        updateDownloadTask(taskId) {
            it.copy(status = DownloadTaskStatus.CANCELLED, statusText = "Cancelled")
        }
        syncGlobalDownloadState()
    }

    fun cancelAllDownloads() {
        for ((_, job) in downloadJobs) {
            job.cancel()
        }
        downloadJobs.clear()
        mediaDownloadJob?.cancel()
        downloadTimerJob?.cancel()
        mediaDownloadJob = null
        downloadTimerJob = null

        val updated = _downloadTasks.value.map {
            if (it.isActive) it.copy(status = DownloadTaskStatus.CANCELLED, statusText = "Cancelled") else it
        }
        _downloadTasks.value = updated
        _isDownloadingMedia.value = false
        _downloadProgressState.value = null
        _downloadErrorMessage.value = null
        _sharedMediaUrl.value = null
        _webImportResolutionState.value = null
    }

    fun clearCompletedDownloads() {
        _downloadTasks.value = _downloadTasks.value.filter { it.isActive }
    }

    fun retryDownloadTask(taskId: String) {
        val task = _downloadTasks.value.find { it.id == taskId } ?: return
        startConcurrentDownloads(listOf(task.url), task.resolutionOption)
    }

    fun processSocialMediaUrl(url: String, targetResolution: com.example.media.MediaResolutionOption? = null) {
        if (url.isBlank()) return
        val parsed = parseUrlsFromInput(url)
        if (parsed.isNotEmpty()) {
            startConcurrentDownloads(parsed, targetResolution)
        } else {
            startConcurrentDownloads(listOf(url.trim()), targetResolution)
        }
    }

    fun cancelMediaDownload() {
        cancelAllDownloads()
    }

    fun dismissDownloadDialog() {
        // Dismissing the dialog view lets concurrent downloads continue running seamlessly in the background!
        _downloadErrorMessage.value = null
        _sharedMediaUrl.value = null
        _webImportResolutionState.value = null
    }

    fun clearDownloadError() {
        _downloadErrorMessage.value = null
    }

    fun exportMedia(
        video: VideoItemEntity,
        format: com.example.export.ExportFormat,
        deleteOriginalAfterExport: Boolean = false
    ) {
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
            val result = com.example.export.AudioExporter.exportMedia(
                context = context,
                video = video,
                format = format,
                onProgress = { progress ->
                    _exportProgress.value = progress
                }
            )

            result.onSuccess { res ->
                _exportResult.value = res
                _isExporting.value = false
                if (deleteOriginalAfterExport) {
                    deleteVideo(video)
                }
            }.onFailure { err ->
                _exportError.value = err.localizedMessage ?: "Export failed"
                _isExporting.value = false
            }
        }
    }

    fun exportVideo(video: VideoItemEntity, deleteOriginalAfterExport: Boolean = false) {
        exportMedia(video, com.example.export.ExportFormat.VIDEO_VOCALS_MP4, deleteOriginalAfterExport)
    }

    fun shareExportResult(exportResult: com.example.export.ExportResult) {
        val context = getApplication<Application>()
        val fileName = exportResult.internalFile.name.lowercase()
        val mimeType = when {
            fileName.endsWith(".wav") -> "audio/wav"
            fileName.endsWith(".m4a") -> "audio/mp4"
            fileName.endsWith(".mp3") -> "audio/mpeg"
            else -> "video/mp4"
        }
        com.example.export.AudioExporter.shareFile(
            context = context,
            file = exportResult.internalFile,
            mimeType = mimeType,
            title = exportResult.internalFile.nameWithoutExtension
        )
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

    fun selectVideo(video: VideoItemEntity, autoStartSeparation: Boolean = false) {
        _selectedVideo.value = video
        if (autoStartSeparation && video.status == VideoStatus.NEW) {
            startSeparation(video)
        }
    }

    fun clearSelectedVideo() {
        _selectedVideo.value = null
    }

    fun importVideoFromUri(uri: Uri) {
        importMediaFromUri(uri)
    }

    fun importMediaFromUri(uri: Uri, customTitle: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val retriever = MediaMetadataRetriever()
                var hasVideo = false
                var durationMs = 15_000L
                var titleMeta: String? = null
                var artistMeta: String? = null

                try {
                    retriever.setDataSource(context, uri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 15_000L
                    titleMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    artistMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Retriever inspection skipped for $uri: ${e.message}")
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                // Determine file extension
                val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (_: Exception) { "" }
                val extFromUri = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
                val resolvedExt = when {
                    extFromUri.isNotBlank() && extFromUri.length in 2..4 -> extFromUri
                    mimeType.contains("wav") -> "wav"
                    mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
                    mimeType.contains("flac") -> "flac"
                    mimeType.contains("ogg") -> "ogg"
                    mimeType.contains("aac") -> "aac"
                    mimeType.contains("audio/mp4") || mimeType.contains("m4a") -> "m4a"
                    mimeType.startsWith("audio/") -> "m4a"
                    hasVideo -> "mp4"
                    else -> "mp4"
                }

                val mediaDir = File(context.filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val mediaId = UUID.randomUUID().toString().substring(0, 8)
                val localFile = File(mediaDir, "media_${mediaId}.$resolvedExt")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val resolvedTitle = when {
                    !customTitle.isNullOrBlank() -> customTitle.substringBeforeLast('.')
                    !titleMeta.isNullOrBlank() && !artistMeta.isNullOrBlank() -> "$artistMeta - $titleMeta"
                    !titleMeta.isNullOrBlank() -> titleMeta
                    else -> uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported Media $mediaId"
                }

                val newMedia = VideoItemEntity(
                    id = mediaId,
                    videoUri = localFile.absolutePath,
                    title = resolvedTitle,
                    duration = durationMs,
                    status = VideoStatus.NEW
                )

                videoDao.insertVideo(newMedia)
                Log.i("MainViewModel", "Imported media $mediaId: $resolvedTitle (hasVideo=$hasVideo, ext=$resolvedExt)")

                withContext(Dispatchers.Main) {
                    _selectedVideo.value = newMedia
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error importing media from $uri", e)
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
