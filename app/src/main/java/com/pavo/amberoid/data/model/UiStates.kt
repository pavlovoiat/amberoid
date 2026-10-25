package com.pavo.amberoid.data.model

data class UiStates(
    val isPlaying: Boolean,
    val currentSong: Song?,
    val currentPosition: Long,
    val duration: Long,
    val volume: Float,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int,
    val songs: List<Song>
)