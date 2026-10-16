/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunLiveBarTest {

    @Test
    fun `the segments add up to the total whether or not the input is split`() {
        val bar = RunLiveBar()
        assertEquals(listOf(10, 40, 40, 10), bar.segments)
        assertEquals(RunLiveBar.TOTAL_POINTS, bar.segments.sum())

        bar.splitInput = true
        assertEquals(listOf(20, 30, 40, 10), bar.segments)
        assertEquals(RunLiveBar.TOTAL_POINTS, bar.segments.sum())
    }

    @Test
    fun `the bar has no stage and no progress before the run starts`() {
        val bar = RunLiveBar()

        assertNull(bar.stage)
        assertEquals(0, bar.progress)
    }

    @Test
    fun `a finished stage counts its whole segment and the next one starts after it`() {
        val bar = RunLiveBar()
        bar.report(LiveBarStage.READ_LOAD, 1f)
        bar.report(LiveBarStage.PATCH, 0f)

        assertEquals(10, bar.progress)
        bar.report(LiveBarStage.PATCH, 0.5f)
        assertEquals(30, bar.progress)
        bar.report(LiveBarStage.WRITE, 1f)
        assertEquals(90, bar.progress)
        bar.report(LiveBarStage.SIGN, 1f)
        assertEquals(RunLiveBar.TOTAL_POINTS, bar.progress)
    }

    @Test
    fun `a stage fraction never falls back within its stage`() {
        val bar = RunLiveBar()
        bar.report(LiveBarStage.WRITE, 0.6f)
        val before = bar.progress

        assertFalse(bar.report(LiveBarStage.WRITE, 0.2f))
        assertEquals(before, bar.progress)
    }

    @Test
    fun `a report for a stage the run has left is ignored`() {
        val bar = RunLiveBar()
        bar.report(LiveBarStage.WRITE, 0.5f)

        assertFalse(bar.report(LiveBarStage.PATCH, 1f))
        assertEquals(LiveBarStage.WRITE, bar.stage)
    }

    @Test
    fun `a NaN or out of range fraction is clamped or ignored`() {
        val bar = RunLiveBar()
        assertFalse(bar.report(LiveBarStage.PATCH, Float.NaN))
        assertNull(bar.stage)

        bar.report(LiveBarStage.PATCH, 7f)
        assertEquals(50, bar.progress)
    }

    @Test
    fun `a report moves the bar only when the stage or the points change`() {
        val bar = RunLiveBar()
        assertTrue(bar.report(LiveBarStage.READ_LOAD, 0f))
        assertFalse(bar.report(LiveBarStage.READ_LOAD, 0f))
        assertTrue(bar.report(LiveBarStage.READ_LOAD, 0.5f))
    }

    @Test
    fun `a reset puts the bar back before its first stage`() {
        val bar = RunLiveBar()
        bar.report(LiveBarStage.WRITE, 0.5f)

        bar.reset()

        assertNull(bar.stage)
        assertEquals(0, bar.progress)
    }

    @Test
    fun `steps map onto the bar stages, and the split step belongs to reading and loading`() {
        assertEquals(LiveBarStage.READ_LOAD, StepId.LOAD_PATCHES.liveBarStage())
        assertEquals(LiveBarStage.READ_LOAD, StepId.PREPARE_SPLIT_APK.liveBarStage())
        assertEquals(LiveBarStage.READ_LOAD, StepId.READ_APK.liveBarStage())
        assertEquals(LiveBarStage.PATCH, StepId.EXECUTE_PATCHES.liveBarStage())
        assertEquals(LiveBarStage.WRITE, StepId.WRITE_PATCHED_APK.liveBarStage())
        assertEquals(LiveBarStage.SIGN, StepId.SIGN_PATCHED_APK.liveBarStage())
    }
}
