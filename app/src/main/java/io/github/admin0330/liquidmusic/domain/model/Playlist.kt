package io.github.admin0330.liquidmusic.domain.model

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUri: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val trackCount: Int,
)

data class PlaylistEntry(
    val entryId: String,
    val playlistId: String,
    val track: Track,
    val position: Int,
    val addedAtMs: Long,
)

data class PlaylistDetail(
    val playlist: Playlist,
    val entries: List<PlaylistEntry>,
)

enum class PlaylistSortOrder {
    RECENTLY_UPDATED,
    RECENTLY_CREATED,
    NAME_ASCENDING,
    NAME_DESCENDING,
}
