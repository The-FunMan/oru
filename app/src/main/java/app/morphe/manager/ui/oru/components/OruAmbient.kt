/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.util.endEdgeX
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startEdgeX
import kotlin.math.hypot
import kotlin.math.max

/** Opacity of the glow at the top-start corner. */
private const val TopGlowAlpha = 0.28f

/** Opacity of the glow low at the end edge. */
private const val LowerGlowAlpha = 0.18f

/** Radius of both glows as a share of the longer side. Large, so the falloff reads as a blur. */
private const val GlowRadiusShare = 0.7f

/** Height of the lower glow as a share of the screen height. */
private const val LowerGlowHeightShare = 0.85f

/**
 * A soft ambient light behind a screen: [accent] glows from the top-start corner and again low at the end,
 * each fading to nothing. Radial gradients only, so no blur pass runs. The brushes are built once per size, and
 * nothing changes per frame. Mirrored in right-to-left layouts.
 *
 * The layer draws behind the modifier's content, so the screen surface shows through wherever a glow fades.
 *
 * With [reveal], the glow opens from [origin] (a tile) instead of appearing at once: a glow the size of the tile grows
 * to the whole screen as [reveal] goes from 0 to 1, and the corner glows fade in with it.
 */
@Composable
fun Modifier.oruAmbient(
    accent: Color,
    reveal: State<Float>? = null,
    origin: OruRevealOrigin? = null,
): Modifier {
    val rtl = isRtl()
    // This layer's own coordinates, so the origin's centre is read in the same space the glow is drawn in
    val self = remember { OruRevealOrigin() }
    return this.onGloballyPositioned { self.coords = it }.drawWithCache {
        val radius = maxOf(size.width, size.height) * GlowRadiusShare
        val topGlow = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = TopGlowAlpha), accent.copy(alpha = 0f)),
            center = Offset(startEdgeX(size.width, rtl), 0f),
            radius = radius,
        )
        val lowerGlow = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = LowerGlowAlpha), accent.copy(alpha = 0f)),
            center = Offset(endEdgeX(size.width, rtl), size.height * LowerGlowHeightShare),
            radius = radius,
        )
        onDrawBehind {
            val progress = reveal?.value?.coerceIn(0f, 1f) ?: 1f
            drawRect(topGlow, alpha = progress)
            drawRect(lowerGlow, alpha = progress)
            if (reveal != null) {
                drawRevealGlow(accent, progress, revealCentre(self, origin))
            }
        }
    }
}

/**
 * Where a reveal starts: the centre of a layout, typically a tile. [coords] is set by onGloballyPositioned on that
 * layout, and read in draw, so a move of the tile costs no recomposition.
 */
class OruRevealOrigin {
    internal var coords: LayoutCoordinates? = null
}

/** Opacity of the reveal at its centre. */
private const val RevealAlpha = 0.3f

/** The radius the reveal starts at: about the size of a tile. */
private val RevealStartRadius = 56.dp

/** The centre of the reveal in this layer, or the middle of the layer while the origin is not placed. */
private fun DrawScope.revealCentre(self: OruRevealOrigin, origin: OruRevealOrigin?): Offset {
    val from = self.coords
    val to = origin?.coords
    return if (from != null && to != null && from.isAttached && to.isAttached) {
        from.localPositionOf(to, Offset(to.size.width / 2f, to.size.height / 2f))
    } else {
        Offset(size.width / 2f, size.height / 2f)
    }
}

/**
 * The reveal itself: a radial glow from [centre] whose radius grows from about a tile to the farthest corner of the
 * layer, and whose opacity follows [progress].
 */
private fun DrawScope.drawRevealGlow(accent: Color, progress: Float, centre: Offset) {
    if (progress <= 0f) return
    val farthest = hypot(
        max(centre.x, size.width - centre.x),
        max(centre.y, size.height - centre.y),
    )
    val start = RevealStartRadius.toPx()
    val radius = start + (farthest - start) * progress
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = RevealAlpha), accent.copy(alpha = 0f)),
            center = centre,
            radius = radius,
        ),
        alpha = progress,
    )
}

/**
 * The colour a Focus glows with. It starts at the card's own container colour, the tint the Focus opened from, and eases
 * to the app's accent, so the light continues the card's colour instead of jumping. When a later accent arrives (the
 * icon decoded after the first frame), the glow eases from wherever it is now.
 */
@Composable
fun rememberOruAmbientAccent(tint: OruAppTint): Color {
    val reduced = oruReducedMotion()
    val morph = remember { OruAccentMorph(start = tint.container) }
    LaunchedEffect(tint.accent) {
        morph.retarget(tint.accent, OruMotion.accentMorph(reduced))
    }
    return morph.current
}

/** A colour on its way from one value to another. [current] is read in composition, so it follows the animation. */
private class OruAccentMorph(start: Color) {
    private var from by mutableStateOf(start)
    private var to by mutableStateOf(start)
    private val fraction = Animatable(1f)

    val current: Color
        get() = lerp(from, to, fraction.value)

    suspend fun retarget(target: Color, spec: AnimationSpec<Float>) {
        from = current
        to = target
        fraction.snapTo(0f)
        fraction.animateTo(1f, spec)
    }
}
