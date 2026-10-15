/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.LocalOruStatusColors

/**
 * The Oru wordmark: a brand dot and the lower-case name, set in the Yuji Boku brush face at [WordmarkSizeSp]. The name is a
 * brand, not translated, and always lower case whatever the display name is. [scale] sizes the whole mark, its dot,
 * spacing and type together, so a scaled mark is the same mark at another size: the Home header draws it larger than the
 * bar, and the bar draws it at 1.
 */
@Composable
fun OruWordmark(modifier: Modifier = Modifier, scale: Float = 1f) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((8f * scale).dp),
    ) {
        Box(
            modifier = Modifier
                .size((12f * scale).dp)
                // The lower-case band of the brush face sits below the centre of its line box, so the dot drops onto it
                .offset(y = oruWordmarkDotDrop(scale))
                .clip(CircleShape)
                .background(LocalOruStatusColors.current.brand)
                .clearAndSetSemantics { },
        )
        Text(
            text = WordmarkText,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = WordmarkFamily,
                fontSize = (WordmarkSizeSp * scale).sp,
                lineHeight = (WordmarkLineHeightSp * scale).sp,
                fontWeight = FontWeight.Normal,
                fontSynthesis = FontSynthesis.None,
                letterSpacing = 0.02.em,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The name as it is set. Lower case on purpose: the wordmark is the brand's lower-case mark, not the display name. */
internal const val WordmarkText = "oru"

/** Font size of the wordmark. The lower-case letters have no ascenders, so it is a little larger than the old 24 sp. */
internal const val WordmarkSizeSp = 27f

/** Line height of the wordmark, the same 7:6 ratio the old 28 sp line had over 24 sp. */
private const val WordmarkLineHeightSp = 31.5f

/**
 * Where the centre of the lower-case band sits, measured from the centre of the line box, in em. The line box centre sits
 * 0.436 em above the baseline (ascent 1.160 em, descent 0.288 em), and the band of "oru" (its o, r and u tops) reaches 0.48
 * em, so its centre is 0.24 em above the baseline: the dot drops 0.20 em to meet it.
 */
private const val DotDropEm = 0.20f

/** How far the brand dot sits below the centre of the wordmark, at [scale]. The splash lands on this. */
internal fun oruWordmarkDotDrop(scale: Float): Dp = (DotDropEm * WordmarkSizeSp * scale).dp

/** Yuji Boku, subset to the letters of the wordmark (SIL Open Font License 1.1, see the About licences). */
private val WordmarkFamily = FontFamily(Font(R.font.yuji_boku_wordmark, FontWeight.Normal))
