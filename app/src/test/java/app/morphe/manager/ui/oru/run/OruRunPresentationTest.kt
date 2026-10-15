/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.ui.oru.components.OruHeroState as ComponentHeroState
import app.morphe.manager.ui.oru.components.OruStageState as ComponentStageState
import app.morphe.manager.ui.model.IoSample
import app.morphe.manager.ui.model.StepId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OruRunPresentationTest {

    private fun stage(
        id: StepId,
        state: OruStageState,
        durationMs: Long? = null,
        warnings: Int = 0,
        lines: List<Pair<LogLevel, String>> = emptyList(),
    ) = OruStageUi(
        id = id,
        labelRes = 0,
        state = state,
        startedAtMs = null,
        durationMs = durationMs,
        warningCount = warnings,
        logLines = lines,
    )

    private fun usage(
        heap: List<Int> = emptyList(),
        cpu: List<Int> = emptyList(),
        io: List<IoSample> = emptyList(),
        limit: Int? = null,
    ) = OruRunUsage(
        heapSamples = heap,
        heapLimitMb = limit,
        peakHeapMb = null,
        cpuCoreLoads = cpu,
        ioSamples = io,
    )

    @Test
    fun `cancelled is a neutral hero, not a failed one`() {
        assertEquals(ComponentHeroState.Ready, OruHeroState.CANCELLED.toComponent())
        assertEquals(ComponentHeroState.Ready, OruHeroState.READY.toComponent())
        assertEquals(ComponentHeroState.Working, OruHeroState.WORKING.toComponent())
        assertEquals(ComponentHeroState.Done, OruHeroState.DONE.toComponent())
        assertEquals(ComponentHeroState.Failed, OruHeroState.FAILED.toComponent())
    }

    @Test
    fun `a stage keeps its state and maps to the rail`() {
        assertEquals(ComponentStageState.Todo, OruStageState.PENDING.toComponent())
        assertEquals(ComponentStageState.Now, OruStageState.ACTIVE.toComponent())
        assertEquals(ComponentStageState.Done, OruStageState.DONE.toComponent())
        assertEquals(ComponentStageState.Error, OruStageState.ERROR.toComponent())
    }

    @Test
    fun `the rail keeps the run order, durations, warnings and lines`() {
        val stages = listOf(
            stage(
                StepId.LOAD_PATCHES,
                OruStageState.DONE,
                durationMs = 900,
                warnings = 2,
                lines = listOf(LogLevel.INFO to "loaded", LogLevel.WARN to "old option"),
            ),
            stage(StepId.EXECUTE_PATCHES, OruStageState.ACTIVE),
        )

        val rail = oruRailStages(stages) { it.name.lowercase() }

        assertEquals(listOf("LOAD_PATCHES", "EXECUTE_PATCHES"), rail.map { it.id })
        assertEquals(listOf("load_patches", "execute_patches"), rail.map { it.name })
        assertEquals(ComponentStageState.Done, rail[0].state)
        assertEquals("0.9 s", rail[0].duration)
        assertEquals(2, rail[0].warnings)
        assertEquals(listOf("loaded", "old option"), rail[0].lines.map { it.text })
        assertEquals(listOf(false, true), rail[0].lines.map { it.warning })
        assertNull(rail[1].duration)
        assertEquals(ComponentStageState.Now, rail[1].state)
    }

    @Test
    fun `a group is done only when all of its stages are done`() {
        assertEquals(ComponentStageState.Todo, oruGroupState(emptyList()))
        assertEquals(ComponentStageState.Done, oruGroupState(listOf(OruStageState.DONE, OruStageState.DONE)))
        assertEquals(ComponentStageState.Now, oruGroupState(listOf(OruStageState.DONE, OruStageState.PENDING)))
        assertEquals(ComponentStageState.Todo, oruGroupState(listOf(OruStageState.PENDING)))
        assertEquals(ComponentStageState.Now, oruGroupState(listOf(OruStageState.ACTIVE, OruStageState.PENDING)))
    }

    @Test
    fun `an error in a group wins over an active stage`() {
        assertEquals(ComponentStageState.Error, oruGroupState(listOf(OruStageState.ERROR, OruStageState.DONE)))
        assertEquals(ComponentStageState.Error, oruGroupState(listOf(OruStageState.ERROR, OruStageState.ACTIVE)))
    }

    @Test
    fun `simple steps group the real stages and follow the run`() {
        val stages = listOf(
            stage(StepId.LOAD_PATCHES, OruStageState.DONE),
            stage(StepId.READ_APK, OruStageState.DONE),
            stage(StepId.EXECUTE_PATCHES, OruStageState.ACTIVE),
            stage(StepId.WRITE_PATCHED_APK, OruStageState.PENDING),
            stage(StepId.SIGN_PATCHED_APK, OruStageState.PENDING),
        )

        assertEquals(
            listOf(
                OruSimpleStep.PREPARE to ComponentStageState.Done,
                OruSimpleStep.APPLY to ComponentStageState.Now,
                OruSimpleStep.FINISH to ComponentStageState.Todo,
            ),
            oruSimpleSteps(stages),
        )
    }

    @Test
    fun `a split merge is part of the prepare step`() {
        val stages = listOf(
            stage(StepId.LOAD_PATCHES, OruStageState.DONE),
            stage(StepId.PREPARE_SPLIT_APK, OruStageState.ACTIVE),
            stage(StepId.READ_APK, OruStageState.PENDING),
        )

        assertEquals(ComponentStageState.Now, oruSimpleSteps(stages).first().second)
    }

    @Test
    fun `a finished run has every simple step done`() {
        val stages = StepId.entries.filter { it != StepId.PREPARE_SPLIT_APK }
            .map { stage(it, OruStageState.DONE) }

        assertEquals(
            listOf(ComponentStageState.Done, ComponentStageState.Done, ComponentStageState.Done),
            oruSimpleSteps(stages).map { it.second },
        )
    }

    @Test
    fun `no graphs before the first sample`() {
        assertNull(usage().toGraphs())
    }

    @Test
    fun `the readout takes the latest samples and the average core load`() {
        val run = usage(
            heap = listOf(100, 180),
            cpu = listOf(10, 41, 42, 48),
            io = listOf(IoSample(100, 20), IoSample(300, 200)),
        )

        assertEquals(180, run.heapMb)
        assertEquals(35, run.cpuPercent)
        assertEquals(500, run.ioKbPerSec)
    }

    @Test
    fun `figures are null before any sample and the limit is kept only when known`() {
        val empty = usage()
        assertNull(empty.heapMb)
        assertNull(empty.cpuPercent)
        assertNull(empty.ioKbPerSec)

        val graphs = usage(heap = listOf(120), limit = 1024, io = listOf(IoSample(1, 2)))
            .toGraphs()
        assertEquals(1024, graphs?.heapLimitMb)
        assertEquals(listOf(3), graphs?.ioMbPerSec)
    }
}
