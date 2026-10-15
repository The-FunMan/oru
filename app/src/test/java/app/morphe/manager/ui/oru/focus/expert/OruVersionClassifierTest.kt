/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruVersionClassifierTest {

    private fun stable(version: String, installable: Boolean = true) = OruVersionCandidate(
        version = version,
        isExperimental = false,
        experimentalEnabled = false,
        installable = installable,
    )

    private fun experimental(version: String, enabled: Boolean) = OruVersionCandidate(
        version = version,
        isExperimental = true,
        experimentalEnabled = enabled,
        installable = true,
    )

    @Test
    fun `the recommended version is listed first`() {
        val entries = OruVersionClassifier.classify(
            candidates = listOf(stable("18.1"), stable("18.2"), stable("18.3")),
            recommended = "18.2",
        )

        assertEquals(listOf("18.2", "18.1", "18.3"), entries.map { it.version })
        assertEquals(OruVersionKind.Recommended, entries.first().kind)
    }

    @Test
    fun `other stable versions are compatible and keep the source order`() {
        val entries = OruVersionClassifier.classify(
            candidates = listOf(stable("3.2"), stable("3.1"), stable("3.0")),
            recommended = "3.2",
        )

        assertEquals(
            listOf(OruVersionKind.Recommended, OruVersionKind.Compatible, OruVersionKind.Compatible),
            entries.map { it.kind },
        )
        assertEquals(listOf("3.2", "3.1", "3.0"), entries.map { it.version })
        assertTrue(entries.all { it.selectable })
    }

    @Test
    fun `experimental versions are marked and say whether the source has them on`() {
        val entries = OruVersionClassifier.classify(
            candidates = listOf(
                stable("5.0"),
                experimental("5.1", enabled = true),
                experimental("5.2", enabled = false),
            ),
            recommended = "5.0",
        )

        val byVersion = entries.associateBy { it.version }
        assertEquals(OruVersionKind.Experimental, byVersion.getValue("5.1").kind)
        assertTrue(byVersion.getValue("5.1").isExperimental)
        assertFalse(byVersion.getValue("5.1").experimentalOff)
        assertTrue(byVersion.getValue("5.2").experimentalOff)
        // Experimental versions stay after the compatible ones
        assertEquals(listOf("5.0", "5.1", "5.2"), entries.map { it.version })
        assertTrue(entries.all { it.selectable })
    }

    @Test
    fun `a version the phone cannot install is unsupported, last and not selectable`() {
        val entries = OruVersionClassifier.classify(
            candidates = listOf(stable("9.0", installable = false), stable("8.0")),
            recommended = "8.0",
        )

        assertEquals(listOf("8.0", "9.0"), entries.map { it.version })
        val unsupported = entries.last()
        assertEquals(OruVersionKind.Unsupported, unsupported.kind)
        assertFalse(unsupported.selectable)
    }

    @Test
    fun `a version listed stable by one source is stable whatever another source says`() {
        val entries = OruVersionClassifier.classify(
            candidates = listOf(experimental("7.0", enabled = true), stable("7.0")),
            recommended = null,
        )

        assertEquals(1, entries.size)
        assertEquals(OruVersionKind.Compatible, entries.single().kind)
        assertFalse(entries.single().isExperimental)
    }

    @Test
    fun `without a recommended version nothing is marked recommended`() {
        val entries = OruVersionClassifier.classify(
            candidates = listOf(stable("1.0"), stable("0.9")),
            recommended = null,
        )

        assertTrue(entries.none { it.kind == OruVersionKind.Recommended })
        assertEquals(listOf("1.0", "0.9"), entries.map { it.version })
    }

    @Test
    fun `no candidates give no entries`() {
        assertTrue(OruVersionClassifier.classify(emptyList(), recommended = "1.0").isEmpty())
    }
}
