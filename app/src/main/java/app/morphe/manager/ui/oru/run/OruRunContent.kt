/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruContainerRole
import app.morphe.manager.ui.oru.components.OruDecisionDialog
import app.morphe.manager.ui.oru.components.OruDialogAction
import app.morphe.manager.ui.oru.components.OruFitMinSize
import app.morphe.manager.ui.oru.components.OruFrostedDock
import app.morphe.manager.ui.oru.components.OruHero
import app.morphe.manager.ui.oru.components.OruHeroCounter
import app.morphe.manager.ui.oru.components.OruMorphText
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruProgressHold
import app.morphe.manager.ui.oru.components.OruRollingCount
import app.morphe.manager.ui.oru.components.LocalOruAppTint
import app.morphe.manager.ui.oru.components.OruStageRail
import app.morphe.manager.ui.oru.components.OruSwapText
import app.morphe.manager.ui.oru.components.OruStageUi as ComponentStageUi
import app.morphe.manager.ui.oru.components.OruTileSource
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.components.OruUsageReadout
import app.morphe.manager.ui.oru.components.OruVitalUnits
import app.morphe.manager.ui.oru.components.oruContainerColors
import app.morphe.manager.ui.oru.components.oruDigitCount
import app.morphe.manager.ui.oru.components.oruSlab
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruMonoStyle
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import app.morphe.manager.ui.oru.theme.oruHeroTitleStyle
import app.morphe.manager.ui.model.StepId
import app.morphe.manager.ui.screen.shared.KeepScreenOn

/**
 * The working and done views of a patch run, drawn from the run's data. Stateless: the caller owns the
 * run and receives [onCancel] once the user confirms the cancel.
 *
 * Expert mode shows the compact hero, the usage readout and one stage row per real stage with its log
 * lines. Simple mode shows the three friendly steps, each grouped from the real stages. The screen stays
 * on while the run works. A finished run shows its facts. Failed and cancelled outcomes are not drawn
 * here; see OruFailedContent.
 *
 * @param run the run's hero, stages and counts, from [oruRunUi].
 * @param expert whether the Expert view is on (the compact hero, the readout and the stage rail).
 * @param tile the app's tile and [shape], the glyph of the hero.
 * @param usage the samples for the readout, see [OruRunUsage.of].
 * @param facts the output size and time of a finished run, null while it works.
 * @param runKey identifies the run. The percentage it shows never goes back, and a new key starts it from zero.
 */
@Composable
fun OruRunContent(
    run: OruRunUi,
    expert: Boolean,
    tile: OruTileSource,
    shape: OruBlobShape,
    usage: OruRunUsage,
    facts: OruRunFacts?,
    runKey: Any,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = run.hero == OruHeroState.WORKING
    val done = run.hero == OruHeroState.DONE
    val scheme = MaterialTheme.colorScheme
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var usageOpen by rememberSaveable { mutableStateOf(false) }
    var openStage by rememberSaveable { mutableStateOf<String?>(null) }

    KeepScreenOn(enabled = working)

    val labels = stageLabels()
    val title = when (run.hero) {
        OruHeroState.DONE -> stringResource(R.string.oru_run_title_done)
        OruHeroState.CANCELLED -> stringResource(R.string.oru_run_title_cancelled)
        else -> stringResource(R.string.oru_run_title_patching)
    }
    val applyingPatches = run.currentStageLabelRes == R.string.oru_stage_execute_patches
    val counter = if (working && applyingPatches && run.patchesTotal > 0) {
        OruHeroCounter(done = run.patchesDone, total = run.patchesTotal)
    } else {
        null
    }
    // The percentage of this run never steps back: a retry that starts over holds the figure it had reached
    val hold = remember(runKey) { OruProgressHold() }
    val shownProgress = hold.show(run.progress)

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
                state = run.hero.toComponent(),
                tile = tile,
                shape = shape,
                progress = { shownProgress },
                compact = expert,
                counter = counter,
                applyingPatches = applyingPatches,
                patchTotal = run.patchesTotal,
                failedPatchIndex = run.failedPatchIndex,
                fill = shownProgress,
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
                if (done) {
                    OruSwapText(
                        text = pluralStringResource(
                            R.plurals.oru_run_patches_applied,
                            run.patchesDone,
                            run.patchesDone,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    OruRunStageLine(
                        parts = oruRunLineParts(run),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (expert) {
                OruUsageReadout(
                    heapMb = usage.heapMb,
                    cpuPercent = usage.cpuPercent,
                    ioMbPerSec = usage.ioKbPerSec,
                    graphs = usage.toGraphs(),
                    expanded = usageOpen,
                    onToggle = { usageOpen = !usageOpen },
                    units = OruVitalUnits(io = KbPerSecUnit),
                )
                OruStageRail(
                    stages = oruRailStages(run.stages) { id -> labels.getValue(id) },
                    expandedId = openStage,
                    onToggle = { id -> openStage = if (openStage == id) null else id },
                )
            } else {
                OruStageRail(
                    stages = oruSimpleSteps(run.stages).map { (step, state) ->
                        ComponentStageUi(
                            id = step.name,
                            name = stringResource(simpleStepLabelRes(step)),
                            state = state,
                        )
                    },
                    expandedId = null,
                    onToggle = {},
                )
            }

            if (done) {
                OruRunFactsRow(facts = facts, peakHeapMb = usage.peakHeapMb)
            }
            Spacer(Modifier.height(BottomSpacing))
        }

        if (working) {
            OruFrostedDock(frost = OruSurfaceFill.Solid) {
                OruOutlinedButton(
                    text = stringResource(R.string.oru_action_cancel),
                    onClick = { confirmCancel = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmCancel) {
        OruDecisionDialog(
            title = stringResource(R.string.oru_run_cancel_title),
            body = stringResource(R.string.oru_run_cancel_body),
            primary = OruDialogAction(
                label = stringResource(R.string.oru_run_cancel_go),
                onClick = {
                    confirmCancel = false
                    onCancel()
                },
            ),
            secondary = OruDialogAction(
                label = stringResource(R.string.oru_run_keep_patching),
                onClick = { confirmCancel = false },
            ),
            tone = OruTone.Error,
            onDismiss = { confirmCancel = false },
        )
    }
}

/** The label of every stage, resolved once per composition. */
@Composable
internal fun stageLabels(): Map<StepId, String> {
    val labels = HashMap<StepId, String>()
    for (id in StepId.entries) {
        labels[id] = stringResource(oruStageLabelRes(id))
    }
    return labels
}

/**
 * The stage line under the title, see [oruRunLineParts]. The stage's name is a [OruMorphText], so it forms out of
 * the cloud when the stage changes. While patches apply, the count follows it: the number rolls on each patch and the
 * total stays put. A screen reader reads the whole line as one string, the same text the count always had.
 */
@Composable
private fun OruRunStageLine(
    parts: OruRunLineParts,
    style: TextStyle,
    color: Color,
) {
    val label = stringResource(parts.labelRes)
    val done = parts.patchesDone
    val total = parts.patchesTotal
    val fullLine = if (done != null && total != null) {
        stringResource(R.string.oru_run_patch_count, done, total)
    } else {
        label
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = fullLine },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OruMorphText(text = label, style = style, color = color)
        if (done != null && total != null) {
            Spacer(Modifier.width(CountGap))
            // The slot is as wide as the total, so the text after the number never moves as the count rolls
            OruRollingCount(value = done, slotDigits = oruDigitCount(total), style = style, color = color)
            Spacer(Modifier.width(CountGap))
            Text(
                text = stringResource(R.string.oru_run_counter_of, total),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The three done facts: output size, time and peak heap. A fact the run did not record is left out. */
@Composable
private fun OruRunFactsRow(
    facts: OruRunFacts?,
    peakHeapMb: Int?,
) {
    val items = buildList {
        facts?.outputBytes?.let { add(OruRunFormat.size(it) to R.string.oru_done_output) }
        facts?.elapsedMs?.let { add(OruRunFormat.elapsed(it) to R.string.oru_done_time) }
        peakHeapMb?.let { add(OruRunFormat.megabytes(it) to R.string.oru_done_peak_heap) }
    }
    if (items.isEmpty()) return

    val colors = oruContainerColors(OruContainerRole.Ok)
    // The stat chips wash with the app's chip tint in its Focus, and keep the status colour elsewhere
    val appTint = LocalOruAppTint.current
    val factContainer = appTint?.chip ?: colors.container
    val factContent = appTint?.onChip ?: colors.content
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FactsSpacing),
    ) {
        items.forEach { (value, labelRes) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .oruSlab(shape = FactShape, color = factContainer, compact = true)
                    .padding(FactPadding),
            ) {
                OruFactValue(
                    value = value,
                    style = OruMonoStyle.copy(fontSize = FactValueSize, fontWeight = FontWeight.SemiBold),
                    color = factContent,
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = factContent,
                )
            }
        }
    }
}

/**
 * A done fact's value. It is a [OruMorphText], so it forms out of the cloud when the done view appears: it starts
 * empty and takes its value on the first frame. It shrinks to fit its chip (see [oruFactFontSp]), so a figure such as
 * "115.6 MB" stays whole at a large font scale. [style] must set its size in sp.
 */
@Composable
private fun OruFactValue(
    value: String,
    style: TextStyle,
    color: Color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(value) { shown = value }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val fitted = remember(measurer, value, style, constraints.maxWidth) {
            val measured = measurer.measure(text = value, style = style).size.width
            val sp = oruFactFontSp(
                baseSp = style.fontSize.value,
                minSp = OruFitMinSize.value,
                measuredPx = measured,
                availablePx = constraints.maxWidth,
            )
            style.copy(fontSize = sp.sp)
        }
        // The height of a line at the fitted size, so the empty first frame does not shrink the chip
        val lineHeight = remember(measurer, fitted) {
            with(density) { measurer.measure(text = "0", style = fitted).size.height.toDp() }
        }
        OruMorphText(text = shown, style = fitted, color = color, modifier = Modifier.height(lineHeight))
    }
}

private fun simpleStepLabelRes(step: OruSimpleStep): Int = when (step) {
    OruSimpleStep.PREPARE -> R.string.oru_run_step_prepare
    OruSimpleStep.APPLY -> R.string.oru_stage_execute_patches
    OruSimpleStep.FINISH -> R.string.oru_run_step_finish
}

private const val KbPerSecUnit = "KB/s"
private val GutterDp = 16.dp
private val TopSpacing = 8.dp
private val SectionSpacing = 16.dp
private val TitleSpacing = 4.dp
private val BottomSpacing = 16.dp
private val FactsSpacing = 8.dp
private val FactPadding = 12.dp
private val FactShape = RoundedCornerShape(18.dp)
private val FactValueSize = 17.sp
private val CountGap = 4.dp
