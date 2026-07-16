package io.github.admin0330.liquidmusic.data.local.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.admin0330.liquidmusic.data.local.database.entity.LyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    fun observe(trackId: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun get(trackId: String): LyricsEntity?

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: String): Int
}
