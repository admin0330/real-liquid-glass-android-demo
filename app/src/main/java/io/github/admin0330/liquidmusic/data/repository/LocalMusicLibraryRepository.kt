package io.github.admin0330.liquidmusic.data.repository

import androidx.room.withTransaction
import io.github.admin0330.liquidmusic.data.local.database.LiquidMusicDatabase
import io.github.admin0330.liquidmusic.data.local.database.dao.FavoriteDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaybackHistoryDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaylistDao
import io.github.admin0330.liquidmusic.data.local.database.dao.TrackDao
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackSources
import io.github.admin0330.liquidmusic.data.media.MediaStoreAudioScanner
import io.github.admin0330.liquidmusic.data.media.MediaStoreScanOutcome
import io.github.admin0330.liquidmusic.data.media.ScannedAudio
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.AlbumSortOrder
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.ArtistSortOrder
import io.github.admin0330.liquidmusic.domain.model.LibraryScanFailure
import io.github.admin0330.liquidmusic.domain.model.LibraryScanResult
import io.github.admin0330.liquidmusic.domain.model.LibrarySearchResult
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.model.TrackSortOrder
import io.github.admin0330.liquidmusic.domain.repository.MusicLibraryRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class LocalMusicLibraryRepository @Inject constructor(
    private val database: LiquidMusicDatabase,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: PlaybackHistoryDao,
    private val playlistDao: PlaylistDao,
    private val scanner: MediaStoreAudioScanner,
) : MusicLibraryRepository {
    override fun observeTracks(sortOrder: TrackSortOrder): Flow<List<Track>> =
        trackEntities(sortOrder).withPlaybackMetadata()

    override fun observeTrack(trackId: String): Flow<Track?> = combine(
        trackDao.observeById(trackId),
        favoriteDao.observeAll(),
        historyDao.observeStats(),
    ) { entity, favorites, stats ->
        entity?.toDomain(
            favorite = favorites.firstOrNull { it.trackId == entity.id },
            stats = stats.firstOrNull { it.trackId == entity.id },
        )
    }

    override fun observeAlbums(sortOrder: AlbumSortOrder): Flow<List<Album>> =
        observeTracks(TrackSortOrder.TITLE_ASCENDING).map { it.toAlbums(sortOrder) }

    override fun observeArtists(sortOrder: ArtistSortOrder): Flow<List<Artist>> =
        observeTracks(TrackSortOrder.ARTIST_ASCENDING).map { it.toArtists(sortOrder) }

    override fun observeAlbumTracks(albumId: String): Flow<List<Track>> =
        observeTracks(TrackSortOrder.ALBUM_ASCENDING).map { tracks ->
            tracks.filter { it.albumGroupingKey() == albumId }
        }

    override fun observeArtistTracks(artistId: String): Flow<List<Track>> =
        observeTracks(TrackSortOrder.ARTIST_ASCENDING).map { tracks ->
            tracks.filter { it.artistGroupingKey() == artistId }
        }

    override fun observeFavorites(sortOrder: TrackSortOrder): Flow<List<Track>> =
        observeTracks(sortOrder).map { tracks -> tracks.filter(Track::isFavorite) }

    override fun observeRecentlyAdded(limit: Int): Flow<List<Track>> =
        observeTracks(TrackSortOrder.NEWEST_ADDED).map { tracks -> tracks.take(limit.coerceAtLeast(0)) }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<Track>> =
        observeTracks(TrackSortOrder.TITLE_ASCENDING).map { tracks ->
            tracks.asSequence()
                .filter { it.lastPlayedAtMs != null }
                .sortedByDescending(Track::lastPlayedAtMs)
                .take(limit.coerceAtLeast(0))
                .toList()
        }

    override fun search(query: String, limit: Int): Flow<LibrarySearchResult> {
        val normalizedQuery = query.trim()
        val safeLimit = limit.coerceIn(0, MAX_SEARCH_RESULTS)
        if (normalizedQuery.isEmpty() || safeLimit == 0) return flowOf(LibrarySearchResult.Empty)

        val pattern = normalizedQuery.toLikePattern()
        return combine(
            trackDao.search(pattern, safeLimit),
            favoriteDao.observeAll(),
            historyDao.observeStats(),
            playlistDao.search(pattern, safeLimit),
        ) { entities, favorites, stats, playlists ->
            val favoritesByTrack = favorites.associateBy(FavoriteEntity::trackId)
            val statsByTrack = stats.associateBy { it.trackId }
            val tracks = entities.map { entity ->
                entity.toDomain(favoritesByTrack[entity.id], statsByTrack[entity.id])
            }
            LibrarySearchResult(
                tracks = tracks,
                albums = tracks.toAlbums(AlbumSortOrder.TITLE_ASCENDING),
                artists = tracks.toArtists(ArtistSortOrder.NAME_ASCENDING),
                playlists = playlists.map { it.toDomain() },
            )
        }
    }

    override suspend fun getTracks(trackIds: List<String>): List<Track> {
        if (trackIds.isEmpty()) return emptyList()
        val uniqueIds = trackIds.distinct()
        val entitiesById = uniqueIds
            .chunked(QUERY_CHUNK_SIZE)
            .flatMap { trackDao.getByIds(it) }
            .associateBy(TrackEntity::id)
        val favoritesByTrack = favoriteDao.getAll().associateBy(FavoriteEntity::trackId)
        val statsByTrack = historyDao.getStats().associateBy { it.trackId }
        return trackIds.mapNotNull { id ->
            entitiesById[id]?.toDomain(favoritesByTrack[id], statsByTrack[id])
        }
    }

    override suspend fun setFavorite(trackId: String, favorite: Boolean): Boolean =
        database.withTransaction {
            if (trackDao.getById(trackId) == null) return@withTransaction false
            if (favorite) {
                favoriteDao.upsert(FavoriteEntity(trackId, System.currentTimeMillis()))
            } else {
                favoriteDao.delete(trackId)
            }
            true
        }

    override suspend fun toggleFavorite(trackId: String): Boolean? = database.withTransaction {
        if (trackDao.getById(trackId) == null) return@withTransaction null
        val shouldFavorite = favoriteDao.get(trackId) == null
        if (shouldFavorite) {
            favoriteDao.upsert(FavoriteEntity(trackId, System.currentTimeMillis()))
        } else {
            favoriteDao.delete(trackId)
        }
        shouldFavorite
    }

    override suspend fun refreshFromMediaStore(): LibraryScanResult = when (val outcome = scanner.scan()) {
        MediaStoreScanOutcome.PermissionRequired -> LibraryScanResult.PermissionRequired
        MediaStoreScanOutcome.ProviderUnavailable -> LibraryScanResult.Failed(
            LibraryScanFailure.MEDIA_PROVIDER_UNAVAILABLE,
        )
        MediaStoreScanOutcome.QueryFailed -> LibraryScanResult.Failed(LibraryScanFailure.QUERY_FAILED)
        is MediaStoreScanOutcome.Success -> synchronizeScan(outcome.audio)
    }

    private suspend fun synchronizeScan(audio: List<ScannedAudio>): LibraryScanResult.Success {
        val scanToken = UUID.randomUUID().toString()
        val uniqueAudio = audio.distinctBy(ScannedAudio::id)
        val removed = database.withTransaction {
            uniqueAudio.chunked(UPSERT_CHUNK_SIZE).forEach { chunk ->
                val legacyIds = trackDao.getLegacyIds(chunk.map(ScannedAudio::id))
                    .associate { it.id to it.legacyId }
                trackDao.upsert(chunk.map { it.toEntity(scanToken, legacyIds[it.id]) })
            }
            trackDao.deleteMissingFromScan(TrackSources.MEDIA_STORE, scanToken)
        }
        return LibraryScanResult.Success(
            discoveredCount = uniqueAudio.size,
            removedCount = removed,
        )
    }

    private fun trackEntities(sortOrder: TrackSortOrder): Flow<List<TrackEntity>> = when (sortOrder) {
        TrackSortOrder.TITLE_ASCENDING -> trackDao.observeTitleAscending()
        TrackSortOrder.TITLE_DESCENDING -> trackDao.observeTitleDescending()
        TrackSortOrder.ARTIST_ASCENDING -> trackDao.observeArtistAscending()
        TrackSortOrder.ALBUM_ASCENDING -> trackDao.observeAlbumAscending()
        TrackSortOrder.NEWEST_ADDED -> trackDao.observeNewestAdded()
        TrackSortOrder.OLDEST_ADDED -> trackDao.observeOldestAdded()
        TrackSortOrder.LONGEST_FIRST -> trackDao.observeLongestFirst()
    }

    private fun Flow<List<TrackEntity>>.withPlaybackMetadata(): Flow<List<Track>> = combine(
        this,
        favoriteDao.observeAll(),
        historyDao.observeStats(),
    ) { entities, favorites, stats ->
        val favoritesByTrack = favorites.associateBy(FavoriteEntity::trackId)
        val statsByTrack = stats.associateBy { it.trackId }
        entities.map { entity ->
            entity.toDomain(favoritesByTrack[entity.id], statsByTrack[entity.id])
        }
    }

    private companion object {
        const val UPSERT_CHUNK_SIZE = 400
        const val QUERY_CHUNK_SIZE = 400
        const val MAX_SEARCH_RESULTS = 500
    }
}

private fun ScannedAudio.toEntity(scanToken: String, legacyId: String?): TrackEntity = TrackEntity(
    id = id,
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
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
    source = TrackSources.MEDIA_STORE,
    lastScanToken = scanToken,
)

private fun String.toLikePattern(): String = buildString(length + 2) {
    append('%')
    this@toLikePattern.forEach { character ->
        when (character) {
            '|', '%', '_' -> append('|')
        }
        append(character)
    }
    append('%')
}
