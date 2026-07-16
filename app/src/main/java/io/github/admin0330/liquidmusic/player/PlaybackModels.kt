package io.github.admin0330.liquidmusic.player

import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.admin0330.liquidmusic.domain.model.Track

enum class PlayerConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

enum class RepeatMode {
    OFF,
    ONE,
    ALL;

    internal fun toPlayerRepeatMode(): Int = when (this) {
        OFF -> Player.REPEAT_MODE_OFF
        ONE -> Player.REPEAT_MODE_ONE
        ALL -> Player.REPEAT_MODE_ALL
    }

    internal fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    internal companion object {
        fun fromPlayerRepeatMode(repeatMode: Int): RepeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> ONE
            Player.REPEAT_MODE_ALL -> ALL
            else -> OFF
        }
    }
}

data class QueueEntry(
    val queueEntryId: String,
    val track: Track,
)

data class PlaybackFailure(
    val code: Int,
    val codeName: String,
    val message: String,
)

data class PlaybackState(
    val connectionState: PlayerConnectionState = PlayerConnectionState.CONNECTING,
    val currentQueueEntryId: String? = null,
    val currentTrackId: String? = null,
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val isBuffering: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = C.INDEX_UNSET,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val error: PlaybackFailure? = null,
)
