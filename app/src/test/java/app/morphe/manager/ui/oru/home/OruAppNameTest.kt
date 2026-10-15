/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.util.KnownApps
import kotlin.test.Test
import kotlin.test.assertEquals

class OruAppNameTest {

    @Test
    fun `a known app shows its stock name whatever its label says`() {
        assertEquals("YouTube", oruAppName(KnownApps.YOUTUBE, "YouTube Morphe"))
        assertEquals("YouTube Music", oruAppName(KnownApps.YOUTUBE_MUSIC, "YouTube Music Morphe"))
        assertEquals("Reddit", oruAppName(KnownApps.REDDIT, "Reddit (Morphe)"))
    }

    @Test
    fun `the brand word is taken off the end of a label`() {
        assertEquals("Telegram", oruAppName("org.telegram.messenger", "Telegram Morphe"))
        assertEquals("YouTube Music", oruAppName("com.example.app", "YouTube Music Morphe"))
    }

    @Test
    fun `the brand word is taken off the start of a label`() {
        assertEquals("YouTube", oruAppName("com.example.app", "Morphe YouTube"))
    }

    @Test
    fun `the brand word is taken out of brackets and after a dash`() {
        assertEquals("YouTube", oruAppName("com.example.app", "YouTube (Morphe)"))
        assertEquals("YouTube", oruAppName("com.example.app", "YouTube - Morphe"))
    }

    @Test
    fun `the brand word is matched whatever its case`() {
        assertEquals("YouTube", oruAppName("com.example.app", "YouTube MORPHE"))
        assertEquals("YouTube", oruAppName("com.example.app", "MORPHE YouTube"))
    }

    @Test
    fun `a label that is only the brand word is shown as it is`() {
        assertEquals("Morphe", oruAppName("com.example.app", "Morphe"))
    }

    @Test
    fun `a label without the brand word is shown as it is`() {
        assertEquals("Fancy Tool", oruAppName("com.example.app", "Fancy Tool"))
    }

    @Test
    fun `a word that only starts with the brand word is kept`() {
        assertEquals("Morphed Tool", oruAppName("com.example.app", "Morphed Tool"))
        assertEquals("YouTubeMorphe", oruAppName("com.example.app", "YouTubeMorphe"))
    }

    @Test
    fun `a clone is named from its label the same way, so the card suffix goes on the stock name`() {
        assertEquals("YouTube", oruAppName("com.google.android.youtube.clone", "YouTube Morphe"))
    }
}
