/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.min

/*
 * A label that grows into a pill button. Collapsed, it is only text in the app's ink. Expanded, a filled pill opens
 * around the same text: the pill grows from the text's own bounds, its corners round off as it grows, and the text
 * changes colour to the fill's text colour. The expansion is a soft spring with a little squash at its start.
 *
 * The pill uses the depth system of the app (the cast shadow and the edge light), faded in with the expansion, so it
 * stands on the page like a button does. A collapsed label is not a button: it takes no input until it is expanded.
 */

/** Squash at the start of the expansion: the pill is 4 percent wider and 4 percent shorter, and settles to 1. */
private const val SquashAmount = 0.04f

/** A pressed pill shrinks to this scale on the snappy spring. */
private const val MorphPressScale = 0.96f

/** The padding around the text when fully expanded, in dp. The collapsed label has none. */
private const val PillPaddingHorizontalDp = 22f
private const val PillPaddingVerticalDp = 12f

/** The corner radius of the pill while it is still close to the text, in dp. It rounds off into a full pill. */
private const val StartCornerDp = 4f

/** Fully rounded ends. The depth system draws its shadow and edge against this shape. */
private val PillShape = RoundedCornerShape(percent = 50)

/**
 * A [label] that is text in [accent] (the app's ink when null) while [expanded] is false, and a filled pill around the
 * text while it is true. The pill is filled with [accent] (or the app's fill), and the text takes the readable colour on
 * it. [onClick] runs when the expanded pill is tapped; a collapsed label takes no taps. Reduced motion expands at once.
 */
@Composable
fun OruLabelToButton(
    expanded: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val reduced = oruReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    // 0 collapsed, 1 expanded. Springs overshoot a little, so the padding can briefly go past its size
    val expansion = remember { Animatable(if (expanded) 1f else 0f) }
    // 1 at the start of an expansion, settling to 0: the squash
    val squash = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        expansion.animateTo(if (expanded) 1f else 0f, OruMotion.springSoft(reduced))
    }
    LaunchedEffect(expanded) {
        if (expanded && !reduced) {
            squash.snapTo(1f)
            squash.animateTo(0f, OruMotion.springSoft(reduced = false))
        }
    }

    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && expanded) MorphPressScale else 1f,
        animationSpec = OruMotion.springSnappy(reduced),
        label = "oruLabelPress",
    )
    // A tick as the finger goes down on an expanded pill, the same tick a chosen segment makes
    val tick = rememberOruSelectTick()
    LaunchedEffect(pressed) {
        if (pressed && expanded) tick()
    }

    val ink = accent ?: oruInk()
    val fill = accent ?: oruSelectedFill()
    val onFill = if (accent != null) Color(oruFillText(accent.toArgb())) else oruSelectedContent()
    val textColor by animateColorAsState(
        targetValue = if (expanded) onFill else ink,
        animationSpec = OruMotion.accentMorph<Color>(reduced),
        label = "oruLabelInk",
    )
    // The padding is read here, in composition: it is the one thing of the label that changes size
    val grow = expansion.value.coerceAtLeast(0f)
    val expanding: State<Float> = expansion.asState()

    Box(
        modifier = modifier
            .graphicsLayer {
                val squashed = squash.value
                scaleX = pressScale * (1f + SquashAmount * squashed)
                scaleY = pressScale * (1f - SquashAmount * squashed)
            }
            .oruShadow(
                shape = PillShape,
                elevation = OruElevation.Raised,
                tint = fill,
                compact = true,
                strength = expanding,
            )
            .drawPillFill(fill = fill, amount = expanding)
            .oruEdgeLight(shape = PillShape, elevation = OruElevation.Raised, strength = expanding)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = expanded,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = (PillPaddingHorizontalDp * grow).dp,
                vertical = (PillPaddingVerticalDp * grow).dp,
            ),
        )
    }
}

/**
 * Fills the pill with [fill] as the expansion [amount] grows: transparent at 0, opaque at 1. The corner radius moves from
 * [StartCornerDp] to half the height, so the pill is round at the end. Read in the draw phase, so it costs no
 * recomposition per frame.
 */
private fun Modifier.drawPillFill(fill: Color, amount: State<Float>): Modifier =
    drawBehind {
        val share = amount.value.coerceIn(0f, 1f)
        if (share <= 0f) return@drawBehind
        val fullRadius = min(size.width, size.height) / 2f
        val startRadius = StartCornerDp.dp.toPx()
        val radius = startRadius + (fullRadius - startRadius) * share
        drawRoundRect(color = fill, alpha = share, cornerRadius = CornerRadius(radius))
    }
