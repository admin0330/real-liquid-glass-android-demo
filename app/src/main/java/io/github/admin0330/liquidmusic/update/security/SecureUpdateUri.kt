package io.github.admin0330.liquidmusic.update.security

import java.net.URI

class InvalidUpdateUriException : IllegalArgumentException("Update URL is not an allowed HTTPS URL")

object SecureUpdateUri {
    fun parse(value: String): URI {
        if (value.isBlank() || value != value.trim()) throw InvalidUpdateUriException()
        val uri = runCatching { URI(value) }.getOrElse { throw InvalidUpdateUriException() }
        if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) {
            throw InvalidUpdateUriException()
        }
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null) {
            throw InvalidUpdateUriException()
        }
        if (uri.port != -1 && uri.port !in 1..65_535) throw InvalidUpdateUriException()
        return uri
    }

    fun resolveRedirect(origin: URI, location: String): URI {
        val redirected = runCatching { origin.resolve(location) }
            .getOrElse { throw InvalidUpdateUriException() }
        val secure = parse(redirected.toASCIIString())
        if (!sameOrigin(origin, secure)) throw CrossOriginRedirectException()
        return secure
    }

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port
}

class CrossOriginRedirectException : SecurityException("Cross-origin update redirect is not allowed")
