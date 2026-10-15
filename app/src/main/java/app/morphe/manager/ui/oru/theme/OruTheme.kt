/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.morphe.manager.ui.theme.LocalAppCardColorResolver
import app.morphe.manager.ui.theme.LocalThemeTraits
import app.morphe.manager.ui.theme.ThemeTraits

/**
 * Legacy components read [LocalThemeTraits]. Oru has no per-app colours and no monochrome style,
 * but keeps the hairline outlines.
 */
private val OruThemeTraits = ThemeTraits(
    monochrome = false,
    colorAccents = false,
    outlines = true,
)

/**
 * Whether the theme draws dark. Light and Dark win over the phone; System follows [systemDark].
 * Pure, so the rule is tested on the JVM.
 */
internal fun isDarkEffective(mode: OruThemeMode, systemDark: Boolean): Boolean = when (mode) {
    OruThemeMode.System -> systemDark
    OruThemeMode.Light -> false
    OruThemeMode.Dark -> true
}

/**
 * The Oru theme. [themeMode] picks light or dark, System by default, which follows the phone. The palette is
 * fixed, unless [dynamicColor] is on and the device is on Android 12 or later, which takes the
 * Material roles from the wallpaper. The status roles and the brand dot stay fixed in both cases, and so does the
 * translucent scrim behind sheets and dialogs.
 * [glassStyle] is provided as [LocalOruGlassStyle] for the glass surfaces.
 *
 * Replaces [app.morphe.manager.ui.theme.ManagerTheme] in the oru flavor only.
 */
@Composable
fun OruTheme(
    themeMode: OruThemeMode = OruThemeMode.System,
    glassStyle: OruGlassStyle = OruGlassStyle.Frosted,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // One value drives the scheme and the bar icons, so they never disagree
    val darkTheme = isDarkEffective(themeMode, isSystemInDarkTheme())
    val context = LocalContext.current

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val wallpaper = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // The wallpaper palette has no scrim of its own, and the Material default is opaque black. Every sheet and
        // dialog would then black out the whole window behind it, so the Oru scrim is kept, as in the fixed palette.
        wallpaper.copy(scrim = if (darkTheme) OruDarkMaterialRoles.scrim else OruLightMaterialRoles.scrim)
    } else {
        if (darkTheme) OruDarkMaterialRoles.toColorScheme() else OruLightMaterialRoles.toColorScheme()
    }
    val statusColors = if (darkTheme) OruDarkStatusColors else OruLightStatusColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        // The edge to edge layout and the transparent bars come from enableEdgeToEdge() in
        // MainActivity, so only the icon tint is left to follow the theme
        SideEffect {
            val activity = view.context as Activity
            val insetsController = WindowCompat.getInsetsController(activity.window, view)

            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalThemeTraits provides OruThemeTraits,
        LocalAppCardColorResolver provides null,
        LocalOruStatusColors provides statusColors,
        LocalOruGlassStyle provides glassStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OruTypography,
            shapes = OruShapes,
        ) {
            // Text and icons outside a Surface default to black, which is unreadable in dark mode
            CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
                // The lamp of the depth system: one light for every lit edge and cast shadow (OruLight.kt)
                ProvideOruLight(content = content)
            }
        }
    }
}
