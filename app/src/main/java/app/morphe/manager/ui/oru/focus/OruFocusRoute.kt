/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import android.app.Application
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.R
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.ui.oru.components.OruBackdropHost
import app.morphe.manager.ui.oru.sources.resetOruAppOverrides
import app.morphe.manager.ui.oru.sources.rememberOruAppOverrides
import app.morphe.manager.ui.oru.sources.rememberOruAppSources
import app.morphe.manager.ui.oru.components.OruDecisionDialog
import app.morphe.manager.ui.oru.components.OruDialogAction
import app.morphe.manager.ui.oru.components.OruSkeleton
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.components.LocalOruAppTint
import app.morphe.manager.ui.oru.components.rememberOruAppTint
import app.morphe.manager.ui.oru.focus.expert.OruExpertSheetHost
import app.morphe.manager.ui.oru.focus.expert.OruExpertSheets
import app.morphe.manager.ui.oru.focus.expert.LocalOruExpertSheets
import app.morphe.manager.ui.oru.install.AppOruInstallerReadings
import app.morphe.manager.ui.oru.install.AppOruPackageReadings
import app.morphe.manager.ui.oru.install.AppSignatureSource
import app.morphe.manager.ui.oru.install.ViewModelOruInstallExecutor
import app.morphe.manager.ui.oru.settings.OruAdvancedGroup
import app.morphe.manager.ui.oru.settings.OruSettingsOpen
import app.morphe.manager.ui.oru.settings.OruSettingsPage
import app.morphe.manager.ui.oru.settings.OruSettingsSheet
import app.morphe.manager.ui.oru.settings.rememberOruSettingsViewModel
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.PM
import app.morphe.manager.util.toast
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The Focus of one card, hosted in its navigation entry. [packageKey] is the card's `HomeAppItem.id`, which
 * is also the shared tile key the hero receives (`oruTileSharedKey`).
 *
 * The APK flow is the existing one in [HomeViewModel]: it is opened for this card when the Focus opens, and
 * its dialogs are answered here instead of on Home. The run the flow would start is held by [OruFocusViewModel]
 * until the user starts it, and it starts through [onStartRun] (`NavController.startOruRun`).
 *
 * @param run the run parameters the nav entry holds, once the user started the run. Null before that.
 * The Settings sheet opens over this screen when the signing key is imported from the install decision, so the decision
 * stays on screen. Its source list leaves the Focus, as the Advanced page always did.
 *
 * @param onStartRun starts the run in this entry.
 */
@Composable
fun OruFocusRoute(
    packageKey: String,
    run: Patcher.ViewModelParams?,
    onBack: () -> Unit,
    onStartRun: (Patcher.ViewModelParams) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val activity = LocalActivity.current
    // HomeViewModel lives with the activity, as in MainActivity; the Focus reads the same instance
    val home: HomeViewModel = koinViewModel(viewModelStoreOwner = activity as ComponentActivity)
    val installViewModel: InstallViewModel = koinViewModel()
    val installerManager: InstallerManager = koinInject()
    val sessionInstaller: SessionInstaller = koinInject()
    val rootInstaller: RootInstaller = koinInject()
    val prefs: PreferencesManager = koinInject()
    val pm: PM = koinInject()
    val keystore: KeystoreManager = koinInject()
    val singleRunOwner: SingleRunOwner = koinInject()
    val sourceMute: SourceMuteRepository = koinInject()
    val patchSelections: PatchSelectionRepository = koinInject()
    val patchOptions: PatchOptionsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val resetDoneText = stringResource(R.string.oru_simple_reset_done)

    val factory = remember(home, installViewModel, onStartRun, singleRunOwner) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OruFocusViewModel(
                OruFocusDeps(
                    home = home,
                    installViewModel = installViewModel,
                    installers = AppOruInstallerReadings(app, prefs, installerManager, sessionInstaller, rootInstaller),
                    packages = AppOruPackageReadings(pm, sessionInstaller),
                    signatures = AppSignatureSource(app, pm, keystore),
                    executor = ViewModelOruInstallExecutor(installViewModel, installerManager),
                    freeBytes = { runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrNull() },
                    startRun = onStartRun,
                    singleRunOwner = singleRunOwner,
                    app = app,
                ),
            ) as T
        }
    }
    val vm: OruFocusViewModel = viewModel(key = "oru-focus", factory = factory)

    val homeState by home.apps.homeAppState.collectAsStateWithLifecycle()
    val item = homeState?.visible?.firstOrNull { it.id == packageKey }
    val expert by home.prefs.useExpertMode.getAsState()
    // The app's light: every control of this Focus reads the palette the route provides (LocalOruAppTint)
    val appTint = rememberOruAppTint(item?.packageInfo)

    // The flow's run is held here rather than started by Home's callback. Home's callback comes back when the
    // Focus leaves, so this one is undone on the way out.
    // While the Focus is mounted the old Home dialogs are not composed (OruHomeScreen), so the flow shows only here
    DisposableEffect(home, vm, activity) {
        val previous = home.onStartQuickPatch
        val previouslyMounted = home.oruFocusMounted
        home.onStartQuickPatch = { params -> vm.hold(params) }
        home.oruFocusMounted = true
        onDispose {
            home.onStartQuickPatch = previous
            home.oruFocusMounted = previouslyMounted
            // A rotation or theme change disposes this screen too, and it does not leave the Focus
            vm.onLeave(leaving = activity?.isChangingConfigurations != true)
        }
    }

    LaunchedEffect(item?.id) {
        item?.let { vm.autoStart(it, expert) }
    }

    // Permissions and Shizuku may change in the settings the install sends the user to; the plan is read again on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshInstall()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // S07: the Focus rows open their sheets here, and the route draws them (OruExpertSheets)
    val expertSheets = remember { OruExpertSheetHost() }
    // The Settings sheet, for the signing key import: it opens on the Advanced page at its Backup group
    var settingsShown by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(OruSettingsOpen()) }
    val settingsViewModel = rememberOruSettingsViewModel(prefs)
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val openSigningKey: () -> Unit = {
        settingsOpen = OruSettingsOpen(page = OruSettingsPage.ADVANCED, advancedGroup = OruAdvancedGroup.BACKUP)
        settingsShown = true
    }
    val hasRun = run != null
    val sources = rememberOruAppSources(home, item?.packageName.orEmpty())
    val overrides = rememberOruAppOverrides(home, item?.packageName.orEmpty(), sources)
    val state = OruFocusReducer.reduce(vm.flags(item, hasRun, sources, expert))
    // Simple's way back from a customized app: its own records are cleared, and a held run is built again from the defaults
    val customized = overrides?.isCustomized() == true
    val onReset: () -> Unit = {
        val plan = overrides?.resetPlan()
        if (item != null && plan != null) {
            scope.launch {
                resetOruAppOverrides(sourceMute, patchSelections, patchOptions, item.packageName, plan)
                vm.restartIfHeld(item)
                context.toast(resetDoneText)
            }
        }
    }
    // The install's app info, opened from Simple's Focus: Home shows its dialog once the Focus is left
    val infoPackage = item?.let { it.installedApp?.currentPackageName ?: it.installedPatchedPackage }
    val onAppInfo: (() -> Unit)? = infoPackage?.let { packageName ->
        val open: () -> Unit = {
            home.openInstalledAppInfo(packageName)
            onBack()
        }
        open
    }

    // Back during the install's wait asks to stop waiting (never swallowed silently).
    // Back during a run leaves the Focus without cancelling (the run stays active in SingleRunOwner).
    // An explicit Cancel button in the run layout asks for confirmation and cancels.
    val installing = vm.isInstalling()
    BackHandler(enabled = installing) {
        vm.askStopWaiting()
    }

    CompositionLocalProvider(
        LocalOruExpertSheets provides expertSheets,
        LocalOruAppTint provides appTint,
    ) {
        // The screen draws its own ambient glow behind the status bar, so the padding moved into the body
        Box(modifier = Modifier.fillMaxSize()) {
            // The Focus content is the backdrop of the expert sheets, so their glass blurs it
            OruBackdropHost(
                modifier = Modifier.fillMaxSize(),
                backdrop = {
                    when {
                        item == null -> OruSkeleton(modifier = Modifier.fillMaxSize())
                        run != null && state is OruFocusState.Run -> key(vm.runGeneration) {
                            OruFocusRunRoute(
                                vm = vm,
                                run = run,
                                item = item,
                                expert = expert,
                                onBack = onBack,
                                onImportKey = openSigningKey,
                            )
                        }
                        else -> OruFocusShell(
                            state = state,
                            vm = vm,
                            home = home,
                            item = item,
                            expert = expert,
                            tint = appTint,
                            onBack = onBack,
                            customized = customized,
                            onReset = onReset,
                            onAppInfo = onAppInfo,
                        )
                    }
                },
                floating = {
                    item?.let {
                        OruExpertSheets(
                            host = expertSheets,
                            vm = vm,
                            home = home,
                            item = it,
                            choosing = state is OruFocusState.Choose,
                            customized = customized,
                            onReset = onReset,
                        )
                    }
                    OruSettingsSheet(
                        visible = settingsShown,
                        onDismiss = { settingsShown = false },
                        state = settingsState,
                        events = settingsViewModel,
                        open = settingsOpen,
                        onOpenPatchSources = {
                            settingsShown = false
                            home.showBundleManagementSheet = true
                            onBack()
                        },
                    )
                },
            )

            OruSelectionDialogs(home)

            // The install's wait stops only when the user confirms here; the APK is kept for Try again
            if (installing && vm.stopWaitAsked) {
                OruDecisionDialog(
                    title = stringResource(R.string.oru_install_stop_title),
                    body = stringResource(R.string.oru_install_stop_body),
                    primary = OruDialogAction(
                        label = stringResource(R.string.oru_install_stop_go),
                        onClick = { vm.stopWaiting() },
                    ),
                    secondary = OruDialogAction(
                        label = stringResource(R.string.oru_install_stop_keep),
                        onClick = { vm.keepWaiting() },
                    ),
                    tone = OruTone.Error,
                    onDismiss = { vm.keepWaiting() },
                )
            }
        }
    }
}
