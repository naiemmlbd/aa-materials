package com.raywenderlich.podplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.raywenderlich.podplay.R
import com.raywenderlich.podplay.ui.PodcastActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL

class PodplayMediaService : MediaBrowserServiceCompat(), PodplayMediaListener {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createMediaSession()
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(this, "PopplayMediaService")
        sessionToken = mediaSession.sessionToken
        val callback = PodplayMediaCallback(this, mediaSession)
        callback.listener = this
        mediaSession.setCallback(callback)
    }

    private fun displayNotification() {
        // 1
        if (mediaSession.controller.metadata == null) {
            return
        }
        // 2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        // 3
        val mediaDescription =
            mediaSession.controller.metadata.description
        // 4
        GlobalScope.launch {
            // 5
            val iconUrl = URL(mediaDescription.iconUri.toString())
            // 6
            val bitmap =
                BitmapFactory.decodeStream(iconUrl.openStream())
            // 7
            val notification = createNotification(
                mediaDescription,
                bitmap
            )
            // 8
            ContextCompat.startForegroundService(
                this@PodplayMediaService,
                Intent(
                    this@PodplayMediaService,
                    PodplayMediaService::class.java
                )
            )
            // 9
            startForeground(
                PodplayMediaService.NOTIFICATION_ID,
                notification
            )
        }
    }

    // 1
    private fun createNotification(
        mediaDescription: MediaDescriptionCompat,
        bitmap: Bitmap?
    ): Notification {

        // 2
        val notificationIntent = getNotificationIntent()
        // 3
        val (pauseAction, playAction) = getPausePlayActions()
        // 4
        val notification = NotificationCompat.Builder(
            this@PodplayMediaService, PLAYER_CHANNEL_ID
        )
        // 5
        notification
            .setContentTitle(mediaDescription.title)
            .setContentText(mediaDescription.subtitle)
            .setLargeIcon(bitmap)
            .setContentIntent(notificationIntent)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent
                    (this, PlaybackStateCompat.ACTION_STOP)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_episode_icon)
            .addAction(if (isPlaying()) pauseAction else playAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this, PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
        // 6
        return notification.build()
    }

    private fun getPausePlayActions():
            Pair<NotificationCompat.Action, NotificationCompat.Action> {
        val pauseAction = NotificationCompat.Action(
            R.drawable.ic_pause_white, getString(R.string.pause),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PAUSE
            )
        )

        val playAction = NotificationCompat.Action(
            R.drawable.ic_play_arrow_white, getString(R.string.play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PLAY
            )
        )
        return Pair(pauseAction, playAction)
    }

    private fun isPlaying() =
        mediaSession.controller.playbackState != null &&
                mediaSession.controller.playbackState.state ==
                PlaybackStateCompat.STATE_PLAYING

    private fun getNotificationIntent(): PendingIntent {
        val openActivityIntent = Intent(
            this,
            PodcastActivity::class.java
        )
        openActivityIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(
                this@PodplayMediaService,
                0,
                openActivityIntent,
                PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this@PodplayMediaService,
                0,
                openActivityIntent,
                FLAG_CANCEL_CURRENT
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
        if (notificationManager.getNotificationChannel
                (PLAYER_CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                PLAYER_CHANNEL_ID,
                "Player", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId.equals(PODPLAY_EMPTY_ROOT_MEDIA_ID)) {
            result.sendResult(null)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int, rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(
            PODPLAY_EMPTY_ROOT_MEDIA_ID, null
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession.controller.transportControls.stop()
    }


    companion object {
        private const val PODPLAY_EMPTY_ROOT_MEDIA_ID =
            "podplay_empty_root_media_id"
        private const val PLAYER_CHANNEL_ID = "podplay_player_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStateChanged() {
        displayNotification()
    }

    override fun onStopPlaying() {
        stopSelf()
        stopForeground(true)
    }

    override fun onPausePlaying() {
        stopForeground(false)
    }
}
