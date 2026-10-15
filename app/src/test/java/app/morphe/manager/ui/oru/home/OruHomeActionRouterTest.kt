/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.model.HomeAppItem
import kotlin.test.Test
import kotlin.test.assertEquals

class OruHomeActionRouterTest {

    /** Records every call, in order. [pending] is the package the flow is waiting on, if any. */
    private class FakeCommands(private val pending: String? = null) : OruHomeCommands {
        val calls = mutableListOf<String>()

        override fun patch(packageName: String, repatchedPackageName: String?) {
            calls += "patch:$packageName:${repatchedPackageName ?: "-"}"
        }

        override fun appInfo(installedPackageName: String) {
            calls += "info:$installedPackageName"
        }

        override fun isPendingFor(packageName: String): Boolean = packageName == pending

        override fun showFilePickerPrompt() {
            calls += "filePrompt"
        }

        override fun reopenDownloadStep() {
            calls += "download"
        }

        override fun openApp(installedPackageName: String) {
            calls += "open:$installedPackageName"
        }
    }

    private fun card(
        packageName: String = "com.example.app",
        installed: InstalledApp? = null,
        installedPatched: String? = null,
    ): OruHomeCard {
        val item = HomeAppItem(
            id = installed?.currentPackageName ?: packageName,
            packageName = packageName,
            displayName = "Example",
            gradientColors = emptyList(),
            installedApp = installed,
            packageInfo = null,
            version = "1.0",
            isPinnedByDefault = false,
            isInstalledOnDevice = false,
            isDeleted = false,
            isInstallStateNotPatched = false,
            isInstallStateUnknown = false,
            isInstallStatePending = false,
            savedApkFile = null,
            hasUpdate = false,
            versionStatus = null,
            patchCount = 0,
            isClone = installed?.isClone == true,
            installedPatchedPackage = installedPatched,
        )
        return OruHomeCard(
            item = item,
            status = OruHomeCardStatus.NeedsApk,
            primary = OruHomeAction.FindOnApkMirror,
            secondary = null,
            chipTone = OruTone.Warn,
            version = null,
            patchCount = 0,
            hint = null,
            hintVersion = null,
            repatchedPackageName = installed?.trackingKey,
        )
    }

    private fun installed(current: String, original: String, clone: Boolean) = InstalledApp(
        currentPackageName = current,
        originalPackageName = original,
        version = "1.0",
        installType = InstallType.DEFAULT,
        isClone = clone,
    )

    @Test
    fun `find on apkmirror starts the patch flow for the app`() {
        val commands = FakeCommands()
        OruHomeActionRouter.route(card(), OruHomeAction.FindOnApkMirror, commands)
        assertEquals(listOf("patch:com.example.app:-"), commands.calls)
    }

    @Test
    fun `an update rebuilds the tracked install under its tracking name`() {
        val commands = FakeCommands()
        val installed = installed("com.example.app", "com.example.app", clone = false)
        val card = card(installed = installed)
        OruHomeActionRouter.route(card, OruHomeAction.Update, commands)
        assertEquals(listOf("patch:com.example.app:com.example.app"), commands.calls)
    }

    @Test
    fun `app info uses the install's own package, which a clone keeps apart from its app`() {
        val commands = FakeCommands()
        val clone = installed("com.example.app.clone", "com.example.app", clone = true)
        OruHomeActionRouter.route(card(packageName = "com.example.app", installed = clone), OruHomeAction.AppInfo, commands)
        assertEquals(listOf("info:com.example.app.clone"), commands.calls)
    }

    @Test
    fun `open launches the installed package`() {
        val commands = FakeCommands()
        val installed = installed("com.example.app", "com.example.app", clone = false)
        OruHomeActionRouter.route(card(installed = installed), OruHomeAction.Open, commands)
        assertEquals(listOf("open:com.example.app"), commands.calls)
    }

    @Test
    fun `open launches the patched copy installed under its own package when the app itself is gone`() {
        val commands = FakeCommands()
        val card = card(installedPatched = "app.example.patched")
        OruHomeActionRouter.route(card, OruHomeAction.Open, commands)
        assertEquals(listOf("open:app.example.patched"), commands.calls)
    }

    @Test
    fun `choose the file goes straight to the file prompt when the flow is waiting on this app`() {
        val commands = FakeCommands(pending = "com.example.app")
        OruHomeActionRouter.route(card(), OruHomeAction.ChooseFile, commands)
        assertEquals(listOf("filePrompt"), commands.calls)
    }

    @Test
    fun `choose the file starts the patch flow when nothing is pending for this app`() {
        val commands = FakeCommands(pending = "com.other.app")
        OruHomeActionRouter.route(card(), OruHomeAction.ChooseFile, commands)
        assertEquals(listOf("patch:com.example.app:-"), commands.calls)
    }

    @Test
    fun `open apkmirror again reopens the download step only for the pending app`() {
        val pendingCommands = FakeCommands(pending = "com.example.app")
        OruHomeActionRouter.route(card(), OruHomeAction.OpenApkMirrorAgain, pendingCommands)
        assertEquals(listOf("download"), pendingCommands.calls)

        val idleCommands = FakeCommands()
        OruHomeActionRouter.route(card(), OruHomeAction.OpenApkMirrorAgain, idleCommands)
        assertEquals(listOf("patch:com.example.app:-"), idleCommands.calls)
    }

    @Test
    fun `saved copy and file actions start the patch flow`() {
        val commands = FakeCommands()
        OruHomeActionRouter.route(card(), OruHomeAction.PatchFromSaved, commands)
        OruHomeActionRouter.route(card(), OruHomeAction.UseFile, commands)
        OruHomeActionRouter.route(card(), OruHomeAction.UseAnotherFile, commands)
        assertEquals(
            listOf("patch:com.example.app:-", "patch:com.example.app:-", "patch:com.example.app:-"),
            commands.calls,
        )
    }
}
