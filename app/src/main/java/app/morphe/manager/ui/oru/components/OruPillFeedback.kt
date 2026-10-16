/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.morphe.manager.ui.oru.theme.OruMotion

/*
 * What a toggle segment does when it is chosen, so the tap is felt at once: a haptic tick, a short pulse (a swell and
 * a white sheen that settle), and a lifted, lit look while it stays selected. The states are read in the draw phase.
 */

/**
 * A value that jumps to 1 when [selected] turns true and settles to 0. A segment that starts selected does not pulse,
 * and reduced motion never does.
 */
@Composable
internal fun rememberOruSelectPulse(selected: Boolean): State<Float> {
    val reduced = oruReducedMotion()
    val pulse = remember { Animatable(0f) }
    val wasSelected = remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val chosen = selected && !wasSelected.value
        wasSelected.value = selected
        if (chosen && !reduced) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, OruMotion.selectPulse(reduced = false))
        }
    }
    return pulse.asState()
}

/** 1 while [selected], 0 otherwise, moving on the expand spring. */
@Composable
internal fun rememberOruPillLift(selected: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = OruMotion.expand(oruReducedMotion()),
        label = "oruPillLift",
    )

/** The haptic tick of choosing a segment. Call it only when the choice changes. */
@Composable
internal fun rememberOruSelectTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** Swells the segment up to [OruMotion.SelectPulsePeakScale] with [pulse]. */
internal fun Modifier.oruPulseScale(pulse: State<Float>): Modifier = graphicsLayer {
    val scale = 1f + (OruMotion.SelectPulsePeakScale - 1f) * pulse.value
    scaleX = scale
    scaleY = scale
}

/**
 * The shadow under a segment of [shape] that is lifted by [lift] (0 to 1): the contact shadow of a raised control, from
 * the depth system. Place it before the clip, so the shadow spills past the segment, and keep the segment's fill
 * opaque while it is lifted.
 */
@Composable
internal fun Modifier.oruPillShadow(shape: Shape, lift: State<Float>): Modifier =
    oruShadow(shape = shape, elevation = OruElevation.Raised, compact = true, strength = lift)

/**
 * Lights a segment of [shape] once its fill is drawn: the lit rim and the thin highlight of the depth system, strongest
 * when [lift] is 1, and the white sheen of [pulse]. Place it after the clip and the background.
 */
@Composable
internal fun Modifier.oruPillLit(shape: Shape, lift: State<Float>, pulse: State<Float>): Modifier =
    oruEdgeLight(shape = shape, elevation = OruElevation.Raised, strength = lift)
        .drawBehind {
            val sheen = pulse.value.coerceIn(0f, 1f)
            if (sheen > 0f) drawRect(Color.White.copy(alpha = SheenAlpha * sheen))
        }

private const val SheenAlpha = 0.40f
