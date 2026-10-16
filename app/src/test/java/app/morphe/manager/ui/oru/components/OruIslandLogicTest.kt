/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The scroll rules of the floating island (oruIslandCollapsed), with deltas in dp. */
class OruIslandLogicTest {

    /** Feeds [deltas] through the rule from [start], and returns the step after the last one. */
    private fun run(start: OruIslandStep, deltas: List<Float>, atTop: Boolean = false): OruIslandStep =
        deltas.fold(start) { step, delta ->
            oruIslandCollapsed(step.collapsed, delta, step.accumulated, atTop)
        }

    private val open = OruIslandStep(collapsed = false, accumulated = 0f)
    private val closed = OruIslandStep(collapsed = true, accumulated = CollapseThresholdDp)

    @Test
    fun smallDownwardScrollKeepsTheIslandOpen() {
        val step = run(open, listOf(-10f))
        assertFalse(step.collapsed)
        assertEquals(10f, step.accumulated)
    }

    @Test
    fun collapsesOnceTheDownwardRunReachesTheThreshold() {
        val step = run(open, listOf(-10f, -13f))
        assertFalse(step.collapsed, "23 dp is short of the 24 dp threshold")

        val reached = run(open, listOf(-10f, -14f))
        assertTrue(reached.collapsed, "24 dp of downward scroll collapses the island")
        assertEquals(CollapseThresholdDp, reached.accumulated)
    }

    @Test
    fun oneLargeDownwardScrollCollapsesAtOnce() {
        assertTrue(run(open, listOf(-40f)).collapsed)
    }

    @Test
    fun staysCollapsedWhileTheListKeepsGoingDown() {
        val step = run(closed, listOf(-5f, -50f))
        assertTrue(step.collapsed)
        assertEquals(CollapseThresholdDp, step.accumulated, "the count is held at the threshold")
    }

    @Test
    fun anyUpwardScrollOpensTheIslandAtOnce() {
        val step = run(closed, listOf(0.5f))
        assertFalse(step.collapsed)
        assertEquals(0f, step.accumulated)
    }

    @Test
    fun anUpwardScrollClearsTheCountSoTheRunStartsAgain() {
        // Down 20, up 1, down 20: no single run reaches 24, so the island stays open
        val step = run(open, listOf(-20f, 1f, -20f))
        assertFalse(step.collapsed)
        assertEquals(20f, step.accumulated)
    }

    @Test
    fun wobblingAroundOnePlaceDoesNotFlicker() {
        // Each cycle moves 10 dp down and then 9 dp up, so the count never reaches the threshold
        val wobble = List(20) { listOf(-10f, 9f) }.flatten()
        val states = wobble.runningFold(open) { step, delta ->
            oruIslandCollapsed(step.collapsed, delta, step.accumulated, atTop = false)
        }.map { it.collapsed }
        assertTrue(states.none { it }, "the island never collapses on a wobble")
    }

    @Test
    fun theTopOfTheListOpensTheIslandAndClearsTheCount() {
        val step = run(closed, listOf(-50f), atTop = true)
        assertFalse(step.collapsed)
        assertEquals(0f, step.accumulated)
    }

    @Test
    fun aZeroDeltaChangesNothing() {
        assertEquals(closed, oruIslandCollapsed(current = true, delta = 0f, accumulated = CollapseThresholdDp, atTop = false))
        assertEquals(
            OruIslandStep(collapsed = false, accumulated = 10f),
            oruIslandCollapsed(current = false, delta = 0f, accumulated = 10f, atTop = false),
        )
    }

    @Test
    fun aTabWithoutRoomForItsLabelIsStillAtLeastFiftySixDp() {
        assertEquals(56f, oruIslandTabDp(labelDp = 0f))
        assertEquals(56f, oruIslandTabDp(labelDp = 2f))
        assertEquals(86f, oruIslandTabDp(labelDp = 34f), "the icon, the gap and the padding come on top of the label")
    }

    @Test
    fun theSwitchTakesBothLabelsWithTheChecksOfOneSelectedOption() {
        // 54 + 58 for the labels, 4 x 16 dp of option padding, one 18 dp check with its 6 dp gap, and the 2 dp between
        assertEquals(202f, oruConnectedPairDp(firstLabelDp = 54f, secondLabelDp = 58f))
    }

    @Test
    fun theLabelsStayWhenTheTabsAndTheSwitchFitTheWidth() {
        // The widths are illustrative. A 448 dp screen gives a 420 dp island, less the 4 dp lens inset on each side
        val switch = oruConnectedPairDp(firstLabelDp = 54f, secondLabelDp = 58f) + 8f
        assertTrue(
            oruIslandShowLabels(
                availableDp = 412f,
                appsLabelDp = 34f,
                settingsLabelDp = 60f,
                switchDp = switch,
            ),
        )
    }

    @Test
    fun theLabelsGoWhenTheyWouldNotFit() {
        val switch = oruConnectedPairDp(firstLabelDp = 54f, secondLabelDp = 58f) + 8f
        // A 360 dp phone gives a 336 dp island, so the same labels need icon only tabs
        assertFalse(
            oruIslandShowLabels(
                availableDp = 328f,
                appsLabelDp = 34f,
                settingsLabelDp = 60f,
                switchDp = switch,
            ),
        )
    }

    @Test
    fun aLargerFontScaleFallsBackToIconsOnly() {
        // At a 1.3 font scale the labels and the switch labels grow, and the same 412 dp no longer holds them
        val switch = oruConnectedPairDp(firstLabelDp = 70f, secondLabelDp = 75f) + 8f
        assertFalse(
            oruIslandShowLabels(
                availableDp = 412f,
                appsLabelDp = 44f,
                settingsLabelDp = 78f,
                switchDp = switch,
            ),
        )
    }

    private companion object {
        const val CollapseThresholdDp = 24f
    }
}
