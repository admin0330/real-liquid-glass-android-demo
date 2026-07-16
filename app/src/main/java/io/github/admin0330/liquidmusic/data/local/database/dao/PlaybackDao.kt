package io.github.admin0330.liquidmusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

data class PlaybackStatsRow(
    val trackId: String,
    val lastPlayedAtMs: Long,
    val playCount: Long,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY favoritedAtMs DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY favoritedAtMs DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE trackId = :trackId LIMIT 1")
    suspend fun get(trackId: String): FavoriteEntity?

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: String): Int
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAtMs DESC, historyId DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query(
        """
        SELECT trackId, MAX(playedAtMs) AS lastPlayedAtMs, COUNT(*) AS playCount
        FROM playback_history
        GROUP BY trackId
        """,
    )
    fun observeStats(): Flow<List<PlaybackStatsRow>>

    @Query(
        """
        SELECT trackId, MAX(playedAtMs) AS lastPlayedAtMs, COUNT(*) AS playCount
        FROM playback_history
        GROUP BY trackId
        """,
    )
    suspend fun getStats(): List<PlaybackStatsRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: PlaybackHistoryEntity): Long

    @Query(
        """
        DELETE FROM playback_history
        WHERE historyId NOT IN (
            SELECT historyId FROM playback_history
            ORDER BY playedAtMs DESC, historyId DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun trimToMostRecent(keep: Int): Int

    @Query("DELETE FROM playback_history")
    suspend fun clear()
}
