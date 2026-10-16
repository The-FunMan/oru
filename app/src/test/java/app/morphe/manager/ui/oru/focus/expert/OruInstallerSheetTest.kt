/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.ui.oru.install.InstallerChoice
import app.morphe.manager.ui.oru.install.OruInstallerOption
import kotlin.test.Test
import kotlin.test.assertEquals

class OruInstallerSheetTest {

    private fun option(token: String, enabled: Boolean) = OruInstallerOption(
        choice = InstallerChoice.SYSTEM,
        token = token,
        label = token,
        enabled = enabled,
    )

    @Test
    fun `available installers are listed first, then the unavailable ones, each keeping its order`() {
        val options = listOf(
            option("root", enabled = false),
            option("system", enabled = true),
            option("shizuku", enabled = false),
            option("play", enabled = true),
        )
        val (available, unavailable) = oruInstallerGroups(options)
        assertEquals(listOf("system", "play"), available.map { it.token })
        assertEquals(listOf("root", "shizuku"), unavailable.map { it.token })
    }

    @Test
    fun `a list with only available installers has no unavailable group`() {
        val (available, unavailable) = oruInstallerGroups(listOf(option("system", enabled = true)))
        assertEquals(listOf("system"), available.map { it.token })
        assertEquals(emptyList<String>(), unavailable.map { it.token })
    }
}
