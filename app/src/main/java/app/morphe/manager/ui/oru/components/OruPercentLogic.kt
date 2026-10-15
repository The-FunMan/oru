/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The arithmetic of the run percentage, kept free of Compose so it runs on the JVM. The percentage is shown as a whole
 * number, and within one run it never goes back: a figure that has been shown stays until the run is over. Rounding is
 * monotone, so holding the progress holds the percentage it reads as.
 */

/** The digits the percentage slot is wide enough for. 100 is the widest figure a run shows. */
internal const val PercentSlotDigits = 3

private const val PercentScale = 100f

/** The whole percent a [progress] in [0, 1] reads as. Out of range values are clamped, and NaN reads as zero. */
internal fun oruPercent(progress: Float): Int =
    if (progress.isNaN()) 0 else (progress.coerceIn(0f, 1f) * PercentScale).roundToInt()

/**
 * The progress a run shows once [progress] is reported, given the [held] progress already shown. It is the larger of
 * the two, so the percentage it reads as never goes back. A NaN report keeps what was held.
 */
internal fun oruHeldProgress(held: Float, progress: Float): Float =
    if (progress.isNaN()) held else max(held, progress.coerceIn(0f, 1f))

/**
 * The progress one run shows. A new run gets a new holder, so its percentage starts from zero again. A retry of the
 * same run keeps the holder, and so holds the figure it had reached.
 */
internal class OruProgressHold {
    private var held = 0f

    /** Takes the reported [progress] and returns what the run shows: never less than it showed before. */
    fun show(progress: Float): Float {
        held = oruHeldProgress(held, progress)
        return held
    }
}
