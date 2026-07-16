package io.github.admin0330.liquidmusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val rawText: String,
    val sourceUri: String?,
    val updatedAtMs: Long,
)
