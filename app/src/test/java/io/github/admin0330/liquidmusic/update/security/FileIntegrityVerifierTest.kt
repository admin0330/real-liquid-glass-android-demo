package io.github.admin0330.liquidmusic.update.security

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileIntegrityVerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun checksSizeAndSha256() {
        val content = "verified apk bytes".toByteArray()
        val file = temporaryFolder.newFile("update.apk").apply { writeBytes(content) }
        val digest = content.sha256()

        assertNull(FileIntegrityVerifier.verify(file, content.size.toLong(), digest))
        assertEquals(
            FileIntegrityFailure.SIZE,
            FileIntegrityVerifier.verify(file, content.size + 1L, digest),
        )
        assertEquals(
            FileIntegrityFailure.SHA256,
            FileIntegrityVerifier.verify(file, content.size.toLong(), "00".repeat(32)),
        )
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
