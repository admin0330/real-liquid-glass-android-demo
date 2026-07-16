package io.github.admin0330.liquidmusic.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.admin0330.liquidmusic.domain.model.PlaylistDetail
import io.github.admin0330.liquidmusic.domain.usecase.ObservePlaylistUseCase
import io.github.admin0330.liquidmusic.domain.usecase.RemovePlaylistEntryUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ReorderPlaylistUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePlaylist: ObservePlaylistUseCase,
    private val removeEntry: RemovePlaylistEntryUseCase,
    private val reorderPlaylist: ReorderPlaylistUseCase,
) : ViewModel() {
    private val playlistId: String = requireNotNull(savedStateHandle["playlistId"])
    val detail = observePlaylist(playlistId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun remove(entryId: String) {
        viewModelScope.launch {
            runCatching { removeEntry(playlistId, entryId) }
                .onSuccess { removed -> if (!removed) _messages.emit("无法移除这首歌曲") }
                .onFailure { _messages.emit("移除歌曲失败") }
        }
    }

    fun move(entryId: String, delta: Int) {
        val entries = detail.value?.entries ?: return
        val from = entries.indexOfFirst { it.entryId == entryId }
        val to = (from + delta).coerceIn(entries.indices)
        if (from < 0 || from == to) return
        val order = entries.map { it.entryId }.toMutableList()
        val moved = order.removeAt(from)
        order.add(to, moved)
        viewModelScope.launch {
            runCatching { reorderPlaylist(playlistId, order) }
                .onSuccess { reordered -> if (!reordered) _messages.emit("无法调整播放顺序") }
                .onFailure { _messages.emit("调整播放顺序失败") }
        }
    }
}
