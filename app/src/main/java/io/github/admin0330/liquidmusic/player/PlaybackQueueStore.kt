package io.github.admin0330.liquidmusic.player

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredQueueEntry(
    val queueEntryId: String,
    val trackId: String,
)

internal data class StoredQueue(
    val entries: List<StoredQueueEntry>,
    val currentQueueEntryId: String?,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
)

@Singleton
internal class PlaybackQueueStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun read(): StoredQueue? {
        val encoded = preferences.getString(KEY_QUEUE, null) ?: return null
        return runCatching { decode(encoded) }
            .getOrElse {
                preferences.edit { remove(KEY_QUEUE) }
                null
            }
    }

    fun write(queue: StoredQueue?) {
        if (queue == null || queue.entries.isEmpty()) {
            preferences.edit { remove(KEY_QUEUE) }
            return
        }
        preferences.edit { putString(KEY_QUEUE, encode(queue)) }
    }

    private fun encode(queue: StoredQueue): String {
        val entries = JSONArray()
        queue.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put(JSON_ENTRY_ID, entry.queueEntryId)
                    .put(JSON_TRACK_ID, entry.trackId),
            )
        }
        return JSONObject()
            .put(JSON_VERSION, FORMAT_VERSION)
            .put(JSON_ENTRIES, entries)
            .put(JSON_CURRENT_ENTRY_ID, queue.currentQueueEntryId ?: JSONObject.NULL)
            .put(JSON_POSITION_MS, queue.positionMs.coerceAtLeast(0))
            .put(JSON_SHUFFLE, queue.shuffleEnabled)
            .put(JSON_REPEAT, queue.repeatMode.name)
            .toString()
    }

    private fun decode(encoded: String): StoredQueue? {
        val root = JSONObject(encoded)
        if (root.optInt(JSON_VERSION, 0) != FORMAT_VERSION) return null
        val encodedEntries = root.getJSONArray(JSON_ENTRIES)
        val entries = buildList {
            val seenEntryIds = hashSetOf<String>()
            val count = encodedEntries.length().coerceAtMost(MAX_STORED_QUEUE_SIZE)
            for (index in 0 until count) {
                val item = encodedEntries.optJSONObject(index) ?: continue
                val entryId = item.optString(JSON_ENTRY_ID).trim()
                val trackId = item.optString(JSON_TRACK_ID).trim()
                if (entryId.isEmpty() || trackId.isEmpty() || !seenEntryIds.add(entryId)) continue
                add(StoredQueueEntry(queueEntryId = entryId, trackId = trackId))
            }
        }
        if (entries.isEmpty()) return null

        val currentEntryId = if (root.isNull(JSON_CURRENT_ENTRY_ID)) {
            null
        } else {
            root.optString(JSON_CURRENT_ENTRY_ID).takeIf(String::isNotBlank)
        }
        val repeatMode = runCatching {
            RepeatMode.valueOf(root.optString(JSON_REPEAT, RepeatMode.OFF.name))
        }.getOrDefault(RepeatMode.OFF)

        return StoredQueue(
            entries = entries,
            currentQueueEntryId = currentEntryId,
            positionMs = root.optLong(JSON_POSITION_MS, 0).coerceAtLeast(0),
            shuffleEnabled = root.optBoolean(JSON_SHUFFLE, false),
            repeatMode = repeatMode,
        )
    }

    private companion object {
        const val PREFERENCES_FILE = "liquid_playback_queue"
        const val KEY_QUEUE = "queue_v1"
        const val FORMAT_VERSION = 1
        const val MAX_STORED_QUEUE_SIZE = 50_000
        const val JSON_VERSION = "version"
        const val JSON_ENTRIES = "entries"
        const val JSON_ENTRY_ID = "entryId"
        const val JSON_TRACK_ID = "trackId"
        const val JSON_CURRENT_ENTRY_ID = "currentEntryId"
        const val JSON_POSITION_MS = "positionMs"
        const val JSON_SHUFFLE = "shuffle"
        const val JSON_REPEAT = "repeat"
    }
}
