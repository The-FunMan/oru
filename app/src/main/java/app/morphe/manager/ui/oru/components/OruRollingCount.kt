/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

/*
 * A OruOdometer in a slot of fixed width. The odometer keeps its columns where they are when a count gains a digit,
 * but only when its right edge stays put; a centred or left-aligned line would still move. The slot holds the widest
 * count the line will show, and the count sits at its right, so a count that grows goes to the left and whatever
 * follows it does not move.
 */

/**
 * A count that rolls like [OruOdometer], in a slot as wide as [slotDigits] digits. The count sits at the right of the
 * slot. A count wider than its slot grows past the left edge of it, and does not push the text after it.
 */
@Composable
internal fun OruRollingCount(
    value: Int,
    slotDigits: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Measured with the figures the odometer draws, so every digit has the width of the widest one
    val slotWidth = remember(measurer, style, slotDigits) {
        val figures = "0".repeat(slotDigits.coerceAtLeast(1))
        val measured = measurer.measure(text = figures, style = style.copy(fontFeatureSettings = TabularNumbers))
        with(density) { measured.size.width.toDp() }
    }
    Box(modifier = modifier.width(slotWidth), contentAlignment = Alignment.CenterEnd) {
        OruOdometer(value = value, style = style, color = color)
    }
}
