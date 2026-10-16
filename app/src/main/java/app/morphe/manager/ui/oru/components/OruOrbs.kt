/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import app.morphe.manager.ui.oru.theme.OruLightRestAngle
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import app.morphe.manager.ui.oru.theme.LocalOruLight
import app.morphe.manager.ui.oru.theme.rememberOruSurfaceFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/*
 * The orbs behind Home. Plain Home is a near flat gradient, so a glass card over it would blur nothing the owner can
 * see. A few large soft discs of colour (the theme primary and the accents of the first apps) sit behind the list
 * and drift slowly; the glass of the cards, the toggles and the bar blur and tint them, so switching the glass style
 * has something to act on. The orbs are drawn in a layer of their own, with radial gradients only: one infinite
 * transition read in the draw phase, no composition per frame, and no motion at all when motion is reduced or the
 * glass is solid.
 */

/** How many orbs there are. The first takes the theme primary, the others the accents of the first three apps. */
internal const val OruOrbCount = 4

/** The most opaque point of one orb, in the light and the dark theme. OruGlassParamsTest holds card text to it. */
internal fun oruOrbPeakAlpha(isDark: Boolean): Float = if (isDark) OrbPeakAlphaDark else OrbPeakAlphaLight

private const val OrbPeakAlphaLight = 0.32f
private const val OrbPeakAlphaDark = 0.36f

/** Share of the radius that stays at the peak before the edge falls to nothing, so the edge reads under a blur. */
private const val OrbCoreShare = 0.6f

/** The alpha at the end of the core, as a share of the peak. */
private const val OrbCoreFall = 0.85f

/**
 * Where one orb sits and how it drifts. [anchorX] and [anchorY] are shares of the width and the height, [radiusShare]
 * and [driftShare] shares of the width. The drift is a circle of a whole number of turns per lap on each axis, so the
 * last frame of a lap meets the first.
 */
internal class OruOrbSpec(
    val anchorX: Float,
    val anchorY: Float,
    val radiusShare: Float,
    val driftShare: Float,
    val turnsX: Int,
    val turnsY: Int,
    val phaseX: Float,
    val phaseY: Float,
)

/**
 * The layout, top to bottom of a phone: one behind the title and the toggles, one at the end edge by the first card,
 * one low at the start edge and one at the bottom end, so every card and pill has colour behind part of it.
 */
internal val OruOrbSpecs = listOf(
    OruOrbSpec(anchorX = 0.15f, anchorY = 0.22f, radiusShare = 0.50f, driftShare = 0.10f, turnsX = 1, turnsY = 1, phaseX = 0.00f, phaseY = 0.25f),
    OruOrbSpec(anchorX = 0.88f, anchorY = 0.42f, radiusShare = 0.55f, driftShare = 0.08f, turnsX = 1, turnsY = 2, phaseX = 0.35f, phaseY = 0.60f),
    OruOrbSpec(anchorX = 0.20f, anchorY = 0.66f, radiusShare = 0.60f, driftShare = 0.09f, turnsX = 2, turnsY = 1, phaseX = 0.70f, phaseY = 0.10f),
    OruOrbSpec(anchorX = 0.82f, anchorY = 0.90f, radiusShare = 0.55f, driftShare = 0.10f, turnsX = 1, turnsY = 1, phaseX = 0.50f, phaseY = 0.85f),
)

private val TwoPi = (2.0 * PI).toFloat()

/** The x of the centre of [spec] at [phase] (0..1 around a lap) on a surface [width] wide. */
internal fun oruOrbX(spec: OruOrbSpec, phase: Float, width: Float): Float =
    width * spec.anchorX + width * spec.driftShare * sin(TwoPi * (spec.turnsX * phase + spec.phaseX))

/** The y of the centre of [spec] at [phase] on a surface [width] wide and [height] high. The drift is round, so it scales with the width. */
internal fun oruOrbY(spec: OruOrbSpec, phase: Float, width: Float, height: Float): Float =
    height * spec.anchorY + width * spec.driftShare * cos(TwoPi * (spec.turnsY * phase + spec.phaseY))

/**
 * The [OruOrbCount] colours of the orbs: [primary] first, then the accents of the apps, and a [fallbacks] colour
 * for each slot an app does not fill (fewer apps than orbs), then [primary] again if those run out too.
 */
internal fun oruOrbPalette(primary: Color, accents: List<Color>, fallbacks: List<Color>): List<Color> =
    List(OruOrbCount) { index ->
        if (index == 0) primary else accents.getOrNull(index - 1) ?: fallbacks.getOrNull(index - 1) ?: primary
    }

/**
 * How far the orbs are moved up by a list scrolled [scrollPx], on a surface [height] high. The orbs lie behind the
 * list and move a tenth as far, so they read as farther away; the shift eases to a limit of a fifth of the height
 * instead of running off the screen.
 */
internal fun oruOrbParallax(scrollPx: Float, height: Float): Float {
    val limit = height * ParallaxLimitShare
    if (limit <= 0f) return 0f
    return -limit * tanh(scrollPx * ParallaxShare / limit)
}

/** How far the orbs move sideways as the lamp turns (see OruLight.kt), as a share of the width per radian. */
internal fun oruOrbTilt(lampTurn: Float, width: Float): Float = -lampTurn * width * TiltShare

private const val ParallaxShare = 0.1f
private const val ParallaxLimitShare = 0.2f
private const val TiltShare = 0.04f

/**
 * The orbs, filling [modifier]. [colors] has [OruOrbCount] entries (see [oruOrbPalette]). They drift while
 * motion is allowed and the glass is blurring; otherwise they hold their first position. [scroll] is the scroll
 * offset of the list in front of them, in pixels, read in the draw phase: the orbs move a tenth as far, and slide a
 * little as the phone is tilted (the lamp of OruLight.kt). Neither moves when motion is reduced.
 */
@Composable
fun OruOrbBackdrop(colors: List<Color>, modifier: Modifier = Modifier, scroll: (() -> Float)? = null) {
    val reducedMotion = oruReducedMotion()
    val light = LocalOruLight.current
    val drifting = !reducedMotion && rememberOruSurfaceFill() == OruSurfaceFill.Frosted
    val peak = oruOrbPeakAlpha(MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    val phase: State<Float> = if (drifting) {
        rememberInfiniteTransition(label = "oruOrbs").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = OruMotion.OrbDriftMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "oruOrbPhase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Box(
        modifier
            // A layer of its own: the drift redraws this layer alone, and the glass copies it as it is
            .graphicsLayer { }
            .drawWithCache {
                val brushes = OruOrbSpecs.mapIndexed { index, spec ->
                    val color = colors[index]
                    Brush.radialGradient(
                        0f to color.copy(alpha = peak),
                        OrbCoreShare to color.copy(alpha = peak * OrbCoreFall),
                        1f to color.copy(alpha = 0f),
                        center = Offset.Zero,
                        radius = (spec.radiusShare * size.width).coerceAtLeast(1f),
                    )
                }
                onDrawBehind {
                    val lap = phase.value
                    val parallax = if (reducedMotion) 0f else oruOrbParallax(scroll?.invoke() ?: 0f, size.height)
                    val tilt = if (reducedMotion) 0f else oruOrbTilt(light.angle - OruLightRestAngle, size.width)
                    OruOrbSpecs.forEachIndexed { index, spec ->
                        translate(
                            left = oruOrbX(spec, lap, size.width) + tilt,
                            top = oruOrbY(spec, lap, size.width, size.height) + parallax,
                        ) {
                            drawCircle(brush = brushes[index], radius = spec.radiusShare * size.width, center = Offset.Zero)
                        }
                    }
                }
            },
    )
}
