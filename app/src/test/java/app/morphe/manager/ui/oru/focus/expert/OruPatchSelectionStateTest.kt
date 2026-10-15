/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.util.PatchSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruPatchSelectionStateTest {

    private val noRules: OruSettle = { it }

    private fun patch(
        uid: Int,
        name: String,
        universal: Boolean = false,
        lock: PatchLockState = PatchLockState.NONE,
        defaultSelected: Boolean = false,
        category: String? = null,
        description: String? = null,
    ) = OruPatchItem(
        bundleUid = uid,
        name = name,
        displayName = name.replace('-', ' '),
        description = description,
        category = category,
        universal = universal,
        lock = lock,
        defaultSelected = defaultSelected,
        optionCount = 0,
    )

    private fun state(
        items: List<OruPatchItem>,
        selected: PatchSelection = emptyMap(),
    ) = OruPatchSelectionState(items = items, selected = selected)

    private val sample = listOf(
        patch(1, "remove-ads", defaultSelected = true, category = "Ads"),
        patch(1, "hide-feed", category = "Feed", description = "Hides the home feed"),
        patch(1, "dark-theme", universal = true, category = "Theme"),
        patch(2, "custom-logo", defaultSelected = true),
    )

    @Test
    fun `toggle adds a patch and a second toggle removes it`() {
        val on = state(sample).toggle(1, "hide-feed", noRules)
        assertEquals(setOf("hide-feed"), on.selected[1])

        val off = on.toggle(1, "hide-feed", noRules)
        assertEquals(emptyMap(), off.selected)
    }

    @Test
    fun `toggle never unticks a required patch`() {
        val items = listOf(patch(1, "required", lock = PatchLockState.LOCKED_ON))
        val start = state(items, selected = mapOf(1 to setOf("required")))

        assertEquals(start, start.toggle(1, "required", noRules))
    }

    @Test
    fun `toggle never ticks an unavailable patch`() {
        val items = listOf(patch(1, "unavailable", lock = PatchLockState.LOCKED_OFF))
        val start = state(items)

        assertEquals(start, start.toggle(1, "unavailable", noRules))
    }

    @Test
    fun `toggle hands the result to the availability rules`() {
        // A rule that always keeps one patch off, whatever the user picks
        val dropHide: OruSettle = { selection ->
            selection.mapValues { (_, names) -> names - "hide-feed" }.filterValues { it.isNotEmpty() }
        }
        val next = state(sample).toggle(1, "hide-feed", dropHide)

        assertEquals(emptyMap(), next.selected)
    }

    @Test
    fun `select all takes the visible regular patches and stages universal ones behind them`() {
        val first = state(sample).selectAll(noRules)

        assertEquals(setOf("remove-ads", "hide-feed"), first.selected[1])
        assertTrue("dark-theme" !in first.selected.getValue(1))
    }

    @Test
    fun `a second select all arms the universal patches`() {
        val first = state(sample).selectAll(noRules)
        val second = first.selectAll(noRules)

        assertEquals(setOf("remove-ads", "hide-feed", "dark-theme"), second.selected[1])
    }

    @Test
    fun `select all follows the search and leaves other sources alone`() {
        val narrowed = state(sample, selected = mapOf(2 to setOf("custom-logo")))
            .withQuery("feed")
            .selectAll(noRules)

        assertEquals(setOf("hide-feed"), narrowed.selected[1])
        assertEquals(setOf("custom-logo"), narrowed.selected[2])
    }

    @Test
    fun `select all skips patches that are off for the install target`() {
        val items = listOf(
            patch(1, "off", lock = PatchLockState.LOCKED_OFF),
            patch(1, "on"),
        )
        val next = state(items).selectAll(noRules)

        assertEquals(setOf("on"), next.selected[1])
    }

    @Test
    fun `clear empties the visible patches and keeps required ones`() {
        val items = listOf(
            patch(1, "required", lock = PatchLockState.LOCKED_ON),
            patch(1, "optional"),
            patch(2, "other"),
        )
        val start = state(items, selected = mapOf(1 to setOf("required", "optional"), 2 to setOf("other")))

        val cleared = start.withQuery("optional").clear(noRules)

        assertEquals(mapOf(1 to setOf("required"), 2 to setOf("other")), cleared.selected)
    }

    @Test
    fun `clear without a search empties every source except required patches`() {
        val items = listOf(
            patch(1, "required", lock = PatchLockState.LOCKED_ON),
            patch(2, "other"),
        )
        val cleared = state(items, selected = mapOf(1 to setOf("required"), 2 to setOf("other"))).clear(noRules)

        assertEquals(mapOf(1 to setOf("required")), cleared.selected)
    }

    @Test
    fun `recommended selects the defaults of every source and ignores the search`() {
        val start = state(sample, selected = mapOf(1 to setOf("hide-feed", "dark-theme")))
            .withQuery("zzz")

        val reset = start.recommended(noRules)

        assertEquals(mapOf(1 to setOf("remove-ads"), 2 to setOf("custom-logo")), reset.selected)
    }

    @Test
    fun `recommended runs through the availability rules`() {
        val keepAllOff: OruSettle = { emptyMap() }
        val reset = state(sample).recommended(keepAllOff)

        assertEquals(emptyMap(), reset.selected)
    }

    @Test
    fun `search matches the name and the description, not case sensitive`() {
        val byName = state(sample).withQuery("ADS").visible().map { it.name }
        val byDescription = state(sample).withQuery("home feed").visible().map { it.name }

        assertEquals(listOf("remove-ads"), byName)
        assertEquals(listOf("hide-feed"), byDescription)
    }

    @Test
    fun `the category filter narrows the visible patches`() {
        val themed = state(sample).withCategory("Theme").visible().map { it.name }

        assertEquals(listOf("dark-theme"), themed)
    }

    @Test
    fun `categories are the ones the bundles declare, once each`() {
        val categories = state(sample + patch(2, "again", category = "Ads")).categories

        assertEquals(listOf("Ads", "Feed", "Theme"), categories)
    }

    @Test
    fun `no declared categories give an empty list`() {
        assertTrue(state(listOf(patch(1, "plain"))).categories.isEmpty())
    }

    @Test
    fun `the count is the number of selected patches over all sources`() {
        val counted = state(sample, selected = mapOf(1 to setOf("a", "b"), 2 to setOf("c")))

        assertEquals(3, counted.selectedCount)
    }
}
