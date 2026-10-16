/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.runtime.process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TruncateForIpcTest {
    private fun utf8Size(text: String) = text.toByteArray(Charsets.UTF_8).size

    @Test
    fun `text within the limit is returned unchanged`() {
        assertEquals("short log line", truncateForIpc("short log line"))
        assertEquals("", truncateForIpc(""))
    }

    @Test
    fun `text exactly at the limit is returned unchanged`() {
        val text = "a".repeat(MAX_IPC_TEXT_BYTES)
        assertEquals(text, truncateForIpc(text))
    }

    @Test
    fun `long ascii text keeps its head and ends with the marker`() {
        val text = "0123456789".repeat(3_000)
        val result = truncateForIpc(text)

        assertEquals(MAX_IPC_TEXT_BYTES, utf8Size(result))
        assertTrue(result.endsWith("...(truncated)"))
        assertTrue(text.startsWith(result.removeSuffix("...(truncated)")))
    }

    @Test
    fun `multi byte text is never split inside a character`() {
        // Two-byte characters with an odd budget force a cut that would otherwise split one.
        val text = "é".repeat(100)
        val result = truncateForIpc(text, maxBytes = 21)

        assertTrue(utf8Size(result) <= 21)
        assertEquals("ééé...(truncated)", result)
        assertFalse(result.contains('�'))
    }

    @Test
    fun `surrogate pairs are kept whole`() {
        // Each emoji is 4 UTF-8 bytes and 2 chars. A budget that is not a multiple of 4 must not split one.
        val text = "😀".repeat(50)
        val result = truncateForIpc(text, maxBytes = 30)

        // Budget is 30 - 14 = 16 bytes, which holds four emoji, so the fifth one is dropped.
        assertTrue(utf8Size(result) <= 30)
        assertEquals("😀".repeat(4) + "...(truncated)", result)
    }
}
