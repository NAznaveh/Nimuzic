package com.example.repository

import com.example.data.dao.MusicDao
import com.example.data.models.Playlist
import com.example.data.models.PlaylistTrackCrossRef
import com.example.data.models.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import android.content.Context
import android.provider.MediaStore

class MusicRepository(private val musicDao: MusicDao) {

    val allTracks: Flow<List<Track>> = musicDao.getAllTracks()
    val top10MonthlyTracks: Flow<List<Track>> = musicDao.getTop10MonthlyTracks()
    val favoriteTracks: Flow<List<Track>> = musicDao.getFavoriteTracks()
    val downloadedTracks: Flow<List<Track>> = musicDao.getDownloadedTracks()
    val offlineTracks: Flow<List<Track>> = musicDao.getOfflineTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun incrementPlayCount(trackId: Long) {
        val track = musicDao.getTrackById(trackId) ?: return
        val now = System.currentTimeMillis()
        val calNow = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val calLast = java.util.Calendar.getInstance().apply { timeInMillis = track.lastPlayTimestamp }
        val isNewMonth = (track.lastPlayTimestamp == 0L) ||
                (calNow.get(java.util.Calendar.YEAR) != calLast.get(java.util.Calendar.YEAR)) ||
                (calNow.get(java.util.Calendar.MONTH) != calLast.get(java.util.Calendar.MONTH))
        musicDao.incrementPlayCount(trackId, isNewMonth, now)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun toggleFavorite(trackId: Long, isFavorite: Boolean) {
        musicDao.setFavorite(trackId, !isFavorite)
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        val newPlaylist = Playlist(
            name = name,
            description = description,
            dateCreated = System.currentTimeMillis()
        )
        return musicDao.insertPlaylist(newPlaylist)
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        musicDao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.clearPlaylistTracks(playlistId)
        musicDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val crossRef = PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId)
        musicDao.insertPlaylistTrackCrossRef(crossRef)
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        musicDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun deleteTrackPermanently(context: Context, track: Track) = withContext(Dispatchers.IO) {
        val filePath = track.downloadedPath.ifEmpty { track.audioUrl }
        if (filePath.isNotEmpty() && !filePath.startsWith("http://") && !filePath.startsWith("https://")) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val contentResolver = context.contentResolver
                contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(filePath)
                )
            } catch (_: Exception) {}
        }

        musicDao.removeTrackFromAllPlaylists(track.id)
        musicDao.deleteTrack(track.id)
    }

    suspend fun scanLocalMedia(context: Context): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )
            cursor?.use { c ->
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val title = c.getString(titleCol) ?: "Unknown Track"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Local Album"
                    val duration = c.getLong(durationCol)
                    val path = c.getString(dataCol) ?: continue

                    // Check if track is already present in DB
                    if (musicDao.findTrackByUrl(path) != null) continue

                    val track = Track(
                        title = title,
                        artist = if (artist == "<unknown>") "Local Artist" else artist,
                        album = album,
                        durationMs = if (duration > 0) duration else 180000L,
                        audioUrl = path,
                        isOffline = true,
                        isDownloaded = true,
                        downloadedPath = path,
                        category = "Offline"
                    )
                    musicDao.insertTrack(track)
                    addedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Also scan internal files directory
        val deviceFolder = context.filesDir
        if (deviceFolder.exists() && deviceFolder.isDirectory) {
            val audioFiles = deviceFolder.listFiles { file ->
                file.extension.lowercase() in listOf("mp3", "wav", "m4a", "aac", "flac")
            } ?: emptyArray()

            audioFiles.forEach { file ->
                if (musicDao.findTrackByUrl(file.absolutePath) == null) {
                    val track = Track(
                        title = file.nameWithoutExtension,
                        artist = "Local Storage",
                        album = "Downloads",
                        audioUrl = file.absolutePath,
                        isOffline = true,
                        isDownloaded = true,
                        downloadedPath = file.absolutePath,
                        category = "Offline"
                    )
                    musicDao.insertTrack(track)
                    addedCount++
                }
            }
        }
        addedCount
    }
}
