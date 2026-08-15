package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.models.DownloadItem
import com.example.data.models.GoogleUser
import com.example.data.models.Playlist
import com.example.data.models.Track
import com.example.player.AudioPlayerController
import com.example.player.EqualizerSettings
import com.example.player.PlayerState
import com.example.player.ShuffleMode
import com.example.repository.DownloadRepository
import com.example.repository.GoogleAccountRepository
import com.example.repository.MusicRepository
import com.example.repository.OnlineSearchResult
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

import com.example.ui.theme.AppLanguage

data class MainUiState(
    val allTracks: List<Track> = emptyList(),
    val top10MonthlyTracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val offlineTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val googleUser: GoogleUser? = null,
    val searchQuery: String = "",
    val selectedCategory: String = "ALL",
    val searchResults: List<OnlineSearchResult> = emptyList(),
    val activeThemeMode: AppThemeMode = AppThemeMode.SPOTIFY_DARK,
    val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
    val userNoticeMessage: String? = null,
    val selectedPlaylist: Playlist? = null,
    val playlistTracks: List<Track> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("nimusic_prefs", Context.MODE_PRIVATE)

    private val _username = MutableStateFlow(prefs.getString("username", "")?.trim().orEmpty())
    val username: StateFlow<String> = _username.asStateFlow()

    private val db = AppDatabase.getInstance(application)
    private val musicRepository = MusicRepository(db.musicDao())
    private val googleRepository = GoogleAccountRepository(db.musicDao())
    private val downloadRepository = DownloadRepository(application, db.musicDao())

    val audioController = AudioPlayerController.getInstance(application)
    val playerState: StateFlow<PlayerState> = audioController.playerState

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow("ALL")
    private val _searchResults = MutableStateFlow<List<OnlineSearchResult>>(emptyList())
    private val _activeThemeMode = MutableStateFlow(
        runCatching {
            AppThemeMode.valueOf(prefs.getString("selected_theme", AppThemeMode.SPOTIFY_DARK.name) ?: AppThemeMode.SPOTIFY_DARK.name)
        }.getOrDefault(AppThemeMode.SPOTIFY_DARK)
    )
    private val _selectedLanguage = MutableStateFlow(
        runCatching {
            AppLanguage.valueOf(prefs.getString("selected_language", AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name)
        }.getOrDefault(AppLanguage.ENGLISH)
    )
    private val _userNotice = MutableStateFlow<String?>(null)
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())

    val uiState: StateFlow<MainUiState> = combine(
        musicRepository.allTracks,
        musicRepository.top10MonthlyTracks,
        musicRepository.favoriteTracks,
        musicRepository.downloadedTracks,
        musicRepository.offlineTracks,
        musicRepository.allPlaylists,
        downloadRepository.allDownloads,
        googleRepository.googleUser,
        _searchQuery,
        _selectedCategory,
        _searchResults,
        _activeThemeMode,
        _selectedLanguage,
        _userNotice,
        _selectedPlaylist,
        _playlistTracks
    ) { args ->
        val tracks = args[0] as List<Track>
        val top10 = args[1] as List<Track>
        val favorites = args[2] as List<Track>
        val downloaded = args[3] as List<Track>
        val offline = args[4] as List<Track>
        val playlists = args[5] as List<Playlist>
        val downloads = args[6] as List<DownloadItem>
        val gUser = args[7] as GoogleUser?
        val query = args[8] as String
        val cat = args[9] as String
        val searchRes = args[10] as List<OnlineSearchResult>
        val theme = args[11] as AppThemeMode
        val lang = args[12] as AppLanguage
        val notice = args[13] as String?
        val selPlaylist = args[14] as Playlist?
        val pTracks = args[15] as List<Track>

        MainUiState(
            allTracks = tracks,
            top10MonthlyTracks = top10,
            favoriteTracks = favorites,
            downloadedTracks = downloaded,
            offlineTracks = offline,
            playlists = playlists,
            downloads = downloads,
            googleUser = gUser,
            searchQuery = query,
            selectedCategory = cat,
            searchResults = searchRes,
            activeThemeMode = theme,
            selectedLanguage = lang,
            userNoticeMessage = notice,
            selectedPlaylist = selPlaylist,
            playlistTracks = pTracks
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    private var isPlayerStateRestored = false

    init {
        // Perform initial search to show default featured songs in search screen
        updateOnlineSearch("", "ALL")

        viewModelScope.launch {
            musicRepository.allTracks.collect { tracks ->
                if (tracks.isNotEmpty() && !isPlayerStateRestored) {
                    isPlayerStateRestored = true
                    audioController.restoreSavedState(tracks)
                }
            }
        }

        // Count every actual playback session start (including auto next-track and Replay One),
        // filtering out position updates, pause/resume, and recompositions.
        viewModelScope.launch {
            var lastCountedSessionId: Long = -1L
            playerState.collect { state ->
                val id = state.currentTrack?.id
                if (state.isPlaying && id != null && state.playbackSessionId > 0L && state.playbackSessionId != lastCountedSessionId) {
                    lastCountedSessionId = state.playbackSessionId
                    musicRepository.incrementPlayCount(id)
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        _selectedLanguage.value = language
        prefs.edit().putString("selected_language", language.name).apply()
        val notice = if (language == AppLanguage.ENGLISH) "Language set to English" else "زبان به فارسی تغییر یافت"
        showNotice(notice)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _activeThemeMode.value = mode
        prefs.edit().putString("selected_theme", mode.name).apply()
        val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
        showNotice(if (isEn) "New theme applied" else "تم جدید اعمال شد")
    }

    fun scanLocalDeviceAudio(userInitiated: Boolean = false) {
        viewModelScope.launch {
            val count = musicRepository.scanLocalMedia(getApplication())
            if (count > 0) {
                val msg = if (_selectedLanguage.value == AppLanguage.ENGLISH)
                    "$count new track(s) added from device"
                else
                    "$count آهنگ جدید در دستگاه پیدا شد"
                showNotice(msg)
            } else if (userInitiated) {
                val msg = if (_selectedLanguage.value == AppLanguage.ENGLISH)
                    "Scan complete: No new tracks found"
                else
                    "اسکن کامل شد: آهنگ جدیدی یافت نشد"
                showNotice(msg)
            }
        }
    }

    fun setUsername(value: String) {
        val clean = value.trim().take(32)
        _username.value = clean
        prefs.edit().putString("username", clean).apply()
        val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
        val msg = if (clean.isBlank()) {
            if (isEn) "Username cleared" else "نام کاربری پاک شد"
        } else {
            if (isEn) "Username saved" else "نام کاربری ذخیره شد"
        }
        showNotice(msg)
    }

    fun fetchOnlineLyricsForTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Fetching lyrics online..." else "در حال دریافت متن ترانه از اینترنت...")
            try {
                val lyrics = fetchLyricsFromLrclib(track.artist, track.title)
                    ?: fetchLyricsFromLyricsOvh(track.artist, track.title)

                if (lyrics.isNullOrBlank()) {
                    showNotice(if (isEn) "Lyrics not found in online sources" else "متن این آهنگ در منابع آنلاین پیدا نشد")
                    return@launch
                }

                db.musicDao().updateTrackLyrics(track.id, lyrics)
                audioController.updateCurrentTrackLyrics(track.id, lyrics)
                showNotice(if (isEn) "Lyrics downloaded successfully ✨" else "متن ترانه از اینترنت دریافت شد ✨")
            } catch (e: Exception) {
                android.util.Log.e("Lyrics", "Lyrics fetch failed", e)
                showNotice(if (isEn) "Lyrics fetch failed; check internet connection" else "دریافت متن ترانه ناموفق بود؛ اتصال اینترنت را بررسی کنید")
            }
        }
    }

    private fun fetchLyricsFromLrclib(artist: String, title: String): String? {
        val url = "https://lrclib.net/api/get?artist_name=${encode(artist)}&track_name=${encode(title)}"
        val json = httpGetJson(url) ?: return null
        val synced = json.optString("syncedLyrics").trim()
        val plain = json.optString("plainLyrics").trim()
        return synced.ifBlank { plain }.ifBlank { null }
    }

    private fun fetchLyricsFromLyricsOvh(artist: String, title: String): String? {
        val url = "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}"
        val json = httpGetJson(url) ?: return null
        return json.optString("lyrics").trim().ifBlank { null }
    }

    private fun httpGetJson(urlString: String): JSONObject? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NiMusic/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    fun playTrack(track: Track, tracksList: List<Track>) {
        audioController.setQueueAndPlay(tracksList, tracksList.indexOf(track).coerceAtLeast(0))
    }

    fun addTrackToQueue(track: Track) {
        audioController.addToQueue(track)
        val msg = if (_selectedLanguage.value == AppLanguage.ENGLISH)
            "Added to currently playing queue"
        else
            "آهنگ «${track.title}» به صف پخش اضافه شد"
        showNotice(msg)
    }

    fun playNextInQueue(track: Track) {
        audioController.playNextInQueue(track)
        val msg = if (_selectedLanguage.value == AppLanguage.ENGLISH)
            "Will play after current track"
        else
            "آهنگ «${track.title}» بعد از آهنگ فعلی پخش خواهد شد"
        showNotice(msg)
    }

    fun deleteTrackPermanently(track: Track) {
        viewModelScope.launch {
            musicRepository.deleteTrackPermanently(getApplication(), track)
            audioController.removeTrackCompletely(track.id)
            val msg = if (_selectedLanguage.value == AppLanguage.ENGLISH)
                "Track permanently deleted"
            else
                "آهنگ «${track.title}» به طور کامل پاک شد"
            showNotice(msg)
        }
    }

    fun playAllWithFairShuffle(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        audioController.setShuffleMode(ShuffleMode.FAIR_NON_REPEAT)
        audioController.setQueueAndPlay(tracks, startIndex = 0, isManualSelect = false)
        val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
        showNotice(if (isEn) "Fair Shuffle activated! No repeated songs until list ends." else "حالت شافل عادلانه فعال شد! هیچ آهنگ تکراری تا انتهای لیست نخواهید شنید.")
    }

    fun playAllWithSmartShuffle(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        audioController.setShuffleMode(ShuffleMode.SMART_SHUFFLE)
        audioController.setQueueAndPlay(tracks, startIndex = 0, isManualSelect = false)
        val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
        showNotice(if (isEn) "Smart Shuffle active (Personalized AI Queue)" else "شافل هوشمند فعال شد (صف هوشمند بر اساس سلیقه شما)")
    }

    fun toggleShuffleMode() {
        audioController.toggleShuffleMode()
        val mode = playerState.value.shuffleMode
        val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
        val msg = when (mode) {
            ShuffleMode.OFF -> if (isEn) "Shuffle: Off ⏹️" else "شافل: غیرفعال ⏹️"
            ShuffleMode.FAIR_NON_REPEAT -> if (isEn) "True Fair Shuffle 🔀" else "شافل عادلانه 🔀"
            ShuffleMode.SMART_SHUFFLE -> if (isEn) "Smart Shuffle ✨" else "شافل هوشمند ✨"
            ShuffleMode.STANDARD_RANDOM -> if (isEn) "Standard Random Shuffle 🎲" else "شافل تصادفی استاندارد 🎲"
        }
        showNotice(msg)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track.id, track.isFavorite)
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            val actionMsg = if (track.isFavorite) {
                if (isEn) "removed from Favorites" else "از علاقمندی‌ها حذف شد"
            } else {
                if (isEn) "added to Favorites ❤️" else "به علاقمندی‌ها اضافه شد ❤️"
            }
            showNotice("${track.title} $actionMsg")
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            musicRepository.createPlaylist(name, description)
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Playlist \"$name\" created 🎵" else "پلی‌لیست «$name» با موفقیت ساخته شد 🎵")
        }
    }

    fun updatePlaylist(playlist: Playlist, newName: String, newDescription: String, newCoverUrl: String = playlist.coverUrl) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = playlist.copy(name = newName.trim(), description = newDescription.trim(), coverUrl = newCoverUrl)
            musicRepository.updatePlaylist(updated)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = updated
            }
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Playlist updated successfully ✨" else "پلی‌لیست با موفقیت ویرایش شد ✨")
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlist.id)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
            }
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Playlist \"${playlist.name}\" deleted" else "پلی‌لیست «${playlist.name}» حذف شد")
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch {
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            when (playlist.smartType) {
                "FAVORITES" -> {
                    if (!track.isFavorite) {
                        musicRepository.toggleFavorite(track.id, false)
                    }
                    showNotice(if (isEn) "Added \"${track.title}\" to Favorites" else "آهنگ «${track.title}» به علاقمندی‌ها اضافه شد")
                }
                "DOWNLOADS" -> {
                    val updated = track.copy(isDownloaded = true)
                    db.musicDao().updateTrack(updated)
                    showNotice(if (isEn) "Added \"${track.title}\" to Downloads" else "آهنگ «${track.title}» به دانلود شده‌ها اضافه شد")
                }
                else -> {
                    musicRepository.addTrackToPlaylist(playlist.id, track.id)
                    showNotice(if (isEn) "Added \"${track.title}\" to \"${playlist.name}\"" else "آهنگ «${track.title}» به «${playlist.name}» اضافه شد")
                }
            }
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch {
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            when (playlist.smartType) {
                "FAVORITES" -> {
                    musicRepository.toggleFavorite(track.id, true)
                    showNotice(if (isEn) "Removed \"${track.title}\" from Favorites" else "آهنگ «${track.title}» از علاقمندی‌ها حذف شد")
                }
                "DOWNLOADS" -> {
                    val updated = track.copy(isDownloaded = false)
                    db.musicDao().updateTrack(updated)
                    showNotice(if (isEn) "Removed \"${track.title}\" from Downloads" else "آهنگ «${track.title}» از دانلود شده‌ها حذف شد")
                }
                else -> {
                    musicRepository.removeTrackFromPlaylist(playlist.id, track.id)
                    showNotice(if (isEn) "Removed \"${track.title}\" from \"${playlist.name}\"" else "آهنگ «${track.title}» از «${playlist.name}» حذف شد")
                }
            }
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        if (playlist != null) {
            viewModelScope.launch {
                val flow = when (playlist.smartType) {
                    "FAVORITES" -> musicRepository.favoriteTracks
                    "DOWNLOADS" -> musicRepository.downloadedTracks
                    else -> musicRepository.getTracksForPlaylist(playlist.id)
                }
                flow.collect { tracks ->
                    _playlistTracks.value = tracks
                }
            }
        } else {
            _playlistTracks.value = emptyList()
        }
    }

    fun updateOnlineSearch(query: String, category: String) {
        _searchQuery.value = query
        _selectedCategory.value = category
        _searchResults.value = downloadRepository.searchOnlineAudio(query, category)
    }

    fun startDownload(result: OnlineSearchResult) {
        viewModelScope.launch {
            downloadRepository.startDownload(result)
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Started downloading \"${result.title}\" 📥" else "دانلود «${result.title}» آغاز شد 📥")
        }
    }

    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(downloadId)
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Downloaded file removed" else "فایل دانلود شده حذف شد")
        }
    }

    fun connectGoogleAccount(email: String, name: String) {
        viewModelScope.launch {
            googleRepository.connectGoogleAccount(email, name)
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "Google account \"$name\" connected successfully ✨" else "اتصال به حساب گوگل «$name» با موفقیت برقرار شد ✨")
        }
    }

    fun backupToGoogleCloud() {
        viewModelScope.launch {
            val success = googleRepository.performCloudBackup()
            if (success) {
                val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
                showNotice(if (isEn) "Playlists and stats backed up to Google Cloud ☁️" else "پلی‌لیست‌ها و آمار شما در Google Cloud پشتیبان‌گیری شد ☁️")
            }
        }
    }

    fun restoreFromGoogleCloud() {
        viewModelScope.launch {
            val count = googleRepository.performCloudRestore()
            val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
            showNotice(if (isEn) "$count synced playlist(s) restored from Google 🔄" else "$count پلی‌لیست همگام‌سازی‌شده از گوگل بازیابی شد 🔄")
        }
    }

    fun updateEqualizer(settings: EqualizerSettings) {
        audioController.updateEqualizer(settings)
        val isEn = _selectedLanguage.value == AppLanguage.ENGLISH
        showNotice(if (isEn) "Equalizer settings saved 🎛️" else "تنظیمات اکولایزر ذخیره شد 🎛️")
    }

    fun showNotice(message: String) {
        _userNotice.value = message
    }

    fun clearNotice() {
        _userNotice.value = null
    }

    override fun onCleared() {
        // Playback is process-wide and may be owned by the foreground playback service.
        // Do not release the controller when the UI ViewModel is recreated/destroyed.
        super.onCleared()
    }
}
