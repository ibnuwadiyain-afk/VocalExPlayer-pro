package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.onnx.AccelerationMode
import com.example.onnx.AudioSeparationPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android Foreground Service providing ongoing notification and process elevation,
 * ensuring that AI vocal separation completes reliably when the app is backgrounded.
 *
 * Implements strict Android 14+ (API 34+) foreground service compliance:
 * - Promotes immediately to foreground in onCreate() before ANY heavy operation
 * - Defers all model loading, ONNX Runtime init, and audio I/O to background coroutine
 * - Handles missing configuration and background failures cleanly
 */
class VocalProcessingService : Service() {

    companion object {
        private const val TAG = "VocalProcessingService"
        const val CHANNEL_ID = "vocal_separation_processing_channel"
        private const val NOTIFICATION_ID = 2001
        private const val COMPLETION_NOTIFICATION_ID = 2002
        private const val ERROR_NOTIFICATION_ID = 2003

        const val ACTION_START_PROCESSING = "com.example.service.ACTION_START_PROCESSING"
        const val ACTION_STOP_PROCESSING = "com.example.service.ACTION_STOP_PROCESSING"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_CUSTOM_MODEL_PATH = "extra_custom_model_path"
        const val EXTRA_ACCELERATION_MODE = "extra_acceleration_mode"
        const val EXTRA_FORCE_RECOMPUTE = "extra_force_recompute"
        const val EXTRA_IS_SUCCESS = "extra_is_success"
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var processingJob: Job? = null

    private var isForegroundPromoted = false
    private var currentVideoTitle: String = "Audio Track"
    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VocalProcessingService.onCreate() called")

        // 1. Create notification channel immediately
        createNotificationChannel()

        // 2. Call startForeground immediately from the service startup path
        // before performing ANY expensive operation (ONNX runtime, model load, audio I/O)
        promoteToForegroundImmediately(
            title = currentVideoTitle,
            progress = 0f,
            isIndeterminate = true,
            statusText = "Initializing vocal separation engine…"
        )

        // Hook into VocalProcessingManager's updates for high-frequency progress/ETA updates
        VocalProcessingManager.onNotificationUpdateRequested = { progress, elapsedText, remainingText ->
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdateTime >= 800L || progress >= 0.99f) {
                lastNotificationUpdateTime = now
                updateNotification(progress, elapsedText, remainingText)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "VocalProcessingService.onStartCommand: action=${intent?.action}, startId=$startId")

        // 3. Ensure every service startup path reaches startForeground()
        if (!isForegroundPromoted) {
            promoteToForegroundImmediately(
                title = currentVideoTitle,
                progress = 0f,
                isIndeterminate = true,
                statusText = "Initializing vocal separation engine…"
            )
        }

        // Handle missing intent / configuration gracefully without crash
        if (intent == null) {
            Log.w(TAG, "Received null intent in onStartCommand - stopping service cleanly")
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START_PROCESSING -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
                val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)
                val customModelPath = intent.getStringExtra(EXTRA_CUSTOM_MODEL_PATH)
                val accelerationModeId = intent.getStringExtra(EXTRA_ACCELERATION_MODE)
                val forceRecompute = intent.getBooleanExtra(EXTRA_FORCE_RECOMPUTE, false)

                if (!videoTitle.isNullOrBlank()) {
                    currentVideoTitle = videoTitle
                }

                // Verify configuration presence
                if (videoId.isNullOrBlank()) {
                    Log.w(TAG, "Missing processing configuration: videoId is null or blank")
                    showErrorNotification("Processing configuration is missing")
                    VocalProcessingManager.onProcessingError(null, "Missing processing configuration")
                    stopServiceCleanly()
                    return START_NOT_STICKY
                }

                // Update foreground notification with active track info
                updateNotification(
                    progress = 0.05f,
                    elapsedText = "00:00",
                    remainingText = "Estimating…"
                )

                // Move all expensive initialization and separation to a background coroutine
                // strictly AFTER the service has successfully entered foreground state
                startBackgroundProcessing(
                    videoId = videoId,
                    videoTitle = currentVideoTitle,
                    customModelPath = customModelPath,
                    accelerationModeId = accelerationModeId,
                    forceRecompute = forceRecompute
                )
            }

            ACTION_STOP_PROCESSING -> {
                val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: currentVideoTitle
                val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)

                processingJob?.cancel()
                processingJob = null

                if (isSuccess) {
                    showCompletionNotification(title)
                }

                stopServiceCleanly()
            }

            else -> {
                Log.w(TAG, "Unknown action: ${intent.action} - stopping service cleanly")
                stopServiceCleanly()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        VocalProcessingManager.onNotificationUpdateRequested = null
        processingJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForegroundImmediately(
        title: String,
        progress: Float,
        isIndeterminate: Boolean,
        statusText: String
    ) {
        if (isForegroundPromoted) return

        try {
            val notification = buildNotification(
                title = title,
                progress = progress,
                isIndeterminate = isIndeterminate,
                statusText = statusText
            )
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            isForegroundPromoted = true
            Log.i(TAG, "Foreground service promoted immediately (type=$serviceType)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to promote foreground service: ${e.message}", e)
        }
    }

    private fun startBackgroundProcessing(
        videoId: String,
        videoTitle: String,
        customModelPath: String?,
        accelerationModeId: String?,
        forceRecompute: Boolean
    ) {
        processingJob?.cancel()
        processingJob = serviceScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting background AI separation for video: $videoId ($videoTitle)")
                val appContext = applicationContext
                val db = AppDatabase.getInstance(appContext)
                val videoDao = db.videoDao()
                val video = videoDao.getVideoById(videoId)

                if (video == null) {
                    val errorMsg = "Video item $videoId not found in local database"
                    Log.e(TAG, errorMsg)
                    showErrorNotification("Media record not found: $videoTitle")
                    VocalProcessingManager.onProcessingError(videoId, errorMsg)
                    stopServiceCleanly()
                    return@launch
                }

                val accelerationMode = AccelerationMode.fromId(accelerationModeId)

                // Expensive initialization: AudioSeparationPipeline, ONNX runtime, AudioExtractor
                // strictly run here in background coroutine
                val pipeline = AudioSeparationPipeline(appContext, videoDao)
                val result = pipeline.processVideo(
                    video = video,
                    customModelPath = customModelPath,
                    accelerationMode = accelerationMode,
                    forceRecompute = forceRecompute,
                    onProgress = { currentProgress ->
                        VocalProcessingManager.updateProgress(currentProgress, accelerationMode)
                    }
                )

                result.onSuccess { wavFile ->
                    Log.i(TAG, "Vocal separation completed successfully: ${wavFile.absolutePath}")
                    VocalProcessingManager.onProcessingSuccess(video, wavFile)
                    showCompletionNotification(videoTitle)
                    stopServiceCleanly()
                }.onFailure { error ->
                    Log.e(TAG, "Vocal separation failed: ${error.message}", error)
                    val errorMsg = error.localizedMessage ?: "AI vocal separation failed"
                    showErrorNotification("Separation failed for $videoTitle: $errorMsg")
                    VocalProcessingManager.onProcessingError(videoId, errorMsg)
                    stopServiceCleanly()
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.i(TAG, "Background processing was cancelled")
                } else {
                    Log.e(TAG, "Unexpected error in background processing: ${e.message}", e)
                    val errorMsg = e.localizedMessage ?: "Unexpected separation error"
                    showErrorNotification("Error separating vocals: $errorMsg")
                    VocalProcessingManager.onProcessingError(videoId, errorMsg)
                    stopServiceCleanly()
                }
            }
        }
    }

    private fun stopServiceCleanly() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundPromoted = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground: ${e.message}")
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Vocal Separation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and elapsed time for background AI separation"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        progress: Float,
        isIndeterminate: Boolean,
        statusText: String
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val progressPct = (progress * 100).toInt().coerceIn(0, 100)
        val contentText = "$title\n$statusText"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Extracting Vocals with UVR AI")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setProgress(100, progressPct, isIndeterminate)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun updateNotification(
        progress: Float,
        elapsedText: String,
        remainingText: String?
    ) {
        try {
            val progressPct = (progress * 100).toInt().coerceIn(0, 100)
            val etaPart = if (!remainingText.isNullOrBlank()) " • $remainingText" else ""
            val statusText = "$progressPct% • ⏱ Elapsed: $elapsedText$etaPart"

            val notification = buildNotification(
                title = currentVideoTitle,
                progress = progress,
                isIndeterminate = progress <= 0.05f,
                statusText = statusText
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }

    private fun showErrorNotification(errorMessage: String) {
        try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("AI Vocal Separation Failed")
                .setContentText(errorMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(ERROR_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show error notification: ${e.message}")
        }
    }

    private fun showCompletionNotification(title: String) {
        try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Vocal Separation Complete!")
                .setContentText("Vocals & accompaniment are ready for $title")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(COMPLETION_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show completion notification: ${e.message}")
        }
    }
}
