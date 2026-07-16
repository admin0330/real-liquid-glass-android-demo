package io.github.admin0330.liquidmusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntryEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistSummaryRow(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUri: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.*, CAST(COUNT(e.entryId) AS INTEGER) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_entries e ON e.playlistId = p.id
        GROUP BY p.id
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query(
        """
        SELECT p.*, CAST(COUNT(e.entryId) AS INTEGER) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_entries e ON e.playlistId = p.id
        WHERE p.id = :playlistId
        GROUP BY p.id
        LIMIT 1
        """,
    )
    fun observeSummary(playlistId: String): Flow<PlaylistSummaryRow?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC, addedAtMs ASC, entryId ASC")
    fun observeEntries(playlistId: String): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries ORDER BY playlistId ASC, position ASC, addedAtMs ASC, entryId ASC")
    fun observeAllEntries(): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC, addedAtMs ASC, entryId ASC")
    suspend fun getEntries(playlistId: String): List<PlaylistEntryEntity>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: String): Int

    @Query(
        """
        SELECT p.*, CAST(COUNT(e.entryId) AS INTEGER) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_entries e ON e.playlistId = p.id
        WHERE p.name LIKE :pattern ESCAPE '|' COLLATE NOCASE
           OR p.description LIKE :pattern ESCAPE '|' COLLATE NOCASE
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun search(pattern: String, limit: Int): Flow<List<PlaylistSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun updateMetadata(
        playlistId: String,
        name: String,
        description: String?,
        updatedAtMs: Long,
    ): Int

    @Query("UPDATE playlists SET updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun touch(playlistId: String, updatedAtMs: Long): Int

    @Query("UPDATE playlist_entries SET position = :position WHERE entryId = :entryId AND playlistId = :playlistId")
    suspend fun updatePosition(playlistId: String, entryId: String, position: Int): Int

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND entryId = :entryId")
    suspend fun deleteEntry(playlistId: String, entryId: String): Int

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun deleteEntries(playlistId: String): Int

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String): Int
}
