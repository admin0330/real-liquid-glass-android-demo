package io.github.admin0330.liquidmusic.data.legacy

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.admin0330.liquidmusic.core.preferences.AppPreferences
import io.github.admin0330.liquidmusic.data.local.database.LiquidMusicDatabase
import io.github.admin0330.liquidmusic.data.local.database.entity.FavoriteEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.PlaylistEntryEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackEntity
import io.github.admin0330.liquidmusic.data.local.database.entity.TrackSources
import io.github.admin0330.liquidmusic.data.local.database.entity.LyricsEntity
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LegacyMigrationResult(
    val trackCount: Int,
    val playlistCount: Int,
    val skippedTrackCount: Int,
)

@Singleton
class LegacyDataMigrator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: LiquidMusicDatabase,
    private val preferences: AppPreferences,
) {
    suspend fun migrateIfNeeded(): LegacyMigrationResult? = withContext(Dispatchers.IO) {
        if (preferences.legacyMigrationComplete.first()) return@withContext null
        val legacyPreferences = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        val library = parseArray(legacyPreferences.stringValue(LOCAL_LIBRARY_KEY))
        val offline = parseArray(legacyPreferences.stringValue(OFFLINE_TRACKS_KEY))
        val playlistsJson = parseArray(legacyPreferences.stringValue(PLAYLISTS_KEY))

        val trackJsonByLegacyId = linkedMapOf<String, JSONObject>()
        var skipped = 0
        fun collect(value: JSONObject) {
            val legacyId = value.optString("id").trim()
            val path = value.optString("localPath").trim()
            if (legacyId.isEmpty() || path.isEmpty() || !File(path).isFile) {
                skipped += 1
                return
            }
            trackJsonByLegacyId[legacyId] = value
        }
        library.objects().forEach(::collect)
        offline.objects().forEach(::collect)
        playlistsJson.objects().forEach { playlist ->
            playlist.optJSONArray("tracks")?.objects()?.forEach(::collect)
        }

        val tracks = trackJsonByLegacyId.mapNotNull { (legacyId, value) ->
            value.toTrackEntity(legacyId)
        }
        val idByLegacyId = tracks.associate { requireNotNull(it.legacyId) to it.id }
        val playlistPayloads = playlistsJson.objects().mapNotNull { playlist ->
            playlist.toPlaylistPayload(idByLegacyId)
        }

        database.withTransaction {
            if (tracks.isNotEmpty()) database.trackDao().upsert(tracks)
            tracks.forEach { track ->
                val value = trackJsonByLegacyId[track.legacyId]
                if (value?.optBoolean("favorite", false) == true) {
                    database.favoriteDao().upsert(FavoriteEntity(track.id, track.dateAddedEpochSeconds * 1_000))
                }
                value?.optString("lyrics")?.trim()?.takeIf(String::isNotEmpty)?.let { rawLyrics ->
                    database.lyricsDao().upsert(
                        LyricsEntity(
                            trackId = track.id,
                            rawText = rawLyrics,
                            sourceUri = null,
                            updatedAtMs = track.dateModifiedEpochSeconds * 1_000,
                        ),
                    )
                }
            }
            playlistPayloads.forEach { payload ->
                val existing = database.playlistDao().getPlaylist(payload.playlist.id)
                if (existing == null) {
                    database.playlistDao().insertPlaylist(payload.playlist)
                } else {
                    database.playlistDao().updateMetadata(
                        playlistId = payload.playlist.id,
                        name = payload.playlist.name,
                        description = payload.playlist.description,
                        updatedAtMs = payload.playlist.updatedAtMs,
                    )
                }
                database.playlistDao().deleteEntries(payload.playlist.id)
                if (payload.entries.isNotEmpty()) database.playlistDao().insertEntries(payload.entries)
            }
        }

        legacyPreferences.stringValue(UPDATE_MANIFEST_KEY)
            ?.trim()
            ?.takeIf { it.startsWith("https://") }
            ?.let { runCatching { preferences.setUpdateManifestUrl(it) } }
        removeObsoleteRemoteCredentials(legacyPreferences)
        preferences.markLegacyMigrationComplete()
        LegacyMigrationResult(
            trackCount = tracks.size,
            playlistCount = playlistPayloads.size,
            skippedTrackCount = skipped,
        )
    }

    private fun JSONObject.toTrackEntity(legacyId: String): TrackEntity? {
        val file = File(optString("localPath"))
        if (!file.isFile) return null
        val suffix = optString("suffix").ifBlank { file.extension }.lowercase()
        val cover = optString("localCoverPath").takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
        val modifiedSeconds = (file.lastModified() / 1_000).coerceAtLeast(0)
        val title = optString("title").trim().ifBlank { file.nameWithoutExtension }
        return TrackEntity(
            id = "legacy:$legacyId",
            mediaStoreId = null,
            volumeName = null,
            contentUri = Uri.fromFile(file).toString(),
            displayName = file.name,
            title = title,
            artist = optString("artist").trim(),
            artistId = null,
            album = optString("album").trim(),
            albumId = null,
            albumArtist = null,
            durationMs = optLong("durationMs", 0).coerceAtLeast(0),
            sizeBytes = file.length().coerceAtLeast(0),
            mimeType = suffix.toMimeType(),
            relativePath = null,
            dateAddedEpochSeconds = modifiedSeconds,
            dateModifiedEpochSeconds = modifiedSeconds,
            trackNumber = optNullableInt("trackNumber"),
            discNumber = null,
            year = null,
            artworkUri = cover?.let(Uri::fromFile)?.toString(),
            legacyId = legacyId,
            source = TrackSources.LEGACY,
            lastScanToken = null,
        )
    }

    private fun JSONObject.toPlaylistPayload(idByLegacyId: Map<String, String>): PlaylistPayload? {
        val legacyPlaylistId = optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val name = optString("name").trim().takeIf(String::isNotEmpty) ?: "导入的播放列表"
        val createdAt = legacyPlaylistId.substringAfterLast('-', "").toLongOrNull()
            ?.takeIf { it in 1_000_000_000_000L..Long.MAX_VALUE }
            ?: System.currentTimeMillis()
        val id = "legacy-playlist:$legacyPlaylistId"
        val entries = optJSONArray("tracks")?.objects().orEmpty().mapIndexedNotNull { index, trackJson ->
            val legacyTrackId = trackJson.optString("id")
            val trackId = idByLegacyId[legacyTrackId] ?: return@mapIndexedNotNull null
            PlaylistEntryEntity(
                entryId = UUID.nameUUIDFromBytes("$id:$index:$legacyTrackId".toByteArray(StandardCharsets.UTF_8)).toString(),
                playlistId = id,
                trackId = trackId,
                position = index,
                addedAtMs = createdAt + index,
            )
        }
        return PlaylistPayload(
            playlist = PlaylistEntity(
                id = id,
                name = name,
                description = "从 Liquid Music 2.x 导入",
                artworkUri = null,
                createdAtMs = createdAt,
                updatedAtMs = createdAt,
            ),
            entries = entries,
        )
    }

    private fun removeObsoleteRemoteCredentials(legacyPreferences: android.content.SharedPreferences) {
        legacyPreferences.edit {
            remove(SUBSONIC_SERVER_KEY)
            remove(SUBSONIC_USER_KEY)
            remove(SUBSONIC_SERVER_KEY.removePrefix(FLUTTER_PREFIX))
            remove(SUBSONIC_USER_KEY.removePrefix(FLUTTER_PREFIX))
        }
        context.getSharedPreferences("FlutterSecureStorage", Context.MODE_PRIVATE).edit {
            remove("subsonic_password")
            remove("flutter.subsonic_password")
        }
    }

    private data class PlaylistPayload(
        val playlist: PlaylistEntity,
        val entries: List<PlaylistEntryEntity>,
    )

    private companion object {
        const val LEGACY_PREFERENCES = "FlutterSharedPreferences"
        const val FLUTTER_PREFIX = "flutter."
        const val LOCAL_LIBRARY_KEY = "${FLUTTER_PREFIX}local_music_library_v2"
        const val OFFLINE_TRACKS_KEY = "${FLUTTER_PREFIX}offline_tracks_v2"
        const val PLAYLISTS_KEY = "${FLUTTER_PREFIX}personal_playlists_v2"
        const val UPDATE_MANIFEST_KEY = "${FLUTTER_PREFIX}update_manifest_url_v1"
        const val SUBSONIC_SERVER_KEY = "${FLUTTER_PREFIX}subsonic_server"
        const val SUBSONIC_USER_KEY = "${FLUTTER_PREFIX}subsonic_user"
    }
}

private fun android.content.SharedPreferences.stringValue(prefixedKey: String): String? =
    getString(prefixedKey, null) ?: getString(prefixedKey.removePrefix("flutter."), null)

private fun parseArray(raw: String?): JSONArray = if (raw.isNullOrBlank()) {
    JSONArray()
} else {
    runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
}

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

private fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key).takeIf { it > 0 }

private fun String.toMimeType(): String? = when (lowercase()) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "m4a", "aac" -> "audio/mp4"
    "ogg", "opus" -> "audio/ogg"
    "aif", "aiff" -> "audio/aiff"
    else -> null
}
