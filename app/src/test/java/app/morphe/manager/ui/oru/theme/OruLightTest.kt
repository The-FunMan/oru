/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruLightTest {
    private val g = 9.81

    private fun tilt(x: Double, y: Double, z: Double) = oruTiltFromGravity(x.toFloat(), y.toFloat(), z.toFloat())

    @Test
    fun `the lamp at rest hangs at the top left`() {
        assertTrue(cos(OruLightRestAngle) < 0f, "left")
        assertTrue(sin(OruLightRestAngle) < 0f, "top, with y down")
    }

    @Test
    fun `a phone held to read is level in roll and in pitch`() {
        val reading = Math.toRadians(40.0)
        val held = tilt(0.0, g * cos(reading), g * sin(reading))
        assertEquals(0f, held.roll, 0.001f)
        assertEquals(0f, held.pitch, 0.001f)
    }

    @Test
    fun `an upright phone reads fully back and a flat one fully forward`() {
        assertEquals(-1f, tilt(0.0, g, 0.0).pitch, 0.001f)
        assertEquals(1f, tilt(0.0, 0.0, g).pitch, 0.001f)
    }

    @Test
    fun `the roll is the sideways lean, a full one at 45 degrees and clamped beyond it`() {
        val half = Math.toRadians(22.5)
        assertEquals(0.5f, tilt(g * sin(half), g * cos(half), 0.0).roll, 0.001f)
        val full = Math.toRadians(45.0)
        assertEquals(1f, tilt(g * sin(full), g * cos(full), 0.0).roll, 0.001f)
        assertEquals(1f, tilt(g, 0.0, 0.0).roll, 0.0001f)
        assertEquals(-1f, tilt(-g, 0.0, 0.0).roll, 0.0001f)
    }

    @Test
    fun `a sensor that has not reported is a phone held to read`() {
        val none = oruTiltFromGravity(0f, 0f, 0f)
        assertEquals(0f, none.roll)
        assertEquals(0f, none.pitch)
    }

    @Test
    fun `the tilt never leaves minus one to one`() {
        for (x in listOf(-30.0, -g, 0.0, g, 30.0)) for (y in listOf(-30.0, -g, 0.0, g, 30.0)) {
            for (z in listOf(-30.0, 0.0, g, 30.0)) {
                val t = tilt(x, y, z)
                assertTrue(t.roll in -1f..1f && t.pitch in -1f..1f, "$x $y $z -> $t")
            }
        }
    }

    @Test
    fun `no roll leaves the lamp at rest, and a roll turns it by at most the maximum`() {
        assertEquals(OruLightRestAngle, oruLightAngle(0f))
        assertEquals(OruLightRestAngle + OruLightMaxTurn, oruLightAngle(1f), 0.0001f)
        assertEquals(OruLightRestAngle - OruLightMaxTurn, oruLightAngle(-1f), 0.0001f)
        assertTrue(OruLightMaxTurn < PI.toFloat() / 4f, "the lamp stays in the upper left half")
    }

    @Test
    fun `the lamp angle clamps a roll beyond one and follows the roll in between`() {
        assertEquals(oruLightAngle(1f), oruLightAngle(7f))
        assertEquals(oruLightAngle(-1f), oruLightAngle(-7f))
        var previous = oruLightAngle(-1f)
        for (step in -9..10) {
            val angle = oruLightAngle(step / 10f)
            assertTrue(angle >= previous, "monotonic at $step")
            previous = angle
        }
    }

    @Test
    fun `the lamp never swings to the bottom half of the screen`() {
        for (step in -10..10) {
            assertTrue(sin(oruLightAngle(step / 10f)) < 0f, "step $step")
        }
    }

    @Test
    fun `the low pass moves a share of the way and settles on the reading`() {
        assertEquals(2f, oruLowPass(2f, 10f, 0f))
        assertEquals(10f, oruLowPass(2f, 10f, 1f))
        assertEquals(6f, oruLowPass(2f, 10f, 0.5f), 0.0001f)
        var value = 0f
        repeat(120) { value = oruLowPass(value, 1f, 0.12f) }
        assertEquals(1f, value, 0.001f)
    }

    @Test
    fun `a change under a step is noise and is not published`() {
        assertFalse(oruLightChanged(0f, 0.005f))
        assertFalse(oruLightChanged(0.3f, 0.3f))
        assertTrue(oruLightChanged(0f, 0.01f))
        assertTrue(oruLightChanged(0.5f, 0.4f))
    }

    @Test
    fun `the lamp state rests where it hangs`() {
        val state = OruLightState()
        state.angle = 0.3f
        state.lift = 0.8f
        state.rest()
        assertEquals(OruLightRestAngle, state.angle)
        assertEquals(0f, state.lift)
    }
}
