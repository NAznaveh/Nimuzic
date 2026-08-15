package com.example.player

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class MediaPlaybackService : Service() {

    // Keep a strong reference to the process-wide player so UI destruction does not
    // accidentally tear down background playback.
    private lateinit var playerController: AudioPlayerController

    override fun onCreate() {
        super.onCreate()
        playerController = AudioPlayerController.getInstance(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_FOREGROUND) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        } else if (action == ACTION_PAUSE_FOREGROUND) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            return START_STICKY
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_NOTIFICATION, Notification::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Notification>(EXTRA_NOTIFICATION)
        }
        if (notification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    MediaNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    companion object {
        const val EXTRA_NOTIFICATION = "extra_notification"
        const val ACTION_STOP_FOREGROUND = "com.example.player.STOP_FOREGROUND"
        const val ACTION_PAUSE_FOREGROUND = "com.example.player.PAUSE_FOREGROUND"
    }
}
