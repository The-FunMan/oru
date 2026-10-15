/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import kotlin.test.Test
import kotlin.test.assertEquals

class OruRunStateTest {

    private fun stateOf(
        isPatching: Boolean = false,
        succeeded: Boolean? = null,
        cancelled: Boolean = false,
        resultShown: Boolean = false,
        memoryOffer: Boolean = false
    ) = oruRunStateOf(
        isPatching = isPatching,
        succeeded = succeeded,
        cancelled = cancelled,
        resultShown = resultShown,
        memoryOfferAvailable = memoryOffer
    )

    @Test
    fun `nothing started and nothing running is ready`() {
        assertEquals(OruRunStateUi(OruHeroState.READY, false), stateOf())
    }

    @Test
    fun `a running attempt is working`() {
        assertEquals(OruRunStateUi(OruHeroState.WORKING, false), stateOf(isPatching = true))
    }

    @Test
    fun `a cancelled run is cancelled even when the worker still reports patching`() {
        assertEquals(
            OruRunStateUi(OruHeroState.CANCELLED, false),
            stateOf(isPatching = false, cancelled = true)
        )
    }

    @Test
    fun `a success is still working during the two second beat`() {
        assertEquals(
            OruRunStateUi(OruHeroState.WORKING, false),
            stateOf(isPatching = false, succeeded = true, resultShown = false)
        )
    }

    @Test
    fun `a success is done once its result is shown`() {
        assertEquals(
            OruRunStateUi(OruHeroState.DONE, false),
            stateOf(isPatching = false, succeeded = true, resultShown = true)
        )
    }

    @Test
    fun `a failure is failed and offers the memory retry only when one is available`() {
        assertEquals(
            OruRunStateUi(OruHeroState.FAILED, offerMemoryRetry = true),
            stateOf(isPatching = false, succeeded = false, memoryOffer = true)
        )
        assertEquals(
            OruRunStateUi(OruHeroState.FAILED, offerMemoryRetry = false),
            stateOf(isPatching = false, succeeded = false, memoryOffer = false)
        )
    }

    @Test
    fun `a failure is never shown as working while the worker still reports patching`() {
        assertEquals(
            OruRunStateUi(OruHeroState.FAILED, false),
            stateOf(isPatching = true, succeeded = false)
        )
    }

    @Test
    fun `the memory offer never turns a non failed state into a retry offer`() {
        assertEquals(
            OruRunStateUi(OruHeroState.WORKING, false),
            stateOf(isPatching = true, memoryOffer = true)
        )
    }
}
