/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

/**
 * The installer the user chose (`installer_primary`), or the one a run resolved to. Mirrors
 * `InstallerManager.Token` without Android types, so the planner runs on the JVM.
 */
enum class InstallerChoice {
    /** The system installer (`:internal:`). */
    SYSTEM,

    /** Play Store spoof: claims Play Store as the installer package (`:play_store:`). */
    PLAY_STORE,

    SHIZUKU,
    SHIZUKU_PLAY_STORE,

    /** Root mount (`InstallerManager.Token.AutoSaved`), also called "root" in preferences. */
    ROOT,
    ROOT_PLAY_STORE,

    /** A third-party installer picked as a component. */
    CUSTOM,

    /** `:none:`, which resolves to the system installer like today. */
    NONE
}

/** Shizuku's state, in the order `SessionInstaller.shizukuStatus` checks it. */
enum class ShizukuState {
    READY,
    NOT_INSTALLED,
    UNSUPPORTED,
    NOT_RUNNING,
    PERMISSION_MISSING
}

/** Everything the planner needs. Built by the adapters in OruInstallInputs.kt, or by tests. */
data class InstallPlanInput(
    /** `installer_primary`, parsed. */
    val primary: InstallerChoice,
    /** The installer picked in the one-time picker for this install, if any. */
    val pickedInstaller: InstallerChoice? = null,
    val shizuku: ShizukuState,
    /** Root is granted to Oru (`RootInstaller.hasRootAccess`). Not the same as [deviceRooted]. */
    val rootAccessGranted: Boolean,
    /** `RootInstaller.isDeviceRooted`, which never raises the root prompt. */
    val deviceRooted: Boolean,
    /** Whether the root patch-mode question was answered for this run. Only asked when [deviceRooted]. */
    val rootModeChosen: Boolean,
    /** Whether a custom installer component still resolves. */
    val customInstallerAvailable: Boolean,
    /** The auto-install rule, [OruInstallPlanner.AUTO_INSTALL_PREF] in the app. */
    val autoInstallPref: Boolean,
    /** `prompt_installer_on_install`. */
    val promptInstallerPref: Boolean,
    /** `auto_uninstall_with_shizuku`. */
    val autoUninstallPref: Boolean,
    /** `SessionInstaller.canUpdateSilently` for the package being installed. */
    val silentUpdateAllowed: Boolean,
    /** `PM.canInstallPackages()`: the "install unknown apps" permission. */
    val installPermissionGranted: Boolean,
    /** Whether the one-time explainer for that permission was already shown. */
    val permissionExplained: Boolean,
    val signature: SignatureCheck,
    /** The install is older than the installed app (`InstallViewModel.install`, the version check). */
    val downgrade: Boolean = false,
    /** The signature check was already skipped for this attempt (root "Install anyway" retry). */
    val signatureCheckBypassed: Boolean = false
)

enum class ConflictReason { SIGNATURE, DOWNGRADE }

enum class UnavailableReason {
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_UNSUPPORTED,
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_PERMISSION_MISSING,
    ROOT_REQUIRED
}

/** The one extra action offered next to Try again and Install with Android. */
enum class UnavailableAction {
    /** Opens Shizuku (mirrors `canOpenApp = true`). */
    OPEN_INSTALLER,

    /** Asks Shizuku for Oru's permission. */
    GRANT_PERMISSION,

    NONE
}

/** The next install step. Each outcome names the rule it came from in [OruInstallPlanner.plan]. */
sealed interface InstallStep {
    /** Rooted device, question not answered: Root mount or Standard, before the run. */
    data object RootPatchMode : InstallStep

    /** `prompt_installer_on_install` is on and no installer was picked: show the picker. */
    data object AskInstaller : InstallStep

    /**
     * The install needs the installed app removed first, which erases its data.
     * [canUninstallSilently] means the auto-install path removes it through Shizuku without a prompt.
     * [canIgnoreSignatureMismatch] means root may install over it (the caller then bypasses the check).
     * When [canUninstallSilently] is true the caller removes the app and then plans again with
     * the signature treated as cleared.
     */
    data class ConflictNeedsUninstall(
        val reason: ConflictReason,
        val dataLoss: Boolean,
        val canUninstallSilently: Boolean,
        val canIgnoreSignatureMismatch: Boolean
    ) : InstallStep

    /** The system installer needs "install unknown apps". [explainFirst] is true the first time only. */
    data class NeedsPermission(val explainFirst: Boolean) : InstallStep

    /** The chosen installer cannot run. Shown inline with [reason] and [action]; nothing falls back silently. */
    data class InstallerUnavailable(
        val installer: InstallerChoice,
        val reason: UnavailableReason,
        val action: UnavailableAction
    ) : InstallStep

    /** Starts without a tap (D19). Only for an installer that is silent and ready. */
    data class AutoInstall(val installer: InstallerChoice) : InstallStep

    /**
     * One tap. [silent] means no system prompt follows. [fallbackFrom] is set when the preferred
     * installer was missing and the system installer is used instead; the Done line names it.
     */
    data class ShowInstall(
        val installer: InstallerChoice,
        val silent: Boolean,
        val fallbackFrom: InstallerChoice?
    ) : InstallStep
}

/**
 * Decides the next install step. Pure: no Android types, no I/O.
 *
 * The order follows `InstallViewModel.install` (picker, then version and signature checks, then
 * installer resolution) with the new permission gate placed after the installer is known.
 */
object OruInstallPlanner {

    /**
     * The auto-install rule (SETTINGS_IA row 18). Oru has no switch for it: a run starts with no
     * tap when the installer is silent, unless the user asked to be asked which installer each time.
     */
    const val AUTO_INSTALL_PREF = true

    fun plan(input: InstallPlanInput): InstallStep {
        // Root mode is chosen before the run, on rooted devices only (HomeViewModel.kt:603, isDeviceRooted)
        if (input.deviceRooted && !input.rootModeChosen) return InstallStep.RootPatchMode

        // Auto-start is decided from preferences and the primary installer alone, before any conflict
        // check runs (InstallerManager.kt:143-156). Mirrored here, not re-derived from the outcome.
        val autoStart = autoStartAllowed(input)

        // The picker comes before the version and signature checks (InstallViewModel.kt:223-229)
        if (input.promptInstallerPref && input.pickedInstaller == null) return InstallStep.AskInstaller

        val chosen = input.pickedInstaller ?: input.primary

        // A downgrade is a conflict; it never reaches the silent uninstall (InstallViewModel.kt:243-249)
        if (input.downgrade) {
            return InstallStep.ConflictNeedsUninstall(
                reason = ConflictReason.DOWNGRADE,
                dataLoss = true,
                canUninstallSilently = false,
                canIgnoreSignatureMismatch = false
            )
        }

        // Signature conflict: only checked when not bypassed (InstallViewModel.kt:252-263)
        if (!input.signatureCheckBypassed && input.signature.conflicts) {
            // tryAutoUninstallSignatureConflict (InstallViewModel.kt:1268-1287): auto context, the
            // auto-uninstall switch, Shizuku as the token and Shizuku ready
            val silentUninstall = autoStart &&
                    input.autoUninstallPref &&
                    chosen.isShizuku() &&
                    input.shizuku == ShizukuState.READY
            return InstallStep.ConflictNeedsUninstall(
                reason = ConflictReason.SIGNATURE,
                dataLoss = true,
                canUninstallSilently = silentUninstall,
                // signatureConflictState (InstallViewModel.kt:1456-1463): root access, check not already skipped
                canIgnoreSignatureMismatch = input.rootAccessGranted
            )
        }

        // Installer resolution (InstallerManager.resolvePlanWithStatus; InstallViewModel.kt:297-328)
        val resolved: InstallerChoice
        var fallbackFrom: InstallerChoice? = null
        when (chosen) {
            InstallerChoice.SHIZUKU, InstallerChoice.SHIZUKU_PLAY_STORE -> {
                if (input.shizuku != ShizukuState.READY) {
                    return InstallStep.InstallerUnavailable(
                        installer = chosen,
                        reason = input.shizuku.toUnavailableReason(),
                        action = if (input.shizuku == ShizukuState.PERMISSION_MISSING) {
                            UnavailableAction.GRANT_PERMISSION
                        } else {
                            UnavailableAction.OPEN_INSTALLER
                        }
                    )
                }
                resolved = chosen
            }

            // Root installers show the dialog with no open action (canOpenApp = false)
            InstallerChoice.ROOT, InstallerChoice.ROOT_PLAY_STORE -> {
                if (!input.rootAccessGranted) {
                    return InstallStep.InstallerUnavailable(
                        installer = chosen,
                        reason = UnavailableReason.ROOT_REQUIRED,
                        action = UnavailableAction.NONE
                    )
                }
                resolved = chosen
            }

            // Custom installers fall back to the system installer with no notice today (InstallViewModel.kt:320-327).
            // Oru keeps the fallback and names it on Done (fallbackFrom).
            InstallerChoice.CUSTOM -> {
                if (input.customInstallerAvailable) {
                    resolved = chosen
                } else {
                    resolved = InstallerChoice.SYSTEM
                    fallbackFrom = chosen
                }
            }

            // :none: resolves to the system installer (InstallerManager.resolvePlanWithStatus fallback sequence)
            InstallerChoice.NONE -> {
                resolved = InstallerChoice.SYSTEM
                fallbackFrom = chosen
            }

            InstallerChoice.SYSTEM, InstallerChoice.PLAY_STORE -> resolved = chosen
        }

        // Install-unknown-apps applies to the installers that go through the system package installer.
        // Shizuku, root and custom installers do not need it. New gate (plan I8), not in today's code.
        if (resolved.usesSystemPackageInstaller() && !input.installPermissionGranted) {
            return InstallStep.NeedsPermission(explainFirst = !input.permissionExplained)
        }

        val silent = when (resolved) {
            InstallerChoice.SHIZUKU, InstallerChoice.SHIZUKU_PLAY_STORE,
            InstallerChoice.ROOT, InstallerChoice.ROOT_PLAY_STORE -> true
            InstallerChoice.SYSTEM -> input.silentUpdateAllowed
            else -> false
        }

        // autoInstallAllowed only ever returns true for the primary installer itself, so a fallback never auto-starts
        if (autoStart) return InstallStep.AutoInstall(resolved)
        return InstallStep.ShowInstall(resolved, silent = silent, fallbackFrom = fallbackFrom)
    }

    /**
     * `InstallerManager.autoInstallAllowed`: the switch is on, the picker is off, and the primary is
     * Shizuku (any state, the install then shows the unavailable notice) or the system installer
     * that Android updates silently. Root, Play Store and custom installers never auto-start.
     */
    fun autoStartAllowed(input: InstallPlanInput): Boolean {
        if (!input.autoInstallPref) return false
        if (input.promptInstallerPref) return false
        return when (input.primary) {
            InstallerChoice.SHIZUKU, InstallerChoice.SHIZUKU_PLAY_STORE -> true
            InstallerChoice.SYSTEM -> input.silentUpdateAllowed
            else -> false
        }
    }

    private fun InstallerChoice.isShizuku() =
        this == InstallerChoice.SHIZUKU || this == InstallerChoice.SHIZUKU_PLAY_STORE

    private fun InstallerChoice.usesSystemPackageInstaller() =
        this == InstallerChoice.SYSTEM || this == InstallerChoice.PLAY_STORE

    private fun ShizukuState.toUnavailableReason(): UnavailableReason = when (this) {
        ShizukuState.NOT_INSTALLED -> UnavailableReason.SHIZUKU_NOT_INSTALLED
        ShizukuState.UNSUPPORTED -> UnavailableReason.SHIZUKU_UNSUPPORTED
        ShizukuState.NOT_RUNNING -> UnavailableReason.SHIZUKU_NOT_RUNNING
        ShizukuState.PERMISSION_MISSING -> UnavailableReason.SHIZUKU_PERMISSION_MISSING
        // Only reached for non-READY states; READY never reaches an unavailable outcome
        ShizukuState.READY -> error("READY is not an unavailable state")
    }
}
