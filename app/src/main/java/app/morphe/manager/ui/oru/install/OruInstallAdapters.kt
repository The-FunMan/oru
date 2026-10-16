/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import android.app.Application
import android.util.Log
import app.morphe.manager.domain.installer.InstallWaitPolicy
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.PM
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Android-facing adapters for OruInstallCoordinator. Each one wraps an existing class and adds no
 * install logic: the coordinator's decisions are in the planner, and the installs run in InstallViewModel.
 */

/** [OruInstallerReadings] over PreferencesManager, InstallerManager, SessionInstaller and RootInstaller. */
class AppOruInstallerReadings(
    private val app: Application,
    private val prefs: PreferencesManager,
    private val installerManager: InstallerManager,
    private val sessionInstaller: SessionInstaller,
    private val rootInstaller: RootInstaller,
) : OruInstallerReadings {

    private var labels: Map<InstallerChoice, String> = emptyMap()

    private suspend fun primaryInstallerToken(): InstallerManager.Token =
        installerManager.parseToken(prefs.installerPrimary.get())

    override suspend fun primaryChoice(): InstallerChoice =
        primaryInstallerToken().toInstallerChoice()

    override suspend fun primaryToken(): String =
        installerManager.tokenToPreference(primaryInstallerToken())

    override suspend fun promptInstallerPref(): Boolean = prefs.promptInstallerOnInstall.get()

    // The decided rule, not a stored switch: Oru has no auto-install toggle (SETTINGS_IA row 18)
    override suspend fun autoInstallPref(): Boolean = OruInstallPlanner.AUTO_INSTALL_PREF

    override suspend fun autoUninstallPref(): Boolean = prefs.autoUninstallWithShizuku.get()

    override suspend fun shizukuState(): ShizukuState = withContext(Dispatchers.IO) {
        installerManager.shizukuStatus(InstallerManager.InstallTarget.PATCHER).toShizukuState()
    }

    override suspend fun rootAccessGranted(): Boolean = withContext(Dispatchers.IO) {
        rootInstaller.hasRootAccess()
    }

    override suspend fun deviceRooted(): Boolean = withContext(Dispatchers.IO) {
        rootInstaller.isDeviceRooted()
    }

    override suspend fun customInstallerAvailable(token: String): Boolean = withContext(Dispatchers.IO) {
        when (val parsed = installerManager.parseToken(token)) {
            is InstallerManager.Token.Component ->
                installerManager.describeEntry(parsed, InstallerManager.InstallTarget.PATCHER)
                    ?.availability?.available == true
            else -> true
        }
    }

    override suspend fun pickerOptions(): List<OruInstallerOption> {
        val entries = withContext(Dispatchers.IO) {
            val raw = installerManager.listEntries(InstallerManager.InstallTarget.PATCHER, includeNone = false)
            installerManager.ensureValidEntries(raw, primaryInstallerToken(), InstallerManager.InstallTarget.PATCHER)
        }
        val options = entries.map { entry ->
            OruInstallerOption(
                choice = entry.token.toInstallerChoice(),
                token = installerManager.tokenToPreference(entry.token),
                label = entry.label,
                enabled = entry.availability.available,
                reason = entry.availability.reason?.let { app.getString(it) },
            )
        }
        labels = options.associate { it.choice to it.label }
        return options
    }

    override fun labelFor(choice: InstallerChoice): String? = labels[choice]
}

/** [OruPackageReadings] over PM and SessionInstaller. */
class AppOruPackageReadings(
    private val pm: PM,
    private val sessionInstaller: SessionInstaller,
) : OruPackageReadings {

    override suspend fun isDowngrade(packageName: String, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            // Same comparison as InstallViewModel.install (version code of the archive against the installed app)
            val archive = pm.getPackageInfo(outputFile) ?: return@withContext false
            val installed = pm.getPackageInfo(packageName) ?: return@withContext false
            pm.getVersionCode(archive) < pm.getVersionCode(installed)
        }

    override suspend fun silentUpdateAllowed(packageName: String): Boolean =
        sessionInstaller.canUpdateSilently(packageName)

    override suspend fun installPermissionGranted(): Boolean = withContext(Dispatchers.IO) {
        pm.canInstallPackages()
    }

    override suspend fun archivePackageName(file: File): String? = withContext(Dispatchers.IO) {
        // An archive that cannot be parsed has no name; the caller falls back to the stock package
        runCatching { pm.getPackageInfo(file)?.packageName }.getOrNull()
    }

    override suspend fun installedLabel(packageName: String): String? = withContext(Dispatchers.IO) {
        pm.getPackageInfo(packageName)?.let { with(pm) { it.label() } }
    }
}

/**
 * [OruInstallExecutor] over the existing InstallViewModel. Every method is one call to that
 * ViewModel; the ViewModel still decides, checks and installs.
 */
class ViewModelOruInstallExecutor(
    private val viewModel: InstallViewModel,
    private val installerManager: InstallerManager,
) : OruInstallExecutor {

    override val execution: OruExecution
        get() = when (val state = viewModel.installState) {
            InstallViewModel.InstallState.Ready -> OruExecution.Idle
            is InstallViewModel.InstallState.Installing -> OruExecution.Running
            is InstallViewModel.InstallState.Installed -> OruExecution.Installed(state.packageName)
            is InstallViewModel.InstallState.Conflict -> OruExecution.Conflict
            is InstallViewModel.InstallState.Error -> OruExecution.Failed(state.message)
        }

    override fun startInstall(request: OruInstallRequest, autoUninstallOnConflict: Boolean) {
        viewModel.install(
            outputFile = request.outputFile,
            originalPackageName = request.packageName,
            onPersistApp = request.persist,
            autoUninstallOnConflict = autoUninstallOnConflict,
        )
    }

    override fun startWithPicked(token: String) {
        viewModel.proceedWithSelectedInstaller(installerManager.parseToken(token))
    }

    override fun dismissPicker() {
        viewModel.dismissInstallerSelectionDialog()
    }

    override fun installWithAndroid() {
        viewModel.proceedWithFallbackInstaller()
    }

    override fun uninstallAndInstall(packageName: String) {
        viewModel.requestUninstall(packageName, installAfterUninstall = true)
    }

    override fun installIgnoringSignatureMismatch() {
        viewModel.installIgnoringSignatureMismatch()
    }

    override fun openInstallerApp(): Boolean = viewModel.openShizukuApp()

    override fun requestShizukuPermission(): Boolean = viewModel.requestShizukuPermission()

    override fun openApp() {
        viewModel.openApp()
    }

    override fun resetExecution() {
        viewModel.resetInstallState()
    }

    override fun stopWaiting() {
        // The state the ViewModel holds when the stop lands: a stop on anything but Installing does nothing there
        Log.i(
            InstallWaitPolicy.LOG_TAG,
            "step=stop-waiting-ui installing=${viewModel.installState is InstallViewModel.InstallState.Installing}"
        )
        viewModel.stopWaiting()
    }
}
