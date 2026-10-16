/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_MINIMUM
import app.morphe.manager.patcher.runtime.coerceMemoryLimit
import app.morphe.manager.patcher.runtime.initialMemoryLimit
import app.morphe.manager.patcher.runtime.maxMemoryLimit
import app.morphe.manager.patcher.runtime.memoryLimitSliderSteps
import app.morphe.manager.patcher.runtime.snapMemoryLimit
import app.morphe.manager.ui.oru.components.OruNotice
import app.morphe.manager.ui.oru.components.OruRowControl
import app.morphe.manager.ui.oru.components.OruSectionLabel
import app.morphe.manager.ui.oru.components.OruSettingsRow
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.components.oruSlab
import app.morphe.manager.ui.oru.components.rememberOruSelectTick
import app.morphe.manager.ui.oru.theme.OruGroupShape
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.screen.settings.advanced.GitHubPatDialog
import app.morphe.manager.ui.screen.settings.system.ImportModeDialog
import app.morphe.manager.ui.screen.settings.system.KeystoreCredentialsDialog
import app.morphe.manager.ui.screen.settings.system.PatchSelectionManagementDialog
import app.morphe.manager.ui.screen.settings.system.SettingsBackupDialog
import app.morphe.manager.ui.screen.settings.system.SigningKeyBackupDialog
import app.morphe.manager.ui.screen.shared.ConfirmDialog
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.JSON_MIMETYPE
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.formatMebibytes
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.util.toast
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The Advanced page of the Settings sheet. It is pushed inside the sheet, which holds the back chevron, so the page has
 * no bar of its own. Every group reuses the existing dialogs and view models, except the installer, which opens the
 * Install with list nested over the sheet. Rows that duplicate a row of the Settings list (mobile data, free up space,
 * the APK managers) are not repeated here.
 *
 * @param focusGroup the group the page scrolls to when it opens (the signing key row opens Backup), or null for the top.
 * @param onOpenSources closes the sheet and opens the patch source list on Home, which owns the add, rename and update
 *   dialogs. Reused as-is rather than copied here.
 * @param onOpenInstaller opens the Install with list over the sheet.
 */
@Composable
fun OruAdvancedContent(
    focusGroup: OruAdvancedGroup?,
    onOpenSources: () -> Unit,
    onOpenInstaller: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel(),
) {
    val prefs = settingsViewModel.prefs
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    // Top of the group the page opens at, in the page's coordinates. Known once that group has been laid out
    var focusTop by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusGroup) {
        if (focusGroup != null) {
            snapshotFlow { focusTop }.first { it >= 0 }
            scroll.animateScrollTo(focusTop)
        }
    }

    val useExpertMode by prefs.useExpertMode.getAsState()
    val promptInstaller by prefs.promptInstallerOnInstall.getAsState()
    val autoUninstall by prefs.autoUninstallWithShizuku.getAsState()
    val installerPrimary by prefs.installerPrimary.getAsState()
    val managerPrereleases by prefs.useManagerPrereleases.getAsState()
    val externalEnabled by prefs.externalBatchPatchEnabled.getAsState()
    val allowlist by prefs.externalBatchPatchAllowlist.getAsState()
    val gitHubPat by prefs.gitHubPat.getAsState()
    val includeGitHubPat by prefs.includeGitHubPatInExports.getAsState()
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()
    val heapLimitIgnored by prefs.patcherHeapLimitIgnored.getAsState()

    val installerToken = remember(installerPrimary) { settingsViewModel.parseInstallerToken(installerPrimary) }
    val installerLabel = remember(installerToken) {
        settingsViewModel.describeInstallerEntry(installerToken, InstallerManager.InstallTarget.PATCHER)?.label.orEmpty()
    }
    val installerIsShizuku = installerToken is InstallerManager.Token.Shizuku ||
        installerToken is InstallerManager.Token.ShizukuPlayStore

    var showPatchSelections by rememberSaveable { mutableStateOf(false) }
    var showGitHubToken by rememberSaveable { mutableStateOf(false) }
    var showSigningKey by rememberSaveable { mutableStateOf(false) }
    var showSettingsBackup by rememberSaveable { mutableStateOf(false) }
    var revokePackage by rememberSaveable { mutableStateOf<String?>(null) }

    // Backup: the same launchers and import rules as the Settings screen
    var pendingSettingsImportUri by remember { mutableStateOf<Uri?>(null) }
    val importKeystoreLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("*/*"),
        customPickerMimeTypes = arrayOf(
            "application/x-pkcs12",
            "application/x-java-keystore",
            "application/vnd.morphe.keystore",
        ),
        onResult = { uri -> uri?.let { importExportViewModel.startKeystoreImport(it) } }
    )
    val importSettingsLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(JSON_MIMETYPE, TEXT_MIMETYPE),
        customPickerMimeTypes = arrayOf(JSON_MIMETYPE),
        onResult = { uri ->
            uri ?: return@rememberAdaptiveFilePicker
            val sections = importExportViewModel.settingsSections
            if (SettingsSection.SOURCES in sections || SettingsSection.PATCH_SELECTIONS in sections) {
                pendingSettingsImportUri = uri
            } else {
                importExportViewModel.importManagerSettings(uri)
            }
        }
    )
    val exportKeystoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> uri?.let { importExportViewModel.exportKeystore(it) } }
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportManagerSettings(it) } }
    val exportDebugLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(TEXT_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportDebugLogs(it) } }

    // Patching creates the key and an import replaces it, so the signing key is read again when the page opens
    LaunchedEffect(Unit) { importExportViewModel.refreshSigningKey() }

    // Dialogs
    if (showPatchSelections) {
        PatchSelectionManagementDialog(
            settingsViewModel = settingsViewModel,
            importExportViewModel = importExportViewModel,
            onDismiss = { showPatchSelections = false }
        )
    }
    if (showGitHubToken) {
        GitHubPatDialog(
            currentPat = gitHubPat,
            currentIncludeInExport = includeGitHubPat,
            onSubmit = { pat, include ->
                settingsViewModel.setGitHubPat(pat, include)
                showGitHubToken = false
            },
            onDismiss = { showGitHubToken = false },
        )
    }
    if (showSigningKey) {
        SigningKeyBackupDialog(
            importExportViewModel = importExportViewModel,
            onImportKeystore = { importKeystoreLauncher() },
            onExportKeystore = { exportKeystoreLauncher.launch("Oru.keystore") },
            onDismiss = { showSigningKey = false },
        )
    }
    if (showSettingsBackup) {
        SettingsBackupDialog(
            importExportViewModel = importExportViewModel,
            onImportSettings = { importSettingsLauncher() },
            onExportSettings = { exportSettingsLauncher.launch("oru_settings.json") },
            onDismiss = { showSettingsBackup = false },
        )
    }
    revokePackage?.let { packageName ->
        ConfirmDialog(
            title = stringResource(R.string.remove),
            message = packageName,
            primaryText = stringResource(R.string.remove),
            onConfirm = {
                scope.launch { prefs.externalBatchPatchAllowlist.update(allowlist - packageName) }
                revokePackage = null
            },
            onDismiss = { revokePackage = null }
        )
    }
    if (importExportViewModel.showCredentialsDialog) {
        val wrongCredentials = stringResource(R.string.settings_system_import_keystore_wrong_credentials)
        KeystoreCredentialsDialog(
            onDismiss = { importExportViewModel.cancelKeystoreImport() },
            initialFormat = importExportViewModel.detectedKeystoreFormat,
            onSubmit = { alias, pass, storePass, format ->
                scope.launch {
                    val ok = importExportViewModel.tryKeystoreImport(alias, pass, storePass, format)
                    if (!ok) context.toast(wrongCredentials)
                }
            }
        )
    }
    pendingSettingsImportUri?.let { uri ->
        ImportModeDialog(
            titleRes = R.string.settings_system_import_manager_settings_mode_title,
            descriptionRes = R.string.settings_system_import_manager_settings_mode_description,
            onDismiss = { pendingSettingsImportUri = null },
            onSelect = { mode ->
                importExportViewModel.importManagerSettings(uri, mode)
                pendingSettingsImportUri = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(OruSpacing.Large),
    ) {
        Text(
            text = stringResource(R.string.oru_advanced_confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        AdvancedGroup(
            title = stringResource(R.string.oru_advanced_group_installing),
            anchored = focusGroup == OruAdvancedGroup.INSTALLING,
            scroll = scroll,
            onTop = { focusTop = it },
        ) {
            OruSettingsRow(
                title = stringResource(R.string.oru_focus_install_with),
                supporting = installerLabel.ifBlank { null },
                control = OruRowControl.Chevron,
                onClick = onOpenInstaller,
            )
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_installer_ask),
                supporting = null,
                control = OruRowControl.Switch(promptInstaller),
                onClick = { settingsViewModel.setPromptInstallerOnInstall(!promptInstaller) },
            )
            // Removing the original needs Shizuku, so the row only shows when Shizuku installs
            if (installerIsShizuku) {
                OruSettingsRow(
                    title = stringResource(R.string.oru_settings_uninstall_original),
                    supporting = stringResource(R.string.oru_settings_needs_shizuku),
                    control = OruRowControl.Switch(autoUninstall),
                    onClick = { settingsViewModel.setAutoUninstallWithShizuku(!autoUninstall) },
                )
            }
        }

        AdvancedGroup(
            title = stringResource(R.string.oru_advanced_group_sources),
            anchored = focusGroup == OruAdvancedGroup.SOURCES,
            scroll = scroll,
            onTop = { focusTop = it },
        ) {
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_patch_sources),
                supporting = null,
                control = OruRowControl.Chevron,
                onClick = onOpenSources,
            )
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_github_token),
                supporting = stringResource(R.string.oru_settings_github_token_helper),
                control = if (gitHubPat.isNotBlank()) {
                    OruRowControl.Value(stringResource(R.string.oru_settings_github_token_saved))
                } else {
                    OruRowControl.Chevron
                },
                onClick = { showGitHubToken = true },
            )
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_manager_prerelease),
                supporting = stringResource(R.string.oru_settings_manager_prerelease_helper),
                control = OruRowControl.Switch(managerPrereleases),
                onClick = { scope.launch { prefs.useManagerPrereleases.update(!managerPrereleases) } },
            )
        }

        AdvancedGroup(
            title = stringResource(R.string.oru_advanced_group_automation),
            anchored = focusGroup == OruAdvancedGroup.AUTOMATION,
            scroll = scroll,
            onTop = { focusTop = it },
        ) {
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_other_apps),
                supporting = stringResource(R.string.oru_settings_other_apps_helper),
                control = OruRowControl.Switch(externalEnabled),
                onClick = { settingsViewModel.toggleExternalBatchPatch(externalEnabled) },
            )
            if (externalEnabled) {
                OruAllowlist(
                    packages = allowlist.sorted(),
                    onRevoke = { revokePackage = it },
                )
            }
        }

        if (useExpertMode) {
            AdvancedGroup(
                title = stringResource(R.string.oru_advanced_group_patching),
                anchored = focusGroup == OruAdvancedGroup.PATCHING,
                scroll = scroll,
                onTop = { focusTop = it },
            ) {
                OruSettingsRow(
                    title = stringResource(R.string.settings_system_patch_selections_title),
                    supporting = stringResource(R.string.settings_system_patch_selections_description),
                    control = OruRowControl.Chevron,
                    onClick = { showPatchSelections = true },
                )
                // Process runtime, and so the memory limit, needs Android 11, as on the Settings list
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    OruMemoryLimitRow(
                        storedLimit = memoryLimit,
                        processRuntime = useProcessRuntime,
                        heapLimitIgnored = heapLimitIgnored,
                        onLimitChange = { settingsViewModel.setMemoryLimit(it) },
                    )
                }
            }
        }

        AdvancedGroup(
            title = stringResource(R.string.oru_advanced_group_backup),
            anchored = focusGroup == OruAdvancedGroup.BACKUP,
            scroll = scroll,
            onTop = { focusTop = it },
        ) {
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_signing_key),
                supporting = stringResource(R.string.oru_settings_signing_key_helper),
                control = OruRowControl.Chevron,
                onClick = { showSigningKey = true },
            )
            OruSettingsRow(
                title = stringResource(R.string.settings_system_morphe_settings),
                supporting = stringResource(R.string.settings_system_import_manager_settings_description),
                control = OruRowControl.Chevron,
                onClick = { showSettingsBackup = true },
            )
        }

        AdvancedGroup(
            title = stringResource(R.string.oru_advanced_group_help),
            anchored = focusGroup == OruAdvancedGroup.HELP,
            scroll = scroll,
            onTop = { focusTop = it },
        ) {
            OruSettingsRow(
                title = stringResource(R.string.oru_settings_export_log),
                supporting = stringResource(R.string.settings_system_export_debug_logs_description),
                control = OruRowControl.Chevron,
                onClick = { exportDebugLogsLauncher.launch(importExportViewModel.debugLogFileName) },
            )
        }
    }
}

/**
 * One group of the Advanced page: its heading, then its rows on one raised slab, as the Settings list draws its groups.
 * An [anchored] group reports its top through [onTop], so the page can scroll to it.
 */
@Composable
private fun AdvancedGroup(
    title: String,
    anchored: Boolean,
    scroll: ScrollState,
    onTop: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (anchored) {
                    Modifier.onGloballyPositioned { coordinates ->
                        // The scroll offset is added back, so the value does not change while the page scrolls
                        onTop((coordinates.positionInParent().y + scroll.value).roundToInt())
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        OruSectionLabel(text = title, modifier = AdvancedLabelModifier)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .oruSlab(
                    shape = OruGroupShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    overContent = true,
                ),
            verticalArrangement = Arrangement.spacedBy(OruSpacing.GroupGap),
            content = content,
        )
    }
}

/**
 * The memory limit of the patcher process, with the range and steps the Process runtime dialog uses. The slider shows
 * the value under the finger while it moves and writes it when the finger lifts. A stored limit outside this device's
 * range shows as the nearest value the device allows, and the recommendation is the limit the device starts with.
 */
@Composable
private fun OruMemoryLimitRow(
    storedLimit: Int,
    processRuntime: Boolean,
    heapLimitIgnored: Boolean,
    onLimitChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val maxLimit = remember(context) { maxMemoryLimit(context) }
    val recommended = remember(context) { initialMemoryLimit(context) }
    val shownStored = remember(context, storedLimit) { coerceMemoryLimit(context, storedLimit) }
    val tick = rememberOruSelectTick()

    // The value being dragged, or the value last written. It is dropped once the store reports a new limit
    var pendingLimit by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(storedLimit) { pendingLimit = null }
    val shownLimit = pendingLimit ?: shownStored
    val shownText = context.formatMebibytes(shownLimit)
    val valueDescription = stringResource(R.string.oru_settings_memory_limit_description, shownText)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(OruSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.oru_settings_memory_limit),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shownText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Slider(
            value = shownLimit.toFloat(),
            onValueChange = { position ->
                val next = snapMemoryLimit(position, maxLimit)
                if (next != (pendingLimit ?: shownStored)) tick()
                pendingLimit = next
            },
            onValueChangeFinished = { pendingLimit?.let(onLimitChange) },
            valueRange = PROCESS_RUNTIME_MEMORY_MINIMUM.toFloat()..maxLimit.toFloat(),
            steps = memoryLimitSliderSteps(maxLimit),
            enabled = processRuntime,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = OruSpacing.TouchTarget)
                .alpha(if (processRuntime) 1f else DisabledAlpha)
                .semantics {
                    contentDescription = valueDescription
                    stateDescription = shownText
                },
        )
        Text(
            text = stringResource(R.string.oru_settings_memory_limit_helper),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = scheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                pendingLimit = recommended
                onLimitChange(recommended)
            },
            enabled = processRuntime,
            modifier = Modifier.defaultMinSize(minHeight = OruSpacing.TouchTarget),
        ) {
            Text(
                text = stringResource(
                    R.string.oru_settings_memory_limit_recommended,
                    context.formatMebibytes(recommended),
                ),
            )
        }
        if (heapLimitIgnored) {
            OruNotice(
                text = stringResource(R.string.oru_settings_memory_limit_ignored),
                tone = OruTone.Warn,
                actions = emptyList(),
            )
        }
    }
}

/** The opacity of the limit slider while the process runtime is off, where the limit has no effect. */
private const val DisabledAlpha = 0.5f

/** Packages allowed to start a batch run without asking. Each one can be revoked with a confirm. */
@Composable
private fun OruAllowlist(
    packages: List<String>,
    onRevoke: (String) -> Unit,
) {
    if (packages.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_advanced_external_batch_patch_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        return
    }
    packages.forEach { packageName ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onRevoke(packageName) }) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}

/** Group headings, as the Settings list sets its own: indented to the rows, with room below them. */
private val AdvancedLabelModifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
