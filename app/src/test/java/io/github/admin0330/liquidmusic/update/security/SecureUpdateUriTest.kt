package io.github.admin0330.liquidmusic.update.security

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureUpdateUriTest {
    @Test
    fun acceptsHttpsWithoutCredentials() {
        assertEquals(
            URI("https://updates.example.com/releases/latest.json"),
            SecureUpdateUri.parse("https://updates.example.com/releases/latest.json"),
        )
    }

    @Test
    fun rejectsPlaintextCredentialsAndInvalidPort() {
        listOf(
            "http://updates.example.com/latest.json",
            "https://user:pass@updates.example.com/latest.json",
            "https://updates.example.com:99999/latest.json",
            "https://updates.example.com/latest.json#fragment",
        ).forEach { value ->
            assertThrows(InvalidUpdateUriException::class.java) {
                SecureUpdateUri.parse(value)
            }
        }
    }

    @Test
    fun acceptsHttpsOnExplicitPort() {
        assertEquals(
            URI("https://updates.example.com:8443/latest.json"),
            SecureUpdateUri.parse("https://updates.example.com:8443/latest.json"),
        )
    }

    @Test
    fun redirectMustRemainSameOriginAndHttps() {
        val origin = URI("https://updates.example.com/releases/latest.json")
        assertEquals(
            URI("https://updates.example.com/files/app.apk"),
            SecureUpdateUri.resolveRedirect(origin, "/files/app.apk"),
        )
        assertThrows(CrossOriginRedirectException::class.java) {
            SecureUpdateUri.resolveRedirect(origin, "https://cdn.example.com/app.apk")
        }
        assertThrows(InvalidUpdateUriException::class.java) {
            SecureUpdateUri.resolveRedirect(origin, "http://updates.example.com/app.apk")
        }
    }
}
