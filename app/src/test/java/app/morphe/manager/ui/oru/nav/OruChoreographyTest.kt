/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.oruSpringSettleSeconds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruChoreographyTest {
    private fun assertNear(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 1e-4f, "expected $expected but was $actual")
    }

    @Test
    fun cardsLeaveOneStepLaterForEachCardOfDistance() {
        assertEquals(0, oruPartingDelayMillis(index = 2, tappedIndex = 2))
        assertEquals(OruPartingStepMillis, oruPartingDelayMillis(index = 1, tappedIndex = 2))
        assertEquals(OruPartingStepMillis, oruPartingDelayMillis(index = 3, tappedIndex = 2))
        assertEquals(3 * OruPartingStepMillis, oruPartingDelayMillis(index = 5, tappedIndex = 2))
        assertEquals(3 * OruPartingStepMillis, oruPartingDelayMillis(index = -1, tappedIndex = 2))
    }

    @Test
    fun partingDelayStopsAtTheCap() {
        assertEquals(OruPartingMaxDelayMillis, oruPartingDelayMillis(index = 40, tappedIndex = 0))
        assertEquals(OruPartingMaxDelayMillis, oruPartingDelayMillis(index = 0, tappedIndex = 40))
    }

    @Test
    fun cardsAboveTheTappedOneMoveUpAndBelowItMoveDown() {
        assertEquals(-OruPartingRiseDp, oruPartingOffsetDp(index = 0, tappedIndex = 2, away = 1f))
        assertEquals(OruPartingRiseDp, oruPartingOffsetDp(index = 4, tappedIndex = 2, away = 1f))
        assertEquals(0f, oruPartingOffsetDp(index = 2, tappedIndex = 2, away = 1f))
    }

    @Test
    fun partingOffsetFollowsHowFarTheCardHasGone() {
        assertNear(-8f, oruPartingOffsetDp(index = 0, tappedIndex = 2, away = 0.5f))
        assertNear(0f, oruPartingOffsetDp(index = 0, tappedIndex = 2, away = 0f))
    }

    @Test
    fun cascadeStepsByThirtyMillisecondsAndStopsAtTheCap() {
        assertEquals(0, oruCascadeDelayMillis(0))
        assertEquals(OruCascadeStepMillis, oruCascadeDelayMillis(1))
        assertEquals(2 * OruCascadeStepMillis, oruCascadeDelayMillis(2))
        assertEquals(OruCascadeCapMillis, oruCascadeDelayMillis(8))
        assertEquals(OruCascadeCapMillis, oruCascadeDelayMillis(20))
        assertEquals(0, oruCascadeDelayMillis(-3))
    }

    @Test
    fun theHomeToFocusTransitionEndsWithinHalfASecond() {
        // Each piece of the transition starts after its wait and runs for the settle time of its spring
        val glide = oruSpringSettleSeconds(OruMotion.GlideResponseSeconds, OruMotion.GlideDampingRatio)
        val soft = oruSpringSettleSeconds(OruMotion.SoftResponseSeconds, OruMotion.SoftDampingRatio)
        val settle = oruSpringSettleSeconds(OruMotion.SettleResponseSeconds, OruMotion.SettleDampingRatio)
        val tile = oruSpringSettleSeconds(OruMotion.TileResponseSeconds, OruMotion.TileFlightDampingRatio)
        val ends = listOf(
            OruPartingMaxDelayMillis / 1000f + glide,
            OruDockDelayMillis / 1000f + soft,
            oruCascadeDelayMillis(1) / 1000f + soft,
            tile,
            settle,
        )
        assertTrue(ends.all { it <= 0.5f }, "transition ends at $ends")
    }

    @Test
    fun staggerShareIsTheDelayAsAShareOfTheHorizonUpToNearlyAll() {
        assertEquals(0f, oruStaggerShare(0))
        assertEquals(0.5f, oruStaggerShare(OruStaggerHorizonMillis / 2))
        assertEquals(0.9f, oruStaggerShare(OruStaggerHorizonMillis * 3))
    }

    @Test
    fun staggeredHoldsStillUntilItsShareHasPassed() {
        assertEquals(0f, oruStaggered(global = 0.25f, share = 0.5f))
        assertEquals(0f, oruStaggered(global = 0.5f, share = 0.5f))
        assertNear(0.5f, oruStaggered(global = 0.75f, share = 0.5f))
        assertNear(1f, oruStaggered(global = 1f, share = 0.5f))
    }

    @Test
    fun staggeredWithoutAShareIsTheGlobalValue() {
        assertEquals(0.3f, oruStaggered(global = 0.3f, share = 0f))
        assertEquals(1.2f, oruStaggered(global = 1.2f, share = 0f))
    }

    @Test
    fun staggeredKeepsAnOvershootAboveOne() {
        // A spring that passes its target by 10 percent still passes it after the stagger
        assertNear(1.2f, oruStaggered(global = 1.1f, share = 0.5f))
    }
}
