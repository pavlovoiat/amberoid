package com.pavo.amberoid.player.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VolumeManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private val _volume = MutableStateFlow(getCurrentVolumeRatio())
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                _volume.value = getCurrentVolumeRatio()
            }
        }
    }

    init {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(volumeReceiver, filter)
        }
    }

    private fun getCurrentVolumeRatio(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (maxVolume > 0) current.toFloat() / maxVolume else 0f
    }

    fun setVolume(newVolumeRatio: Float) {
        val clampedRatio = newVolumeRatio.coerceIn(0f, 1f)
        _volume.value = clampedRatio
        val targetVolume = (clampedRatio * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }

    fun increaseVolume(step: Float = 0.1f) {
        setVolume(_volume.value + step)
    }

    fun decreaseVolume(step: Float = 0.1f) {
        setVolume(_volume.value - step)
    }

    fun release() {
        try {
            context.unregisterReceiver(volumeReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}
