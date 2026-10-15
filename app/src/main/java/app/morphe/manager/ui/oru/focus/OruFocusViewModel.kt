/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import android.app.Application
import android.net.Uri
import app.morphe.manager.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.ui.oru.install.OruInstallCoordinator
import app.morphe.manager.ui.oru.sources.OruAppSources
import app.morphe.manager.ui.oru.sources.oruAppSourcesNow
import app.morphe.manager.ui.oru.install.OruInstallExecutor
import app.morphe.manager.ui.oru.install.OruInstallState
import app.morphe.manager.ui.oru.install.OruInstallUi
import app.morphe.manager.ui.oru.install.OruInstallerOption
import app.morphe.manager.ui.oru.install.OruInstallerReadings
import app.morphe.manager.ui.oru.install.OruPackageReadings
import app.morphe.manager.ui.oru.install.SignatureSource
import app.morphe.manager.ui.oru.install.oruInstallRequest
import app.morphe.manager.ui.oru.run.OruFailure
import app.morphe.manager.ui.oru.run.OruMemoryOffer
import app.morphe.manager.ui.oru.run.oruFailureOf
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.screen.patcher.PatcherErrorInfo
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the Focus hosts, injected by the route. Each member is an existing object or a one-line adapter over
 * one; nothing here decides anything the existing view models do not decide.
 *
 * @param startRun starts the patch run in the Focus entry (`NavController.startOruRun`).
 * @param freeBytes free storage of the phone, or null when it cannot be read.
 * @param singleRunOwner the application-scoped owner of the one run, which holds the run and its install.
 */
class OruFocusDeps(
    val home: HomeViewModel,
    val installViewModel: InstallViewModel,
    val installers: OruInstallerReadings,
    val packages: OruPackageReadings,
    val signatures: SignatureSource,
    val executor: OruInstallExecutor,
    val freeBytes: () -> Long?,
    val startRun: (Patcher.ViewModelParams) -> Unit,
    val singleRunOwner: SingleRunOwner? = null,
    val app: Application? = null,
)

/** What [OruFocusViewModel.startHeldRun] did. [InProgress] is the only one the user needs to hear about. */
enum class HeldRunStart { Started, NothingHeld, InProgress }

/** The parameters the flow handed over, kept until the Focus leaves, with what the Focus needs to judge them. */
data class OruHeldRun(
    val params: Patcher.ViewModelParams,
    val apkBytes: Long?,
    val freeBytes: Long?,
)

/** The Focus for one card. It drives the APK flow of [HomeViewModel] and hosts the run and the install. */
class OruFocusViewModel(private val deps: OruFocusDeps) : ViewModel() {

    private val home: HomeViewModel get() = deps.home

    /** The run parameters the flow handed over (`proceedWithPatching`). Null while the flow is still choosing. */
    var held: OruHeldRun? by mutableStateOf(null)
        private set

    /** True from the hand-over until the file size is read. */
    var holding by mutableStateOf(false)
        private set

    /** Counts the runs this Focus started. Each run gets its own patcher view model, keyed by this. */
    var runGeneration by mutableIntStateOf(0)
        private set

    /** The generation whose run was closed by the user, so it is not shown again. */
    private var closedGeneration = -1
    private var attachedGeneration = -1

    var runStatus: OruRunStatus? by mutableStateOf(null)
        private set

    /** The failure of the run, latched once when the run failed or was cancelled (see [latchFailure]). */
    var failure: OruFailure? by mutableStateOf(null)
        private set

    var errorInfo: PatcherErrorInfo? by mutableStateOf(null)
        private set

    /** The install of the finished run. Null until the run is done. */
    var installer: OruInstallCoordinator? by mutableStateOf(null)
        private set

    var installState: OruInstallState? by mutableStateOf(null)
        private set

    /** The installer choices, for the Install with row. Loaded once. */
    var installerOptions: List<OruInstallerOption> by mutableStateOf(emptyList())
        private set

    /** The label of the installer the phone would use by default. */
    var primaryInstallerLabel: String? by mutableStateOf(null)
        private set

    /** The installer the user picked before the run. Applied to the install when it starts. */
    var pickedInstaller: OruInstallerOption? by mutableStateOf(null)
        private set

    /** HomeViewModel.isOnMeteredWithUpdatesDisabled(), read off the main thread (see [refreshMetered]). */
    private var metered by mutableStateOf(false)

    private var runJob: Job? = null
    private var autoStarted = false

    /** The patch selection was asked for by the user (Patches, Customize, Change patches), so it is not automatic. */
    private var userSelecting by mutableStateOf(false)

    /** The patch selection is being answered with its defaults, so the sheet does not open (see [OruFocusReducer.autoSelects]). */
    var autoSelecting by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { loadInstallerReadings() }
        refreshMetered()
        // The executor's state is the install's truth; the coordinator replans whenever it changes
        viewModelScope.launch {
            snapshotFlow { deps.installViewModel.installState }.collect { installer?.refresh() }
        }
        // Metered and low-storage gates are banners or blocks on the Focus, never a dialog it must answer
        // first (D18). The gate's own action runs the flow it was guarding.
        viewModelScope.launch {
            snapshotFlow { home.showMeteredPatchingDialog }.collect { open ->
                if (open) home.dismissMeteredPatchingDialogAndProceed()
            }
        }
        viewModelScope.launch {
            snapshotFlow { home.showLowDiskSpaceDialog }.collect { open ->
                if (open) home.dismissLowDiskSpaceDialogAndProceed()
            }
        }
        // The patch selection answers itself with the defaults unless the user asked to change the patches. Paths
        // that cannot be read, or an empty selection, leave it to the sheet.
        viewModelScope.launch {
            snapshotFlow { home.showExpertModeDialog to home.expertModeUnreadablePaths.isNotEmpty() }
                .collect { (open, unreadable) -> onSelectionChanged(open, unreadable) }
        }
        // The install belongs to the owner, which outlives this Focus. These only mirror it while the Focus is open,
        // and the owner's last value stays when it releases the run.
        deps.singleRunOwner?.let { owner ->
            viewModelScope.launch { owner.install.filterNotNull().collect { installer = it } }
            viewModelScope.launch {
                owner.install.filterNotNull().flatMapLatest { it.state }.collect { installState = it }
            }
            viewModelScope.launch {
                owner.rootMode.filterNotNull().collect { home.usingMountInstall = it }
            }
        }
    }

    private fun onSelectionChanged(open: Boolean, unreadable: Boolean) {
        when {
            // The sheet takes over and keeps the selection until the user is done with it
            unreadable -> {
                userSelecting = true
                autoSelecting = false
            }
            !open -> if (!autoSelecting) userSelecting = false
            autoSelecting -> Unit
            OruFocusReducer.autoSelects(userSelecting, home.expertModeTotalSelectedCount) -> {
                autoSelecting = true
                home.proceedExpertMode()
            }
        }
    }

    private suspend fun loadInstallerReadings() {
        installerOptions = deps.installers.pickerOptions()
        primaryInstallerLabel = deps.installers.labelFor(deps.installers.primaryChoice())
    }

    /** Reads the metered setting and the network off the main thread; it is a banner, so a moment late is fine. */
    private fun refreshMetered() {
        viewModelScope.launch {
            metered = withContext(Dispatchers.IO) { home.isOnMeteredWithUpdatesDisabled() }
        }
    }

    /**
     * The flags of the reducer, read from the flows and states they come from. Reads Compose state, so calling it
     * in composition recomposes when any of them changes.
     */
    fun flags(item: HomeAppItem?, hasRun: Boolean, sources: OruAppSources, expert: Boolean): OruFocusFlags {
        val current = held
        val pkg = item?.packageName
        val showRun = showsRun(hasRun)
        return OruFocusFlags(
            pendingForApp = pkg != null && home.pendingPackageName == pkg,
            processing = home.processingApkSelection || holding || autoSelecting,
            pickOpen = home.showApkAvailabilityDialog,
            downloadOpen = home.showDownloadInstructionsDialog,
            waitingForFile = home.showFilePickerPromptDialog,
            wrongPackage = home.showWrongPackageDialog?.actualPackage,
            splitApk = home.showSplitApkWarningDialog,
            invalidSignature = home.showInvalidSignatureDialog != null,
            unsupportedVersion = home.showUnsupportedVersionDialog?.version,
            unsupportedIsExperimental = home.showUnsupportedVersionDialog?.isExperimental == true,
            experimentalVersion = home.showExperimentalVersionDialog?.version,
            noCompatibleVersions = pkg != null && home.showNoCompatibleVersionsDialog == pkg,
            expert = expert,
            selecting = (home.showExpertModeDialog && !autoSelecting) || home.showPrePatchInstallerDialog,
            supportedVersion = sources.effective?.recommendedVersion,
            held = current != null,
            apkBytes = current?.apkBytes,
            freeBytes = current?.freeBytes,
            metered = metered,
            run = if (showRun) runStatus ?: OruRunStatus.Working else null,
        )
    }

    /** Whether a run is shown for [hasRun] (the Focus entry holds run parameters that were not closed yet). */
    fun showsRun(hasRun: Boolean): Boolean = hasRun && runGeneration != closedGeneration

    /**
     * Opens the APK flow for [item] once, when the Focus opens, so the card's own actions are not needed first.
     * Nothing starts while a flow for the app is already going.
     */
    fun autoStart(item: HomeAppItem) {
        if (autoStarted) return
        autoStarted = true
        if (held == null && home.pendingPackageName != item.packageName) startFlow(item)
    }

    /**
     * Restarts the APK flow for [item], the way the card's Patch action does (`HomeViewModel.showPatchDialog`). The flow
     * starts from the source the app is patched from, so no source is asked for before the Focus.
     */
    fun startFlow(item: HomeAppItem) {
        held = null
        userSelecting = false
        autoSelecting = false
        viewModelScope.launch {
            val source = oruAppSourcesNow(home, item.packageName).effective?.uid
            home.showPatchDialog(item.packageName, item.installedApp?.trackingKey, source)
        }
    }

    /**
     * The Focus picked another source for [item]: it is remembered for the app, and the APK step follows it. A run
     * already held was built from the old source, so it is started again from the new one.
     */
    fun changeSource(item: HomeAppItem, bundleUid: Int) {
        viewModelScope.launch {
            home.changeSourceForPendingFlow(bundleUid)
            if (held != null) startFlow(item)
        }
    }

    /** Starts the APK flow again when a run is held, so the run is built from the settings as they are now. */
    fun restartIfHeld(item: HomeAppItem) {
        if (held != null) startFlow(item)
    }

    /** The Focus took the run parameters from `proceedWithPatching` instead of starting them (see the route). */
    fun hold(params: Patcher.ViewModelParams) {
        holding = true
        userSelecting = false
        autoSelecting = false
        viewModelScope.launch {
            val local = params.selectedApp as? SelectedApp.Local
            val size = withContext(Dispatchers.IO) { local?.file?.length()?.takeIf { it > 0 } }
            held = OruHeldRun(params = params, apkBytes = size, freeBytes = deps.freeBytes())
            holding = false
        }
    }

    // Choose: the APK choice and the APKMirror hand-off, as the old dialogs did them

    /** "Find on APKMirror": the download step opens with the link resolved, as `onNeedApk` does. */
    fun findOnApkMirror() {
        home.showApkAvailabilityDialog = false
        home.showDownloadInstructionsDialog = true
        home.resolveDownloadRedirect()
    }

    /** The download step's open action. [openUrl] returns whether the browser took the link. */
    fun openApkMirror(openUrl: (String) -> Boolean) = home.handleDownloadInstructionsContinue(openUrl)

    /** Leaves the download step without a hand-off. */
    fun leaveDownloadStep() {
        home.showDownloadInstructionsDialog = false
        home.cleanupPendingData()
    }

    /** "Open APKMirror again" from the wait for the file. */
    fun openApkMirrorAgain() {
        home.showFilePickerPromptDialog = false
        home.showDownloadInstructionsDialog = true
        home.resolveDownloadRedirect()
    }

    /** "Use a file" on the APK choice: the choice ends first, as the old dialog's `onHaveApk` did, then the picker opens. */
    fun useFileTapped() {
        home.showApkAvailabilityDialog = false
    }

    /**
     * "Use another file" on a notice: the notice is dismissed and the picker opens (the caller launches it). The flow
     * the file is for is kept, so the picked file is handled as the same app's file.
     */
    fun useAnotherFileTapped() = dismissValidation()

    /** "Choose the file": the wait ends and the system picker opens (the caller launches it). */
    fun chooseFileTapped() {
        home.showFilePickerPromptDialog = false
    }

    /** The picker's answer. Null (the picker was closed) returns to the APK choice, as the old flow does. */
    fun fileChosen(uri: Uri?) = home.handleApkSelection(uri)

    fun useSavedCopy() = home.handleSavedApkSelection()

    fun useInstalledCopy() = home.handleInstalledApkSelection()

    /** Picks the download version (the Version row in Choose, `pendingSelectedDownloadVersion`). */
    fun pickDownloadVersion(version: String) {
        home.pendingCompatibleVersions.firstOrNull { it.target.version == version }?.let {
            home.pendingSelectedDownloadVersion = it.target
        }
    }

    // Notices: each answer maps to the call the old dialog made for it

    /** Answers a notice. */
    fun act(action: OruFocusAction, item: HomeAppItem?) {
        when (action) {
            OruFocusAction.UseAnother, OruFocusAction.PickVersion -> {
                dismissValidation()
                item?.let(::startFlow)
            }
            OruFocusAction.ProceedSplit -> home.proceedWithSplitApk()
            OruFocusAction.PatchAnyway -> home.proceedIgnoringSignature()
            OruFocusAction.PatchAnywayUnsupported -> home.proceedWithUnsupportedVersion()
            OruFocusAction.ShowExperimental -> showExperimentalVersions(item)
            OruFocusAction.ProceedExperimental -> home.proceedWithExperimentalVersion()
            OruFocusAction.Dismiss -> {
                home.showNoCompatibleVersionsDialog = null
                home.cleanupPendingData()
            }
            OruFocusAction.FindSupported -> {
                dismissValidation()
                item?.let(::startFlow)
            }
            OruFocusAction.Back -> {
                dismissValidation()
                home.cleanupPendingData()
            }
        }
    }

    private fun dismissValidation() {
        home.dismissWrongPackageDialog()
        home.dismissSplitApkWarning()
        home.dismissInvalidSignatureDialog()
        home.dismissUnsupportedVersionDialog()
        home.dismissExperimentalVersionDialog()
    }

    /** Turns experimental versions on for every source that patches [item], then looks again (`setUseExperimentalVersions`). */
    private fun showExperimentalVersions(item: HomeAppItem?) {
        item ?: return
        val uids = home.getPatchesForPackage(item.packageName).keys.toList()
        viewModelScope.launch {
            uids.forEach { home.patchBundleRepository.setUseExperimentalVersions(it, true) }
            dismissValidation()
            startFlow(item)
        }
    }

    // Ready and the run

    val singleRunOwner: SingleRunOwner? get() = deps.singleRunOwner

    /**
     * Starts the held run inside this Focus entry. A busy run (working, or finished with its install under way)
     * blocks the start. A run that is not busy, such as a failed one, is released first, so the new generation
     * gets a new patcher view model and a retry with the same parameters is a new run.
     */
    fun startHeldRun(): HeldRunStart {
        val run = held ?: return HeldRunStart.NothingHeld
        val owner = singleRunOwner
        if (owner != null) {
            if (!owner.canStart()) return HeldRunStart.InProgress
            owner.finishIdleRun()
        }
        runGeneration += 1
        runStatus = null
        failure = null
        errorInfo = null
        deps.startRun(run.params)
        return HeldRunStart.Started
    }

    /** "Change patches" on the Ready state: the expert selection opens again on the same file. */
    fun changePatches() {
        val run = held ?: return
        userSelecting = true
        home.reopenPatchSelection(run.params)
    }

    /** "Change patches" after a failure: the outcome is left, then the selection opens. */
    fun changePatchesAfterFailure() {
        closeRun()
        changePatches()
    }

    /**
     * Accepts the memory offer. The lower limit is stored first, and the run starts again only once the stored value
     * is the one offered, so the new run reads the lower limit rather than the old one.
     */
    fun acceptMemoryOffer(patcher: PatcherViewModel) {
        val target = patcher.memoryAdjustmentDialog?.takeIf { it.canAdjust }?.suggestedLimit
        patcher.applyMemoryAdjustment()
        viewModelScope.launch {
            if (target != null) {
                withTimeoutOrNull(MEMORY_LIMIT_WAIT_MS) {
                    while (home.prefs.patcherProcessMemoryLimit.get() != target) delay(MEMORY_LIMIT_POLL_MS)
                }
            }
            if (startHeldRun() == HeldRunStart.InProgress) {
                deps.app?.let {
                    android.widget.Toast.makeText(it, R.string.oru_run_in_progress, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Closes the shown run and returns to Ready (cancel, or leaving a failure). The held run stays for another start. */
    fun closeRun() {
        closedGeneration = runGeneration
        runJob?.cancel()
        runJob = null
        runStatus = null
        failure = null
        errorInfo = null
        singleRunOwner?.finishRun()
    }

    /** Attaches the patcher view model of the current generation and follows its outcome. Idempotent per generation. */
    fun attachRun(patcher: PatcherViewModel, item: HomeAppItem) {
        val generation = runGeneration
        if (attachedGeneration == generation) return
        attachedGeneration = generation
        runJob?.cancel()
        runJob = viewModelScope.launch {
            combine(
                patcher.patcherSucceeded,
                patcher.patcherCancelled,
                snapshotFlow { patcher.showSuccessScreen },
            ) { succeeded, cancelled, resultShown ->
                when {
                    cancelled -> OruRunStatus.Cancelled
                    succeeded == true && resultShown -> OruRunStatus.Done
                    succeeded == false -> OruRunStatus.Failed
                    else -> OruRunStatus.Working
                }
            }.collect { status -> onRunStatus(status, patcher, generation) }
        }
    }

    /**
     * The user confirmed the cancel: the worker stops, the run is released (its temporary files go with it, and no
     * install is begun), and the Focus returns to Ready with the APK still held.
     */
    fun cancelRun(patcher: PatcherViewModel) {
        patcher.cancelPatching()
        singleRunOwner?.cancelRun()
        closeRun()
    }

    private fun onRunStatus(status: OruRunStatus, patcher: PatcherViewModel, generation: Int) {
        if (generation != runGeneration || generation == closedGeneration) return
        runStatus = status
        when (status) {
            OruRunStatus.Failed -> if (failure == null) latchFailure(patcher, generation)
            OruRunStatus.Cancelled -> if (failure == null) failure = OruFailure.Cancelled
            OruRunStatus.Done -> beginInstall(patcher)
            OruRunStatus.Working -> Unit
        }
    }

    /**
     * Latches the failure once. The memory offer is set a moment after the worker reports the failure (the
     * limit is read from preferences first), so the latch waits for it briefly. Accepting the offer clears
     * the patcher's dialog state, which is why the offer is copied here.
     */
    private fun latchFailure(patcher: PatcherViewModel, generation: Int) {
        viewModelScope.launch {
            val offerState = withTimeoutOrNull(MEMORY_OFFER_WAIT_MS) {
                snapshotFlow { patcher.memoryAdjustmentDialog }.first { it != null }
            }
            // Only a kill by the system carries an offer. A heap the patcher filled itself has none
            val offer = offerState?.takeUnless { it.finished }?.let {
                OruMemoryOffer(currentLimitMb = it.currentLimit, suggestedLimitMb = it.suggestedLimit)
            }
            val step = patcher.steps.firstOrNull { it.state == State.FAILED }
            val logTail = patcher.patchRun.logs.takeLast(FAILURE_LOG_LINES).map { it.second }
            val info = patcher.buildErrorInfo()
            if (generation != runGeneration || failure != null) return@launch
            failure = oruFailureOf(
                memoryOffer = offer,
                failedPatch = patcher.patchRun.failedPatch,
                message = step?.message,
                logTail = logTail,
            )
            errorInfo = info
        }
    }

    /**
     * Hands the finished run to the owner, which builds the install coordinator and runs it in the owner's scope.
     * The lambda captures only the owner and the app-wide readings, never this view model.
     */
    private fun beginInstall(patcher: PatcherViewModel) {
        val owner = singleRunOwner ?: return
        val packages = deps.packages
        val installers = deps.installers
        val signatures = deps.signatures
        val executor = deps.executor
        owner.beginInstall(picked = pickedInstaller) {
            OruInstallCoordinator(
                request = oruInstallRequest(
                    outputFile = patcher.outputFile,
                    stockPackage = patcher.packageName,
                    packages = packages,
                    persist = patcher::persistPatchedApp,
                ),
                installers = installers,
                packages = packages,
                signatures = signatures,
                executor = executor,
                onRootModeChosen = owner::recordRootMode,
            )
        }
    }

    /** The Install with row: the choice applies to this run's install, now or when it starts. */
    suspend fun pickInstaller(option: OruInstallerOption) {
        pickedInstaller = option
        installer?.pickInstaller(option)
    }

    /**
     * Called when the Focus leaves. Clears what the flow left open, unless an install is writing now. [leaving] is
     * false for a configuration change, which rebuilds the screen without leaving it, so the run is kept across it.
     */
    fun onLeave(leaving: Boolean) {
        if (installState?.ui is OruInstallUi.Installing) return
        val runWorking = singleRunOwner?.isActive == true
        if (runWorking) return
        if (leaving) singleRunOwner?.releaseLeftRun()

        home.showApkAvailabilityDialog = false
        home.showDownloadInstructionsDialog = false
        home.showFilePickerPromptDialog = false
        if (home.showExpertModeDialog) home.cleanupExpertModeData()
        if (runStatus == null || runStatus == OruRunStatus.Failed || runStatus == OruRunStatus.Cancelled) {
            home.cleanupPendingData()
        }
        held = null
        userSelecting = false
        autoSelecting = false
    }

    override fun onCleared() {
        // Only the watch on the run stops here. An install in progress belongs to the owner and keeps going.
        runJob?.cancel()
    }

    /** Whether the install is writing now, when back must wait. */
    fun isInstalling(): Boolean = installState?.ui is OruInstallUi.Installing

    /** Back or the Stop button asked to stop waiting on the install. The confirm is shown while this is true. */
    var stopWaitAsked by mutableStateOf(false)
        private set

    fun askStopWaiting() {
        stopWaitAsked = true
    }

    fun keepWaiting() {
        stopWaitAsked = false
    }

    /** The user confirmed the stop: the wait is cancelled and the screen returns to the plan (see [OruInstallCoordinator]). */
    fun stopWaiting() {
        stopWaitAsked = false
        viewModelScope.launch { installer?.stopWaiting() }
    }

    /** Replans the install after the screen resumed (permissions or the notification may have changed). */
    fun refreshInstall() {
        refreshMetered()
        viewModelScope.launch { installer?.refresh() }
    }

    private companion object {
        /** How long the failure latch waits for the memory offer the worker reports. */
        const val MEMORY_OFFER_WAIT_MS = 1_000L

        /** Log lines kept for the failure summary when the worker gave no message. */
        const val FAILURE_LOG_LINES = 40

        /** How long a memory retry waits for the lower limit to be stored before it starts the run. */
        const val MEMORY_LIMIT_WAIT_MS = 2_000L
        const val MEMORY_LIMIT_POLL_MS = 50L
    }
}
