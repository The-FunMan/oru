/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import app.morphe.manager.R

/**
 * The run line under the title, split into the parts the screen draws: the stage's name, which morphs when the stage
 * changes, and while patches apply the count of them, whose number rolls on each patch. [patchesDone] and
 * [patchesTotal] are both set or both null.
 */
data class OruRunLineParts(
    val labelRes: Int,
    val patchesDone: Int? = null,
    val patchesTotal: Int? = null,
)

/**
 * The parts of the run line while a run is not done. A stage that applies patches carries the count once the run knows
 * the total. Every other state is the plain line of [oruRunLineRes]. A finished run has its own sentence and does not
 * use this.
 */
fun oruRunLineParts(run: OruRunUi): OruRunLineParts =
    if (run.currentStageLabelRes == R.string.oru_stage_execute_patches && run.patchesTotal > 0) {
        OruRunLineParts(
            labelRes = R.string.oru_stage_execute_patches,
            patchesDone = run.patchesDone,
            patchesTotal = run.patchesTotal,
        )
    } else {
        OruRunLineParts(labelRes = oruRunLineRes(run.stages))
    }

/**
 * The font size, in sp, at which a done fact's value fits [availablePx] wide: its own size when it already fits,
 * else the size that scales its width down to the space, never below [minSp]. The width of text is close to linear
 * in its size, so one step is enough; the small margin keeps the rounding of glyph advances from overflowing.
 */
internal fun oruFactFontSp(baseSp: Float, minSp: Float, measuredPx: Int, availablePx: Int): Float {
    if (measuredPx <= 0 || measuredPx <= availablePx) return baseSp
    return (baseSp * availablePx / measuredPx * FactFitMargin).coerceIn(minSp, baseSp)
}

private const val FactFitMargin = 0.98f

/**
 * The parts to draw in the given mode. Expert shows the patch count, which rolls on every patch. Simple keeps only
 * the stage's name: the hexagon filling is its progress, and a ticking number is the kind of detail Simple leaves out.
 */
fun OruRunLineParts.forMode(expert: Boolean): OruRunLineParts =
    if (expert) this else copy(patchesDone = null, patchesTotal = null)
