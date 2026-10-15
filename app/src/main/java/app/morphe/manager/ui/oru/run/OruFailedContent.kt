/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruFrostedDock
import app.morphe.manager.ui.oru.components.OruHero
import app.morphe.manager.ui.oru.components.OruNotice
import app.morphe.manager.ui.oru.components.OruNoticeAction
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruPrimaryButton
import app.morphe.manager.ui.oru.components.OruSecondaryButton
import app.morphe.manager.ui.oru.components.OruStageRail
import app.morphe.manager.ui.oru.components.OruSwapText
import app.morphe.manager.ui.oru.components.OruTileSource
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.home.oruAppName
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import app.morphe.manager.ui.oru.theme.oruHeroTitleStyle
import app.morphe.manager.ui.screen.patcher.PatcherErrorInfo
import app.morphe.manager.ui.screen.shared.rememberCopyToClipboard
import app.morphe.manager.ui.oru.components.OruHeroState as ComponentHeroState

/**
 * The failed and cancelled outcome of a run. A failure shows the red hero, the reason and what can be
 * done about it. A cancelled run shows the neutral hero and that nothing changed.
 *
 * The caller classifies the failure once, when the run fails, with [oruFailureOf], and keeps that value.
 * Accepting the memory offer clears the ViewModel's dialog state, so classifying again would turn the
 * offer into a plain error.
 *
 * @param failure the outcome to show, see [OruFailure].
 * @param run the run's stages and counts, from [oruRunUi]. Its stage in error shows in the Expert rail.
 * @param expert whether the Expert view is on (the compact hero, the stage rail and Change patches).
 * @param errorInfo the app's name and the failed patch, see [PatcherErrorInfo]. Null falls back to [tile].
 * @param logText the run's log as plain text, for Copy log.
 * @param onApplyMemoryOffer stores the lower limit for the next run, see [OruMemoryOffer].
 * @param onShareLog exports the log through the existing debug log export.
 * @param onChangePatches reopens the patch selection, shown in Expert only when set.
 * @param onBack leaves the outcome for the Focus screen.
 */
@Composable
fun OruFailedContent(
    failure: OruFailure,
    run: OruRunUi,
    expert: Boolean,
    errorInfo: PatcherErrorInfo?,
    tile: OruTileSource,
    shape: OruBlobShape,
    logText: String,
    onApplyMemoryOffer: () -> Unit,
    onShareLog: () -> Unit,
    onChangePatches: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cancelled = failure == OruFailure.Cancelled
    val scheme = MaterialTheme.colorScheme
    val copyToClipboard = rememberCopyToClipboard()
    var openStage by rememberSaveable { mutableStateOf<String?>(null) }

    val appName = errorInfo?.takeIf { it.appName.isNotBlank() }
        ?.let { oruAppName(it.packageName, it.appName) }
        ?: tile.name
    val title = when (failure) {
        is OruFailure.MemoryOffer, is OruFailure.MemoryAtMinimum -> stringResource(R.string.oru_memory_title)
        is OruFailure.Error -> stringResource(R.string.oru_failed_title)
        OruFailure.Cancelled -> stringResource(R.string.oru_run_title_cancelled)
    }
    val caption = if (cancelled) {
        stringResource(R.string.oru_focus_cancelled)
    } else {
        stringResource(R.string.oru_failed_caption, appName)
    }
    val stepRes = run.currentStageLabelRes
    val stepLine = if (!cancelled && stepRes != null) {
        stringResource(R.string.oru_failed_step, stringResource(stepRes))
    } else {
        null
    }
    val patchesLine = if (run.patchesTotal > 0) {
        stringResource(R.string.oru_failed_patches_done, run.patchesDone, run.patchesTotal)
    } else {
        null
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GutterDp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Spacer(Modifier.height(TopSpacing))
            OruHero(
                state = if (cancelled) ComponentHeroState.Ready else ComponentHeroState.Failed,
                tile = tile,
                shape = shape,
                progress = null,
                compact = expert,
                patchTotal = run.patchesTotal,
                failedPatchIndex = run.failedPatchIndex,
                fill = run.progress,
                patchShare = run.patchShare,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TitleSpacing),
            ) {
                OruSwapText(
                    text = title,
                    style = oruHeroTitleStyle(),
                    color = scheme.onSurface,
                )
                OruSwapText(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            FailureNotice(failure = failure, onApplyMemoryOffer = onApplyMemoryOffer)

            stepLine?.let {
                OruSwapText(text = it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            patchesLine?.let {
                OruSwapText(text = it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }

            if (expert && failure is OruFailure.Error) {
                val labels = stageLabels()
                OruStageRail(
                    stages = oruRailStages(run.stages) { id -> labels.getValue(id) },
                    expandedId = openStage,
                    onToggle = { id -> openStage = if (openStage == id) null else id },
                )
            }

            if (!cancelled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ActionSpacing),
                ) {
                    OruSecondaryButton(
                        text = stringResource(R.string.oru_action_copy_log),
                        onClick = { copyToClipboard(logText) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OruOutlinedButton(
                        text = stringResource(R.string.oru_action_share_log),
                        onClick = onShareLog,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (expert && onChangePatches != null) {
                        OruOutlinedButton(
                            text = stringResource(R.string.patcher_change_patches),
                            onClick = onChangePatches,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(BottomSpacing))
        }

        OruFrostedDock(frost = OruSurfaceFill.Solid) {
            OruPrimaryButton(
                text = stringResource(R.string.oru_failed_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The reason, as a notice. A memory offer carries its one action, which only stores the lower limit.
 * An error names the patch it stopped on and the first lines of its message.
 */
@Composable
private fun FailureNotice(
    failure: OruFailure,
    onApplyMemoryOffer: () -> Unit,
) {
    when (failure) {
        is OruFailure.MemoryOffer -> OruNotice(
            text = stringResource(R.string.oru_memory_body, failure.currentLimitMb, failure.lowerToMb),
            tone = OruTone.Warn,
            actions = listOf(
                OruNoticeAction(
                    label = stringResource(R.string.oru_memory_action_lower, failure.lowerToMb),
                    onClick = onApplyMemoryOffer,
                ),
            ),
        )
        is OruFailure.MemoryAtMinimum -> OruNotice(
            text = stringResource(R.string.oru_memory_at_minimum, failure.limitMb),
            tone = OruTone.Error,
            actions = emptyList(),
        )
        is OruFailure.Error -> {
            val patchLine = failure.failedPatch?.let { stringResource(R.string.failed_to_execute_patch, it) }
            val text = listOfNotNull(patchLine, failure.summary).joinToString(separator = "\n")
            OruNotice(
                text = if (text.isEmpty()) stringResource(R.string.oru_failed_no_detail) else text,
                tone = OruTone.Error,
                actions = emptyList(),
            )
        }
        OruFailure.Cancelled -> Unit
    }
}

private val GutterDp = 16.dp
private val TopSpacing = 8.dp
private val SectionSpacing = 16.dp
private val TitleSpacing = 4.dp
private val ActionSpacing = 8.dp
private val BottomSpacing = 16.dp
