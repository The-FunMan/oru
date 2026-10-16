/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import app.morphe.manager.ui.model.StepTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruDoneFactsTest {

    @Test
    fun `measured figures are formatted with the run format units`() {
        val facts = OruDoneFacts.of(outputBytes = 1_572_864L, elapsedMs = 12_050L, logLines = emptyList())

        assertEquals("1.5 MB", facts.outputSize)
        assertEquals("0:12", facts.elapsed)
    }

    @Test
    fun `an output under a megabyte is shown in kilobytes`() {
        assertEquals("512 KB", OruDoneFacts.of(524_288L, 1_000L, emptyList()).outputSize)
    }

    @Test
    fun `minutes roll over past a minute`() {
        assertEquals("12:05", OruDoneFacts.of(0L, 725_000L, emptyList()).elapsed)
    }

    @Test
    fun `a zero elapsed time is a real value, not an unknown one`() {
        assertEquals("0:00", OruDoneFacts.of(0L, 0L, emptyList()).elapsed)
    }

    @Test
    fun `unknown figures stay null so the screen can say not available`() {
        val facts = OruDoneFacts.of(outputBytes = null, elapsedMs = null, logLines = emptyList())

        assertNull(facts.outputSize)
        assertNull(facts.elapsed)
    }

    @Test
    fun `negative sizes and times count as unknown`() {
        val facts = OruDoneFacts.of(outputBytes = -1L, elapsedMs = -5L, logLines = emptyList())

        assertNull(facts.outputSize)
        assertNull(facts.elapsed)
    }

    @Test
    fun `the patch names come from the run log`() {
        val facts = OruDoneFacts.of(
            outputBytes = 1L,
            elapsedMs = 1L,
            logLines = listOf("Wide search bar succeeded", "Hide ads applied"),
        )

        assertEquals(listOf("Wide search bar", "Hide ads"), facts.patchNames)
    }

    @Test
    fun `a patch name is read from a success line`() {
        assertEquals("Wide search bar", oruPatchResultName("Wide search bar succeeded"))
        assertEquals("Hide ads", oruPatchResultName("  Hide ads applied  "))
    }

    @Test
    fun `lines that are not a patch result give no name`() {
        assertNull(oruPatchResultName("Patch failed: Wide search bar"))
        assertNull(oruPatchResultName("Wide search bar failed:"))
        assertNull(oruPatchResultName("Patching succeeded"))
        assertNull(oruPatchResultName("succeeded"))
        assertNull(oruPatchResultName("null succeeded"))
        assertNull(oruPatchResultName("Loading patches"))
    }

    @Test
    fun `a line that names the Morphe brand is dropped`() {
        assertNull(oruPatchResultName("Morphe branding succeeded"))
        assertNull(oruPatchResultName("morphe logo applied"))
    }

    @Test
    fun `a patch that is logged twice, such as after a retry, is listed once`() {
        val names = oruChangedPatchNames(
            listOf("Hide ads succeeded", "Wide search bar succeeded", "Hide ads succeeded"),
        )

        assertEquals(listOf("Hide ads", "Wide search bar"), names)
    }

    @Test
    fun `no run log means no patch names`() {
        assertTrue(oruChangedPatchNames(emptyList()).isEmpty())
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
