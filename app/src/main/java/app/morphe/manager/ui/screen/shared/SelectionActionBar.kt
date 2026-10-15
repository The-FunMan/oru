/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.ThemeTraitsDefaults

/** Shape of the [MultiSelectShell] surface: rounded where it leaves the bottom edge, like a sheet. */
private val ShellShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Slide-up surface hosting a selection panel or a reorder bar. It sits on the bottom edge of the
 * screen like a sheet, with its content kept clear of the navigation bar, so it is meant for the
 * home screen's footer dock and the [AppDialog] bottom bar. Keeps the surface, elevation and
 * enter/exit animations the same everywhere a selection is made.
 *
 * @param onBack Closes the panel on back, which then follows the predictive gesture down like
 * an [AppBottomSheet] does. Required so no panel is left to a back handler that cannot animate
 * it, such as the dismissal of the [AppDialog] it is docked to.
 */
@Composable
fun MultiSelectShell(
    visible: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backProgress = remember { Animatable(0f) }
    // A gesture that closed the panel leaves it drawn down, which the next opening must not inherit
    LaunchedEffect(visible) { if (visible) backProgress.snapTo(0f) }
    PredictiveBackSlideHandler(backProgress, enabled = visible, onBack = onBack)

    AnimatedVisibility(
        visible = visible,
        enter = Animations.springSlideUpEnter,
        exit = Animations.springSlideDownExit,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Defaults.SheetSideInset)
                .predictiveBackSlide(backProgress),
            shape = ShellShape,
            // Drawn like an AppBottomSheet, on the dialogs' background with an edge of its own, so
            // a panel docked over a list reads as the same kind of surface as a sheet
            color = ThemeTraitsDefaults.surfaceColor(MaterialTheme.colorScheme.background),
            border = CardBorder.neutral,
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 4.dp)) { content() }
        }
    }
}

private class LastVisibleValue<T>(var value: T)

/**
 * Returns [value] while [visible] is true, and the last value seen before that afterward.
 *
 * Action handlers clear the selection in the same pass that hides the bar, so a row rendered
 * from live state loses buttons and zeroes its counter while it is still sliding out.
 *
 * This holds only while both writes land in one snapshot. A handler that hides the bar and
 * clears its state in separate frames has nothing left to freeze by the time [visible] flips.
 */
@Composable
fun <T> rememberWhileVisible(visible: Boolean, value: T): T {
    val holder = remember { LastVisibleValue(value) }
    if (visible) holder.value = value
    return holder.value
}

/**
 * Top line of a panel in a [MultiSelectShell] or of an [AppBottomSheet]: what it is about at the
 * start, and its controls, if any, at the end, which are [TitleAction]s so they read like the
 * ones in a dialog's title row.
 */
@Composable
fun PanelHeader(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { title() }
        // Outside a dialog nothing sets the text color title actions tint with, so they take the
        // panel's own
        CompositionLocalProvider(LocalDialogTextColor provides LocalContentColor.current) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Title of a [PanelHeader], set like the section headers across the app. */
@Composable
fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Line under a [PanelTitle] with a detail of what the panel holds, such as a count or a size. */
@Composable
fun PanelSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Gap between a panel's edge and the plate a pressed action row lights up. */
private val PanelActionInset = Defaults.ContentPaddingSmall

private val PanelActionShape = RoundedCornerShape(Defaults.CompactCornerRadius)

/** What an action of a panel does, as far as its color goes. */
enum class ActionTone { Neutral, Primary, Secondary, Tertiary, Destructive }

/**
 * One action of a panel, listed by [PanelActions] under its [label]. The label also keys its row,
 * so no two actions of one panel share a label.
 */
@Immutable
data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tone: ActionTone = ActionTone.Neutral
)

/**
 * Selection panel: the "N selected" counter with SelectAll (or DeselectAll) and Cancel, over every
 * one of [actions] as a row under its full name. Meant to be placed inside a [MultiSelectShell].
 *
 * None of the actions has anything to act on until something is selected.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onDeselectAll: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    val selectAllLabel = stringResource(R.string.select_all)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val cancelLabel = stringResource(android.R.string.cancel)
    val selectedLabel = stringResource(R.string.selected).lowercase()
    val allSelected = totalCount in 1..selectedCount
    val canToggleToDeselect = allSelected && onDeselectAll != null
    val selectionToggleLabel = if (canToggleToDeselect) deselectAllLabel else selectAllLabel
    val hasSelection = selectedCount > 0

    Column(modifier = modifier.fillMaxWidth()) {
        PanelHeader(
            title = {
                Column {
                    AnimatedContent(
                        targetState = selectedCount,
                        transitionSpec = Animations.compactCounterTransitionSpec,
                        label = "selected_count"
                    ) { count ->
                        PanelTitle(text = "$count $selectedLabel")
                    }
                    if (subtitle != null) {
                        AnimatedContent(
                            targetState = subtitle,
                            transitionSpec = Animations.compactCounterTransitionSpec,
                            label = "selection_subtitle"
                        ) { text ->
                            PanelSubtitle(text = text)
                        }
                    }
                }
            }
        ) {
            TitleAction(
                icon = if (canToggleToDeselect) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
                contentDescription = selectionToggleLabel,
                onClick = { if (canToggleToDeselect) onDeselectAll() else onSelectAll() },
                style = TitleActionStyle.Toggle,
                active = canToggleToDeselect,
                enabled = canToggleToDeselect || selectedCount < totalCount
            )
            if (onCancel != null) {
                TitleAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = cancelLabel,
                    onClick = onCancel,
                    style = TitleActionStyle.Neutral
                )
            }
        }

        PanelActions(actions = actions, enabled = hasSelection)
    }
}

/**
 * The actions of a panel in a [MultiSelectShell], one row each under its full name, so what every
 * one does reads without guessing from an icon. Nothing is folded away, and the rows are kept
 * short enough for the panel to leave the list above it usable. Destructive actions are kept
 * apart from the rest by a divider. [enabled] holds them all back at once, as an empty selection
 * does.
 */
@Composable
fun PanelActions(actions: List<SelectionAction>, enabled: Boolean = true) {
    actions.forEachIndexed { index, action ->
        val startsDestructiveGroup = action.tone == ActionTone.Destructive &&
                index > 0 && actions[index - 1].tone != ActionTone.Destructive
        if (startsDestructiveGroup) {
            SettingsDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        // Keyed so a row keeps its own state when an action ahead of it comes or goes
        key(action.label) {
            PanelActionRow(action = action, enabled = enabled && action.enabled)
        }
    }
}

@Composable
private fun PanelActionRow(action: SelectionAction, enabled: Boolean) {
    val alpha = if (enabled) 1f else Defaults.DISABLED_ALPHA
    val accent = action.tone.accentColor()
    // The icon carries the tone; the label only takes it as a warning, so the list reads as one
    // piece with its destructive entries standing out
    val textColor = if (action.tone == ActionTone.Destructive) accent else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Pressed like a menu item, on a rounded plate held off the panel's edge, so the
            // highlight does not run into the border and the corners the panel is drawn with
            .padding(horizontal = PanelActionInset)
            .clip(PanelActionShape)
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = action.onClick)
            .padding(horizontal = Defaults.ContentPadding - PanelActionInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = accent.copy(alpha = accent.alpha * alpha),
            modifier = Modifier.size(Defaults.IconSize)
        )
        // Set like the item rows across the app
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = textColor.alpha * alpha)
        )
    }
}

/** The tone as a color that stands on the plain surface a panel is drawn over. */
@Composable
private fun ActionTone.accentColor(): Color = when (this) {
    ActionTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    ActionTone.Primary -> MaterialTheme.colorScheme.primary
    ActionTone.Secondary -> MaterialTheme.colorScheme.secondary
    ActionTone.Tertiary -> MaterialTheme.colorScheme.tertiary
    ActionTone.Destructive -> destructiveColor()
}
