package com.pavo.amberoid.data.model

data class UiState(
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val volume: Float = 0f,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val songs: List<Song> = emptyList()
)