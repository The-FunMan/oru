/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import kotlin.test.Test
import kotlin.test.assertEquals

class OruDoneChangesTest {

    @Test
    fun `the expanded list takes at most forty percent of the screen height`() {
        assertEquals(366f, oruChangesListMaxHeightDp(screenHeightDp = 915f, spaceLeftDp = 10_000f), 0.01f)
    }

    @Test
    fun `the space left caps the list when it is smaller than the share of the screen`() {
        assertEquals(120f, oruChangesListMaxHeightDp(screenHeightDp = 915f, spaceLeftDp = 120f), 0.01f)
    }

    @Test
    fun `an infinite space left, as inside a scrolling parent, leaves the screen share`() {
        assertEquals(
            366f,
            oruChangesListMaxHeightDp(screenHeightDp = 915f, spaceLeftDp = Float.POSITIVE_INFINITY),
            0.01f,
        )
    }

    @Test
    fun `the list height is never negative`() {
        assertEquals(0f, oruChangesListMaxHeightDp(screenHeightDp = 915f, spaceLeftDp = -40f), 0f)
        assertEquals(0f, oruChangesListMaxHeightDp(screenHeightDp = 0f, spaceLeftDp = 200f), 0f)
    }

    @Test
    fun `collapsed shows the first three names and expanded shows them all`() {
        val names = listOf("A", "B", "C", "D", "E")

        assertEquals(listOf("A", "B", "C"), oruVisibleChangeRows(names, expanded = false))
        assertEquals(names, oruVisibleChangeRows(names, expanded = true))
    }

    @Test
    fun `a short list is shown whole even when collapsed`() {
        assertEquals(listOf("A", "B"), oruVisibleChangeRows(listOf("A", "B"), expanded = false))
    }
}
