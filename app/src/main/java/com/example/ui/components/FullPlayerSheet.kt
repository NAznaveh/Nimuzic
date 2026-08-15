package com.example.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.Track
import com.example.player.AudioPlayerController
import com.example.player.PlayerState
import com.example.player.RepeatMode
import com.example.player.ShuffleMode
import com.example.ui.theme.FairShuffleBadgeColor
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.LanguageManager
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGreenBright

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playerState: PlayerState,
    audioController: AudioPlayerController,
    selectedLanguage: AppLanguage = AppLanguage.PERSIAN,
    onFavoriteToggle: (Track) -> Unit,
    onOpenEqualizer: () -> Unit,
    onFetchOnlineLyrics: (Track) -> Unit = {},
    onDismiss: () -> Unit
) {
    val track = playerState.currentTrack ?: return
    val strings = remember(selectedLanguage) { LanguageManager.getStrings(selectedLanguage) }
    val isEn = selectedLanguage == AppLanguage.ENGLISH
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("full_player_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Dismiss button, Header title, Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = strings.playingFrom,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = track.album.ifEmpty { "Nimusic Playlist" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = onOpenEqualizer) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = strings.equalizerButtonLabel,
                                tint = SpotifyGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compact Artwork Card
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(20.dp, shape = RoundedCornerShape(20.dp), ambientColor = SpotifyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        TrackCoverImage(
                            coverUrl = track.coverUrl,
                            coverDrawableResName = track.coverDrawableResName,
                            audioPath = track.downloadedPath.ifEmpty { track.audioUrl },
                            title = track.title,
                            artist = track.artist,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 36.dp,
                            cornerRadius = 24.dp,
                            showDetails = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Track Title, Artist, & Favorite Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onFavoriteToggle(track) },
                            modifier = Modifier.testTag("full_player_favorite_btn")
                        ) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Shuffle Indicator Badge (Fair Shuffle or Smart Shuffle)
                    if (playerState.shuffleMode == ShuffleMode.FAIR_NON_REPEAT) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FairShuffleBadgeColor.copy(alpha = 0.25f))
                                .border(1.dp, FairShuffleBadgeColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${strings.fairShuffleActiveBadge} (${playerState.fairShufflePlayedCount}/${playerState.fairShuffleTotalCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    } else if (playerState.shuffleMode == ShuffleMode.SMART_SHUFFLE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpotifyGreen.copy(alpha = 0.15f))
                                .border(1.dp, SpotifyGreen, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmartShuffleIcon(
                                    modifier = Modifier.size(18.dp),
                                    tint = SpotifyGreenBright
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (strings.navProfile == "Settings") "Smart Shuffle Active (Personalized AI Queue)" else "حالت شافل هوشمند فعال است (صف شخصی‌سازی‌شده)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrubbable Progress Bar & Time text
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = playerState.currentPositionMs.toFloat(),
                            onValueChange = { newPos ->
                                audioController.seekTo(newPos.toLong())
                            },
                            valueRange = 0f..(playerState.durationMs.toFloat().coerceAtLeast(1000f)),
                            colors = SliderDefaults.colors(
                                thumbColor = SpotifyGreen,
                                activeTrackColor = SpotifyGreen,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTimeMs(playerState.currentPositionMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTimeMs(playerState.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Main Transport Controls Row (Shuffle, Prev, Big Play/Pause, Next, Repeat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(
                            onClick = { audioController.toggleShuffleMode() },
                            modifier = Modifier.testTag("shuffle_mode_btn")
                        ) {
                            if (playerState.shuffleMode == ShuffleMode.SMART_SHUFFLE) {
                                SmartShuffleIcon(
                                    modifier = Modifier.size(26.dp),
                                    tint = SpotifyGreenBright
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle Mode",
                                    tint = when (playerState.shuffleMode) {
                                        ShuffleMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ShuffleMode.FAIR_NON_REPEAT -> FairShuffleBadgeColor
                                        ShuffleMode.STANDARD_RANDOM -> SpotifyGreen
                                        else -> SpotifyGreen
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Previous Track
                        IconButton(
                            onClick = { audioController.previousTrack() },
                            modifier = Modifier.testTag("prev_track_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Big Play / Pause Circle
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                                .clickable { audioController.togglePlayPause() }
                                .testTag("big_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Next Track
                        IconButton(
                            onClick = { audioController.nextTrack() },
                            modifier = Modifier.testTag("next_track_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Repeat Button
                        IconButton(
                            onClick = { audioController.toggleRepeatMode() },
                            modifier = Modifier.testTag("repeat_mode_btn")
                        ) {
                            Icon(
                                imageVector = if (playerState.repeatMode == RepeatMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Repeat Mode",
                                tint = if (playerState.repeatMode != RepeatMode.OFF) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed Bar Row - Positioned directly UNDER the Play Button Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = SpotifyGreenBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.speedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSelected = playerState.playbackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SpotifyGreen else Color.Transparent)
                                    .clickable { audioController.setPlaybackSpeed(speed) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${speed}x",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Action Icons Row (Queue, Lyrics, Equalizer) - Directly below Speed bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Queue Sheet Button
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Queue",
                                tint = NeonCyan
                            )
                        }

                        // Lyrics Sheet Button
                        IconButton(onClick = { showLyricsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = "Lyrics",
                                tint = SpotifyGreenBright
                            )
                        }

                        // Sleep Timer Button (Bottom-Right area of player screen)
                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.testTag("sleep_timer_btn")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (playerState.sleepTimerStatus.isActive) {
                                        Badge(containerColor = SpotifyGreen)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = strings.sleepTimerTitle,
                                    tint = if (playerState.sleepTimerStatus.isActive) SpotifyGreenBright else FairShuffleBadgeColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Sleep Timer Dialog Modal
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            status = playerState.sleepTimerStatus,
            selectedLanguage = selectedLanguage,
            onSetTimer = { mins -> audioController.setSleepTimer(mins) },
            onCancelTimer = { audioController.cancelSleepTimer() },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    // Queue Sheet Modal
    if (showQueueSheet) {
        val upNextTracks = remember(
            playerState.shuffleMode,
            playerState.smartShuffleUpcoming,
            playerState.fairShuffleRemainingQueue,
            playerState.standardShuffleRemainingQueue,
            playerState.currentQueueIndex,
            playerState.forcedNextTrackId,
            playerState.queue
        ) {
            playerState.upNextQueue
        }
        val modeLabel = when (playerState.shuffleMode) {
            com.example.player.ShuffleMode.FAIR_NON_REPEAT -> if (isEn) "True Fair Shuffle Queue (${upNextTracks.size} remaining)" else "صف شافل عادلانه (${upNextTracks.size} آهنگ باقیمانده)"
            com.example.player.ShuffleMode.SMART_SHUFFLE -> if (isEn) "Smart AI Recommendation Window (${upNextTracks.size} tracks)" else "پنجره هوشمند پیش‌بینی (${upNextTracks.size} آهنگ)"
            com.example.player.ShuffleMode.STANDARD_RANDOM -> if (isEn) "Random Queue (${upNextTracks.size} tracks)" else "صف تصادفی (${upNextTracks.size} آهنگ)"
            com.example.player.ShuffleMode.OFF -> if (isEn) "Up Next (${upNextTracks.size} tracks)" else "در ادامه (${upNextTracks.size} آهنگ)"
        }

        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.queueTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyGreenBright
                        )
                    }
                    if (upNextTracks.isNotEmpty()) {
                        IconButton(onClick = { audioController.clearQueue() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Queue",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Currently Playing Track Card
                playerState.currentTrack?.let { currTrack ->
                    Text(
                        text = if (isEn) "NOW PLAYING" else "در حال پخش",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SpotifyGreenBright,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = SpotifyGreen.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackCoverImage(
                                coverUrl = currTrack.coverUrl,
                                audioPath = currTrack.downloadedPath,
                                title = currTrack.title,
                                artist = currTrack.artist,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currTrack.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SpotifyGreenBright,
                                    maxLines = 1
                                )
                                Text(
                                    text = currTrack.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (upNextTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEn) "No upcoming tracks in queue" else "هیچ آهنگی در ادامه صف وجود ندارد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = if (isEn) "UP NEXT" else "در ادامه",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        itemsIndexed(
                            items = upNextTracks,
                            key = { idx, qTrack -> "${qTrack.id}_$idx" }
                        ) { idx, qTrack ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { audioController.playTrack(qTrack, isManualSelect = true) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackCoverImage(
                                        coverUrl = qTrack.coverUrl,
                                        audioPath = qTrack.downloadedPath,
                                        title = qTrack.title,
                                        artist = qTrack.artist,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = qTrack.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = qTrack.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            onClick = { audioController.moveUpNextItem(idx, -1) },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move up",
                                                tint = if (idx > 0) SpotifyGreenBright else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { audioController.moveUpNextItem(idx, 1) },
                                            enabled = idx < upNextTracks.lastIndex,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move down",
                                                tint = if (idx < upNextTracks.lastIndex) SpotifyGreenBright else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { audioController.removeFromQueue(idx) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Synchronized Lyrics View
    if (showLyricsSheet) {
        ModalBottomSheet(onDismissRequest = { showLyricsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.lyricsTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = SpotifyGreenBright
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpotifyGreen.copy(alpha = 0.2f))
                            .border(1.dp, SpotifyGreen, RoundedCornerShape(12.dp))
                            .clickable { onFetchOnlineLyrics(track) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = strings.fetchOnlineLyricsLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SpotifyGreenBright
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val parsedLyrics = remember(track.id, track.lyrics) { parseSyncedLyrics(track.lyrics) }
                if (parsedLyrics.isEmpty()) {
                    Text(
                        text = if (track.lyrics.isBlank()) strings.noLyricsText else track.lyrics,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Start
                    )
                } else {
                    val lyricsListState = rememberLazyListState()
                    val activeIndex = parsedLyrics.indexOfLast { it.timeMs <= playerState.currentPositionMs }
                        .coerceAtLeast(0)
                    LaunchedEffect(activeIndex) {
                        if (activeIndex in parsedLyrics.indices) {
                            lyricsListState.animateScrollToItem(activeIndex)
                        }
                    }
                    LazyColumn(
                        state = lyricsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        itemsIndexed(parsedLyrics, key = { _, line -> "${line.timeMs}_${line.text}" }) { index, line ->
                            val isSelected = index == activeIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (playerState.currentTrack != null && line.timeMs >= 0L) {
                                            val maxDur = playerState.durationMs.coerceAtLeast(0L)
                                            val targetSeek = if (maxDur > 0L) line.timeMs.coerceIn(0L, maxDur) else line.timeMs
                                            audioController.seekTo(targetSeek)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = if (isSelected) 20.sp else 18.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) SpotifyGreenBright else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SyncedLyricLine(val timeMs: Long, val text: String)

private fun parseSyncedLyrics(lyrics: String): List<SyncedLyricLine> {
    if (lyrics.isBlank()) return emptyList()
    val tagPattern = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    val result = mutableListOf<SyncedLyricLine>()

    for (rawLine in lyrics.lineSequence()) {
        val trimmed = rawLine.trim()
        if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
            trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")) {
            continue
        }
        val tagMatches = tagPattern.findAll(trimmed).toList()
        if (tagMatches.isEmpty()) continue

        val text = trimmed.substring(tagMatches.last().range.last + 1).trim()
        if (text.isBlank()) continue

        for (match in tagMatches) {
            val minutes = match.groupValues[1].toLongOrNull() ?: continue
            val seconds = match.groupValues[2].toLongOrNull() ?: continue
            val fraction = match.groupValues[3]
            val fractionMs = when (fraction.length) {
                1 -> (fraction.toLongOrNull() ?: 0L) * 100L
                2 -> (fraction.toLongOrNull() ?: 0L) * 10L
                3 -> fraction.toLongOrNull() ?: 0L
                else -> 0L
            }
            val timeMs = minutes * 60_000L + seconds * 1_000L + fractionMs
            result.add(SyncedLyricLine(timeMs, text))
        }
    }
    return result.sortedBy { it.timeMs }
}

fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
