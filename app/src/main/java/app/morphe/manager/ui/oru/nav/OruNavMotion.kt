/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import app.morphe.manager.ui.oru.theme.OruMotion

/*
 * Screen transitions of the oru navigation host. A screen fades and moves a little and is never scaled:
 * a shared element sits inside the screen, and a scale on that screen moves its bounds off the place the
 * shared bounds were measured at, so the morph from a card to its Focus would start and end in the wrong place.
 */

/** A screen coming in: it fades and rises from a little below. */
internal fun oruScreenEnter(reduced: Boolean): EnterTransition =
    if (reduced) {
        EnterTransition.None
    } else {
        fadeIn(tween(OruMotion.ScreenFadeMillis)) +
            slideInVertically(OruMotion.expand<IntOffset>(reduced)) { it / OruMotion.ScreenSlideShare }
    }

/** A screen going out: it fades and rises a little. */
internal fun oruScreenExit(reduced: Boolean): ExitTransition =
    if (reduced) {
        ExitTransition.None
    } else {
        fadeOut(tween(OruMotion.ScreenFadeMillis)) +
            slideOutVertically(OruMotion.expand<IntOffset>(reduced)) { -it / OruMotion.ScreenSlideShare }
    }
