/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import app.morphe.patcher.apk.ApkUtils.writeAlignedApkOrDelete
import com.reandroid.apk.ApkModule
import com.reandroid.archive.FileInputSource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse

internal class ApkUtilsWriteFailureTest {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `failed APK write leaves no partial output behind`() {
        val source = temporaryDirectory.resolve("source.bin").apply { writeText("content") }
        val output = temporaryDirectory.resolve("output.apk")

        ApkModule().use { module ->
            module.add(FileInputSource(source, "source.bin"))
            // Removing the source after it was registered makes reading it fail during the write
            source.delete()

            assertFails { module.writeAlignedApkOrDelete(output) }
        }

        assertFalse(output.exists())
    }
}
