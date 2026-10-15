/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OruMotionLogicTest {
    @Test
    fun `glyphs stagger by the step and stop at the cap`() {
        assertEquals(0, oruGlyphDelayMillis(0))
        assertEquals(OruMotion.GlyphStaggerMillis, oruGlyphDelayMillis(1))
        assertEquals(10 * OruMotion.GlyphStaggerMillis, oruGlyphDelayMillis(10))
        assertEquals(OruMotion.GlyphStaggerCapMillis, oruGlyphDelayMillis(40))
        assertEquals(OruMotion.GlyphStaggerCapMillis, oruGlyphDelayMillis(1000))
    }

    @Test
    fun `a negative glyph index counts as the first glyph`() {
        assertEquals(0, oruGlyphDelayMillis(-2))
    }

    @Test
    fun `the drift of a glyph is the same every time it is asked for`() {
        assertEquals(oruGlyphDrift(seed = 7, index = 3), oruGlyphDrift(seed = 7, index = 3))
    }

    @Test
    fun `the drift of each glyph stays in its range`() {
        for (index in 0 until 40) {
            val drift = oruGlyphDrift(seed = 11, index = index)
            val distance = hypot(drift.dxDp, drift.dyDp)

            assertTrue(distance >= 2f - Tolerance && distance <= 6f + Tolerance, "distance $distance at $index")
            assertTrue(abs(drift.rotationDegrees) <= 6f, "turn ${drift.rotationDegrees} at $index")
            assertTrue(drift.scale >= 0.9f - Tolerance && drift.scale <= 1f, "scale ${drift.scale} at $index")
        }
    }

    @Test
    fun `neighbouring glyphs drift in different directions`() {
        val directions = (0 until 10).map { oruGlyphDrift(seed = 11, index = it).dxDp }.toSet()

        assertTrue(directions.size > 1)
        assertNotEquals(oruGlyphDrift(seed = 11, index = 0), oruGlyphDrift(seed = 12, index = 0))
    }

    @Test
    fun `the digits of a count are read most significant first`() {
        assertEquals(listOf(0), oruOdometerDigits(0))
        assertEquals(listOf(9, 6), oruOdometerDigits(96))
        assertEquals(listOf(1, 0, 0), oruOdometerDigits(100))
    }

    @Test
    fun `a negative count shows as zero`() {
        assertEquals(listOf(0), oruOdometerDigits(-4))
    }

    @Test
    fun `the digits at the edges of each digit count`() {
        assertEquals(listOf(0), oruOdometerDigits(0))
        assertEquals(listOf(9), oruOdometerDigits(9))
        assertEquals(listOf(1, 0), oruOdometerDigits(10))
        assertEquals(listOf(9, 9), oruOdometerDigits(99))
        assertEquals(listOf(1, 0, 0), oruOdometerDigits(100))
    }

    @Test
    fun `the digit text of every percent is the decimal string of the value`() {
        for (value in 0..100) {
            assertEquals(value.toString(), oruOdometerDigits(value).joinToString(separator = ""), "value $value")
        }
    }

    @Test
    fun `the digit count of a value is the number of its digits`() {
        assertEquals(1, oruDigitCount(0))
        assertEquals(1, oruDigitCount(9))
        assertEquals(2, oruDigitCount(10))
        assertEquals(2, oruDigitCount(99))
        assertEquals(3, oruDigitCount(100))
        assertEquals(1, oruDigitCount(-4))
    }

    @Test
    fun `a percent slot holds every percent from zero to one hundred`() {
        for (value in 0..100) {
            assertTrue(oruDigitCount(value) <= PercentSlotDigits, "value $value")
        }
        assertEquals(PercentSlotDigits, oruDigitCount(100))
    }

    @Test
    fun `a digit rolls one step to its neighbour`() {
        assertEquals(1f, oruRollTarget(position = 0f, digit = 1))
        assertEquals(6f, oruRollTarget(position = 3.4f, digit = 6))
        assertEquals(4f, oruRollTarget(position = 4f, digit = 4))
    }

    @Test
    fun `a digit takes the shorter way round the ring of digits`() {
        // 9 to 0 is one step up across the wrap, 0 to 9 one step down
        assertEquals(10f, oruRollTarget(position = 9f, digit = 0))
        assertEquals(-1f, oruRollTarget(position = 0f, digit = 9))
        // A step of exactly five goes up
        assertEquals(5f, oruRollTarget(position = 0f, digit = 5))
    }

    @Test
    fun `a roll that is under way is taken from where it is`() {
        // Halfway from 3 to 4, a change to 3 turns back to 3 rather than running on to 4
        assertEquals(3f, oruRollTarget(position = 3.4f, digit = 3))
        assertTrue(abs(oruRollTarget(position = 3.6f, digit = 4) - 4f) < Tolerance)
    }
}

private const val Tolerance = 1e-3f
