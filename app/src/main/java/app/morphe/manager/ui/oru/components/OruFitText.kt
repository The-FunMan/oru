/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * One line of text that never wraps and never ends in an ellipsis: when it is wider than its space it steps
 * its font size down, to [minSize] at most, so a figure such as "115.6 MB" stays whole at a large font scale.
 * [style] must set its font size in sp. The size resets when [text] changes.
 */
@Composable
fun OruFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSize: TextUnit = OruFitMinSize,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val baseSp = style.fontSize.takeIf { it.isSp }?.value ?: OruFitFallbackSp
    var sizeSp by remember(text, baseSp) { mutableStateOf(baseSp) }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = sizeSp.sp),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result: TextLayoutResult ->
            val overflow = result.didOverflowWidth || result.hasVisualOverflow
            val next = oruFitTextSize(sizeSp, minSize.value, overflow)
            if (next != sizeSp) sizeSp = next
        },
    )
}

/**
 * The font size, in sp, for the next layout of a fitting text. A text that overflows steps down by
 * [OruFitStepSp] and never below [minSp]; one that fits keeps its size.
 */
internal fun oruFitTextSize(currentSp: Float, minSp: Float, overflow: Boolean): Float =
    if (overflow && currentSp > minSp) (currentSp - OruFitStepSp).coerceAtLeast(minSp) else currentSp

internal const val OruFitStepSp = 0.5f
private const val OruFitFallbackSp = 14f
internal val OruFitMinSize: TextUnit = 10.sp
