/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruRevealPacingTest {

    /**
     * Runs the paced reveal frame by frame, the way the hexagon does. [completedAt] is the real count at a time, [gapAt]
     * the gap at a time. Returns the start time of every patch begun, and the displayed count of every frame.
     */
    private fun reveal(
        total: Int,
        frames: Int,
        completedAt: (Long) -> Int,
        gapAt: (Long) -> Long,
        frameMs: Long = 16L,
    ): Pair<List<Long>, List<Int>> {
        val starts = mutableListOf<Long>()
        val shownAt = mutableListOf<Int>()
        var now = 0L
        repeat(frames) {
            val real = completedAt(now).coerceIn(0, total)
            val shown = oruRevealClamp(starts.size, real)
            while (starts.size > shown) starts.removeAt(starts.lastIndex)
            for (start in oruRevealStarts(starts.size, real, starts.lastOrNull(), now, gapAt(now))) starts += start
            shownAt += starts.size
            now += frameMs
        }
        return starts to shownAt
    }

    @Test
    fun `the displayed count never passes the real count`() {
        val completedAt = { t: Long -> (t / 100L).toInt() }
        val (_, shownAt) = reveal(96, frames = 1000, completedAt = completedAt, gapAt = { 40L })
        for ((frame, shown) in shownAt.withIndex()) {
            assertTrue(shown <= completedAt(frame * 16L).coerceAtMost(96), "frame $frame shows $shown")
        }
    }

    @Test
    fun `the displayed count only goes up while the run goes on`() {
        val completedAt = { t: Long -> (t / 100L).toInt() }
        val (_, shownAt) = reveal(96, frames = 1000, completedAt = completedAt, gapAt = { 40L })
        assertTrue(shownAt.zipWithNext().all { (before, after) -> after >= before })
    }

    @Test
    fun `a restart drops the displayed count to the real one and then counts up again`() {
        val completedAt = { t: Long -> if (t < 1000L) (t / 50L).toInt() else if (t < 2000L) 0 else (t / 50L - 40L).toInt() }
        val (_, shownAt) = reveal(96, frames = 200, completedAt = completedAt, gapAt = { 40L })
        for ((frame, shown) in shownAt.withIndex()) {
            assertTrue(shown <= completedAt(frame * 16L).coerceIn(0, 96), "frame $frame shows $shown")
        }
        assertEquals(0, shownAt[(1000 / 16) + 1])
        assertTrue(shownAt.last() > 0)
    }

    @Test
    fun `a clamp never raises the count and drops it to a lower real count`() {
        assertEquals(0, oruRevealClamp(96, 0))
        assertEquals(40, oruRevealClamp(40, 50))
        assertEquals(50, oruRevealClamp(50, 50))
    }

    @Test
    fun `consecutive patches begin at least one gap apart, so ninety six take four seconds or more`() {
        val gap = OruMotion.PieceGapMillis.toLong()
        val (starts, _) = reveal(96, frames = 600, completedAt = { 96 }, gapAt = { gap })
        assertEquals(96, starts.size)
        assertTrue(starts.zipWithNext().all { (before, after) -> after - before >= gap })
        assertTrue(starts.last() - starts.first() >= 95 * gap)
    }

    @Test
    fun `a finished run plays its catch-up out in at most three seconds, flights included`() {
        val gap = oruRevealGapMs(96, ended = true)
        val (starts, _) = reveal(96, frames = 400, completedAt = { 96 }, gapAt = { gap })
        assertEquals(96, starts.size)
        val landed = starts.last() + OruMotion.PieceFlightMillis
        assertTrue(landed - starts.first() <= OruMotion.PieceCatchUpMillis, "catch-up ends at $landed")
        assertTrue(starts.zipWithNext().all { (before, after) -> after - before >= OruMotion.PieceMinGapMillis })
    }

    @Test
    fun `the gap is the normal one while a run goes on, and shrinks to fit a finished catch-up`() {
        assertEquals(OruMotion.PieceGapMillis.toLong(), oruRevealGapMs(96, ended = false))
        assertEquals(24L, oruRevealGapMs(96, ended = true))
        assertEquals(OruMotion.PieceGapMillis.toLong(), oruRevealGapMs(1, ended = true))
        assertEquals(OruMotion.PieceMinGapMillis.toLong(), oruRevealGapMs(1000, ended = true))
    }

    @Test
    fun `a pause does not release a burst of patches at once`() {
        val starts = oruRevealStarts(shown = 1, completed = 10, lastStartMs = 0L, nowMs = 1000L, gapMs = 40L)
        assertTrue(starts.size <= 2, "a long pause released ${starts.size} patches")
        assertTrue(starts.all { it <= 1000L })
    }

    @Test
    fun `a run with nothing begun starts one patch at once`() {
        assertEquals(listOf(100L), oruRevealStarts(shown = 0, completed = 3, lastStartMs = null, nowMs = 100L, gapMs = 40L))
    }

    @Test
    fun `starts are due one gap after the last, never in the future, and never beyond the real count`() {
        assertEquals(listOf(260L, 300L), oruRevealStarts(shown = 1, completed = 10, lastStartMs = 100L, nowMs = 300L, gapMs = 40L))
        assertEquals(listOf(300L), oruRevealStarts(shown = 2, completed = 3, lastStartMs = 260L, nowMs = 300L, gapMs = 40L))
        assertTrue(oruRevealStarts(shown = 3, completed = 3, lastStartMs = 260L, nowMs = 9000L, gapMs = 40L).isEmpty())
    }

    @Test
    fun `a patch has landed one flight after it began`() {
        assertTrue(oruRevealLanded(null, nowMs = 0L))
        assertFalse(oruRevealLanded(100L, nowMs = 100L + OruMotion.PieceFlightMillis - 1))
        assertTrue(oruRevealLanded(100L, nowMs = 100L + OruMotion.PieceFlightMillis))
    }

    @Test
    fun `a flight starts at full travel, lands at zero and eases out`() {
        val flight = OruMotion.PieceFlightMillis.toLong()
        assertEquals(1f, oruFlightTravel(0L))
        assertEquals(1f, oruFlightTravel(-5L))
        assertEquals(0f, oruFlightTravel(flight))
        assertEquals(0f, oruFlightTravel(flight + 300L))
        val travel = (0L..flight).map { oruFlightTravel(it) }
        assertTrue(travel.zipWithNext().all { (before, after) -> after <= before })
        assertTrue(oruFlightTravel(flight / 2) < 0.5f, "a soft ease out covers most of the way early")
    }
}
