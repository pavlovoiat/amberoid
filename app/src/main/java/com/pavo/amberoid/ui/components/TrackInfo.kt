package com.pavo.amberoid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.pavo.amberoid.NerdFont

@Composable
fun TrackImage(artworkBytes: ByteArray?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (artworkBytes != null) {
            AsyncImage(
                model = artworkBytes,
                contentDescription = "Track Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "\uDB80\uDF84",

                fontSize = 40.em,
                fontFamily = NerdFont,
                color = Color.Blue,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TrackTitle(
    title: String,
    color: Color
) {
    Text(
        text = title,

        fontSize = 7.em,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .width(300.dp)
            .basicMarquee()
    )
}

@Composable
fun ArtistName(
    artist: String,
    color: Color
) {
    Text(
        text = artist,

        fontSize = 5.em,
        fontWeight = FontWeight.Medium,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .width(300.dp)
            .basicMarquee()
    )
}

//@Composable
//fun TrackAlbum() {
//    Text(
//        text = "Track Album",
//
//        fontSize = 4.em,
//        fontWeight = FontWeight.Normal,
//        color = Color.White,
//        textAlign = TextAlign.Center,
//        maxLines = 1,
//        modifier = Modifier
//            .width(300.dp)
//            .basicMarquee()
//    )
//}