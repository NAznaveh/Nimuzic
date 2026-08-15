package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import com.example.ui.components.SmartShuffleIcon
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Playlist
import com.example.data.models.Track
import com.example.ui.components.AddPlaylistDialog
import com.example.ui.components.EditPlaylistDialog
import com.example.ui.components.PlaylistCard
import com.example.ui.components.TrackItem
import com.example.ui.theme.FairShuffleBadgeColor
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGreenBright

import com.example.ui.theme.LocalizedStrings

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    selectedPlaylist: Playlist?,
    playlistTracks: List<Track>,
    allTracks: List<Track>,
    strings: LocalizedStrings,
    onCreatePlaylist: (String, String) -> Unit,
    onUpdatePlaylist: (Playlist, String, String, String) -> Unit = { _, _, _, _ -> },
    onDeletePlaylist: (Playlist) -> Unit,
    onSelectPlaylist: (Playlist?) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFairShufflePlayAll: (List<Track>) -> Unit,
    onSmartShufflePlayAll: (List<Track>) -> Unit = {},
    onFavoriteToggle: (Track) -> Unit,
    onRemoveTrackFromPlaylist: (Playlist, Track) -> Unit = { _, _ -> },
    onAddToPlaylist: (Playlist, Track) -> Unit = { _, _ -> },
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToCurrentQueue: ((Track) -> Unit)? = null,
    onDeletePermanently: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    if (selectedPlaylist != null) {
        // Playlist Detail View
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("playlist_detail_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSelectPlaylist(null) }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedPlaylist.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${playlistTracks.size} ${strings.playlistTracksCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editingPlaylist = selectedPlaylist }) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Playlist", tint = SpotifyGreenBright)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { if (playlistTracks.isNotEmpty()) onTrackClick(playlistTracks.first(), playlistTracks) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.playlistNormalPlay, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { onFairShufflePlayAll(playlistTracks) },
                    colors = ButtonDefaults.buttonColors(containerColor = FairShuffleBadgeColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.playlistFairShuffle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = { onSmartShufflePlayAll(playlistTracks) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmartShuffleIcon(modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (strings.navProfile == "Settings") "Smart" else "هوشمند",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(playlistTracks, key = { index, track -> "${track.id}_$index" }) { idx, track ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag Reorder Handle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(24.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                TrackItem(
                                    track = track,
                                    playlists = playlists,
                                    strings = strings,
                                    onTrackClick = { onTrackClick(track, playlistTracks) },
                                    onFavoriteToggle = { onFavoriteToggle(track) },
                                    onAddToPlaylist = { playlist -> onAddToPlaylist(playlist, track) },
                                    onPlayNext = { onPlayNext?.invoke(track) },
                                    onAddToCurrentQueue = { onAddToCurrentQueue?.invoke(track) },
                                    onDeletePermanently = { onDeletePermanently?.invoke(track) }
                                )
                            }
                            IconButton(onClick = { onRemoveTrackFromPlaylist(selectedPlaylist, track) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Track From Playlist",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Playlists Main Grid/List
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = SpotifyGreen,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .padding(bottom = 16.dp, end = 8.dp)
                        .testTag("add_playlist_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Playlist")
                }
            },
            modifier = modifier.fillMaxSize()
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("playlists_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = strings.playlistsLibraryTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.playlistsLibrarySub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(playlists) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onSelectPlaylist(playlist) },
                        onEdit = { editingPlaylist = playlist },
                        onDelete = { onDeletePlaylist(playlist) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            strings = strings,
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingPlaylist != null) {
        EditPlaylistDialog(
            playlist = editingPlaylist!!,
            strings = strings,
            onSavePlaylist = { pl, newName, newDesc, newCoverUrl ->
                onUpdatePlaylist(pl, newName, newDesc, newCoverUrl)
            },
            onDismiss = { editingPlaylist = null }
        )
    }
}
