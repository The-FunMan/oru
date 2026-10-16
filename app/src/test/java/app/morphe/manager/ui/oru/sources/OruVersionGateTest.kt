/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.sources

import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.patcher.patch.AppTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruVersionGateTest {

    private fun target(
        version: String?,
        experimental: Boolean = false,
        experimentalEnabled: Boolean = false,
        buildCodes: Set<Int>? = null,
    ) = BundledAppTarget(
        target = AppTarget(version = version, isExperimental = experimental),
        bundleUid = 0,
        bundleName = "Source",
        buildCodes = buildCodes,
        experimentalEnabled = experimentalEnabled,
    )

    private val stable = listOf(target("21.20.405"), target("21.16.256"))

    @Test
    fun `a version the source patches is supported`() {
        assertEquals(OruVersionGate.Supported, oruVersionGate("21.20.405", null, stable))
        assertTrue(oruVersionGate("21.20.405", null, stable).patchable)
    }

    @Test
    fun `a version newer than the source covers is unsupported`() {
        val gate = oruVersionGate("21.39.522", null, stable)
        assertEquals(OruVersionGate.Unsupported, gate)
        assertFalse(gate.patchable)
    }

    @Test
    fun `a version older than the source covers is unsupported`() {
        assertEquals(OruVersionGate.Unsupported, oruVersionGate("21.01.000", null, stable))
    }

    @Test
    fun `an experimental version is allowed only while the source has experimental versions on`() {
        val off = listOf(target("21.30.000", experimental = true, experimentalEnabled = false))
        assertEquals(OruVersionGate.ExperimentalOff, oruVersionGate("21.30.000", null, off))
        assertFalse(oruVersionGate("21.30.000", null, off).patchable)

        val on = listOf(target("21.30.000", experimental = true, experimentalEnabled = true))
        assertEquals(OruVersionGate.ExperimentalAllowed, oruVersionGate("21.30.000", null, on))
        assertTrue(oruVersionGate("21.30.000", null, on).patchable)
    }

    @Test
    fun `a version the source has as stable is supported even when another version is experimental`() {
        val mixed = stable + target("21.30.000", experimental = true, experimentalEnabled = false)
        assertEquals(OruVersionGate.Supported, oruVersionGate("21.20.405", null, mixed))
    }

    @Test
    fun `no version data from the source, or no version of the app, is not judged`() {
        assertEquals(OruVersionGate.Supported, oruVersionGate("21.39.522", null, emptyList()))
        assertEquals(OruVersionGate.Supported, oruVersionGate(null, null, stable))
        assertEquals(OruVersionGate.Supported, oruVersionGate("", null, stable))
    }

    @Test
    fun `a source with a universal target accepts any version`() {
        assertEquals(OruVersionGate.Supported, oruVersionGate("9.9.9", null, listOf(target(null))))
    }

    @Test
    fun `a build code outside the ones the source names is unsupported`() {
        val coded = listOf(target("21.20.405", buildCodes = setOf(100)))
        assertEquals(OruVersionGate.Unsupported, oruVersionGate("21.20.405", 99L, coded))
        assertEquals(OruVersionGate.Supported, oruVersionGate("21.20.405", 100L, coded))
        assertEquals(OruVersionGate.Supported, oruVersionGate("21.20.405", null, coded))
    }

    @Test
    fun `an unsupported version is read as newer or older than the newest the source patches`() {
        assertEquals(OruVersionSide.Newer, oruVersionSide("21.39.522", "21.20.405"))
        assertEquals(OruVersionSide.Older, oruVersionSide("21.01.000", "21.20.405"))
        assertEquals(OruVersionSide.Unknown, oruVersionSide("21.39.522", null))
    }
}
