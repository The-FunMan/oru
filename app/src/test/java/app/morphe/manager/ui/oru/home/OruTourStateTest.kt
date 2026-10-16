/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruTourStateTest {

    private fun input(
        vararg statuses: OruHomeCardStatus,
        loading: Boolean = false,
        runActive: Boolean = false,
    ) = OruTourInput(loading = loading, statuses = statuses.toList(), runActive = runActive)

    private val fresh = OruTourState()

    // Visibility matrix

    @Test
    fun freshInstallShowsOnNewCards() {
        assertTrue(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.NeedsApk, OruHomeCardStatus.ReadyOnDevice)))
    }

    @Test
    fun installedCardHidesTheTour() {
        assertFalse(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.NeedsApk, OruHomeCardStatus.Installed)))
    }

    @Test
    fun updateAvailableCardHidesTheTour() {
        assertFalse(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.UpdateAvailable)))
    }

    @Test
    fun patchedElsewhereCardHidesTheTour() {
        assertFalse(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.PatchedElsewhere)))
    }

    @Test
    fun savedCopyHidesTheTour() {
        assertFalse(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.SavedCopy)))
        assertFalse(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.SavedCopyGated)))
    }

    @Test
    fun unpatchedInstalledDoesNotHideTheTour() {
        assertTrue(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.UnpatchedInstalled)))
    }

    @Test
    fun storedDoneFlagHidesTheTour() {
        assertFalse(oruTourVisible(done = true, state = fresh, input = input(OruHomeCardStatus.NeedsApk)))
    }

    @Test
    fun dismissedStateHidesTheTour() {
        val dismissed = fresh.dismiss()
        assertFalse(oruTourVisible(done = false, state = dismissed, input = input(OruHomeCardStatus.NeedsApk)))
    }

    @Test
    fun loadingListHidesTheTour() {
        assertFalse(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.NeedsApk, loading = true)))
    }

    @Test
    fun emptyListHidesTheTour() {
        assertFalse(oruTourVisible(done = false, state = fresh, input = input()))
    }

    @Test
    fun runInProgressKeepsTheTour() {
        assertTrue(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.NeedsApk, runActive = true)))
    }

    @Test
    fun finishedRunWaitingForInstallKeepsTheTour() {
        assertTrue(oruTourVisible(done = false, state = fresh, input = input(OruHomeCardStatus.ReadyToInstall)))
    }

    @Test
    fun patchedCardIsRecognised() {
        assertTrue(oruTourPatchedOn(listOf(OruHomeCardStatus.NeedsApk, OruHomeCardStatus.Installed)))
        assertTrue(oruTourPatchedOn(listOf(OruHomeCardStatus.UpdateAvailable)))
        assertTrue(oruTourPatchedOn(listOf(OruHomeCardStatus.PatchedElsewhere)))
        assertFalse(oruTourPatchedOn(listOf(OruHomeCardStatus.NeedsApk, OruHomeCardStatus.ReadyToInstall)))
    }

    // Step derivation

    @Test
    fun derivedStepFollowsTheRunAndTheInstall() {
        assertEquals(OruTourStep.Find, oruTourDerivedStep(input(OruHomeCardStatus.NeedsApk)))
        assertEquals(OruTourStep.Find, oruTourDerivedStep(input(OruHomeCardStatus.Waiting)))
        assertEquals(OruTourStep.Patch, oruTourDerivedStep(input(OruHomeCardStatus.NeedsApk, runActive = true)))
        assertEquals(OruTourStep.Install, oruTourDerivedStep(input(OruHomeCardStatus.ReadyToInstall)))
        assertEquals(OruTourStep.Install, oruTourDerivedStep(input(OruHomeCardStatus.ReadyToInstall, runActive = true)))
    }

    @Test
    fun advanceNeverMovesBack() {
        val patching = fresh.advanceTo(OruTourStep.Patch)
        assertEquals(OruTourStep.Patch, patching.step)
        assertEquals(OruTourStep.Patch, patching.advanceTo(OruTourStep.Find).step)
        assertEquals(OruTourStep.Install, patching.advanceTo(OruTourStep.Install).step)
    }

    // Reducer

    @Test
    fun nextMovesForwardAndHoldsAtTheLastStep() {
        val second = fresh.next()
        assertEquals(OruTourStep.Patch, second.step)
        assertEquals(OruTourStep.Install, second.next().step)
        assertEquals(OruTourStep.Install, second.next().next().step)
    }

    @Test
    fun goToShowsThePickedStepInEitherDirection() {
        assertEquals(OruTourStep.Install, fresh.goTo(OruTourStep.Install).step)
        assertEquals(OruTourStep.Find, fresh.goTo(OruTourStep.Install).goTo(OruTourStep.Find).step)
    }

    @Test
    fun skipAndGotItBothDismiss() {
        assertTrue(fresh.dismiss().dismissed)
        assertTrue(fresh.next().next().dismiss().dismissed)
    }

    @Test
    fun dismissKeepsTheStep() {
        assertEquals(OruTourStep.Patch, fresh.next().dismiss().step)
    }

    @Test
    fun tourStepNextIsTheSequenceOfSteps() {
        assertEquals(OruTourStep.Patch, OruTourStep.Find.next())
        assertEquals(OruTourStep.Install, OruTourStep.Patch.next())
        assertEquals(OruTourStep.Install, OruTourStep.Install.next())
    }
}
