/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Material roles of the Oru palette, taken from the prototype's CSS variables (`--primary`,
 * `--surface-high` and so on). Fixed values, so every screen draws the same colors on every device.
 *
 * Roles the prototype does not use (secondary, tertiary, inverse, bright and dim surfaces) follow the
 * same ultramarine-neutral family, so no stray baseline purple reaches a component.
 */
internal data class OruMaterialRoles(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val scrim: Color,
) {
    /**
     * Builds the Material scheme from these roles. `background` mirrors `surface` and the prototype
     * draws the page on `--surface`, so the two are one role here.
     */
    fun toColorScheme(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            surfaceTint = primary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainerHighest,
            onSurfaceVariant = onSurfaceVariant,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            scrim = scrim,
        )
    }
}

internal val OruLightMaterialRoles = OruMaterialRoles(
    isDark = false,
    primary = Color(0xFF3D4FCB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    inversePrimary = Color(0xFFB8C3FF),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF5C5D72),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E1F9),
    onTertiaryContainer = Color(0xFF181A2C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2FB),
    surfaceContainer = Color(0xFFEEEDF6),
    surfaceContainerHigh = Color(0xFFE8E7F0),
    surfaceContainerHighest = Color(0xFFE2E1EA),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2F0F7),
    scrim = Color(0x66000000),
)

internal val OruDarkMaterialRoles = OruMaterialRoles(
    isDark = true,
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF13248F),
    primaryContainer = Color(0xFF2A3BB0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    inversePrimary = Color(0xFF3D4FCB),
    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFC5C4DD),
    onTertiary = Color(0xFF2E2F42),
    tertiaryContainer = Color(0xFF444559),
    onTertiaryContainer = Color(0xFFE0E1F9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF121318),
    surfaceDim = Color(0xFF121318),
    surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    onSurface = Color(0xFFE4E1E9),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    scrim = Color(0x99000000),
)
