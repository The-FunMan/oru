/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import android.content.Context
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI

/**
 * Motion tokens. The prototype's cubic-bezier overshoot maps to springs; each token is a function
 * of the reduced-motion flag, which turns every token into an instant snap.
 *
 * Nothing runs longer than [MaxDurationMillis], except the patch hexagon's paced reveal and settle, timed by the
 * piece tokens below. Only the hexagon's waiting sweep, the running stage dot and the drift of the Home orbs loop
 * run on, and each of them stops when motion is reduced.
 */
object OruMotion {
    const val MaxDurationMillis = 600

    /** Fade that accompanies [expand], on top of the spring. */
    const val ExpandFadeMillis = 180

    /** Running stage dot: scales from 1 to [StageDotPeakScale] and back. */
    const val StageDotPulseMillis = 1400
    const val StageDotPeakScale = 1.18f

    /** Screen fade. Screens move by 1 / [ScreenSlideShare] of their height and are never scaled. */
    const val ScreenFadeMillis = 280
    const val ScreenSlideShare = 40

    /** Scrim behind a sheet: fades over this long. */
    const val ScrimFadeMillis = 320

    /** Text swap: the old line leaves and the new one arrives over this long. */
    const val SwapMillis = 300

    /** Old line leaves this far up, the new one arrives from this far below (dp). */
    const val SwapOutDp = -8f
    const val SwapInDp = 10f

    /** Where a finished hero's check or exclamation starts before it pops to full size. */
    const val PopInScale = 0.4f

    /** Glass parameters (blur, tint, rim) move to a new style over this long. */
    const val GlassMorphMillis = 400

    /** A chosen toggle segment pulses: scales up to [SelectPulsePeakScale] with a sheen, then settles over this long. */
    const val SelectPulseMillis = 460
    const val SelectPulsePeakScale = 1.06f

    /** One lap of the Home backdrop orbs. Slow, so they drift rather than move. */
    const val OrbDriftMillis = 26000

    /** The glow of a Focus eases from the card's tint to the app's accent over this long. */
    const val AccentMorphMillis = 480

    /** The sheen that crosses a finished hero, once. */
    const val SweepMillis = 440

    /** Colour of a ring (a focused or selected outline) changes over this long. */
    const val RingColorMillis = 300

    /**
     * The patch hexagon's pace. A piece flies in over [PieceFlightMillis], and the next patch begins at least
     * [PieceGapMillis] after the last. A finished or failed run plays its rest out in at most [PieceCatchUpMillis],
     * with no gap under [PieceMinGapMillis]. Once the last patch begins, the success settle takes [PieceSettleMillis],
     * and the check draws in over [PieceCheckMillis].
     */
    const val PieceFlightMillis = 650
    const val PieceGapMillis = 40
    const val PieceMinGapMillis = 12
    const val PieceCatchUpMillis = 3000
    const val PieceSettleMillis = 600
    const val PieceCheckMillis = 500

    /** The waiting sweep turns once per [SweepTurnMillis]. It fades over [PieceCrossfadeMillis] when patches begin. */
    const val SweepTurnMillis = 3000
    const val PieceCrossfadeMillis = 400

    /** Buttons and cards shrink to this scale while pressed. */
    const val PressScale = 0.97f

    /** A pressed surface also leans away from the finger by up to this many degrees, about the point that is pressed. */
    const val PressTiltDegrees = 2.5f

    /** While pressed, the lit edge and the highlight dim by this share, and the cast shadow tightens by this share. */
    const val PressHighlightDim = 0.45f
    const val PressShadowTighten = 0.45f

    /** Sheet rows rise in turn when a sheet opens: each waits [CascadeStaggerMillis] after the one above, up to the cap. */
    const val CascadeStaggerMillis = 40
    const val CascadeCapMillis = 320

    /** A cascading row starts [CascadeRiseDp] lower and [CascadeBlurDp] blurred (dp; the blur needs API 31). */
    const val CascadeRiseDp = 20f
    const val CascadeBlurDp = 4f

    /** Blur at the peak of the glyph cloud of OruMorphText, in dp. Needs API 31; below it the cloud is alpha and scale. */
    const val CloudBlurDp = 10f

    /** Blur of a digit while it rolls in OruOdometer, in dp. It fades as the digit comes to rest. */
    const val RollBlurDp = 4f

    /** Blur that fuses the two pills of OruGooPair while they are merged, in dp. */
    const val GooBlurDp = 8f

    /** Glyphs of a morph start this many milliseconds after their left neighbour. The whole wave stops growing at the cap. */
    const val GlyphStaggerMillis = 18
    const val GlyphStaggerCapMillis = 300

    /** Fade of text that cannot morph: reduced motion, or a label too long to split into glyphs. */
    const val CrossFadeMillis = 220

    /** Plain fade of a dialog or a card that has no other motion of its own. */
    const val FadeMillis = 240

    /** Card to focus shared bounds. A large move, so it takes the tile response, and it settles without overshoot. */
    fun <T> tileBounds(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = TileResponseSeconds, damping = TileBoundsDampingRatio)

    /** Blob corner radii on the card to focus to done morph. */
    fun <T> tileCorners(reduced: Boolean): AnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = TileResponseSeconds, damping = TileBoundsDampingRatio)

    /** Presses and taps: the snappy response, nearly flat. */
    fun <T> press(reduced: Boolean): AnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = SnappyResponseSeconds, damping = SnappyDampingRatio)

    /**
     * Expert rows, vitals graphs, stage lines, and the segment corners. Finite, so it also drives
     * expandVertically and the other size and offset transitions. A large layout move, so the glide response.
     */
    fun <T> expand(reduced: Boolean): FiniteAnimationSpec<T> = springGlide(reduced)

    /** The check or the exclamation of a finished hero, growing from [PopInScale] with a visible settle. */
    fun <T> pop(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = SoftResponseSeconds, damping = PopDampingRatio)

    /** Frosted sheet entering. Finite, so it drives slideInVertically. A large move, so the glide response. */
    fun <T> sheetRise(reduced: Boolean): FiniteAnimationSpec<T> = springGlide(reduced)

    /** Title and line changes. Alpha and a short rise, no blur. */
    fun <T> swapText(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = SwapMillis, easing = FastOutSlowInEasing)

    /** Scrim fade, a plain tween. */
    fun <T> scrimFade(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = ScrimFadeMillis)

    /** Glass style change: the blur, tint and rim of every glass surface move together. */
    fun <T> glassMorph(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = GlassMorphMillis, easing = FastOutSlowInEasing)

    /** The pulse of a toggle segment that was just chosen, from 1 back to 0. */
    fun <T> selectPulse(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = SelectPulseMillis, easing = FastOutSlowInEasing)

    /** Ring colour change. */
    fun <T> ringColor(reduced: Boolean): AnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = RingColorMillis)

    /** The Focus glow easing from the card's tint to the app's accent. */
    fun <T> accentMorph(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = AccentMorphMillis, easing = FastOutSlowInEasing)

    /** The sheen across a finished hero. */
    fun <T> lightSweep(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = SweepMillis, easing = FastOutSlowInEasing)

    /**
     * Quick and nearly flat: presses and small state changes. The snappy response, damping 0.80, so it stops
     * without a visible overshoot.
     */
    fun <T> springSnappy(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = SnappyResponseSeconds, damping = SnappyDampingRatio)

    /**
     * A visible overshoot that settles: labels that grow into a pill, glyphs that form out of the cloud. The soft
     * response with damping 0.70 gives a few percent of overshoot, and settles in about 0.35 s.
     */
    fun <T> springSoft(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = SoftResponseSeconds, damping = SoftDampingRatio)

    /** Settles with no visible overshoot: the digits of an odometer rolling into place. */
    fun <T> springSettle(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = SettleResponseSeconds, damping = SettleDampingRatio)

    /** Large layout moves: a panel, a sheet, an expanding section. Settles with almost no overshoot. */
    fun <T> springGlide(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else oruSpring<T>(response = GlideResponseSeconds, damping = GlideDampingRatio)

    /**
     * The fade of text that cannot morph. Kept in reduced motion too: a fade moves nothing, so it is the reduced
     * fallback itself.
     */
    fun <T> crossFade(): FiniteAnimationSpec<T> = tween<T>(durationMillis = CrossFadeMillis)

    /** The plain fade of a dialog or a card, for the fade that comes with a size or scale change. */
    fun <T> fade(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap<T>() else tween<T>(durationMillis = FadeMillis, easing = FastOutSlowInEasing)

    /**
     * Whether motion is reduced: animations are switched off in system settings, or battery saver
     * is on. Android has no separate "reduce motion" switch, so these two are the signal.
     */
    fun reduced(state: OruSystemState): Boolean =
        state.animatorDurationScale == 0f || state.powerSaveMode

    fun reduced(context: Context): Boolean = reduced(readOruSystemState(context))

    /**
     * Spring responses, in seconds: the time of one natural cycle of the spring. See [oruSpring]. Each one has a damping
     * ratio below, and [oruSpringSettleSeconds] gives the time it takes to settle.
     */
    internal const val SnappyResponseSeconds = 0.28f
    internal const val SoftResponseSeconds = 0.40f
    internal const val SettleResponseSeconds = 0.38f
    internal const val TileResponseSeconds = 0.52f
    internal const val GlideResponseSeconds = 0.46f

    /** Damping ratios that go with the responses above. 1 is no overshoot, below 1 a visible one. */
    internal const val SnappyDampingRatio = 0.80f
    internal const val SoftDampingRatio = 0.70f
    internal const val SettleDampingRatio = 0.90f
    internal const val GlideDampingRatio = 0.85f
    internal const val TileFlightDampingRatio = 0.74f
    internal const val TileBoundsDampingRatio = 0.85f

    /** The pop of a finished hero keeps a deeper overshoot than the soft spring. */
    internal const val PopDampingRatio = 0.68f
}

/**
 * Whether the Animations switch in Settings is on. MainActivity provides the preference once, so every call site
 * reads it without plumbing; the default holds in previews and tests.
 */
val LocalOruAnimationsEnabled = staticCompositionLocalOf { true }

/**
 * The reduced-motion rule of the oru UI: motion is reduced when the system asks for less, or when the user has
 * switched Animations off. Reduced motion snaps or fades in place, and it never moves anything.
 */
fun oruReducedMotion(systemReduced: Boolean, animationsEnabled: Boolean): Boolean =
    systemReduced || !animationsEnabled

/** Reduced motion, read live: the system signal combined with the Animations switch. */
@Composable
fun rememberOruReducedMotion(): Boolean = oruReducedMotion(
    systemReduced = OruMotion.reduced(rememberOruSystemState()),
    animationsEnabled = LocalOruAnimationsEnabled.current,
)

/**
 * The stiffness of a spring with mass 1 whose natural cycle takes [response] seconds: an undamped spring of stiffness k
 * has a period of 2 pi / sqrt(k), so k is (2 pi / response) squared.
 */
internal fun oruSpringStiffness(response: Float): Float {
    val angularFrequency = 2f * PI.toFloat() / response
    return angularFrequency * angularFrequency
}

/**
 * A spring defined by how long it takes ([response], seconds) and how much it is damped ([damping], 1 for none, below 1
 * for a visible overshoot), rather than by a raw stiffness.
 */
internal fun <T> oruSpring(response: Float, damping: Float): SpringSpec<T> =
    spring(dampingRatio = damping, stiffness = oruSpringStiffness(response))

/** A damped spring's swing falls inside 2 percent of its target after this many decay constants (ln 50, about 3.9). */
private const val SettleEnvelope = 3.9f

/**
 * How long a spring with this [response] (seconds) and [damping] takes to settle within 2 percent of its target. The
 * decay rate is damping times 2 pi over the response, so the time is 3.9 over that rate. The snappy token (response
 * 0.28, damping 0.80) settles in about 0.22 s.
 */
internal fun oruSpringSettleSeconds(response: Float, damping: Float): Float =
    SettleEnvelope / (damping * 2f * PI.toFloat() / response)

/*
 * Home to Focus choreography. Kept out of the object above, so its tokens stay as they were.
 */

/**
 * A tile or a title travelling from a card to Focus. A spring with a visible overshoot: it passes its place a little
 * and settles, so the shape reads as something with weight. Reduced motion snaps.
 */
fun <T> OruMotion.tileFlight(reduced: Boolean): FiniteAnimationSpec<T> =
    if (reduced) {
        snap<T>()
    } else {
        oruSpring<T>(response = OruMotion.TileResponseSeconds, damping = OruMotion.TileFlightDampingRatio)
    }
