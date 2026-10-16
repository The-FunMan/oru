/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.roundToInt

/*
 * The blur that the motion pieces put on a single glyph, digit or pill while it moves. A blur is a platform
 * RenderEffect, which costs an allocation to build, and an animation asks for a new radius on every frame. So the
 * effect is rebuilt only when the radius moves by half a pixel, and the frames in between reuse the one built last.
 */

/**
 * The blur of one moving element. Holds no platform class until [of] runs, which is only on API 31 and above. One
 * instance belongs to one element and is only used from the draw phase, so it needs no lock.
 */
internal class OruBlurEffect {
    private var radiusPx = -1f
    private var effect: RenderEffect? = null

    /**
     * A blur of [radiusPx] as a Compose RenderEffect, or null when the radius rounds to nothing. The radius is rounded
     * to half a pixel, so a slow animation rebuilds the effect only a few times.
     */
    @RequiresApi(OruBlurMinSdk)
    fun of(radiusPx: Float): RenderEffect? {
        val step = (radiusPx * 2f).roundToInt() / 2f
        if (step <= 0f) return null
        if (step != this.radiusPx) {
            this.radiusPx = step
            effect = android.graphics.RenderEffect.createBlurEffect(
                step,
                step,
                android.graphics.Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        }
        return effect
    }
}
