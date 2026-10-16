/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Delay between two neighbouring cards of the entrance. */
internal const val OruEntranceStaggerMillis = 45

/** The stagger stops growing after this many cards, so a long list does not make the last card wait. */
internal const val OruEntranceMaxSteps = 5

/** Length of one card's fade and rise. */
internal const val OruEntranceMillis = 300

/** How far a card rises into place. */
private val EntranceRise = 12.dp

/** The delay of the card at [index] in the entrance, in milliseconds. Negative indexes count as the first card. */
internal fun oruEntranceDelayMillis(index: Int): Int =
    index.coerceIn(0, OruEntranceMaxSteps) * OruEntranceStaggerMillis

/**
 * The cards of one Home screen that have had their entrance. A card that is composed again, after it
 * scrolled away and back, is already here and shows at once. Remember one per screen with
 * [rememberOruEntranceLedger].
 */
class OruEntranceLedger {
    private val played = HashSet<String>()

    /** True the first time [id] is asked about, so that one composition plays the entrance. */
    internal fun claim(id: String): Boolean = played.add(id)
}

/** The ledger of the Home screen, kept for as long as the screen's composition is. */
@Composable
fun rememberOruEntranceLedger(): OruEntranceLedger = remember { OruEntranceLedger() }

/**
 * Fades a Home card in and lifts it [EntranceRise] into place, after a stagger of [index] cards. It plays
 * once per card: [ledger] remembers the cards that already had it, so scrolling and recomposition do not
 * replay it. Reduced motion shows the card at once. The transform is read in the draw phase, so the fade
 * costs no recomposition per frame.
 */
@Composable
fun Modifier.oruEntrance(
    id: String,
    index: Int,
    ledger: OruEntranceLedger,
): Modifier {
    val reduced = oruReducedMotion()
    val density = LocalDensity.current
    val risePx = with(density) { EntranceRise.toPx() }
    val plays = remember(id) { ledger.claim(id) && !reduced }
    val progress = remember(id) { Animatable(if (plays) 0f else 1f) }
    LaunchedEffect(id) {
        if (plays) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = OruEntranceMillis,
                    delayMillis = oruEntranceDelayMillis(index),
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * risePx
    }
}
