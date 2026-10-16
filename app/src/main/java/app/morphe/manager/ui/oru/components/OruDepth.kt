/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruLightRestAngle
import app.morphe.manager.ui.oru.theme.OruLightState
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.LocalOruGlassStyle
import app.morphe.manager.ui.oru.theme.LocalOruLight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The depth system. One small vocabulary gives the whole app the same thickness: slabs that stand on the page, lit
 * from one lamp (OruLight.kt), edged with a rim that is bright on the lit side and dim opposite, with a thin
 * highlight inside the top edge and a faint shade inside the bottom edge, and cast shadows made of a few soft layers.
 *
 * Elevation says how far a surface stands off the page: Flat draws nothing, Raised is a card, a group of rows, a
 * chip or a button, Floating is the dock, the bar, a sheet or a dialog. The numbers are pure functions
 * (oruShadowLayers, oruEdgeParams) that grow with the elevation and are stronger in the dark, where a shadow
 * needs more to be seen, and in the Clear style, which is the lit, refracting glass. The modifiers only draw them.
 *
 * Shadows. A soft shadow is a few stacked copies of the shape, each a little larger and fainter, drawn behind the
 * surface and clipped so that nothing is drawn inside its outline: a translucent glass would otherwise show its
 * own shadow through itself. The copies are offset away from the lamp. The edge light is drawn after the clip and
 * the fill of the surface, so its centred strokes keep only their inner half.
 */

/** How far a surface stands off the page. */
enum class OruElevation { Flat, Raised, Floating }

/**
 * One layer of a cast shadow: [offsetYDp] below the surface with the lamp at the top, a transition [blurDp] wide, and
 * [alpha] at its darkest. The shadow of a surface is a few of these, from a tight contact shadow to a wide, faint one.
 */
data class OruShadowLayer(val offsetYDp: Float, val blurDp: Float, val alpha: Float)

/**
 * The shadow layers of [elevation]: none for Flat, a contact shadow and a wide soft one for Raised, and a third, wider
 * layer for Floating. Dark themes get stronger alphas. A [compact] surface (a button, a tile) keeps only the contact
 * layer, so a small control does not cast a large shadow. No alpha passes [MaxShadowAlpha].
 */
fun oruShadowLayers(elevation: OruElevation, isDark: Boolean, compact: Boolean = false): List<OruShadowLayer> {
    val base = when (elevation) {
        OruElevation.Flat -> emptyList()
        OruElevation.Raised -> listOf(
            OruShadowLayer(offsetYDp = 1.5f, blurDp = 4f, alpha = 0.10f),
            OruShadowLayer(offsetYDp = 7f, blurDp = 18f, alpha = 0.07f),
        )
        OruElevation.Floating -> listOf(
            OruShadowLayer(offsetYDp = 2f, blurDp = 5f, alpha = 0.12f),
            OruShadowLayer(offsetYDp = 9f, blurDp = 22f, alpha = 0.09f),
            OruShadowLayer(offsetYDp = 20f, blurDp = 44f, alpha = 0.07f),
        )
    }
    val layers = if (compact) base.take(1) else base
    val gain = if (isDark) DarkShadowGain else 1f
    return layers.map { it.copy(alpha = (it.alpha * gain).coerceAtMost(MaxShadowAlpha)) }
}

/** The darkest a shadow layer may be, in both themes. */
const val MaxShadowAlpha = 0.5f

/** Shadows are stronger in the dark, where a black shadow on a dark page needs more to be seen. */
private const val DarkShadowGain = 1.7f

/**
 * Where a shadow falls: [dx] and [dy] in dp for a layer of [offsetYDp] with the lamp at [angle] (see OruLight.kt).
 * At the rest angle, the lamp at the top left, the shadow falls [offsetYDp] down and a little to the right; a tilt
 * moves it away from the lamp. [scale] (0 to 1) shrinks the offset, as a pressed surface sinks towards the page.
 */
data class OruShadowShift(val dx: Float, val dy: Float)

fun oruShadowShift(offsetYDp: Float, angle: Float, scale: Float = 1f): OruShadowShift {
    val k = offsetYDp * scale / RestLampY
    return OruShadowShift(dx = -cos(angle) * k * SideShare, dy = -sin(angle) * k)
}

/** The vertical component of the lamp at rest, which a shadow offset of [OruShadowLayer.offsetYDp] is measured against. */
private const val RestLampY = 0.70710677f

/** A shadow falls to the side by this share of how far it falls down. */
private const val SideShare = 0.45f

/**
 * Every number of the edge treatment of one surface.
 * - [rimNear], [rimFar]: the alpha of the rim at the edge nearest the lamp (white) and farthest from it (white in the
 *   dark, a deep ink in the light, so a pale slab still has an edge on a pale page).
 * - [rimWidthDp]: the width of the rim, which lies inside the edge.
 * - [topLine]: the alpha of the thin highlight just inside the lit edge.
 * - [bottomShade]: the alpha of the faint shade inside the bottom edge.
 * - [sheen]: the alpha of the white light over the lit side of the surface, which fades over [sheenShare] of its height.
 */
data class OruEdgeParams(
    val rimNear: Float,
    val rimFar: Float,
    val rimWidthDp: Float,
    val topLine: Float,
    val bottomShade: Float,
    val sheen: Float,
    val sheenShare: Float = SlabSheenShare,
) {
    /** True when nothing would be drawn. */
    val isNone: Boolean
        get() = rimNear <= 0f && rimFar <= 0f && topLine <= 0f && bottomShade <= 0f && sheen <= 0f

    companion object {
        val None = OruEdgeParams(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/**
 * The edge treatment of [elevation] in [style] and the theme. Flat draws nothing. Floating is a quarter stronger than
 * Raised, and Clear a third stronger than Frosted, so the lit glass reads thicker. No alpha passes [MaxEdgeAlpha].
 */
fun oruEdgeParams(elevation: OruElevation, style: OruGlassStyle, isDark: Boolean): OruEdgeParams {
    if (elevation == OruElevation.Flat) return OruEdgeParams.None
    val floating = elevation == OruElevation.Floating
    val clear = style == OruGlassStyle.Clear
    val k = (if (floating) 1.25f else 1f) * (if (clear) 1.35f else 1f)
    val width = 1f + (if (floating) 0.25f else 0f) + (if (clear) 0.25f else 0f)
    fun alpha(base: Float) = (base * k).coerceIn(0f, MaxEdgeAlpha)
    return if (isDark) {
        OruEdgeParams(
            rimNear = alpha(0.24f),
            rimFar = alpha(0.05f),
            rimWidthDp = width,
            topLine = alpha(0.22f),
            bottomShade = alpha(0.20f),
            sheen = alpha(0.06f),
        )
    } else {
        OruEdgeParams(
            rimNear = alpha(0.60f),
            rimFar = alpha(0.09f),
            rimWidthDp = width,
            topLine = alpha(0.60f),
            bottomShade = alpha(0.05f),
            sheen = alpha(0.14f),
        )
    }
}

/** The strongest any alpha of the edge treatment may be. */
const val MaxEdgeAlpha = 0.95f

/** The sheen of a slab fades over the top half; a glass keeps the top third it always had. */
const val SlabSheenShare = 0.5f

/** A cool near-black, the ink of shadows and shades. A pure black on a tinted page looks dirty. */
private val DepthInk = Color(0xFF0B0C22)

/** The colour of a shadow: the ink, a little toward [tint] (the accent of the app) when there is one. */
internal fun oruShadowInk(tint: Color): Color =
    if (tint.isSpecified) lerp(DepthInk, tint, ShadowTintShare).copy(alpha = 1f) else DepthInk

private const val ShadowTintShare = 0.22f

/** True when the colour scheme is dark. The same threshold the glass uses. */
@Composable
internal fun oruIsDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < DarkLuminance

private const val DarkLuminance = 0.5f

// ---- Press ----------------------------------------------------------------------------------------------------------

/**
 * The press of one surface: how far it is pressed (0 to 1, on the press spring, so it overshoots a little on the way
 * down and settles) and where the finger touched. Read in draw phases and graphics layers only.
 */
@Stable
class OruPress internal constructor(
    private val depth: State<Float>,
    private val anchor: State<Offset>,
    internal val tiltDegrees: Float,
) {
    val value: Float get() = depth.value
    internal val point: Offset get() = anchor.value
}

/** The [OruPress] of [interactionSource]. Motion that is reduced snaps and does not lean. */
@Composable
internal fun rememberOruPress(interactionSource: InteractionSource): OruPress {
    val reduced = oruReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val depth = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = OruMotion.press(reduced),
        label = "oruPressDepth",
    )
    val anchor = remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) anchor.value = interaction.pressPosition
        }
    }
    return remember(depth, anchor, reduced) {
        OruPress(depth, anchor, tiltDegrees = if (reduced) 0f else OruMotion.PressTiltDegrees)
    }
}

/**
 * The scale of a pressed surface: down to [OruMotion.PressScale], leaning away from the finger by up to
 * [OruPress.tiltDegrees] about the point that was pressed, all on the press spring. The leaning needs a point; a
 * press that has none (a key press) only scales.
 */
internal fun Modifier.oruPressScale(press: OruPress): Modifier = graphicsLayer {
    val amount = press.value
    val scale = 1f - (1f - OruMotion.PressScale) * amount
    scaleX = scale
    scaleY = scale
    val point = press.point
    if (press.tiltDegrees > 0f && point.isSpecified && size.width > 0f && size.height > 0f) {
        val nx = ((point.x / size.width - 0.5f) * 2f).coerceIn(-1f, 1f)
        val ny = ((point.y / size.height - 0.5f) * 2f).coerceIn(-1f, 1f)
        rotationY = nx * press.tiltDegrees * amount
        rotationX = -ny * press.tiltDegrees * amount
        cameraDistance = CameraDistanceDp * density
    }
}

private const val CameraDistanceDp = 14f

// ---- Drawing --------------------------------------------------------------------------------------------------------

/**
 * Casts the layered shadow of [elevation] behind a surface of [shape]. Place it before the clip of the surface, so the
 * shadow is not cut by it. [tint] is the accent of the surface (the shadow leans a little toward it), [compact] keeps
 * only the contact layer, and a [press] tightens the shadow as the surface sinks. [strength] (0 to 1, read in the draw
 * phase) fades the whole shadow, for a surface that lifts and settles.
 */
@Composable
internal fun Modifier.oruShadow(
    shape: Shape,
    elevation: OruElevation,
    tint: Color = Color.Unspecified,
    compact: Boolean = false,
    press: OruPress? = null,
    strength: State<Float>? = null,
): Modifier {
    val isDark = oruIsDark()
    val light = LocalOruLight.current
    val layers = remember(elevation, isDark, compact) { oruShadowLayers(elevation, isDark, compact) }
    val ink = remember(tint) { oruShadowInk(tint) }
    if (layers.isEmpty()) return this
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addDepthOutline(outline) }
        onDrawBehind {
            if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
            val amount = (strength?.value ?: 1f).coerceIn(0f, 1f)
            if (amount <= 0f) return@onDrawBehind
            val sink = (press?.value ?: 0f).coerceIn(0f, 1f)
            val tighten = 1f - OruMotion.PressShadowTighten * sink
            val angle = light.angle
            clipPath(path, ClipOp.Difference) {
                layers.forEach { layer -> drawSoftShadow(path, layer, ink, angle, tighten, amount) }
            }
        }
    }
}

/**
 * One layer of a shadow: [ShadowSteps] copies of [path], each larger and fainter than the one before, which add up to
 * a transition of [OruShadowLayer.blurDp] from the full alpha inside to nothing outside. [tighten] shrinks the
 * offset and the blur, and [amount] the alpha. Nothing here allocates: the shape is scaled with the transform of the canvas.
 */
private fun DrawScope.drawSoftShadow(
    path: Path,
    layer: OruShadowLayer,
    ink: Color,
    angle: Float,
    tighten: Float,
    amount: Float,
) {
    val shift = oruShadowShift(layer.offsetYDp, angle, tighten)
    val blur = layer.blurDp.dp.toPx() * tighten
    val steps = (blur / StepWidth.toPx()).roundToInt().coerceIn(MinShadowSteps, MaxShadowSteps)
    val stepColor = ink.copy(alpha = layer.alpha * amount / steps)
    val dx = shift.dx.dp.toPx()
    val dy = shift.dy.dp.toPx()
    for (step in 0 until steps) {
        val grow = -blur / 2f + blur * (step + 0.5f) / steps
        val scaleX = ((size.width + 2f * grow) / size.width).coerceAtLeast(MinShadowScale)
        val scaleY = ((size.height + 2f * grow) / size.height).coerceAtLeast(MinShadowScale)
        translate(left = dx, top = dy) {
            scale(scaleX = scaleX, scaleY = scaleY) {
                drawPath(path, stepColor)
            }
        }
    }
}

private val StepWidth = 3.dp
private const val MinShadowSteps = 2
private const val MaxShadowSteps = 7
private const val MinShadowScale = 0.02f

private fun Path.addDepthOutline(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

/**
 * Lights the edge of a surface of [shape] at [elevation]: the sheen of the lit side, the faint shade inside the bottom
 * edge, the thin highlight inside the lit edge and the rim. Place it after the clip and the fill of the surface,
 * before its content. Pressing the surface ([press]) dims the light. With [sheen] off the lit side is left plain.
 * With [overContent] the light is drawn above the content of the surface, for a group whose rows cover its fill; only
 * the rim and the thin highlight are drawn then, since the sheen and the shade would fall on text. [strength] (0 to 1,
 * read in the draw phase) fades the whole light, for a surface that lifts and settles.
 */
@Composable
internal fun Modifier.oruEdgeLight(
    shape: Shape,
    elevation: OruElevation,
    press: OruPress? = null,
    sheen: Boolean = true,
    overContent: Boolean = false,
    strength: State<Float>? = null,
): Modifier {
    val style = LocalOruGlassStyle.current
    val isDark = oruIsDark()
    val light = LocalOruLight.current
    val look = remember(elevation, style, isDark, sheen, overContent) {
        val params = oruEdgeParams(elevation, style, isDark)
        when {
            overContent -> params.copy(sheen = 0f, bottomShade = 0f)
            sheen -> params
            else -> params.copy(sheen = 0f)
        }
    }
    val painter = remember { OruEdgePainter() }
    if (look.isNone) return this
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        if (overContent) {
            onDrawWithContent {
                drawContent()
                painter.draw(this, outline, look, isDark, light, press?.value ?: 0f, strength?.value ?: 1f)
            }
        } else {
            onDrawBehind {
                painter.draw(this, outline, look, isDark, light, press?.value ?: 0f, strength?.value ?: 1f)
            }
        }
    }
}

/**
 * A slab: the shadow, the clip, the fill and the edge light of one surface, in the order they must be drawn. [color]
 * is the fill. A [shadow] of false leaves the cast shadow out (a slab inside a group, a surface that fills its parent).
 * With [overContent] the edge light is drawn above the content (see [oruEdgeLight]).
 */
@Composable
internal fun Modifier.oruSlab(
    shape: Shape,
    color: Color,
    elevation: OruElevation = OruElevation.Raised,
    tint: Color = Color.Unspecified,
    compact: Boolean = false,
    press: OruPress? = null,
    shadow: Boolean = true,
    sheen: Boolean = true,
    overContent: Boolean = false,
): Modifier = this
    .then(if (shadow) Modifier.oruShadow(shape, elevation, tint, compact, press) else Modifier)
    .clip(shape)
    .background(color)
    .oruEdgeLight(shape, elevation, press, sheen, overContent)

/**
 * Draws the edge light into a draw scope, keeping the brushes it builds until the size, the numbers, the theme or the
 * lamp (a degree at a time) change. One painter belongs to one surface and draws on one thread.
 */
internal class OruEdgePainter {
    private var built: OruEdgeParams? = null
    private var builtDark = false
    private var builtStep = Int.MIN_VALUE
    private var builtWidth = -1f
    private var builtHeight = -1f

    private var rim: Brush = SolidColor(Color.Transparent)
    private var top: Brush = SolidColor(Color.Transparent)
    private var sheen: Brush = SolidColor(Color.Transparent)
    private var shade: Brush = SolidColor(Color.Transparent)
    private var rimStroke = Stroke(0f)
    private var topStroke = Stroke(0f)
    private var shadeTop = 0f
    private var shadeHeight = 0f

    fun draw(
        scope: DrawScope,
        outline: Outline,
        look: OruEdgeParams,
        isDark: Boolean,
        light: OruLightState,
        press: Float,
        strength: Float = 1f,
    ) {
        if (look.isNone || strength <= 0f) return
        val width = scope.size.width
        val height = scope.size.height
        if (width <= 0f || height <= 0f) return
        val angle = light.angle
        val lift = light.lift
        val step = (angle / AngleStep).roundToInt()
        if (look != built || isDark != builtDark || step != builtStep || width != builtWidth || height != builtHeight) {
            rebuild(scope, look, isDark, step * AngleStep, width, height)
            built = look
            builtDark = isDark
            builtStep = step
            builtWidth = width
            builtHeight = height
        }
        val dim = (1f - OruMotion.PressHighlightDim * press).coerceIn(0f, 1f) * strength.coerceIn(0f, 1f)
        val glint = (dim * (1f + LiftGain * lift)).coerceIn(0f, 1f)
        with(scope) {
            if (look.sheen > 0f) drawRect(brush = sheen, alpha = dim)
            if (look.bottomShade > 0f) {
                drawRect(brush = shade, topLeft = Offset(0f, shadeTop), size = Size(width, shadeHeight))
            }
            if (look.topLine > 0f) drawOutline(outline = outline, brush = top, alpha = glint, style = topStroke)
            if (look.rimNear > 0f || look.rimFar > 0f) {
                drawOutline(outline = outline, brush = rim, alpha = dim, style = rimStroke)
            }
        }
    }

    private fun rebuild(
        scope: DrawScope,
        look: OruEdgeParams,
        isDark: Boolean,
        angle: Float,
        width: Float,
        height: Float,
    ) = with(scope) {
        val cx = width / 2f
        val cy = height / 2f
        // The rim runs along the lamp: bright at the edge nearest it, dim at the edge farthest from it
        val lx = cos(angle)
        val ly = sin(angle)
        val reach = abs(lx) * cx + abs(ly) * cy
        val far = if (isDark) Color.White else DepthInk
        rim = Brush.linearGradient(
            0f to Color.White.copy(alpha = look.rimNear),
            0.5f to Color.White.copy(alpha = 0f),
            0.62f to far.copy(alpha = 0f),
            1f to far.copy(alpha = look.rimFar),
            start = Offset(cx + lx * reach, cy + ly * reach),
            end = Offset(cx - lx * reach, cy - ly * reach),
        )
        rimStroke = Stroke(width = look.rimWidthDp.dp.toPx() * 2f)
        topStroke = Stroke(width = (look.rimWidthDp + 1f).dp.toPx() * 2f)

        // The top highlight and the sheen come from the lit side: the top at rest, turned a little with the lamp
        val turn = angle - OruLightRestAngle
        val ux = sin(turn)
        val uy = -cos(turn)
        val litReach = abs(ux) * cx + abs(uy) * cy
        val sx = cx + ux * litReach
        val sy = cy + uy * litReach
        val fade = TopLineFade.toPx()
        top = Brush.linearGradient(
            0f to Color.White.copy(alpha = look.topLine),
            1f to Color.White.copy(alpha = 0f),
            start = Offset(sx, sy),
            end = Offset(sx - ux * fade, sy - uy * fade),
        )
        val sheenLength = (2f * litReach * look.sheenShare).coerceAtLeast(1f)
        sheen = Brush.linearGradient(
            0f to Color.White.copy(alpha = look.sheen),
            1f to Color.White.copy(alpha = 0f),
            start = Offset(sx, sy),
            end = Offset(sx - ux * sheenLength, sy - uy * sheenLength),
        )

        shadeHeight = min(height * ShadeMaxShare, ShadeHeight.toPx())
        shadeTop = height - shadeHeight
        shade = Brush.verticalGradient(
            0f to DepthInk.copy(alpha = 0f),
            1f to DepthInk.copy(alpha = look.bottomShade),
            startY = shadeTop,
            endY = height,
        )
    }
}

/** The lamp moves in steps of a degree; a finer turn rebuilds nothing. */
private const val AngleStep = 0.0175f

/** The glint along the lit edge brightens or dims by this share as the phone leans. */
private const val LiftGain = 0.2f

private val TopLineFade = 14.dp
private val ShadeHeight = 16.dp
private const val ShadeMaxShare = 0.4f
