package io.github.admin0330.liquidmusic.update.repository

import io.github.admin0330.liquidmusic.update.model.UpdateCheckResult
import io.github.admin0330.liquidmusic.update.model.UpdateDownloadResult
import io.github.admin0330.liquidmusic.update.model.UpdateInstallResult
import io.github.admin0330.liquidmusic.update.model.UpdateManifest
import io.github.admin0330.liquidmusic.update.model.UpdateState
import kotlinx.coroutines.flow.StateFlow

interface UpdateRepository {
    val state: StateFlow<UpdateState>

    suspend fun checkForUpdate(): UpdateCheckResult

    suspend fun download(manifest: UpdateManifest): UpdateDownloadResult

    fun cancelDownload()

    suspend fun prepareInstall(manifest: UpdateManifest): UpdateInstallResult

    fun reset()
}
