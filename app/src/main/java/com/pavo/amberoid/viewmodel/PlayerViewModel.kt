package com.pavo.amberoid.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pavo.amberoid.data.local.PlaybackPreferences
import com.pavo.amberoid.data.model.CoverPalette
import com.pavo.amberoid.data.model.Song
import com.pavo.amberoid.data.model.SortOrder
import com.pavo.amberoid.data.model.UiEvent
import com.pavo.amberoid.data.model.UiState
import com.pavo.amberoid.data.repository.AudioRepository
import com.pavo.amberoid.service.PlaybackService
import com.pavo.amberoid.viewmodel.modules.ArtworkPalette
import com.pavo.amberoid.viewmodel.modules.PlayerController
import com.pavo.amberoid.viewmodel.modules.VolumeManager
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
    private val prefs = PlaybackPreferences(appContext)
    private val artworkPalette = ArtworkPalette()
    private var mediaController: MediaController? = null
    private var progressUpdateJob: Job? = null

    private var originalSongs = emptyList<Song>()
    private var isStateRestored = false

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    private val mediaControllerFlow: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private var currentSortOrder: SortOrder = prefs.getSortOrder()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val volumeManager = VolumeManager(
        appContext,
        onUpdateState = { volume ->
            _uiState.update { it.copy(volume = volume) }
        }
    )

    init {
        setupMediaController()
        observeInitialization()
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Play -> playerController.play()
            is UiEvent.PlayPrevious -> playerController.playPrevious()
            is UiEvent.PlayNext -> playerController.playNext()
            is UiEvent.TogglePlayPause -> playerController.togglePlayPause()
            is UiEvent.ToggleShuffle -> playerController.toggleShuffle()
            is UiEvent.ToggleRepeatMode -> playerController.toggleRepeatMode()
            is UiEvent.SeekTo -> playerController.seekTo(event.positionMs)
            is UiEvent.SetVolume -> volumeManager.setVolume(event.volume)
            is UiEvent.UpdateSortOrder -> updateSortOrder(event.sortOrder)
            is UiEvent.SelectSong -> playerController.selectSong(event.song)
        }
    }

    private val playerController = PlayerController(
        getController = { mediaController },
        getUiState = { _uiState.value },
        getOriginalSongs = { originalSongs },
        getCurrentSortOrder = { currentSortOrder },
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
        applySort = ::applySort
    )

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }

            if (isPlaying) {
                startProgressUpdate()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _uiState.update { it.copy(duration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val newIndex = controller.currentMediaItemIndex
            val songList = _uiState.value.songs

            if (newIndex in songList.indices) {
                _uiState.update { it.copy(currentSong = songList[newIndex]) }
            }
        }
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )

        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                _mediaController.value = controller

                updateStateFromController(controller)

                if (controller.isPlaying) {
                    startProgressUpdate()
                }

                controller.addListener(playerListener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

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
                mediaControllerFlow
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
                                songList.find { it.id.toString() == mediaId }?.let { currentItems.add(it) }
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
        val lastSongId = prefs.getLastSongId()
        val lastPosition = prefs.getLastPosition()

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

    private fun startProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (mediaController?.isPlaying == true) {
                val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                _uiState.update { it.copy(currentPosition = pos) }
                _uiState.update { it.copy(duration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L) }
                delay(500.milliseconds)
            }
        }
    }

    fun loadSongs() {
        if (_uiState.value.songs.isNotEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val loadedSongs = repository.getAudioFiles()
            originalSongs = loadedSongs
            _uiState.update { it.copy(songs = applySort(loadedSongs, currentSortOrder)) }
        }
    }

    private fun syncControllerWithSongs(controller: MediaController, songs: List<Song>) {
        if (songs.isEmpty()) return

        if (controller.mediaItemCount == 0) {
            val mediaItems = songs.map { 
                MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build() 
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
        progressUpdateJob?.cancel()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
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
            mediaController?.pause()
        }
    }

    fun updateSortOrder(newOrder: SortOrder) {
        currentSortOrder = newOrder
        prefs.saveSortOrder(newOrder)

        val currentSongObj = _uiState.value.currentSong
        val currentPos = _uiState.value.currentPosition

        val sortedList = applySort(originalSongs, newOrder)

        _uiState.update { it.copy(songs = sortedList) }

        mediaController?.let { controller ->
            val mediaItems = sortedList.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.contentUri)
                    .build()
            }

            val newIndex = sortedList.indexOfFirst { it.id == currentSongObj?.id }

            if (newIndex != -1) {
                controller.setMediaItems(mediaItems, newIndex, currentPos)
            } else {
                controller.setMediaItems(mediaItems)
            }

            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            if (_uiState.value.isPlaying) controller.play()
        }
    }

    private fun applySort(list: List<Song>, order: SortOrder): List<Song> {
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

    fun getSavedSortOrder() = prefs.getSortOrder()
}
