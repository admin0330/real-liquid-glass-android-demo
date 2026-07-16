package io.github.admin0330.liquidmusic.update.network

import io.github.admin0330.liquidmusic.update.security.CrossOriginRedirectException
import io.github.admin0330.liquidmusic.update.security.SecureUpdateUri
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

interface UpdateHttpClient {
    @Throws(IOException::class, UpdateCancelledException::class)
    fun open(uri: URI, cancellation: UpdateCancellation): UpdateHttpResponse
}

interface UpdateHttpResponse : Closeable {
    val body: InputStream
    val contentLength: Long?
}

class UpdateHttpStatusException : IOException("Update server returned an unexpected status")

class InsecureUpdateRedirectException : IOException("Update redirect was rejected")

@Singleton
class HttpsUpdateHttpClient @Inject constructor() : UpdateHttpClient {
    override fun open(uri: URI, cancellation: UpdateCancellation): UpdateHttpResponse {
        var current = SecureUpdateUri.parse(uri.toASCIIString())
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            cancellation.throwIfCancelled()
            val connection = openConnection(current)
            val handle = CancelHandle { connection.disconnect() }
            cancellation.attach(handle)
            try {
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_OK) {
                    validateEncoding(connection)
                    val contentLength = readContentLength(connection)
                    return UrlConnectionResponse(connection, handle, cancellation, contentLength)
                }
                closeResponseBody(connection)
                if (status !in REDIRECT_CODES || redirectCount == MAX_REDIRECTS) {
                    throw UpdateHttpStatusException()
                }
                val location = connection.getHeaderField("Location")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw UpdateHttpStatusException()
                current = try {
                    SecureUpdateUri.resolveRedirect(current, location)
                } catch (_: CrossOriginRedirectException) {
                    throw InsecureUpdateRedirectException()
                } catch (_: IllegalArgumentException) {
                    throw InsecureUpdateRedirectException()
                }
            } catch (error: Exception) {
                cancellation.detach(handle)
                connection.disconnect()
                if (cancellation.isCancelled) throw UpdateCancelledException()
                throw error
            }
            cancellation.detach(handle)
            connection.disconnect()
        }
        throw UpdateHttpStatusException()
    }

    private fun openConnection(uri: URI): HttpsURLConnection {
        val connection = uri.toURL().openConnection() as? HttpsURLConnection
            ?: throw InsecureUpdateRedirectException()
        return connection.apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            doInput = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    private fun validateEncoding(connection: HttpsURLConnection) {
        val encoding = connection.getHeaderField("Content-Encoding")?.trim()
        if (!encoding.isNullOrEmpty() && !encoding.equals("identity", ignoreCase = true)) {
            throw ProtocolException("Encoded update responses are not accepted")
        }
    }

    private fun readContentLength(connection: HttpsURLConnection): Long? {
        val values = connection.headerFields.entries
            .filter { it.key.equals("Content-Length", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
        if (values.isEmpty()) return null
        if (values.size != 1 || ',' in values.single()) {
            throw ProtocolException("Ambiguous Content-Length")
        }
        return values.single().trim().toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: throw ProtocolException("Invalid Content-Length")
    }

    private fun closeResponseBody(connection: HttpsURLConnection) {
        runCatching { connection.errorStream?.close() }
        runCatching { connection.inputStream?.close() }
    }

    private class UrlConnectionResponse(
        private val connection: HttpsURLConnection,
        private val handle: CancelHandle,
        private val cancellation: UpdateCancellation,
        override val contentLength: Long?,
    ) : UpdateHttpResponse {
        override val body: InputStream = connection.inputStream

        override fun close() {
            runCatching { body.close() }
            cancellation.detach(handle)
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 3
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT = "LiquidMusic-Android-Updater"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
