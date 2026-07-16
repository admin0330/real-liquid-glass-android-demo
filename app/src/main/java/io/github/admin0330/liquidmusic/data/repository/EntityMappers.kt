package io.github.admin0330.liquidmusic.data.repository

import io.github.admin0330.liquidmusic.data.local.database.dao.PlaybackStatsRow
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaylistSummaryRow
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.AlbumSortOrder
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.ArtistSortOrder
import io.github.admin0330.liquidmusic.domain.model.Playlist
import io.github.admin0330.liquidmusic.domain.model.Track
import java.util.Locale

internal fun TrackEntity.toDomain(
    favorite: FavoriteEntity?,
    stats: PlaybackStatsRow?,
): Track = Track(
    id = id,
    mediaStoreId = mediaStoreId,
    contentUri = contentUri,
    displayName = displayName,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    albumArtist = albumArtist,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    relativePath = relativePath,
    dateAddedEpochSeconds = dateAddedEpochSeconds,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    artworkUri = artworkUri,
    legacyId = legacyId,
    isFavorite = favorite != null,
    favoriteAddedAtMs = favorite?.favoritedAtMs,
    lastPlayedAtMs = stats?.lastPlayedAtMs,
    playCount = stats?.playCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0,
)

internal fun PlaylistSummaryRow.toDomain(artworkFallback: String? = null): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    artworkUri = artworkUri ?: artworkFallback,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    trackCount = trackCount,
)

internal fun List<Track>.toAlbums(sortOrder: AlbumSortOrder): List<Album> {
    val aggregates = groupBy(Track::albumGroupingKey).values.map { albumTracks ->
        val orderedTracks = albumTracks.sortedWith(
            compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.sortKey() },
        )
        val first = orderedTracks.first()
        AlbumAggregate(
            album = Album(
                id = first.albumGroupingKey(),
                title = first.album,
                artist = albumTracks.firstNotNullOfOrNull { track ->
                    track.albumArtist?.takeIf(String::isNotBlank)
                } ?: albumTracks.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty(),
                artworkUri = orderedTracks.firstNotNullOfOrNull(Track::artworkUri),
                year = albumTracks.mapNotNull(Track::year).maxOrNull(),
                trackCount = albumTracks.size,
                totalDurationMs = albumTracks.sumOf(Track::durationMs),
            ),
            newestAdded = albumTracks.maxOf(Track::dateAddedEpochSeconds),
        )
    }

    return when (sortOrder) {
        AlbumSortOrder.TITLE_ASCENDING -> aggregates.sortedBy { it.album.title.sortKey() }
        AlbumSortOrder.TITLE_DESCENDING -> aggregates.sortedByDescending { it.album.title.sortKey() }
        AlbumSortOrder.ARTIST_ASCENDING -> aggregates.sortedWith(
            compareBy<AlbumAggregate> { it.album.artist.sortKey() }
                .thenBy { it.album.title.sortKey() },
        )
        AlbumSortOrder.NEWEST_ADDED -> aggregates.sortedByDescending(AlbumAggregate::newestAdded)
    }.map(AlbumAggregate::album)
}

internal fun List<Track>.toArtists(sortOrder: ArtistSortOrder): List<Artist> {
    val artists = groupBy(Track::artistGroupingKey).values.map { artistTracks ->
        val first = artistTracks.first()
        Artist(
            id = first.artistGroupingKey(),
            name = artistTracks.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty(),
            artworkUri = artistTracks.firstNotNullOfOrNull(Track::artworkUri),
            albumCount = artistTracks.map(Track::albumGroupingKey).distinct().size,
            trackCount = artistTracks.size,
            totalDurationMs = artistTracks.sumOf(Track::durationMs),
        )
    }

    return when (sortOrder) {
        ArtistSortOrder.NAME_ASCENDING -> artists.sortedBy { it.name.sortKey() }
        ArtistSortOrder.NAME_DESCENDING -> artists.sortedByDescending { it.name.sortKey() }
        ArtistSortOrder.MOST_TRACKS -> artists.sortedWith(
            compareByDescending<Artist> { it.trackCount }.thenBy { it.name.sortKey() },
        )
    }
}

internal fun Track.albumGroupingKey(): String = albumId?.let {
    "${sourceNamespace()}:album:$it"
} ?: "album:${album.normalizedKey()}:${(albumArtist ?: artist).normalizedKey()}"

internal fun Track.artistGroupingKey(): String = artistId?.let {
    "${sourceNamespace()}:artist:$it"
} ?: "artist:${artist.normalizedKey()}"

private fun Track.sourceNamespace(): String = id.substringBeforeLast(':', id)

private fun String.normalizedKey(): String = trim().lowercase(Locale.ROOT)

private fun String.sortKey(): String = lowercase(Locale.ROOT)

private data class AlbumAggregate(
    val album: Album,
    val newestAdded: Long,
)
