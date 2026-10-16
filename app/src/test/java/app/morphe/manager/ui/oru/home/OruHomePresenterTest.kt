/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.bundles.AppVersionStatus
import app.morphe.manager.ui.oru.components.OruButtonStyle
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.model.HomeAppItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruHomePresenterTest {

    private fun item(
        packageName: String = "com.example.app",
        displayName: String = "Example",
        installed: InstalledApp? = null,
        version: String = "1.0",
        pinned: Boolean = false,
        onDevice: Boolean = false,
        deleted: Boolean = false,
        notPatched: Boolean = false,
        unknown: Boolean = false,
        pending: Boolean = false,
        savedFile: File? = null,
        hasUpdate: Boolean = false,
        behind: Boolean = false,
        installedPatched: String? = null,
    ) = HomeAppItem(
        id = packageName,
        packageName = packageName,
        displayName = displayName,
        gradientColors = emptyList(),
        installedApp = installed,
        packageInfo = null,
        version = version,
        isPinnedByDefault = pinned,
        isInstalledOnDevice = onDevice,
        isDeleted = deleted,
        isInstallStateNotPatched = notPatched,
        isInstallStateUnknown = unknown,
        isInstallStatePending = pending,
        savedApkFile = savedFile,
        hasUpdate = hasUpdate,
        versionStatus = if (behind) AppVersionStatus("1.0", "2.0", isBehind = true) else null,
        patchCount = 0,
        isClone = false,
        installedPatchedPackage = installedPatched,
    )

    private fun installed(packageName: String = "com.example.app", clone: Boolean = false) = InstalledApp(
        currentPackageName = packageName,
        originalPackageName = "com.example.app",
        version = "1.0",
        installType = InstallType.DEFAULT,
        isClone = clone,
    )

    private fun input(
        items: List<HomeAppItem>? = emptyList(),
        loading: Boolean = false,
        expert: Boolean = true,
        handedOff: Set<String> = emptySet(),
        recommended: Map<String, String> = emptyMap(),
        supported: Map<String, Set<String>> = emptyMap(),
        patchCounts: Map<String, Int> = emptyMap(),
        batch: OruBatchSignal = OruBatchSignal(OruBatchPhase.Idle),
        customized: Set<String> = emptySet(),
    ) = OruHomeInput(
        items = items,
        installedAppsLoading = loading,
        expert = expert,
        handedOff = handedOff,
        recommendedVersions = recommended,
        supportedVersions = supported,
        patchCounts = patchCounts,
        customized = customized,
        banner = OruBannerInput(
            managerUpdate = null,
            sourcesAttention = false,
            meteredSkipped = false,
            batch = batch,
            bundleUpdate = null,
        ),
    )

    private fun statusOf(item: HomeAppItem, handedOff: Boolean = false, supported: Set<String>? = null) =
        OruHomePresenter.statusOf(item, handedOff, supported)

    @Test
    fun `a pending install state is checking with only app info`() {
        val card = OruHomePresenter.card(item(pending = true, deleted = true), input())
        assertEquals(OruHomeCardStatus.Checking, card.status)
        assertEquals(OruHomeAction.AppInfo, card.primary)
        assertNull(card.secondary)
    }

    @Test
    fun `an unknown install state is unverified and offers a check`() {
        val card = OruHomePresenter.card(item(unknown = true), input())
        assertEquals(OruHomeCardStatus.Unverified, card.status)
        assertEquals(OruHomeAction.CheckAndPatch, card.primary)
        assertEquals(OruHomeAction.AppInfo, card.secondary)
    }

    @Test
    fun `an uninstalled app with no saved copy needs the apk from apkmirror`() {
        val card = OruHomePresenter.card(
            item(deleted = true, installed = installed()),
            input(recommended = mapOf("com.example.app" to "2.5")),
        )
        assertEquals(OruHomeCardStatus.NeedsApk, card.status)
        assertEquals(OruHomeAction.FindOnApkMirror, card.primary)
        assertEquals(OruHomeAction.UseFile, card.secondary)
        assertEquals(OruHomeHint.ApkMirror, card.hint)
        assertEquals("2.5", card.hintVersion)
        assertEquals("2.5", card.version)
        assertEquals(OruTone.Warn, card.chipTone)
    }

    @Test
    fun `an uninstalled app with a saved copy patches from the copy`() {
        val card = OruHomePresenter.card(
            item(deleted = true, installed = installed(), savedFile = File("saved.apk")),
            input(),
        )
        assertEquals(OruHomeCardStatus.SavedCopy, card.status)
        assertEquals(OruHomeAction.PatchFromSaved, card.primary)
        assertEquals(OruHomeAction.UseAnotherFile, card.secondary)
        assertEquals(OruHomeHint.SavedCopy, card.hint)
    }

    @Test
    fun `simple mode needs the apk with one button that opens the focus, and names the version`() {
        val card = OruHomePresenter.card(
            item(deleted = true, installed = installed()),
            input(expert = false, recommended = mapOf("com.example.app" to "2.5")),
        )
        assertEquals(OruHomeCardStatus.NeedsApk, card.status)
        assertEquals(OruHomeAction.GetIt, card.primary)
        assertNull(card.secondary)
        assertNull(card.hint)
        assertNull(card.source)
        assertEquals("2.5", card.version)
        assertEquals(OruHomeSentence.NeedsApk, card.status.simpleSentence())
    }

    @Test
    fun `the focus opens on the step its card names, for each mode and state`() {
        val needsApk = item(deleted = true, installed = installed())
        assertEquals(OruHomeAction.GetIt, OruHomePresenter.primaryActionOf(needsApk, null, expert = false))
        assertEquals(OruHomeAction.FindOnApkMirror, OruHomePresenter.primaryActionOf(needsApk, null, expert = true))

        val saved = item(deleted = true, installed = installed(), savedFile = File("saved.apk"))
        assertEquals(OruHomeAction.PatchFromSaved, OruHomePresenter.primaryActionOf(saved, null, expert = false))

        val ready = item(onDevice = true)
        assertEquals(OruHomeAction.Patch, OruHomePresenter.primaryActionOf(ready, setOf("1.0"), expert = false))
    }

    @Test
    fun `the step a focus opens on is the primary action its card shows`() {
        val needsApk = item(deleted = true, installed = installed())
        val card = OruHomePresenter.card(needsApk, input(expert = false))
        assertEquals(card.primary, OruHomePresenter.primaryActionOf(needsApk, null, expert = false))
    }

    @Test
    fun `simple mode drops the patch count, the source and the second button`() {
        val card = OruHomePresenter.card(
            item(installed = installed(), hasUpdate = true),
            input(expert = false, patchCounts = mapOf("com.example.app" to 42), recommended = mapOf("com.example.app" to "2.5")),
        )
        assertEquals(OruHomeCardStatus.UpdateAvailable, card.status)
        assertEquals(0, card.patchCount)
        assertNull(card.source)
        assertNull(card.secondary)
    }

    @Test
    fun `expert mode keeps the patch count and the second button of a card`() {
        val card = OruHomePresenter.card(
            item(installed = installed(), hasUpdate = true),
            input(expert = true, patchCounts = mapOf("com.example.app" to 42)),
        )
        assertEquals(42, card.patchCount)
        assertEquals(OruHomeAction.AppInfo, card.secondary)
    }

    @Test
    fun `each status has one simple sentence`() {
        val sentences = OruHomeCardStatus.values().associateWith { it.simpleSentence() }
        assertEquals(OruHomeSentence.Checking, sentences[OruHomeCardStatus.Checking])
        assertEquals(OruHomeSentence.NotChecked, sentences[OruHomeCardStatus.Unverified])
        assertEquals(OruHomeSentence.NeedsApk, sentences[OruHomeCardStatus.NeedsApk])
        assertEquals(OruHomeSentence.WaitingApk, sentences[OruHomeCardStatus.Waiting])
        assertEquals(OruHomeSentence.Ready, sentences[OruHomeCardStatus.SavedCopy])
        assertEquals(OruHomeSentence.Ready, sentences[OruHomeCardStatus.ReadyOnDevice])
        assertEquals(OruHomeSentence.Ready, sentences[OruHomeCardStatus.UnpatchedInstalled])
        assertEquals(OruHomeSentence.UpdateReady, sentences[OruHomeCardStatus.UpdateAvailable])
        assertEquals(OruHomeSentence.UpToDate, sentences[OruHomeCardStatus.Installed])
        assertEquals(OruHomeSentence.Patched, sentences[OruHomeCardStatus.PatchedElsewhere])
    }

    @Test
    fun `an app patched on an older version than the latest reads as an update in both modes`() {
        // The patched copy is 21.16.251 and the sources recommend 21.20.405: the card offers the rebuild
        val item = item(version = "21.16.251", installed = installed(), hasUpdate = true)
        val recommended = mapOf("com.example.app" to "21.20.405")
        val expert = OruHomePresenter.card(item, input(expert = true, recommended = recommended))
        assertEquals(OruHomeCardStatus.UpdateAvailable, expert.status)
        assertEquals("21.16.251", expert.version)
        assertEquals("21.20.405", expert.latestVersion)
        assertEquals(OruHomeAction.Update, expert.primary)
        val simple = OruHomePresenter.card(item, input(expert = false, recommended = recommended))
        assertEquals(OruHomeSentence.UpdateReady, simple.status.simpleSentence())
        assertEquals(OruHomeAction.Update, simple.primary)
    }

    @Test
    fun `customized apps are marked in both modes`() {
        val item = item(installed = installed())
        assertTrue(OruHomePresenter.card(item, input(customized = setOf("com.example.app"))).customized)
        assertTrue(OruHomePresenter.card(item, input(expert = false, customized = setOf("com.example.app"))).customized)
        assertFalse(OruHomePresenter.card(item, input(customized = setOf("other.app"))).customized)
    }

    @Test
    fun `a saved copy with no install of its own is ready from the copy`() {
        val card = OruHomePresenter.card(item(savedFile = File("saved.apk")), input())
        assertEquals(OruHomeCardStatus.SavedCopy, card.status)
        assertEquals(OruTone.Ok, card.chipTone)
    }

    @Test
    fun `an unpatched install is patched and keeps its name for the rebuild`() {
        val card = OruHomePresenter.card(
            item(installed = installed(), notPatched = true),
            input(),
        )
        assertEquals(OruHomeCardStatus.UnpatchedInstalled, card.status)
        assertEquals(OruHomeAction.Patch, card.primary)
        assertEquals(OruHomeAction.AppInfo, card.secondary)
        assertEquals("com.example.app", card.repatchedPackageName)
    }

    @Test
    fun `a patched install with newer patches offers an update`() {
        val card = OruHomePresenter.card(item(installed = installed(), hasUpdate = true), input())
        assertEquals(OruHomeCardStatus.UpdateAvailable, card.status)
        assertEquals(OruHomeAction.Update, card.primary)
        assertEquals(OruHomeAction.AppInfo, card.secondary)
        assertEquals(OruTone.Neutral, card.chipTone)
    }

    @Test
    fun `a supported version that is behind the install offers an update too`() {
        val card = OruHomePresenter.card(item(installed = installed(), behind = true), input())
        assertEquals(OruHomeCardStatus.UpdateAvailable, card.status)
    }

    @Test
    fun `a current patched install opens and can be patched again`() {
        val card = OruHomePresenter.card(item(installed = installed()), input())
        assertEquals(OruHomeCardStatus.Installed, card.status)
        assertEquals(OruHomeAction.Open, card.primary)
        assertEquals(OruHomeAction.PatchAgain, card.secondary)
        assertEquals(OruTone.Ok, card.chipTone)
        assertNull(card.hint)
    }

    @Test
    fun `a clone keeps its own package as the rebuild name`() {
        val clone = installed(packageName = "com.example.app.clone", clone = true)
        val card = OruHomePresenter.card(item(packageName = "com.example.app.clone", installed = clone), input())
        assertEquals("com.example.app.clone", card.repatchedPackageName)
    }

    @Test
    fun `an app on the phone that the sources cover is ready to patch in simple mode, with one button`() {
        val item = item(onDevice = true, version = "2.5")
        val card = OruHomePresenter.card(item, input(expert = false, supported = mapOf("com.example.app" to setOf("2.5", "2.4"))))
        assertEquals(OruHomeCardStatus.ReadyOnDevice, card.status)
        assertEquals(OruHomeAction.Patch, card.primary)
        assertNull(card.secondary)
    }

    @Test
    fun `the same ready app is patched in expert mode`() {
        val item = item(onDevice = true, version = "2.5")
        val card = OruHomePresenter.card(
            item,
            input(expert = true, supported = mapOf("com.example.app" to setOf("2.5"))),
        )
        assertEquals(OruHomeAction.Patch, card.primary)
    }

    @Test
    fun `an app on the phone at a version the sources do not cover needs the apk`() {
        val status = statusOf(item(onDevice = true, version = "9.9"), supported = setOf("2.5"))
        assertEquals(OruHomeCardStatus.NeedsApk, status)
    }

    @Test
    fun `a handed off apk is waiting and asks for the file`() {
        val card = OruHomePresenter.card(
            item(deleted = true, installed = installed()),
            input(handedOff = setOf("com.example.app")),
        )
        assertEquals(OruHomeCardStatus.Waiting, card.status)
        assertEquals(OruHomeAction.ChooseFile, card.primary)
        assertEquals(OruHomeAction.OpenApkMirrorAgain, card.secondary)
        assertEquals(OruHomeHint.ReturnDownload, card.hint)
        assertNull(card.hintVersion)
    }

    @Test
    fun `a hand off for another app does not make this one wait`() {
        val status = statusOf(item(deleted = true, installed = installed()), handedOff = false)
        assertEquals(OruHomeCardStatus.NeedsApk, status)
    }

    @Test
    fun `checking outranks every other state`() {
        val status = statusOf(item(pending = true, deleted = true, savedFile = File("a.apk"), unknown = true))
        assertEquals(OruHomeCardStatus.Checking, status)
    }

    @Test
    fun `patch count comes from the sources and defaults to zero`() {
        val withCount = OruHomePresenter.card(
            item(packageName = "a.app"),
            input(patchCounts = mapOf("a.app" to 42)),
        )
        val withoutCount = OruHomePresenter.card(item(packageName = "b.app"), input(patchCounts = mapOf("a.app" to 42)))
        assertEquals(42, withCount.patchCount)
        assertEquals(0, withoutCount.patchCount)
    }

    @Test
    fun `cards list recommended apps first and then by name, ignoring case`() {
        val state = OruHomePresenter.state(
            input(
                items = listOf(
                    item(packageName = "c", displayName = "beta"),
                    item(packageName = "d", displayName = "Zed", pinned = true),
                    item(packageName = "a", displayName = "Alpha"),
                    item(packageName = "b", displayName = "alpha two", pinned = true),
                ),
            ),
        )
        assertEquals(listOf("b", "d", "a", "c"), state.cards.map { it.packageName })
    }

    @Test
    fun `items not yet loaded show loading and no cards`() {
        val state = OruHomePresenter.state(input(items = null))
        assertTrue(state.loading)
        assertFalse(state.empty)
        assertTrue(state.cards.isEmpty())
    }

    @Test
    fun `installed apps still loading keep the skeleton up`() {
        val state = OruHomePresenter.state(input(items = listOf(item()), loading = true))
        assertTrue(state.loading)
        assertFalse(state.empty)
    }

    @Test
    fun `no supported apps once loaded is the empty state`() {
        val state = OruHomePresenter.state(input(items = emptyList()))
        assertFalse(state.loading)
        assertTrue(state.empty)
    }

    @Test
    fun `the repatch banner counts the cards with an update badge`() {
        val state = OruHomePresenter.state(
            input(
                items = listOf(
                    item(packageName = "a", installed = installed("a"), hasUpdate = true),
                    item(packageName = "b", installed = installed("b"), hasUpdate = true, pending = true),
                    item(packageName = "c", installed = installed("c")),
                ),
            ),
        )
        assertEquals(OruHomeBanner.Repatch(1), state.banner)
    }

    @Test
    fun `a running batch takes the banner over the repatch count`() {
        val state = OruHomePresenter.state(
            input(
                items = listOf(item(packageName = "a", installed = installed("a"), hasUpdate = true)),
                batch = OruBatchSignal(OruBatchPhase.Running, processed = 1, total = 3),
            ),
        )
        assertEquals(OruHomeBanner.BatchRunning(1, 3), state.banner)
    }

    @Test
    fun `status tones follow the Oru status roles`() {
        assertEquals(OruTone.Neutral, OruHomeCardStatus.Checking.tone())
        assertEquals(OruTone.Warn, OruHomeCardStatus.Waiting.tone())
        assertEquals(OruTone.Warn, OruHomeCardStatus.UnpatchedInstalled.tone())
        assertEquals(OruTone.Ok, OruHomeCardStatus.Installed.tone())
        assertEquals(OruTone.Ok, OruHomeCardStatus.ReadyOnDevice.tone())
    }

    @Test
    fun `only the find and choose actions are filled or tonal, the rest are outlined`() {
        assertEquals(OruButtonStyle.Tonal, OruHomeAction.FindOnApkMirror.style())
        assertEquals(OruButtonStyle.Filled, OruHomeAction.ChooseFile.style())
        assertEquals(OruButtonStyle.Filled, OruHomeAction.Patch.style())
        assertEquals(OruButtonStyle.Outlined, OruHomeAction.UseFile.style())
        assertEquals(OruButtonStyle.Outlined, OruHomeAction.AppInfo.style())
    }

    @Test
    fun `a patched copy this manager did not make is shown as installed, not as an apk needed`() {
        val card = OruHomePresenter.card(
            item(installedPatched = "app.example.patched", version = "1.2"),
            input(recommended = mapOf("com.example.app" to "2.5")),
        )
        assertEquals(OruHomeCardStatus.PatchedElsewhere, card.status)
        assertEquals(OruTone.Ok, card.chipTone)
        assertEquals(OruHomeAction.Open, card.primary)
        assertEquals(OruHomeAction.PatchAgain, card.secondary)
        assertNull(card.hint)
        assertNull(card.hintVersion)
        assertEquals("1.2", card.version)
        assertEquals("app.example.patched", card.item.installedPatchedPackage)
    }

    @Test
    fun `a patched copy is not ready on device even when its version is one the sources support`() {
        val status = statusOf(
            item(installedPatched = "app.example.patched", onDevice = true, version = "1.0"),
            supported = setOf("1.0"),
        )
        assertEquals(OruHomeCardStatus.PatchedElsewhere, status)
    }

    @Test
    fun `a patched copy behind the sources offers the update that patches a newer version`() {
        val card = OruHomePresenter.card(
            item(installedPatched = "app.example.patched", behind = true),
            input(),
        )
        assertEquals(OruHomeCardStatus.UpdateAvailable, card.status)
        assertEquals(OruHomeAction.Update, card.primary)
        assertEquals(OruHomeAction.AppInfo, card.secondary)
    }

    @Test
    fun `an install this manager tracks is installed rather than a patched copy`() {
        val status = statusOf(item(installed = installed(), installedPatched = "app.example.patched"), supported = null)
        assertEquals(OruHomeCardStatus.Installed, status)
    }
}
