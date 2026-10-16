/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import app.morphe.manager.ui.oru.run.OruRunFormat
import app.morphe.manager.ui.model.StepTiming

/**
 * The facts shown on the done state, already formatted. A fact the phone did not measure is null, so the screen can
 * say "not available" instead of showing a 0.
 */
data class OruDoneFacts(
    /** The output size, for example "115.6 MB". Null when the output file could not be measured. */
    val outputSize: String?,
    /** The elapsed time of the run, for example "0:12". Null when the start or the end is unknown. */
    val elapsed: String?,
) {
    companion object {
        /**
         * Formats the raw figures. Negative sizes and times count as unknown. Units and separators come from
         * [OruRunFormat].
         */
        fun of(outputBytes: Long?, elapsedMs: Long?): OruDoneFacts = OruDoneFacts(
            outputSize = outputBytes?.takeIf { it >= 0L }?.let { OruRunFormat.size(it) },
            elapsed = elapsedMs?.takeIf { it >= 0L }?.let { OruRunFormat.elapsed(it) },
        )
    }
}

/**
 * The elapsed time of a run, from the start of its first step to the end of its last finished step, in
 * milliseconds. Null when no step has finished or the clock went backwards.
 */
internal fun oruElapsedMs(timings: Collection<StepTiming>): Long? {
    val start = timings.minOfOrNull { it.startedAtMs } ?: return null
    val end = timings.mapNotNull { it.endedAtMs }.maxOrNull() ?: return null
    return (end - start).takeIf { it >= 0L }
}
