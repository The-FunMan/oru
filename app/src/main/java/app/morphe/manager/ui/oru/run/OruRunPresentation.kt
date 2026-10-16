/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.ui.oru.components.OruHeroState as ComponentHeroState
import app.morphe.manager.ui.oru.components.OruStageLine
import app.morphe.manager.ui.oru.components.OruStageState as ComponentStageState
import app.morphe.manager.ui.oru.components.OruStageUi as ComponentStageUi
import app.morphe.manager.ui.oru.components.OruUsageGraphs
import app.morphe.manager.ui.model.IoSample
import app.morphe.manager.ui.model.PatchProgressSource
import app.morphe.manager.ui.model.StepId
import kotlin.math.roundToInt

/**
 * Usage samples of the run for the readout, read from the live source. [heapLimitMb] and [peakHeapMb]
 * are null while they are not known yet, so the readout shows a dash rather than a zero.
 */
data class OruRunUsage(
    val heapSamples: List<Int>,
    val heapLimitMb: Int?,
    val peakHeapMb: Int?,
    val cpuCoreLoads: List<Int>,
    val ioSamples: List<IoSample>,
) {
    /** The latest heap sample in megabytes. */
    val heapMb: Int? get() = heapSamples.lastOrNull()

    /** The average load over all cores in percent, from the latest sample. */
    val cpuPercent: Int? get() = cpuCoreLoads.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    /** The latest storage throughput in kilobytes per second, reads and writes together. */
    val ioKbPerSec: Int? get() = ioSamples.lastOrNull()?.totalKbPerSec

    /** Whether there is anything to draw, so the readout only opens when it has graphs. */
    val hasGraphs: Boolean get() = heapSamples.isNotEmpty() || cpuCoreLoads.isNotEmpty() || ioSamples.isNotEmpty()

    companion object {
        /** A copy of the run's samples, so the composition reads one consistent snapshot. */
        fun of(source: PatchProgressSource): OruRunUsage = OruRunUsage(
            heapSamples = source.heapSamples.toList(),
            heapLimitMb = source.heapLimitMb.takeIf { it > 0 },
            peakHeapMb = source.peakHeapMb.takeIf { it > 0 },
            cpuCoreLoads = source.cpuCoreLoads.toList(),
            ioSamples = source.ioSamples.toList(),
        )
    }
}

/**
 * The figures of a finished run that are not in the live usage: the output file's size and the time
 * from the start of the run to its end. Null where the host could not read them.
 */
data class OruRunFacts(
    val outputBytes: Long?,
    val elapsedMs: Long?,
)

/**
 * The graphs for the readout, or null when there are no samples yet. The I/O graph holds kilobytes
 * per second, which the readout labels as KB/s.
 */
fun OruRunUsage.toGraphs(): OruUsageGraphs? = if (hasGraphs) {
    OruUsageGraphs(
        heapMb = heapSamples,
        heapLimitMb = heapLimitMb,
        cpuPercent = cpuCoreLoads,
        ioMbPerSec = ioSamples.map { it.totalKbPerSec },
    )
} else {
    null
}

/** The hero state the components draw. Cancelled is a neutral hero, since nothing failed. */
fun OruHeroState.toComponent(): ComponentHeroState = when (this) {
    OruHeroState.READY, OruHeroState.CANCELLED -> ComponentHeroState.Ready
    OruHeroState.WORKING -> ComponentHeroState.Working
    OruHeroState.DONE -> ComponentHeroState.Done
    OruHeroState.FAILED -> ComponentHeroState.Failed
}

/** The rail state of one run stage. */
fun OruStageState.toComponent(): ComponentStageState = when (this) {
    OruStageState.PENDING -> ComponentStageState.Todo
    OruStageState.ACTIVE -> ComponentStageState.Now
    OruStageState.DONE -> ComponentStageState.Done
    OruStageState.ERROR -> ComponentStageState.Error
}

/**
 * The Expert stage rail: one row per real stage, in run order, with its own log lines, timing and
 * warning count. [labelOf] resolves the stage label, so the mapping stays free of resources.
 */
fun oruRailStages(
    stages: List<OruStageUi>,
    labelOf: (StepId) -> String,
): List<ComponentStageUi> = stages.map { stage ->
    ComponentStageUi(
        id = stage.id.name,
        name = labelOf(stage.id),
        state = stage.state.toComponent(),
        duration = stage.durationMs?.let(OruRunFormat::stageDuration),
        warnings = stage.warningCount,
        lines = stage.logLines.map { (level, text) ->
            OruStageLine(text = text, warning = level == LogLevel.WARN)
        },
    )
}

/**
 * The three steps Simple mode shows. Each one groups the real stages it stands for, so the steps
 * never claim progress the run did not make.
 */
enum class OruSimpleStep(val stages: Set<StepId>) {
    PREPARE(setOf(StepId.LOAD_PATCHES, StepId.PREPARE_SPLIT_APK, StepId.READ_APK)),
    APPLY(setOf(StepId.EXECUTE_PATCHES)),
    FINISH(setOf(StepId.WRITE_PATCHED_APK, StepId.SIGN_PATCHED_APK)),
}

/**
 * The Simple steps in order. A step is done when every stage it groups is done, failed when one of
 * them failed, running while one is active or some are done, and waiting otherwise.
 */
fun oruSimpleSteps(stages: List<OruStageUi>): List<Pair<OruSimpleStep, ComponentStageState>> =
    OruSimpleStep.entries.map { step ->
        val states = stages.filter { it.id in step.stages }.map { it.state }
        step to oruGroupState(states)
    }

/** The rail state of a group of run stages, see [oruSimpleSteps]. */
internal fun oruGroupState(states: List<OruStageState>): ComponentStageState = when {
    OruStageState.ERROR in states -> ComponentStageState.Error
    OruStageState.ACTIVE in states -> ComponentStageState.Now
    states.isNotEmpty() && states.all { it == OruStageState.DONE } -> ComponentStageState.Done
    OruStageState.DONE in states -> ComponentStageState.Now
    else -> ComponentStageState.Todo
}
