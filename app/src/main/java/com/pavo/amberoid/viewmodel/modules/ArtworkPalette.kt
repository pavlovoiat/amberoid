package com.pavo.amberoid.viewmodel.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import com.pavo.amberoid.data.model.CoverPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtworkPalette {
    private val artworkCache = mutableMapOf<Uri, ByteArray?>()

    fun getArtwork(context: Context, uri: Uri): ByteArray? {
        if (artworkCache.containsKey(uri)) return artworkCache[uri]

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture
            artworkCache[uri] = bytes
            bytes
        } catch (e: Exception) {
            artworkCache[uri] = null
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun extractFullPalette(bytes: ByteArray?): CoverPalette {
        val defaultPrimary = Color.White
        val defaultBackgroundTop = Color(0xFF121212)
        val defaultBackgroundBottom = Color.Black

        if (bytes == null) {
            return CoverPalette(
                primary = defaultPrimary,
                secondary = Color.LightGray,
                backgroundTop = defaultBackgroundTop,
                backgroundBottom = defaultBackgroundBottom,
                surface = Color(0xFF1E1E1E),
                textPrimary = Color.White,
                textSecondary = Color.LightGray.copy(0.7f),
                accent = Color.Gray,
                isDark = true,
                allSwatches = emptyList()
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext extractFullPalette(null)

                val palette = Palette.from(bitmap)
                    .maximumColorCount(24)
                    .generate()

                val vibrant = palette.vibrantSwatch
                val lightVibrant = palette.lightVibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val dominant = palette.dominantSwatch
                val muted = palette.mutedSwatch

                val avgSaturation = palette.swatches.map { it.hsl[1] }.average()
                val isMonochrome = avgSaturation < 0.12f

                val primaryColor: Color
                val accentColor: Color
                val topColor: Color
                val bottomColor: Color
                val isDark: Boolean

                if (isMonochrome) {
                    primaryColor = Color.White.copy(alpha = 0.95f)
                    accentColor = Color(0xFF8E8E93)
                    topColor = Color(0xFF1C1C1E)
                    bottomColor = Color.Black
                    isDark = true
                } else {
                    primaryColor = vibrant?.rgb?.let { Color(it) }
                        ?: lightVibrant?.rgb?.let { Color(it) }
                        ?: dominant?.rgb?.let { Color(it) }
                        ?: defaultPrimary

                    accentColor = lightVibrant?.rgb?.let { Color(it) }
                        ?: vibrant?.rgb?.let { Color(it) }
                        ?: muted?.rgb?.let { Color(it) }
                        ?: Color.Gray

                    topColor = darkVibrant?.rgb?.let { Color(it) }
                        ?: muted?.rgb?.let { Color(it) }
                        ?: Color(0xFF1A1A1A)

                    bottomColor = dominant?.rgb?.let { Color(it) }
                        ?: defaultBackgroundBottom
                    
                    isDark = calculateLuminance(bottomColor) < 0.5f
                }

                val titleTextColor = if (isDark) Color.White else Color.Black
                val bodyTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)

                val extractedSwatches = palette.swatches
                    .sortedByDescending { it.population }
                    .take(10)
                    .map { Color(it.rgb) }

                CoverPalette(
                    primary = primaryColor,
                    secondary = accentColor,
                    backgroundTop = topColor,
                    backgroundBottom = bottomColor,
                    surface = if (isDark) Color(0xFF1E1E2C) else Color(0xFFE0E0E0),
                    textPrimary = titleTextColor,
                    textSecondary = bodyTextColor,
                    accent = accentColor,
                    isDark = isDark,
                    allSwatches = extractedSwatches
                )
            } catch (e: Exception) {
                extractFullPalette(null)
            }
        }
    }

    private fun calculateLuminance(color: Color): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            color.luminance()
        } else {
            0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        }
    }
}
