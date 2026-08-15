package com.example.repository

import android.content.Context
import com.example.data.dao.MusicDao
import com.example.data.models.DownloadItem
import com.example.data.models.DownloadStatus
import com.example.data.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

data class OnlineSearchResult(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val audioUrl: String,
    val fileSizeMb: Float,
    val category: String,
    val coverDrawableResName: String = "img_hero_playlist"
)

class DownloadRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {
    val allDownloads: Flow<List<DownloadItem>> = musicDao.getAllDownloads()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeJobs = mutableMapOf<Long, Job>()

    // Catalog of online audio streams available for search & download
    val onlineCatalog = listOf(
        OnlineSearchResult(
            id = 101L,
            title = "Aura Cyberpunk Echoes",
            artist = "Tehran Synthwave Club",
            album = "Persian Cyberpunk 2088",
            durationMs = 215000L,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            fileSizeMb = 5.2f,
            category = "Synthwave"
        ),
        OnlineSearchResult(
            id = 102L,
            title = "Midnight Coffee Study Lofi",
            artist = "Lofi Chill Beats",
            album = "Rainy Afternoon",
            durationMs = 190000L,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            fileSizeMb = 4.5f,
            category = "Lofi"
        ),
        OnlineSearchResult(
            id = 103L,
            title = "Persian Fusion Set - Tar & Beats",
            artist = "Alireza & Electro Band",
            album = "Modern Silk Road",
            durationMs = 270000L,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            fileSizeMb = 6.8f,
            category = "Persian Electro"
        ),
        OnlineSearchResult(
            id = 104L,
            title = "Tech & Tech Audio Podcast Ep. 42",
            artist = "Tech Persian Podcast",
            album = "AI & Music Technology",
            durationMs = 320000L,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            fileSizeMb = 8.1f,
            category = "Podcasts"
        ),
        OnlineSearchResult(
            id = 105L,
            title = "Acoustic Sunset Guitar Serenade",
            artist = "Kaveh Solo Guitar",
            album = "Persian Nights",
            durationMs = 230000L,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
            fileSizeMb = 5.6f,
            category = "Traditional"
        ),
        OnlineSearchResult(
            id = 106L,
            title = "Deep Focus Ambient Ocean",
            artist = "Mindfulness Audio",
            album = "Serene Meditation",
            durationMs = 280000L,
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3",
            fileSizeMb = 7.0f,
            category = "Ambient"
        )
    )

    fun searchOnlineAudio(query: String, categoryFilter: String = "ALL"): List<OnlineSearchResult> {
        return onlineCatalog.filter { item ->
            val matchesCategory = categoryFilter == "ALL" || item.category.equals(categoryFilter, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.artist.contains(query, ignoreCase = true) ||
                    item.album.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    suspend fun startDownload(result: OnlineSearchResult): Long {
        // Prevent duplicate concurrent downloads of the same resource
        val activeList = activeJobs.keys.toList()
        for (id in activeList) {
            val item = musicDao.getAllDownloadsSnapshot(id)
            if (item?.audioUrl == result.audioUrl && (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED)) {
                return id
            }
        }

        val downloadItem = DownloadItem(
            trackId = result.id,
            title = result.title,
            artist = result.artist,
            audioUrl = result.audioUrl,
            coverUrl = result.coverDrawableResName,
            progress = 0,
            speedKbps = 0f,
            downloadedBytes = 0,
            totalBytes = 0L,
            status = DownloadStatus.QUEUED
        )

        val downloadId = musicDao.insertDownload(downloadItem)
        executeDownloadProcess(downloadId, result)
        return downloadId
    }

    private fun executeDownloadProcess(downloadId: Long, result: OnlineSearchResult) {
        val job = scope.launch {
            var connection: HttpURLConnection? = null
            var targetFile: File? = null
            try {
                val musicDir = File(context.filesDir, "downloads")
                if (!musicDir.exists()) musicDir.mkdirs()
                val sanitizedTitle = result.title.replace(Regex("[^a-zA-Z0-9_آ-ی]"), "_")
                targetFile = File(musicDir, "${sanitizedTitle}_${result.id}.mp3")

                val existingBytes = if (targetFile.exists()) targetFile.length() else 0L
                connection = (URL(result.audioUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NiMusic/1.0")
                    if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
                    connect()
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw IllegalStateException("HTTP $responseCode")
                }

                val contentType = connection.contentType.orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("application/json")) {
                    throw IllegalArgumentException("The selected URL is not a direct audio file")
                }

                val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
                val startBytes = if (append) existingBytes else 0L
                val responseLength = connection.contentLengthLong.coerceAtLeast(0L)
                val totalBytes = if (append && responseLength > 0L) startBytes + responseLength else responseLength
                musicDao.updateDownload(
                    DownloadItem(
                        id = downloadId,
                        trackId = result.id,
                        title = result.title,
                        artist = result.artist,
                        audioUrl = result.audioUrl,
                        coverUrl = result.coverDrawableResName,
                        progress = if (totalBytes > 0L) ((startBytes * 100L) / totalBytes).toInt() else 0,
                        speedKbps = 0f,
                        downloadedBytes = startBytes,
                        totalBytes = totalBytes,
                        status = DownloadStatus.DOWNLOADING,
                        savedFilePath = targetFile.absolutePath
                    )
                )

                var downloadedBytes = startBytes
                var lastSampleBytes = downloadedBytes
                var lastSampleAt = System.currentTimeMillis()
                BufferedInputStream(connection.inputStream).use { input ->
                    java.io.FileOutputStream(targetFile, append).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastSampleAt >= 250L) {
                                val elapsedSec = (now - lastSampleAt).coerceAtLeast(1L) / 1000f
                                val speedKbps = ((downloadedBytes - lastSampleBytes) / 1024f) / elapsedSec
                                val progress = if (totalBytes > 0L) ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
                                musicDao.updateDownload(
                                    DownloadItem(
                                        id = downloadId,
                                        trackId = result.id,
                                        title = result.title,
                                        artist = result.artist,
                                        audioUrl = result.audioUrl,
                                        coverUrl = result.coverDrawableResName,
                                        progress = progress,
                                        speedKbps = speedKbps,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        status = DownloadStatus.DOWNLOADING,
                                        savedFilePath = targetFile.absolutePath
                                    )
                                )
                                lastSampleBytes = downloadedBytes
                                lastSampleAt = now
                            }
                        }
                    }
                }

                val completedItem = DownloadItem(
                    id = downloadId,
                    trackId = result.id,
                    title = result.title,
                    artist = result.artist,
                    audioUrl = result.audioUrl,
                    coverUrl = result.coverDrawableResName,
                    progress = 100,
                    speedKbps = 0f,
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
                    status = DownloadStatus.COMPLETED,
                    savedFilePath = targetFile.absolutePath
                )
                musicDao.updateDownload(completedItem)

                val existingTrack = musicDao.findTrackByUrl(targetFile.absolutePath)
                    ?: musicDao.findTrackByUrl(result.audioUrl)

                if (existingTrack != null) {
                    val updated = existingTrack.copy(
                        isDownloaded = true,
                        downloadedPath = targetFile.absolutePath,
                        isOffline = true
                    )
                    musicDao.updateTrack(updated)
                } else {
                    val newTrack = Track(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        durationMs = result.durationMs,
                        audioUrl = result.audioUrl,
                        coverDrawableResName = result.coverDrawableResName,
                        isDownloaded = true,
                        downloadedPath = targetFile.absolutePath,
                        category = result.category,
                        isOffline = true
                    )
                    musicDao.insertTrack(newTrack)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                targetFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepository", "Download failed: ${result.audioUrl}", e)
                val existing = musicDao.getAllDownloadsSnapshot(downloadId)
                musicDao.updateDownload(
                    DownloadItem(
                        id = downloadId,
                        trackId = result.id,
                        title = result.title,
                        artist = result.artist,
                        audioUrl = result.audioUrl,
                        coverUrl = result.coverDrawableResName,
                        progress = existing?.progress ?: 0,
                        speedKbps = 0f,
                        downloadedBytes = existing?.downloadedBytes ?: 0L,
                        totalBytes = existing?.totalBytes ?: 0L,
                        status = DownloadStatus.FAILED,
                        savedFilePath = targetFile?.absolutePath.orEmpty()
                    )
                )
            } finally {
                connection?.disconnect()
            }
        }
        activeJobs[downloadId] = job
    }

    suspend fun pauseDownload(downloadId: Long) {
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        musicDao.getAllDownloadsSnapshot(downloadId)?.let { current ->
            musicDao.updateDownload(current.copy(status = DownloadStatus.PAUSED, speedKbps = 0f))
        }
    }

    suspend fun deleteDownload(downloadId: Long) {
        val item = musicDao.getAllDownloadsSnapshot(downloadId)
        pauseDownload(downloadId)
        item?.savedFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
            musicDao.findTrackByUrl(path)?.let { track ->
                musicDao.updateTrack(
                    track.copy(
                        isDownloaded = false,
                        isOffline = false,
                        downloadedPath = ""
                    )
                )
            }
        }
        musicDao.deleteDownload(downloadId)
    }
}
