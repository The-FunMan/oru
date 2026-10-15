/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Elliptical corner radii as percentages, the same values as the prototype's CSS `border-radius`
 * (`PROTO:99`). Horizontal radii are percent of the width, vertical radii percent of the height.
 * Corners run top-left, top-right, bottom-right, bottom-left.
 */
@Immutable
data class BlobCorners(
    val topLeftX: Float,
    val topRightX: Float,
    val bottomRightX: Float,
    val bottomLeftX: Float,
    val topLeftY: Float,
    val topRightY: Float,
    val bottomRightY: Float,
    val bottomLeftY: Float,
) {
    /**
     * The CSS clamp rule. Radii below zero become zero. If two radii that share a side add up to
     * more than 100 percent, every radius is scaled by the smallest factor that makes all sides fit,
     * so the corners never overlap.
     */
    fun clamped(): BlobCorners {
        val tlX = topLeftX.coerceAtLeast(0f)
        val trX = topRightX.coerceAtLeast(0f)
        val brX = bottomRightX.coerceAtLeast(0f)
        val blX = bottomLeftX.coerceAtLeast(0f)
        val tlY = topLeftY.coerceAtLeast(0f)
        val trY = topRightY.coerceAtLeast(0f)
        val brY = bottomRightY.coerceAtLeast(0f)
        val blY = bottomLeftY.coerceAtLeast(0f)

        // Each side is shared by two radii: top (TL, TR horizontal), right (TR, BR vertical),
        // bottom (BR, BL horizontal), left (BL, TL vertical)
        val factor = minOf(
            1f,
            WholeSide / (tlX + trX),
            WholeSide / (trY + brY),
            WholeSide / (brX + blX),
            WholeSide / (blY + tlY),
        )
        return BlobCorners(
            topLeftX = tlX * factor,
            topRightX = trX * factor,
            bottomRightX = brX * factor,
            bottomLeftX = blX * factor,
            topLeftY = tlY * factor,
            topRightY = trY * factor,
            bottomRightY = brY * factor,
            bottomLeftY = blY * factor,
        )
    }

    /** Linear blend of every radius, for morphing between two shapes. */
    fun lerp(stop: BlobCorners, fraction: Float): BlobCorners = BlobCorners(
        topLeftX = topLeftX + (stop.topLeftX - topLeftX) * fraction,
        topRightX = topRightX + (stop.topRightX - topRightX) * fraction,
        bottomRightX = bottomRightX + (stop.bottomRightX - bottomRightX) * fraction,
        bottomLeftX = bottomLeftX + (stop.bottomLeftX - bottomLeftX) * fraction,
        topLeftY = topLeftY + (stop.topLeftY - topLeftY) * fraction,
        topRightY = topRightY + (stop.topRightY - topRightY) * fraction,
        bottomRightY = bottomRightY + (stop.bottomRightY - bottomRightY) * fraction,
        bottomLeftY = bottomLeftY + (stop.bottomLeftY - bottomLeftY) * fraction,
    )

    private companion object {
        const val WholeSide = 100f
    }
}

/**
 * The app tile's blob. A custom [Shape] built from four elliptical corners. The radii are clamped
 * on construction, so any [BlobCorners] yields a valid shape.
 */
class OruBlobShape(corners: BlobCorners) : Shape {
    val corners: BlobCorners = corners.clamped()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(blobPath(size, corners))

    companion object {
        /** Prototype `.s1`: `34% 66% 58% 42% / 46% 40% 60% 54%`. */
        val First = OruBlobShape(
            BlobCorners(
                topLeftX = 34f, topRightX = 66f, bottomRightX = 58f, bottomLeftX = 42f,
                topLeftY = 46f, topRightY = 40f, bottomRightY = 60f, bottomLeftY = 54f,
            )
        )

        /** Prototype `.s2`: a uniform 30% radius. */
        val Second = OruBlobShape(
            BlobCorners(
                topLeftX = 30f, topRightX = 30f, bottomRightX = 30f, bottomLeftX = 30f,
                topLeftY = 30f, topRightY = 30f, bottomRightY = 30f, bottomLeftY = 30f,
            )
        )

        /** Prototype `.s3`: `50% 50% 38% 62% / 60% 42% 58% 40%`. */
        val Third = OruBlobShape(
            BlobCorners(
                topLeftX = 50f, topRightX = 50f, bottomRightX = 38f, bottomLeftX = 62f,
                topLeftY = 60f, topRightY = 42f, bottomRightY = 58f, bottomLeftY = 40f,
            )
        )

        val Presets: List<OruBlobShape> = listOf(First, Second, Third)

        /**
         * The preset for an app, chosen from its id. The same id always gets the same blob, on every
         * launch, because `String.hashCode` is defined by the language and not by the runtime.
         */
        fun presetFor(key: String): OruBlobShape =
            Presets[(key.hashCode() and Int.MAX_VALUE) % Presets.size]
    }
}

/**
 * [target], with each radius animated on the `tileCorners` token. Used for the blob morph from a
 * card to focus and on to done. Reduced motion snaps straight to the target.
 */
@Composable
fun rememberOruBlobShape(target: BlobCorners): OruBlobShape {
    val spec = OruMotion.tileCorners<Float>(rememberOruReducedMotion())
    val topLeftX by animateFloatAsState(target.topLeftX, spec)
    val topRightX by animateFloatAsState(target.topRightX, spec)
    val bottomRightX by animateFloatAsState(target.bottomRightX, spec)
    val bottomLeftX by animateFloatAsState(target.bottomLeftX, spec)
    val topLeftY by animateFloatAsState(target.topLeftY, spec)
    val topRightY by animateFloatAsState(target.topRightY, spec)
    val bottomRightY by animateFloatAsState(target.bottomRightY, spec)
    val bottomLeftY by animateFloatAsState(target.bottomLeftY, spec)

    val current = BlobCorners(
        topLeftX, topRightX, bottomRightX, bottomLeftX,
        topLeftY, topRightY, bottomRightY, bottomLeftY,
    )
    return remember(current) { OruBlobShape(current) }
}

/** The blob outline as a path: straight sides between four quarter-ellipse corners (cubic Bezier). */
private fun blobPath(size: Size, corners: BlobCorners): Path {
    val w = size.width
    val h = size.height
    val k = ELLIPSE_KAPPA

    // Radii in pixels; the X values are along the width, the Y values along the height
    val tlX = corners.topLeftX / 100f * w
    val trX = corners.topRightX / 100f * w
    val brX = corners.bottomRightX / 100f * w
    val blX = corners.bottomLeftX / 100f * w
    val tlY = corners.topLeftY / 100f * h
    val trY = corners.topRightY / 100f * h
    val brY = corners.bottomRightY / 100f * h
    val blY = corners.bottomLeftY / 100f * h

    return Path().apply {
        moveTo(tlX, 0f)
        lineTo(w - trX, 0f)
        cubicTo(w - trX + trX * k, 0f, w, trY - trY * k, w, trY)
        lineTo(w, h - brY)
        cubicTo(w, h - brY + brY * k, w - brX + brX * k, h, w - brX, h)
        lineTo(blX, h)
        cubicTo(blX - blX * k, h, 0f, h - blY + blY * k, 0f, h - blY)
        lineTo(0f, tlY)
        cubicTo(0f, tlY - tlY * k, tlX - tlX * k, 0f, tlX, 0f)
        close()
    }
}

/** Control-point distance for a quarter ellipse, as a fraction of its radius. */
private const val ELLIPSE_KAPPA = 0.5522847498f
