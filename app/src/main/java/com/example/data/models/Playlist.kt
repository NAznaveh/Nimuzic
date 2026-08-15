package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val isSmart: Boolean = false,
    val smartType: String = "", // FAVORITES, DOWNLOADS, RECENT, MOST_PLAYED
    val dateCreated: Long = System.currentTimeMillis(),
    val isSyncedWithGoogle: Boolean = false
)
