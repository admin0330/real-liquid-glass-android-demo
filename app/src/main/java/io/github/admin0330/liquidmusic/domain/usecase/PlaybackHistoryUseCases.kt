package io.github.admin0330.liquidmusic.domain.usecase

import io.github.admin0330.liquidmusic.domain.model.PlaybackHistoryItem
import io.github.admin0330.liquidmusic.domain.repository.PlaybackHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlaybackHistoryUseCase @Inject constructor(
    private val repository: PlaybackHistoryRepository,
) {
    operator fun invoke(limit: Int = 100): Flow<List<PlaybackHistoryItem>> =
        repository.observeHistory(limit.coerceAtLeast(0))
}

class RecordPlaybackUseCase @Inject constructor(
    private val repository: PlaybackHistoryRepository,
) {
    suspend operator fun invoke(
        trackId: String,
        playedAtMs: Long,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
    ): Boolean = repository.recordPlayback(
        trackId = trackId,
        playedAtMs = playedAtMs,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = completed,
    )
}

class ClearPlaybackHistoryUseCase @Inject constructor(
    private val repository: PlaybackHistoryRepository,
) {
    suspend operator fun invoke() = repository.clearHistory()
}
