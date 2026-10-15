/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher

/**
 * Receives the fraction [0, 1] of the write stage done each time [Patcher.get] finishes one of its phases: the dex
 * compile, then the resource compile. Called on the thread that finished the phase, which may be a compile thread.
 */
fun interface WriteProgressListener {
    fun onProgress(fraction: Float)
}

/**
 * The phases of the write stage, weighted by their share of it. The seconds are measured on one device (an emulator
 * with 4 cores and a 640 MB heap, YouTube 21.40.161 with 95 patches). They are approximate weights for a real event,
 * not a timer, and they say nothing about other apps or devices.
 *
 * [PACK] is the merge and packing that follows [RESOURCE_COMPILE]. The library does not report it, so the fraction
 * reported after the resource compile is the share of the stage before packing.
 */
internal enum class WritePhase(val measuredSeconds: Double) {
    DEX_COMPILE(12.9),
    RESOURCE_COMPILE(30.2),
    PACK(11.7);
}

/** The seconds of the whole write stage, the total the phase shares are taken from. */
internal const val WRITE_STAGE_SECONDS = 12.9 + 30.2 + 11.7

/**
 * Turns the phases of the write stage into the fraction of it they complete, and passes that to [listener]. Phases may
 * finish on different threads (the dex and resource compile run side by side when memory allows), so each report is
 * the sum of every phase finished so far, which only ever rises. Without a listener nothing is reported.
 * [initialSecondsDone] is the time already spent in earlier phases, so reports start from that share.
 */
internal class WriteStageProgress(
    private val listener: WriteProgressListener?,
    initialSecondsDone: Double = 0.0,
) {
    private var secondsDone = initialSecondsDone

    @Synchronized
    fun finished(phase: WritePhase) {
        val target = listener ?: return
        secondsDone += phase.measuredSeconds
        target.onProgress((secondsDone / WRITE_STAGE_SECONDS).toFloat().coerceIn(0f, 1f))
    }

    @Synchronized
    fun packProgress(fraction: Float) {
        val target = listener ?: return
        val current = secondsDone + WritePhase.PACK.measuredSeconds * fraction.coerceIn(0f, 1f)
        target.onProgress((current / WRITE_STAGE_SECONDS).toFloat().coerceIn(0f, 1f))
    }
}
