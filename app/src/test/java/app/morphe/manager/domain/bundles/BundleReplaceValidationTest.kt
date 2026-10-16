/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BundleReplaceValidationTest {
    private val dir = Files.createTempDirectory("morphe-bundle-replace").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun bundle() = LocalPatchBundle(
        name = "test",
        uid = 1,
        displayName = null,
        createdAt = null,
        updatedAt = null,
        error = null,
        directory = dir,
        enabled = true
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun validBundle() = zipOf("classes.dex" to ByteArray(4096) { (it % 251).toByte() })

    private fun installedBundle(): ByteArray {
        val bytes = validBundle()
        dir.resolve("patches.jar").writeBytes(bytes)
        return bytes
    }

    @Test
    fun `truncated stream keeps the installed bundle`() = runBlocking {
        val installed = installedBundle()
        val truncated = validBundle().let { it.copyOf(it.size / 2) }

        assertFailsWith<IOException> {
            bundle().replace(ByteArrayInputStream(truncated))
        }

        assertContentEquals(installed, dir.resolve("patches.jar").readBytes())
    }

    @Test
    fun `html page keeps the installed bundle`() = runBlocking {
        val installed = installedBundle()

        assertFailsWith<IOException> {
            bundle().replace(ByteArrayInputStream("<html>error</html>".toByteArray()))
        }

        assertContentEquals(installed, dir.resolve("patches.jar").readBytes())
    }

    @Test
    fun `zip without dex code keeps the installed bundle`() = runBlocking {
        val installed = installedBundle()
        val noDex = zipOf("readme.txt" to "not a bundle".toByteArray())

        assertFailsWith<IOException> {
            bundle().replace(ByteArrayInputStream(noDex))
        }

        assertContentEquals(installed, dir.resolve("patches.jar").readBytes())
    }

    @Test
    fun `truncated temp file keeps the installed bundle`() = runBlocking {
        val installed = installedBundle()
        val incoming = dir.resolve("incoming.tmp")
        val truncated = validBundle().let { it.copyOf(it.size / 2) }
        incoming.writeBytes(truncated)

        assertFailsWith<IOException> {
            bundle().replaceFromTempFile(incoming)
        }

        assertContentEquals(installed, dir.resolve("patches.jar").readBytes())
    }

    @Test
    fun `valid temp file replaces an installed read-only bundle`() = runBlocking {
        // Installed bundles are made read-only, and Windows refuses to replace such a file
        installedBundle()
        dir.resolve("patches.jar").setReadOnly()
        val replacement = zipOf("classes.dex" to ByteArray(8192) { 9 })
        val incoming = dir.resolve("incoming.tmp").apply { writeBytes(replacement) }

        val moved = bundle().replaceFromTempFile(incoming)

        assertTrue(moved)
        assertContentEquals(replacement, dir.resolve("patches.jar").readBytes())
    }

    @Test
    fun `valid temp file replaces the installed bundle`() = runBlocking {
        installedBundle()
        val replacement = zipOf("classes.dex" to ByteArray(8192) { 7 })
        val incoming = dir.resolve("incoming.tmp").apply { writeBytes(replacement) }

        val moved = bundle().replaceFromTempFile(incoming)

        assertTrue(moved)
        assertContentEquals(replacement, dir.resolve("patches.jar").readBytes())
    }
}
