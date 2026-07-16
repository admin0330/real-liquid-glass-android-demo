package io.github.admin0330.liquidmusic.domain.repository

import io.github.admin0330.liquidmusic.domain.model.Playlist
import io.github.admin0330.liquidmusic.domain.model.PlaylistDetail
import io.github.admin0330.liquidmusic.domain.model.PlaylistSortOrder
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(
        sortOrder: PlaylistSortOrder = PlaylistSortOrder.RECENTLY_UPDATED,
    ): Flow<List<Playlist>>

    fun observePlaylist(playlistId: String): Flow<PlaylistDetail?>

    suspend fun createPlaylist(name: String, description: String? = null): String

    suspend fun updatePlaylist(playlistId: String, name: String, description: String?): Boolean

    suspend fun deletePlaylist(playlistId: String): Boolean

    /** Appends every id, including repeated ids, as an independent playlist entry. */
    suspend fun addTracks(playlistId: String, trackIds: List<String>): List<String>

    suspend fun removeEntry(playlistId: String, entryId: String): Boolean

    suspend fun clearPlaylist(playlistId: String): Boolean

    /** Replaces the order atomically. The list must contain every current entry exactly once. */
    suspend fun reorderEntries(playlistId: String, orderedEntryIds: List<String>): Boolean
}
