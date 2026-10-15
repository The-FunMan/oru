/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WCAG 2.x contrast of the fixed Oru role tables, in both themes.
 *
 * Every role value is checked as it stands. None was adjusted to pass: each text pair clears 4.5:1
 * and each non-text pair clears 3:1 with the prototype's values, so no waiver is needed. The
 * tightest text pair is warn on warnContainer in light mode (about 4.99:1).
 *
 * `outlineVariant` is a decorative divider, not a boundary a user has to see to operate a control,
 * so WCAG 1.4.11 does not apply to it and it is not checked here (it is about 1.6:1 on surface).
 */
class OruContrastTest {
    private data class ColorPair(val label: String, val foreground: Color, val background: Color)

    @Test
    fun `text on surfaces reaches 4 and a half to 1 in light and dark`() {
        assertNoFailures(textPairs(OruLightMaterialRoles, OruLightStatusColors), TEXT_RATIO, "light")
        assertNoFailures(textPairs(OruDarkMaterialRoles, OruDarkStatusColors), TEXT_RATIO, "dark")
    }

    @Test
    fun `controls and status marks reach 3 to 1 in light and dark`() {
        assertNoFailures(nonTextPairs(OruLightMaterialRoles, OruLightStatusColors), NON_TEXT_RATIO, "light")
        assertNoFailures(nonTextPairs(OruDarkMaterialRoles, OruDarkStatusColors), NON_TEXT_RATIO, "dark")
    }

    @Test
    fun `the contrast formula matches the WCAG reference values`() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.01)
        // #767676 on white is the classic 4.54:1 boundary case
        assertEquals(4.54, contrast(Color(0xFF767676), Color.White), 0.01)
    }

    private fun textPairs(m: OruMaterialRoles, s: OruStatusColors): List<ColorPair> {
        val surfaces = listOf(
            "surface" to m.surface,
            "surfaceContainerLow" to m.surfaceContainerLow,
            "surfaceContainer" to m.surfaceContainer,
            "surfaceContainerHigh" to m.surfaceContainerHigh,
            "surfaceContainerHighest" to m.surfaceContainerHighest,
        )
        val onSurfaces = surfaces.flatMap { (name, color) ->
            listOf(
                ColorPair("onSurface on $name", m.onSurface, color),
                ColorPair("onSurfaceVariant on $name", m.onSurfaceVariant, color),
            )
        }
        return onSurfaces + listOf(
            ColorPair("primary on surface", m.primary, m.surface),
            ColorPair("primary on surfaceContainer", m.primary, m.surfaceContainer),
            ColorPair("onPrimary on primary", m.onPrimary, m.primary),
            ColorPair("onPrimaryContainer on primaryContainer", m.onPrimaryContainer, m.primaryContainer),
            ColorPair("onSecondaryContainer on secondaryContainer", m.onSecondaryContainer, m.secondaryContainer),
            ColorPair("error on surface", m.error, m.surface),
            ColorPair("onErrorContainer on errorContainer", m.onErrorContainer, m.errorContainer),
            ColorPair("ok on surface", s.ok, m.surface),
            ColorPair("warn on surface", s.warn, m.surface),
            ColorPair("onOk on ok", s.onOk, s.ok),
            ColorPair("onOkContainer on okContainer", s.onOkContainer, s.okContainer),
            ColorPair("warn on warnContainer", s.warn, s.warnContainer),
            ColorPair("onWarnContainer on warnContainer", s.onWarnContainer, s.warnContainer),
        )
    }

    private fun nonTextPairs(m: OruMaterialRoles, s: OruStatusColors): List<ColorPair> = listOf(
        ColorPair("outline on surface", m.outline, m.surface),
        ColorPair("outline on surfaceContainer", m.outline, m.surfaceContainer),
        ColorPair("outline on surfaceContainerHighest", m.outline, m.surfaceContainerHighest),
        ColorPair("primary on surface", m.primary, m.surface),
        ColorPair("ok on surface", s.ok, m.surface),
        ColorPair("warn on surface", s.warn, m.surface),
        ColorPair("error on surface", m.error, m.surface),
        ColorPair("brand dot on surface", s.brand, m.surface),
    )

    private fun assertNoFailures(pairs: List<ColorPair>, minimum: Double, theme: String) {
        val failures = pairs
            .map { it to contrast(it.foreground, it.background) }
            .filter { (_, ratio) -> ratio < minimum }
            .map { (pair, ratio) -> "${pair.label} ($theme): ${"%.2f".format(ratio)}:1" }
        assertTrue(failures.isEmpty(), "Below ${minimum}:1: $failures")
    }

    /** WCAG 2.x contrast ratio, from the sRGB relative luminance in the spec. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private companion object {
        const val TEXT_RATIO = 4.5
        const val NON_TEXT_RATIO = 3.0
    }
}
