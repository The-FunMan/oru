/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

internal class ApkMergerCleanupTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `failed merge leaves no extracted directory or partial output`() {
        val input = File(tempDir, "bundle.apkm")
        ZipOutputStream(input.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("base.apk"))
            zip.write("not an apk".toByteArray())
            zip.closeEntry()
        }
        val output = File(tempDir, "merged.apk")

        assertFails { ApkMerger().merge(input, output) }

        assertEquals(listOf("bundle.apkm"), tempDir.list()!!.sorted())
    }
}
