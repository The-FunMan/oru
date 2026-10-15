/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

/** The one hero state the Oru run screen shows. */
enum class OruHeroState { READY, WORKING, DONE, FAILED, CANCELLED }

/**
 * The hero state, and whether a failed run offers the lower memory retry. The offer is a detail of
 * the failure, so it never changes the hero state.
 */
data class OruRunStateUi(val hero: OruHeroState, val offerMemoryRetry: Boolean)

/**
 * Maps the existing run result states to the hero state.
 *
 * @param isPatching the run is preparing or working (`PatcherViewModel.isPatching`).
 * @param succeeded `PatcherViewModel.patcherSucceeded`: null while no result is known.
 * @param cancelled `PatcherViewModel.patcherCancelled`.
 * @param resultShown the result screen is up. A success counts as done only from here on, so the
 * two second success beat (`PatcherViewModel.scheduleSuccessScreen`) still reads as working.
 * @param memoryOfferAvailable a failed run can be retried with a lower memory limit.
 */
fun oruRunStateOf(
    isPatching: Boolean,
    succeeded: Boolean?,
    cancelled: Boolean,
    resultShown: Boolean,
    memoryOfferAvailable: Boolean
): OruRunStateUi = when {
    cancelled -> OruRunStateUi(OruHeroState.CANCELLED, offerMemoryRetry = false)
    succeeded == true && resultShown -> OruRunStateUi(OruHeroState.DONE, offerMemoryRetry = false)
    succeeded == false -> OruRunStateUi(OruHeroState.FAILED, offerMemoryRetry = memoryOfferAvailable)
    isPatching || succeeded == true -> OruRunStateUi(OruHeroState.WORKING, offerMemoryRetry = false)
    else -> OruRunStateUi(OruHeroState.READY, offerMemoryRetry = false)
}
