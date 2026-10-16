/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.model.HomeAppItem

/**
 * Where a Home card stands, read off the install state of its app. The presenter picks the first
 * status whose rule holds, see [OruHomePresenter.statusOf].
 */
enum class OruHomeCardStatus {
    /** The install state is still being read from the device. */
    Checking,

    /** The install is not a state Oru can describe, so its signature has to be checked first. */
    Unverified,

    /** No APK for the app is on the phone, and the APKMirror hand-off has not happened yet. */
    NeedsApk,

    /** The APKMirror hand-off happened this session and the APK is still to be picked. */
    Waiting,

    /** A patched copy from an earlier run is kept on the phone and can be patched again. */
    SavedCopy,

    /** A patched copy is kept on the phone, but the sources do not patch its version, so the APK has to be found again. */
    SavedCopyGated,

    /** The app is on the phone, unpatched by Oru, and the sources cover its version. */
    ReadyOnDevice,

    /** A version Oru has not patched is installed. */
    UnpatchedInstalled,

    /** The installed patched copy can be rebuilt with newer patches or a newer supported version. */
    UpdateAvailable,

    /** A patched copy is installed and current. */
    Installed,

    /**
     * A patched copy is installed that this manager did not make, so it keeps no record of it. It is shown as
     * installed, with the copy's own icon and version, and can be patched again from the app's stock APK.
     */
    PatchedElsewhere,

    /** A finished run of this app waits for its install, and nothing has started it yet. See [OruHomeInput.readyToInstall]. */
    ReadyToInstall,
}

/**
 * What a card button does. The screen turns each one into a call on HomeViewModel, see
 * [OruHomeActionRouter].
 */
enum class OruHomeAction {
    AppInfo,
    CheckAndPatch,
    FindOnApkMirror,
    UseFile,
    ChooseFile,
    OpenApkMirrorAgain,
    PatchFromSaved,
    UseAnotherFile,

    /** Simple mode's way to the APK choice: opens the Focus of the app, where the APK is found or picked. */
    GetIt,
    Patch,
    Update,
    Open,
    PatchAgain,

    /** Opens the Focus of a finished run, where its install is. */
    Install,
}

/** The line under the status chip that says what happens next. */
enum class OruHomeHint { ApkMirror, ReturnDownload, SavedCopy, SavedCopyGated }

/** The one sentence a Simple card says about its app. Expert reads the status as chips instead, see [OruHomeCardStatus]. */
enum class OruHomeSentence { Checking, NotChecked, NeedsApk, WaitingApk, SavedCopyGated, Ready, ReadyToInstall, UpdateReady, UpToDate, Patched }

/** One card on Home, already decided. The composable only turns its parts into strings. */
@Immutable
data class OruHomeCard(
    val item: HomeAppItem,
    val status: OruHomeCardStatus,
    val primary: OruHomeAction,
    val secondary: OruHomeAction?,
    val chipTone: OruTone,
    /** The version the card names, or null when there is none to show. Simple reads it for the APK sentence only. */
    val version: String?,
    /** The version the sources recommend, shown as the Latest chip of an app that can be updated. */
    val latestVersion: String? = null,
    /** Real patch count of the app across the enabled sources. Zero means no patches are known, and Simple shows none. */
    val patchCount: Int,
    /** The line that says what happens next. Expert only. */
    val hint: OruHomeHint?,
    /** The version the APKMirror hint names, when the sources name one. */
    val hintVersion: String?,
    /** The tracked install a rebuild starts from, or null when the app has no install of its own. */
    val repatchedPackageName: String?,
    /** The name of the source the app is patched from, when more than one source can patch it. Expert only. */
    val source: String? = null,
    /** Whether Expert changed this app from the recommended setup. Simple shows a badge for it. */
    val customized: Boolean = false,
) {
    val id: String get() = item.id
    val packageName: String get() = item.packageName
}

/** Everything the Home screen draws. [cards] is empty while [loading] or when [empty]. */
@Immutable
data class OruHomeUiState(
    val loading: Boolean,
    val expert: Boolean,
    val cards: List<OruHomeCard>,
    val banner: OruHomeBanner?,
    /** No network connection, see [OruHomeInput.offline]. Read by the empty state only. */
    val offline: Boolean = false,
) {
    /** No card to show and nothing left to load: the empty state. */
    val empty: Boolean get() = !loading && cards.isEmpty()
}

/**
 * The run the accessory above the island shows: the app a batch run is patching now, or the app of a single-app run.
 * [progress] is 0 to 1, or null while it is not known; [done] and [total] count the patches of the app.
 * [focusKey] is the Focus of a single-app run, which the accessory opens; null for a batch run, which opens the batch.
 * [ready] is a finished single-app run waiting to be installed; the accessory says so.
 */
@Immutable
data class OruHomeRun(
    val packageName: String,
    val appName: String,
    val packageInfo: PackageInfo?,
    val progress: Float?,
    val done: Int,
    val total: Int,
    val focusKey: String?,
    val ready: Boolean = false,
)

/** What the presenter reads from the Home view model. A null [items] means the list is not loaded. */
data class OruHomeInput(
    val items: List<HomeAppItem>?,
    val installedAppsLoading: Boolean,
    val expert: Boolean,
    /** Packages whose APKMirror hand-off happened this session, see [OruHomeCard]. */
    val handedOff: Set<String>,
    /** Recommended version name per package, from the source the app is patched from. */
    val recommendedVersions: Map<String, String>,
    /** The versions of the source each app is patched from, read by [oruVersionGate] for the version the card names. */
    val effectiveVersions: Map<String, List<BundledAppTarget>>,
    /** Patch count per package, from the source the app is patched from. */
    val patchCounts: Map<String, Int>,
    /** Name of the source to show as a chip, per package, for an app more than one source can patch. */
    val sourceNames: Map<String, String> = emptyMap(),
    /** Packages Expert set up differently from the recommended setup, see [oruAppOverrides]. */
    val customized: Set<String> = emptySet(),
    /**
     * The ids of the cards whose finished single-app run waits to be installed, see [oruReadyToInstallOf]. Such a card
     * says so and offers the install. It stops once the install starts or the run is released.
     */
    val readyToInstall: Set<String> = emptySet(),
    val banner: OruBannerInput,
    /** No network connection. An empty list with no connection is the network's fault, so the empty state says so. */
    val offline: Boolean = false,
)
