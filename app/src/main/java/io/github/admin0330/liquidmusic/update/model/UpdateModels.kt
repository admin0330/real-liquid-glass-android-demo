package io.github.admin0330.liquidmusic.update.model

import android.content.Intent
import java.io.File

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val changelog: String,
)

enum class UpdateStage {
    CHECK,
    DOWNLOAD,
    VERIFY,
    INSTALL,
}

enum class UpdateFailureCode {
    INVALID_URL,
    INSECURE_REDIRECT,
    NETWORK,
    HTTP_STATUS,
    MANIFEST_TOO_LARGE,
    INVALID_MANIFEST,
    CONTENT_LENGTH_MISMATCH,
    SIZE_MISMATCH,
    SHA256_MISMATCH,
    PACKAGE_MISMATCH,
    SIGNATURE_MISMATCH,
    VERSION_MISMATCH,
    INVALID_APK,
    STORAGE,
    INSTALL_INTENT,
}

data class UpdateFailure(
    val code: UpdateFailureCode,
    val stage: UpdateStage,
)

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class Available(
        val manifest: UpdateManifest,
        val cached: Boolean,
    ) : UpdateState

    data class UpToDate(
        val installedVersionCode: Long,
        val manifest: UpdateManifest,
    ) : UpdateState

    data class Downloading(
        val manifest: UpdateManifest,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes <= 0L) 0f
            else (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    data class Verifying(val manifest: UpdateManifest) : UpdateState

    data class ReadyToInstall(
        val manifest: UpdateManifest,
        val apk: File,
        val reusedCache: Boolean,
    ) : UpdateState

    data class Cancelled(val manifest: UpdateManifest) : UpdateState

    data class Failed(val failure: UpdateFailure) : UpdateState
}

sealed interface UpdateCheckResult {
    data class Available(
        val manifest: UpdateManifest,
        val cached: Boolean,
    ) : UpdateCheckResult

    data class UpToDate(
        val installedVersionCode: Long,
        val manifest: UpdateManifest,
    ) : UpdateCheckResult

    data class Failure(val failure: UpdateFailure) : UpdateCheckResult
}

sealed interface UpdateDownloadResult {
    data class Ready(
        val manifest: UpdateManifest,
        val apk: File,
        val reusedCache: Boolean,
    ) : UpdateDownloadResult

    data class Cancelled(val manifest: UpdateManifest) : UpdateDownloadResult

    data class Failure(val failure: UpdateFailure) : UpdateDownloadResult
}

sealed interface UpdateInstallResult {
    data class Ready(val intent: Intent) : UpdateInstallResult

    data class PermissionRequired(val settingsIntent: Intent) : UpdateInstallResult

    data class Failure(val failure: UpdateFailure) : UpdateInstallResult
}
