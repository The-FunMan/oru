/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/*
 * The edge lens. A thick pane of glass bends the light where it curves down to its edge, so the picture behind it
 * is pulled and, a little, parted into colours along the rim. On API 33 and later the frost layer of a glass slab
 * is passed through a small AGSL shader after the blur: inside a band along the edge, each pixel samples the blurred
 * picture from further in, by an amount that grows towards the edge, and the red and blue channels sample a little
 * less and a little more than the green one. Clear glass has a wider band, a deeper pull and more colour than
 * Frosted (oruGlassParams). Below API 33, and if the shader ever fails to build, the glass keeps the gradient rim and
 * highlight only; the lens adds to them and replaces nothing.
 *
 * The shader is built once per frost layer and its uniforms are set when the size or the numbers change. A failed
 * build is logged once and the lens is then left out for that layer.
 */

/** The platform level of RuntimeShader. */
internal const val OruLensMinSdk = 33

/**
 * What the lens needs to know about one slab: its size in pixels, the corner radii of its top and bottom, the width of
 * the band, how far the picture is pulled at the edge, and the share by which the colours part.
 */
internal data class OruLensSpec(
    val widthPx: Float,
    val heightPx: Float,
    val topRadiusPx: Float,
    val bottomRadiusPx: Float,
    val bandPx: Float,
    val bendPx: Float,
    val chroma: Float,
)

/**
 * The lens of a slab of [size] and [outline] under [params], or null when there is none to draw: below API 33, with no
 * band or no pull, or for an outline that is not a rounded rectangle.
 */
internal fun oruLensSpec(
    sdkInt: Int,
    size: Size,
    outline: Outline,
    params: OruGlassParams,
    density: Density,
): OruLensSpec? {
    if (sdkInt < OruLensMinSdk) return null
    if (params.lensBandDp <= 0f || params.lensBendDp <= 0f) return null
    if (size.width <= 0f || size.height <= 0f) return null
    val round = (outline as? Outline.Rounded)?.roundRect ?: return null
    return with(density) {
        OruLensSpec(
            widthPx = size.width,
            heightPx = size.height,
            topRadiusPx = round.topLeftCornerRadius.x,
            bottomRadiusPx = round.bottomLeftCornerRadius.x,
            bandPx = params.lensBandDp.dp.toPx(),
            bendPx = params.lensBendDp.dp.toPx(),
            chroma = params.lensChroma,
        )
    }
}

/** Holds the shader of one frost layer. Holds no platform class until [chain] runs, which is only on API 33 and later. */
internal class OruLensEffect {
    private var shader: Any? = null
    private var failed = false

    /** [inner] with the lens of [spec] applied to its result, or [inner] itself if the lens cannot be built. */
    @RequiresApi(OruLensMinSdk)
    fun chain(inner: RenderEffect, spec: OruLensSpec): RenderEffect {
        if (failed) return inner
        return try {
            val lens = (shader as? RuntimeShader) ?: RuntimeShader(OruLensAgsl).also { shader = it }
            lens.setFloatUniform("size", spec.widthPx, spec.heightPx)
            lens.setFloatUniform("radii", spec.topRadiusPx, spec.bottomRadiusPx)
            lens.setFloatUniform("band", spec.bandPx)
            lens.setFloatUniform("bend", spec.bendPx)
            lens.setFloatUniform("chroma", spec.chroma)
            RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(lens, "content"), inner)
        } catch (e: RuntimeException) {
            failed = true
            Log.w(LogTag, "The edge lens could not be built, so the glass keeps the plain rim", e)
            inner
        }
    }
}

private const val LogTag = "OruGlass"

/**
 * The lens. `p` is the pixel relative to the centre; `edgeDistance` is the signed distance to the rounded rectangle
 * (negative inside). Inside the band, the pixel samples `content` along the inward normal by `bend * t * t`, where t
 * is 1 at the edge and 0 at the inner end of the band; the channels use `1 - chroma`, 1 and `1 + chroma` of that pull.
 */
private const val OruLensAgsl = """
uniform shader content;
uniform float2 size;
uniform float2 radii;
uniform float band;
uniform float bend;
uniform float chroma;

float edgeDistance(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float r = min(p.y < 0.0 ? radii.x : radii.y, min(halfSize.x, halfSize.y));
    float depth = -edgeDistance(p, halfSize, r);
    if (depth <= 0.0 || depth >= band) {
        return content.eval(coord);
    }
    float t = 1.0 - depth / band;
    float2 ex = float2(1.0, 0.0);
    float2 ey = float2(0.0, 1.0);
    float2 grad = float2(
        edgeDistance(p + ex, halfSize, r) - edgeDistance(p - ex, halfSize, r),
        edgeDistance(p + ey, halfSize, r) - edgeDistance(p - ey, halfSize, r));
    float2 inward = -grad / max(length(grad), 0.0001);
    float2 pull = inward * (bend * t * t);
    half4 red = content.eval(coord + pull * (1.0 - chroma));
    half4 green = content.eval(coord + pull);
    half4 blue = content.eval(coord + pull * (1.0 + chroma));
    return half4(red.r, green.g, blue.b, green.a);
}
"""
