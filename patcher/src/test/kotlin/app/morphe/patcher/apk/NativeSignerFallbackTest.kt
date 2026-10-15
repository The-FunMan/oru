package app.morphe.patcher.apk

import app.morphe.patcher.PatcherResult
import app.morphe.patcher.WriteProgressListener
import app.morphe.patcher.apk.ApkUtils.applyToWithProgress
import com.android.apksig.apk.ApkUtils as ApkSigUtils
import com.android.apksig.util.DataSources
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Date
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NativeSignerFallbackTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `a native signer that cannot link leaves the unsigned APK unchanged and reports false`() {
        val apk = unsignedApk()
        val before = apk.readBytes()

        val signed = NativePackager.signInPlace(apk, signingKey.privateKey, signingKey.certificate) { _, _, _ ->
            throw UnsatisfiedLinkError("liboru_packager.so")
        }

        assertFalse(signed)
        assertContentEquals(before, apk.readBytes())
    }

    @Test
    fun `a native signer that fails after writing into the central directory is undone`() {
        val apk = unsignedApk()
        val before = apk.readBytes()
        val centralDirectoryOffset = RandomAccessFile(apk, "r").use { file ->
            ApkSigUtils.findZipSections(DataSources.asDataSource(file)).zipCentralDirectoryOffset
        }

        val signed = NativePackager.signInPlace(apk, signingKey.privateKey, signingKey.certificate) { path, _, _ ->
            RandomAccessFile(path, "rw").use { file ->
                file.seek(centralDirectoryOffset)
                file.write(ByteArray(before.size - centralDirectoryOffset.toInt()) { 0x5A })
            }
            throw IOException("No space left on device")
        }

        assertFalse(signed)
        assertContentEquals(before, apk.readBytes())
    }

    @Test
    fun `an APK without a readable central directory falls back without running the native signer`() {
        val apk = File(tempDir, "not-a-zip.apk").apply { writeBytes(ByteArray(256) { 0x11 }) }
        val before = apk.readBytes()
        var signerRan = false

        val signed = NativePackager.signInPlace(apk, signingKey.privateKey, signingKey.certificate) { _, _, _ ->
            signerRan = true
            0L
        }

        assertFalse(signed)
        assertFalse(signerRan)
        assertContentEquals(before, apk.readBytes())
    }

    @Test
    fun `a restore that fails after a failed native signer is logged`() {
        val apk = unsignedApk().apply { setReadOnly() }
        assumeTrue(!apk.canWrite(), "the file system does not enforce read-only files")

        val records = captureLog(NativePackager::class.java.name) {
            try {
                val signed = NativePackager.signInPlace(apk, signingKey.privateKey, signingKey.certificate) { _, _, _ ->
                    throw IOException("No space left on device")
                }
                assertFalse(signed)
            } finally {
                apk.setWritable(true)
            }
        }

        assertTrue(records.any { it.level == Level.WARNING && it.message.contains("Could not restore") })
    }

    @Test
    fun `a native writer that cannot link reports false and logs the cause`() {
        val input = unsignedApk()
        val output = File(tempDir, "output.apk")

        val records = captureLog(NativePackager::class.java.name) {
            assertFalse(NativePackager.write(input, output, emptyList(), emptySet()))
        }

        assertTrue(records.any { it.level == Level.WARNING && it.message.contains("failed to write") })
    }

    @Test
    fun `an APK that cannot be read counts as signed, so the native signer is not run on it`() {
        val apk = File(tempDir, "not-a-zip.apk").apply { writeBytes(ByteArray(256) { 0x11 }) }

        val records = captureLog(ApkSigner::class.java.name) {
            assertTrue(ApkSigner.newApkSigner("Test", signingKey).hasSigningBlock(apk))
        }

        assertTrue(records.any { it.level == Level.WARNING })
    }

    @Test
    fun `an unsigned APK has no signing block`() {
        assertFalse(ApkSigner.newApkSigner("Test", signingKey).hasSigningBlock(unsignedApk()))
    }

    /** Records what the logger named [loggerName] logs while [block] runs. */
    private fun captureLog(loggerName: String, block: () -> Unit): List<LogRecord> {
        val records = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }
        val logger = Logger.getLogger(loggerName)
        logger.addHandler(handler)
        try {
            block()
        } finally {
            logger.removeHandler(handler)
        }
        return records
    }

    @Test
    fun `jvm fallback applyTo reports completion progress`() {
        System.setProperty("oru.packager", "jvm")
        try {
            assertApplyToWithProgressReports("jvm-progress")
        } finally {
            System.clearProperty("oru.packager")
        }
    }

    @Test
    fun `native applyToWithProgress reports non-decreasing progress ending at one`() {
        assumeTrue(NativePackager.enabled, "the native packager library is not loaded")
        assertApplyToWithProgressReports("native-progress")
    }

    /** Applies a manifest-only base with progress reporting and checks the reports end at one and never fall. */
    private fun assertApplyToWithProgressReports(name: String) {
        val target = File(tempDir, "$name.apk")
        val base = File(tempDir, "$name-base.apk")
        ZipOutputStream(base.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest/>".toByteArray())
            zip.closeEntry()
        }
        val result = PatcherResult(emptySet(), PatcherResult.PatchedResources(base, null, emptySet(), emptySet()))
        val reports = mutableListOf<Float>()
        result.applyToWithProgress(target, WriteProgressListener { reports += it })
        assertTrue(reports.isNotEmpty())
        assertTrue(reports.zipWithNext().all { (a, b) -> a <= b }, "progress went backwards: $reports")
        assertEquals(1f, reports.last())
    }

    @Test
    fun `jvm fallback signing reports completion progress`() {
        val apk = manifestApk()
        val reports = mutableListOf<Float>()
        ApkSigner.newApkSigner("Test", signingKey).signApk(apk, apk, minSdkVersion = 21) { fraction ->
            reports += fraction
        }
        assertTrue(reports.isNotEmpty())
        assertEquals(1f, reports.last())
    }

    /** A signed-ready APK carrying only a manifest. */
    private fun manifestApk(): File {
        val apk = File(tempDir, "manifest.apk")
        ApkModule().use { module ->
            val manifest = AndroidManifestBlock()
            manifest.packageName = "com.test.fallback"
            manifest.versionCode = 1
            manifest.versionName = "1.0"
            manifest.setMinSdkVersion(21)
            module.setManifest(manifest)
            module.writeApk(apk)
        }
        return apk
    }

    /** A zip whose only entry is stored, so that it has a central directory and no signing block. */
    private fun unsignedApk(): File {
        val apk = File(tempDir, "unsigned.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("res/raw/data.bin"))
            zip.write(ByteArray(2048) { (it % 13).toByte() })
            zip.closeEntry()
        }
        return apk
    }

    private companion object {
        val signingKey by lazy {
            ApkSigner.newPrivateKeyCertificatePair("Test", Date(System.currentTimeMillis() + 86_400_000L))
        }
    }
}
