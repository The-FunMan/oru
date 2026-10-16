/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/MainActivity.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.morphe.manager.domain.batch.BatchTarget
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
// ORU: theme for the oru flavor, see the theme call in onCreate
import app.morphe.manager.ui.oru.theme.OruTheme
import app.morphe.manager.ui.oru.theme.LocalOruAnimationsEnabled
import app.morphe.manager.ui.model.navigation.*
// ORU: oru navigation (routes, shared transition host, Home, Focus)
import app.morphe.manager.ui.oru.nav.OruDestinationScope
import app.morphe.manager.ui.oru.nav.OruFocus
import app.morphe.manager.ui.oru.nav.OruHomeScreen
import app.morphe.manager.ui.oru.nav.OruSharedTransitionHost
import app.morphe.manager.ui.oru.nav.oruDestinations
import app.morphe.manager.ui.oru.nav.oruScreenEnter
import app.morphe.manager.ui.oru.nav.oruScreenExit
import app.morphe.manager.ui.oru.nav.returnToOruFocus
import app.morphe.manager.ui.oru.theme.rememberOruReducedMotion
import app.morphe.manager.ui.oru.components.OruLaunchSplash
import app.morphe.manager.ui.oru.nav.startOruRun
import app.morphe.manager.ui.screen.BatchPatcherScreen
import app.morphe.manager.ui.screen.PatcherScreen
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.shared.RandomBackground
import app.morphe.manager.ui.theme.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.MainViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.androidx.viewmodel.ext.android.getViewModel as getActivityViewModel

class MainActivity : AppCompatActivity() {

    /** Language the activity was attached in, to tell when it has to be recreated. */
    private var attachedLanguage = AppLocale.SYSTEM

    /**
     * Applies the interface scale to the activity context, so every window it opens is drawn at
     * that scale rather than only the composition inside [setContent].
     *
     * On Android 12 and lower the app language is applied here too, since Android has no per-app
     * language there. Both go into one configuration delta, because a context created from
     * another does not keep the overrides the first one was created with.
     */
    override fun attachBaseContext(newBase: Context) {
        // The oru flavor has no UI scale setting, so the default always applies
        val scale = UI_SCALE_DEFAULT

        attachedLanguage = AppLocale.selected.value
        val overrides = Configuration().apply {
            AppLocale.applyTo(this)
            applyUiScale(newBase.resources.configuration.densityDpi, scale)
        }

        super.attachBaseContext(
            if (overrides == Configuration()) newBase else newBase.createConfigurationContext(overrides)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        installSplashScreen()

        // Android 13+ recreates the activity by itself when the app language changes
        if (!AppLocale.appliedBySystem) {
            lifecycleScope.launch {
                AppLocale.selected.first { it != attachedLanguage }
                recreate()
            }
        }

        val vm: MainViewModel = getActivityViewModel()

        // A recreated activity keeps the background RANDOM drew, only a fresh launch draws again
        if (savedInstanceState == null) RandomBackground.newLaunch()

        // Handle deep link on cold start. The task keeps the intent that started it, so a restore
        // after the process was reclaimed - or any recreate - would replay a link already acted on
        if (savedInstanceState == null) {
            handleDeepLinkIntent(intent, vm)
        }

        setContent {
            // The Home quick toggles. Collected here so a change recomposes the whole theme at once
            val oruThemeMode by vm.prefs.oruThemeMode.getAsState()
            val oruGlassStyle by vm.prefs.oruGlassStyle.getAsState()
            // The Settings switch Match my wallpaper colors reaches the theme here
            val oruWallpaperColors by vm.prefs.wallpaperColors.getAsState()
            // The Settings switch Animations. Read once here; every reduced-motion check below follows it
            val oruAnimations by vm.prefs.oruAnimations.getAsState()

            // The oru theme is drawn from the Home quick toggles and ignores the stored theme settings
            CompositionLocalProvider(LocalOruAnimationsEnabled provides oruAnimations) {
                OruTheme(themeMode = oruThemeMode, glassStyle = oruGlassStyle, dynamicColor = oruWallpaperColors) {
                    MorpheManager(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Replaces the launch intent, so a later recreate restores this one rather than the
        // link the task was originally started from
        setIntent(intent)
        handleDeepLinkIntent(intent, getActivityViewModel())
    }

    /**
     * Tells the launcher a shortcut was used, so it can rank the list by use. The id is dropped
     * once reported, or the same activation counts again when the activity is recreated.
     */
    private fun reportShortcutUsage(intent: Intent?) {
        val shortcutId = intent?.getStringExtra(EXTRA_SHORTCUT_ID) ?: return
        intent.removeExtra(EXTRA_SHORTCUT_ID)
        runCatching { ShortcutManagerCompat.reportShortcutUsed(this, shortcutId) }
            .onFailure { Log.w(tag, "Failed to report shortcut usage", it) }
    }

    /** Records what an incoming intent asks for on the view model, to be handled by the screens. */
    private fun handleDeepLinkIntent(intent: Intent?, vm: MainViewModel) {
        reportShortcutUsage(intent)

        // Handled here rather than in onNewIntent so a cold start from a notification or a
        // launcher shortcut triggers the check as well
        if (intent?.getBooleanExtra(UpdateNotificationManager.EXTRA_TRIGGER_UPDATE_CHECK, false) == true) {
            vm.pendingUpdateCheck = true
            return
        }

        // Changelog action of an update notification: checks for nothing on the way in, so the
        // notes are read against the installed version
        if (intent?.action == ACTION_SHOW_BUNDLE_CHANGELOG) {
            vm.pendingBundleChangelogUid = intent.getIntExtra(
                EXTRA_CHANGELOG_BUNDLE_UID,
                PatchBundleRepository.DEFAULT_SOURCE_UID
            )
            return
        }

        // Same action on a manager update notification. This one does resolve the release on
        // the way in, because the notes it shows belong to a version that is not installed yet
        if (intent?.action == ACTION_SHOW_MANAGER_CHANGELOG) {
            vm.pendingManagerChangelog = true
            return
        }

        // Batch result notification: reopens the queue it reports about
        if (intent?.action == ACTION_SHOW_BATCH_RESULT) {
            vm.pendingBatchResult = true
            return
        }

        // Launcher shortcut for one app: opens the usual patch dialog for it
        if (intent?.action == ACTION_PATCH_APP) {
            intent.getStringExtra(EXTRA_PATCH_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?.let { vm.pendingPatchPackage = it }
            return
        }

        // ORU: Notification completion tap returns directly to Focus for that package
        if (intent?.action == ACTION_OPEN_FOCUS) {
            intent.getStringExtra(EXTRA_FOCUS_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?.let { vm.pendingFocusPackage = it }
            return
        }

        // Batch patch requested by another app, for example an automation tool.
        // The request is only recorded here; MorpheManager gates it before anything runs
        if (intent?.action == ACTION_BATCH_PATCH) {
            val singleRunBusy = runCatching {
                GlobalContext.get().getOrNull<app.morphe.manager.ui.oru.focus.SingleRunOwner>()?.isBusy == true
            }.getOrDefault(false)
            if (singleRunBusy) {
                toast(getString(R.string.oru_run_in_progress))
                return
            }

            val packageNames = intent.getStringArrayExtra(EXTRA_BATCH_PACKAGES)?.toList()
                ?: intent.getStringExtra(EXTRA_BATCH_PACKAGES)
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)

            if (packageNames.isNullOrEmpty()) {
                // No list means the launcher shortcut or the re-patch notification, which
                // ask the app to work out what is outdated. Either only opens the preflight
                // list, so neither needs a caller gate
                vm.pendingOutdatedBatch = true
            } else {
                vm.pendingBatchPatch = MainViewModel.BatchPatchRequest(
                    // An external request names apps, so it rebuilds each one's own install
                    targets = packageNames.map { BatchTarget(it) },
                    // Only the system-supplied caller is trustworthy here. getReferrer() reads
                    // EXTRA_REFERRER, which the sender fills in itself, so it could name any
                    // package and both skip the confirmation and misname it in the dialog
                    callerPackage = callingPackage
                )
            }
            return
        }

        // Handle APK-family file shared via system share sheet (.apk/.apks/.xapk/.apkm)
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null && uri.hasApkExtension(contentResolver)) {
                vm.pendingExternalApkUri = uri
            }
            return
        }

        val data = intent?.data ?: return

        // Handle .mpp file open from file manager
        if (intent.action == Intent.ACTION_VIEW && data.scheme in listOf("file", "content")) {
            if (data.hasMppExtension(contentResolver)) {
                vm.pendingMppUri = data
            }
            // Not .mpp - don't process further regardless
            return
        }
    }

    companion object {
        /** Action other apps use to queue a batch patch run. */
        const val ACTION_BATCH_PATCH = "app.morphe.manager.action.BATCH_PATCH"

        /** Package names to patch, either a string array or a comma-separated string. */
        const val EXTRA_BATCH_PACKAGES = "packages"

        /** Identifies the launcher shortcut an intent came from, for usage reporting. */
        const val EXTRA_SHORTCUT_ID = "shortcut_id"

        /** Action behind the per-app launcher shortcuts. */
        const val ACTION_PATCH_APP = "app.morphe.manager.action.PATCH_APP"

        /** Action behind the update check shortcut. Shortcut intents must carry one. */
        const val ACTION_CHECK_UPDATES = "app.morphe.manager.action.CHECK_UPDATES"

        /** Action that reopens the batch queue from an automatic re-patch notification. */
        const val ACTION_SHOW_BATCH_RESULT = "app.morphe.manager.action.SHOW_BATCH_RESULT"

        /** Action behind the changelog button of an update notification. */
        const val ACTION_SHOW_BUNDLE_CHANGELOG = "app.morphe.manager.action.SHOW_BUNDLE_CHANGELOG"

        /** Source whose changelog the update notification opens. */
        const val EXTRA_CHANGELOG_BUNDLE_UID = "changelog_bundle_uid"

        /** Action behind the changelog button of a manager update notification. */
        const val ACTION_SHOW_MANAGER_CHANGELOG = "app.morphe.manager.action.SHOW_MANAGER_CHANGELOG"

        /** Package the per-app shortcut opens the patch dialog for. */
        const val EXTRA_PATCH_PACKAGE = "patch_package"

        /** ORU: Action to return to the Focus screen from a background completion notification. */
        const val ACTION_OPEN_FOCUS = "app.morphe.manager.action.OPEN_FOCUS"

        /** ORU: Target package key for returning to Focus screen. */
        const val EXTRA_FOCUS_PACKAGE = "extra_focus_package"
    }
}

@Composable
private fun MorpheManager(vm: MainViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val prefs: PreferencesManager = koinInject()
    val homeAppButtonPrefs: HomeAppButtonPreferences = koinInject()

    // Patcher background speed - driven by PatcherViewModel when on patcher screen.
    // Exposed as top-level mutable state so PatcherScreen can write into it
    val patcherBackgroundSpeed = remember { mutableFloatStateOf(1f) }
    val patchingCompleted = remember { mutableStateOf(false) }

    // HomeViewModel must be scoped to the Activity, not to a NavBackStackEntry
    val homeViewModel: HomeViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )

    // Handle .mpp file opened from file manager
    LaunchedEffect(vm.pendingMppUri) {
        vm.pendingMppUri?.let { uri ->
            navController.popBackStack(HomeScreen, false)
            homeViewModel.setPendingMpp(uri)
            vm.pendingMppUri = null
        }
    }

    // Gate an incoming external batch request, then run it once approved
    LaunchedEffect(vm.pendingBatchPatch) {
        vm.pendingBatchPatch?.let(vm::onExternalBatchRequest)
    }

    LaunchedEffect(vm.pendingOutdatedBatch) {
        if (vm.pendingOutdatedBatch) vm.onShortcutBatchRequest()
    }

    LaunchedEffect(vm.pendingBatchResult) {
        if (vm.pendingBatchResult) vm.onShowBatchResult()
    }

    // Changelog asked for from an update notification, shown over whichever screen is open
    val patchSources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
    BundleChangelogHost(
        request = vm.pendingBundleChangelogUid?.let { BundleChangelogRequest(it) },
        sources = patchSources,
        onDismissRequest = { vm.pendingBundleChangelogUid = null }
    )

    // The manager has no changelog view of its own for a release it has not installed, so the
    // update dialog is the answer to "what's new": it lists exactly the entries being offered
    if (vm.pendingManagerChangelog) {
        ManagerChangelogDialog(
            onDismiss = { vm.pendingManagerChangelog = false },
            expectsUpdate = true
        )
    }

    // Per-app shortcut reuses the trigger the installed-app dialog already goes through
    LaunchedEffect(vm.pendingPatchPackage) {
        vm.pendingPatchPackage?.let { packageName ->
            vm.pendingPatchPackage = null
            navController.popBackStack(HomeScreen, false)
            navController.getBackStackEntry(HomeScreen)
                .savedStateHandle["patch_trigger_package"] = packageName
        }
    }

    // ORU: Notification completion tap returns directly to Focus for that package
    LaunchedEffect(vm.pendingFocusPackage) {
        vm.pendingFocusPackage?.let { packageKey ->
            vm.pendingFocusPackage = null
            navController.returnToOruFocus(packageKey)
        }
    }

    val context = LocalContext.current
    val nothingToRepatchText = stringResource(R.string.batch_patch_nothing_outdated)
    LaunchedEffect(vm.nothingToRepatch) {
        if (vm.nothingToRepatch) {
            context.toast(nothingToRepatchText)
            vm.consumeNothingToRepatch()
        }
    }

    LaunchedEffect(vm.approvedBatchPatch) {
        vm.approvedBatchPatch?.let { request ->
            vm.consumeApprovedBatch()
            navController.popBackStack(HomeScreen, false)
            navController.navigateComplex(
                BatchPatcher,
                BatchPatcher.ViewModelParams(
                    targets = request.targets,
                    useMount = false
                )
            )
        }
    }

    vm.batchPatchConfirmation?.let { request ->
        ExternalBatchPatchDialog(
            callerPackage = request.callerPackage,
            packageCount = request.targets.size,
            onConfirm = { trustCaller -> vm.approveExternalBatch(trustCaller) },
            onDismiss = vm::dismissExternalBatch
        )
    }

    // Handle .apk file shared via share sheet
    LaunchedEffect(vm.pendingExternalApkUri) {
        vm.pendingExternalApkUri?.let { uri ->
            navController.popBackStack(HomeScreen, false)
            vm.pendingExternalApkUri = null
            homeViewModel.handleExternalApkUri(uri)
        }
    }

    // Shared state between HomeScreen and PatcherScreen for mount install mode.
    // Set by HomeViewModel.resolvePrePatchInstallerChoice()
    val usingMountInstallState = remember { mutableStateOf(false) }

    // The onboarding tour is not mounted in oru. Its entry points still clear the saved app-card
    // filter, since the tour would otherwise point at cards the list does not show
    val startOnboardingTour: () -> Unit = {
        homeAppButtonPrefs.setFilterMode(null)
    }
    val declineOnboardingTour: () -> Unit = {
        scope.launch { prefs.firstLaunch.update(false) }
    }

    // Box with background at the highest level
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // All content on top of background. The host is a SharedTransitionLayout
        OruSharedTransitionHost(enabled = true) {
            // Screens fade and rise, they never scale (see oruScreenEnter)
            val oruReduced = rememberOruReducedMotion()
            NavHost(
                navController = navController,
                startDestination = HomeScreen,
                enterTransition = { oruScreenEnter(oruReduced) },
                exitTransition = { oruScreenExit(oruReduced) },
                popEnterTransition = { oruScreenEnter(oruReduced) },
                popExitTransition = { oruScreenExit(oruReduced) },
                predictivePopEnterTransition = { oruScreenEnter(oruReduced) },
                // A predictive back leaves the Focus the way a plain back does, so the gesture scrubs the same fade
                predictivePopExitTransition = { oruScreenExit(oruReduced) }
            ) {
                composable<HomeScreen> { entry ->
                    // If opened from an FCM notification, trigger an update check.
                    // vm.pendingUpdateCheck is set in onNewIntent() and reset here after handling.
                    LaunchedEffect(vm.pendingUpdateCheck) {
                        if (vm.pendingUpdateCheck) {
                            homeViewModel.patchBundleRepository.updateCheck()
                            homeViewModel.checkForManagerUpdates()
                            vm.pendingUpdateCheck = false
                        }
                    }

                    // The oru Home replaces HomeScreen. A run it starts opens the Focus entry
                    OruDestinationScope(animatedVisibilityScope = this) {
                        OruHomeScreen(
                            homeViewModel = homeViewModel,
                            onCardClick = { id -> navController.navigate(OruFocus(id)) { launchSingleTop = true } },
                            onStartQuickPatch = { params ->
                                entry.lifecycleScope.launch { navController.startOruRun(params) }
                            },
                            onStartBatchPatch = { targets, useMount ->
                                entry.lifecycleScope.launch {
                                    navController.navigateComplex(
                                        BatchPatcher,
                                        BatchPatcher.ViewModelParams(targets = targets, useMount = useMount)
                                    )
                                }
                            },
                            onReturnToRun = { packageKey -> navController.returnToOruFocus(packageKey) },
                        )
                    }
                }

                composable<BatchPatcher> { entry ->
                    val params = entry.getComplexArg<BatchPatcher.ViewModelParams>() ?: return@composable
                    BatchPatcherScreen(
                        targets = params.targets,
                        useMount = params.useMount,
                        onBackClick = {
                            patchingCompleted.value = false
                            navController.popBackStack()
                        },
                        onStartTour = startOnboardingTour,
                        onDeclineTour = declineOnboardingTour,
                        onAppStateChanged = homeViewModel.apps::notifyAppStateChanged,
                        onBackgroundSpeedChange = { patcherBackgroundSpeed.floatValue = it },
                        onPatchingCompleted = { patchingCompleted.value = true }
                    )
                }

                composable<Patcher> { it ->
                    val params = it.getComplexArg<Patcher.ViewModelParams>() ?: return@composable
                    val patcherViewModel: PatcherViewModel = koinViewModel { parametersOf(params) }
                    val leavePatcher: () -> Unit = {
                        patcherViewModel.stopCompletionSound()
                        patcherBackgroundSpeed.floatValue = 1f
                        patchingCompleted.value = false
                        navController.popBackStack()
                    }
                    PatcherScreen(
                        onBackClick = leavePatcher,
                        patcherViewModel = patcherViewModel,
                        usingMountInstall = usingMountInstallState.value,
                        onBackgroundSpeedChange = { patcherBackgroundSpeed.floatValue = it },
                        onPatchingCompleted = { patchingCompleted.value = true },
                        onStartTour = startOnboardingTour,
                        onDeclineTour = declineOnboardingTour,
                        onChangePatches = {
                            leavePatcher()
                            homeViewModel.reopenPatchSelection(params)
                        }
                    )
                }

                // Focus and Advanced
                oruDestinations(navController)
            }
        }

        // Launch splash, above the NavHost so Home composes underneath it. It plays only on a plain launcher start.
        OruLaunchSplash(launchIntent = LocalActivity.current?.intent)
    }
}

// Androidx Navigation does not support storing complex types in route objects, so we have
// to store them inside the saved state handle of the back stack entry instead
private fun <T : Parcelable, R : ComplexParameter<T>> NavController.navigateComplex(
    route: R,
    data: T
) {
    navigate(route)
    getBackStackEntry(route).savedStateHandle["args"] = data
}

private fun <T : Parcelable> NavBackStackEntry.getComplexArg(): T? = savedStateHandle["args"]
