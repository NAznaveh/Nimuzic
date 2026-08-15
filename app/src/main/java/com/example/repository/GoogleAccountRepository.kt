package com.example.repository

import com.example.data.dao.MusicDao
import com.example.data.models.GoogleUser
import kotlinx.coroutines.flow.Flow

class GoogleAccountRepository(private val musicDao: MusicDao) {

    val googleUser: Flow<GoogleUser?> = musicDao.getGoogleUser()

    suspend fun connectGoogleAccount(email: String, name: String, photoUrl: String = "") {
        val user = GoogleUser(
            id = 1,
            isConnected = true,
            userEmail = email,
            displayName = name,
            photoUrl = photoUrl,
            cloudPlaylistsCount = 5,
            lastSyncedTimestamp = System.currentTimeMillis()
        )
        musicDao.updateGoogleUser(user)
    }

    suspend fun disconnectGoogleAccount() {
        val user = GoogleUser(
            id = 1,
            isConnected = false,
            userEmail = "",
            displayName = "",
            cloudPlaylistsCount = 0,
            lastSyncedTimestamp = 0
        )
        musicDao.updateGoogleUser(user)
    }

    suspend fun performCloudBackup(): Boolean {
        val currentUser = musicDao.getGoogleUser()
        // Simulate uploading local playlists & favorites to Google Drive / Cloud database
        kotlinx.coroutines.delay(1200)
        val updated = GoogleUser(
            id = 1,
            isConnected = true,
            userEmail = "nima.mahmoudi.az1384@gmail.com",
            displayName = "Nima Mahmoudi",
            cloudPlaylistsCount = 6,
            lastSyncedTimestamp = System.currentTimeMillis()
        )
        musicDao.updateGoogleUser(updated)
        return true
    }

    suspend fun performCloudRestore(): Int {
        // Simulates restoring synced playlists from Google Drive
        kotlinx.coroutines.delay(1500)
        return 3 // Returns restored playlist count
    }
}
