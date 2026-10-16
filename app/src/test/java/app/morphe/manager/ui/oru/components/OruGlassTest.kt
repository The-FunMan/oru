/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals

class OruGlassTest {
    @Test
    fun `the dock and the Home bar have 28 dp corners, the pills 24 dp, and the sheets round only their top`() {
        assertEquals(28, OruGlassForm.Dock.topCornerDp)
        assertEquals(28, OruGlassForm.Dock.bottomCornerDp)
        assertEquals(24, OruGlassForm.Pill.topCornerDp)
        assertEquals(24, OruGlassForm.Pill.bottomCornerDp)
        assertEquals(28, OruGlassForm.Sheet.topCornerDp)
        assertEquals(0, OruGlassForm.Sheet.bottomCornerDp)
    }

    @Test
    fun `a bar draws no glass while the list is at its top`() {
        assertEquals(0f, oruGlassPresence(firstVisibleItem = 0, scrollOffsetPx = 0, fadeDistancePx = 24f))
    }

    @Test
    fun `a bar draws glass in step with the scroll, and in full once the fade distance has scrolled`() {
        assertEquals(0.5f, oruGlassPresence(firstVisibleItem = 0, scrollOffsetPx = 12, fadeDistancePx = 24f))
        assertEquals(1f, oruGlassPresence(firstVisibleItem = 0, scrollOffsetPx = 24, fadeDistancePx = 24f))
        assertEquals(1f, oruGlassPresence(firstVisibleItem = 0, scrollOffsetPx = 60, fadeDistancePx = 24f))
    }

    @Test
    fun `a bar is in full once the first item is off screen, whatever the offset`() {
        assertEquals(1f, oruGlassPresence(firstVisibleItem = 2, scrollOffsetPx = 0, fadeDistancePx = 24f))
    }

    @Test
    fun `a bar without a fade distance steps straight to glass on any scroll`() {
        assertEquals(0f, oruGlassPresence(firstVisibleItem = 0, scrollOffsetPx = 0, fadeDistancePx = 0f))
        assertEquals(1f, oruGlassPresence(firstVisibleItem = 0, scrollOffsetPx = 1, fadeDistancePx = 0f))
    }
}
