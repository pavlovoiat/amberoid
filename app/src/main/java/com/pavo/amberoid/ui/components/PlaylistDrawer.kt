package com.pavo.amberoid.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.pavo.amberoid.NerdFont
import com.pavo.amberoid.data.model.Song
import com.pavo.amberoid.data.model.SortOrder
import com.pavo.amberoid.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlaylistDrawer(
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    textColor: Color,
    viewModel: PlayerViewModel = viewModel(),
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier
            .width(320.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = secondaryColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, bottom = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fontSize = 5.em,
                    fontWeight = FontWeight.Bold,
                    color = textColor,

                    text = "Playlist"
                )

                Box {
                    IconButton(onClick = { expanded = true }) {
                        Text(
                            text = "\uf0dc",

                            fontSize = 20.sp,
                            fontFamily = NerdFont,
                            color = textColor
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    onSortSelected(order)
                                    viewModel.updateSortOrder(order)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs) { song ->
                    val isSelected = song.id == currentSong?.id
                    PlaylistItem(
                        song = song,
                        isSelected = isSelected,
                        onClick = { onSongClick(song) },
                        primaryColor = primaryColor,
                        textColor = textColor,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistItem(
    song: Song,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryColor: Color,
    textColor: Color,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current

    val artworkBytes by produceState<ByteArray?>(initialValue = null, key1 = song.contentUri) {
        value = withContext(Dispatchers.IO) {
            viewModel.getArtwork(context, song.contentUri)
        }
    }

    val backgroundColor = if (isSelected) {
        primaryColor
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor),
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
                    fontSize = 20.sp,
                    fontFamily = NerdFont,
                    color = Color.Blue,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                fontSize = 3.em,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                fontSize = 2.em,
                color = textColor.copy(0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}