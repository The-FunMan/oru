/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import app.morphe.manager.R
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.logger.logField
// ORU: heap-limit and done-line telemetry is parsed by parseRunTelemetry, see below.
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_FIELD_MAX
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_PREFIX_CURRENT
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_PREFIX_DONE
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_CPU
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_IO_READ
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_IO_WRITE
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_PREFIX_CURRENT
import app.morphe.manager.patcher.runtime.process.PatcherProcess.Companion.LOG_PROCESS_PREFIX_PROCESS_HEAP
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_PROCESS_PREFIX_COROUTINE_HEAP
import app.morphe.manager.ui.screen.patcher.LogItem
import app.morphe.manager.ui.screen.patcher.LogItemAccumulator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Morphe Patcher"

/** Lines kept per stage for the stage rail; older lines drop off. */
// ORU: bounded per-stage log buffer, step timings and peak heap (S08).
internal const val STAGE_LOG_LIMIT = 50

/** One storage throughput sample of the patcher run, in kilobytes per second. */
data class IoSample(val readKbPerSec: Int, val writeKbPerSec: Int) {
    val totalKbPerSec get() = readKbPerSec + writeKbPerSec
}

/** The log lines of one stage (the last [STAGE_LOG_LIMIT]) and how many WARN lines the stage printed. */
// ORU: per-stage log and warning count (S08).
data class StageLog(
    val lines: List<Pair<LogLevel, String>> = emptyList(),
    val warningCount: Int = 0
)

/** When a step started and ended, from [System.currentTimeMillis]. [endedAtMs] stays null while the step runs. */
data class StepTiming(val startedAtMs: Long, val endedAtMs: Long? = null) {
    val durationMs: Long? get() = endedAtMs?.let { it - startedAtMs }
}

/** Live state of one patcher run, as consumed by the patching screens. */
interface PatchProgressSource {
    val steps: List<Step>
    val logs: List<Pair<LogLevel, String>>
    val logItems: List<LogItem>
    val heapSamples: List<Int>
    val heapLimitMb: Int

    /** Highest heap use (MB) seen during this attempt, from the samples or the run's closing line. */
    val peakHeapMb: Int get() = 0

    /** Load of every CPU core in percent, empty while the device exposes no core counters. */
    val cpuCoreLoads: List<Int>

    /** Storage throughput samples, collected alongside [heapSamples]. */
    val ioSamples: List<IoSample>

    /** Whether this run printed a log that is no longer available to show. */
    val logsLost: Boolean

    /** Cumulative number of log entries recorded during this run. */
    val totalLogCount: Int get() = logs.size

    // ORU: new per-stage and timing members default to empty so existing readers are unaffected.
    /** Per-stage log lines and warning counts, keyed by the stage they were printed in. */
    val stageLogs: Map<StepId, StageLog> get() = emptyMap()

    /** Start and end of each step that has started, keyed by step. */
    val stepTimings: Map<StepId, StepTiming> get() = emptyMap()
}

/**
 * Step, log and progress bookkeeping for a single patcher run.
 *
 * Both the single-app flow and the batch queue drive one of these, which is what lets them
 * share the same patching screens: the screens only ever read from [PatchProgressSource].
 *
 * @param restoredSteps Steps handed back after process death, null for a fresh run.
 */
class PatchRunProgress(
    context: Context,
    private val scope: CoroutineScope,
    private val totalPatches: Int,
    splitStepActive: Boolean = false,
    restoredSteps: List<Step>? = null,
    restoredCompletedPatches: Int = 0,
    restoredFailedPatch: String? = null
) : PatchProgressSource, AutoCloseable {

    private val appContext: Context = context.applicationContext

    override val steps = (restoredSteps ?: generatePatchSteps(context, splitStepActive)).toMutableStateList()

    /** Real-time raw log entries, collected from the patcher worker. Kept intact for export/copying. */
    override val logs = mutableStateListOf<Pair<LogLevel, String>>()

    /** Display log items rendered by the expert log panel. */
    override val logItems = mutableStateListOf<LogItem>()

    override var totalLogCount by mutableIntStateOf(0)
        private set

    private val accumulator = LogItemAccumulator(targetList = logItems, maxItems = DISPLAYED_LOG_ITEMS_LIMIT)
    private val logChannel = Channel<Pair<LogLevel, String>>(Channel.UNLIMITED)
    private var consumerJob: Job? = null

    // ORU: per-stage bookkeeping, written where the consumer drains log lines.
    private val stageLogTracker = StageLogTracker()
    private val stepTimingTracker = StepTimingTracker()

    /** Per-stage log lines and warning counts, replaced as a whole on every change. */
    override var stageLogs by mutableStateOf<Map<StepId, StageLog>>(emptyMap())
        private set

    /** Step start and end times, replaced as a whole on every change. */
    override var stepTimings by mutableStateOf<Map<StepId, StepTiming>>(emptyMap())
        private set

    init {
        consumerJob = scope.launch(Dispatchers.Main.immediate) {
            val buffer = ArrayList<Pair<LogLevel, String>>()
            try {
                for (first in logChannel) {
                    buffer.add(first)
                    while (true) {
                        val next = logChannel.tryReceive().getOrNull() ?: break
                        buffer.add(next)
                    }
                    totalLogCount += buffer.size
                    logs.addAll(buffer)
                    accumulator.appendAll(buffer)

                    // Lines belong to the stage that was running when they were consumed
                    steps.getOrNull(currentStepIndex)?.id?.let { stage ->
                        buffer.forEach { (level, message) -> stageLogTracker.append(stage, level, message) }
                        stageLogs = stageLogTracker.snapshot()
                    }
                    buffer.clear()
                }
            } catch (_: CancellationException) {
                // Canceled when run finishes or closes
            }
        }
    }

    /**
     * The log of a run that was interrupted by process death is gone: it only ever lived in
     * memory, and the steps came back from the saved state without it.
     */
    override val logsLost = restoredSteps != null

    // ORU: the usage sampling interval is 2 seconds, not 1 (ResourceMonitor.MONITOR_INTERVAL).
    /** Heap usage samples (MB) collected every 2 seconds while patching. */
    override val heapSamples = mutableStateListOf<Int>()

    /** Heap limit (MB) of the patcher process, parsed from the runtime's log line. */
    override var heapLimitMb by mutableIntStateOf(0)
        private set

    // ORU: peak heap, from samples and the closing done line.
    override var peakHeapMb by mutableIntStateOf(0)
        private set

    /**
     * Replaced as a whole rather than mutated in place: the cores are read as one snapshot and
     * only mean anything next to each other.
     */
    override var cpuCoreLoads by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Storage throughput samples (KB/s) collected while patching. */
    override val ioSamples = mutableStateListOf<IoSample>()

    var completedPatches by mutableIntStateOf(restoredCompletedPatches)
        private set

    /** The patch the run failed on, null while none has. */
    var failedPatch by mutableStateOf(restoredFailedPatch)
        private set

    /**
     * The fraction [0, 1] of a stage that reports its own progress, for the stage that is running. A stage with no entry
     * counts as nothing done until it completes. Cleared when the run restarts.
     */
    var stageFractions by mutableStateOf<Map<StepId, Float>>(emptyMap())
        private set

    private var currentStepIndex = steps.indexOfFirst { it.state == State.RUNNING }.coerceAtLeast(0)

    private var requiresSplitPreparation = steps.any { it.id == StepId.PREPARE_SPLIT_APK }

    private var stallWatchJob: Job? = null

    private val _showLongStepWarning = MutableStateFlow(false)

    /** True once the current step has been running for over a minute without progress. */
    val showLongStepWarning: StateFlow<Boolean> = _showLongStepWarning.asStateFlow()

    val patchesProgress get() = completedPatches to totalPatches

    /** [0, 1] progress across every step of the run. */
    val progress by derivedStateOf {
        runProgressOf(steps, completedPatches, totalPatches, stageFractions)
    }

    /** Logger handed to the patcher worker. */
    val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) = record(level, message)
    }

    /**
     * Files the worker reports back as the real input, which is also where the split
     * preparation step is confirmed or dropped.
     */
    fun onProgress(name: String?, state: State?, message: String?) {
        scope.launch(Dispatchers.Main) {
            steps[currentStepIndex] = steps[currentStepIndex].run {
                copy(
                    name = name ?: this.name,
                    state = state ?: this.state,
                    message = message ?: this.message
                )
            }

            if (state == State.COMPLETED && currentStepIndex != steps.lastIndex) {
                currentStepIndex++
                steps[currentStepIndex] = steps[currentStepIndex].copy(state = State.RUNNING)
            }

            // ORU: step timings follow every step change.
            publishStepTimings()
        }
    }

    fun onPatchCompleted() {
        scope.launch(Dispatchers.Main) { completedPatches += 1 }
    }

    fun onPatchFailed(patchName: String) {
        scope.launch(Dispatchers.Main) { failedPatch = patchName }
    }

    /**
     * Records how far a running stage has got, as a fraction of its own share of the bar. A report never lowers the
     * fraction already held for that stage, and a NaN report is ignored.
     */
    fun onStageFraction(stage: StepId, fraction: Float) {
        if (fraction.isNaN()) return
        scope.launch(Dispatchers.Main) {
            val held = stageFractions[stage] ?: 0f
            stageFractions = stageFractions + (stage to max(held, fraction.coerceIn(0f, 1f)))
        }
    }

    /**
     * Drops everything the abandoned attempt reported and puts the pipeline back at its first
     * step, so the retry that follows counts from zero instead of on top of it.
     *
     * The log survives: it is the only record of why the run started over. Whether the input is
     * a split archive is a property of the input rather than of the attempt, so that is kept too.
     */
    fun onRestart() {
        scope.launch(Dispatchers.Main) {
            completedPatches = 0
            failedPatch = null
            currentStepIndex = 0
            steps.clear()
            steps.addAll(generatePatchSteps(appContext, requiresSplitPreparation))
            // A memory retry reports its own limit, and a fallback to the app's process has the app's heap
            heapLimitMb = 0
            peakHeapMb = 0
            heapSamples.clear()
            ioSamples.clear()
            cpuCoreLoads = emptyList()
            _showLongStepWarning.value = false
            accumulator.reset()
            totalLogCount = logs.size
            accumulator.appendAll(logs)
            // The stage counts and timings describe the attempt that was abandoned
            stageLogTracker.clear()
            stageLogs = emptyMap()
            stageFractions = emptyMap()
            stepTimingTracker.clear()
            // ORU: restart clears per-stage counts and timings.
            publishStepTimings()
        }
    }

    /**
     * Adds or removes the split preparation step as the worker learns what the input
     * actually is. [merged] marks the step as done once the archive has been merged.
     */
    fun updateSplitRequirement(
        file: File?,
        needsSplitOverride: Boolean? = null,
        merged: Boolean = false
    ) {
        val needsSplit = needsSplitOverride
            ?: merged
            || file?.let(SplitApkPreparer::isSplitArchive) == true

        when {
            needsSplit && !requiresSplitPreparation -> {
                requiresSplitPreparation = true
                addSplitStep()
            }

            !needsSplit && requiresSplitPreparation -> {
                requiresSplitPreparation = false
                removeSplitStep()
                // ORU: split step removed, timing dropped.
                publishStepTimings()
                return
            }
        }

        if (needsSplit && merged) {
            val index = steps.indexOfFirst { it.id == StepId.PREPARE_SPLIT_APK }
            if (index >= 0) {
                steps[index] = steps[index].copy(state = State.COMPLETED)
                if (currentStepIndex == index && index < steps.lastIndex) {
                    currentStepIndex++
                    steps[currentStepIndex] = steps[currentStepIndex].copy(state = State.RUNNING)
                }
            }
        }

        // ORU: split step added or merged, timing recorded.
        publishStepTimings()
    }

    /**
     * Starts watching for a stalled step. The warning clears whenever real progress moves
     * again, so a slow but advancing run never triggers it.
     */
    fun startStallWatch() {
        stallWatchJob?.cancel()
        stallWatchJob = scope.launch {
            var lastProgress = Snapshot.withoutReadObservation { progress }
            var stepStartTime = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()
                val current = Snapshot.withoutReadObservation { progress }
                if (current != lastProgress) {
                    lastProgress = current
                    stepStartTime = now
                    _showLongStepWarning.value = false
                } else if (!_showLongStepWarning.value && now - stepStartTime > STALL_THRESHOLD_MS) {
                    _showLongStepWarning.value = true
                }
                delay(250.milliseconds)
            }
        }
    }

    /**
     * Holds back the display of a run that is waiting on an answer before it starts, so the
     * screen behind the dialog shows neither progress nor a warning about a slow step.
     */
    fun holdBeforeStart() {
        stopStallWatch()
        setOpeningStepState(State.WAITING)
    }

    /** Puts back what [holdBeforeStart] held, once the run is free to start. */
    fun resumeBeforeStart() {
        setOpeningStepState(State.RUNNING)
        startStallWatch()
    }

    /** Moves the opening step between waiting and running, leaving one past that point alone. */
    private fun setOpeningStepState(state: State) {
        val opening = steps.firstOrNull() ?: return
        if (opening.state != State.WAITING && opening.state != State.RUNNING) return

        steps[0] = opening.copy(state = state)
        // ORU: opening step hold and resume, timing recorded.
        publishStepTimings()
    }

    /** Recomputes step start and end times from the steps as they are now. Main dispatcher only. */
    private fun publishStepTimings() {
        stepTimings = stepTimingTracker.sync(steps, System.currentTimeMillis())
    }

    fun stopStallWatch() {
        stallWatchJob?.cancel()
        stallWatchJob = null
        _showLongStepWarning.value = false
    }

    // ORU: telemetry parsed by a pure function; the log panel rules are unchanged.
    private fun record(level: LogLevel, message: String) {
        level.androidLog(message)

        val parsed = parseRunLine(level, message)
        when (val telemetry = parsed.telemetry) {
            is RunTelemetry.HeapLimit -> scope.launch(Dispatchers.Main) { heapLimitMb = telemetry.mb }

            is RunTelemetry.HeapSample -> scope.launch(Dispatchers.Main) {
                heapSamples.add(telemetry.mb)
                if (heapSamples.size > SAMPLE_HISTORY_LIMIT) heapSamples.removeAt(0)
                peakHeapMb = max(peakHeapMb, telemetry.mb)
            }

            is RunTelemetry.HeapDone -> scope.launch(Dispatchers.Main) {
                peakHeapMb = max(peakHeapMb, telemetry.maxMb)
            }

            is RunTelemetry.Usage -> scope.launch(Dispatchers.Main) {
                if (telemetry.coreLoads.isNotEmpty()) cpuCoreLoads = telemetry.coreLoads

                val read = telemetry.ioRead
                val write = telemetry.ioWrite
                if (read != null && write != null) {
                    ioSamples.add(IoSample(read, write))
                    if (ioSamples.size > SAMPLE_HISTORY_LIMIT) ioSamples.removeAt(0)
                }
            }

            null -> Unit
        }

        if (!parsed.showInLog) return

        logChannel.trySend(level to message)
    }

    private fun addSplitStep() {
        if (steps.any { it.id == StepId.PREPARE_SPLIT_APK }) return

        val loadIndex = steps.indexOfFirst { it.id == StepId.LOAD_PATCHES }
        val insertIndex = when {
            loadIndex >= 0 -> loadIndex + 1
            else -> steps.indexOfFirst { it.id == StepId.READ_APK }.takeIf { it >= 0 } ?: steps.size
        }
        val state = if (insertIndex <= currentStepIndex) State.COMPLETED else State.WAITING

        steps.add(insertIndex, buildSplitStep(appContext, state = state))

        if (insertIndex <= currentStepIndex) {
            currentStepIndex++
        }
    }

    private fun removeSplitStep() {
        val index = steps.indexOfFirst { it.id == StepId.PREPARE_SPLIT_APK }
        if (index == -1) return

        val removingCurrent = index == currentStepIndex
        steps.removeAt(index)

        when {
            currentStepIndex > index -> currentStepIndex--
            removingCurrent -> {
                currentStepIndex = index.coerceAtMost(steps.lastIndex).coerceAtLeast(0)
                if (steps.isNotEmpty()) {
                    val current = steps[currentStepIndex]
                    if (current.state == State.WAITING) {
                        steps[currentStepIndex] = current.copy(state = State.RUNNING)
                    }
                }
            }
        }
    }

    /** Closes background log consuming coroutines and channels when the run finishes. */
    fun finish() {
        stopStallWatch()
        logChannel.close()
        consumerJob?.cancel()
    }

    override fun close() = finish()

    private companion object {
        const val SAMPLE_HISTORY_LIMIT = 60
        const val STALL_THRESHOLD_MS = 60_000L
        const val DISPLAYED_LOG_ITEMS_LIMIT = 2000

        fun LogLevel.androidLog(msg: String) = when (this) {
            LogLevel.TRACE -> Log.v(TAG, msg)
            LogLevel.INFO -> Log.i(TAG, msg)
            LogLevel.WARN -> Log.w(TAG, msg)
            LogLevel.ERROR -> Log.e(TAG, msg)
        }
    }
}

/** A line the run prints about its own resource use. These feed the usage readout. */
// ORU: pure parsing used by PatchRunProgress and the parse tests.
internal sealed interface RunTelemetry {
    data class HeapLimit(val mb: Int) : RunTelemetry
    data class HeapSample(val mb: Int) : RunTelemetry
    data class HeapDone(val maxMb: Int) : RunTelemetry
    data class Usage(val coreLoads: List<Int>, val ioRead: Int?, val ioWrite: Int?) : RunTelemetry
}

/**
 * How one run log line is treated: [telemetry] is what it reports, [showInLog] says whether it
 * reaches the log panel. Raw polls and TRACE lines do not.
 */
private const val PROFILE_LOG_PREFIX = "oru.profile"

internal class ParsedRunLine(val telemetry: RunTelemetry?, val showInLog: Boolean)

internal fun parseRunLine(level: LogLevel, message: String): ParsedRunLine {
    val rawPoll = message.startsWith(LOG_MEMORY_PREFIX_CURRENT) || message.startsWith(LOG_USAGE_PREFIX_CURRENT)
    // ORU: developer phase profile lines (marker file) go to logcat and the profile file only
    val profileLine = message.startsWith(PROFILE_LOG_PREFIX)
    return ParsedRunLine(
        telemetry = parseRunTelemetry(message),
        showInLog = level != LogLevel.TRACE && !rawPoll && !profileLine
    )
}

/**
 * Both heap-limit prefixes are accepted: the separate patcher process logs the first, the
 * in-process runtime logs the second.
 */
internal fun parseRunTelemetry(message: String): RunTelemetry? = when {
    message.startsWith(LOG_PROCESS_PREFIX_PROCESS_HEAP) ->
        message.megabytesAfter(LOG_PROCESS_PREFIX_PROCESS_HEAP)?.let { RunTelemetry.HeapLimit(it) }

    message.startsWith(LOG_PROCESS_PREFIX_COROUTINE_HEAP) ->
        message.megabytesAfter(LOG_PROCESS_PREFIX_COROUTINE_HEAP)?.let { RunTelemetry.HeapLimit(it) }

    message.startsWith(LOG_MEMORY_PREFIX_CURRENT) ->
        message.megabytesAfter(LOG_MEMORY_PREFIX_CURRENT)?.let { RunTelemetry.HeapSample(it) }

    message.startsWith(LOG_MEMORY_PREFIX_DONE) ->
        message.logField(LOG_MEMORY_FIELD_MAX)?.substringBefore("MB")?.toIntOrNull()
            ?.let { RunTelemetry.HeapDone(it) }

    message.startsWith(LOG_USAGE_PREFIX_CURRENT) -> RunTelemetry.Usage(
        coreLoads = message.logField(LOG_USAGE_FIELD_CPU)
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            .orEmpty(),
        ioRead = message.logField(LOG_USAGE_FIELD_IO_READ)?.toIntOrNull(),
        ioWrite = message.logField(LOG_USAGE_FIELD_IO_WRITE)?.toIntOrNull()
    )

    else -> null
}

private fun String.megabytesAfter(prefix: String): Int? =
    removePrefix(prefix).substringBefore("MB").trim().toIntOrNull()

/**
 * Keeps the last [STAGE_LOG_LIMIT] lines of each stage and counts its WARN lines. Kept apart from
 * [PatchRunProgress] so the bookkeeping can be tested without Android. Not thread safe: main only.
 */
// ORU: pure per-stage bookkeeping (S08).
internal class StageLogTracker {
    private val lines = HashMap<StepId, ArrayDeque<Pair<LogLevel, String>>>()
    private val warnings = HashMap<StepId, Int>()

    fun append(stage: StepId, level: LogLevel, message: String) {
        val stageLines = lines.getOrPut(stage) { ArrayDeque() }
        stageLines.addLast(level to message)
        if (stageLines.size > STAGE_LOG_LIMIT) stageLines.removeFirst()

        if (level == LogLevel.WARN) warnings[stage] = (warnings[stage] ?: 0) + 1
    }

    fun snapshot(): Map<StepId, StageLog> = (lines.keys + warnings.keys).associateWith { stage ->
        StageLog(
            lines = lines[stage]?.toList().orEmpty(),
            warningCount = warnings[stage] ?: 0
        )
    }

    fun clear() {
        lines.clear()
        warnings.clear()
    }
}

/**
 * Derives each step's start and end from its state. A step gets a start when it first runs and an
 * end when it completes or fails. A step that has left the list (the split step once it is dropped)
 * loses its timing. Not thread safe: main only.
 */
// ORU: pure step timing bookkeeping (S08).
internal class StepTimingTracker {
    private var timings: Map<StepId, StepTiming> = emptyMap()

    fun sync(steps: List<Step>, now: Long): Map<StepId, StepTiming> {
        val next = LinkedHashMap<StepId, StepTiming>()
        for (step in steps) {
            val current = timings[step.id]
            val timing = when (step.state) {
                State.WAITING -> current
                State.RUNNING -> current ?: StepTiming(startedAtMs = now)
                State.COMPLETED, State.FAILED -> when {
                    current == null -> StepTiming(startedAtMs = now, endedAtMs = now)
                    current.endedAtMs == null -> current.copy(endedAtMs = now)
                    else -> current
                }
            }
            if (timing != null) next[step.id] = timing
        }

        timings = next
        return next
    }

    fun clear() {
        timings = emptyMap()
    }
}

/**
 * The share [0, 1] of the whole run that is done, see [PatchRunProgress.progress].
 *
 * A completed stage counts its full share. A running stage counts its share times its fraction in [stageFractions],
 * and nothing while it reports none, so the bar only moves for a stage that reports real progress. Executing patches
 * counts the patches done out of the share the fixed steps leave it, so a split step takes its share from the patches.
 * Pure, so the weighting can be tested without Android.
 */
internal fun runProgressOf(
    steps: List<Step>,
    completedPatches: Int,
    totalPatches: Int,
    stageFractions: Map<StepId, Float> = emptyMap(),
): Float {
    val patchesShare = max(0.0, 1.0 - steps.sumOf { it.progressPercentage })
    val stagesDone = steps.sumOf { step ->
        when {
            step.category == StepCategory.PATCHING -> 0.0
            step.state == State.COMPLETED -> step.progressPercentage
            step.state == State.RUNNING -> {
                // A NaN report is no report: the stage counts as nothing done
                val fraction = stageFractions[step.id]?.takeUnless { it.isNaN() } ?: 0f
                step.progressPercentage * fraction.coerceIn(0f, 1f)
            }

            else -> 0.0
        }
    }
    val patchesDone = completedPatches / totalPatches.coerceAtLeast(1).toDouble()

    return min(1.0, stagesDone + patchesDone * patchesShare).toFloat()
}

/**
 * Builds the step pipeline of a patcher run. The percentages add up to less than one, the
 * remainder is what executing patches is worth.
 */
fun generatePatchSteps(context: Context, splitStepActive: Boolean): List<Step> = listOfNotNull(
    Step(
        id = StepId.LOAD_PATCHES,
        name = context.getString(R.string.patcher_step_load_patches),
        category = StepCategory.PREPARING,
        state = State.RUNNING,
        progressPercentage = 0.05
    ),
    buildSplitStep(context).takeIf { splitStepActive },
    Step(
        id = StepId.READ_APK,
        name = context.getString(R.string.patcher_step_unpack),
        category = StepCategory.PREPARING,
        progressPercentage = 0.05
    ),
    Step(
        id = StepId.EXECUTE_PATCHES,
        name = context.getString(R.string.applying_patches),
        category = StepCategory.PATCHING,
        // Takes whatever percentage the other steps did not claim
        progressPercentage = 0.0
    ),
    Step(
        id = StepId.WRITE_PATCHED_APK,
        name = context.getString(R.string.patcher_step_write_patched),
        category = StepCategory.SAVING,
        progressPercentage = 0.4
    ),
    Step(
        id = StepId.SIGN_PATCHED_APK,
        name = context.getString(R.string.patcher_step_sign_apk),
        category = StepCategory.SAVING,
        progressPercentage = 0.1
    )
)

fun buildSplitStep(
    context: Context,
    state: State = State.WAITING,
    message: String? = null
) = Step(
    id = StepId.PREPARE_SPLIT_APK,
    name = context.getString(R.string.patcher_step_prepare_split_apk),
    category = StepCategory.PREPARING,
    state = state,
    message = message,
    progressPercentage = 0.1
)
