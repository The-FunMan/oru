/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.oru.components.OruFrostedSheet
import app.morphe.manager.ui.oru.focus.expert.OruInstallerPicker
import app.morphe.manager.ui.oru.install.AppOruInstallerReadings
import app.morphe.manager.ui.oru.install.OruInstallerOption
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Install with list, opened from the Advanced page over the settings sheet. It is the list the Focus sheet shows,
 * and choosing an installer writes the default that later installs use. The run's own choice is made in Focus.
 */
@Composable
internal fun OruSettingsInstallerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val prefs: PreferencesManager = koinInject()
    val installerManager: InstallerManager = koinInject()
    val sessionInstaller: SessionInstaller = koinInject()
    val rootInstaller: RootInstaller = koinInject()
    val scope = rememberCoroutineScope()
    val primary by prefs.installerPrimary.getAsState()
    var options by remember { mutableStateOf<List<OruInstallerOption>>(emptyList()) }

    // Read again on each open, since the Shizuku and root states can change in the settings the install sends the user to
    LaunchedEffect(visible) {
        if (visible) {
            options = AppOruInstallerReadings(app, prefs, installerManager, sessionInstaller, rootInstaller).pickerOptions()
        }
    }

    OruFrostedSheet(visible = visible, onDismiss = onDismiss) {
        OruInstallerPicker(
            options = options,
            current = primary,
            onPick = { option ->
                scope.launch {
                    installerManager.updatePrimaryToken(installerManager.parseToken(option.token))
                    onDismiss()
                }
            },
            onClose = onDismiss,
        )
    }
}
