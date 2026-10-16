/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import android.os.Build
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.morphe.manager.ui.oru.components.OruBlurEffect
import app.morphe.manager.ui.oru.components.OruBlurMinSdk
import app.morphe.manager.ui.oru.components.oruReducedMotion
import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.abs

/*
 * The choreography of a card opening its Focus. Everything here follows the visibility transition of the
 * destination it sits in (LocalOruAnimatedVisibilityScope), not a timer of its own. A Back gesture seeks that
 * transition, so the parting and the arrival run in reverse with the finger, and a new target retargets the springs.
 *
 * The helpers below are pure, so the stagger and the offsets are tested on the JVM (OruChoreographyTest).
 */

/** Each card further from the tapped one leaves this many milliseconds after the card nearer to it. */
internal const val OruPartingStepMillis = 20

/** The longest wait of a leaving card. The cards leave with the tile flight, so a long list still clears at once. */
internal const val OruPartingMaxDelayMillis = 120

/** How far a card above or below the tapped one moves away from it, in dp. */
internal const val OruPartingRiseDp = 16f

/** A leaving card shrinks to this scale. */
internal const val OruPartingScale = 0.96f

/** A leaving card blurs up to this radius, in dp, on API 31 and above. */
internal const val OruPartingBlurDp = 8f

/** The island drops this far, in dp, as Focus opens. */
internal const val OruDropDp = 24f

/** A Focus section rises this far, in dp, into its place. */
internal const val OruArriveRiseDp = 24f

/** A Focus section starts this blurred, in dp, and sharpens as it rises. */
internal const val OruArriveBlurDp = 6f

/** The Focus dock rises this far from the bottom, in dp. */
internal const val OruDockRiseDp = 24f

/** The Focus dock starts its rise this many milliseconds after the Focus opens, so the tile and the rows lead. */
internal const val OruDockDelayMillis = 80

/** Each Focus section starts this many milliseconds after the one before it. */
internal const val OruCascadeStepMillis = 30

/** No Focus section starts later than this, in milliseconds. */
internal const val OruCascadeCapMillis = 240

/** A delay of this many milliseconds is a share of 1 of a stagger. About how long a token spring takes to settle. */
internal const val OruStaggerHorizonMillis = 400

/** A stagger never takes the whole transition, so its element still moves at the end. */
private const val MaxStaggerShare = 0.9f

/**
 * Where the element stands between a card and its Focus, from 0 while it is away to 1 while it rests in its place. It
 * follows the visibility transition of the destination it sits in, with [spec] as the spring. Outside a oru
 * destination the element rests at 1.
 */
@Composable
internal fun rememberOruPresence(spec: FiniteAnimationSpec<Float>): State<Float> {
    val transition = LocalOruAnimatedVisibilityScope.current?.transition
    return if (transition != null) {
        transition.animateFloat(
            transitionSpec = { spec },
            label = "oruPresence",
        ) { state -> if (state == EnterExitState.PostExit) 0f else 1f }
    } else {
        remember { mutableFloatStateOf(1f) }
    }
}

/** The direction of card [index] from the tapped card: -1 above it, 1 below it, 0 for the card itself. */
internal fun oruPartingDirection(index: Int, tappedIndex: Int): Int = when {
    index < tappedIndex -> -1
    index > tappedIndex -> 1
    else -> 0
}

/** The wait of card [index] before it leaves, in milliseconds: one step per card between it and the tapped card. */
internal fun oruPartingDelayMillis(index: Int, tappedIndex: Int): Int =
    (abs(index - tappedIndex) * OruPartingStepMillis).coerceAtMost(OruPartingMaxDelayMillis)

/**
 * How far card [index] has moved, in dp, when it is [away] of the way gone (0 to 1). A card above the tapped one goes
 * up and a card below goes down, both by [OruPartingRiseDp] at full distance.
 */
internal fun oruPartingOffsetDp(index: Int, tappedIndex: Int, away: Float): Float =
    oruPartingDirection(index, tappedIndex) * OruPartingRiseDp * away

/** The wait of Focus section [step] (0 is the first), in milliseconds, capped at [OruCascadeCapMillis]. */
internal fun oruCascadeDelayMillis(step: Int): Int =
    (step.coerceAtLeast(0) * OruCascadeStepMillis).coerceAtMost(OruCascadeCapMillis)

/** The share of a stagger that a [delayMillis] wait takes, between 0 and [MaxStaggerShare]. */
internal fun oruStaggerShare(delayMillis: Int): Float =
    (delayMillis.coerceAtLeast(0).toFloat() / OruStaggerHorizonMillis).coerceAtMost(MaxStaggerShare)

/**
 * The stagger of an element whose whole transition is [global]: it holds still for the first [share] of the transition,
 * then runs over the rest. Not clamped above, so an overshoot of a spring still overshoots.
 */
internal fun oruStaggered(global: Float, share: Float): Float =
    if (share <= 0f) global else ((global - share) / (1f - share)).coerceAtLeast(0f)

/**
 * A Home card, leaving for its Focus. The cards above the tapped one move up and the cards below it move down by
 * [OruPartingRiseDp], shrink to [OruPartingScale], fade and blur. Each one waits [OruPartingStepMillis] per card
 * of distance. The tapped card does not move: its tile and name are the shared elements, and they travel from it.
 * Reduced motion keeps the fade only.
 *
 * @param index the card's place in the list of cards.
 * @param tappedIndex the place of the card that was tapped, or null when no card opened Focus.
 */
@Composable
fun Modifier.oruLeaveParting(index: Int, tappedIndex: Int?): Modifier {
    val reduced = oruReducedMotion()
    val presence = rememberOruPresence(OruMotion.expand(reduced))
    val blur = remember { OruBlurEffect() }
    // Without a tapped card, or for the tapped card itself, the origin is the card itself and nothing moves
    val origin = tappedIndex ?: index
    val active = index != origin
    val share = oruStaggerShare(oruPartingDelayMillis(index, origin))
    return graphicsLayer {
        if (!active) return@graphicsLayer
        val away = oruStaggered((1f - presence.value).coerceIn(0f, 1f), share)
        alpha = 1f - away
        if (!reduced) {
            translationY = oruPartingOffsetDp(index, origin, away) * density
            scaleX = 1f - (1f - OruPartingScale) * away
            scaleY = scaleX
            if (Build.VERSION.SDK_INT >= OruBlurMinSdk) {
                renderEffect = blur.of(away * OruPartingBlurDp * density)
            }
        }
    }
}

/** The Home island, leaving for Focus: it drops [OruDropDp] and fades. Reduced motion keeps the fade only. */
@Composable
fun Modifier.oruLeaveDrop(): Modifier {
    val reduced = oruReducedMotion()
    val presence = rememberOruPresence(OruMotion.expand(reduced))
    return graphicsLayer {
        val away = (1f - presence.value).coerceIn(0f, 1f)
        alpha = 1f - away
        if (!reduced) {
            translationY = away * OruDropDp * density
        }
    }
}

/**
 * A Focus section arriving: it rises [OruArriveRiseDp] into place with a fade and a blur that clears, on a spring
 * with a little overshoot. [step] sets its wait in the cascade (see [oruCascadeDelayMillis]). Reduced motion keeps the
 * fade only.
 *
 * Only for a section that holds no shared element: a shared element moved by an ancestor's transform would land
 * away from the place its bounds were measured at.
 */
@Composable
fun Modifier.oruArriveCascade(step: Int): Modifier {
    val reduced = oruReducedMotion()
    val presence = rememberOruPresence(OruMotion.springSoft(reduced))
    val share = oruStaggerShare(oruCascadeDelayMillis(step))
    val blur = remember { OruBlurEffect() }
    return graphicsLayer {
        val arrived = oruStaggered(presence.value, share)
        alpha = arrived.coerceIn(0f, 1f)
        if (!reduced) {
            translationY = (1f - arrived) * OruArriveRiseDp * density
            if (Build.VERSION.SDK_INT >= OruBlurMinSdk) {
                renderEffect = blur.of((1f - arrived).coerceIn(0f, 1f) * OruArriveBlurDp * density)
            }
        }
    }
}

/**
 * The Focus dock arriving: it rises [OruDockRiseDp] from the bottom on [OruMotion.springSoft], starting
 * [OruDockDelayMillis] after the Focus opens. Reduced motion keeps the fade only.
 */
@Composable
fun Modifier.oruArriveFromBottom(): Modifier {
    val reduced = oruReducedMotion()
    val presence = rememberOruPresence(OruMotion.springSoft(reduced))
    val share = oruStaggerShare(OruDockDelayMillis)
    return graphicsLayer {
        val arrived = oruStaggered(presence.value, share)
        alpha = arrived.coerceIn(0f, 1f)
        if (!reduced) {
            translationY = (1f - arrived) * OruDockRiseDp * density
        }
    }
}
