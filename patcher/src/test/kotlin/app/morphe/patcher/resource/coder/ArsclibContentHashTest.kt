/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.coder

import com.android.tools.build.apkzlib.zip.ZFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ArsclibContentHashTest {

    private val coders = CoderRegistry()

    @AfterEach
    fun closeCoders() = coders.close()

    /**
     * [1, 0] and [0, 31] both hash to 31 under the polynomial hash the change detection used to
     * compare with, so an edit to the second must still be reported.
     */
    @Test
    fun `same-length edit that collides under the old hash is reported as modified`(@TempDir tempDir: File) {
        val coder = openCoderWithRootEntry(tempDir, byteArrayOf(1, 0))

        val file = coder.getFile("root.bin")
        file.writeBytes(byteArrayOf(0, 31))
        coder.detectFileChanges()

        assertTrue(coder.modifiedBinaryResources.contains(file), "Changed root file should be reported as modified")
    }

    @Test
    fun `extracted root file that is not changed is not reported as modified`(@TempDir tempDir: File) {
        val coder = openCoderWithRootEntry(tempDir, byteArrayOf(1, 0))

        val file = coder.getFile("root.bin")
        coder.detectFileChanges()

        assertFalse(coder.modifiedBinaryResources.contains(file), "Unchanged root file should not be reported")
    }

    @Test
    fun `edit in the last buffer of a large root file is reported as modified`(@TempDir tempDir: File) {
        val original = ByteArray(3 * DEFAULT_BUFFER_SIZE + 5) { it.toByte() }
        val coder = openCoderWithRootEntry(tempDir, original)

        val file = coder.getFile("root.bin")
        file.writeBytes(original.copyOf().also { it[it.lastIndex] = 0x55 })
        coder.detectFileChanges()

        assertTrue(coder.modifiedBinaryResources.contains(file), "Edited tail should be reported as modified")
    }

    private fun openCoderWithRootEntry(tempDir: File, contents: ByteArray): ArsclibResourceCoder {
        val apk = tempDir.resolve("input.apk")
        ZFile.openReadWrite(apk).use { zFile -> zFile.add("root.bin", contents.inputStream(), false) }
        return coders.open(tempDir.resolve("working").apply { mkdirs() }, apk)
    }
}
