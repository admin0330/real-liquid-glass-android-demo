package io.github.admin0330.liquidmusic.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedAudio(
    val id: String,
    val mediaStoreId: Long,
    val volumeName: String,
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
)

sealed interface MediaStoreScanOutcome {
    data class Success(val audio: List<ScannedAudio>) : MediaStoreScanOutcome

    data object PermissionRequired : MediaStoreScanOutcome

    data object ProviderUnavailable : MediaStoreScanOutcome

    data object QueryFailed : MediaStoreScanOutcome
}

@Singleton
class MediaStoreAudioScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun scan(): MediaStoreScanOutcome = withContext(Dispatchers.IO) {
        if (!hasReadAudioPermission()) return@withContext MediaStoreScanOutcome.PermissionRequired
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(BaseColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.Audio.AudioColumns.TITLE)
            add(MediaStore.Audio.AudioColumns.ARTIST)
            add(MediaStore.Audio.AudioColumns.ARTIST_ID)
            add(MediaStore.Audio.AudioColumns.ALBUM)
            add(MediaStore.Audio.AudioColumns.ALBUM_ID)
            add(MediaStore.Audio.AudioColumns.DURATION)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.Audio.AudioColumns.TRACK)
            add(MediaStore.Audio.AudioColumns.YEAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(ALBUM_ARTIST_COLUMN)
            }
        }.distinct().toTypedArray()

        val cursor = try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                null,
            )
        } catch (_: SecurityException) {
            return@withContext MediaStoreScanOutcome.PermissionRequired
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            return@withContext MediaStoreScanOutcome.QueryFailed
        } ?: return@withContext MediaStoreScanOutcome.ProviderUnavailable

        try {
            cursor.use { mediaCursor ->
                val rows = ArrayList<ScannedAudio>(mediaCursor.count.coerceAtLeast(0))
                while (mediaCursor.moveToNext()) {
                    val row = mediaCursor.toScannedAudio(collection) ?: continue
                    if (row.isSupportedAudio()) rows += row
                }
                MediaStoreScanOutcome.Success(rows)
            }
        } catch (_: SecurityException) {
            MediaStoreScanOutcome.PermissionRequired
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            MediaStoreScanOutcome.QueryFailed
        }
    }

    private fun Cursor.toScannedAudio(defaultCollection: Uri): ScannedAudio? {
        val mediaStoreId = longOrNull(BaseColumns._ID) ?: return null
        val displayName = stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME).orEmpty()
        val mimeType = stringOrNull(MediaStore.MediaColumns.MIME_TYPE)
            ?.trim()
            ?.lowercase(Locale.ROOT)
        val volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stringOrNull(MediaStore.MediaColumns.VOLUME_NAME)
                ?.takeIf(String::isNotBlank)
                ?: MediaStore.VOLUME_EXTERNAL
        } else {
            LEGACY_EXTERNAL_VOLUME
        }
        val itemCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.Audio.Media.getContentUri(volumeName) }.getOrDefault(defaultCollection)
        } else {
            defaultCollection
        }
        val rawTrackNumber = intOrNull(MediaStore.Audio.AudioColumns.TRACK)
        val parsedDiscNumber = rawTrackNumber
            ?.takeIf { it >= DISC_TRACK_MULTIPLIER }
            ?.div(DISC_TRACK_MULTIPLIER)
            ?.takeIf { it > 0 }
        val parsedTrackNumber = rawTrackNumber
            ?.let { if (it >= DISC_TRACK_MULTIPLIER) it % DISC_TRACK_MULTIPLIER else it }
            ?.takeIf { it > 0 }
        val albumId = longOrNull(MediaStore.Audio.AudioColumns.ALBUM_ID)?.takeIf { it >= 0 }

        return ScannedAudio(
            id = "mediastore:$volumeName:$mediaStoreId",
            mediaStoreId = mediaStoreId,
            volumeName = volumeName,
            contentUri = ContentUris.withAppendedId(itemCollection, mediaStoreId).toString(),
            displayName = displayName,
            title = normalizeMetadata(stringOrNull(MediaStore.Audio.AudioColumns.TITLE))
                .ifBlank { displayName.substringBeforeLast('.', displayName) },
            artist = normalizeMetadata(stringOrNull(MediaStore.Audio.AudioColumns.ARTIST)),
            artistId = longOrNull(MediaStore.Audio.AudioColumns.ARTIST_ID)?.takeIf { it >= 0 },
            album = normalizeMetadata(stringOrNull(MediaStore.Audio.AudioColumns.ALBUM)),
            albumId = albumId,
            albumArtist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                normalizeMetadata(stringOrNull(ALBUM_ARTIST_COLUMN)).takeIf(String::isNotBlank)
            } else {
                null
            },
            durationMs = longOrNull(MediaStore.Audio.AudioColumns.DURATION)?.coerceAtLeast(0) ?: 0,
            sizeBytes = longOrNull(MediaStore.MediaColumns.SIZE)?.coerceAtLeast(0) ?: 0,
            mimeType = mimeType,
            relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                null
            },
            dateAddedEpochSeconds = longOrNull(MediaStore.MediaColumns.DATE_ADDED)?.coerceAtLeast(0) ?: 0,
            dateModifiedEpochSeconds = longOrNull(MediaStore.MediaColumns.DATE_MODIFIED)?.coerceAtLeast(0) ?: 0,
            trackNumber = parsedTrackNumber,
            discNumber = parsedDiscNumber,
            year = intOrNull(MediaStore.Audio.AudioColumns.YEAR)?.takeIf { it > 0 },
            artworkUri = albumId?.let { "$ALBUM_ART_BASE_URI/$it" },
        )
    }

    private fun ScannedAudio.isSupportedAudio(): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return mimeType in SUPPORTED_MIME_TYPES || extension in SUPPORTED_EXTENSIONS
    }

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun Cursor.intOrNull(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    private fun normalizeMetadata(value: String?): String = value
        ?.trim()
        ?.takeUnless { it.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) }
        .orEmpty()

    private fun hasReadAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val LEGACY_EXTERNAL_VOLUME = "external"
        const val DISC_TRACK_MULTIPLIER = 1_000
        const val ALBUM_ARTIST_COLUMN = "album_artist"
        const val ALBUM_ART_BASE_URI = "content://media/external/audio/albumart"

        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "ogg")
        val SUPPORTED_MIME_TYPES = setOf(
            "audio/mpeg",
            "audio/mp3",
            "audio/flac",
            "audio/x-flac",
            "audio/wav",
            "audio/x-wav",
            "audio/x-pn-wav",
            "audio/vnd.wave",
            "audio/mp4",
            "audio/mp4a-latm",
            "audio/m4a",
            "audio/x-m4a",
            "audio/aac",
            "audio/ogg",
            "audio/vorbis",
            "application/ogg",
        )
    }
}
