/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruPercentLogicTest {
    @Test
    fun `progress reads as its whole percent`() {
        assertEquals(0, oruPercent(0f))
        assertEquals(9, oruPercent(0.09f))
        assertEquals(10, oruPercent(0.1f))
        assertEquals(99, oruPercent(0.99f))
        assertEquals(100, oruPercent(1f))
    }

    @Test
    fun `every whole percent from zero to one hundred reads back as itself`() {
        for (value in 0..100) {
            assertEquals(value, oruPercent(value / 100f), "value $value")
        }
    }

    @Test
    fun `progress out of range is clamped and NaN reads as zero`() {
        assertEquals(0, oruPercent(-0.3f))
        assertEquals(100, oruPercent(1.7f))
        assertEquals(0, oruPercent(Float.NaN))
    }

    @Test
    fun `a held progress keeps the larger of the held and the reported`() {
        assertEquals(0.9f, oruHeldProgress(held = 0.9f, progress = 0.1f))
        assertEquals(0.95f, oruHeldProgress(held = 0.9f, progress = 0.95f))
        assertEquals(0.4f, oruHeldProgress(held = 0f, progress = 0.4f))
    }

    @Test
    fun `a NaN report keeps what was held`() {
        assertEquals(0.6f, oruHeldProgress(held = 0.6f, progress = Float.NaN))
    }

    @Test
    fun `a held progress is clamped to the range`() {
        assertEquals(1f, oruHeldProgress(held = 0f, progress = 3f))
        assertEquals(0f, oruHeldProgress(held = 0f, progress = -1f))
    }

    @Test
    fun `the shown percent of a run never goes back`() {
        // A retry reports progress from zero again; the run keeps the figure it had reached
        val reports = listOf(0f, 0.1f, 0.5f, 0.9f, 0.1f, 0.3f, 0.5f, 0.9f, 1f)
        val hold = OruProgressHold()
        var highest = 0
        for (report in reports) {
            val shown = oruPercent(hold.show(report))
            assertTrue(shown >= highest, "shown $shown after $highest at $report")
            highest = shown
        }
        assertEquals(100, highest)
    }

    @Test
    fun `the shown percent is the running maximum of the reported percents`() {
        val reports = listOf(0.2f, 0.15f, 0.6f, 0.59f, 0.61f, 0.4f, 0.99f, 0.97f, 1f)
        val hold = OruProgressHold()
        var runningMax = 0
        for (report in reports) {
            runningMax = maxOf(runningMax, oruPercent(report))
            assertEquals(runningMax, oruPercent(hold.show(report)), "after $report")
        }
    }

    @Test
    fun `a new run starts its percent from zero`() {
        val first = OruProgressHold()
        first.show(0.9f)

        val second = OruProgressHold()
        assertEquals(10, oruPercent(second.show(0.1f)))
    }
}
