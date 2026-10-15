/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruDecisionDialog
import app.morphe.manager.ui.oru.components.OruDialogAction
import app.morphe.manager.ui.oru.components.OruTone

/**
 * The decision for an install that needs the installed app removed first (S10b). The data loss is
 * stated in the body, and the destructive action is explicit: nothing here runs on dismiss.
 *
 * The second action is "Import key" when the phone has no way to keep the data, and "Install anyway"
 * on rooted devices, where a module may skip the platform signature check. A downgrade has no second
 * action, because importing a key does not help an older version.
 *
 * The Shizuku hint replaces the import hint when [InstallStep.ConflictNeedsUninstall.canUninstallSilently]
 * is true, since the removal then needs no system prompt.
 *
 * @param appName the app's label, used for the body when [installedLabel] is not known
 * @param installedLabel the installed copy's label, which the body names; the installed copy can be another
 * package than the one patched
 * @param onUninstallAndInstall removes the installed app and installs the patched build; the caller runs
 * `InstallViewModel.requestUninstall(pkg, installAfterUninstall = true)` or the silent path
 * @param onImportKey opens the signing key import (Settings, Signing key)
 * @param onInstallAnyway retries without removing the app; only offered when the step allows it
 * @param onDismiss the cancel path; no install starts
 */
@Composable
fun OruSignatureDecision(
    step: InstallStep.ConflictNeedsUninstall,
    appName: String,
    installedLabel: String?,
    onUninstallAndInstall: () -> Unit,
    onImportKey: () -> Unit,
    onInstallAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = OruDecisionCopy.forStep(step)
    val title = copy.title?.let { stringResource(it) }.orEmpty()
    val body = copy.body?.let { stringResource(it, installedLabel ?: appName) }.orEmpty()
    // Two hints can apply (the Shizuku or import hint, and the root "Install anyway" note)
    val hint = listOfNotNull(
        copy.hint?.let { stringResource(it) },
        copy.secondaryHint?.let { stringResource(it) },
    ).joinToString(separator = " ").ifEmpty { null }

    val secondary: OruDialogAction? = when {
        step.reason == ConflictReason.DOWNGRADE -> null
        step.canIgnoreSignatureMismatch -> OruDialogAction(
            label = stringResource(R.string.oru_signature_ignore),
            onClick = onInstallAnyway,
        )
        else -> OruDialogAction(
            label = stringResource(R.string.oru_action_import_key),
            onClick = onImportKey,
        )
    }

    OruDecisionDialog(
        title = title,
        body = body,
        primary = OruDialogAction(
            label = stringResource(copy.primaryAction ?: R.string.oru_signature_go),
            onClick = onUninstallAndInstall,
        ),
        secondary = secondary,
        tone = OruTone.Error,
        hint = hint,
        onDismiss = onDismiss,
    )
}
