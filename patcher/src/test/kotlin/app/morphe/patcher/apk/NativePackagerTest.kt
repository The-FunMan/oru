package app.morphe.patcher.apk

import app.morphe.patcher.PatcherResult
import app.morphe.patcher.apk.ApkUtils.applyTo
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Date
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs with the native packager when `ORU_NATIVE_PACKAGER_LIB` names its library (for example
 * `native-packager/target/release/oru_packager.dll`), and is skipped otherwise.
 */
internal class NativePackagerTest {
    @TempDir
    lateinit var temporaryDirectory: File

    @BeforeEach
    fun loadNativePackager() {
        val library = System.getenv("ORU_NATIVE_PACKAGER_LIB")
        assumeTrue(library != null, "ORU_NATIVE_PACKAGER_LIB is not set")
        System.setProperty("oru.packager.lib", library)
        System.clearProperty("oru.packager")
    }

    @Test
    fun `native and zip paths write the same entries with the same content and compression`() {
        val zipOutput = applyFixture("zip", switch = "jvm")
        val nativeOutput = applyFixture("native", switch = null)

        assertEquals(entryMethods(zipOutput), entryMethods(nativeOutput))
        assertEquals(readZip(zipOutput).mapValues { it.value.toList() }, readZip(nativeOutput).mapValues { it.value.toList() })
    }

    @Test
    fun `the native path keeps the patched dex files, drops stale ones and strips v1 signatures`() {
        val output = applyFixture("dex", switch = null)
        val entries = readZip(output)

        assertEquals("patched dex", String(entries.getValue("classes.dex")))
        assertEquals("patched dex 2", String(entries.getValue("classes2.dex")))
        assertFalse("classes99.dex" in entries)
        assertFalse("META-INF/CERT.SF" in entries)
        assertFalse("assets/delete-me.txt" in entries)
    }

    @Test
    fun `a v2 signature written by the native signer verifies to the signing certificate`() {
        val apk = manifestApk()
        ApkSigner.newApkSigner("Test", signingKey).signApk(apk, apk, minSdkVersion = 30)

        assertEquals(
            listOf(signingKey.certificate),
            ApkUtils.verifiedSigningCertificates(apk, PLATFORM_VERSION),
        )
        assertTrue(apk.length() > 0)
    }

    @Test
    fun `the switch restores the zip path`() {
        System.setProperty("oru.packager", "jvm")
        try {
            assertFalse(NativePackager.enabled)
        } finally {
            System.clearProperty("oru.packager")
        }
        assertTrue(NativePackager.enabled)
    }

    /** Applies the fixture result to a fresh target, with the native path on unless [switch] is `jvm`. */
    private fun applyFixture(name: String, switch: String?): File {
        val target = temporaryDirectory.resolve("$name.apk")
        val resourcesApk = temporaryDirectory.resolve("$name-resources.apk")
        val otherResources = temporaryDirectory.resolve("$name-other").apply { mkdirs() }
        writeZip(
            target,
            mapOf(
                "assets/original-only.txt" to "original".toByteArray(),
                "assets/delete-me.txt" to "delete me".toByteArray(),
                "classes.dex" to "original dex".toByteArray(),
                "classes2.dex" to "untouched dex".toByteArray(),
                "classes3.dex" to "stale dex".toByteArray(),
                "META-INF/MANIFEST.MF" to "manifest".toByteArray(),
                "META-INF/CERT.SF" to "signature file".toByteArray(),
                "META-INF/CERT.RSA" to "signature block".toByteArray(),
                "META-INF/services/kept" to "service".toByteArray(),
                "lib/arm64-v8a/libx.so" to ByteArray(3000) { it.toByte() },
            ),
        )
        writeZip(
            resourcesApk,
            mapOf(
                "resources.arsc" to ByteArray(2048) { 5 },
                "res/layout/main.xml" to "<layout/>".repeat(64).toByteArray(),
                "assets/resource-base-only.txt" to "resource base".repeat(64).toByteArray(),
            ),
            stored = setOf("resources.arsc"),
        )
        otherResources.resolve("assets/raw-added.txt").apply { parentFile.mkdirs() }.writeText("raw resource")
        otherResources.resolve("assets/raw-stored.bin").writeBytes(ByteArray(64) { 9 })
        otherResources.resolve("res/raw/new.txt").apply { parentFile.mkdirs() }.writeText("new")
        otherResources.resolve("assets/compressible.txt").writeText("abcdefgh".repeat(512))
        otherResources.resolve("assets/random.bin").writeBytes(java.util.Random(7).let { random -> ByteArray(700).also(random::nextBytes) })

        val result = PatcherResult(
            linkedSetOf(
                PatcherResult.PatchedDexFile("classes.dex", ByteArrayInputStream("patched dex".toByteArray())),
                PatcherResult.PatchedDexFile("classes2.dex", ByteArrayInputStream("patched dex 2".toByteArray())),
            ),
            PatcherResult.PatchedResources(
                resourcesApk,
                otherResources,
                setOf("assets/raw-stored.bin"),
                setOf("assets/delete-me.txt"),
            ),
        )
        if (switch != null) System.setProperty("oru.packager", switch)
        try {
            result.applyTo(target)
        } finally {
            System.clearProperty("oru.packager")
        }
        return target
    }

    /** A signed-ready APK carrying only a manifest. */
    private fun manifestApk(): File {
        val apk = temporaryDirectory.resolve("unsigned.apk")
        ApkModule().use { module ->
            val manifest = AndroidManifestBlock()
            manifest.packageName = "com.test.native"
            manifest.versionCode = 1
            manifest.versionName = "1.0"
            manifest.setMinSdkVersion(PLATFORM_VERSION)
            module.setManifest(manifest)
            module.writeApk(apk)
        }
        return apk
    }

    /** Writes [entries]; those named in [stored] are stored, as aapt writes `resources.arsc`. */
    private fun writeZip(file: File, entries: Map<String, ByteArray>, stored: Set<String> = emptySet()) {
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, contents) ->
                val entry = ZipEntry(name)
                if (name in stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = contents.size.toLong()
                    entry.compressedSize = contents.size.toLong()
                    entry.crc = CRC32().apply { update(contents) }.value
                }
                output.putNextEntry(entry)
                output.write(contents)
                output.closeEntry()
            }
        }
    }

    private fun readZip(file: File): Map<String, ByteArray> =
        ZipFile(file).use { zip ->
            zip.entries().asSequence().associate { entry ->
                entry.name to zip.getInputStream(entry).use { it.readBytes() }
            }
        }

    private fun entryMethods(file: File): Map<String, Int> =
        ZipFile(file).use { zip -> zip.entries().asSequence().associate { it.name to it.method } }

    private companion object {
        const val PLATFORM_VERSION = 30

        val signingKey by lazy {
            ApkSigner.newPrivateKeyCertificatePair("Native", Date(System.currentTimeMillis() + 86_400_000L))
        }
    }
}
