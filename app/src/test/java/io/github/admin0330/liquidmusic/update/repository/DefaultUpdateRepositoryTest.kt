package io.github.admin0330.liquidmusic.update.repository

import io.github.admin0330.liquidmusic.update.model.UpdateCheckResult
import io.github.admin0330.liquidmusic.update.model.UpdateDownloadResult
import io.github.admin0330.liquidmusic.update.model.UpdateFailureCode
import io.github.admin0330.liquidmusic.update.model.UpdateManifest
import io.github.admin0330.liquidmusic.update.network.UpdateCancellation
import io.github.admin0330.liquidmusic.update.network.UpdateHttpClient
import io.github.admin0330.liquidmusic.update.network.UpdateHttpResponse
import io.github.admin0330.liquidmusic.update.platform.ApkIdentityFailure
import io.github.admin0330.liquidmusic.update.platform.ApkIdentityVerifier
import io.github.admin0330.liquidmusic.update.platform.InstallIntentFactory
import io.github.admin0330.liquidmusic.update.platform.InstallLaunch
import io.github.admin0330.liquidmusic.update.platform.InstalledAppInfoProvider
import io.github.admin0330.liquidmusic.update.storage.UpdateFileStore
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultUpdateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun checkUsesProviderAndReportsAvailable() = runBlocking {
        val apk = "apk".toByteArray()
        val manifest = manifestFor(apk)
        val json = manifestJson(manifest).toByteArray()
        val client = QueueHttpClient(mutableListOf(ByteResponse(json, json.size.toLong())))
        val repository = repository(client)

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(1, client.openCount)
        assertEquals(manifest, (result as UpdateCheckResult.Available).manifest)
    }

    @Test
    fun checkMapsCurrentAliyunLegacyManifestToVersionCode18() = runBlocking {
        val json = """
            {
              "version": "2.4.9",
              "apk_url": "liquid-music-v2.4.9.apk",
              "sha256": "d4efcce20ee637a82323f0f78b178240629fda7602a838361e20f533c4c24514",
              "size": 55901896,
              "notes": "Liquid Music v2.4.9"
            }
        """.trimIndent().toByteArray()
        val repository = repository(
            QueueHttpClient(mutableListOf(ByteResponse(json, json.size.toLong()))),
        )

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateCheckResult.UpToDate)
        val upToDate = result as UpdateCheckResult.UpToDate
        assertEquals(18L, upToDate.manifest.versionCode)
        assertEquals(
            "https://updates.example.com/liquid-music-v2.4.9.apk",
            upToDate.manifest.apkUrl,
        )
    }

    @Test
    fun successfulDownloadIsAtomicallyCachedAndReused() = runBlocking {
        val apk = "signed apk payload".toByteArray()
        val manifest = manifestFor(apk)
        val client = QueueHttpClient(mutableListOf(ByteResponse(apk, apk.size.toLong())))
        val repository = repository(client)

        val first = repository.download(manifest)
        val second = repository.download(manifest)

        assertTrue(first is UpdateDownloadResult.Ready)
        val firstReady = first as UpdateDownloadResult.Ready
        assertFalse(firstReady.reusedCache)
        assertTrue(second is UpdateDownloadResult.Ready)
        val secondReady = second as UpdateDownloadResult.Ready
        assertTrue(secondReady.reusedCache)
        assertEquals(1, client.openCount)
        assertTrue(secondReady.apk.name.endsWith(".apk"))
        assertFalse(secondReady.apk.resolveSibling(secondReady.apk.name + ".part").exists())
    }

    @Test
    fun contentLengthMismatchFailsBeforeWriting() = runBlocking {
        val apk = "apk payload".toByteArray()
        val manifest = manifestFor(apk)
        val client = QueueHttpClient(mutableListOf(ByteResponse(apk, apk.size + 10L)))
        val repository = repository(client)

        val result = repository.download(manifest)

        assertTrue(result is UpdateDownloadResult.Failure)
        assertEquals(
            UpdateFailureCode.CONTENT_LENGTH_MISMATCH,
            (result as UpdateDownloadResult.Failure).failure.code,
        )
    }

    @Test
    fun packageMismatchRejectsDownloadBeforeAtomicCommit() = runBlocking {
        val apk = "valid bytes from wrong package".toByteArray()
        val manifest = manifestFor(apk)
        val store = UpdateFileStore(temporaryFolder.newFolder())
        val client = QueueHttpClient(mutableListOf(ByteResponse(apk, apk.size.toLong())))
        val repository = repository(client, store, ApkIdentityFailure.PACKAGE)

        val result = repository.download(manifest)

        assertTrue(result is UpdateDownloadResult.Failure)
        assertEquals(
            UpdateFailureCode.PACKAGE_MISMATCH,
            (result as UpdateDownloadResult.Failure).failure.code,
        )
        assertFalse(store.partialFile(manifest.versionCode).exists())
        assertFalse(store.completeFile(manifest.versionCode).exists())
    }

    @Test
    fun versionMismatchRejectsDownloadBeforeAtomicCommit() = runBlocking {
        val apk = "valid bytes from wrong version".toByteArray()
        val manifest = manifestFor(apk)
        val store = UpdateFileStore(temporaryFolder.newFolder())
        val client = QueueHttpClient(mutableListOf(ByteResponse(apk, apk.size.toLong())))
        val repository = repository(client, store, ApkIdentityFailure.VERSION)

        val result = repository.download(manifest)

        assertTrue(result is UpdateDownloadResult.Failure)
        assertEquals(
            UpdateFailureCode.VERSION_MISMATCH,
            (result as UpdateDownloadResult.Failure).failure.code,
        )
        assertFalse(store.partialFile(manifest.versionCode).exists())
        assertFalse(store.completeFile(manifest.versionCode).exists())
    }

    @Test
    fun cancelDownloadStopsStreamAndRemovesPartFile() = runBlocking {
        val apk = ByteArray(256) { it.toByte() }
        val manifest = manifestFor(apk)
        val started = CountDownLatch(1)
        val client = CancelAwareHttpClient(started, manifest.size)
        val store = UpdateFileStore(temporaryFolder.newFolder())
        val repository = repository(client, store)

        val result = async(Dispatchers.Default) { repository.download(manifest) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        repository.cancelDownload()

        assertTrue(result.await() is UpdateDownloadResult.Cancelled)
        val cancelled = repository.state.value as io.github.admin0330.liquidmusic.update.model.UpdateState.Cancelled
        assertEquals(manifest, cancelled.manifest)
        assertFalse(store.partialFile(manifest.versionCode).exists())
    }

    private fun repository(
        httpClient: UpdateHttpClient,
        fileStore: UpdateFileStore = UpdateFileStore(temporaryFolder.newFolder()),
        identityFailure: ApkIdentityFailure? = null,
    ): DefaultUpdateRepository =
        DefaultUpdateRepository(
            manifestUrlProvider = object : UpdateManifestUrlProvider {
                override suspend fun manifestUri() = URI("https://updates.example.com/latest.json")
            },
            httpClient = httpClient,
            fileStore = fileStore,
            installedAppInfo = InstalledAppInfoProvider { 19L },
            apkIdentityVerifier = object : ApkIdentityVerifier {
                override fun verify(
                    apk: java.io.File,
                    expectedVersionCode: Long,
                    expectedVersionName: String,
                ): ApkIdentityFailure? = identityFailure
            },
            installIntentFactory = object : InstallIntentFactory {
                override fun create(apk: java.io.File): InstallLaunch = error("Not called")
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

    private fun manifestFor(apk: ByteArray) = UpdateManifest(
        versionCode = 20,
        versionName = "3.0.0",
        apkUrl = "https://updates.example.com/app.apk",
        sha256 = apk.sha256(),
        size = apk.size.toLong(),
        changelog = "changes",
    )

    private fun manifestJson(manifest: UpdateManifest): String =
        "{\"versionCode\":${manifest.versionCode},\"versionName\":\"${manifest.versionName}\"," +
            "\"apkUrl\":\"${manifest.apkUrl}\",\"sha256\":\"${manifest.sha256}\"," +
            "\"size\":${manifest.size},\"changelog\":\"${manifest.changelog}\"}"

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class QueueHttpClient(
        private val responses: MutableList<UpdateHttpResponse>,
    ) : UpdateHttpClient {
        var openCount: Int = 0
            private set

        override fun open(uri: URI, cancellation: UpdateCancellation): UpdateHttpResponse {
            openCount++
            return responses.removeFirst()
        }
    }

    private class ByteResponse(
        bytes: ByteArray,
        override val contentLength: Long?,
    ) : UpdateHttpResponse {
        override val body: InputStream = ByteArrayInputStream(bytes)
        override fun close() = body.close()
    }

    private class CancelAwareHttpClient(
        private val started: CountDownLatch,
        private val size: Long,
    ) : UpdateHttpClient {
        override fun open(uri: URI, cancellation: UpdateCancellation): UpdateHttpResponse =
            object : UpdateHttpResponse {
                override val contentLength: Long = size
                override val body: InputStream = object : InputStream() {
                    override fun read(): Int = error("Bulk reads only")

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        started.countDown()
                        while (!cancellation.isCancelled) Thread.sleep(5)
                        throw java.io.IOException("cancelled")
                    }
                }

                override fun close() = body.close()
            }
    }
}

private fun java.io.File.resolveSibling(name: String): java.io.File =
    java.io.File(parentFile, name)
