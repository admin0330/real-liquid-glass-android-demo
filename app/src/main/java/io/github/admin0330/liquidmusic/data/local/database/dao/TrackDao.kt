package io.github.admin0330.liquidmusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class TrackLegacyIdRow(
    val id: String,
    val legacyId: String?,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC, artist COLLATE NOCASE ASC, id ASC")
    fun observeTitleAscending(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE DESC, artist COLLATE NOCASE ASC, id ASC")
    fun observeTitleDescending(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun observeArtistAscending(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY album COLLATE NOCASE ASC, discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun observeAlbumAscending(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY dateAddedEpochSeconds DESC, title COLLATE NOCASE ASC")
    fun observeNewestAdded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY dateAddedEpochSeconds ASC, title COLLATE NOCASE ASC")
    fun observeOldestAdded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY durationMs DESC, title COLLATE NOCASE ASC")
    fun observeLongestFirst(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    fun observeById(trackId: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getById(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:trackIds)")
    suspend fun getByIds(trackIds: List<String>): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE :pattern ESCAPE '|' COLLATE NOCASE
           OR artist LIKE :pattern ESCAPE '|' COLLATE NOCASE
           OR album LIKE :pattern ESCAPE '|' COLLATE NOCASE
           OR albumArtist LIKE :pattern ESCAPE '|' COLLATE NOCASE
           OR displayName LIKE :pattern ESCAPE '|' COLLATE NOCASE
        ORDER BY title COLLATE NOCASE ASC, artist COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun search(pattern: String, limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT id, legacyId FROM tracks WHERE id IN (:trackIds)")
    suspend fun getLegacyIds(trackIds: List<String>): List<TrackLegacyIdRow>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE source = :source AND (lastScanToken IS NULL OR lastScanToken != :scanToken)")
    suspend fun deleteMissingFromScan(source: String, scanToken: String): Int
}
