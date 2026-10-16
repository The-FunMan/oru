/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruThemeModeTest {

    @Test
    fun `System follows the phone`() {
        assertTrue(isDarkEffective(OruThemeMode.System, systemDark = true))
        assertFalse(isDarkEffective(OruThemeMode.System, systemDark = false))
    }

    @Test
    fun `Light is light whatever the phone says`() {
        assertFalse(isDarkEffective(OruThemeMode.Light, systemDark = true))
        assertFalse(isDarkEffective(OruThemeMode.Light, systemDark = false))
    }

    @Test
    fun `Dark is dark whatever the phone says`() {
        assertTrue(isDarkEffective(OruThemeMode.Dark, systemDark = true))
        assertTrue(isDarkEffective(OruThemeMode.Dark, systemDark = false))
    }

    /** The quick toggle lists its segments in this order and reads the choice by index, so the enum order is the UI order. */
    @Test
    fun `the enum order is the order of the quick toggle segments`() {
        assertEquals(listOf(OruThemeMode.System, OruThemeMode.Light, OruThemeMode.Dark), OruThemeMode.entries)
        assertEquals(listOf(OruGlassStyle.Frosted, OruGlassStyle.Clear), OruGlassStyle.entries)
    }
}
