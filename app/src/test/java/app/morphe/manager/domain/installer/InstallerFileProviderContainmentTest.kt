package app.morphe.manager.domain.installer

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstallerFileProviderContainmentTest {
    private val root = createTempDirectory(prefix = "share-containment").toFile()
    private val shareDir = File(root, "share").apply { mkdirs() }

    @Test
    fun `a file inside the share directory is resolved`() {
        val file = File(shareDir, "youtube.apk")

        val resolved = InstallerFileProvider.resolveInside(shareDir, file)

        assertEquals(file.canonicalFile, resolved)
    }

    @Test
    fun `a sibling directory that shares the name prefix is rejected`() {
        File(root, "share2").mkdirs()

        assertFailsWith<SecurityException> {
            InstallerFileProvider.resolveInside(shareDir, File(root, "share2/secret.apk"))
        }
    }

    @Test
    fun `the share directory itself is rejected`() {
        assertFailsWith<SecurityException> {
            InstallerFileProvider.resolveInside(shareDir, shareDir)
        }
    }
}
