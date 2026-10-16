/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable

/** How a floating layer is filled: blurred content behind it, or a solid surface. */
enum class OruSurfaceFill { Frosted, Solid }

/**
 * Chooses between frosted glass and a solid fill. Frost needs `RenderEffect` (API 31+) and costs
 * GPU time, so it is turned off whenever the device is in a state where that cost matters.
 *
 * Android has no "reduce transparency" setting, so this is the closest honest mapping of the
 * prototype's `prefers-reduced-transparency` rule.
 */
object OruFrostPolicy {
    /** `RenderEffect` blur is available from API 31 (Android 12). */
    private const val FrostMinSdk = 31

    /**
     * Frosted only when every condition holds; otherwise solid. Inputs are plain values so the
     * matrix can be unit-tested without a device.
     */
    fun decide(
        sdkInt: Int,
        powerSaveMode: Boolean,
        lowRamDevice: Boolean,
        animatorDurationScale: Float,
    ): OruSurfaceFill =
        if (sdkInt >= FrostMinSdk && !powerSaveMode && !lowRamDevice && animatorDurationScale > 0f) {
            OruSurfaceFill.Frosted
        } else {
            OruSurfaceFill.Solid
        }

    fun resolve(context: Context): OruSurfaceFill {
        val state = readOruSystemState(context)
        return decide(
            sdkInt = Build.VERSION.SDK_INT,
            powerSaveMode = state.powerSaveMode,
            lowRamDevice = state.lowRamDevice,
            animatorDurationScale = state.animatorDurationScale,
        )
    }
}

/** The fill in effect now, following battery saver, animator scale and low-RAM changes live. */
@Composable
fun rememberOruSurfaceFill(): OruSurfaceFill {
    val state = rememberOruSystemState()
    // The Animations switch counts as the animator scale of 0 that turns frost off, so one switch reads the same everywhere
    val animationsEnabled = LocalOruAnimationsEnabled.current
    return OruFrostPolicy.decide(
        sdkInt = Build.VERSION.SDK_INT,
        powerSaveMode = state.powerSaveMode,
        lowRamDevice = state.lowRamDevice,
        animatorDurationScale = if (animationsEnabled) state.animatorDurationScale else 0f,
    )
}
