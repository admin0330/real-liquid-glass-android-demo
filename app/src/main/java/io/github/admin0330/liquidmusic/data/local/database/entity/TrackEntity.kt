package io.github.admin0330.liquidmusic.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["legacyId"], unique = true),
        Index(value = ["mediaStoreId"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["dateAddedEpochSeconds"]),
        Index(value = ["source", "lastScanToken"]),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val mediaStoreId: Long?,
    val volumeName: String?,
    val contentUri: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val artistId: Long?,
    val album: String,
    val albumId: Long?,
    val albumArtist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val relativePath: String?,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val artworkUri: String?,
    val legacyId: String?,
    /** MEDIA_STORE or LEGACY. Kept as text so a future source can be added without a DB converter migration. */
    val source: String,
    /** A successful scan marks every MediaStore row before stale rows are removed. */
    val lastScanToken: String?,
)

object TrackSources {
    const val MEDIA_STORE = "MEDIA_STORE"
    const val LEGACY = "LEGACY"
}
