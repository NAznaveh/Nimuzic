package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.SpotifyMiniPlayer
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.ProfileAndSyncScreen
import com.example.ui.screens.SearchAndDownloadScreen
import com.example.ui.theme.AuraTheme
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGreenBright
import com.example.ui.viewmodels.MainViewModel

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextOverflow

data class NavItem(val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val playerState by viewModel.playerState.collectAsStateWithLifecycle()
            val username by viewModel.username.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current

            var selectedNavIndex by remember { mutableIntStateOf(0) }
            var isFullPlayerExpanded by remember { mutableStateOf(false) }
            var isEqualizerDialogOpen by remember { mutableStateOf(false) }

            // Automatic Storage / Audio Permission Request on Install/Launch
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted = permissions.values.any { it }
                if (granted) {
                    viewModel.scanLocalDeviceAudio(userInitiated = false)
                }
            }

            LaunchedEffect(Unit) {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                permissionLauncher.launch(perms)
            }

            // Handle user notice feedback
            LaunchedEffect(uiState.userNoticeMessage) {
                uiState.userNoticeMessage?.let { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearNotice()
                }
            }

            val strings = com.example.ui.theme.LanguageManager.getStrings(uiState.selectedLanguage)

            AuraTheme(themeMode = uiState.activeThemeMode) {
                val navItems = listOf(
                    NavItem(strings.navHome, Icons.Default.Home),
                    NavItem(strings.navSearch, Icons.Default.Search),
                    NavItem(strings.navPlaylists, Icons.Default.QueueMusic),
                    NavItem(strings.navLibrary, Icons.Default.LibraryMusic),
                    NavItem(strings.navProfile, Icons.Default.Person)
                )

                Scaffold(
                    bottomBar = {
                        Column {
                            // Keep the player in the Scaffold bottom area so it never
                            // overlaps the content or the system navigation region.
                            if (playerState.currentTrack != null) {
                                SpotifyMiniPlayer(
                                    playerState = playerState,
                                    onExpandClick = { isFullPlayerExpanded = true },
                                    onPlayPauseClick = { viewModel.audioController.togglePlayPause() },
                                    onNextClick = { viewModel.audioController.nextTrack() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                )
                            }

                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = SpotifyGreen,
                                modifier = Modifier.testTag("main_navigation_bar")
                            ) {
                                navItems.forEachIndexed { index, item ->
                                    NavigationBarItem(
                                        selected = selectedNavIndex == index,
                                        onClick = { selectedNavIndex = index },
                                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                                        label = {
                                            Text(
                                                text = item.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = SpotifyGreenBright,
                                            selectedTextColor = SpotifyGreenBright,
                                            indicatorColor = SpotifyGreen.copy(alpha = 0.2f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_item_$index")
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Main Navigation Views
                        when (selectedNavIndex) {
                            0 -> HomeScreen(
                                allTracks = uiState.allTracks,
                                top10MonthlyTracks = uiState.top10MonthlyTracks,
                                favoriteTracks = uiState.favoriteTracks,
                                playlists = uiState.playlists,
                                playerState = playerState,
                                username = username,
                                strings = strings,
                                selectedLanguage = uiState.selectedLanguage,
                                onToggleLanguage = {
                                    val nextLang = if (uiState.selectedLanguage == com.example.ui.theme.AppLanguage.PERSIAN) com.example.ui.theme.AppLanguage.ENGLISH else com.example.ui.theme.AppLanguage.PERSIAN
                                    viewModel.setLanguage(nextLang)
                                },
                                onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                                onFairShufflePlayAll = { list -> viewModel.playAllWithFairShuffle(list) },
                                onSmartShufflePlayAll = { list -> viewModel.playAllWithSmartShuffle(list) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddToPlaylist = { pl, track -> viewModel.addTrackToPlaylist(pl, track) },
                                onPlayNext = { track -> viewModel.playNextInQueue(track) },
                                onAddToCurrentQueue = { track -> viewModel.addTrackToQueue(track) },
                                onDeletePermanently = { track -> viewModel.deleteTrackPermanently(track) },
                                onOpenEqualizer = { isEqualizerDialogOpen = true },
                                onNavigateToProfile = { selectedNavIndex = 4 },
                                onSelectPlaylist = { pl ->
                                    viewModel.selectPlaylist(pl)
                                    selectedNavIndex = 2
                                }
                            )

                            1 -> SearchAndDownloadScreen(
                                searchQuery = uiState.searchQuery,
                                selectedCategory = uiState.selectedCategory,
                                searchResults = uiState.searchResults,
                                downloads = uiState.downloads,
                                strings = strings,
                                onQueryChange = { q, cat -> viewModel.updateOnlineSearch(q, cat) },
                                onStartDownload = { result -> viewModel.startDownload(result) },
                                onDeleteDownload = { id -> viewModel.deleteDownload(id) },
                                onPlayTrack = { track -> viewModel.playTrack(track, listOf(track)) }
                            )

                            2 -> PlaylistsScreen(
                                playlists = uiState.playlists,
                                selectedPlaylist = uiState.selectedPlaylist,
                                playlistTracks = uiState.playlistTracks,
                                allTracks = uiState.allTracks,
                                strings = strings,
                                onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                                onUpdatePlaylist = { pl, name, desc, coverUrl -> viewModel.updatePlaylist(pl, name, desc, coverUrl) },
                                onDeletePlaylist = { pl -> viewModel.deletePlaylist(pl) },
                                onSelectPlaylist = { pl -> viewModel.selectPlaylist(pl) },
                                onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                                onFairShufflePlayAll = { list -> viewModel.playAllWithFairShuffle(list) },
                                onSmartShufflePlayAll = { list -> viewModel.playAllWithSmartShuffle(list) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onRemoveTrackFromPlaylist = { pl, track -> viewModel.removeTrackFromPlaylist(pl, track) },
                                onAddToPlaylist = { pl, track -> viewModel.addTrackToPlaylist(pl, track) },
                                onPlayNext = { track -> viewModel.playNextInQueue(track) },
                                onAddToCurrentQueue = { track -> viewModel.addTrackToQueue(track) },
                                onDeletePermanently = { track -> viewModel.deleteTrackPermanently(track) }
                            )

                            3 -> LibraryScreen(
                                allTracks = uiState.allTracks,
                                downloadedTracks = uiState.downloadedTracks,
                                offlineTracks = uiState.offlineTracks,
                                playlists = uiState.playlists,
                                playerState = playerState,
                                strings = strings,
                                onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddToPlaylist = { pl, track -> viewModel.addTrackToPlaylist(pl, track) },
                                onPlayNext = { track -> viewModel.playNextInQueue(track) },
                                onAddToCurrentQueue = { track -> viewModel.addTrackToQueue(track) },
                                onDeletePermanently = { track -> viewModel.deleteTrackPermanently(track) },
                                onScanLocalDeviceAudio = { viewModel.scanLocalDeviceAudio(userInitiated = true) }
                            )

                            4 -> ProfileAndSyncScreen(
                                username = username,
                                activeThemeMode = uiState.activeThemeMode,
                                selectedLanguage = uiState.selectedLanguage,
                                strings = strings,
                                playlistCount = uiState.playlists.size,
                                totalStreamSeconds = playerState.totalStreamSeconds,
                                fairShufflePlayedCount = playerState.fairShufflePlayedCount,
                                fairShuffleTotalCount = playerState.fairShuffleTotalCount,
                                onSetUsername = { viewModel.setUsername(it) },
                                onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
                                onOpenEqualizer = { isEqualizerDialogOpen = true }
                            )
                        }

                    }
                }

                // Expandable Full Screen Player
                if (isFullPlayerExpanded) {
                    FullPlayerSheet(
                        playerState = playerState,
                        audioController = viewModel.audioController,
                        selectedLanguage = uiState.selectedLanguage,
                        onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                        onOpenEqualizer = { isEqualizerDialogOpen = true },
                        onFetchOnlineLyrics = { track -> viewModel.fetchOnlineLyricsForTrack(track) },
                        onDismiss = { isFullPlayerExpanded = false }
                    )
                }

                // Equalizer FX Modal Dialog
                if (isEqualizerDialogOpen) {
                    EqualizerDialog(
                        currentSettings = playerState.equalizerSettings,
                        strings = strings,
                        onSaveSettings = { settings -> viewModel.updateEqualizer(settings) },
                        onDismiss = { isEqualizerDialogOpen = false }
                    )
                }
            }
        }
    }
}
