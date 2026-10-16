/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.run.OruInstallShare
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.LocalOruStatusColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The hexagon of run pieces. The regular hexagon is six wedges around its centre. Each wedge is an equilateral
 * triangle cut into k by k congruent triangles, so the hexagon holds 6 k squared pieces. Coordinates are in units of
 * the hexagon radius, centred on the origin, with a point at the top, as in the prototype.
 *
 * The hexagon is the whole run: its fill is the run's fill (see oruRunFill), and the pieces light as the fill passes
 * them, from the bottom up (see [oruHexFillOrder]). The patch stage is cut one piece to a patch within its share of the
 * fill, see [oruHexRingFor].
 *
 * The reveal is paced by the frame clock: each piece flies in over [OruMotion.PieceFlightMillis], and the starts are
 * spaced by the rules in OruComponentLogic.kt, so a fast run still reads one piece at a time. Before any patch
 * applies the hexagon waits: a sweep lights the pieces round the wedges, and it fades into the fill once patches begin.
 */

/** A point in hexagon units. */
internal data class OruHexPoint(val x: Float, val y: Float)

/** One triangular piece of the hexagon, in hexagon units. */
internal data class OruHexTriangle(val a: OruHexPoint, val b: OruHexPoint, val c: OruHexPoint)

/** The hexagon has six wedges. */
internal const val OruHexWedges = 6

/**
 * Patches the hexagon is cut for while the real total is not known: four pieces to a wedge side, so 96 pieces. The
 * hexagon is cut again for the real count, see [oruHexRingFor], once it is known.
 */
internal const val OruHexPrepPatches = 96

/** Pieces in a hexagon cut into k by k triangles per wedge. */
internal fun oruHexPieceCount(k: Int): Int = OruHexWedges * k * k

/** The smallest k whose hexagon holds at least [count] pieces. A count below 1 gives k = 1. */
internal fun oruHexRingSize(count: Int): Int {
    var k = 1
    while (oruHexPieceCount(k) < count) k++
    return k
}

/** A patch stage share below this is too small to divide by, so the hexagon is cut for the patches alone. */
private const val MinPatchShare = 0.01f

/**
 * The ring a run is cut for. The patch stage fills [patchShare] of the whole run, and its [patches] each take one piece,
 * so the hexagon holds about patches / patchShare pieces in all, the rest of it for the other stages. Until the real
 * count is known the hexagon is cut for [OruHexPrepPatches].
 */
internal fun oruHexRingFor(patches: Int, patchShare: Float): Int {
    val wanted = when {
        patches < 1 -> OruHexPrepPatches
        patchShare > MinPatchShare -> ceil(patches / patchShare).toInt()
        else -> patches
    }
    return oruHexRingSize(wanted)
}

/** How long the fill may stand still before the sweep shows that the run is working. */
private const val StallSweepMillis = 1_500L

/** Slack for the float error of a fill that is exactly on a piece's edge, in pieces. */
private const val LitRoundingSlack = 1e-3f

/**
 * The pieces lit at [fill], a share of the whole run in [0, 1]. A piece lights once the fill has passed it, so all of
 * them light at a full fill and no piece lights before the run has reached it. NaN reads as nothing lit.
 */
internal fun oruHexLit(fill: Float, pieceCount: Int): Int {
    if (fill.isNaN()) return 0
    return floor(fill.coerceIn(0f, 1f) * pieceCount + LitRoundingSlack).toInt().coerceAtMost(pieceCount)
}

/**
 * The picture of a hexagon at rest: how many pieces are lit and whether it has settled to the success colour with its
 * check. Reduced motion draws it, and an animated reveal ends on it.
 */
internal data class OruHexRest(val lit: Int, val settled: Boolean)

/**
 * The resting picture at [fill]. The hexagon settles only at a full fill of a run that did not fail, so a run that
 * still has a step ahead, or one that failed, never reads as complete.
 */
internal fun oruHexRest(fill: Float, pieceCount: Int, failed: Boolean): OruHexRest =
    OruHexRest(lit = oruHexLit(oruHexSpread(fill), pieceCount), settled = !failed && fill >= 1f)

/**
 * The share of the pieces the run's [fill] lights. The run reaches [OruInstallShare] short of a full fill when the patch
 * run is signed, and the install takes the rest. The pieces are the run's work, so they are all lit at that point and
 * the install settles the hexagon with its check, rather than the hexagon waiting at nine tenths while the run is done.
 */
internal fun oruHexSpread(fill: Float): Float = if (fill.isNaN()) 0f else fill / (1f - OruInstallShare)

/** Corner [index] of the hexagon, counted round from the top-right, at radius 1. */
private fun oruHexCorner(index: Int): OruHexPoint {
    val angle = PI / 3 * index - PI / 6
    return OruHexPoint(cos(angle).toFloat(), sin(angle).toFloat())
}

/**
 * Piece [index] of a hexagon cut into k by k triangles per wedge, for index in 0 until [oruHexPieceCount].
 *
 * Within a wedge the lattice points are (i / k) of the first corner plus (j / k) of the second corner. Each cell
 * (i, j) with i + j below k gives an upward triangle, and a downward one when i + j is below k - 1. Cells are walked
 * row by row, so the order is fixed and the same index always draws the same piece.
 */
internal fun oruHexTriangle(index: Int, k: Int): OruHexTriangle {
    require(k >= 1 && index in 0 until oruHexPieceCount(k)) { "piece $index is outside a $k ring" }
    val perWedge = k * k
    val wedge = index / perWedge
    var left = index % perWedge
    val first = oruHexCorner(wedge)
    val second = oruHexCorner((wedge + 1) % OruHexWedges)

    fun point(i: Int, j: Int) = OruHexPoint(
        x = first.x * i / k + second.x * j / k,
        y = first.y * i / k + second.y * j / k,
    )

    for (j in 0 until k) {
        for (i in 0 until k - j) {
            if (left == 0) return OruHexTriangle(point(i, j), point(i + 1, j), point(i, j + 1))
            left--
            if (i + j <= k - 2) {
                if (left == 0) return OruHexTriangle(point(i + 1, j), point(i, j + 1), point(i + 1, j + 1))
                left--
            }
        }
    }
    throw IllegalStateException("every piece of a $k ring is walked above")
}

/** Centroid coordinates are compared in steps of this many per hexagon radius, so float noise cannot reorder a row. */
private const val OrderStepsPerRadius = 10_000f

/**
 * The order the pieces light in, for a hexagon cut into k by k triangles per wedge: entry k is the piece lit k-th. The
 * pieces run from the bottom of the hexagon to the top, so the lit area rises like liquid in a glass and stays one
 * connected region. Within a row the pieces run left to right, and ties are broken by index, so the order is fixed.
 */
internal fun oruHexFillOrder(ring: Int): IntArray {
    val count = oruHexPieceCount(ring)
    val heights = IntArray(count)
    val offsets = IntArray(count)
    for (piece in 0 until count) {
        val triangle = oruHexTriangle(piece, ring)
        heights[piece] = ((triangle.a.y + triangle.b.y + triangle.c.y) / 3f * OrderStepsPerRadius).roundToInt()
        offsets[piece] = ((triangle.a.x + triangle.b.x + triangle.c.x) / 3f * OrderStepsPerRadius).roundToInt()
    }
    return (0 until count)
        .sortedWith(compareByDescending<Int> { heights[it] }.thenBy { offsets[it] }.thenBy { it })
        .toIntArray()
}

/**
 * Pseudo random number in [0, 1) for an index. The same index always gets the same value, so a piece keeps its
 * flight direction and its spin from one frame to the next.
 */
internal fun oruNoise(index: Int): Float {
    val x = sin(index * 127.1 + 311.7) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** The direction a piece flies in from, in radians. */
internal fun oruHexFlightAngle(piece: Int): Float = oruNoise(piece) * TwoPi

/** The spin of a piece while it flies in, in radians at full travel. Half the pieces turn each way. */
internal fun oruHexSpin(piece: Int): Float = if (oruNoise(piece + SpinSeed) > 0.5f) FlightSpin else -FlightSpin

/** A small lightening of a finished piece, so neighbouring pieces read as separate. Repeats every four. */
internal fun oruHexShade(piece: Int): Float = (piece % ShadeCycle) * ShadeStep

/**
 * Alpha of a piece at [pieceAngle] while the waiting sweep points at [sweepAngle], both in radians. A piece under the
 * sweep is at the crest, the light falls off with a cosine over [SweepReachRadians] either side, and beyond that a
 * piece rests at the floor.
 */
internal fun oruSweepAlpha(pieceAngle: Float, sweepAngle: Float): Float {
    var away = (pieceAngle - sweepAngle) % TwoPi
    if (away < 0f) away += TwoPi
    val distance = if (away > PiF) TwoPi - away else away
    val lit = if (distance >= SweepReachRadians) 0f else 0.5f + 0.5f * cos(PiF * distance / SweepReachRadians)
    return SweepFloorAlpha + (SweepCrestAlpha - SweepFloorAlpha) * lit
}

/** The failed piece rests outside the hexagon, to the upper right, in hexagon units. */
internal const val OruHexFailX = 1.3f
internal const val OruHexFailY = -0.9f

/** The failed piece is turned by this many radians. */
internal const val OruHexFailTurn = 0.5f

/** Distance a piece flies from its place at full travel, in hexagon units. */
private const val FlightDistance = 2.4f

private const val FlightSpin = 1.4f
private const val SpinSeed = 9
private const val ShadeCycle = 4
private const val ShadeStep = 0.05f
private val TwoPi = (2 * PI).toFloat()
private val PiF = PI.toFloat()

/** The waiting sweep lights a piece between this floor and this crest. */
private const val SweepFloorAlpha = 0.18f
private const val SweepCrestAlpha = 0.5f

/** The sweep reaches a piece this far either side of its angle, a sixth of a turn. */
private const val SweepReachRadians = 1.0471976f

/** Nanoseconds in a millisecond, to turn the frame clock into the milliseconds the reveal counts. */
private const val NanosPerMilli = 1_000_000L

/** The hexagon fills this share of the smaller side of its space. */
private const val RadiusShare = 0.44f

/** Alpha of the pieces that are finished while a failed piece is outside, so the rest reads as dim. */
private const val DimAlpha = 0.55f

/** The faint home of every piece, drawn under the pieces that have arrived. */
private const val GhostAlpha = 0.1f

/** Check size as a share of the hexagon radius. */
private const val CheckShare = 1.1f

/** Gap between locked pieces, in dp. */
private val GapDp = 1.dp

private const val RadiansToDegrees = 57.295776f

/** One drawable piece: its path in pixels, the centre to turn about, its angle from the centre and its finished tint. */
private class HexPiece(
    val path: Path,
    val centerX: Float,
    val centerY: Float,
    val angle: Float,
    val tint: Color,
)

/**
 * The run as a hexagon of pieces, lit by the run's [fill] (see oruRunFill), so it fills from the first step of the run to
 * the last. The pieces are cut for [patches] patches, one a patch across the patch stage, which is [patchShare] of the
 * whole. A piece flies in as the fill passes it, paced so a fast run still reads one piece at a time, and the pieces
 * lock from the bottom of the hexagon up (see [oruHexFillOrder]). The piece being applied is the accent, finished
 * pieces take the accent lightened a little. A full fill
 * of a run that did not fail settles the pieces to the success colour and draws a check over them. A failed run leaves
 * the next piece outside the hexagon in the error colour once the reveal reaches it, and the finished pieces dim.
 *
 * While [preparing] the hexagon waits: its faint home shows and a sweep lights the pieces, and the sweep fades into the
 * fill once patches begin. [alreadyPlaced] pieces the run has already lit land at once, so a screen that carries the run
 * on does not fly them in again. Reduced motion places every lit piece at once, with no flight and no sweep.
 *
 * The hexagon has no semantics of its own, the hero announces the run.
 */
@Composable
fun OruHexagonAssembly(
    fill: Float,
    patches: Int,
    patchShare: Float,
    failed: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    preparing: Boolean = false,
    alreadyPlaced: Boolean = false,
) {
    val reduced = oruReducedMotion()
    val success = LocalOruStatusColors.current.ok
    val failure = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface
    val ring = oruHexRingFor(patches, oruHexSpread(patchShare))
    val pieceCount = oruHexPieceCount(ring)
    // The k-th lit piece is order[k], so the reveal counts pieces lit and the piece's place comes from the order
    val order = remember(ring) { oruHexFillOrder(ring) }
    val rest = oruHexRest(fill, pieceCount, failed)
    val real = rest.lit
    val ended = failed || fill >= 1f
    // The paced reveal of this run. Drawing reads its clock, so a frame redraws without recomposing the hero
    val track = remember(pieceCount) { OruRevealTrack(pieceCount, placed = if (alreadyPlaced) real else 0) }
    val frameMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(track, real, ended, reduced) {
        if (reduced) return@LaunchedEffect
        val gapMs = oruRevealGapMs(pieceCount, ended)
        while (true) {
            val now = withFrameNanos { it / NanosPerMilli }
            frameMs.longValue = now
            track.shown = oruRevealClamp(track.shown, real)
            for (start in oruRevealStarts(track.shown, real, track.lastStart(), now, gapMs)) track.begin(start)
            if (track.shown >= real && oruRevealLanded(track.lastStart(), now)) return@LaunchedEffect
        }
    }
    // The waiting sweep turns while the hexagon prepares. Its share fades it in and out, so the fill takes over softly
    // Quiet stretches, like a long write, light the sweep over the pieces still to come, so a run that is working reads
    // as working. It lights no piece, so the fill stays the run's real progress
    val stalled = remember { mutableStateOf(false) }
    LaunchedEffect(fill, ended, reduced) {
        stalled.value = false
        if (ended || reduced) return@LaunchedEffect
        delay(StallSweepMillis)
        stalled.value = true
    }
    val sweeping = (preparing || stalled.value) && !reduced
    val sweepShare = remember { Animatable(if (sweeping) 1f else 0f) }
    val sweepAngle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sweeping, reduced) {
        val target = if (sweeping) 1f else 0f
        if (reduced) {
            sweepShare.snapTo(target)
        } else {
            sweepShare.animateTo(target, tween(OruMotion.PieceCrossfadeMillis))
        }
    }
    LaunchedEffect(sweeping) {
        if (!sweeping) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (last >= 0L) {
                    val turned = TwoPi * ((nanos - last) / NanosPerMilli).toFloat() / OruMotion.SweepTurnMillis
                    sweepAngle.floatValue = (sweepAngle.floatValue + turned) % TwoPi
                }
                last = nanos
            }
        }
    }
    // Pieces that have begun. Reduced motion has them all at once, so the picture does not wait for the reveal
    val shown = if (reduced) real else track.shown
    val settled = rest.settled && shown >= pieceCount
    val settle = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(settled, reduced) {
        when {
            !settled -> {
                settle.snapTo(0f)
                check.snapTo(0f)
            }
            reduced -> {
                settle.snapTo(1f)
                check.snapTo(1f)
            }
            else -> {
                settle.animateTo(1f, tween(OruMotion.PieceSettleMillis))
                check.animateTo(1f, tween(OruMotion.PieceCheckMillis))
            }
        }
    }

    val gapPx = with(LocalDensity.current) { GapDp.toPx() }
    val gapStroke = remember(gapPx) { Stroke(width = gapPx) }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val radius = min(width, height) * RadiusShare
        val pieces = remember(ring, width, height, accent) {
            buildHexPieces(ring, width, height, radius, accent)
        }

        Canvas(Modifier.fillMaxSize()) {
            val now = frameMs.longValue
            val sweepFade = sweepShare.value
            val settleShare = if (reduced) (if (settled) 1f else 0f) else settle.value
            val checkShare = if (reduced) (if (settled) 1f else 0f) else check.value
            // The failed piece is the next one the run would have lit, so it sits where the fill stopped
            val failedShown = failed && shown >= real
            val flightRadius = FlightDistance * radius
            // The faint home of every piece shows from the start, so the long preparation is not an empty space
            if (!settled) {
                for (piece in 0 until pieceCount) drawPath(pieces[piece].path, color = accent, alpha = GhostAlpha)
            }
            if (sweepFade > 0f) {
                val angle = sweepAngle.floatValue
                for (piece in 0 until pieceCount) {
                    val alpha = sweepFade * oruSweepAlpha(pieces[piece].angle, angle)
                    drawPath(pieces[piece].path, color = accent, alpha = alpha)
                }
            }
            // rank is the order a piece is lit in, and piece is where it sits in the hexagon
            for (rank in 0 until pieceCount) {
                val piece = order[rank]
                val hex = pieces[piece]
                val dx: Float
                val dy: Float
                val turn: Float
                val color: Color
                val alpha: Float
                if (failedShown && rank == real) {
                    dx = OruHexFailX * radius
                    dy = OruHexFailY * radius
                    turn = OruHexFailTurn
                    color = failure
                    alpha = 1f
                } else {
                    if (rank >= shown) continue
                    val travel = if (reduced) 0f else oruFlightTravel(now - track.starts[rank])
                    val landed = travel <= 0f
                    val angle = oruHexFlightAngle(piece)
                    dx = cos(angle) * flightRadius * travel
                    dy = sin(angle) * flightRadius * travel
                    turn = oruHexSpin(piece) * travel
                    val base = if (landed) hex.tint else accent
                    color = if (settleShare > 0f) lerp(base, success, settleShare) else base
                    alpha = if (landed && failedShown) DimAlpha else 1f
                }
                translate(left = dx, top = dy) {
                    rotate(degrees = turn * RadiansToDegrees, pivot = Offset(hex.centerX, hex.centerY)) {
                        drawPath(hex.path, color = color, alpha = alpha)
                        drawPath(hex.path, color = surface, alpha = alpha, style = gapStroke)
                    }
                }
            }

            if (settled) {
                drawOruCheck(
                    cx = size.width / 2f,
                    cy = size.height / 2f,
                    s = radius * CheckShare,
                    color = surface,
                    alpha = 0.95f,
                    progress = checkShare,
                )
            }
        }
    }
}

/** The pieces of a ring of [ring] per wedge, built in pixels around the centre of a [width] by [height] space. */
private fun buildHexPieces(
    ring: Int,
    width: Float,
    height: Float,
    radius: Float,
    accent: Color,
): List<HexPiece> {
    val centerX = width / 2f
    val centerY = height / 2f
    return List(oruHexPieceCount(ring)) { piece ->
        val triangle = oruHexTriangle(piece, ring)
        val path = Path().apply {
            moveTo(centerX + triangle.a.x * radius, centerY + triangle.a.y * radius)
            lineTo(centerX + triangle.b.x * radius, centerY + triangle.b.y * radius)
            lineTo(centerX + triangle.c.x * radius, centerY + triangle.c.y * radius)
            close()
        }
        val centroidX = (triangle.a.x + triangle.b.x + triangle.c.x) / 3f
        val centroidY = (triangle.a.y + triangle.b.y + triangle.c.y) / 3f
        HexPiece(
            path = path,
            centerX = centerX + centroidX * radius,
            centerY = centerY + centroidY * radius,
            angle = atan2(centroidY, centroidX),
            tint = lerp(accent, Color.White, oruHexShade(piece)),
        )
    }
}

/**
 * The paced reveal of one hexagon: how many of its [count] pieces have begun to fly in, and the frame time each one
 * began, indexed by the order the pieces light in (see [oruHexFillOrder]). Ranks [shown] onward have not begun. The
 * first [placed] ranks began long before the first frame, so they have landed.
 */
private class OruRevealTrack(count: Int, placed: Int) {
    val starts = LongArray(count).also { it.fill(LandedBeforeFirstFrame, 0, placed) }
    var shown by mutableIntStateOf(placed)

    /** The frame time the latest piece began, or null before the first. */
    fun lastStart(): Long? = if (shown > 0) starts[shown - 1] else null

    fun begin(atMs: Long) {
        starts[shown] = atMs
        shown++
    }
}

/** A start time far enough before any frame that the piece has landed, and far enough from overflow to subtract. */
private const val LandedBeforeFirstFrame = Long.MIN_VALUE / 4
