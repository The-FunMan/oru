/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R

/**
 * A focus row: a title, an optional value line and, when [onClick] is set, a chevron. [trailing]
 * replaces the chevron. The row is at least 56 dp tall and grows with its text. [shape] is the row's
 * corner; a row inside a group takes the group's inner shape, and the group clips the outer corners. A row of its own
 * is a raised slab (see OruDepth.kt) with a contact shadow; a row inside a group is [OruElevation.Flat], since the
 * group is the slab.
 */
@Composable
fun OruRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    shape: Shape = RowShape,
    elevation: OruElevation = oruRowElevation(shape),
) {
    val tap = onClick
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .oruSlab(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                elevation = elevation,
                compact = true,
                shadow = elevation != OruElevation.Flat,
            )
            .then(if (tap != null) Modifier.clickable(role = Role.Button, onClick = tap) else Modifier)
            .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (tap != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ChevronSize),
            )
        }
    }
}

/**
 * The patch channel of a source: Stable or Pre-release. The pre-release option is disabled when the
 * source offers only stable patches, and [OruChannelUi.supporting] says which one is in use.
 */
@Composable
fun OruChannelRow(
    channel: OruChannelUi,
    onSelect: (preRelease: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RowShape,
    elevation: OruElevation = oruRowElevation(shape),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .oruSlab(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                elevation = elevation,
                compact = true,
                shadow = elevation != OruElevation.Flat,
            )
            .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.oru_focus_channel),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = channel.supporting,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OruConnectedPair(
            groupLabel = stringResource(R.string.oru_focus_channel),
            firstLabel = stringResource(R.string.oru_focus_channel_stable),
            secondLabel = stringResource(R.string.oru_focus_channel_prerelease),
            secondSelected = channel.preRelease,
            onSelect = onSelect,
            secondEnabled = channel.preReleaseAvailable,
        )
    }
}

/** A row with its own shape stands on its own; one with the shape of a group (anything but [RowShape]) is in a group. */
private fun oruRowElevation(shape: Shape): OruElevation =
    if (shape == RowShape) OruElevation.Raised else OruElevation.Flat

private val RowShape = RoundedCornerShape(20.dp)
private val RowMinHeight = 56.dp
private val RowPaddingHorizontal = 18.dp
private val RowPaddingVertical = 14.dp
private val ChevronSize = 22.dp
