/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.sources

import app.morphe.manager.domain.bundles.AppSourceFact
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.patcher.patch.AppTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruAppSourcesTest {

    private fun fact(uid: Int, name: String, patches: Int, recommended: String?, isDefault: Boolean = false) = AppSourceFact(
        uid = uid,
        name = name,
        isDefault = isDefault,
        repositoryPath = if (isDefault) null else "owner/$uid",
        patchVersion = "1.0.0",
        patchCount = patches,
        recommendedVersion = recommended,
    )

    private fun target(uid: Int, version: String) = BundledAppTarget(
        target = AppTarget(version = version),
        bundleUid = uid,
        bundleName = "source $uid",
    )

    private val default = fact(uid = 0, name = "Default patches", patches = 25, recommended = "21.20.405", isDefault = true)
    private val community = fact(uid = 9, name = "Community", patches = 7, recommended = "21.16.256")
    private val versions = listOf(
        target(0, "21.20.405"),
        target(0, "21.19.100"),
        target(9, "21.16.256"),
        target(9, "21.20.405"),
    )

    @Test
    fun `the count of an app is the effective source's, never the sum over the sources`() {
        val twin = fact(uid = 9, name = "Community", patches = 25, recommended = "21.16.256")
        val sources = oruAppSourcesOf(listOf(default, twin), versions, turnedDown = emptySet())
        // Both sources have 25 patches for the app: the card says 25 for the one it is patched from, not 50
        assertEquals(25, sources.patchCount)
        assertEquals(0, sources.effective?.uid)
    }

    @Test
    fun `the recommended version is the effective source's, not the first source's`() {
        val sources = oruAppSourcesOf(listOf(default, community), versions, turnedDown = setOf(0))
        assertEquals("21.16.256", sources.recommendedVersion)
        assertEquals(7, sources.patchCount)
    }

    @Test
    fun `the choice is shown only when two sources can patch the app`() {
        assertFalse(oruAppSourcesOf(listOf(default), versions, emptySet()).choosable)
        assertTrue(oruAppSourcesOf(listOf(default, community), versions, emptySet()).choosable)
    }

    @Test
    fun `the version list holds only the effective source's versions, newest first`() {
        val sources = oruAppSourcesOf(listOf(default, community), versions, turnedDown = setOf(0))
        assertEquals(listOf("21.20.405", "21.16.256"), sources.effectiveVersions.map { it.target.version })
    }

    @Test
    fun `versions of one source are sorted newest first whatever order they arrive in`() {
        val unsorted = listOf(target(0, "21.19.100"), target(0, "21.20.405"))
        val sources = oruAppSourcesOf(listOf(default), unsorted, turnedDown = emptySet())
        assertEquals(listOf("21.20.405", "21.19.100"), sources.effectiveVersions.map { it.target.version })
    }

    @Test
    fun `a stored selection of several sources collapses to the default one`() {
        // Legacy state: nothing turned down, both sources used. The Focus shows and patches the default one only
        val sources = oruAppSourcesOf(listOf(community, default), versions, turnedDown = emptySet())
        assertEquals(0, sources.effective?.uid)
        assertEquals(listOf(0, 0), sources.effectiveVersions.map { it.bundleUid })
    }

    @Test
    fun `the home card chip names the effective source only where two sources can patch the app`() {
        val single = oruAppSourcesOf(listOf(default), versions, emptySet())
        val pair = oruAppSourcesOf(listOf(default, community), versions, turnedDown = setOf(0))
        val chips = oruSourceChips(mapOf("one.app" to single, "two.app" to pair))
        assertEquals(mapOf("two.app" to "Community"), chips)
    }
}
