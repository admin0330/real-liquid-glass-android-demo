package io.github.admin0330.liquidmusic.domain.repository

import io.github.admin0330.liquidmusic.domain.model.PlaybackHistoryItem
import kotlinx.coroutines.flow.Flow

interface PlaybackHistoryRepository {
    fun observeHistory(limit: Int = 100): Flow<List<PlaybackHistoryItem>>

    suspend fun recordPlayback(
        trackId: String,
        playedAtMs: Long,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
    ): Boolean

    suspend fun clearHistory()
}
