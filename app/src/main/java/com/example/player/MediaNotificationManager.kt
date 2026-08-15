package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import com.example.MainActivity
import com.example.data.models.Track

class MediaNotificationManager(
    private val context: Context,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeekTo: ((Long) -> Unit)? = null
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val mediaSession: MediaSession = MediaSession(context, "NimusicMediaSession").apply {
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() { onPlayPause() }
            override fun onPause() { onPlayPause() }
            override fun onSkipToNext() { onNext() }
            override fun onSkipToPrevious() { onPrevious() }
            override fun onSeekTo(pos: Long) {
                onSeekTo?.invoke(pos)
            }
        })
        isActive = true
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAY -> onPlayPause()
                ACTION_NEXT -> onNext()
                ACTION_PREVIOUS -> onPrevious()
            }
        }
    }

    init {
        createNotificationChannel()
        registerReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mediaReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(mediaReceiver, filter)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nimusic Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback notification for Nimusic"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private var lastMetadataTrackId: Long = -1L
    private var lastTrackDuration: Long = -1L
    private var cachedCoverBitmap: Bitmap? = null

    fun updateNotification(
        track: Track?,
        isPlaying: Boolean,
        currentPositionMs: Long,
        isPreparing: Boolean = false
    ) {
        if (track == null) {
            notificationManager.cancel(NOTIFICATION_ID)
            updatePlaybackState(PlaybackState.STATE_STOPPED, 0L, 0.0f)
            lastMetadataTrackId = -1L
            lastTrackDuration = -1L
            cachedCoverBitmap = null
            stopForegroundService()
            return
        }

        if (track.id != lastMetadataTrackId || track.durationMs != lastTrackDuration) {
            updateMediaMetadata(track)
            lastMetadataTrackId = track.id
            lastTrackDuration = track.durationMs
        }
        val playbackStateCode = when {
            isPreparing -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val playbackSpeed = if (isPlaying && !isPreparing) 1.0f else 0.0f
        updatePlaybackState(playbackStateCode, currentPositionMs, playbackSpeed)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getBroadcast(
            context, 10, Intent(ACTION_PREVIOUS).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            context, 11, Intent(ACTION_TOGGLE_PLAY).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            context, 12, Intent(ACTION_NEXT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying || isPreparing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying || isPreparing) "Pause" else "Play"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        val isOngoing = isPlaying || isPreparing

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setContentIntent(contentIntent)
            .setOngoing(isOngoing)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)

        cachedCoverBitmap?.let { bmp ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder.setLargeIcon(android.graphics.drawable.Icon.createWithBitmap(bmp))
                } else {
                    @Suppress("DEPRECATION")
                    builder.setLargeIcon(bmp)
                }
            } catch (_: Exception) {}
        }

        builder.addAction(android.app.Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "Previous", prevIntent
        ).build())
            .addAction(android.app.Notification.Action.Builder(
                playPauseIcon, playPauseTitle, playPauseIntent
            ).build())
            .addAction(android.app.Notification.Action.Builder(
                android.R.drawable.ic_media_next, "Next", nextIntent
            ).build())
            .setStyle(
                android.app.Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)

        if (isOngoing) {
            startForegroundService(notification)
        } else {
            pauseForegroundService()
        }
    }

    private fun startForegroundService(notification: android.app.Notification) {
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            putExtra(MediaPlaybackService.EXTRA_NOTIFICATION, notification)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseForegroundService() {
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_PAUSE_FOREGROUND
        }
        try {
            context.startService(serviceIntent)
        } catch (_: Exception) {}
    }

    private fun stopForegroundService() {
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_STOP_FOREGROUND
        }
        try {
            context.startService(serviceIntent)
        } catch (_: Exception) {}
    }

    private fun updateMediaMetadata(track: Track) {
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, if (track.durationMs > 0) track.durationMs else 210000L)

        var coverBitmap: Bitmap? = null

        // 1. Try extracting embedded album art from local audio file
        val audioPath = track.downloadedPath.ifEmpty { track.audioUrl }
        val embeddedBytes = AudioArtworkUtils.extractEmbeddedArtworkBytes(audioPath)
        if (embeddedBytes != null) {
            coverBitmap = AudioArtworkUtils.decodeSampledBitmap(embeddedBytes, maxDimension = 192)
        }

        // 2. Try drawable resource if coverDrawableResName is specified
        if (coverBitmap == null && track.coverDrawableResName.isNotBlank()) {
            try {
                val resId = context.resources.getIdentifier(track.coverDrawableResName, "drawable", context.packageName)
                if (resId != 0) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    coverBitmap = BitmapFactory.decodeResource(context.resources, resId, opts)
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback to default app logo if no embedded cover art found
        if (coverBitmap == null) {
            try {
                val logoRes = context.resources.getIdentifier("nimusic_n_logo_1786277350684", "drawable", context.packageName)
                if (logoRes != 0) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    coverBitmap = BitmapFactory.decodeResource(context.resources, logoRes, opts)
                }
            } catch (_: Exception) {}
        }

        cachedCoverBitmap = coverBitmap
        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState(playbackStateCode: Int, positionMs: Long, speed: Float) {
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(playbackStateCode, positionMs, speed)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    fun release() {
        try {
            context.unregisterReceiver(mediaReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notificationManager.cancel(NOTIFICATION_ID)
        mediaSession.isActive = false
        mediaSession.release()
    }

    companion object {
        const val CHANNEL_ID = "nimusic_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE_PLAY = "com.example.nimusic.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.nimusic.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.nimusic.ACTION_PREVIOUS"
    }
}
