/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install.postinstall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruGlassRole
import app.morphe.manager.ui.oru.components.OruGlassSurface
import app.morphe.manager.ui.oru.components.OruSectionLabel

/**
 * The "What changed" list of a finished run: the first [ShownPatches] patches that succeeded, in plain lines, and a
 * "+ N more" that expands the rest in place. A second tap collapses it again. Shows nothing when no patch is known.
 *
 * The expanded rows scroll inside a box no taller than [oruChangesListMaxHeightDp] allows, so a long list never pushes
 * the toggle or the buttons of the screen off the bottom edge. The toggle stays directly below that box.
 */
@Composable
internal fun OruDoneChanges(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hidden = names.size - ShownPatches
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    OruGlassSurface(
        shape = RoundedCornerShape(CardCorner),
        role = OruGlassRole.Card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(LineGap),
        ) {
            OruSectionLabel(text = stringResource(R.string.oru_done_changes_title))
            // The space left here is infinite inside a scrolling parent, and then the screen height alone caps the list
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val listMaxHeight = oruChangesListMaxHeightDp(screenHeightDp, maxHeight.value)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LineGap),
                ) {
                    oruVisibleChangeRows(names, expanded).forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (hidden > 0) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.oru_done_changes_fewer)
                    } else {
                        stringResource(R.string.oru_done_changes_more, hidden)
                    },
                    modifier = Modifier
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(vertical = LinkPadding),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The share of the screen height the expanded "What changed" list may take. */
internal const val OruChangesMaxHeightFraction = 0.4f

/**
 * The tallest the list may be, in dp: [OruChangesMaxHeightFraction] of the screen height, but never more than the
 * space left in its parent. Never negative.
 */
internal fun oruChangesListMaxHeightDp(screenHeightDp: Float, spaceLeftDp: Float): Float =
    minOf(screenHeightDp * OruChangesMaxHeightFraction, spaceLeftDp).coerceAtLeast(0f)

/** The rows the card lists: the first [ShownPatches] when collapsed, every name when expanded. */
internal fun oruVisibleChangeRows(names: List<String>, expanded: Boolean): List<String> =
    if (expanded) names else names.take(ShownPatches)

/** The patches listed before the "+ N more" line. */
private const val ShownPatches = 3

private val CardCorner = 24.dp
private val CardPadding = 16.dp
private val LineGap = 6.dp
private val LinkPadding = 12.dp
