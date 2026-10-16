/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

/**
 * Collisions between a base APK and split APKs when [ApkMerger] merges a directory of them.
 *
 * The base is the APK with the resource table, since these splits carry no manifest to mark one.
 * A file that the base already has is kept, and a split's classes.dex is added under the next free
 * name rather than replacing the base's.
 */
internal class SplitApkMergeCollisionTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `a file the base already has is kept over the split copy`() {
        val input = inputDirectory(
            "base.apk" to mapOf(
                "resources.arsc" to SyntheticApkBuilder.resourceTable(),
                "res/a.png" to byteArrayOf(1),
            ),
            "split.apk" to mapOf(
                "res/a.png" to byteArrayOf(2),
                "res/b.png" to byteArrayOf(3),
            ),
        )

        val entries = merge(input)

        assertContentEquals(byteArrayOf(1), entries.getValue("res/a.png"))
        assertContentEquals(byteArrayOf(3), entries.getValue("res/b.png"))
    }

    @Test
    fun `a split DEX file is added beside the base DEX file instead of replacing it`() {
        val baseDex = SyntheticApkBuilder.dexBytes("Lbase/A;")
        val splitDex = SyntheticApkBuilder.dexBytes("Lsplit/B;")
        val input = inputDirectory(
            "base.apk" to mapOf(
                "resources.arsc" to SyntheticApkBuilder.resourceTable(),
                "classes.dex" to baseDex,
            ),
            "split.apk" to mapOf("classes.dex" to splitDex),
        )

        val entries = merge(input)

        assertContentEquals(baseDex, entries.getValue("classes.dex"))
        assertContentEquals(splitDex, entries.getValue("classes2.dex"))
    }

    @Test
    fun `a failed merge leaves no output file behind`() {
        val input = tempDir.resolve("empty").apply { mkdirs() }
        val output = tempDir.resolve("merged.apk").apply { writeBytes(byteArrayOf(9)) }

        assertFails { ApkMerger().merge(input, output, validateResDir = false) }

        assertFalse(output.exists())
    }

    private fun inputDirectory(vararg apks: Pair<String, Map<String, ByteArray>>): File {
        val dir = tempDir.resolve("input").apply { mkdirs() }
        apks.forEach { (name, entries) -> SyntheticApkBuilder.write(dir.resolve(name), entries) }
        return dir
    }

    /** Merges [input] and returns the bytes of every entry of the result, by name. */
    private fun merge(input: File): Map<String, ByteArray> {
        val output = tempDir.resolve("merged.apk")
        ApkMerger().merge(input, output, validateResDir = false)

        val entries = linkedMapOf<String, ByteArray>()
        ZipFile(output).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                entries[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
            }
        }
        return entries
    }
}
