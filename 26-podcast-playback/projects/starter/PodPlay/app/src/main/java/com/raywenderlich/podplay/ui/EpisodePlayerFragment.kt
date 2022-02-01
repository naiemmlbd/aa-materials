package com.raywenderlich.podplay.ui

import android.animation.ValueAnimator
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
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
    private var episodeDuration: Long = 0
    private var draggingScrubber: Boolean = false
    private var progressAnimator: ValueAnimator? = null
    private var nextPrevVal = 0

    companion object {
        fun newInstance(): EpisodePlayerFragment {
            return EpisodePlayerFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initMediaBrowser()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        databinding = FragmentEpisodePlayerBinding.inflate(inflater, container, false)
        return databinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        updateControls()
        nextPrevVal = podcastViewModel.intialEpisodePosition

    }
    private fun onNextClick(){
        nextPrevVal++
        podcastViewModel.activeEpisodeViewData = podcastViewModel.episodeList[nextPrevVal]
        updateControls()
        checkAndRegisterMediaController()
        podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        controller.transportControls.skipToNext()

    }

    private fun togglePlayPause() {
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller.playbackState != null) {
            if (controller.playbackState.state ==
                PlaybackStateCompat.STATE_PLAYING
            ) {
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
        val newPosition = controller.playbackState.position + seconds * 1000
        controller.transportControls.seekTo(newPosition)
    }

    // 1
    private fun animateScrubber(progress: Int, speed: Float) {
        // 2
        val timeRemaining = ((episodeDuration - progress) / speed).toInt()
        // 3
        if (timeRemaining < 0) {
            return;
        }
        // 4
        progressAnimator = ValueAnimator.ofInt(
            progress, episodeDuration.toInt()
        )
        progressAnimator?.let { animator ->
            // 5
            animator.duration = timeRemaining.toLong()
            // 6
            animator.interpolator = LinearInterpolator()
            // 7
            animator.addUpdateListener {
                if (draggingScrubber) {
                    // 8
                    animator.cancel()
                } else {
                    // 9
                    databinding.seekBar.progress = animator.animatedValue as Int
                }
            }
            // 10
            animator.start()
        }
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
        databinding.nextButton.setOnClickListener {
            onNextClick()
        }
        databinding.forwardButton.setOnClickListener {
            seekBy(30)
        }
        databinding.replayButton.setOnClickListener {
            seekBy(-10)
        }

        // 1
        databinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // 2
                databinding.currentTimeTextView.text =
                    DateUtils.formatElapsedTime((progress / 1000).toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 3
                draggingScrubber = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // 4
                draggingScrubber = false
                // 5
                val fragmentActivity = activity as FragmentActivity
                val controller = MediaControllerCompat.getMediaController(fragmentActivity)
                if (controller.playbackState != null) {
                    // 6
                    controller.transportControls.seekTo(seekBar.progress.toLong())
                } else {
                    // 7
                    seekBar.progress = 0
                }
            }
        })
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

    private fun updateControlsFromMetadata(metadata: MediaMetadataCompat) {
        episodeDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        databinding.endTimeTextView.text = DateUtils.formatElapsedTime((episodeDuration / 1000))
        databinding.seekBar.max = episodeDuration.toInt()
    }

    private fun updateControlsFromController() {
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller != null) {
            val metadata = controller.metadata
            if (metadata != null) {
                handleStateChange(
                    controller.playbackState.state,
                    controller.playbackState.position, playerSpeed
                )
                updateControlsFromMetadata(controller.metadata)
            }
        }
    }

    private fun startPlaying(episodeViewData: PodcastViewModel.EpisodeViewData) {
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        val viewData = podcastViewModel.activePodcast ?: return
        val bundle = Bundle()
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_TITLE,
            episodeViewData.title
        )
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_ARTIST,
            viewData.feedTitle
        )
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            viewData.imageUrl
        )

        controller.transportControls.playFromUri(
            Uri.parse(episodeViewData.mediaUrl), bundle
        )
    }

    private fun handleStateChange(state: Int, position: Long, speed: Float) {
        progressAnimator?.let {
            it.cancel()
            progressAnimator = null
        }
        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        databinding.playToggleButton.isActivated = isPlaying

        val progress = position.toInt()
        databinding.seekBar.progress = progress
        val speedButtonText = "${playerSpeed}x"
        databinding.speedButton.text = speedButtonText

        if (isPlaying) {
            animateScrubber(progress, speed)
        }
    }

    override fun onStart() {
        super.onStart()
        checkAndRegisterMediaController()
    }

    private fun checkAndRegisterMediaController() {
        if (mediaBrowser.isConnected) {
            val fragmentActivity = activity as FragmentActivity
            if (MediaControllerCompat.getMediaController
                    (fragmentActivity) == null
            ) {
                registerMediaController(mediaBrowser.sessionToken)
            }
            updateControlsFromController()
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
        progressAnimator?.cancel()
    }

    private fun initMediaBrowser() {
        val fragmentActivity = activity as FragmentActivity
        mediaBrowser = MediaBrowserCompat(
            fragmentActivity,
            ComponentName(
                fragmentActivity,
                PodplayMediaService::class.java
            ),
            MediaBrowserCallBacks(),
            null
        )
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

    inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            println(
                "metadata changed to ${
                    metadata?.getString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_URI
                    )
                }"
            )
            metadata?.let { updateControlsFromMetadata(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)
            println("state changed to $state")
            val state = state ?: return
            handleStateChange(state.state, state.position, state.playbackSpeed)
        }
    }

    inner class MediaBrowserCallBacks :
        MediaBrowserCompat.ConnectionCallback() {
        // 1
        override fun onConnected() {
            super.onConnected()
            // 2
            registerMediaController(mediaBrowser.sessionToken)
            println("onConnected")
            updateControlsFromController()
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
