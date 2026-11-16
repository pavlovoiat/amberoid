package com.pavo.amberoid.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import com.pavo.amberoid.data.local.PlaybackPreferences
import com.pavo.amberoid.data.model.CoverPalette
import com.pavo.amberoid.data.model.Song
import com.pavo.amberoid.data.model.UiEvent
import com.pavo.amberoid.data.model.UiState
import com.pavo.amberoid.data.repository.AudioRepository
import com.pavo.amberoid.viewmodel.module.ArtworkThemeManager
import com.pavo.amberoid.viewmodel.module.MediaConnectionManager
import com.pavo.amberoid.viewmodel.module.PlayerControlManager
import com.pavo.amberoid.viewmodel.module.SongSortManager
import com.pavo.amberoid.viewmodel.module.VolumeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application
    private val repository = AudioRepository(appContext)
    private val preferences = PlaybackPreferences(appContext)

    private var originalSongs = emptyList<Song>()
    private var isStateRestored = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()


    private val mediaConnectionManager = MediaConnectionManager(
        appContext,
        viewModelScope,
        onPlayingChanged = { isPlaying ->
            _uiState.update { it.copy(isPlaying = isPlaying) }
        },
        onPlaybackStateChanged = { duration ->
            _uiState.update { it.copy(duration = duration) }
        },
        onMediaItemTransition = { newIndex ->
            val songs = _uiState.value.songs
            if (newIndex in songs.indices) {
                _uiState.update { it.copy(currentSong = songs[newIndex]) }
            }
        },
        onProgressUpdate = { position, duration ->
            _uiState.update { it.copy(currentPosition = position) }
            _uiState.update { it.copy(duration = duration) }
        }
    )

    private val artworkPalette = ArtworkThemeManager()
    private val volumeManager = VolumeManager(
        appContext,
        onUpdateState = { volume ->
            _uiState.update { it.copy(volume = volume) }
        }
    )

    private val songSortManager = SongSortManager(
        appContext,
        getController = { mediaConnectionManager.getController() },
        getOriginalSongs = { originalSongs },
        getUiState = { _uiState.value },
        onSongsChanged = { songs ->
            _uiState.update { it.copy(songs = songs) }
        }
    )


    init {
        observeInitialization()
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Play -> playerControlManager.play()
            is UiEvent.PlayPrevious -> playerControlManager.playPrevious()
            is UiEvent.PlayNext -> playerControlManager.playNext()
            is UiEvent.TogglePlayPause -> playerControlManager.togglePlayPause()
            is UiEvent.ToggleShuffle -> playerControlManager.toggleShuffle()
            is UiEvent.ToggleRepeatMode -> playerControlManager.toggleRepeatMode()
            is UiEvent.SeekTo -> playerControlManager.seekTo(event.positionMs)
            is UiEvent.SetVolume -> volumeManager.setVolume(event.volume)
            is UiEvent.UpdateSortOrder -> songSortManager.updateSortOrder(event.sortOrder)
            is UiEvent.SelectSong -> playerControlManager.selectSong(event.song)
        }
    }

    private val playerControlManager = PlayerControlManager(
        getController = { mediaConnectionManager.getController() },
        getUiState = { _uiState.value },
        getOriginalSongs = { originalSongs },
        getCurrentSortOrder = { songSortManager.currentSortOrder },
        onCurrentSongChanged = { currentSong ->
            _uiState.update { it.copy(currentSong = currentSong) }
        },
        onCurrentPositionChanged = { currentPosition ->
            _uiState.update { it.copy(currentPosition = currentPosition) }
        },
        onShuffleChanged = { isShuffleEnabled ->
            _uiState.update { it.copy(isShuffleEnabled = isShuffleEnabled) }
        },
        onRepeatModeChanged = { repeatMode ->
            _uiState.update { it.copy(repeatMode = repeatMode) }
        },
        onPlaylistsChanged = { songs ->
            _uiState.update { it.copy(songs = songs) }
        },
        applySort = { songList, order -> songSortManager.applySort(songList, order) }
    )

    private fun observeInitialization() {
        viewModelScope.launch {
            combine(
                _uiState
                    .map { it.songs }
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.WhileSubscribed(5000),
                        initialValue = _uiState.value.songs
                    ),
                mediaConnectionManager.controllerFlow
            ) { songList, controller ->
                songList to controller
            }.collectLatest { (songList, controller) ->
                if (songList.isNotEmpty() && controller != null) {
                    if (!isStateRestored) {
                        if (controller.mediaItemCount == 0) {
                            syncControllerWithSongs(controller, songList)
                            restorePlaybackState(controller, songList)
                        } else {
                            val currentItems = mutableListOf<Song>()
                            for (i in 0 until controller.mediaItemCount) {
                                val mediaId = controller.getMediaItemAt(i).mediaId
                                songList.find { it.id.toString() == mediaId }
                                    ?.let { currentItems.add(it) }
                            }
                            if (currentItems.isNotEmpty()) {
                                _uiState.update { it.copy(songs = currentItems) }
                            }
                        }
                        isStateRestored = true
                    }
                    updateStateFromController(controller)
                }
            }
        }
    }

    private fun restorePlaybackState(controller: MediaController, songs: List<Song>) {
        val lastSongId = preferences.getLastSongId()
        val lastPosition = preferences.getLastPosition()

        if (lastSongId != -1L) {
            val index = songs.indexOfFirst { it.id == lastSongId }
            if (index != -1) {
                controller.seekTo(index, lastPosition)
                _uiState.update { it.copy(currentSong = songs[index]) }
                _uiState.update { it.copy(currentPosition = lastPosition) }
                controller.prepare()
            }
        }
        isStateRestored = true
    }

    private fun updateStateFromController(controller: MediaController) {
        _uiState.update { it.copy(isPlaying = controller.isPlaying) }
        _uiState.update { it.copy(duration = controller.duration.coerceAtLeast(0L)) }
        _uiState.update { it.copy(repeatMode = controller.repeatMode) }
        _uiState.update { it.copy(currentPosition = controller.currentPosition.coerceAtLeast(0L)) }

        val currentIndex = controller.currentMediaItemIndex
        val currentList = _uiState.value.songs

        if (currentList.isNotEmpty() && currentIndex in currentList.indices) {
            _uiState.update { it.copy(currentSong = currentList[currentIndex]) }
        }
    }

    private fun syncControllerWithSongs(controller: MediaController, songs: List<Song>) {
        if (songs.isEmpty()) return

        if (controller.mediaItemCount == 0) {
            val mediaItems = songs.map {
                MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri)
                    .build()
            }
            controller.setMediaItems(mediaItems)
            controller.prepare()
        }

        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex in songs.indices) {
            _uiState.update { it.copy(currentSong = songs[currentIndex]) }
        }
    }

    override fun onCleared() {
        mediaConnectionManager.release()
        volumeManager.release()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val artworkBytes: StateFlow<ByteArray?> = _uiState
        .map { it.currentSong }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _uiState.value.currentSong
        )
        .mapLatest { song ->
            song?.let {
                withContext(Dispatchers.IO) {
                    artworkPalette.getArtwork(appContext, it.contentUri)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun getArtwork(context: Context, uri: Uri): ByteArray? {
        return artworkPalette.getArtwork(context, uri)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val colorScheme: StateFlow<CoverPalette> = artworkBytes
        .mapLatest { bytes -> artworkPalette.extractFullPalette(bytes) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CoverPalette(
                primary = Color.White,
                secondary = Color.LightGray,
                backgroundTop = Color(0xFF121212),
                backgroundBottom = Color.Black,
                surface = Color(0xFF1E1E1E),
                textPrimary = Color.White,
                textSecondary = Color.LightGray,
                accent = Color.Gray,
                isDark = true,
                allSwatches = emptyList()
            )
        )

    private val _sleepTimerText = MutableStateFlow("Off")
    val sleepTimerText: StateFlow<String> = _sleepTimerText.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerText.value = "Off"
            return
        }

        sleepTimerJob = viewModelScope.launch {
            var totalSeconds = minutes * 60
            while (totalSeconds > 0) {
                val mins = totalSeconds / 60
                val secs = totalSeconds % 60
                _sleepTimerText.value = String.format(Locale.US, "%02d:%02d", mins, secs)
                delay(1000.milliseconds)
                totalSeconds--
            }
            _sleepTimerText.value = "Off"
            mediaConnectionManager.getController()?.pause()
        }
    }

    fun loadSongs() {
        if (_uiState.value.songs.isNotEmpty()) return

        viewModelScope.launch {
            val loadedSongs = repository.getAudioFiles()
            originalSongs = loadedSongs
            _uiState.update { it.copy(songs = songSortManager.applySort(originalSongs, songSortManager.currentSortOrder)) }
        }
    }

    fun getSavedSortOrder() = preferences.getSortOrder()
}
