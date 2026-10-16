/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.ui.model.StepId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OruRunLineTest {

    private val almostThere = 9001
    private val starting = 9002

    /** Each stage's line is its ordinal, so the test reads which stage named the line. */
    private val labelRes: (StepId) -> Int = { 100 + it.ordinal }

    private fun stage(id: StepId, state: OruStageState) = OruStageUi(
        id = id,
        labelRes = labelRes(id),
        state = state,
        startedAtMs = null,
        durationMs = null,
        warningCount = 0,
        logLines = emptyList()
    )

    private fun lineOf(vararg stages: OruStageUi) =
        oruRunLineRes(stages.toList(), labelRes, almostThere, starting)

    @Test
    fun `a running stage names itself`() {
        val line = lineOf(
            stage(StepId.LOAD_PATCHES, OruStageState.DONE),
            stage(StepId.READ_APK, OruStageState.DONE),
            stage(StepId.EXECUTE_PATCHES, OruStageState.ACTIVE),
            stage(StepId.WRITE_PATCHED_APK, OruStageState.PENDING),
            stage(StepId.SIGN_PATCHED_APK, OruStageState.PENDING),
        )

        assertEquals(labelRes(StepId.EXECUTE_PATCHES), line)
    }

    @Test
    fun `the write stage has its own line, not the line of the stage before`() {
        val line = lineOf(
            stage(StepId.EXECUTE_PATCHES, OruStageState.DONE),
            stage(StepId.WRITE_PATCHED_APK, OruStageState.ACTIVE),
            stage(StepId.SIGN_PATCHED_APK, OruStageState.PENDING),
        )

        assertEquals(labelRes(StepId.WRITE_PATCHED_APK), line)
    }

    @Test
    fun `a failed stage keeps its own line`() {
        val line = lineOf(
            stage(StepId.LOAD_PATCHES, OruStageState.DONE),
            stage(StepId.READ_APK, OruStageState.ERROR),
        )

        assertEquals(labelRes(StepId.READ_APK), line)
    }

    @Test
    fun `between stages, once one is done, the run is almost there`() {
        val line = lineOf(
            stage(StepId.EXECUTE_PATCHES, OruStageState.DONE),
            stage(StepId.WRITE_PATCHED_APK, OruStageState.PENDING),
            stage(StepId.SIGN_PATCHED_APK, OruStageState.PENDING),
        )

        assertEquals(almostThere, line)
    }

    @Test
    fun `after the last stage is done the run is almost there`() {
        val line = lineOf(
            stage(StepId.WRITE_PATCHED_APK, OruStageState.DONE),
            stage(StepId.SIGN_PATCHED_APK, OruStageState.DONE),
        )

        assertEquals(almostThere, line)
    }

    @Test
    fun `before any stage starts the run is starting`() {
        val line = lineOf(
            stage(StepId.LOAD_PATCHES, OruStageState.PENDING),
            stage(StepId.READ_APK, OruStageState.PENDING),
        )

        assertEquals(starting, line)
    }

    @Test
    fun `no stages at all is starting`() {
        assertEquals(starting, oruRunLineRes(emptyList(), labelRes, almostThere, starting))
    }

    @Test
    fun `every stage that runs has a line of its own`() {
        val lines = StepId.entries.map { id ->
            oruRunLineRes(listOf(stage(id, OruStageState.ACTIVE)), labelRes, almostThere, starting)
        }

        assertEquals(StepId.entries.size, lines.toSet().size)
        assertNotEquals(starting, lines.first())
    }

    @Test
    fun `each stage has its own default line string`() {
        // Every stage resolves to a string resource, so no stage can fall through to a blank line
        val ids = StepId.entries.map(::oruRunLineLabelRes)

        assertEquals(StepId.entries.size, ids.toSet().size)
    }
}
