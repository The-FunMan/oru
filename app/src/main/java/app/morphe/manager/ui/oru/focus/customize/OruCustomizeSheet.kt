/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.oru.components.OruPrimaryButton
import app.morphe.manager.ui.oru.components.OruSectionLabel
import app.morphe.manager.ui.oru.focus.expert.OruExpertSheet
import app.morphe.manager.ui.oru.focus.expert.OruExpertSheetHost
import app.morphe.manager.ui.oru.focus.expert.OruOptionItems
import app.morphe.manager.ui.oru.focus.expert.oruOptionValue
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The Customize sheet: the name and icon the patched app is built with, for the selected patches that declare them.
 * The values are the patch options HomeViewModel holds, saved with the selection by `proceedExpertMode`, so no
 * storage of its own.
 */
@Composable
internal fun ColumnScope.OruCustomizeContent(
    host: OruExpertSheetHost,
    home: HomeViewModel,
) {
    val targets = oruBrandingTargets(home.expertModePatches) { uid, name ->
        patchOf(home, uid, name)?.options.orEmpty().mapTo(mutableSetOf<String>()) { it.key }
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.oru_customize_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.oru_customize_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        targets.forEach { (uid, name) ->
            val patch = patchOf(home, uid, name)
            val branding = patch?.options.orEmpty().filter { it.key in ORU_BRANDING_OPTION_KEYS }
            val values = home.expertModeOptions[uid]?.get(name).orEmpty()
            item(key = "patch-$uid/$name") {
                OruSectionLabel(text = patch?.displayName ?: name)
            }
            OruOptionItems(
                options = branding,
                valueOf = { option -> oruOptionValue(option, values) },
                onChange = { option, value -> home.updateOptionInExpertMode(uid, name, option.key, value) },
            )
        }
    }

    OruPrimaryButton(
        text = stringResource(R.string.oru_action_back),
        onClick = { host.open(OruExpertSheet.Patches) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = 12.dp),
    )
}

/** The patch [name] of source [uid] as the selection sheet lists it, or null when the source no longer has it. */
private fun patchOf(home: HomeViewModel, uid: Int, name: String): PatchInfo? =
    home.expertModeBundles.firstOrNull { it.uid == uid }?.patches?.firstOrNull { it.name == name }

private val SheetGutter = 20.dp
