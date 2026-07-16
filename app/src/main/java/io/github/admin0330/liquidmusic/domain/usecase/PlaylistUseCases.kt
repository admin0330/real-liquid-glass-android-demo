package io.github.admin0330.liquidmusic.domain.usecase

import io.github.admin0330.liquidmusic.domain.model.Playlist
import io.github.admin0330.liquidmusic.domain.model.PlaylistDetail
import io.github.admin0330.liquidmusic.domain.model.PlaylistSortOrder
import io.github.admin0330.liquidmusic.domain.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlaylistsUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(
        sortOrder: PlaylistSortOrder = PlaylistSortOrder.RECENTLY_UPDATED,
    ): Flow<List<Playlist>> = repository.observePlaylists(sortOrder)
}

class ObservePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(playlistId: String): Flow<PlaylistDetail?> = repository.observePlaylist(playlistId)
}

class CreatePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(name: String, description: String? = null): String =
        repository.createPlaylist(name, description)
}

class UpdatePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String, name: String, description: String?): Boolean =
        repository.updatePlaylist(playlistId, name, description)
}

class DeletePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String): Boolean = repository.deletePlaylist(playlistId)
}

class AddPlaylistTracksUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String, trackIds: List<String>): List<String> =
        repository.addTracks(playlistId, trackIds)
}

class RemovePlaylistEntryUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String, entryId: String): Boolean =
        repository.removeEntry(playlistId, entryId)
}

class ClearPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String): Boolean = repository.clearPlaylist(playlistId)
}

class ReorderPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String, orderedEntryIds: List<String>): Boolean =
        repository.reorderEntries(playlistId, orderedEntryIds)
}
