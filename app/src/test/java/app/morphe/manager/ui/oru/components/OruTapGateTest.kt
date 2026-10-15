/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruTapGateTest {

    /** A clock the test moves by hand, in milliseconds. */
    private class ManualClock(var now: Long = 0L)

    private fun gate(clock: ManualClock) = OruTapGate(windowMs = 400L) { clock.now }

    @Test
    fun `the first tap is taken`() {
        val clock = ManualClock()
        assertTrue(gate(clock).accept())
    }

    @Test
    fun `a second tap within the window is ignored`() {
        val clock = ManualClock()
        val gate = gate(clock)
        assertTrue(gate.accept())
        clock.now = 120L
        assertFalse(gate.accept())
    }

    @Test
    fun `a tap after the window is taken again`() {
        val clock = ManualClock()
        val gate = gate(clock)
        assertTrue(gate.accept())
        clock.now = 400L
        assertTrue(gate.accept())
    }

    @Test
    fun `an ignored tap does not start a window of its own`() {
        val clock = ManualClock()
        val gate = gate(clock)
        assertTrue(gate.accept())
        clock.now = 200L
        assertFalse(gate.accept())
        // 400 ms after the taken tap, not after the ignored one
        clock.now = 401L
        assertTrue(gate.accept())
    }

    @Test
    fun `a screen armed when it appears ignores the taps in its first moments`() {
        val clock = ManualClock(now = 1_000L)
        val gate = gate(clock)
        gate.arm()
        clock.now = 1_100L
        assertFalse(gate.accept())
        clock.now = 1_400L
        assertTrue(gate.accept())
    }
}
