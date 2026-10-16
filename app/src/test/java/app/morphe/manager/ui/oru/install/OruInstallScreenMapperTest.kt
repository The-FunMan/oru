/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruHeroState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruInstallScreenMapperTest {

    private val labels: (InstallerChoice) -> String? = { choice ->
        when (choice) {
            InstallerChoice.SYSTEM -> "Android"
            InstallerChoice.SHIZUKU -> "Shizuku"
            else -> null
        }
    }

    private val show = InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = null)

    @Test
    fun noPlanYetIsChecking() {
        val ui = OruInstallScreenMapper.map(null, OruExecution.Idle, emptyList(), labels)
        assertEquals(OruInstallUi.Checking, ui)
    }

    @Test
    fun showInstallIsAPlanWithItsInstallerLabel() {
        val ui = OruInstallScreenMapper.map(show, OruExecution.Idle, emptyList(), labels)
        assertEquals(
            OruInstallUi.Plan(step = show, installerLabel = "Android", fallbackLabel = null, options = emptyList()),
            ui
        )
    }

    @Test
    fun fallbackNamesThePreferredInstaller() {
        val fallback = InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = InstallerChoice.SHIZUKU)
        val ui = OruInstallScreenMapper.map(fallback, OruExecution.Idle, emptyList(), labels)
        assertTrue(ui is OruInstallUi.Plan && ui.fallbackLabel == "Shizuku")
    }

    @Test
    fun autoInstallWhileIdleIsTheAutomaticState() {
        val auto = InstallStep.AutoInstall(InstallerChoice.SHIZUKU)
        val ui = OruInstallScreenMapper.map(auto, OruExecution.Idle, emptyList(), labels)
        assertEquals(OruInstallUi.Installing(automatic = true, installerLabel = "Shizuku"), ui)
    }

    @Test
    fun declinedPlanKeepsTheInstallAndSaysSo() {
        val ui = OruInstallScreenMapper.map(show, OruExecution.Idle, emptyList(), labels, declined = true)
        assertEquals(
            OruInstallUi.Plan(step = show, installerLabel = "Android", fallbackLabel = null, options = emptyList(), declined = true),
            ui
        )
    }

    @Test
    fun automaticRunIsAutomaticEvenAfterItsPlanAsksForATap() {
        assertEquals(
            OruInstallUi.Installing(automatic = true, installerLabel = "Android"),
            OruInstallScreenMapper.map(show, OruExecution.Running, emptyList(), labels, automatic = true)
        )
    }

    @Test
    fun runningShowsInstallingWithoutAutomaticUnlessItStartedAutomatically() {
        assertEquals(
            OruInstallUi.Installing(automatic = false, installerLabel = "Android"),
            OruInstallScreenMapper.map(show, OruExecution.Running, emptyList(), labels)
        )
        assertEquals(
            OruInstallUi.Installing(automatic = true, installerLabel = "Shizuku"),
            OruInstallScreenMapper.map(
                InstallStep.AutoInstall(InstallerChoice.SHIZUKU), OruExecution.Running, emptyList(), labels
            )
        )
    }

    @Test
    fun installedNamesTheInstaller() {
        val ui = OruInstallScreenMapper.map(show, OruExecution.Installed("app.pkg"), emptyList(), labels)
        assertEquals(OruInstallUi.Installed(installerLabel = "Android"), ui)
    }

    @Test
    fun failedKeepsTheExecutorMessage() {
        val ui = OruInstallScreenMapper.map(show, OruExecution.Failed("Android stopped the install"), emptyList(), labels)
        assertEquals(OruInstallUi.Failed("Android stopped the install"), ui)
    }

    @Test
    fun conflictExecutionKeepsThePlanSoTheDecisionIsOffered() {
        val conflict = InstallStep.ConflictNeedsUninstall(
            reason = ConflictReason.SIGNATURE,
            dataLoss = true,
            canUninstallSilently = false,
            canIgnoreSignatureMismatch = false,
        )
        val ui = OruInstallScreenMapper.map(conflict, OruExecution.Conflict, emptyList(), labels)
        assertTrue(ui is OruInstallUi.Plan && ui.step == conflict)
    }

    @Test
    fun heroStateFollowsTheUi() {
        // Ready to install is not done: the run's hexagon waits for the install, and only an installed run shows its check
        assertEquals(OruHeroState.Working, OruInstallScreenMapper.heroState(OruInstallUi.Checking))
        assertEquals(OruHeroState.Working, OruInstallScreenMapper.heroState(OruInstallUi.Plan(show, null, null, emptyList())))
        assertEquals(OruHeroState.Working, OruInstallScreenMapper.heroState(OruInstallUi.Installing(false, null)))
        assertEquals(OruHeroState.Done, OruInstallScreenMapper.heroState(OruInstallUi.Installed(null)))
        assertEquals(OruHeroState.Failed, OruInstallScreenMapper.heroState(OruInstallUi.Failed("x")))
    }

    @Test
    fun theHexagonHoldsWhilePlannedFillsWhileInstallingAndIsWholeOnceInstalled() {
        val runFill = 0.9f
        assertEquals(runFill, OruInstallScreenMapper.fill(OruInstallUi.Checking, runFill))
        assertEquals(runFill, OruInstallScreenMapper.fill(OruInstallUi.Plan(show, null, null, emptyList()), runFill))
        assertEquals(runFill, OruInstallScreenMapper.fill(OruInstallUi.Failed("x"), runFill))
        assertEquals(0.95f, OruInstallScreenMapper.fill(OruInstallUi.Installing(false, null), runFill), 1e-6f)
        assertEquals(1f, OruInstallScreenMapper.fill(OruInstallUi.Installed(null), runFill))
    }

    @Test
    fun theInstallFillNeverGoesBackFromTheRunsFill() {
        val steps = listOf(
            OruInstallUi.Checking,
            OruInstallUi.Plan(show, null, null, emptyList()),
            OruInstallUi.Installing(false, null),
            OruInstallUi.Installed(null),
        )
        for (runFill in listOf(0f, 0.5f, 0.9f, 1f)) {
            val fills = steps.map { OruInstallScreenMapper.fill(it, runFill) }
            assertTrue(fills.zipWithNext().all { (before, after) -> after >= before }, "run fill $runFill: $fills")
            assertTrue(fills.all { it in 0f..1f })
        }
    }

    @Test
    fun installerOfOnlyNamesStepsThatResolveOne() {
        assertEquals(InstallerChoice.SYSTEM, OruInstallScreenMapper.installerOf(show))
        assertNull(OruInstallScreenMapper.installerOf(InstallStep.AskInstaller))
        assertNull(OruInstallScreenMapper.installerOf(null))
    }

    @Test
    fun silentShizukuLineNamesTheInstallerAndDropsTheSystemPrompt() {
        val silent = InstallStep.ShowInstall(InstallerChoice.SHIZUKU, silent = true, fallbackFrom = null)
        assertEquals(R.string.oru_install_with_silent, OruDecisionCopy.forStep(silent).body)
    }

    @Test
    fun systemInstallLineIsTheSystemLine() {
        assertEquals(R.string.oru_install_with_system, OruDecisionCopy.forStep(show).body)
    }

    @Test
    fun silentSystemLineKeepsThePromptPossible() {
        val silentSystem = InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = true, fallbackFrom = null)
        assertEquals(R.string.oru_install_with_system_silent, OruDecisionCopy.forStep(silentSystem).body)
    }

    @Test
    fun fallbackHintIsTheFallbackLine() {
        val fallback = InstallStep.ShowInstall(InstallerChoice.SYSTEM, silent = false, fallbackFrom = InstallerChoice.CUSTOM)
        assertEquals(R.string.oru_install_fallback, OruDecisionCopy.forStep(fallback).hint)
    }

    @Test
    fun unavailableActionsHaveTheirOwnLabel() {
        val open = InstallStep.InstallerUnavailable(
            InstallerChoice.SHIZUKU, UnavailableReason.SHIZUKU_NOT_RUNNING, UnavailableAction.OPEN_INSTALLER
        )
        val grant = InstallStep.InstallerUnavailable(
            InstallerChoice.SHIZUKU, UnavailableReason.SHIZUKU_PERMISSION_MISSING, UnavailableAction.GRANT_PERMISSION
        )
        val root = InstallStep.InstallerUnavailable(
            InstallerChoice.ROOT, UnavailableReason.ROOT_REQUIRED, UnavailableAction.NONE
        )
        assertEquals(R.string.oru_action_open_shizuku, OruDecisionCopy.forStep(open).primaryAction)
        assertEquals(R.string.oru_action_grant_shizuku, OruDecisionCopy.forStep(grant).primaryAction)
        assertNull(OruDecisionCopy.forStep(root).primaryAction)
        assertEquals(R.string.oru_unavailable_root, OruDecisionCopy.forStep(root).hint)
    }

    @Test
    fun shizukuHintShowsOnlyWhenTheUninstallIsSilent() {
        val silent = InstallStep.ConflictNeedsUninstall(
            reason = ConflictReason.SIGNATURE,
            dataLoss = true,
            canUninstallSilently = true,
            canIgnoreSignatureMismatch = false,
        )
        val manual = silent.copy(canUninstallSilently = false)
        assertEquals(R.string.oru_signature_hint_shizuku, OruDecisionCopy.forStep(silent).hint)
        assertEquals(R.string.oru_signature_hint_import, OruDecisionCopy.forStep(manual).hint)
    }

    @Test
    fun downgradeIsAConflictWithItsOwnTitle() {
        val downgrade = InstallStep.ConflictNeedsUninstall(
            reason = ConflictReason.DOWNGRADE,
            dataLoss = true,
            canUninstallSilently = false,
            canIgnoreSignatureMismatch = false,
        )
        val copy = OruDecisionCopy.forStep(downgrade)
        assertEquals(R.string.oru_install_downgrade_title, copy.title)
        assertEquals(R.string.oru_install_downgrade, copy.body)
        assertNull(copy.secondaryAction)
    }
}
