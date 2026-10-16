/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import app.morphe.manager.ui.oru.theme.OruSurfaceFill

/*
 * Backdrop blur for floating layers.
 *
 * A floating layer cannot blur what is behind it by itself: Compose only blurs what a layer draws, so the
 * screen behind has to be recorded first. OruBackdropHost records its `backdrop` slot into a GraphicsLayer, the
 * source, and draws it. The `floating` slot (the glass bars, the dock, the sheets) is drawn above it. A glass
 * surface that blurs owns a second GraphicsLayer, its frost layer: it records a copy of the source into that layer,
 * shifted so the part of the screen under the glass lands in the glass, and the frost layer carries the platform
 * blur and saturation as its own render effect (OruFrostEffect, set in the glass's draw). The glass then draws
 * the frost layer, clipped to its shape.
 *
 * The floating slot must not contain the screen itself. A layer that reads the layer it is recorded into
 * would draw a picture of itself, so the split into two slots is the contract, not a style choice.
 *
 * Why the frost layer is a leaf. The render effect belongs to the frost layer alone, and the source is never drawn
 * through an effect. On a device the blurred sheet blanked the screen behind it, while the blurred top bar did not.
 * The sheet's frost sat inside a translated layer and an outline clip that is not a rounded rectangle, and the top
 * bar's frost sat inside a rounded clip only. The frost is therefore drawn with no Compose layer between it and the
 * glass: that is the difference this design removes, and the first thing to re-check if the blank screen returns.
 *
 * Why the offset is state. The frost layer records `translate(host - own) { drawLayer(source) }` as part of its
 * display list. A glass sheet that enters by sliding up is first drawn below the screen, and a layout move does
 * not record its draw again, so a plain read of the position at draw time keeps the first offset forever:
 * the copied backdrop then lands outside the glass, nothing is blurred, and the screen behind shows through the
 * tint, sharp. Both origins are snapshot state, read inside the draw, so each change re-records the frost layer.
 *
 * Why the blur waits for a recording. Until the host has drawn its source once with a non-empty size, the source
 * holds nothing to copy. A glass then draws its nearly opaque fill instead of a blur, so it never shows a blank
 * window through its tint.
 */

/** `RenderEffect` blur is available from API 31 (Android 12). Same value as OruFrostPolicy. */
internal const val OruBlurMinSdk = 31

/** What a floating layer draws: the blurred backdrop, the nearly opaque fallback, or the plain fill. */
internal enum class OruFrostMode { Blur, Veil, Solid }

/**
 * Picks the drawing mode of a floating layer. Blur needs the policy's Frosted fill, API 31 and a recorded
 * backdrop. Frosted without any of those keeps the nearly opaque fill the layers always had, so a missing
 * host changes nothing visible. Solid is always opaque.
 */
internal fun oruFrostMode(fill: OruSurfaceFill, sdkInt: Int, hasBackdrop: Boolean): OruFrostMode = when {
    fill == OruSurfaceFill.Solid -> OruFrostMode.Solid
    sdkInt >= OruBlurMinSdk && hasBackdrop -> OruFrostMode.Blur
    else -> OruFrostMode.Veil
}

/** The reason [oruFrostMode] chose its mode, written to the OruGlass log so a mode can be checked on a device. */
internal fun oruFrostReason(fill: OruSurfaceFill, sdkInt: Int, hasBackdrop: Boolean): String = when {
    fill == OruSurfaceFill.Solid -> "policy is Solid (battery saver, low RAM or animator scale 0)"
    sdkInt < OruBlurMinSdk -> "API $sdkInt is below $OruBlurMinSdk, so there is no RenderEffect"
    !hasBackdrop -> "no OruBackdropHost above this surface, so nothing is recorded to blur"
    else -> "Frosted, API $sdkInt and a recorded backdrop"
}

/** The recorded backdrop of one host, read by the floating layers it holds. */
internal class OruBackdrop(internal val layer: GraphicsLayer) {
    /** Root position of the host. Written after each placement; read in draw, so a moved host re-records the frost. */
    internal var hostOrigin: Offset by mutableStateOf(Offset.Unspecified)

    /**
     * Set by the first draw of the host that records a non-empty source. Until it is set, [layer] has nothing a frost
     * may copy, so the glass draws its nearly opaque fill (see the note above).
     */
    internal var recorded: Boolean by mutableStateOf(false)
}

internal val LocalOruBackdrop = staticCompositionLocalOf<OruBackdrop?> { null }

/**
 * Records [backdrop] (the screen behind the floating layers) and draws it, then draws [floating] above it.
 * Glass layers (OruGlassSurface, OruFrostedDock, OruFrostedSheet) placed in [floating] blur what [backdrop]
 * shows. Pass a [modifier] that gives the host its size, usually `Modifier.fillMaxSize()`.
 *
 * Without a host, the floating layers draw as they always did: nearly opaque when Frosted, opaque when Solid.
 */
@Composable
fun OruBackdropHost(
    backdrop: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floating: @Composable BoxScope.() -> Unit = {},
) {
    val layer = rememberGraphicsLayer()
    val state = remember(layer) { OruBackdrop(layer) }
    Box(modifier.onGloballyPositioned { state.hostOrigin = it.positionInRoot() }) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    // The recording runs the children's draw into the layer; drawLayer then shows it on screen
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    // The first recording that covers the screen sets the flag; a frost reads it in its own draw
                    if (!state.recorded && size.width > 0f && size.height > 0f) state.recorded = true
                },
        ) {
            backdrop()
        }
        CompositionLocalProvider(LocalOruBackdrop provides state) {
            Box(Modifier.fillMaxSize(), content = floating)
        }
    }
}

/**
 * The render effect of one frost layer, rebuilt only when its blur, saturation or lens changes. A glass morphing
 * between two styles changes them on every frame, so the effect is set in the draw phase from the animated value, not
 * in composition. Holds no platform class until [applyTo] runs, which is only called on API 31 and above.
 */
internal class OruFrostEffect {
    private var layer: GraphicsLayer? = null
    private var blurPx = Float.NaN
    private var saturation = Float.NaN
    private var lens: OruLensSpec? = null
    private val lensEffect = OruLensEffect()

    @RequiresApi(31)
    fun applyTo(target: GraphicsLayer, blurPx: Float, saturation: Float, lens: OruLensSpec? = null) {
        if (target === layer && blurPx == this.blurPx && saturation == this.saturation && lens == this.lens) return
        layer = target
        this.blurPx = blurPx
        this.saturation = saturation
        this.lens = lens
        target.renderEffect = oruFrostRenderEffect(blurPx, saturation, lens, lensEffect)
    }
}

/**
 * Records a copy of [backdrop] into [frost] and draws it here, through the blur of [frost]. The copy is shifted so
 * that the part of the screen under this surface lands here: [own] is the root position of the surface, read in
 * draw. Returns false, and draws nothing, while the backdrop has not been recorded or placed yet; the caller then
 * draws the nearly opaque fill instead. Only called on API 31 and above.
 */
@RequiresApi(31)
internal fun DrawScope.drawOruFrost(frost: GraphicsLayer, backdrop: OruBackdrop, own: Offset): Boolean {
    val host = backdrop.hostOrigin
    if (!backdrop.recorded || !host.isSpecified || !own.isSpecified) return false
    frost.record {
        translate(left = host.x - own.x, top = host.y - own.y) {
            drawLayer(backdrop.layer)
        }
    }
    drawLayer(frost)
    return true
}

/**
 * The blur, then the colour matrix of the saturation, then the edge lens when there is one, as one platform chain.
 * The matrix sits on the blur, so the blur sees the screen pixels first, and the lens bends the blurred picture. The
 * lens is only asked for on API 33 and above (see OruLens.kt). Created only on API 31 and above, where `RenderEffect`
 * exists.
 */
@RequiresApi(31)
internal fun oruFrostRenderEffect(
    blurRadiusPx: Float,
    saturation: Float,
    lens: OruLensSpec? = null,
    lensEffect: OruLensEffect? = null,
): RenderEffect {
    val matrix = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
    val blur = android.graphics.RenderEffect.createBlurEffect(
        blurRadiusPx,
        blurRadiusPx,
        android.graphics.Shader.TileMode.CLAMP,
    )
    val frost = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix), blur)
    val lit = if (lens != null && lensEffect != null && Build.VERSION.SDK_INT >= OruLensMinSdk) {
        lensEffect.chain(frost, lens)
    } else {
        frost
    }
    return lit.asComposeRenderEffect()
}

/** Holds the root position of one glass surface. A snapshot state, so a move re-records the frost drawn in it. */
internal class FrostAnchor {
    var origin: Offset by mutableStateOf(Offset.Unspecified)
}
