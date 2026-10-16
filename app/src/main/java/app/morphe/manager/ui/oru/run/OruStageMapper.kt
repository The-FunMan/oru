/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.R
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.model.Step
import app.morphe.manager.ui.model.StageLog
import app.morphe.manager.ui.model.StepId
import app.morphe.manager.ui.model.StepTiming

/** How one stage of the run looks in the stage rail. */
enum class OruStageState { PENDING, ACTIVE, DONE, ERROR }

/** One row of the stage rail. Ids come from [StepId], never from the step name the runtime overwrites. */
data class OruStageUi(
    val id: StepId,
    val labelRes: Int,
    val state: OruStageState,
    val startedAtMs: Long?,
    val durationMs: Long?,
    val warningCount: Int,
    val logLines: List<Pair<LogLevel, String>>
)

/**
 * Maps the run's steps, per-stage logs and timings to the stage rail, in the order of [steps].
 *
 * @param runFailed whether the run failed. A failure marks the stage that was running as an error.
 * @param labelRes the string resource of each stage's label, see [oruStageLabelRes].
 */
fun oruStages(
    steps: List<Step>,
    stageLogs: Map<StepId, StageLog>,
    stepTimings: Map<StepId, StepTiming>,
    runFailed: Boolean,
    labelRes: (StepId) -> Int
): List<OruStageUi> = steps.map { step ->
    val log = stageLogs[step.id] ?: StageLog()
    val timing = stepTimings[step.id]

    val state = when (step.state) {
        State.WAITING -> OruStageState.PENDING
        State.RUNNING -> if (runFailed) OruStageState.ERROR else OruStageState.ACTIVE
        State.FAILED -> OruStageState.ERROR
        State.COMPLETED -> OruStageState.DONE
    }

    OruStageUi(
        id = step.id,
        labelRes = labelRes(step.id),
        state = state,
        startedAtMs = timing?.startedAtMs,
        durationMs = timing?.durationMs,
        warningCount = log.warningCount,
        logLines = log.lines
    )
}

/** The label string of each stage, from oru_strings.xml. */
fun oruStageLabelRes(id: StepId): Int = when (id) {
    StepId.LOAD_PATCHES -> R.string.oru_stage_load_patches
    StepId.PREPARE_SPLIT_APK -> R.string.oru_stage_split_apk
    StepId.READ_APK -> R.string.oru_stage_read_apk
    StepId.EXECUTE_PATCHES -> R.string.oru_stage_execute_patches
    StepId.WRITE_PATCHED_APK -> R.string.oru_stage_write_apk
    StepId.SIGN_PATCHED_APK -> R.string.oru_stage_sign_apk
}

/** The plain-language line of each stage under the run title, see [oruRunLineRes]. */
fun oruRunLineLabelRes(id: StepId): Int = when (id) {
    StepId.LOAD_PATCHES -> R.string.oru_stage_load_patches
    StepId.PREPARE_SPLIT_APK -> R.string.oru_stage_split_apk
    StepId.READ_APK -> R.string.oru_run_line_reading_app
    StepId.EXECUTE_PATCHES -> R.string.oru_stage_execute_patches
    StepId.WRITE_PATCHED_APK -> R.string.oru_run_line_writing_app
    StepId.SIGN_PATCHED_APK -> R.string.oru_run_line_signing_app
}

/**
 * The string of the line under the run title while a run works. Every state has one:
 * - a stage that runs, or failed, names itself, see [labelRes];
 * - between stages, once a stage has finished, the run reads as almost there;
 * - before the first stage starts, it reads as starting.
 *
 * Pure, so the screen only draws the result. The counts of applying patches are added by the screen.
 */
fun oruRunLineRes(
    stages: List<OruStageUi>,
    labelRes: (StepId) -> Int = ::oruRunLineLabelRes,
    almostThereRes: Int = R.string.oru_run_line_almost_there,
    startingRes: Int = R.string.oru_run_starting,
): Int {
    val current = stages.firstOrNull { it.state == OruStageState.ACTIVE || it.state == OruStageState.ERROR }
    return when {
        current != null -> labelRes(current.id)
        stages.any { it.state == OruStageState.DONE } -> almostThereRes
        else -> startingRes
    }
}
