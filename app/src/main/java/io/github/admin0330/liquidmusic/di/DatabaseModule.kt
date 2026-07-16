package io.github.admin0330.liquidmusic.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.admin0330.liquidmusic.data.local.database.LiquidMusicDatabase
import io.github.admin0330.liquidmusic.data.local.database.dao.FavoriteDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaybackHistoryDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaylistDao
import io.github.admin0330.liquidmusic.data.local.database.dao.TrackDao
import io.github.admin0330.liquidmusic.data.local.database.dao.LyricsDao
import io.github.admin0330.liquidmusic.data.repository.LocalMusicLibraryRepository
import io.github.admin0330.liquidmusic.data.repository.LocalPlaybackHistoryRepository
import io.github.admin0330.liquidmusic.data.repository.LocalPlaylistRepository
import io.github.admin0330.liquidmusic.data.repository.LocalLyricsRepository
import io.github.admin0330.liquidmusic.domain.repository.MusicLibraryRepository
import io.github.admin0330.liquidmusic.domain.repository.PlaybackHistoryRepository
import io.github.admin0330.liquidmusic.domain.repository.PlaylistRepository
import io.github.admin0330.liquidmusic.domain.repository.LyricsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LiquidMusicDatabase =
        Room.databaseBuilder(
            context,
            LiquidMusicDatabase::class.java,
            LiquidMusicDatabase.FILE_NAME,
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideTrackDao(database: LiquidMusicDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: LiquidMusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideFavoriteDao(database: LiquidMusicDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providePlaybackHistoryDao(database: LiquidMusicDatabase): PlaybackHistoryDao =
        database.playbackHistoryDao()

    @Provides
    fun provideLyricsDao(database: LiquidMusicDatabase): LyricsDao = database.lyricsDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicLibraryRepository(
        implementation: LocalMusicLibraryRepository,
    ): MusicLibraryRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        implementation: LocalPlaylistRepository,
    ): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackHistoryRepository(
        implementation: LocalPlaybackHistoryRepository,
    ): PlaybackHistoryRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(
        implementation: LocalLyricsRepository,
    ): LyricsRepository
}
