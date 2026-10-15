/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import app.morphe.manager.ui.oru.install.InstallStep.AskInstaller
import app.morphe.manager.ui.oru.install.InstallStep.AutoInstall
import app.morphe.manager.ui.oru.install.InstallStep.ConflictNeedsUninstall
import app.morphe.manager.ui.oru.install.InstallStep.InstallerUnavailable
import app.morphe.manager.ui.oru.install.InstallStep.NeedsPermission
import app.morphe.manager.ui.oru.install.InstallStep.RootPatchMode
import app.morphe.manager.ui.oru.install.InstallStep.ShowInstall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruInstallPlannerTest {

    private val stockHashes = setOf("STOCK")
    private val oruHashes = setOf("ORU")

    /** A phone with the system installer, the install permission, no conflict and no Shizuku. */
    private val base = InstallPlanInput(
        primary = InstallerChoice.SYSTEM,
        shizuku = ShizukuState.NOT_INSTALLED,
        rootAccessGranted = false,
        deviceRooted = false,
        rootModeChosen = true,
        customInstallerAvailable = true,
        autoInstallPref = false,
        promptInstallerPref = false,
        autoUninstallPref = false,
        silentUpdateAllowed = false,
        installPermissionGranted = true,
        permissionExplained = true,
        signature = SignatureCheck.NoConflict
    )

    private val conflict = SignatureCheck.WillConflict(stockHashes, oruHashes)

    private fun plan(input: InstallPlanInput) = OruInstallPlanner.plan(input)

    private data class Case(val name: String, val input: InstallPlanInput, val expected: InstallStep)

    private val cases = listOf(
        // System installer and the standard path
        Case(
            "system installer, no conflict, one tap, not silent",
            base,
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),
        Case(
            "system installer updated silently, auto off: one tap, silent",
            base.copy(silentUpdateAllowed = true),
            ShowInstall(InstallerChoice.SYSTEM, silent = true, fallbackFrom = null)
        ),
        Case(
            "system installer updated silently, auto on: starts without a tap",
            base.copy(silentUpdateAllowed = true, autoInstallPref = true),
            AutoInstall(InstallerChoice.SYSTEM)
        ),
        Case(
            "system installer, auto on, but not silent: one tap",
            base.copy(autoInstallPref = true, silentUpdateAllowed = false),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),
        Case(
            "system installer needs install-unknown-apps, first time: explain",
            base.copy(installPermissionGranted = false, permissionExplained = false),
            NeedsPermission(explainFirst = true)
        ),
        Case(
            "system installer needs install-unknown-apps, explained before: no explainer",
            base.copy(installPermissionGranted = false, permissionExplained = true),
            NeedsPermission(explainFirst = false)
        ),
        Case(
            "silent system install still needs the permission, so auto does not start",
            base.copy(
                silentUpdateAllowed = true,
                autoInstallPref = true,
                installPermissionGranted = false,
                permissionExplained = false
            ),
            NeedsPermission(explainFirst = true)
        ),

        // Shizuku states, in the order SessionInstaller reports them
        Case(
            "Shizuku ready, auto on: starts silently without a tap",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.READY, autoInstallPref = true),
            AutoInstall(InstallerChoice.SHIZUKU)
        ),
        Case(
            "Shizuku ready, auto off: one tap, silent",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.READY),
            ShowInstall(InstallerChoice.SHIZUKU, silent = true, fallbackFrom = null)
        ),
        Case(
            "Shizuku ready with Play Store spoof, auto on: starts silently",
            base.copy(
                primary = InstallerChoice.SHIZUKU_PLAY_STORE,
                shizuku = ShizukuState.READY,
                autoInstallPref = true
            ),
            AutoInstall(InstallerChoice.SHIZUKU_PLAY_STORE)
        ),
        Case(
            "Shizuku not installed, auto on: unavailable inline, not a silent start",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.NOT_INSTALLED, autoInstallPref = true),
            InstallerUnavailable(InstallerChoice.SHIZUKU, UnavailableReason.SHIZUKU_NOT_INSTALLED, UnavailableAction.OPEN_INSTALLER)
        ),
        Case(
            "Shizuku unsupported version: open action",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.UNSUPPORTED),
            InstallerUnavailable(InstallerChoice.SHIZUKU, UnavailableReason.SHIZUKU_UNSUPPORTED, UnavailableAction.OPEN_INSTALLER)
        ),
        Case(
            "Shizuku not running: open action",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.NOT_RUNNING),
            InstallerUnavailable(InstallerChoice.SHIZUKU, UnavailableReason.SHIZUKU_NOT_RUNNING, UnavailableAction.OPEN_INSTALLER)
        ),
        Case(
            "Shizuku without Oru's permission: grant action",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.PERMISSION_MISSING),
            InstallerUnavailable(InstallerChoice.SHIZUKU, UnavailableReason.SHIZUKU_PERMISSION_MISSING, UnavailableAction.GRANT_PERMISSION)
        ),
        Case(
            "Shizuku primary never falls back silently, even with the system installer ready",
            base.copy(primary = InstallerChoice.SHIZUKU_PLAY_STORE, shizuku = ShizukuState.NOT_RUNNING),
            InstallerUnavailable(InstallerChoice.SHIZUKU_PLAY_STORE, UnavailableReason.SHIZUKU_NOT_RUNNING, UnavailableAction.OPEN_INSTALLER)
        ),
        Case(
            "Shizuku does not need install-unknown-apps",
            base.copy(
                primary = InstallerChoice.SHIZUKU,
                shizuku = ShizukuState.READY,
                installPermissionGranted = false,
                permissionExplained = false
            ),
            ShowInstall(InstallerChoice.SHIZUKU, silent = true, fallbackFrom = null)
        ),

        // Root
        Case(
            "root mount, granted: one tap, silent",
            base.copy(primary = InstallerChoice.ROOT, rootAccessGranted = true),
            ShowInstall(InstallerChoice.ROOT, silent = true, fallbackFrom = null)
        ),
        Case(
            "root mount, auto on: root never auto-starts",
            base.copy(primary = InstallerChoice.ROOT, rootAccessGranted = true, autoInstallPref = true),
            ShowInstall(InstallerChoice.ROOT, silent = true, fallbackFrom = null)
        ),
        Case(
            "root mount, not granted: unavailable with no open action",
            base.copy(primary = InstallerChoice.ROOT, rootAccessGranted = false),
            InstallerUnavailable(InstallerChoice.ROOT, UnavailableReason.ROOT_REQUIRED, UnavailableAction.NONE)
        ),
        Case(
            "root with Play Store spoof, not granted: unavailable",
            base.copy(primary = InstallerChoice.ROOT_PLAY_STORE, rootAccessGranted = false),
            InstallerUnavailable(InstallerChoice.ROOT_PLAY_STORE, UnavailableReason.ROOT_REQUIRED, UnavailableAction.NONE)
        ),
        Case(
            "rooted device, root mode not chosen: ask before the run",
            base.copy(deviceRooted = true, rootModeChosen = false, rootAccessGranted = true),
            RootPatchMode
        ),
        Case(
            "rooted device, root mode chosen as standard: normal install",
            base.copy(deviceRooted = true, rootModeChosen = true, rootAccessGranted = true),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),
        Case(
            "not rooted, root mode never asked",
            base.copy(deviceRooted = false, rootModeChosen = false),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),

        // Custom installers and :none: fall back silently today; Oru names the fallback
        Case(
            "custom installer available: uses it, not silent",
            base.copy(primary = InstallerChoice.CUSTOM, customInstallerAvailable = true),
            ShowInstall(InstallerChoice.CUSTOM, silent = false, fallbackFrom = null)
        ),
        Case(
            "custom installer gone: falls back to the system installer and says so",
            base.copy(primary = InstallerChoice.CUSTOM, customInstallerAvailable = false),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = InstallerChoice.CUSTOM)
        ),
        Case(
            "custom installer gone, auto on: the fallback never auto-starts",
            base.copy(
                primary = InstallerChoice.CUSTOM,
                customInstallerAvailable = false,
                autoInstallPref = true,
                silentUpdateAllowed = true
            ),
            ShowInstall(InstallerChoice.SYSTEM, silent = true, fallbackFrom = InstallerChoice.CUSTOM)
        ),
        Case(
            "custom installer gone and permission missing: the fallback is a system install, so it is gated",
            base.copy(
                primary = InstallerChoice.CUSTOM,
                customInstallerAvailable = false,
                installPermissionGranted = false,
                permissionExplained = false
            ),
            NeedsPermission(explainFirst = true)
        ),
        Case(
            "custom installer available and permission missing: no permission needed",
            base.copy(
                primary = InstallerChoice.CUSTOM,
                customInstallerAvailable = true,
                installPermissionGranted = false
            ),
            ShowInstall(InstallerChoice.CUSTOM, silent = false, fallbackFrom = null)
        ),
        Case(
            "no installer chosen falls back to the system installer",
            base.copy(primary = InstallerChoice.NONE),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = InstallerChoice.NONE)
        ),
        Case(
            "Play Store spoof needs install-unknown-apps",
            base.copy(primary = InstallerChoice.PLAY_STORE, installPermissionGranted = false, permissionExplained = false),
            NeedsPermission(explainFirst = true)
        ),
        Case(
            "Play Store spoof with permission: one tap, not silent, never auto",
            base.copy(primary = InstallerChoice.PLAY_STORE, autoInstallPref = true, silentUpdateAllowed = true),
            ShowInstall(InstallerChoice.PLAY_STORE, silent = false, fallbackFrom = null)
        ),

        // Picker
        Case(
            "picker on with no pick: ask first, before any conflict",
            base.copy(promptInstallerPref = true, signature = conflict, shizuku = ShizukuState.READY),
            AskInstaller
        ),
        Case(
            "picker on with a pick: the pick is used and auto stays off",
            base.copy(
                promptInstallerPref = true,
                pickedInstaller = InstallerChoice.SHIZUKU,
                primary = InstallerChoice.SYSTEM,
                shizuku = ShizukuState.READY,
                autoInstallPref = true
            ),
            ShowInstall(InstallerChoice.SHIZUKU, silent = true, fallbackFrom = null)
        ),
        Case(
            "picker on with a pick of root: uses the pick, not the primary",
            base.copy(
                promptInstallerPref = true,
                pickedInstaller = InstallerChoice.ROOT,
                rootAccessGranted = true
            ),
            ShowInstall(InstallerChoice.ROOT, silent = true, fallbackFrom = null)
        ),
        Case(
            "picker on, auto on: auto stays off so the picker shows",
            base.copy(promptInstallerPref = true, autoInstallPref = true, silentUpdateAllowed = true),
            AskInstaller
        ),

        // Signature conflicts (C7 meaning b: the installed app has another key)
        Case(
            "signature conflict, manual install: uninstall with the data-loss prompt",
            base.copy(signature = conflict),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict, no key yet: same conflict",
            base.copy(signature = SignatureCheck.UnknownNoKeystore(stockHashes)),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict on rooted device with root access: Ignore is offered",
            base.copy(signature = conflict, rootAccessGranted = true, deviceRooted = true, rootModeChosen = true),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = true)
        ),
        Case(
            "signature conflict, auto with Shizuku primary, auto-uninstall on and Shizuku ready: silent uninstall",
            base.copy(
                primary = InstallerChoice.SHIZUKU,
                shizuku = ShizukuState.READY,
                autoInstallPref = true,
                autoUninstallPref = true,
                signature = conflict
            ),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = true, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict, auto with Shizuku, auto-uninstall off: asks",
            base.copy(
                primary = InstallerChoice.SHIZUKU,
                shizuku = ShizukuState.READY,
                autoInstallPref = true,
                autoUninstallPref = false,
                signature = conflict
            ),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict, auto-uninstall on but Shizuku not ready: asks, and no silent uninstall",
            base.copy(
                primary = InstallerChoice.SHIZUKU,
                shizuku = ShizukuState.NOT_RUNNING,
                autoInstallPref = true,
                autoUninstallPref = true,
                signature = conflict
            ),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict, auto-uninstall on, Shizuku ready, but manual install (auto off): asks",
            base.copy(
                primary = InstallerChoice.SHIZUKU,
                shizuku = ShizukuState.READY,
                autoInstallPref = false,
                autoUninstallPref = true,
                signature = conflict
            ),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict, auto with the system installer: no silent uninstall (only Shizuku removes silently)",
            base.copy(
                silentUpdateAllowed = true,
                autoInstallPref = true,
                autoUninstallPref = true,
                signature = conflict
            ),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "signature conflict already bypassed by a root retry: no conflict, install proceeds",
            base.copy(signature = conflict, signatureCheckBypassed = true),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),
        Case(
            "signature conflict on a root retry does not offer Ignore again",
            base.copy(signature = conflict, rootAccessGranted = true, signatureCheckBypassed = true),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),
        Case(
            "installed signature unreadable: no conflict (same as PM.hasSignatureMismatch)",
            base.copy(signature = SignatureCheck.UnknownInstalledSignature),
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)
        ),
        Case(
            "signature conflict and Shizuku unavailable: the conflict comes first",
            base.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.NOT_INSTALLED, signature = conflict),
            ConflictNeedsUninstall(ConflictReason.SIGNATURE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),

        // Downgrades use the same conflict state but never the silent uninstall
        Case(
            "downgrade: conflict, data loss, no silent uninstall even with Shizuku auto",
            base.copy(
                downgrade = true,
                primary = InstallerChoice.SHIZUKU,
                shizuku = ShizukuState.READY,
                autoInstallPref = true,
                autoUninstallPref = true
            ),
            ConflictNeedsUninstall(ConflictReason.DOWNGRADE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "downgrade on rooted device: no Ignore offered (version, not certificates)",
            base.copy(downgrade = true, rootAccessGranted = true),
            ConflictNeedsUninstall(ConflictReason.DOWNGRADE, dataLoss = true, canUninstallSilently = false, canIgnoreSignatureMismatch = false)
        ),
        Case(
            "downgrade with picker on: picker first, then the conflict",
            base.copy(downgrade = true, promptInstallerPref = true),
            AskInstaller
        ),
        Case(
            "rooted device with root mode unanswered: root question before everything",
            base.copy(deviceRooted = true, rootModeChosen = false, promptInstallerPref = true, signature = conflict),
            RootPatchMode
        )
    )

    @Test
    fun everyTableCaseMatchesItsExpectedStep() {
        val failures = cases.mapNotNull { case ->
            val actual = plan(case.input)
            if (actual == case.expected) null else "${case.name}: expected ${case.expected}, got $actual"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun everyConflictOutcomeReportsDataLoss() {
        // Each conflict erases the installed app's data, so the copy must always be able to say so
        cases.map { plan(it.input) }
            .filterIsInstance<ConflictNeedsUninstall>()
            .forEach { assertTrue(it.dataLoss) }
    }

    @Test
    fun silentUninstallNeverWithoutShizukuAutoStart() {
        // Only the Shizuku auto path can remove the app without a prompt
        cases.filter { plan(it.input) is ConflictNeedsUninstall }
            .filter { (plan(it.input) as ConflictNeedsUninstall).canUninstallSilently }
            .forEach { case ->
                assertTrue(case.input.autoInstallPref, case.name)
                assertTrue(case.input.autoUninstallPref, case.name)
                assertTrue(case.input.primary == InstallerChoice.SHIZUKU || case.input.primary == InstallerChoice.SHIZUKU_PLAY_STORE, case.name)
                assertEquals(ShizukuState.READY, case.input.shizuku, case.name)
            }
    }

    @Test
    fun decidedAutoInstallRuleStartsWithoutATapUnlessAskedFirst() {
        // The adapter feeds the planner this constant in place of a stored switch (SETTINGS_IA row 18)
        assertTrue(OruInstallPlanner.AUTO_INSTALL_PREF)
        val decided = base.copy(autoInstallPref = OruInstallPlanner.AUTO_INSTALL_PREF)

        assertEquals(AutoInstall(InstallerChoice.SYSTEM), plan(decided.copy(silentUpdateAllowed = true)))
        assertEquals(
            AutoInstall(InstallerChoice.SHIZUKU),
            plan(decided.copy(primary = InstallerChoice.SHIZUKU, shizuku = ShizukuState.READY))
        )
        assertEquals(
            ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null),
            plan(decided)
        )
        assertEquals(AskInstaller, plan(decided.copy(promptInstallerPref = true, silentUpdateAllowed = true)))
    }

    @Test
    fun autoStartRuleMatchesTheInstallerManagerRule() {
        assertFalse(OruInstallPlanner.autoStartAllowed(base.copy(primary = InstallerChoice.SHIZUKU)))
        assertTrue(OruInstallPlanner.autoStartAllowed(base.copy(primary = InstallerChoice.SHIZUKU, autoInstallPref = true)))
        assertFalse(
            OruInstallPlanner.autoStartAllowed(
                base.copy(primary = InstallerChoice.SHIZUKU, autoInstallPref = true, promptInstallerPref = true)
            )
        )
        assertFalse(OruInstallPlanner.autoStartAllowed(base.copy(autoInstallPref = true, silentUpdateAllowed = false)))
        assertTrue(OruInstallPlanner.autoStartAllowed(base.copy(autoInstallPref = true, silentUpdateAllowed = true)))
        assertFalse(
            OruInstallPlanner.autoStartAllowed(
                base.copy(primary = InstallerChoice.ROOT, autoInstallPref = true, rootAccessGranted = true)
            )
        )
        assertFalse(
            OruInstallPlanner.autoStartAllowed(
                base.copy(primary = InstallerChoice.CUSTOM, autoInstallPref = true, silentUpdateAllowed = true)
            )
        )
    }
}
