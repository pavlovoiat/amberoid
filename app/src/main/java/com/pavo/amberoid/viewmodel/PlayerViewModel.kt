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
import com.pavo.amberoid.data.repository.AudioRepository
import com.pavo.amberoid.service.PlaybackService
import com.pavo.amberoid.viewmodel.modules.ArtworkPalette
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application

    private val repository = AudioRepository(appContext)
    private val prefs = PlaybackPreferences(appContext)
    private val artworkPalette = ArtworkPalette()
    private val volumeManager = VolumeManager(appContext)

    private var mediaController: MediaController? = null
    private var progressUpdateJob: Job? = null

    private var originalSongs = emptyList<Song>()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private var isStateRestored = false

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    private val mediaControllerFlow: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    val volume: StateFlow<Float> = volumeManager.volume

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private var currentSortOrder: SortOrder = prefs.getSortOrder()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdate()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val newIndex = controller.currentMediaItemIndex
            val songList = _songs.value

            if (newIndex in songList.indices) {
                _currentSong.value = songList[newIndex]
            }
        }
    }

    init {
        setupMediaController()
        observeInitialization()
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
            combine(songs, mediaControllerFlow) { songList, controller ->
                songList to controller
            }.collectLatest { (songList, controller) ->
                if (songList.isNotEmpty() && controller != null) {
                    if (!isStateRestored) {
                        if (controller.mediaItemCount == 0) {
                            syncControllerWithSongs(controller, songList)
                            restorePlaybackState(controller, songList)
                        } else {
                            // If controller already has items (service was running), sync our local UI list once
                            val currentItems = mutableListOf<Song>()
                            for (i in 0 until controller.mediaItemCount) {
                                val mediaId = controller.getMediaItemAt(i).mediaId
                                songList.find { it.id.toString() == mediaId }?.let { currentItems.add(it) }
                            }
                            if (currentItems.isNotEmpty()) {
                                _songs.value = currentItems
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
                _currentSong.value = songs[index]
                _currentPosition.value = lastPosition
                controller.prepare()
            }
        }
        isStateRestored = true
    }

    private fun updateStateFromController(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _duration.value = controller.duration.coerceAtLeast(0L)
        _repeatMode.value = controller.repeatMode
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)

        val currentIndex = controller.currentMediaItemIndex
        val currentList = _songs.value
        
        if (currentList.isNotEmpty() && currentIndex in currentList.indices) {
            _currentSong.value = currentList[currentIndex]
        }
    }

    private fun startProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (mediaController?.isPlaying == true) {
                val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                _currentPosition.value = pos
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                delay(500.milliseconds)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun loadSongs() {
        if (_songs.value.isNotEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val loadedSongs = repository.getAudioFiles()
            originalSongs = loadedSongs
            _songs.value = applySort(loadedSongs, currentSortOrder)
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
            _currentSong.value = songs[currentIndex]
        }
    }

    fun selectSong(song: Song) {
        val allSongs = _songs.value
        val index = allSongs.indexOf(song)
        if (index == -1) return

        _currentSong.value = song

        mediaController?.let { controller ->
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

    fun togglePlayPause() {
        val controller = mediaController ?: return
        val current = _currentSong.value ?: return

        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                selectSong(current)
            }
            controller.play()
        }
    }

    fun play() {
        mediaController?.play()
    }

    fun playPrevious() {
        mediaController?.let {
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
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            }
        }
    }

    fun toggleShuffle() {
        mediaController?.let { controller ->
            val newState = !isShuffleEnabled.value
            _isShuffleEnabled.value = newState

            val current = _currentSong.value
            val currentPos = _currentPosition.value

            if (newState) {
                val shuffledList = originalSongs.filter { it.id != current?.id }.shuffled()
                _songs.value = if (current != null) listOf(current) + shuffledList else shuffledList
            } else {
                _songs.value = applySort(originalSongs, currentSortOrder)
            }

            val mediaItems = _songs.value.map {
                MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build()
            }
            val newIndex = _songs.value.indexOfFirst { it.id == current?.id }

            if (newIndex != -1) {
                controller.setMediaItems(mediaItems, newIndex, currentPos)
            } else {
                controller.setMediaItems(mediaItems)
            }

            controller.shuffleModeEnabled = false
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            if (_isPlaying.value) controller.play()
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let { controller ->
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            _repeatMode.value = nextMode
        }
    }

    override fun onCleared() {
        progressUpdateJob?.cancel()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        volumeManager.release()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val artworkBytes: StateFlow<ByteArray?> = _currentSong
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

    fun setVolume(newVolumeRatio: Float) {
        volumeManager.setVolume(newVolumeRatio)
    }

    fun increaseVolume(step: Float = 0.1f) {
        volumeManager.increaseVolume(step)
    }

    fun decreaseVolume(step: Float = 0.1f) {
        volumeManager.decreaseVolume(step)
    }

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

        val currentSongObj = _currentSong.value
        val currentPos = _currentPosition.value

        val sortedList = applySort(originalSongs, newOrder)

        _songs.value = sortedList

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
            if (_isPlaying.value) controller.play()
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
