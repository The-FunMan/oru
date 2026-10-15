/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruMonoStyle

/**
 * A status chip. The text carries the meaning, the tone only adds the colour. [mono] sets the text in
 * the monospace figure style, for versions and counts. A neutral chip takes the app's tint when its screen provides one
 * (LocalOruAppTint), such as an app's card or its Focus.
 */
@Composable
fun OruChip(
    text: String,
    tone: OruTone,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = oruContainerColors(oruContainerRole(tone))
    val neutralTint = LocalOruAppTint.current.takeIf { tone == OruTone.Neutral }
    val container = neutralTint?.chip ?: colors.container
    val content = neutralTint?.onChip ?: colors.content
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 28.dp)
            .oruSlab(shape = MaterialTheme.shapes.extraSmall, color = container, compact = true)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (mono) {
                OruMonoStyle.copy(fontWeight = FontWeight.Medium)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = content,
        )
    }
}

/**
 * A row of chips that shares the width. Each chip may wrap its own text, so long chips do not push the
 * row out of the screen at a large font size.
 */
@Composable
internal fun OruChipRow(
    chips: List<OruChipUi>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            OruChip(
                text = chip.text,
                tone = chip.tone,
                mono = chip.mono,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
