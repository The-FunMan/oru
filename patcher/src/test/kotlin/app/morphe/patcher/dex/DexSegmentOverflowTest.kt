/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.dex

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

internal class DexSegmentOverflowTest {
    @TempDir
    lateinit var outputDir: File

    private val opcodes = Opcodes.getDefault()

    /** Pools created by the segment. The factory fails the test instead of looping forever if the segment never ends. */
    private val createdPools = mutableListOf<DexPool>()

    private fun newPool(opcodes: Opcodes): DexPool {
        check(createdPools.size < MAX_POOLS) { "segment kept creating pools: ${createdPools.size} so far" }
        return OverflowOnType(opcodes, OVERSIZED_TYPE).also { createdPools += it }
    }

    private fun classDef(type: String) =
        ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, null)

    /** A pool that reports overflow as soon as a class of [overflowingType] has been interned. */
    private class OverflowOnType(opcodes: Opcodes, private val overflowingType: String) : DexPool(opcodes) {
        private var lastInterned: String? = null

        override fun internClass(classDef: ClassDef) {
            lastInterned = classDef.type
            super.internClass(classDef)
        }

        override fun hasOverflowed(): Boolean = lastInterned == overflowingType
    }

    @Test
    fun `class that overflows an empty pool fails the segment instead of looping`() {
        val queue = ArrayDeque<ClassDef>(listOf(classDef(OVERSIZED_TYPE)))

        val error = assertFailsWith<PatchException> {
            DexReadWrite.processSegment(queue, opcodes, outputDir, 0, ::newPool)
        }

        assertContains(error.message.orEmpty(), OVERSIZED_TYPE)
        assertContains(error.message.orEmpty(), "by itself")
        assertEquals(1, createdPools.size)
        assertEquals(emptyList(), outputDir.listFiles()!!.toList())
    }

    @Test
    fun `temp files of earlier pools are deleted when the segment fails`() {
        val queue = ArrayDeque<ClassDef>(listOf(classDef("Lpkg/small0;"), classDef("Lpkg/small1;"), classDef(OVERSIZED_TYPE)))

        assertFailsWith<PatchException> {
            DexReadWrite.processSegment(queue, opcodes, outputDir, 0, ::newPool)
        }

        // The first pool held two classes and was written before the oversized class failed the second pool.
        assertEquals(2, createdPools.size)
        assertEquals(emptyList(), outputDir.listFiles()!!.toList())
    }

    /** A pool that overflows once it holds more than [limit] classes. */
    private class ClassLimitPool(opcodes: Opcodes, private val limit: Int) : DexPool(opcodes) {
        private var internedCount = 0

        override fun internClass(classDef: ClassDef) {
            internedCount++
            super.internClass(classDef)
        }

        override fun hasOverflowed(): Boolean = internedCount > limit
    }

    @Test
    fun `temp files of earlier pools are deleted when a later write fails`() {
        // A directory where the second pool's temp file would go makes that write fail after the first pool is on disk
        outputDir.resolve(".tmp_seg0_1.dex").mkdir()
        val queue = ArrayDeque<ClassDef>(
            listOf(classDef("Lpkg/small0;"), classDef("Lpkg/small1;"), classDef("Lpkg/small2;")),
        )

        assertFails {
            DexReadWrite.processSegment(queue, opcodes, outputDir, 0) { ClassLimitPool(it, 2) }
        }

        assertFalse(outputDir.resolve(".tmp_seg0_0.dex").exists())
    }

    private companion object {
        const val OVERSIZED_TYPE = "Lpkg/oversized;"
        const val MAX_POOLS = 5
    }
}
