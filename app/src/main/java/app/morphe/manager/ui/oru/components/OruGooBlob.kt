/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.roundToInt

/*
 * Two pills that fuse like liquid while they are merged and come apart when split. Merged, the second pill sits over
 * the first, and the pair is blurred and then thresholded (the classic metaball step): where the blurred alpha is above
 * half, the pixel is kept fully opaque, and the edges between the pills bridge into one shape. The colours of the
 * pills are kept; only their alpha is sharpened. Once the pills are apart the blur is zero and the effect is removed,
 * so the pills are drawn crisp.
 *
 * The threshold is an AGSL shader, so it needs API 33. Below that, or when the shader does not build, the pair slides
 * apart on the spring with no fusing. The layout is the same on both paths.
 */

/** The platform level of RuntimeShader, which the threshold needs. */
private const val GooMinSdk = 33

/** How far the second pill overlaps the first while the pair is merged, in dp. */
private const val MergedOverlapDp = 24f

/** The gap between the pills when they are apart, in dp. */
private const val SplitGapDp = 8f

/**
 * The pair of pills, merged when [split] is false and apart when it is true. [first] and [second] are the pills, drawn
 * in their own colours. The move between the two is a soft spring (see [OruMotion.springSoft]). Reduced motion moves
 * at once.
 */
@Composable
fun OruGooPair(
    split: Boolean,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = oruReducedMotion()
    // 0 while the pills are merged, 1 while they are apart. Springs overshoot a little past both.
    val spread = remember { Animatable(if (split) 1f else 0f) }
    LaunchedEffect(split) {
        spread.animateTo(if (split) 1f else 0f, OruMotion.springSoft(reduced))
    }
    val goo = remember { OruGooEffect() }
    val density = LocalDensity.current
    val gapPx = with(density) { SplitGapDp.dp.toPx() }
    val overlapPx = with(density) { MergedOverlapDp.dp.toPx() }

    Row(
        modifier = modifier.graphicsLayer {
            // The fusing is only on while the pills are (nearly) merged: the blur and the threshold would soften apart pills
            val merged = 1f - spread.value.coerceIn(0f, 1f)
            if (Build.VERSION.SDK_INT >= GooMinSdk) {
                // this.density is the scope's Float; the local `density` is a Density, which cannot scale a Float
                renderEffect = if (merged > 0f) goo.of(merged * OruMotion.GooBlurDp * this.density) else null
            }
        },
        horizontalArrangement = Arrangement.spacedBy(SplitGapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        first()
        Box(
            modifier = Modifier.graphicsLayer {
                // Slides the second pill back over the first; the spacing of the row is the gap when apart
                val merged = 1f - spread.value.coerceIn(0f, 1f)
                translationX = -(overlapPx + gapPx) * merged
            },
        ) {
            second()
        }
    }
}

/**
 * Holds the threshold shader of one pair, and the blur-and-threshold effect built from it. Holds no platform class
 * until [of] runs, which is only on API 33 and above (the shader is kept as Any, as in OruLens.kt). A failed build is
 * logged once, and the pair then slides with no fusing.
 */
private class OruGooEffect {
    private var shader: Any? = null
    private var failed = false
    private var builtBlurPx = -1f
    private var effect: RenderEffect? = null

    /**
     * The effect for a [blurPx] blur, rebuilt when the radius moves by half a pixel. Null when the shader could not be
     * built.
     */
    @RequiresApi(GooMinSdk)
    fun of(blurPx: Float): RenderEffect? {
        if (failed) return null
        val step = (blurPx * 2f).roundToInt() / 2f
        if (step != builtBlurPx) {
            builtBlurPx = step
            effect = try {
                build(step)
            } catch (e: RuntimeException) {
                failed = true
                Log.w(LogTag, "The goo threshold could not be built, so the pills slide apart with no fusing", e)
                null
            }
        }
        return effect
    }

    @RequiresApi(GooMinSdk)
    private fun build(blurPx: Float): RenderEffect {
        val threshold = (shader as? RuntimeShader) ?: RuntimeShader(OruGooAgsl).also { shader = it }
        val thresholdEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(threshold, "content")
        // The threshold is applied after the blur: the blur is the inner effect of the chain
        val chained = if (blurPx > 0f) {
            val blur = android.graphics.RenderEffect.createBlurEffect(
                blurPx,
                blurPx,
                android.graphics.Shader.TileMode.CLAMP,
            )
            android.graphics.RenderEffect.createChainEffect(thresholdEffect, blur)
        } else {
            thresholdEffect
        }
        return chained.asComposeRenderEffect()
    }
}

private const val LogTag = "OruGoo"

/**
 * The threshold. The layer arrives premultiplied and blurred; its alpha is taken through a smoothstep from 0.45 to 0.55,
 * so a pixel that is about half covered becomes solid and one that is mostly clear becomes clear. The colour is
 * un-premultiplied first, so the colour of each pill survives the step.
 */
private const val OruGooAgsl = """
uniform shader content;

half4 main(float2 coord) {
    half4 c = content.eval(coord);
    float edge = smoothstep(0.45, 0.55, float(c.a));
    half3 colour = c.a > 0.0 ? c.rgb / c.a : half3(0.0);
    return half4(colour * half(edge), half(edge));
}
"""
