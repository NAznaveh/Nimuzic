package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.models.DownloadItem
import com.example.data.models.GoogleUser
import com.example.data.models.Playlist
import com.example.data.models.PlaylistTrackCrossRef
import com.example.data.models.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Tracks
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY monthlyPlayCount DESC, playCount DESC, dateAdded DESC LIMIT 10")
    fun getTop10MonthlyTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY dateAdded DESC")
    fun getDownloadedTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isOffline = 1 ORDER BY title ASC")
    fun getOfflineTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE audioUrl = :url OR downloadedPath = :url LIMIT 1")
    suspend fun findTrackByUrl(url: String): Track?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Update
    suspend fun updateTrack(track: Track)

    @Query("UPDATE tracks SET isFavorite = :isFav WHERE id = :trackId")
    suspend fun setFavorite(trackId: Long, isFav: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, monthlyPlayCount = CASE WHEN :isNewMonth THEN 1 ELSE monthlyPlayCount + 1 END, lastPlayTimestamp = :now WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: Long, isNewMonth: Boolean, now: Long)

    @Query("UPDATE tracks SET lyrics = :lyrics WHERE id = :trackId")
    suspend fun updateTrackLyrics(trackId: Long, lyrics: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY dateCreated DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // Playlist Tracks CrossRef
    @Query("SELECT t.* FROM tracks t INNER JOIN playlist_track_cross_ref ref ON t.id = ref.trackId WHERE ref.playlistId = :playlistId ORDER BY ref.sortOrder ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId = :trackId")
    suspend fun removeTrackFromAllPlaylists(trackId: Long)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    // Downloads
    @Query("SELECT * FROM download_items ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getAllDownloadsSnapshot(id: Long): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadItem): Long

    @Update
    suspend fun updateDownload(download: DownloadItem)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    // Google User Profile
    @Query("SELECT * FROM google_user WHERE id = 1")
    fun getGoogleUser(): Flow<GoogleUser?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGoogleUser(user: GoogleUser)
}
