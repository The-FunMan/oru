/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.morphe.manager.ui.oru.theme.LocalOruStatusColors
import app.morphe.manager.ui.oru.theme.rememberOruReducedMotion

/**
 * Preview override for reduced motion, set only by the debug gallery. Null in the app, so components
 * follow the system signal from [rememberOruReducedMotion].
 */
internal val LocalOruReducedMotionPreview = staticCompositionLocalOf<Boolean?> { null }

/** Whether components should skip their motion. */
@Composable
internal fun oruReducedMotion(): Boolean =
    LocalOruReducedMotionPreview.current ?: rememberOruReducedMotion()

/** A container colour and the colour of the content on it. */
internal data class OruRoleColors(
    val container: Color,
    val content: Color,
)

/** The container pair of a role, taken from the Material roles or the Oru status roles. */
@Composable
internal fun oruContainerColors(role: OruContainerRole): OruRoleColors {
    val scheme = MaterialTheme.colorScheme
    val status = LocalOruStatusColors.current
    return when (role) {
        OruContainerRole.Neutral -> OruRoleColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        OruContainerRole.Ok -> OruRoleColors(status.okContainer, status.onOkContainer)
        OruContainerRole.Warn -> OruRoleColors(status.warnContainer, status.onWarnContainer)
        OruContainerRole.Error -> OruRoleColors(scheme.errorContainer, scheme.onErrorContainer)
    }
}
