/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import app.morphe.manager.ui.oru.theme.rememberOruSurfaceFill
import app.morphe.manager.ui.screen.shared.PredictiveBackSlideHandler
import kotlin.math.roundToInt

/*
 * Frost. The dock and the sheet are glass surfaces (OruGlassSurface, OruGlass.kt): blurred over a
 * OruBackdropHost when Frosted, otherwise the nearly opaque or opaque fill, so a missing host changes nothing
 * readable. The policy (OruFrostPolicy) decides Frosted or Solid from battery saver, low RAM and animator scale.
 * A run uses Solid on the dock.
 */

/**
 * The dock of a floating surface: a glass pill row at the bottom of Focus, holding its actions. It floats 20 dp in
 * from the edges, above the navigation bar. A run uses Solid.
 */
@Composable
fun OruFrostedDock(
    frost: OruSurfaceFill,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    OruGlassSurface(
        shape = oruGlassShape(OruGlassForm.Dock),
        role = OruGlassRole.Dock,
        fill = frost,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = DockHorizontal, end = DockHorizontal, bottom = DockBottom),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DockInner),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * A bottom sheet over a scrim. Tapping the scrim or going back dismisses it, and predictive back drags the
 * sheet down with the gesture. The sheet is at most 86 percent of the screen. Its content must hold its
 * own scrolling child, which takes the rest of the height (a LazyColumn with `Modifier.weight(1f, fill = false)`).
 * A glass sheet over a OruBackdropHost when Frosted; the scrim stays as it is.
 *
 * The scrim fades by its alpha in draw, and the predictive back drag is a layout offset, not a graphics layer: the
 * blurred glass of the sheet then sits in the layers of the screen with no layer of its own around it
 * (OruBackdrop.kt explains why).
 */
@Composable
fun OruFrostedSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fill: OruSurfaceFill = rememberOruSurfaceFill(),
    /** Takes the input of the whole sheet, scrim included, while it is closed (see [oruTapSettle]). */
    settle: OruTapGate? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    val dismissLabel = stringResource(R.string.oru_action_dismiss)
    val reduced = oruReducedMotion()
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = OruMotion.scrimFade(reduced),
        label = "oruScrimAlpha",
    )

    PredictiveBackSlideHandler(
        progress = progress,
        enabled = visible,
        onBack = onDismiss,
    )
    // Only while the sheet or its scrim shows: a full-screen pointer input that stays composed takes every tap away
    // from the screen under a closed sheet
    val takesInput = visible || scrimAlpha > 0f
    val rootModifier = modifier.fillMaxSize().let { root -> if (settle != null && takesInput) root.oruTapSettle(settle) else root }
    BoxWithConstraints(modifier = rootModifier) {
        val maxSheetHeight = maxHeight * SheetHeightShare
        // Composed only while it shows, so a hidden scrim takes no taps; drawn at the alpha of the fade
        if (visible || scrimAlpha > 0f) {
            val scrim = scheme.scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(scrim.copy(alpha = scrim.alpha * scrimAlpha)) }
                    .clickable(onClickLabel = dismissLabel, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(OruMotion.sheetRise(reduced)) { it },
            exit = slideOutVertically(OruMotion.sheetRise(reduced)) { it },
        ) {
            // Rows that opt in with oruCascade rise once the sheet has been composed, so each opening plays them again
            val entered = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered.value = true }
            OruGlassSurface(
                shape = oruGlassShape(OruGlassForm.Sheet),
                // Sheets are glass too: the Sheet role raises the tint floor so long text stays readable (oruGlassParams)
                role = OruGlassRole.Sheet,
                fill = fill,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .sheetBackSlide(progress),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = GrabTop, bottom = GrabBottom)
                            .size(width = GrabWidth, height = GrabHeight)
                            .clip(CircleShape)
                            .background(scheme.outline.copy(alpha = GrabAlpha)),
                    )
                    CompositionLocalProvider(LocalOruCascade provides entered) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * The predictive back drag of a sheet: the same distance and easing as predictiveBackSlide, drawn as a layout offset
 * instead of a graphics layer around the sheet.
 */
private fun Modifier.sheetBackSlide(progress: Animatable<Float, AnimationVector1D>): Modifier = offset {
    IntOffset(x = 0, y = (FastOutSlowInEasing.transform(progress.value) * SheetBackSlide.toPx()).roundToInt())
}

/** How far a predictive back gesture draws a sheet down before it lets go, as the shared surfaces do. */
private val SheetBackSlide = 96.dp

private const val GrabAlpha = 0.5f
private const val SheetHeightShare = 0.86f
private val DockHorizontal = 20.dp
private val DockBottom = 8.dp
private val DockInner = 12.dp
private val GrabTop = 12.dp
private val GrabBottom = 4.dp
private val GrabWidth = 32.dp
private val GrabHeight = 4.dp
