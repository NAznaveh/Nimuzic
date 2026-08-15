package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.player.PlayerState
import com.example.ui.components.TrackCoverImage
import com.example.ui.components.TrackItem
import com.example.ui.theme.LocalizedStrings
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGreenBright
import java.text.Collator

data class AlbumGroup(
    val name: String,
    val artist: String,
    val coverUrl: String,
    val audioPath: String,
    val tracks: List<Track>
)

private val localeCollator: Collator = Collator.getInstance().apply {
    strength = Collator.PRIMARY
}

/**
 * Strips leading non-alphanumeric characters (like brackets, dashes, underscores)
 * and trims whitespace so that titles like "[ HitSound.ir ]" sort properly by title.
 */
fun cleanTitleForSorting(title: String): String {
    if (title.isBlank()) return ""
    var start = 0
    val len = title.length
    while (start < len && !title[start].isLetterOrDigit()) {
        start++
    }
    val cleaned = if (start < len) title.substring(start).trim() else title.trim()
    return if (cleaned.isEmpty()) title.trim() else cleaned
}

fun compareTitles(t1: String, t2: String): Int {
    val c1 = cleanTitleForSorting(t1)
    val c2 = cleanTitleForSorting(t2)
    val cmp = localeCollator.compare(c1, c2)
    return if (cmp != 0) cmp else t1.compareTo(t2, ignoreCase = true)
}

@Composable
fun LibraryScreen(
    allTracks: List<Track>,
    downloadedTracks: List<Track>,
    offlineTracks: List<Track>,
    playlists: List<Playlist>,
    playerState: PlayerState,
    strings: LocalizedStrings,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddToPlaylist: (Playlist, Track) -> Unit,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToCurrentQueue: ((Track) -> Unit)? = null,
    onDeletePermanently: ((Track) -> Unit)? = null,
    onScanLocalDeviceAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var sortByTitleAsc by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf("ALL") }
    var expandedArtist by remember { mutableStateOf<String?>(null) }
    var expandedAlbum by remember { mutableStateOf<String?>(null) }

    // Alphabet index list: ALL, A..Z, #
    val alphabetList = remember {
        listOf("ALL") + ('A'..'Z').map { it.toString() } + listOf("#")
    }

    // Build artist groups once per library update instead of filtering the full library
    // again for every artist row during composition. This keeps large libraries responsive.
    val tracksByArtist = remember(allTracks) {
        allTracks.groupBy { it.artist.trim().lowercase() }
    }

    // 1. Search Filtered Tracks for All Tracks Tab
    val searchFilteredTracks = remember(allTracks, searchQuery) {
        if (searchQuery.isBlank()) allTracks
        else {
            val q = searchQuery.trim().lowercase()
            allTracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    // 2. Displayed Tracks for All Tracks Tab (Tab 0) - Uses clean title Collator sorting
    val displayedTracks = remember(searchFilteredTracks, sortByTitleAsc) {
        if (sortByTitleAsc) {
            searchFilteredTracks.sortedWith { t1, t2 -> compareTitles(t1.title, t2.title) }
        } else {
            searchFilteredTracks.sortedByDescending { it.dateAdded }
        }
    }

    // 3. Album Groups for Albums Tab (Tab 1)
    val albumGroups = remember(allTracks, searchQuery, selectedLetter, sortByTitleAsc, strings) {
        val rawGroups = allTracks.groupBy { track ->
            val trimmed = track.album.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown", ignoreCase = true)) {
                if (strings.navProfile == "Settings") "Unknown Album" else "آلبوم نامشخص"
            } else {
                trimmed
            }
        }

        val groups = rawGroups.map { (albumName, tracksInAlbum) ->
            val distinctArtists = tracksInAlbum.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct()
            val repArtist = when {
                distinctArtists.isEmpty() -> if (strings.navProfile == "Settings") "Unknown Artist" else "خواننده نامشخص"
                distinctArtists.size == 1 -> distinctArtists.first()
                else -> if (strings.navProfile == "Settings") "Various Artists" else "هنرمندان مختلف"
            }
            val repCover = tracksInAlbum.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl ?: ""
            val repAudio = tracksInAlbum.firstOrNull { it.downloadedPath.isNotBlank() || it.audioUrl.isNotBlank() }?.let { it.downloadedPath.ifEmpty { it.audioUrl } } ?: ""

            AlbumGroup(
                name = albumName,
                artist = repArtist,
                coverUrl = repCover,
                audioPath = repAudio,
                tracks = tracksInAlbum
            )
        }

        val searchFiltered = if (searchQuery.isBlank()) groups else {
            val q = searchQuery.trim().lowercase()
            groups.filter { album ->
                album.name.lowercase().contains(q) || album.artist.lowercase().contains(q)
            }
        }

        val letterFiltered = searchFiltered.filter { album ->
            if (selectedLetter == "ALL") true
            else if (selectedLetter == "#") {
                val cleanName = cleanTitleForSorting(album.name)
                val firstChar = cleanName.firstOrNull()?.uppercaseChar() ?: ' '
                !firstChar.isLetter()
            } else {
                val cleanName = cleanTitleForSorting(album.name)
                cleanName.startsWith(selectedLetter, ignoreCase = true)
            }
        }

        if (sortByTitleAsc) {
            letterFiltered.sortedWith { a1, a2 -> compareTitles(a1.name, a2.name) }
        } else {
            letterFiltered.sortedWith { a1, a2 -> compareTitles(a2.name, a1.name) }
        }
    }

    // 4. Grouped Artists for Artists Tab (Tab 2)
    val artistsGrouped = remember(allTracks, searchQuery, selectedLetter, sortByTitleAsc) {
        val searchFiltered = if (searchQuery.isBlank()) allTracks else {
            val q = searchQuery.trim().lowercase()
            allTracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }

        val distinctArtistNames = searchFiltered.map { it.artist.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { name ->
                if (selectedLetter == "ALL") true
                else if (selectedLetter == "#") {
                    val cleanName = cleanTitleForSorting(name)
                    val firstChar = cleanName.firstOrNull()?.uppercaseChar() ?: ' '
                    !firstChar.isLetter()
                } else {
                    val cleanName = cleanTitleForSorting(name)
                    cleanName.startsWith(selectedLetter, ignoreCase = true)
                }
            }
            .sortedWith { a1, a2 -> if (sortByTitleAsc) compareTitles(a1, a2) else compareTitles(a2, a1) }

        distinctArtistNames.groupBy { artistName ->
            val cleanName = cleanTitleForSorting(artistName)
            val firstChar = cleanName.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar in 'A'..'Z') firstChar.toString() else "#"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Tab Row: All Tracks | Albums | Artists
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SpotifyGreen
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("${strings.libraryAllTracksTab} (${allTracks.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("${strings.libraryAlbumsTab} (${albumGroups.size})") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("${strings.libraryArtistsTab} (${tracksByArtist.keys.count { it.isNotBlank() }})") }
            )
        }

        // Local Device Audio Scanner Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = SpotifyGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.libraryScanDeviceTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.libraryScanDeviceSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onScanLocalDeviceAudio,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("scan_device_audio_btn")
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.libraryScanBtn, color = Color.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // By Search & Alphabet Controls Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = when (selectedTab) {
                            0 -> if (strings.navProfile == "Settings") "By Search: Search title, artist, album..." else "جست‌وجو: نام آهنگ، خواننده، آلبوم..."
                            1 -> if (strings.navProfile == "Settings") "By Search: Search album or artist..." else "جست‌وجو: نام آلبوم یا خواننده..."
                            else -> if (strings.navProfile == "Settings") "By Search: Enter artist or song name..." else "جست‌وجو: نام خواننده یا آهنگ..."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = SpotifyGreen
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sort & Filter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.sortByPrefix} ${if (sortByTitleAsc) strings.sortAlphabet else strings.sortNewest}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { sortByTitleAsc = !sortByTitleAsc }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort", tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.changeSort, style = MaterialTheme.typography.labelSmall, color = SpotifyGreenBright)
                }
            }

            // A-Z Quick Index Selector Bar (For Albums and Artists Tabs)
            if (selectedTab == 1 || selectedTab == 2) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "By Search" Badge Button
                    item {
                        val isSearchActiveBtn = searchQuery.isNotBlank()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSearchActiveBtn) SpotifyGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (isSearchActiveBtn) searchQuery = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (isSearchActiveBtn) Color.Black else SpotifyGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "By Search 🔍",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSearchActiveBtn) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    items(alphabetList) { letter ->
                        val isSelected = selectedLetter == letter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) SpotifyGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedLetter = letter }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Main Content List
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp, start = 12.dp, end = 12.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (selectedTab) {
                1 -> {
                    // TAB 1: ALBUMS TAB
                    if (albumGroups.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (strings.navProfile == "Settings") "No albums found" else "هیچ آلبومی یافت نشد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(albumGroups, key = { it.name }) { album ->
                            val isExpanded = expandedAlbum == album.name

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        expandedAlbum = if (isExpanded) null else album.name
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded) SpotifyGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            TrackCoverImage(
                                                coverUrl = album.coverUrl,
                                                audioPath = album.audioPath,
                                                title = album.name,
                                                artist = album.artist,
                                                modifier = Modifier.size(52.dp),
                                                cornerRadius = 12.dp
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = album.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${album.artist} • ${album.tracks.size} ${strings.playlistTracksCount}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SpotifyGreenBright
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand",
                                            tint = SpotifyGreen
                                        )
                                    }

                                    // Expandable Track List for this Album
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            album.tracks.forEach { track ->
                                                val isPlaying = playerState.currentTrack?.id == track.id
                                                TrackItem(
                                                    track = track,
                                                    isPlaying = isPlaying,
                                                    playlists = playlists,
                                                    strings = strings,
                                                    onTrackClick = { onTrackClick(track, album.tracks) },
                                                    onFavoriteToggle = { onFavoriteToggle(track) },
                                                    onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) },
                                                    onPlayNext = { onPlayNext?.invoke(track) },
                                                    onAddToCurrentQueue = { onAddToCurrentQueue?.invoke(track) },
                                                    onDeletePermanently = { onDeletePermanently?.invoke(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: ARTISTS TAB WITH A-Z SECTIONS
                    if (artistsGrouped.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (strings.navProfile == "Settings") "No artists found" else "هیچ خواننده‌ای یافت نشد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        artistsGrouped.forEach { (letterHeader, artistNames) ->
                            // Letter Header Section Badge (A, B, C...)
                            item(key = "header_$letterHeader") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = letterHeader,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = SpotifyGreenBright
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }
                            }

                            // Artist Cards under this letter
                            items(artistNames, key = { it }) { artistName ->
                                val artistTracks = tracksByArtist[artistName.trim().lowercase()].orEmpty()
                                val isExpanded = expandedArtist == artistName

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            expandedArtist = if (isExpanded) null else artistName
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isExpanded) SpotifyGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(SpotifyGreen),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column {
                                                    Text(
                                                        text = artistName,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${artistTracks.size} ${strings.playlistTracksCount}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = SpotifyGreenBright
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand",
                                                tint = SpotifyGreen
                                            )
                                        }

                                        // Expandable Track List for this artist
                                        AnimatedVisibility(
                                            visible = isExpanded,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                artistTracks.forEach { track ->
                                                    val isPlaying = playerState.currentTrack?.id == track.id
                                                    TrackItem(
                                                        track = track,
                                                        isPlaying = isPlaying,
                                                        playlists = playlists,
                                                        strings = strings,
                                                        onTrackClick = { onTrackClick(track, artistTracks) },
                                                        onFavoriteToggle = { onFavoriteToggle(track) },
                                                        onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) },
                                                        onPlayNext = { onPlayNext?.invoke(track) },
                                                        onAddToCurrentQueue = { onAddToCurrentQueue?.invoke(track) },
                                                        onDeletePermanently = { onDeletePermanently?.invoke(track) }
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
                else -> {
                    // TAB 0: ALL TRACKS TAB
                    items(displayedTracks, key = { it.id }) { track ->
                        val isPlaying = playerState.currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isPlaying = isPlaying,
                            playlists = playlists,
                            strings = strings,
                            onTrackClick = { onTrackClick(track, displayedTracks) },
                            onFavoriteToggle = { onFavoriteToggle(track) },
                            onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) },
                            onPlayNext = { onPlayNext?.invoke(track) },
                            onAddToCurrentQueue = { onAddToCurrentQueue?.invoke(track) },
                            onDeletePermanently = { onDeletePermanently?.invoke(track) }
                        )
                    }
                }
            }
        }
    }
}
