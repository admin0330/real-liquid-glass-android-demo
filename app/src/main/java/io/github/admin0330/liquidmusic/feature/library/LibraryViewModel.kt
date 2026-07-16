package io.github.admin0330.liquidmusic.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.admin0330.liquidmusic.domain.usecase.AddPlaylistTracksUseCase
import io.github.admin0330.liquidmusic.domain.usecase.CreatePlaylistUseCase
import io.github.admin0330.liquidmusic.domain.usecase.DeletePlaylistUseCase
import io.github.admin0330.liquidmusic.domain.usecase.UpdatePlaylistUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ToggleFavoriteUseCase
import io.github.admin0330.liquidmusic.domain.model.Track
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryActionState(
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val createPlaylist: CreatePlaylistUseCase,
    private val updatePlaylist: UpdatePlaylistUseCase,
    private val deletePlaylist: DeletePlaylistUseCase,
    private val addPlaylistTracks: AddPlaylistTracksUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {
    private val _actionState = MutableStateFlow(LibraryActionState())
    val actionState = _actionState.asStateFlow()

    fun create(name: String) = perform("已创建播放列表") {
        createPlaylist(name)
    }

    fun rename(id: String, name: String, description: String?) = perform("已重命名播放列表") {
        check(updatePlaylist(id, name, description))
    }

    fun delete(id: String) = perform("已删除播放列表") {
        check(deletePlaylist(id))
    }

    fun addTrack(playlistId: String, trackId: String) = perform("已添加到播放列表") {
        addPlaylistTracks(playlistId, listOf(trackId))
    }

    fun toggleFavorite(track: Track) = perform(if (track.isFavorite) "已取消收藏" else "已添加到喜爱") {
        check(toggleFavorite(track.id) != null)
    }

    fun consumeMessage() {
        _actionState.value = _actionState.value.copy(message = null)
    }

    private fun perform(successMessage: String, block: suspend () -> Unit) {
        if (_actionState.value.busy) return
        viewModelScope.launch {
            _actionState.value = LibraryActionState(busy = true)
            _actionState.value = runCatching {
                block()
                LibraryActionState(message = successMessage)
            }.getOrElse { error ->
                LibraryActionState(message = error.message ?: "操作失败")
            }
        }
    }
}
