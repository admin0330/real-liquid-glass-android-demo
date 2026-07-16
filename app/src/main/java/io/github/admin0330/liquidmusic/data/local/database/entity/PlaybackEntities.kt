package io.github.admin0330.liquidmusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["favoritedAtMs"])],
)
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val favoritedAtMs: Long,
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["playedAtMs"]),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val trackId: String,
    val playedAtMs: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
)
