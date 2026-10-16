/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.R
import app.morphe.manager.ui.model.StepId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruRunLinePartsTest {

    private fun stage(id: StepId, state: OruStageState) = OruStageUi(
        id = id,
        labelRes = oruStageLabelRes(id),
        state = state,
        startedAtMs = null,
        durationMs = null,
        warningCount = 0,
        logLines = emptyList()
    )

    /** A working run whose current stage is the first one that is active or failed, as [oruRunUi] assembles it. */
    private fun workingRun(
        stages: List<OruStageUi>,
        patchesDone: Int = 0,
        patchesTotal: Int = 0,
    ) = OruRunUi(
        hero = OruHeroState.WORKING,
        offerMemoryRetry = false,
        patchesDone = patchesDone,
        patchesTotal = patchesTotal,
        currentStageLabelRes = stages.firstOrNull {
            it.state == OruStageState.ACTIVE || it.state == OruStageState.ERROR
        }?.labelRes,
        progress = 0f,
        stages = stages,
    )

    @Test
    fun `applying patches splits into the stage name and the count`() {
        val parts = oruRunLineParts(
            workingRun(
                stages = listOf(
                    stage(StepId.LOAD_PATCHES, OruStageState.DONE),
                    stage(StepId.READ_APK, OruStageState.DONE),
                    stage(StepId.EXECUTE_PATCHES, OruStageState.ACTIVE),
                ),
                patchesDone = 66,
                patchesTotal = 96,
            ),
        )

        assertEquals(OruRunLineParts(R.string.oru_stage_execute_patches, 66, 96), parts)
    }

    @Test
    fun `a failed patch keeps the count of the patches applied`() {
        val parts = oruRunLineParts(
            workingRun(
                stages = listOf(stage(StepId.EXECUTE_PATCHES, OruStageState.ERROR)),
                patchesDone = 3,
                patchesTotal = 10,
            ),
        )

        assertEquals(OruRunLineParts(R.string.oru_stage_execute_patches, 3, 10), parts)
    }

    @Test
    fun `applying patches without a known total has no count`() {
        val parts = oruRunLineParts(
            workingRun(stages = listOf(stage(StepId.EXECUTE_PATCHES, OruStageState.ACTIVE))),
        )

        assertEquals(OruRunLineParts(R.string.oru_stage_execute_patches), parts)
    }

    @Test
    fun `a stage that is not applying patches has no count, even with a total left over`() {
        val parts = oruRunLineParts(
            workingRun(
                stages = listOf(
                    stage(StepId.EXECUTE_PATCHES, OruStageState.DONE),
                    stage(StepId.WRITE_PATCHED_APK, OruStageState.ACTIVE),
                ),
                patchesDone = 96,
                patchesTotal = 96,
            ),
        )

        assertEquals(OruRunLineParts(R.string.oru_run_line_writing_app), parts)
    }

    @Test
    fun `a stage without a count reads its own line`() {
        val parts = oruRunLineParts(
            workingRun(stages = listOf(stage(StepId.READ_APK, OruStageState.ACTIVE))),
        )

        assertEquals(OruRunLineParts(R.string.oru_run_line_reading_app), parts)
    }

    @Test
    fun `between stages the line is almost there, with no count`() {
        val parts = oruRunLineParts(
            workingRun(
                stages = listOf(
                    stage(StepId.LOAD_PATCHES, OruStageState.DONE),
                    stage(StepId.READ_APK, OruStageState.PENDING),
                ),
            ),
        )

        assertEquals(OruRunLineParts(R.string.oru_run_line_almost_there), parts)
    }

    @Test
    fun `a fact that fits keeps its size`() {
        assertEquals(20f, oruFactFontSp(baseSp = 20f, minSp = 10f, measuredPx = 80, availablePx = 100))
    }

    @Test
    fun `a fact that does not fit scales down to the space`() {
        val sp = oruFactFontSp(baseSp = 20f, minSp = 10f, measuredPx = 160, availablePx = 100)

        assertTrue(sp < 20f && sp > 10f, "expected a size between the minimum and the base, was $sp")
    }

    @Test
    fun `a fact never scales below its minimum`() {
        assertEquals(10f, oruFactFontSp(baseSp = 20f, minSp = 10f, measuredPx = 1600, availablePx = 100))
    }

    @Test
    fun `an unmeasured fact keeps its size`() {
        assertEquals(20f, oruFactFontSp(baseSp = 20f, minSp = 10f, measuredPx = 0, availablePx = 100))
    }
}
