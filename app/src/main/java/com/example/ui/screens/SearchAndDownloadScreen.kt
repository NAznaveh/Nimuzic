package com.example.ui.screens

import android.graphics.Bitmap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.models.DownloadItem
import com.example.data.models.DownloadStatus
import com.example.data.models.Track
import com.example.repository.OnlineSearchResult
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGreenBright
import java.net.URLEncoder

import com.example.ui.theme.LocalizedStrings

@Composable
fun SearchAndDownloadScreen(
    searchQuery: String,
    selectedCategory: String,
    searchResults: List<OnlineSearchResult>,
    downloads: List<DownloadItem>,
    strings: LocalizedStrings? = null,
    onQueryChange: (String, String) -> Unit,
    onStartDownload: (OnlineSearchResult) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onPlayTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val categories = listOf("ALL", "Lofi", "Synthwave", "Persian Electro", "Traditional", "Podcasts", "Ambient")

    // Google Browser States
    var googleSearchInput by remember { mutableStateOf("https://www.google.com") }
    var currentWebUrl by remember { mutableStateOf("https://www.google.com") }
    var webTitle by remember { mutableStateOf("Google") }
    var isWebLoading by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_download_screen")
    ) {
        // Tab Row (0: Google Web Browser, 1: Direct Search, 2: Downloads Manager)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SpotifyGreen
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings?.webBrowserTab ?: "Google Browser", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings?.directSearchTab ?: "Direct Search", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${strings?.downloadsTab ?: "Downloads"} (${downloads.size})", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        when (selectedTab) {
            0 -> {
                // In-App Google Browser & Music Downloader
                Column(modifier = Modifier.fillMaxSize()) {
                    // Google Browser Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = googleSearchInput,
                            onValueChange = { googleSearchInput = it },
                            placeholder = { Text(strings?.googleInputPlaceholder ?: "Search Google or enter web URL...") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = SpotifyGreenBright) },
                            trailingIcon = {
                                if (googleSearchInput.isNotEmpty()) {
                                    IconButton(onClick = { googleSearchInput = "" }) {
                                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpotifyGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("google_web_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val url = if (googleSearchInput.startsWith("http://") || googleSearchInput.startsWith("https://")) {
                                    googleSearchInput
                                } else {
                                    "https://www.google.com/search?q=" + URLEncoder.encode(googleSearchInput, "UTF-8")
                                }
                                currentWebUrl = url
                                webViewInstance?.loadUrl(url)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(strings?.searchBtn ?: "Search", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quick Search Pills for Music Download Sites
                    val quickLinks = listOf(
                        "🌐 Google" to "https://www.google.com",
                        "🎵 Top Songs" to "https://www.google.com/search?q=" + URLEncoder.encode("top songs mp3 download", "UTF-8"),
                        "🔥 New Hits" to "https://www.google.com/search?q=" + URLEncoder.encode("new music hits mp3 download", "UTF-8"),
                        "📻 Radio Javan" to "https://www.radiojavan.com",
                        "🎧 Pop Music" to "https://www.google.com/search?q=" + URLEncoder.encode("pop music download mp3", "UTF-8")
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        items(quickLinks) { (label, url) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        currentWebUrl = url
                                        webViewInstance?.loadUrl(url)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SpotifyGreenBright
                                )
                            }
                        }
                    }

                    // Web Controls (Back, Forward, Refresh, Add Current Page Download)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { webViewInstance?.goBack() }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { webViewInstance?.goForward() }) {
                                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Forward", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = {
                                val url = "https://www.google.com"
                                currentWebUrl = url
                                webViewInstance?.loadUrl(url)
                            }) {
                                Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = SpotifyGreenBright)
                            }
                        }

                        // Download Current Track / Link Action
                        val activeUrl = webViewInstance?.url ?: currentWebUrl
                        val isDirectAudio = activeUrl.substringBefore('?').substringBefore('#').lowercase().let {
                            it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".aac") ||
                            it.endsWith(".wav") || it.endsWith(".flac") || it.endsWith(".ogg") || it.endsWith(".opus")
                        }

                        Button(
                            onClick = {
                                if (isDirectAudio) {
                                    val title = webTitle.ifBlank { "Audio Download" }
                                    onStartDownload(
                                        OnlineSearchResult(
                                            id = System.currentTimeMillis(),
                                            title = title,
                                            artist = "Web Audio Download",
                                            album = "Downloads",
                                            durationMs = 210000L,
                                            audioUrl = activeUrl,
                                            fileSizeMb = 4.5f,
                                            category = "Downloaded Audio"
                                        )
                                    )
                                }
                            },
                            enabled = isDirectAudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = if (isDirectAudio) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isDirectAudio) (strings?.downloadPageBtn ?: "Download Audio") else "No Audio Link",
                                    color = if (isDirectAudio) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    if (isWebLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = SpotifyGreen
                        )
                    }

                    // WebView Container
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    try {
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                    } catch (_: Exception) {}
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.setGeolocationEnabled(false)

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isWebLoading = true
                                            url?.let {
                                                currentWebUrl = it
                                                googleSearchInput = it
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isWebLoading = false
                                            view?.title?.let { webTitle = it }
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val uri = request?.url ?: return false
                                            val scheme = uri.scheme?.lowercase()
                                            if (scheme != "http" && scheme != "https") {
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                            handler?.cancel()
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            title?.let { webTitle = it }
                                        }
                                    }

                                    // Intercept Direct MP3 Downloads
                                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                        onStartDownload(
                                            OnlineSearchResult(
                                                id = System.currentTimeMillis(),
                                                title = fileName.replace(".mp3", "").ifBlank { "Audio Download" },
                                                artist = "Web Download",
                                                album = "Downloads",
                                                durationMs = 210000L,
                                                audioUrl = url,
                                                fileSizeMb = (contentLength / (1024f * 1024f)).coerceAtLeast(3.5f),
                                                category = "Audio Download"
                                            )
                                        )
                                    }

                                    loadUrl(currentWebUrl)
                                    webViewInstance = this
                                }
                            },
                            update = { webView ->
                                webViewInstance = webView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            1 -> {
                // Direct Search Tab
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { onQueryChange(it, selectedCategory) },
                        placeholder = { Text(strings?.searchPlaceholder ?: "Search song, artist, podcast...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SpotifyGreen) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("", selectedCategory) }) {
                                    Icon(imageVector = Icons.Default.Cancel, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("search_audio_input")
                    )

                    // Genre Category Filter Pills
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) SpotifyGreen else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onQueryChange(searchQuery, cat) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (cat == "ALL") (strings?.allTracksTitle ?: "All Categories") else cat,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Search Results List
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(searchResults) { result ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${result.artist} • ${result.fileSizeMb} MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    Button(
                                        onClick = { onStartDownload(result) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("download_btn_${result.id}")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings?.downloadPageBtn ?: "Download", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Download Manager Tab
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (downloads.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = strings?.noDownloads ?: "No downloads found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = strings?.noDownloadsSub ?: "Download music from Google browser or online search",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(downloads, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${item.artist} • ${item.status}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        if (item.status == DownloadStatus.COMPLETED) {
                                            IconButton(onClick = {
                                                onPlayTrack(
                                                    Track(
                                                        title = item.title,
                                                        artist = item.artist,
                                                        audioUrl = item.savedFilePath,
                                                        isDownloaded = true,
                                                        downloadedPath = item.savedFilePath,
                                                        isOffline = true
                                                    )
                                                )
                                            }) {
                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = SpotifyGreenBright)
                                            }
                                        }

                                        IconButton(onClick = { onDeleteDownload(item.id) }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                                    LinearProgressIndicator(
                                        progress = { item.progress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = SpotifyGreen,
                                        trackColor = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Progress: ${item.progress}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SpotifyGreenBright
                                        )
                                        Text(
                                            text = "Speed: ${item.speedKbps.toInt()} KB/s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan
                                        )
                                    }
                                } else if (item.status == DownloadStatus.COMPLETED) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SpotifyGreenBright, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings?.downloadCompletedText ?: "Download completed - Ready for offline play", style = MaterialTheme.typography.labelSmall, color = SpotifyGreenBright)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
