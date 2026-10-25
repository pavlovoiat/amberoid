package com.pavo.amberoid.data.model

sealed interface UiEvent {
    object PlayPrevious : UiEvent
    object PlayNext : UiEvent
    object TogglePlayPause : UiEvent
    object ToggleShuffle : UiEvent
    object ToggleRepeatMode : UiEvent
    data class SeekTo(val positionMs: Long) : UiEvent
    data class SetVolume(val volume: Float) : UiEvent
    data class UpdateSortOrder(val sortOrder: SortOrder) : UiEvent
}