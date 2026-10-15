/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Roles Material 3 has no slot for: success and warning states, and the wordmark dot. They are fixed
 * in every theme, including when the wallpaper palette is on, so a status never changes meaning.
 */
@Immutable
data class OruStatusColors(
    val ok: Color,
    val onOk: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    /** Wordmark dot only, never used for text. */
    val brand: Color,
)

internal val OruLightStatusColors = OruStatusColors(
    ok = Color(0xFF1B6D3A),
    onOk = Color(0xFFFFFFFF),
    okContainer = Color(0xFFB5F1C3),
    onOkContainer = Color(0xFF00210C),
    warn = Color(0xFF8A5100),
    warnContainer = Color(0xFFFFDDB4),
    onWarnContainer = Color(0xFF2B1700),
    brand = Color(0xFF3D4FCB),
)

internal val OruDarkStatusColors = OruStatusColors(
    ok = Color(0xFF8AD8A0),
    onOk = Color(0xFF00391A),
    okContainer = Color(0xFF0A5226),
    onOkContainer = Color(0xFFB5F1C3),
    warn = Color(0xFFFFB95C),
    warnContainer = Color(0xFF5C3D00),
    onWarnContainer = Color(0xFFFFDDB4),
    brand = Color(0xFF8F9DFF),
)

/** The status roles in effect. [OruTheme] provides the light or dark set. */
val LocalOruStatusColors = staticCompositionLocalOf { OruLightStatusColors }
