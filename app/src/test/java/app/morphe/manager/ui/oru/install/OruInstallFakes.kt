/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import java.io.File

/** Installer settings and status the test controls. Edit the vars between steps to simulate the phone changing. */
internal class FakeInstallerReadings(
    var primary: InstallerChoice = InstallerChoice.SYSTEM,
    var primaryTokenValue: String = "internal",
    var prompt: Boolean = false,
    var autoInstall: Boolean = false,
    var autoUninstall: Boolean = false,
    var shizuku: ShizukuState = ShizukuState.READY,
    var root: Boolean = false,
    var rooted: Boolean = false,
    var options: List<OruInstallerOption> = emptyList(),
) : OruInstallerReadings {

    override suspend fun primaryChoice(): InstallerChoice = primary
    override suspend fun primaryToken(): String = primaryTokenValue
    override suspend fun promptInstallerPref(): Boolean = prompt
    override suspend fun autoInstallPref(): Boolean = autoInstall
    override suspend fun autoUninstallPref(): Boolean = autoUninstall
    override suspend fun shizukuState(): ShizukuState = shizuku
    override suspend fun rootAccessGranted(): Boolean = root
    override suspend fun deviceRooted(): Boolean = rooted
    override suspend fun customInstallerAvailable(token: String): Boolean = true
    override suspend fun pickerOptions(): List<OruInstallerOption> = options
    override fun labelFor(choice: InstallerChoice): String? = choice.name.lowercase()
}

/** Package reads the test controls. */
internal class FakePackages(
    var downgrade: Boolean = false,
    var silent: Boolean = false,
    var permission: Boolean = true,
    var archivePackage: String? = null,
    var label: String? = null,
) : OruPackageReadings {

    override suspend fun isDowngrade(packageName: String, outputFile: File): Boolean = downgrade
    override suspend fun silentUpdateAllowed(packageName: String): Boolean = silent
    override suspend fun installPermissionGranted(): Boolean = permission
    override suspend fun archivePackageName(file: File): String? = archivePackage
    override suspend fun installedLabel(packageName: String): String? = label
}

/** Signature reads. The installed app is absent by default, so there is no conflict. */
internal class FakeSignatureSource(
    var installed: Boolean = false,
    var installedHashes: Set<String> = setOf("installed-key"),
    var oruHashes: Set<String> = setOf("oru-key"),
) : SignatureSource {

    override fun isInstalled(packageName: String): Boolean = installed
    override fun installedSignatureHashes(packageName: String): Set<String> =
        if (installed) installedHashes else emptySet()

    override suspend fun oruKeyHashes(): Set<String> = oruHashes
}

/** Records each executor call as a short string, in order. */
internal class FakeExecutor(
    override var execution: OruExecution = OruExecution.Idle,
) : OruInstallExecutor {

    val calls = mutableListOf<String>()

    override fun startInstall(request: OruInstallRequest, autoUninstallOnConflict: Boolean) {
        calls += "startInstall:$autoUninstallOnConflict"
    }

    override fun startWithPicked(token: String) {
        calls += "startWithPicked:$token"
    }

    override fun dismissPicker() {
        calls += "dismissPicker"
    }

    override fun installWithAndroid() {
        calls += "installWithAndroid"
    }

    override fun uninstallAndInstall(packageName: String) {
        calls += "uninstallAndInstall:$packageName"
    }

    override fun installIgnoringSignatureMismatch() {
        calls += "installIgnoringSignatureMismatch"
    }

    override fun openInstallerApp(): Boolean {
        calls += "openInstallerApp"
        return true
    }

    override fun requestShizukuPermission(): Boolean {
        calls += "requestShizukuPermission"
        return true
    }

    override fun openApp() {
        calls += "openApp"
    }

    override fun resetExecution() {
        calls += "resetExecution"
        execution = OruExecution.Idle
    }

    override fun stopWaiting() {
        calls += "stopWaiting"
        execution = OruExecution.Idle
    }
}

internal fun testRequest(): OruInstallRequest = OruInstallRequest(
    outputFile = File("patched.apk"),
    packageName = "app.pkg",
    installPackageName = "app.pkg",
    persist = { _, _ -> true },
)
