/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import android.text.format.Formatter
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruAppTint
import app.morphe.manager.ui.oru.components.OruBackdropHost
import app.morphe.manager.ui.oru.components.OruRevealOrigin
import app.morphe.manager.ui.oru.components.oruReducedMotion
import app.morphe.manager.ui.oru.components.OruGlassForm
import app.morphe.manager.ui.oru.components.OruChip
import app.morphe.manager.ui.oru.components.OruGlassSurface
import app.morphe.manager.ui.oru.components.OruHero
import app.morphe.manager.ui.oru.components.oruAmbient
import app.morphe.manager.ui.oru.components.rememberOruAmbientAccent
import app.morphe.manager.ui.oru.components.OruHeroState
import app.morphe.manager.ui.oru.components.OruNotice
import app.morphe.manager.ui.oru.components.OruNoticeAction
import app.morphe.manager.ui.oru.components.OruTileSource
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.components.oruGlassShape
import app.morphe.manager.ui.oru.home.OruHomePresenter
import app.morphe.manager.ui.oru.home.oruAppName
import app.morphe.manager.ui.oru.nav.oruArriveCascade
import app.morphe.manager.ui.oru.nav.oruArriveFromBottom
import app.morphe.manager.ui.oru.nav.oruCtaSharedKey
import app.morphe.manager.ui.oru.nav.oruSharedTile
import app.morphe.manager.ui.oru.nav.oruSharedText
import app.morphe.manager.ui.oru.nav.oruTileSharedKey
import app.morphe.manager.ui.oru.nav.oruTitleSharedKey
import app.morphe.manager.ui.oru.nav.rememberOruPresence
import app.morphe.manager.ui.oru.components.OruSwapText
import app.morphe.manager.ui.oru.sources.OruAppSources
import app.morphe.manager.ui.oru.sources.rememberOruAppSources
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import app.morphe.manager.ui.oru.theme.oruHeroTitleStyle
import app.morphe.manager.ui.oru.theme.rememberOruSurfaceFill
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.util.APK_FILE_MIME_TYPES
import app.morphe.manager.util.rememberAdaptiveFilePicker

/**
 * The Focus shell: the hero that receives the card's shared tile, the title and one line, the notices, the
 * Expert rows and the dock. Drawn for the APK choice, the download step, the wait for the file, the checks, the
 * selection and the ready state. The run and the install have their own layouts (see [OruFocusRunRoute]).
 *
 * The content scrolls under the dock. The dock floats over a [OruBackdropHost], so it frosts what scrolls
 * beneath it, and the content keeps a bottom space equal to the measured dock height.
 */
@Composable
internal fun OruFocusShell(
    state: OruFocusState,
    vm: OruFocusViewModel,
    home: HomeViewModel,
    item: HomeAppItem,
    expert: Boolean,
    tint: OruAppTint,
    onBack: () -> Unit,
    /** Whether Expert changed the app. Simple shows the badge and the reset for it. */
    customized: Boolean,
    onReset: () -> Unit,
    /** Opens the app's info. Simple has no App info button on the card, so the Focus offers it here. Null in Expert. */
    onAppInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val appName = oruAppName(item.packageName, item.displayName)
    val tile = OruTileSource(name = appName, packageInfo = item.packageInfo)
    val shape = OruBlobShape.presetFor(item.id)
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Boolean = { url -> runCatching { uriHandler.openUri(url) }.isSuccess }
    val openFilePicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = { uri -> vm.fileChosen(uri) },
        preferInAppPicker = true,
    )
    val notices = when (state) {
        is OruFocusState.Choose -> state.notices
        is OruFocusState.Ready -> state.notices
        is OruFocusState.Notice -> listOf(state.notice)
        else -> emptyList()
    }
    // The source the app is patched from: the line names it, and the choice of another one is offered beside it
    val sources = rememberOruAppSources(home, item.packageName)
    val line = focusLine(state, vm, home, item, sources)
    val plan = oruDockPlan(
        state = state,
        pending = home.pendingPackageName == item.packageName,
        hasSaved = home.pendingSavedApkInfo != null,
        hasInstalled = home.pendingInstalledApkInfo != null,
    )
    val dockItem = focusDockItems(state, vm, item, openFilePicker, openUrl)
    // A run keeps its dock Solid; the APK steps follow the policy
    val policyFill = rememberOruSurfaceFill()
    val frost = if (state is OruFocusState.Run) OruSurfaceFill.Solid else policyFill
    val showRows = expert && (state is OruFocusState.Choose || state is OruFocusState.Ready)
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(0.dp) }
    val bottomSpace = if (plan != null) dockHeight + GutterDp else GutterDp

    // The focused app's accent glows behind the whole screen, arriving from the card's own tint
    val ambient = rememberOruAmbientAccent(tint)
    // The glow opens from the hero tile: the tile reports its place, and the glow grows from there as Focus arrives
    val glowOrigin = remember { OruRevealOrigin() }
    val reveal = rememberOruPresence(OruMotion.springSettle(oruReducedMotion()))
    OruBackdropHost(
        modifier = modifier.fillMaxSize().oruAmbient(ambient, reveal = reveal, origin = glowOrigin).statusBarsPadding(),
        backdrop = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Without rows the hero group is centred in the space above the dock, so no gap opens above it
                val groupMinHeight = (maxHeight - TopBarSpace - bottomSpace - SectionSpacing).coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = GutterDp, top = TopBarSpace, end = GutterDp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = if (showRows) 0.dp else groupMinHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                        ) {
                            OruHero(
                                state = OruHeroState.Ready,
                                tile = tile,
                                shape = shape,
                                progress = null,
                                compact = showRows,
                                modifier = Modifier.onGloballyPositioned { glowOrigin.coords = it },
                                sharedKey = oruTileSharedKey(item.id),
                            )
                            OruSwapText(
                                text = appName,
                                style = oruHeroTitleStyle(),
                                color = scheme.onSurface,
                                modifier = Modifier.oruSharedText(oruTitleSharedKey(item.id)),
                            )
                            OruSwapText(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            // Simple keeps the app's own settings out of sight, except the badge and its way back
                            if (!expert && customized) {
                                OruChip(text = stringResource(R.string.oru_simple_customized), tone = OruTone.Neutral)
                                TextButton(onClick = onReset) {
                                    Text(stringResource(R.string.oru_simple_reset))
                                }
                            }
                            notices.forEach { notice ->
                                OruFocusNoticeCard(
                                    notice = notice,
                                    appName = appName,
                                    expert = expert,
                                    onAct = { action ->
                                        if (action == OruFocusAction.UseAnother) {
                                            // "Use another file" opens the picker, as "Use a file" does, instead of starting the APK choice again
                                            vm.useAnotherFileTapped()
                                            openFilePicker()
                                        } else {
                                            vm.act(action, item)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (showRows) {
                        // The rows hold no shared element, so they can rise without moving one (see oruArriveCascade)
                        OruFocusRows(
                            vm = vm,
                            home = home,
                            item = item,
                            modifier = Modifier.oruArriveCascade(step = RowsCascadeStep),
                        )
                    }
                    Spacer(Modifier.height(bottomSpace))
                }
            }
        },
        floating = {
            // The back button is a glass pill over the content, blurred by the host
            OruGlassSurface(
                shape = oruGlassShape(OruGlassForm.Pill),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.oru_action_back),
                    )
                }
            }
            // Expert screens name their mode in the top bar, so the mode is never in doubt
            if (expert) {
                OruChip(
                    text = stringResource(R.string.oru_mode_expert),
                    tone = OruTone.Neutral,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 12.dp),
                )
            }
            // Simple's App info: the card's long press opens the same dialog, this is the way from the Focus
            onAppInfo?.let { open ->
                OruGlassSurface(
                    shape = oruGlassShape(OruGlassForm.Pill),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 4.dp),
                ) {
                    IconButton(onClick = open) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.oru_action_app_info),
                        )
                    }
                }
            }
            if (plan != null) {
                OruFocusDock(
                    plan = plan,
                    item = dockItem,
                    frost = frost,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { dockHeight = with(density) { it.size.height.toDp() } }
                        .oruArriveFromBottom(),
                    primarySharedKey = oruCtaSharedKey(item.id),
                )
            }
        },
    )
}

/** The line under the title. Every figure is the app's own (the version the held run or the download step uses). */
@Composable
private fun focusLine(
    state: OruFocusState,
    vm: OruFocusViewModel,
    home: HomeViewModel,
    item: HomeAppItem,
    sources: OruAppSources,
): String =
    when (state) {
        OruFocusState.Checking -> stringResource(R.string.oru_focus_checking)
        is OruFocusState.Choose -> if (state.stage == OruChooseStage.Pick) {
            // An installed app, whichever way it was installed, is the app on the phone, so its version is the line to read.
            // The status is the Home card's, so the Focus does not say "Needs" for an app Home shows as installed.
            // The source is named by the Setup line, not here: the headline says what the APK has to be
            val installedStatus = OruHomePresenter.statusOf(item, handedOff = false, supportedVersions = null)
            if (oruFocusShowsInstalled(installedStatus)) {
                stringResource(R.string.oru_focus_installed_line, item.version)
            } else {
                needsLine(oruAppName(item.packageName, item.displayName), home, sources)
            }
        } else {
            val version = (home.pendingSelectedDownloadVersion ?: home.pendingRecommendedVersion)?.version
            stringResource(R.string.oru_hint_apkmirror, version.orEmpty())
        }
        OruFocusState.WaitingForFile -> stringResource(R.string.oru_hint_return_download)
        is OruFocusState.Ready ->
            stringResource(R.string.oru_focus_ready_patch, vm.held?.params?.selectedApp?.version.orEmpty())
        else -> ""
    }

/** The line of the APK choice in Simple: the app and the version its APK has to be, with no source named. */
@Composable
private fun needsLine(appName: String, home: HomeViewModel, sources: OruAppSources): String {
    val needed = home.pendingSelectedDownloadVersion?.version
        ?: home.pendingRecommendedVersion?.version
        ?: sources.recommendedVersion
    return if (needed == null) {
        stringResource(R.string.oru_focus_pick_line)
    } else {
        stringResource(R.string.oru_focus_needs, appName, needed)
    }
}

/**
 * The label and the handler of each dock action. Every action the rule can pick has one here. The
 * secondary labels are the short ones, so they fit half a row on a 320 dp screen at font scale 1.3.
 */
@Composable
private fun focusDockItems(
    state: OruFocusState,
    vm: OruFocusViewModel,
    item: HomeAppItem,
    openFilePicker: () -> Unit,
    openUrl: (String) -> Boolean,
): (DockAction) -> DockItem {
    val findLabel = stringResource(R.string.oru_action_find_apkmirror)
    val findShortLabel = stringResource(R.string.oru_focus_dock_find_short)
    val savedLabel = stringResource(R.string.oru_focus_dock_saved)
    val installedLabel = stringResource(R.string.oru_focus_dock_installed)
    val useFileLabel = stringResource(R.string.oru_action_use_file)
    val getApkLabel = stringResource(R.string.oru_action_get_apk)
    val openApkMirrorLabel = stringResource(R.string.oru_focus_open_apkmirror)
    val backLabel = stringResource(R.string.oru_action_back)
    val chooseLabel = stringResource(R.string.oru_action_choose_file)
    val openAgainLabel = stringResource(R.string.oru_focus_dock_open_again)
    val appName = oruAppName(item.packageName, item.displayName)
    val context = LocalContext.current
    val startLabel = stringResource(R.string.oru_focus_dock_patch, appName)
    val startEnabled = (state as? OruFocusState.Ready)?.canStart ?: true
    return { action ->
        when (action) {
            DockAction.FindOnApkMirror -> DockItem(label = findLabel, shortLabel = findShortLabel) { vm.findOnApkMirror() }
            DockAction.UseFile -> DockItem(useFileLabel) {
                vm.useFileTapped()
                openFilePicker()
            }
            DockAction.GetApk -> DockItem(getApkLabel) { vm.startFlow(item) }
            DockAction.OpenApkMirror -> DockItem(openApkMirrorLabel) { vm.openApkMirror(openUrl) }
            DockAction.Back -> DockItem(backLabel) { vm.leaveDownloadStep() }
            DockAction.ChooseFile -> DockItem(chooseLabel) {
                vm.chooseFileTapped()
                openFilePicker()
            }
            DockAction.OpenAgain -> DockItem(openAgainLabel) { vm.openApkMirrorAgain() }
            DockAction.UseSavedCopy -> DockItem(savedLabel) { vm.useSavedCopy() }
            DockAction.UseInstalledCopy -> DockItem(installedLabel) { vm.useInstalledCopy() }
            DockAction.StartRun -> DockItem(startLabel, enabled = startEnabled) {
                if (vm.startHeldRun() == HeldRunStart.InProgress) {
                    android.widget.Toast.makeText(context, R.string.oru_run_in_progress, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** A notice with its answers as text buttons (OruNotice). The copy follows the notice's values, see [noticeText]. */
@Composable
private fun OruFocusNoticeCard(
    notice: OruFocusNotice,
    appName: String,
    expert: Boolean,
    onAct: (OruFocusAction) -> Unit,
) {
    OruNotice(
        text = noticeText(notice, appName, expert),
        tone = notice.tone,
        actions = notice.actions.map { action ->
            OruNoticeAction(label = actionLabel(action, appName), onClick = { onAct(action) })
        },
    )
}

@Composable
private fun noticeText(notice: OruFocusNotice, appName: String, expert: Boolean): String {
    val context = LocalContext.current
    val version = notice.version.orEmpty()
    return when (notice.kind) {
        OruNoticeKind.WrongPackage -> stringResource(R.string.oru_error_wrong_app)
        OruNoticeKind.Split -> stringResource(R.string.oru_error_split)
        OruNoticeKind.InvalidSignature -> stringResource(R.string.oru_error_changed)
        // Simple names the app and the version its source can patch at. Expert points to that version in its own words
        OruNoticeKind.Unsupported -> when {
            !expert -> notice.supportedVersion?.let {
                stringResource(R.string.oru_notice_unsupported_simple, appName, version, it)
            } ?: stringResource(R.string.oru_notice_unsupported_simple_none, appName, version)
            notice.supportedVersion != null ->
                stringResource(R.string.oru_notice_unsupported, version, notice.supportedVersion)
            else -> stringResource(R.string.oru_notice_unsupported_none, version)
        }
        OruNoticeKind.ExperimentalOff -> stringResource(R.string.oru_notice_experimental_off, version, appName)
        OruNoticeKind.ExperimentalOn -> stringResource(R.string.oru_notice_experimental_on, version, appName)
        OruNoticeKind.NoCompatible -> stringResource(R.string.oru_error_generic)
        OruNoticeKind.LowStorage -> stringResource(
            R.string.oru_error_low_storage,
            Formatter.formatShortFileSize(context, notice.neededBytes ?: 0L),
        )
        OruNoticeKind.Metered -> stringResource(R.string.oru_banner_metered)
    }
}

@Composable
private fun actionLabel(action: OruFocusAction, appName: String): String = when (action) {
    OruFocusAction.UseAnother -> stringResource(R.string.oru_action_use_another_file)
    OruFocusAction.ProceedSplit -> stringResource(R.string.oru_action_continue)
    OruFocusAction.PatchAnyway, OruFocusAction.PatchAnywayUnsupported ->
        stringResource(R.string.oru_action_patch_anyway, appName)
    OruFocusAction.PickVersion -> stringResource(R.string.oru_notice_action_pick_version)
    OruFocusAction.ShowExperimental -> stringResource(R.string.oru_notice_action_show_experimental)
    OruFocusAction.ProceedExperimental -> stringResource(R.string.oru_action_continue)
    OruFocusAction.Dismiss -> stringResource(R.string.oru_action_dismiss)
    OruFocusAction.FindSupported -> stringResource(R.string.oru_action_find_apkmirror)
    OruFocusAction.Back -> stringResource(R.string.oru_action_back)
}

private val GutterDp = 20.dp
private val TopBarSpace = 56.dp
private val SectionSpacing = 12.dp

/** The rows of Focus are the second section to arrive, after the hero that holds the tile. */
private const val RowsCascadeStep = 1
