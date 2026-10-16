/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_MINIMUM
import app.morphe.manager.patcher.runtime.lowerMemoryLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruFailureTest {

    @Test
    fun `the offer is the same step down the memory retries take`() {
        val offer = OruMemoryOffer.lowerFrom(1536)

        assertEquals(1536, offer.currentLimitMb)
        assertEquals(lowerMemoryLimit(1536), offer.suggestedLimitMb)
        assertTrue(offer.canAdjust)
    }

    @Test
    fun `the lowest limit gives no offer to lower`() {
        val offer = OruMemoryOffer.lowerFrom(PROCESS_RUNTIME_MEMORY_MINIMUM)

        assertEquals(PROCESS_RUNTIME_MEMORY_MINIMUM, offer.suggestedLimitMb)
        assertFalse(offer.canAdjust)
    }

    @Test
    fun `a memory kill above the minimum is an offer to lower the limit`() {
        val failure = oruFailureOf(
            memoryOffer = OruMemoryOffer.lowerFrom(1536),
            failedPatch = null,
            message = "Patcher process exited with code 137",
            logTail = emptyList(),
        )

        assertEquals(
            OruFailure.MemoryOffer(currentLimitMb = 1536, lowerToMb = lowerMemoryLimit(1536)),
            failure,
        )
    }

    @Test
    fun `a memory kill at the minimum says so and offers nothing`() {
        val failure = oruFailureOf(
            memoryOffer = OruMemoryOffer.lowerFrom(PROCESS_RUNTIME_MEMORY_MINIMUM),
            failedPatch = null,
            message = null,
            logTail = emptyList(),
        )

        assertEquals(OruFailure.MemoryAtMinimum(PROCESS_RUNTIME_MEMORY_MINIMUM), failure)
    }

    @Test
    fun `heap exhaustion has no offer and is an error with the worker's text`() {
        val failure = oruFailureOf(
            memoryOffer = null,
            failedPatch = null,
            message = "The patcher ran out of memory with a 512 MB heap.",
            logTail = emptyList(),
        )

        assertEquals(
            OruFailure.Error(failedPatch = null, summary = "The patcher ran out of memory with a 512 MB heap."),
            failure,
        )
    }

    @Test
    fun `an error names the failed patch and keeps the first lines of the message`() {
        val message = "First line\n\n  second line  \nthird line\nfourth line"

        val failure = oruFailureOf(
            memoryOffer = null,
            failedPatch = "Remove ads",
            message = message,
            logTail = emptyList(),
        ) as OruFailure.Error

        assertEquals("Remove ads", failure.failedPatch)
        assertEquals("First line\nsecond line\nthird line", failure.summary)
    }

    @Test
    fun `a blank failed patch name is no patch`() {
        val failure = oruFailureOf(
            memoryOffer = null,
            failedPatch = "  ",
            message = "boom",
            logTail = emptyList(),
        ) as OruFailure.Error

        assertNull(failure.failedPatch)
    }

    @Test
    fun `a blank message falls back to the last lines of the log`() {
        val logTail = listOf("[INFO] one", "[INFO] two", "[ERROR] three", "[ERROR] four")

        val failure = oruFailureOf(
            memoryOffer = null,
            failedPatch = null,
            message = "   ",
            logTail = logTail,
        ) as OruFailure.Error

        assertEquals("[INFO] two\n[ERROR] three\n[ERROR] four", failure.summary)
    }

    @Test
    fun `nothing to show gives no summary`() {
        val failure = oruFailureOf(
            memoryOffer = null,
            failedPatch = null,
            message = null,
            logTail = listOf(" ", ""),
        ) as OruFailure.Error

        assertNull(failure.summary)
    }
}
