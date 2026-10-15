/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruDecisionDialog
import app.morphe.manager.ui.oru.components.OruDialogAction
import app.morphe.manager.ui.oru.components.OruTileSource
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.home.oruAppName
import app.morphe.manager.ui.oru.install.InstallStep
import app.morphe.manager.ui.oru.install.OruInstallActions
import app.morphe.manager.ui.oru.install.OruInstallContent
import app.morphe.manager.ui.oru.install.OruInstallUi
import app.morphe.manager.ui.oru.install.OruSignatureDecision
import app.morphe.manager.ui.oru.install.postinstall.OruDoneFacts
import app.morphe.manager.ui.oru.install.postinstall.oruElapsedMs
import app.morphe.manager.ui.oru.run.OruFailedContent
import app.morphe.manager.ui.oru.run.OruFailure
import app.morphe.manager.ui.oru.run.OruHeroState
import app.morphe.manager.ui.oru.run.OruRunContent
import app.morphe.manager.ui.oru.run.OruRunHexagon
import app.morphe.manager.ui.oru.run.OruRunUsage
import app.morphe.manager.ui.oru.run.oruRunStateOf
import app.morphe.manager.ui.oru.run.oruRunUi
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.screen.patcher.BatteryOptimizationDialog
import app.morphe.manager.ui.screen.patcher.IncompatiblePatcherVersionDialog
import app.morphe.manager.ui.screen.patcher.MissingPatchesDialog
import app.morphe.manager.ui.screen.patcher.UnusableOptionPathsDialog
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.RequestInstallAppsContract
import app.morphe.manager.util.TEXT_MIMETYPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * The run of the Focus: the patcher view model of this run's generation, its working, done, failed and cancelled
 * layouts, and the dialogs the patcher holds the start on. The worker itself is the existing one, started by the
 * existing [PatcherViewModel] (see [OruFocusViewModel.attachRun]). The run is held by [SingleRunOwner], so this
 * screen attaches to it rather than owning it.
 *
 * @param onBack leaves the Focus, from the install's Done.
 * @param onImportKey opens the Settings sheet to import the signing key, from the install decision.
 */
@Composable
internal fun OruFocusRunRoute(
    vm: OruFocusViewModel,
    run: Patcher.ViewModelParams,
    item: HomeAppItem,
    expert: Boolean,
    onBack: () -> Unit,
    onImportKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val singleRunOwner: SingleRunOwner = koinInject()
    var patcher by remember(item.id, vm.runGeneration) {
        mutableStateOf(singleRunOwner.getRun(item.id) as? PatcherViewModel)
    }
    LaunchedEffect(singleRunOwner, item.id, vm.runGeneration, run) {
        if (patcher == null) {
            val started = singleRunOwner.getOrStartRun(item.id, item, run) as? PatcherViewModel
            if (started == null) {
                onBack()
            } else {
                patcher = started
            }
        }
    }
    val currentPatcher = patcher ?: return
    LaunchedEffect(currentPatcher) { vm.attachRun(currentPatcher, item) }

    var cancelAsked by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val succeeded by currentPatcher.patcherSucceeded.collectAsStateWithLifecycle()
    val cancelled by currentPatcher.patcherCancelled.collectAsStateWithLifecycle()
    val importExportViewModel: ImportExportViewModel = koinViewModel()
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TEXT_MIMETYPE),
    ) { uri -> uri?.let { importExportViewModel.exportDebugLogs(it) } }
    val installPermissionLauncher = rememberLauncherForActivityResult(RequestInstallAppsContract) {
        scope.launch { vm.installer?.refresh() }
    }
    val shareLog: () -> Unit = { exportLogLauncher.launch(importExportViewModel.debugLogFileName) }
    val requestInstallPermission: () -> Unit = { installPermissionLauncher.launch(context.packageName) }

    val tile = OruTileSource(name = oruAppName(item.packageName, item.displayName), packageInfo = item.packageInfo)
    val shape = OruBlobShape.presetFor(item.id)
    val failure = vm.failure
    val runState = oruRunStateOf(
        isPatching = currentPatcher.isPatching,
        succeeded = succeeded,
        cancelled = cancelled,
        resultShown = currentPatcher.showSuccessScreen,
        memoryOfferAvailable = failure is OruFailure.MemoryOffer,
    )
    val runUi = oruRunUi(
        run = runState,
        steps = currentPatcher.steps,
        stageLogs = currentPatcher.patchRun.stageLogs,
        stepTimings = currentPatcher.patchRun.stepTimings,
        patchesDone = currentPatcher.patchRun.completedPatches,
        patchesTotal = currentPatcher.patchCount,
        progress = currentPatcher.progress,
        // A single run always goes on to the install, which takes the last share of the hexagon and the percentage
        installAhead = true,
    )
    val usage = OruRunUsage.of(currentPatcher.patchRun)

    // The done facts of a finished run: the output's size on disk, the run's elapsed time and its peak heap.
    // None of them was passed before, so the done screen had no facts row.
    val done = runState.hero == OruHeroState.DONE
    val outputBytes by produceState<Long?>(initialValue = null, currentPatcher.outputFile, done) {
        value = if (done) withContext(Dispatchers.IO) { currentPatcher.outputFile.takeIf { it.isFile }?.length() } else null
    }
    val doneFacts = if (done) {
        OruDoneFacts.of(
            outputBytes = outputBytes,
            elapsedMs = oruElapsedMs(currentPatcher.patchRun.stepTimings.values),
            peakHeapMb = usage.peakHeapMb,
        )
    } else {
        null
    }

    val appName = oruAppName(item.packageName, item.displayName)
    // The run screens sit under the status bar otherwise: the hero pieces and tiles drew over it
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        when (runState.hero) {
            OruHeroState.READY, OruHeroState.WORKING -> OruRunContent(
                run = runUi,
                expert = expert,
                tile = tile,
                shape = shape,
                usage = usage,
                facts = null,
                runKey = currentPatcher.patchRun,
                onCancel = {
                    cancelAsked = true
                },
                modifier = Modifier.fillMaxSize(),
            )

            OruHeroState.DONE -> OruInstallContent(
                ui = vm.installState?.ui ?: OruInstallUi.Checking,
                appName = appName,
                tile = tile,
                shape = shape,
                actions = installActions(vm, scope, requestInstallPermission, onBack, shareLog),
                runHexagon = OruRunHexagon(fill = runUi.progress, patches = runUi.patchesTotal, patchShare = runUi.patchShare),
                modifier = Modifier.fillMaxSize(),
                facts = doneFacts,
                installedLabel = vm.installState?.installedLabel,
            )

            OruHeroState.FAILED, OruHeroState.CANCELLED -> {
                val outcome = failure ?: if (runState.hero == OruHeroState.CANCELLED) OruFailure.Cancelled else null
                if (outcome == null) {
                    // The failure is latched a moment after the worker reports it; the run layout holds the gap
                    OruRunContent(
                        run = runUi,
                        expert = expert,
                        tile = tile,
                        shape = shape,
                        usage = usage,
                        facts = null,
                        runKey = currentPatcher.patchRun,
                        onCancel = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OruFailedContent(
                        failure = outcome,
                        run = runUi,
                        expert = expert,
                        errorInfo = vm.errorInfo,
                        tile = tile,
                        shape = shape,
                        logText = currentPatcher.patchRun.logs.joinToString(separator = "\n") { it.second },
                        onApplyMemoryOffer = { vm.acceptMemoryOffer(currentPatcher) },
                        onShareLog = shareLog,
                        onChangePatches = changePatchesAction(expert, vm),
                        onBack = { vm.closeRun() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (runState.hero == OruHeroState.DONE) {
            val install = vm.installState
            val plan = install?.plan
            if (install?.decisionOpen == true && plan is InstallStep.ConflictNeedsUninstall) {
                OruSignatureDecision(
                    step = plan,
                    appName = appName,
                    installedLabel = install.installedLabel,
                    onUninstallAndInstall = { scope.launch { vm.installer?.confirmUninstall() } },
                    onImportKey = onImportKey,
                    onInstallAnyway = { scope.launch { vm.installer?.installIgnoringSignatureMismatch() } },
                    onDismiss = { scope.launch { vm.installer?.dismissDecision() } },
                )
            }
        }

        if (cancelAsked && runState.hero == OruHeroState.WORKING) {
            OruDecisionDialog(
                title = stringResource(R.string.oru_run_cancel_title),
                body = stringResource(R.string.oru_run_cancel_body),
                primary = OruDialogAction(
                    label = stringResource(R.string.oru_run_cancel_go),
                    onClick = {
                        cancelAsked = false
                        vm.cancelRun(currentPatcher)
                    },
                ),
                secondary = OruDialogAction(
                    label = stringResource(R.string.oru_run_keep_patching),
                    onClick = { cancelAsked = false },
                ),
                tone = OruTone.Error,
                onDismiss = { cancelAsked = false },
            )
        }
    }

    // The patcher holds the start on these until they are answered (PatcherScreen shows the same ones)
    currentPatcher.missingPatchWarning?.let { warning ->
        MissingPatchesDialog(
            patchNames = warning.patchNames,
            onContinue = currentPatcher::continueWithoutMissingPatches,
            onDismiss = {
                currentPatcher.dismissMissingPatchWarning()
                vm.closeRun()
            },
        )
    }
    currentPatcher.inaccessibleOptionPaths?.let { errorState ->
        UnusableOptionPathsDialog(
            failures = errorState.failures,
            onRetryAfterPermission = currentPatcher::retryAfterPermission,
            canClearPaths = errorState.canClear,
            onClearPaths = currentPatcher::clearInaccessibleOptionPaths,
            onDismiss = {
                currentPatcher.dismissInaccessibleOptionPathsError()
                vm.closeRun()
            },
        )
    }
    currentPatcher.incompatiblePatcherVersion?.let { state ->
        IncompatiblePatcherVersionDialog(
            bundleName = state.bundleName,
            requiredVersion = state.requiredVersion,
            onDismiss = {
                currentPatcher.dismissIncompatiblePatcherVersion()
                vm.closeRun()
            },
        )
    }
    if (currentPatcher.batteryOptimizationDialog) {
        BatteryOptimizationDialog(onResult = currentPatcher::onBatteryOptimizationDialogResult)
    }
}

/** "Change patches" after a failure, in Expert mode only. */
private fun changePatchesAction(expert: Boolean, vm: OruFocusViewModel): (() -> Unit)? =
    if (expert) {
        { vm.changePatchesAfterFailure() }
    } else {
        null
    }

/** The install's answers, each one a call on the coordinator (see [OruInstallCoordinator]). */
private fun installActions(
    vm: OruFocusViewModel,
    scope: CoroutineScope,
    requestInstallPermission: () -> Unit,
    onDone: () -> Unit,
    shareLog: () -> Unit,
): OruInstallActions = OruInstallActions(
    onInstall = { scope.launch { vm.installer?.install() } },
    onPickInstaller = { option -> scope.launch { vm.pickInstaller(option) } },
    onChooseRootMode = { useMount -> scope.launch { vm.installer?.chooseRootMode(useMount) } },
    onReviewConflict = { scope.launch { vm.installer?.reviewConflict() } },
    onAllowInstalls = {
        scope.launch {
            vm.installer?.onPermissionExplained()
            requestInstallPermission()
        }
    },
    onOpenInstaller = { scope.launch { vm.installer?.openInstallerApp() } },
    onGrantPermission = { scope.launch { vm.installer?.grantInstallerPermission() } },
    onInstallWithAndroid = { scope.launch { vm.installer?.installWithAndroid() } },
    onOpen = { vm.installer?.open() },
    onDone = onDone,
    onRetry = { scope.launch { vm.installer?.retry() } },
    onShareLog = shareLog,
    onStopWaiting = { vm.askStopWaiting() },
)
