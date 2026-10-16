/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.domain.manager.OruVersion
import app.morphe.manager.domain.batch.BatchTarget
import app.morphe.manager.domain.bundles.AppSourceChoice
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.ui.oru.components.OruFrostedSheet
import app.morphe.manager.ui.oru.sources.OruSourceChoiceContent
import app.morphe.manager.ui.oru.sources.oruAppSources
import app.morphe.manager.ui.oru.sources.oruSourceChips
import app.morphe.manager.ui.oru.sources.rememberOruCustomizedApps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import app.morphe.manager.ui.oru.focus.ActiveRunTracker
import app.morphe.manager.ui.oru.home.HomeViewModelOruHomeCommands
import app.morphe.manager.ui.oru.home.OruBannerInput
import app.morphe.manager.ui.oru.focus.SingleRunOwner
import app.morphe.manager.ui.oru.home.OruBatchPhase
import app.morphe.manager.ui.oru.home.OruBatchSignal
import app.morphe.manager.ui.oru.home.OruBundleUpdateState
import app.morphe.manager.ui.oru.home.OruHomeAction
import app.morphe.manager.ui.oru.home.OruHomeActionRouter
import app.morphe.manager.ui.oru.home.OruHomeBanner
import app.morphe.manager.ui.oru.home.OruHomeContent
import app.morphe.manager.ui.oru.home.OruHomeInput
import app.morphe.manager.ui.oru.home.OruHomePresenter
import app.morphe.manager.ui.oru.home.OruHomeRun
import app.morphe.manager.ui.oru.home.OruManagerUpdate
import app.morphe.manager.ui.oru.home.oruAppName
import app.morphe.manager.ui.oru.home.oruHomeRunOf
import app.morphe.manager.ui.oru.home.oruReadyToInstallOf
import app.morphe.manager.ui.oru.home.oruSingleHomeRunOf
import app.morphe.manager.ui.oru.home.oruTourPatchedOn
import app.morphe.manager.ui.oru.settings.OruSettingsOpen
import app.morphe.manager.ui.oru.settings.OruSettingsSection
import app.morphe.manager.ui.oru.settings.OruSettingsSheet
import app.morphe.manager.ui.oru.settings.rememberOruSettingsViewModel
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.screen.home.Android11Dialog
import app.morphe.manager.ui.screen.home.HomeDialogs
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The oru Home. It replaces the old Home for the oru flavour and reads the same view model,
 * so every card action calls the function the old Home calls for the same intent (see
 * [HomeViewModelOruHomeCommands] and [OruHomeActionRouter]).
 *
 * @param onCardClick Opens Focus on the card with this id, which is `HomeAppItem.id`.
 * @param onStartQuickPatch Receives a run started from the Home flow, see `HomeViewModel.onStartQuickPatch`.
 * @param onStartBatchPatch Opens the batch screen for these targets. Its callers pass the batch route.
 * @param onReturnToRun Brings back the Focus of the single-app run in progress, see [returnToOruFocus].
 */
@Composable
fun OruHomeScreen(
    homeViewModel: HomeViewModel,
    onCardClick: (String) -> Unit,
    onStartQuickPatch: (Patcher.ViewModelParams) -> Unit,
    onStartBatchPatch: (List<BatchTarget>, Boolean) -> Unit = { _, _ -> },
    onReturnToRun: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installViewModel: InstallViewModel = koinViewModel()
    val updateViewModel: UpdateViewModel = koinViewModel()
    val activeRuns: ActiveRunTracker = koinInject()
    val networkInfo: NetworkInfo = koinInject()
    val singleRunOwner: SingleRunOwner = koinInject()
    val sourcesLoadingText = stringResource(R.string.home_sources_are_loading)
    val batchInProgressText = stringResource(R.string.batch_patch_in_progress)
    val runInProgressText = stringResource(R.string.oru_run_in_progress)
    val runStoppedText = stringResource(R.string.oru_run_stopped)

    // Notify user if previous process was killed mid-run
    LaunchedEffect(Unit) {
        if (singleRunOwner.consumeLastRunStopped()) {
            context.toast(runStoppedText)
        }
    }

    // Same hooks HomeScreen sets, so a run started from the home flow lands in the Focus entry
    LaunchedEffect(Unit) {
        homeViewModel.onStartQuickPatch = { params ->
            if (singleRunOwner.isBusy) {
                context.toast(runInProgressText)
            } else {
                onStartQuickPatch(params)
            }
        }
        if (!homeViewModel.rootInstaller.isDeviceRooted()) homeViewModel.usingMountInstall = false
    }

    // Home state, the same flows HomeScreen collects
    val homeAppState by homeViewModel.apps.homeAppState.collectAsStateWithLifecycle()
    val availablePatches by homeViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val batchRun by homeViewModel.batchRun.collectAsStateWithLifecycle()
    val activeRun by activeRuns.run.collectAsStateWithLifecycle()
    val sourceVersions by homeViewModel.sourceVersionsFlow.collectAsStateWithLifecycle()
    val loadedSources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
    val sourceMute: SourceMuteRepository = koinInject()
    val turnedDown by sourceMute.mutedSources.collectAsStateWithLifecycle(initialValue = emptyMap())
    val blockedSources by homeViewModel.patchBundleRepository.blockedSources.collectAsStateWithLifecycle(emptyMap())
    val metadataFetchErrors by homeViewModel.patchBundleRepository.metadataFetchErrors
        .collectAsStateWithLifecycle(emptyMap())
    val hasOutdatedManagerSources by homeViewModel.patchBundleRepository.hasOutdatedManagerSources
        .collectAsStateWithLifecycle()
    val hasHeldBackSources by homeViewModel.patchBundleRepository.hasHeldBackSources.collectAsStateWithLifecycle()
    val isRefreshing by homeViewModel.isRefreshing.collectAsStateWithLifecycle()
    val useExpertMode by homeViewModel.prefs.useExpertMode.getAsState()
    val tourFlag by homeViewModel.prefs.oruTourDone.getAsState()
    // A first patch that ended on its Done screen has already taught what the tour would
    val keyNoticeShown by homeViewModel.prefs.oruKeyNoticeShown.getAsState()
    val tourDone = tourFlag || keyNoticeShown

    val visibleItems = homeAppState?.visible.orEmpty()

    // What each app is patched from: one source, and every count and version of the card reads that source
    val appSources = remember(homeAppState, availablePatches, loadedSources, sourceVersions, turnedDown) {
        val sources = loadedSources.associateBy { it.uid }
        visibleItems.associate { item ->
            val patchCounts = homeViewModel.getPatchesForPackage(item.packageName).mapValues { it.value.size }
            item.packageName to oruAppSources(
                patchCounts = patchCounts,
                sources = sources,
                versions = sourceVersions[item.packageName].orEmpty(),
                turnedDown = turnedDown[item.packageName].orEmpty(),
            )
        }
    }
    val patchCounts = remember(appSources) { appSources.mapValues { it.value.effective?.patchCount ?: 0 } }
    val effectiveVersions = remember(appSources) { appSources.mapValues { it.value.effectiveVersions } }
    val recommended = remember(appSources) {
        appSources.mapNotNull { (packageName, sources) -> sources.effective?.recommendedVersion?.let { packageName to it } }
            .toMap()
    }
    val sourceNames = remember(appSources) { oruSourceChips(appSources) }
    // The apps Expert set up differently from the recommended setup. Simple marks them with a badge
    val customizedApps = rememberOruCustomizedApps(homeViewModel, visibleItems.map { it.packageName }, appSources)

    // The source choice of one app, opened from its card's source chip
    var sourcePickerOpen by remember { mutableStateOf(false) }
    var sourcePickerPackage by remember { mutableStateOf<String?>(null) }

    // A source added later does not take over an app: it is turned down for the apps another source already patches
    LaunchedEffect(Unit) {
        var known: Set<Int>? = null
        homeViewModel.patchBundleRepository.sources.collect { all ->
            // The list is empty until the bundles are read, and its first full read is the set the others are judged against
            if (all.isEmpty()) return@collect
            val enabled = all.filter { it.enabled }.mapTo(mutableSetOf()) { it.uid }
            val before = known
            known = enabled
            if (before == null) return@collect
            for (uid in enabled - before) {
                val infos = homeViewModel.patchBundleRepository.bundleInfoFlow
                val info = withTimeoutOrNull(SourceLoadWait) { infos.first { uid in it } }?.get(uid) ?: continue
                val others = infos.first().filterKeys { it != uid && it in enabled }.values.map { it.listedApps() }
                val apps = AppSourceChoice.appsToTurnDown(info.listedApps(), others)
                if (apps.isNotEmpty()) sourceMute.muteApps(uid, apps)
            }
        }
    }

    // The packages whose APKMirror hand-off happened: the flow waits on their file until it is picked
    var handedOff by remember { mutableStateOf(emptySet<String>()) }
    val pendingPackage = homeViewModel.pendingPackageName
    val filePromptShown = homeViewModel.showFilePickerPromptDialog
    LaunchedEffect(pendingPackage, filePromptShown) {
        if (pendingPackage == null) {
            handedOff = emptySet()
        } else if (filePromptShown) {
            handedOff = handedOff + pendingPackage
        }
    }

    val run = batchRun
    val batchSignal = when {
        run != null && run.isActive -> OruBatchSignal(OruBatchPhase.Running, run.processed, run.total)
        run != null && run.hasOutcome -> OruBatchSignal(OruBatchPhase.Finished)
        else -> OruBatchSignal(OruBatchPhase.Idle)
    }

    // The app the queue is patching now, for the run accessory above the island. Null between two apps and when idle
    val patchingItem = run?.takeIf { it.isActive }?.activeItem
    val patchingRun = run?.activeRun
    val batchHomeRun = if (patchingItem == null) {
        null
    } else {
        OruHomeRun(
            packageName = patchingItem.packageName,
            appName = oruAppName(patchingItem.packageName, patchingItem.appName),
            packageInfo = visibleItems.firstOrNull { it.packageName == patchingItem.packageName }?.packageInfo,
            progress = patchingRun?.progress,
            done = patchingRun?.patchesProgress?.first ?: 0,
            total = patchingRun?.patchesProgress?.second ?: 0,
            focusKey = null,
        )
    }
    // The batch run wins over a single-app run: the queue is the run the accessory reopens
    val homeRun = oruHomeRunOf(
        batch = batchHomeRun,
        single = oruSingleHomeRunOf(activeRun) { key -> visibleItems.firstOrNull { it.id == key }?.packageInfo },
    )

    // A dev build is offered only while the owner's pre-release switch is on, the rule the update check applies too
    val useManagerPrereleases by homeViewModel.prefs.useManagerPrereleases.getAsState()
    val announcedVersion = homeViewModel.updatedManagerVersion
        ?.takeIf { useManagerPrereleases || OruVersion.parse(it)?.isDev != true }

    // Read again whenever the list changes, which is when an empty list is judged. A connection that comes back shows
    // up with the list the refresh loads
    val offline = remember(homeAppState) { !networkInfo.isConnected() }

    val input = OruHomeInput(
        items = homeAppState?.visible,
        installedAppsLoading = homeAppState == null || homeViewModel.installedAppsLoading,
        expert = useExpertMode,
        handedOff = handedOff,
        offline = offline,
        recommendedVersions = recommended,
        effectiveVersions = effectiveVersions,
        patchCounts = patchCounts,
        sourceNames = sourceNames,
        customized = customizedApps,
        // A finished single-app run waiting for its install marks its card, see oruReadyToInstallOf
        readyToInstall = oruReadyToInstallOf(activeRun),
        banner = OruBannerInput(
            managerUpdate = oruManagerUpdateOf(
                vm = updateViewModel,
                announcedVersion = announcedVersion,
                failedFallback = stringResource(R.string.oru_update_error_generic),
            ),
            sourcesAttention = blockedSources.isNotEmpty() ||
                    metadataFetchErrors.isNotEmpty() ||
                    hasOutdatedManagerSources ||
                    hasHeldBackSources,
            meteredSkipped = homeViewModel.updatesSkippedDueToMetered,
            batch = batchSignal,
            bundleUpdate = if (!homeViewModel.showBundleUpdateSnackbar) {
                null
            } else {
                when (homeViewModel.snackbarStatus) {
                    BundleUpdateStatus.Updating -> OruBundleUpdateState.Updating
                    BundleUpdateStatus.Success -> OruBundleUpdateState.Done
                    BundleUpdateStatus.Error -> OruBundleUpdateState.Failed
                    // The metered skip has its own banner
                    BundleUpdateStatus.Warning -> null
                }
            },
        ),
    )
    val uiState = remember(input) { OruHomePresenter.state(input) }

    // The first patch ends the first-run tour for good, however it was made: a patched app on Home is the proof
    LaunchedEffect(tourFlag, uiState) {
        if (!tourFlag && oruTourPatchedOn(uiState.cards.map { it.status })) {
            homeViewModel.prefs.oruTourDone.update(true)
        }
    }

    // Dialog and sheet state. The patch dialogs themselves live in HomeDialogs, as on the old Home
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // What the sheet shows when it opens. The gear opens the top of the list, a banner can point at one of its groups
    var settingsOpen by remember { mutableStateOf(OruSettingsOpen()) }
    fun openSettings(section: OruSettingsSection?) {
        settingsOpen = OruSettingsOpen(section = section)
        showSettings = true
    }
    val patchesItem = remember { mutableStateOf<HomeAppItem?>(null) }

    val openApkPicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = { uri -> uri?.let { homeViewModel.handleApkSelection(it) } },
        preferInAppPicker = true
    )
    val openBundlePicker = rememberAdaptiveMultiFilePicker(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onResult = homeViewModel::pickBundles
    )
    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestInstallAppsContract
    ) { homeViewModel.showAndroid11Dialog = false }

    // The settings sheet reads its own view model, built on the Home view model's preferences
    val settingsViewModel = rememberOruSettingsViewModel(homeViewModel.prefs)
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    // The same rules HomeScreen applies to its batch actions
    val startBatchPatch: (List<HomeAppItem>) -> Unit = { apps ->
        val targets = apps.map { app ->
            BatchTarget(
                packageName = app.packageName,
                repatchedPackageName = app.installedApp?.trackingKey,
            )
        }
        when {
            singleRunOwner.isBusy -> context.toast(runInProgressText)
            availablePatches <= 0 -> context.toast(sourcesLoadingText)
            homeViewModel.android11BugActive -> homeViewModel.showAndroid11Dialog = true
            targets.isEmpty() -> Unit
            // A live queue keeps its own selection, so open it instead of swapping the apps
            homeViewModel.batchPatchRunning -> {
                context.toast(batchInProgressText)
                onStartBatchPatch(targets, false)
            }
            else -> onStartBatchPatch(targets, false)
        }
    }

    val commands = remember(homeViewModel, context) { HomeViewModelOruHomeCommands(homeViewModel, context) }

    // Reopens the batch screen on the queue, from the banner or the run accessory
    val reopenBatch: () -> Unit = { run?.let { onStartBatchPatch(it.targets, it.useMount) } }
    // The run accessory opens its own run: the Focus of a single-app run, else the batch screen
    val openRun: () -> Unit = {
        val focusKey = homeRun?.focusKey
        if (focusKey != null) onReturnToRun(focusKey) else reopenBatch()
    }

    // The banner's action follows the state of the update: start it, try again, or install the APK it downloaded
    fun onManagerUpdateAction(update: OruManagerUpdate) {
        when (update) {
            is OruManagerUpdate.Available, is OruManagerUpdate.Failed ->
                // Without a resolved release there is nothing to download yet, so the check runs again first
                if (updateViewModel.releaseInfo == null) updateViewModel.retryUpdateCheck() else updateViewModel.downloadUpdate()
            OruManagerUpdate.Ready -> updateViewModel.installUpdate()
            is OruManagerUpdate.Downloading, OruManagerUpdate.Installing -> Unit
        }
    }

    // A release found after this screen opened replaces the one the update view model resolved on opening
    LaunchedEffect(announcedVersion) {
        val resolved = updateViewModel.releaseInfo?.version
        if (announcedVersion != null && resolved != announcedVersion &&
            updateViewModel.state == UpdateViewModel.State.CAN_DOWNLOAD
        ) {
            updateViewModel.retryUpdateCheck()
        }
    }

    // An installer activity reports nothing when it is dismissed, so an abandoned install shows up only as the app
    // holding the foreground while the update is still installing. Same grace as the update dialog gives it
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, updateViewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(AbandonedInstallGrace)
            updateViewModel.resetIfInstallCancelled()
        }
    }

    // Updates are off on a metered network. The sheet opens at the Updates group, which holds that switch
    LaunchedEffect(updateViewModel.showInternetCheckDialog) {
        if (updateViewModel.showInternetCheckDialog) {
            updateViewModel.showInternetCheckDialog = false
            openSettings(OruSettingsSection.UPDATES)
        }
    }

    val onBannerAction: (OruHomeBanner) -> Unit = { banner ->
        when (banner) {
            is OruHomeBanner.ManagerUpdate -> onManagerUpdateAction(banner.update)
            OruHomeBanner.SourcesAttention -> homeViewModel.showBundleManagementSheet = true
            // The sheet opens at the group that explains the skipped updates
            OruHomeBanner.Metered -> openSettings(OruSettingsSection.UPDATES)
            is OruHomeBanner.BatchRunning, OruHomeBanner.BatchFinished -> reopenBatch()
            is OruHomeBanner.BundleUpdate -> homeViewModel.refresh()
            is OruHomeBanner.Repatch -> startBatchPatch(visibleItems.filter { it.showsUpdateBadge })
        }
    }

    OruHomeContent(
        state = uiState,
        refreshing = isRefreshing,
        settingsShown = showSettings,
        run = homeRun,
        onRefresh = { homeViewModel.refresh() },
        onSettingsClick = { openSettings(null) },
        onRunClick = openRun,
        onModeChange = { expert -> scope.launch { homeViewModel.prefs.useExpertMode.update(expert) } },
        onBannerAction = onBannerAction,
        onCardClick = { card -> onCardClick(card.id) },
        onCardLongClick = { card ->
            card.item.installedApp?.let { homeViewModel.openInstalledAppInfo(it.currentPackageName) }
        },
        onSourceClick = { card ->
            sourcePickerPackage = card.packageName
            sourcePickerOpen = true
        },
        // ORU: every patch step happens on the Focus screen; the old Home dialog chain (Morphe styled) is never shown
        onPrimary = { card ->
            if (oruActionOpensFocus(card.primary)) onCardClick(card.id)
            else OruHomeActionRouter.route(card, card.primary, commands)
        },
        onSecondary = { card ->
            card.secondary?.let {
                if (oruActionOpensFocus(it)) onCardClick(card.id)
                else OruHomeActionRouter.route(card, it, commands)
            }
        },
        onCheckUpdates = { homeViewModel.refresh() },
        onAddSource = { homeViewModel.showBundleManagementSheet = true },
        tourDone = tourDone,
        onTourDone = { scope.launch { homeViewModel.prefs.oruTourDone.update(true) } },
        // The settings sheet is drawn in the Home backdrop host, so its glass blurs the Home content
        overlay = {
            OruFrostedSheet(
                visible = sourcePickerOpen,
                onDismiss = { sourcePickerOpen = false },
            ) {
                val picked = sourcePickerPackage?.let { appSources[it] }
                val pickedApp = sourcePickerPackage?.let { packageName ->
                    visibleItems.firstOrNull { it.packageName == packageName }?.let { oruAppName(it.packageName, it.displayName) }
                }
                if (picked != null && pickedApp != null) {
                    OruSourceChoiceContent(
                        sources = picked,
                        appName = pickedApp,
                        onPick = { uid ->
                            scope.launch { homeViewModel.keepSourceForApp(sourcePickerPackage.orEmpty(), uid) }
                            sourcePickerOpen = false
                        },
                        onClose = { sourcePickerOpen = false },
                    )
                }
            }
            OruSettingsSheet(
                visible = showSettings,
                onDismiss = { showSettings = false },
                state = settingsState,
                events = settingsViewModel,
                open = settingsOpen,
                // Sources close the sheet and open the source list here, so the two sheets are never stacked
                onOpenPatchSources = {
                    showSettings = false
                    homeViewModel.showBundleManagementSheet = true
                },
            )
        },
    )

    // ORU: the old dialog host is not composed while the Focus is mounted, including the frames of the Focus
    // transitions, where this screen is still on screen. The Focus draws the APK flow, the selection and the
    // notices itself (OruFocusRoute, OruFocusDialogs), so the Morphe-styled ones must not be drawn over it.
    if (!homeViewModel.oruFocusMounted) {
        if (homeViewModel.showAndroid11Dialog) {
            Android11Dialog(
                onDismissRequest = { homeViewModel.showAndroid11Dialog = false },
                onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
            )
        }

        HomeDialogs(
            homeViewModel = homeViewModel,
            storagePickerLauncher = { openApkPicker() },
            openBundlePicker = { openBundlePicker() },
            patchesItem = patchesItem,
        )

        if (homeViewModel.showPrePatchInstallerDialog) {
            PrePatchInstallerDialog(
                packageName = homeViewModel.pendingPackageName,
                onSelectMount = { homeViewModel.resolvePrePatchInstallerChoice(useMount = true) },
                onSelectStandard = { homeViewModel.resolvePrePatchInstallerChoice(useMount = false) },
                onDismiss = homeViewModel::dismissPrePatchInstallerDialog
            )
        }
    }

    // The install prompts of InstallViewModel are not part of the APK flow, so the Focus does not gate them
    InstallerFlowDialogs(installViewModel = installViewModel)
}

/** App info and Open stay on Home; every other card action starts or continues the patch flow on Focus. */
internal fun oruActionOpensFocus(action: OruHomeAction): Boolean =
    action != OruHomeAction.AppInfo && action != OruHomeAction.Open

private val AbandonedInstallGrace = 1500.milliseconds

/** How long a newly added source is waited for, to read which apps it patches. */
private val SourceLoadWait = 15.seconds

/**
 * The banner's state of the Oru update, read off the update view model that the update dialog also uses.
 * Null when there is nothing to tell. [announcedVersion] is the release the Home screen was told of.
 */
private fun oruManagerUpdateOf(
    vm: UpdateViewModel,
    announcedVersion: String?,
    failedFallback: String,
): OruManagerUpdate? = when (vm.state) {
    UpdateViewModel.State.CAN_DOWNLOAD -> announcedVersion?.let { OruManagerUpdate.Available(it) }
    UpdateViewModel.State.DOWNLOADING -> OruManagerUpdate.Downloading(
        percent = (vm.downloadProgress * 100).toInt().takeIf { vm.totalSize > 0L },
    )
    UpdateViewModel.State.CAN_INSTALL -> OruManagerUpdate.Ready
    UpdateViewModel.State.INSTALLING -> OruManagerUpdate.Installing
    UpdateViewModel.State.FAILED -> OruManagerUpdate.Failed(vm.installError.ifBlank { failedFallback })
    UpdateViewModel.State.SUCCESS -> null
}
