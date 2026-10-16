/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruGlassRole
import app.morphe.manager.ui.oru.components.OruGlassSurface
import app.morphe.manager.ui.oru.components.OruTextButton
import app.morphe.manager.ui.oru.components.oruReducedMotion
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSpacing

/**
 * The first-run tour: a glass card above the app cards on a fresh install. It names the three steps of a first patch and
 * highlights one at a time. A numbered rail picks the step, and the active step shows its sentence. Skip is always on the
 * card, and the card is never a modal: the cards below it stay usable.
 *
 * [onPick] shows the step its number was tapped for, [onNext] moves one step forward, and [onDismiss] ends the tour, from
 * Skip or from Got it on the last step.
 */
@Composable
fun OruTourCard(
    state: OruTourState,
    onPick: (OruTourStep) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val steps = OruTourStep.entries
    val total = steps.size
    val skipDescription = stringResource(R.string.oru_tour_skip_description)
    OruGlassSurface(
        shape = MaterialTheme.shapes.extraLarge,
        role = OruGlassRole.Card,
        modifier = modifier.fillMaxWidth().semantics { isTraversalGroup = true },
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.oru_tour_title),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                OruTextButton(
                    text = stringResource(R.string.oru_tour_skip),
                    onClick = onDismiss,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = skipDescription },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StepGap)) {
                steps.forEachIndexed { index, step ->
                    val number = index + 1
                    val (container, content) = when {
                        step == state.step -> scheme.primary to scheme.onPrimary
                        step < state.step -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        else -> Color.Transparent to scheme.onSurfaceVariant
                    }
                    TourStepNumber(
                        number = number,
                        name = stringResource(stepNameRes(step)),
                        stateText = if (step == state.step) {
                            stringResource(R.string.oru_tour_step_state_current, number, total)
                        } else {
                            stringResource(R.string.oru_tour_step_state, number, total)
                        },
                        container = container,
                        content = content,
                        outlined = step > state.step,
                        onClick = { onPick(step) },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TourLine(
                    text = stringResource(stepNameRes(state.step)),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                TourLine(
                    text = stringResource(stepSentenceRes(state.step)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (state.step == steps.last()) {
                    OruTextButton(
                        text = stringResource(R.string.oru_tour_got_it),
                        onClick = onDismiss,
                        color = scheme.primary,
                    )
                } else {
                    OruTextButton(
                        text = stringResource(R.string.oru_tour_next),
                        onClick = onNext,
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * The tour's state over a recreation of the screen. Its two parts are plain values, so they are saved as a list of them.
 */
internal val OruTourStateSaver: Saver<OruTourState, Any> = listSaver(
    save = { listOf(it.step.ordinal, it.dismissed) },
    restore = { saved ->
        OruTourState(
            step = OruTourStep.entries[saved[0] as Int],
            dismissed = saved[1] as Boolean,
        )
    },
)

/** One step of the rail. A number in a disc, with a 48 dp touch target, and the step's name and state for TalkBack. */
@Composable
private fun TourStepNumber(
    number: Int,
    name: String,
    stateText: String,
    container: Color,
    content: Color,
    outlined: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = OruSpacing.TouchTarget, minHeight = OruSpacing.TouchTarget)
            .semantics {
                contentDescription = name
                stateDescription = stateText
            }
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = StepSize, minHeight = StepSize)
                .clip(CircleShape)
                .background(container)
                .then(
                    if (outlined) Modifier.border(OutlineWidth, MaterialTheme.colorScheme.outline, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = content,
            )
        }
    }
}

/**
 * A line that changes with the step. The old line fades out and the new one fades in, on the swap token. Reduced
 * motion changes it at once.
 */
@Composable
private fun TourLine(
    text: String,
    style: TextStyle,
    color: Color,
) {
    val reduced = oruReducedMotion()
    AnimatedContent(
        targetState = text,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(OruMotion.swapText(reduced)) togetherWith fadeOut(OruMotion.swapText(reduced))
            }
        },
        label = "oruTourLine",
    ) { current ->
        Text(text = current, style = style, color = color)
    }
}

private fun stepNameRes(step: OruTourStep): Int = when (step) {
    OruTourStep.Find -> R.string.oru_tour_step_name_find
    OruTourStep.Patch -> R.string.oru_tour_step_name_patch
    OruTourStep.Install -> R.string.oru_tour_step_name_install
}

private fun stepSentenceRes(step: OruTourStep): Int = when (step) {
    OruTourStep.Find -> R.string.oru_tour_step_find
    OruTourStep.Patch -> R.string.oru_tour_step_patch
    OruTourStep.Install -> R.string.oru_tour_step_install
}

private val CardPadding = 16.dp
private val StepGap = 8.dp
private val StepSize = 36.dp
private val OutlineWidth = 1.dp
