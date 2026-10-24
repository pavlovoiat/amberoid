package com.pavo.amberoid.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.pavo.amberoid.player.viewmodel.PlayerViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.pavo.amberoid.ui.components.ArtistName
import com.pavo.amberoid.ui.components.PlayPause
import com.pavo.amberoid.ui.components.PlayedTrackTime
import com.pavo.amberoid.ui.components.PlaylistButton
import com.pavo.amberoid.ui.components.PlaylistDrawer
import com.pavo.amberoid.ui.components.RepeatPlaylistButton
import com.pavo.amberoid.ui.components.FunctionsMenuButton
import com.pavo.amberoid.ui.components.FunctionsBottomSheet
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pavo.amberoid.data.model.SortOrder
import com.pavo.amberoid.ui.components.ShufflePlaylistButton
import com.pavo.amberoid.ui.components.SkipNext
import com.pavo.amberoid.ui.components.SkipPrevious
import com.pavo.amberoid.ui.components.TrackImage
import com.pavo.amberoid.ui.components.TrackLength
import com.pavo.amberoid.ui.components.TrackTitle
import com.pavo.amberoid.ui.components.VolumeBar
import com.pavo.amberoid.ui.components.VolumeDown
import com.pavo.amberoid.ui.components.VolumeUp
import com.pavo.amberoid.ui.components.WaveformSeekBar
import kotlinx.coroutines.launch

fun getContrastColor(color: Color): Color {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

@Composable
fun AmberoidUI(
    windowSizeClass: WindowSizeClass,
    viewModel: PlayerViewModel = viewModel()
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val artworkBytes by viewModel.artworkBytes.collectAsStateWithLifecycle()
    val colorScheme by viewModel.colorScheme.collectAsStateWithLifecycle()
    val topColor = colorScheme.backgroundTop
    val bottomColor = colorScheme.backgroundBottom
    val primaryButtonColor = colorScheme.primary
    val secondaryButtonColor = colorScheme.secondary
    val contentColor = if (colorScheme.isDark) Color.White else Color.Black
    
    val primaryIconColor = getContrastColor(primaryButtonColor)
    val secondaryIconColor = getContrastColor(secondaryButtonColor)
    
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    var currentSort by remember { mutableStateOf(viewModel.getSavedSortOrder()) }

    val animatedTopColor by animateColorAsState(
        targetValue = topColor,
        animationSpec = tween(800),
        label = "TopColorAnimation"
    )
    val animatedBottomColor by animateColorAsState(
        targetValue = bottomColor,
        animationSpec = tween(800),
        label = "BottomColorAnimation"
    )

    val progressFraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showFunctionsSheet by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PlaylistDrawer(
                primaryColor = colorScheme.accent.copy(0.5f),
                secondaryColor = colorScheme.surface.copy(0.9f),
                textColor = contentColor,
                songs = songs,
                currentSong = currentSong,
                onSongClick = { song ->
                    viewModel.selectSong(song)
                    viewModel.play()
                    scope.launch { drawerState.close() }
                },
                currentSort = currentSort,
                onSortSelected = {newSort ->
                    currentSort = newSort
                    viewModel.updateSortOrder(newSort)
                }
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (artworkBytes != null) {
                AsyncImage(
                    model = artworkBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(80.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                animatedTopColor.copy(alpha = 0.4f),
                                animatedBottomColor.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 25.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TrackImage(
                        artworkBytes = artworkBytes,
                        modifier = Modifier.size(300.dp)
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    TrackTitle(
                        title = currentSong?.title ?: "No Track",
                        color = contentColor
                    )

                    ArtistName(
                        artist = currentSong?.artist ?: "Unknown Artist",
                        color = contentColor.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(50.dp))

                    WaveformSeekBar(
                        progressFraction = progressFraction,
                        songId = currentSong?.id ?: 0L,
                        onSeek = { fraction ->
                            val targetMs = (fraction * duration).toLong()
                            viewModel.seekTo(targetMs)
                        },
                        modifier = Modifier.padding(horizontal = 30.dp),
                        activeColor = contentColor,
                        inactiveColor = contentColor.copy(alpha = 0.3f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.size(300.dp, 17.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlayedTrackTime(positionMs = currentPosition, color = contentColor)
                        TrackLength(
                            positionMs = currentPosition,
                            durationMs = duration,
                            color = contentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.size(300.dp, 25.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VolumeDown(
                            color = primaryButtonColor,
                            onClick = { viewModel.decreaseVolume() },
                            iconColor = primaryIconColor
                        )

                        VolumeBar(
                            color = secondaryButtonColor,
                            onVolumeChange = { newVolume -> viewModel.setVolume(newVolume) },
                            volume = volume
                        )

                        VolumeUp(
                            color = primaryButtonColor,
                            onClick = { viewModel.increaseVolume() },
                            iconColor = primaryIconColor
                        )
                    }

                    Row(
                        modifier = Modifier.size(250.dp, 100.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkipPrevious(
                            onPreviousClick = { viewModel.playPrevious() },
                            color = secondaryButtonColor,
                            iconColor = secondaryIconColor
                        )

                        PlayPause(
                            isPlaying = isPlaying,
                            onPlayToggle = { viewModel.togglePlayPause() },
                            color = primaryButtonColor,
                            iconColor = primaryIconColor
                        )

                        SkipNext(
                            onNextClick = { viewModel.playNext() },
                            color = secondaryButtonColor,
                            iconColor = secondaryIconColor
                        )
                    }

                    Row(
                        modifier = Modifier.size(300.dp, 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistButton(
                            color = secondaryButtonColor,
                            onClick = { scope.launch { drawerState.open() } },
                            iconColor = secondaryIconColor
                        )

                        ShufflePlaylistButton(
                            color = secondaryButtonColor,
                            onShuffle = { viewModel.toggleShuffle() },
                            isShuffleEnabled = isShuffleEnabled,
                            iconColor = secondaryIconColor
                        )

                        Spacer(modifier = Modifier.width(100.dp))

                        RepeatPlaylistButton(
                            color = secondaryButtonColor,
                            onRepeat = { viewModel.toggleRepeatMode() },
                            repeatMode = repeatMode,
                            iconColor = secondaryIconColor
                        )

                        FunctionsMenuButton(
                            color = secondaryButtonColor,
                            onClick = { showFunctionsSheet = true },
                            iconColor = secondaryIconColor
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(0.8f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        TrackImage(
                            artworkBytes = artworkBytes,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(64.dp))

                    Column(
                        modifier = Modifier.weight(1.2f).scale(0.9f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TrackTitle(
                                title = currentSong?.title ?: "No Track",
                                color = contentColor
                            )
                            ArtistName(
                                artist = currentSong?.artist ?: "Unknown Artist",
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            WaveformSeekBar(
                                progressFraction = progressFraction,
                                songId = currentSong?.id ?: 0L,
                                onSeek = { fraction ->
                                    val targetMs = (fraction * duration).toLong()
                                    viewModel.seekTo(targetMs)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                activeColor = contentColor,
                                inactiveColor = contentColor.copy(alpha = 0.2f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PlayedTrackTime(positionMs = currentPosition, color = contentColor)
                                TrackLength(
                                    positionMs = currentPosition,
                                    durationMs = duration,
                                    color = contentColor
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkipPrevious(
                                onPreviousClick = { viewModel.playPrevious() },
                                color = secondaryButtonColor,
                                iconColor = secondaryIconColor
                            )

                            PlayPause(
                                isPlaying = isPlaying,
                                onPlayToggle = { viewModel.togglePlayPause() },
                                color = primaryButtonColor,
                                iconColor = primaryIconColor
                            )

                            SkipNext(
                                onNextClick = { viewModel.playNext() },
                                color = secondaryButtonColor,
                                iconColor = secondaryIconColor
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VolumeDown(
                                color = primaryButtonColor,
                                onClick = { viewModel.decreaseVolume() },
                                iconColor = primaryIconColor
                            )

                            VolumeBar(
                                color = secondaryButtonColor,
                                onVolumeChange = { newVolume -> viewModel.setVolume(newVolume) },
                                volume = volume
                            )

                            VolumeUp(
                                color = primaryButtonColor,
                                onClick = { viewModel.increaseVolume() },
                                iconColor = primaryIconColor
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistButton(
                                color = secondaryButtonColor,
                                onClick = { scope.launch { drawerState.open() } },
                                iconColor = secondaryIconColor
                            )

                            ShufflePlaylistButton(
                                color = secondaryButtonColor,
                                onShuffle = { viewModel.toggleShuffle() },
                                isShuffleEnabled = isShuffleEnabled,
                                iconColor = secondaryIconColor
                            )

                            RepeatPlaylistButton(
                                color = secondaryButtonColor,
                                onRepeat = { viewModel.toggleRepeatMode() },
                                repeatMode = repeatMode,
                                iconColor = secondaryIconColor
                            )

                            FunctionsMenuButton(
                                color = secondaryButtonColor,
                                onClick = { showFunctionsSheet = true },
                                iconColor = secondaryIconColor
                            )
                        }
                    }
                }
            }
            if (showFunctionsSheet) {
                FunctionsBottomSheet(
                    onDismissRequest = { showFunctionsSheet = false },
                    primaryColor = colorScheme.primary,
                    secondaryColor = colorScheme.surface,
                    textColor = contentColor,
                    viewModel = viewModel
                )
            }
        }
    }
}
