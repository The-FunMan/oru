/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal object StringsXmlSanitizePrefilterTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test
    fun `valid text with multi byte characters is not flagged`() {
        assertEquals(false, mayNeedSanitize(bytes("<resources><string>← café 😀 ﷐</string></resources>")))
    }

    @Test
    fun `tab line feed and carriage return are not flagged`() {
        assertEquals(false, mayNeedSanitize(bytes("<a>\t\n\r</a>")))
    }

    @Test
    fun `numeric character references are flagged`() {
        assertEquals(true, mayNeedSanitize(bytes("<a>&#8230;</a>")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["\u0000", "\u0001", "\u000B", "\u000C", "\u001F", "￾", "￿"])
    fun `invalid characters are flagged`(invalid: String) {
        assertEquals(true, mayNeedSanitize(bytes("<a>x${invalid}y</a>")))
    }

    @Test
    fun `invalid characters stay invalid in the text check`() {
        // The prefilter must agree with the text check on every input it flags or skips.
        for (input in listOf("<a>\u0001</a>", "<a>￿</a>", "<a>&#65535;</a>", "<a>←</a>")) {
            assertEquals(sanitizeXmlText(input) != input, mayNeedSanitize(bytes(input)), input)
        }
    }
}
