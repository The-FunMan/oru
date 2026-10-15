/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruChannelRulesTest {

    private fun source(uid: Int, usesPrerelease: Boolean, supported: Boolean) = OruChannelSource(
        uid = uid,
        name = "Source $uid",
        usesPrerelease = usesPrerelease,
        preReleaseSupported = supported,
    )

    @Test
    fun `a source that supports pre-release offers both channels`() {
        val row = oruChannelStates(listOf(source(1, usesPrerelease = false, supported = true))).single()

        assertTrue(row.preReleaseAvailable)
        assertFalse(row.preRelease)
    }

    @Test
    fun `a source switched to pre-release shows pre-release`() {
        val row = oruChannelStates(listOf(source(1, usesPrerelease = true, supported = true))).single()

        assertTrue(row.preRelease)
        assertTrue(row.preReleaseAvailable)
    }

    @Test
    fun `a source that cannot offer pre-release shows stable only`() {
        // A stale switch on a source with no branch must not read as pre-release
        val row = oruChannelStates(listOf(source(2, usesPrerelease = true, supported = false))).single()

        assertFalse(row.preRelease)
        assertFalse(row.preReleaseAvailable)
    }

    @Test
    fun `one row per source, in the order given`() {
        val rows = oruChannelStates(
            listOf(
                source(3, usesPrerelease = false, supported = true),
                source(1, usesPrerelease = true, supported = true),
            ),
        )

        assertEquals(listOf(3, 1), rows.map { it.uid })
        assertEquals(listOf("Source 3", "Source 1"), rows.map { it.name })
    }

    @Test
    fun `no sources give no rows`() {
        assertTrue(oruChannelStates(emptyList()).isEmpty())
    }

    private fun state(uid: Int, preRelease: Boolean) = OruChannelState(
        uid = uid,
        name = "Source $uid",
        preRelease = preRelease,
        preReleaseAvailable = true,
    )

    @Test
    fun `no covering source has no channel summary`() {
        assertEquals(null, oruChannelSummary(emptyList()))
    }

    @Test
    fun `every source on stable summarises as stable`() {
        val summary = oruChannelSummary(listOf(state(1, preRelease = false), state(2, preRelease = false)))

        assertEquals(OruChannelSummary.Stable, summary)
    }

    @Test
    fun `every source on pre-release summarises as pre-release`() {
        val summary = oruChannelSummary(listOf(state(1, preRelease = true), state(2, preRelease = true)))

        assertEquals(OruChannelSummary.PreRelease, summary)
    }

    @Test
    fun `one source on pre-release of two is mixed and counts both`() {
        val summary = oruChannelSummary(listOf(state(1, preRelease = true), state(2, preRelease = false)))

        assertEquals(OruChannelSummary.Mixed(preRelease = 1, sources = 2), summary)
    }

    @Test
    fun `a single source follows its own channel`() {
        assertEquals(OruChannelSummary.Stable, oruChannelSummary(listOf(state(1, preRelease = false))))
        assertEquals(OruChannelSummary.PreRelease, oruChannelSummary(listOf(state(1, preRelease = true))))
    }
}
