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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VocalProcessingService : Service() {

    companion object {
        private const val TAG = "VocalProcService"
        const val CHANNEL_ID = "vocal_separation_channel"
        const val NOTIFICATION_ID = 2024

        const val ACTION_START_SEPARATION = "action_start_separation"
        const val ACTION_STOP_SEPARATION = "action_stop_separation"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"

        fun start(context: Context, videoId: Long, title: String) {
            val intent = Intent(context, VocalProcessingService::class.java).apply {
                action = ACTION_START_SEPARATION
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VocalProcessingService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping service: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Promote to foreground immediately in onCreate() to prevent ForegroundServiceDidNotStartInTimeException
        startForegroundSafely("Starting UVR Vocal Extraction…", 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SEPARATION) {
            stopSelf()
            return START_NOT_STICKY
        }

        val videoTitle = intent?.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Audio Track"
        startForegroundSafely("Processing: $videoTitle", 0)

        // Observe progress from VocalProcessingManager
        serviceScope.launch {
            VocalProcessingManager.progressFlow.collect { progress ->
                val title = VocalProcessingManager.activeVideoTitle.value ?: videoTitle
                val percent = (progress * 100).toInt().coerceIn(0, 100)
                updateNotification(title, percent)
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafely(content: String, progress: Int) {
        if (isForegroundStarted) return
        val notification = buildNotification(content, progress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "Foreground promotion failed: ${e.message}", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
                isForegroundStarted = true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback foreground promotion failed: ${fallbackEx.message}")
                stopSelf()
            }
        }
    }

    private fun updateNotification(title: String, percent: Int) {
        if (!isForegroundStarted) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notification = buildNotification("Extracting vocals: $title ($percent%)", percent)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_vocal)
            .setContentTitle("UVR Vocal Extractor")
            .setContentText(contentText)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vocal Separation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress during AI vocal and accompaniment separation"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isForegroundStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
