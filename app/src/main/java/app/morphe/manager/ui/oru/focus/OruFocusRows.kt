/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.oru.components.oruSlab
import app.morphe.manager.ui.oru.focus.expert.LocalOruExpertSheets
import app.morphe.manager.ui.oru.focus.expert.OruExpertSheet
import app.morphe.manager.ui.oru.focus.expert.oruSetupSummary
import app.morphe.manager.ui.oru.focus.expert.rememberOruSetup
import app.morphe.manager.ui.oru.theme.OruGroupShape
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The summary of the Expert Focus that says how the app is set up: source, channel, version, patches and installer, in
 * two lines (see oruSetupSummary). Its Edit action opens the Setup sheet, which holds every control the lines stand for.
 */
@Composable
internal fun OruFocusRows(
    vm: OruFocusViewModel,
    home: HomeViewModel,
    item: HomeAppItem,
    modifier: Modifier = Modifier,
) {
    val sheets = LocalOruExpertSheets.current
    val lines = oruSetupSummary(rememberOruSetup(vm, home, item))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .oruSlab(
                shape = OruGroupShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                overContent = true,
            )
            .padding(start = SummaryStart, top = SummaryVertical, end = SummaryEnd, bottom = SummaryVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = SummaryGap),
            verticalArrangement = Arrangement.spacedBy(SummaryLineGap),
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { sheets?.open(OruExpertSheet.Setup) }) {
            Text(stringResource(R.string.oru_action_edit))
        }
    }
}

private val SummaryStart = 16.dp
private val SummaryEnd = 8.dp
private val SummaryVertical = 8.dp
private val SummaryGap = 8.dp
private val SummaryLineGap = 2.dp
