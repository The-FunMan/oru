/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.OruMonoStyle
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.LocalOruStatusColors

/**
 * The stage rail of a patch run, one row per stage. The running stage is open and its dot pulses (the pulse
 * stops when motion is reduced). A finished stage folds to its row and opens on tap when it has lines. A
 * failed stage is in the error container. A warning count shows as a badge.
 */
@Composable
fun OruStageRail(
    stages: List<OruStageUi>,
    expandedId: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stages.forEach { stage ->
            OruStageItem(
                stage = stage,
                expanded = expandedId == stage.id,
                onToggle = { onToggle(stage.id) },
            )
        }
    }
}

@Composable
private fun OruStageItem(
    stage: OruStageUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val reduced = oruReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val status = LocalOruStatusColors.current
    val hasLines = stage.lines.isNotEmpty()
    val canToggle = oruStageCanToggle(stage.state, hasLines)
    val running = stage.state == OruStageState.Now || stage.state == OruStageState.Error
    val open = hasLines && (running || expanded)
    val lines = oruVisibleStageLines(stage.lines, stage.state)
    val failed = stage.state == OruStageState.Error
    // The running stage takes the app's wash in its Focus, with the text that wash was checked for
    val runningTint = LocalOruAppTint.current.takeIf { stage.state == OruStageState.Now }
    val container = when (stage.state) {
        OruStageState.Now -> runningTint?.container ?: scheme.surfaceContainer
        OruStageState.Error -> scheme.errorContainer
        else -> Color.Transparent
    }
    val content = when {
        failed -> scheme.onErrorContainer
        runningTint != null -> runningTint.onContainer
        else -> scheme.onSurface
    }
    val secondary = runningTint?.onContainer ?: scheme.onSurfaceVariant
    val lineColor = when {
        failed -> content
        else -> secondary
    }
    val tap = if (canToggle) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier
    val todo = stage.state == OruStageState.Todo

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (todo) Modifier.alpha(TodoAlpha) else Modifier)
            .oruSlab(
                shape = StageShape,
                color = container,
                // The running and the failed stage stand on a slab; a stage that waits or is done is a plain row
                elevation = if (running) OruElevation.Raised else OruElevation.Flat,
                tint = if (failed) scheme.error else Color.Unspecified,
                compact = true,
            )
            .padding(horizontal = StagePaddingHorizontal),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = StageMinHeight)
                .then(tap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageDot(state = stage.state, reduced = reduced)
            Text(
                text = stage.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = StageNameSize, fontWeight = FontWeight.SemiBold),
                color = content,
            )
            if (stage.warnings > 0) {
                StageWarningBadge(count = stage.warnings)
            }
            stage.duration?.let { duration ->
                Text(
                    text = duration,
                    style = OruMonoStyle,
                    color = secondary,
                )
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduced) {
                EnterTransition.None
            } else {
                fadeIn(tween(OruMotion.ExpandFadeMillis)) + expandVertically(OruMotion.expand(reduced))
            },
            exit = if (reduced) {
                ExitTransition.None
            } else {
                fadeOut(tween(OruMotion.ExpandFadeMillis)) + shrinkVertically(OruMotion.expand(reduced))
            },
        ) {
            Column(
                modifier = Modifier.padding(start = LinesIndent, bottom = LinesBottomPadding),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                lines.forEach { line ->
                    Text(
                        text = line.text,
                        style = OruMonoStyle.copy(fontSize = LineTextSize, lineHeight = LineHeight),
                        color = if (line.warning && !failed) status.warn else lineColor,
                    )
                }
            }
        }
    }
}

/** The stage dot. Only the running stage pulses, and only when motion is not reduced. */
@Composable
private fun StageDot(
    state: OruStageState,
    reduced: Boolean,
) {
    if (oruStagePulses(state) && !reduced) {
        PulsingStageDot(state = state)
    } else {
        StageDotShape(state = state, scale = 1f)
    }
}

@Composable
private fun PulsingStageDot(state: OruStageState) {
    val transition = rememberInfiniteTransition(label = "oruStageDot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = OruMotion.StageDotPeakScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = OruMotion.StageDotPulseMillis / 2),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "oruStageDotScale",
    )
    StageDotShape(state = state, scale = scale)
}

@Composable
private fun StageDotShape(
    state: OruStageState,
    scale: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalOruStatusColors.current
    Box(
        modifier = Modifier
            .size(DotSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            OruStageState.Todo -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(width = TodoRing, color = scheme.outline, shape = CircleShape),
            )
            OruStageState.Now -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(width = NowRing, color = oruInk(), shape = CircleShape),
            )
            OruStageState.Done -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(status.ok),
                )
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = status.onOk,
                    modifier = Modifier.size(CheckSize),
                )
            }
            OruStageState.Error -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(scheme.error),
            )
        }
    }
}

@Composable
private fun StageWarningBadge(count: Int) {
    val status = LocalOruStatusColors.current
    Text(
        text = pluralStringResource(R.plurals.oru_stage_warnings, count, count),
        modifier = Modifier
            .clip(BadgeShape)
            .background(status.warn)
            .padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = BadgeTextSize, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.surface,
    )
}

private val StageShape = RoundedCornerShape(18.dp)
private val BadgeShape = RoundedCornerShape(8.dp)
private val StagePaddingHorizontal = 14.dp
private val StageMinHeight = 48.dp
private val StageNameSize = 14.5.sp
private val DotSize = 18.dp
private val CheckSize = 12.dp
private val TodoRing = 2.dp
private val NowRing = 5.dp
private val LinesIndent = 30.dp
private val LinesBottomPadding = 10.dp
private val LineTextSize = 11.5.sp
private val LineHeight = 17.sp
private val BadgePaddingHorizontal = 8.dp
private val BadgePaddingVertical = 2.dp
private val BadgeTextSize = 11.sp
private const val TodoAlpha = 0.5f
