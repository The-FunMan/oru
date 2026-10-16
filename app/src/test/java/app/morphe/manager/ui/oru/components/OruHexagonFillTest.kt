/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.run.oruRunFill
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hexagon of a run of [PATCHES] patches. Its patch stage is [SHARE] of the whole fill: the share the signing and the
 * other stages leave, less the install, see oruRunFill. Those stages come before the patches, at [BEFORE_PATCHES].
 */
class OruHexagonFillTest {

    private val ring = oruHexRingFor(PATCHES, SHARE)
    private val pieces = oruHexPieceCount(ring)
    private val order = oruHexFillOrder(ring)

    @Test
    fun `the fill order lists every piece once`() {
        assertEquals((0 until pieces).toList(), order.sorted())
        assertEquals(order.toList(), oruHexFillOrder(ring).toList(), "the order is the same every time")
    }

    @Test
    fun `the fill starts at the bottom of the hexagon and rises`() {
        // Screen y grows downwards, so the bottom has the largest centroid y
        for ((lower, higher) in order.zipWithNext()) {
            assertTrue(
                centroidY(lower) >= centroidY(higher) - ROW_EPSILON,
                "piece $higher is lit after $lower but sits lower",
            )
        }
    }

    @Test
    fun `at every fill the lit pieces lie below the unlit ones, so the lit area is one region`() {
        val row = sqrt(3f) / 2f / ring
        for (lit in 0..pieces) {
            val litLowest = order.take(lit).minOfOrNull { centroidY(it) } ?: continue
            val unlitHighest = order.drop(lit).maxOfOrNull { centroidY(it) } ?: continue
            assertTrue(litLowest >= unlitHighest - row, "at $lit lit pieces a lit piece sits above an unlit one")
        }
    }

    private fun centroidY(piece: Int): Float {
        val triangle = oruHexTriangle(piece, ring)
        return (triangle.a.y + triangle.b.y + triangle.c.y) / 3f
    }

    @Test
    fun `the lit pieces never go back as the fill rises`() {
        var previous = 0
        for (step in 0..1000) {
            val lit = oruHexLit(step / 1000f, pieces)
            assertTrue(lit >= previous, "fill ${step / 1000f} lights $lit after $previous")
            previous = lit
        }
    }

    @Test
    fun `no piece lights before the run reaches it, and all of them only at a full fill`() {
        assertEquals(0, oruHexLit(0f, pieces))
        assertEquals(0, oruHexLit(Float.NaN, pieces))
        assertTrue(oruHexLit(0.99999f, pieces) < pieces)
        assertEquals(pieces, oruHexLit(1f, pieces))
    }

    @Test
    fun `the hexagon is whole when the run is signed but settles only at the final step`() {
        // The run's pieces are all lit once it is signed, and the install settles them with the check
        val beforeInstall = oruRunFill(1f, installAhead = true)
        assertEquals(pieces, oruHexRest(beforeInstall, pieces, failed = false).lit)
        assertFalse(oruHexRest(beforeInstall, pieces, failed = false).settled)
        assertTrue(oruHexRest(0.5f, pieces, failed = false).lit < pieces)
        for (step in 0..899) {
            assertFalse(oruHexRest(step / 1000f, pieces, failed = false).settled, "fill ${step / 1000f} settled")
        }
        assertTrue(oruHexRest(1f, pieces, failed = false).settled)
    }

    @Test
    fun `a failed run never completes, wherever its fill stopped`() {
        assertFalse(oruHexRest(0.5f, pieces, failed = true).settled)
        assertFalse(oruHexRest(1f, pieces, failed = true).settled)
    }

    @Test
    fun `with and without an install the run completes at its own last step`() {
        assertTrue(oruHexRest(oruRunFill(1f, installAhead = false), pieces, failed = false).settled)
        assertFalse(oruHexRest(oruRunFill(1f, installAhead = true), pieces, failed = false).settled)
    }

    @Test
    fun `the patch stage lights about one piece a patch inside its share`() {
        val litAt = { done: Int -> oruHexLit(BEFORE_PATCHES + SHARE * done / PATCHES, pieces) }
        val perPatch = (1..PATCHES).map { litAt(it) - litAt(it - 1) }
        assertTrue(perPatch.all { it in 1..2 }, "pieces per patch $perPatch")
        val lit = litAt(PATCHES) - litAt(0)
        assertTrue(abs(lit - PATCHES) <= 5, "the patch stage lights $lit pieces for $PATCHES patches")
    }

    @Test
    fun `the ring holds a piece a patch across the patch stage, and a wait is cut for the preparing count`() {
        assertTrue(pieces >= PATCHES / SHARE)
        assertEquals(oruHexRingSize(OruHexPrepPatches), oruHexRingFor(0, SHARE))
        assertEquals(oruHexRingSize(PATCHES), oruHexRingFor(PATCHES, 0f))
    }

    @Test
    fun `reduced motion lands on the picture the animated reveal ends on`() {
        for (fill in listOf(0f, BEFORE_PATCHES, 0.5f, oruRunFill(1f, installAhead = true), 1f)) {
            val rest = oruHexRest(fill, pieces, failed = false)
            val (lit, landed) = revealEnd(rest.lit, ended = fill >= 1f)
            assertEquals(rest.lit, lit, "fill $fill")
            assertTrue(landed, "fill $fill: the last flight lands")
        }
    }

    /**
     * Runs the paced reveal frame by frame to its end, the way the hexagon does, for a hexagon that must light [lit]
     * pieces. Returns how many pieces began to fly in and whether the last flight has landed.
     */
    private fun revealEnd(lit: Int, ended: Boolean): Pair<Int, Boolean> {
        val starts = mutableListOf<Long>()
        val gap = oruRevealGapMs(pieces, ended)
        var now = 0L
        repeat(FRAMES) {
            now += FRAME_MS
            starts += oruRevealStarts(starts.size, lit, starts.lastOrNull(), now, gap)
        }
        return starts.size to oruRevealLanded(starts.lastOrNull(), now)
    }

    private companion object {
        const val PATCHES = 50
        const val SHARE = 0.36f
        const val BEFORE_PATCHES = 0.09f
        const val FRAMES = 2000
        const val FRAME_MS = 16L
        /** Float noise between two centroids of one row, in hexagon units. */
        const val ROW_EPSILON = 1e-3f
    }
}
