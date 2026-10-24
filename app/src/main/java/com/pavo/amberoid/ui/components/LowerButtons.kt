package com.pavo.amberoid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pavo.amberoid.NerdFont

@Composable
fun PlaylistButton(
    color: Color,
    onClick: () -> Unit,
    iconColor: Color = Color.White
) {
    IconButton(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(50)
            ),
        onClick = onClick
    ) {
        Text(
            text = "\uDB80\uDF5C",

            color = iconColor,
            fontFamily = NerdFont
        )
    }
}

@Composable
fun ShufflePlaylistButton(
    color: Color,
    onShuffle: () -> Unit,
    isShuffleEnabled: Boolean,
    iconColor: Color = Color.White
) {
    IconButton(
        modifier = Modifier
            .background(
                color = if (!isShuffleEnabled) color else color.copy(0.3f),
                shape = RoundedCornerShape(50)
            ),
        onClick = onShuffle
    ) {
        Text(
            text = "\uDB81\uDC9F",

            color = iconColor,
            fontFamily = NerdFont,
        )
    }
}

@Composable
fun RepeatPlaylistButton(
    color: Color,
    onRepeat: () -> Unit,
    repeatMode: Int,
    iconColor: Color = Color.White
) {
    IconButton(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(50)
                    ),
        onClick = onRepeat
    ) {
        Text(
            text = when (repeatMode) {
                2 -> {
                    "\uDB81\uDC56"
                }
                1 -> {
                    "\uDB81\uDC58"
                }
                else -> {
                    "\uF061"
                }
            },

            color = iconColor,
            fontFamily = NerdFont,
        )
    }
}

@Composable
fun FunctionsMenuButton(color: Color, onClick: () -> Unit, iconColor: Color = Color.White) {
    IconButton(
        modifier = Modifier
            .size(48.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(50)
            ),
        onClick = onClick
    ) {
        Text(
            text = "\uDB80\uDDD9",

            color = iconColor,
            fontFamily = NerdFont
        )
    }
}
