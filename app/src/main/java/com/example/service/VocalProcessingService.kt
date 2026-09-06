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
import com.example.MainActivity
import com.example.R

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
        createNotificationChannel()

        // CRITICAL FIX: Always call startForeground immediately in onCreate()
        // so the system timeout never triggers ForegroundServiceDidNotStartInTimeException
        startForegroundSafely()

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
        when (intent?.action) {
            ACTION_START_PROCESSING -> {
                currentVideoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video Track"
                updateNotification(
                    progress = 0.05f,
                    elapsedText = "00:00",
                    remainingText = "Estimating…"
                )
                Log.i(TAG, "Foreground service active for $currentVideoTitle")
            }

            ACTION_STOP_PROCESSING -> {
                val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: currentVideoTitle
                val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)

                // Show completion notification if succeeded
                if (isSuccess) {
                    showCompletionNotification(title)
                }

                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping foreground: ${e.message}")
                }
                stopSelf()
            }

            else -> {
                // If intent is null or unrecognized, ensure foreground notification is present
                startForegroundSafely()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(
        progress: Float = 0.05f,
        elapsedText: String = "00:00",
        remainingText: String? = "Estimating…"
    ) {
        val initialNotification = buildNotification(progress, elapsedText, remainingText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            Log.i(TAG, "startForeground executed successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Typed startForeground failed: ${e.message}, attempting default fallback")
            try {
                startForeground(NOTIFICATION_ID, initialNotification)
            } catch (e2: Exception) {
                Log.e(TAG, "All startForeground calls failed: ${e2.message}")
                // Immediate stopSelf prevents OS from throwing ForegroundServiceDidNotStartInTimeException
                try {
                    stopSelf()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        VocalProcessingManager.onNotificationUpdateRequested = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        val contentText = "$currentVideoTitle\n$progressPct% • ⏱ Elapsed: $elapsedText$etaPart"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_vocal)
            .setContentTitle("Extracting Vocals with UVR AI")
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
            val notification = buildNotification(progress, elapsedText, remainingText)
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
                .setSmallIcon(R.drawable.ic_notification_vocal)
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
