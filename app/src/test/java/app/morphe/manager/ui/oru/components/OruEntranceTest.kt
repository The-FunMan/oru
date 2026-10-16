/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruEntranceTest {
    @Test
    fun `the first card starts at once and each next card waits one more step`() {
        assertEquals(0, oruEntranceDelayMillis(0))
        assertEquals(OruEntranceStaggerMillis, oruEntranceDelayMillis(1))
        assertEquals(2 * OruEntranceStaggerMillis, oruEntranceDelayMillis(2))
    }

    @Test
    fun `the stagger stops at the last step, so a long list does not wait`() {
        val capped = OruEntranceMaxSteps * OruEntranceStaggerMillis

        assertEquals(capped, oruEntranceDelayMillis(OruEntranceMaxSteps))
        assertEquals(capped, oruEntranceDelayMillis(OruEntranceMaxSteps + 1))
        assertEquals(capped, oruEntranceDelayMillis(40))
    }

    @Test
    fun `a negative index counts as the first card`() {
        assertEquals(0, oruEntranceDelayMillis(-3))
    }

    @Test
    fun `a card plays its entrance once per ledger`() {
        val ledger = OruEntranceLedger()

        assertTrue(ledger.claim("com.example.app"))
        assertFalse(ledger.claim("com.example.app"))
    }

    @Test
    fun `cards claim the entrance independently`() {
        val ledger = OruEntranceLedger()

        assertTrue(ledger.claim("com.example.app"))
        assertTrue(ledger.claim("com.example.app.clone1"))
    }
}
