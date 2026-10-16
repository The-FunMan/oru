/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import app.morphe.manager.ui.oru.theme.LocalOruGlassStyle
import app.morphe.manager.ui.oru.theme.LocalOruLight
import app.morphe.manager.ui.oru.theme.rememberOruSurfaceFill

/*
 * Liquid glass. A glass surface is a translucent floating layer: the screen behind it is blurred
 * (OruBackdropHost records it), bent along its edge by a lens on API 33 and later (OruLens.kt), tinted with the
 * surface colour, lit by a soft highlight on the side of the lamp, edged with a rim that is strongest there, given
 * thickness by a thin highlight inside the lit edge and a faint shade inside the bottom edge, and cast with layered
 * soft shadows (OruDepth.kt). The numbers come from oruGlassParams, which depend on the style the owner picked
 * (LocalOruGlassStyle) and on the role of the surface.
 *
 * Modes. oruFrostMode picks one per surface: Blur when the policy says Frosted, API 31 holds and a host
 * exists; Veil (nearly opaque) when Frosted but there is no host or the API is lower; Solid when the policy
 * says Solid (battery saver, low RAM, animator scale 0). The rim is drawn in every mode. The choice and its
 * reason are logged under the tag OruGlass, once per change, so it can be checked on a device.
 *
 * The edge light is a node of its own, drawn above the frost: the lamp (LocalOruLight) moves with the tilt of the
 * phone, and a node of its own redraws without recording the frost again.
 *
 * Blur draws the surface's own frost layer (OruBackdrop.kt) inside the clip of the glass. If the host has not
 * recorded its backdrop yet, the glass draws the Veil fill instead, so a Blur surface never shows the window through
 * its glass. Every mode clips the glass through the same layer clip (OruGlassLayerClip).
 */

/**
 * The elevation a glass of [role] takes unless the caller says: a card and a pill in the page stand on it, the dock, the
 * bar and the sheet float above it.
 */
internal fun oruGlassElevation(role: OruGlassRole): OruElevation = when (role) {
    OruGlassRole.Card, OruGlassRole.Pill -> OruElevation.Raised
    OruGlassRole.Dock, OruGlassRole.Bar, OruGlassRole.Sheet -> OruElevation.Floating
}

/** The corner forms of the glass: the dock and the Home bar, the pills, and the sheets with square bottoms. */
internal enum class OruGlassForm(val topCornerDp: Int, val bottomCornerDp: Int) {
    Dock(topCornerDp = 28, bottomCornerDp = 28),
    Pill(topCornerDp = 24, bottomCornerDp = 24),
    Sheet(topCornerDp = 28, bottomCornerDp = 0),
}

internal fun oruGlassShape(form: OruGlassForm): Shape = RoundedCornerShape(
    topStart = form.topCornerDp.dp,
    topEnd = form.topCornerDp.dp,
    bottomStart = form.bottomCornerDp.dp,
    bottomEnd = form.bottomCornerDp.dp,
)

/**
 * The presence of a bar that floats over a scrolling list: 0 while the list is at its top (nothing is under the
 * bar, so it draws no glass), rising in step with the scroll until [fadeDistancePx] has scrolled, and 1 from then on.
 * Once the first item is off screen, the bar is at 1 whatever the offset.
 */
internal fun oruGlassPresence(firstVisibleItem: Int, scrollOffsetPx: Int, fadeDistancePx: Float): Float {
    if (firstVisibleItem > 0) return 1f
    if (fadeDistancePx <= 0f) return if (scrollOffsetPx > 0) 1f else 0f
    return (scrollOffsetPx / fadeDistancePx).coerceIn(0f, 1f)
}

/**
 * A translucent floating surface of [shape]. [content] sits on top of it. [role] picks the parameters of
 * oruGlassParams (a sheet has a higher tint floor, a card a lighter blur). [fill] picks the drawing mode (see
 * the modes above); [presence] fades the whole glass, not the content, from 0 (none) to 1 (full). [tint] is the
 * colour laid over the blur: the surface colour, unless a card brings the colour of its app. The opaque modes
 * fill with it too. [elevation] picks how far the glass stands off the page (a card is Raised, the rest Floating),
 * and a [press] sinks it: the shadow tightens and the light dims. A sheet casts no shadow, since its bottom is the
 * edge of the screen. The shadow is not clipped by the glass, so the caller must not clip the modifier it passes.
 *
 * The parameters are not drawn as they are picked: when the style or the theme changes they move to the new
 * numbers (rememberOruGlassMorph), and the draw reads them as they move.
 */
@Composable
fun OruGlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    role: OruGlassRole = OruGlassRole.Bar,
    fill: OruSurfaceFill = rememberOruSurfaceFill(),
    presence: Float = 1f,
    tint: Color = Color.Unspecified,
    elevation: OruElevation = oruGlassElevation(role),
    press: OruPress? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val style = LocalOruGlassStyle.current
    val backdrop = LocalOruBackdrop.current
    val isDark = scheme.surface.luminance() < DarkLuminanceLimit
    val target = oruGlassParams(style, isDark, role)
    val morph = rememberOruGlassMorph(target)
    val hasBackdrop = backdrop != null
    val mode = oruFrostMode(fill, Build.VERSION.SDK_INT, hasBackdrop)
    val reason = oruFrostReason(fill, Build.VERSION.SDK_INT, hasBackdrop)
    LaunchedEffect(role, style, mode, reason, target) {
        Log.d(OruGlassLogTag, "$role $mode (style ${style.name}, tint ${target.tintAlpha}): $reason")
    }

    // Blur only: the frost layer this surface owns, and the anchor that gives the root position of the glass
    val frost = if (mode == OruFrostMode.Blur && Build.VERSION.SDK_INT >= OruBlurMinSdk) {
        rememberGraphicsLayer()
    } else {
        null
    }
    val frostEffect = remember { OruFrostEffect() }
    val anchor = remember { FrostAnchor() }
    val layerClip = remember(shape) { OruGlassLayerClip(shape) }
    val glassColor = if (tint.isSpecified) tint else scheme.surface
    val light = LocalOruLight.current
    val shadow = if (role == OruGlassRole.Sheet) {
        Modifier
    } else {
        // Drawn outside the outline only, so a translucent glass never shows it through itself
        Modifier.oruShadow(shape, elevation, tint = tint, press = press)
    }

    // The base carries the shadow; a translucent base is safe, because the shadow is never drawn inside the outline
    val base = when (mode) {
        OruFrostMode.Blur -> Color.Transparent
        OruFrostMode.Veil -> glassColor.copy(alpha = VeilAlpha)
        OruFrostMode.Solid -> if (tint.isSpecified) tint else scheme.surfaceContainer
    }

    Box(modifier) {
        // The fade is a layer only while it runs: a layer at alpha 1 would sit around the frost for nothing
        Box(
            Modifier
                .matchParentSize()
                .then(if (presence < 1f) Modifier.graphicsLayer { alpha = presence } else Modifier),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(shadow)
                    .background(base, shape),
            ) {}
            Box(
                Modifier
                    .matchParentSize()
                    // The bounds cut what the layer clip lets past the bottom edge of a sheet (see OruGlassLayerClip)
                    .clipToBounds()
                    .clip(layerClip)
                    .then(
                        if (frost != null) Modifier.onGloballyPositioned { anchor.origin = it.positionInRoot() } else Modifier,
                    )
                    .drawWithCache {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val veil = glassColor.copy(alpha = VeilAlpha)
                        onDrawWithContent {
                            val params = morph.value
                            if (mode == OruFrostMode.Blur && Build.VERSION.SDK_INT >= OruBlurMinSdk) {
                                if (frost != null) {
                                    frostEffect.applyTo(
                                        frost,
                                        params.blurDp.dp.toPx(),
                                        params.saturation,
                                        oruLensSpec(Build.VERSION.SDK_INT, size, outline, params, this),
                                    )
                                }
                                // The frost is drawn only when the backdrop is recorded and placed; otherwise the veil
                                val frosted = frost != null &&
                                    backdrop != null &&
                                    drawOruFrost(frost, backdrop, anchor.origin)
                                if (frosted) {
                                    drawRect(glassColor.copy(alpha = params.tintAlpha))
                                } else {
                                    drawRect(veil)
                                }
                            }
                        }
                    },
            )
            // The edge light: the light of the lit side, the rim, the thin highlight and the shade. A node of its own,
            // so the lamp and the press redraw it without recording the frost again. The rim is centred on the edge and
            // the layer clip keeps the inner half, so it reads as an inner border
            Box(
                Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .clip(layerClip)
                    .drawWithCache {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val painter = OruEdgePainter()
                        // The look follows the numbers, which move while the style morphs; a still glass keeps it
                        var painted: OruGlassParams? = null
                        var look = OruEdgeParams.None
                        onDrawBehind {
                            val params = morph.value
                            if (params != painted) {
                                look = params.toEdge()
                                painted = params
                            }
                            painter.draw(this, outline, look, isDark, light, press?.value ?: 0f)
                        }
                    },
            )
        }
        content()
    }
}

/**
 * The glass parameters [target] as they are drawn now. A new [target] (the owner picked the other style, or the
 * theme changed) does not jump: the numbers move linearly over OruMotion.GlassMorphMillis, from wherever they are,
 * so a second tap in the middle of a morph turns around smoothly. The state is read in the draw phase, so a morph
 * costs a redraw per frame and no recomposition. Reduced motion sets the new numbers at once.
 */
@Composable
internal fun rememberOruGlassMorph(target: OruGlassParams): State<OruGlassParams> {
    val reduced = oruReducedMotion()
    val current = remember { mutableStateOf(target) }
    LaunchedEffect(target, reduced) {
        val from = current.value
        if (from == target) return@LaunchedEffect
        if (reduced) {
            current.value = target
            return@LaunchedEffect
        }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = OruMotion.glassMorph<Float>(reduced = false),
        ) { fraction, _ -> current.value = oruGlassLerp(from, target, fraction) }
        current.value = target
    }
    return current
}

/**
 * The outline a glass layer is clipped to. A uniform rounded rectangle is the clip the Home bar has always drawn
 * through. A sheet has square bottom corners, and its clip would then be a path. So the clip of a glass with square
 * bottoms is its top corners on a rectangle that runs past the bottom edge: the glass is cut at its own bottom by
 * clipToBounds, which keeps the bottom edge square and the clip a rounded rectangle.
 */
private class OruGlassLayerClip(private val glass: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outline = glass.createOutline(size, layoutDirection, density)
        if (outline !is Outline.Rounded) return outline
        val round = outline.roundRect
        val squareBottom = round.bottomLeftCornerRadius.x == 0f && round.bottomRightCornerRadius.x == 0f
        val top = round.topLeftCornerRadius
        if (!squareBottom || round.topRightCornerRadius != top) return outline
        return Outline.Rounded(
            RoundRect(
                left = round.left,
                top = round.top,
                right = round.right,
                bottom = round.bottom + top.x,
                topLeftCornerRadius = top,
                topRightCornerRadius = top,
                bottomRightCornerRadius = top,
                bottomLeftCornerRadius = top,
            ),
        )
    }
}

private const val OruGlassLogTag = "OruGlass"

/** Dark when the surface is below this luminance (a 0..1 scale). The dark surfaces are near 0.007, the light near 0.95. */
private const val DarkLuminanceLimit = 0.5f

/** Veil alpha: the nearly opaque fill of Frosted without a blur, the value the layers always used. */
private const val VeilAlpha = 0.94f

