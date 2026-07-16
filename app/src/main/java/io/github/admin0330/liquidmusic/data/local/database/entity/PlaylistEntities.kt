package io.github.admin0330.liquidmusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"]), Index(value = ["updatedAtMs"])],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val artworkUri: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["trackId"]),
    ],
)
data class PlaylistEntryEntity(
    /** Independent id deliberately permits the same track to appear more than once. */
    @PrimaryKey val entryId: String,
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAtMs: Long,
)
