/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruLensTest {
    private val size = Size(400f, 200f)
    private val rounded = Outline.Rounded(RoundRect(Rect(0f, 0f, 400f, 200f), CornerRadius(28f)))
    private val density = Density(2f)
    private val clear = oruGlassParams(OruGlassStyle.Clear, isDark = false, role = OruGlassRole.Bar)
    private val frosted = oruGlassParams(OruGlassStyle.Frosted, isDark = false, role = OruGlassRole.Bar)

    @Test
    fun `there is no lens below API 33`() {
        assertNull(oruLensSpec(32, size, rounded, clear, density))
        assertNull(oruLensSpec(31, size, rounded, clear, density))
        assertNotNull(oruLensSpec(OruLensMinSdk, size, rounded, clear, density))
    }

    @Test
    fun `there is no lens for an empty size, a rectangle or numbers of no band or no pull`() {
        assertNull(oruLensSpec(34, Size.Zero, rounded, clear, density))
        assertNull(oruLensSpec(34, size, Outline.Rectangle(Rect(0f, 0f, 400f, 200f)), clear, density))
        assertNull(oruLensSpec(34, size, rounded, clear.copy(lensBandDp = 0f), density))
        assertNull(oruLensSpec(34, size, rounded, clear.copy(lensBendDp = 0f), density))
    }

    @Test
    fun `the spec is in pixels, with the corner radii of the top and the bottom`() {
        val spec = assertNotNull(oruLensSpec(34, size, rounded, clear, density))
        assertEquals(400f, spec.widthPx)
        assertEquals(200f, spec.heightPx)
        assertEquals(28f, spec.topRadiusPx)
        assertEquals(28f, spec.bottomRadiusPx)
        assertEquals(clear.lensBandDp * 2f, spec.bandPx, 0.001f)
        assertEquals(clear.lensBendDp * 2f, spec.bendPx, 0.001f)
        assertEquals(clear.lensChroma, spec.chroma)
    }

    @Test
    fun `clear bends the light more than frosted, in a wider band with more colour`() {
        assertTrue(clear.lensBandDp > frosted.lensBandDp)
        assertTrue(clear.lensBendDp > frosted.lensBendDp)
        assertTrue(clear.lensChroma > frosted.lensChroma)
    }

    @Test
    fun `the pull is smaller than the band, so the light is bent and never folded`() {
        for (style in OruGlassStyle.entries) for (role in OruGlassRole.entries) {
            val params = oruGlassParams(style, isDark = false, role = role)
            assertTrue(params.lensBendDp <= params.lensBandDp, "$style $role")
            assertTrue(params.lensChroma in 0f..0.2f, "$style $role chroma ${params.lensChroma}")
        }
    }

    @Test
    fun `a sheet has no lens, since its sides and bottom are the edges of the screen`() {
        for (style in OruGlassStyle.entries) for (isDark in listOf(false, true)) {
            val sheet = oruGlassParams(style, isDark, OruGlassRole.Sheet)
            assertNull(oruLensSpec(34, size, rounded, sheet, density), "$style dark=$isDark")
        }
    }
}
