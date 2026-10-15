package app.morphe.patcher.util

import app.morphe.patcher.resource.coder.lazyXmlFitsHeap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TwoStageHeapPolicyTest {
    private fun mib(value: Long) = value * 1024 * 1024

    @Test
    fun `the overlap gate needs more heap than the lazy XML gate`() {
        for (heap in listOf(mib(512), mib(640), mib(999))) {
            assertFalse(overlapFitsHeap(heap), "heap of $heap bytes")
            assertFalse(lazyXmlFitsHeap(heap), "heap of $heap bytes")
        }
        for (heap in listOf(mib(1000), mib(1280), mib(1399))) {
            assertFalse(overlapFitsHeap(heap), "heap of $heap bytes")
            assertTrue(lazyXmlFitsHeap(heap), "heap of $heap bytes")
        }
        for (heap in listOf(mib(1400), mib(2048))) {
            assertTrue(overlapFitsHeap(heap), "heap of $heap bytes")
            assertTrue(lazyXmlFitsHeap(heap), "heap of $heap bytes")
        }
    }

    @Test
    fun `each gate switches on at its own threshold`() {
        assertFalse(lazyXmlFitsHeap(TWO_STAGE_MIN_HEAP_BYTES - 1))
        assertTrue(lazyXmlFitsHeap(TWO_STAGE_MIN_HEAP_BYTES))
        assertFalse(overlapFitsHeap(OVERLAP_MIN_HEAP_BYTES - 1))
        assertTrue(overlapFitsHeap(OVERLAP_MIN_HEAP_BYTES))
    }
}
