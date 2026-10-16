/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.installableOnDevice
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruRow
import app.morphe.manager.ui.oru.focus.OruFocusViewModel
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The version sheet: the versions the sources cover for this app, the recommended one first. Choosing one sets
 * the download version the APKMirror link is resolved for (`pendingSelectedDownloadVersion`, which
 * `resolveDownloadRedirect` reads). The file the user picks is checked by its own version, as before, so the
 * choice does not change that check. With no choice the recommended version is used.
 */
@Composable
internal fun ColumnScope.OruVersionContent(
    host: OruExpertSheetHost,
    vm: OruFocusViewModel,
    home: HomeViewModel,
) {
    // Newest first: the list holds the versions of the one source the app is patched from, so it is never mixed
    val candidates = home.pendingCompatibleVersions.sortedByDescending { it.target }.mapNotNull { bundled ->
        bundled.target.version?.let { version ->
            OruVersionCandidate(
                version = version,
                isExperimental = bundled.target.isExperimental,
                experimentalEnabled = bundled.experimentalEnabled,
                installable = bundled.installableOnDevice(),
            )
        }
    }
    val recommended = home.pendingRecommendedVersion?.version
    val entries = OruVersionClassifier.classify(candidates, recommended)
    val chosen = home.pendingSelectedDownloadVersion?.version ?: recommended

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.oru_version_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.oru_version_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.version }) { entry ->
            val pick: () -> Unit = {
                vm.pickDownloadVersion(entry.version)
                // A link already resolved for the old version would send the user to the wrong page
                if (home.showDownloadInstructionsDialog) home.resolveDownloadRedirect()
                host.close()
            }
            OruRow(
                title = entry.version,
                value = versionLabel(entry),
                onClick = if (entry.selectable) pick else null,
                trailing = oruChosenMark(entry.version == chosen),
            )
        }
    }

    OruOutlinedButton(
        text = stringResource(R.string.oru_action_close),
        onClick = { host.close() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = 12.dp),
    )
}

@Composable
private fun versionLabel(entry: OruVersionEntry): String = when (entry.kind) {
    OruVersionKind.Recommended -> stringResource(R.string.oru_version_recommended)
    OruVersionKind.Compatible -> stringResource(R.string.oru_version_compatible)
    OruVersionKind.Experimental -> stringResource(
        if (entry.experimentalOff) R.string.oru_version_experimental_off else R.string.oru_version_experimental,
    )
    OruVersionKind.Unsupported -> stringResource(R.string.oru_version_unsupported)
}

private val SheetGutter = 20.dp
