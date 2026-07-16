package io.github.admin0330.liquidmusic.domain.model

/** A playable audio item whose source is local to the device. */
data class Track(
    val id: String,
    val mediaStoreId: Long?,
    val contentUri: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val artistId: Long?,
    val album: String,
    val albumId: Long?,
    val albumArtist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val relativePath: String?,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val artworkUri: String?,
    val legacyId: String?,
    val isFavorite: Boolean,
    val favoriteAddedAtMs: Long?,
    val lastPlayedAtMs: Long?,
    val playCount: Int,
)

enum class TrackSortOrder {
    TITLE_ASCENDING,
    TITLE_DESCENDING,
    ARTIST_ASCENDING,
    ALBUM_ASCENDING,
    NEWEST_ADDED,
    OLDEST_ADDED,
    LONGEST_FIRST,
}
