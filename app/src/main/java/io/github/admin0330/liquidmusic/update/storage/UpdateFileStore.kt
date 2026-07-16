package io.github.admin0330.liquidmusic.update.storage

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UpdateFileStore(rootDirectory: File) {
    private val root = rootDirectory.canonicalFile

    @Throws(IOException::class)
    fun ensureReady() {
        if (!root.exists() && !root.mkdirs()) throw IOException("Cannot create update directory")
        if (!root.isDirectory) throw IOException("Update path is not a directory")
    }

    fun partialFile(versionCode: Long): File = child("liquid-music-$versionCode.apk.part", versionCode)

    fun completeFile(versionCode: Long): File = child("liquid-music-$versionCode.apk", versionCode)

    fun contains(file: File): Boolean = runCatching {
        file.canonicalFile.parentFile == root
    }.getOrDefault(false)

    @Throws(IOException::class)
    fun atomicCommit(partial: File, complete: File) {
        if (!contains(partial) || !contains(complete) || !partial.isFile) {
            throw IOException("Invalid update file location")
        }
        try {
            Files.move(
                partial.toPath(),
                complete.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: AtomicMoveNotSupportedException) {
            throw IOException("Atomic update commit is unavailable", error)
        }
    }

    fun delete(file: File) {
        if (contains(file)) runCatching { Files.deleteIfExists(file.toPath()) }
    }

    private fun child(name: String, versionCode: Long): File {
        require(versionCode > 0L) { "versionCode must be positive" }
        return File(root, name)
    }
}
