/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.ApkDownloadHelperContract
import app.morphe.manager.util.isAndroidTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files & storage settings section. Owns the entry into the full storage-management screen,
 * the expert-mode patch selection dialog, the file picker toggle and the download helper trust list.
 */
@Composable
fun FilesAndStorageSection(
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    modifier: Modifier = Modifier,
    onFilePickerPositioned: ((Rect) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    val useCustomFilePicker by settingsViewModel.prefs.useCustomFilePicker.getAsState()
    val trustedApkDownloadHelpers by settingsViewModel.prefs.trustedApkDownloadHelpers.getAsState()
    val showStorageDialog = remember { mutableStateOf(false) }
    val showPatchSelectionDialog = remember { mutableStateOf(false) }
    val showApkDownloadHelpersDialog = remember { mutableStateOf(false) }

    // Keeps the entry hidden until a helper is installed, so it never advertises a third-party app
    var apkDownloadHelpers by remember { mutableStateOf(emptyList<ApkDownloadHelperApp>()) }
    LaunchedEffect(Unit) {
        apkDownloadHelpers = withContext(Dispatchers.IO) {
            ApkDownloadHelperContract.findHelpers(context)
                .distinctBy { it.componentName.packageName }
                .map { ApkDownloadHelperApp(packageName = it.componentName.packageName, label = it.label) }
        }
    }

    if (showStorageDialog.value) {
        StorageManagementDialog(onDismissRequest = { showStorageDialog.value = false })
    }

    if (showApkDownloadHelpersDialog.value) {
        ApkDownloadHelpersDialog(
            settingsViewModel = settingsViewModel,
            helpers = apkDownloadHelpers,
            onDismiss = { showApkDownloadHelpersDialog.value = false }
        )
    }

    if (showPatchSelectionDialog.value) {
        PatchSelectionManagementDialog(
            settingsViewModel = settingsViewModel,
            importExportViewModel = importExportViewModel,
            onDismiss = { showPatchSelectionDialog.value = false }
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
    ) {
        SectionTitle(
            text = stringResource(R.string.settings_system_files),
            icon = Icons.Outlined.Storage
        )

        SettingsGroup {
            SettingsItem(
                onClick = { showStorageDialog.value = true },
                title = stringResource(R.string.settings_system_storage_management_title),
                subtitle = stringResource(R.string.settings_system_storage_management_description),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.Storage) }
            )

            // Patch Selections (Expert mode only)
            if (useExpertMode) {
                SettingsDivider()

                SettingsItem(
                    onClick = { showPatchSelectionDialog.value = true },
                    title = stringResource(R.string.settings_system_patch_selections_title),
                    subtitle = stringResource(R.string.settings_system_patch_selections_description),
                    leadingContent = { ThemedIcon(icon = Icons.Outlined.Tune) }
                )
            }
        }

        // TV always uses the custom picker regardless of this toggle, so hide it to avoid confusion
        val showFilePicker = !isTV
        val showApkDownloadHelpers = apkDownloadHelpers.isNotEmpty()

        // Both are ways of getting hold of the original APK, so they share a card
        if (showFilePicker || showApkDownloadHelpers) {
            SettingsGroup {
                if (showFilePicker) {
                    SettingsSwitchItem(
                        checked = useCustomFilePicker,
                        onToggle = { settingsViewModel.setUseCustomFilePicker(!useCustomFilePicker) },
                        modifier = if (onFilePickerPositioned != null)
                            Modifier.onGloballyPositioned { coords -> onFilePickerPositioned(coords.boundsInWindow()) }
                        else Modifier,
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.settings_system_custom_file_picker),
                        subtitle = stringResource(R.string.settings_system_custom_file_picker_description)
                    )
                }

                if (showFilePicker && showApkDownloadHelpers) SettingsDivider()

                if (showApkDownloadHelpers) {
                    SettingsItem(
                        onClick = { showApkDownloadHelpersDialog.value = true },
                        title = stringResource(R.string.settings_system_apk_download_helper),
                        subtitle = stringResource(
                            R.string.settings_system_apk_download_helper_trusted_count,
                            apkDownloadHelpers.count { it.packageName in trustedApkDownloadHelpers },
                            apkDownloadHelpers.size
                        ),
                        leadingContent = { ThemedIcon(icon = Icons.Outlined.Download) }
                    )
                }
            }
        }
    }
}
