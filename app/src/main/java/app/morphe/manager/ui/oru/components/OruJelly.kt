/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.abs

/*
 * Tactile motion for controls and sheets. A pressed control squashes like a soft body and springs back past rest, and
 * the rows of a sheet rise in one after another when it opens. The arithmetic is pure, so it runs on the JVM.
 */

/**
 * How far a press squashes an element: the scale of each axis at full press. The release overshoots the other way by
 * [OruJellyOvershoot] of the same squash, and the element sinks [liftDp] while it is held.
 */
internal data class OruJelly(
    val pressScaleX: Float,
    val pressScaleY: Float,
    val liftDp: Float = 1f,
) {
    companion object {
        /** Buttons and toggle segments: a firm squash. */
        val Control = OruJelly(pressScaleX = 1.03f, pressScaleY = 0.94f)

        /** Large cards: a gentler squash, so a card does not wobble like a button. */
        val Card = OruJelly(pressScaleX = 1.01f, pressScaleY = 0.975f)
    }
}

/** How far past rest a release overshoots, as a share of the squash. 2/3 takes 0.94 to 1.04 and 1.03 to 0.98. */
internal const val OruJellyOvershoot = 2f / 3f

/** A release from a squash below this settles straight back to rest, without the overshoot. */
private const val ReleaseOvershootFloor = 0.05f

/**
 * The scale of one axis at squash [amount]: [pressScale] at full press (1), rest at 0, and past rest when the amount is
 * negative, as a release overshoots.
 */
internal fun oruJellyScale(pressScale: Float, amount: Float): Float = 1f + (pressScale - 1f) * amount

/**
 * Resists a drag past its edge, like a rubber band. [offset] is how far the drag has gone past the edge and [limit] the
 * most the surface may be pulled: it follows the finger at first and then less and less, and never passes [limit]. The
 * sign of [offset] is kept.
 */
internal fun oruRubberBand(offset: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    return offset * limit / (limit + abs(offset))
}

/**
 * The delay of the row at [index] in a sheet's cascade, in milliseconds: [OruMotion.CascadeStaggerMillis] per row,
 * stopped at [OruMotion.CascadeCapMillis]. A negative index counts as the first row.
 */
internal fun oruCascadeDelayMillis(index: Int): Int =
    (index.coerceAtLeast(0) * OruMotion.CascadeStaggerMillis).coerceAtMost(OruMotion.CascadeCapMillis)

/**
 * The squash of a pressed element, on [interactionSource]. While it is pressed the element squashes to [jelly] on the
 * snappy press spring and sinks a little, with a light haptic tick. When the press ends it springs past rest on the soft
 * spring, then settles. A press that starts again mid-release turns from where the element is, keeping its speed.
 *
 * Only the layer changes, so it costs no layout and no recomposition. Reduced motion keeps the tick and drops the
 * transform. This replaces the press scale of [oruPressScale]; do not put both on one element.
 */
@Composable
internal fun Modifier.oruJellyPress(
    interactionSource: InteractionSource,
    jelly: OruJelly = OruJelly.Control,
): Modifier {
    val reduced = oruReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val tick = rememberOruSelectTick()
    // 0 at rest, 1 when fully pressed, and below 0 while a release overshoots past rest
    val squash = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) tick()
    }
    LaunchedEffect(pressed, reduced) {
        if (reduced) return@LaunchedEffect
        if (pressed) {
            squash.animateTo(1f, OruMotion.springSnappy(reduced = false))
        } else {
            if (squash.value > ReleaseOvershootFloor) {
                squash.animateTo(-OruJellyOvershoot, OruMotion.springSoft(reduced = false))
            }
            squash.animateTo(0f, OruMotion.springSoft(reduced = false))
        }
    }
    if (reduced) return this
    return graphicsLayer {
        val amount = squash.value
        scaleX = oruJellyScale(jelly.pressScaleX, amount)
        scaleY = oruJellyScale(jelly.pressScaleY, amount)
        translationY = jelly.liftDp * amount * density
    }
}

/**
 * Whether the sheet around the content has entered. A sheet provides its own state, which turns true after its first
 * composition, so its rows cascade in once each time it opens. Outside a sheet it is always true, and rows show at once.
 */
internal val LocalOruCascade = compositionLocalOf<State<Boolean>> { OruCascadeEntered }

/** Never written, so every row outside a sheet counts as entered. */
private val OruCascadeEntered: State<Boolean> = mutableStateOf(true)

/**
 * Rises a row of a sheet into place after the rows above it. The row starts [OruMotion.CascadeRiseDp] lower and faded,
 * and blurs in on API 31 and above; it settles on the soft spring, so it overshoots a little. [index] is the row's place
 * in the sheet (see [oruCascadeDelayMillis]). It plays once each time the sheet opens. A row composed after that, such
 * as one on a page opened later, is already in place. Reduced motion shows it at once.
 */
@Composable
fun Modifier.oruCascade(index: Int): Modifier {
    val entered = LocalOruCascade.current
    val reduced = oruReducedMotion()
    val progress = remember { Animatable(if (reduced || entered.value) 1f else 0f) }
    val blur = remember { OruBlurEffect() }
    LaunchedEffect(entered.value, reduced) {
        if (entered.value && !reduced && progress.value < 1f) {
            delay(oruCascadeDelayMillis(index).toLong())
            progress.animateTo(1f, OruMotion.springSoft(reduced = false))
        }
    }
    if (reduced) return this
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * OruMotion.CascadeRiseDp * density
        if (Build.VERSION.SDK_INT >= OruBlurMinSdk) {
            renderEffect = blur.of((1f - p).coerceAtLeast(0f) * OruMotion.CascadeBlurDp * density)
        }
    }
}
