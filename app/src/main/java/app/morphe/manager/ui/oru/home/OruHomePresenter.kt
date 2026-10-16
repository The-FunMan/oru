/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.ui.oru.components.OruButtonStyle
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.withNameSuffixes

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
        )
    }

    /**
     * One card. Exposed for tests of a single app. The mode decides what the card carries: Simple keeps one button
     * and no chips, source or hint, Expert keeps all of them. Every other rule is the same in both modes.
     */
    fun card(item: HomeAppItem, input: OruHomeInput): OruHomeCard {
        val handedOff = item.packageName in input.handedOff
        val status = statusOf(item, handedOff, input.supportedVersions[item.packageName])
        val recommended = input.recommendedVersions[item.packageName]
        val needsFile = status == OruHomeCardStatus.NeedsApk || status == OruHomeCardStatus.Waiting
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
            hintVersion = recommended.takeIf { status == OruHomeCardStatus.NeedsApk },
            repatchedPackageName = item.installedApp?.trackingKey,
            source = if (expert) input.sourceNames[item.packageName] else null,
            customized = item.packageName in input.customized,
        )
    }

    /** The status of an app. [supportedVersions] are the version names the sources cover for its package. */
    fun statusOf(
        item: HomeAppItem,
        handedOff: Boolean,
        supportedVersions: Set<String>?,
    ): OruHomeCardStatus = when {
        item.isInstallStatePending -> OruHomeCardStatus.Checking
        item.isInstallStateUnknown -> OruHomeCardStatus.Unverified
        item.isDeleted && !item.isInstallStateNotPatched ->
            if (item.hasSavedCopy) OruHomeCardStatus.SavedCopy else needsFile(handedOff)
        item.isInstallStateNotPatched -> OruHomeCardStatus.UnpatchedInstalled
        item.showsRebuildBadge -> OruHomeCardStatus.UpdateAvailable
        item.installedApp != null -> OruHomeCardStatus.Installed
        item.installedPatchedPackage != null -> OruHomeCardStatus.PatchedElsewhere
        item.hasSavedCopy -> OruHomeCardStatus.SavedCopy
        item.isInstalledOnDevice && supportedVersions?.contains(item.version) == true ->
            OruHomeCardStatus.ReadyOnDevice
        else -> needsFile(handedOff)
    }

    /**
     * The primary action the card of [item] shows, read off the same status [card] uses. The Focus opened from that
     * card reads it too, so the step it opens on is the one the card names. A Focus is open for an app only when no
     * hand-off waits on it, so [handedOff] is false here.
     */
    fun primaryActionOf(item: HomeAppItem, supportedVersions: Set<String>?, expert: Boolean): OruHomeAction =
        primaryFor(statusOf(item, handedOff = false, supportedVersions = supportedVersions), expert)

    private fun needsFile(handedOff: Boolean): OruHomeCardStatus =
        if (handedOff) OruHomeCardStatus.Waiting else OruHomeCardStatus.NeedsApk

    private fun primaryFor(status: OruHomeCardStatus, expert: Boolean): OruHomeAction = when (status) {
        OruHomeCardStatus.Checking -> OruHomeAction.AppInfo
        OruHomeCardStatus.Unverified -> OruHomeAction.CheckAndPatch
        OruHomeCardStatus.NeedsApk -> if (expert) OruHomeAction.FindOnApkMirror else OruHomeAction.GetIt
        OruHomeCardStatus.Waiting -> OruHomeAction.ChooseFile
        OruHomeCardStatus.SavedCopy -> OruHomeAction.PatchFromSaved
        OruHomeCardStatus.ReadyOnDevice,
        OruHomeCardStatus.UnpatchedInstalled -> OruHomeAction.Patch
        OruHomeCardStatus.UpdateAvailable -> OruHomeAction.Update
        OruHomeCardStatus.Installed,
        OruHomeCardStatus.PatchedElsewhere -> OruHomeAction.Open
    }

    private fun secondaryFor(status: OruHomeCardStatus): OruHomeAction? = when (status) {
        OruHomeCardStatus.Checking -> null
        OruHomeCardStatus.Unverified -> OruHomeAction.AppInfo
        OruHomeCardStatus.NeedsApk -> OruHomeAction.UseFile
        OruHomeCardStatus.Waiting -> OruHomeAction.OpenApkMirrorAgain
        OruHomeCardStatus.SavedCopy -> OruHomeAction.UseAnotherFile
        OruHomeCardStatus.ReadyOnDevice -> OruHomeAction.AppInfo
        OruHomeCardStatus.UnpatchedInstalled -> OruHomeAction.AppInfo
        OruHomeCardStatus.UpdateAvailable -> OruHomeAction.AppInfo
        OruHomeCardStatus.Installed,
        OruHomeCardStatus.PatchedElsewhere -> OruHomeAction.PatchAgain
    }

    private fun hintFor(status: OruHomeCardStatus): OruHomeHint? = when (status) {
        OruHomeCardStatus.NeedsApk -> OruHomeHint.ApkMirror
        OruHomeCardStatus.Waiting -> OruHomeHint.ReturnDownload
        OruHomeCardStatus.SavedCopy -> OruHomeHint.SavedCopy
        else -> null
    }

    private val RecommendedThenName: Comparator<HomeAppItem> =
        compareByDescending<HomeAppItem> { it.isPinnedByDefault }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
}

/** The chip tone of a status. Warn asks for something from the user, Ok says the app is ready. */
fun OruHomeCardStatus.tone(): OruTone = when (this) {
    OruHomeCardStatus.Checking -> OruTone.Neutral
    OruHomeCardStatus.Unverified,
    OruHomeCardStatus.NeedsApk,
    OruHomeCardStatus.Waiting,
    OruHomeCardStatus.UnpatchedInstalled -> OruTone.Warn
    OruHomeCardStatus.SavedCopy,
    OruHomeCardStatus.ReadyOnDevice,
    OruHomeCardStatus.Installed,
    OruHomeCardStatus.PatchedElsewhere -> OruTone.Ok
    OruHomeCardStatus.UpdateAvailable -> OruTone.Neutral
}

/** The Simple sentence of a status. Each status has one sentence, so Simple never reads two states at once. */
fun OruHomeCardStatus.simpleSentence(): OruHomeSentence = when (this) {
    OruHomeCardStatus.Checking -> OruHomeSentence.Checking
    OruHomeCardStatus.Unverified -> OruHomeSentence.NotChecked
    OruHomeCardStatus.NeedsApk -> OruHomeSentence.NeedsApk
    OruHomeCardStatus.Waiting -> OruHomeSentence.WaitingApk
    OruHomeCardStatus.SavedCopy,
    OruHomeCardStatus.ReadyOnDevice,
    OruHomeCardStatus.UnpatchedInstalled -> OruHomeSentence.Ready
    OruHomeCardStatus.UpdateAvailable -> OruHomeSentence.UpdateReady
    OruHomeCardStatus.Installed -> OruHomeSentence.UpToDate
    OruHomeCardStatus.PatchedElsewhere -> OruHomeSentence.Patched
}

/** The button look of an action. One filled action per card at most, and it is always the primary one. */
fun OruHomeAction.style(): OruButtonStyle = when (this) {
    OruHomeAction.CheckAndPatch,
    OruHomeAction.ChooseFile,
    OruHomeAction.PatchFromSaved,
    OruHomeAction.GetIt,
    OruHomeAction.Patch,
    OruHomeAction.Update,
    OruHomeAction.Open -> OruButtonStyle.Filled
    OruHomeAction.FindOnApkMirror -> OruButtonStyle.Tonal
    OruHomeAction.AppInfo,
    OruHomeAction.UseFile,
    OruHomeAction.OpenApkMirrorAgain,
    OruHomeAction.UseAnotherFile,
    OruHomeAction.PatchAgain -> OruButtonStyle.Outlined
}
