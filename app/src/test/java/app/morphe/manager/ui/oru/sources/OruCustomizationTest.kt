/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.sources

import app.morphe.manager.domain.bundles.AppSourceFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruCustomizationTest {

    private val builtIn = AppSourceFact(
        uid = 0,
        name = "Built-in",
        isDefault = true,
        repositoryPath = null,
        patchVersion = "1",
        patchCount = 10,
        recommendedVersion = "2.5",
    )
    private val other = AppSourceFact(
        uid = 7,
        name = "Other",
        isDefault = false,
        repositoryPath = "someone/patches",
        patchVersion = "3",
        patchCount = 4,
        recommendedVersion = "2.6",
    )

    private val recommended = mapOf(0 to setOf("a", "b"))

    // By default every recommended patch applies and nothing else exists
    private fun overrides(
        effective: AppSourceFact? = builtIn,
        preRelease: Boolean = false,
        turnedDown: Set<Int> = emptySet(),
        selection: Map<Int, Set<String>> = emptyMap(),
        applicable: Map<Int, Set<String>> = recommended,
        options: Set<Int> = emptySet(),
    ) = oruAppOverrides(
        effective = effective,
        preRelease = preRelease,
        turnedDown = turnedDown,
        selection = selection,
        recommended = recommended,
        applicable = applicable,
        optionBundles = options,
    )

    @Test
    fun `a saved name for a patch the source no longer has is not a change`() {
        // "gone" was removed from the source: the selection is the recommended one for the patches that exist
        val o = overrides(selection = mapOf(0 to setOf("a", "b", "gone")))
        assertFalse(o.patchesChanged)
        assertFalse(o.isCustomized())
    }

    @Test
    fun `a saved patch the installer or architecture does not offer is not a change`() {
        // "c" is unavailable for this installer, so the run drops it: the rest of the selection is the recommended one
        val o = overrides(selection = mapOf(0 to setOf("a", "b", "c")), applicable = mapOf(0 to setOf("a", "b")))
        assertFalse(o.patchesChanged)
    }

    @Test
    fun `a recommended patch the user turned off is a change even when another patch is unavailable`() {
        val o = overrides(selection = mapOf(0 to setOf("a", "c")), applicable = mapOf(0 to setOf("a", "b")))
        assertTrue(o.patchesChanged)
    }

    @Test
    fun `an app with nothing saved is not customized`() {
        val o = overrides()
        assertFalse(o.isCustomized())
        assertEquals(OruResetPlan(sources = false, patches = false, options = false), o.resetPlan())
    }

    @Test
    fun `a source other than the built-in one is a customization`() {
        val o = overrides(effective = other, turnedDown = setOf(0))
        assertTrue(o.otherSource)
        assertTrue(o.isCustomized())
        assertEquals(OruResetPlan(sources = true, patches = false, options = false), o.resetPlan())
    }

    @Test
    fun `the built-in source that is still used is not a customization, even with another one turned down`() {
        // A source added later turns the apps it shares down: the built-in source is still the one the app uses
        val o = overrides(effective = builtIn, turnedDown = setOf(7))
        assertFalse(o.otherSource)
        assertFalse(o.isCustomized())
    }

    @Test
    fun `a pre-release channel on the source counts, but a reset never clears it`() {
        val o = overrides(preRelease = true)
        assertTrue(o.isCustomized())
        assertEquals(OruResetPlan(sources = false, patches = false, options = false), o.resetPlan())
    }

    @Test
    fun `no source means no pre-release change`() {
        assertFalse(overrides(effective = null, preRelease = true).preRelease)
    }

    @Test
    fun `a saved selection equal to the recommended one is not a change`() {
        val o = overrides(selection = mapOf(0 to setOf("a", "b")))
        assertFalse(o.patchesChanged)
        assertFalse(o.isCustomized())
    }

    @Test
    fun `a saved selection that differs from the recommended one is a change and resets the selection`() {
        val o = overrides(selection = mapOf(0 to setOf("a")))
        assertTrue(o.patchesChanged)
        assertTrue(o.isCustomized())
        assertEquals(OruResetPlan(sources = false, patches = true, options = false), o.resetPlan())
    }

    @Test
    fun `a set option is a change and resets the options`() {
        val o = overrides(options = setOf(0))
        assertTrue(o.optionsSet)
        assertTrue(o.isCustomized())
        assertEquals(OruResetPlan(sources = false, patches = false, options = true), o.resetPlan())
    }

    @Test
    fun `every kind of change is cleared by the reset, and only those`() {
        val o = overrides(
            effective = other,
            preRelease = true,
            turnedDown = setOf(0),
            selection = mapOf(0 to setOf("a")),
            options = setOf(7),
        )
        assertEquals(OruResetPlan(sources = true, patches = true, options = true), o.resetPlan())
        assertTrue(o.isCustomized())
    }
}
