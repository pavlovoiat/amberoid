package com.pavo.amberoid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pavo.amberoid.NerdFont

@Composable
fun VolumeDown(
    color: Color,
    onClick: () -> Unit,
    iconColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uDB81\uDD80",

            color = iconColor,
            fontFamily = NerdFont
        )
    }
}

@Composable
fun VolumeUp(
    color: Color,
    onClick: () -> Unit,
    iconColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uDB81\uDD7E",

            color = iconColor,
            fontFamily = NerdFont
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeBar(
    color: Color,
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    Slider(
        value = volume,
        onValueChange = onVolumeChange,
        valueRange = 0f..1f,
        thumb = { sliderState -> Box(Modifier.size(0.dp)) },
        track = {sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(16.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 2.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = color,
                    inactiveTrackColor = color.copy(alpha = 0.3f)
                )
            )
        },
        modifier = Modifier.width(200.dp)
    )
}