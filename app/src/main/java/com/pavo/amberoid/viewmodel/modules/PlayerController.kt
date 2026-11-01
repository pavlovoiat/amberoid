package com.pavo.amberoid.viewmodel.modules

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pavo.amberoid.data.model.Song
import com.pavo.amberoid.data.model.SortOrder

class PlayerController(
    private val controller: MediaController?,
    private val isPlaying: Boolean,
    private val currentSong: Song?,
    private val currentPosition: Long,
    private val songs: List<Song>,
    private val isShuffleEnabled: Boolean,
    private val originalSongs: List<Song>,
    private val currentSortOrder: SortOrder,
    private val onCurrentSongChanged: (Song) -> Unit,
    private val onCurrentPositionChanged: (Long) -> Unit,
    private val onShuffleChanged: (Boolean) -> Unit,
    private val onRepeatModeChanged: (Int) -> Unit,
    private val onPlaylistsChanged: (List<Song>) -> Unit,
    private val applySort: (List<Song>, SortOrder) -> List<Song>

    ) {
    fun selectSong(song: Song) {
        val allSongs = songs
        val index = allSongs.indexOf(song)
        if (index == -1) return

        onCurrentSongChanged(song)

        controller?.let { controller ->
            if (controller.mediaItemCount != allSongs.size) {
                val mediaItems = allSongs.map {
                    MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build()
                }
                controller.setMediaItems(mediaItems)
                controller.prepare()
            }

            if (index in 0 until controller.mediaItemCount) {
                controller.seekTo(index, 0L)
                controller.prepare()
            }
        }
    }

    fun play() {
        controller?.play()
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        val currentSong = currentSong ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                selectSong(currentSong)
            }
            controller.play()
        }
    }

    fun playPrevious() {
        controller?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0L)
            } else if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            } else {
                it.seekTo(it.mediaItemCount - 1, 0L)
            }
        }
    }

    fun playNext() {
        controller?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            }
        }
    }

    fun toggleShuffle() {
        controller?.let { controller ->
            val newState = !isShuffleEnabled
            onShuffleChanged(newState)

            val current = currentSong
            val currentPos = currentPosition

            if (newState) {
                val shuffledList = originalSongs.filter { it.id != current?.id }.shuffled()
                onPlaylistsChanged(if (current != null) listOf(current) + shuffledList else shuffledList)
            } else {
                onPlaylistsChanged(applySort(originalSongs, currentSortOrder))
            }

            val mediaItems = songs.map {
                MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build()
            }
            val newIndex = songs.indexOfFirst { it.id == current?.id }

            if (newIndex != -1) {
                controller.setMediaItems(mediaItems, newIndex, currentPos)
            } else {
                controller.setMediaItems(mediaItems)
            }

            controller.shuffleModeEnabled = false
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            if (isPlaying) controller.play()
        }
    }

    fun toggleRepeatMode() {
        controller?.let { controller ->
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            onRepeatModeChanged(nextMode)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        onCurrentPositionChanged(positionMs)
    }
}