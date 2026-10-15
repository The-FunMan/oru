package app.morphe.patcher.dex

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class NativeDexIndexHandleTest {
    private class CountingOps(
        private val statsResult: LongArray = longArrayOf(1, 1, 0),
        private val failDescriptors: Boolean = false,
    ) : NativeDexIndexOps {
        val opened = mutableListOf<Long>()
        val closed = mutableListOf<Long>()

        override fun open(paths: Array<String>): Long = 42L.also { opened += it }
        override fun close(handle: Long) { closed += handle }
        override fun stats(handle: Long): LongArray = statsResult
        override fun descriptors(handle: Long): Array<String> {
            if (failDescriptors) throw IllegalStateException("simulated failure after open")
            return arrayOf("Lfoo/Bar;")
        }
    }

    private val dexFiles = listOf(File("classes.dex"))

    @Test
    fun `handle is closed when a call fails after open`() {
        val ops = CountingOps(failDescriptors = true)
        assertFailsWith<IllegalStateException> { NativeDexIndex.openWith(ops, dexFiles) }
        assertEquals(listOf(42L), ops.opened)
        assertEquals(listOf(42L), ops.closed)
    }

    @Test
    fun `handle is closed when the decoder disagrees with the code items`() {
        val ops = CountingOps(statsResult = longArrayOf(1, 1, 3))
        assertNull(NativeDexIndex.openWith(ops, dexFiles))
        assertEquals(listOf(42L), ops.closed)
    }

    @Test
    fun `handle stays open when the index is returned`() {
        val ops = CountingOps()
        assertNotNull(NativeDexIndex.openWith(ops, dexFiles))
        assertEquals(1, ops.opened.size)
        assertEquals(emptyList(), ops.closed)
    }
}
