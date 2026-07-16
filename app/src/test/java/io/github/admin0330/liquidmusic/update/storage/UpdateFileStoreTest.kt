package io.github.admin0330.liquidmusic.update.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun commitsPartFileWithinUpdateDirectory() {
        val store = UpdateFileStore(temporaryFolder.newFolder("updates"))
        store.ensureReady()
        val partial = store.partialFile(20).apply { writeText("apk") }
        val complete = store.completeFile(20)

        store.atomicCommit(partial, complete)

        assertFalse(partial.exists())
        assertTrue(complete.isFile)
        assertTrue(store.contains(complete))
    }
}
