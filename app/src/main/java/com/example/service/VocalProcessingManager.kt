package com.example.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.VideoItemEntity
import com.example.onnx.AccelerationMode
import com.example.onnx.AudioSeparationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object VocalProcessingManager {
    private const val TAG = "VocalProcManager"

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var activeJob: Job? = null
    private var timerJob: Job? = null

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _progressFlow = MutableStateFlow(0f)
    val progressFlow = _progressFlow.asStateFlow()

    private val _statusMessageFlow = MutableStateFlow("Idle")
    val statusMessageFlow = _statusMessageFlow.asStateFlow()

    private val _activeVideoId = MutableStateFlow<Long?>(null)
    val activeVideoId = _activeVideoId.asStateFlow()

    private val _activeVideoTitle = MutableStateFlow<String?>(null)
    val activeVideoTitle = _activeVideoTitle.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    fun startSeparation(
        context: Context,
        video: VideoItemEntity,
        customModelPath: String? = null,
        mode: AccelerationMode = AccelerationMode.AUTO,
        onComplete: (success: Boolean, errorMsg: String?) -> Unit = { _, _ -> }
    ) {
        if (_isProcessing.value) {
            Log.w(TAG, "Separation already in progress")
            return
        }

        _isProcessing.value = true
        _progressFlow.value = 0f
        _statusMessageFlow.value = "Starting separation…"
        _activeVideoId.value = video.id
        _activeVideoTitle.value = video.title
        _elapsedSeconds.value = 0

        // Start Foreground Service
        VocalProcessingService.start(context, video.id, video.title)

        // Elapsed time ticker
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _isProcessing.value) {
                delay(1000L)
                _elapsedSeconds.value += 1
            }
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            val pipeline = AudioSeparationPipeline(context)
            try {
                pipeline.initializeEngine(customModelPath, mode)

                val result = pipeline.processVideo(video) { progress, message ->
                    _progressFlow.value = progress
                    _statusMessageFlow.value = message
                }

                // Update Database
                val db = AppDatabase.getDatabase(context)
                val updated = video.copy(
                    isSeparated = true,
                    vocalsUri = Uri.fromFile(result.vocalsFile).toString(),
                    accompanimentUri = Uri.fromFile(result.accompanimentFile).toString(),
                    durationMs = if (video.durationMs > 0) video.durationMs else result.durationMs
                )
                db.videoDao().updateVideo(updated)

                _statusMessageFlow.value = "Extraction complete!"
                _progressFlow.value = 1.0f
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Separation failed: ${e.message}", e)
                _statusMessageFlow.value = "Failed: ${e.message}"
                onComplete(false, e.message)
            } finally {
                _isProcessing.value = false
                _activeVideoId.value = null
                _activeVideoTitle.value = null
                timerJob?.cancel()
                VocalProcessingService.stop(context)
            }
        }
    }

    fun cancelActiveSeparation(context: Context) {
        if (!_isProcessing.value) return
        activeJob?.cancel()
        timerJob?.cancel()
        _isProcessing.value = false
        _activeVideoId.value = null
        _activeVideoTitle.value = null
        _statusMessageFlow.value = "Separation cancelled"
        VocalProcessingService.stop(context)
    }
}
