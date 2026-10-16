package app.morphe.manager.domain.installer

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallerFileProviderPruneTest {
    @Test
    fun `prune keeps only the copy for the new plan`() {
        val shareDir = createTempDirectory(prefix = "share").toFile()
        try {
            listOf("youtube_1.apk", "youtube_2.apk", "music_3.apk").forEach {
                File(shareDir, it).writeBytes(ByteArray(64))
            }
            val keep = File(shareDir, "youtube_2.apk")

            InstallerFileProvider.pruneShareDir(shareDir, keep)

            assertEquals(listOf("youtube_2.apk"), shareDir.list()?.toList())
        } finally {
            shareDir.deleteRecursively()
        }
    }

    @Test
    fun `prune on a missing directory does nothing`() {
        val missing = File(createTempDirectory(prefix = "gone").toFile().also { it.delete() }, "installer_share")

        InstallerFileProvider.pruneShareDir(missing, File(missing, "a.apk"))

        assertTrue(!missing.exists())
    }
}
