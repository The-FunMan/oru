/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.ui.model.Step
import kotlin.math.max

/**
 * The share of the whole run that the install takes once the patch run is signed. A single run always goes on to the
 * install, so the fill and the percentage stop short of a full run at the end of the signing, see [oruRunFill].
 */
internal const val OruInstallShare = 0.1f

/**
 * Maps a share of the patch run, in [0, 1], onto the fill of the whole run. With an install ahead the patch run fills all
 * but [OruInstallShare], so the hexagon and the percentage reach a full run only at the install's last step. Without an
 * install the patch run is the whole run. Out of range values are clamped, and NaN reads as zero.
 */
internal fun oruRunFill(patchRun: Float, installAhead: Boolean): Float {
    val share = if (patchRun.isNaN()) 0f else patchRun.coerceIn(0f, 1f)
    return if (installAhead) share * (1f - OruInstallShare) else share
}

/**
 * The share of the whole run that the patch stage fills: what the steps other than patching leave, see
 * [app.morphe.manager.ui.model.runProgressOf]. A split step takes its share from it.
 */
internal fun oruPatchShare(steps: List<Step>): Float =
    max(0.0, 1.0 - steps.sumOf { it.progressPercentage }).toFloat()

/**
 * What the install screen carries on from the run's hexagon: the [fill] the run ended at, the [patches] of the run and the
 * [patchShare] of the patch stage, so the same pieces are cut and lit on both screens.
 */
data class OruRunHexagon(
    val fill: Float,
    val patches: Int,
    val patchShare: Float,
)
