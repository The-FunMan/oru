/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.model.Step
import app.morphe.manager.ui.model.StageLog
import app.morphe.manager.ui.model.StepCategory
import app.morphe.manager.ui.model.StepId
import app.morphe.manager.ui.model.StepTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OruStageMapperTest {

    private val labelOf: (StepId) -> Int = { it.ordinal }

    private fun step(id: StepId, state: State) = Step(
        id = id,
        name = "overwritten by the runtime",
        category = StepCategory.PREPARING,
        progressPercentage = 0.0,
        state = state
    )

    private fun stagesOf(
        steps: List<Step>,
        logs: Map<StepId, StageLog> = emptyMap(),
        timings: Map<StepId, StepTiming> = emptyMap(),
        failed: Boolean = false
    ) = oruStages(steps, logs, timings, failed, labelOf)

    @Test
    fun `without a split step the rail has the five real stages in order`() {
        val stages = stagesOf(
            listOf(
                step(StepId.LOAD_PATCHES, State.COMPLETED),
                step(StepId.READ_APK, State.RUNNING),
                step(StepId.EXECUTE_PATCHES, State.WAITING),
                step(StepId.WRITE_PATCHED_APK, State.WAITING),
                step(StepId.SIGN_PATCHED_APK, State.WAITING)
            )
        )

        assertEquals(
            listOf(
                StepId.LOAD_PATCHES, StepId.READ_APK, StepId.EXECUTE_PATCHES,
                StepId.WRITE_PATCHED_APK, StepId.SIGN_PATCHED_APK
            ),
            stages.map { it.id }
        )
        assertEquals(
            listOf(
                OruStageState.DONE, OruStageState.ACTIVE, OruStageState.PENDING,
                OruStageState.PENDING, OruStageState.PENDING
            ),
            stages.map { it.state }
        )
    }

    @Test
    fun `labels come from the label function, not from the step name`() {
        val stages = stagesOf(listOf(step(StepId.READ_APK, State.RUNNING)))

        assertEquals(StepId.READ_APK.ordinal, stages.single().labelRes)
    }

    @Test
    fun `split step added at the start appears after load and before read`() {
        val stages = stagesOf(
            listOf(
                step(StepId.LOAD_PATCHES, State.COMPLETED),
                step(StepId.PREPARE_SPLIT_APK, State.RUNNING),
                step(StepId.READ_APK, State.WAITING),
                step(StepId.EXECUTE_PATCHES, State.WAITING),
                step(StepId.WRITE_PATCHED_APK, State.WAITING),
                step(StepId.SIGN_PATCHED_APK, State.WAITING)
            ),
            timings = mapOf(
                StepId.LOAD_PATCHES to StepTiming(startedAtMs = 0, endedAtMs = 50),
                StepId.PREPARE_SPLIT_APK to StepTiming(startedAtMs = 50)
            )
        )

        assertEquals(StepId.PREPARE_SPLIT_APK, stages[1].id)
        assertEquals(OruStageState.ACTIVE, stages[1].state)
        assertEquals(50L, stages[1].startedAtMs)
        assertNull(stages[1].durationMs)
        assertEquals(6, stages.size)
    }

    @Test
    fun `split step removed mid run leaves no row and no timing`() {
        val stages = stagesOf(
            steps = listOf(
                step(StepId.LOAD_PATCHES, State.COMPLETED),
                step(StepId.READ_APK, State.RUNNING),
                step(StepId.EXECUTE_PATCHES, State.WAITING)
            ),
            timings = mapOf(
                StepId.LOAD_PATCHES to StepTiming(startedAtMs = 0, endedAtMs = 50),
                StepId.READ_APK to StepTiming(startedAtMs = 50)
            )
        )

        assertEquals(
            listOf(StepId.LOAD_PATCHES, StepId.READ_APK, StepId.EXECUTE_PATCHES),
            stages.map { it.id }
        )
        assertEquals(50L, stages[0].durationMs)
        assertEquals(OruStageState.ACTIVE, stages[1].state)
    }

    @Test
    fun `restart resets counts and timings`() {
        // After a restart the runtime clears the stage logs and timings, so the rail starts empty
        val stages = stagesOf(
            listOf(
                step(StepId.LOAD_PATCHES, State.RUNNING),
                step(StepId.READ_APK, State.WAITING)
            ),
            logs = emptyMap(),
            timings = emptyMap()
        )

        assertEquals(OruStageState.ACTIVE, stages[0].state)
        assertEquals(0, stages[0].warningCount)
        assertEquals(emptyList(), stages[0].logLines)
        assertNull(stages[0].startedAtMs)
        assertNull(stages[0].durationMs)
    }

    @Test
    fun `failure marks the running stage as an error and leaves the others alone`() {
        val stages = stagesOf(
            steps = listOf(
                step(StepId.LOAD_PATCHES, State.COMPLETED),
                step(StepId.READ_APK, State.COMPLETED),
                step(StepId.EXECUTE_PATCHES, State.RUNNING),
                step(StepId.WRITE_PATCHED_APK, State.WAITING)
            ),
            failed = true
        )

        assertEquals(
            listOf(
                OruStageState.DONE, OruStageState.DONE,
                OruStageState.ERROR, OruStageState.PENDING
            ),
            stages.map { it.state }
        )
    }

    @Test
    fun `a step the runtime itself marked failed is an error even without a run failure`() {
        val stages = stagesOf(listOf(step(StepId.READ_APK, State.FAILED)))

        assertEquals(OruStageState.ERROR, stages.single().state)
    }

    @Test
    fun `warnings are counted per stage`() {
        val stages = stagesOf(
            steps = listOf(
                step(StepId.READ_APK, State.COMPLETED),
                step(StepId.EXECUTE_PATCHES, State.RUNNING)
            ),
            logs = mapOf(
                StepId.READ_APK to StageLog(
                    lines = listOf(LogLevel.WARN to "a", LogLevel.INFO to "b", LogLevel.WARN to "c"),
                    warningCount = 2
                )
            )
        )

        assertEquals(2, stages[0].warningCount)
        assertEquals(3, stages[0].logLines.size)
        assertEquals(0, stages[1].warningCount)
        assertEquals(emptyList(), stages[1].logLines)
    }

    @Test
    fun `current stage label is the running stage, or the failed one`() {
        val running = oruRunUi(
            run = OruRunStateUi(OruHeroState.WORKING, offerMemoryRetry = false),
            steps = listOf(
                step(StepId.LOAD_PATCHES, State.COMPLETED),
                step(StepId.EXECUTE_PATCHES, State.RUNNING)
            ),
            stageLogs = emptyMap(),
            stepTimings = emptyMap(),
            patchesDone = 3,
            patchesTotal = 12,
            progress = 0.5f,
            labelRes = labelOf
        )
        assertEquals(StepId.EXECUTE_PATCHES.ordinal, running.currentStageLabelRes)

        val failed = oruRunUi(
            run = OruRunStateUi(OruHeroState.FAILED, offerMemoryRetry = true),
            steps = listOf(step(StepId.EXECUTE_PATCHES, State.RUNNING)),
            stageLogs = emptyMap(),
            stepTimings = emptyMap(),
            patchesDone = 3,
            patchesTotal = 12,
            progress = 0.5f,
            labelRes = labelOf
        )
        assertEquals(StepId.EXECUTE_PATCHES.ordinal, failed.currentStageLabelRes)
        assertEquals(true, failed.offerMemoryRetry)
    }
}
