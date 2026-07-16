package io.github.admin0330.liquidmusic.update.repository

import io.github.admin0330.liquidmusic.core.preferences.AppPreferences
import io.github.admin0330.liquidmusic.update.security.SecureUpdateUri
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface UpdateManifestUrlProvider {
    suspend fun manifestUri(): URI
}

@Singleton
class PreferencesUpdateManifestUrlProvider @Inject constructor(
    private val preferences: AppPreferences,
) : UpdateManifestUrlProvider {
    override suspend fun manifestUri(): URI =
        SecureUpdateUri.parse(preferences.preferences.first().updateManifestUrl)
}
