package com.pavo.amberoid.viewmodel.modules

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pavo.amberoid.data.local.PlaybackPreferences
import com.pavo.amberoid.data.model.Song
import com.pavo.amberoid.data.model.SortOrder
import com.pavo.amberoid.data.model.UiState
import com.pavo.amberoid.data.repository.AudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SongSortManager(
    context: Context,
    private val getController: () -> MediaController?,
    private val getOriginalSongs: () -> List<Song>,
    private val getUiState: () -> UiState,
    private val onSongsChanged: (List<Song>) -> Unit
) {
    private val repository = AudioRepository(context)
    private val preferences = PlaybackPreferences(context)
    private var _currentSortOrder = preferences.getSortOrder()
    val currentSortOrder = _currentSortOrder

    suspend fun loadSongs() {
        if (getUiState().songs.isNotEmpty()) return

        withContext(Dispatchers.IO) {
            val loadedSongs = repository.getAudioFiles()
            onSongsChanged(applySort(loadedSongs, _currentSortOrder))
        }
    }

    fun updateSortOrder(newOrder: SortOrder) {
        _currentSortOrder = newOrder
        preferences.saveSortOrder(newOrder)

        val currentSong = getUiState().currentSong
        val currentPos = getUiState().currentPosition

        val sortedList = applySort(getOriginalSongs(), newOrder)

        onSongsChanged(sortedList)

        getController()?.let { controller ->
            val mediaItems = sortedList.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.contentUri)
                    .build()
            }

            val newIndex = sortedList.indexOfFirst { it.id == currentSong?.id }

            if (newIndex == -1) {
                controller.setMediaItems(mediaItems, newIndex, currentPos)
            } else {
                controller.setMediaItems(mediaItems)
            }

            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            if (getUiState().isPlaying) controller.play()

        }
    }

    fun applySort(list: List<Song>, order: SortOrder): List<Song> {
        return when (order) {
            SortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> list.sortedBy { it.artist.lowercase() }
            SortOrder.ARTIST_DESC -> list.sortedByDescending { it.artist.lowercase() }
            SortOrder.DURATION_ASC -> list.sortedBy { it.duration }
            SortOrder.DURATION_DESC -> list.sortedByDescending { it.duration }
            SortOrder.DATE_ASC -> list.sortedBy { it.dateAdded }
            SortOrder.DATE_DESC -> list.sortedByDescending { it.dateAdded }
        }
    }
}