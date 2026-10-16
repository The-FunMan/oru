/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruMotion
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The arithmetic of the motion building blocks (OruMorphText, OruOdometer), kept free of Compose and of the
 * platform so it runs on the JVM. Every value is a pure function of its arguments: a glyph drifts the same way on every
 * recomposition, and a digit always rolls the same way for the same change.
 */

/**
 * The delay of glyph [index] in a morph, in milliseconds: [OruMotion.GlyphStaggerMillis] per glyph, stopped at
 * [OruMotion.GlyphStaggerCapMillis]. A negative index counts as the first glyph.
 */
internal fun oruGlyphDelayMillis(index: Int): Int =
    (index.coerceAtLeast(0) * OruMotion.GlyphStaggerMillis).coerceAtMost(OruMotion.GlyphStaggerCapMillis)

/**
 * Where a glyph goes when it is in the cloud, at full cloud: [dxDp] and [dyDp] in dp, a [rotationDegrees] turn and the
 * [scale] it shrinks to. A glyph at rest has none of these; the morph scales each one by the cloud amount.
 */
internal data class OruGlyphDrift(
    val dxDp: Float,
    val dyDp: Float,
    val rotationDegrees: Float,
    val scale: Float,
)

/** The drift of a glyph is between these distances from its place, in dp. */
private const val MinDriftDp = 2f
private const val MaxDriftDp = 6f

/** The turn of a glyph is at most this many degrees either way. */
private const val MaxTurnDegrees = 6f

/** A glyph in the cloud shrinks to at least `1 - MaxShrink`, so 0.9. */
private const val MaxShrink = 0.1f

private const val TwoPi = 6.2831855f

/**
 * The drift of glyph [index] for the morph [seed]. The same pair always gives the same drift, so a glyph does not
 * jump when the text recomposes. The direction, distance, turn and shrink are drawn from a hash of the two numbers.
 */
internal fun oruGlyphDrift(seed: Int, index: Int): OruGlyphDrift {
    val angle = unitHash(seed, index, 0) * TwoPi
    val reach = MinDriftDp + (MaxDriftDp - MinDriftDp) * unitHash(seed, index, 1)
    val turn = (unitHash(seed, index, 2) * 2f - 1f) * MaxTurnDegrees
    val scale = 1f - MaxShrink * unitHash(seed, index, 3)
    return OruGlyphDrift(
        dxDp = cos(angle) * reach,
        dyDp = sin(angle) * reach,
        rotationDegrees = turn,
        scale = scale,
    )
}

/** A hash of [seed], [index] and [salt] in the range 0 until 1. Integer mixing, the last step of murmur3's finalizer. */
private fun unitHash(seed: Int, index: Int, salt: Int): Float {
    var h = seed * HashPrimeSeed + index * HashPrimeIndex + salt * HashPrimeSalt
    h = h xor (h ushr 16)
    h *= HashMixA
    h = h xor (h ushr 15)
    h *= HashMixB
    h = h xor (h ushr 16)
    // The top 24 bits, so the result is exact in a Float
    return (h ushr 8).toFloat() / TwentyFourBits
}

private const val HashPrimeSeed = 0x27d4eb2d
private const val HashPrimeIndex = 0x165667b1
private const val HashPrimeSalt = 0x2c1b3c6d
private const val HashMixA = 0x7feb352d
// 0x846ca68b as a signed Int
private const val HashMixB = -2073254261
private const val TwentyFourBits = 16777216f

/**
 * The decimal digits of [value], most significant first. A negative value shows as zero, since an odometer counts
 * up from zero; zero itself is one digit.
 */
internal fun oruOdometerDigits(value: Int): List<Int> =
    value.coerceAtLeast(0).toString().map { it - '0' }

/** How many digit places a count needs: the slot of a rolling count holds this many digits to fit the count. */
internal fun oruDigitCount(value: Int): Int = oruOdometerDigits(value).size

/**
 * Where a digit that rolls to [digit] comes to rest, measured on the unbounded strip of digits that a roll runs along.
 * [position] is where the roll is now, in digits (a roll in progress has a fraction). The roll takes the shorter way
 * round the ring of ten digits, so 9 to 0 is one step up and 0 to 9 is one step down. A step of exactly five goes up.
 */
internal fun oruRollTarget(position: Float, digit: Int): Float {
    val base = position.roundToInt()
    val current = Math.floorMod(base, RingSize)
    var step = Math.floorMod(digit - current, RingSize)
    if (step > HalfRing) step -= RingSize
    return (base + step).toFloat()
}

private const val RingSize = 10
private const val HalfRing = 5
