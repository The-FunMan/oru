/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruJellyTest {
    @Test
    fun `a release overshoots to the mirrored squash of the button`() {
        val overshootX = oruJellyScale(pressScale = 1.03f, amount = -OruJellyOvershoot)
        val overshootY = oruJellyScale(pressScale = 0.94f, amount = -OruJellyOvershoot)

        assertEquals(0.98f, overshootX, absoluteTolerance = 0.001f)
        assertEquals(1.04f, overshootY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `a full press reaches the press scale and rest is one`() {
        assertEquals(1.03f, oruJellyScale(pressScale = 1.03f, amount = 1f), absoluteTolerance = 0.0001f)
        assertEquals(0.94f, oruJellyScale(pressScale = 0.94f, amount = 1f), absoluteTolerance = 0.0001f)
        assertEquals(1f, oruJellyScale(pressScale = 0.94f, amount = 0f), absoluteTolerance = 0.0001f)
    }

    @Test
    fun `the rubber band follows a small drag almost one to one`() {
        assertEquals(0f, oruRubberBand(offset = 0f, limit = 100f), absoluteTolerance = 0.0001f)
        assertEquals(9.0909f, oruRubberBand(offset = 10f, limit = 100f), absoluteTolerance = 0.001f)
    }

    @Test
    fun `the rubber band halves the limit at the limit and never passes it`() {
        assertEquals(50f, oruRubberBand(offset = 100f, limit = 100f), absoluteTolerance = 0.0001f)
        assertTrue(oruRubberBand(offset = 1_000_000f, limit = 100f) < 100f)
    }

    @Test
    fun `the rubber band keeps the direction of the drag`() {
        assertEquals(-50f, oruRubberBand(offset = -100f, limit = 100f), absoluteTolerance = 0.0001f)
    }

    @Test
    fun `a rubber band with no room does not move`() {
        assertEquals(0f, oruRubberBand(offset = 40f, limit = 0f), absoluteTolerance = 0.0001f)
    }

    @Test
    fun `the first row of a cascade starts at once and each next row waits one more step`() {
        assertEquals(0, oruCascadeDelayMillis(0))
        assertEquals(OruMotion.CascadeStaggerMillis, oruCascadeDelayMillis(1))
        assertEquals(2 * OruMotion.CascadeStaggerMillis, oruCascadeDelayMillis(2))
    }

    @Test
    fun `the cascade stops waiting at the cap, so a long sheet does not hold its last row`() {
        assertEquals(OruMotion.CascadeCapMillis, oruCascadeDelayMillis(8))
        assertEquals(OruMotion.CascadeCapMillis, oruCascadeDelayMillis(9))
        assertEquals(OruMotion.CascadeCapMillis, oruCascadeDelayMillis(40))
    }

    @Test
    fun `a negative row index counts as the first row`() {
        assertEquals(0, oruCascadeDelayMillis(-2))
    }
}
