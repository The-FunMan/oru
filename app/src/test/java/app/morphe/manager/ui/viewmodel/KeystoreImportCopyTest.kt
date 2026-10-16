/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.viewmodel

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeystoreImportCopyTest {
    @Test
    fun `copies the stream into a temp file`() {
        val dir = createTempDirectory("keystore-copy").toFile()
        try {
            val path = copyToTempFile(dir, "signing", "ks") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }

            assertEquals(dir, path.parent.toFile())
            assertContentEquals(byteArrayOf(1, 2, 3), path.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a failed open leaves no temp file behind`() {
        val dir = createTempDirectory("keystore-copy").toFile()
        try {
            assertFailsWith<NullPointerException> {
                copyToTempFile(dir, "signing", "ks") { null }
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty(), "temp files left: ${dir.list().orEmpty().toList()}")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a failed read leaves no temp file behind`() {
        val dir = createTempDirectory("keystore-copy").toFile()
        // Not a ByteArrayInputStream: its transferTo would bypass a read override
        val failingStream = object : InputStream() {
            override fun read(): Int = throw IOException("disk gone")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("disk gone")
        }
        try {
            assertFailsWith<IOException> {
                copyToTempFile(dir, "signing", "ks") { failingStream }
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty(), "temp files left: ${dir.list().orEmpty().toList()}")
        } finally {
            dir.deleteRecursively()
        }
    }
}
