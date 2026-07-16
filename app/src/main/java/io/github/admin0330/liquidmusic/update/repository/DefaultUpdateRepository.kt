package io.github.admin0330.liquidmusic.update.repository

import io.github.admin0330.liquidmusic.update.di.UpdateIoDispatcher
import io.github.admin0330.liquidmusic.update.manifest.InvalidUpdateManifestException
import io.github.admin0330.liquidmusic.update.manifest.UpdateManifestParser
import io.github.admin0330.liquidmusic.update.model.UpdateCheckResult
import io.github.admin0330.liquidmusic.update.model.UpdateDownloadResult
import io.github.admin0330.liquidmusic.update.model.UpdateFailure
import io.github.admin0330.liquidmusic.update.model.UpdateFailureCode
import io.github.admin0330.liquidmusic.update.model.UpdateInstallResult
import io.github.admin0330.liquidmusic.update.model.UpdateManifest
import io.github.admin0330.liquidmusic.update.model.UpdateStage
import io.github.admin0330.liquidmusic.update.model.UpdateState
import io.github.admin0330.liquidmusic.update.network.InsecureUpdateRedirectException
import io.github.admin0330.liquidmusic.update.network.UpdateCancellation
import io.github.admin0330.liquidmusic.update.network.UpdateCancelledException
import io.github.admin0330.liquidmusic.update.network.UpdateHttpClient
import io.github.admin0330.liquidmusic.update.network.UpdateHttpStatusException
import io.github.admin0330.liquidmusic.update.platform.ApkIdentityFailure
import io.github.admin0330.liquidmusic.update.platform.ApkIdentityVerifier
import io.github.admin0330.liquidmusic.update.platform.InstallIntentFactory
import io.github.admin0330.liquidmusic.update.platform.InstallLaunch
import io.github.admin0330.liquidmusic.update.platform.InstalledAppInfoProvider
import io.github.admin0330.liquidmusic.update.security.FileIntegrityFailure
import io.github.admin0330.liquidmusic.update.security.FileIntegrityVerifier
import io.github.admin0330.liquidmusic.update.security.InvalidUpdateUriException
import io.github.admin0330.liquidmusic.update.security.SecureUpdateUri
import io.github.admin0330.liquidmusic.update.storage.UpdateFileStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultUpdateRepository @Inject constructor(
    private val manifestUrlProvider: UpdateManifestUrlProvider,
    private val httpClient: UpdateHttpClient,
    private val fileStore: UpdateFileStore,
    private val installedAppInfo: InstalledAppInfoProvider,
    private val apkIdentityVerifier: ApkIdentityVerifier,
    private val installIntentFactory: InstallIntentFactory,
    @param:UpdateIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UpdateRepository {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    private val operationMutex = Mutex()
    private val activeDownload = AtomicReference<UpdateCancellation?>()

    override suspend fun checkForUpdate(): UpdateCheckResult = operationMutex.withLock {
        mutableState.value = UpdateState.Checking
        try {
            withContext(ioDispatcher) {
                val manifestUri = manifestUrlProvider.manifestUri()
                val manifest = fetchManifest(manifestUri)
                val installedVersionCode = try {
                    installedAppInfo.versionCode()
                } catch (error: Exception) {
                    throw UpdateOperationException(UpdateFailureCode.INVALID_APK, UpdateStage.CHECK, error)
                }
                if (manifest.versionCode <= installedVersionCode) {
                    val result = UpdateCheckResult.UpToDate(installedVersionCode, manifest)
                    mutableState.value = UpdateState.UpToDate(installedVersionCode, manifest)
                    result
                } else {
                    fileStore.ensureReady()
                    val cachedFile = fileStore.completeFile(manifest.versionCode)
                    val cached = cachedFile.isFile && verifyCandidate(manifest, cachedFile) == null
                    if (cachedFile.exists() && !cached) fileStore.delete(cachedFile)
                    val result = UpdateCheckResult.Available(manifest, cached)
                    mutableState.value = UpdateState.Available(manifest, cached)
                    result
                }
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = UpdateState.Idle
            throw cancelled
        } catch (error: Exception) {
            val failure = error.toFailure(UpdateStage.CHECK)
            mutableState.value = UpdateState.Failed(failure)
            UpdateCheckResult.Failure(failure)
        }
    }

    override suspend fun download(manifest: UpdateManifest): UpdateDownloadResult =
        operationMutex.withLock {
            withContext(ioDispatcher) {
                val normalized = try {
                    UpdateManifestParser.validate(manifest)
                } catch (_: InvalidUpdateManifestException) {
                    return@withContext downloadFailure(UpdateFailureCode.INVALID_MANIFEST)
                }
                val installedVersionCode = try {
                    installedAppInfo.versionCode()
                } catch (_: Exception) {
                    return@withContext downloadFailure(UpdateFailureCode.INVALID_APK, UpdateStage.VERIFY)
                }
                if (normalized.versionCode <= installedVersionCode) {
                    return@withContext downloadFailure(UpdateFailureCode.INVALID_MANIFEST)
                }

                val cancellation = UpdateCancellation()
                check(activeDownload.compareAndSet(null, cancellation)) { "An update download is already active" }
                val job = currentCoroutineContext()[Job]
                val cancellationRegistration = job?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) cancellation.cancel()
                }
                val partial = fileStore.partialFile(normalized.versionCode)
                try {
                    fileStore.ensureReady()
                    val complete = fileStore.completeFile(normalized.versionCode)
                    if (complete.isFile) {
                        mutableState.value = UpdateState.Verifying(normalized)
                        if (verifyCandidate(normalized, complete, cancellation) == null) {
                            return@withContext ready(normalized, complete, reusedCache = true)
                        }
                        fileStore.delete(complete)
                    }

                    fileStore.delete(partial)
                    if (partial.exists() || !fileStore.contains(partial)) {
                        return@withContext downloadFailure(UpdateFailureCode.STORAGE)
                    }
                    mutableState.value = UpdateState.Downloading(normalized, 0L, normalized.size)
                    downloadToPartial(normalized, partial, cancellation)
                    mutableState.value = UpdateState.Verifying(normalized)
                    val verificationFailure = verifyCandidate(normalized, partial, cancellation)
                    if (verificationFailure != null) {
                        fileStore.delete(partial)
                        return@withContext downloadFailure(verificationFailure, UpdateStage.VERIFY)
                    }
                    cancellation.throwIfCancelled()
                    fileStore.atomicCommit(partial, complete)
                    ready(normalized, complete, reusedCache = false)
                } catch (cancelled: CancellationException) {
                    fileStore.delete(partial)
                    mutableState.value = UpdateState.Cancelled(normalized)
                    throw cancelled
                } catch (_: UpdateCancelledException) {
                    fileStore.delete(partial)
                    mutableState.value = UpdateState.Cancelled(normalized)
                    UpdateDownloadResult.Cancelled(normalized)
                } catch (error: Exception) {
                    fileStore.delete(partial)
                    downloadFailure(error.toFailureCode(), error.toFailureStage())
                } finally {
                    cancellationRegistration?.dispose()
                    activeDownload.compareAndSet(cancellation, null)
                }
            }
        }

    override fun cancelDownload() {
        activeDownload.get()?.cancel()
    }

    override suspend fun prepareInstall(manifest: UpdateManifest): UpdateInstallResult =
        operationMutex.withLock {
            try {
                withContext(ioDispatcher) {
                    val normalized = UpdateManifestParser.validate(manifest)
                    fileStore.ensureReady()
                    val apk = fileStore.completeFile(normalized.versionCode)
                    if (!fileStore.contains(apk)) {
                        return@withContext installFailure(UpdateFailureCode.INVALID_APK)
                    }
                    mutableState.value = UpdateState.Verifying(normalized)
                    val verificationFailure = verifyCandidate(normalized, apk)
                    if (verificationFailure != null) {
                        fileStore.delete(apk)
                        return@withContext installFailure(verificationFailure, UpdateStage.VERIFY)
                    }
                    val launch = try {
                        installIntentFactory.create(apk)
                    } catch (error: Exception) {
                        throw UpdateOperationException(
                            UpdateFailureCode.INSTALL_INTENT,
                            UpdateStage.INSTALL,
                            error,
                        )
                    }
                    mutableState.value = UpdateState.ReadyToInstall(normalized, apk, reusedCache = true)
                    when (launch) {
                        is InstallLaunch.Installer -> UpdateInstallResult.Ready(launch.intent)
                        is InstallLaunch.PermissionSettings ->
                            UpdateInstallResult.PermissionRequired(launch.intent)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                installFailure(error.toFailureCode(), error.toFailureStage(UpdateStage.INSTALL))
            }
        }

    override fun reset() {
        if (activeDownload.get() == null) mutableState.value = UpdateState.Idle
    }

    private fun fetchManifest(uri: java.net.URI): UpdateManifest {
        val cancellation = UpdateCancellation()
        httpClient.open(uri, cancellation).use { response ->
            val declaredLength = response.contentLength
            if (declaredLength != null && declaredLength > MAX_MANIFEST_BYTES) {
                throw UpdateOperationException(UpdateFailureCode.MANIFEST_TOO_LARGE, UpdateStage.CHECK)
            }
            val output = ByteArrayOutputStream(
                (declaredLength ?: DEFAULT_MANIFEST_BUFFER.toLong())
                    .coerceAtMost(MAX_MANIFEST_BYTES.toLong())
                    .toInt(),
            )
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = response.body.read(buffer)
                if (count == -1) break
                total += count
                if (total > MAX_MANIFEST_BYTES) {
                    throw UpdateOperationException(UpdateFailureCode.MANIFEST_TOO_LARGE, UpdateStage.CHECK)
                }
                output.write(buffer, 0, count)
            }
            if (declaredLength != null && total != declaredLength) {
                throw UpdateOperationException(UpdateFailureCode.CONTENT_LENGTH_MISMATCH, UpdateStage.CHECK)
            }
            val text = try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray()))
                    .toString()
            } catch (_: Exception) {
                throw UpdateOperationException(UpdateFailureCode.INVALID_MANIFEST, UpdateStage.CHECK)
            }
            return try {
                UpdateManifestParser.parse(text, uri)
            } catch (_: InvalidUpdateManifestException) {
                throw UpdateOperationException(UpdateFailureCode.INVALID_MANIFEST, UpdateStage.CHECK)
            }
        }
    }

    private suspend fun downloadToPartial(
        manifest: UpdateManifest,
        partial: File,
        cancellation: UpdateCancellation,
    ) {
        val uri = try {
            SecureUpdateUri.parse(manifest.apkUrl)
        } catch (_: InvalidUpdateUriException) {
            throw UpdateOperationException(UpdateFailureCode.INVALID_URL, UpdateStage.DOWNLOAD)
        }
        val response = try {
            httpClient.open(uri, cancellation)
        } catch (error: Exception) {
            if (cancellation.isCancelled) throw UpdateCancelledException()
            throw error
        }
        response.use {
            if (it.contentLength != null && it.contentLength != manifest.size) {
                throw UpdateOperationException(
                    UpdateFailureCode.CONTENT_LENGTH_MISMATCH,
                    UpdateStage.DOWNLOAD,
                )
            }
            val output = try {
                FileOutputStream(partial, false)
            } catch (error: IOException) {
                throw UpdateOperationException(UpdateFailureCode.STORAGE, UpdateStage.DOWNLOAD, error)
            }
            output.use { fileOutput ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    cancellation.throwIfCancelled()
                    currentCoroutineContext().ensureActive()
                    val count = try {
                        it.body.read(buffer)
                    } catch (error: IOException) {
                        if (cancellation.isCancelled) throw UpdateCancelledException()
                        throw UpdateOperationException(UpdateFailureCode.NETWORK, UpdateStage.DOWNLOAD, error)
                    }
                    if (count == -1) break
                    if (total > manifest.size - count) {
                        throw UpdateOperationException(UpdateFailureCode.SIZE_MISMATCH, UpdateStage.DOWNLOAD)
                    }
                    try {
                        fileOutput.write(buffer, 0, count)
                    } catch (error: IOException) {
                        throw UpdateOperationException(UpdateFailureCode.STORAGE, UpdateStage.DOWNLOAD, error)
                    }
                    total += count
                    mutableState.value = UpdateState.Downloading(manifest, total, manifest.size)
                }
                if (total != manifest.size) {
                    throw UpdateOperationException(UpdateFailureCode.SIZE_MISMATCH, UpdateStage.DOWNLOAD)
                }
                try {
                    fileOutput.fd.sync()
                } catch (error: IOException) {
                    throw UpdateOperationException(UpdateFailureCode.STORAGE, UpdateStage.DOWNLOAD, error)
                }
            }
        }
    }

    private fun verifyCandidate(
        manifest: UpdateManifest,
        file: File,
        cancellation: UpdateCancellation = UpdateCancellation(),
    ): UpdateFailureCode? {
        when (FileIntegrityVerifier.verify(file, manifest.size, manifest.sha256, cancellation)) {
            FileIntegrityFailure.SIZE -> return UpdateFailureCode.SIZE_MISMATCH
            FileIntegrityFailure.SHA256 -> return UpdateFailureCode.SHA256_MISMATCH
            null -> Unit
        }
        val identityFailure = try {
            apkIdentityVerifier.verify(file, manifest.versionCode, manifest.versionName)
        } catch (_: Exception) {
            return UpdateFailureCode.INVALID_APK
        }
        return when (identityFailure) {
            ApkIdentityFailure.PACKAGE -> UpdateFailureCode.PACKAGE_MISMATCH
            ApkIdentityFailure.SIGNATURE -> UpdateFailureCode.SIGNATURE_MISMATCH
            ApkIdentityFailure.VERSION -> UpdateFailureCode.VERSION_MISMATCH
            ApkIdentityFailure.INVALID_APK -> UpdateFailureCode.INVALID_APK
            null -> null
        }
    }

    private fun ready(
        manifest: UpdateManifest,
        apk: File,
        reusedCache: Boolean,
    ): UpdateDownloadResult.Ready {
        mutableState.value = UpdateState.ReadyToInstall(manifest, apk, reusedCache)
        return UpdateDownloadResult.Ready(manifest, apk, reusedCache)
    }

    private fun downloadFailure(
        code: UpdateFailureCode,
        stage: UpdateStage = UpdateStage.DOWNLOAD,
    ): UpdateDownloadResult.Failure {
        val failure = UpdateFailure(code, stage)
        mutableState.value = UpdateState.Failed(failure)
        return UpdateDownloadResult.Failure(failure)
    }

    private fun installFailure(
        code: UpdateFailureCode,
        stage: UpdateStage = UpdateStage.INSTALL,
    ): UpdateInstallResult.Failure {
        val failure = UpdateFailure(code, stage)
        mutableState.value = UpdateState.Failed(failure)
        return UpdateInstallResult.Failure(failure)
    }

    private fun Throwable.toFailure(defaultStage: UpdateStage): UpdateFailure =
        UpdateFailure(toFailureCode(), toFailureStage(defaultStage))

    private fun Throwable.toFailureStage(default: UpdateStage = UpdateStage.DOWNLOAD): UpdateStage =
        (this as? UpdateOperationException)?.stage ?: default

    private fun Throwable.toFailureCode(): UpdateFailureCode = when (this) {
        is UpdateOperationException -> code
        is InvalidUpdateUriException -> UpdateFailureCode.INVALID_URL
        is InsecureUpdateRedirectException -> UpdateFailureCode.INSECURE_REDIRECT
        is UpdateHttpStatusException -> UpdateFailureCode.HTTP_STATUS
        is InvalidUpdateManifestException -> UpdateFailureCode.INVALID_MANIFEST
        is IOException -> UpdateFailureCode.NETWORK
        else -> UpdateFailureCode.STORAGE
    }

    private class UpdateOperationException(
        val code: UpdateFailureCode,
        val stage: UpdateStage,
        cause: Throwable? = null,
    ) : IOException("Update operation failed", cause)

    private companion object {
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val DEFAULT_MANIFEST_BUFFER = 4 * 1024
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
    }
}
