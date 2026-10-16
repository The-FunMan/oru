/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruRow
import app.morphe.manager.ui.oru.components.oruSlab
import app.morphe.manager.ui.oru.focus.OruFocusViewModel
import app.morphe.manager.ui.oru.focus.customize.oruBrandingTargets
import app.morphe.manager.ui.oru.focus.oruFocusShowsInstalled
import app.morphe.manager.ui.oru.home.OruHomePresenter
import app.morphe.manager.ui.oru.sources.rememberOruAppSources
import app.morphe.manager.ui.oru.theme.OruGroupInnerShape
import app.morphe.manager.ui.oru.theme.OruGroupShape
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.screen.shared.ConfirmDialog
import app.morphe.manager.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * What the Expert Focus reads for its one summary line and its Setup sheet: the one source the app is patched from,
 * its channel, the version the APK is for, the patches of a held run, and the installer. Every value is the app's own.
 */
internal data class OruSetup(
    /** The name of the app's source, or null when no source covers the app. */
    val sourceLabel: String?,
    /** The patch version of that source, when it has one. */
    val patchVersion: String?,
    /** More than one source covers the app, so the source can be changed. */
    val sourceChoosable: Boolean,
    /** The channel of the app's source, or null when none covers the app. */
    val channel: OruChannelSummary?,
    /** The source whose pre-release switch the channel row changes, when it supports one. */
    val channelSource: PatchBundleSource?,
    /** The version the APK is for: the held run's, else the version the download would ask for. */
    val appVersion: String?,
    /** Patches selected by the held run, or null while no run is held. */
    val selectedPatches: Int?,
    /** Patches the app's source has for the app. */
    val patchTotal: Int,
    /** The installer the run installs with, by its label. */
    val installer: String?,
    /** The held run selects patches that declare branding, so Customize is offered. */
    val hasBranding: Boolean,
    /** A saved copy of the app is kept, so it can be patched again. */
    val hasSavedCopy: Boolean,
    /** The app is installed, so [appVersion] is the next patch target, labelled as one. */
    val appVersionIsNext: Boolean = false,
)

/** One part of the summary line. A part is left out when its value is not known. */
internal sealed interface OruSetupPart {
    data class Source(val label: String, val patchVersion: String?) : OruSetupPart
    data class Channel(val summary: OruChannelSummary) : OruSetupPart
    data class AppVersion(val version: String, val next: Boolean = false) : OruSetupPart
    data class Patches(val selected: Int, val total: Int) : OruSetupPart
    data class Installer(val label: String) : OruSetupPart
}

/** The parts of the summary line, in its order: source, channel, version, patches, installer. */
internal fun oruSetupParts(setup: OruSetup): List<OruSetupPart> = buildList {
    setup.sourceLabel?.let { add(OruSetupPart.Source(it, setup.patchVersion)) }
    setup.channel?.let { add(OruSetupPart.Channel(it)) }
    setup.appVersion?.let { add(OruSetupPart.AppVersion(it, setup.appVersionIsNext)) }
    setup.selectedPatches?.let { add(OruSetupPart.Patches(it, setup.patchTotal)) }
    setup.installer?.let { add(OruSetupPart.Installer(it)) }
}

/** The setup of the app, read off the Focus and the sources. Read again whenever they change. */
@Composable
internal fun rememberOruSetup(vm: OruFocusViewModel, home: HomeViewModel, item: HomeAppItem): OruSetup {
    val originals: OriginalApkRepository = koinInject()
    val packageName = item.packageName
    val appSources = rememberOruAppSources(home, packageName)
    val effective = appSources.effective
    val loadedSources by home.patchBundleRepository.sources.collectAsStateWithLifecycle()
    val held = vm.held
    var hasSavedCopy by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(packageName) {
        hasSavedCopy = originals.get(packageName) != null
    }
    val effectiveSource = loadedSources.firstOrNull { it.uid == effective?.uid }
    val channels = oruChannelStates(
        listOfNotNull(effectiveSource).map { source ->
            OruChannelSource(
                uid = source.uid,
                name = oruSourceDisplayName(source),
                usesPrerelease = source.usesPrerelease,
                preReleaseSupported = oruPreReleaseSupported(source),
            )
        },
    )
    val branding = held?.let { current ->
        val patches = home.getPatchesForPackage(packageName)
        oruBrandingTargets(current.params.selectedPatches) { uid, name ->
            patches[uid].orEmpty()
                .firstOrNull { it.name == name }
                ?.options
                ?.mapTo(mutableSetOf<String>()) { it.key }
                .orEmpty()
        }
    }.orEmpty()
    return OruSetup(
        sourceLabel = appSources.effectiveLabel,
        patchVersion = effective?.patchVersion,
        sourceChoosable = appSources.choosable,
        channel = oruChannelSummary(channels),
        channelSource = effectiveSource?.takeIf { oruPreReleaseSupported(it) },
        appVersion = held?.params?.selectedApp?.version
            ?: (home.pendingSelectedDownloadVersion ?: home.pendingRecommendedVersion)?.version,
        selectedPatches = held?.params?.selectedPatches?.values?.sumOf { it.size },
        patchTotal = effective?.patchCount ?: 0,
        installer = vm.pickedInstaller?.label ?: vm.primaryInstallerLabel,
        hasBranding = branding.isNotEmpty(),
        hasSavedCopy = hasSavedCopy,
        // Once the app is installed, the version the run would download is the next patch, not the one for this APK
        appVersionIsNext = held == null &&
            oruFocusShowsInstalled(OruHomePresenter.statusOf(item, handedOff = false, targets = null)),
    )
}

/**
 * The summary as two lines: the source and channel, then the version, patches and installer. Each line joins its parts
 * with a middle dot. A line with no parts is left out, so the list may hold one line or none.
 */
@Composable
internal fun oruSetupSummary(setup: OruSetup): List<String> {
    val (sourceParts, detailParts) = oruSetupParts(setup).partition { part ->
        part is OruSetupPart.Source || part is OruSetupPart.Channel
    }
    val sourceLine = sourceParts.map { part -> oruSetupPartText(part) }.joinToString(separator = SummarySeparator)
    val detailLine = detailParts.map { part -> oruSetupPartText(part) }.joinToString(separator = SummarySeparator)
    return listOf(sourceLine, detailLine).filter { it.isNotEmpty() }
}

/** One part of the summary as text. */
@Composable
private fun oruSetupPartText(part: OruSetupPart): String = when (part) {
    is OruSetupPart.Source -> part.patchVersion
        ?.let { stringResource(R.string.oru_focus_source_row_value, part.label, it) }
        ?: part.label
    is OruSetupPart.Channel -> when (val summary = part.summary) {
        OruChannelSummary.Stable -> stringResource(R.string.oru_focus_channel_stable)
        OruChannelSummary.PreRelease -> stringResource(R.string.oru_focus_channel_prerelease)
        is OruChannelSummary.Mixed -> stringResource(R.string.oru_focus_channel_mixed, summary.preRelease, summary.sources)
    }
    is OruSetupPart.AppVersion -> if (part.next) stringResource(R.string.oru_focus_next_patch, part.version) else part.version
    is OruSetupPart.Patches -> stringResource(R.string.oru_focus_summary_patches, part.selected, part.total)
    is OruSetupPart.Installer -> part.label
}

/**
 * The Setup sheet of the Expert Focus: every setting of the app in one place, in the order Version, Source, Channel,
 * Patches, Installer, then the two rows that were under More options. The rows that change the run open their own
 * sheets, which return here when they close. Version is changed only before the run starts.
 */
@Composable
internal fun ColumnScope.OruSetupContent(
    host: OruExpertSheetHost,
    vm: OruFocusViewModel,
    home: HomeViewModel,
    item: HomeAppItem,
    choosing: Boolean,
) {
    val setup = rememberOruSetup(vm, home, item)
    val scope = rememberCoroutineScope()
    // Enabling pre-release waits for confirmation, as the source list does
    var prereleaseToConfirm by remember { mutableStateOf<PatchBundleSource?>(null) }
    // The write the source list makes. The changelog cache holds the branch that was just left, so it goes first
    val setPrerelease: (PatchBundleSource, Boolean) -> Unit = { source, usePrerelease ->
        (source as? RemotePatchBundle)?.clearChangelogCache()
        scope.launch { home.patchBundleRepository.setUsePrerelease(source.uid, usePrerelease) }
    }

    val openSetup = OruExpertSheet.Setup
    // The rows below reopen the patch selection or the flow, so this sheet closes before they open
    val openVersion: () -> Unit = { host.open(OruExpertSheet.Version, returnTo = openSetup) }
    val openSources: () -> Unit = { host.open(OruExpertSheet.Sources, returnTo = openSetup) }
    val openInstaller: () -> Unit = { host.open(OruExpertSheet.Installer, returnTo = openSetup) }
    val openPatches: () -> Unit = {
        host.close()
        host.openWhenSelecting(OruExpertSheet.Patches)
        vm.changePatches()
    }
    val openCustomize: () -> Unit = {
        host.close()
        host.openWhenSelecting(OruExpertSheet.Customize)
        vm.changePatches()
    }
    val repatch: () -> Unit = {
        host.close()
        vm.startFlow(item)
    }
    val channelSource = setup.channelSource
    val toggleChannel: (() -> Unit)? = channelSource?.let { source ->
        {
            if (source.usesPrerelease) setPrerelease(source, false) else prereleaseToConfirm = source
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.oru_setup_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.oru_setup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SheetGutter)
            .oruSlab(
                shape = OruGroupShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                overContent = true,
            ),
        verticalArrangement = Arrangement.spacedBy(OruSpacing.GroupGap),
    ) {
        OruRow(
            title = stringResource(R.string.oru_focus_version),
            value = setup.appVersion?.let {
                if (setup.appVersionIsNext) stringResource(R.string.oru_focus_next_patch, it) else it
            },
            onClick = if (choosing) openVersion else null,
            shape = OruGroupInnerShape,
        )
        if (setup.sourceChoosable && setup.sourceLabel != null) {
            OruRow(
                title = stringResource(R.string.oru_focus_source_row_title),
                value = setup.patchVersion?.let {
                    stringResource(R.string.oru_focus_source_row_value, setup.sourceLabel, it)
                } ?: setup.sourceLabel,
                onClick = openSources,
                shape = OruGroupInnerShape,
            )
        }
        setup.channel?.let { summary ->
            OruRow(
                title = stringResource(R.string.oru_focus_channel),
                value = when (summary) {
                    OruChannelSummary.Stable -> stringResource(R.string.oru_focus_channel_stable)
                    OruChannelSummary.PreRelease -> stringResource(R.string.oru_focus_channel_prerelease)
                    // One source is shown, so the channels never mix; the stable label is the fallback
                    is OruChannelSummary.Mixed -> stringResource(R.string.oru_focus_channel_stable)
                },
                onClick = toggleChannel,
                shape = OruGroupInnerShape,
            )
        }
        setup.selectedPatches?.let { selected ->
            OruRow(
                title = stringResource(R.string.oru_focus_patches),
                value = stringResource(R.string.oru_focus_patches_selected, selected, setup.patchTotal),
                onClick = openPatches,
                shape = OruGroupInnerShape,
            )
        }
        OruRow(
            title = stringResource(R.string.oru_focus_install_with),
            value = setup.installer,
            onClick = openInstaller,
            shape = OruGroupInnerShape,
        )
        if (setup.hasBranding) {
            OruRow(
                title = stringResource(R.string.oru_focus_customize),
                value = null,
                onClick = openCustomize,
                shape = OruGroupInnerShape,
            )
        }
        if (setup.hasSavedCopy) {
            OruRow(
                title = stringResource(R.string.oru_focus_repatch),
                value = stringResource(R.string.oru_focus_repatch_value),
                onClick = repatch,
                shape = OruGroupInnerShape,
            )
        }
    }

    OruOutlinedButton(
        text = stringResource(R.string.oru_action_close),
        onClick = { host.close() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = 12.dp),
    )

    prereleaseToConfirm?.let { source ->
        ConfirmDialog(
            title = stringResource(R.string.sources_prerelease_warning_title),
            message = stringResource(R.string.sources_prerelease_warning_message),
            primaryText = stringResource(R.string.enable),
            isPrimaryDestructive = false,
            onDismiss = { prereleaseToConfirm = null },
            onConfirm = {
                prereleaseToConfirm = null
                setPrerelease(source, true)
            },
        )
    }
}

private const val SummarySeparator = " · "
private val SheetGutter = 20.dp
