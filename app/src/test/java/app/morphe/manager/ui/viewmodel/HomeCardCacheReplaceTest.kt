/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.ui.graphics.Color
import app.morphe.manager.domain.bundles.AppVersionStatus
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.model.HomeAppItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class HomeCardCacheReplaceTest {
    private val dir = Files.createTempDirectory("morphe-home-replace").toFile()
    private val file = dir.resolve("home").resolve("cards.json")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val prefs = HomePrefs(
        hiddenPackages = emptySet(),
        customOrder = emptyList(),
        sourceOrders = emptyMap(),
        sortMode = HomeAppSortMode.entries.first()
    )

    private fun item(packageName: String) = HomeAppItem(
        id = packageName,
        packageName = packageName,
        displayName = "App $packageName",
        gradientColors = listOf(Color(0xFF112233), Color(0xFF445566)),
        installedApp = null,
        packageInfo = null,
        version = "1.0",
        isPinnedByDefault = false,
        isInstalledOnDevice = true,
        isDeleted = false,
        isInstallStateNotPatched = false,
        isInstallStateUnknown = false,
        isInstallStatePending = false,
        savedApkFile = null,
        hasUpdate = true,
        versionStatus = AppVersionStatus("1.0", "2.0", isBehind = true),
        patchCount = 0,
        isClone = false,
        nameSuffix = "web"
    )

    private fun cards(vararg items: HomeAppItem) = HomeCards(
        visible = items.toList(),
        hidden = emptyList(),
        sortMode = prefs.sortMode,
        sourceGroups = emptyList()
    )

    @Test
    fun `a second write replaces the first one`() {
        val cache = HomeCardCache(file)
        cache.write(cards(item("a")))

        cache.write(cards(item("b")))

        val read = assertNotNull(HomeCardCache(file).read()).toCards(emptyList(), prefs)
        assertEquals(listOf("b"), read.visible.map { it.id })
    }

    @Test
    fun `a failed write leaves no temp file behind`() {
        // A non-empty directory cannot be replaced, so the move fails after the temp file is written
        file.mkdirs()
        file.resolve("blocker").writeText("x")

        HomeCardCache(file).write(cards(item("a")))

        assertFalse(File(file.path + ".tmp").exists())
    }
}
