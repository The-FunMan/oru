/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruBlobShapeTest {
    @Test
    fun `presets keep the prototype radii`() {
        assertEquals(
            BlobCorners(34f, 66f, 58f, 42f, 46f, 40f, 60f, 54f),
            OruBlobShape.First.corners,
        )
        assertEquals(
            BlobCorners(30f, 30f, 30f, 30f, 30f, 30f, 30f, 30f),
            OruBlobShape.Second.corners,
        )
        assertEquals(
            BlobCorners(50f, 50f, 38f, 62f, 60f, 42f, 58f, 40f),
            OruBlobShape.Third.corners,
        )
    }

    @Test
    fun `radii that already fit are left as they are`() {
        val fitting = BlobCorners(40f, 60f, 70f, 30f, 25f, 40f, 50f, 50f)

        assertEquals(fitting, OruBlobShape(fitting).corners)
    }

    @Test
    fun `an overflowing side scales every radius by the same factor`() {
        // Top side: 80 + 80 = 160, so the factor is 100 / 160 = 0.625
        val shape = OruBlobShape(BlobCorners(80f, 80f, 50f, 50f, 50f, 50f, 50f, 50f))

        assertEquals(50f, shape.corners.topLeftX, absoluteTolerance = 0.001f)
        assertEquals(50f, shape.corners.topRightX, absoluteTolerance = 0.001f)
        assertEquals(31.25f, shape.corners.bottomRightX, absoluteTolerance = 0.001f)
        assertEquals(31.25f, shape.corners.bottomLeftX, absoluteTolerance = 0.001f)
    }

    @Test
    fun `the smallest factor across all four sides wins`() {
        // Top: 60 + 60 = 120 (factor 0.833). Left: 90 + 90 = 180 (factor 0.556), which is smaller
        val corners = OruBlobShape(BlobCorners(60f, 60f, 0f, 0f, 90f, 0f, 0f, 90f)).corners
        val factor = 100f / 180f

        assertEquals(90f * factor, corners.topLeftY, absoluteTolerance = 0.001f)
        assertEquals(90f * factor, corners.bottomLeftY, absoluteTolerance = 0.001f)
        assertEquals(100f, corners.topLeftY + corners.bottomLeftY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `negative radii become zero`() {
        val corners = OruBlobShape(BlobCorners(-10f, 20f, 20f, 20f, -5f, 20f, 20f, 20f)).corners

        assertEquals(0f, corners.topLeftX)
        assertEquals(0f, corners.topLeftY)
        assertEquals(20f, corners.topRightX)
    }

    @Test
    fun `every preset keeps radii in bounds and each side within 100 percent`() {
        OruBlobShape.Presets.forEach { shape ->
            val c = shape.corners
            val radii = listOf(
                c.topLeftX, c.topRightX, c.bottomRightX, c.bottomLeftX,
                c.topLeftY, c.topRightY, c.bottomRightY, c.bottomLeftY,
            )
            assertTrue(radii.all { it in 0f..100f }, "radius out of bounds in $c")

            val sides = listOf(
                c.topLeftX + c.topRightX,
                c.topRightY + c.bottomRightY,
                c.bottomRightX + c.bottomLeftX,
                c.bottomLeftY + c.topLeftY,
            )
            assertTrue(sides.all { it <= 100f + 0.001f }, "side over 100 percent in $c")
        }
    }

    @Test
    fun `lerp returns the start and the stop at the ends`() {
        val start = OruBlobShape.First.corners
        val stop = OruBlobShape.Second.corners

        assertEquals(start, start.lerp(stop, 0f))
        val end = start.lerp(stop, 1f)
        assertEquals(stop.topLeftX, end.topLeftX, absoluteTolerance = 0.001f)
        assertEquals(stop.bottomLeftY, end.bottomLeftY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `the preset for an app is stable and always a preset`() {
        val keys = listOf("app.morphe.youtube", "com.google.android.youtube", "", "x")

        keys.forEach { key ->
            val first = OruBlobShape.presetFor(key)
            assertTrue(first in OruBlobShape.Presets)
            assertTrue(first === OruBlobShape.presetFor(key))
        }
    }
}
