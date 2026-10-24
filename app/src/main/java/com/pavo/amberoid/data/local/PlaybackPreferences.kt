package com.pavo.amberoid.data.local

import android.content.Context
import androidx.core.content.edit
import com.pavo.amberoid.data.model.SortOrder

class PlaybackPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)

    fun saveLastPosition(songId: Long, positionMs: Long) {
        preferences.edit {
            putLong("last_song_id", songId)
                .putLong("last_position", positionMs)
        }
    }

    fun saveSortOrder(order: SortOrder) {
        preferences.edit {
            putString("sort_order", order.name)
        }
    }

    fun getLastSongId(): Long = preferences.getLong("last_song_id", -1L)
    fun getLastPosition(): Long = preferences.getLong("last_position", 0L)
    fun getSortOrder(): SortOrder {
        val name = preferences.getString("sort_order", SortOrder.DATE_DESC.name)
        return try {
            SortOrder.valueOf(name ?: SortOrder.DATE_DESC.name)
        } catch (e: Exception) {
            SortOrder.DATE_DESC
        }
    }
}