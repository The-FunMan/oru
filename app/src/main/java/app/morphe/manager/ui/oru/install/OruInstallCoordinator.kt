/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.ui.oru.home.oruAppName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The install the coordinator runs. [packageName] is the stock app's package, which the root mount handling
 * of the install needs. [installPackageName] is the package the patched archive installs as, which can differ
 * (a patched app may carry another package), and which every check against the installed app reads.
 * [persist] is the patcher's callback that records the installed app (`PatcherViewModel.persistPatchedApp`).
 * The coordinator never calls it itself.
 */
data class OruInstallRequest(
    val outputFile: File,
    val packageName: String,
    val installPackageName: String,
    val persist: suspend (String, InstallType) -> Boolean,
)

/**
 * Builds the request for a finished run. The install package is read off the patched archive, because a patch
 * can change it; when the archive cannot be read the stock package stands in.
 */
suspend fun oruInstallRequest(
    outputFile: File,
    stockPackage: String,
    packages: OruPackageReadings,
    persist: suspend (String, InstallType) -> Boolean,
): OruInstallRequest = OruInstallRequest(
    outputFile = outputFile,
    packageName = stockPackage,
    installPackageName = packages.archivePackageName(outputFile)?.takeIf { it.isNotBlank() } ?: stockPackage,
    persist = persist,
)

/** Installer settings and installer status the planner reads. Implemented over PreferencesManager and InstallerManager. */
interface OruInstallerReadings {
    /** The primary installer, parsed (`installer_primary`). */
    suspend fun primaryChoice(): InstallerChoice

    /** The primary installer in preference form, for [OruInstallExecutor.startWithPicked]. */
    suspend fun primaryToken(): String

    suspend fun promptInstallerPref(): Boolean
    suspend fun autoInstallPref(): Boolean
    suspend fun autoUninstallPref(): Boolean

    /** Shizuku's state, in the order `SessionInstaller.shizukuStatus` checks it. */
    suspend fun shizukuState(): ShizukuState

    /** Root is granted to Oru (`RootInstaller.hasRootAccess`). */
    suspend fun rootAccessGranted(): Boolean

    /** `RootInstaller.isDeviceRooted`, which never raises the root prompt. */
    suspend fun deviceRooted(): Boolean

    /** Whether the custom installer for [token] still resolves. Always true for built-in installers. */
    suspend fun customInstallerAvailable(token: String): Boolean

    /** The picker rows in display order. Also refreshes the labels [labelFor] returns. */
    suspend fun pickerOptions(): List<OruInstallerOption>

    /** The display label for [choice], from the last [pickerOptions] call. */
    fun labelFor(choice: InstallerChoice): String?
}

/** Package and system reads the planner needs. Implemented over PM and SessionInstaller. */
interface OruPackageReadings {
    /** The output APK's version is lower than the installed app's (`InstallViewModel.install`). */
    suspend fun isDowngrade(packageName: String, outputFile: File): Boolean

    /** `SessionInstaller.canUpdateSilently` for [packageName]. */
    suspend fun silentUpdateAllowed(packageName: String): Boolean

    /** `PM.canInstallPackages()`: the install unknown apps permission. */
    suspend fun installPermissionGranted(): Boolean

    /** The package name the archive at [file] installs as, or null when it cannot be read. */
    suspend fun archivePackageName(file: File): String?

    /** The label of the installed app [packageName], or null when it is not installed. */
    suspend fun installedLabel(packageName: String): String?
}

/**
 * The executor side. Implemented over `InstallViewModel`, the only place installs run. Each method names
 * the existing function it calls.
 */
interface OruInstallExecutor {
    /** The executor's state now. Mirrors `InstallViewModel.installState`. */
    val execution: OruExecution

    /**
     * `InstallViewModel.install`. With the installer picker on, this stores the request and returns at
     * the picker (the picker itself is Oru's, so the ViewModel's dialog must not be shown).
     */
    fun startInstall(request: OruInstallRequest, autoUninstallOnConflict: Boolean)

    /** `InstallViewModel.proceedWithSelectedInstaller`, with the token from the picker. Needs [startInstall] first. */
    fun startWithPicked(token: String)

    /** `InstallViewModel.dismissInstallerSelectionDialog`. */
    fun dismissPicker()

    /** `InstallViewModel.proceedWithFallbackInstaller`: installs with the system installer after an unavailable one. */
    fun installWithAndroid()

    /**
     * `InstallViewModel.requestUninstall(packageName, installAfterUninstall = true)`. The ViewModel removes
     * the app (through Shizuku with no prompt when it can) and then installs the patched build itself.
     */
    fun uninstallAndInstall(packageName: String)

    /** `InstallViewModel.installIgnoringSignatureMismatch`. Root only. */
    fun installIgnoringSignatureMismatch()

    /** `InstallViewModel.openShizukuApp`. */
    fun openInstallerApp(): Boolean

    /** `InstallViewModel.requestShizukuPermission`. */
    fun requestShizukuPermission(): Boolean

    /** `InstallViewModel.openApp`. */
    fun openApp()

    /** `InstallViewModel.resetInstallState`. */
    fun resetExecution()

    /** `InstallViewModel.stopWaiting`: cancels the running install or uninstall wait and keeps the APK. */
    fun stopWaiting()
}

/** What the install screen shows: the planner's step, the mapped UI, and whether the decision dialog is open. */
data class OruInstallState(
    val plan: InstallStep? = null,
    val ui: OruInstallUi = OruInstallUi.Checking,
    val decisionOpen: Boolean = false,
    /** The label of the installed copy the conflict would remove. Set only while the plan is a conflict. */
    val installedLabel: String? = null,
)

/**
 * Runs the Oru install screen: plans each step with [OruInstallPlanner], keeps the user's picker and
 * root answers, and calls [OruInstallExecutor] to run the install. Plain Kotlin, no Android types.
 *
 * Caller contract (the Focus screen):
 * - Call [start] when the Done phase appears. It plans, seeds the executor for the picker or the
 *   fallback, and starts an automatic install.
 * - Call [refresh] whenever the executor's state changes (`InstallViewModel.installState`), and on resume
 *   (the notification or Shizuku and install-permission settings may have changed).
 * - The picker is Oru's. Do not render `InstallerFlowDialogs` for the same ViewModel on this screen.
 * - [pickInstaller] only feeds the choice back into the plan. [install] runs it, through
 *   `proceedWithSelectedInstaller` when a choice was picked, so the picked installer is honoured.
 * - After a removal, [confirmUninstall] asks the ViewModel to remove and reinstall. Call [onPackageRemoved]
 *   once the package is gone, so the next plan sees no signature conflict. Never call [install] after
 *   [confirmUninstall]: the ViewModel already reinstalls.
 * - [install] replans first and does nothing unless the fresh plan is a plain install, so a conflict that
 *   appeared since the screen was drawn is never installed over.
 * - Call [close] when the screen leaves, so the ViewModel's own picker flag is cleared.
 */
class OruInstallCoordinator(
    private val request: OruInstallRequest,
    private val installers: OruInstallerReadings,
    private val packages: OruPackageReadings,
    private val signatures: SignatureSource,
    private val executor: OruInstallExecutor,
    /** Records the root patch mode the user chose (Root mount or Standard), for the patch run to read. */
    private val onRootModeChosen: (useMount: Boolean) -> Unit,
) {
    private val signatureCheck = OruSignatureCheck(signatures)

    private val _state = MutableStateFlow(OruInstallState())
    val state: StateFlow<OruInstallState> = _state.asStateFlow()

    private var picked: OruInstallerOption? = null
    private var rootModeChosen = false
    private var permissionExplained = false
    private var signatureCleared = false
    private var signatureBypassed = false
    private var seeded = false
    private var decisionOpen = false

    /** The automatic start is used up by the first install this screen starts, so a later plan asks for a tap. */
    private var autoStartSpent = false

    /** The run in progress was started without a tap (the automatic start). */
    private var runAutomatic = false

    /** The executor's state at the last replan, to see a run end. Null before the first replan. */
    private var lastExecution: OruExecution? = null

    /** The last run ended without installing, and no run has started since. */
    private var declined = false

    /** Plans once and starts whatever the plan says starts without a tap. */
    suspend fun start() {
        refresh()
        when (_state.value.plan) {
            // The executor needs the request before the picker or the fallback can run
            InstallStep.AskInstaller, is InstallStep.InstallerUnavailable -> seed()
            is InstallStep.AutoInstall -> install()
            else -> Unit
        }
    }

    /** Replans from the current readings and the executor's state. */
    suspend fun refresh() {
        val input = readInput()
        val plan = OruInstallPlanner.plan(input)
        val options = installers.pickerOptions()
        val conflict = plan is InstallStep.ConflictNeedsUninstall
        val installPackage = request.installPackageName
        val installedLabel = if (conflict) {
            packages.installedLabel(installPackage)?.let { oruAppName(installPackage, it) }
        } else {
            null
        }
        // Read last: nothing suspends from here to the state write, so the screen never keeps an outcome the executor left
        val execution = executor.execution
        when {
            execution == OruExecution.Running -> declined = false
            lastExecution == OruExecution.Running && execution == OruExecution.Idle -> declined = true
        }
        lastExecution = execution
        val ui = OruInstallScreenMapper.map(
            plan = plan,
            execution = execution,
            options = options,
            labelFor = installers::labelFor,
            automatic = runAutomatic,
            declined = declined,
        )
        _state.value = OruInstallState(
            plan = plan,
            ui = ui,
            decisionOpen = decisionOpen && conflict,
            installedLabel = installedLabel,
        )
    }

    /** The user picked an installer. Replans with it; nothing installs until [install]. */
    suspend fun pickInstaller(option: OruInstallerOption) {
        if (!option.enabled) return
        picked = option
        refresh()
    }

    /** The Install tap, or the automatic start. Runs only when a fresh plan is a plain install. */
    suspend fun install() {
        refresh()
        // Never start a second install over one that is running or already done
        if (executor.execution == OruExecution.Running || executor.execution is OruExecution.Installed) return
        val plan = _state.value.plan
        if (plan !is InstallStep.ShowInstall && plan !is InstallStep.AutoInstall) return
        val automatic = plan is InstallStep.AutoInstall
        // Any start ends the automatic one, so when this run ends the screen asks for a tap
        autoStartSpent = true
        runAutomatic = automatic
        val chosen = picked
        if (chosen != null) {
            executor.startWithPicked(chosen.token)
        } else {
            executor.startInstall(request, autoUninstallOnConflict = automatic)
        }
    }

    /** The user looked at the signature conflict and wants the decision dialog. */
    suspend fun reviewConflict() {
        decisionOpen = true
        refresh()
    }

    /** The user cancelled the decision. Nothing changes on the phone. */
    suspend fun dismissDecision() {
        decisionOpen = false
        refresh()
    }

    /**
     * "Remove it and install". Removes the installed copy of the patched package (silently through Shizuku when the plan allows it,
     * otherwise through the system prompt) and installs the patched build; the ViewModel does both.
     */
    suspend fun confirmUninstall() {
        if (_state.value.plan !is InstallStep.ConflictNeedsUninstall) return
        decisionOpen = false
        runAutomatic = false
        executor.uninstallAndInstall(request.installPackageName)
        refresh()
    }

    /** Call once the installed package is gone. The next plan drops the signature conflict. */
    suspend fun onPackageRemoved() {
        signatureCleared = true
        decisionOpen = false
        refresh()
    }

    /** "Install anyway" on a rooted device, where a module may skip the platform signature check. */
    suspend fun installIgnoringSignatureMismatch() {
        val plan = _state.value.plan as? InstallStep.ConflictNeedsUninstall ?: return
        if (!plan.canIgnoreSignatureMismatch) return
        signatureBypassed = true
        decisionOpen = false
        runAutomatic = false
        executor.installIgnoringSignatureMismatch()
        refresh()
    }

    /** The user answered the root patch mode step. */
    suspend fun chooseRootMode(useMount: Boolean) {
        rootModeChosen = true
        onRootModeChosen(useMount)
        refresh()
    }

    /** The user saw the install unknown apps explanation and is sent to the system setting. */
    suspend fun onPermissionExplained() {
        permissionExplained = true
        refresh()
    }

    /** "Open Shizuku" on an unavailable installer. */
    suspend fun openInstallerApp() {
        executor.openInstallerApp()
        refresh()
    }

    /** "Grant permission" on an unavailable Shizuku. */
    suspend fun grantInstallerPermission() {
        executor.requestShizukuPermission()
        refresh()
    }

    /** "Install with Android" on an unavailable installer. The user chose the system installer. */
    suspend fun installWithAndroid() {
        runAutomatic = false
        executor.installWithAndroid()
        refresh()
    }

    /** "Try again" after a failed install. */
    suspend fun retry() {
        executor.resetExecution()
        install()
    }

    /**
     * The user stopped waiting on the install or the uninstall. The executor cancels the wait, abandons an
     * unfinished session and keeps the APK, so the screen is back at the plan and Install works again. Where the
     * executor does not leave the running state, the coordinator resets it, so the screen never stays on Installing.
     */
    suspend fun stopWaiting() {
        autoStartSpent = true
        executor.stopWaiting()
        if (executor.execution == OruExecution.Running) executor.resetExecution()
        // A stop is not a decline, so no notice follows it
        lastExecution = executor.execution
        declined = false
        refresh()
    }

    /** "Open" after the install. */
    fun open() {
        executor.openApp()
    }

    /** The screen left. Clears the executor's picker flag. */
    fun close() {
        executor.dismissPicker()
    }

    /** Seeds the executor once, so a later picker choice or fallback has the install request. */
    private fun seed() {
        if (seeded) return
        seeded = true
        executor.startInstall(request, autoUninstallOnConflict = false)
    }

    private suspend fun readInput(): InstallPlanInput {
        val chosen = picked
        val chosenToken = chosen?.token ?: installers.primaryToken()
        val signature = when {
            signatureCleared -> SignatureCheck.NoConflict
            // The executor found a conflict the pre-check did not: the decision must be offered
            executor.execution == OruExecution.Conflict ->
                SignatureCheck.WillConflict(installedHashes = emptySet(), oruHashes = emptySet())
            else -> signatureCheck.check(request.installPackageName)
        }
        return InstallPlanInput(
            primary = installers.primaryChoice(),
            pickedInstaller = chosen?.choice,
            shizuku = installers.shizukuState(),
            rootAccessGranted = installers.rootAccessGranted(),
            deviceRooted = installers.deviceRooted(),
            rootModeChosen = rootModeChosen,
            customInstallerAvailable = installers.customInstallerAvailable(chosenToken),
            autoInstallPref = installers.autoInstallPref() && !autoStartSpent,
            promptInstallerPref = installers.promptInstallerPref(),
            autoUninstallPref = installers.autoUninstallPref(),
            silentUpdateAllowed = packages.silentUpdateAllowed(request.installPackageName),
            installPermissionGranted = packages.installPermissionGranted(),
            permissionExplained = permissionExplained,
            signature = signature,
            downgrade = packages.isDowngrade(request.installPackageName, request.outputFile),
            signatureCheckBypassed = signatureBypassed,
        )
    }
}
