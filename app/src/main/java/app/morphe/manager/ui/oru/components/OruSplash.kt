/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.MainActivity
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.oru.theme.LocalOruStatusColors
import app.morphe.manager.ui.oru.theme.rememberOruReducedMotion
import app.morphe.manager.util.UpdateNotificationManager
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/** The ring draws in this long, on [FastOutSlowInEasing]. */
internal const val OruSplashDrawMillis = 700

/** The finished ring holds this long before it gathers into the dot. */
internal const val OruSplashHoldMillis = 120

/** The mark gathers into the wordmark dot, and the screen fades to Home, in this long. */
internal const val OruSplashGatherMillis = 240

/** Under reduced motion the finished ring holds this long, then fades out over [OruSplashGatherMillis]. */
internal const val OruSplashReducedHoldMillis = 300

/** The ring's ink across on screen. */
private val SplashMarkSize: Dp = 80.dp

/*
 * Where the wordmark dot's centre sits on Home, measured from the top left of the screen. Mirrors the top bar in
 * OruHomeContent: the dock starts at ScreenGutter, its inner start is 16dp, the dot is 12dp wide, so its centre is 6dp
 * in. The dock is inset 8dp below the status bar and the bar is 64dp tall, so the bar's centre is 40dp down. The dot sits
 * below the centre of the wordmark by its own drop (see oruWordmarkDotDrop).
 */
private val WordmarkDotLeft: Dp = OruSpacing.ScreenGutter + 16.dp + 6.dp
private val WordmarkDotTop: Dp = 8.dp + 32.dp + oruWordmarkDotDrop(scale = 1f)

/**
 * Process-wide claim on the launch splash. The first activity of a process may take it; every later one,
 * after a configuration change or a return from the background, finds it taken.
 */
object OruSplashGate {
    private val claimed = AtomicBoolean(false)

    /** True for the first call in the process, false for every call after it. */
    fun claimProcessStart(): Boolean = claimed.compareAndSet(false, true)
}

/**
 * Whether an activity start is the plain launcher one: MAIN with LAUNCHER, no data, and no purpose extra.
 * [hasPurposeExtra] marks the other starts that reuse MAIN, such as a launcher shortcut or a notification
 * with an update check.
 */
internal fun isPlainLauncherStart(
    action: String?,
    categories: Set<String>,
    hasData: Boolean,
    hasPurposeExtra: Boolean,
): Boolean =
    action == Intent.ACTION_MAIN &&
        Intent.CATEGORY_LAUNCHER in categories &&
        !hasData &&
        !hasPurposeExtra

/**
 * [isPlainLauncherStart] for a real intent. A share, an ACTION_BATCH_PATCH from another app, a deep link, a
 * launcher shortcut or a notification fails it. MainActivity routes those by action, data or extra
 * (its handleDeepLinkIntent), and this reads the same signals.
 */
internal fun isLauncherStart(intent: Intent?): Boolean {
    if (intent == null) return false
    return isPlainLauncherStart(
        action = intent.action,
        categories = intent.categories ?: emptySet(),
        hasData = intent.data != null,
        hasPurposeExtra = intent.hasExtra(MainActivity.EXTRA_SHORTCUT_ID) ||
            intent.hasExtra(UpdateNotificationManager.EXTRA_TRIGGER_UPDATE_CHECK),
    )
}

/**
 * The Oru launch moment, drawn above the NavHost so Home composes underneath and is never held back.
 *
 * It plays once per process, on the plain launcher start of a fresh activity. The ring draws itself in, the brand dot
 * is drawn with it, the finished mark holds, and then it gathers into the wordmark dot while the screen fades to Home.
 * Under reduced motion (animator scale 0 or battery saver) the finished ring holds for [OruSplashReducedHoldMillis]
 * and fades. A tap ends it at once. The overlay goes away within [OruSplashDrawMillis] + [OruSplashHoldMillis] +
 * [OruSplashGatherMillis], so it never holds input.
 */
@Composable
fun OruLaunchSplash(launchIntent: Intent?) {
    // A restored activity (configuration change or process death) comes back with this false
    val freshActivity = rememberSaveable { mutableStateOf(true) }
    val shouldPlay = remember {
        freshActivity.value && OruSplashGate.claimProcessStart() && isLauncherStart(launchIntent)
    }
    LaunchedEffect(Unit) { freshActivity.value = false }

    val reduced = rememberOruReducedMotion()
    var running by remember { mutableStateOf(shouldPlay) }
    if (!running) return

    OruSplashCurtain(reduced = reduced, onDone = { running = false })
}

/**
 * The splash itself: the ring draws, holds, gathers into the wordmark dot, and the surface fades out. Under reduced
 * motion the ring is drawn finished from the start, holds, and fades out where it is.
 */
@Composable
private fun OruSplashCurtain(reduced: Boolean, onDone: () -> Unit) {
    val drawn = remember { Animatable(if (reduced) 1f else 0f) }
    val gather = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val currentDone by rememberUpdatedState(onDone)

    // Leaving the composition (on onDone, or a tap) cancels this, so the timeline stops with it
    LaunchedEffect(Unit) {
        if (reduced) {
            delay(OruSplashReducedHoldMillis.toLong())
            fade.animateTo(1f, tween(durationMillis = OruSplashGatherMillis, easing = FastOutSlowInEasing))
        } else {
            drawn.animateTo(1f, tween(durationMillis = OruSplashDrawMillis, easing = FastOutSlowInEasing))
            delay(OruSplashHoldMillis.toLong())
            gather.animateTo(1f, tween(durationMillis = OruSplashGatherMillis, easing = FastOutSlowInEasing))
        }
        currentDone()
    }

    val scheme = MaterialTheme.colorScheme
    val brand = LocalOruStatusColors.current.brand
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val markSizePx = with(density) { SplashMarkSize.toPx() }
    val dotTarget = with(density) {
        Offset(WordmarkDotLeft.toPx(), (statusTop + WordmarkDotTop).toPx())
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .pointerInput(Unit) { detectTapGestures { currentDone() } },
    ) {
        val rest = oruRingRestPose(centre = Offset(size.width / 2f, size.height / 2f), inkDiameterPx = markSizePx)
        val pose = if (reduced) {
            rest.copy(alpha = 1f - fade.value)
        } else {
            oruRingGatherPose(rest, dotTarget = dotTarget, gather = gather.value)
        }
        val surfaceOut = if (reduced) fade.value else gather.value
        drawRect(color = scheme.surface, alpha = 1f - surfaceOut)
        drawOruRing(progress = drawn.value, pose = pose, ringColor = scheme.primary, dotColor = brand)
    }
}
