package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Playlist
import com.example.data.models.Track
import com.example.ui.theme.LocalizedStrings
import com.example.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsMenu(
    track: Track,
    playlists: List<Playlist> = emptyList(),
    strings: LocalizedStrings? = null,
    onPlayNext: () -> Unit,
    onAddToCurrentQueue: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onToggleFavorite: () -> Unit,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showPlaylistsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header: Title + Artist + Favorite Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = {
                    onToggleFavorite()
                }) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Menu Option Items
            OptionMenuItem(
                icon = Icons.Default.Info,
                label = strings?.songInfo ?: "Song Info",
                onClick = {
                    showSongInfoDialog = true
                }
            )

            OptionMenuItem(
                icon = Icons.Default.SkipNext,
                label = strings?.playNext ?: "Play after current song",
                onClick = {
                    onPlayNext()
                    onDismiss()
                }
            )

            OptionMenuItem(
                icon = Icons.Default.QueueMusic,
                label = strings?.addToCurrentQueue ?: "Add to currently playing queue",
                onClick = {
                    onAddToCurrentQueue()
                    onDismiss()
                }
            )

            OptionMenuItem(
                icon = Icons.Default.QueueMusic,
                label = strings?.addToAQueue ?: "Add to a queue",
                onClick = {
                    onAddToCurrentQueue()
                    onDismiss()
                }
            )

            OptionMenuItem(
                icon = Icons.Default.PlaylistAdd,
                label = strings?.addToPlaylists ?: "Add to playlists",
                onClick = {
                    if (playlists.isNotEmpty()) {
                        showPlaylistsDialog = true
                    } else {
                        onDismiss()
                    }
                }
            )

            OptionMenuItem(
                icon = Icons.Default.Share,
                label = strings?.share ?: "Share",
                onClick = {
                    shareTrack(context, track, strings)
                    onDismiss()
                }
            )

            Divider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OptionMenuItem(
                icon = Icons.Default.DeleteForever,
                label = strings?.deletePermanently ?: "Delete permanently",
                textColor = Color.Red,
                iconColor = Color.Red,
                onClick = {
                    showDeleteConfirmDialog = true
                }
            )
        }
    }

    // Playlists selection sub-dialog
    if (showPlaylistsDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistsDialog = false },
            title = {
                Text(
                    text = strings?.selectPlaylistTitle ?: "Select Playlist",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlists.filter { !it.isSmart }) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onAddToPlaylist(pl)
                                    showPlaylistsDialog = false
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistsDialog = false }) {
                    Text(strings?.cancel ?: "Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Song Info Dialog
    if (showSongInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSongInfoDialog = false },
            title = {
                Text(
                    text = strings?.songInfoTitle ?: "Song Info 🎵",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${strings?.titleLabel ?: "Title"}: ${track.title}", color = MaterialTheme.colorScheme.onSurface)
                    Text("${strings?.artistLabel ?: "Artist"}: ${track.artist}", color = MaterialTheme.colorScheme.onSurface)
                    Text("${strings?.albumLabel ?: "Album"}: ${track.album}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (track.downloadedPath.isNotEmpty()) {
                        Text("${strings?.filePathLabel ?: "File Path"}: ${track.downloadedPath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSongInfoDialog = false }) {
                    Text(strings?.ok ?: "OK")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = strings?.deleteTrackTitle ?: "Delete Track 🗑️",
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            },
            text = {
                val confirmMsg = strings?.deleteTrackConfirm?.let {
                    try { it.format(track.title) } catch (e: Exception) { "Are you sure you want to delete ${track.title}?" }
                } ?: "Are you sure you want to delete ${track.title}?"
                Text(
                    text = confirmMsg,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePermanently()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(strings?.deletePermanentlyBtn ?: "Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(strings?.cancel ?: "Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun OptionMenuItem(
    icon: ImageVector,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = textColor
        )
    }
}

private fun shareTrack(context: Context, track: Track, strings: LocalizedStrings?) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            val textToShare = strings?.shareText?.let {
                try { it.format(track.title, track.artist) } catch (e: Exception) { "Listen to ${track.title} by ${track.artist}" }
            } ?: "Listen to ${track.title} by ${track.artist}"
            putExtra(Intent.EXTRA_TEXT, textToShare)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooserTitle = strings?.shareSubject ?: "Share Track"
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
