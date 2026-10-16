/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.ui.oru.focus.customize.oruBrandingTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruCustomizeTest {

    private val optionKeys: Map<Pair<Int, String>, Set<String>> = mapOf(
        (1 to "Custom branding") to setOf("customName", "customIcon", "appIcon"),
        (1 to "Change header") to setOf("custom"),
        (2 to "Hide Shorts") to setOf("hideShortsWidget"),
        (2 to "Branding name only") to setOf("customName"),
    )

    private fun targets(selected: Map<Int, Set<String>>) =
        oruBrandingTargets(selected) { uid, name -> optionKeys[uid to name].orEmpty() }

    @Test
    fun `no selected patch declares branding means no targets`() {
        assertTrue(targets(mapOf(2 to setOf("Hide Shorts"))).isEmpty())
    }

    @Test
    fun `a selected branding patch is a target`() {
        assertEquals(listOf(1 to "Custom branding"), targets(mapOf(1 to setOf("Custom branding"))))
    }

    @Test
    fun `a branding patch that is not selected is not a target`() {
        assertTrue(targets(emptyMap()).isEmpty())
    }

    @Test
    fun `a header option is not branding`() {
        assertTrue(targets(mapOf(1 to setOf("Change header"))).isEmpty())
    }

    @Test
    fun `a patch with only an app name counts as branding`() {
        assertEquals(listOf(2 to "Branding name only"), targets(mapOf(2 to setOf("Branding name only"))))
    }

    @Test
    fun `targets follow source and then patch order`() {
        val selected = mapOf(2 to setOf("Branding name only"), 1 to setOf("Custom branding"))

        assertEquals(
            listOf(1 to "Custom branding", 2 to "Branding name only"),
            targets(selected),
        )
    }
}
