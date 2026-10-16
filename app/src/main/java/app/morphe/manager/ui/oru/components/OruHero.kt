/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.nav.oruSharedTile
import app.morphe.manager.ui.oru.theme.BlobCorners
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruMonoStyle
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.LocalOruLight
import app.morphe.manager.ui.oru.theme.rememberOruBlobShape
import app.morphe.manager.ui.screen.shared.AppIcon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The patch count of a working run, shown under the percentage: [done] of [total]. */
data class OruHeroCounter(val done: Int, val total: Int)

/**
 * The Focus hero. Its centre is one of four things.
 *
 * The app's tile is a blob that morphs into a circle on every state but Ready, and it holds the glyph: the tile,
 * a check or an exclamation. A working run draws the hexagon of its pieces, lit by [fill], the share of the whole run
 * done: [OruHexagonAssembly], which waits with a sweep until patches apply. The pieces stay on screen when the run is
 * done, settled and with the check, and a failed run keeps them with the failing one outside.
 *
 * [description] is what TalkBack announces for the hero. The caller changes it only on a stage change or a step of the
 * percentage (see oruAnnouncedPercent), so the polite live region does not announce every tick. When it is null, the
 * state name is used.
 *
 * [applyingPatches], [patchTotal] and [failedPatchIndex] describe the patch run, and [patchShare] is the share of the
 * whole run the patch stage fills. The pieces show only when [patchTotal] is at least 1, and the hexagon waits until
 * [applyingPatches] is set. [carryOver] places the pieces the run has already lit at once, see [OruHexagonAssembly].
 * The percentage is drawn only when [progress] is given, so a wait with no progress shows no false zero.
 */
@Composable
fun OruHero(
    state: OruHeroState,
    tile: OruTileSource,
    shape: OruBlobShape,
    progress: (() -> Float)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    counter: OruHeroCounter? = null,
    sharedKey: String? = null,
    applyingPatches: Boolean = false,
    patchTotal: Int = 0,
    failedPatchIndex: Int? = null,
    fill: Float = 0f,
    patchShare: Float = 0f,
    carryOver: Boolean = false,
) {
    val reduced = oruReducedMotion()
    val visual = oruHeroVisual(state, patchTotal, failedPatchIndex)
    val morphed = rememberOruBlobShape(if (oruHeroIsCircle(state)) CircleCorners else shape.corners)
    val tileSize by animateDpAsState(
        targetValue = oruHeroTileSizeDp(state, compact).dp,
        animationSpec = OruMotion.tileBounds(reduced),
        label = "oruHeroTileSize",
    )
    // A ready hero wears the app's own tint, the same blob the card showed, so the morph keeps its colour
    val appTint = rememberOruAppTint(tile.packageInfo)
    // The tile's own light: a soft glow of the app's accent that shows while the tile does, see drawTileGlow
    val glowAlpha by animateFloatAsState(
        targetValue = if (visual == OruHeroVisual.Tile) 1f else 0f,
        animationSpec = OruMotion.glassMorph(reduced),
        label = "oruHeroGlow",
    )
    val lamp = LocalOruLight.current
    val colors = if (visual == OruHeroVisual.Tile) {
        oruHeroColors(state, appTint)
    } else {
        OruRoleColors(container = Color.Transparent, content = MaterialTheme.colorScheme.onSurface)
    }
    val containerColor by animateColorAsState(
        targetValue = colors.container,
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruHeroColor",
    )
    // The circle is hidden and a little smaller while the hexagon shows, and scales in when the run is done
    val tileScale by animateFloatAsState(
        targetValue = if (visual == OruHeroVisual.Tile) 1f else HiddenTileScale,
        animationSpec = OruMotion.pop(reduced),
        label = "oruHeroTileScale",
    )
    // The visible tile is the shared element, so the card's tile morphs into the tile itself, not into the box around it
    val sizedTile = Modifier.size(tileSize)
    val sharedTile = if (sharedKey != null) sizedTile.oruSharedTile(sharedKey) else sizedTile
    val areaHeight by animateDpAsState(
        targetValue = heroAreaHeight(visual, compact),
        animationSpec = OruMotion.tileBounds(reduced),
        label = "oruHeroArea",
    )
    val glyphSize = if (compact) CompactGlyphSize else GlyphSize
    val glyph = if (visual == OruHeroVisual.Tile) oruHeroGlyph(state) else null
    val label = description ?: when (state) {
        OruHeroState.Ready -> tile.name
        OruHeroState.Working -> stringResource(R.string.oru_run_title_patching)
        OruHeroState.Done -> stringResource(R.string.oru_action_done)
        OruHeroState.Failed -> stringResource(R.string.oru_failed_title)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(areaHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visual == OruHeroVisual.Hexagon,
            modifier = Modifier.matchParentSize(),
            enter = if (reduced) EnterTransition.None else fadeIn(tween(GlyphFadeMillis)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(GlyphFadeMillis)),
        ) {
            HeroCentre(progress = progress, counter = counter, color = colors.content, compact = compact) {
                OruHexagonAssembly(
                    fill = fill,
                    patches = patchTotal,
                    patchShare = patchShare,
                    failed = failedPatchIndex != null,
                    accent = appTint.accent,
                    modifier = Modifier.fillMaxSize(),
                    preparing = oruHeroPreparing(state, applyingPatches, patchTotal),
                    alreadyPlaced = carryOver,
                )
            }
        }
        // The glow lies under the tile, pushed toward the lamp; the bloom of a finished run is under it too
        Box(modifier = sizedTile.drawBehind { drawTileGlow(appTint.accent, glowAlpha, lamp.angle) })
        if (glyph == OruHeroGlyph.Done) {
            OruDoneBloom(color = appTint.accent, reduced = reduced, modifier = sizedTile)
        }
        // The tile stands on a soft contact shadow and is edged with the light of the depth system, only while it shows
        // (not while the pieces take its place); the scale comes first, so they shrink with it
        val tileElevation = if (visual == OruHeroVisual.Tile) OruElevation.Raised else OruElevation.Flat
        Box(
            modifier = sharedTile
                .graphicsLayer {
                    scaleX = tileScale
                    scaleY = tileScale
                }
                .oruShadow(shape = morphed, elevation = tileElevation, tint = appTint.accent, compact = true)
                .clip(morphed)
                .background(if (visual == OruHeroVisual.Tile) containerColor else Color.Transparent)
                .oruEdgeLight(shape = morphed, elevation = tileElevation),
            contentAlignment = Alignment.Center,
        ) {
            // Crossfade lays its children out top-start by default, which pinned the glyph to the corner of the
            // circle. The glyph is centred in the tile instead.
            Crossfade(
                targetState = glyph,
                modifier = Modifier.fillMaxSize(),
                animationSpec = if (reduced) snap<Float>() else tween<Float>(durationMillis = GlyphFadeMillis),
                label = "oruHeroGlyph",
            ) { target ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (target) {
                        null -> Unit
                        OruHeroGlyph.Tile -> HeroTileGlyph(tile = tile, tileSize = tileSize, color = colors.content)
                        OruHeroGlyph.Done -> OruPopIn(reduced = reduced) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.content,
                                modifier = Modifier.size(glyphSize),
                            )
                        }
                        OruHeroGlyph.Failed -> OruPopIn(reduced = reduced) {
                            HeroExclamation(glyphSize = glyphSize, color = colors.content)
                        }
                    }
                    if (target == OruHeroGlyph.Done) {
                        OruLightSweep(color = appTint.accent, reduced = reduced, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * The centre of a patch or wait hero: the drawing takes the space above the text, and the percentage and counter
 * sit under it, each on one line.
 */
@Composable
private fun HeroCentre(
    progress: (() -> Float)?,
    counter: OruHeroCounter?,
    color: Color,
    compact: Boolean,
    drawing: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            drawing()
        }
        Spacer(Modifier.height(if (compact) CompactTextGap else TextGap))
        HeroPercent(progress = progress, counter = counter, color = color, compact = compact)
    }
}

@Composable
private fun HeroTileGlyph(
    tile: OruTileSource,
    tileSize: Dp,
    color: Color,
) {
    val packageInfo = tile.packageInfo
    if (packageInfo != null) {
        AppIcon(
            packageInfo = packageInfo,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.7f),
        )
    } else {
        Text(
            text = oruTileLetter(tile.name),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = (tileSize.value * LetterShare).sp,
                lineHeight = (tileSize.value * LetterLineShare).sp,
            ),
            color = color,
        )
    }
}

/**
 * The percentage of the run, read from [progress] through a derived state so it changes once per step. Nothing is
 * drawn for the percentage when [progress] is null. A [counter], when the run passes one, sits under it. The digits
 * roll to each new value and the unit after them stays put (see [OruRollingCount]). Both lines are single lines, so
 * a large font scale never wraps them. Neither has semantics of its own: the hero's description reads the progress.
 */
@Composable
private fun HeroPercent(
    progress: (() -> Float)?,
    counter: OruHeroCounter?,
    color: Color,
    compact: Boolean,
) {
    val percent by remember(progress) {
        derivedStateOf {
            progress?.let { read -> oruPercent(read()) } ?: 0
        }
    }
    val percentStyle = OruMonoStyle.copy(
        fontSize = if (compact) CompactPercentSize else PercentSize,
        lineHeight = if (compact) CompactPercentLine else PercentLine,
        fontWeight = FontWeight.Medium,
    )
    val counterStyle = OruMonoStyle.copy(
        fontSize = if (compact) CompactCounterSize else CounterSize,
        lineHeight = if (compact) CompactCounterLine else CounterLine,
    )
    // The hero's description carries the run's progress in steps, so the digits and the count are left out of its
    // semantics. Otherwise every tick and every patch would change the live region's text and be read out.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress != null) {
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OruRollingCount(value = percent, slotDigits = PercentSlotDigits, style = percentStyle, color = color)
                Text(text = "%", style = percentStyle, color = color, maxLines = 1, softWrap = false)
            }
        }
        if (counter != null) {
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OruRollingCount(
                    value = counter.done,
                    slotDigits = oruDigitCount(counter.total),
                    style = counterStyle,
                    color = color,
                )
                Spacer(Modifier.width(CounterGap))
                Text(
                    text = stringResource(R.string.oru_run_counter_of, counter.total),
                    style = counterStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Grows its content from [OruMotion.PopInScale] to full size on the pop spring, once, when it first
 * appears. The transition state starts false and is set true straight away, so the enter runs on the
 * first composition. Reduced motion shows it at full size at once.
 */
@Composable
private fun OruPopIn(
    reduced: Boolean,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = if (reduced) {
            EnterTransition.None
        } else {
            scaleIn(initialScale = OruMotion.PopInScale, animationSpec = OruMotion.pop(reduced)) +
                fadeIn(OruMotion.fade(reduced))
        },
    ) {
        content()
    }
}

/** An exclamation mark drawn as a bar and a dot on a 24 unit grid. */
@Composable
private fun HeroExclamation(
    glyphSize: Dp,
    color: Color,
) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * BarLeft, h * BarTop),
            size = Size(w * BarWidth, h * BarHeight),
            cornerRadius = CornerRadius(w * BarWidth / 2f),
        )
        drawCircle(
            color = color,
            radius = w * DotRadius,
            center = Offset(w / 2f, h * DotCenterY),
        )
    }
}

/**
 * A soft glow of [color] behind the tile, at [alpha]. The glow sits toward the lamp at [lampAngle], so the tile reads as
 * lit from there: the light of the depth system, drawn as one radial gradient.
 */
private fun DrawScope.drawTileGlow(color: Color, alpha: Float, lampAngle: Float) {
    if (alpha <= 0f) return
    val reach = size.minDimension * GlowOffsetShare
    val centre = Offset(size.width / 2f + cos(lampAngle) * reach, size.height / 2f + sin(lampAngle) * reach)
    val radius = size.minDimension * GlowRadiusShare
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = GlowAlpha * alpha), color.copy(alpha = 0f)),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * The halo of a finished hero: the app's light swells once from behind the circle and settles. With reduced motion it
 * is at its full size from the start.
 */
@Composable
private fun OruDoneBloom(
    color: Color,
    reduced: Boolean,
    modifier: Modifier,
) {
    val swell = remember { Animatable(0f) }
    LaunchedEffect(reduced) { swell.animateTo(1f, OruMotion.expand(reduced)) }
    Box(
        modifier = modifier.drawBehind {
            val t = swell.value
            val radius = size.minDimension * BloomRadiusShare * (BloomStartShare + (1f - BloomStartShare) * t)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = BloomAlpha * t), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        },
    )
}

/**
 * A band of the app's light that crosses a finished hero once, diagonally, and fades out as it leaves. Reduced motion
 * runs the sweep at once, so it is never seen.
 */
@Composable
private fun OruLightSweep(
    color: Color,
    reduced: Boolean,
    modifier: Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduced) { progress.animateTo(1f, OruMotion.lightSweep(reduced)) }
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            val p = progress.value
            // Zero at both ends of the sweep, so the band is seen only while it crosses
            val fade = 1f - abs(2f * p - 1f)
            if (fade <= 0f) return@drawWithContent
            val centreX = size.width * (SweepStartShare + (SweepEndShare - SweepStartShare) * p)
            val centre = Offset(centreX, size.height / 2f)
            val reach = size.maxDimension * SweepReachShare * DiagonalShare
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = SweepAlpha * fade), Color.Transparent),
                    start = Offset(centre.x - reach, centre.y - reach),
                    end = Offset(centre.x + reach, centre.y + reach),
                ),
            )
        },
    )
}

/**
 * Colours of the tile for each state. Ready and Working wear the app's tint. Done and failed use the status roles.
 */
@Composable
private fun oruHeroColors(state: OruHeroState, appTint: OruAppTint): OruRoleColors = when (state) {
    OruHeroState.Ready, OruHeroState.Working -> OruRoleColors(container = appTint.tile, content = appTint.onContainer)
    OruHeroState.Done -> oruContainerColors(OruContainerRole.Ok)
    OruHeroState.Failed -> oruContainerColors(OruContainerRole.Error)
}

/**
 * The height of the hero's centre. The Expert view's pieces take the compact room, and the other visuals take the
 * hero's own square: the full square for Simple, the compact one for a tile in Expert.
 */
private fun heroAreaHeight(visual: OruHeroVisual, compact: Boolean): Dp = when {
    visual == OruHeroVisual.Hexagon && compact -> CompactPieceAreaHeight
    compact -> CompactHeroSize
    else -> HeroSize
}

/** The circle every non-ready state morphs into. */
private val CircleCorners = BlobCorners(
    topLeftX = 50f, topRightX = 50f, bottomRightX = 50f, bottomLeftX = 50f,
    topLeftY = 50f, topRightY = 50f, bottomRightY = 50f, bottomLeftY = 50f,
)

private val HeroSize = 244.dp
private val CompactHeroSize = 128.dp
private val CompactPieceAreaHeight = 170.dp
private val GlyphSize = 68.dp
private val CompactGlyphSize = 40.dp
private val TextGap = 8.dp
private val CompactTextGap = 4.dp
private const val HiddenTileScale = 0.6f
private const val GlyphFadeMillis = 250
private const val LetterShare = 0.4f
private const val LetterLineShare = 0.5f
private val PercentSize = 32.sp
private val PercentLine = 36.sp
private val CompactPercentSize = 19.sp
private val CompactPercentLine = 24.sp
private val CounterSize = 13.sp
private val CounterLine = 16.sp
private val CompactCounterSize = 10.sp
private val CompactCounterLine = 13.sp
private val CounterGap = 4.dp

/** Exclamation geometry as shares of the glyph box, on a 24 unit grid. */
private const val BarLeft = 11f / 24f
private const val BarTop = 5f / 24f
private const val BarWidth = 2f / 24f
private const val BarHeight = 9f / 24f
private const val DotCenterY = 17f / 24f
private const val DotRadius = 0.06f

/** The accent glow behind the tile: its opacity at the centre, how far it reaches and how far toward the lamp it sits. */
private const val GlowAlpha = 0.34f
private const val GlowRadiusShare = 0.78f
private const val GlowOffsetShare = 0.12f

/** The bloom of a finished run: its opacity at full swell, its radius, and the radius it starts from. */
private const val BloomAlpha = 0.5f
private const val BloomRadiusShare = 0.8f
private const val BloomStartShare = 0.6f

/** The sheen crosses a finished tile from left of it to right of it, as shares of the width, and its band is this long. */
private const val SweepAlpha = 0.5f
private const val SweepStartShare = -0.6f
private const val SweepEndShare = 1.6f
private const val SweepReachShare = 0.35f
private const val DiagonalShare = 0.7071f
