/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.dex

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

internal class DexSegmentDeterminismTest {
    @TempDir
    lateinit var temporaryDirectory: File

    private fun classDefs(count: Int) = List(count) { index ->
        ImmutableClassDef("Lpkg/c$index;", AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, null)
    }

    /** Writes the classes with the given thread limit and returns each output file name with its bytes. */
    private fun writeWithThreads(classCount: Int, threads: Int): List<Pair<String, ByteArray>> {
        val outputDir = temporaryDirectory.resolve("threads$threads")
        val files = DexReadWrite.writeMultiDexFile(
            outputDir,
            classDefs(classCount).toMutableList(),
            Opcodes.getDefault(),
            threads,
        )
        return files.map { it.name to it.readBytes() }
    }

    @Test
    fun `segment count and bytes do not depend on the thread limit`() {
        val threadLimits = listOf(1, 2, 4, 8)
        val reference = writeWithThreads(classCount = 4000, threads = 8)

        assertEquals(
            listOf("classes.dex", "classes2.dex", "classes3.dex", "classes4.dex"),
            reference.map { it.first },
        )
        for (threads in threadLimits) {
            val output = writeWithThreads(classCount = 4000, threads = threads)
            assertEquals(reference.map { it.first }, output.map { it.first }, "file names with $threads threads")
            reference.zip(output).forEach { (expected, actual) ->
                assertEquals(
                    expected.second.toList(),
                    actual.second.toList(),
                    "${expected.first} differs with $threads threads",
                )
            }
        }
    }

    @Test
    fun `small class sets stay in one dex file for every thread limit`() {
        for (threads in listOf(1, 2, 4, 8)) {
            val output = writeWithThreads(classCount = 1500, threads = threads)
            assertEquals(listOf("classes.dex"), output.map { it.first }, "file names with $threads threads")
        }
    }
}
