/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.morphe.manager.ui.oru.theme.OruDarkMaterialRoles
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruLightMaterialRoles
import app.morphe.manager.ui.oru.theme.OruMaterialRoles
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OruGlassParamsTest {
    private val themes = listOf(
        Theme("light", OruLightMaterialRoles, isDark = false),
        Theme("dark", OruDarkMaterialRoles, isDark = true),
    )
    private val roles = OruGlassRole.entries

    @Test
    fun `frosted is the owner's 28 dp blur under a 78 and 80 percent tint`() {
        val light = oruGlassParams(OruGlassStyle.Frosted, isDark = false, role = OruGlassRole.Bar)
        val dark = oruGlassParams(OruGlassStyle.Frosted, isDark = true, role = OruGlassRole.Bar)
        assertEquals(28f, light.blurDp)
        assertEquals(0.78f, light.tintAlpha)
        assertEquals(0.80f, dark.tintAlpha)
        assertEquals(0.12f, light.highlight)
    }

    @Test
    fun `clear is the 14 dp blur under a 30 and 34 percent tint, with the brighter, wider rim`() {
        val light = oruGlassParams(OruGlassStyle.Clear, isDark = false, role = OruGlassRole.Bar)
        val dark = oruGlassParams(OruGlassStyle.Clear, isDark = true, role = OruGlassRole.Bar)
        assertEquals(14f, light.blurDp)
        assertEquals(0.30f, light.tintAlpha)
        assertEquals(0.34f, dark.tintAlpha)
        assertEquals(1.5f, light.rimWidthDp)
        assertEquals(0.55f, light.rimTop)
        assertEquals(0.08f, light.rimBottom)
        assertTrue(light.highlight > 0f, "clear draws an inner highlight at the top")
    }

    @Test
    fun `the dock, the bar and the pills share one set of values`() {
        for (style in OruGlassStyle.entries) {
            for (isDark in listOf(false, true)) {
                val bar = oruGlassParams(style, isDark, OruGlassRole.Bar)
                assertEquals(bar, oruGlassParams(style, isDark, OruGlassRole.Dock), "dock, $style, dark=$isDark")
                assertEquals(bar, oruGlassParams(style, isDark, OruGlassRole.Pill), "pill, $style, dark=$isDark")
            }
        }
    }

    @Test
    fun `a sheet keeps a tint floor of 86 percent when frosted and 62 percent when clear, in both themes`() {
        for (theme in themes) {
            val frosted = oruGlassParams(OruGlassStyle.Frosted, theme.isDark, OruGlassRole.Sheet)
            val clear = oruGlassParams(OruGlassStyle.Clear, theme.isDark, OruGlassRole.Sheet)
            assertTrue(frosted.tintAlpha >= 0.86f, "${theme.name} frosted sheet tint ${frosted.tintAlpha}")
            assertTrue(clear.tintAlpha >= 0.62f, "${theme.name} clear sheet tint ${clear.tintAlpha}")
        }
    }

    @Test
    fun `frosted is stronger than clear for every role and theme`() {
        for (theme in themes) for (role in roles) {
            val frosted = oruGlassParams(OruGlassStyle.Frosted, theme.isDark, role)
            val clear = oruGlassParams(OruGlassStyle.Clear, theme.isDark, role)
            val where = "${theme.name} $role"
            assertTrue(frosted.blurDp > clear.blurDp, "blur, $where")
            assertTrue(frosted.tintAlpha > clear.tintAlpha, "tint, $where")
        }
    }

    @Test
    fun `dark glass is tinted at least as strongly as light glass in each style`() {
        for (style in OruGlassStyle.entries) for (role in roles) {
            val light = oruGlassParams(style, isDark = false, role = role)
            val dark = oruGlassParams(style, isDark = true, role = role)
            assertTrue(dark.tintAlpha >= light.tintAlpha, "$style $role")
        }
    }

    @Test
    fun `onSurface keeps 4 and a half to 1 on frosted glass and on every sheet, over black or white, in both themes`() {
        // Blending is linear in the backdrop, so black and white bound every blurred backdrop the glass can show.
        // Clear bars and docks carry short labels and are not held to this bound: their tint is the owner's 30 percent.
        for (theme in themes) {
            val checked = roles.map { role -> OruGlassStyle.Frosted to role } +
                (OruGlassStyle.Clear to OruGlassRole.Sheet)
            for ((style, role) in checked) {
                val alpha = oruGlassParams(style, theme.isDark, role).tintAlpha
                for (backdrop in listOf(Color.Black, Color.White)) {
                    val behind = over(theme.roles.surface, backdrop, alpha)
                    val ratio = contrast(theme.roles.onSurface, behind)
                    assertTrue(
                        ratio >= TEXT_RATIO,
                        "${theme.name} $style $role over $backdrop: ${"%.2f".format(ratio)}:1",
                    )
                }
            }
        }
    }

    @Test
    fun `a frosted card is tinted more and blurred less than a frosted bar, and a clear card blurs 12 dp`() {
        for (isDark in listOf(false, true)) {
            val bar = oruGlassParams(OruGlassStyle.Frosted, isDark, OruGlassRole.Bar)
            val frosted = oruGlassParams(OruGlassStyle.Frosted, isDark, OruGlassRole.Card)
            val clear = oruGlassParams(OruGlassStyle.Clear, isDark, OruGlassRole.Card)
            assertTrue(frosted.tintAlpha > bar.tintAlpha, "frosted card tint, dark=$isDark")
            assertTrue(frosted.blurDp < bar.blurDp, "frosted card blur, dark=$isDark")
            assertEquals(24f, frosted.blurDp)
            assertEquals(12f, clear.blurDp)
        }
    }

    @Test
    fun `a clear card is lightly tinted with a brighter rim and highlight than a frosted one`() {
        for (isDark in listOf(false, true)) {
            val frosted = oruGlassParams(OruGlassStyle.Frosted, isDark, OruGlassRole.Card)
            val clear = oruGlassParams(OruGlassStyle.Clear, isDark, OruGlassRole.Card)
            assertTrue(clear.tintAlpha < frosted.tintAlpha - 0.2f, "tint gap, dark=$isDark")
            assertTrue(clear.rimTop > frosted.rimTop, "rim, dark=$isDark")
            assertTrue(clear.highlight > frosted.highlight, "highlight, dark=$isDark")
            assertTrue(clear.rimWidthDp > frosted.rimWidthDp, "rim width, dark=$isDark")
        }
    }

    @Test
    fun `onSurface and onSurfaceVariant keep 4 and a half to 1 on a card over the strongest orb, for every accent`() {
        // The card is the app's container laid at the tint over the backdrop. The backdrop is the surface with one
        // orb at its peak; black and white bound every orb colour, because blending is linear
        for (theme in themes) {
            val share = if (theme.isDark) 0.18f else 0.12f
            for (style in OruGlassStyle.entries) {
                val alpha = oruGlassParams(style, theme.isDark, OruGlassRole.Card).tintAlpha
                for (accent in accents) {
                    val container = Color(
                        oruMix(
                            over = accent,
                            base = theme.roles.surfaceContainer.toArgb(),
                            share = share,
                        ),
                    )
                    for (orb in listOf(Color.Black, Color.White)) {
                        val backdrop = over(orb, theme.roles.surface, oruOrbPeakAlpha(theme.isDark))
                        val card = over(container, backdrop, alpha)
                        val where = "${theme.name} $style accent ${accent.toUInt().toString(16)} orb $orb"
                        val primary = contrast(theme.roles.onSurface, card)
                        val secondary = contrast(theme.roles.onSurfaceVariant, card)
                        assertTrue(primary >= TEXT_RATIO, "onSurface, $where: ${"%.2f".format(primary)}:1")
                        assertTrue(secondary >= TEXT_RATIO, "onSurfaceVariant, $where: ${"%.2f".format(secondary)}:1")
                    }
                }
            }
        }
    }

    @Test
    fun `lerp returns the ends at 0 and 1 and the middle halfway`() {
        val from = oruGlassParams(OruGlassStyle.Frosted, isDark = false, role = OruGlassRole.Card)
        val to = oruGlassParams(OruGlassStyle.Clear, isDark = false, role = OruGlassRole.Card)
        assertEquals(from, oruGlassLerp(from, to, 0f))
        assertEquals(to, oruGlassLerp(from, to, 1f))
        val middle = oruGlassLerp(from, to, 0.5f)
        assertEquals((from.blurDp + to.blurDp) / 2f, middle.blurDp, 0.0001f)
        assertEquals((from.tintAlpha + to.tintAlpha) / 2f, middle.tintAlpha, 0.0001f)
        assertEquals((from.rimTop + to.rimTop) / 2f, middle.rimTop, 0.0001f)
        assertEquals((from.rimWidthDp + to.rimWidthDp) / 2f, middle.rimWidthDp, 0.0001f)
    }

    @Test
    fun `lerp clamps a fraction outside 0 to 1, so an overshoot never leaves the two styles`() {
        val from = oruGlassParams(OruGlassStyle.Frosted, isDark = true, role = OruGlassRole.Bar)
        val to = oruGlassParams(OruGlassStyle.Clear, isDark = true, role = OruGlassRole.Bar)
        assertEquals(from, oruGlassLerp(from, to, -0.2f))
        assertEquals(to, oruGlassLerp(from, to, 1.2f))
    }

    @Test
    fun `lerp moves every number monotonically between frosted and clear`() {
        val frosted = oruGlassParams(OruGlassStyle.Frosted, isDark = false, role = OruGlassRole.Pill)
        val clear = oruGlassParams(OruGlassStyle.Clear, isDark = false, role = OruGlassRole.Pill)
        var previous = frosted
        for (step in 1..10) {
            val now = oruGlassLerp(frosted, clear, step / 10f)
            assertTrue(now.blurDp <= previous.blurDp, "blur falls, step $step")
            assertTrue(now.tintAlpha <= previous.tintAlpha, "tint falls, step $step")
            assertTrue(now.rimTop >= previous.rimTop, "rim rises, step $step")
            previous = now
        }
    }

    @Test
    fun `clear is lit and shaded more than frosted, and a dark glass has the deeper shade`() {
        for (role in roles) {
            for (isDark in listOf(false, true)) {
                val frosted = oruGlassParams(OruGlassStyle.Frosted, isDark, role)
                val clear = oruGlassParams(OruGlassStyle.Clear, isDark, role)
                assertTrue(clear.topLine > frosted.topLine, "top line, $role dark=$isDark")
                assertTrue(clear.bottomShade > frosted.bottomShade, "shade, $role dark=$isDark")
            }
            for (style in OruGlassStyle.entries) {
                val light = oruGlassParams(style, isDark = false, role = role)
                val dark = oruGlassParams(style, isDark = true, role = role)
                assertTrue(dark.bottomShade > light.bottomShade, "dark shade, $style $role")
            }
        }
    }

    @Test
    fun `a glass hands its numbers to the edge light unchanged`() {
        val params = oruGlassParams(OruGlassStyle.Clear, isDark = false, role = OruGlassRole.Card)
        val edge = params.toEdge()
        assertEquals(params.rimTop, edge.rimNear)
        assertEquals(params.rimBottom, edge.rimFar)
        assertEquals(params.rimWidthDp, edge.rimWidthDp)
        assertEquals(params.topLine, edge.topLine)
        assertEquals(params.bottomShade, edge.bottomShade)
        assertEquals(params.highlight, edge.sheen)
        assertTrue(edge.sheenShare < SlabSheenShare, "a glass keeps its lit top third, a slab fades over the half")
    }

    @Test
    fun `lerp moves the depth numbers between frosted and clear too`() {
        val from = oruGlassParams(OruGlassStyle.Frosted, isDark = false, role = OruGlassRole.Bar)
        val to = oruGlassParams(OruGlassStyle.Clear, isDark = false, role = OruGlassRole.Bar)
        val middle = oruGlassLerp(from, to, 0.5f)
        assertEquals((from.topLine + to.topLine) / 2f, middle.topLine, 0.0001f)
        assertEquals((from.bottomShade + to.bottomShade) / 2f, middle.bottomShade, 0.0001f)
        assertEquals((from.lensBandDp + to.lensBandDp) / 2f, middle.lensBandDp, 0.0001f)
        assertEquals((from.lensBendDp + to.lensBendDp) / 2f, middle.lensBendDp, 0.0001f)
        assertEquals((from.lensChroma + to.lensChroma) / 2f, middle.lensChroma, 0.0001f)
    }

    @Test
    fun `the contrast helpers match the WCAG reference values`() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.01)
    }

    /** The tint drawn over a backdrop colour at [alpha], in sRGB, as the compositor blends it. */
    private fun over(tint: Color, backdrop: Color, alpha: Float): Color {
        fun mix(t: Float, b: Float) = t * alpha + b * (1f - alpha)
        return Color(
            red = mix(tint.red, backdrop.red),
            green = mix(tint.green, backdrop.green),
            blue = mix(tint.blue, backdrop.blue),
        )
    }

    /** WCAG 2.x contrast ratio, from the sRGB relative luminance in the spec. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private val accents = listOf(
        0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFFFFFF,
        0xFF000000, 0xFFFF00FF, 0xFF00FFFF, 0xFFB8C3FF, 0xFF3D4FCB,
    ).map { it.toInt() }

    private class Theme(val name: String, val roles: OruMaterialRoles, val isDark: Boolean)

    private companion object {
        const val TEXT_RATIO = 4.5
    }
}
