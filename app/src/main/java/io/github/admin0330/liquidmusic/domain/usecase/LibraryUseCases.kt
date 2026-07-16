package io.github.admin0330.liquidmusic.domain.usecase

import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.AlbumSortOrder
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.ArtistSortOrder
import io.github.admin0330.liquidmusic.domain.model.LibraryScanResult
import io.github.admin0330.liquidmusic.domain.model.LibrarySearchResult
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.model.TrackSortOrder
import io.github.admin0330.liquidmusic.domain.repository.MusicLibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTracksUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(sortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASCENDING): Flow<List<Track>> =
        repository.observeTracks(sortOrder)
}

class ObserveAlbumsUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(sortOrder: AlbumSortOrder = AlbumSortOrder.TITLE_ASCENDING): Flow<List<Album>> =
        repository.observeAlbums(sortOrder)
}

class ObserveArtistsUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(sortOrder: ArtistSortOrder = ArtistSortOrder.NAME_ASCENDING): Flow<List<Artist>> =
        repository.observeArtists(sortOrder)
}

class ObserveAlbumTracksUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(albumId: String): Flow<List<Track>> = repository.observeAlbumTracks(albumId)
}

class ObserveArtistTracksUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(artistId: String): Flow<List<Track>> = repository.observeArtistTracks(artistId)
}

class ObserveFavoritesUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(sortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASCENDING): Flow<List<Track>> =
        repository.observeFavorites(sortOrder)
}

class ObserveRecentlyAddedUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(limit: Int): Flow<List<Track>> = repository.observeRecentlyAdded(limit.coerceAtLeast(0))
}

class ObserveRecentlyPlayedUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(limit: Int): Flow<List<Track>> = repository.observeRecentlyPlayed(limit.coerceAtLeast(0))
}

class SearchLibraryUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(query: String, limit: Int = 100): Flow<LibrarySearchResult> =
        repository.search(query.trim(), limit.coerceAtLeast(0))
}

class RefreshLibraryUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke(): LibraryScanResult = repository.refreshFromMediaStore()
}

class SetFavoriteUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke(trackId: String, favorite: Boolean): Boolean =
        repository.setFavorite(trackId, favorite)
}

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke(trackId: String): Boolean? = repository.toggleFavorite(trackId)
}
