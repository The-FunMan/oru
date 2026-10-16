/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.screen.settings.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FingerprintSpokenLinesTest {

    private val sha256 = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"

    @Test
    fun `the four lines of a sha-256 are joined with a comma, with no line break or entity left`() {
        val spoken = fingerprintSpokenLines(sha256)
        assertEquals(
            "00:11:22:33:44:55:66:77, 88:99:AA:BB:CC:DD:EE:FF, " +
                "00:11:22:33:44:55:66:77, 88:99:AA:BB:CC:DD:EE:FF",
            spoken,
        )
        assertFalse(spoken.contains("\n"))
        assertFalse(spoken.contains("&#"))
    }

    @Test
    fun `the lines are read as the fingerprint is shown, in upper case`() {
        val spoken = fingerprintSpokenLines("abcd")
        assertEquals("AB:CD", spoken)
    }
}
