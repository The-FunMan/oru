/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals

class OruSegmentCornerTest {
    @Test
    fun `a selected option is round on every corner`() {
        assertEquals(20f, oruSegmentCornerDp(selected = true, outerCorner = true))
        assertEquals(20f, oruSegmentCornerDp(selected = true, outerCorner = false))
    }

    @Test
    fun `an unselected option keeps its outer corner round`() {
        assertEquals(20f, oruSegmentCornerDp(selected = false, outerCorner = true))
    }

    @Test
    fun `an unselected option squares its inner corner`() {
        assertEquals(8f, oruSegmentCornerDp(selected = false, outerCorner = false))
    }
}
