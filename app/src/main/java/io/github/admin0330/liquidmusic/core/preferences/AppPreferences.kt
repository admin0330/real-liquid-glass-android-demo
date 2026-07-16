package io.github.admin0330.liquidmusic.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "liquid_music_settings")

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

enum class DefaultRepeatMode { OFF, ALL, ONE }

data class UserPreferences(
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val defaultShuffle: Boolean = false,
    val defaultRepeat: DefaultRepeatMode = DefaultRepeatMode.OFF,
    val usbExclusiveRequested: Boolean = false,
    val updateManifestUrl: String = DEFAULT_UPDATE_MANIFEST_URL,
)

const val DEFAULT_UPDATE_MANIFEST_URL = "https://ym3861.cn/liquid-music-updates/latest.json"

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val appearance = stringPreferencesKey("appearance")
        val defaultShuffle = booleanPreferencesKey("default_shuffle")
        val defaultRepeat = stringPreferencesKey("default_repeat")
        val updateManifestUrl = stringPreferencesKey("update_manifest_url")
        val usbExclusiveRequested = booleanPreferencesKey("usb_exclusive_requested")
        val legacyMigrationComplete = booleanPreferencesKey("legacy_migration_complete_v1")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { values ->
        UserPreferences(
            appearance = values[Keys.appearance]
                ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
                ?: AppearanceMode.SYSTEM,
            defaultShuffle = values[Keys.defaultShuffle] ?: false,
            defaultRepeat = values[Keys.defaultRepeat]
                ?.let { runCatching { DefaultRepeatMode.valueOf(it) }.getOrNull() }
                ?: DefaultRepeatMode.OFF,
            usbExclusiveRequested = values[Keys.usbExclusiveRequested] ?: false,
            updateManifestUrl = values[Keys.updateManifestUrl]
                ?.takeIf { it.startsWith("https://") }
                ?: DEFAULT_UPDATE_MANIFEST_URL,
        )
    }

    val legacyMigrationComplete: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.legacyMigrationComplete] ?: false
    }

    suspend fun setAppearance(value: AppearanceMode) {
        context.dataStore.edit { it[Keys.appearance] = value.name }
    }

    suspend fun setDefaultShuffle(value: Boolean) {
        context.dataStore.edit { it[Keys.defaultShuffle] = value }
    }

    suspend fun setDefaultRepeat(value: DefaultRepeatMode) {
        context.dataStore.edit { it[Keys.defaultRepeat] = value.name }
    }

    suspend fun setUsbExclusiveRequested(value: Boolean) {
        context.dataStore.edit { it[Keys.usbExclusiveRequested] = value }
    }

    suspend fun setUpdateManifestUrl(value: String) {
        require(value.startsWith("https://")) { "Update manifest must use HTTPS" }
        context.dataStore.edit { it[Keys.updateManifestUrl] = value }
    }

    suspend fun markLegacyMigrationComplete() {
        context.dataStore.edit { it[Keys.legacyMigrationComplete] = true }
    }
}
