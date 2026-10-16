/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import kotlin.math.max
import kotlin.math.roundToInt

/** The stages of a run that the status bar shows, in the order the run goes through them. */
internal enum class LiveBarStage { READ_LOAD, PATCH, WRITE, SIGN }

/**
 * The bar of a patcher run as the ongoing notification shows it: one segment per stage, sized from the same shares
 * [runProgressOf] gives those stages, so the bar fills the way the in-app hexagon does. Reading and loading take 10
 * points, patching 40, writing 40 and signing 10. A split input adds its own step to reading and loading, which
 * takes 10 points out of patching. The bar is the sum of its segments, always [TOTAL_POINTS].
 *
 * Before the first stage the bar has none ([stage] is null) and the notification shows it as indeterminate.
 * Not thread safe: the worker drives it from the run.
 */
internal class RunLiveBar {
    /** The stage the run is in, null before the run starts. */
    var stage: LiveBarStage? = null
        private set

    /** Whether the input is a split archive. Set by the worker once the input is known. */
    var splitInput: Boolean = false

    /** How far the current stage has got, from 0 to 1. Only ever rises within a stage. */
    private var stageFraction = 0f

    /** The segments of the bar, in stage order. */
    val segments: List<Int> get() = LiveBarStage.entries.map(::segmentOf)

    /** The points of the bar that are done, from 0 to [TOTAL_POINTS]. */
    val progress: Int
        get() {
            val current = stage ?: return 0
            val done = LiveBarStage.entries.takeWhile { it != current }.sumOf(::segmentOf)
            return done + (segmentOf(current) * stageFraction).roundToInt()
        }

    /**
     * Records how far [reported] has got, as a fraction of its own segment. A later stage moves the bar on into it,
     * and a report for a stage the run has already left is ignored. A NaN is no report. Returns true when the bar
     * moved, so the caller posts the notification only then.
     */
    fun report(reported: LiveBarStage, fraction: Float): Boolean {
        if (fraction.isNaN()) return false
        val current = stage
        if (current != null && reported < current) return false

        val before = progress
        val stageChanged = reported != current
        if (stageChanged) {
            stage = reported
            stageFraction = 0f
        }
        stageFraction = max(stageFraction, fraction.coerceIn(0f, 1f))
        return stageChanged || progress != before
    }

    /** Puts the bar back before the first stage, for a run that starts over. */
    fun reset() {
        stage = null
        stageFraction = 0f
    }

    private fun segmentOf(stage: LiveBarStage): Int = when (stage) {
        LiveBarStage.READ_LOAD -> if (splitInput) 20 else 10
        LiveBarStage.PATCH -> if (splitInput) 30 else 40
        LiveBarStage.WRITE -> 40
        LiveBarStage.SIGN -> 10
    }

    companion object {
        const val TOTAL_POINTS = 100
    }
}

/** The bar stage a step of the run reports into, or null for a step the bar does not show. */
internal fun StepId.liveBarStage(): LiveBarStage? = when (this) {
    StepId.LOAD_PATCHES, StepId.PREPARE_SPLIT_APK, StepId.READ_APK -> LiveBarStage.READ_LOAD
    StepId.EXECUTE_PATCHES -> LiveBarStage.PATCH
    StepId.WRITE_PATCHED_APK -> LiveBarStage.WRITE
    StepId.SIGN_PATCHED_APK -> LiveBarStage.SIGN
}
