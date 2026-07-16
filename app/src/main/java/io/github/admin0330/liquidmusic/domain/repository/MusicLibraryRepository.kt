package io.github.admin0330.liquidmusic.domain.repository

import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.AlbumSortOrder
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.ArtistSortOrder
import io.github.admin0330.liquidmusic.domain.model.LibraryScanResult
import io.github.admin0330.liquidmusic.domain.model.LibrarySearchResult
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.model.TrackSortOrder
import kotlinx.coroutines.flow.Flow

interface MusicLibraryRepository {
    fun observeTracks(sortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASCENDING): Flow<List<Track>>

    fun observeTrack(trackId: String): Flow<Track?>

    fun observeAlbums(sortOrder: AlbumSortOrder = AlbumSortOrder.TITLE_ASCENDING): Flow<List<Album>>

    fun observeArtists(sortOrder: ArtistSortOrder = ArtistSortOrder.NAME_ASCENDING): Flow<List<Artist>>

    fun observeAlbumTracks(albumId: String): Flow<List<Track>>

    fun observeArtistTracks(artistId: String): Flow<List<Track>>

    fun observeFavorites(sortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASCENDING): Flow<List<Track>>

    fun observeRecentlyAdded(limit: Int): Flow<List<Track>>

    fun observeRecentlyPlayed(limit: Int): Flow<List<Track>>

    fun search(query: String, limit: Int = 100): Flow<LibrarySearchResult>

    suspend fun getTracks(trackIds: List<String>): List<Track>

    suspend fun setFavorite(trackId: String, favorite: Boolean): Boolean

    suspend fun toggleFavorite(trackId: String): Boolean?

    suspend fun refreshFromMediaStore(): LibraryScanResult
}
