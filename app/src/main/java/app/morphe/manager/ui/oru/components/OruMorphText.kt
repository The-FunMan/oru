/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import app.morphe.manager.ui.oru.theme.OruMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * A line of text that changes by dissolving and forming again, the way Apple's text does: the old glyphs blur out
 * into a cloud that drifts and shrinks a little, then the new glyphs form out of the same cloud, each one a few
 * milliseconds after its left neighbour, and overshoot their place slightly before they settle.
 *
 * Each glyph is its own Text, so a glyph can move on its own. Kerning between glyphs is therefore lost, which is
 * acceptable for the short labels this is used for. A glyph's motion is one Animatable from 0 (at rest) to 1 (in the
 * cloud): the old character leaves at 1, the new one is put in at 1, and it comes back to 0. A change in the middle of
 * a morph starts each glyph from where it is, so nothing restarts from zero.
 */

/** The longest text that morphs glyph by glyph. A longer label cross-fades. */
internal const val OruMorphMaxGlyphs = 40

/** The seed of the glyph drift. Any value works; it only has to stay the same, so every morph drifts the same way. */
private const val MorphSeed = 0x4B4149

/**
 * A line that morphs when [text] changes. Reduced motion, and a text longer than [OruMorphMaxGlyphs], cross-fade
 * instead. Below API 31 the cloud has no blur and is only faded and drifted. The text is one semantic label, so a
 * screen reader reads the whole line and not the glyphs.
 */
@Composable
fun OruMorphText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (oruReducedMotion() || text.length > OruMorphMaxGlyphs) {
        OruCrossFadeText(text = text, style = style, color = color, modifier = modifier)
    } else {
        OruGlyphMorphText(text = text, style = style, color = color, modifier = modifier)
    }
}

/** One position of a morph: the character it shows, how far it is in the cloud, and its own drift and blur. */
private class OruGlyph(val drift: OruGlyphDrift, initial: String) {
    /** 0 at rest, 1 in the cloud. Springs overshoot a little past both. */
    val cloud = Animatable(0f)

    /** The character drawn here. It changes while the glyph is in the cloud, so the cloud is never the wrong letter. */
    val shown = mutableStateOf(initial)

    val blur = OruBlurEffect()
}

@Composable
private fun OruGlyphMorphText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    // One glyph per possible position, created once: a morph never allocates while it runs
    val glyphs = remember {
        Array(OruMorphMaxGlyphs) { index ->
            OruGlyph(drift = oruGlyphDrift(MorphSeed, index), initial = text.getOrNull(index)?.toString().orEmpty())
        }
    }
    // The glyphs on screen. While a removed glyph is still in the cloud it stays on screen with the old text's length
    var span by remember { mutableStateOf(text.length) }
    LaunchedEffect(text) {
        val last = maxOf(span, text.length)
        span = last
        coroutineScope {
            for (index in 0 until last) {
                val glyph = glyphs[index]
                val target = text.getOrNull(index)?.toString().orEmpty()
                launch {
                    delay(oruGlyphDelayMillis(index).toLong())
                    // A glyph that must change goes into the cloud first, then takes its new character and forms
                    if (glyph.shown.value != target) {
                        glyph.cloud.animateTo(1f, OruMotion.springSnappy(reduced = false))
                        glyph.shown.value = target
                    }
                    glyph.cloud.animateTo(0f, OruMotion.springSoft(reduced = false))
                }
            }
        }
        span = text.length
    }

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until span) {
            val glyph = glyphs[index]
            Text(
                text = glyph.shown.value,
                style = style,
                color = color,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { applyCloud(glyph) },
            )
        }
    }
}

/**
 * Places a glyph for its cloud amount: faded, shrunk, drifted, turned and blurred. Read in the draw phase, so an
 * animation costs no recomposition. The blur needs a RenderEffect, so it is only set on API 31 and above.
 */
private fun GraphicsLayerScope.applyCloud(glyph: OruGlyph) {
    val cloud = glyph.cloud.value
    val drift = glyph.drift
    alpha = (1f - cloud).coerceIn(0f, 1f)
    val shrink = 1f + (drift.scale - 1f) * cloud
    scaleX = shrink
    scaleY = shrink
    translationX = drift.dxDp * cloud * density
    translationY = drift.dyDp * cloud * density
    rotationZ = drift.rotationDegrees * cloud
    if (Build.VERSION.SDK_INT >= OruBlurMinSdk) {
        renderEffect = glyph.blur.of(cloud.coerceAtLeast(0f) * OruMotion.CloudBlurDp * density)
    }
}

/** A plain cross-fade: the fallback of reduced motion and of a label too long to morph. */
@Composable
private fun OruCrossFadeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            fadeIn(OruMotion.crossFade<Float>()) togetherWith fadeOut(OruMotion.crossFade<Float>())
        },
        label = "oruMorphCrossFade",
    ) { current ->
        Text(text = current, style = style, color = color, textAlign = TextAlign.Center)
    }
}
