/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Oru mark: an open ring with the brand dot in its gap. Its geometry is the launcher icon's, in the icon's 108 unit
 * viewport, so the splash and the launcher icon draw the same mark. The ring is one round-capped stroke that starts at -35
 * degrees and runs clockwise for 280 degrees, to 245 degrees, which leaves a gap at the upper right. The brand dot sits in
 * that gap at -62 degrees, on the same radius. Angles are clockwise from 3 o'clock, as Compose and SVG measure them, with y
 * growing downwards.
 */

/** Centre of the mark, in viewport units. */
internal const val OruRingCentre = 54f

/** Radius of the ring's centreline, in viewport units. */
internal const val OruRingRadius = 20.5f

/** Width of the ring's stroke, in viewport units. */
internal const val OruRingStrokeWidth = 9.61f

/** Radius of the brand dot: a round-capped stroke 12 units wide is a circle of radius 6. */
internal const val OruRingDotRadius = 6f

/** Where the ring starts, and how far it runs clockwise, in degrees. */
internal const val OruRingStartDegrees = -35f
internal const val OruRingSweepDegrees = 280f

/** Where the brand dot sits on the centreline, in degrees. */
internal const val OruRingDotDegrees = -62f

/** Outer radius of all the ink. The dot reaches its own radius past the centreline, further than the stroke does. */
internal const val OruRingInkRadius = OruRingRadius + OruRingDotRadius

/** The share of its rest size the mark ends at when it gathers into the wordmark dot. */
internal const val OruRingGatherScale = 0.15f

/** The gather starts to fade the mark once it is this far along. */
private const val OruRingFadeFrom = 0.7f

/** Where and how big the mark is drawn: its centre in pixels, the pixels per viewport unit, and its opacity. */
internal data class OruRingPose(val centre: Offset, val pixelsPerUnit: Float, val alpha: Float)

/** The ring's sweep in degrees at [progress] (0 to 1, clamped). Zero draws nothing, one draws the whole 280 degrees. */
internal fun oruRingSweep(progress: Float): Float = OruRingSweepDegrees * progress.coerceIn(0f, 1f)

/** A point on the ring's centreline at [degrees], in viewport units. */
internal fun oruRingPointAt(degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = OruRingCentre + OruRingRadius * cos(radians).toFloat(),
        y = OruRingCentre + OruRingRadius * sin(radians).toFloat(),
    )
}

/** The brand dot's centre, in viewport units. */
internal fun oruRingDotPoint(): Offset = oruRingPointAt(OruRingDotDegrees)

/** The pose of the mark at rest, centred on [centre], with its ink [inkDiameterPx] across. */
internal fun oruRingRestPose(centre: Offset, inkDiameterPx: Float): OruRingPose =
    OruRingPose(centre = centre, pixelsPerUnit = inkDiameterPx / (2f * OruRingInkRadius), alpha = 1f)

/**
 * The pose of the mark while it gathers into the wordmark dot. At 0 it is [rest]. At 1 its centre is on [dotTarget] and it
 * has shrunk to [OruRingGatherScale] of its rest size. Its opacity fades over the last 30 per cent, so what is left is the
 * wordmark's own dot. [gather] is clamped to 0..1.
 */
internal fun oruRingGatherPose(rest: OruRingPose, dotTarget: Offset, gather: Float): OruRingPose {
    val g = gather.coerceIn(0f, 1f)
    val fade = if (g > OruRingFadeFrom) 1f - (g - OruRingFadeFrom) / (1f - OruRingFadeFrom) else 1f
    return OruRingPose(
        centre = Offset(
            x = rest.centre.x + (dotTarget.x - rest.centre.x) * g,
            y = rest.centre.y + (dotTarget.y - rest.centre.y) * g,
        ),
        pixelsPerUnit = rest.pixelsPerUnit * (1f + (OruRingGatherScale - 1f) * g),
        alpha = rest.alpha * fade,
    )
}

/**
 * Draws the mark at [progress]. The ring sweeps clockwise from its start, and the brand dot is drawn with it from the
 * first frame, as the reference page does. [ringColor] paints the ring and [dotColor] the dot.
 */
internal fun DrawScope.drawOruRing(progress: Float, pose: OruRingPose, ringColor: Color, dotColor: Color) {
    val ppu = pose.pixelsPerUnit
    val sweep = oruRingSweep(progress)
    if (sweep > 0f) {
        val radius = OruRingRadius * ppu
        drawArc(
            color = ringColor,
            startAngle = OruRingStartDegrees,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(pose.centre.x - radius, pose.centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            alpha = pose.alpha,
            style = Stroke(width = OruRingStrokeWidth * ppu, cap = StrokeCap.Round),
        )
    }
    if (progress > 0f) {
        val dot = oruRingDotPoint()
        drawCircle(
            color = dotColor,
            radius = OruRingDotRadius * ppu,
            center = Offset(
                x = pose.centre.x + (dot.x - OruRingCentre) * ppu,
                y = pose.centre.y + (dot.y - OruRingCentre) * ppu,
            ),
            alpha = pose.alpha,
        )
    }
}
