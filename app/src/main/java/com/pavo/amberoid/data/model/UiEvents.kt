package com.pavo.amberoid.data.model

sealed interface UiEvents {
    object playPrevious : UiEvents
    object playNext : UiEvents
    object togglePlayPause : UiEvents
    object toggleShuffle : UiEvents
    object toggleRepeatMode : UiEvents
    data class seekTo(val positionMs: Long) : UiEvents
    data class setVolume(val volume: Float) : UiEvents
    data class updateSortOrder(val sortOrder: SortOrder) : UiEvents
}