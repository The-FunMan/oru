/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.components.LocalOruAppTint
import app.morphe.manager.ui.oru.components.oruSlab

/** A selectable filter chip of the patch sheet: a category, or All. */
@Composable
internal fun OruFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // A selected chip takes the app's tile colour in its Focus, with the text the tile was checked for
    val appTint = LocalOruAppTint.current
    val selectedContainer = appTint?.tile ?: scheme.secondaryContainer
    val selectedContent = appTint?.onContainer ?: scheme.onSecondaryContainer
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .oruSlab(
                shape = MaterialTheme.shapes.small,
                color = if (selected) selectedContainer else scheme.surfaceContainerHigh,
                compact = true,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) selectedContent else scheme.onSurfaceVariant,
        )
    }
}
