/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.ui.oru.theme.OruMotion

/**
 * A settings row. A [OruRowControl.Switch] row toggles as a whole and TalkBack reads its state.
 * Every other row opens through [onClick].
 */
@Composable
fun OruSettingsRow(
    title: String,
    supporting: String?,
    control: OruRowControl,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val switch = control as? OruRowControl.Switch
    val tap = if (switch != null) {
        Modifier.toggleable(
            value = switch.checked,
            role = Role.Switch,
            onValueChange = { onClick() },
        )
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .clip(RowShape)
            .then(tap)
            .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (control) {
            is OruRowControl.Switch -> OruSwitch(checked = control.checked)
            is OruRowControl.Value -> Text(
                text = control.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            is OruRowControl.Chevron -> Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ChevronSize),
            )
            is OruRowControl.None -> Unit
        }
    }
}

/**
 * The switch as in the prototype: an outlined track that fills when on, and a thumb that grows and gains
 * a check when on. Its motion follows the reduced-motion signal.
 */
@Composable
private fun OruSwitch(checked: Boolean) {
    val reduced = oruReducedMotion()
    val scheme = MaterialTheme.colorScheme
    // A switch that is on takes the app's fill inside its Focus, and the Oru primary everywhere else
    val selectedFill = oruSelectedFill()
    val selectedContent = oruSelectedContent()
    val trackColor by animateColorAsState(
        targetValue = if (checked) selectedFill else scheme.surfaceContainerHighest,
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) selectedContent else scheme.outline,
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruSwitchThumb",
    )
    val thumbSize by animateDpAsState(
        targetValue = if (checked) ThumbOnSize else ThumbOffSize,
        animationSpec = OruMotion.expand(reduced),
        label = "oruSwitchThumbSize",
    )
    val thumbStart by animateDpAsState(
        targetValue = if (checked) ThumbOnStart else ThumbOffStart,
        animationSpec = OruMotion.expand(reduced),
        label = "oruSwitchThumbStart",
    )
    Box(
        modifier = Modifier
            .clearAndSetSemantics { }
            .size(width = TrackWidth, height = TrackHeight)
            .clip(TrackShape)
            .background(trackColor)
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier.border(width = TrackBorder, color = scheme.outline, shape = TrackShape)
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbStart, y = (TrackHeight - thumbSize) / 2f)
                .size(thumbSize)
                .oruSlab(shape = CircleShape, color = thumbColor, compact = true),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = selectedFill,
                    modifier = Modifier.size(CheckSize),
                )
            }
        }
    }
}

private val RowShape = RoundedCornerShape(16.dp)
private val RowMinHeight = 56.dp
private val RowPaddingHorizontal = 12.dp
private val RowPaddingVertical = 10.dp
private val ChevronSize = 22.dp
private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val TrackBorder = 2.dp
private val TrackShape = RoundedCornerShape(16.dp)
private val ThumbOffSize: Dp = 16.dp
private val ThumbOnSize: Dp = 24.dp
private val ThumbOffStart: Dp = 8.dp
private val ThumbOnStart: Dp = 22.dp
private val CheckSize = 18.dp
