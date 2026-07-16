package io.github.admin0330.liquidmusic.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.admin0330.liquidmusic.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackHistoryTracker(
    private val repository: PlaybackHistoryRepository,
    private val writeScope: CoroutineScope,
) {
    private var attempt: PlaybackAttempt? = null

    fun onMediaItemTransition(player: Player, mediaItem: MediaItem?, reason: Int) {
        val incomingEntryId = mediaItem?.mediaId
        if (attempt?.queueEntryId == incomingEntryId &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        ) {
            updatePosition(player)
            return
        }
        finishAttempt(
            completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        )
        mediaItem?.let { beginAttempt(it, player) }
    }

    fun onIsPlayingChanged(player: Player, isPlaying: Boolean) {
        ensureAttempt(player)
        updateAccumulatedPlayback()
        attempt?.activeSinceRealtimeMs = if (isPlaying) SystemClock.elapsedRealtime() else null
        updatePosition(player)
        recordIfEligible(completed = false)
    }

    fun onPlaybackStateChanged(player: Player, playbackState: Int) {
        tick(player)
        if (playbackState == Player.STATE_ENDED) {
            recordIfEligible(completed = true)
        }
    }

    fun onTimelineChanged(player: Player) {
        val currentEntryId = player.currentMediaItem?.mediaId
        if (currentEntryId == null) {
            finishAttempt(completed = false)
            return
        }
        if (attempt == null) ensureAttempt(player)
    }

    fun onPositionDiscontinuity(
        player: Player,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val activeAttempt = attempt
        val oldEntryId = oldPosition.mediaItem?.mediaId
        val newEntryId = newPosition.mediaItem?.mediaId
        if (oldEntryId != newEntryId) {
            if (attempt?.queueEntryId != newEntryId) {
                finishAttempt(completed = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
                newPosition.mediaItem?.let { beginAttempt(it, player) }
            } else {
                updatePosition(player)
            }
            return
        }
        val restartedFinishedItem = reason == Player.DISCONTINUITY_REASON_SEEK &&
            activeAttempt?.recorded == true &&
            oldPosition.positionMs >= (activeAttempt.durationMs * COMPLETION_PERCENT) / 100 &&
            newPosition.positionMs <= RESTART_POSITION_TOLERANCE_MS
        if (restartedFinishedItem) {
            finishAttempt(completed = false)
            newPosition.mediaItem?.let { beginAttempt(it, player) }
        } else {
            ensureAttempt(player)
            updatePosition(player)
        }
    }

    fun tick(player: Player) {
        ensureAttempt(player)
        updateAccumulatedPlayback()
        if (player.isPlaying) {
            attempt?.activeSinceRealtimeMs = SystemClock.elapsedRealtime()
        }
        updatePosition(player)
        recordIfEligible(completed = false)
    }

    fun finish(player: Player, completed: Boolean = false) {
        ensureAttempt(player)
        updateAccumulatedPlayback()
        updatePosition(player)
        finishAttempt(completed)
    }

    private fun ensureAttempt(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val current = attempt
        if (current?.queueEntryId == mediaItem.mediaId) return
        current?.let { finishAttempt(completed = false) }
        beginAttempt(mediaItem, player)
    }

    private fun beginAttempt(mediaItem: MediaItem, player: Player) {
        val trackId = TrackMediaItemMapper.trackId(mediaItem) ?: return
        val durationMs = player.duration.validTimeOrZero()
            .takeIf { it > 0 }
            ?: TrackMediaItemMapper.toQueueEntry(mediaItem)?.track?.durationMs.orZero()
        attempt = PlaybackAttempt(
            queueEntryId = mediaItem.mediaId,
            trackId = trackId,
            startedAtMs = System.currentTimeMillis(),
            durationMs = durationMs,
            lastPositionMs = player.currentPosition.validTimeOrZero(),
            activeSinceRealtimeMs = if (player.isPlaying) SystemClock.elapsedRealtime() else null,
        )
    }

    private fun updateAccumulatedPlayback() {
        val activeAttempt = attempt ?: return
        val activeSince = activeAttempt.activeSinceRealtimeMs ?: return
        val now = SystemClock.elapsedRealtime()
        activeAttempt.accumulatedPlaybackMs += (now - activeSince).coerceAtLeast(0)
        activeAttempt.activeSinceRealtimeMs = null
    }

    private fun updatePosition(player: Player) {
        val activeAttempt = attempt ?: return
        activeAttempt.lastPositionMs = player.currentPosition.validTimeOrZero()
        player.duration.validTimeOrZero().takeIf { it > 0 }?.let { activeAttempt.durationMs = it }
    }

    private fun finishAttempt(completed: Boolean) {
        updateAccumulatedPlayback()
        recordIfEligible(completed)
        attempt = null
    }

    private fun recordIfEligible(completed: Boolean) {
        val activeAttempt = attempt ?: return
        if (activeAttempt.recorded) return

        val positionCompleted = activeAttempt.durationMs > 0 &&
            activeAttempt.lastPositionMs >=
            (activeAttempt.durationMs * COMPLETION_PERCENT) / 100
        val shouldMarkCompleted = completed || positionCompleted
        val reachedThreshold = activeAttempt.accumulatedPlaybackMs >=
            historyThresholdMs(activeAttempt.durationMs)
        if (!shouldMarkCompleted && !reachedThreshold) return

        activeAttempt.recorded = true
        val trackId = activeAttempt.trackId
        val playedAtMs = activeAttempt.startedAtMs
        val positionMs = activeAttempt.lastPositionMs
        val durationMs = activeAttempt.durationMs
        writeScope.launch {
            runCatching {
                repository.recordPlayback(
                    trackId = trackId,
                    playedAtMs = playedAtMs,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    completed = shouldMarkCompleted,
                )
            }
        }
    }

    private fun historyThresholdMs(durationMs: Long): Long {
        if (durationMs <= 0) return DEFAULT_HISTORY_THRESHOLD_MS
        return minOf(
            DEFAULT_HISTORY_THRESHOLD_MS,
            maxOf(MINIMUM_HISTORY_THRESHOLD_MS, durationMs / 2),
        )
    }

    private data class PlaybackAttempt(
        val queueEntryId: String,
        val trackId: String,
        val startedAtMs: Long,
        var durationMs: Long,
        var lastPositionMs: Long,
        var accumulatedPlaybackMs: Long = 0,
        var activeSinceRealtimeMs: Long?,
        var recorded: Boolean = false,
    )

    private companion object {
        const val DEFAULT_HISTORY_THRESHOLD_MS = 30_000L
        const val MINIMUM_HISTORY_THRESHOLD_MS = 5_000L
        const val COMPLETION_PERCENT = 90L
        const val RESTART_POSITION_TOLERANCE_MS = 1_000L
    }
}

private fun Long.validTimeOrZero(): Long = if (this == C.TIME_UNSET || this < 0) 0 else this

private fun Long?.orZero(): Long = this?.coerceAtLeast(0) ?: 0
