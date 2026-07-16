package io.github.admin0330.liquidmusic.domain.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val year: Int?,
    val trackCount: Int,
    val totalDurationMs: Long,
)

data class Artist(
    val id: String,
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val trackCount: Int,
    val totalDurationMs: Long,
)

enum class AlbumSortOrder {
    TITLE_ASCENDING,
    TITLE_DESCENDING,
    ARTIST_ASCENDING,
    NEWEST_ADDED,
}

enum class ArtistSortOrder {
    NAME_ASCENDING,
    NAME_DESCENDING,
    MOST_TRACKS,
}

data class LibrarySearchResult(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    companion object {
        val Empty = LibrarySearchResult(
            tracks = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
        )
    }
}

sealed interface LibraryScanResult {
    data class Success(
        val discoveredCount: Int,
        val removedCount: Int,
    ) : LibraryScanResult

    data object PermissionRequired : LibraryScanResult

    data class Failed(val reason: LibraryScanFailure) : LibraryScanResult
}

enum class LibraryScanFailure {
    MEDIA_PROVIDER_UNAVAILABLE,
    QUERY_FAILED,
}
