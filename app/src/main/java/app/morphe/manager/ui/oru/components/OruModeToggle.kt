/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSpacing

/** Simple or Expert, as one connected pair of 48 dp radio options. */
@Composable
fun OruModeToggle(
    expert: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The pair sits in a glass pill, which blurs the orbs behind the Home list
    OruGlassSurface(shape = oruGlassShape(OruGlassForm.Pill), modifier = modifier) {
        OruConnectedPair(
            groupLabel = stringResource(R.string.oru_mode_label),
            firstLabel = stringResource(R.string.oru_mode_simple),
            secondLabel = stringResource(R.string.oru_mode_expert),
            secondSelected = expert,
            onSelect = onChange,
            modifier = Modifier.padding(4.dp),
        )
    }
}

/**
 * Two connected radio options. [onSelect] receives true for the second option. The selected option is a
 * pill, and the other one has its inner corners squared.
 */
@Composable
internal fun OruConnectedPair(
    groupLabel: String,
    firstLabel: String,
    secondLabel: String,
    secondSelected: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .semantics { contentDescription = groupLabel }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OruConnectedOption(
            label = firstLabel,
            selected = !secondSelected,
            enabled = true,
            isFirst = true,
            onClick = { onSelect(false) },
        )
        OruConnectedOption(
            label = secondLabel,
            selected = secondSelected,
            enabled = secondEnabled,
            isFirst = false,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun OruConnectedOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    isFirst: Boolean,
    onClick: () -> Unit,
) {
    val reduced = oruReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    // Each side keeps its outer corner round; the inner corner squares off unless the option is selected
    val startRadius by animateDpAsState(
        targetValue = oruSegmentCornerDp(selected = selected, outerCorner = isFirst).dp,
        animationSpec = OruMotion.expand(reduced),
        label = "oruSegmentStart",
    )
    val endRadius by animateDpAsState(
        targetValue = oruSegmentCornerDp(selected = selected, outerCorner = !isFirst).dp,
        animationSpec = OruMotion.expand(reduced),
        label = "oruSegmentEnd",
    )
    // An unselected option is clear, so the surface behind the pair shows through; the selected one is filled and lifted
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.secondaryContainer.copy(alpha = 0f),
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruSegmentContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruSegmentContent",
    )
    val shape = RoundedCornerShape(
        topStart = startRadius,
        bottomStart = startRadius,
        topEnd = endRadius,
        bottomEnd = endRadius,
    )
    val pulse = rememberOruSelectPulse(selected)
    val lift = rememberOruPillLift(selected)
    val tick = rememberOruSelectTick()
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = OruSpacing.TouchTarget)
            .oruJellyPress(interactionSource)
            .oruPulseScale(pulse)
            .oruPillShadow(shape, lift)
            .clip(shape)
            .background(container)
            .oruPillLit(shape, lift, pulse)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) tick()
                    onClick()
                },
            )
            .padding(horizontal = OptionPaddingHorizontal, vertical = OptionPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check grows in from its own width, and its gap comes with it, so an unselected label sits flush
        AnimatedVisibility(
            visible = selected,
            enter = if (reduced) {
                EnterTransition.None
            } else {
                fadeIn(tween(OruMotion.ExpandFadeMillis)) +
                    expandHorizontally(OruMotion.expand(reduced), expandFrom = Alignment.Start)
            },
            exit = if (reduced) {
                ExitTransition.None
            } else {
                fadeOut(tween(OruMotion.ExpandFadeMillis)) +
                    shrinkHorizontally(OruMotion.expand(reduced), shrinkTowards = Alignment.Start)
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(CheckSize).padding(end = CheckGap),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) content else content.copy(alpha = DisabledAlpha),
        )
    }
}

/**
 * The width of [OruConnectedPair] in dp, given the widths of its two labels: the padding of both options, the check of
 * the selected option (one option is always selected, so one check is reserved) and the gap between the options.
 */
internal fun oruConnectedPairDp(firstLabelDp: Float, secondLabelDp: Float): Float =
    firstLabelDp + secondLabelDp + 2f * 2f * OptionPaddingHorizontal.value + CheckSize.value + CheckGap.value + 2f

/**
 * Corner radius of one side of a segment, in dp. A selected option is a pill, so every corner is round. An
 * unselected option keeps its outer corner round and squares its inner one.
 */
internal fun oruSegmentCornerDp(selected: Boolean, outerCorner: Boolean): Float =
    if (selected || outerCorner) SegmentRoundDp else SegmentSquareDp

private const val SegmentRoundDp = 20f
private const val SegmentSquareDp = 8f
private val OptionPaddingHorizontal = 16.dp
private val OptionPaddingVertical = 10.dp
private val CheckSize = 18.dp
private val CheckGap = 6.dp
private const val DisabledAlpha = 0.38f
