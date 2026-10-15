/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import app.morphe.manager.ui.oru.components.OruTone

/** Which step of the APK hand-off the Focus shows before a file is chosen. */
enum class OruChooseStage {
    /** The APK availability choice: saved copy, installed copy, Find on APKMirror, Use a file. */
    Pick,

    /** The download step: the APKMirror link is known and can be opened. */
    Download,
}

/** The patch run as the Focus reads it. Done is the patcher's success after its short hold; install follows. */
enum class OruRunStatus { Working, Done, Failed, Cancelled }

/** Which notice the Focus shows. The copy is picked by the composable from the kind and the values. */
enum class OruNoticeKind {
    WrongPackage,
    Split,
    InvalidSignature,
    Unsupported,
    ExperimentalOff,
    ExperimentalOn,
    NoCompatible,
    LowStorage,
    Metered,
}

/** An answer a notice offers. The view model maps each one to the call the old dialog made for it. */
enum class OruFocusAction {
    /** Dismiss the notice and look for the APK again. */
    UseAnother,

    /** Merge the split archive and carry on (`proceedWithSplitApk`). */
    ProceedSplit,

    /** Patch despite the APK signature mismatch (`proceedIgnoringSignature`). */
    PatchAnyway,

    /** Patch an unsupported version. The notice's button is the one confirmation. */
    PatchAnywayUnsupported,

    /** Dismiss and choose another version. */
    PickVersion,

    /** Turn experimental versions on for the source, then look again. */
    ShowExperimental,

    /** Carry on with an experimental version the switch allows (`proceedWithExperimentalVersion`). */
    ProceedExperimental,

    /** Dismiss a notice that has nothing to continue with. */
    Dismiss,

    /** Simple: dismiss the notice and start the flow again, which offers the version the source can patch at. */
    FindSupported,

    /** Simple: dismiss the notice and leave the APK flow, as Dismiss does for the notices that have no answer. */
    Back,
}

/**
 * One notice. [blocking] means the notice stops the start of the run until its answer is given.
 * [version] is set on the version notices, [freeBytes] and [neededBytes] on [OruNoticeKind.LowStorage] only.
 */
data class OruFocusNotice(
    val kind: OruNoticeKind,
    val tone: OruTone,
    val actions: List<OruFocusAction>,
    val blocking: Boolean = false,
    val version: String? = null,
    /** The version the app can be patched at from its source, set on [OruNoticeKind.Unsupported]. Null when there is none. */
    val supportedVersion: String? = null,
    /** Free storage on the phone, set on [OruNoticeKind.LowStorage]. */
    val freeBytes: Long? = null,
    /** Storage the run needs on top of what is free, set on [OruNoticeKind.LowStorage]. */
    val neededBytes: Long? = null,
)

/**
 * What the Focus shows. Exactly one of these at a time. Install and the done facts are not here:
 * once the run is [Run] with [OruRunStatus.Done], the install coordinator owns the next screen.
 */
sealed interface OruFocusState {
    /** The file is being read and checked (`processingApkSelection`), or a run is being held. */
    data object Checking : OruFocusState

    /** The APK is needed. The stage says whether the choice or the download step is open. */
    data class Choose(val stage: OruChooseStage, val notices: List<OruFocusNotice>) : OruFocusState

    /** The download step was done and the Focus waits for the file in memory. Not persisted. */
    data object WaitingForFile : OruFocusState

    /** A check failed or needs an answer before the run can start. */
    data class Notice(val notice: OruFocusNotice) : OruFocusState

    /** The patch selection or the root mode is open (expert dialog, source choice, pre-patch step). */
    data object Selecting : OruFocusState

    /** The APK is checked and the run can start with the Patch button. */
    data class Ready(
        val notices: List<OruFocusNotice>,
        val canStart: Boolean,
    ) : OruFocusState

    /** A patch run is in progress or has ended. */
    data class Run(val status: OruRunStatus) : OruFocusState
}

/**
 * The inputs of [OruFocusReducer]. Each field is read from one existing flag or value; the names say
 * which. Plain data, so the table tests build it without any view model.
 */
data class OruFocusFlags(
    /** HomeViewModel.pendingPackageName is this app. */
    val pendingForApp: Boolean = false,
    /** HomeViewModel.processingApkSelection, or the view model is still holding a run. */
    val processing: Boolean = false,
    /** HomeViewModel.showApkAvailabilityDialog. */
    val pickOpen: Boolean = false,
    /** HomeViewModel.showDownloadInstructionsDialog. */
    val downloadOpen: Boolean = false,
    /** HomeViewModel.showFilePickerPromptDialog, set in memory only after the hand-off. */
    val waitingForFile: Boolean = false,
    /** HomeViewModel.showWrongPackageDialog, the package the file actually holds. Null when none. */
    val wrongPackage: String? = null,
    /** HomeViewModel.showSplitApkWarningDialog. */
    val splitApk: Boolean = false,
    /** HomeViewModel.showInvalidSignatureDialog is set. */
    val invalidSignature: Boolean = false,
    /** HomeViewModel.showUnsupportedVersionDialog: the version. Null when not raised. */
    val unsupportedVersion: String? = null,
    /** The version the app's source can patch at, the one an unsupported version is pointed to. */
    val supportedVersion: String? = null,
    /** The unsupported dialog is for an experimental version, which the switch below has turned off. */
    val unsupportedIsExperimental: Boolean = false,
    /** HomeViewModel.showExperimentalVersionDialog: the version, raised while the switch is on. */
    val experimentalVersion: String? = null,
    /** HomeViewModel.showNoCompatibleVersionsDialog is this app. */
    val noCompatibleVersions: Boolean = false,
    /** Expert mode. Simple answers a version the app cannot be patched at with the source's version, and no patch-anyway. */
    val expert: Boolean = true,
    /** HomeViewModel.showExpertModeDialog or showPrePatchInstallerDialog. */
    val selecting: Boolean = false,
    /** The view model holds the run parameters the flow produced (the run has not started yet). */
    val held: Boolean = false,
    /** Size of the APK the run would patch, when it is known. */
    val apkBytes: Long? = null,
    /** Free storage on the phone, when it is known. */
    val freeBytes: Long? = null,
    /** HomeViewModel.isOnMeteredWithUpdatesDisabled(). A banner, never a block (D18). */
    val metered: Boolean = false,
    /** The current run as the view model reports it. Null when no run is shown. */
    val run: OruRunStatus? = null,
)
