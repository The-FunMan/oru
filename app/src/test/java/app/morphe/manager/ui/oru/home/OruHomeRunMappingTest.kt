/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.ui.oru.focus.ActiveRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class OruHomeRunMappingTest {
    private val batch = OruHomeRun(
        packageName = "com.batch.app",
        appName = "Batch app",
        packageInfo = null,
        progress = 0.5f,
        done = 1,
        total = 2,
        focusKey = null,
    )

    private val single = OruHomeRun(
        packageName = "com.single.app",
        appName = "Single app",
        packageInfo = null,
        progress = 0.25f,
        done = 3,
        total = 12,
        focusKey = "slot-single",
    )

    @Test
    fun `an active batch run is chosen over a single-app run`() {
        assertSame(batch, oruHomeRunOf(batch = batch, single = single))
    }

    @Test
    fun `a single-app run is chosen when no batch run is active`() {
        assertSame(single, oruHomeRunOf(batch = null, single = single))
    }

    @Test
    fun `no run when neither is going`() {
        assertNull(oruHomeRunOf(batch = null, single = null))
    }

    @Test
    fun `a finished single-app run marks its card until it is released`() {
        val finished = ActiveRun(
            packageKey = "slot-yt",
            packageName = "com.google.android.youtube",
            label = "YouTube",
            progress = 1f,
            done = 96,
            total = 96,
            ready = true,
        )
        assertEquals(setOf("slot-yt"), oruReadyToInstallOf(finished))
        assertEquals(emptySet<String>(), oruReadyToInstallOf(finished.copy(ready = false)))
        assertEquals(emptySet<String>(), oruReadyToInstallOf(null))
    }

    @Test
    fun `no single-app run when none is published`() {
        assertNull(oruSingleHomeRunOf(active = null) { null })
    }

    @Test
    fun `a single-app run keeps its numbers and opens its own Focus`() {
        val active = ActiveRun(
            packageKey = "slot-yt",
            packageName = "com.google.android.youtube",
            label = "YouTube",
            progress = 0.42f,
            done = 42,
            total = 96,
        )

        val run = oruSingleHomeRunOf(active) { null }

        assertEquals(
            OruHomeRun(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                packageInfo = null,
                progress = 0.42f,
                done = 42,
                total = 96,
                focusKey = "slot-yt",
            ),
            run,
        )
    }

    @Test
    fun `the brand word is taken off the label like the card name`() {
        val active = ActiveRun(
            packageKey = "slot-1",
            packageName = "com.example.app",
            label = "Example (Morphe)",
            progress = 0f,
            done = 0,
            total = 1,
        )

        assertEquals("Example", oruSingleHomeRunOf(active) { null }?.appName)
    }

    @Test
    fun `the package info is looked up by the Focus key, not the package name`() {
        val asked = mutableListOf<String>()
        val active = ActiveRun(
            packageKey = "slot-clone",
            packageName = "com.example.clone",
            label = "Clone",
            progress = 0.1f,
            done = 1,
            total = 10,
        )

        oruSingleHomeRunOf(active) { key ->
            asked += key
            null
        }

        assertEquals(listOf("slot-clone"), asked)
    }
}
