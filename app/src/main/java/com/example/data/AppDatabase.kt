package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.MusicDao
import com.example.data.models.DownloadItem
import com.example.data.models.GoogleUser
import com.example.data.models.Playlist
import com.example.data.models.PlaylistTrackCrossRef
import com.example.data.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Track::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
        DownloadItem::class,
        GoogleUser::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN monthlyPlayCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tracks ADD COLUMN lastPlayTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aura_music_db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateInitialData(getInstance(context).musicDao())
                            }
                        }
                    })
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(dao: MusicDao) {
            // Default Smart Playlists
            dao.insertPlaylist(
                Playlist(
                    name = "Favorites",
                    description = "Your starred tracks collection",
                    isSmart = true,
                    smartType = "FAVORITES"
                )
            )

            dao.insertPlaylist(
                Playlist(
                    name = "Downloaded",
                    description = "Offline ready tracks",
                    isSmart = true,
                    smartType = "DOWNLOADS"
                )
            )

            dao.insertPlaylist(
                Playlist(
                    name = "Chill Night",
                    description = "Relaxing late night tracks",
                    isSmart = false
                )
            )
        }
    }
}
