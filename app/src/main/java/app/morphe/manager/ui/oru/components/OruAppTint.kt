/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The colour of an app, read from its launcher icon. The pixel rules are pure ARGB math so they run on the JVM;
 * the composables at the end only fetch the icon and map the result onto the theme.
 *
 * The accent of an icon is its dominant vivid hue. An icon with no vivid hue gets a muted colour of its average hue,
 * and an icon with no hue at all (a black or grey mark) gets the Oru primary. Every other colour of an app is the
 * accent mixed over the theme's surfaces. The Focus of the app provides the whole palette (LocalOruAppTint), so
 * everything drawn in that context takes the app's light.
 */

/** Side of the square an icon is drawn into before its pixels are read. Small, so the hue vote stays cheap. */
internal const val OruTintSamplePixels = 24

/** Hue buckets of [oruDominantAccent], 20 degrees each. */
private const val HueBuckets = 18

/** Pixels with less alpha than this are see-through and do not vote. */
private const val MinOpaqueAlpha = 128

/** Pixels whose brightest channel is below this are near-black and do not vote. */
private const val MinBrightest = 40

/** Pixels whose channels spread less than this are near-grey or near-white and do not vote. */
private const val MinChroma = 40

/** The winning hue must weigh at least this share of the pixels, so a speck of colour does not decide. */
private const val MinColorfulShare = 0.04f

/** Pixels whose channels spread less than this have no hue worth reading for the muted fallback. */
private const val MutedMinChroma = 12

/** The muted fallback needs at least this share of the pixels to carry a hue, so a few tinted specks do not decide. */
private const val MutedMinShare = 0.1f

/** How far the hues of the carrying pixels must agree (0 opposite, 1 one hue) for their average to be the icon's hue. */
private const val MutedMinCoherence = 0.6

/** The lift of a faint hue: at least this saturation and brightness, so it reads as a colour on the surfaces. */
private const val MutedMinSaturation = 0.35f
private const val MutedMinValue = 0.6f

/** WCAG AA for body text. */
internal const val OruTextMinContrast = 4.5

/** Steps from the accent to black or white that the text colour search tries. */
private const val TextSearchSteps = 16

/** Accent share of the card background, in light and dark. */
private const val ContainerShareLight = 0.12f
private const val ContainerShareDark = 0.18f

/** Accent share of a neutral chip over the highest container. */
private const val ChipShare = 0.14f

/** Accent share of the tile blob over the highest container. Lower in dark, so white letters keep 4.5:1. */
private const val TileShareLight = 0.30f
private const val TileShareDark = 0.28f

/**
 * Accent share of a solid control fill over the highest container. Strong, so the control reads as the app's colour,
 * and lower in dark, where the fill sits on a dark surface.
 */
private const val FillShareLight = 0.9f
private const val FillShareDark = 0.8f

/** Relative luminance where black and white read equally well on a fill, so a darker fill takes white text. */
private const val FillLightLuminance = 0.18

/** Surfaces darker than this relative luminance are the dark theme. */
private const val DarkSurfaceLuminance = 0.2

private val BlackArgb = 0xFF000000.toInt()
private val WhiteArgb = 0xFFFFFFFF.toInt()

/**
 * The dominant colour of [pixels] (ARGB, row by row), or null when no colourful pixels are there.
 *
 * Each opaque pixel that is neither near-black nor near-grey votes for its hue bucket with its saturation,
 * so a vivid pixel counts for more than a dull one of the same hue and area still counts. The winning
 * bucket returns the saturation-weighted average of its pixels.
 */
internal fun oruDominantAccent(pixels: IntArray): Int? {
    val weight = FloatArray(HueBuckets)
    val red = FloatArray(HueBuckets)
    val green = FloatArray(HueBuckets)
    val blue = FloatArray(HueBuckets)
    for (argb in pixels) {
        if ((argb ushr 24) < MinOpaqueAlpha) continue
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val brightest = maxOf(r, g, b)
        val chroma = brightest - minOf(r, g, b)
        if (brightest < MinBrightest || chroma < MinChroma) continue
        val saturation = chroma.toFloat() / brightest
        val bucket = hueBucket(r, g, b, brightest, chroma)
        weight[bucket] += saturation
        red[bucket] += r * saturation
        green[bucket] += g * saturation
        blue[bucket] += b * saturation
    }
    var best = 0
    for (index in 1 until HueBuckets) {
        if (weight[index] > weight[best]) best = index
    }
    val total = weight[best]
    if (total <= 0f || total < MinColorfulShare * pixels.size) return null
    return opaqueArgb(red[best] / total, green[best] / total, blue[best] / total)
}

/**
 * The muted colour of an icon that has no vivid hue, or null when it has no hue to read: greys, black and white, a
 * black mark. The hue is the average hue of the pixels that carry one, weighted by their chroma. The colour keeps that
 * hue, lifted to a minimum saturation and brightness.
 */
internal fun oruMutedAccent(pixels: IntArray): Int? {
    var along = 0.0
    var across = 0.0
    var weight = 0.0
    var saturation = 0f
    var value = 0f
    var carriers = 0
    for (argb in pixels) {
        if ((argb ushr 24) < MinOpaqueAlpha) continue
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val brightest = maxOf(r, g, b)
        val chroma = brightest - minOf(r, g, b)
        if (chroma < MutedMinChroma) continue
        val radians = Math.toRadians(hueDegrees(r, g, b, brightest, chroma).toDouble())
        along += cos(radians) * chroma
        across += sin(radians) * chroma
        weight += chroma
        saturation += chroma.toFloat() / brightest
        value += brightest / 255f
        carriers++
    }
    if (carriers == 0 || carriers < MutedMinShare * pixels.size) return null
    // Hues that cancel out, a red and a cyan of equal weight, have no average hue
    if (hypot(along, across) < MutedMinCoherence * weight) return null
    val hue = Math.toDegrees(atan2(across, along)).toFloat().mod(360f)
    return oruHsvArgb(
        hue = hue,
        saturation = maxOf(saturation / carriers, MutedMinSaturation),
        value = maxOf(value / carriers, MutedMinValue),
    )
}

/** Hue of a colour in degrees, from 0 until 360 (the standard HSV formula). [chroma] is never zero here. */
private fun hueDegrees(r: Int, g: Int, b: Int, brightest: Int, chroma: Int): Float {
    // Hue in sextants (0 until 6), the standard HSV formula
    val sextant = when (brightest) {
        r -> (g - b).toFloat() / chroma
        g -> (b - r).toFloat() / chroma + 2f
        else -> (r - g).toFloat() / chroma + 4f
    }
    val wrapped = if (sextant < 0f) sextant + 6f else sextant
    return wrapped * 60f
}

/** Hue of a colour cut into [HueBuckets] equal buckets of the colour wheel. */
private fun hueBucket(r: Int, g: Int, b: Int, brightest: Int, chroma: Int): Int =
    (hueDegrees(r, g, b, brightest, chroma) / (360f / HueBuckets)).toInt().coerceIn(0, HueBuckets - 1)

/** The ARGB colour of [hue] in degrees with [saturation] and [value], both from 0 to 1. */
internal fun oruHsvArgb(hue: Float, saturation: Float, value: Float): Int {
    val chroma = value * saturation
    val sector = hue.mod(360f) / 60f
    val second = chroma * (1f - abs(sector % 2f - 1f))
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val match = value - chroma
    return opaqueArgb((r + match) * 255f, (g + match) * 255f, (b + match) * 255f)
}

/** Relative luminance of an ARGB colour, as WCAG defines it. Alpha is ignored. */
internal fun oruRelativeLuminance(argb: Int): Double {
    fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear((argb shr 16) and 0xFF) +
        0.7152 * linear((argb shr 8) and 0xFF) +
        0.0722 * linear(argb and 0xFF)
}

/** WCAG contrast ratio of two opaque ARGB colours, from 1 to 21. */
internal fun oruContrast(a: Int, b: Int): Double {
    val la = oruRelativeLuminance(a)
    val lb = oruRelativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** [over] laid on [base] at [share], opaque. The arithmetic of CSS `color-mix` in sRGB. */
internal fun oruMix(over: Int, base: Int, share: Float): Int {
    fun channel(shift: Int): Int {
        val top = (over shr shift) and 0xFF
        val bottom = (base shr shift) and 0xFF
        return (bottom + (top - bottom) * share).roundToInt().coerceIn(0, 255)
    }
    return opaqueArgb(channel(16).toFloat(), channel(8).toFloat(), channel(0).toFloat())
}

/**
 * The text colour for [accent] on every one of [backgrounds]: the accent itself when it is readable, else
 * the nearest colour on the way to white ([towardWhite]) or to black that is. Black or white always reach 4.5:1
 * on an opaque background whose lightness is on their side of the split, so the search does not fail for the tints
 * of this palette.
 */
internal fun oruReadableOn(accent: Int, backgrounds: List<Int>, towardWhite: Boolean): Int {
    val target = if (towardWhite) WhiteArgb else BlackArgb
    for (step in 0..TextSearchSteps) {
        val candidate = oruMix(over = target, base = accent, share = step / TextSearchSteps.toFloat())
        if (backgrounds.all { oruContrast(candidate, it) >= OruTextMinContrast }) return candidate
    }
    return target
}

/**
 * The text colour on a solid [fill]: the nearest colour on the way to black or to white that reads on it. The side is
 * the fill's lightness, so a dark fill takes light text and a light fill dark text.
 */
internal fun oruFillText(fill: Int): Int =
    oruReadableOn(fill, listOf(fill), towardWhite = oruRelativeLuminance(fill) < FillLightLuminance)

/** The colours of one app for one theme, as ARGB. */
internal class OruTintPalette(
    val accent: Int,
    val container: Int,
    val chip: Int,
    val tile: Int,
    val fill: Int,
    val ink: Int,
    val onContainer: Int,
    val onChip: Int,
    val onFill: Int,
)

/**
 * The palette of [accent] over the theme's container surfaces. [surfaceContainer] and [surfaceHighest] are the surfaces
 * the colours sit on; the highest container is the lightest in dark and the darkest in light, so text is checked on it.
 */
internal fun oruTintPalette(
    accent: Int,
    surfaceContainer: Int,
    surfaceHighest: Int,
    darkTheme: Boolean,
): OruTintPalette {
    val container = oruMix(accent, surfaceContainer, if (darkTheme) ContainerShareDark else ContainerShareLight)
    val chip = oruMix(accent, surfaceHighest, ChipShare)
    val tile = oruMix(accent, surfaceHighest, if (darkTheme) TileShareDark else TileShareLight)
    val fill = oruMix(accent, surfaceHighest, if (darkTheme) FillShareDark else FillShareLight)
    return OruTintPalette(
        accent = accent,
        container = container,
        chip = chip,
        tile = tile,
        fill = fill,
        ink = oruReadableOn(accent, listOf(surfaceContainer, surfaceHighest), towardWhite = darkTheme),
        onContainer = oruReadableOn(accent, listOf(container, tile), towardWhite = darkTheme),
        onChip = oruReadableOn(accent, listOf(chip), towardWhite = darkTheme),
        onFill = oruFillText(fill),
    )
}

private fun opaqueArgb(red: Float, green: Float, blue: Float): Int =
    (0xFF shl 24) or
        (red.roundToInt().coerceIn(0, 255) shl 16) or
        (green.roundToInt().coerceIn(0, 255) shl 8) or
        blue.roundToInt().coerceIn(0, 255)

/** The colours one app draws with, in the active theme. */
@Immutable
data class OruAppTint(
    /** The app's own colour, or the Oru primary when its icon has no hue. Drawn at low alpha as a glow or outline. */
    val accent: Color,
    /** The card background: the accent over the container. */
    val container: Color,
    /** The neutral chip background: the accent over the highest container. */
    val chip: Color,
    /** The app tile blob: the accent over the highest container, stronger than the card so the blob reads. */
    val tile: Color,
    /** A solid control fill in the app's Focus: the accent, strong over the highest container. */
    val fill: Color,
    /** The accent as text or a mark on the surfaces, at least 4.5:1 on the container and the highest container. */
    val ink: Color,
    /** Text and letters on [container] and [tile], at least 4.5:1 on both. */
    val onContainer: Color,
    /** Text on [chip], at least 4.5:1. */
    val onChip: Color,
    /** Text and marks on [fill], at least 4.5:1. */
    val onFill: Color,
)

/**
 * The palette of the app whose Focus is open, provided by the Focus route. Null everywhere else, where the Oru colours
 * apply. A control reads it as `LocalOruAppTint.current?.fill ?: <Oru colour>`, so only the app's own context takes
 * its light, and Home chrome and the settings keep Oru's own colours.
 */
val LocalOruAppTint = compositionLocalOf<OruAppTint?> { null }

/** The solid fill of a selected control: the app's fill in its Focus, else the Oru primary. */
@Composable
internal fun oruSelectedFill(): Color = LocalOruAppTint.current?.fill ?: MaterialTheme.colorScheme.primary

/** The mark on a selected control: the app's text colour on its fill in the Focus, else the Oru colour on the primary. */
@Composable
internal fun oruSelectedContent(): Color = LocalOruAppTint.current?.onFill ?: MaterialTheme.colorScheme.onPrimary

/** The accent as a mark or text on the surfaces: the app's ink in its Focus, else the Oru primary. */
@Composable
internal fun oruInk(): Color = LocalOruAppTint.current?.ink ?: MaterialTheme.colorScheme.primary

/** Process-wide accent per package, so a card that scrolls away and back does not decode its icon again. */
private class CachedAccent(val updateTime: Long, val accent: Int?)

private val tintCache = ConcurrentHashMap<String, CachedAccent>()

private fun cachedAccent(packageName: String, updateTime: Long): Int? =
    tintCache[packageName]?.takeIf { it.updateTime == updateTime }?.accent

/**
 * The tint of the app described by [packageInfo]. Null, such as a letter tile, gives the Oru primary.
 *
 * The accent is read from the launcher icon on first use and cached per package name and last update time, icons
 * without a hue included. The palette is derived again only when the accent or the surfaces change.
 */
@Composable
fun rememberOruAppTint(packageInfo: PackageInfo?): OruAppTint {
    val context = LocalContext.current
    val packageName = packageInfo?.packageName
    val updateTime = packageInfo?.lastUpdateTime ?: 0L
    val cached = remember(packageName, updateTime) { packageName?.let { cachedAccent(it, updateTime) } }
    val accent by produceState<Int?>(cached, packageName, updateTime) {
        val info = packageInfo
        if (info == null) {
            value = null
            return@produceState
        }
        val hit = tintCache[info.packageName]?.takeIf { it.updateTime == updateTime }
        value = if (hit != null) {
            hit.accent
        } else {
            withContext(Dispatchers.IO) {
                val found = oruIconAccent(context, info)
                tintCache[info.packageName] = CachedAccent(updateTime, found)
                found
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    return remember(accent, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHighest) {
        oruAppTintOf(accent, scheme)
    }
}

private fun oruAppTintOf(accent: Int?, scheme: ColorScheme): OruAppTint {
    val darkTheme = oruRelativeLuminance(scheme.surface.toArgb()) < DarkSurfaceLuminance
    val palette = oruTintPalette(
        accent = accent ?: scheme.primary.toArgb(),
        surfaceContainer = scheme.surfaceContainer.toArgb(),
        surfaceHighest = scheme.surfaceContainerHighest.toArgb(),
        darkTheme = darkTheme,
    )
    return OruAppTint(
        accent = Color(palette.accent),
        container = Color(palette.container),
        chip = Color(palette.chip),
        tile = Color(palette.tile),
        fill = Color(palette.fill),
        ink = Color(palette.ink),
        onContainer = Color(palette.onContainer),
        onChip = Color(palette.onChip),
        onFill = Color(palette.onFill),
    )
}

/**
 * The accent of an app's icon: its dominant hue, else its muted hue, else null (the Oru primary). Coil decodes the icon
 * the same way [app.morphe.manager.ui.screen.shared.AppIcon] shows it, so the request usually hits the image cache
 * already filled by the list.
 */
private suspend fun oruIconAccent(context: Context, packageInfo: PackageInfo): Int? {
    val request = ImageRequest.Builder(context)
        .data(packageInfo)
        .allowHardware(false)
        .build()
    val drawable = Coil.imageLoader(context).execute(request).drawable ?: return null
    val side = OruTintSamplePixels
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    return try {
        // A copy, so the bounds set here never reach the drawable Coil shares with the icon on screen
        val icon = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        icon.setBounds(0, 0, side, side)
        icon.draw(Canvas(bitmap))
        val pixels = IntArray(side * side)
        bitmap.getPixels(pixels, 0, side, 0, 0, side, side)
        oruDominantAccent(pixels) ?: oruMutedAccent(pixels)
    } finally {
        bitmap.recycle()
    }
}
