package app.morphe.manager.domain.installer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ApkCopyTest {
    /** Counts read calls and the buffer length each one asked for. */
    private class CountingInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var readCalls = 0
            private set

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            readCalls++
            return delegate.read(b, off, len)
        }
    }

    private val payload = Random(7).nextBytes(5 * APK_COPY_BUFFER_SIZE + 123)

    @Test
    fun `default copy reads in 8 KB steps`() {
        val input = CountingInputStream(payload)
        input.copyTo(ByteArrayOutputStream())

        assertEquals(ceilDiv(payload.size, 8192) + 1, input.readCalls)
    }

    @Test
    fun `apk copy reads in 1 MB steps and keeps the bytes`() {
        val input = CountingInputStream(payload)
        val out = ByteArrayOutputStream()

        val copied = input.copyApkTo(out)

        assertEquals(payload.size.toLong(), copied)
        assertContentEquals(payload, out.toByteArray())
        assertEquals(ceilDiv(payload.size, APK_COPY_BUFFER_SIZE) + 1, input.readCalls)
    }

    // The reads that carry data, plus the final read that returns -1
    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
