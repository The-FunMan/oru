package app.morphe.patcher.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class OverlapHeapPolicyTest {
    private fun mib(value: Long) = value * 1024 * 1024

    @Test
    fun `a 640 MiB heap runs the compile stages one after the other`() {
        assertFalse(overlapFitsHeap(mib(640)))
    }

    @Test
    fun `a 1000 MiB heap runs the compile stages one after the other`() {
        assertFalse(overlapFitsHeap(mib(1000)))
    }

    @Test
    fun `a heap one byte under the overlap threshold runs the compile stages one after the other`() {
        assertFalse(overlapFitsHeap(OVERLAP_MIN_HEAP_BYTES - 1))
    }

    @Test
    fun `a heap at the overlap threshold overlaps the compile stages`() {
        assertTrue(overlapFitsHeap(OVERLAP_MIN_HEAP_BYTES))
    }

    @Test
    fun `a 2048 MiB heap overlaps the compile stages`() {
        assertTrue(overlapFitsHeap(mib(2048)))
    }
}
