/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class OruFrostPolicyTest {
    private fun decide(
        sdkInt: Int = 34,
        powerSaveMode: Boolean = false,
        lowRamDevice: Boolean = false,
        animatorDurationScale: Float = 1f,
    ) = OruFrostPolicy.decide(sdkInt, powerSaveMode, lowRamDevice, animatorDurationScale)

    @Test
    fun `below API 31 the fill is solid`() {
        assertEquals(OruSurfaceFill.Solid, decide(sdkInt = 30))
        assertEquals(OruSurfaceFill.Solid, decide(sdkInt = 26))
    }

    @Test
    fun `API 31 and above is frosted when nothing reduces it`() {
        assertEquals(OruSurfaceFill.Frosted, decide(sdkInt = 31))
        assertEquals(OruSurfaceFill.Frosted, decide(sdkInt = 36))
    }

    @Test
    fun `battery saver makes the fill solid`() {
        assertEquals(OruSurfaceFill.Solid, decide(powerSaveMode = true))
    }

    @Test
    fun `low RAM makes the fill solid`() {
        assertEquals(OruSurfaceFill.Solid, decide(lowRamDevice = true))
    }

    @Test
    fun `animator scale zero makes the fill solid`() {
        assertEquals(OruSurfaceFill.Solid, decide(animatorDurationScale = 0f))
    }

    @Test
    fun `a positive animator scale keeps the frost`() {
        assertEquals(OruSurfaceFill.Frosted, decide(animatorDurationScale = 0.5f))
        assertEquals(OruSurfaceFill.Frosted, decide(animatorDurationScale = 2f))
    }

    @Test
    fun `any one reduction is enough, and the combination is still solid`() {
        assertEquals(OruSurfaceFill.Solid, decide(sdkInt = 30, powerSaveMode = true, lowRamDevice = true))
        assertEquals(OruSurfaceFill.Solid, decide(powerSaveMode = true, animatorDurationScale = 0f))
    }
}
