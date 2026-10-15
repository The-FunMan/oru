/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import app.morphe.manager.data.room.apps.installed.InstallType
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruInstallCoordinatorTest {

    private val shizukuOption = OruInstallerOption(
        choice = InstallerChoice.SHIZUKU,
        token = "shizuku",
        label = "Shizuku",
        enabled = true,
    )

    private fun coordinator(
        readings: FakeInstallerReadings = FakeInstallerReadings(),
        packages: FakePackages = FakePackages(),
        signatures: FakeSignatureSource = FakeSignatureSource(),
        executor: FakeExecutor = FakeExecutor(),
        onRoot: (Boolean) -> Unit = {},
    ) = OruInstallCoordinator(
        request = testRequest(),
        installers = readings,
        packages = packages,
        signatures = signatures,
        executor = executor,
        onRootModeChosen = onRoot,
    )

    private val persist: suspend (String, InstallType) -> Boolean = { _, _ -> true }

    @Test
    fun requestTakesThePackageOfTheArchive() = runBlocking {
        val request = oruInstallRequest(
            File("patched.apk"), "com.stock", FakePackages(archivePackage = "app.patched"), persist,
        )
        assertEquals("com.stock", request.packageName)
        assertEquals("app.patched", request.installPackageName)
    }

    @Test
    fun requestFallsBackToTheStockPackage() = runBlocking {
        for (unreadable in listOf(null, "", "  ")) {
            val request = oruInstallRequest(
                File("patched.apk"), "com.stock", FakePackages(archivePackage = unreadable), persist,
            )
            assertEquals("com.stock", request.installPackageName)
        }
    }

    @Test
    fun conflictThenUninstallThenReplan() = runBlocking {
        val signatures = FakeSignatureSource(installed = true)
        val executor = FakeExecutor()
        val sut = coordinator(signatures = signatures, executor = executor)

        sut.refresh()
        assertTrue(sut.state.value.plan is InstallStep.ConflictNeedsUninstall)
        assertFalse(sut.state.value.decisionOpen)

        sut.reviewConflict()
        assertTrue(sut.state.value.decisionOpen)

        sut.confirmUninstall()
        assertEquals(listOf("uninstallAndInstall:app.pkg"), executor.calls)
        assertFalse(sut.state.value.decisionOpen)

        // The package is gone: the next plan has no conflict, and the install runs on tap
        signatures.installed = false
        sut.onPackageRemoved()
        assertEquals(
            InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null),
            sut.state.value.plan
        )
        sut.install()
        assertEquals(listOf("uninstallAndInstall:app.pkg", "startInstall:false"), executor.calls)
    }

    @Test
    fun installRefusesWhenAConflictAppearedAfterThePlan() = runBlocking {
        val signatures = FakeSignatureSource(installed = false)
        val executor = FakeExecutor()
        val sut = coordinator(signatures = signatures, executor = executor)

        sut.refresh()
        assertTrue(sut.state.value.plan is InstallStep.ShowInstall)

        signatures.installed = true
        sut.install()

        assertTrue(sut.state.value.plan is InstallStep.ConflictNeedsUninstall)
        assertTrue(executor.calls.isEmpty())
    }

    @Test
    fun executorConflictBecomesTheDecisionInsteadOfARetryLoop() = runBlocking {
        val executor = FakeExecutor(execution = OruExecution.Conflict)
        val sut = coordinator(executor = executor)

        sut.refresh()

        assertTrue(sut.state.value.plan is InstallStep.ConflictNeedsUninstall)
        assertTrue(sut.state.value.ui is OruInstallUi.Plan)
    }

    @Test
    fun pickerChoiceFeedsThePlanAndTheInstallUsesIt() = runBlocking {
        val systemOption = OruInstallerOption(
            choice = InstallerChoice.SYSTEM,
            token = "internal",
            label = "Android",
            enabled = true,
        )
        val readings = FakeInstallerReadings(
            prompt = true,
            shizuku = ShizukuState.READY,
            options = listOf(shizukuOption, systemOption),
        )
        val executor = FakeExecutor()
        val sut = coordinator(readings = readings, executor = executor)

        sut.start()
        assertEquals(InstallStep.AskInstaller, sut.state.value.plan)
        val ui = sut.state.value.ui
        assertTrue(ui is OruInstallUi.Plan && ui.options.size == 2)
        // The executor is seeded once, so the picked installer can run through the ViewModel
        assertEquals(listOf("startInstall:false"), executor.calls)

        sut.pickInstaller(shizukuOption)
        assertEquals(
            InstallStep.ShowInstall(InstallerChoice.SHIZUKU, silent = true, fallbackFrom = null),
            sut.state.value.plan
        )
        // Picking does not install; the tap does, with the picked token
        assertEquals(listOf("startInstall:false"), executor.calls)

        sut.install()
        assertEquals(listOf("startInstall:false", "startWithPicked:shizuku"), executor.calls)
    }

    @Test
    fun disabledPickerRowsDoNothing() = runBlocking {
        val disabled = shizukuOption.copy(enabled = false, reason = "Not running")
        val readings = FakeInstallerReadings(prompt = true, options = listOf(disabled))
        val sut = coordinator(readings = readings)

        sut.start()
        sut.pickInstaller(disabled)

        assertEquals(InstallStep.AskInstaller, sut.state.value.plan)
    }

    @Test
    fun permissionThenInstall() = runBlocking {
        val packages = FakePackages(permission = false)
        val executor = FakeExecutor()
        val sut = coordinator(packages = packages, executor = executor)

        sut.start()
        assertEquals(InstallStep.NeedsPermission(explainFirst = true), sut.state.value.plan)
        assertTrue(executor.calls.isEmpty())

        sut.onPermissionExplained()
        assertEquals(InstallStep.NeedsPermission(explainFirst = false), sut.state.value.plan)

        packages.permission = true
        sut.refresh()
        assertEquals(
            InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null),
            sut.state.value.plan
        )

        sut.install()
        assertEquals(listOf("startInstall:false"), executor.calls)
    }

    @Test
    fun unavailableThenFixed() = runBlocking {
        val readings = FakeInstallerReadings(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.NOT_RUNNING)
        val executor = FakeExecutor()
        val sut = coordinator(readings = readings, executor = executor)

        sut.start()
        assertEquals(
            InstallStep.InstallerUnavailable(
                installer = InstallerChoice.SHIZUKU,
                reason = UnavailableReason.SHIZUKU_NOT_RUNNING,
                action = UnavailableAction.OPEN_INSTALLER
            ),
            sut.state.value.plan
        )
        // Seeded so that "Install with Android" has the request
        assertEquals(listOf("startInstall:false"), executor.calls)

        sut.openInstallerApp()
        readings.shizuku = ShizukuState.READY
        sut.refresh()
        assertEquals(
            InstallStep.ShowInstall(InstallerChoice.SHIZUKU, silent = true, fallbackFrom = null),
            sut.state.value.plan
        )

        sut.install()
        assertEquals(
            listOf("startInstall:false", "openInstallerApp", "startInstall:false"),
            executor.calls
        )
    }

    @Test
    fun installWithAndroidIsTheExplicitFallback() = runBlocking {
        val readings = FakeInstallerReadings(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.NOT_INSTALLED)
        val executor = FakeExecutor()
        val sut = coordinator(readings = readings, executor = executor)

        sut.start()
        sut.installWithAndroid()

        assertEquals(listOf("startInstall:false", "installWithAndroid"), executor.calls)
    }

    @Test
    fun rootModeIsItsOwnStepUntilAnswered() = runBlocking {
        val readings = FakeInstallerReadings(rooted = true)
        var chosen: Boolean? = null
        val sut = coordinator(readings = readings, onRoot = { chosen = it })

        sut.start()
        assertEquals(InstallStep.RootPatchMode, sut.state.value.plan)

        sut.chooseRootMode(useMount = true)

        assertEquals(true, chosen)
        assertEquals(
            InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null),
            sut.state.value.plan
        )
    }

    @Test
    fun failedInstallRetriesAfterResettingTheExecutor() = runBlocking {
        val executor = FakeExecutor(execution = OruExecution.Failed("Android stopped the install"))
        val sut = coordinator(executor = executor)

        sut.refresh()
        assertEquals(OruInstallUi.Failed("Android stopped the install"), sut.state.value.ui)

        sut.retry()

        assertEquals(listOf("resetExecution", "startInstall:false"), executor.calls)
    }

    @Test
    fun autoInstallStartsOnceAndNotAgainAfterItFinished() = runBlocking {
        val readings = FakeInstallerReadings(primary = InstallerChoice.SHIZUKU, autoInstall = true)
        val executor = FakeExecutor()
        val sut = coordinator(readings = readings, executor = executor)

        sut.start()
        // The automatic start is the only install, and it runs the same way as the auto-uninstall path
        assertTrue(sut.state.value.plan is InstallStep.AutoInstall)
        assertEquals(listOf("startInstall:true"), executor.calls)

        // Once the executor reports the install done, a second start must not install again
        executor.execution = OruExecution.Installed("app.pkg")
        sut.start()

        assertEquals(listOf("startInstall:true"), executor.calls)
    }

    @Test
    fun closeClearsThePickerFlag() = runBlocking {
        val executor = FakeExecutor()
        val sut = coordinator(executor = executor)

        sut.close()

        assertEquals(listOf("dismissPicker"), executor.calls)
    }

    @Test
    fun stoppingTheWaitHandsTheExecutorBackAndReplans() = runBlocking {
        val executor = FakeExecutor(execution = OruExecution.Running)
        val sut = coordinator(executor = executor)

        sut.refresh()
        sut.stopWaiting()

        // The executor is told to cancel the wait, and the plan is shown again so Try again works
        assertEquals(listOf("stopWaiting"), executor.calls)
        assertTrue(sut.state.value.plan is InstallStep.ShowInstall)
        assertTrue(sut.state.value.ui is OruInstallUi.Plan)
    }
}
