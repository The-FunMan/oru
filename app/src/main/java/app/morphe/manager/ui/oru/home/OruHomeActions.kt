/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import android.content.Context
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The calls a Home card can make on the Home view model. The router below decides which one an
 * action means, so that decision is unit tested without Android.
 */
interface OruHomeCommands {
    /**
     * Starts the patch flow for [packageName], which opens the same dialogs the old Home shows
     * (`HomeViewModel.showPatchDialog`). [repatchedPackageName] names the tracked install to rebuild,
     * or null for an install of its own.
     */
    fun patch(packageName: String, repatchedPackageName: String?)

    /** Opens the info dialog of an installed app, as the old Home does on a tap. */
    fun appInfo(installedPackageName: String)

    /** Whether the patch flow is waiting on [packageName], so its download and file steps can resume. */
    fun isPendingFor(packageName: String): Boolean

    /** Opens the file picker prompt of the pending app, the step after the APKMirror hand-off. */
    fun showFilePickerPrompt()

    /** Opens the APKMirror download step of the pending app again. */
    fun reopenDownloadStep()

    /** Launches an installed app. */
    fun openApp(installedPackageName: String)
}

/** Turns a card button into one [OruHomeCommands] call. */
object OruHomeActionRouter {

    fun route(card: OruHomeCard, action: OruHomeAction, commands: OruHomeCommands) {
        val packageName = card.packageName
        val installedPackageName = card.item.installedApp?.currentPackageName
            ?: card.item.installedPatchedPackage
            ?: packageName
        when (action) {
            OruHomeAction.AppInfo -> commands.appInfo(installedPackageName)
            OruHomeAction.Open -> commands.openApp(installedPackageName)
            OruHomeAction.CheckAndPatch,
            OruHomeAction.FindOnApkMirror,
            OruHomeAction.UseFile,
            OruHomeAction.PatchFromSaved,
            OruHomeAction.UseAnotherFile,
            OruHomeAction.Patch,
            OruHomeAction.Update,
            OruHomeAction.PatchAgain -> commands.patch(packageName, card.repatchedPackageName)
            // The screen opens the Focus for this action, see oruActionOpensFocus, so the router has nothing to call
            OruHomeAction.GetIt -> Unit
            // The same: the Focus of the finished run holds its install
            OruHomeAction.Install -> Unit
            // The APKMirror step and the file step only make sense for the app the flow is waiting on
            OruHomeAction.ChooseFile ->
                if (commands.isPendingFor(packageName)) commands.showFilePickerPrompt()
                else commands.patch(packageName, card.repatchedPackageName)
            OruHomeAction.OpenApkMirrorAgain ->
                if (commands.isPendingFor(packageName)) commands.reopenDownloadStep()
                else commands.patch(packageName, card.repatchedPackageName)
        }
    }
}

/**
 * The commands on the real [HomeViewModel]. Each one sets the same state the old Home sets for
 * the same intent, see the comments on each method.
 */
class HomeViewModelOruHomeCommands(
    private val homeViewModel: HomeViewModel,
    private val context: Context,
) : OruHomeCommands {

    /**
     * `handleAppClick` calls this for an app with no install (`HomeViewModel.handleAppClick`), and
     * HomeScreen's `startBatchPatch` passes the same repatch name for a rebuild.
     */
    override fun patch(packageName: String, repatchedPackageName: String?) {
        homeViewModel.showPatchDialog(packageName, repatchedPackageName)
    }

    /** HomeScreen `onAppClick` calls `openInstalledAppInfo(installedApp.currentPackageName)`. */
    override fun appInfo(installedPackageName: String) {
        homeViewModel.openInstalledAppInfo(installedPackageName)
    }

    override fun isPendingFor(packageName: String): Boolean =
        homeViewModel.pendingPackageName == packageName

    /**
     * `HomeDialogs` sets this after the download step continues (`handleDownloadInstructionsContinue`),
     * and its "Open file picker" button then calls the storage picker.
     */
    override fun showFilePickerPrompt() {
        homeViewModel.showFilePickerPromptDialog = true
    }

    /** The "Need APK" path of `ApkAvailabilityDialog` (`HomeDialogs` `onNeedApk`), minus its 50 ms delay. */
    override fun reopenDownloadStep() {
        homeViewModel.showDownloadInstructionsDialog = true
        homeViewModel.resolveDownloadRedirect()
    }

    /** Launches the installed app through its launch intent. */
    override fun openApp(installedPackageName: String) {
        context.packageManager.getLaunchIntentForPackage(installedPackageName)
            ?.let { context.startActivity(it) }
    }
}
