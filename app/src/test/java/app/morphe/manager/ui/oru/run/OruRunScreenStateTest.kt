/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import kotlin.test.Test
import kotlin.test.assertEquals

class OruRunScreenStateTest {

    @Test
    fun `each hero state shows its own view`() {
        assertEquals(OruRunScreenState.Idle, oruRunScreenStateOf(OruRunStateUi(OruHeroState.READY, false)))
        assertEquals(OruRunScreenState.Working, oruRunScreenStateOf(OruRunStateUi(OruHeroState.WORKING, false)))
        assertEquals(OruRunScreenState.Done, oruRunScreenStateOf(OruRunStateUi(OruHeroState.DONE, false)))
        assertEquals(OruRunScreenState.Cancelled, oruRunScreenStateOf(OruRunStateUi(OruHeroState.CANCELLED, false)))
    }

    @Test
    fun `a failed run keeps the memory offer decision`() {
        assertEquals(
            OruRunScreenState.Failed(offerMemoryRetry = true),
            oruRunScreenStateOf(OruRunStateUi(OruHeroState.FAILED, true)),
        )
        assertEquals(
            OruRunScreenState.Failed(offerMemoryRetry = false),
            oruRunScreenStateOf(OruRunStateUi(OruHeroState.FAILED, false)),
        )
    }

    @Test
    fun `a cancelled run never carries a memory offer`() {
        // The data layer only sets the offer on a failure, so a cancelled state has no offer to keep.
        val run = oruRunStateOf(
            isPatching = false,
            succeeded = null,
            cancelled = true,
            resultShown = false,
            memoryOfferAvailable = true,
        )

        assertEquals(OruRunScreenState.Cancelled, oruRunScreenStateOf(run))
    }
}
