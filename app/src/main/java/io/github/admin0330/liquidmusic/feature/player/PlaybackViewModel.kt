package io.github.admin0330.liquidmusic.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.admin0330.liquidmusic.core.lyrics.ParsedLyrics
import io.github.admin0330.liquidmusic.core.preferences.AppPreferences
import io.github.admin0330.liquidmusic.core.preferences.DefaultRepeatMode
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.repository.LyricsRepository
import io.github.admin0330.liquidmusic.player.PlaybackState
import io.github.admin0330.liquidmusic.player.PlayerConnection
import io.github.admin0330.liquidmusic.player.RepeatMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val lyricsRepository: LyricsRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    val state = playerConnection.state

    private val _lyrics = MutableStateFlow<ParsedLyrics?>(null)
    val lyrics = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playerConnection.currentTrack.collectLatest { track ->
                _lyrics.value = null
                if (track != null) {
                    runCatching { lyricsRepository.discoverSidecar(track) }
                        .getOrNull()
                        ?.let { _lyrics.value = it }
                    lyricsRepository.observeLyrics(track.id).collect { parsed ->
                        if (parsed != null) _lyrics.value = parsed
                    }
                }
            }
        }
    }

    fun play(track: Track, queue: List<Track>) {
        viewModelScope.launch {
            val actualQueue = queue.ifEmpty { listOf(track) }
            val index = actualQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val wasEmpty = playerConnection.state.value.queue.isEmpty()
            if (playerConnection.playQueue(actualQueue, index) && wasEmpty) {
                val defaults = preferences.preferences.first()
                playerConnection.setShuffleEnabled(defaults.defaultShuffle)
                playerConnection.setRepeatMode(
                    when (defaults.defaultRepeat) {
                        DefaultRepeatMode.OFF -> RepeatMode.OFF
                        DefaultRepeatMode.ALL -> RepeatMode.ALL
                        DefaultRepeatMode.ONE -> RepeatMode.ONE
                    },
                )
            }
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()
    fun next() = playerConnection.next()
    fun previous() = playerConnection.previous()
    fun seekTo(positionMs: Long) = playerConnection.seekTo(positionMs)
    fun toggleShuffle() = playerConnection.toggleShuffle()
    fun cycleRepeat() = playerConnection.cycleRepeatMode()
    fun removeQueueEntry(entryId: String) = playerConnection.removeQueueEntry(entryId)
    fun moveQueueEntry(entryId: String, toIndex: Int) = playerConnection.moveQueueEntry(entryId, toIndex)
    fun playQueueEntry(entryId: String) = playerConnection.playQueueEntry(entryId)
    fun clearError() = playerConnection.clearError()

    fun attachLyrics(uri: Uri) {
        val trackId = state.value.currentTrackId ?: return
        viewModelScope.launch {
            runCatching { lyricsRepository.attach(trackId, uri) }
                .onSuccess { _lyrics.value = it }
        }
    }
}

data class PlaybackActions(
    val togglePlayPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val attachLyrics: (Uri) -> Unit,
    val playQueueEntry: (String) -> Unit,
    val removeQueueEntry: (String) -> Unit,
    val moveQueueEntry: (String, Int) -> Unit,
    val clearError: () -> Unit,
)
