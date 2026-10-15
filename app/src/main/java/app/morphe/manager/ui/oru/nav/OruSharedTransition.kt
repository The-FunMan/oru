/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.morphe.manager.ui.oru.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import app.morphe.manager.ui.oru.components.oruReducedMotion
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.tileFlight

/** The shared element scope of the whole navigation host. Null outside a [OruSharedTransitionHost]. */
val LocalOruSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The visibility scope of the destination being composed. Null outside a oru destination. */
val LocalOruAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Wraps the navigation host in a [SharedTransitionLayout] when [enabled]. Otherwise the content is
 * composed as it is, so the morphe flavor keeps its own tree.
 */
@Composable
fun OruSharedTransitionHost(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOruSharedTransitionScope provides this) {
            content()
        }
    }
}

/** Provides the visibility scope of a navigation destination to the oru screens inside it. */
@Composable
fun OruDestinationScope(
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalOruAnimatedVisibilityScope provides animatedVisibilityScope) {
        content()
    }
}

/**
 * Makes this element one side of a shared bounds transition under [key]. A card and the Focus hero
 * that it opens use the same key, so the bounds morph between the two destinations. The element
 * scales its content to the animating bounds, which suits a blob or an icon. Does nothing when
 * there is no shared transition scope above it.
 *
 * Put this on the element that is drawn, not on a larger box around it: the bounds are what morph,
 * so a box wider than the visible shape leaves the shape to jump at the end of the transition.
 *
 * The bounds travel on a spring that overshoots a little (see [OruMotion.tileFlight]).
 */
fun Modifier.oruSharedTile(key: String): Modifier = oruSharedBounds(key, ContentScale.FillWidth)

/**
 * [oruSharedTile] for text. The line scales to the animating bounds rather than reflowing, so a name glides from
 * the card's size to the Focus title's and grows with it.
 */
fun Modifier.oruSharedText(key: String): Modifier = oruSharedBounds(key, ContentScale.FillWidth)

/**
 * [oruSharedTile] for a pill, such as a primary button. The pill stretches to the animating bounds in both
 * directions, so its height follows the bounds too. The label stretches with it, and it crossfades with the
 * other button's label, which the shared bounds fade by default.
 */
fun Modifier.oruSharedPill(key: String): Modifier = oruSharedBounds(key, ContentScale.FillBounds)

private fun Modifier.oruSharedBounds(key: String, contentScale: ContentScale): Modifier = composed {
    val sharedScope = LocalOruSharedTransitionScope.current
    val visibilityScope = LocalOruAnimatedVisibilityScope.current
    // The bounds follow the flight token: a spring with an overshoot, or a snap when motion is reduced
    val reduced = oruReducedMotion()
    val boundsTransform = remember(reduced) {
        BoundsTransform { _, _ -> OruMotion.tileFlight<Rect>(reduced) }
    }
    if (sharedScope == null || visibilityScope == null) {
        this
    } else {
        with(sharedScope) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = boundsTransform,
                resizeMode = ResizeMode.scaleToBounds(contentScale),
            )
        }
    }
}
