/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import android.app.Application
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruRow
import app.morphe.manager.ui.oru.focus.OruFocusViewModel
import app.morphe.manager.ui.oru.install.AppOruInstallerReadings
import app.morphe.manager.ui.oru.install.OruInstallerOption
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Install with sheet. It lists the installers the phone reports, unavailable ones with the reason, and choosing
 * one writes `installer_primary` (the default for later installs) and applies it to this run. The list is read again
 * on open, since the Shizuku and root states can change in the settings the install sends the user to.
 */
@Composable
internal fun ColumnScope.OruInstallerContent(
    host: OruExpertSheetHost,
    vm: OruFocusViewModel,
) {
    val app = LocalContext.current.applicationContext as Application
    val prefs: PreferencesManager = koinInject()
    val installerManager: InstallerManager = koinInject()
    val sessionInstaller: SessionInstaller = koinInject()
    val rootInstaller: RootInstaller = koinInject()
    val scope = rememberCoroutineScope()
    val primary by prefs.installerPrimary.getAsState()
    var options by remember { mutableStateOf(vm.installerOptions) }
    LaunchedEffect(Unit) {
        options = AppOruInstallerReadings(app, prefs, installerManager, sessionInstaller, rootInstaller).pickerOptions()
    }
    // The run's own choice wins over the default, as the Install with row shows it
    val current = vm.pickedInstaller?.token ?: primary

    OruInstallerPicker(
        options = options,
        current = current,
        onPick = { option ->
            scope.launch {
                installerManager.updatePrimaryToken(installerManager.parseToken(option.token))
                vm.pickInstaller(option)
                host.close()
            }
        },
        onClose = { host.close() },
    )
}

/**
 * The list of the Install with sheet: the title, the installers with their state (unavailable ones with the reason),
 * the chosen one marked, and a close button. Shared by the Focus sheet and the Advanced page, which both pass their own
 * [onPick] and [onClose]. [current] is the token of the installer that is chosen.
 */
@Composable
internal fun ColumnScope.OruInstallerPicker(
    options: List<OruInstallerOption>,
    current: String,
    onPick: (OruInstallerOption) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.oru_installer_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.oru_installer_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.token }) { option ->
            val state = if (option.enabled) {
                stringResource(R.string.oru_installer_available)
            } else {
                option.reason ?: stringResource(R.string.oru_installer_unavailable)
            }
            OruRow(
                title = option.label,
                value = state,
                onClick = if (option.enabled) ({ onPick(option) }) else null,
                trailing = oruChosenMark(option.token == current),
            )
        }
    }

    OruOutlinedButton(
        text = stringResource(R.string.oru_action_close),
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = 12.dp),
    )
}

private val SheetGutter = 20.dp
