/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruAppTintTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val grey = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val faintWarm = 0xFF807070.toInt()
    private val pixelCount = OruTintSamplePixels * OruTintSamplePixels

    /** Surfaces of the light and dark schemes the palettes are checked on: the container and the highest container. */
    private val lightContainer = 0xFFEEEDF6.toInt()
    private val lightHighest = 0xFFE2E1EA.toInt()
    private val darkContainer = 0xFF1F1F25.toInt()
    private val darkHighest = 0xFF34343A.toInt()

    /** Ten accents across the hues, lightness and darkness an app icon can have: the sample for the contrast checks. */
    private val accentSample = listOf(
        red,
        0xFFFF4500.toInt(), // orange
        0xFFFFFF00.toInt(), // yellow
        0xFFFFF0A0.toInt(), // very light yellow
        0xFF3D4FCB.toInt(), // blue
        0xFF000040.toInt(), // very dark navy
        0xFF0A0A2A.toInt(), // darkest navy
        0xFFFFC0CB.toInt(), // pink
        0xFF1DB954.toInt(), // green
        0xFF00FF00.toInt(), // lime
    )

    @Test
    fun `a red icon gives red`() {
        assertEquals(red, oruDominantAccent(IntArray(pixelCount) { red }))
    }

    @Test
    fun `the larger colourful area wins a mixed icon`() {
        val pixels = IntArray(pixelCount) { if (it < 300) red else blue }
        assertEquals(red, oruDominantAccent(pixels))
    }

    @Test
    fun `a grey, white and black icon has no accent`() {
        assertNull(oruDominantAccent(IntArray(pixelCount) { grey }))
        assertNull(oruDominantAccent(IntArray(pixelCount) { if (it % 2 == 0) white else black }))
    }

    @Test
    fun `an empty icon has no accent`() {
        assertNull(oruDominantAccent(IntArray(0)))
    }

    @Test
    fun `see-through red pixels do not vote`() {
        assertNull(oruDominantAccent(IntArray(pixelCount) { 0x00FF0000 }))
    }

    @Test
    fun `a speck of colour below the share does not decide`() {
        val pixels = IntArray(pixelCount) { if (it < 5) red else grey }
        assertNull(oruDominantAccent(pixels))
    }

    @Test
    fun `near-white colour is ignored in favour of a real hue`() {
        val pixels = IntArray(pixelCount) { if (it < 200) 0xFFF8F8F8.toInt() else blue }
        assertEquals(blue, oruDominantAccent(pixels))
    }

    @Test
    fun `contrast follows the WCAG reference`() {
        assertTrue(abs(oruContrast(black, white) - 21.0) < 0.01)
        assertTrue(abs(oruContrast(white, white) - 1.0) < 0.01)
    }

    @Test
    fun `mix with share zero keeps the base`() {
        assertEquals(blue, oruMix(over = red, base = blue, share = 0f))
    }

    @Test
    fun `text on the container and the tile reaches 4 and a half to 1 for every accent and theme`() {
        val accents = listOf(
            red,
            0xFFFFFF00.toInt(),
            0xFF3D4FCB.toInt(),
            0xFF000040.toInt(),
            0xFFFFC0CB.toInt(),
            0xFF00FF00.toInt(),
            0xFFFFF0A0.toInt(),
        )
        for (accent in accents) {
            val light = oruTintPalette(accent, 0xFFEEEDF6.toInt(), 0xFFE2E1EA.toInt(), darkTheme = false)
            assertTrue(oruContrast(light.onContainer, light.container) >= OruTextMinContrast, "light container $accent")
            assertTrue(oruContrast(light.onContainer, light.tile) >= OruTextMinContrast, "light tile $accent")

            val dark = oruTintPalette(accent, 0xFF1F1F25.toInt(), 0xFF34343A.toInt(), darkTheme = true)
            assertTrue(oruContrast(dark.onContainer, dark.container) >= OruTextMinContrast, "dark container $accent")
            assertTrue(oruContrast(dark.onContainer, dark.tile) >= OruTextMinContrast, "dark tile $accent")
        }
    }

    @Test
    fun `a pale accent gets dark text in light and light text in dark`() {
        val paleYellow = 0xFFFFF0A0.toInt()
        val light = oruTintPalette(paleYellow, 0xFFEEEDF6.toInt(), 0xFFE2E1EA.toInt(), darkTheme = false)
        assertTrue(oruRelativeLuminance(light.onContainer) < 0.2)
        val dark = oruTintPalette(paleYellow, 0xFF1F1F25.toInt(), 0xFF34343A.toInt(), darkTheme = true)
        assertTrue(oruRelativeLuminance(dark.onContainer) > 0.4)
    }

    @Test
    fun `a black or grey mark has no muted accent, so it takes the Oru primary`() {
        assertNull(oruMutedAccent(IntArray(pixelCount) { black }))
        assertNull(oruMutedAccent(IntArray(pixelCount) { grey }))
        assertNull(oruMutedAccent(IntArray(pixelCount) { if (it % 2 == 0) white else black }))
    }

    @Test
    fun `a faint warm cast reads as its own hue with the saturation lifted`() {
        val accent = assertNotNull(oruMutedAccent(IntArray(pixelCount) { faintWarm }))
        val r = (accent shr 16) and 0xFF
        val g = (accent shr 8) and 0xFF
        val b = accent and 0xFF
        assertTrue(r > g && g == b, "a red hue, got $g $b against $r")
        assertTrue((r - g).toFloat() / r >= 0.34f, "saturation lifted to the minimum")
    }

    @Test
    fun `hues that cancel out, or a speck of hue, have no muted accent`() {
        val cancelling = IntArray(pixelCount) { if (it % 2 == 0) faintWarm else 0xFF708080.toInt() }
        assertNull(oruMutedAccent(cancelling))
        val speck = IntArray(pixelCount) { if (it < 5) faintWarm else grey }
        assertNull(oruMutedAccent(speck))
    }

    @Test
    fun `hsv gives the primaries at full saturation and value`() {
        assertEquals(red, oruHsvArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), oruHsvArgb(120f, 1f, 1f))
        assertEquals(blue, oruHsvArgb(240f, 1f, 1f))
        assertEquals(white, oruHsvArgb(0f, 0f, 1f))
    }

    @Test
    fun `the fill, the ink and the chip text reach 4 and a half to 1 for ten accents in both themes`() {
        for (accent in accentSample) {
            val light = oruTintPalette(accent, lightContainer, lightHighest, darkTheme = false)
            assertTrue(oruContrast(light.onFill, light.fill) >= OruTextMinContrast, "light fill $accent")
            assertTrue(oruContrast(light.ink, lightContainer) >= OruTextMinContrast, "light ink on container $accent")
            assertTrue(oruContrast(light.ink, lightHighest) >= OruTextMinContrast, "light ink on highest $accent")
            assertTrue(oruContrast(light.onChip, light.chip) >= OruTextMinContrast, "light chip $accent")

            val dark = oruTintPalette(accent, darkContainer, darkHighest, darkTheme = true)
            assertTrue(oruContrast(dark.onFill, dark.fill) >= OruTextMinContrast, "dark fill $accent")
            assertTrue(oruContrast(dark.ink, darkContainer) >= OruTextMinContrast, "dark ink on container $accent")
            assertTrue(oruContrast(dark.ink, darkHighest) >= OruTextMinContrast, "dark ink on highest $accent")
            assertTrue(oruContrast(dark.onChip, dark.chip) >= OruTextMinContrast, "dark chip $accent")
        }
    }

    @Test
    fun `a dark fill takes light text and a light fill dark text`() {
        val darkNavy = 0xFF0A0A2A.toInt()
        assertTrue(oruRelativeLuminance(oruFillText(darkNavy)) > 0.2)
        val paleYellow = 0xFFFFF0A0.toInt()
        assertTrue(oruRelativeLuminance(oruFillText(paleYellow)) < 0.2)
    }
}
