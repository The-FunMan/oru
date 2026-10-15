package app.morphe.manager.util

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SameFileContentTest {
    private fun file(dir: File, name: String, bytes: ByteArray): File =
        File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun `different sizes never hash`() {
        val dir = createTempDirectory(prefix = "same-size").toFile()
        try {
            val installed = file(dir, "installed.apk", ByteArray(100))
            val expected = file(dir, "expected.apk", ByteArray(99))
            var hashed = 0

            val same = sameFileContent(installed, expected) {
                hashed++
                it.sha256OrNull()
            }

            assertFalse(same)
            assertEquals(0, hashed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `equal sizes compare by hash`() {
        val dir = createTempDirectory(prefix = "same-hash").toFile()
        try {
            val installed = file(dir, "installed.apk", ByteArray(64) { 1 })
            val expected = file(dir, "expected.apk", ByteArray(64) { 2 })
            val other = file(dir, "other.apk", ByteArray(64) { 1 })

            assertFalse(sameFileContent(installed, expected))
            assertTrue(sameFileContent(installed, other))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing installed file is not a match`() {
        val dir = createTempDirectory(prefix = "missing").toFile()
        try {
            val expected = file(dir, "expected.apk", ByteArray(8))

            assertFalse(sameFileContent(File(dir, "gone.apk"), expected))
        } finally {
            dir.deleteRecursively()
        }
    }
}
