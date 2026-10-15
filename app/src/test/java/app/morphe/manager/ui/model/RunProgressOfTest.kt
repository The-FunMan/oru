/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class RunProgressOfTest {

    private fun step(id: StepId, category: StepCategory, share: Double, state: State) =
        Step(id = id, name = id.name, category = category, progressPercentage = share, state = state)

    /** The pipeline of [generatePatchSteps]: load 0.05, read 0.05, patches take the rest, write 0.4, sign 0.1. */
    private fun pipeline(
        split: State? = null,
        execute: State = State.COMPLETED,
        write: State = State.WAITING,
        sign: State = State.WAITING,
    ): List<Step> = listOfNotNull(
        step(StepId.LOAD_PATCHES, StepCategory.PREPARING, 0.05, State.COMPLETED),
        split?.let { step(StepId.PREPARE_SPLIT_APK, StepCategory.PREPARING, 0.1, it) },
        step(StepId.READ_APK, StepCategory.PREPARING, 0.05, State.COMPLETED),
        step(StepId.EXECUTE_PATCHES, StepCategory.PATCHING, 0.0, execute),
        step(StepId.WRITE_PATCHED_APK, StepCategory.SAVING, 0.4, write),
        step(StepId.SIGN_PATCHED_APK, StepCategory.SAVING, 0.1, sign),
    )

    private fun assertNear(expected: Float, actual: Float, message: String? = null) {
        assertTrue(abs(expected - actual) < 1e-5f, "${message.orEmpty()} expected $expected but was $actual")
    }

    @Test
    fun `the bar stops at half when the patches are applied and the write has not reported`() {
        val progress = runProgressOf(pipeline(write = State.RUNNING), completedPatches = 10, totalPatches = 10)

        assertNear(0.5f, progress, "write without a fraction")
    }

    @Test
    fun `a running write moves with the fraction it reports`() {
        val steps = pipeline(write = State.RUNNING)
        val fractions = mapOf(StepId.WRITE_PATCHED_APK to 0.25f)

        assertNear(0.6f, runProgressOf(steps, 10, 10, fractions), "write at a quarter")
    }

    @Test
    fun `the write fraction maps from zero to one monotonically onto its share`() {
        val steps = pipeline(write = State.RUNNING)
        var previous = -1f
        for (tick in 0..100) {
            val fraction = tick / 100f
            val progress = runProgressOf(steps, 10, 10, mapOf(StepId.WRITE_PATCHED_APK to fraction))

            assertTrue(progress >= previous, "fraction $fraction went back from $previous to $progress")
            previous = progress
        }
        assertNear(0.5f, runProgressOf(steps, 10, 10, mapOf(StepId.WRITE_PATCHED_APK to 0f)), "start")
        assertNear(0.9f, runProgressOf(steps, 10, 10, mapOf(StepId.WRITE_PATCHED_APK to 1f)), "end")
    }

    @Test
    fun `a stage fraction outside the range is clamped`() {
        val steps = pipeline(write = State.RUNNING)

        assertNear(0.5f, runProgressOf(steps, 10, 10, mapOf(StepId.WRITE_PATCHED_APK to -1f)), "below zero")
        assertNear(0.9f, runProgressOf(steps, 10, 10, mapOf(StepId.WRITE_PATCHED_APK to 3f)), "above one")
    }

    @Test
    fun `a NaN fraction counts as no report`() {
        val steps = pipeline(write = State.RUNNING)

        assertNear(0.5f, runProgressOf(steps, 10, 10, mapOf(StepId.WRITE_PATCHED_APK to Float.NaN)))
    }

    @Test
    fun `a completed stage counts its full share whatever fraction it last reported`() {
        val steps = pipeline(write = State.COMPLETED, sign = State.RUNNING)
        val fractions = mapOf(StepId.WRITE_PATCHED_APK to 0.3f, StepId.SIGN_PATCHED_APK to 0.5f)

        assertNear(0.95f, runProgressOf(steps, 10, 10, fractions), "write done, sign half")
    }

    @Test
    fun `the sign stage moves with its own fraction after the write is done`() {
        val steps = pipeline(write = State.COMPLETED, sign = State.RUNNING)

        assertNear(0.9f, runProgressOf(steps, 10, 10), "sign without a fraction")
    }

    @Test
    fun `a split step takes its share from the patches and does not overshoot`() {
        val withSplit = pipeline(split = State.COMPLETED, write = State.WAITING)

        assertNear(0.5f, runProgressOf(withSplit, 10, 10), "all patches done")
        assertNear(0.35f, runProgressOf(pipeline(split = State.COMPLETED, execute = State.RUNNING), 5, 10), "half the patches")
    }

    @Test
    fun `patches count by the share the fixed steps leave, and zero patches counts as none`() {
        assertNear(0.3f, runProgressOf(pipeline(execute = State.RUNNING), 5, 10), "half the patches")
        assertNear(0.1f, runProgressOf(pipeline(execute = State.RUNNING), 0, 0), "no patches yet")
    }

    @Test
    fun `a finished run reads as one`() {
        val steps = pipeline(execute = State.COMPLETED, write = State.COMPLETED, sign = State.COMPLETED)

        assertNear(1f, runProgressOf(steps, 10, 10), "finished")
    }
}
