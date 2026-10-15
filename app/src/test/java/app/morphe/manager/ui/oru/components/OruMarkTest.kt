/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruMarkTest {
    private fun assertNear(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.001f, "expected $expected but was $actual")
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertNear(expected.x, actual.x)
        assertNear(expected.y, actual.y)
    }

    @Test
    fun `the ring sweeps 280 degrees clockwise from minus 35 to 245`() {
        assertNear(0f, oruRingSweep(0f))
        assertNear(140f, oruRingSweep(0.5f))
        assertNear(280f, oruRingSweep(1f))
        assertNear(OruRingStartDegrees + OruRingSweepDegrees, 245f)
    }

    @Test
    fun `the sweep is clamped to the ring`() {
        assertNear(0f, oruRingSweep(-1f))
        assertNear(280f, oruRingSweep(3f))
    }

    @Test
    fun `the brand dot sits in the gap of the ring`() {
        // The gap runs from 245 to 325 degrees, and the dot is at minus 62, which is 298
        val dotAngle = (OruRingDotDegrees + 360f)
        assertTrue(dotAngle > OruRingStartDegrees + OruRingSweepDegrees && dotAngle < OruRingStartDegrees + 360f)
    }

    @Test
    fun `the dot is on the ring's radius at minus 62 degrees`() {
        val dot = oruRingDotPoint()
        assertOffset(Offset(54f + 20.5f * 0.46947f, 54f - 20.5f * 0.88295f), dot)
        assertNear(20.5f, Offset(dot.x - 54f, dot.y - 54f).getDistance())
    }

    @Test
    fun `the mark with its dot fits the 66 dp safe circle of the launcher icon`() {
        // The 108 dp icon's central circle is 66 dp across: a radius of 33 units around the centre
        assertTrue(OruRingInkRadius < 33f)
        assertNear(26.5f, OruRingInkRadius)
        assertNear(25.305f, OruRingRadius + OruRingStrokeWidth / 2f)
    }

    @Test
    fun `the rest pose puts the ink across the given diameter`() {
        val rest = oruRingRestPose(centre = Offset(100f, 200f), inkDiameterPx = 53f * 2f)
        assertNear(2f, rest.pixelsPerUnit)
        assertNear(1f, rest.alpha)
        assertOffset(Offset(100f, 200f), rest.centre)
    }

    @Test
    fun `the gather starts at rest and ends on the dot target at the gather scale`() {
        val rest = oruRingRestPose(centre = Offset(100f, 200f), inkDiameterPx = 106f)
        val target = Offset(20f, 40f)

        val start = oruRingGatherPose(rest, target, gather = 0f)
        assertOffset(rest.centre, start.centre)
        assertNear(rest.pixelsPerUnit, start.pixelsPerUnit)
        assertNear(1f, start.alpha)

        val end = oruRingGatherPose(rest, target, gather = 1f)
        assertOffset(target, end.centre)
        assertNear(rest.pixelsPerUnit * OruRingGatherScale, end.pixelsPerUnit)
        assertNear(0f, end.alpha)
    }

    @Test
    fun `the gather keeps the mark opaque until 70 per cent, then fades it`() {
        val rest = oruRingRestPose(centre = Offset.Zero, inkDiameterPx = 106f)
        assertNear(1f, oruRingGatherPose(rest, Offset.Zero, gather = 0.7f).alpha)
        assertNear(0.5f, oruRingGatherPose(rest, Offset.Zero, gather = 0.85f).alpha)
    }

    @Test
    fun `the gather is clamped`() {
        val rest = oruRingRestPose(centre = Offset(100f, 200f), inkDiameterPx = 106f)
        val target = Offset(20f, 40f)
        assertOffset(rest.centre, oruRingGatherPose(rest, target, gather = -1f).centre)
        assertOffset(target, oruRingGatherPose(rest, target, gather = 3f).centre)
    }

    @Test
    fun `the splash stages fit in under 1100 ms`() {
        assertTrue(OruSplashDrawMillis + OruSplashHoldMillis + OruSplashGatherMillis < 1100)
        assertEquals(700, OruSplashDrawMillis)
        assertEquals(120, OruSplashHoldMillis)
        assertEquals(240, OruSplashGatherMillis)
        assertEquals(300, OruSplashReducedHoldMillis)
    }

    @Test
    fun `the wordmark is lower case oru and its dot drops onto the lower-case band`() {
        assertEquals("oru", WordmarkText)
        // 0.20 em of the 27 sp wordmark
        assertNear(5.4f, oruWordmarkDotDrop(scale = 1f).value)
        assertNear(10.8f, oruWordmarkDotDrop(scale = 2f).value)
    }
}
