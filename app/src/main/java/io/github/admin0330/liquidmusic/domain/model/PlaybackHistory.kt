package io.github.admin0330.liquidmusic.domain.model

data class PlaybackHistoryItem(
    val historyId: Long,
    val track: Track,
    val playedAtMs: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
)
