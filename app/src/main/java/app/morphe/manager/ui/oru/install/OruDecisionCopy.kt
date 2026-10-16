/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import androidx.annotation.StringRes
import app.morphe.manager.R

/**
 * String resource ids for one decision. Every field is a resource id from `values/oru_strings.xml`
 * or `values/oru_strings_install.xml`, or null when the decision has no such line.
 *
 * Placeholders are not filled here: a `%1$s` in [body] or [hint] takes the installer or app label
 * the caller already has.
 */
data class DecisionCopy(
    @param:StringRes val title: Int? = null,
    @param:StringRes val body: Int? = null,
    @param:StringRes val primaryAction: Int? = null,
    @param:StringRes val secondaryAction: Int? = null,
    @param:StringRes val hint: Int? = null,
    @param:StringRes val secondaryHint: Int? = null
)

/** Maps the planner's results and the signature check to Oru copy. No Android I/O. */
object OruDecisionCopy {

    /**
     * Copy for the install step the planner returned. The silent line in [DecisionCopy.body] takes the
     * installer's label as its `%1$s`, which the caller formats.
     */
    fun forStep(step: InstallStep): DecisionCopy = when (step) {
        InstallStep.RootPatchMode -> DecisionCopy(
            title = R.string.oru_root_title,
            body = R.string.oru_root_body,
            primaryAction = R.string.oru_root_action_mount,
            secondaryAction = R.string.oru_root_action_standard
        )

        InstallStep.AskInstaller -> DecisionCopy(
            title = R.string.oru_settings_installer,
            body = R.string.oru_install_pick_body
        )

        is InstallStep.ConflictNeedsUninstall -> conflictCopy(step)

        is InstallStep.NeedsPermission -> DecisionCopy(
            body = R.string.oru_install_unknown_sources,
            primaryAction = R.string.oru_action_allow_installs
        )

        is InstallStep.InstallerUnavailable -> DecisionCopy(
            title = R.string.oru_installer_unavailable_title,
            body = R.string.oru_installer_unavailable_body,
            primaryAction = when (step.action) {
                UnavailableAction.OPEN_INSTALLER -> R.string.oru_action_open_shizuku
                UnavailableAction.GRANT_PERMISSION -> R.string.oru_action_grant_shizuku
                UnavailableAction.NONE -> null
            },
            secondaryAction = R.string.oru_action_install_with_android,
            hint = unavailableHint(step.reason)
        )

        is InstallStep.AutoInstall -> DecisionCopy(
            body = R.string.oru_install_automatic
        )

        is InstallStep.ShowInstall -> DecisionCopy(
            // Silent: a no-prompt line for Shizuku or root, a may-still-ask line for the system installer.
            // Otherwise the system line or the Android line.
            body = installLine(step),
            primaryAction = R.string.oru_action_install,
            // Shown when a preferred installer was missing; the caller passes the fallback's label
            hint = if (step.fallbackFrom != null) R.string.oru_install_fallback else null
        )
    }

    /** Key notice on the first successful run (I10). Shown once; its action opens the signing key settings. */
    fun keyNotice(): DecisionCopy = DecisionCopy(
        title = R.string.oru_key_notice_title,
        body = R.string.oru_key_notice_body,
        primaryAction = R.string.oru_action_open_signing_key
    )

    private fun conflictCopy(step: InstallStep.ConflictNeedsUninstall): DecisionCopy =
        when (step.reason) {
            ConflictReason.SIGNATURE -> DecisionCopy(
                title = R.string.oru_signature_title,
                body = R.string.oru_signature_body,
                primaryAction = R.string.oru_signature_go,
                secondaryAction = if (step.canIgnoreSignatureMismatch) R.string.oru_signature_ignore else null,
                hint = if (step.canUninstallSilently) R.string.oru_signature_hint_shizuku
                else R.string.oru_signature_hint_import,
                secondaryHint = if (step.canIgnoreSignatureMismatch) R.string.oru_signature_ignore_hint else null
            )

            ConflictReason.DOWNGRADE -> DecisionCopy(
                title = R.string.oru_install_downgrade_title,
                body = R.string.oru_install_downgrade,
                primaryAction = R.string.oru_signature_go
            )
        }

    private fun installLine(step: InstallStep.ShowInstall): Int = when {
        // The system installer can still refuse a silent update, so its line keeps the prompt possible
        step.silent && step.installer == InstallerChoice.SYSTEM -> R.string.oru_install_with_system_silent
        step.silent -> R.string.oru_install_with_silent   // "Installs with %1$s, without a prompt."
        step.installer == InstallerChoice.SYSTEM -> R.string.oru_install_with_system
        else -> R.string.oru_install_with_android
    }

    private fun unavailableHint(reason: UnavailableReason): Int? = when (reason) {
        UnavailableReason.SHIZUKU_PERMISSION_MISSING -> R.string.oru_error_shizuku_permission
        UnavailableReason.SHIZUKU_NOT_INSTALLED -> R.string.oru_unavailable_shizuku_not_installed
        UnavailableReason.SHIZUKU_UNSUPPORTED -> R.string.oru_unavailable_shizuku_unsupported
        UnavailableReason.SHIZUKU_NOT_RUNNING -> R.string.oru_unavailable_shizuku_not_running
        UnavailableReason.ROOT_REQUIRED -> R.string.oru_unavailable_root
    }
}
