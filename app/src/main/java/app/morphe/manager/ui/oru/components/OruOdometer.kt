/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/*
 * A count that rolls like a mechanical odometer: each digit is a strip of the ten digits, and a change moves the strip
 * so the new digit slides up into view, blurred while it is still moving and sharp once it settles. A digit that does
 * not change does not move. Columns are keyed by their place from the right, so a count that gains a digit (9 to 10)
 * keeps the other columns where they are and the new leading column grows from nothing.
 */

/** The figures of a count are tabular, so every digit takes the width of the widest one and a roll does not shift the rest. */
internal const val TabularNumbers = "tnum"

private const val RingSize = 10

/**
 * The value shown as digits that roll to each new value. Negative values show as zero (see [oruOdometerDigits]).
 * Reduced motion swaps the digits at once. Starting a new roll while one is under way takes the digit from where it
 * is, so a fast count never jumps back.
 */
@Composable
fun OruOdometer(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val digits = oruOdometerDigits(value)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tabular = remember(style) { style.copy(fontFeatureSettings = TabularNumbers) }
    // The widest digit and the line height, measured once for the style
    val cell = remember(measurer, tabular) {
        val sizes = (0 until RingSize).map { measurer.measure(text = it.toString(), style = tabular).size }
        IntSize(width = sizes.maxOf { it.width }, height = sizes.maxOf { it.height })
    }
    val cellWidth = with(density) { cell.width.toDp() }
    val cellHeight = with(density) { cell.height.toDp() }
    // The columns of the first composition are the count the odometer starts with. A column made after it is a digit
    // the count has gained, and grows from nothing; the columns made before stay full size.
    val started = remember { mutableStateOf(false) }
    SideEffect { started.value = true }

    Row(
        modifier = modifier
            .height(cellHeight)
            // Measured unbounded, so a count wider than the slot it sits in keeps all its columns. Bounded, the Row
            // would hand its last columns no width and drop the units digit (100 showing as 10). The count grows past
            // the slot's left edge instead.
            .wrapContentWidth(Alignment.End, unbounded = true)
            .clearAndSetSemantics { contentDescription = value.coerceAtLeast(0).toString() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        digits.forEachIndexed { index, digit ->
            val place = digits.size - 1 - index
            key(place) {
                OruDigitColumn(
                    digit = digit,
                    appear = started.value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    style = tabular,
                    color = color,
                )
            }
        }
    }
}

/** The position of one column's strip, in digits, with the position it is rolling to and the blur of the roll. */
private class OruDigitRoll(digit: Int) {
    val position = Animatable(digit.toFloat())

    /** Where the roll is going. Read in the draw phase, for the blur that fades as the digit comes to rest. */
    var target = digit.toFloat()

    val blur = OruBlurEffect()
}

@Composable
private fun OruDigitColumn(
    digit: Int,
    appear: Boolean,
    cellWidth: Dp,
    cellHeight: Dp,
    style: TextStyle,
    color: Color,
) {
    val reduced = oruReducedMotion()
    val roll = remember { OruDigitRoll(digit) }
    // A new leading column grows from nothing. The others start at full size and never grow.
    val grow = remember { Animatable(if (appear) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear) grow.animateTo(1f, OruMotion.springSoft(reduced))
    }
    LaunchedEffect(digit) {
        val target = oruRollTarget(roll.position.value, digit)
        roll.target = target
        roll.position.animateTo(target, OruMotion.springSettle(reduced))
    }
    // Only the whole digit is composed. The column recomposes when the digit in view changes, not on every frame of a roll
    val lower by remember(roll) { derivedStateOf { floor(roll.position.value).toInt() } }
    val cellHeightPx = with(LocalDensity.current) { cellHeight.toPx() }

    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .clipToBounds()
            .graphicsLayer {
                scaleX = grow.value
                scaleY = grow.value
                alpha = grow.value.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        // The digit at the lower place of the strip and the one above it: the roll shows the pair that straddles it
        val below = lower
        val above = lower + 1
        Text(
            text = Math.floorMod(below, RingSize).toString(),
            style = style,
            color = color,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { placeDigit(roll, below, cellHeightPx) },
        )
        Text(
            text = Math.floorMod(above, RingSize).toString(),
            style = style,
            color = color,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { placeDigit(roll, above, cellHeightPx) },
        )
    }
}

/**
 * Puts digit [slot] of the strip where it belongs for the roll: digit k sits (k - position) cells below the middle, so a
 * roll up moves the next digit in from below. Blurred by how far the roll still has to go. Read in the draw phase only.
 */
private fun GraphicsLayerScope.placeDigit(roll: OruDigitRoll, slot: Int, cellHeightPx: Float) {
    val position = roll.position.value
    translationY = (slot - position) * cellHeightPx
    if (Build.VERSION.SDK_INT >= OruBlurMinSdk) {
        val remaining = min(1f, abs(position - roll.target))
        renderEffect = roll.blur.of(remaining * OruMotion.RollBlurDp * density)
    }
}
