/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PatchOptionKeys
import app.morphe.manager.patcher.patch.ExplicitOptionKind
import app.morphe.manager.patcher.patch.Option
import app.morphe.manager.patcher.patch.coerceOptionValue
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruRow
import app.morphe.manager.ui.oru.components.OruRowControl
import app.morphe.manager.ui.oru.components.OruSettingsRow
import app.morphe.manager.ui.viewmodel.HomeViewModel

/** How an option is edited. File, folder and image options are read only here: there is no picker yet. */
internal enum class OruOptionControl { ReadOnly, Choice, Toggle, Number, Decimal, List, Color, Text }

/** The control an option is edited with. The same type tests the expert dialog used, minus its picker kinds. */
internal fun oruOptionControl(option: Option<*>): OruOptionControl {
    val explicit = option.explicitKind
    val classifier = option.type.classifier
    val key = option.key
    return when {
        explicit == ExplicitOptionKind.Folder ||
                explicit == ExplicitOptionKind.FilePath ||
                explicit == ExplicitOptionKind.Files ||
                explicit == ExplicitOptionKind.Image -> OruOptionControl.ReadOnly
        !option.presets.isNullOrEmpty() -> OruOptionControl.Choice
        classifier == Boolean::class -> OruOptionControl.Toggle
        classifier == Int::class || classifier == Long::class -> OruOptionControl.Number
        classifier == Float::class || classifier == Double::class -> OruOptionControl.Decimal
        classifier == List::class -> OruOptionControl.List
        classifier == String::class &&
                (explicit == ExplicitOptionKind.Color || key.contains("color", ignoreCase = true)) -> OruOptionControl.Color
        classifier == String::class && key != PatchOptionKeys.CUSTOM_NAME &&
                listOf("icon", "header", "custom").any { key.contains(it, ignoreCase = true) } -> OruOptionControl.ReadOnly
        classifier == String::class -> OruOptionControl.Text
        else -> OruOptionControl.ReadOnly
    }
}

/** The value an option holds: the stored one when the selection has a key for it, otherwise the patch default. */
internal fun oruOptionValue(option: Option<*>, values: Map<String, Any?>): Any? =
    if (option.key in values) values[option.key] else option.default

/** The value the editor stores for [text]. Null for an empty or unreadable number and for an empty list. */
private fun typedValue(option: Option<*>, control: OruOptionControl, text: String): Any? = when (control) {
    OruOptionControl.Number, OruOptionControl.Decimal ->
        if (text.isBlank()) null else coerceOptionValue(option.type, text)
    OruOptionControl.List -> text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null }
    else -> text
}

private fun displayText(value: Any?): String = when (value) {
    null -> ""
    is List<*> -> value.joinToString(", ")
    else -> value.toString()
}

/**
 * The options of one patch as cards, each with its own control. [valueOf] gives the value shown and [onChange] stores
 * a new one (null drops it back to the default), through the same call the expert dialog used.
 */
internal fun LazyListScope.OruOptionItems(
    options: List<Option<*>>,
    valueOf: (Option<*>) -> Any?,
    onChange: (Option<*>, Any?) -> Unit,
) {
    items(options, key = { it.key }) { option ->
        OruOptionEditor(
            option = option,
            value = valueOf(option),
            onChange = { onChange(option, it) },
        )
    }
}

/** The options sheet of one patch: its options, and a way back to the selection. */
@Composable
internal fun ColumnScope.OruOptionsContent(
    host: OruExpertSheetHost,
    home: HomeViewModel,
    bundleUid: Int,
    patchName: String,
) {
    val patch = home.expertModeBundles.firstOrNull { it.uid == bundleUid }?.patches?.firstOrNull { it.name == patchName }
    val options = patch?.options.orEmpty()
    val values = home.expertModeOptions[bundleUid]?.get(patchName).orEmpty()

    LazyColumn(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = SheetGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = patch?.displayName ?: patchName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.oru_options_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OruOptionItems(
            options = options,
            valueOf = { option -> oruOptionValue(option, values) },
            onChange = { option, value ->
                home.updateOptionInExpertMode(bundleUid, patchName, option.key, value)
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OruOutlinedButton(
            text = stringResource(R.string.oru_action_back),
            onClick = { host.open(OruExpertSheet.Patches) },
            modifier = Modifier.weight(1f),
        )
        OruOutlinedButton(
            text = stringResource(R.string.oru_options_reset_all),
            onClick = { home.resetOptionsInExpertMode(bundleUid, patchName) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OruOptionEditor(
    option: Option<*>,
    value: Any?,
    onChange: (Any?) -> Unit,
) {
    val control = oruOptionControl(option)
    if (control == OruOptionControl.Toggle) {
        val checked = value as? Boolean ?: (option.default as? Boolean) ?: false
        OruSettingsRow(
            title = option.title,
            supporting = option.description.takeIf { it.isNotBlank() },
            control = OruRowControl.Switch(checked = checked),
            onClick = { onChange(!checked) },
        )
        return
    }

    val changed = displayText(value) != displayText(option.default)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (option.description.isNotBlank()) {
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (changed) {
                TextButton(onClick = { onChange(null) }) {
                    Text(stringResource(R.string.oru_options_reset_one))
                }
            }
        }
        when (control) {
            OruOptionControl.Choice -> option.presets.orEmpty().forEach { (label, presetValue) ->
                OruRow(
                    title = label,
                    value = null,
                    onClick = { onChange(presetValue) },
                    trailing = oruChosenMark(displayText(presetValue) == displayText(value)),
                )
            }
            OruOptionControl.ReadOnly -> {
                if (displayText(value).isNotBlank()) {
                    Text(
                        text = displayText(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.oru_options_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> OruTextOption(option = option, control = control, value = value, onChange = onChange)
        }
    }
}

/** A text, number, list or colour field. It keeps what the user typed, and follows the stored value when that changes. */
@Composable
private fun OruTextOption(
    option: Option<*>,
    control: OruOptionControl,
    value: Any?,
    onChange: (Any?) -> Unit,
) {
    var text by remember(option.key) { mutableStateOf(displayText(value)) }
    LaunchedEffect(value) {
        if (typedValue(option, control, text) != value) text = displayText(value)
    }
    val invalid = (control == OruOptionControl.Number || control == OruOptionControl.Decimal) &&
            text.isNotBlank() && typedValue(option, control, text) == null
    val keyboardType = when (control) {
        OruOptionControl.Number -> KeyboardType.Number
        OruOptionControl.Decimal -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                onChange(typedValue(option, control, typed))
            },
            modifier = Modifier.weight(1f),
            singleLine = control != OruOptionControl.List,
            isError = invalid,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        if (control == OruOptionControl.Color) {
            ColourSwatch(text = text)
        }
    }
    if (invalid) {
        Text(
            text = stringResource(R.string.oru_options_invalid_number),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The colour a hex text names, drawn as a disc. Nothing is drawn for text that is not a colour. */
@Composable
private fun ColourSwatch(text: String) {
    val colour = runCatching { Color(android.graphics.Color.parseColor(text)) }.getOrNull()
    if (colour != null) {
        Box(
            modifier = Modifier
                .size(SwatchSize)
                .clip(CircleShape)
                .background(colour),
        )
    }
}

private val SheetGutter = 20.dp
private val SwatchSize = 32.dp
