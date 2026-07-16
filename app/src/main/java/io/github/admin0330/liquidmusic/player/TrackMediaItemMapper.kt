package io.github.admin0330.liquidmusic.player

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.admin0330.liquidmusic.domain.model.Track
import java.io.File
import java.util.Locale
import java.util.UUID

internal object TrackMediaItemMapper {
    private const val EXTRAS_VERSION = 1
    private const val KEY_VERSION = "liquid.version"
    private const val KEY_TRACK_ID = "liquid.track.id"
    private const val KEY_MEDIA_STORE_ID = "liquid.track.media_store_id"
    private const val KEY_CONTENT_URI = "liquid.track.content_uri"
    private const val KEY_DISPLAY_NAME = "liquid.track.display_name"
    private const val KEY_TITLE = "liquid.track.title"
    private const val KEY_ARTIST = "liquid.track.artist"
    private const val KEY_ARTIST_ID = "liquid.track.artist_id"
    private const val KEY_ALBUM = "liquid.track.album"
    private const val KEY_ALBUM_ID = "liquid.track.album_id"
    private const val KEY_ALBUM_ARTIST = "liquid.track.album_artist"
    private const val KEY_DURATION_MS = "liquid.track.duration_ms"
    private const val KEY_SIZE_BYTES = "liquid.track.size_bytes"
    private const val KEY_MIME_TYPE = "liquid.track.mime_type"
    private const val KEY_RELATIVE_PATH = "liquid.track.relative_path"
    private const val KEY_DATE_ADDED = "liquid.track.date_added"
    private const val KEY_DATE_MODIFIED = "liquid.track.date_modified"
    private const val KEY_TRACK_NUMBER = "liquid.track.track_number"
    private const val KEY_DISC_NUMBER = "liquid.track.disc_number"
    private const val KEY_YEAR = "liquid.track.year"
    private const val KEY_ARTWORK_URI = "liquid.track.artwork_uri"
    private const val KEY_LEGACY_ID = "liquid.track.legacy_id"
    private const val KEY_IS_FAVORITE = "liquid.track.is_favorite"
    private const val KEY_FAVORITE_ADDED_AT = "liquid.track.favorite_added_at"
    private const val KEY_LAST_PLAYED_AT = "liquid.track.last_played_at"
    private const val KEY_PLAY_COUNT = "liquid.track.play_count"

    fun createQueueEntry(track: Track): QueueEntry = QueueEntry(
        queueEntryId = UUID.randomUUID().toString(),
        track = track,
    )

    fun toMediaItem(entry: QueueEntry): MediaItem = toMediaItem(
        track = entry.track,
        queueEntryId = entry.queueEntryId,
    )

    fun toMediaItem(track: Track, queueEntryId: String): MediaItem {
        require(queueEntryId.isNotBlank()) { "Queue entry ID must not be blank" }
        val sourceUri = requireLocalUri(track.contentUri)
        val extras = track.toExtras()
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setDisplayTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setDurationMs(track.durationMs.coerceAtLeast(0))
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setRecordingYear(track.year)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setExtras(extras)

        track.albumArtist
            ?.takeIf(String::isNotBlank)
            ?.let(metadataBuilder::setAlbumArtist)
        localUriOrNull(track.artworkUri)?.let(metadataBuilder::setArtworkUri)

        return MediaItem.Builder()
            .setMediaId(queueEntryId)
            .setUri(sourceUri)
            .apply {
                track.mimeType?.takeIf(String::isNotBlank)?.let(::setMimeType)
            }
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun toQueueEntry(mediaItem: MediaItem): QueueEntry? {
        val queueEntryId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return null
        val track = toTrack(mediaItem) ?: return null
        return QueueEntry(queueEntryId = queueEntryId, track = track)
    }

    fun trackId(mediaItem: MediaItem?): String? = mediaItem
        ?.mediaMetadata
        ?.extras
        ?.getString(KEY_TRACK_ID)
        ?.takeIf(String::isNotBlank)

    fun isPlayableLocal(mediaItem: MediaItem): Boolean {
        val uri = mediaItem.localConfiguration?.uri ?: return false
        return trackId(mediaItem) != null && uri.isLocalUri()
    }

    private fun toTrack(mediaItem: MediaItem): Track? {
        val extras = mediaItem.mediaMetadata.extras ?: return null
        if (extras.getInt(KEY_VERSION, 0) != EXTRAS_VERSION) return null
        val id = extras.getString(KEY_TRACK_ID)?.takeIf(String::isNotBlank) ?: return null
        val contentUri = extras.getString(KEY_CONTENT_URI)?.takeIf(String::isNotBlank) ?: return null
        if (localUriOrNull(contentUri) == null) return null

        return Track(
            id = id,
            mediaStoreId = extras.getNullableLong(KEY_MEDIA_STORE_ID),
            contentUri = contentUri,
            displayName = extras.getString(KEY_DISPLAY_NAME).orEmpty(),
            title = extras.getString(KEY_TITLE).orEmpty(),
            artist = extras.getString(KEY_ARTIST).orEmpty(),
            artistId = extras.getNullableLong(KEY_ARTIST_ID),
            album = extras.getString(KEY_ALBUM).orEmpty(),
            albumId = extras.getNullableLong(KEY_ALBUM_ID),
            albumArtist = extras.getString(KEY_ALBUM_ARTIST),
            durationMs = extras.getLong(KEY_DURATION_MS, 0).coerceAtLeast(0),
            sizeBytes = extras.getLong(KEY_SIZE_BYTES, 0).coerceAtLeast(0),
            mimeType = extras.getString(KEY_MIME_TYPE),
            relativePath = extras.getString(KEY_RELATIVE_PATH),
            dateAddedEpochSeconds = extras.getLong(KEY_DATE_ADDED, 0).coerceAtLeast(0),
            dateModifiedEpochSeconds = extras.getLong(KEY_DATE_MODIFIED, 0).coerceAtLeast(0),
            trackNumber = extras.getNullableInt(KEY_TRACK_NUMBER),
            discNumber = extras.getNullableInt(KEY_DISC_NUMBER),
            year = extras.getNullableInt(KEY_YEAR),
            artworkUri = extras.getString(KEY_ARTWORK_URI),
            legacyId = extras.getString(KEY_LEGACY_ID),
            isFavorite = extras.getBoolean(KEY_IS_FAVORITE, false),
            favoriteAddedAtMs = extras.getNullableLong(KEY_FAVORITE_ADDED_AT),
            lastPlayedAtMs = extras.getNullableLong(KEY_LAST_PLAYED_AT),
            playCount = extras.getInt(KEY_PLAY_COUNT, 0).coerceAtLeast(0),
        )
    }

    private fun Track.toExtras(): Bundle = Bundle(32).apply {
        putInt(KEY_VERSION, EXTRAS_VERSION)
        putString(KEY_TRACK_ID, id)
        putNullableLong(KEY_MEDIA_STORE_ID, mediaStoreId)
        putString(KEY_CONTENT_URI, contentUri)
        putString(KEY_DISPLAY_NAME, displayName)
        putString(KEY_TITLE, title)
        putString(KEY_ARTIST, artist)
        putNullableLong(KEY_ARTIST_ID, artistId)
        putString(KEY_ALBUM, album)
        putNullableLong(KEY_ALBUM_ID, albumId)
        putString(KEY_ALBUM_ARTIST, albumArtist)
        putLong(KEY_DURATION_MS, durationMs.coerceAtLeast(0))
        putLong(KEY_SIZE_BYTES, sizeBytes.coerceAtLeast(0))
        putString(KEY_MIME_TYPE, mimeType)
        putString(KEY_RELATIVE_PATH, relativePath)
        putLong(KEY_DATE_ADDED, dateAddedEpochSeconds.coerceAtLeast(0))
        putLong(KEY_DATE_MODIFIED, dateModifiedEpochSeconds.coerceAtLeast(0))
        putNullableInt(KEY_TRACK_NUMBER, trackNumber)
        putNullableInt(KEY_DISC_NUMBER, discNumber)
        putNullableInt(KEY_YEAR, year)
        putString(KEY_ARTWORK_URI, artworkUri)
        putString(KEY_LEGACY_ID, legacyId)
        putBoolean(KEY_IS_FAVORITE, isFavorite)
        putNullableLong(KEY_FAVORITE_ADDED_AT, favoriteAddedAtMs)
        putNullableLong(KEY_LAST_PLAYED_AT, lastPlayedAtMs)
        putInt(KEY_PLAY_COUNT, playCount.coerceAtLeast(0))
    }

    private fun requireLocalUri(rawUri: String): Uri = localUriOrNull(rawUri)
        ?: throw IllegalArgumentException("Only local audio sources can be played")

    private fun localUriOrNull(rawUri: String?): Uri? {
        val value = rawUri?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val parsed = value.toUri()
        if (parsed.scheme.isNullOrBlank()) return Uri.fromFile(File(value))
        return parsed.takeIf { it.isLocalUri() }
    }

    private fun Uri.isLocalUri(): Boolean = when (scheme?.lowercase(Locale.ROOT)) {
        null, "", "content", "file", "android.resource", "asset" -> true
        else -> false
    }

    private fun Bundle.putNullableLong(key: String, value: Long?) {
        value?.let { putLong(key, it) }
    }

    private fun Bundle.putNullableInt(key: String, value: Int?) {
        value?.let { putInt(key, it) }
    }

    private fun Bundle.getNullableLong(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun Bundle.getNullableInt(key: String): Int? =
        if (containsKey(key)) getInt(key) else null
}
