package com.example.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import com.example.onnx.AccelerationMode
import com.example.onnx.AudioSeparationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Singleton coordinator for managing AI separation jobs, elapsed time tracking,
 * and background execution stability via Foreground Service and CPU WakeLock.
 */
object VocalProcessingManager {

    private const val TAG = "VocalProcessingMgr"
    private const val WAKELOCK_TAG = "VocalExtractor:ProcessingWakeLock"

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeProcessingJob: Job? = null
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Reactive StateFlows for UI observation
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    private val _activeVideoTitle = MutableStateFlow<String?>(null)
    val activeVideoTitle: StateFlow<String?> = _activeVideoTitle.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow("Extracting vocals with AI…")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _elapsedTimeFormatted = MutableStateFlow("00:00")
    val elapsedTimeFormatted: StateFlow<String> = _elapsedTimeFormatted.asStateFlow()

    private val _estimatedRemainingFormatted = MutableStateFlow<String?>(null)
    val estimatedRemainingFormatted: StateFlow<String?> = _estimatedRemainingFormatted.asStateFlow()

    private val _isBackgroundProtected = MutableStateFlow(false)
    val isBackgroundProtected: StateFlow<Boolean> = _isBackgroundProtected.asStateFlow()

    private var startTimeRealtime: Long = 0L

    // Notification update listener for Foreground Service
    var onNotificationUpdateRequested: ((progress: Float, elapsedText: String, remainingText: String?) -> Unit)? = null

    /**
     * Formats duration in seconds into MM:SS or HH:MM:SS.
     */
    fun formatSeconds(seconds: Long): String {
        val totalSec = seconds.coerceAtLeast(0L)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    /**
     * Starts AI vocal separation with active elapsed timer and background stability service.
     */
    fun startSeparation(
        context: Context,
        video: VideoItemEntity,
        customModelPath: String? = null,
        accelerationMode: AccelerationMode = AccelerationMode.TURBO,
        forceRecompute: Boolean = false,
        onComplete: ((Result<File>) -> Unit)? = null
    ) {
        val appContext = context.applicationContext

        // If another separation is already running for this video, don't restart
        if (_isProcessing.value && _activeVideoId.value == video.id) {
            Log.d(TAG, "Separation already active for video ${video.id}")
            return
        }

        // Cancel previous if any
        cancelActiveProcessing(appContext)

        _isProcessing.value = true
        _activeVideoId.value = video.id
        _activeVideoTitle.value = video.title
        _progress.value = 0.05f
        _message.value = "Extracting vocals with UVR AI (${accelerationMode.displayName})…"
        _elapsedSeconds.value = 0L
        _elapsedTimeFormatted.value = "00:00"
        _estimatedRemainingFormatted.value = null

        startTimeRealtime = SystemClock.elapsedRealtime()

        // 1. Acquire Partial WakeLock to prevent CPU sleep during inference
        acquireWakeLock(appContext)

        // 2. Start Foreground Service to elevate process priority and show ongoing notification
        startForegroundService(appContext, video.id, video.title)

        // 3. Start high-frequency ticker for elapsed time & ETA calculation
        startTimerTicker()

        // 4. Run audio separation pipeline in background coroutine
        activeProcessingJob = applicationScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(appContext)
            val pipeline = AudioSeparationPipeline(appContext, db.videoDao())

            val result = pipeline.processVideo(
                video = video,
                customModelPath = customModelPath,
                accelerationMode = accelerationMode,
                forceRecompute = forceRecompute,
                onProgress = { currentProgress ->
                    _progress.value = currentProgress
                    val pct = (currentProgress * 100).toInt()
                    _message.value = "Extracting vocals with UVR AI (${accelerationMode.displayName})… $pct%"
                    notifyServiceProgress()
                }
            )

            // Finalize processing state
            _progress.value = if (result.isSuccess) 1.0f else 0f
            _isProcessing.value = false
            _isBackgroundProtected.value = false

            // Stop timer & release wakelock
            timerJob?.cancel()
            releaseWakeLock()

            // Notify Foreground Service to show completion or stop
            stopForegroundService(appContext, video.title, result.isSuccess)

            onComplete?.invoke(result)
        }
    }

    /**
     * Cancels active processing, terminates jobs, releases wakelock and stops service.
     */
    fun cancelActiveProcessing(context: Context) {
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        timerJob?.cancel()
        timerJob = null

        releaseWakeLock()
        _isProcessing.value = false
        _isBackgroundProtected.value = false
        _activeVideoId.value = null
        _activeVideoTitle.value = null
        _estimatedRemainingFormatted.value = null

        stopForegroundService(context, null, false)
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        timerJob = applicationScope.launch(Dispatchers.Default) {
            while (isActive && _isProcessing.value) {
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = (now - startTimeRealtime).coerceAtLeast(0L)
                val elapsedSec = elapsedMs / 1000
                _elapsedSeconds.value = elapsedSec
                _elapsedTimeFormatted.value = formatSeconds(elapsedSec)

                // Calculate estimated remaining time if progress > 5%
                val currentProgress = _progress.value
                if (currentProgress > 0.05f && currentProgress < 0.99f && elapsedMs > 2000L) {
                    val totalEstimatedMs = (elapsedMs / currentProgress).toLong()
                    val remainingMs = (totalEstimatedMs - elapsedMs).coerceAtLeast(0L)
                    val remainingSec = remainingMs / 1000
                    _estimatedRemainingFormatted.value = "~${formatSeconds(remainingSec)} left"
                } else if (currentProgress >= 0.99f) {
                    _estimatedRemainingFormatted.value = "Finishing up…"
                }

                notifyServiceProgress()
                delay(500)
            }
        }
    }

    private fun notifyServiceProgress() {
        onNotificationUpdateRequested?.invoke(
            _progress.value,
            _elapsedTimeFormatted.value,
            _estimatedRemainingFormatted.value
        )
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKELOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                    acquire(20 * 60 * 1000L) // 20-minute safety timeout
                }
                _isBackgroundProtected.value = true
                Log.d(TAG, "Partial WakeLock acquired for background AI inference")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
            _isBackgroundProtected.value = false
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        } finally {
            wakeLock = null
            _isBackgroundProtected.value = false
        }
    }

    private fun startForegroundService(context: Context, videoId: String, videoTitle: String) {
        try {
            val intent = Intent(context, VocalProcessingService::class.java).apply {
                action = VocalProcessingService.ACTION_START_PROCESSING
                putExtra(VocalProcessingService.EXTRA_VIDEO_ID, videoId)
                putExtra(VocalProcessingService.EXTRA_VIDEO_TITLE, videoTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start VocalProcessingService: ${e.message}")
        }
    }

    private fun stopForegroundService(context: Context, videoTitle: String?, isSuccess: Boolean) {
        try {
            val intent = Intent(context, VocalProcessingService::class.java).apply {
                action = VocalProcessingService.ACTION_STOP_PROCESSING
                putExtra(VocalProcessingService.EXTRA_VIDEO_TITLE, videoTitle)
                putExtra(VocalProcessingService.EXTRA_IS_SUCCESS, isSuccess)
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop VocalProcessingService: ${e.message}")
        }
    }
}
