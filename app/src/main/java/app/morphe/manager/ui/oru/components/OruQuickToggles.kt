/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.oru.theme.OruThemeMode

/**
 * The theme choice of the Settings flyout: Follow the phone, Light and Dark as one connected group. It reads and
 * writes the stored theme, and the themed tree behind the sheet repaints when it changes.
 */
@Composable
fun OruThemeToggle(
    themeMode: OruThemeMode,
    onThemeModeChange: (OruThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        OruQuickToggleOption(
            contentDescription = stringResource(R.string.oru_quick_theme_system),
            icon = Icons.Outlined.PhoneAndroid,
        ),
        OruQuickToggleOption(
            contentDescription = stringResource(R.string.oru_quick_theme_light),
            icon = Icons.Outlined.LightMode,
        ),
        OruQuickToggleOption(
            contentDescription = stringResource(R.string.oru_quick_theme_dark),
            icon = Icons.Outlined.DarkMode,
        ),
    )
    OruQuickToggleGroup(
        options = options,
        selectedIndex = themeMode.ordinal,
        onSelect = { onThemeModeChange(OruThemeMode.entries[it]) },
        groupLabel = stringResource(R.string.oru_quick_theme_group),
        modifier = modifier,
    )
}

/**
 * The glass choice of the Settings flyout: Frosted or Clear, as one connected group over the stored glass style.
 * The sheet's glass reads the same style, so Clear shows under the finger.
 */
@Composable
fun OruGlassToggle(
    glassStyle: OruGlassStyle,
    onGlassStyleChange: (OruGlassStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        OruQuickToggleOption(
            contentDescription = stringResource(R.string.oru_quick_glass_frosted_description),
            label = stringResource(R.string.oru_quick_glass_frosted),
        ),
        OruQuickToggleOption(
            contentDescription = stringResource(R.string.oru_quick_glass_clear_description),
            label = stringResource(R.string.oru_quick_glass_clear),
        ),
    )
    OruQuickToggleGroup(
        options = options,
        selectedIndex = glassStyle.ordinal,
        onSelect = { onGlassStyleChange(OruGlassStyle.entries[it]) },
        groupLabel = stringResource(R.string.oru_quick_glass_group),
        modifier = modifier,
    )
}

/** One segment of a [OruQuickToggleGroup]. Set [icon], [label] or both. [contentDescription] is what a screen reader says. */
@Immutable
data class OruQuickToggleOption(
    val contentDescription: String,
    val label: String? = null,
    val icon: ImageVector? = null,
)

/**
 * One choice out of [options], as a glass pill of connected segments. [onSelect] receives the index of the tapped
 * segment. The selected segment takes the tonal container and a round shape, the others keep their outer corners
 * round and square the inner ones, as the Simple and Expert pair does.
 *
 * [groupLabel] names the whole group for screen readers.
 */
@Composable
fun OruQuickToggleGroup(
    options: List<OruQuickToggleOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    groupLabel: String,
    modifier: Modifier = Modifier,
) {
    OruGlassSurface(shape = oruGlassShape(OruGlassForm.Pill), modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(GroupPadding)
                .semantics { contentDescription = groupLabel }
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SegmentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                OruQuickToggleSegment(
                    option = option,
                    selected = index == selectedIndex,
                    isFirst = index == 0,
                    isLast = index == options.lastIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun OruQuickToggleSegment(
    option: OruQuickToggleOption,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val reduced = oruReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val startRadius by animateDpAsState(
        targetValue = oruSegmentCornerDp(selected = selected, outerCorner = isFirst).dp,
        animationSpec = OruMotion.expand(reduced),
        label = "oruQuickSegmentStart",
    )
    val endRadius by animateDpAsState(
        targetValue = oruSegmentCornerDp(selected = selected, outerCorner = isLast).dp,
        animationSpec = OruMotion.expand(reduced),
        label = "oruQuickSegmentEnd",
    )
    // An unselected segment is clear, so the glass of the pill shows through; the selected one is filled and lifted
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.secondaryContainer.copy(alpha = 0f),
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruQuickSegmentContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruQuickSegmentContent",
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
            .defaultMinSize(minWidth = OruSpacing.TouchTarget, minHeight = OruSpacing.TouchTarget)
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
                role = Role.RadioButton,
                onClick = {
                    if (!selected) tick()
                    onClick()
                },
            )
            // One focus stop per segment, named by its content description
            .semantics(mergeDescendants = true) { contentDescription = option.contentDescription }
            .padding(horizontal = SegmentPaddingHorizontal),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        option.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(IconSize),
            )
        }
        option.label?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = content,
            )
        }
    }
}

private val GroupPadding = 4.dp
private val SegmentGap = 2.dp
private val SegmentPaddingHorizontal = 12.dp
private val IconSize = 22.dp
