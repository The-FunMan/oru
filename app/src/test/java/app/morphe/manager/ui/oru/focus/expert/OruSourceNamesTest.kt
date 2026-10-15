/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.domain.bundles.AppSourceFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OruSourceNamesTest {

    @Test
    fun `the default source is shown under the neutral label, whatever it is configured as`() {
        assertEquals(ORU_DEFAULT_SOURCE_LABEL, oruSourceDisplayName(0, "Morphe Patches"))
        assertEquals("Default patches", oruSourceDisplayName(0, ""))
    }

    @Test
    fun `a user-added source keeps its own configured name`() {
        assertEquals("My patches", oruSourceDisplayName(4, "My patches"))
    }

    @Test
    fun `the neutral label does not name the upstream project`() {
        assertFalse(ORU_DEFAULT_SOURCE_LABEL.contains("morphe", ignoreCase = true))
    }

    private fun labelFact(uid: Int, name: String, path: String?) = AppSourceFact(
        uid = uid,
        name = name,
        isDefault = path == null,
        repositoryPath = path,
        patchVersion = null,
        patchCount = 0,
        recommendedVersion = null,
    )

    @Test
    fun `two sources of the same name are told apart by their repository path`() {
        val labels = oruSourceLabels(
            listOf(
                labelFact(1, "Morphe Patches", "Morphe/patches"),
                labelFact(2, "Morphe Patches", "someone/fork"),
            ),
        )
        assertEquals("Morphe Patches (Morphe/patches)", labels[1])
        assertEquals("Morphe Patches (someone/fork)", labels[2])
    }

    @Test
    fun `a source with a unique name keeps it, with no path added`() {
        val labels = oruSourceLabels(listOf(labelFact(1, "Morphe Patches", "Morphe/patches"), labelFact(0, "Default patches", null)))
        assertEquals("Morphe Patches", labels[1])
        assertEquals("Default patches", labels[0])
    }

    @Test
    fun `a built-in source that shares a name with a remote one keeps its name, the remote one gets its path`() {
        val labels = oruSourceLabels(
            listOf(labelFact(0, "Patches", null), labelFact(5, "Patches", "owner/repo")),
        )
        assertEquals("Patches", labels[0])
        assertEquals("Patches (owner/repo)", labels[5])
    }

    @Test
    fun `names are compared without regard to case`() {
        val labels = oruSourceLabels(
            listOf(labelFact(1, "Community", "a/b"), labelFact(2, "community", "c/d")),
        )
        assertEquals("Community (a/b)", labels[1])
        assertEquals("community (c/d)", labels[2])
    }
}
