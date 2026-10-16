/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.ui.oru.components.OruButtonStyle
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.withNameSuffixes
import app.morphe.manager.ui.oru.sources.oruVersionGate

/**
 * Maps the Home view model state to the Home cards and banner. Pure: no Android or Compose types
 * are read here, so every rule is unit tested on the JVM.
 *
 * Card rules follow the table in IMPLEMENTATION_PLAN.md section 3.1, with the Oru button set from
 * the prototype. The first matching status wins.
 */
object OruHomePresenter {

    fun state(input: OruHomeInput): OruHomeUiState {
        // Names are cleaned before the sort and the clone suffixes, so both read the names as they are shown
        val items = input.items.orEmpty()
            .map { it.copy(displayName = oruAppName(it.packageName, it.displayName), nameSuffix = null) }
            .withNameSuffixes()
            .sortedWith(RecommendedThenName)
        return OruHomeUiState(
            loading = input.items == null || input.installedAppsLoading,
            expert = input.expert,
            cards = items.map { card(it, input) },
            banner = pickOruHomeBanner(
                input = input.banner,
                // The same apps the card badges and the batch queue pick, see HomeScreen's repatchableApps
                repatchCount = items.count { it.showsUpdateBadge },
            ),
            offline = input.offline,
        )
    }

    /**
     * One card. Exposed for tests of a single app. The mode decides what the card carries: Simple keeps one button
     * and no chips, source or hint, Expert keeps all of them. Every other rule is the same in both modes.
     */
    fun card(item: HomeAppItem, input: OruHomeInput): OruHomeCard {
        val handedOff = item.packageName in input.handedOff
        // A finished run that waits for its install is the newest state of the app, so it outranks the install state
        val status = if (item.id in input.readyToInstall) {
            OruHomeCardStatus.ReadyToInstall
        } else {
            statusOf(item, handedOff, input.effectiveVersions[item.packageName])
        }
        val recommended = input.recommendedVersions[item.packageName]
        val needsFile = status in NeedsFileStatuses
        val expert = input.expert

        return OruHomeCard(
            item = item,
            status = status,
            primary = primaryFor(status, expert),
            secondary = if (expert) secondaryFor(status) else null,
            chipTone = status.tone(),
            version = if (needsFile) recommended else item.version.takeIf { it.isNotBlank() },
            latestVersion = recommended.takeIf { status == OruHomeCardStatus.UpdateAvailable },
            patchCount = if (expert) input.patchCounts[item.packageName] ?: 0 else 0,
            hint = if (expert) hintFor(status) else null,
            hintVersion = recommended.takeIf { status in NeedsFileStatuses },
            repatchedPackageName = item.installedApp?.trackingKey,
            source = if (expert) input.sourceNames[item.packageName] else null,
            customized = item.packageName in input.customized,
        )
    }

    /**
     * The status of an app. [targets] are the versions of the source the app is patched from, see [oruVersionGate]: a
     * saved copy or an app on the phone is ready only when that gate lets its version through.
     */
    fun statusOf(
        item: HomeAppItem,
        handedOff: Boolean,
        targets: List<BundledAppTarget>?,
    ): OruHomeCardStatus {
        val patchable = oruVersionGate(item.version, null, targets.orEmpty()).patchable
        val savedCopy = if (patchable) OruHomeCardStatus.SavedCopy else OruHomeCardStatus.SavedCopyGated
        return when {
            item.isInstallStatePending -> OruHomeCardStatus.Checking
            item.isInstallStateUnknown -> OruHomeCardStatus.Unverified
            item.isDeleted && !item.isInstallStateNotPatched ->
                if (item.hasSavedCopy) savedCopy else needsFile(handedOff)
            item.isInstallStateNotPatched -> OruHomeCardStatus.UnpatchedInstalled
            item.showsRebuildBadge -> OruHomeCardStatus.UpdateAvailable
            item.installedApp != null -> OruHomeCardStatus.Installed
            item.installedPatchedPackage != null -> OruHomeCardStatus.PatchedElsewhere
            item.hasSavedCopy -> savedCopy
            // Without version data from the sources there is nothing to patch the app with, so it is not ready
            item.isInstalledOnDevice && patchable && !targets.isNullOrEmpty() -> OruHomeCardStatus.ReadyOnDevice
            else -> needsFile(handedOff)
        }
    }

    /**
     * The primary action the card of [item] shows, read off the same status [card] uses. The Focus opened from that
     * card reads it too, so the step it opens on is the one the card names. A Focus is open for an app only when no
     * hand-off waits on it, so [handedOff] is false here.
     */
    fun primaryActionOf(item: HomeAppItem, targets: List<BundledAppTarget>?, expert: Boolean): OruHomeAction =
        primaryFor(statusOf(item, handedOff = false, targets = targets), expert)

    private fun needsFile(handedOff: Boolean): OruHomeCardStatus =
        if (handedOff) OruHomeCardStatus.Waiting else OruHomeCardStatus.NeedsApk

    private fun primaryFor(status: OruHomeCardStatus, expert: Boolean): OruHomeAction = when (status) {
        OruHomeCardStatus.Checking -> OruHomeAction.AppInfo
        OruHomeCardStatus.Unverified -> OruHomeAction.CheckAndPatch
        OruHomeCardStatus.NeedsApk,
        OruHomeCardStatus.SavedCopyGated -> if (expert) OruHomeAction.FindOnApkMirror else OruHomeAction.GetIt
        OruHomeCardStatus.Waiting -> OruHomeAction.ChooseFile
        OruHomeCardStatus.SavedCopy -> OruHomeAction.PatchFromSaved
        OruHomeCardStatus.ReadyOnDevice,
        OruHomeCardStatus.UnpatchedInstalled -> OruHomeAction.Patch
        OruHomeCardStatus.UpdateAvailable -> OruHomeAction.Update
        OruHomeCardStatus.Installed,
        OruHomeCardStatus.PatchedElsewhere -> OruHomeAction.Open
        OruHomeCardStatus.ReadyToInstall -> OruHomeAction.Install
    }

    private fun secondaryFor(status: OruHomeCardStatus): OruHomeAction? = when (status) {
        OruHomeCardStatus.Checking -> null
        OruHomeCardStatus.Unverified -> OruHomeAction.AppInfo
        OruHomeCardStatus.NeedsApk,
        OruHomeCardStatus.SavedCopyGated -> OruHomeAction.UseFile
        OruHomeCardStatus.Waiting -> OruHomeAction.OpenApkMirrorAgain
        OruHomeCardStatus.SavedCopy -> OruHomeAction.UseAnotherFile
        OruHomeCardStatus.ReadyOnDevice -> OruHomeAction.AppInfo
        OruHomeCardStatus.UnpatchedInstalled -> OruHomeAction.AppInfo
        OruHomeCardStatus.UpdateAvailable -> OruHomeAction.AppInfo
        OruHomeCardStatus.Installed,
        OruHomeCardStatus.PatchedElsewhere -> OruHomeAction.PatchAgain
        OruHomeCardStatus.ReadyToInstall -> null
    }

    private fun hintFor(status: OruHomeCardStatus): OruHomeHint? = when (status) {
        OruHomeCardStatus.NeedsApk -> OruHomeHint.ApkMirror
        OruHomeCardStatus.Waiting -> OruHomeHint.ReturnDownload
        OruHomeCardStatus.SavedCopy -> OruHomeHint.SavedCopy
        OruHomeCardStatus.SavedCopyGated -> OruHomeHint.SavedCopyGated
        else -> null
    }

    /** The statuses that need the APK from APKMirror, so the card names the version it has to be. */
    private val NeedsFileStatuses = setOf(
        OruHomeCardStatus.NeedsApk,
        OruHomeCardStatus.Waiting,
        OruHomeCardStatus.SavedCopyGated,
    )

    private val RecommendedThenName: Comparator<HomeAppItem> =
        compareByDescending<HomeAppItem> { it.isPinnedByDefault }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
}

/** The chip tone of a status. Warn asks for something from the user, Ok says the app is ready. */
fun OruHomeCardStatus.tone(): OruTone = when (this) {
    OruHomeCardStatus.Checking -> OruTone.Neutral
    OruHomeCardStatus.Unverified,
    OruHomeCardStatus.NeedsApk,
    OruHomeCardStatus.SavedCopyGated,
    OruHomeCardStatus.Waiting,
    OruHomeCardStatus.UnpatchedInstalled -> OruTone.Warn
    OruHomeCardStatus.SavedCopy,
    OruHomeCardStatus.ReadyOnDevice,
    OruHomeCardStatus.ReadyToInstall,
    OruHomeCardStatus.Installed,
    OruHomeCardStatus.PatchedElsewhere -> OruTone.Ok
    OruHomeCardStatus.UpdateAvailable -> OruTone.Neutral
}

/** The Simple sentence of a status. Each status has one sentence, so Simple never reads two states at once. */
fun OruHomeCardStatus.simpleSentence(): OruHomeSentence = when (this) {
    OruHomeCardStatus.Checking -> OruHomeSentence.Checking
    OruHomeCardStatus.Unverified -> OruHomeSentence.NotChecked
    OruHomeCardStatus.NeedsApk -> OruHomeSentence.NeedsApk
    OruHomeCardStatus.SavedCopyGated -> OruHomeSentence.SavedCopyGated
    OruHomeCardStatus.Waiting -> OruHomeSentence.WaitingApk
    OruHomeCardStatus.SavedCopy,
    OruHomeCardStatus.ReadyOnDevice,
    OruHomeCardStatus.UnpatchedInstalled -> OruHomeSentence.Ready
    OruHomeCardStatus.UpdateAvailable -> OruHomeSentence.UpdateReady
    OruHomeCardStatus.Installed -> OruHomeSentence.UpToDate
    OruHomeCardStatus.PatchedElsewhere -> OruHomeSentence.Patched
    OruHomeCardStatus.ReadyToInstall -> OruHomeSentence.ReadyToInstall
}

/** The button look of an action. One filled action per card at most, and it is always the primary one. */
fun OruHomeAction.style(): OruButtonStyle = when (this) {
    OruHomeAction.CheckAndPatch,
    OruHomeAction.ChooseFile,
    OruHomeAction.PatchFromSaved,
    OruHomeAction.Patch,
    OruHomeAction.Update,
    OruHomeAction.Open,
    OruHomeAction.Install -> OruButtonStyle.Filled
    // A card that still needs its APK leads with the find step, so the ready apps keep the filled buttons
    OruHomeAction.FindOnApkMirror,
    OruHomeAction.GetIt -> OruButtonStyle.Tonal
    OruHomeAction.AppInfo,
    OruHomeAction.UseFile,
    OruHomeAction.OpenApkMirrorAgain,
    OruHomeAction.UseAnotherFile,
    OruHomeAction.PatchAgain -> OruButtonStyle.Outlined
}
