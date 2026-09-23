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

/**
 * Android Foreground Service providing ongoing notification and process elevation,
 * ensuring that AI vocal separation completes reliably when the app is backgrounded.
 */
class VocalProcessingService : Service() {

    companion object {
        private const val TAG = "VocalProcessingService"
        const val CHANNEL_ID = "vocal_separation_processing_channel"
        private const val NOTIFICATION_ID = 2001
        private const val COMPLETION_NOTIFICATION_ID = 2002

        @Volatile
        var isServiceRunning = false

        const val ACTION_START_PROCESSING = "com.example.service.ACTION_START_PROCESSING"
        const val ACTION_STOP_PROCESSING = "com.example.service.ACTION_STOP_PROCESSING"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_IS_SUCCESS = "extra_is_success"
    }

    private var currentVideoTitle: String = "Video Track"
    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()

        // ALWAYS satisfy the startForegroundService contract immediately in onCreate()
        promoteToForeground(currentVideoTitle, 0.05f, "00:00", "Starting…")

        // Hook into VocalProcessingManager's updates
        VocalProcessingManager.onNotificationUpdateRequested = { progress, elapsedText, remainingText ->
            val now = System.currentTimeMillis()
            // Throttle notification updates to once per 800ms to avoid system notification rate-limits
            if (now - lastNotificationUpdateTime >= 800L || progress >= 0.99f) {
                lastNotificationUpdateTime = now
                updateNotification(progress, elapsedText, remainingText)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Guarantee foreground state is satisfied on every start command
        promoteToForeground(currentVideoTitle, 0.05f, "00:00", "Estimating…")

        when (intent?.action) {
            ACTION_START_PROCESSING -> {
                currentVideoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video Track"
                promoteToForeground(currentVideoTitle, 0.05f, "00:00", "Estimating…")
                Log.i(TAG, "Foreground service started for $currentVideoTitle")
            }

            ACTION_STOP_PROCESSING -> {
                val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: currentVideoTitle
                val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)

                // Show completion notification if succeeded
                if (isSuccess) {
                    showCompletionNotification(title)
                }

                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                // If started with null or unexpected action, stop cleanly
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        VocalProcessingManager.onNotificationUpdateRequested = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(
        title: String,
        progress: Float,
        elapsedText: String,
        remainingText: String?
    ) {
        val initialNotification = buildNotification(
            title = title,
            progress = progress,
            elapsedText = elapsedText,
            remainingText = remainingText
        )

        try {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                serviceType
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error starting foreground service: ${e.message}")
        }
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
        title: String = currentVideoTitle,
        progress: Float,
        elapsedText: String,
        remainingText: String?
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
        val etaPart = if (!remainingText.isNullOrBlank()) " • $remainingText" else ""
        val contentText = "$title\n$progressPct% • ⏱ Elapsed: $elapsedText$etaPart"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Extracting Vocals with Spleeter AI")
            .setContentText("$progressPct% • ⏱ Elapsed: $elapsedText$etaPart")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setProgress(100, progressPct, progress <= 0.05f)
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
            val notification = buildNotification(
                title = currentVideoTitle,
                progress = progress,
                elapsedText = elapsedText,
                remainingText = remainingText
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
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
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
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
