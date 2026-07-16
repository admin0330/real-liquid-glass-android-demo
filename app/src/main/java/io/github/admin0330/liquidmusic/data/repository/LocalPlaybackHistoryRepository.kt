package io.github.admin0330.liquidmusic.data.repository

import androidx.room.withTransaction
import io.github.admin0330.liquidmusic.data.local.database.LiquidMusicDatabase
import io.github.admin0330.liquidmusic.data.local.database.dao.FavoriteDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaybackHistoryDao
import io.github.admin0330.liquidmusic.data.local.database.dao.TrackDao
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaybackHistoryEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import io.github.admin0330.liquidmusic.domain.model.PlaybackHistoryItem
import io.github.admin0330.liquidmusic.domain.repository.PlaybackHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class LocalPlaybackHistoryRepository @Inject constructor(
    private val database: LiquidMusicDatabase,
    private val historyDao: PlaybackHistoryDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
) : PlaybackHistoryRepository {
    override fun observeHistory(limit: Int): Flow<List<PlaybackHistoryItem>> = combine(
        historyDao.observeRecent(limit.coerceAtLeast(0)),
        trackDao.observeTitleAscending(),
        favoriteDao.observeAll(),
        historyDao.observeStats(),
    ) { history, tracks, favorites, stats ->
        val tracksById = tracks.associateBy(TrackEntity::id)
        val favoritesByTrack = favorites.associateBy(FavoriteEntity::trackId)
        val statsByTrack = stats.associateBy { it.trackId }
        history.mapNotNull { item ->
            val track = tracksById[item.trackId] ?: return@mapNotNull null
            PlaybackHistoryItem(
                historyId = item.historyId,
                track = track.toDomain(
                    favoritesByTrack[item.trackId],
                    statsByTrack[item.trackId],
                ),
                playedAtMs = item.playedAtMs,
                positionMs = item.positionMs,
                durationMs = item.durationMs,
                completed = item.completed,
            )
        }
    }

    override suspend fun recordPlayback(
        trackId: String,
        playedAtMs: Long,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
    ): Boolean = database.withTransaction {
        if (trackDao.getById(trackId) == null) return@withTransaction false
        historyDao.insert(
            PlaybackHistoryEntity(
                trackId = trackId,
                playedAtMs = playedAtMs.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
                completed = completed,
            ),
        )
        historyDao.trimToMostRecent(MAX_HISTORY_ITEMS)
        true
    }

    override suspend fun clearHistory() = historyDao.clear()

    private companion object {
        const val MAX_HISTORY_ITEMS = 2_000
    }
}
