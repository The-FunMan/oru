/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruHexagonAssemblyTest {

    private val sizes = listOf(1, 6, 24, 54, 96, 102, 150)

    @Test
    fun `the smallest ring holds the patch count`() {
        assertEquals(1, oruHexRingSize(0))
        assertEquals(1, oruHexRingSize(1))
        assertEquals(1, oruHexRingSize(6))
        assertEquals(2, oruHexRingSize(24))
        assertEquals(3, oruHexRingSize(54))
        assertEquals(4, oruHexRingSize(96))
        assertEquals(5, oruHexRingSize(102))
        assertEquals(5, oruHexRingSize(150))
    }

    @Test
    fun `every piece is inside the hexagon and all pieces have the same area that sums to the hexagon`() {
        val hexagonArea = 3.0 * sqrt(3.0) / 2.0
        for (total in sizes) {
            val k = oruHexRingSize(total)
            val count = oruHexPieceCount(k)
            val firstArea = area(oruHexTriangle(0, k))
            var sum = 0.0
            for (index in 0 until count) {
                val triangle = oruHexTriangle(index, k)
                for (point in listOf(triangle.a, triangle.b, triangle.c)) {
                    val radius = sqrt((point.x * point.x + point.y * point.y).toDouble())
                    assertTrue(radius <= 1.0 + EPS, "piece $index of $total stays inside")
                }
                assertTrue(abs(area(triangle) - firstArea) < EPS, "piece $index of $total has the same area")
                sum += area(triangle)
            }
            assertTrue(abs(sum - hexagonArea) < EPS, "pieces of $total cover the hexagon")
        }
    }

    @Test
    fun `no two pieces are the same triangle`() {
        for (total in sizes) {
            val k = oruHexRingSize(total)
            val count = oruHexPieceCount(k)
            val centroids = (0 until count).map { index ->
                val t = oruHexTriangle(index, k)
                Pair(
                    Math.round((t.a.x + t.b.x + t.c.x) / 3f * 1000),
                    Math.round((t.a.y + t.b.y + t.c.y) / 3f * 1000),
                )
            }
            assertEquals(count, centroids.toSet().size, "pieces of $total are distinct")
        }
    }

    @Test
    fun `the piece count is six times k squared`() {
        assertEquals(6, oruHexPieceCount(1))
        assertEquals(24, oruHexPieceCount(2))
        assertEquals(96, oruHexPieceCount(4))
        assertEquals(150, oruHexPieceCount(5))
    }

    @Test
    fun `noise is in range and repeats for the same index`() {
        for (index in 0 until 300) {
            val value = oruNoise(index)
            assertTrue(value >= 0f && value < 1f)
            assertEquals(value, oruNoise(index))
        }
    }

    @Test
    fun `the preparing hexagon is cut for ninety six pieces until the real total is known`() {
        assertEquals(4, oruHexRingSize(OruHexPrepPatches))
        assertEquals(96, oruHexPieceCount(oruHexRingSize(OruHexPrepPatches)))
    }

    @Test
    fun `the sweep lights a piece most under it and rests at the floor beyond its reach`() {
        assertEquals(0.5f, oruSweepAlpha(1f, 1f), EPS_F)
        assertEquals(0.18f, oruSweepAlpha(0f, 3.1f), EPS_F)
    }

    @Test
    fun `the sweep alpha stays between the floor and the crest and does not care about direction`() {
        var angle = -10f
        while (angle <= 10f) {
            val alpha = oruSweepAlpha(angle, 0.7f)
            assertTrue(alpha in 0.18f..0.5f, "angle $angle gives $alpha")
            assertEquals(alpha, oruSweepAlpha(0.7f, angle), EPS_F, "symmetric at $angle")
            angle += 0.1f
        }
    }

    @Test
    fun `the finished shade stays small`() {
        for (piece in 0 until 300) {
            assertTrue(oruHexShade(piece) in 0f..0.2f)
        }
    }

    private fun area(t: OruHexTriangle): Double {
        val cross = (t.b.x - t.a.x).toDouble() * (t.c.y - t.a.y).toDouble() -
            (t.c.x - t.a.x).toDouble() * (t.b.y - t.a.y).toDouble()
        return abs(cross) / 2.0
    }

    private companion object {
        const val EPS = 1e-4
        const val EPS_F = 1e-5f
    }
}
