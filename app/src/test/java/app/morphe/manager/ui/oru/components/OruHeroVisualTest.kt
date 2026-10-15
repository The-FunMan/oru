/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruHeroVisualTest {

    @Test
    fun `a working run always shows the hexagon, waiting or applying`() {
        assertEquals(OruHeroVisual.Hexagon, oruHeroVisual(OruHeroState.Working, 96, null))
        assertEquals(OruHeroVisual.Hexagon, oruHeroVisual(OruHeroState.Working, 0, null))
    }

    @Test
    fun `a finished patch run keeps its pieces, a finished install shows the tile`() {
        assertEquals(OruHeroVisual.Hexagon, oruHeroVisual(OruHeroState.Done, 96, null))
        assertEquals(OruHeroVisual.Tile, oruHeroVisual(OruHeroState.Done, 0, null))
    }

    @Test
    fun `a failed patch keeps its pieces, other failures show the exclamation`() {
        assertEquals(OruHeroVisual.Hexagon, oruHeroVisual(OruHeroState.Failed, 24, 3))
        assertEquals(OruHeroVisual.Tile, oruHeroVisual(OruHeroState.Failed, 24, null))
        assertEquals(OruHeroVisual.Tile, oruHeroVisual(OruHeroState.Ready, 0, null))
    }

    @Test
    fun `the hexagon prepares until patches apply, and only then fills`() {
        assertTrue(oruHeroPreparing(OruHeroState.Working, applyingPatches = false, patchTotal = 96))
        assertTrue(oruHeroPreparing(OruHeroState.Working, applyingPatches = false, patchTotal = 0))
        assertTrue(oruHeroPreparing(OruHeroState.Working, applyingPatches = true, patchTotal = 0))
        assertFalse(oruHeroPreparing(OruHeroState.Working, applyingPatches = true, patchTotal = 96))
        assertFalse(oruHeroPreparing(OruHeroState.Done, applyingPatches = false, patchTotal = 96))
        assertFalse(oruHeroPreparing(OruHeroState.Failed, applyingPatches = false, patchTotal = 96))
    }
}
