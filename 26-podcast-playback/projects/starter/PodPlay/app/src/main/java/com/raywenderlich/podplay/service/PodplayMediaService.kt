package com.raywenderlich.podplay.service

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat

class PodplayMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createMediaSession()
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(this, "PopplayMediaService")
        sessionToken = mediaSession.sessionToken
        val callback = PodplayMediaCallback(this,mediaSession)
        mediaSession.setCallback(callback)
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
            PODPLAY_EMPTY_ROOT_MEDIA_ID, null)
    }

    companion object {
        private const val PODPLAY_EMPTY_ROOT_MEDIA_ID =
            "podplay_empty_root_media_id"
    }

}
