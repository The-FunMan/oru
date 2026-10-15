/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.oru.home.OruHomeCardStatus
import app.morphe.manager.ui.oru.home.OruHomePresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruFocusInstalledTest {

    private fun item(
        installed: InstalledApp? = null,
        hasUpdate: Boolean = false,
    ) = HomeAppItem(
        id = "com.google.android.youtube",
        packageName = "com.google.android.youtube",
        displayName = "YouTube",
        gradientColors = emptyList(),
        installedApp = installed,
        packageInfo = null,
        version = "21.39.522",
        isPinnedByDefault = false,
        isInstalledOnDevice = installed != null,
        isDeleted = false,
        isInstallStateNotPatched = false,
        isInstallStateUnknown = false,
        isInstallStatePending = false,
        savedApkFile = null,
        hasUpdate = hasUpdate,
        versionStatus = null,
        patchCount = 0,
        isClone = false,
    )

    private fun patchedByOru() = InstalledApp(
        currentPackageName = "com.google.android.youtube",
        originalPackageName = "com.google.android.youtube",
        version = "21.39.522",
        installType = InstallType.DEFAULT,
    )

    @Test
    fun `only the installed statuses read as installed in the Focus`() {
        val installed = setOf(OruHomeCardStatus.Installed, OruHomeCardStatus.PatchedElsewhere)
        for (status in OruHomeCardStatus.entries) {
            assertEquals(status in installed, oruFocusShowsInstalled(status), "status $status")
        }
    }

    @Test
    fun `an app Oru just installed reads as installed, as Home shows it`() {
        val item = item(installed = patchedByOru())
        assertEquals(OruHomeCardStatus.Installed, OruHomePresenter.statusOf(item, handedOff = false, supportedVersions = null))
        assertTrue(oruFocusShowsInstalled(OruHomePresenter.statusOf(item, handedOff = false, supportedVersions = null)))
    }

    @Test
    fun `an app with a newer patch to build is not installed, so the Focus still asks for the file`() {
        val item = item(installed = patchedByOru(), hasUpdate = true)
        assertEquals(OruHomeCardStatus.UpdateAvailable, OruHomePresenter.statusOf(item, handedOff = false, supportedVersions = null))
        assertFalse(oruFocusShowsInstalled(OruHomePresenter.statusOf(item, handedOff = false, supportedVersions = null)))
    }
}
