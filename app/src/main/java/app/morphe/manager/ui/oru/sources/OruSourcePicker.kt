/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.focus.expert.oruChosenMark
import app.morphe.manager.ui.oru.theme.OruGroupInnerShape

/**
 * The single-choice list of the sources that patch one app: one row per source, with what each would bring. Picking
 * a row is the whole answer. The caller draws it inside an `OruFrostedSheet`, and the choice is remembered for the app
 * by the caller (see `HomeViewModel.keepSourceForApp` and `HomeViewModel.changeSourceForPendingFlow`).
 */
@Composable
internal fun ColumnScope.OruSourceChoiceContent(
    sources: OruAppSources,
    appName: String,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val chosen = sources.effective?.uid
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PickerGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.oru_source_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.oru_source_picker_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = PickerGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sources.facts, key = { it.uid }) { fact ->
            val isChosen = fact.uid == chosen
            Surface(
                onClick = { onPick(fact.uid) },
                shape = OruGroupInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = sources.labelOf(fact.uid).orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = fact.repositoryPath ?: stringResource(R.string.oru_source_built_in),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        fact.patchVersion?.let { version ->
                            Text(
                                text = stringResource(R.string.oru_source_patch_version, version),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.oru_source_patch_count,
                                fact.patchCount,
                                fact.patchCount,
                                appName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        fact.recommendedVersion?.let { version ->
                            Text(
                                text = stringResource(R.string.oru_source_needs, appName, version),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isChosen) {
                        Spacer(Modifier.width(12.dp))
                        oruChosenMark(true)?.invoke()
                    }
                }
            }
        }
    }

    OruOutlinedButton(
        text = stringResource(R.string.oru_action_close),
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PickerGutter, vertical = 12.dp),
    )
}

private val PickerGutter = 20.dp
