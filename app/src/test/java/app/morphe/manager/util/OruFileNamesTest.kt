/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruFileNamesTest {

    @Test
    fun `oru saves under the oru prefix`() {
        assertEquals("oru", OruFileNames.prefix(oruUx = true))
        assertEquals("oru_export.apk", OruFileNames.patchedApk(oruUx = true))
    }

    @Test
    fun `oru keystore is named after the oru brand`() {
        assertEquals("Oru.keystore", OruFileNames.keystore(oruUx = true))
    }

    @Test
    fun `morphe keeps its file names`() {
        assertEquals("morphe", OruFileNames.prefix(oruUx = false))
        assertEquals("morphe_export.apk", OruFileNames.patchedApk(oruUx = false))
        assertEquals("Morphe.keystore", OruFileNames.keystore(oruUx = false))
    }

    @Test
    fun `oru and the earlier oru secret are the extra import tries`() {
        assertContentEquals(arrayOf("oru"), OruFileNames.extraKeystoreSecrets(oruUx = true))
        assertTrue(OruFileNames.extraKeystoreSecrets(oruUx = false).isEmpty())
    }

    @Test
    fun `a generated oru name never carries the morphe brand`() {
        val names = listOf(
            "${OruFileNames.prefix(true)}_all_selections.json",
            "${OruFileNames.prefix(true)}_logcat.log",
            "${OruFileNames.prefix(true)}_manager_settings.json",
            OruFileNames.keystore(true),
            OruFileNames.patchedApk(true),
        )
        names.forEach { name ->
            assertFalse(name.contains("morphe", ignoreCase = true), name)
        }
    }
}
