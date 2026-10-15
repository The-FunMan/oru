/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals

class OruFitTextTest {

    @Test
    fun `an overflowing text steps down by one step`() {
        assertEquals(16f - OruFitStepSp, oruFitTextSize(16f, 10f, overflow = true))
    }

    @Test
    fun `a text that fits keeps its size`() {
        assertEquals(16f, oruFitTextSize(16f, 10f, overflow = false))
    }

    @Test
    fun `a text never shrinks below the minimum`() {
        assertEquals(10f, oruFitTextSize(10.2f, 10f, overflow = true))
        assertEquals(10f, oruFitTextSize(10f, 10f, overflow = true))
    }

    @Test
    fun `repeated steps reach the minimum and stop there`() {
        var size = 16f
        repeat(50) { size = oruFitTextSize(size, 10f, overflow = true) }
        assertEquals(10f, size)
    }
}
