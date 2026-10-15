/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruBackdropTest {
    @Test
    fun `solid fill never blurs, whatever the backdrop`() {
        assertEquals(OruFrostMode.Solid, oruFrostMode(OruSurfaceFill.Solid, 34, hasBackdrop = true))
        assertEquals(OruFrostMode.Solid, oruFrostMode(OruSurfaceFill.Solid, 30, hasBackdrop = false))
    }

    @Test
    fun `frosted blurs only with a recorded backdrop on API 31 and above`() {
        assertEquals(OruFrostMode.Blur, oruFrostMode(OruSurfaceFill.Frosted, 31, hasBackdrop = true))
        assertEquals(OruFrostMode.Blur, oruFrostMode(OruSurfaceFill.Frosted, 36, hasBackdrop = true))
    }

    @Test
    fun `frosted without a host or below API 31 keeps the nearly opaque fill`() {
        assertEquals(OruFrostMode.Veil, oruFrostMode(OruSurfaceFill.Frosted, 34, hasBackdrop = false))
        assertEquals(OruFrostMode.Veil, oruFrostMode(OruSurfaceFill.Frosted, 30, hasBackdrop = true))
    }

    @Test
    fun `the logged reason names the cause of every mode`() {
        assertTrue("Solid" in oruFrostReason(OruSurfaceFill.Solid, 34, hasBackdrop = true))
        assertTrue("API 30" in oruFrostReason(OruSurfaceFill.Frosted, 30, hasBackdrop = true))
        assertTrue("no OruBackdropHost" in oruFrostReason(OruSurfaceFill.Frosted, 34, hasBackdrop = false))
        assertTrue("recorded backdrop" in oruFrostReason(OruSurfaceFill.Frosted, 34, hasBackdrop = true))
    }
}
