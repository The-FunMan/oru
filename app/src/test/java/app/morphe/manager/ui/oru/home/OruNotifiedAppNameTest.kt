/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.util.KnownApps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OruNotifiedAppNameTest {

    @Test
    fun `a known app is named by its stock name, as on the Home card`() {
        assertEquals("YouTube", oruNotifiedAppName(KnownApps.YOUTUBE, "YouTube Morphe"))
    }

    @Test
    fun `the brand word is taken off the end, the start, the brackets and the middle of a label`() {
        assertEquals("YouTube", oruNotifiedAppName("com.example.tool", "YouTube Morphe"))
        assertEquals("YouTube", oruNotifiedAppName("com.example.tool", "Morphe YouTube"))
        assertEquals("YouTube", oruNotifiedAppName("com.example.tool", "YouTube (Morphe)"))
        assertEquals("YouTube Music", oruNotifiedAppName("com.example.tool", "YouTube Morphe Music"))
    }

    @Test
    fun `a label that is only the brand word falls back to the package name`() {
        assertEquals("tool", oruNotifiedAppName("com.example.tool", "Morphe"))
    }

    @Test
    fun `a blank label falls back to the package name`() {
        assertEquals("tool", oruNotifiedAppName("com.example.tool", ""))
        assertEquals("tool", oruNotifiedAppName("com.example.tool", "   "))
    }

    @Test
    fun `a word that only holds the brand letters is not cut apart, the package name stands in`() {
        assertEquals("tool", oruNotifiedAppName("com.example.tool", "Morphed Tool"))
        assertEquals("tool", oruNotifiedAppName("com.example.tool", "YouTubeMorphe"))
    }

    @Test
    fun `a plain label is shown as it is`() {
        assertEquals("Fancy Tool", oruNotifiedAppName("com.example.tool", "Fancy Tool"))
    }

    @Test
    fun `the result never contains the brand letters, whatever the case`() {
        val labels = listOf(
            "YouTube Morphe",
            "MORPHE",
            "morphe",
            "Morphe",
            "Morphed Tool",
            "YouTubeMorphe",
            "YouTube - MoRpHe Music",
            "",
            "   ",
            "Fancy Tool",
        )
        labels.forEach { label ->
            val name = oruNotifiedAppName("com.example.tool", label)
            assertFalse(name.contains("morphe", ignoreCase = true), "'$label' gave '$name'")
        }
    }
}
