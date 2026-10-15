package app.morphe.patcher.resource.coder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LazyXmlHeapPolicyTest {
    private fun mib(value: Long) = value * 1024 * 1024

    @Test
    fun `a small heap keeps XML decoding eager`() {
        assertFalse(lazyXmlFitsHeap(mib(640)))
        assertFalse(lazyXmlFitsHeap(mib(999)))
    }

    @Test
    fun `a large heap decodes XML lazily`() {
        assertTrue(lazyXmlFitsHeap(mib(1000)))
        assertTrue(lazyXmlFitsHeap(mib(1280)))
    }
}
