/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.ui.model.homeAppSlots
import kotlin.test.Test
import kotlin.test.assertEquals

class OruSharedKeyTest {
    private fun clone(of: String, currentPackage: String) = InstalledApp(
        currentPackageName = currentPackage,
        originalPackageName = of,
        version = "1.0",
        installType = InstallType.DEFAULT,
        isClone = true
    )

    @Test
    fun keyIsTheCardIdUnderTheTilePrefix() {
        assertEquals("tile/com.example.app", oruTileSharedKey("com.example.app"))
    }

    @Test
    fun keysAreUniquePerClone() {
        val records = listOf(
            clone(of = "com.example.app", currentPackage = "com.example.app.clone1"),
            clone(of = "com.example.app", currentPackage = "com.example.app.clone2")
        )

        val keys = homeAppSlots("com.example.app", records).map { oruTileSharedKey(it.id) }

        assertEquals(
            listOf(
                "tile/com.example.app",
                "tile/com.example.app.clone1",
                "tile/com.example.app.clone2"
            ),
            keys
        )
    }

    @Test
    fun keysAreUniquePerPackage() {
        val records = listOf(clone(of = "com.example.a", currentPackage = "com.example.a.clone"))

        val keys = homeAppSlots(listOf("com.example.a", "com.example.b"), records)
            .map { oruTileSharedKey(it.id) }

        assertEquals(keys.size, keys.toSet().size)
        assertEquals(
            listOf("tile/com.example.a", "tile/com.example.a.clone", "tile/com.example.b"),
            keys
        )
    }
}
