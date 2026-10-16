/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedInputStreamTest {
    private val data = ByteArray(10) { it.toByte() }

    @Test
    fun `a stream of exactly the limit is copied in full`() {
        val copied = BoundedInputStream(ByteArrayInputStream(data), limit = 10).readBytes()

        assertContentEquals(data, copied)
    }

    @Test
    fun `a stream one byte past the limit fails`() {
        assertFailsWith<IOException> {
            BoundedInputStream(ByteArrayInputStream(data), limit = 9).readBytes()
        }
    }

    @Test
    fun `single byte reads count towards the limit`() {
        val stream = BoundedInputStream(ByteArrayInputStream(data), limit = 3)

        assertEquals(0, stream.read())
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        assertFailsWith<IOException> { stream.read() }
    }

    @Test
    fun `end of stream is not counted`() {
        val stream = BoundedInputStream(ByteArrayInputStream(data.copyOf(2)), limit = 2)

        assertContentEquals(data.copyOf(2), stream.readBytes())
        assertEquals(-1, stream.read())
    }
}
