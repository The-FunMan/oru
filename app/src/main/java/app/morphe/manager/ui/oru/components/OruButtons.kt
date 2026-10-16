/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruPillShape
import app.morphe.manager.ui.oru.theme.OruSpacing

/** One filled action per surface. 48 dp tall, pill shaped, pressed with the press spring. */
@Composable
fun OruPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OruButton(
        text = text,
        style = OruButtonStyle.Filled,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
    )
}

/** A tonal action, for the second step of a surface. */
@Composable
fun OruSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OruButton(
        text = text,
        style = OruButtonStyle.Tonal,
        onClick = onClick,
        modifier = modifier,
    )
}

/** An outlined action, for a choice that is not the main path. */
@Composable
fun OruOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OruButton(
        text = text,
        style = OruButtonStyle.Outlined,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * A text action in [color], for a step that leaves the surface, such as Cancel. It has no fill and no outline, so it
 * never reads as the main path, and it keeps the 48 dp touch target.
 */
@Composable
fun OruTextButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = OruSpacing.TouchTarget)
            .clip(OruPillShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = ButtonPaddingHorizontal, vertical = ButtonPaddingVertical),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [allowTwoLines] is for a button inside a card, where the label may take a second line at a large font
 * scale rather than be cut. The button grows in height to fit it. Dock buttons keep one line.
 */
@Composable
internal fun OruButton(
    text: String,
    style: OruButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    allowTwoLines: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val outlined = style == OruButtonStyle.Outlined
    // Inside an app's Focus or card, a filled button takes the app's fill and its text, and an outlined one the app's ink
    val appTint = LocalOruAppTint.current
    val filledColor = appTint?.fill ?: scheme.primary
    val container: Color = when {
        !enabled -> if (outlined) Color.Transparent else scheme.onSurface.copy(alpha = DisabledContainerAlpha)
        style == OruButtonStyle.Filled -> filledColor
        style == OruButtonStyle.Tonal -> scheme.secondaryContainer
        else -> Color.Transparent
    }
    val content: Color = when {
        !enabled -> scheme.onSurface.copy(alpha = DisabledContentAlpha)
        style == OruButtonStyle.Filled -> appTint?.onFill ?: scheme.onPrimary
        style == OruButtonStyle.Tonal -> scheme.onSecondaryContainer
        else -> appTint?.ink ?: scheme.primary
    }
    val shape = OruPillShape
    // A filled or tonal button stands on its slab: a contact shadow, a lit rim, a soft light over its top. It sinks
    // when pressed. An outlined or a disabled one stays flat on the surface
    val press = rememberOruPress(interactionSource)
    val raised = enabled && !outlined
    val elevation = if (raised) OruElevation.Raised else OruElevation.Flat
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = OruSpacing.TouchTarget)
            .oruJellyPress(interactionSource)
            .oruShadow(
                shape = shape,
                elevation = elevation,
                tint = if (style == OruButtonStyle.Filled) filledColor else Color.Unspecified,
                compact = true,
                press = press,
            )
            .clip(shape)
            .background(container)
            .oruEdgeLight(shape = shape, elevation = elevation, press = press)
            .then(if (outlined) Modifier.border(width = OutlineWidth, color = scheme.outline, shape = shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ButtonPaddingHorizontal,
                vertical = if (allowTwoLines) CardButtonPaddingVertical else ButtonPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(ButtonIconSize),
                )
            }
            // A dock button holds one line and ends with an ellipsis if it cannot fit. A card button may take a
            // second line first, so its label is not cut at a large font scale.
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                textAlign = TextAlign.Center,
                maxLines = if (allowTwoLines) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val DisabledContainerAlpha = 0.12f
private const val DisabledContentAlpha = 0.38f
private val ButtonPaddingHorizontal: Dp = 22.dp
private val ButtonPaddingVertical: Dp = 12.dp
private val CardButtonPaddingVertical: Dp = 8.dp
private val ButtonIconSize: Dp = 20.dp
private val OutlineWidth: Dp = 1.dp
