/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

internal class ApkSignerFailureTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `a failed signing leaves the destination untouched`() {
        val input = File(tempDir, "input.apk")
        writeApkWithCorruptEntry(input)
        val output = File(tempDir, "output.apk").apply { writeText("previous") }

        assertFails {
            ApkSigner.newApkSigner("Test", signingKey).signApk(input, output, minSdkVersion = 21)
        }

        assertEquals("previous", output.readText())
        assertEquals(listOf("input.apk", "output.apk"), tempDir.list()!!.sorted())
    }

    /**
     * An APK whose manifest is readable but whose first entry is deflated garbage. Signing for v1 inflates
     * every entry, so it fails after the output has been opened.
     */
    private fun writeApkWithCorruptEntry(file: File) {
        val manifest = File(tempDir, "manifest.apk").also { apk ->
            ApkModule().use { module ->
                val block = AndroidManifestBlock()
                block.packageName = "com.test.signer"
                block.versionCode = 1
                block.versionName = "1.0"
                block.setMinSdkVersion(21)
                module.setManifest(block)
                module.writeApk(apk)
            }
        }
        val manifestBytes = ZipFile(manifest).use { zip ->
            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
        }
        manifest.delete()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("res/raw/data.bin"))
            zip.write(ByteArray(4096) { (it % 7).toByte() })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifestBytes)
            zip.closeEntry()
        }
        // The first local header is the data entry. Replace its deflate stream with a block type that is not valid.
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val dataStart = 30 + (header.getShort(26).toInt() and 0xFFFF) + (header.getShort(28).toInt() and 0xFFFF)
        for (i in dataStart until dataStart + 8) bytes[i] = 0xFF.toByte()
        file.writeBytes(bytes)
    }

    private companion object {
        val signingKey by lazy {
            ApkSigner.newPrivateKeyCertificatePair("Test", Date(System.currentTimeMillis() + 86_400_000L))
        }
    }
}
