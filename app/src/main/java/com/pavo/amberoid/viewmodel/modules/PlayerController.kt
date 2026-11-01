package com.pavo.amberoid.viewmodel.modules

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pavo.amberoid.data.model.Song
import com.pavo.amberoid.data.model.SortOrder

class PlayerController(
    private val getController: () -> MediaController?,
    private val getIsPlaying: () -> Boolean,
    private val getCurrentSong: () -> Song?,
    private val getCurrentPosition: () -> Long,
    private val getSongs: () -> List<Song>,
    private val getIsShuffleEnabled: () -> Boolean,
    private val getOriginalSongs: () -> List<Song>,
    private val getCurrentSortOrder: () -> SortOrder,
    private val onCurrentSongChanged: (Song) -> Unit,
    private val onCurrentPositionChanged: (Long) -> Unit,
    private val onShuffleChanged: (Boolean) -> Unit,
    private val onRepeatModeChanged: (Int) -> Unit,
    private val onPlaylistsChanged: (List<Song>) -> Unit,
    private val applySort: (List<Song>, SortOrder) -> List<Song>
) {
    fun selectSong(song: Song) {
        val allSongs = getSongs()
        val index = allSongs.indexOf(song)
        if (index == -1) return

        onCurrentSongChanged(song)

        getController()?.let { controller ->
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
        getController()?.play()
    }

    fun togglePlayPause() {
        val controller = getController() ?: return
        val currentSong = getCurrentSong() ?: return
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
        getController()?.let {
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
        getController()?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            }
        }
    }

    fun toggleShuffle() {
        getController()?.let { controller ->
            val isShuffleEnabled = getIsShuffleEnabled()
            val newState = !isShuffleEnabled
            onShuffleChanged(newState)

            val current = getCurrentSong()
            val currentPos = getCurrentPosition()
            val originalSongs = getOriginalSongs()
            val currentSortOrder = getCurrentSortOrder()

            val newSongs = if (newState) {
                val shuffledList = originalSongs.filter { it.id != current?.id }.shuffled()
                if (current != null) listOf(current) + shuffledList else shuffledList
            } else {
                applySort(originalSongs, currentSortOrder)
            }
            onPlaylistsChanged(newSongs)

            val mediaItems = newSongs.map {
                MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build()
            }
            val newIndex = newSongs.indexOfFirst { it.id == current?.id }

            if (newIndex != -1) {
                controller.setMediaItems(mediaItems, newIndex, currentPos)
            } else {
                controller.setMediaItems(mediaItems)
            }

            controller.shuffleModeEnabled = false
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            if (getIsPlaying()) controller.play()
        }
    }

    fun toggleRepeatMode() {
        getController()?.let { controller ->
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
        getController()?.seekTo(positionMs)
        onCurrentPositionChanged(positionMs)
    }
}