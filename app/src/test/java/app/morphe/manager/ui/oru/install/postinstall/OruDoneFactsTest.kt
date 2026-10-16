/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import app.morphe.manager.ui.model.StepTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OruDoneFactsTest {

    @Test
    fun `measured figures are formatted with the run format units`() {
        val facts = OruDoneFacts.of(outputBytes = 1_572_864L, elapsedMs = 12_050L)

        assertEquals("1.5 MB", facts.outputSize)
        assertEquals("0:12", facts.elapsed)
    }

    @Test
    fun `an output under a megabyte is shown in kilobytes`() {
        assertEquals("512 KB", OruDoneFacts.of(524_288L, 1_000L).outputSize)
    }

    @Test
    fun `minutes roll over past a minute`() {
        assertEquals("12:05", OruDoneFacts.of(0L, 725_000L).elapsed)
    }

    @Test
    fun `a zero elapsed time is a real value, not an unknown one`() {
        assertEquals("0:00", OruDoneFacts.of(0L, 0L).elapsed)
    }

    @Test
    fun `unknown figures stay null so the screen can say not available`() {
        val facts = OruDoneFacts.of(outputBytes = null, elapsedMs = null)

        assertNull(facts.outputSize)
        assertNull(facts.elapsed)
    }

    @Test
    fun `negative sizes and times count as unknown`() {
        val facts = OruDoneFacts.of(outputBytes = -1L, elapsedMs = -5L)

        assertNull(facts.outputSize)
        assertNull(facts.elapsed)
    }

    @Test
    fun `elapsed runs from the first start to the last end`() {
        val timings = listOf(StepTiming(1_000L, 2_000L), StepTiming(2_500L, 13_050L))

        assertEquals(12_050L, oruElapsedMs(timings))
    }

    @Test
    fun `elapsed is unknown while no step has ended`() {
        assertNull(oruElapsedMs(emptyList()))
        assertNull(oruElapsedMs(listOf(StepTiming(1_000L, null))))
    }

    @Test
    fun `a clock that went backwards counts as unknown`() {
        assertNull(oruElapsedMs(listOf(StepTiming(5_000L, 4_000L))))
    }
}
