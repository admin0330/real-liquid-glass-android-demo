package io.github.admin0330.liquidmusic.update.security

import io.github.admin0330.liquidmusic.update.network.UpdateCancellation
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class FileIntegrityFailure {
    SIZE,
    SHA256,
}

object FileIntegrityVerifier {
    fun verify(
        file: File,
        expectedSize: Long,
        expectedSha256: String,
        cancellation: UpdateCancellation = UpdateCancellation(),
    ): FileIntegrityFailure? {
        if (!file.isFile || file.length() != expectedSize) return FileIntegrityFailure.SIZE
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                cancellation.throwIfCancelled()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        val expected = decodeSha256(expectedSha256) ?: return FileIntegrityFailure.SHA256
        return if (MessageDigest.isEqual(digest.digest(), expected)) null else FileIntegrityFailure.SHA256
    }

    private fun decodeSha256(value: String): ByteArray? {
        if (value.length != SHA256_HEX_LENGTH) return null
        return ByteArray(SHA256_BYTE_LENGTH) { index ->
            val high = value[index * 2].digitToIntOrNull(16) ?: return null
            val low = value[index * 2 + 1].digitToIntOrNull(16) ?: return null
            ((high shl 4) or low).toByte()
        }
    }

    private const val SHA256_BYTE_LENGTH = 32
    private const val SHA256_HEX_LENGTH = SHA256_BYTE_LENGTH * 2
}
