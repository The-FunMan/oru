/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.ui.oru.components.OruFrostedDock
import app.morphe.manager.ui.oru.components.OruElevation
import app.morphe.manager.ui.oru.components.LocalOruAppTint
import app.morphe.manager.ui.oru.components.oruEdgeLight
import app.morphe.manager.ui.oru.components.oruPressScale
import app.morphe.manager.ui.oru.components.oruShadow
import app.morphe.manager.ui.oru.components.rememberOruPress
import app.morphe.manager.ui.oru.nav.oruSharedPill
import app.morphe.manager.ui.oru.theme.OruPillShape
import app.morphe.manager.ui.oru.theme.OruSurfaceFill

/*
 * The Focus dock. One primary action spans the width; up to two secondary actions sit in one row beneath it as
 * outlined buttons. Labels never wrap: each button holds one line, so the dock is at most two rows tall.
 */

/** Every action a Focus dock can hold. The rule picks the action; the body picks its label and handler. */
internal enum class DockAction {
    FindOnApkMirror,
    UseFile,
    GetApk,
    OpenApkMirror,
    Back,
    ChooseFile,
    OpenAgain,
    UseSavedCopy,
    UseInstalledCopy,
    StartRun,
}

/** Most secondary actions one dock holds. */
internal const val MaxDockSecondaries = 2

/** The actions of one dock: the primary one (full width) and its secondaries (one row beneath it). */
internal data class DockPlan(val primary: DockAction, val secondaries: List<DockAction> = emptyList()) {
    init {
        require(secondaries.size <= MaxDockSecondaries) { "A dock holds at most $MaxDockSecondaries secondaries" }
        require(primary !in secondaries) { "The primary action is not also a secondary" }
        require(secondaries.distinct().size == secondaries.size) { "A secondary action is listed twice" }
    }
}

/**
 * The dock rule: which action is primary for each state, and which ones sit beneath it. Null when the state
 * has no dock (its answers are in the notices, or the run owns the screen).
 *
 * [pending] is HomeViewModel.pendingPackageName is this app; [hasSaved] and [hasInstalled] say whether a saved
 * copy or the installed app can be patched. The copy the phone already has comes first.
 */
internal fun oruDockPlan(
    state: OruFocusState,
    pending: Boolean,
    hasSaved: Boolean,
    hasInstalled: Boolean,
): DockPlan? = when (state) {
    is OruFocusState.Choose -> when {
        state.stage == OruChooseStage.Download -> DockPlan(DockAction.OpenApkMirror, listOf(DockAction.Back))
        !pending -> DockPlan(DockAction.GetApk)
        hasSaved -> DockPlan(DockAction.UseSavedCopy, listOf(DockAction.FindOnApkMirror, DockAction.UseFile))
        hasInstalled -> DockPlan(DockAction.UseInstalledCopy, listOf(DockAction.FindOnApkMirror, DockAction.UseFile))
        else -> DockPlan(DockAction.FindOnApkMirror, listOf(DockAction.UseFile))
    }
    OruFocusState.WaitingForFile -> DockPlan(DockAction.ChooseFile, listOf(DockAction.OpenAgain))
    is OruFocusState.Ready -> DockPlan(DockAction.StartRun)
    else -> null
}

/** One button of the dock. [shortLabel] is the label in the secondary row, where a button is about half the width. */
internal class DockItem(
    val label: String,
    val shortLabel: String = label,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The dock of the Focus: [plan] drawn over the frost of [frost]. [item] gives each action its label and handler.
 * The primary button is full width; the secondaries share the row beneath it. [primarySharedKey] makes the primary
 * button the shared element of the card it opened from (see oruCtaSharedKey).
 */
@Composable
internal fun OruFocusDock(
    plan: DockPlan,
    item: (DockAction) -> DockItem,
    frost: OruSurfaceFill,
    modifier: Modifier = Modifier,
    primarySharedKey: String? = null,
) {
    OruFrostedDock(frost = frost, modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            val primary = item(plan.primary)
            DockButton(
                item = primary,
                primary = true,
                label = primary.label,
                modifier = Modifier.fillMaxWidth(),
                sharedKey = primarySharedKey,
            )
            if (plan.secondaries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                ) {
                    plan.secondaries.forEach { action ->
                        val secondary = item(action)
                        DockButton(
                            item = secondary,
                            primary = false,
                            label = secondary.shortLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One dock button on one line. The text is capped at one line and ends with an ellipsis if a label is ever too
 * long for its width, so the height never grows with the font scale or the app name.
 */
@Composable
private fun DockButton(
    item: DockItem,
    primary: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = item.enabled
    // The app's fill and its text on it, the app's ink for the secondary labels; the Oru colours outside a Focus
    val appTint = LocalOruAppTint.current
    val fill = appTint?.fill ?: scheme.primary
    val container = when {
        !primary -> Color.Transparent
        enabled -> fill
        else -> scheme.onSurface.copy(alpha = DisabledContainerAlpha)
    }
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = DisabledContentAlpha)
        primary -> appTint?.onFill ?: scheme.onPrimary
        else -> appTint?.ink ?: scheme.primary
    }
    // 16 sp semibold on both kinds
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = DockTextSize)
    // The main action stands on its slab like the filled button; the outlined one and a disabled one stay flat
    val press = rememberOruPress(interactionSource)
    val elevation = if (primary && enabled) OruElevation.Raised else OruElevation.Flat
    // The shared bounds sit outside the press scale, so the pill's travelling size is its resting one
    val placed = if (sharedKey != null) modifier.oruSharedPill(sharedKey) else modifier
    Box(
        modifier = placed
            .defaultMinSize(minHeight = if (primary) PrimaryMinHeight else SecondaryMinHeight)
            .oruPressScale(press)
            .oruShadow(
                shape = OruPillShape,
                elevation = elevation,
                tint = fill,
                compact = true,
                press = press,
            )
            .clip(OruPillShape)
            .background(container)
            .oruEdgeLight(shape = OruPillShape, elevation = elevation, press = press)
            .then(if (primary) Modifier else Modifier.border(OutlineWidth, scheme.outline, OruPillShape))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = item.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (primary) PrimaryTextPadding else SecondaryTextPadding,
                vertical = TextPaddingVertical,
            ),
            style = style,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val DisabledContainerAlpha = 0.12f
private const val DisabledContentAlpha = 0.38f
private val RowGap = 8.dp
private val ButtonGap = 8.dp
// Every dock button is 56 dp
private val PrimaryMinHeight = 56.dp
private val SecondaryMinHeight = 56.dp
private val DockTextSize = 16.sp
private val PrimaryTextPadding = 20.dp
private val SecondaryTextPadding = 12.dp
private val TextPaddingVertical = 10.dp
private val OutlineWidth = 1.dp
