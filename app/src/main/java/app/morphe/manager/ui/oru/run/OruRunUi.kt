/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.ui.model.Step
import app.morphe.manager.ui.model.StageLog
import app.morphe.manager.ui.model.StepId
import app.morphe.manager.ui.model.StepTiming

/**
 * Everything the Oru run screen shows, as plain data.
 *
 * @property patchesDone patches applied so far, shown as "n of total".
 * @property currentStageLabelRes the label of the stage that is running or failed, null when none is.
 * @property progress [0, 1] across the whole run, the fill of the hexagon and the percentage (see [oruRunFill]).
 * @property failedPatchIndex the position of the patch that stopped the run, or null when no patch failed. A
 * failed patch is the next one after the completed count, so it equals [patchesDone].
 * @property patchShare the share of the whole run the patch stage fills, in the same units as [progress].
 */
data class OruRunUi(
    val hero: OruHeroState,
    val offerMemoryRetry: Boolean,
    val patchesDone: Int,
    val patchesTotal: Int,
    val currentStageLabelRes: Int?,
    val progress: Float,
    val stages: List<OruStageUi>,
    val failedPatchIndex: Int? = null,
    val patchShare: Float = 0f,
)

/**
 * Assembles [OruRunUi] from the run's state. Pure, so the screen only renders the result. [progress] is the patch run's
 * own progress, mapped onto the whole run by [installAhead]: a single run always goes on to the install.
 */
fun oruRunUi(
    run: OruRunStateUi,
    steps: List<Step>,
    stageLogs: Map<StepId, StageLog>,
    stepTimings: Map<StepId, StepTiming>,
    patchesDone: Int,
    patchesTotal: Int,
    progress: Float,
    installAhead: Boolean = true,
    labelRes: (StepId) -> Int = ::oruStageLabelRes
): OruRunUi {
    val stages = oruStages(
        steps = steps,
        stageLogs = stageLogs,
        stepTimings = stepTimings,
        runFailed = run.hero == OruHeroState.FAILED,
        labelRes = labelRes
    )

    val patchFailed = stages.any { it.id == StepId.EXECUTE_PATCHES && it.state == OruStageState.ERROR }

    return OruRunUi(
        hero = run.hero,
        offerMemoryRetry = run.offerMemoryRetry,
        patchesDone = patchesDone,
        patchesTotal = patchesTotal,
        currentStageLabelRes = stages.firstOrNull {
            it.state == OruStageState.ACTIVE || it.state == OruStageState.ERROR
        }?.labelRes,
        progress = oruRunFill(progress, installAhead),
        stages = stages,
        failedPatchIndex = patchesDone.takeIf { patchFailed },
        patchShare = oruRunFill(oruPatchShare(steps), installAhead),
    )
}
