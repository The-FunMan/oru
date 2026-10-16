/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruPrimaryButton
import app.morphe.manager.ui.oru.components.OruRowControl
import app.morphe.manager.ui.oru.components.OruSecondaryButton
import app.morphe.manager.ui.oru.components.OruSectionLabel
import app.morphe.manager.ui.oru.components.OruSettingsRow
import app.morphe.manager.ui.oru.home.oruAppName
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability

/**
 * The patch selection sheet, the Oru form of the expert patch dialog. The selection is the one HomeViewModel
 * holds (`expertModePatches`); each change goes through [OruPatchSelectionState], which applies the availability
 * rules, and is written back to the same field. Continue is the existing `proceedExpertMode`, which saves the
 * selection, the options and the seen patches before the run, as the dialog did.
 */
@Composable
internal fun ColumnScope.OruPatchContent(
    host: OruExpertSheetHost,
    home: HomeViewModel,
    item: HomeAppItem,
) {
    val bundles = home.expertModeBundles
    val allPatches = remember(bundles, home.expertModePatches) {
        bundles.flatMap { bundle ->
            // Recommended resets to the defaults among the patches that fit the app, as the dialog's reset did
            val fitting = bundle.patchSequence(allowIncompatible = false).mapTo(mutableSetOf<String>()) { it.name }
            bundle.patches.map { patch ->
                OruPatchItem(
                    bundleUid = bundle.uid,
                    name = patch.name,
                    displayName = patch.displayName,
                    description = patch.description,
                    category = patch.category,
                    universal = patch.isUniversal,
                    lock = home.expertModeLockState(patch),
                    defaultSelected = patch.name in fitting &&
                            patch.defaultSelected(home.currentInstallerType, home.currentApkArchitecture),
                    optionCount = patch.options?.size ?: 0,
                )
            }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }

    val state = OruPatchSelectionState(
        items = allPatches,
        selected = home.expertModePatches,
        query = query,
        category = category,
        armed = armed,
    )
    val settle: OruSettle = { selection ->
        selection.applyAvailability(
            home.currentInstallerType,
            home.currentApkArchitecture,
            bundles.associate { bundle -> bundle.uid to bundle.patches.associateBy { it.name } },
        )
    }
    fun publish(next: OruPatchSelectionState) {
        home.expertModePatches = next.selected
        armed = next.armed
    }

    val visible = state.visible()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.oru_patches_title, oruAppName(item.packageName, item.displayName)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.oru_focus_patches_selected, state.selectedCount, allPatches.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.oru_patches_search)) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
        )
        if (state.categories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OruFilterChip(
                    text = stringResource(R.string.oru_patches_all),
                    selected = category == null,
                    onClick = { category = null },
                )
                state.categories.forEach { name ->
                    OruFilterChip(text = name, selected = category == name, onClick = { category = name })
                }
            }
        }
        // Three equal weights wrapped "Recommended" at font scale 1.3. Each button now takes the width its label
        // needs, and the row scrolls sideways when the labels do not all fit, so no label ever breaks in two.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OruSecondaryButton(
                text = stringResource(R.string.oru_patches_recommended),
                onClick = { publish(state.recommended(settle)) },
            )
            OruSecondaryButton(
                text = stringResource(R.string.oru_patches_select_all),
                onClick = { publish(state.selectAll(settle)) },
            )
            OruOutlinedButton(
                text = stringResource(R.string.oru_patches_clear),
                onClick = { publish(state.clear(settle)) },
            )
        }
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (visible.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.oru_patches_none_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        bundles.forEach { bundle ->
            val patches = visible.filter { it.bundleUid == bundle.uid }
            if (patches.isNotEmpty()) {
                // One source is shown in Oru, so its name is no section to tell apart from others
                if (bundles.size > 1) {
                    item(key = "source-${bundle.uid}") {
                        OruSectionLabel(
                            text = sourceTitle(home, bundle.uid, bundle.name),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(patches, key = { "${it.bundleUid}/${it.name}" }) { patch ->
                    val openOptions: () -> Unit = {
                        host.open(OruExpertSheet.PatchOptions(patch.bundleUid, patch.name))
                    }
                    OruPatchRow(
                        patch = patch,
                        selected = patch.name in home.expertModePatches[patch.bundleUid].orEmpty(),
                        onToggle = { publish(state.toggle(patch.bundleUid, patch.name, settle)) },
                        onOptions = if (patch.optionCount > 0) openOptions else null,
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OruOutlinedButton(
            text = stringResource(R.string.oru_action_cancel),
            onClick = { home.cleanupExpertModeData() },
            modifier = Modifier.weight(1f),
        )
        OruPrimaryButton(
            text = stringResource(R.string.oru_action_continue),
            onClick = { home.proceedExpertMode() },
            enabled = state.selectedCount > 0,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OruPatchRow(
    patch: OruPatchItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onOptions: (() -> Unit)?,
) {
    val note = when (patch.lock) {
        PatchLockState.LOCKED_ON -> stringResource(R.string.oru_patches_required)
        PatchLockState.LOCKED_OFF -> stringResource(R.string.oru_patches_unavailable)
        PatchLockState.NONE -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        OruSettingsRow(
            title = patch.displayName,
            supporting = note ?: patch.description,
            control = OruRowControl.Switch(checked = selected),
            onClick = onToggle,
        )
        if (onOptions != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOptions) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.oru_patch_options_count,
                            patch.optionCount,
                            patch.optionCount,
                        ),
                    )
                }
            }
        }
    }
}

/** The name a source shows under in the sheets (the default source is never named by its own configured name). */
internal fun sourceTitle(home: HomeViewModel, uid: Int, fallback: String): String {
    val source = home.getPatchSource(uid) ?: return oruSourceDisplayName(uid, fallback)
    // Named with its repository where another source has the same name, so the two can be told apart
    return displaySourceName(source.asNameFact(), home.patchBundleRepository.sources.value.map { it.asNameFact() })
}

private val SheetGutter = 20.dp
