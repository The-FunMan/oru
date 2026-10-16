/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.model.Step
import app.morphe.manager.ui.model.StepCategory
import app.morphe.manager.ui.model.StepId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OruRunFillTest {

    private fun step(id: StepId, category: StepCategory, share: Double, state: State) =
        Step(id = id, name = id.name, category = category, progressPercentage = share, state = state)

    /** The pipeline of a run: load 0.05, read 0.05, patches take the rest, write 0.4, sign 0.1, split 0.1 when present. */
    private fun pipeline(split: Boolean = false): List<Step> = listOfNotNull(
        step(StepId.LOAD_PATCHES, StepCategory.PREPARING, 0.05, State.COMPLETED),
        if (split) step(StepId.PREPARE_SPLIT_APK, StepCategory.PREPARING, 0.1, State.COMPLETED) else null,
        step(StepId.READ_APK, StepCategory.PREPARING, 0.05, State.COMPLETED),
        step(StepId.EXECUTE_PATCHES, StepCategory.PATCHING, 0.0, State.COMPLETED),
        step(StepId.WRITE_PATCHED_APK, StepCategory.SAVING, 0.4, State.COMPLETED),
        step(StepId.SIGN_PATCHED_APK, StepCategory.SAVING, 0.1, State.COMPLETED),
    )

    private fun runUi(steps: List<Step>, progress: Float, installAhead: Boolean = true) = oruRunUi(
        run = OruRunStateUi(OruHeroState.WORKING, offerMemoryRetry = false),
        steps = steps,
        stageLogs = emptyMap(),
        stepTimings = emptyMap(),
        patchesDone = 10,
        patchesTotal = 10,
        progress = progress,
        installAhead = installAhead,
        labelRes = { 0 },
    )

    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertTrue(abs(expected - actual) < 1e-5f, "$message: expected $expected but was $actual")
    }

    @Test
    fun `with an install ahead the run stops short of full at the end of the signing`() {
        val signed = runUi(pipeline(), progress = 1f)

        assertNear(0.9f, signed.progress, "the signed run")
        assertNear(1f, signed.progress + OruInstallShare, "the install takes the rest")
        assertTrue(signed.progress < 1f)
    }

    @Test
    fun `without an install the run fills to full at the end of the signing`() {
        assertNear(1f, runUi(pipeline(), progress = 1f, installAhead = false).progress, "no install")
    }

    @Test
    fun `the fill rises with the patch run and never goes back`() {
        var previous = -1f
        for (tick in 0..1000) {
            val fill = runUi(pipeline(), progress = tick / 1000f).progress
            assertTrue(fill >= previous, "progress ${tick / 1000f} went back from $previous to $fill")
            previous = fill
        }
    }

    @Test
    fun `out of range and NaN patch runs read as the nearest whole run`() {
        assertNear(0f, oruRunFill(-1f, installAhead = true), "below zero")
        assertNear(0f, oruRunFill(Float.NaN, installAhead = true), "NaN")
        assertNear(0.9f, oruRunFill(3f, installAhead = true), "above one")
    }

    @Test
    fun `the patch stage fills its share of the run, less the install`() {
        assertNear(0.4f, oruPatchShare(pipeline()), "no split")
        assertNear(0.3f, oruPatchShare(pipeline(split = true)), "with the split step")
        assertNear(0.36f, runUi(pipeline(), progress = 0f).patchShare, "the patch stage in the fill of an install run")
        assertNear(0.27f, runUi(pipeline(split = true), progress = 0f).patchShare, "split, in the fill")
        assertNear(0.4f, runUi(pipeline(), progress = 0f, installAhead = false).patchShare, "no install")
    }
}
