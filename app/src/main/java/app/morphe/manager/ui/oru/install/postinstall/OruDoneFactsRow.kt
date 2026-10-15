/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruContainerRole
import app.morphe.manager.ui.oru.components.OruFitText
import app.morphe.manager.ui.oru.components.oruContainerColors
import app.morphe.manager.ui.oru.components.oruSlab
import app.morphe.manager.ui.oru.theme.OruMonoStyle

/**
 * The three done facts in a row of tinted cells: output size, time and peak heap. A fact the phone did not
 * measure reads as "Not available" rather than a zero.
 */
@Composable
internal fun OruDoneFactsRow(
    facts: OruDoneFacts,
    modifier: Modifier = Modifier,
) {
    val colors = oruContainerColors(OruContainerRole.Ok)
    val notAvailable = stringResource(R.string.oru_run_not_available)
    val cells = listOf(
        (facts.outputSize ?: notAvailable) to R.string.oru_done_output,
        (facts.elapsed ?: notAvailable) to R.string.oru_done_time,
        (facts.peakHeap ?: notAvailable) to R.string.oru_done_peak_heap,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        cells.forEach { (value, labelRes) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .oruSlab(shape = CellShape, color = colors.container, compact = true)
                    .padding(CellPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LabelGap),
            ) {
                // The figure stays on one line and shrinks to fit; the label may wrap instead
                OruFitText(
                    text = value,
                    style = OruMonoStyle.copy(fontSize = ValueSize, fontWeight = FontWeight.SemiBold),
                    color = colors.content,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.content,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val CellShape = RoundedCornerShape(18.dp)
private val CellPadding = 12.dp
private val CellGap = 8.dp
private val LabelGap = 2.dp
private val ValueSize = 16.sp
