/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.graphics.Color
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruLightRestAngle
import app.morphe.manager.ui.oru.theme.oruLightAngle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OruDepthTest {
    private val levels = listOf(OruElevation.Flat, OruElevation.Raised, OruElevation.Floating)

    private fun List<OruShadowLayer>.total() = sumOf { it.alpha.toDouble() }
    private fun List<OruShadowLayer>.reach() = maxOfOrNull { it.offsetYDp + it.blurDp } ?: 0f

    @Test
    fun `flat draws no shadow in any theme, compact or not`() {
        for (isDark in listOf(false, true)) for (compact in listOf(false, true)) {
            assertTrue(oruShadowLayers(OruElevation.Flat, isDark, compact).isEmpty(), "dark=$isDark compact=$compact")
        }
    }

    @Test
    fun `the shadow grows with the elevation in layers, in strength and in reach`() {
        for (isDark in listOf(false, true)) {
            val raised = oruShadowLayers(OruElevation.Raised, isDark)
            val floating = oruShadowLayers(OruElevation.Floating, isDark)
            assertTrue(raised.isNotEmpty(), "raised has a shadow, dark=$isDark")
            assertTrue(floating.size > raised.size, "layers, dark=$isDark")
            assertTrue(floating.total() > raised.total(), "strength, dark=$isDark")
            assertTrue(floating.reach() > raised.reach(), "reach, dark=$isDark")
        }
    }

    @Test
    fun `the layers of one shadow go from a tight contact shadow to a wide faint one`() {
        for (level in levels.drop(1)) {
            val layers = oruShadowLayers(level, isDark = false)
            for ((near, far) in layers.zipWithNext()) {
                assertTrue(far.offsetYDp > near.offsetYDp, "$level offset")
                assertTrue(far.blurDp > near.blurDp, "$level blur")
            }
        }
    }

    @Test
    fun `a compact surface keeps only the contact layer`() {
        for (level in levels.drop(1)) {
            val full = oruShadowLayers(level, isDark = false)
            val compact = oruShadowLayers(level, isDark = false, compact = true)
            assertEquals(listOf(full.first()), compact, "$level")
        }
    }

    @Test
    fun `dark shadows are stronger than light ones and no layer passes the cap`() {
        for (level in levels) {
            val light = oruShadowLayers(level, isDark = false)
            val dark = oruShadowLayers(level, isDark = true)
            assertEquals(light.size, dark.size, "$level")
            light.zip(dark).forEach { (l, d) ->
                assertTrue(d.alpha > l.alpha, "$level dark alpha ${d.alpha} over light ${l.alpha}")
                assertTrue(d.alpha <= MaxShadowAlpha && l.alpha <= MaxShadowAlpha, "$level cap")
                assertEquals(l.offsetYDp, d.offsetYDp, "$level offset is the same in both themes")
            }
        }
    }

    @Test
    fun `at rest a shadow falls down and a little to the right`() {
        val shift = oruShadowShift(offsetYDp = 10f, angle = OruLightRestAngle)
        assertEquals(10f, shift.dy, 0.001f)
        assertTrue(shift.dx > 0f && shift.dx < shift.dy, "dx ${shift.dx}")
    }

    @Test
    fun `a pressed surface casts a shorter shadow, and a scale of zero casts none beside it`() {
        val full = oruShadowShift(10f, OruLightRestAngle, scale = 1f)
        val half = oruShadowShift(10f, OruLightRestAngle, scale = 0.5f)
        val none = oruShadowShift(10f, OruLightRestAngle, scale = 0f)
        assertEquals(full.dy / 2f, half.dy, 0.001f)
        assertEquals(full.dx / 2f, half.dx, 0.001f)
        assertEquals(0f, none.dy, 0.0001f)
        assertEquals(0f, none.dx, 0.0001f)
    }

    @Test
    fun `tilting the phone moves the shadow away from the lamp`() {
        val left = oruShadowShift(10f, oruLightAngle(-1f))
        val rest = oruShadowShift(10f, oruLightAngle(0f))
        val right = oruShadowShift(10f, oruLightAngle(1f))
        assertTrue(left.dx > rest.dx && rest.dx > right.dx, "dx ${left.dx} ${rest.dx} ${right.dx}")
        assertTrue(left.dy > 0f && right.dy > 0f, "the shadow still falls down")
    }

    @Test
    fun `the shadow ink is a cool near black that leans toward the tint`() {
        val plain = oruShadowInk(Color.Unspecified)
        val tinted = oruShadowInk(Color.Red)
        assertEquals(1f, plain.alpha)
        assertEquals(1f, tinted.alpha)
        assertNotEquals(plain, tinted)
        assertTrue(tinted.red > plain.red, "a red tint raises red")
        assertTrue(tinted.red < 0.5f, "still dark")
    }

    @Test
    fun `flat has no edge light`() {
        for (style in OruGlassStyle.entries) for (isDark in listOf(false, true)) {
            val edge = oruEdgeParams(OruElevation.Flat, style, isDark)
            assertTrue(edge.isNone, "$style dark=$isDark")
            assertEquals(OruEdgeParams.None, edge)
        }
    }

    @Test
    fun `raised and floating have an edge light, and floating is the stronger`() {
        for (style in OruGlassStyle.entries) for (isDark in listOf(false, true)) {
            val raised = oruEdgeParams(OruElevation.Raised, style, isDark)
            val floating = oruEdgeParams(OruElevation.Floating, style, isDark)
            val where = "$style dark=$isDark"
            assertTrue(!raised.isNone, "raised, $where")
            assertTrue(floating.rimNear >= raised.rimNear, "rim, $where")
            assertTrue(floating.topLine >= raised.topLine, "top line, $where")
            assertTrue(floating.rimWidthDp > raised.rimWidthDp, "width, $where")
        }
    }

    @Test
    fun `clear is lit more strongly and wider than frosted at every elevation and theme`() {
        for (level in levels.drop(1)) for (isDark in listOf(false, true)) {
            val frosted = oruEdgeParams(level, OruGlassStyle.Frosted, isDark)
            val clear = oruEdgeParams(level, OruGlassStyle.Clear, isDark)
            val where = "$level dark=$isDark"
            assertTrue(clear.rimNear > frosted.rimNear, "rim near, $where")
            assertTrue(clear.rimWidthDp > frosted.rimWidthDp, "width, $where")
            assertTrue(clear.sheen >= frosted.sheen, "sheen, $where")
            assertTrue(clear.topLine >= frosted.topLine, "top line, $where")
            assertTrue(clear.rimFar >= frosted.rimFar, "rim far, $where")
            assertTrue(clear.bottomShade >= frosted.bottomShade, "shade, $where")
        }
    }

    @Test
    fun `the rim is brightest toward the lamp, and every alpha is in range`() {
        for (style in OruGlassStyle.entries) for (level in levels.drop(1)) for (isDark in listOf(false, true)) {
            val edge = oruEdgeParams(level, style, isDark)
            val where = "$style $level dark=$isDark"
            assertTrue(edge.rimNear > edge.rimFar, "near over far, $where")
            for (alpha in listOf(edge.rimNear, edge.rimFar, edge.topLine, edge.bottomShade, edge.sheen)) {
                assertTrue(alpha in 0f..MaxEdgeAlpha, "alpha $alpha, $where")
            }
        }
    }

    @Test
    fun `a light slab leans on a bright rim and a dark slab on a deeper shade`() {
        val light = oruEdgeParams(OruElevation.Raised, OruGlassStyle.Frosted, isDark = false)
        val dark = oruEdgeParams(OruElevation.Raised, OruGlassStyle.Frosted, isDark = true)
        assertTrue(light.rimNear > dark.rimNear, "a white rim shows more on a pale slab")
        assertTrue(dark.bottomShade > light.bottomShade, "the shade has to be deeper on a dark slab")
        assertTrue(light.sheen > dark.sheen, "a pale slab takes more light")
    }

    @Test
    fun `a card and a pill stand on the page, and the dock, the bar and the sheet float`() {
        assertEquals(OruElevation.Raised, oruGlassElevation(OruGlassRole.Card))
        assertEquals(OruElevation.Raised, oruGlassElevation(OruGlassRole.Pill))
        assertEquals(OruElevation.Floating, oruGlassElevation(OruGlassRole.Dock))
        assertEquals(OruElevation.Floating, oruGlassElevation(OruGlassRole.Bar))
        assertEquals(OruElevation.Floating, oruGlassElevation(OruGlassRole.Sheet))
    }
}
