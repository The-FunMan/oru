/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruFrostedDock
import app.morphe.manager.ui.oru.components.OruHero
import app.morphe.manager.ui.oru.components.OruHeroState
import app.morphe.manager.ui.oru.components.OruSwapText
import app.morphe.manager.ui.oru.components.OruNotice
import app.morphe.manager.ui.oru.components.OruNoticeAction
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruPrimaryButton
import app.morphe.manager.ui.oru.components.OruRow
import app.morphe.manager.ui.oru.components.OruTileSource
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.install.postinstall.OruDoneFacts
import app.morphe.manager.ui.oru.install.postinstall.OruDoneFactsRow
import app.morphe.manager.ui.oru.run.OruRunHexagon
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.oru.theme.oruHeroTitleStyle
import app.morphe.manager.ui.oru.theme.rememberOruSurfaceFill

/**
 * The user's answers on the install screen. Each callback maps to one [app.morphe.manager.ui.oru.install.OruInstallCoordinator]
 * method; the content holds no state of its own.
 */
data class OruInstallActions(
    val onInstall: () -> Unit = {},
    val onPickInstaller: (OruInstallerOption) -> Unit = {},
    val onChooseRootMode: (useMount: Boolean) -> Unit = {},
    val onReviewConflict: () -> Unit = {},
    val onAllowInstalls: () -> Unit = {},
    val onOpenInstaller: () -> Unit = {},
    val onGrantPermission: () -> Unit = {},
    val onInstallWithAndroid: () -> Unit = {},
    val onOpen: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShareLog: () -> Unit = {},
    val onStopWaiting: () -> Unit = {},
)

/**
 * The Done and install phase of Oru: the hero, one line of copy, the done facts and at most one primary dock
 * action, all driven by [ui]. Stateless: the coordinator decides [ui] and runs the [actions].
 *
 * The hero is the run's hexagon, continued from where the run ended at [runHexagon]: it holds while the install is
 * planned, fills while it runs, and settles with its check once installed. Failed keeps its own hero. The done screens
 * read "X is ready" with the run's [facts] and the install's line. [installedLabel] names the installed copy a conflict
 * would remove; it defaults to [appName].
 */
@Composable
fun OruInstallContent(
    ui: OruInstallUi,
    appName: String,
    tile: OruTileSource,
    shape: OruBlobShape,
    actions: OruInstallActions,
    runHexagon: OruRunHexagon,
    modifier: Modifier = Modifier,
    facts: OruDoneFacts? = null,
    installedLabel: String? = null,
) {
    val hero = InstallHero(
        state = OruInstallScreenMapper.heroState(ui),
        fill = OruInstallScreenMapper.fill(ui, runHexagon.fill),
        run = runHexagon,
    )
    val readyTitle = stringResource(R.string.oru_done_ready_title, appName)
    val signedHere = stringResource(R.string.oru_done_signed_here)
    when (ui) {
        OruInstallUi.Checking -> InstallScaffold(
            hero = hero,
            tile = tile,
            shape = shape,
            title = readyTitle,
            line = stringResource(R.string.oru_install_plan_checking),
            modifier = modifier,
            extra = { DoneFacts(facts) },
        )

        is OruInstallUi.Plan -> PlanBody(
            ui = ui,
            appName = appName,
            hero = hero,
            tile = tile,
            shape = shape,
            actions = actions,
            modifier = modifier,
            facts = facts,
            installedLabel = installedLabel,
        )

        is OruInstallUi.Installing -> InstallScaffold(
            hero = hero,
            tile = tile,
            shape = shape,
            title = stringResource(R.string.oru_action_installing),
            line = stringResource(
                if (ui.automatic) R.string.oru_install_automatic else R.string.oru_install_waiting
            ),
            modifier = modifier,
            // An automatic install has no tap to start it, but the wait can always be stopped
            dock = {
                if (!ui.automatic) {
                    OruPrimaryButton(
                        text = stringResource(R.string.oru_action_installing),
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false,
                    )
                }
                OruOutlinedButton(
                    text = stringResource(R.string.oru_install_stop_go),
                    onClick = actions.onStopWaiting,
                    modifier = Modifier.weight(1f),
                )
            },
        )

        is OruInstallUi.Installed -> InstallScaffold(
            hero = hero,
            tile = tile,
            shape = shape,
            title = stringResource(R.string.oru_action_installed),
            line = stringResource(R.string.oru_install_done, appName),
            modifier = modifier,
            extra = { DoneFacts(facts) },
            dock = {
                DoneDock(
                    primary = stringResource(R.string.oru_action_open),
                    onPrimary = actions.onOpen,
                    secondary = stringResource(R.string.oru_action_done),
                    onSecondary = actions.onDone,
                )
            },
        )

        is OruInstallUi.Failed -> InstallScaffold(
            hero = hero,
            tile = tile,
            shape = shape,
            title = stringResource(R.string.oru_install_error_title),
            line = stringResource(R.string.oru_install_error_body),
            hint = ui.message,
            modifier = modifier,
            dock = {
                OruPrimaryButton(
                    text = stringResource(R.string.oru_action_try_again),
                    onClick = actions.onRetry,
                    modifier = Modifier.weight(1f),
                )
                // The system installer needs no Shizuku, so it is the way out when Shizuku keeps failing
                OruOutlinedButton(
                    text = stringResource(R.string.oru_action_install_with_android),
                    onClick = actions.onInstallWithAndroid,
                    modifier = Modifier.weight(1f),
                )
                OruOutlinedButton(
                    text = stringResource(R.string.oru_action_share_log),
                    onClick = actions.onShareLog,
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }
}

/** The install screen's hero: its state, the fill of the run's hexagon at this step, and the run it carries on from. */
private data class InstallHero(
    val state: OruHeroState,
    val fill: Float,
    val run: OruRunHexagon,
)

@Composable
private fun PlanBody(
    ui: OruInstallUi.Plan,
    appName: String,
    hero: InstallHero,
    tile: OruTileSource,
    shape: OruBlobShape,
    actions: OruInstallActions,
    modifier: Modifier,
    facts: OruDoneFacts?,
    installedLabel: String?,
) {
    when (val step = ui.step) {
        InstallStep.RootPatchMode -> {
            val copy = OruDecisionCopy.forStep(step)
            InstallScaffold(
                hero = hero,
                tile = tile,
                shape = shape,
                title = copy.title?.let { stringResource(it) } ?: appName,
                line = copy.body?.let { stringResource(it) },
                modifier = modifier,
                extra = {
                    OruRow(
                        title = stringResource(R.string.oru_root_action_mount),
                        value = null,
                        onClick = { actions.onChooseRootMode(true) },
                    )
                    OruRow(
                        title = stringResource(R.string.oru_root_action_standard),
                        value = null,
                        onClick = { actions.onChooseRootMode(false) },
                    )
                },
            )
        }

        InstallStep.AskInstaller -> InstallScaffold(
            hero = hero,
            tile = tile,
            shape = shape,
            title = stringResource(R.string.oru_settings_installer),
            line = stringResource(R.string.oru_install_pick_body),
            modifier = modifier,
            extra = {
                ui.options.forEach { option ->
                    val tap: (() -> Unit)? = if (option.enabled) {
                        { actions.onPickInstaller(option) }
                    } else {
                        null
                    }
                    OruRow(title = option.label, value = option.reason, onClick = tap)
                }
            },
        )

        is InstallStep.ConflictNeedsUninstall -> {
            // The decision itself is OruSignatureDecision; the dock's Review only opens it
            val copy = OruDecisionCopy.forStep(step)
            InstallScaffold(
                hero = hero,
                tile = tile,
                shape = shape,
                title = stringResource(R.string.oru_done_ready_title, appName),
                line = stringResource(R.string.oru_done_signed_here),
                modifier = modifier,
                extra = {
                    DoneFacts(facts)
                    OruNotice(
                        text = copy.body?.let { stringResource(it, installedLabel ?: appName) }.orEmpty(),
                        tone = OruTone.Warn,
                        actions = emptyList<OruNoticeAction>(),
                    )
                },
                dock = {
                    DoneDock(
                        primary = stringResource(R.string.oru_install_review),
                        onPrimary = actions.onReviewConflict,
                    )
                },
            )
        }

        is InstallStep.NeedsPermission -> InstallScaffold(
            hero = hero,
            tile = tile,
            shape = shape,
            title = appName,
            line = stringResource(R.string.oru_install_unknown_sources),
            modifier = modifier,
            dock = {
                OruPrimaryButton(
                    text = stringResource(R.string.oru_action_allow_installs),
                    onClick = actions.onAllowInstalls,
                    modifier = Modifier.weight(1f),
                )
            },
        )

        is InstallStep.InstallerUnavailable -> {
            val copy = OruDecisionCopy.forStep(step)
            InstallScaffold(
                hero = hero,
                tile = tile,
                shape = shape,
                title = copy.title?.let { stringResource(it) } ?: appName,
                line = null,
                modifier = modifier,
                extra = {
                    OruNotice(
                        text = stringResource(copy.hint ?: copy.body ?: R.string.oru_installer_unavailable_body),
                        tone = OruTone.Warn,
                        actions = unavailableActions(step, copy, actions),
                    )
                },
            )
        }

        // The coordinator starts this one itself; the screen shows the automatic state
        is InstallStep.AutoInstall -> InstallScaffold(
            hero = hero.copy(state = OruHeroState.Working),
            tile = tile,
            shape = shape,
            title = stringResource(R.string.oru_action_installing),
            line = stringResource(R.string.oru_install_automatic),
            modifier = modifier,
        )

        is InstallStep.ShowInstall -> {
            val copy = OruDecisionCopy.forStep(step)
            val installLine = copy.body?.let { id ->
                if (id == R.string.oru_install_with_silent) {
                    stringResource(id, ui.installerLabel.orEmpty())
                } else {
                    stringResource(id)
                }
            }
            val fallbackLine = copy.hint?.let { stringResource(it, ui.fallbackLabel.orEmpty()) }
            InstallScaffold(
                hero = hero,
                tile = tile,
                shape = shape,
                title = stringResource(R.string.oru_done_ready_title, appName),
                line = stringResource(R.string.oru_done_signed_here),
                modifier = modifier,
                extra = {
                    DoneFacts(facts)
                    if (installLine != null) InstallerLine(installLine)
                    if (fallbackLine != null) InstallerHint(fallbackLine)
                },
                dock = {
                    DoneDock(
                        primary = stringResource(R.string.oru_action_install),
                        onPrimary = actions.onInstall,
                    )
                },
            )
        }
    }
}

/** The actions next to an unavailable installer: the planner's own action, then install with Android. */
@Composable
private fun unavailableActions(
    step: InstallStep.InstallerUnavailable,
    copy: DecisionCopy,
    actions: OruInstallActions,
): List<OruNoticeAction> {
    val list = mutableListOf<OruNoticeAction>()
    val primary = copy.primaryAction
    if (primary != null) {
        val onClick = when (step.action) {
            UnavailableAction.GRANT_PERMISSION -> actions.onGrantPermission
            else -> actions.onOpenInstaller
        }
        list += OruNoticeAction(label = stringResource(primary), onClick = onClick)
    }
    copy.secondaryAction?.let {
        list += OruNoticeAction(label = stringResource(it), onClick = actions.onInstallWithAndroid)
    }
    return list
}

/** The done facts row, when the run has facts to show. */
@Composable
private fun DoneFacts(facts: OruDoneFacts?) {
    if (facts != null) {
        OruDoneFactsRow(facts = facts)
    }
}

/** The installer line of the done screen, in the body style. */
@Composable
private fun InstallerLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** A note under the installer line, such as the fallback installer's name. */
@Composable
private fun InstallerHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * The done dock: one full width primary action, and an optional outlined secondary beneath it, the same shape
 * as the Focus dock. Each label holds one line.
 */
@Composable
private fun RowScope.DoneDock(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(OruSpacing.Small),
    ) {
        OruPrimaryButton(
            text = primary,
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (secondary != null) {
            OruOutlinedButton(
                text = secondary,
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Hero, title, optional line and hint, then the extra rows and the dock. Keeps every install branch on
 * the same layout so the hero does not jump between states.
 */
@Composable
private fun InstallScaffold(
    hero: InstallHero,
    tile: OruTileSource,
    shape: OruBlobShape,
    title: String,
    line: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    dock: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OruSpacing.ScreenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OruSpacing.Medium),
    ) {
        Spacer(Modifier.height(OruSpacing.XLarge))
        OruHero(
            state = hero.state,
            tile = tile,
            shape = shape,
            progress = null,
            compact = false,
            description = title,
            applyingPatches = true,
            patchTotal = hero.run.patches,
            fill = hero.fill,
            patchShare = hero.run.patchShare,
            carryOver = true,
        )
        OruSwapText(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = oruHeroTitleStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (line != null) {
            OruSwapText(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (extra != null) {
            extra()
        }
        if (dock != null) {
            Spacer(Modifier.height(OruSpacing.Small))
            OruFrostedDock(
                frost = rememberOruSurfaceFill(),
                modifier = Modifier.fillMaxWidth(),
                content = dock,
            )
        }
    }
}
