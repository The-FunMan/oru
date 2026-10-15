/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import kotlin.math.max
import kotlin.math.min

/*
 * Pure rules of the Oru components. No Compose or Android types here, so each rule can be unit tested
 * on the JVM.
 */

/** Which container role a tone draws from. Warn is the warning container, never the error one. */
internal enum class OruContainerRole { Neutral, Ok, Warn, Error }

internal fun oruContainerRole(tone: OruTone): OruContainerRole = when (tone) {
    OruTone.Neutral -> OruContainerRole.Neutral
    OruTone.Ok -> OruContainerRole.Ok
    OruTone.Warn -> OruContainerRole.Warn
    OruTone.Error -> OruContainerRole.Error
}

/**
 * Share of the wave height, from 0 (flat) to 1 (full). The wave grows over the first sixth of a run,
 * lies flat at rest and when complete, and is flat when motion is reduced.
 */
internal fun oruWaveAmplitude(progress: Float, reduced: Boolean): Float {
    if (reduced || progress.isNaN() || progress <= 0f || progress >= 1f) return 0f
    return (progress * WaveRampPerUnitProgress).coerceAtMost(1f)
}

private const val WaveRampPerUnitProgress = 6f

/** Stage states for a rail of [count] stages while stage [currentIndex] runs. */
internal fun oruStageStates(count: Int, currentIndex: Int, failed: Boolean): List<OruStageState> =
    List(count.coerceAtLeast(0)) { index ->
        when {
            currentIndex >= count -> OruStageState.Done
            currentIndex < 0 -> OruStageState.Todo
            index < currentIndex -> OruStageState.Done
            index == currentIndex -> if (failed) OruStageState.Error else OruStageState.Now
            else -> OruStageState.Todo
        }
    }

/** Only the running stage pulses. */
internal fun oruStagePulses(state: OruStageState): Boolean = state == OruStageState.Now

/** A finished stage opens on tap, and only when it has lines to show. */
internal fun oruStageCanToggle(state: OruStageState, hasLines: Boolean): Boolean =
    state == OruStageState.Done && hasLines

/** The running stage shows its last few lines. Other stages show all of theirs. */
internal fun oruVisibleStageLines(lines: List<OruStageLine>, state: OruStageState): List<OruStageLine> =
    if (state == OruStageState.Now) lines.takeLast(RunningStageLineCount) else lines

private const val RunningStageLineCount = 3

/** Kind of trailing control, so the row can pick its semantics. */
internal enum class OruRowControlKind { Switch, Value, Chevron, None }

internal fun oruRowControlKind(control: OruRowControl): OruRowControlKind = when (control) {
    is OruRowControl.Switch -> OruRowControlKind.Switch
    is OruRowControl.Value -> OruRowControlKind.Value
    is OruRowControl.Chevron -> OruRowControlKind.Chevron
    is OruRowControl.None -> OruRowControlKind.None
}

/** The text a value control shows, or null for every other control. */
internal fun oruRowControlValue(control: OruRowControl): String? =
    (control as? OruRowControl.Value)?.text

/** The glyph the finished tile shows: the tile, the check or the exclamation. */
internal enum class OruHeroGlyph { Tile, Done, Failed }

/** The glyph of a state. A working run has none, because its hexagon carries the counter. */
internal fun oruHeroGlyph(state: OruHeroState): OruHeroGlyph? = when (state) {
    OruHeroState.Ready -> OruHeroGlyph.Tile
    OruHeroState.Working -> null
    OruHeroState.Done -> OruHeroGlyph.Done
    OruHeroState.Failed -> OruHeroGlyph.Failed
}

/** Every state except Ready morphs the tile into a circle. */
internal fun oruHeroIsCircle(state: OruHeroState): Boolean = state != OruHeroState.Ready

/**
 * What the hero draws in its centre: a tile, or the hexagon of patch pieces. A working run always draws the hexagon,
 * which waits with its sweep until patches apply. A finished patch run keeps its pieces, all placed, with the check
 * over them. A failed patch run draws its pieces with the failing one outside.
 */
internal enum class OruHeroVisual { Tile, Hexagon }

internal fun oruHeroVisual(
    state: OruHeroState,
    patchTotal: Int,
    failedPatchIndex: Int?,
): OruHeroVisual {
    val patchRun = patchTotal >= 1
    return when {
        state == OruHeroState.Working -> OruHeroVisual.Hexagon
        state == OruHeroState.Done && patchRun -> OruHeroVisual.Hexagon
        state == OruHeroState.Failed && patchRun && failedPatchIndex != null -> OruHeroVisual.Hexagon
        else -> OruHeroVisual.Tile
    }
}

/**
 * Whether the hexagon is still preparing: the run is working and no patch is applying yet. A preparing hexagon shows
 * its waiting sweep, which gives way to the real fill once patches apply.
 */
internal fun oruHeroPreparing(state: OruHeroState, applyingPatches: Boolean, patchTotal: Int): Boolean =
    state == OruHeroState.Working && !(applyingPatches && patchTotal >= 1)

/** Milliseconds a patch piece takes to fly in, and the gaps between starts, as the frame clock counts them. */
private val RevealFlightMs: Long = OruMotion.PieceFlightMillis.toLong()
private val RevealGapMs: Long = OruMotion.PieceGapMillis.toLong()
private val RevealMinGapMs: Long = OruMotion.PieceMinGapMillis.toLong()
private val RevealCatchUpMs: Long = OruMotion.PieceCatchUpMillis.toLong()

/**
 * The gap between two piece starts. While the run goes on it is [OruMotion.PieceGapMillis]. Once the run is complete
 * or failed, the pieces still to show play out as a cascade: the gap spreads the [count] pieces over the catch-up time
 * less one flight, never wider than the normal gap and never below [OruMotion.PieceMinGapMillis].
 */
internal fun oruRevealGapMs(count: Int, ended: Boolean): Long {
    if (!ended) return RevealGapMs
    val share = (RevealCatchUpMs - RevealFlightMs) / count.coerceAtLeast(1)
    return share.coerceIn(RevealMinGapMs, RevealGapMs)
}

/**
 * The displayed count never passes the real count. A real count below the displayed one means the run restarted, so
 * the picture drops to the real count at once.
 */
internal fun oruRevealClamp(shown: Int, completed: Int): Int = min(shown, completed)

/**
 * The frame times at which patches begin to fly in, for a run with [shown] patches begun and [completed] done. The
 * next start is due one [gapMs] after the last, but never earlier than one gap before [nowMs], so a long pause does
 * not release a burst. A run with nothing begun starts its first patch at once. Every start is at or before [nowMs],
 * and the list is in run order, so one frame can begin several patches at their exact times.
 */
internal fun oruRevealStarts(shown: Int, completed: Int, lastStartMs: Long?, nowMs: Long, gapMs: Long): List<Long> {
    val starts = mutableListOf<Long>()
    var next = if (lastStartMs == null) nowMs else max(lastStartMs + gapMs, nowMs - gapMs)
    var count = shown
    while (count < completed && next <= nowMs) {
        starts += next
        count++
        next += gapMs
    }
    return starts
}

/** Whether the latest start has landed by [nowMs]. With nothing begun there is nothing in flight. */
internal fun oruRevealLanded(lastStartMs: Long?, nowMs: Long): Boolean =
    lastStartMs == null || nowMs - lastStartMs >= RevealFlightMs

/**
 * Travel still to go for a patch that began [elapsedMs] ago: 1 as it starts, 0 once it has landed, eased out so it
 * slows as it arrives. A patch that has not begun is at full travel too, so it is never drawn in place.
 */
internal fun oruFlightTravel(elapsedMs: Long): Float {
    if (elapsedMs <= 0L) return 1f
    if (elapsedMs >= RevealFlightMs) return 0f
    return 1f - oruEaseOutCubic(elapsedMs.toFloat() / RevealFlightMs)
}

/** Cubic ease out, clamped to 0 to 1. */
internal fun oruEaseOutCubic(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return 1f - (1f - clamped) * (1f - clamped) * (1f - clamped)
}

/** Tile edge in dp. The ready tile is the largest, the running tiles are a little larger than compact. */
internal fun oruHeroTileSizeDp(state: OruHeroState, compact: Boolean): Int = when {
    compact -> CompactTileDp
    state == OruHeroState.Ready -> ReadyTileDp
    else -> RunningTileDp
}

private const val ReadyTileDp = 132
private const val RunningTileDp = 152
private const val CompactTileDp = 84

/** Share of a bar, 0 to 1. A missing maximum draws no bar. */
internal fun oruBarFraction(value: Int, max: Int): Float {
    if (max <= 0) return 0f
    return (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

/** A bar above this share of its scale is drawn in the error colour. */
internal fun oruBarIsHot(fraction: Float): Boolean = fraction > HotBarShare

private const val HotBarShare = 0.88f

/** The scale of a graph: the known limit, else the largest sample, never below 1. */
internal fun oruGraphScale(limit: Int?, samples: List<Int>): Int {
    val known = limit?.takeIf { it > 0 }
    return known ?: samples.maxOrNull()?.coerceAtLeast(1) ?: 1
}

/** A missing figure reads as a dash, never as zero. */
internal fun oruFigure(value: Int?): String = value?.toString() ?: MissingFigure

private const val MissingFigure = "-"

/** Share of the fill alpha. A frosted layer is translucent, a solid one is opaque. */
internal fun oruFillAlpha(fill: OruSurfaceFill, frostedAlpha: Float): Float = when (fill) {
    OruSurfaceFill.Frosted -> frostedAlpha
    OruSurfaceFill.Solid -> 1f
}

/** Letter shown on a tile without an icon: the first letter of the name, upper case. */
internal fun oruTileLetter(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: FallbackTileLetter

private const val FallbackTileLetter = "?"
