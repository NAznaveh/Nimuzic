package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "google_user")
data class GoogleUser(
    @PrimaryKey val id: Int = 1,
    val isConnected: Boolean = false,
    val userEmail: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val cloudPlaylistsCount: Int = 0,
    val lastSyncedTimestamp: Long = 0,
    val autoCloudSync: Boolean = true,
    val totalPlayTimeSeconds: Long = 0
)
