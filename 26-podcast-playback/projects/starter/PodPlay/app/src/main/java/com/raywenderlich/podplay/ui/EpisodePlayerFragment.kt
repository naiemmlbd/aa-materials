package com.raywenderlich.podplay.ui

import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.raywenderlich.podplay.databinding.FragmentEpisodePlayerBinding
import com.raywenderlich.podplay.service.PodplayMediaCallback.Companion.CMD_CHANGESPEED
import com.raywenderlich.podplay.service.PodplayMediaCallback.Companion.CMD_EXTRA_SPEED
import com.raywenderlich.podplay.service.PodplayMediaService
import com.raywenderlich.podplay.util.HtmlUtils
import com.raywenderlich.podplay.viewmodel.PodcastViewModel

class EpisodePlayerFragment : Fragment() {

  private lateinit var databinding: FragmentEpisodePlayerBinding
  private val podcastViewModel: PodcastViewModel by activityViewModels()

  private lateinit var mediaBrowser: MediaBrowserCompat
  private var mediaControllerCallback: MediaControllerCallback? = null
  private var playerSpeed: Float = 1.0f

  companion object {
    fun newInstance(): EpisodePlayerFragment {
      return EpisodePlayerFragment()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initMediaBrowser()
  }

  override fun onCreateView(inflater: LayoutInflater,
                            container: ViewGroup?,
                            savedInstanceState: Bundle?): View {
  databinding = FragmentEpisodePlayerBinding.inflate(inflater, container, false)
    return databinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupControls()
    updateControls()
  }

  private fun togglePlayPause() {
    val fragmentActivity = activity as FragmentActivity
    val controller = MediaControllerCompat.getMediaController(fragmentActivity)
    if (controller.playbackState != null) {
      if (controller.playbackState.state ==
        PlaybackStateCompat.STATE_PLAYING) {
        controller.transportControls.pause()
      } else {
        podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
      }
    } else {
      podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
    }
  }

  private fun changeSpeed() {
    // 1
    playerSpeed += 0.25f
    if (playerSpeed > 2.0f) {
      playerSpeed = 0.75f
    }
    // 2
    val bundle = Bundle()
    bundle.putFloat(CMD_EXTRA_SPEED, playerSpeed)
    // 3
    val fragmentActivity = activity as FragmentActivity
    val controller = MediaControllerCompat.getMediaController(fragmentActivity)
    controller.sendCommand(CMD_CHANGESPEED, bundle, null)
    // 4
    val speedButtonText = "${playerSpeed}x"
    databinding.speedButton.text = speedButtonText
  }

  private fun seekBy(seconds: Int) {
    val fragmentActivity = activity as FragmentActivity
    val controller = MediaControllerCompat.getMediaController(fragmentActivity)
    val newPosition = controller.playbackState.position + seconds*1000
    controller.transportControls.seekTo(newPosition)
  }

  private fun setupControls() {
    databinding.playToggleButton.setOnClickListener {
      togglePlayPause()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      databinding.speedButton.setOnClickListener {
        changeSpeed()
      }
    } else {
      databinding.speedButton.visibility = View.INVISIBLE
    }
    databinding.forwardButton.setOnClickListener {
      seekBy(30)
    }
    databinding.replayButton.setOnClickListener {
      seekBy(-10)
    }
  }

  private fun updateControls() {
    // 1
    databinding.episodeTitleTextView.text = podcastViewModel.activeEpisodeViewData?.title
    // 2
    val htmlDesc = podcastViewModel.activeEpisodeViewData?.description ?: ""
    val descSpan = HtmlUtils.htmlToSpannable(htmlDesc)
    databinding.episodeDescTextView.text = descSpan
    databinding.episodeDescTextView.movementMethod = ScrollingMovementMethod()
    // 3
    val fragmentActivity = activity as FragmentActivity
    Glide.with(fragmentActivity)
      .load(podcastViewModel.podcastLiveData.value?.imageUrl)
      .into(databinding.episodeImageView)

    val speedButtonText = "${playerSpeed}x"
    databinding.speedButton.text = speedButtonText
  }

  private fun startPlaying(episodeViewData: PodcastViewModel.EpisodeViewData) {
    val fragmentActivity = activity as FragmentActivity
    val controller = MediaControllerCompat.getMediaController(fragmentActivity)
    val viewData = podcastViewModel.activePodcast ?: return
    val bundle = Bundle()
    bundle.putString(
      MediaMetadataCompat.METADATA_KEY_TITLE,
      episodeViewData.title)
    bundle.putString(
      MediaMetadataCompat.METADATA_KEY_ARTIST,
      viewData.feedTitle)
    bundle.putString(
      MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
      viewData.imageUrl)

    controller.transportControls.playFromUri(
      Uri.parse(episodeViewData.mediaUrl), bundle)
  }

  private fun handleStateChange(state: Int) {
    val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
    databinding.playToggleButton.isActivated = isPlaying
  }

  override fun onStart() {
    super.onStart()
    if (mediaBrowser.isConnected) {
      val fragmentActivity = activity as FragmentActivity
      if (MediaControllerCompat.getMediaController
          (fragmentActivity) == null) {
        registerMediaController(mediaBrowser.sessionToken)
      }
    } else {
      mediaBrowser.connect()
    }
  }

  override fun onStop() {
    super.onStop()
    val fragmentActivity = activity as FragmentActivity
    if (MediaControllerCompat.getMediaController(fragmentActivity) != null) {
      mediaControllerCallback?.let {
        MediaControllerCompat.getMediaController(fragmentActivity)
          .unregisterCallback(it)
      }
    }
  }

  private fun initMediaBrowser() {
    val fragmentActivity = activity as FragmentActivity
    mediaBrowser = MediaBrowserCompat(fragmentActivity,
      ComponentName(fragmentActivity,
        PodplayMediaService::class.java),
      MediaBrowserCallBacks(),
      null)
  }

  private fun registerMediaController(token: MediaSessionCompat.Token) {
    // 1
    val fragmentActivity = activity as FragmentActivity
    // 2
    val mediaController = MediaControllerCompat(fragmentActivity, token)
    // 3
    MediaControllerCompat.setMediaController(fragmentActivity, mediaController)
    // 4
    mediaControllerCallback = MediaControllerCallback()
    mediaController.registerCallback(mediaControllerCallback!!)
  }

  inner class MediaControllerCallback: MediaControllerCompat.Callback() {
    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
      super.onMetadataChanged(metadata)
      println(
        "metadata changed to ${metadata?.getString(
          MediaMetadataCompat.METADATA_KEY_MEDIA_URI)}")
    }

    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
      super.onPlaybackStateChanged(state)
      println("state changed to $state")
      val state = state ?: return
      handleStateChange(state.state)
    }
  }

  inner class MediaBrowserCallBacks:
    MediaBrowserCompat.ConnectionCallback() {
    // 1
    override fun onConnected() {
      super.onConnected()
      // 2
      registerMediaController(mediaBrowser.sessionToken)
      println("onConnected")
    }

    override fun onConnectionSuspended() {
      super.onConnectionSuspended()
      println("onConnectionSuspended")
      // Disable transport controls
    }

    override fun onConnectionFailed() {
      super.onConnectionFailed()
      println("onConnectionFailed")
      // Fatal error handling
    }
  }
}
