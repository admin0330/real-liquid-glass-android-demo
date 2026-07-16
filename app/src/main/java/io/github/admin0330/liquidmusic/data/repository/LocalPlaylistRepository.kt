package io.github.admin0330.liquidmusic.data.repository

import androidx.room.withTransaction
import io.github.admin0330.liquidmusic.data.local.database.LiquidMusicDatabase
import io.github.admin0330.liquidmusic.data.local.database.dao.FavoriteDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaybackHistoryDao
import io.github.admin0330.liquidmusic.data.local.database.dao.PlaylistDao
import io.github.admin0330.liquidmusic.data.local.database.dao.TrackDao
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntryEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import io.github.admin0330.liquidmusic.domain.model.Playlist
import io.github.admin0330.liquidmusic.domain.model.PlaylistDetail
import io.github.admin0330.liquidmusic.domain.model.PlaylistEntry
import io.github.admin0330.liquidmusic.domain.model.PlaylistSortOrder
import io.github.admin0330.liquidmusic.domain.repository.PlaylistRepository
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class LocalPlaylistRepository @Inject constructor(
    private val database: LiquidMusicDatabase,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: PlaybackHistoryDao,
) : PlaylistRepository {
    override fun observePlaylists(sortOrder: PlaylistSortOrder): Flow<List<Playlist>> = combine(
        playlistDao.observeSummaries(),
        playlistDao.observeAllEntries(),
        trackDao.observeTitleAscending(),
    ) { summaries, entries, tracks ->
        val tracksById = tracks.associateBy(TrackEntity::id)
        val artworkByPlaylist = entries
            .groupBy(PlaylistEntryEntity::playlistId)
            .mapValues { (_, playlistEntries) ->
                playlistEntries.firstNotNullOfOrNull { tracksById[it.trackId]?.artworkUri }
            }
        val playlists = summaries.map { summary ->
            summary.toDomain(artworkByPlaylist[summary.id])
        }
        when (sortOrder) {
            PlaylistSortOrder.RECENTLY_UPDATED -> playlists.sortedByDescending(Playlist::updatedAtMs)
            PlaylistSortOrder.RECENTLY_CREATED -> playlists.sortedByDescending(Playlist::createdAtMs)
            PlaylistSortOrder.NAME_ASCENDING -> playlists.sortedBy { it.name.lowercase(Locale.ROOT) }
            PlaylistSortOrder.NAME_DESCENDING -> playlists.sortedByDescending { it.name.lowercase(Locale.ROOT) }
        }
    }

    override fun observePlaylist(playlistId: String): Flow<PlaylistDetail?> = combine(
        playlistDao.observeSummary(playlistId),
        playlistDao.observeEntries(playlistId),
        trackDao.observeTitleAscending(),
        favoriteDao.observeAll(),
        historyDao.observeStats(),
    ) { summary, entries, tracks, favorites, stats ->
        if (summary == null) return@combine null
        val tracksById = tracks.associateBy(TrackEntity::id)
        val favoritesByTrack = favorites.associateBy(FavoriteEntity::trackId)
        val statsByTrack = stats.associateBy { it.trackId }
        val domainEntries = entries.mapNotNull { entry ->
            val trackEntity = tracksById[entry.trackId] ?: return@mapNotNull null
            PlaylistEntry(
                entryId = entry.entryId,
                playlistId = entry.playlistId,
                track = trackEntity.toDomain(
                    favoritesByTrack[entry.trackId],
                    statsByTrack[entry.trackId],
                ),
                position = entry.position,
                addedAtMs = entry.addedAtMs,
            )
        }
        PlaylistDetail(
            playlist = summary.toDomain(domainEntries.firstOrNull()?.track?.artworkUri),
            entries = domainEntries,
        )
    }

    override suspend fun createPlaylist(name: String, description: String?): String {
        val normalizedName = name.validPlaylistName()
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = id,
                name = normalizedName,
                description = description.normalizedDescription(),
                artworkUri = null,
                createdAtMs = now,
                updatedAtMs = now,
            ),
        )
        return id
    }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String,
        description: String?,
    ): Boolean = playlistDao.updateMetadata(
        playlistId = playlistId,
        name = name.validPlaylistName(),
        description = description.normalizedDescription(),
        updatedAtMs = System.currentTimeMillis(),
    ) == 1

    override suspend fun deletePlaylist(playlistId: String): Boolean =
        playlistDao.deletePlaylist(playlistId) == 1

    override suspend fun addTracks(playlistId: String, trackIds: List<String>): List<String> {
        if (trackIds.isEmpty()) return emptyList()
        return database.withTransaction {
            requireNotNull(playlistDao.getPlaylist(playlistId)) { "Playlist does not exist" }
            val requestedIds = trackIds.distinct()
            val existingTrackIds = requestedIds
                .chunked(QUERY_CHUNK_SIZE)
                .flatMap { trackDao.getByIds(it) }
                .mapTo(mutableSetOf(), TrackEntity::id)
            require(existingTrackIds.containsAll(requestedIds)) { "One or more tracks do not exist" }

            val now = System.currentTimeMillis()
            val firstPosition = playlistDao.nextPosition(playlistId)
            require(firstPosition <= Int.MAX_VALUE - trackIds.size) { "Playlist has too many entries" }
            val entries = trackIds.mapIndexed { index, trackId ->
                PlaylistEntryEntity(
                    entryId = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    trackId = trackId,
                    position = firstPosition + index,
                    addedAtMs = now,
                )
            }
            playlistDao.insertEntries(entries)
            playlistDao.touch(playlistId, now)
            entries.map(PlaylistEntryEntity::entryId)
        }
    }

    override suspend fun removeEntry(playlistId: String, entryId: String): Boolean =
        database.withTransaction {
            val removed = playlistDao.deleteEntry(playlistId, entryId) == 1
            if (removed) playlistDao.touch(playlistId, System.currentTimeMillis())
            removed
        }

    override suspend fun clearPlaylist(playlistId: String): Boolean = database.withTransaction {
        if (playlistDao.getPlaylist(playlistId) == null) return@withTransaction false
        playlistDao.deleteEntries(playlistId)
        playlistDao.touch(playlistId, System.currentTimeMillis())
        true
    }

    override suspend fun reorderEntries(
        playlistId: String,
        orderedEntryIds: List<String>,
    ): Boolean = database.withTransaction {
        if (playlistDao.getPlaylist(playlistId) == null) return@withTransaction false
        val currentEntries = playlistDao.getEntries(playlistId)
        val currentIds = currentEntries.map(PlaylistEntryEntity::entryId)
        if (orderedEntryIds.size != currentIds.size ||
            orderedEntryIds.distinct().size != orderedEntryIds.size ||
            orderedEntryIds.toSet() != currentIds.toSet()
        ) {
            return@withTransaction false
        }
        if (orderedEntryIds == currentIds) return@withTransaction true

        orderedEntryIds.forEachIndexed { position, entryId ->
            check(playlistDao.updatePosition(playlistId, entryId, position) == 1)
        }
        playlistDao.touch(playlistId, System.currentTimeMillis())
        true
    }
}

private fun String.validPlaylistName(): String = trim().also {
    require(it.isNotEmpty()) { "Playlist name must not be blank" }
    require(it.length <= MAX_PLAYLIST_NAME_LENGTH) { "Playlist name is too long" }
}

private fun String?.normalizedDescription(): String? = this
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.also { require(it.length <= MAX_PLAYLIST_DESCRIPTION_LENGTH) { "Playlist description is too long" } }

private const val MAX_PLAYLIST_NAME_LENGTH = 200
private const val MAX_PLAYLIST_DESCRIPTION_LENGTH = 2_000
private const val QUERY_CHUNK_SIZE = 400
