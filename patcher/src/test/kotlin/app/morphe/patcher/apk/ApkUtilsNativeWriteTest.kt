package app.morphe.patcher.apk

import app.morphe.patcher.PatcherResult
import app.morphe.patcher.apk.ApkUtils.applyTo
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ApkUtilsNativeWriteTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `a native write that fails falls back to the zip path and leaves no partial output`() {
        val apk = File(tempDir, "target.apk")
        writeZip(apk, mapOf("assets/kept.txt" to "kept", "classes.dex" to "original dex"))
        val result = PatcherResult(
            linkedSetOf(PatcherResult.PatchedDexFile("classes.dex", ByteArrayInputStream("patched dex".toByteArray()))),
            PatcherResult.PatchedResources(null, null, emptySet(), emptySet()),
        )

        result.applyTo(apk) { _, staged, _, _ ->
            staged.writeBytes("partial".toByteArray())
            false
        }

        val entries = readZip(apk)
        assertEquals("patched dex", String(entries.getValue("classes.dex")))
        assertEquals("kept", String(entries.getValue("assets/kept.txt")))
        assertEquals(listOf("target.apk"), tempDir.list()!!.sorted())
    }

    @Test
    fun `dex files already on disk reach the native writer as their own files`() {
        val onDisk = File(tempDir, "on-disk").apply { mkdirs() }
        val primary = onDisk.resolve("classes.dex").apply { writeText("patched dex") }
        val secondary = onDisk.resolve("classes2.dex").apply { writeText("patched dex 2") }
        val resources = PatcherResult.PatchedResources(null, null, emptySet(), emptySet())
        val fileBacked = PatcherResult(
            linkedSetOf(
                PatcherResult.PatchedDexFile("classes.dex", primary.inputStream(), primary),
                PatcherResult.PatchedDexFile("classes2.dex", secondary.inputStream(), secondary),
            ),
            resources,
        )
        val streamed = PatcherResult(
            linkedSetOf(
                PatcherResult.PatchedDexFile("classes.dex", ByteArrayInputStream("patched dex".toByteArray())),
                PatcherResult.PatchedDexFile("classes2.dex", ByteArrayInputStream("patched dex 2".toByteArray())),
            ),
            resources,
        )

        val (fileEntries, fileSpools) = writeNatively(fileBacked, "file-backed.apk")
        val (streamEntries, streamSpools) = writeNatively(streamed, "streamed.apk")

        assertEquals(emptyList(), fileSpools)
        assertEquals(2, streamSpools.size)
        assertEquals(streamEntries.mapValues { it.value.toList() }, fileEntries.mapValues { it.value.toList() })
        assertEquals(setOf("classes.dex", "classes2.dex"), fileEntries.keys)
    }

    /**
     * Runs the native path with a writer that records the dex entries it is given, and the dex spool files that
     * exist while it runs.
     */
    private fun writeNatively(result: PatcherResult, name: String): Pair<Map<String, ByteArray>, List<String>> {
        val target = File(tempDir, name)
        writeZip(target, mapOf("assets/kept.txt" to "kept"))
        val written = mutableMapOf<String, ByteArray>()
        var spools = emptyList<String>()
        result.applyTo(target) { _, staged, entries, _ ->
            spools = tempDir.list()!!.filter { it.startsWith("dex") && it.endsWith(".bin") }
            entries.filter { it.name.startsWith("classes") }.forEach { written[it.name] = it.file.readBytes() }
            staged.writeBytes(ByteArray(0))
            true
        }
        return written to spools
    }

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun readZip(file: File): Map<String, ByteArray> = ZipFile(file).use { zip ->
        zip.entries().asSequence().associate { entry ->
            entry.name to zip.getInputStream(entry).use { it.readBytes() }
        }
    }
}
