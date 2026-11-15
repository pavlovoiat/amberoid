package com.pavo.amberoid.viewmodel.modules

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pavo.amberoid.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MediaConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPlayingChanged: (Boolean) -> Unit,
    private val onPlaybackStateChanged: (Long) -> Unit,
    private val onMediaItemTransition: (Int) -> Unit,
    private val onProgressUpdate: (position: Long, duration: Long) -> Unit
) {
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _controllerFlow = MutableStateFlow<MediaController?>(null)
    val controllerFlow: StateFlow<MediaController?> = _controllerFlow.asStateFlow()

    fun getController(): MediaController? = mediaController

    init {
        setupMediaController()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayingChanged(isPlaying)
            if (isPlaying) startProgressUpdate() else progressJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                onPlaybackStateChanged(mediaController?.duration?.coerceAtLeast(0L) ?: 0L)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaController?.let { onMediaItemTransition(it.currentMediaItemIndex) }
        }
    }

    private fun setupMediaController() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                _controllerFlow.value = controller
                controller.addListener(playerListener)
                if (controller.isPlaying) startProgressUpdate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (mediaController?.isPlaying == true) {
                val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val dur = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                onProgressUpdate(pos, dur)
                delay(500.milliseconds)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
    }
}
