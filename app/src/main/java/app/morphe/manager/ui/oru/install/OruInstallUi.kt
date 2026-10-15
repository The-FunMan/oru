/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import app.morphe.manager.ui.oru.components.OruHeroState

/**
 * One installer the picker can offer. [token] is the preference form (`InstallerManager.tokenToPreference`),
 * passed back to the executor unchanged, so custom components keep their component name.
 */
data class OruInstallerOption(
    val choice: InstallerChoice,
    val token: String,
    val label: String,
    val enabled: Boolean,
    val reason: String? = null
)

/** What the install executor is doing. Mirrors `InstallViewModel.InstallState` without Android types. */
sealed interface OruExecution {
    /** Nothing running. */
    data object Idle : OruExecution

    /** `InstallViewModel.install` is running (Installing, including the system prompt wait). */
    data object Running : OruExecution

    /** `InstallViewModel.InstallState.Installed`. */
    data class Installed(val packageName: String) : OruExecution

    /** `InstallViewModel.InstallState.Error`. */
    data class Failed(val message: String) : OruExecution

    /**
     * `InstallViewModel.InstallState.Conflict`: the install-time check found a conflict. The coordinator
     * treats it as a signature conflict on the next plan, so the decision is offered instead of a retry loop.
     */
    data object Conflict : OruExecution
}

/** What the install content shows. The content composable renders exactly one of these. */
sealed interface OruInstallUi {
    /** The planner is still working out the first step. */
    data object Checking : OruInstallUi

    /** The planner asks for something before the install runs, or offers the install itself. */
    data class Plan(
        val step: InstallStep,
        /** The label of the installer the step resolves to, when the step names one. */
        val installerLabel: String?,
        /** The label of the preferred installer that was missing, when the step fell back to another. */
        val fallbackLabel: String?,
        /** Rows for the picker. Only used by [InstallStep.AskInstaller]. */
        val options: List<OruInstallerOption>
    ) : OruInstallUi

    /** Running. [automatic] is true when the install started without a tap. */
    data class Installing(val automatic: Boolean, val installerLabel: String?) : OruInstallUi

    /** Installed. [installerLabel] names what installed it, for the Done line. */
    data class Installed(val installerLabel: String?) : OruInstallUi

    /** The install failed. [message] is the executor's text. */
    data class Failed(val message: String) : OruInstallUi
}

/** Maps the planner's step and the executor's state to what the screen shows. Pure. */
object OruInstallScreenMapper {

    /**
     * [plan] is null until the planner has run. An [InstallStep.AutoInstall] shows the short automatic
     * state (no Install button) while the executor is still idle, because the coordinator starts it.
     */
    fun map(
        plan: InstallStep?,
        execution: OruExecution,
        options: List<OruInstallerOption>,
        labelFor: (InstallerChoice) -> String?
    ): OruInstallUi {
        val installer = installerOf(plan)
        val label = installer?.let(labelFor)
        return when (execution) {
            is OruExecution.Installed -> OruInstallUi.Installed(installerLabel = label)
            is OruExecution.Failed -> OruInstallUi.Failed(execution.message)
            OruExecution.Running -> OruInstallUi.Installing(
                automatic = plan is InstallStep.AutoInstall,
                installerLabel = label
            )
            OruExecution.Idle, OruExecution.Conflict -> when (plan) {
                null -> OruInstallUi.Checking
                is InstallStep.AutoInstall -> OruInstallUi.Installing(automatic = true, installerLabel = label)
                else -> OruInstallUi.Plan(
                    step = plan,
                    installerLabel = label,
                    fallbackLabel = (plan as? InstallStep.ShowInstall)?.fallbackFrom?.let(labelFor),
                    options = options
                )
            }
        }
    }

    /**
     * Hero state for the install screen. The run's hexagon carries on through the install: it is working while the
     * install is planned or runs, and it is done, with its check, only once the install has happened.
     */
    fun heroState(ui: OruInstallUi): OruHeroState = when (ui) {
        OruInstallUi.Checking -> OruHeroState.Working
        is OruInstallUi.Plan -> OruHeroState.Working
        is OruInstallUi.Installing -> OruHeroState.Working
        is OruInstallUi.Installed -> OruHeroState.Done
        is OruInstallUi.Failed -> OruHeroState.Failed
    }

    /**
     * The fill of the run's hexagon on the install screen, from [runFill], the fill the run ended at. The fill holds while
     * the install is planned, fills half of the install's share while it runs, and is whole once it has installed.
     */
    fun fill(ui: OruInstallUi, runFill: Float): Float = when (ui) {
        OruInstallUi.Checking, is OruInstallUi.Plan, is OruInstallUi.Failed -> runFill
        is OruInstallUi.Installing -> runFill + (1f - runFill) * InstallingShare
        is OruInstallUi.Installed -> 1f
    }

    /** The part of the install's share that is filled while the install runs. */
    private const val InstallingShare = 0.5f

    /** The installer a step resolves to, if it names one. */
    fun installerOf(plan: InstallStep?): InstallerChoice? = when (plan) {
        is InstallStep.ShowInstall -> plan.installer
        is InstallStep.AutoInstall -> plan.installer
        is InstallStep.InstallerUnavailable -> plan.installer
        else -> null
    }
}
