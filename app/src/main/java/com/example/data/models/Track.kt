package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val durationMs: Long = 210000L,
    val audioUrl: String,
    val coverUrl: String = "",
    val coverDrawableResName: String = "",
    val isOffline: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadedPath: String = "",
    val category: String = "Pop",
    val dateAdded: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val monthlyPlayCount: Int = 0,
    val lastPlayTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val lyrics: String = ""
)
