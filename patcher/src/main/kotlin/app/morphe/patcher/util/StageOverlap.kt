/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import kotlin.concurrent.thread

/**
 * The heap limit from which the lazy binary XML decoding of the resources (`lazyXmlFitsHeap`) runs.
 */
internal const val TWO_STAGE_MIN_HEAP_BYTES = 1000L * 1024 * 1024

/**
 * The heap limit from which the dex and resource compile overlap ([overlapFitsHeap]). It is above
 * [TWO_STAGE_MIN_HEAP_BYTES] because the overlapped compile holds more transient sets at once.
 */
internal const val OVERLAP_MIN_HEAP_BYTES = 1400L * 1024 * 1024

/**
 * Whether a heap limit of [maxMemoryBytes] leaves room for two large compile stages to hold their
 * transient sets at once. The 640 MB limit of a small device does not, so it runs them one after the other.
 */
internal fun overlapFitsHeap(maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()): Boolean =
    maxMemoryBytes >= OVERLAP_MIN_HEAP_BYTES

/**
 * Runs [first] and [second] on their own threads and returns both results.
 *
 * Neither block is interrupted, so a failure does not cancel the other one, and an interrupt of the
 * caller does not stop them. Both threads are joined before this returns or throws, so no work is left
 * running after the call. An interrupt of the caller received meanwhile is re-asserted on the caller's
 * thread before this returns or throws. When both fail, the failure of [first] is thrown, as it would be
 * when the blocks run one after the other.
 */
internal fun <A, B> runInParallel(
    firstName: String,
    first: () -> A,
    secondName: String,
    second: () -> B,
): Pair<A, B> {
    var firstResult: Result<A>? = null
    var secondResult: Result<B>? = null
    var firstThread: Thread? = null
    var secondThread: Thread? = null

    try {
        firstThread = thread(name = firstName) { firstResult = runCatching(first) }
        secondThread = thread(name = secondName) { secondResult = runCatching(second) }
    } finally {
        val firstInterrupted = joinRecordingInterrupt(firstThread)
        val secondInterrupted = joinRecordingInterrupt(secondThread)
        if (firstInterrupted || secondInterrupted) Thread.currentThread().interrupt()
    }

    return firstResult!!.getOrThrow() to secondResult!!.getOrThrow()
}

/** Joins [thread] until it has finished, and reports whether the caller was interrupted while waiting. */
private fun joinRecordingInterrupt(thread: Thread?): Boolean {
    var interrupted = false
    while (thread?.isAlive == true) {
        try {
            thread.join()
        } catch (_: InterruptedException) {
            interrupted = true
        }
    }
    return interrupted
}
