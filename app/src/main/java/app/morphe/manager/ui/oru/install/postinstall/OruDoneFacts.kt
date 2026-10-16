/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import app.morphe.manager.ui.oru.run.OruRunFormat
import app.morphe.manager.ui.model.StepTiming

/**
 * The facts shown on the done state, already formatted. A fact the phone did not measure is null, so the screen can
 * say "not available" instead of showing a 0. [patchNames] are the patches the run applied, in the order the log has
 * them, for the "What changed" list.
 */
data class OruDoneFacts(
    /** The output size, for example "115.6 MB". Null when the output file could not be measured. */
    val outputSize: String?,
    /** The elapsed time of the run, for example "0:12". Null when the start or the end is unknown. */
    val elapsed: String?,
    /** The patches that succeeded in the run, by name. Empty when the run log is not available. */
    val patchNames: List<String> = emptyList(),
) {
    companion object {
        /**
         * Formats the raw figures and reads the patch names from the run's [logLines]. Negative sizes and times count as
         * unknown. Units and separators come from [OruRunFormat].
         */
        fun of(outputBytes: Long?, elapsedMs: Long?, logLines: List<String>): OruDoneFacts = OruDoneFacts(
            outputSize = outputBytes?.takeIf { it >= 0L }?.let { OruRunFormat.size(it) },
            elapsed = elapsedMs?.takeIf { it >= 0L }?.let { OruRunFormat.elapsed(it) },
            patchNames = oruChangedPatchNames(logLines),
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

/** The words a run log puts after the name of a patch that went through. */
private val PatchResultSuffixes = listOf(" succeeded", " applied")

/**
 * The patch name in one run log line that reports a patch as done: "Wide search bar succeeded" gives "Wide search
 * bar". Null for any other line, such as a failure, a line without a name, or a line that names the app itself. A
 * line that mentions the Morphe name is dropped as well, since the app never shows that name.
 */
internal fun oruPatchResultName(line: String): String? {
    val text = line.trim()
    val suffix = PatchResultSuffixes.firstOrNull { text.endsWith(it) } ?: return null
    val name = text.removeSuffix(suffix).trim()
    if (name.isEmpty() || name == "null" || name == "Patching") return null
    if (name.contains("morphe", ignoreCase = true)) return null
    return name
}

/** The distinct patch names of the run log lines that report a patch as done, in the order of the log. */
internal fun oruChangedPatchNames(logLines: List<String>): List<String> =
    logLines.mapNotNull(::oruPatchResultName).distinct()
