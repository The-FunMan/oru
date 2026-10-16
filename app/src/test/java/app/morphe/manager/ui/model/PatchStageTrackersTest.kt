/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import app.morphe.manager.patcher.logger.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageLogTrackerTest {

    @Test
    fun `keeps only the last fifty lines of a stage`() {
        val tracker = StageLogTracker()
        repeat(STAGE_LOG_LIMIT + 5) { tracker.append(StepId.EXECUTE_PATCHES, LogLevel.INFO, "line $it") }

        val stage = tracker.snapshot().getValue(StepId.EXECUTE_PATCHES)
        assertEquals(STAGE_LOG_LIMIT, stage.lines.size)
        assertEquals("line 5", stage.lines.first().second)
        assertEquals("line ${STAGE_LOG_LIMIT + 4}", stage.lines.last().second)
    }

    @Test
    fun `counts warnings per stage and not the lines of other stages`() {
        val tracker = StageLogTracker()
        tracker.append(StepId.READ_APK, LogLevel.WARN, "a")
        tracker.append(StepId.EXECUTE_PATCHES, LogLevel.WARN, "b")
        tracker.append(StepId.EXECUTE_PATCHES, LogLevel.WARN, "c")
        tracker.append(StepId.EXECUTE_PATCHES, LogLevel.INFO, "d")

        val snapshot = tracker.snapshot()
        assertEquals(1, snapshot.getValue(StepId.READ_APK).warningCount)
        assertEquals(2, snapshot.getValue(StepId.EXECUTE_PATCHES).warningCount)
        assertEquals(3, snapshot.getValue(StepId.EXECUTE_PATCHES).lines.size)
    }

    @Test
    fun `clear drops lines and counts`() {
        val tracker = StageLogTracker()
        tracker.append(StepId.READ_APK, LogLevel.WARN, "a")
        tracker.clear()

        assertEquals(emptyMap(), tracker.snapshot())
    }

    @Test
    fun `snapshot is a copy that later appends do not change`() {
        val tracker = StageLogTracker()
        tracker.append(StepId.READ_APK, LogLevel.INFO, "a")
        val before = tracker.snapshot()

        tracker.append(StepId.READ_APK, LogLevel.INFO, "b")

        assertEquals(1, before.getValue(StepId.READ_APK).lines.size)
    }
}

class StepTimingTrackerTest {

    private fun step(id: StepId, state: State) = Step(
        id = id,
        name = id.name,
        category = StepCategory.PREPARING,
        progressPercentage = 0.0,
        state = state
    )

    @Test
    fun `a step gets a start when it runs and an end when it completes`() {
        val tracker = StepTimingTracker()

        val running = tracker.sync(listOf(step(StepId.LOAD_PATCHES, State.RUNNING)), now = 100)
        assertEquals(StepTiming(startedAtMs = 100), running[StepId.LOAD_PATCHES])

        val done = tracker.sync(listOf(step(StepId.LOAD_PATCHES, State.COMPLETED)), now = 350)
        assertEquals(250L, done.getValue(StepId.LOAD_PATCHES).durationMs)
    }

    @Test
    fun `the next step starts where the previous one ended`() {
        val tracker = StepTimingTracker()
        tracker.sync(listOf(step(StepId.LOAD_PATCHES, State.RUNNING), step(StepId.READ_APK, State.WAITING)), now = 0)

        val timings = tracker.sync(
            listOf(step(StepId.LOAD_PATCHES, State.COMPLETED), step(StepId.READ_APK, State.RUNNING)),
            now = 900
        )

        assertEquals(900L, timings.getValue(StepId.LOAD_PATCHES).endedAtMs)
        assertEquals(StepTiming(startedAtMs = 900), timings.getValue(StepId.READ_APK))
    }

    @Test
    fun `waiting steps have no timing yet`() {
        val tracker = StepTimingTracker()

        val timings = tracker.sync(listOf(step(StepId.WRITE_PATCHED_APK, State.WAITING)), now = 5)

        assertNull(timings[StepId.WRITE_PATCHED_APK])
    }

    @Test
    fun `a split step added already completed is recorded as instant`() {
        val tracker = StepTimingTracker()

        val timings = tracker.sync(listOf(step(StepId.PREPARE_SPLIT_APK, State.COMPLETED)), now = 40)

        assertEquals(StepTiming(startedAtMs = 40, endedAtMs = 40), timings.getValue(StepId.PREPARE_SPLIT_APK))
    }

    @Test
    fun `a split step removed mid run loses its timing`() {
        val tracker = StepTimingTracker()
        tracker.sync(
            listOf(step(StepId.LOAD_PATCHES, State.COMPLETED), step(StepId.PREPARE_SPLIT_APK, State.RUNNING)),
            now = 10
        )

        val timings = tracker.sync(listOf(step(StepId.LOAD_PATCHES, State.COMPLETED)), now = 20)

        assertNull(timings[StepId.PREPARE_SPLIT_APK])
        assertEquals(setOf(StepId.LOAD_PATCHES), timings.keys)
    }

    @Test
    fun `clear makes a restarted step start from zero`() {
        val tracker = StepTimingTracker()
        tracker.sync(listOf(step(StepId.READ_APK, State.COMPLETED)), now = 10)
        tracker.clear()

        val timings = tracker.sync(listOf(step(StepId.READ_APK, State.RUNNING)), now = 500)

        assertEquals(StepTiming(startedAtMs = 500), timings.getValue(StepId.READ_APK))
    }
}
