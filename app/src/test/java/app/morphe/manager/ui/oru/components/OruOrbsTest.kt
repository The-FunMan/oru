/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruOrbsTest {
    private val width = 1000f
    private val height = 2000f

    @Test
    fun `there is one spec for every colour of the palette`() {
        assertEquals(OruOrbCount, OruOrbSpecs.size)
        val palette = oruOrbPalette(Color.Red, emptyList(), emptyList())
        assertEquals(OruOrbCount, palette.size)
    }

    @Test
    fun `the palette takes the primary first, then the app accents, then the fallbacks, then the primary`() {
        val primary = Color.Red
        val first = Color.Green
        val fallbackOne = Color.Blue
        val fallbackTwo = Color.Yellow
        val palette = oruOrbPalette(primary, listOf(first), listOf(fallbackOne, fallbackTwo))
        assertEquals(listOf(primary, first, fallbackTwo, primary), palette)
    }

    @Test
    fun `three accents fill every slot after the primary`() {
        val accents = listOf(Color.Green, Color.Blue, Color.Yellow)
        assertEquals(listOf(Color.Red) + accents, oruOrbPalette(Color.Red, accents, listOf(Color.Black)))
    }

    @Test
    fun `every orb stays on the screen centre-wise for the whole lap`() {
        for (spec in OruOrbSpecs) {
            for (step in 0..200) {
                val phase = step / 200f
                val x = oruOrbX(spec, phase, width)
                val y = oruOrbY(spec, phase, width, height)
                assertTrue(x > 0f && x < width, "x $x at $phase")
                assertTrue(y > 0f && y < height, "y $y at $phase")
            }
        }
    }

    @Test
    fun `the last frame of a lap meets the first, so the loop has no jump`() {
        for (spec in OruOrbSpecs) {
            assertEquals(oruOrbX(spec, 0f, width), oruOrbX(spec, 1f, width), 0.01f)
            assertEquals(oruOrbY(spec, 0f, width, height), oruOrbY(spec, 1f, width, height), 0.01f)
        }
    }

    @Test
    fun `the drift is small beside the radius, so an orb never leaves its place`() {
        for (spec in OruOrbSpecs) {
            assertTrue(spec.driftShare <= spec.radiusShare / 4f, "drift ${spec.driftShare} radius ${spec.radiusShare}")
        }
    }

    @Test
    fun `the orbs sit over the header and over the cards below it`() {
        // The toggles sit in the upper fifth to third of a phone, the cards below. Some orb must reach each band
        val centresY = OruOrbSpecs.map { it.anchorY }
        assertTrue(centresY.any { it in 0.1f..0.35f }, "an orb behind the toggles")
        assertTrue(centresY.any { it in 0.35f..0.6f }, "an orb behind the first card")
        assertTrue(centresY.any { it > 0.6f }, "an orb behind the lower cards")
    }

    @Test
    fun `an orb is more opaque in the dark theme, and never opaque`() {
        assertTrue(oruOrbPeakAlpha(isDark = true) > oruOrbPeakAlpha(isDark = false))
        assertTrue(oruOrbPeakAlpha(isDark = true) < 0.5f)
    }

    @Test
    fun `the orbs stay put at zero scroll and move up as the list scrolls up`() {
        assertEquals(0f, oruOrbParallax(0f, height), 0.0001f)
        assertTrue(oruOrbParallax(500f, height) < 0f, "up")
        assertTrue(oruOrbParallax(-500f, height) > 0f, "a pull past the top moves them down")
    }

    @Test
    fun `the orbs move about a tenth as far as the list for a small scroll`() {
        assertEquals(-50f, oruOrbParallax(500f, height), 1f)
    }

    @Test
    fun `the parallax grows with the scroll but never passes a fifth of the height`() {
        var previous = 0f
        for (scroll in listOf(0f, 200f, 1000f, 5000f, 50_000f, 5_000_000f)) {
            val shift = oruOrbParallax(scroll, height)
            assertTrue(shift <= previous, "monotonic at $scroll")
            assertTrue(shift >= -height * 0.2f, "bounded at $scroll: $shift")
            previous = shift
        }
    }

    @Test
    fun `a surface of no height has no parallax`() {
        assertEquals(0f, oruOrbParallax(500f, 0f))
    }

    @Test
    fun `the orbs slide against the turn of the lamp and stay put when the lamp is at rest`() {
        assertEquals(0f, oruOrbTilt(0f, width), 0.0001f)
        assertTrue(oruOrbTilt(0.5f, width) < 0f)
        assertTrue(oruOrbTilt(-0.5f, width) > 0f)
        assertTrue(kotlin.math.abs(oruOrbTilt(0.6f, width)) < width * 0.05f, "a full turn moves them by a few percent of the width")
    }
}
