package com.pavo.amberoid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.util.Locale
import kotlin.random.Random

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@Composable
fun WaveformSeekBar(
    progressFraction: Float,
    songId: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color(0x66FFFFFF)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 50),
        label = "WaveformProgress"
    )

    val barHeights = remember(songId) {
        val random = Random(songId.toInt())
        val barCount = 75

        List(barCount) { _ ->
            0.25f + random.nextFloat() * 0.75f
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier = modifier
            .size(300.dp, 50.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newFraction)
                }
            }
            .pointerInput(songId) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        isDragging = true
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val barCount = barHeights.size
        val gap = 6f
        val barWidth = (width - (gap * (barCount - 1))) / barCount

        barHeights.forEachIndexed { index, heightFactor ->
            val left = index * (barWidth + gap)
            val barHeight = height * heightFactor
            val top = (height - barHeight) / 2f

            val barCenterFraction = (left + barWidth / 2f) / width
            val color = when {
                isDragging -> when {
                    barCenterFraction in minOf(animatedProgress, dragFraction)..maxOf(animatedProgress, dragFraction) -> {
                        activeColor.copy(0.5f)
                    }
                    barCenterFraction <= animatedProgress -> activeColor
                    else -> inactiveColor
                }
                barCenterFraction <= animatedProgress -> activeColor
                else -> {
                    inactiveColor
                }
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth /2)
            )
        }
    }
}

@Composable
fun PlayedTrackTime(positionMs: Long, color: Color = Color.White) {
    Text(
        text = formatTime(positionMs),

        fontSize = 3.em,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
fun TrackLength(positionMs: Long, durationMs: Long, color: Color = Color.White) {
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    var showRemainingTime by remember { mutableStateOf(true) }

    Text(
        text = if (showRemainingTime) "-${formatTime(remainingMs)}" else formatTime(durationMs),

        fontSize = 3.em,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime}
    )
}
