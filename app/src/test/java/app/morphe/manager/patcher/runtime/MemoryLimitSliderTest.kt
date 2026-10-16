/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryLimitSliderTest {

    @Test
    fun `slider steps match the stops the stock dialog always used`() {
        // The stock dialog computed these with doubles. The integer form must give the same stops
        for (maximum in listOf(1024, 1280, 1536, 2048)) {
            val legacy = ((maximum.toDouble() - PROCESS_RUNTIME_MEMORY_MINIMUM) / PROCESS_RUNTIME_MEMORY_STEP - 1).toInt()
            assertEquals(legacy, memoryLimitSliderSteps(maximum), "maximum $maximum")
        }
    }

    @Test
    fun `slider steps leave one stop per step between the ends`() {
        assertEquals(3, memoryLimitSliderSteps(1024))
        assertEquals(5, memoryLimitSliderSteps(1280))
        assertEquals(11, memoryLimitSliderSteps(2048))
    }

    @Test
    fun `snaps a position to the nearest step`() {
        assertEquals(640, snapMemoryLimit(700f, 1280))
        assertEquals(768, snapMemoryLimit(710f, 1280))
        assertEquals(896, snapMemoryLimit(896f, 1280))
    }

    @Test
    fun `keeps a snapped position between the ends of the range`() {
        assertEquals(512, snapMemoryLimit(100f, 1280))
        assertEquals(1280, snapMemoryLimit(2000f, 1280))
        assertEquals(1280, snapMemoryLimit(5000f, 1280))
    }

    @Test
    fun `every stop of the slider snaps to itself`() {
        val maximum = 1280
        val stops = (0..memoryLimitSliderSteps(maximum) + 1).map { index ->
            PROCESS_RUNTIME_MEMORY_MINIMUM + index * PROCESS_RUNTIME_MEMORY_STEP
        }
        for (stop in stops) {
            assertEquals(stop, snapMemoryLimit(stop.toFloat(), maximum))
        }
        assertEquals(maximum, stops.last())
    }
}
