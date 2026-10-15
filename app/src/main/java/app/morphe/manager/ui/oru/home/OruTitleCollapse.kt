/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.util.lerp

/*
 * The large title of Home collapses into the top bar as the list scrolls, the way an iOS large title does. One progress,
 * read from the scroll, drives all of it: the title travels to the bar and shrinks, the subtitle fades out over the first
 * half, and the bar's glass comes in over the second half. The progress is a function of the scroll and nothing runs on a
 * clock, so the title follows the finger, scrubs, and reverses when the list comes back up.
 */

/**
 * How far the list has collapsed the title: 0 at rest, 1 once the title sits in the bar. The scroll is taken as a fraction
 * of [collapsePx] (the scroll at which the title reaches the bar) and eased with [oruSmoothstep], so the travel is
 * weighted at both ends. Once the first item is off screen the title is in the bar, whatever the offset.
 */
internal fun oruTitleProgress(firstVisibleItem: Int, scrollOffsetPx: Int, collapsePx: Float): Float {
    if (firstVisibleItem > 0) return 1f
    val linear = when {
        collapsePx > 0f -> scrollOffsetPx / collapsePx
        scrollOffsetPx > 0 -> 1f
        else -> 0f
    }
    return oruSmoothstep(linear)
}

/** The smoothstep of [fraction], clamped to 0..1: 0 at 0, 1 at 1, and a flat slope at both ends. */
internal fun oruSmoothstep(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The part of [progress] between [from] and [to], clamped to 0..1: 0 up to [from], 1 from [to] on. It stages the parts of
 * the collapse, such as the subtitle fading out over the first half. [to] must be above [from].
 */
internal fun oruTitlePhase(progress: Float, from: Float, to: Float): Float =
    ((progress - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The scroll at which the title's top meets the bar title's top: the title's top at rest (unscrolled) minus the bar title's
 * top. When that is not a positive distance, because a position is not measured yet, [fallbackPx] stands in.
 */
internal fun oruTitleCollapseDistance(restTopPx: Float, barTitleTopPx: Float, fallbackPx: Float): Float {
    val distance = restTopPx - barTitleTopPx
    return if (distance > 0f) distance else fallbackPx
}

/** Where the travelling title is and how big it is: its top left, and a scale about that top left. */
internal data class OruTitleTransform(val x: Float, val y: Float, val scale: Float)

/**
 * The scale that takes the wordmark from its size in the header ([headerScale]) to its size in the bar ([barScale]). Both
 * are sizes of the same mark (see OruWordmark), so the ratio is exact: a 1.8 header wordmark travels to a 1/1.8 copy.
 */
internal fun oruWordmarkTravelScale(headerScale: Float, barScale: Float): Float = barScale / headerScale

/**
 * The travelling wordmark at [progress]: from its top left at rest ([fromX], [fromY]) to the bar wordmark's top left ([toX],
 * [toY]), and from scale 1 to [toScale]. At 0 it is where it rests, at 1 it sits in the bar at the bar's size. [progress]
 * is clamped to 0..1.
 */
internal fun oruTitleTransform(
    progress: Float,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    toScale: Float,
): OruTitleTransform {
    val p = progress.coerceIn(0f, 1f)
    return OruTitleTransform(
        x = lerp(fromX, toX, p),
        y = lerp(fromY, toY, p),
        scale = lerp(1f, toScale, p),
    )
}

/**
 * Where the title and the bar title are on the screen, in root coordinates, and the scroll that joins them. Positions are
 * plain fields, written in the layout pass and read in the draw pass, so a scroll does not recompose. [collapsePx] is a
 * state, because the subtitle and the glass depend on the progress it sets. Until a position is measured it is
 * Unspecified, and the collapse distance is [fallbackPx].
 */
internal class OruTitleAnchor(private val fallbackPx: Float) {
    /** The in-list title's top left, at the scroll it was last measured. */
    var header: Offset = Offset.Unspecified
        private set

    /** The bar title's top left. */
    var bar: Offset = Offset.Unspecified
        private set

    /** The top left of the layer the travelling title is drawn in. */
    var overlay: Offset = Offset.Unspecified
        private set

    /** The in-list title's top, unscrolled: the header's own top plus how far the list has scrolled past it. */
    private var headerRestTop = Float.NaN

    private val collapse = mutableFloatStateOf(fallbackPx)

    /** The scroll at which the title reaches the bar. */
    val collapsePx: Float get() = collapse.floatValue

    /** Whether the title, the bar title and the overlay are all measured, so the copy can travel. */
    val ready: Boolean get() = header.isSpecified && bar.isSpecified && overlay.isSpecified

    /**
     * Records the in-list title at [position], measured while the list was at [firstVisibleItem] and [scrollOffsetPx]. The
     * title is the first item, so while it is on screen its offset is how far the list has scrolled.
     */
    fun onHeaderMeasured(position: Offset, firstVisibleItem: Int, scrollOffsetPx: Int) {
        header = position
        headerRestTop = if (firstVisibleItem == 0) position.y + scrollOffsetPx else position.y
        updateCollapse()
    }

    fun onBarMeasured(position: Offset) {
        bar = position
        updateCollapse()
    }

    fun onOverlayMeasured(position: Offset) {
        overlay = position
    }

    /**
     * The travelling title at [progress], in the overlay's coordinates. It stays where the list has put the in-list title
     * until the progress moves it, so the copy and the list agree at every scroll.
     */
    fun travel(progress: Float, toScale: Float): OruTitleTransform {
        if (!ready) return OruTitleTransform(x = 0f, y = 0f, scale = 1f)
        return oruTitleTransform(
            progress = progress,
            fromX = header.x - overlay.x,
            fromY = header.y - overlay.y,
            toX = bar.x - overlay.x,
            toY = bar.y - overlay.y,
            toScale = toScale,
        )
    }

    private fun updateCollapse() {
        // An Unspecified bar has a NaN top, so the distance is not positive and the fallback stands in
        val distance = oruTitleCollapseDistance(headerRestTop, bar.y, fallbackPx)
        if (distance != collapse.floatValue) collapse.floatValue = distance
    }
}
