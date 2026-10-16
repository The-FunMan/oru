/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruMotion

/**
 * A line that changes to another one. The old text fades out and moves up [OruMotion.SwapOutDp], the new
 * text fades in from [OruMotion.SwapInDp] below, both on the swap token (250 ms, FastOutSlowIn). No blur.
 * Reduced motion swaps at once.
 *
 * Used for the Focus title and line, and for the same two places on the run and the result screens.
 */
@Composable
fun OruSwapText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    // Where the old and the new line sit while they swap; a centred title needs Alignment.Center
    contentAlignment: Alignment = Alignment.TopStart,
) {
    val reduced = oruReducedMotion()
    val density = LocalDensity.current
    val outPx = with(density) { OruMotion.SwapOutDp.dp.roundToPx() }
    val inPx = with(density) { OruMotion.SwapInDp.dp.roundToPx() }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(OruMotion.swapText(reduced)) + slideInVertically(OruMotion.swapText(reduced)) { inPx }) togetherWith
                    (fadeOut(OruMotion.swapText(reduced)) + slideOutVertically(OruMotion.swapText(reduced)) { outPx })
            }
        },
        label = "oruSwapText",
    ) { current ->
        Text(text = current, style = style, color = color, textAlign = TextAlign.Center)
    }
}
