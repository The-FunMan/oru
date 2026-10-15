/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** How the floating glass layers look. The owner picks it with a quick toggle on Home. */
enum class OruGlassStyle {
    /** Heavy blur and a strong tint: reads like frosted glass and keeps any text on it legible. */
    Frosted,

    /** Light blur, a light tint and a bright rim: reads like clear glass. */
    Clear,
}

/** The theme choice behind the quick toggle. System follows the phone. */
enum class OruThemeMode { System, Light, Dark }

/** Provided by OruTheme from the stored choice; surfaces read it to pick their tint and blur. */
val LocalOruGlassStyle = staticCompositionLocalOf { OruGlassStyle.Frosted }
