/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.patcher.runtime.lowerMemoryLimit

/** How many lines of a failure message the failed view shows. The copied log has the rest. */
internal const val FAILURE_SUMMARY_LINES = 3

/**
 * The memory offer after the system stopped the patcher. Accepting it stores [suggestedLimitMb] as the
 * limit of the next run. It never retries the run in place, and it is only an offer while it can go lower.
 */
data class OruMemoryOffer(
    val currentLimitMb: Int,
    val suggestedLimitMb: Int,
) {
    val canAdjust: Boolean get() = suggestedLimitMb < currentLimitMb

    companion object {
        /** The offer for a run killed at [currentLimitMb]: the same step down the memory retries take. */
        fun lowerFrom(currentLimitMb: Int): OruMemoryOffer =
            OruMemoryOffer(currentLimitMb, lowerMemoryLimit(currentLimitMb))
    }
}

/** Why a run ended without a result, as the failed view explains it. */
sealed interface OruFailure {
    /** The system stopped the patcher for memory. Accepting lowers the limit for the next run. */
    data class MemoryOffer(val currentLimitMb: Int, val lowerToMb: Int) : OruFailure

    /** The system stopped the patcher for memory at the lowest limit this phone allows. No offer. */
    data class MemoryAtMinimum(val limitMb: Int) : OruFailure

    /**
     * Any other failure: the patch it stopped on, when known, and the first lines of the message. A
     * heap the patcher filled on its own lands here too, with the worker's own text that says so.
     */
    data class Error(val failedPatch: String?, val summary: String?) : OruFailure

    /** The user cancelled. Nothing was changed, and this is not a failure. */
    data object Cancelled : OruFailure
}

/**
 * Classifies a run that failed. A memory offer comes only from a process the system killed, which is
 * what [OruMemoryOffer] is for. Without one the failure is an error, so heap exhaustion has no offer.
 *
 * @param failedPatch the patch the run stopped on, from the error info.
 * @param message the failed step's message, which is blank for some failures.
 * @param logTail the last lines of the run's log, as "[LEVEL] message", used when [message] is blank.
 */
fun oruFailureOf(
    memoryOffer: OruMemoryOffer?,
    failedPatch: String?,
    message: String?,
    logTail: List<String>,
): OruFailure = when {
    memoryOffer != null && memoryOffer.canAdjust -> OruFailure.MemoryOffer(
        currentLimitMb = memoryOffer.currentLimitMb,
        lowerToMb = memoryOffer.suggestedLimitMb,
    )
    memoryOffer != null -> OruFailure.MemoryAtMinimum(memoryOffer.currentLimitMb)
    else -> OruFailure.Error(
        failedPatch = failedPatch?.takeIf { it.isNotBlank() },
        summary = failureSummary(message, logTail),
    )
}

/**
 * The first [FAILURE_SUMMARY_LINES] lines of [message]. When the message is blank, the last lines of the
 * log instead, since the failure line is usually there. Null when neither has any text.
 */
internal fun failureSummary(message: String?, logTail: List<String>): String? {
    val fromMessage = message.orEmpty().lines().map(String::trim).filter(String::isNotEmpty)
    val lines = if (fromMessage.isNotEmpty()) {
        fromMessage.take(FAILURE_SUMMARY_LINES)
    } else {
        logTail.map(String::trim).filter(String::isNotEmpty).takeLast(FAILURE_SUMMARY_LINES)
    }
    return lines.joinToString(separator = "\n").ifEmpty { null }
}
