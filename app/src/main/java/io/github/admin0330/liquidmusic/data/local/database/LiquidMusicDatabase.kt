package io.github.admin0330.liquidmusic.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.admin0330.liquidmusic.data.local.database.dao.FavoriteDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaybackHistoryDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaylistDao
import io.github.admin0330.liquidmusic.data.local.database.dao.TrackDao
import io.github.admin0330.liquidmusic.data.local.database.dao.LyricsDao
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaybackHistoryEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntryEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.LyricsEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        LyricsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LiquidMusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    abstract fun lyricsDao(): LyricsDao

    companion object {
        const val FILE_NAME = "liquid_music.db"
    }
}
